<?php

namespace App\Services;

use App\Models\Store;
use App\Models\User;
use App\Services\Billing\TenantAccess;
use App\Services\Billing\TenantAccessDenied;

/**
 * Which shop the caller is acting for, and as whom.
 *
 * This is what replaced the `Device` the merchant API used to run on. A device
 * carried its store on its own row, so every controller could say
 * `$device->store_id` and be done; a person can belong to several stores, so
 * the store has to come from the *session* rather than from the identity.
 *
 * An instance existing is the statement that four checks passed: the token
 * names a store, the store is live, the user still has a membership that
 * reaches it, and the organization is not suspended. See StoreContextResolver,
 * which is the only thing that builds one.
 *
 * It does **not** say the organization may write. An unpaid tenant resolves —
 * its staff can still read their own records — and carries that fact in
 * `$access` for the actions that change something to check. See `canWrite`.
 */
final readonly class StoreContext
{
    public function __construct(
        public User $user,
        public Store $store,
        /** The user's role at this store — 'admin', 'manager', 'cashier'. */
        public string $role,
        /** Never Suspended: the resolver refuses those before building one. */
        public TenantAccess $access = TenantAccess::Allowed,
    ) {}

    public function storeId(): string
    {
        return $this->store->id;
    }

    public function organizationId(): string
    {
        return $this->store->organization_id;
    }

    /**
     * The roles allowed to change what the shop sells, who works there, and
     * what the money did — as opposed to serving a customer.
     */
    public function isManager(): bool
    {
        return in_array($this->role, ['admin', 'manager'], true);
    }

    /**
     * Whether this person's role may use a page of the back office — the same
     * question the till asks before showing it. See RolePermissions.
     *
     * Not memoised: it is asked once or twice per request, and a stale answer
     * after an owner edits a role is worse than one more indexed read.
     */
    public function can(string $page): bool
    {
        return app(RolePermissions::class)->allows($this->organizationId(), $this->role, $page);
    }

    /**
     * Whether this session may change anything.
     *
     * False for an unpaid tenant, whose staff keep read access to their own
     * shop and lose the ability to ring up, restock or reorganize it. Every
     * action that writes asks through `abortUnlessWritable` rather than here
     * directly, so the refusal is the same body everywhere.
     */
    public function canWrite(): bool
    {
        return $this->access->allowsWrites();
    }

    /**
     * Refuse a write from a tenant that may not make one — the same 403 body
     * the resolver sends for a suspension, so a client handles both in one
     * place.
     *
     * @throws TenantAccessDenied
     */
    public function abortUnlessWritable(): void
    {
        if (! $this->canWrite()) {
            throw TenantAccessDenied::for($this->access);
        }
    }
}
