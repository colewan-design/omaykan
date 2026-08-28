<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Store;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Resolving a store from the short code a merchant hands out.
 *
 * Replaces api/resolve-store-code.ts and api/resolve-staff-store-code.ts. Both
 * are unauthenticated by necessity — the whole point is to find out which
 * organization you are talking to before you have any credentials for it.
 *
 * The two lookups stay separate rather than sharing one relaxed endpoint: the
 * customer-facing one must reject stores that cannot sell online, and the staff
 * one must not, because a nail salon owner still has to bind their browser to
 * their org.
 */
class StoreCodeController extends Controller
{
    /**
     * Only these modes can put something in a cart.
     *
     * Public because StoreDirectoryController gates the shop list on the same
     * rule: a mode that cannot be resolved by code must not be listed as a
     * place to order from either, and two copies of this list would drift.
     */
    public const ONLINE_MODES = ['coffee-shop', 'grocery', 'restaurant'];

    /**
     * Customer storefront: "which store is this code?", plus everything the
     * storefront needs to render its header and quote delivery.
     */
    public function resolve(Request $request): JsonResponse
    {
        $store = $this->lookup($request);

        if (! in_array($store->business_mode, self::ONLINE_MODES, true)) {
            return response()->json([
                'message' => 'This store is not set up for online ordering.',
            ], 409);
        }

        return response()->json([
            'orgSlug' => $store->organization->slug,
            'storeCode' => $store->code,
            'businessMode' => $store->business_mode,
            'storeName' => $store->name,
            'storeAddress' => $store->address ?? '',
            'storeLat' => $store->lat,
            'storeLng' => $store->lng,
        ]);
    }

    /**
     * Staff signup: binds a browser to an existing organization. No business
     * mode gating — see the class docblock.
     */
    public function resolveForStaff(Request $request): JsonResponse
    {
        $store = $this->lookup($request);

        return response()->json([
            'organizationSlug' => $store->organization->slug,
            'storeCode' => $store->code,
        ]);
    }

    private function lookup(Request $request): Store
    {
        $validated = $request->validate([
            'code' => ['required', 'string', 'max:64'],
        ]);

        $store = Store::query()
            ->with('organization')
            ->withPairingCode($validated['code'])
            ->first();

        // Deliberately the same message whether the code is unknown or the
        // store is archived: this endpoint is public and enumerable, so it
        // should not confirm which codes exist.
        abort_if($store === null || $store->status !== 'active', 404, "We couldn't find a store with that code.");

        abort_if($store->organization === null, 404, "We couldn't find a store with that code.");

        return $store;
    }
}
