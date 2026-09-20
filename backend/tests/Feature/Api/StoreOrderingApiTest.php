<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\Product;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * The shop's own open/closed switch. See documentation/merchant-features.md §3.
 */
class StoreOrderingApiTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    private function store(): Store
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail()->stores()->firstOrFail();
    }

    private function staffWithRole(string $role): string
    {
        $user = User::query()->create([
            'name' => 'Counter', 'username' => 'counter', 'password' => 'password', 'status' => 'active',
        ]);

        StoreMembership::query()->create([
            'store_id' => $this->store()->id, 'user_id' => $user->id, 'membership_role' => $role,
        ]);

        return $this->staffToken('counter');
    }

    private function order()
    {
        return $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => Product::query()->where('sku', 'ESP-0001')->firstOrFail()->id, 'quantity' => 1]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'pickup'],
        ]);
    }

    /** The person at the counter in a rush is the one who knows. */
    public function test_a_cashier_can_pause_and_orders_are_refused_with_a_reason(): void
    {
        $this->seed();
        $token = $this->staffWithRole('cashier');

        $this->withToken($token)->putJson('/api/seller/ordering', ['paused' => true])
            ->assertOk()
            ->assertJsonPath('paused', true)
            ->assertJsonPath('resumesAt', null);

        $this->order()
            ->assertUnprocessable()
            ->assertJsonPath('message', "This shop isn't taking orders right now.");

        $this->assertSame(User::query()->where('username', 'counter')->value('id'), $this->store()->ordering_paused_by);
    }

    public function test_a_paused_shop_still_shows_its_menu_and_its_listing(): void
    {
        $this->seed();
        $this->withToken($this->staffToken())->putJson('/api/seller/ordering', ['paused' => true])->assertOk();

        $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')
            ->assertOk()
            ->assertJsonCount(1, 'products')
            ->assertJsonPath('store.ordering.paused', true);

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonPath('stores.0.orgSlug', 'demo-coffee')
            ->assertJsonPath('stores.0.orderingPaused', true);
    }

    /** Nothing has to run at four for a shop that said "back at four" to open at four. */
    public function test_a_pause_ends_by_itself_at_its_resume_time(): void
    {
        $this->seed();
        $this->travelTo(now()->setTimezone('Asia/Manila')->setTime(13, 0));

        $resumesAt = now()->addHours(3);

        $this->withToken($this->staffToken())
            ->putJson('/api/seller/ordering', ['paused' => true, 'resumesAt' => $resumesAt->toIso8601String()])
            ->assertOk()
            ->assertJsonPath('message', "This shop isn't taking orders right now — back at 4:00 PM.");

        $this->order()->assertUnprocessable();

        $this->travelTo($resumesAt->copy()->addMinute());

        $this->withoutToken();
        $this->order()->assertCreated();
    }

    public function test_reopening_clears_the_pause(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->withToken($token)->putJson('/api/seller/ordering', ['paused' => true])->assertOk();
        $this->withToken($token)->putJson('/api/seller/ordering', ['paused' => false])
            ->assertOk()
            ->assertJsonPath('paused', false)
            ->assertJsonPath('message', null);

        $this->assertNull($this->store()->ordering_paused_at);
        $this->withoutToken();
        $this->order()->assertCreated();
    }

    public function test_a_resume_time_in_the_past_or_too_far_ahead_is_refused(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->withToken($token)
            ->putJson('/api/seller/ordering', ['paused' => true, 'resumesAt' => now()->subHour()->toIso8601String()])
            ->assertUnprocessable()
            ->assertJsonPath('errors.resumesAt.0', 'Pick a time that has not already passed.');

        $this->withToken($token)
            ->putJson('/api/seller/ordering', ['paused' => true, 'resumesAt' => now()->addDays(30)->toIso8601String()])
            ->assertUnprocessable();
    }

    public function test_a_role_without_the_orders_page_cannot_pause(): void
    {
        $this->seed();

        $this->withToken($this->staffWithRole('guest'))
            ->putJson('/api/seller/ordering', ['paused' => true])
            ->assertForbidden();
    }

    /** Its storefront is already closed; the switch must not be what it cannot reach. */
    public function test_an_unpaid_shop_can_still_use_the_switch(): void
    {
        $this->seed();
        config(['billing.enforce' => true]);

        $this->withToken($this->staffToken())
            ->putJson('/api/seller/ordering', ['paused' => true])
            ->assertOk();
    }
}
