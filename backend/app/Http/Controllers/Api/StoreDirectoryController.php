<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Product;
use App\Models\Store;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;

/**
 * The public list of shops a customer can order from.
 *
 * Until now the storefront could only be reached by knowing a store code:
 * StoreCodeController resolves one code to one store, which is the flow for a
 * shopper holding a receipt or a tarpaulin. It gives a first-time visitor
 * nothing to browse, so the landing page was pinned to a single tenant at
 * build time. This is the other half — "which shops are there?" — and it is
 * what lets the landing page be a market rather than one stall.
 *
 * Deliberately not enumerable in the way store codes are: this returns only
 * what a shop already publishes to its own customers (name, address, pin), and
 * never the pairing code, so there is nothing here worth harvesting.
 */
class StoreDirectoryController extends Controller
{
    /** Sorting is only meaningful within a sane radius; beyond it, order by name. */
    private const MAX_DISTANCE_KM = 60.0;

    private const EARTH_RADIUS_KM = 6371.0;

    public function index(Request $request): JsonResponse
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

        $term = trim((string) ($validated['q'] ?? ''));
        $lat = isset($validated['lat']) ? (float) $validated['lat'] : null;
        $lng = isset($validated['lng']) ? (float) $validated['lng'] : null;

        $stores = $this->query($term)->get();
        $shelves = $this->sellableShelves($stores);

        $rows = $stores
            ->map(function (Store $store) use ($shelves, $lat, $lng): ?array {
                $shelf = $shelves[$store->business_mode][$store->organization_id] ?? null;
                $count = $shelf['count'] ?? 0;

                // A shop with an empty shelf is a dead click. It is not
                // suspended or broken — it has simply not stocked anything
                // yet — so it is left out of the directory rather than
                // listed as unavailable.
                if ($count === 0) {
                    return null;
                }

                return [
                    'orgSlug' => $store->organization?->slug,
                    'storeCode' => $store->code,
                    'name' => $store->name,
                    'businessMode' => $store->business_mode,
                    'businessTypeLabel' => $store->business_type_label,
                    'address' => $store->address ?? '',
                    'imageUrl' => $shelf['imageUrl'] ?? null,
                    'lat' => $store->lat === null ? null : (float) $store->lat,
                    'lng' => $store->lng === null ? null : (float) $store->lng,
                    'productCount' => $count,
                    'distanceKm' => $this->distanceKm($store, $lat, $lng),
                ];
            })
            ->filter()
            ->values();

