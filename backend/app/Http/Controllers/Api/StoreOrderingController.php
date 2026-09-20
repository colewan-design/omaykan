<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\Store;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * The shop's own open/closed switch for online orders.
 *
 * Any role with the Orders page may use it, cashiers included. The person at
 * the counter in the middle of a rush is the one who knows the kitchen is
 * drowning, and making them find a manager first is how a shop ends up taking
 * forty orders it cannot cook. `ordering_paused_by` records who did it.
 *
 * `storeContext`, not `writableStoreContext`: pausing is the one thing an
 * unpaid shop should always be able to do. Its storefront is already closed;
 * this must not become the switch it cannot reach.
 *
 * See documentation/merchant-features.md §3.
 */
class StoreOrderingController extends Controller
{
    use ActsForAStore;

    /** A pause longer than this is not a pause; it is closing the shop. */
    private const MAX_PAUSE_DAYS = 14;

    public function show(Request $request): JsonResponse
    {
        return response()->json(self::stateOf($this->storeContext($request)->store));
    }

    public function update(Request $request): JsonResponse
    {
        $context = $this->storeContext($request);
        abort_unless($context->can('orders'), 403, 'Your role cannot pause online orders.');

        $validated = $request->validate([
            'paused' => ['required', 'boolean'],
            'resumesAt' => ['nullable', 'date', 'after:now', 'before:'.now()->addDays(self::MAX_PAUSE_DAYS)->toIso8601String()],
        ], [
            'resumesAt.after' => 'Pick a time that has not already passed.',
            'resumesAt.before' => 'A pause can last up to '.self::MAX_PAUSE_DAYS.' days.',
        ]);

        $store = $context->store;

        $store->forceFill($validated['paused']
            ? [
                'ordering_paused_at' => now(),
                'ordering_resumes_at' => $validated['resumesAt'] ?? null,
                'ordering_paused_by' => $context->user->id,
            ]
            : [
                'ordering_paused_at' => null,
                'ordering_resumes_at' => null,
                'ordering_paused_by' => null,
            ])->save();

        return response()->json(self::stateOf($store));
    }

    /**
     * The one shape every client reads — the till, the seller app, and (as
     * part of the catalog) the storefront.
     *
     * @return array{paused: bool, resumesAt: ?string, message: ?string}
     */
    public static function stateOf(Store $store): array
    {
        $paused = $store->isOrderingPaused();

        return [
            'paused' => $paused,
            'resumesAt' => $paused ? $store->ordering_resumes_at?->toIso8601String() : null,
            'message' => $store->orderingPausedMessage(),
        ];
    }
}
