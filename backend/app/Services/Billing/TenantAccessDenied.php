<?php

namespace App\Services\Billing;

use Illuminate\Http\Exceptions\HttpResponseException;

/**
 * The one refusal a client sees when an organization may not do something.
 *
 * Every chokepoint throws this rather than composing its own abort, so the till,
 * the seller app and the storefront each handle *one* shape:
 *
 *     { "message": "…", "tenantAccess": "suspended" | "unpaid" }
 *
 * Same idea as EnsureRiderIsApproved's `riderStatus`: prose for a person,
 * a field for the client to branch on.
 *
 * **403, never 401.** The caller's token is real; their shop is what is
 * refused. Clients clear a stored token on 401 — see the `withExceptions` note
 * in bootstrap/app.php for what happened the last time a refusal came back as
 * the wrong status — and a merchant silently signed out on the day their shop
 * was suspended would report a broken login, not a suspension.
 *
 * The storefront endpoints send the same body with a 404-shaped meaning; see
 * `forStorefront`.
 */
class TenantAccessDenied extends HttpResponseException
{
    public static function for(TenantAccess $access): self
    {
        return new self(response()->json([
            'message' => $access->message() ?? 'This shop is not available.',
            'tenantAccess' => $access->value,
        ], 403));
    }

    /**
     * The public side: a shopper looking at, or ordering from, a shop that is
     * not trading.
     *
     * 404 rather than 403, because to a customer the shop is simply not there
     * — whether it is unpaid or suspended is between the merchant and us, and
     * a storefront announcing "this business owes money" would be a strange
     * thing for a marketplace to publish. The `tenantAccess` field is still
     * sent, collapsed to one value, so the page can say "closed" rather than
     * "not found".
     */
    public static function forStorefront(): self
    {
        return new self(response()->json([
            'message' => 'This shop isn\'t taking orders right now.',
            'tenantAccess' => 'closed',
        ], 404));
    }
}
