<?php

namespace App\Http\Controllers\Api;

use App\Events\RiderPositionUpdated;
use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Rider;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * The rider's phone saying where it is.
 *
 * This is the endpoint the rider app's README said did not exist, and its
 * absence was the stated reason that app ships with no location permission at
 * all. It exists now, so the app can ask.
 *
 * The contract is deliberately narrow, because a location endpoint is the most
 * sensitive write on the platform:
 *
 *   - The rider names no order and no shop. They post *their* position; the
 *     server works out who is entitled to see it from the orders they are
 *     actually carrying. A rider cannot broadcast themselves onto a delivery
 *     that is not theirs, because they never get to name one.
 *   - Carrying nothing means telling nobody. The fix is still recorded, so that
 *     a rider who opens the app before taking a job is instantly placeable when
 *     they take one, but no event goes out and no screen shows them. A rider
 *     off shift is not on a map.
 *   - Nothing is retained. Each write overwrites the last (see the migration).
 *     There is no trail to subpoena, sell, or leak.
 */
class RiderPositionController extends Controller
{
    /**
     * Record a fix, and tell whoever is waiting on this delivery.
     *
     * Answers with how long to wait before the next one rather than letting the
     * app hard-code an interval, so the cadence can be slowed from the server
     * on a day the fleet is large without shipping a new APK. A rider carrying
     * nothing is told to back off hard: there is nobody to tell.
     */
    public function store(Request $request): JsonResponse
    {
        $rider = $this->rider($request);

        $validated = $request->validate([
            'lat' => ['required', 'numeric', 'between:-90,90'],
            'lng' => ['required', 'numeric', 'between:-180,180'],
            // Bearing in degrees from true north. Absent when the phone is
            // standing still, which is a normal reading, not a missing one.
            'headingDeg' => ['nullable', 'numeric', 'between:0,360'],
            'speedKph' => ['nullable', 'numeric', 'between:0,300'],
            'accuracyM' => ['nullable', 'numeric', 'min:0'],
        ]);

        $rider->forceFill([
            'last_lat' => $validated['lat'],
            'last_lng' => $validated['lng'],
            'last_heading_deg' => $validated['headingDeg'] ?? null,
            'last_speed_kph' => $validated['speedKph'] ?? null,
            'last_accuracy_m' => $validated['accuracyM'] ?? null,
            'position_updated_at' => now(),
            'last_seen_at' => now(),
        ])->save();

        $position = $rider->positionArray() ?? [];

        $active = Order::query()
            ->where('rider_id', $rider->id)
            ->whereIn('delivery_stage', ['assigned', 'picked_up'])
            ->get(['id', 'store_id', 'delivery_stage']);

        foreach ($active as $order) {
            // event(), not the Dispatchable static: this event is constructed
            // once per order from data the loop already has, and dispatching it
            // by class would rebuild it from arguments.
            event(RiderPositionUpdated::forOrder($order, $position));
        }

        return response()->json([
            'recorded' => true,
            'activeDeliveries' => $active->count(),
            // Ten seconds is a smooth line on a map at tricycle speed. Sixty is
            // a battery the rider still has at the end of their shift, and is
            // plenty when nobody is watching.
            'nextPingSeconds' => $active->isEmpty() ? 60 : 10,
        ]);
    }

    /**
     * Stop reporting.
     *
     * Called when the rider turns sharing off or signs out, so the last fix is
     * forgotten rather than left sitting in the row until it happens to go
     * stale. A rider who says stop should be gone from the database, not merely
     * gone from the map.
     */
    public function destroy(Request $request): JsonResponse
    {
        $rider = $this->rider($request);

        $rider->forceFill([
            'last_lat' => null,
            'last_lng' => null,
            'last_heading_deg' => null,
            'last_speed_kph' => null,
            'last_accuracy_m' => null,
            'position_updated_at' => null,
        ])->save();

        return response()->json(['recorded' => false]);
    }

    private function rider(Request $request): Rider
    {
        $rider = $request->user();
        abort_unless($rider instanceof Rider, 403, 'Rider account required.');

        return $rider;
    }
}
