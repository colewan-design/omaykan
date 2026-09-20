<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Services\ImageRejected;
use App\Services\ImageStore;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\StreamedResponse;

/**
 * Product photographs, as files rather than as base64 inside product rows.
 *
 * Until this existed, the till synced a photographed product's picture as a
 * data URL straight into `products.image_url` — widened to `text` on
 * 2026-09-13 so it would fit — and every catalog and bootstrap response
 * carried tens of kilobytes of base64 per photographed product. Two ways in
 * now, both ending in a file and a URL:
 *
 * - `store()`, for a client that is online when the photo is picked — always
 *   the seller app. Upload first, then put the URL in the product event.
 * - SyncController pulls any data URL still arriving inside a product event
 *   out through the same ImageStore, so an offline till needs no change.
 *
 * See documentation/merchant-features.md §4.
 */
class ProductImageController extends Controller
{
    use ActsForAStore;

    public const DIRECTORY = 'product-images';

    /** `{uuid}.{ext}` and nothing else — the route never takes a path. */
    public const FILE_PATTERN = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)';

    public function __construct(private readonly ImageStore $images)
    {
    }

    /**
     * Keep one photo and say where it lives.
     *
     * Whoever may change products may upload their photos; the till's own
     * Products page permission, not a rank. See RolePermissions.
     */
    public function store(Request $request): JsonResponse
    {
        $context = $this->writableStoreContext($request);
        abort_unless($context->can('products'), 403, 'Your role cannot change products.');

        $validated = $request->validate([
            'image' => ['required', 'string'],
        ]);

        try {
            $path = $this->images->store($validated['image'], self::DIRECTORY);
        } catch (ImageRejected $e) {
            abort(422, $e->getMessage());
        }

        return response()->json(['url' => self::urlFor($path)], 201);
    }

    /**
     * Stream one to anyone. Public on purpose: it is a picture the shop put on
     * its own storefront. The file name is matched against FILE_PATTERN by the
     * route, so nothing here can be walked out of its directory.
     */
    public function show(string $file): StreamedResponse
    {
        $disk = Storage::disk('local');
        $path = self::DIRECTORY.'/'.$file;

        abort_unless($disk->exists($path), 404, 'No such photo.');

        // A year and immutable: a photo's name is a fresh UUID each time, so a
        // file never changes under a URL that has been handed out.
        return $disk->response($path, null, [
            'Cache-Control' => 'public, max-age=31536000, immutable',
        ]);
    }

    /**
     * The absolute URL a stored photo is served from.
     *
     * Absolute, unlike the shop photo's: product rows travel to the till
     * (which may be running in a native shell with no web origin of its own),
     * the seller app and the customer app, and a relative `/api/...` would
     * resolve against the wrong host in all three. `url()` uses the host the
     * request came in on, and APP_URL from the console.
     */
    public static function urlFor(string $path): string
    {
        return url('/api/product-images/'.basename($path));
    }

    /** The stored path behind a URL this controller handed out, or null. */
    public static function pathFor(?string $url): ?string
    {
        if (! is_string($url) || preg_match('#/api/product-images/('.self::FILE_PATTERN.')$#', $url, $m) !== 1) {
            return null;
        }

        return self::DIRECTORY.'/'.$m[1];
    }
}
