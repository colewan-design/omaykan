<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Mail\PlatformAdminReplyMail;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Http\JsonResponse;
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
            ])],
        ]);

        return match ($validated['action']) {
            'listOrgs' => $this->listOrgs(),
            'verify' => $this->setSubscriptionStatus($request, Subscription::STATUS_ACTIVE),
            'reject' => $this->setSubscriptionStatus($request, Subscription::STATUS_REJECTED),
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
                'store' => $store === null ? null : [
                    'name' => $store->name,
                    'businessMode' => $store->business_mode,
                    'businessTypeLabel' => $this->businessTypeLabelFor($store),
                    // Readable because it is a public identifier, not the
                    // pairing secret — see the store discovery migration.
                    'pairingCode' => $store->public_store_code,
                ],
                'subscription' => $subscription === null ? null : [
                    'status' => $subscription->status,
                    'plan' => $subscription->plan,
                    'amountCents' => $subscription->amount_cents,
                    'gcashReference' => $subscription->gcash_reference,
                    'submittedAt' => $subscription->submitted_at?->toIso8601String(),
                    'verifiedAt' => $subscription->verified_at?->toIso8601String(),
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

        $subscription->status = $status;
        $subscription->verified_at = $status === Subscription::STATUS_ACTIVE ? now() : null;
        $subscription->rejection_reason = $status === Subscription::STATUS_REJECTED
            ? $request->string('reason')->trim()->value() ?: null
            : null;
        $subscription->save();

        $this->audit($status === Subscription::STATUS_ACTIVE ? 'verify' : 'reject', $organization->slug);

        return response()->json(['status' => $subscription->status]);
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