        return response()->json(['stores' => $this->sort($rows, $lat !== null)]);
    }

    /**
     * @return \Illuminate\Database\Eloquent\Builder<Store>
     */
    private function query(string $term)
    {
        return Store::query()
            ->with('organization')
            ->where('status', 'active')
            // Only modes that can put something in a cart — the same gate
            // StoreCodeController applies when resolving a code, so a salon
            // does not appear in a list of places to order from.
            ->whereIn('business_mode', StoreCodeController::ONLINE_MODES)
            ->whereHas('organization', function ($query) {
                $query->where('status', 'active')
                    ->where(fn ($inner) => $inner->where('suspended', false)->orWhereNull('suspended'));
            })
            ->when($term !== '', function ($query) use ($term) {
                // lower() + LIKE rather than ILIKE: the test suite runs on
                // SQLite as well as PostgreSQL, and ILIKE exists only on one
                // of them. The wildcards in the term itself are escaped so a
                // search for "100%" is not a search for everything.
                //
                // "!" as the escape character, not the conventional backslash.
                // The ESCAPE clause itself is required — PostgreSQL defaults to
                // a backslash, SQLite has no default at all — but spelling it
                // ESCAPE '\' breaks PDO's pgsql driver, which reads the \'
                // as an escaped quote, decides the string never ends, and
                // miscounts the placeholders into "parameter was not defined".
                $like = '%'.mb_strtolower(str_replace(['!', '%', '_'], ['!!', '!%', '!_'], $term)).'%';

                $query->where(function ($inner) use ($like) {
                    $inner->whereRaw("lower(stores.name) like ? escape '!'", [$like])
                        ->orWhereRaw("lower(coalesce(stores.address, '')) like ? escape '!'", [$like])
                        ->orWhereRaw("lower(coalesce(stores.business_type_label, '')) like ? escape '!'", [$like])
                        ->orWhereHas('organization', fn ($org) => $org->whereRaw("lower(organizations.name) like ? escape '!'", [$like]));
                });
            })
            ->orderBy('name');
    }

    /**
     * What each shop has on the shelf — how many products it could actually
     * sell, and one photo off that shelf — keyed by business mode and then
     * organization.
     *
     * Grouped by mode so this stays a handful of queries however many shops
     * there are: products are organization-scoped, but which of them a shop
     * may sell depends on that shop's mode.
     *
     * The photo is a product's, because a store has no picture of its own in
     * the schema — there is nowhere for an owner to upload a shopfront or a
     * logo. A shelf photo is the honest stand-in: it is the shop's own
     * merchandise, it is already there for every shop that has listed
     * anything, and the day stores carry their own image this becomes the
     * fallback rather than the source.
     *
     * @param  Collection<int, Store>  $stores
     * @return array<string, array<string, array{count: int, imageUrl: ?string}>>
     */
    private function sellableShelves(Collection $stores): array
    {
        $shelves = [];

        foreach ($stores->groupBy('business_mode') as $mode => $group) {
            $shelves[$mode] = Product::query()
                ->whereIn('organization_id', $group->pluck('organization_id')->unique())
                ->where('is_active', true)
                ->whereJsonContains('business_modes', $mode)
                ->groupBy('organization_id')
                // min() over the photos rather than a second query: SQL
                // aggregates skip nulls, and nullif() makes a blank column
                // count as one too, so a shop that lists photos always gets
                // the same one back and a shop that lists none gets null.
                ->selectRaw("organization_id, count(*) as aggregate, min(nullif(image_url, '')) as shelf_photo")
                ->get()
                ->keyBy('organization_id')
                ->map(fn ($row) => [
                    'count' => (int) $row->aggregate,
                    'imageUrl' => $row->shelf_photo,
                ])
                ->all();
        }

        return $shelves;
    }

    /**
     * Great-circle distance, in PHP rather than SQL.
     *
     * SQLite ships no radians()/asin(), and the test suite runs there, so doing
     * this in the query would make the directory untestable on the fast lane
     * for the sake of arithmetic over a list this short.
     */
    private function distanceKm(Store $store, ?float $lat, ?float $lng): ?float
    {
        if ($lat === null || $lng === null || $store->lat === null || $store->lng === null) {
            return null;
        }

        $fromLat = deg2rad((float) $store->lat);
        $toLat = deg2rad($lat);
        $deltaLat = $toLat - $fromLat;
        $deltaLng = deg2rad($lng - (float) $store->lng);

        $a = sin($deltaLat / 2) ** 2 + cos($fromLat) * cos($toLat) * sin($deltaLng / 2) ** 2;

        return round(self::EARTH_RADIUS_KM * 2 * asin(min(1.0, sqrt($a))), 2);
    }

    /**
     * Nearest first when the customer has placed themselves, alphabetical
     * otherwise. Shops with no pin, or too far to be a delivery, keep their
     * alphabetical order behind the ones that can be measured — they are still
     * listed, because a shop without coordinates is not a shop that is closed.
     *
     * @param  Collection<int, array<string, mixed>>  $rows
     * @return array<int, array<string, mixed>>
     */
    private function sort(Collection $rows, bool $hasLocation): array
    {
        if (! $hasLocation) {
            return $rows->values()->all();
        }

        return $rows
            ->sortBy(function (array $row): array {
                $distance = $row['distanceKm'];
                $measurable = $distance !== null && $distance <= self::MAX_DISTANCE_KM;

                return [$measurable ? 0 : 1, $measurable ? $distance : 0, $row['name']];
            })
            ->values()
            ->all();
    }
}
