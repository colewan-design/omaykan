<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\Discovery\ShopDirectory;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * The public list of shops a customer can order from.
 *
 * The storefront used to be reachable only by typing the code a shop handed
 * out, which is a fine flow for a shopper holding a receipt or looking at a
 * tarpaulin and nothing at all for a first-time visitor — the landing page was
 * pinned to a single tenant at build time because of it. This is the other
 * half, "which shops are there?", and since the codes were retired it is the
 * whole of how a customer finds a shop.
 *
 * Deliberately not enumerable in the way store codes are: this returns only
 * what a shop already publishes to its own customers (name, address, pin), and
 * and no credential of any kind, so there is nothing here worth harvesting.
 *
 * The list itself is ShopDirectory's, shared with the town and category
 * landing pages.
 */
class StoreDirectoryController extends Controller
{
    public function index(Request $request, ShopDirectory $directory): JsonResponse
    {
        $validated = $request->validate([
            'q' => ['sometimes', 'nullable', 'string', 'max:120'],
            // Both or neither: a lone latitude cannot place anyone, and
            // silently ignoring it would sort by name while looking like it
            // sorted by distance.
            //
            // No 'sometimes' on these two, unlike `q`. It skips every rule on
            // an absent field — including required_with, which is exactly the
            // case being guarded against — so half a coordinate would pass.
            'lat' => ['nullable', 'numeric', 'between:-90,90', 'required_with:lng'],
            'lng' => ['nullable', 'numeric', 'between:-180,180', 'required_with:lat'],
        ]);

        $listings = $directory->listings(
            trim((string) ($validated['q'] ?? '')),
            isset($validated['lat']) ? (float) $validated['lat'] : null,
            isset($validated['lng']) ? (float) $validated['lng'] : null,
        );

        return response()->json(['stores' => $listings->pluck('row')->all()]);
    }
}
