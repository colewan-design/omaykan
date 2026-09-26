<?php

namespace App\Services\Discovery;

use App\Models\Product;
use App\Models\Store;
use App\Services\ShopSubdomain;
use Illuminate\Support\Collection;

/**
 * Which town each listed shop is in, and which categories it belongs to.
 *
 * Nothing here is stored. A shop's town comes from the address it typed (or
 * failing that its pin), its categories from its business type, its till mode
 * and what is on its shelf — all things it has already told us — so a new
 * shop appears on /baguio/baked-goods the moment it stocks some bread, and a
 * wrong placement is fixed by the shop fixing its own details, not by anyone
 * editing a mapping. The rules are in config/discovery.php.
 *
 * One instance per request: it classifies every shop the first time it is
 * asked and answers the rest of the page from that.
 */
class Discovery
{
    /** @var Collection<int, array{store: Store, row: array<string, mixed>, locality: ?string, categories: array<int, string>}>|null */
    private ?Collection $placed = null;

    public function __construct(private readonly ShopDirectory $directory) {}

    /** @return array<string, array<string, mixed>> */
    public function localities(): array
    {
        return config('discovery.localities', []);
    }

    /** @return array<string, array<string, mixed>> */
    public function categories(): array
    {
        return config('discovery.categories', []);
    }

    /** @return array<string, mixed>|null */
    public function locality(string $slug): ?array
    {
        return $this->localities()[$slug] ?? null;
    }

    /** @return array<string, mixed>|null */
    public function category(string $slug): ?array
    {
        return $this->categories()[$slug] ?? null;
    }

    /**
     * The directory's cards for one town, optionally one category of it, in
     * the directory's own order.
     *
     * @return array<int, array<string, mixed>>
     */
    public function shops(string $locality, ?string $category = null): array
    {
        return $this->placed()
            ->filter(fn (array $shop) => $shop['locality'] === $locality
                && ($category === null || in_array($category, $shop['categories'], true)))
            ->map(fn (array $shop) => $shop['row'] + ['url' => $this->shopUrl((string) $shop['row']['orgSlug'])])
            ->values()
            ->all();
    }

    /**
     * How many shops each page would list: [locality => [category => n]].
     * Zeros included, so a caller can tell an empty page from an unknown one.
     *
     * @return array<string, array<string, int>>
     */
    public function counts(): array
    {
        $counts = [];
        foreach (array_keys($this->localities()) as $locality) {
            foreach (array_keys($this->categories()) as $category) {
                $counts[$locality][$category] = 0;
            }
        }

        foreach ($this->placed() as $shop) {
            if ($shop['locality'] === null) {
                continue;
            }
            foreach ($shop['categories'] as $category) {
                $counts[$shop['locality']][$category]++;
            }
        }

        return $counts;
    }

    /**
     * The shop's own page: its subdomain when this deployment has a shop domain
     * and the slug can be a host, the same rule as shopUrl in
     * apps/web/scripts/prerender.mjs. Otherwise a path on whatever site the
     * visitor is on, so a dev server's links stay on the dev server; the views
     * make it absolute where a URL has to be.
     */
    public function shopUrl(string $orgSlug): string
    {
        $root = ShopSubdomain::rootDomain();

        if ($root === '' || ! ShopSubdomain::isLabel($orgSlug)) {
            return '/shop/'.rawurlencode($orgSlug);
        }

        $scheme = parse_url((string) config('discovery.site_url'), PHP_URL_SCHEME) ?: 'https';

        return "{$scheme}://{$orgSlug}.{$root}";
    }

    /**
     * Whether `$text` contains one of `$words` as a whole word (an optional
     * plural s/es allowed), or as a word prefix for a word ending in *. See
     * config/discovery.php for why substrings will not do.
     *
     * @param  array<int, string>  $words
     */
    public static function mentions(string $text, array $words): bool
    {
        if ($text === '' || $words === []) {
            return false;
        }

        foreach ($words as $word) {
            $prefix = str_ends_with($word, '*');
            $stem = preg_quote(mb_strtolower(rtrim($word, '*')), '/');
            $pattern = $prefix
                ? '/(?<![\p{L}\p{N}])'.$stem.'/u'
                : '/(?<![\p{L}\p{N}])'.$stem.'(?:s|es)?(?![\p{L}\p{N}])/u';

            if (preg_match($pattern, mb_strtolower($text)) === 1) {
                return true;
            }
        }

        return false;
    }

    /**
     * The town an address names, the last one if it names several.
     */
    public function localityFromAddress(string $address): ?string
    {
        $text = mb_strtolower($address);
        $found = null;
        $at = -1;

        foreach ($this->localities() as $slug => $locality) {
            foreach ($locality['aliases'] ?? [] as $alias) {
                $pattern = '/(?<![\p{L}\p{N}])'.preg_quote(mb_strtolower($alias), '/').'(?![\p{L}\p{N}])/u';
                if (preg_match_all($pattern, $text, $matches, PREG_OFFSET_CAPTURE) > 0) {
                    $last = end($matches[0])[1];
                    if ($last > $at) {
                        $at = $last;
                        $found = $slug;
                    }
                }
            }
        }

        return $found;
    }

