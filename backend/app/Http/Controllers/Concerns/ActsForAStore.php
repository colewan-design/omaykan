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
}
