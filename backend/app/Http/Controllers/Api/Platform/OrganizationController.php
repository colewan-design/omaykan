<?php

namespace App\Http\Controllers\Api\Platform;

use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\PlatformAuditLog;
use App\Models\Subscription;
use App\Models\User;
use App\Support\PlatformAudit;
use App\Support\ReadablePassword;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Tenants, as the operator sees them: the signup verification queue, the
 * suspend switch, the owner logins, and deletion.
 *
 * Carries over the logic that was in PlatformAdminController's `action`
 * switch — that part worked and is not worth rewriting — but as one endpoint
 * per operation rather than one endpoint with eight branches, so validation is
 * local to each and the audit action follows the route instead of a string in
 * a `match`.
 */
class OrganizationController extends Controller
{
    /** Rows per page in the tenant list. */
    private const PER_PAGE = 25;

    /**
     * The tenant list, searchable and filterable.
     *
     * Eager-loaded and grouped in memory rather than queried per row — the
     * original did the same, and it is why this stays one query for stores,
     * one for subscriptions and one for owners no matter how many tenants
     * there are.
     */
    public function index(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'q' => ['nullable', 'string', 'max:120'],
            'state' => ['nullable', Rule::in(['active', 'suspended'])],
            'subscription' => ['nullable', Rule::in([
                Subscription::STATUS_PENDING,
                Subscription::STATUS_ACTIVE,
                Subscription::STATUS_REJECTED,
                'none',
            ])],
            'page' => ['nullable', 'integer', 'min:1'],
        ]);

        $query = Organization::query()
            ->with(['subscription', 'stores' => fn ($q) => $q->orderBy('created_at')])
            ->orderBy('name');

        if (($term = trim($filters['q'] ?? '')) !== '') {
            $like = '%'.$term.'%';
            $query->where(fn ($q) => $q->where('name', 'like', $like)->orWhere('slug', 'like', $like));
        }

        if (($state = $filters['state'] ?? null) !== null) {
            $query->where('suspended', $state === 'suspended');
        }

        if (($subscription = $filters['subscription'] ?? null) !== null) {
            $subscription === 'none'
                ? $query->whereDoesntHave('subscription')
                : $query->whereHas('subscription', fn ($q) => $q->where('status', $subscription));
        }

        $page = $query->paginate(self::PER_PAGE);
        $rows = collect($page->items());
        $owners = $this->ownersByOrganization($rows->pluck('id'));

        return response()->json([
            'organizations' => $rows
                ->map(fn (Organization $organization) => $this->rowFor($organization, $owners))
                ->values(),
            'page' => $page->currentPage(),
            'lastPage' => $page->lastPage(),
            'total' => $page->total(),
        ]);
    }

    /**
     * One tenant in full: what the list shows, plus the numbers and the
     * history that make a suspend-or-delete decision possible.
     */
    public function show(Request $request, string $slug): JsonResponse
    {
        $organization = $this->organization($slug);
        $organization->load(['subscription', 'stores' => fn ($q) => $q->orderBy('created_at')]);

        $owners = $this->ownersByOrganization(collect([$organization->id]));

        $orders = Order::query()
            ->where('organization_id', $organization->id)
            ->where('business_date', '>=', now()->subDays(30)->toDateString())
            ->selectRaw('channel, count(*) as order_count, coalesce(sum(total_cents), 0) as revenue_cents')
            ->groupBy('channel')
            ->get();

        // Keyed on the slug, not the id, so a tenant's history is still
        // readable after the row is gone — see destroy().
        $trail = PlatformAuditLog::query()
            ->with('admin')
            ->where('subject_type', 'Organization')
            ->where('subject_id', $organization->slug)
            ->orderByDesc('created_at')
            ->limit(50)
            ->get()
            ->map(fn (PlatformAuditLog $log) => $log->toPortalArray());

        return response()->json([
            'organization' => $this->rowFor($organization, $owners) + [
                'createdAt' => $organization->created_at?->toIso8601String(),
                'rejectionReason' => $organization->subscription?->rejection_reason,
                'staffCount' => OrganizationMembership::query()
                    ->where('organization_id', $organization->id)->count(),
                'stores' => $organization->stores->map(fn ($store) => [
                    'id' => $store->id,
                    'name' => $store->name,
                    'code' => $store->code,
                    'businessMode' => $store->business_mode,
                    'pairingCode' => $store->public_store_code,
                    'address' => $store->address,
                ])->values(),
                'last30Days' => [
                    'orders' => (int) $orders->sum('order_count'),
                    'revenueCents' => (int) $orders->sum('revenue_cents'),
                    'byChannel' => $orders->mapWithKeys(fn ($row) => [$row->channel => [
                        'orders' => (int) $row->order_count,
                        'revenueCents' => (int) $row->revenue_cents,
                    ]]),
                ],
            ],
            'auditTrail' => $trail,
        ]);
    }

    /** Verify or reject a manually-paid subscription. */
    public function setSubscriptionStatus(Request $request, string $slug): JsonResponse
    {
        $data = $request->validate([
            'status' => ['required', Rule::in([Subscription::STATUS_ACTIVE, Subscription::STATUS_REJECTED])],
            'reason' => ['nullable', 'string', 'max:500'],
        ]);

        $organization = $this->organization($slug);
        $subscription = $organization->subscription;

        abort_if($subscription === null, 404, 'This organization has no subscription on record.');

        $rejected = $data['status'] === Subscription::STATUS_REJECTED;

        $subscription->status = $data['status'];
        $subscription->verified_at = $rejected ? null : now();
        $subscription->rejection_reason = $rejected ? (trim($data['reason'] ?? '') ?: null) : null;
        $subscription->save();

        PlatformAudit::record(
            $request,
            'subscription.'.($rejected ? 'rejected' : 'verified'),
            $organization->slug,
            [
                'gcashReference' => $subscription->gcash_reference,
                'amountCents' => $subscription->amount_cents,
                'reason' => $subscription->rejection_reason,
            ],
            'Organization',
        );

        return response()->json(['status' => $subscription->status]);
    }

    /** The kill switch. Keeps the data, locks everyone out, reversible. */
    public function setSuspended(Request $request, string $slug): JsonResponse
    {
        $data = $request->validate([
            'suspended' => ['required', 'boolean'],
            'reason' => ['nullable', 'string', 'max:500'],
        ]);

        $organization = $this->organization($slug);
        $organization->suspended = $data['suspended'];
        $organization->save();

        PlatformAudit::record(
            $request,
            $data['suspended'] ? 'organization.suspended' : 'organization.reactivated',
            $organization->slug,
            ['reason' => trim($data['reason'] ?? '') ?: null],
            'Organization',
        );

        return response()->json(['suspended' => $organization->suspended]);
    }

    /**
     * Issues a new password and returns it once, in the clear — the operator
     * relays it to the owner by hand, the same way the GCash workflow already
     * works. Nothing stores the plaintext.
     */
    public function resetOwnerPassword(Request $request, string $slug, string $userId): JsonResponse
    {
        $organization = $this->organization($slug);
        $user = $this->owner($organization, $userId);

        $password = ReadablePassword::generate();

        $user->password = $password;
        $user->save();

        // Existing sessions must not survive a password reset.
        $user->tokens()->delete();

        PlatformAudit::record($request, 'owner.password_reset', $organization->slug, [
            'userId' => $user->id,
            'username' => $user->username,
        ], 'Organization');

        return response()->json(['password' => $password]);
    }

    public function setOwnerDisabled(Request $request, string $slug, string $userId): JsonResponse
    {
        $data = $request->validate(['disabled' => ['required', 'boolean']]);

        $organization = $this->organization($slug);
        $user = $this->owner($organization, $userId);

        $user->status = $data['disabled'] ? 'disabled' : 'active';
        $user->save();

        if ($data['disabled']) {
            $user->tokens()->delete();
        }

        PlatformAudit::record(
            $request,
            $data['disabled'] ? 'owner.login_disabled' : 'owner.login_enabled',
            $organization->slug,
            ['userId' => $user->id, 'username' => $user->username],
            'Organization',
        );

        return response()->json(['disabled' => $user->status !== 'active']);
    }

    /**
     * Irreversible, and the only action in the portal that is.
     *
     * Two gates, not one: the operator retypes the slug, so a misclick on the
     * wrong row cannot destroy a tenant, and the account has to be an owner.
     * When one shared secret was the whole login, anybody who could work the
     * verification queue could also delete a merchant's entire history.
     */
    public function destroy(Request $request, string $slug): JsonResponse
    {
        abort_unless($request->user()->isOwner(), 403, 'Only an owner can delete a tenant.');

        $organization = $this->organization($slug);

        $data = $request->validate(['confirmSlug' => ['required', 'string']]);

        if ($data['confirmSlug'] !== $organization->slug) {
            throw ValidationException::withMessages([
                'confirmSlug' => 'Type the organization slug exactly to confirm deletion.',
            ]);
        }

        $deletedUsers = DB::transaction(function () use ($organization) {
            $userIds = OrganizationMembership::query()
                ->where('organization_id', $organization->id)
                ->pluck('user_id');

            // Accounts that belong to no other organization go with it;
            // anyone with another membership keeps their login.
            $orphaned = User::query()
                ->whereIn('id', $userIds)
                ->whereDoesntHave(
                    'organizationMemberships',
                    fn ($query) => $query->where('organization_id', '!=', $organization->id),
                )
                ->get();

            foreach ($orphaned as $user) {
                $user->tokens()->delete();
                $user->delete();
            }

            // Everything else hangs off the org by cascading foreign keys.
            $organization->forceDelete();

            return $orphaned->count();
        });

        // Written after the fact on purpose, and keyed by slug rather than a
        // foreign key precisely so the row outlives the tenant it describes.
        // This is the single most important record in the audit table.
        PlatformAudit::record($request, 'organization.deleted', $organization->slug, [
            'organizationName' => $organization->name,
            'deletedUsers' => $deletedUsers,
        ], 'Organization');

        return response()->json(['deleted' => true]);
    }

    private function organization(string $slug): Organization
    {
        return Organization::query()
            ->where('slug', $slug)
            ->firstOr(fn () => abort(404, 'Organization not found.'));
    }

    private function owner(Organization $organization, string $userId): User
    {
        $membership = OrganizationMembership::query()
            ->where('organization_id', $organization->id)
            ->where('user_id', $userId)
            ->with('user')
            ->first();

        abort_if($membership?->user === null, 404, 'That account is not a member of this organization.');

        return $membership->user;
    }

    /**
     * Admin memberships for a set of organizations, in one query.
     *
     * @param  Collection<int, string>  $organizationIds
     * @return Collection<string, Collection<int, OrganizationMembership>>
     */
    private function ownersByOrganization(Collection $organizationIds): Collection
    {
        return OrganizationMembership::query()
            ->where('membership_role', 'admin')
            ->whereIn('organization_id', $organizationIds)
            ->with('user')
            ->get()
            ->groupBy('organization_id');
    }

    /**
     * @param  Collection<string, Collection<int, OrganizationMembership>>  $owners
     * @return array<string, mixed>
     */
    private function rowFor(Organization $organization, Collection $owners): array
    {
        $store = $organization->stores->first();
        $subscription = $organization->subscription;

        return [
            'organizationSlug' => $organization->slug,
            'organizationName' => $organization->name,
            'suspended' => (bool) $organization->suspended,
            'store' => $store === null ? null : [
                'name' => $store->name,
                'businessMode' => $store->business_mode,
                // Readable because it is a public identifier, not the pairing
                // secret — see the store discovery migration.
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
            'admins' => ($owners[$organization->id] ?? collect())
                ->filter(fn (OrganizationMembership $membership) => $membership->user !== null)
                ->map(fn (OrganizationMembership $membership) => [
                    'uid' => $membership->user->id,
                    'username' => $membership->user->username,
                    'fullName' => $membership->user->name,
                    'disabled' => $membership->user->status !== 'active',
                ])->values(),
        ];
    }
}
