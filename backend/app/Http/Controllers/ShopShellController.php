<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Api\ProductImageController;
use App\Http\Controllers\Api\StoreImageController;
use App\Models\Product;
use App\Models\Store;
use App\Services\ShopSubdomain;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

/**
 * The HTML a shop's own address returns, with that shop's name and photo in it.
 *
 * Every shop subdomain used to be answered by nginx from one static file
 * (`location = / { try_files /shop.html =404; }`), and that file says nothing
 * about any particular shop: its title is "Shop — Omaykan" and it carries no
 * `og:` tags at all. The page is only about a shop once Vue has run and asked
 * `GET /api/stores` which one this host is (apps/web/src/shop/main.ts).
 *
 * Nothing that runs in a browser can be seen by the things that make link
 * previews. facebookexternalhit, Twitterbot, Slackbot, Discordbot, LinkedInBot,
 * WhatsApp and Viber all fetch the HTML, read the tags in its head, and execute
 * nothing — so pasting a shop's link anywhere produced either no card or the
 * same blank one for every shop on the platform.
 *
 * This serves the same built shell with those tags stamped into it. The page a
 * visitor gets is unchanged: same bundle, same mount div, Vue still resolves
 * the shop itself and still corrects anything stale here.
 *
 * Two rules hold the whole thing together:
 *
 * 1. **The shell always goes out.** An unknown slug, a suspended shop, a shop
 *    with nothing on the shelf — all of them get the untouched file, exactly
 *    what nginx served before, and the page says its own piece. Only the tags
 *    are conditional. Nothing here can turn a working storefront into a
 *    redirect or a 404.
 * 2. **A card never promises what the page will not show.** The shop is looked
 *    up under the same conditions StoreDirectoryController::index applies,
 *    because that listing is what the page resolves itself against. A preview
 *    naming a shop that then fails to open is worse than no preview.
 *
 * Reached through one nginx line on the shops server block — see
 * documentation/deployment.md §6a.
 */
class ShopShellController extends Controller
{
    /**
     * The file the build produces for a shop's page, and the only place the
     * hashed bundle's name is written down.
     */
    private const SHELL = 'shop.html';

    /**
     * Shown when a shop has no photo of its own and nothing on its shelf
     * carries one. A market-stall photograph, and the reason it is this file
     * rather than the nicer `storefront/hero.webp`: **Facebook does not accept
     * WebP as an og:image**, and a card it refuses to draw is the same as no
     * card. JPEG, 2048×768, and every scraper takes it.
     */
    private const FALLBACK_IMAGE = '/storefront/market-stall-cover.jpg';

    private const FALLBACK_IMAGE_WIDTH = 2048;

    private const FALLBACK_IMAGE_HEIGHT = 768;

    public function __invoke(Request $request): Response
    {
        $slug = ShopSubdomain::slugFromHost($request->getHost());

        // Not a shop's address: the apex, `www`, or a deployment with no shop
        // domain configured at all. In production nginx never routes the main
        // site's `/` here, so this is a developer hitting the backend directly.
        if ($slug === '') {
            return response()->view('welcome');
        }

        $shell = $this->shell();
        $store = $this->store($slug);

        if ($store === null) {
            return $this->html($shell);
        }

        $html = Cache::remember(
            $this->cacheKey($slug, $store),
            now()->addHours(6),
            fn () => $this->stamp($shell, $store, $slug),
        );

        return $this->html($html);
    }

