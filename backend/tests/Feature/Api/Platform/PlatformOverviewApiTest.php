<?php

namespace Tests\Feature\Api\Platform;

use App\Models\Rider;
use App\Models\Subscription;

/**
 * The dashboard.
 *
 * What is asserted is that the numbers answer the operator's three questions —
 * what is waiting on me, is anything stuck, is the platform growing — and that
 * they are read from the live tables rather than a cache that can go stale on
 * a work queue.
 */
class PlatformOverviewApiTest extends PlatformTestCase
{
    public function test_the_queues_count_and_name_what_is_waiting(): void
    {
        $first = $this->signUpTenant();
        $this->signUpTenant(['businessName' => 'Session Road Grocery', 'username' => 'grocer']);

        Rider::query()->create([
            'name' => 'Jun Dela Cruz',
            'email' => 'jun@example.test',
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/license.jpg',
            'plate_image_path' => 'rider-documents/plate.jpg',
            'status' => Rider::STATUS_PENDING,
        ]);

        $token = $this->tokenFor($this->operator());

        $response = $this->getJson('/api/platform/overview', $this->authHeader($token))->assertOk();

        $response->assertJsonPath('queues.pendingSignups', 2)
            ->assertJsonPath('queues.pendingRiders', 1)
            ->assertJsonPath('tenants.total', 2)
            ->assertJsonPath('tenants.suspended', 0)
            ->assertJsonPath('tenants.newLast7Days', 2);

        // Oldest first, so nobody waits behind a later signup.
        $response->assertJsonPath('queues.oldestSignups.0.organizationSlug', $first['organizationSlug'])
            ->assertJsonPath('queues.oldestSignups.0.waitingDays', 0)
            ->assertJsonPath('queues.oldestSignups.0.gcashReference', 'GC-99881');
    }

    public function test_verified_subscriptions_leave_the_queue_and_land_in_revenue(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());

        $amount = Subscription::query()->firstOrFail()->amount_cents;

        $this->postJson(
            "/api/platform/organizations/{$slug}/subscription",
            ['status' => Subscription::STATUS_ACTIVE],
            $this->authHeader($token),
        )->assertOk();

        $this->getJson('/api/platform/overview', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('queues.pendingSignups', 0)
            ->assertJsonPath('revenue.activeSubscriptions', 1)
            ->assertJsonPath('revenue.monthlyCents', $amount);
    }

    public function test_suspending_a_tenant_shows_up_in_the_counts(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());

        $this->postJson(
            "/api/platform/organizations/{$slug}/suspension",
            ['suspended' => true],
            $this->authHeader($token),
        )->assertOk();

        $this->getJson('/api/platform/overview', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('tenants.total', 1)
            ->assertJsonPath('tenants.suspended', 1);
    }

    /** An empty platform must render, not divide by zero. */
    public function test_an_empty_platform_returns_zeroes(): void
    {
        $token = $this->tokenFor($this->operator());

        $this->getJson('/api/platform/overview', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('queues.pendingSignups', 0)
            ->assertJsonPath('queues.oldestSignups', [])
            ->assertJsonPath('tenants.total', 0)
            ->assertJsonPath('revenue.monthlyCents', 0)
            ->assertJsonPath('volume.orders', 0)
            ->assertJsonPath('volume.byDay', [])
            ->assertJsonPath('topTenants', []);
    }
}
