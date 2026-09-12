<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use App\Models\Order;
use App\Models\Rider;
use App\Models\RiderRating;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

/**
 * Scores customers leave for riders, and what a rider can read back.
 *
 * Two audiences on one controller because they are two halves of one fact, and
 * splitting them would put the rules about who may rate what in one file and
 * the rules about what a rider may see in another.
 *
 * ## What makes a rating possible
 *
 * Four conditions, all checked here rather than in the request class, because
 * three of them are about a row and not about input:
 *
 * 1. The order belongs to the customer asking.
 * 2. It was actually **delivered** — an order still on a bike has not finished
 *    being a delivery, and a customer rating one mid-route is rating a guess.
 * 3. It had a rider with an account. A shop that typed a name into
 *    `rider_name` has nobody to attach a score to.
 * 4. Nobody has rated it yet. Enforced by a unique index as well, so a double
 *    submit races into a clean 422 rather than two rows.
 */
class RiderRatingController extends Controller
{
    /**
     * Leave a score for the rider who brought this order.
     *
     * @throws ValidationException
     */
    public function store(Request $request, string $order): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();

        $data = $request->validate([
            'score' => ['required', 'integer', 'between:'.RiderRating::MIN_SCORE.','.RiderRating::MAX_SCORE],
            'comment' => ['nullable', 'string', 'max:500'],
        ]);

        // `online()` and the ownership clause together are what stop this from
        // being a way to probe for orders that are not the caller's: a wrong id
        // and somebody else's id fail identically, as a 404.
        $row = Order::query()
            ->online()
            ->where('customer_account_id', $account->getKey())
            ->findOrFail($order);

        if ($row->delivery_stage !== 'delivered') {
            throw ValidationException::withMessages([
                'score' => 'You can rate the rider once this order has been delivered.',
            ]);
        }

        if ($row->rider_id === null) {
            throw ValidationException::withMessages([
                'score' => 'This order was not carried by a rider with an account.',
            ]);
        }

        if (RiderRating::query()->where('order_id', $row->getKey())->exists()) {
            throw ValidationException::withMessages([
                'score' => 'You have already rated this delivery.',
            ]);
        }

        $rating = RiderRating::query()->create([
            'rider_id' => $row->rider_id,
            'order_id' => $row->getKey(),
            'customer_account_id' => $account->getKey(),
            'score' => $data['score'],
            'comment' => isset($data['comment']) ? trim($data['comment']) : null,
        ]);

        return response()->json([
            'rating' => [
                'score' => $rating->score,
                'comment' => $rating->comment,
            ],
        ], 201);
    }

    /**
     * What this rider has been scored.
     *
     * The summary is the part the app puts on a screen; the list underneath is
     * capped and recent because a rider scrolling three years of remarks about
     * themselves is not a feature, it is a bad afternoon.
     *
     * Comments are returned without the customer attached — see
     * RiderRating::toRiderArray for why that is not an oversight.
     */
    public function index(Request $request): JsonResponse
    {
        /** @var Rider $rider */
        $rider = $request->user();

        $recent = $rider->ratings()
            ->with('order:id,ticket_number')
            ->latest()
            ->limit(20)
            ->get();

        return response()->json([
            'summary' => $rider->ratingSummary(),
            'confidenceThreshold' => Rider::RATING_CONFIDENCE_THRESHOLD,
            'ratings' => $recent->map(fn (RiderRating $r) => $r->toRiderArray())->values(),
        ]);
    }
}
