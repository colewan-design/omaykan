<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Rider;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;
use Illuminate\Validation\Rule;
use Symfony\Component\HttpFoundation\StreamedResponse;

/**
 * The operator's side of rider registration: reading the queue, looking at the
 * documents, and deciding.
 *
 * Gated by the same shared secret as PlatformAdminController rather than a
 * session, because it is the same operator tool — but kept in its own
 * controller: that one is entirely about organizations, and its `handle`
 * action switch is already the longest thing in it.
 *
 * The document endpoint is the reason this file exists at all. A driver's
 * licence photo cannot live on the public disk and cannot be handed to a shop
 * or a customer; it is streamed from private storage, to a caller holding the
 * operator secret, and nowhere else.
 */
class RiderReviewController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $this->requireSecret($request);

        $validated = $request->validate([
            'status' => ['nullable', Rule::in(Rider::STATUSES)],
        ]);

        $riders = Rider::query()
            ->when(
                isset($validated['status']),
                fn ($query) => $query->where('status', $validated['status']),
            )
            // Pending first: the queue is the point of the screen. Then oldest
            // application first, so nobody waits behind a later signup.
            ->orderByRaw("CASE WHEN status = ? THEN 0 ELSE 1 END", [Rider::STATUS_PENDING])
            ->orderBy('created_at')
            ->get();

        return response()->json([
            'riders' => $riders->map(fn (Rider $rider) => $rider->toReviewArray())->values(),
        ]);
    }

    /**
     * Approve, reject or suspend.
     *
     * One endpoint rather than three, because the three differ only in the
     * value written and every one of them wants the same note attached — the
     * note is what the rider reads on their status screen, and a rejection
     * without one is a dead end for them.
     */
    public function decide(Request $request, Rider $rider): JsonResponse
    {
        $this->requireSecret($request);

        $validated = $request->validate([
            'status' => ['required', Rule::in([
                Rider::STATUS_APPROVED,
                Rider::STATUS_REJECTED,
                Rider::STATUS_SUSPENDED,
            ])],
            'note' => ['nullable', 'string', 'max:500'],
        ]);

        $rider->forceFill([
            'status' => $validated['status'],
            'review_note' => isset($validated['note']) ? trim($validated['note']) : null,
            'reviewed_at' => now(),
        ])->save();

        // Losing access has to mean losing it now, not at token expiry: a
        // suspended rider holding a live token could still claim orders.
        if ($validated['status'] !== Rider::STATUS_APPROVED) {
            $rider->tokens()->delete();
        }

        Log::info('[rider-review] '.$validated['status'], [
            'riderId' => $rider->id,
            'email' => $rider->email,
        ]);

        return response()->json(['rider' => $rider->toReviewArray()]);
    }

    /**
     * Streams one of a rider's two document images.
     *
     * Streamed through the app rather than linked, because the whole point of
     * the private disk is that there is no URL. The path is never taken from
     * the request — the caller names which document, and the path comes off
     * the record.
     */
    public function document(Request $request, Rider $rider, string $document): StreamedResponse
    {
        $this->requireSecret($request);

        $path = match ($document) {
            'license' => $rider->license_image_path,
            'plate' => $rider->plate_image_path,
            default => abort(404),
        };

        $disk = Storage::disk('local');

        abort_unless($path !== null && $disk->exists($path), 404, 'That document is missing.');

        // Inline so the operator can look at it in the browser, and explicitly
        // no-store: an identity document should not sit in a disk cache after
        // the tab closes.
        return $disk->response($path, null, [
            'Cache-Control' => 'no-store, max-age=0',
        ]);
    }

    /**
     * Constant-time compare so the secret cannot be recovered by timing the
     * response, and a missing config is a 500 rather than an open door.
     * Deliberately identical to PlatformAdminController's — one operator
     * secret, checked the same way wherever it is checked.
     */
    private function requireSecret(Request $request): void
    {
        $expected = config('services.platform_admin.secret');

        abort_if(! is_string($expected) || $expected === '', 500, 'Platform admin is not configured.');

        $provided = (string) $request->input('secret', '');

        abort_unless(hash_equals($expected, $provided), 401, 'Incorrect secret.');
    }
}
