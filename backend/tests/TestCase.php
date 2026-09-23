<?php

namespace Tests;

use Illuminate\Foundation\Testing\TestCase as BaseTestCase;
use Illuminate\Testing\TestResponse;

abstract class TestCase extends BaseTestCase
{
    /**
     * Every request in a test starts with no identity already resolved.
     *
     * A real request gets a fresh auth guard; a test makes all of its requests
     * inside one process, and the guard caches the identity it last resolved.
     * Without this, a test that signs in as one person and then presents a
     * different token is answered as the first — which quietly turns an
     * assertion about authorization into an assertion about nothing.
     *
     * It mattered much less when merchant clients paired: pairing was a single
     * unauthenticated call, so nothing was cached before the token was used.
     */
    public function json($method, $uri, array $data = [], array $headers = [], $options = 0): TestResponse
    {
        $this->app['auth']->forgetGuards();

        return parent::json($method, $uri, $data, $headers, $options);
    }
}
