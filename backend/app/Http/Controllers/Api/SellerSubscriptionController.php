<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\PlatformSetting;
use App\Models\Subscription;
use App\Services\Billing\GatewaySettlement;
use App\Services\Billing\PayMongoGateway;
use App\Models\SubscriptionPayment;
use App\Services\Billing\SubscriptionBilling;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

/**
 * The merchant's own view of what they owe us, and how they say they paid it.
 *
 * This did not exist before 2026-09-20. `gcash_reference` was written once at
 * signup by a form that no longer sends it, nothing displayed it, and a shop
 * had no way at all to tell us about a transfer. "Manual collection" meant an
 * email to a person and an operator clicking Verify.
 *
 * **Deliberately on `storeContext`, not `writableStoreContext`.** Every other
 * action that changes the shop's records is refused for an unpaid tenant —
 * but an unpaid tenant is exactly the one that needs to pay, and locking the
 * pay-us screen behind being paid up is a door that only opens from inside.
 * A suspended org still cannot reach this: the resolver refuses those before a
 * context exists, and a suspension is a conversation with a person, not a
 * transfer.
 *
 * Owner only. Billing is the person whose name is on the business, and a
 * cashier seeing the shop's payment history is a privacy leak rather than a
 * convenience.
 *
 * See documentation/subscription-and-suspension.md §6.3.
 */
class SellerSubscriptionController extends Controller
{
    use ActsForAStore;

    /** What the shop owes, what it has sent, and where that got to. */
    public function show(Request $request, PayMongoGateway $gateway): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->abortUnlessOwner($context->role);

        $subscription = Subscription::query()
            ->where('organization_id', $context->organizationId())
            ->first();

        $plan = PlatformSetting::current()->planSettings();

        $payments = $subscription === null
            ? collect()
            : $subscription->payments()->with('submittedBy')->limit(24)->get();

