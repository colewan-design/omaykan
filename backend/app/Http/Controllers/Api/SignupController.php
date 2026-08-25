<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Public self-serve signup: creates an organization, its first store, the owner
 * account and a pending subscription in one shot, so a new merchant has a
 * working store immediately.
 *
 * Replaces api/signup.ts. Payment is a manual GCash transfer verified later
 * through PlatformAdminController — this endpoint only records the reference.
 *
 * The whole thing runs in one transaction, which fixes a flaw in the Firestore
 * version: there, the auth user was created before the batch write, so a failed
 * batch orphaned the account with no cleanup. Here nothing is committed unless
 * all of it is.
 */
class SignupController extends Controller
{
    /** Placeholder until a real price exists. Mirrored for display by
     *  apps/web/src/onboarding/pricingConstants.ts. */
    private const PLAN_ID = 'standard-monthly';

    private const PLAN_AMOUNT_CENTS = 49900;

    private const BUSINESS_MODES = ['coffee-shop', 'grocery', 'restaurant', 'nail-salon'];

    /** No 0/O or 1/I/L: the owner reads this back to customers out loud. */
    private const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'businessName' => ['required', 'string', 'max:120'],
            'ownerFullName' => ['required', 'string', 'max:120'],
            'username' => ['required', 'string', 'max:60'],
            'password' => ['required', 'string', 'min:6'],
            'businessMode' => ['required', Rule::in(self::BUSINESS_MODES)],
            'gcashReference' => ['required', 'string', 'max:80'],
        ]);

        $username = strtolower(trim($validated['username']));

        if (User::query()->where('username', $username)->exists()) {
            throw ValidationException::withMessages([
                'username' => 'That username is already taken — try a different one.',
            ]);
        }

        $result = DB::transaction(function () use ($validated, $username) {
            $businessName = trim($validated['businessName']);

            $organization = Organization::query()->create([
                'id' => (string) str()->uuid(),
                'name' => $businessName,
                'slug' => $this->uniqueSlug($businessName),
                'status' => 'active',
            ]);

            $storeCode = $this->uniqueStoreCode();

            $store = new Store([
                'id' => (string) str()->uuid(),
                'organization_id' => $organization->id,
                'name' => $businessName,
                // The first store is always 'main'; multi-branch comes later.
                'code' => 'main',
                'address' => '',
                'business_mode' => $validated['businessMode'],
                'timezone' => 'Asia/Manila',
                'currency_code' => 'PHP',
                'status' => 'active',
            ]);
            $store->setPairingCode($storeCode);
            $store->save();

            $owner = User::query()->create([
                'name' => trim($validated['ownerFullName']),
                'username' => $username,
                'password' => $validated['password'],
                'status' => 'active',
            ]);

            OrganizationMembership::query()->create([
                'organization_id' => $organization->id,
                'user_id' => $owner->id,
                'membership_role' => 'admin',
            ]);

            StoreMembership::query()->create([
                'store_id' => $store->id,
                'user_id' => $owner->id,
                'membership_role' => 'admin',
            ]);

            Subscription::query()->create([
                'id' => (string) str()->uuid(),
                'organization_id' => $organization->id,
                'status' => Subscription::STATUS_PENDING,
                'plan' => self::PLAN_ID,
                'amount_cents' => self::PLAN_AMOUNT_CENTS,
                'gcash_reference' => trim($validated['gcashReference']),
                'submitted_at' => now(),
            ]);

            return [
                'organizationSlug' => $organization->slug,
                'storeCode' => $store->code,
                // The code the owner hands to customers. Returned once here
                // and also readable later from Settings > Online Store.
                'pairingCode' => $store->public_store_code,
            ];
        });

        return response()->json($result, 201);
    }

    /**
     * Slugs are derived from the business name, so two merchants with the same
     * name collide. Suffix until free.
     */
    private function uniqueSlug(string $businessName): string
    {
        $base = str()->slug($businessName) ?: 'store';
        $candidate = $base;

        for ($attempt = 2; $attempt <= 50; $attempt++) {
            if (! Organization::query()->where('slug', $candidate)->exists()) {
                return $candidate;
            }

            $candidate = "{$base}-{$attempt}";
        }

        throw ValidationException::withMessages([
            'businessName' => 'Could not allocate a store ID — try a slightly different business name.',
        ]);
    }

    private function uniqueStoreCode(): string
    {
        for ($attempt = 0; $attempt < 20; $attempt++) {
            $code = '';
            for ($i = 0; $i < 6; $i++) {
                $code .= self::CODE_ALPHABET[random_int(0, strlen(self::CODE_ALPHABET) - 1)];
            }

            if (! Store::query()->where('public_store_code', $code)->exists()) {
                return $code;
            }
        }

        abort(500, 'Could not generate a unique store code — please try again.');
    }
}
