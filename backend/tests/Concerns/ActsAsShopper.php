<?php

namespace Tests\Concerns;

use App\Models\CustomerAccount;

/**
 * A signed-in shopper, for the suites that place storefront orders.
 *
 * POST /api/online-orders requires an account (`auth:customer` on the route),
 * so every test that places one needs a token. Shared rather than repeated in
 * each suite: four of them place orders, and a helper that drifts between
 * copies is how "verified" quietly becomes false in one of them and the
 * failure gets blamed on the endpoint.
 *
 * The account gate itself is covered in OnlineOrderApiTest. Everywhere else
 * this is scaffolding — the tests are about mail, riders and the seller's
 * dashboard, and simply need an order to exist.
 */
trait ActsAsShopper
{
    private ?CustomerAccount $shopperAccount = null;

    /**
     * Signs the next request in as a shopper with a verified email.
     *
     * Memoised because several tests place two orders, and a second account
     * would collide on the unique email column.
     */
    protected function asShopper(): static
    {
        return $this->withToken(
            $this->shopper()->createToken('customer-portal', ['customer'])->plainTextToken,
        );
    }

    protected function shopper(): CustomerAccount
    {
        // markEmailAsVerified() rather than passing email_verified_at to
        // create(): it is not fillable, so mass assignment silently drops it
        // and the account comes back unverified — which the endpoint refuses.
        return $this->shopperAccount ??= tap(
            CustomerAccount::query()->create([
                'name' => 'Maria Santos',
                'email' => 'maria@example.com',
                'phone' => '09171234567',
                'password' => 'a-shopper-password',
            ]),
            fn (CustomerAccount $account) => $account->markEmailAsVerified(),
        );
    }
}
