<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Mail\PlatformAdminReplyMail;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\Subscription;
use App\Models\SubscriptionPayment;
use App\Models\User;
use App\Services\Billing\SubscriptionBilling;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Carbon;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Superadmin dashboard backend: reviews pending self-serve signups, verifies or
 * rejects GCash payments, and manages orgs and owner accounts across every
 * tenant.
 *
 * Replaces api/platform-admin.ts. Reached behind `auth:platform` — a signed-in
 * PlatformAdmin, not any organization's own Admin role, because this is a
 * cross-tenant tool and is deliberately not scoped to one tenant.
 *
 * This used to be gated by a single shared PLATFORM_ADMIN_SECRET. Every action
 * below is logged, and an audit line naming a secret tells you nothing about
 * who acted; the routes now carry a real account. See routes/api.php.
 */
class PlatformAdminController extends Controller
{
    /** No 0/O or 1/I/l: the operator reads these back to an owner by hand. */
    private const PASSWORD_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';
    private const BUSINESS_MODE_LABELS = [
        'coffee-shop' => 'Coffee shop',
        'grocery' => 'Grocery store',
        'restaurant' => 'Restaurant',
        'nail-salon' => 'Nail Salon',
    ];

    public function handle(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'action' => ['required', Rule::in([
                'listOrgs', 'verify', 'reject', 'suspendOrg', 'reactivateOrg',
                'resetOwnerPassword', 'setOwnerDisabled', 'deleteOrg', 'sendOwnerEmail',
                'listPayments', 'acceptPayment', 'rejectPayment',
            ])],
        ]);

        return match ($validated['action']) {
            'listOrgs' => $this->listOrgs(),
            'verify' => $this->setSubscriptionStatus($request, Subscription::STATUS_ACTIVE),
            'reject' => $this->setSubscriptionStatus($request, Subscription::STATUS_REJECTED),
            'listPayments' => $this->listPayments($request),
            'acceptPayment' => $this->acceptPayment($request),
            'rejectPayment' => $this->rejectPayment($request),
            'suspendOrg' => $this->setSuspended($request, true),
            'reactivateOrg' => $this->setSuspended($request, false),
            'resetOwnerPassword' => $this->resetOwnerPassword($request),
            'setOwnerDisabled' => $this->setOwnerDisabled($request),
            'deleteOrg' => $this->deleteOrg($request),
            'sendOwnerEmail' => $this->sendOwnerEmail($request),
        };
    }

    private function organizationFrom(Request $request): Organization
    {
        $validated = $request->validate([
            'organizationSlug' => ['required', 'string'],
        ]);

        return Organization::query()
            ->where('slug', $validated['organizationSlug'])
            ->firstOr(fn () => abort(404, 'Organization not found.'));
    }

    /**
     * Names the operator who acted. The whole point of replacing the shared
     * secret was that these lines resolve to a person; `?->` rather than a bare
     * access so a log call can never be the thing that 500s an action.
     */
    private function audit(string $action, string $slug, array $context = []): void
    {
        Log::info('[platform-admin] '.$action, [
            'organizationSlug' => $slug,
            'operator' => auth('platform')->user()?->email,
        ] + $context);
    }

    /**
     * Every org with its store, subscription and admin accounts.
     *
     * Eager-loaded rather than queried per row — the Firestore version fanned
     * out one read per organization and then batched Auth lookups to undo the
     * damage; a relational database just joins.
     */
    private function listOrgs(): JsonResponse
    {
        $organizations = Organization::query()
            ->with(['subscription', 'stores' => fn ($query) => $query->orderBy('created_at')])
            ->orderBy('name')
            ->get();

        $admins = OrganizationMembership::query()
            ->where('membership_role', 'admin')
            ->whereIn('organization_id', $organizations->pluck('id'))
            ->with('user')
            ->get()
            ->groupBy('organization_id');

        $rows = $organizations->map(function (Organization $organization) use ($admins) {
            $store = $organization->stores->first();
            $subscription = $organization->subscription;

            return [
                'organizationSlug' => $organization->slug,
                'organizationName' => $organization->name,
                'suspended' => (bool) $organization->suspended,
                // What the till and the storefront will actually do, computed
                // by the same method they call. `suspended` and the subscription
                // are the inputs; this is the answer, and showing it beside
                // them is how an operator sees that the system agrees with them.
                'tenantAccess' => $organization->accessVerdict()->value,
                'store' => $store === null ? null : [
                    'name' => $store->name,
                    'businessMode' => $store->business_mode,
                    'businessTypeLabel' => $this->businessTypeLabelFor($store),
                    // The branch slug, not a credential. The shop-wide pairing
                    // code that used to sit here was retired with device
                    // pairing; staff sign in as themselves now.
                    'storeCode' => $store->code,
                ],
                'subscription' => $subscription === null ? null : [
                    'status' => $subscription->status,
                    'plan' => $subscription->plan,
                    'amountCents' => $subscription->amount_cents,
                    'gcashReference' => $subscription->gcash_reference,
                    'submittedAt' => $subscription->submitted_at?->toIso8601String(),
                    'verifiedAt' => $subscription->verified_at?->toIso8601String(),
                    'trialEndsAt' => $subscription->trial_ends_at?->toIso8601String(),
                    'currentPeriodEndsAt' => $subscription->current_period_ends_at?->toIso8601String(),
                ],
                'admins' => ($admins[$organization->id] ?? collect())
                    ->filter(fn ($membership) => $membership->user !== null)
                    ->map(fn ($membership) => [
                        'uid' => $membership->user->id,
                        'username' => $membership->user->username,
                        'fullName' => $membership->user->name,
                        'email' => $membership->user->email,
                        'disabled' => $membership->user->status !== 'active',
                    ])->values(),
            ];
        });

        return response()->json(['organizations' => $rows]);
    }

    private function businessTypeLabelFor(Store $store): string
    {
        return trim((string) $store->business_type_label) !== ''
            ? $store->business_type_label
            : (self::BUSINESS_MODE_LABELS[$store->business_mode] ?? $store->business_mode ?? '—');
    }

    private function setSubscriptionStatus(Request $request, string $status): JsonResponse
    {
        $organization = $this->organizationFrom($request);
        $subscription = $organization->subscription;

        abort_if($subscription === null, 404, 'This organization has no subscription on record.');

        if ($status === Subscription::STATUS_ACTIVE) {
            // Verify goes through the billing service rather than writing the
            // row here, so that "this subscription has been paid for" happens
            // in exactly one place — including clearing the dunning trail, so
            // a merchant who renews stops being chased. There is no gateway
            // and none planned (§6.3); this is the whole of collection.
            app(SubscriptionBilling::class)->recordPayment($subscription);
        } else {
            $subscription->status = $status;
            $subscription->verified_at = null;
            $subscription->rejection_reason = $status === Subscription::STATUS_REJECTED
                ? $request->string('reason')->trim()->value() ?: null
                : null;
            $subscription->save();
        }

        $this->audit($status === Subscription::STATUS_ACTIVE ? 'verify' : 'reject', $organization->slug);

        return response()->json(['status' => $subscription->status]);
    }

    /**
     * The review queue: manual transfers merchants say they have made.
     *
     * Pending first and oldest first within that, because this is a queue
     * somebody works through rather than a report they browse. Recent decided
     * ones stay visible so an operator can see what they just did — and undo
     * it by hand if it was wrong, which with manual collection is the only
     * "undo" there is.
     */
    private function listPayments(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'organizationSlug' => ['nullable', 'string'],
        ]);

        $payments = SubscriptionPayment::query()
            ->with(['organization', 'submittedBy'])
            ->when(
                ! empty($validated['organizationSlug']),
                fn ($query) => $query->whereHas(
                    'organization',
                    fn ($inner) => $inner->where('slug', $validated['organizationSlug'])
                ),
            )
            // `submitted` sorts before `accepted`/`rejected` alphabetically by
            // luck rather than design, so order on the status explicitly.
            ->orderByRaw("CASE WHEN status = ? THEN 0 ELSE 1 END", [SubscriptionPayment::STATUS_SUBMITTED])
            ->orderBy('created_at')
            ->limit(200)
            ->get();

        return response()->json([
            'payments' => $payments->map(fn (SubscriptionPayment $payment) => [
                'id' => $payment->id,
                'organizationSlug' => $payment->organization?->slug,
                'organizationName' => $payment->organization?->name,
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
        ]);
    }

    /**
     * "I found this transfer, and it buys them until X."
     *
     * The only caller that passes a date to `recordPayment()`, and therefore
     * the only thing in the product that ever sets a billing period. Until an
     * operator does this, `current_period_ends_at` stays null and the
     * subscription rests on its trial — which is exactly right while the price
     * is still a placeholder.
     */
    private function acceptPayment(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'paymentId' => ['required', 'string'],
            // The operator decides what the money bought. Not derived from the
            // amount: a merchant who pays for two months at once, or short,
            // is a conversation rather than an arithmetic problem.
            'periodStart' => ['required', 'date'],
            'periodEnd' => ['required', 'date', 'after:periodStart'],
        ]);

        $payment = SubscriptionPayment::query()
            ->with('subscription')
            ->findOr($validated['paymentId'], fn () => abort(404, 'Payment not found.'));

        abort_unless(
            $payment->isPending(),
            409,
            'That payment has already been reviewed.',
        );

        $subscription = $payment->subscription;

        abort_if($subscription === null, 404, 'That payment has no subscription.');

        $periodEnd = Carbon::parse($validated['periodEnd']);

        DB::transaction(function () use ($payment, $subscription, $validated, $periodEnd) {
            $payment->forceFill([
                'status' => SubscriptionPayment::STATUS_ACCEPTED,
                'period_start' => Carbon::parse($validated['periodStart']),
                'period_end' => $periodEnd,
                'reviewed_by_platform_admin_id' => auth('platform')->id(),
                'reviewed_at' => now(),
                'rejection_reason' => null,
            ])->save();

            // The one seam. Activates the subscription, extends the period,
            // and clears the dunning trail so the merchant stops being chased.
            app(SubscriptionBilling::class)->recordPayment($subscription, $periodEnd);
        });

        $this->audit('acceptPayment', $payment->organization?->slug ?? '—', [
            'paymentId' => $payment->id,
            'periodEnd' => $periodEnd->toDateString(),
        ]);

        return response()->json([
            'status' => $payment->status,
            'subscriptionStatus' => $subscription->fresh()->status,
        ]);
    }

    /** "I cannot find this transfer" — with a reason the merchant will read. */
    private function rejectPayment(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'paymentId' => ['required', 'string'],
            'reason' => ['required', 'string', 'max:500'],
        ]);

        $payment = SubscriptionPayment::query()
            ->findOr($validated['paymentId'], fn () => abort(404, 'Payment not found.'));

        abort_unless(
            $payment->isPending(),
            409,
            'That payment has already been reviewed.',
        );

        // Nothing touches the subscription. A rejected claim leaves the shop
        // exactly where it was — still owing, still inside whatever grace it
        // had. Rejecting a payment is not a punishment.
        $payment->forceFill([
            'status' => SubscriptionPayment::STATUS_REJECTED,
            'rejection_reason' => trim($validated['reason']),
            'reviewed_by_platform_admin_id' => auth('platform')->id(),
            'reviewed_at' => now(),
        ])->save();

        $this->audit('rejectPayment', $payment->organization?->slug ?? '—', [
            'paymentId' => $payment->id,
        ]);

        return response()->json(['status' => $payment->status]);
    }

    private function setSuspended(Request $request, bool $suspended): JsonResponse
    {
        $organization = $this->organizationFrom($request);

        $organization->suspended = $suspended;
        $organization->save();

        $this->audit($suspended ? 'suspendOrg' : 'reactivateOrg', $organization->slug);

        return response()->json(['suspended' => $organization->suspended]);
    }

    /**
     * Issues a new password and returns it once, in the clear — the operator
     * relays it to the owner by hand, the same way the GCash workflow already
     * works. Nothing stores the plaintext.
     */
    private function resetOwnerPassword(Request $request): JsonResponse
    {
        $organization = $this->organizationFrom($request);
        $user = $this->ownerFrom($request, $organization);

        $password = '';
        for ($i = 0; $i < 12; $i++) {
            $password .= self::PASSWORD_ALPHABET[random_int(0, strlen(self::PASSWORD_ALPHABET) - 1)];
        }

        $user->password = $password;
        $user->save();

        // Existing sessions must not survive a password reset.
        $user->tokens()->delete();

        $this->audit('resetOwnerPassword', $organization->slug, ['uid' => $user->id]);

        return response()->json(['password' => $password]);
    }

    private function setOwnerDisabled(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'disabled' => ['required', 'boolean'],
        ]);

        $organization = $this->organizationFrom($request);
        $user = $this->ownerFrom($request, $organization);

        $user->status = $validated['disabled'] ? 'disabled' : 'active';
        $user->save();

        if ($validated['disabled']) {
            $user->tokens()->delete();
        }

        $this->audit('setOwnerDisabled', $organization->slug, [
            'uid' => $user->id,
            'disabled' => $validated['disabled'],
        ]);

        return response()->json(['disabled' => $user->status !== 'active']);
    }

    private function sendOwnerEmail(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'subject' => ['required', 'string', 'max:190'],
            'message' => ['required', 'string', 'max:5000'],
        ]);

        $organization = $this->organizationFrom($request);
        $user = $this->ownerFrom($request, $organization);
        $store = $organization->stores()->orderBy('created_at')->first();

        $email = is_string($user->email) ? trim($user->email) : '';
        abort_if($email === '', 422, 'That account does not have an email address.');

        Mail::to($email)->queue(new PlatformAdminReplyMail(
            recipient: $user,
            organization: $organization,
            store: $store,
            subjectLine: trim($validated['subject']),
            messageBody: trim($validated['message']),
        ));

        $this->audit('sendOwnerEmail', $organization->slug, ['uid' => $user->id]);

        return response()->json(['queued' => true]);
    }

    /**
     * Irreversible. Requires the operator to retype the slug, so a misclick on
     * the wrong row in a list cannot destroy a tenant.
     */
    private function deleteOrg(Request $request): JsonResponse
    {
        $organization = $this->organizationFrom($request);

        $validated = $request->validate([
            'confirmSlug' => ['required', 'string'],
        ]);

        if ($validated['confirmSlug'] !== $organization->slug) {
            throw ValidationException::withMessages([
                'confirmSlug' => 'Type the organization slug exactly to confirm deletion.',
            ]);
        }

        DB::transaction(function () use ($organization) {
            $userIds = OrganizationMembership::query()
                ->where('organization_id', $organization->id)
                ->pluck('user_id');

            // Accounts that belong to no other organization go with it;
            // anyone with another membership keeps their login.
            $orphaned = User::query()
                ->whereIn('id', $userIds)
                ->whereDoesntHave('organizationMemberships', fn ($query) => $query->where('organization_id', '!=', $organization->id))
                ->get();

            foreach ($orphaned as $user) {
                $user->tokens()->delete();
                $user->delete();
            }

            // Everything else hangs off the org by cascading foreign keys.
            $organization->forceDelete();
        });

        $this->audit('deleteOrg', $organization->slug);

        return response()->json(['deleted' => true]);
    }

    private function ownerFrom(Request $request, Organization $organization): User
    {
        $validated = $request->validate([
            'uid' => ['required', 'string'],
        ]);

        $membership = OrganizationMembership::query()
            ->where('organization_id', $organization->id)
            ->where('user_id', $validated['uid'])
            ->with('user')
            ->first();

        abort_if($membership?->user === null, 404, 'That account is not a member of this organization.');

        return $membership->user;
    }
}
