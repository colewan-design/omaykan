<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Rider;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Storage;
use Illuminate\Validation\Rules\Password as PasswordRule;
use Illuminate\Validation\ValidationException;

/**
 * Sign-up and sign-in for riders.
 *
 * Registration is self-serve but not self-approving: anyone can create an
 * account by submitting a licence and a plate, and the account can do nothing
 * but look at its own status until an operator has seen those documents. That
 * is the whole reason the documents are collected at registration rather than
 * later — an unreviewed rider must never be able to claim a stranger's address.
 *
 * Tokens are Sanctum personal access tokens on the `rider` guard. They are
 * issued to pending riders too, deliberately: without one, a rider who closed
 * the tab after signing up would have no way back to their own status.
 */
class RiderAuthController extends Controller
{
    private const TOKEN_NAME = 'rider-portal';

    /** Where document images live on the private disk. */
    private const DOCUMENT_DIRECTORY = 'rider-documents';

    /** 8MB. Comfortably over a phone photo, well under a denial-of-service. */
    private const MAX_IMAGE_KB = 8192;

    public function register(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:120'],
            'email' => ['required', 'email', 'max:190'],
            'phone' => ['required', 'string', 'max:40'],
            'password' => ['required', 'confirmed', PasswordRule::min(8)],
            'licenseNumber' => ['required', 'string', 'max:60'],
            'plateNumber' => ['required', 'string', 'max:20'],
            // `image` rather than `mimes`: it checks the actual decoded image,
            // so a .jpg that is really a PHP file does not get through.
            'licenseImage' => ['required', 'image', 'mimes:jpeg,jpg,png,webp', 'max:'.self::MAX_IMAGE_KB],
            'plateImage' => ['required', 'image', 'mimes:jpeg,jpg,png,webp', 'max:'.self::MAX_IMAGE_KB],
        ]);

        // Checked by hand rather than with `unique:` so the comparison happens
        // on the same lowercased value the column stores — `unique` would let
        // "Ana@x.com" through when "ana@x.com" is already taken.
        if (Rider::findByEmail($data['email']) !== null) {
            throw ValidationException::withMessages([
                'email' => 'There is already a rider account with that email. Try signing in instead.',
            ]);
        }

        $plate = strtoupper(preg_replace('/\s+/', ' ', trim($data['plateNumber'])));

        // Stored before the row so a failed upload cannot leave an account
        // that claims to have documents it does not have; the transaction
        // below then makes the reverse true — a failed insert leaves two
        // orphaned files, which the cleanup in the catch removes.
        $licensePath = $this->storeDocument($data['licenseImage']);
        $platePath = $this->storeDocument($data['plateImage']);

        try {
            $rider = DB::transaction(fn () => Rider::query()->create([
                'name' => trim($data['name']),
                'email' => $data['email'],
                'phone' => trim($data['phone']),
                'password' => $data['password'],
                'license_number' => strtoupper(trim($data['licenseNumber'])),
                'plate_number' => $plate,
                'license_image_path' => $licensePath,
                'plate_image_path' => $platePath,
                'status' => Rider::STATUS_PENDING,
            ]));
        } catch (\Throwable $e) {
            Storage::disk('local')->delete([$licensePath, $platePath]);

            throw $e;
        }

        return $this->sessionResponse($rider, 201);
    }

    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
        ]);

        $rider = Rider::findByEmail($data['email']);

        // One message for "no such account" and for "wrong password", and the
        // hash is checked against a dummy when there is no account, so the
        // reply neither says nor times differently whether an address is
        // registered here.
        if ($rider === null) {
            Hash::check($data['password'], '$2y$12$'.str_repeat('.', 53));

            throw ValidationException::withMessages([
                'email' => 'That email and password don\'t match a rider account.',
            ]);
        }

        if (! Hash::check($data['password'], $rider->password)) {
            throw ValidationException::withMessages([
                'email' => 'That email and password don\'t match a rider account.',
            ]);
        }

        // Rejected and suspended riders still sign in. Being told why, on
        // their own status screen, is the only route they have back to an
        // operator; a blank "wrong password" would just look like a bug.
        return $this->sessionResponse($rider);
    }

    /** Signs out this device only; other phones stay signed in. */
    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()->delete();

        return response()->json(['signedOut' => true]);
    }

    public function me(Request $request): JsonResponse
    {
        $rider = $request->user();
        $rider->forceFill(['last_seen_at' => now()])->save();

        return response()->json(['rider' => $rider->toPortalArray()]);
    }

    /**
     * Names the file by a random id, never by anything the uploader supplied.
     * The original filename reaches the disk nowhere: it is attacker-chosen
     * text, and it is the usual way a path ends up somewhere it should not be.
     */
    private function storeDocument(UploadedFile $file): string
    {
        return $file->store(self::DOCUMENT_DIRECTORY, 'local');
    }

    private function sessionResponse(Rider $rider, int $status = 200): JsonResponse
    {
        $rider->forceFill(['last_seen_at' => now()])->save();

        return response()->json([
            'rider' => $rider->toPortalArray(),
            'token' => $rider->createToken(self::TOKEN_NAME, ['rider'])->plainTextToken,
        ], $status);
    }
}