    /** The nearest town whose radius the pin falls inside. */
    public function localityFromPin(?float $lat, ?float $lng): ?string
    {
        if ($lat === null || $lng === null) {
            return null;
        }

        $found = null;
        $nearest = INF;

        foreach ($this->localities() as $slug => $locality) {
            [$centerLat, $centerLng] = $locality['center'];
            $km = ShopDirectory::kmBetween($lat, $lng, (float) $centerLat, (float) $centerLng);
            if ($km <= (float) $locality['radius_km'] && $km < $nearest) {
                $nearest = $km;
                $found = $slug;
            }
        }

        return $found;
    }

    /**
     * @return Collection<int, array{store: Store, row: array<string, mixed>, locality: ?string, categories: array<int, string>}>
     */
    private function placed(): Collection
    {
        if ($this->placed !== null) {
            return $this->placed;
        }

        $listings = $this->directory->listings();
        $shelfMatches = $this->shelfMatches($listings->pluck('store'));

        return $this->placed = $listings->map(function (array $listing) use ($shelfMatches) {
            /** @var Store $store */
            $store = $listing['store'];
            $label = (string) ($store->business_type_label ?? '');
            $onShelf = $shelfMatches[$store->business_mode][$store->organization_id] ?? [];

            $categories = [];
            foreach ($this->categories() as $slug => $rule) {
                $minimum = (int) ($rule['min_products'] ?? 0);
                if (in_array($store->business_mode, $rule['modes'] ?? [], true)
                    || self::mentions($label, $rule['labels'] ?? [])
                    || ($minimum > 0 && ($onShelf[$slug] ?? 0) >= $minimum)) {
                    $categories[] = $slug;
                }
            }

            return $listing + [
                'locality' => $this->localityFromAddress((string) ($store->address ?? ''))
                    ?? $this->localityFromPin($store->lat, $store->lng),
                'categories' => $categories,
            ];
        });
    }

    /**
     * For each shop, how many of its sellable products satisfy each category's
     * shelf rule: [mode => [organization id => [category => n]]].
     *
     * One query per business mode, pre-filtered in SQL on the bare words so
     * only candidate products come back, then matched properly in PHP — the
     * whole-word rule is a regular expression, which SQLite (the test lane)
     * does not have.
     *
     * @param  Collection<int, Store>  $stores
     * @return array<string, array<string, array<string, int>>>
     */
    private function shelfMatches(Collection $stores): array
    {
        $rules = array_filter(
            $this->categories(),
            fn (array $rule) => (int) ($rule['min_products'] ?? 0) > 0
                && (($rule['aisles'] ?? []) !== [] || ($rule['products'] ?? []) !== []),
        );
        if ($rules === [] || $stores->isEmpty()) {
            return [];
        }

        $stems = collect($rules)
            ->flatMap(fn (array $rule) => [...($rule['aisles'] ?? []), ...($rule['products'] ?? [])])
            ->map(fn (string $word) => mb_strtolower(rtrim($word, '*')))
            ->unique()
            ->values();

        $matches = [];
        foreach ($stores->groupBy('business_mode') as $mode => $group) {
            $candidates = Product::query()
                ->leftJoin('categories', 'categories.id', '=', 'products.category_id')
                ->whereIn('products.organization_id', $group->pluck('organization_id')->unique())
                ->where('products.is_active', true)
                ->whereJsonContains('products.business_modes', $mode)
                ->where(function ($query) use ($stems) {
                    foreach ($stems as $stem) {
                        // Escaped as in ShopDirectory::query, with "!" for the
                        // same PDO reason.
                        $like = '%'.str_replace(['!', '%', '_'], ['!!', '!%', '!_'], $stem).'%';
                        $query->orWhereRaw("lower(products.name) like ? escape '!'", [$like])
                            ->orWhereRaw("lower(coalesce(categories.name, '')) like ? escape '!'", [$like]);
                    }
                })
                ->get(['products.organization_id', 'products.name as product_name', 'categories.name as aisle_name']);

            foreach ($candidates as $product) {
                foreach ($rules as $slug => $rule) {
                    if (self::mentions((string) $product->aisle_name, $rule['aisles'] ?? [])
                        || self::mentions((string) $product->product_name, $rule['products'] ?? [])) {
                        $matches[$mode][$product->organization_id][$slug] =
                            ($matches[$mode][$product->organization_id][$slug] ?? 0) + 1;
                    }
                }
            }
        }

        return $matches;
    }
}