    /**
     * The shop this host is for, or null when there is nothing to advertise.
     *
     * The conditions are StoreDirectoryController's, in the same order and for
     * the same reasons: an active store, a mode that can fill a basket, an
     * organization allowed to trade in SQL and then again by verdict, and at
     * least one product it could actually sell. A shop that fails any of them
     * is absent from `GET /api/stores`, which is what shop/main.ts matches the
     * hostname against — so it is a shop whose page will say it cannot be
     * found, and it must not have a card.
     */
    private function store(string $slug): ?Store
    {
        $store = Store::query()
            ->with('organization.subscription')
            ->where('status', 'active')
            ->whereIn('business_mode', Store::ONLINE_MODES)
            ->whereHas('organization', fn ($query) => $query->where('slug', $slug)->tradable())
            // A slug belongs to an organization, which may hold several stores.
            // The directory lists them all and the page picks the first it
            // matches; oldest wins here so the card is stable across deploys
            // rather than following whatever the database returns first.
            ->orderBy('id')
            ->first();

        if ($store === null || ! ($store->organization?->accessVerdict()->allowsStorefront() ?? false)) {
            return null;
        }

        return $this->shelfCount($store) > 0 ? $store : null;
    }

    /** How many products this shop could sell online — the directory's test for a dead click. */
    private function shelfCount(Store $store): int
    {
        return $this->sellable($store)->count();
    }

    /**
     * @return Builder<Product>
     */
    private function sellable(Store $store)
    {
        return Product::query()
            ->where('organization_id', $store->organization_id)
            ->where('is_active', true)
            ->whereJsonContains('business_modes', $store->business_mode);
    }

    /**
     * Keyed so it expires by itself on all three things that change the card.
     *
     * The shop's own row — its name, address, photo. A deploy replacing the
     * shell with one that names a new bundle, without which a shop page would
     * keep serving the previous release's script tag until the entry aged out,
     * which is a blank page. And this file, because the tags are its opinion:
     * the first fix to what goes in a card was invisible behind a six-hour
     * entry that had been written by the code before it.
     */
    private function cacheKey(string $slug, Store $store): string
    {
        return 'shop-shell:'.$slug
            .':'.($store->updated_at?->getTimestamp() ?? 0)
            .':'.$this->shellStamp()
            .':'.(@filemtime(__FILE__) ?: 0);
    }

    private function stamp(string $shell, Store $store, string $slug): string
    {
        $root = ShopSubdomain::rootDomain();
        $url = 'https://'.$slug.'.'.$root.'/';
        $name = trim((string) $store->name);
        $title = $name.' — order on Omaykan';
        $description = $this->description($store);
        $image = $this->image($store, $root);

        $tags = [
            '<meta name="description" content="'.e($description).'" />',
            '<link rel="canonical" href="'.e($url).'" />',
            '<meta property="og:type" content="website" />',
            '<meta property="og:site_name" content="Omaykan" />',
            '<meta property="og:locale" content="en_PH" />',
            '<meta property="og:title" content="'.e($title).'" />',
            '<meta property="og:description" content="'.e($description).'" />',
            '<meta property="og:url" content="'.e($url).'" />',
            '<meta property="og:image" content="'.e($image['url']).'" />',
            // The same URL again. Old scrapers read only the secure one, and
            // everything we serve is https anyway.
            '<meta property="og:image:secure_url" content="'.e($image['url']).'" />',
            '<meta property="og:image:alt" content="'.e($name).'" />',
        ];

        if ($image['type'] !== null) {
            $tags[] = '<meta property="og:image:type" content="'.e($image['type']).'" />';
        }

        // Only when they are known, and never guessed: a square logo declared
        // as a wide banner is cropped through the middle, which looks worse
        // than the small card a scraper falls back to when it has to measure
        // the file itself.
        if ($image['width'] !== null && $image['height'] !== null) {
            $tags[] = '<meta property="og:image:width" content="'.$image['width'].'" />';
            $tags[] = '<meta property="og:image:height" content="'.$image['height'].'" />';
        }

        $tags[] = '<meta name="twitter:card" content="'.$this->twitterCard($image).'" />';
        $tags[] = '<meta name="twitter:title" content="'.e($title).'" />';
        $tags[] = '<meta name="twitter:description" content="'.e($description).'" />';
        $tags[] = '<meta name="twitter:image" content="'.e($image['url']).'" />';
        $tags[] = '<meta name="twitter:image:alt" content="'.e($name).'" />';

        return $this->inject($shell, $title, $tags);
    }

