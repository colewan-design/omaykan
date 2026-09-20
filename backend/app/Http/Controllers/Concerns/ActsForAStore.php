<?php

namespace App\Http\Controllers\Concerns;

use App\Services\StoreContext;
use App\Services\StoreContextResolver;
use Illuminate\Http\Request;

/**
 * The merchant API's "which shop is this?" — one line in every controller that
 * used to start by unwrapping a Device.
 *
 * Memoised because several actions ask twice: once to scope the query, once to
 * authorize the row it found. The cache hangs off the *request*, not off `$this`
 * — a controller instance is cached on its Route and survives between requests
 * in a long-lived process, so a property here would answer the next request
 * with the last one's shop. That is a cross-tenant read, and it is silent.
 */
trait ActsForAStore
{
    private const CONTEXT_KEY = 'omaykan.store_context';

    protected function storeContext(Request $request): StoreContext
    {
        $cached = $request->attributes->get(self::CONTEXT_KEY);

        if ($cached instanceof StoreContext) {
            return $cached;
        }

        $context = app(StoreContextResolver::class)->resolve($request);
        $request->attributes->set(self::CONTEXT_KEY, $context);

        return $context;
    }

    /**
     * The same, for an action that *starts* something — a sale, a shift, a
     * catalog change, a new member of staff — and so is refused for a tenant
     * whose subscription has lapsed.
     *
     * The rule for an unpaid shop is "finish, don't start". Progressing an
     * order a customer has already placed stays on `storeContext`, because
     * refusing it strands somebody's dinner halfway to their door; the
     * storefront is already closed, so no new ones arrive to be progressed.
     *
     * `grep writableStoreContext` is the list of what an unpaid shop cannot do.
     * A new action that changes the shop's own records belongs on it.
     */
    protected function writableStoreContext(Request $request): StoreContext
    {
        $context = $this->storeContext($request);
        $context->abortUnlessWritable();

        return $context;
    }
}
