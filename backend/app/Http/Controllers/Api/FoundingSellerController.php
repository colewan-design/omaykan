<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Mail\SellerApplicationAlertMail;
use App\Mail\SellerApplicationReceivedMail;
use App\Models\SellerApplication;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Mail;
use Illuminate\Validation\Rule;

/**
 * The founding-seller campaign: the first thirty Baguio / La Trinidad
 * businesses on Omaykan.
 *
 * Two public endpoints, both unauthenticated because the people using them do
 * not have accounts yet — that is the point of the page.
 *
 * `status` is what lets the page say how many places are left without inventing
 * a countdown. The number is the badges actually issued, so it only moves when
 * an operator has really taken a business on.
 */
class FoundingSellerController extends Controller
{
    /**
     * The counters this campaign is aimed at. Kept as labels rather than the
     * internal business modes: an applicant picks the words they'd use for
     * their own stall, and mapping that onto one of the four modes the app can
     * be set up as is a judgement call made at setup, not one the form should
     * pretend to have made. The page reads this list from `status` so the two
     * halves cannot drift — `store` rejects anything not in it.
     */
    private const CATEGORIES = [
        'Sari-sari / grocery',
        'Market stall / fresh produce',
        'Restaurant / carinderia',
        'Coffee shop / milk tea',
        'Bakery / pastries',
        'Handicrafts / souvenirs',
        'Salon / services',
        'Other',
    ];

    public function status(): JsonResponse
    {
        $claimed = SellerApplication::foundingClaimed();
        $limit = SellerApplication::FOUNDING_LIMIT;

        return response()->json([
            'limit' => $limit,
            'claimed' => $claimed,
            'remaining' => max($limit - $claimed, 0),
            'open' => $claimed < $limit,
            'categories' => self::CATEGORIES,
        ]);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'businessName' => ['required', 'string', 'max:120'],
            'ownerName' => ['required', 'string', 'max:120'],
            'category' => ['required', Rule::in(self::CATEGORIES)],
            // Not a phone regex. Numbers here arrive as 0917…, +63 917…, with
            // spaces and dashes, and a rule tight enough to be worth having
            // would turn away business over punctuation.
            'mobile' => ['required', 'string', 'max:40'],
            'email' => ['required', 'email', 'max:190'],
            // A page name or a handle, not necessarily a URL — the form asks
            // for "facebook.com/yourpage or @yourhandle".
            'socialUrl' => ['nullable', 'string', 'max:190'],
            'address' => ['required', 'string', 'max:255'],
            // The same 500 the form counts down from.
            'productsDescription' => ['required', 'string', 'max:500'],
            'offersDelivery' => ['required', 'boolean'],
            'wantsFounding' => ['required', 'boolean'],
        ]);

        $application = SellerApplication::create([
            'business_name' => trim($validated['businessName']),
            'owner_name' => trim($validated['ownerName']),
            'business_category' => $validated['category'],
            'mobile' => trim($validated['mobile']),
            'email' => strtolower(trim($validated['email'])),
            'social_url' => isset($validated['socialUrl']) && trim($validated['socialUrl']) !== ''
                ? trim($validated['socialUrl'])
                : null,
            'address' => trim($validated['address']),
            'products_description' => trim($validated['productsDescription']),
            'offers_delivery' => $validated['offersDelivery'],
            'wants_founding' => $validated['wantsFounding'],
        ]);

        $claimed = SellerApplication::foundingClaimed();

        $this->announce($application, $claimed);

        return response()->json([
            'id' => $application->id,
            'businessName' => $application->business_name,
            // What the applicant is told: whether the campaign still had room
            // when they applied. Not a promise of a number — an operator has
            // to accept them before a badge exists.
            'foundingOpen' => $claimed < SellerApplication::FOUNDING_LIMIT,
            'remaining' => max(SellerApplication::FOUNDING_LIMIT - $claimed, 0),
        ], 201);
    }

    /**
     * Tell the applicant it arrived, and the operators that it is waiting.
     *
     * Failures are swallowed for the reason SignupController::announce gives:
     * the row is already written, and reporting a mail problem as a failed
     * application would have somebody filling the form in a second time.
     */
    private function announce(SellerApplication $application, int $claimed): void
    {
        try {
            Mail::to($application->email)->queue(new SellerApplicationReceivedMail($application));

            $alertsTo = config('mail.alerts_to');

            if (\is_string($alertsTo) && trim($alertsTo) !== '') {
                Mail::to($alertsTo)->queue(new SellerApplicationAlertMail($application, $claimed));
            }
        } catch (\Throwable $e) {
            report($e);
        }
    }
}