        return response()->json([
            // The *current* platform price, not the one frozen on the row at
            // signup. This is the number the operator edits in Settings →
            // Subscription, and quoting it here is what makes that control
            // actually reach a merchant.
            // Whether this install can actually take a payment. The panel
            // hides its Pay button on false rather than offering one that
            // answers 503.
            'gatewayReady' => $gateway->enabled(),
            'plan' => [
                'id' => $plan['id'],
                'amountCents' => (int) $plan['amountCents'],
            ],
            'subscription' => $subscription === null ? null : [
                'status' => $subscription->status,
                // What this organization agreed to at signup. Shown beside the
                // plan price so a merchant whose price has changed can see
                // both rather than being surprised by one.
                'agreedAmountCents' => (int) $subscription->amount_cents,
                'trialEndsAt' => $subscription->trial_ends_at?->toIso8601String(),
                'currentPeriodEndsAt' => $subscription->current_period_ends_at?->toIso8601String(),
                'graceEndsAt' => $subscription->current_period_ends_at === null
                    ? null
                    : app(SubscriptionBilling::class)->graceEndsAt($subscription)?->toIso8601String(),
            ],
            'payments' => $payments->map(fn (SubscriptionPayment $payment) => [
                'id' => $payment->id,
                'status' => $payment->status,
                'reference' => $payment->reference,
                'amountCents' => $payment->amount_cents,
                'note' => $payment->note,
                'submittedBy' => $payment->submittedBy?->name,
                'submittedAt' => $payment->created_at?->toIso8601String(),
                'periodStart' => $payment->period_start?->toIso8601String(),
                'periodEnd' => $payment->period_end?->toIso8601String(),
                'rejectionReason' => $payment->rejection_reason,
            ])->values(),
            'howToPay' => [
                'method' => 'Manual GCash or bank transfer',
                'supportEmail' => config('support.email'),
            ],
        ]);
    }

    /** "I sent this." Recorded as a claim; an operator decides what it buys. */
    public function storePayment(Request $request): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->abortUnlessOwner($context->role);

        $subscription = Subscription::query()
            ->where('organization_id', $context->organizationId())
            ->first();

        abort_if($subscription === null, 404, 'This shop has no subscription on record.');

        $validated = $request->validate([
            'reference' => ['required', 'string', 'max:120'],
            // Accepted as sent, even when it disagrees with the plan price. A
            // short payment is a fact about the conversation; rounding it to
            // what we expected would hide it from the operator reviewing it.
            'amountCents' => ['required', 'integer', 'min:1', 'max:100000000'],
            'note' => ['nullable', 'string', 'max:500'],
        ]);

        // One open claim at a time. Without this, a merchant who does not see
        // an immediate response sends the same reference three times and the
        // operator reviews a queue of duplicates.
        $pending = $subscription->payments()
            ->where('status', SubscriptionPayment::STATUS_SUBMITTED)
            ->first();

        if ($pending !== null) {
            throw ValidationException::withMessages([
                'reference' => 'You already have a payment awaiting review. '
                    .'We will email you when it has been checked.',
            ]);
        }

        $payment = SubscriptionPayment::query()->create([
            'subscription_id' => $subscription->id,
            'organization_id' => $subscription->organization_id,
            'status' => SubscriptionPayment::STATUS_SUBMITTED,
            'reference' => trim($validated['reference']),
            'amount_cents' => (int) $validated['amountCents'],
            'note' => isset($validated['note']) ? trim($validated['note']) : null,
            'submitted_by_user_id' => $context->user->id,
        ]);

        return response()->json([
            'id' => $payment->id,
            'status' => $payment->status,
            'message' => 'Thanks — we will check this against our records and confirm by email.',
        ], 201);
    }

    /**
     * "Let me pay now." Opens a PayMongo checkout and hands back the URL.
     *
     * The opposite provenance from `storePayment` above: nothing here is a
     * claim to be reviewed. The row is written before the merchant leaves so
     * that an abandoned checkout is still something we can look up, and it is
     * marked `source = paymongo` so it never appears in the operator's queue
     * of things to verify by hand.
     *
     * 503 rather than 404 when unconfigured: the gateway is an install-level
     * fact, and a merchant who is told "not found" goes looking for a button
     * they did not lose.
     */
    public function startGatewayPayment(Request $request, PayMongoGateway $gateway): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->abortUnlessOwner($context->role);

        abort_unless($gateway->enabled(), 503, 'Online payment is not switched on.');

        $subscription = Subscription::query()
            ->where('organization_id', $context->organizationId())
            ->first();

        abort_if($subscription === null, 404, 'This shop has no subscription on record.');

        $plan = PlatformSetting::current()->planSettings();
        $amount = (int) ($plan['amountCents'] ?? 0);

        $checkout = $gateway->openCheckout(
            $subscription,
            rtrim(config('app.url'), '/').'/app.html#/settings?subscription=paid',
            rtrim(config('app.url'), '/').'/app.html#/settings?subscription=cancelled',
            // The owner, because `abortUnlessOwner` above means that is who
            // this is. No phone: nothing in the schema holds one, and
            // PayMongo asks for it as optional anyway.
            ['name' => $context->user->name, 'email' => $context->user->email],
        );

        $payment = SubscriptionPayment::query()->create([
            'subscription_id' => $subscription->id,
            'organization_id' => $subscription->organization_id,
            'status' => SubscriptionPayment::STATUS_SUBMITTED,
            'source' => 'paymongo',
            'provider_session_id' => $checkout['id'],
            // PayMongo's own id is the reference; there is nothing for the
            // merchant to type and nothing for an operator to match by eye.
            'reference' => $checkout['id'],
            'amount_cents' => $amount,
            'submitted_by_user_id' => $context->user->id,
        ]);

        return response()->json([
            'id' => $payment->id,
            'checkoutUrl' => $checkout['url'],
        ], 201);
    }

    /**
     * The merchant came back from GCash. Settle, if PayMongo agrees.
     *
     * Not the source of truth and not required: the webhook settles the same
     * checkout through the same service. This exists so a merchant who is
     * looking at the screen sees it turn paid immediately rather than
     * whenever the notification lands.
     */
    public function settleGatewayPayment(Request $request, GatewaySettlement $settlement): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->abortUnlessOwner($context->role);

        $validated = $request->validate([
            'sessionId' => ['required', 'string', 'max:120'],
        ]);

        $payment = SubscriptionPayment::query()
            ->where('provider_session_id', $validated['sessionId'])
            ->where('organization_id', $context->organizationId())
            ->first();

        // Scoped to the caller's own organization, so one merchant cannot
        // poke at another's checkout by guessing a session id.
        abort_if($payment === null, 404, 'No such payment.');

        $settlement->settle($validated['sessionId']);

        return response()->json([
            'status' => $payment->fresh()->status,
        ]);
    }

    /**
     * `admin` is the owner role at a store. Managers run the shop; the
     * subscription is the owner's business with us.
     */
    private function abortUnlessOwner(string $role): void
    {
        abort_unless(
            $role === 'admin',
            403,
            'Only the shop owner can see or submit subscription payments.',
        );
    }
}