    /**
     * The sentence under the title.
     *
     * "Coffee shop in 12 Session Road, Baguio City. Order on Omaykan and pay
     * cash or GCash on delivery." Kept under about 160 characters, which is
     * where Google truncates; Facebook shows rather less than that on a phone,
     * so the shop and the place come first and the platform's line last.
     *
     * **Nothing here says whether the shop is open.** A paused shop still gets
     * its card — the directory lists it too — because Facebook keeps a scraped
     * page for days, and a card that had said "not taking orders" would go on
     * saying it long after the shop reopened.
     */
    private function description(Store $store): string
    {
        $what = trim((string) $store->business_type_label) ?: 'Local shop';
        $where = trim((string) $store->address);

        return Str::limit(
            $what.($where !== '' ? ' in '.$where : '').'. Order on Omaykan and pay cash or GCash on delivery.',
            157,
        );
    }

    /**
     * The picture, in the order a shopper would recognise it: the photo the
     * owner chose in Settings, then one off their own shelf, then the market
     * stall. Same order the directory card uses, so a link preview and the card
     * on the homepage show the same shop.
     *
     * Absolute, always — a relative og:image is ignored by most scrapers — and
     * pointed at the apex rather than this shop's own subdomain, so every
     * card's image comes off the one host every scraper has already met.
     *
     * @return array{url: string, width: ?int, height: ?int, type: ?string}
     */
    private function image(Store $store, string $root): array
    {
        $own = StoreImageController::urlFor($store);

        if ($own !== null) {
            return $this->measured('https://'.$root.$own, $store->image_path);
        }

        // min() over the shelf rather than "the first product": SQL aggregates
        // skip nulls and nullif() makes a blank column count as one, so a shop
        // with photos always yields the same one and a shop with none yields
        // null. Lifted from StoreDirectoryController::sellableShelves.
        $shelf = $this->sellable($store)
            ->selectRaw("min(nullif(image_url, '')) as shelf_photo")
            ->first()
            ?->getAttribute('shelf_photo');

        if (is_string($shelf) && str_starts_with($shelf, 'http')) {
            // Ours when it is one of our product files, and then the bytes are
            // on disk to be measured. Anything else is a URL we merely stored:
            // it is served, but not by us, and not measured.
            return $this->measured($shelf, ProductImageController::pathFor($shelf));
        }

        return [
            'url' => 'https://'.$root.self::FALLBACK_IMAGE,
            'width' => self::FALLBACK_IMAGE_WIDTH,
            'height' => self::FALLBACK_IMAGE_HEIGHT,
            'type' => 'image/jpeg',
        ];
    }

    /**
     * Read a stored image's real dimensions, so the tags can state them.
     *
     * The file sits on the private disk and is streamed by the controller that
     * owns it, so this is the only way to know its shape. One read per cache
     * miss, which is once per shop per change — and a failure here costs the
     * width and height hints, never the card.
     *
     * @return array{url: string, width: ?int, height: ?int, type: ?string}
     */
    private function measured(string $url, ?string $path): array
    {
        $size = null;

        try {
            if ($path !== null && $path !== '' && Storage::disk('local')->exists($path)) {
                $size = @getimagesizefromstring((string) Storage::disk('local')->get($path)) ?: null;
            }
        } catch (\Throwable $e) {
            Log::warning('Could not measure a shop preview image.', ['path' => $path, 'error' => $e->getMessage()]);
        }

        return [
            'url' => $url,
            'width' => $size !== null ? (int) $size[0] : null,
            'height' => $size !== null ? (int) $size[1] : null,
            'type' => is_string($size['mime'] ?? null) ? $size['mime'] : null,
        ];
    }

