<?php

namespace Tests\Feature\Api;

use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Covers the replacements for api/resolve-store-code.ts and
 * api/resolve-staff-store-code.ts.
 */
class StoreCodeApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_customer_lookup_returns_everything_the_storefront_renders(): void
    {
        $this->seed();

        $this->postJson('/api/store-codes/resolve', ['code' => '123456'])
            ->assertOk()
            ->assertJson([
                'orgSlug' => 'demo-coffee',
                'storeCode' => 'main',
                'businessMode' => 'coffee-shop',
                'storeName' => 'Main Branch',
                'storeAddress' => '12 Session Road, Baguio City',
            ])
            ->assertJsonPath('storeLat', 16.4123)
            ->assertJsonPath('storeLng', 120.596);
    }

    public function test_lookup_ignores_case_and_surrounding_whitespace(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();
        $store->setPairingCode('ab12cd');
        $store->save();

        $this->postJson('/api/store-codes/resolve', ['code' => '  Ab12Cd '])
            ->assertOk()
            ->assertJsonPath('storeCode', 'main');
    }

    public function test_unknown_code_is_a_404(): void
    {
        $this->seed();

        $this->postJson('/api/store-codes/resolve', ['code' => 'NOPE99'])
            ->assertNotFound();
    }

    public function test_code_is_required(): void
    {
        $this->seed();

        $this->postJson('/api/store-codes/resolve', [])
            ->assertStatus(422)
            ->assertJsonValidationErrors('code');
    }

    public function test_store_that_cannot_sell_online_is_rejected_for_customers(): void
    {
        $this->seed();

        Store::query()->firstOrFail()->forceFill(['business_mode' => 'nail-salon'])->save();

        $this->postJson('/api/store-codes/resolve', ['code' => '123456'])
            ->assertStatus(409);
    }

    public function test_archived_store_is_not_discoverable(): void
    {
        $this->seed();

        Store::query()->firstOrFail()->forceFill(['status' => 'archived'])->save();

        $this->postJson('/api/store-codes/resolve', ['code' => '123456'])
            ->assertNotFound();
    }

    public function test_staff_lookup_allows_a_store_that_cannot_sell_online(): void
    {
        $this->seed();

        // A nail salon owner still has to bind their browser to their org.
        Store::query()->firstOrFail()->forceFill(['business_mode' => 'nail-salon'])->save();

        $this->postJson('/api/store-codes/resolve-staff', ['code' => '123456'])
            ->assertOk()
            ->assertExactJson([
                'organizationSlug' => 'demo-coffee',
                'storeCode' => 'main',
            ]);
    }

    public function test_neither_lookup_leaks_the_pairing_secret(): void
    {
        $this->seed();

        $customer = $this->postJson('/api/store-codes/resolve', ['code' => '123456'])->assertOk();
        $staff = $this->postJson('/api/store-codes/resolve-staff', ['code' => '123456'])->assertOk();

        foreach ([$customer, $staff] as $response) {
            $body = $response->getContent();
            $this->assertStringNotContainsString('pairing_code_hash', $body);
            $this->assertStringNotContainsString('$2y$', $body);
        }
    }

    public function test_pairing_secret_can_be_rotated_without_changing_the_public_code(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();
        $store->rotatePairingCode('NEWSECRET');
        $store->save();

        // Customers keep using the code they were already given...
        $this->postJson('/api/store-codes/resolve', ['code' => '123456'])->assertOk();

        // ...but the old code no longer pairs a till.
        $pair = fn (string $code) => $this->postJson('/api/device-sessions', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'pairingCode' => $code,
            'deviceName' => 'Counter 1',
            'platform' => 'web',
            'appVersion' => '0.1.0',
        ]);

        $pair('123456')->assertStatus(422);
        $pair('NEWSECRET')->assertOk();
    }

    public function test_setting_a_code_keeps_pairing_and_discovery_in_step(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();
        $store->setPairingCode('ZZ9900');
        $store->save();

        // Discovery finds it...
        $this->postJson('/api/store-codes/resolve', ['code' => 'ZZ9900'])->assertOk();

        // ...and a till can still pair with the same code.
        $this->postJson('/api/device-sessions', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'pairingCode' => 'zz9900',
            'deviceName' => 'Counter 1',
            'platform' => 'web',
            'appVersion' => '0.1.0',
        ])->assertOk();
    }
}
