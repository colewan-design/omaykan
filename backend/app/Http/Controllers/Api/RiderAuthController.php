<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Rider;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Password;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
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
            /*
             * The bike. Constrained when sent, but not required: the web rider
             * portal registered accounts for months before this column
             * existed, and a client that has not been updated must keep
             * working rather than start 422-ing on a field it has never heard
             * of. The column's own default carries those — see the migration.
             *
             * The three descriptive fields are optional for a different
             * reason: a rider signing up at a junction should not be blocked
             * on remembering what their scooter is officially called.
             */
            'vehicleType' => ['sometimes', 'required', 'string', Rule::in(Rider::VEHICLE_TYPES)],
            'vehicleMake' => ['nullable', 'string', 'max:60'],
            'vehicleModel' => ['nullable', 'string', 'max:60'],
            'vehicleColor' => ['nullable', 'string', 'max:40'],
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
                'vehicle_type' => $data['vehicleType'] ?? 'motorcycle',
                'vehicle_make' => $this->cleanOrNull($data['vehicleMake'] ?? null),
                'vehicle_model' => $this->cleanOrNull($data['vehicleModel'] ?? null),
                'vehicle_color' => $this->cleanOrNull($data['vehicleColor'] ?? null),
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
     * Asks for a reset link, and says the same thing either way.
     *
     * Same shape as the shopper's, and the constant reply matters more here:
     * the set of people who ride for the platform is small and knowable, so an
     * endpoint that distinguished "no such rider" from "link sent" would be a
     * way to enumerate them by address.
     *
     * Until this existed a rider who forgot their password was simply locked
     * out — there is no operator screen that sets one, and the licence photos
     * they would have to re-upload to register again are already on file
     * against the address they cannot use.
     */
    public function forgotPassword(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
        ]);

        Password::broker('riders')->sendResetLink([
            'email' => strtolower(trim($data['email'])),
        ]);

        return response()->json([
            'message' => 'If that email has a rider account, a reset link is on its way.',
        ]);
    }

    public function resetPassword(Request $request): JsonResponse
    {
        $data = $request->validate([
            'token' => ['required', 'string'],
            'email' => ['required', 'email'],
            'password' => ['required', 'confirmed', PasswordRule::min(8)],
        ]);

        $status = Password::broker('riders')->reset(
            [
                'email' => strtolower(trim($data['email'])),
                'password' => $data['password'],
                'password_confirmation' => $data['password_confirmation'] ?? $data['password'],
                'token' => $data['token'],
            ],
            function (Rider $rider, string $password) {
                $rider->forceFill([
                    'password' => $password,
                    'remember_token' => Str::random(60),
                ])->save();

                /*
                 * Every phone, signed out.
                 *
                 * A rider resetting a password may be doing it because somebody
                 * else has it, and a rider token is not a shopping session: it
                 * reads a live list of strangers' home addresses and phone
                 * numbers. The status the account is in does not matter here —
                 * a suspended rider's tokens are already gone, and an approved
                 * one gets a clean set on the next sign-in.
                 */
                $rider->tokens()->delete();
            },
        );

        if ($status !== Password::PASSWORD_RESET) {
            throw ValidationException::withMessages([
                'token' => 'That reset link has expired or has already been used. Ask for a new one.',
            ]);
        }

        return response()->json(['message' => 'Your password has been changed. Sign in with it.']);
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

    /**
     * Trim a free-text field, and treat whitespace as absence.
     *
     * A vehicle colour of `" "` is not a colour, and storing it would make
     * `vehicleLabel()` render a stray space between the make and the model.
     */
    private function cleanOrNull(?string $value): ?string
    {
        if ($value === null) {
            return null;
        }

        $trimmed = trim($value);

        return $trimmed === '' ? null : $trimmed;
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
