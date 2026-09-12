<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Rider;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\StreamedResponse;

/**
 * The rider's own photograph.
 *
 * Separate from RiderAccountController because this one speaks multipart and
 * streams bytes, and because the rules are different: everything on the
 * account controller is a string a rider may change freely, while this is a
 * file that a stranger will be shown.
 *
 * ## Not behind `rider.approved`
 *
 * Mounted beside `/me` and `/password`, outside the approval gate, for the
 * reason those are: a pending rider filling in their profile while they wait
 * is the normal case, and there is nothing about an unreviewed account that
 * makes a photograph dangerous — it is not shown to anybody until that rider
 * is carrying an order, which the gate already prevents.
 */
class RiderAvatarController extends Controller
{
    /** Where avatars live on the private disk, away from the documents. */
    private const AVATAR_DIRECTORY = 'rider-avatars';

    /** 8MB, matching the licence upload: one phone photo, not a payload. */
    private const MAX_IMAGE_KB = 8192;

    /**
     * Replace the rider's photo.
     *
     * The old file is deleted only after the new path is committed, so a
     * failure anywhere in between leaves the rider with the photo they had
     * rather than none.
     */
    public function update(Request $request): JsonResponse
    {
        /** @var Rider $rider */
        $rider = $request->user();

        $data = $request->validate([
            // `image` rather than `mimes` alone: it decodes the file, so a
            // renamed script does not become an avatar. Same rule as the
            // licence upload — see RiderAuthController.
            'photo' => ['required', 'image', 'mimes:jpeg,jpg,png,webp', 'max:'.self::MAX_IMAGE_KB],
        ]);

        $previous = $rider->avatar_path;
        $path = $this->storeAvatar($data['photo']);

        try {
            $rider->forceFill(['avatar_path' => $path])->save();
        } catch (\Throwable $e) {
            Storage::disk('local')->delete($path);

            throw $e;
        }

        if ($previous !== null && $previous !== '' && $previous !== $path) {
            Storage::disk('local')->delete($previous);
        }

        return response()->json(['rider' => $rider->fresh()->toPortalArray()]);
    }

    /**
     * Take the photo down.
     *
     * The row is cleared before the file is deleted: if the delete fails, the
     * photo has still stopped resolving everywhere it was shown, which is what
     * the rider asked for. An orphaned file is a housekeeping problem; a face
     * that stays up after somebody removed it is not.
     */
    public function destroy(Request $request): JsonResponse
    {
        /** @var Rider $rider */
        $rider = $request->user();

        $previous = $rider->avatar_path;

        if ($previous !== null && $previous !== '') {
            $rider->forceFill(['avatar_path' => null])->save();
            Storage::disk('local')->delete($previous);
        }

        return response()->json(['rider' => $rider->fresh()->toPortalArray()]);
    }

    /**
     * Stream a rider's photo.
     *
     * Reachable without a token, exactly like a shop's photo, and for the same
     * reason it is safe: the path never comes from the request — only the
     * rider's UUID does, and the URL carrying it is only ever handed to the two
     * parties on an order that rider is carrying.
     *
     * A rider with no photo is a 404 and not a placeholder image, so the client
     * keeps ownership of what to draw instead. Every client already has an
     * initial-letter avatar for the rider who has not uploaded one.
     */
    public function show(Rider $rider): StreamedResponse
    {
        $disk = Storage::disk('local');
        $path = $rider->avatar_path;

        abort_if($path === null || $path === '' || ! $disk->exists($path), 404, 'That rider has no photo.');

        // A day, with the version stamp in urlFor() doing the invalidation —
        // the same bargain StoreImageController strikes, so a rider who
        // replaces their photo is not arguing with a cache for a month.
        return $disk->response($path, null, [
            'Cache-Control' => 'public, max-age=86400',
        ]);
    }

    /**
     * Where a rider's photo is served from, versioned so replacing the file is
     * enough to make clients fetch it again.
     *
     * Null for a rider who has not uploaded one — every caller treats that as
     * "draw the initial instead", and none of them should be handed a URL that
     * 404s.
     */
    public static function urlFor(Rider $rider): ?string
    {
        if ($rider->avatar_path === null || $rider->avatar_path === '') {
            return null;
        }

        return '/api/riders/'.$rider->id.'/avatar?v='.substr(sha1($rider->avatar_path), 0, 8);
    }

    private function storeAvatar(UploadedFile $file): string
    {
        return $file->store(self::AVATAR_DIRECTORY, 'local');
    }
}
