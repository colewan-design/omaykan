<?php

namespace App\Services;

use App\Models\Store;
use App\Models\User;

/**
 * Which shop the caller is acting for, and as whom.
 *
 * This is what replaced the `Device` the merchant API used to run on. A device
 * carried its store on its own row, so every controller could say
 * `$device->store_id` and be done; a person can belong to several stores, so
 * the store has to come from the *session* rather than from the identity.
 *
 * An instance existing is the statement that all three checks passed: the token
 * names a store, the store is live, and the user still has a membership that
 * reaches it. See StoreContextResolver, which is the only thing that builds one.
 */
final readonly class StoreContext
{
    public function __construct(
        public User $user,
        public Store $store,
        /** The user's role at this store — 'admin', 'manager', 'cashier'. */
        public string $role,
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
}