    /**
     * `summary_large_image` is the wide card, and Twitter drops it — showing
     * nothing at all — for an image under 300×157. Claim it only for a picture
     * measured wide enough to fill it; a square logo or an unmeasured URL gets
     * the small card, which is the one that always draws.
     *
     * @param  array{url: string, width: ?int, height: ?int, type: ?string}  $image
     */
    private function twitterCard(array $image): string
    {
        $wide = $image['width'] !== null
            && $image['height'] !== null
            && $image['width'] >= 300
            && $image['height'] >= 157
            && $image['width'] >= $image['height'] * 1.4;

        return $wide ? 'summary_large_image' : 'summary';
    }

    /**
     * Put the tags in the head, and replace the shell's own title.
     *
     * The `<title>` matters as much as the `og:` tags: Google reads it in
     * preference to og:title, and the shell's says "Shop — Omaykan" for every
     * shop on the platform.
     *
     * If the shell is ever built without a head to inject into, the file goes
     * out untouched rather than mangled — a shop page with a generic preview
     * is a working page, and that is the failure worth having.
     *
     * @param  array<int, string>  $tags
     */
    private function inject(string $shell, string $title, array $tags): string
    {
        if (! str_contains($shell, '</head>')) {
            Log::error('The shop shell has no </head>; served without preview tags.', ['shell' => self::SHELL]);

            return $shell;
        }

        $html = preg_replace(
            '#<title>.*?</title>#is',
            '<title>'.e($title).'</title>',
            $shell,
            1,
        ) ?? $shell;

        return str_replace(
            '</head>',
            '    '.implode("\n    ", $tags)."\n  </head>",
            $this->stripOwnKind($html),
        );
    }

    /**
     * Take out the shell's own copy of anything being written back in.
     *
     * The built `shop.html` carries a generic `<meta name="description">` —
     * "Order from a local shop on Omaykan…" — and the tags below go in at the
     * end of the head, after it. **A crawler that finds two reads the first**,
     * so leaving it would have meant every shop keeping the generic sentence
     * while a perfectly good one sat further down the same file. The same holds
     * for anything an entry later grows: og: and twitter: tags, and a canonical
     * link, are this controller's to state for a shop's page.
     *
     * Both attribute orders, and across newlines, because shop.html writes that
     * description over three lines.
     */
    private function stripOwnKind(string $html): string
    {
        $patterns = [
            '#<meta\b[^>]*\bname\s*=\s*(["\'])(?:description|twitter:[^"\']*)\1[^>]*>\s*#is',
            '#<meta\b[^>]*\bproperty\s*=\s*(["\'])og:[^"\']*\1[^>]*>\s*#is',
            '#<link\b[^>]*\brel\s*=\s*(["\'])canonical\1[^>]*>\s*#is',
        ];

        return preg_replace($patterns, '', $html) ?? $html;
    }

    private function shell(): string
    {
        $path = $this->shellPath();
        $shell = is_file($path) ? @file_get_contents($path) : false;

        // There is no useful stand-in for this file: it is where the hashed
        // bundle's name is written, so a page built without it cannot start
        // Vue at all. Better a 503 that says which file and which setting than
        // a blank white storefront. nginx keeps serving the static copy while
        // this is true — see the error_page fallback in deployment.md §6a.
        if ($shell === false) {
            abort(503, 'The shop page is unavailable: '.$path.' could not be read. Check WEB_ROOT.');
        }

        return $shell;
    }

    private function shellPath(): string
    {
        return rtrim((string) config('shops.web_root'), '/\\').'/'.self::SHELL;
    }

    /** Changes when a deploy replaces the shell. */
    private function shellStamp(): string
    {
        return (string) (@filemtime($this->shellPath()) ?: 0);
    }

    private function html(string $body): Response
    {
        return response($body, 200, [
            'Content-Type' => 'text/html; charset=UTF-8',
            // Five minutes, not a day: an owner who changes their shop's name
            // or photo should see a re-scrape pick it up while they are still
            // looking. Facebook's own cache is the slow one, and no header
            // here shortens it.
            'Cache-Control' => 'public, max-age=300',
        ]);
    }
}
