<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Rider;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rules\Password as PasswordRule;
use Illuminate\Validation\ValidationException;

/**
 * What a rider can change about themselves.
 *
 * Its own controller rather than more methods on RiderAuthController, for the
 * split CustomerAccountController already draws: that one is about getting a
 * token, this one is about the account behind it.
 *
 * Sits **outside** `rider.approved`, alongside `/me` and `/logout`. A rider
 * waiting on review is exactly the person most likely to need to fix a
 * mistyped phone number, and a suspended one must still be able to change a
 * password they think somebody else has. Neither can see a job.
 *
 * Three things are deliberately not editable here:
 *
 * - **The email.** It is the login handle and the address a reset link goes
 *   to, so changing it is as good as owning the account. The shopper's version
 *   asks for the password and allows it; a rider's account has more on the
 *   other side of it than an order history, and there is no support tooling to
 *   undo a takeover. It stays an operator's job until there is.
 * - **The licence number**, and the photographs behind it. An operator
 *   approved *those documents*; letting the number be edited afterwards would
 *   make the approval meaningless and is the one field a rider might have a
 *   real motive to change quietly.
 * - **The status.** Obviously.
 */
class RiderAccountController extends Controller
{
    public function update(Request $request): JsonResponse
    {
        /** @var Rider $rider */
        $rider = $request->user();

        $data = $request->validate([
            'name' => ['sometimes', 'required', 'string', 'max:120'],
            'phone' => ['sometimes', 'required', 'string', 'max:40'],
            /*
             * The plate is editable, and it is the one document-ish field that
             * is: riders change bikes, and a plate that no longer matches the
             * one on the shop's screen is worse than one that was never
             * checked — it makes the collection handshake fail at the counter.
             * The photograph is not re-collected here, so the operator's copy
             * and this string can disagree; the review screen shows both, and
             * that disagreement is the signal.
             */
            'plateNumber' => ['sometimes', 'required', 'string', 'max:20'],
        ]);

        if (array_key_exists('name', $data)) {
            $rider->name = trim($data['name']);
        }

        if (array_key_exists('phone', $data)) {
            $rider->phone = trim($data['phone']);
        }

        if (array_key_exists('plateNumber', $data)) {
            $rider->plate_number = strtoupper(trim($data['plateNumber']));
        }

        $rider->save();

        return response()->json(['rider' => $rider->fresh()->toPortalArray()]);
    }

    /**
     * The current password is required, always.
     *
     * There is no Google-shaped exception here the way there is for shoppers —
     * every rider account has a password, because registration is the only way
     * one is created.
     */
    public function updatePassword(Request $request): JsonResponse
    {
        /** @var Rider $rider */
        $rider = $request->user();

        $data = $request->validate([
            'currentPassword' => ['required', 'string'],
            'password' => ['required', 'confirmed', PasswordRule::min(8)],
        ]);

        if (! Hash::check($data['currentPassword'], $rider->password)) {
            throw ValidationException::withMessages([
                'currentPassword' => 'That is not your current password.',
            ]);
        }

        $rider->forceFill(['password' => $data['password']])->save();

        /*
         * Every other phone, signed out — but not this one.
         *
         * The same argument as the reset route makes, with one difference: the
         * rider is holding this device and did not ask to be signed out of it.
         * Killing the current token too would drop them onto the sign-in screen
         * mid-shift, possibly mid-delivery, which is how a safety feature
         * teaches people not to use it.
         */
        $current = $rider->currentAccessToken();
        $rider->tokens()->where('id', '!=', $current?->id)->delete();

        return response()->json(['rider' => $rider->fresh()->toPortalArray()]);
    }
}
