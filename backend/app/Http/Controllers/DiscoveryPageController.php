<?php

namespace App\Http\Controllers;

use App\Services\Discovery\Discovery;
use Illuminate\Http\Response;

/**
 * Town and category landing pages — /baguio, /baguio/restaurants — and
 * /sitemap-towns.xml, which lists them.
 *
 * A sitemap of their own because /sitemap.xml is the build's: a static file
 * apps/web/scripts/prerender.mjs writes with the storefront's pages and every
 * shop. These pages change as shops join, so they are listed from here, and
 * robots.txt names both.
 *
 * Rendered here, as HTML, rather than by the Vue build. These pages exist to
 * be found by search as much as to be browsed, and a crawler that reads the
 * served HTML should find the shops in it, not an empty <div> waiting on
 * /api/stores. Everything they show comes from Discovery, and so from the same
 * list the directory API serves.
 *
 * A page with no shops yet still answers, so a link to it is never a 404 once
 * the town and category exist; it is marked noindex and left out of the
 * sitemap until there is something on it.
 */
class DiscoveryPageController extends Controller
{
    /** How long a browser or proxy may keep a page; a new shop shows up within this. */
    private const MAX_AGE_SECONDS = 300;

    public function __construct(private readonly Discovery $discovery) {}

    public function town(string $locality): Response
    {
        $town = $this->discovery->locality($locality) ?? abort(404);
        $counts = $this->discovery->counts()[$locality];
        $shops = $this->discovery->shops($locality);

        return $this->page('discovery.town', [
            'town' => $town + ['slug' => $locality],
            'categories' => $this->categoriesWithCounts($counts),
            'shops' => $shops,
            'otherTowns' => $this->otherTowns($locality, null),
            'indexable' => $shops !== [],
        ]);
    }

    public function category(string $locality, string $category): Response
    {
        $town = $this->discovery->locality($locality) ?? abort(404);
        $rule = $this->discovery->category($category) ?? abort(404);
        $counts = $this->discovery->counts()[$locality];
        $shops = $this->discovery->shops($locality, $category);

        return $this->page('discovery.category', [
            'town' => $town + ['slug' => $locality],
            'category' => $rule + ['slug' => $category],
            'shops' => $shops,
            'siblings' => array_filter(
                $this->categoriesWithCounts($counts),
                fn (array $sibling) => $sibling['slug'] !== $category && $sibling['count'] > 0,
            ),
            'otherTowns' => $this->otherTowns($locality, $category),
            'indexable' => $shops !== [],
        ]);
    }

    /**
     * Every town and every town-and-category with a shop on it. Pages with
     * none are left out: a sitemap full of "no shops yet" is a sitemap a
     * search engine learns to distrust. The home page is /sitemap.xml's.
     */
    public function sitemap(): Response
    {
        $site = (string) config('discovery.site_url');
        $urls = [];

        foreach ($this->discovery->counts() as $locality => $counts) {
            if (array_sum($counts) === 0) {
                continue;
            }
            $urls[] = "{$site}/{$locality}";
            foreach ($counts as $category => $count) {
                if ($count > 0) {
                    $urls[] = "{$site}/{$locality}/{$category}";
                }
            }
        }

        return response()
            ->view('discovery.sitemap', ['site' => $site, 'urls' => $urls])
            ->header('Content-Type', 'application/xml; charset=UTF-8')
            ->header('Cache-Control', 'public, max-age='.self::MAX_AGE_SECONDS);
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function page(string $view, array $data): Response
    {
        return response()
            ->view($view, $data + ['site' => (string) config('discovery.site_url')])
            ->header('Cache-Control', 'public, max-age='.self::MAX_AGE_SECONDS);
    }

    /**
     * @param  array<string, int>  $counts
     * @return array<int, array<string, mixed>>
     */
    private function categoriesWithCounts(array $counts): array
    {
        $categories = [];
        foreach ($this->discovery->categories() as $slug => $rule) {
            $categories[] = $rule + ['slug' => $slug, 'count' => $counts[$slug] ?? 0];
        }

        return $categories;
    }

    /**
     * The same page in the other towns, where it has shops — "baked goods in
     * La Trinidad" from "baked goods in Baguio".
     *
     * @return array<int, array{slug: string, name: string, count: int}>
     */
    private function otherTowns(string $locality, ?string $category): array
    {
        $towns = [];
        foreach ($this->discovery->counts() as $slug => $counts) {
            $count = $category === null ? count($this->discovery->shops($slug)) : $counts[$category];
            if ($slug !== $locality && $count > 0) {
                $towns[] = ['slug' => $slug, 'name' => $this->discovery->locality($slug)['name'], 'count' => $count];
            }
        }

        return $towns;
    }
}
