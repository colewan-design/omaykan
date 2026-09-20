<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\Store;
use App\Services\ImageRejected;
use App\Services\ImageStore;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\StreamedResponse;

/**
 * A shop's own photo: the one its owner uploads in Settings > Business image.
 *
 * Two halves that belong together. The register pushes the image here when the
 * owner changes it (packages/data saveSettings), and anywhere a customer meets
 * the shop — the directory, the partner carousel on /seller/signup — reads it back
 * out through show().
 *
 * The register holds that image as a data URL, so that is what arrives: this
 * is not a multipart upload endpoint, and the string is treated as hostile
 * until the decoded bytes have been shown to be an image of a type we accept.
 *
 * Bytes land on the private disk rather than a public one, and are streamed
 * back by this controller. There is no directory anyone can browse, and no
 * URL that outlives a store record.
 */
class StoreImageController extends Controller
{
    use ActsForAStore;

    public function __construct(private readonly ImageStore $images)
    {
    }

    /** Alongside `rider-documents/` on the same private disk. */
    private const DIRECTORY = 'store-images';

    /**
     * Set or clear the calling till's store photo.
     *
     * A null image is the owner pressing Remove, and has to be a real case
     * rather than a no-op: taking the picture down is as much a change as
     * putting it up.
     */
    public function update(Request $request): JsonResponse
    {
        $store = $this->storeFromRequest($request);

        $validated = $request->validate([
            // `present` and not `required`: null is the clear, and `required`
            // would reject exactly the payload that means "take it down".
            'image' => ['present', 'nullable', 'string'],
        ]);

        $image = $validated['image'] ?? null;

        if ($image === null || trim($image) === '') {
            $this->forget($store);

            return response()->json(['imageUrl' => null]);
        }

        try {
            $path = $this->images->store($image, self::DIRECTORY);
        } catch (ImageRejected $e) {
            abort(422, $e->getMessage());
        }

        $previous = $store->image_path;
        $store->forceFill(['image_path' => $path])->save();

        // Only after the new one is safely written and recorded — the reverse
        // order leaves a store pointing at a file that has been deleted.
        if ($previous !== null && $previous !== $path) {
            $this->images->forget($previous);
        }

        return response()->json(['imageUrl' => self::urlFor($store)]);
    }

    /**
     * Stream a shop's photo to anyone.
     *
     * Public on purpose: this is the picture the shop chose to put on its own
     * storefront. The path is never taken from the request — only the store
     * is, and the path comes off that record.
     */
    public function show(Store $store): StreamedResponse
    {
        $disk = Storage::disk('local');
        $path = $store->image_path;

        abort_if($path === null || $path === '' || ! $disk->exists($path), 404, 'That shop has no photo.');

        // A day, not a year: the URL the directory hands out carries a version
        // stamp that changes when the photo does, but the bare URL does not,
        // and an owner who replaces their photo should not be arguing with
        // somebody's browser cache a month later.
        return $disk->response($path, null, [
            'Cache-Control' => 'public, max-age=86400',
        ]);
    }

    /**
     * Where a shop's photo is served from, versioned so that replacing the
     * photo is enough to get browsers to fetch it again.
     *
     * Null for a shop that has not uploaded one — the caller decides what to
     * show instead.
     */
    public static function urlFor(Store $store): ?string
    {
        if ($store->image_path === null || $store->image_path === '') {
            return null;
        }

        return '/api/stores/'.$store->id.'/image?v='.substr(sha1($store->image_path), 0, 8);
    }

    private function forget(Store $store): void
    {
        $previous = $store->image_path;
        if ($previous === null) {
            return;
        }

        $store->forceFill(['image_path' => null])->save();
        $this->images->forget($previous);
    }

    /**
     * The store this session is signed in to — the same scoping rule the sync
     * and seller-order endpoints follow.
     *
     * The shop's photo is what customers see in the directory, so changing it
     * is a manager's call rather than something any signed-in cashier can do.
     */
    private function storeFromRequest(Request $request): Store
    {
        $context = $this->writableStoreContext($request);

        abort_unless($context->isManager(), 403, 'Only an admin or manager can change the shop photo.');

        return $context->store;
    }
}
