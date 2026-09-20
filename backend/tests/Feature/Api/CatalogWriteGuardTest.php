<?php

namespace Tests\Feature\Api;

use App\Models\InventoryLevel;
use App\Models\Organization;
use App\Models\PosRole;
use App\Models\Product;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * `POST /sync/push` used to apply catalog and stock events for anyone signed
 * in. The till and the seller app hid the controls from cashiers; the server
 * did not care. See documentation/merchant-features.md §2.
 */
class CatalogWriteGuardTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    private function demoCoffee(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
    }

    private function store(): Store
    {
        return $this->demoCoffee()->stores()->firstOrFail();
    }

    private function espresso(): Product
    {
        return Product::query()->where('sku', 'ESP-0001')->firstOrFail();
    }

    /** A second member of staff at the seeded shop, with the role given. */
    private function staffWithRole(string $role, string $username = 'counter'): string
    {
        $user = User::query()->create([
            'name' => ucfirst($username),
            'username' => $username,
            'password' => 'password',
            'status' => 'active',
        ]);

        StoreMembership::query()->create([
            'store_id' => $this->store()->id,
            'user_id' => $user->id,
            'membership_role' => $role,
        ]);

        return $this->staffToken($username);
    }

    private function event(string $entityType, array $payload, ?string $entityId = null): array
    {
        return [
            'id' => (string) Str::uuid(),
            'entityType' => $entityType,
            'entityId' => $entityId ?? (string) Str::uuid(),
            'operation' => 'upsert',
            'occurredAt' => now()->toIso8601String(),
            'payload' => $payload,
        ];
    }

    private function priceChange(int $priceCents): array
    {
        $product = $this->espresso();

        return $this->event('product', [
            'name' => $product->name,
            'categoryId' => $product->category_id,
            'sku' => $product->sku,
            'priceCents' => $priceCents,
        ], $product->id);
    }

    private function sale(): array
    {
        $orderId = (string) Str::uuid();

        return $this->event('order', [
            'order' => [
                'id' => $orderId,
                'ticketNumber' => 'TKT-'.Str::random(6),
                'orderType' => 'takeaway',
                'subtotalCents' => 12000,
                'taxCents' => 1440,
                'totalCents' => 13440,
            ],
            'items' => [[
                'productId' => $this->espresso()->id,
                'productName' => 'Espresso',
                'quantity' => 1,
                'unitPriceCents' => 12000,
                'lineTotalCents' => 12000,
            ]],
            'payments' => [],
        ], $orderId);
    }

    private function push(string $token, array $events)
    {
        return $this->withToken($token)->postJson('/api/sync/push', [
            'organizationId' => $this->demoCoffee()->id,
            'storeId' => $this->store()->id,
            'events' => $events,
        ])->assertOk();
    }

    public function test_a_cashier_can_ring_up_a_sale_but_not_reprice_the_catalog(): void
    {
        $this->seed();
        $token = $this->staffWithRole('cashier');

        $this->withToken($token)->postJson('/api/register/orders', $this->sale()['payload'])->assertCreated();

        $this->push($token, [$this->priceChange(1)])
            ->assertJsonPath('results.0.status', 'rejected')
            ->assertJsonPath('results.0.message', 'Your role cannot change products. Ask a manager to make this change.');

        $this->assertSame(12000, $this->espresso()->price_cents);
    }

    public function test_a_cashier_cannot_add_a_category(): void
    {
        $this->seed();
        $token = $this->staffWithRole('cashier');

        $this->push($token, [$this->event('category', ['name' => 'Contraband'])])
            ->assertJsonPath('results.0.status', 'rejected');

        $this->assertDatabaseMissing('categories', ['name' => 'Contraband']);
    }

    public function test_a_cashier_cannot_restock_but_a_sale_still_moves_stock(): void
    {
        $this->seed();
        $token = $this->staffWithRole('cashier');
        $productId = $this->espresso()->id;
        $level = fn () => (float) InventoryLevel::query()->where('product_id', $productId)->value('qty_on_hand');
        $before = $level();

        $this->push($token, [
            $this->event('inventory_adjustment', [
                'productId' => $productId, 'quantityDelta' => 50, 'adjustmentType' => 'restock',
            ]),
        ])
            ->assertJsonPath('results.0.status', 'rejected')
            ->assertJsonPath('results.0.message', 'Your role cannot adjust stock. Ask a manager to make this change.');

        // The sale moves its own stock on the server.
        $this->withToken($token)->postJson('/api/register/orders', $this->sale()['payload'])->assertCreated();

        $this->assertSame($before - 1, $level());
    }

    public function test_a_manager_may_change_the_catalog(): void
    {
        $this->seed();
        $token = $this->staffWithRole('manager');

        $this->push($token, [$this->priceChange(15000)])->assertJsonPath('results.0.status', 'applied');

        $this->assertSame(15000, $this->espresso()->price_cents);
    }

    /** The owner's own roles win over any notion of rank. */
    public function test_a_custom_role_given_the_products_page_may_change_the_catalog(): void
    {
        $this->seed();

        PosRole::query()->create([
            'organization_id' => $this->demoCoffee()->id,
            'role_key' => 'head-cashier',
            'name' => 'Head cashier',
            'permissions' => ['register' => true, 'products' => true],
        ]);

        $token = $this->staffWithRole('head-cashier');

        $this->push($token, [$this->priceChange(13000)])->assertJsonPath('results.0.status', 'applied');
    }

    public function test_an_owner_can_take_the_products_page_away_from_managers(): void
    {
        $this->seed();

        PosRole::query()
            ->where('organization_id', $this->demoCoffee()->id)
            ->where('role_key', 'manager')
            ->update(['permissions' => json_encode(['register' => true, 'products' => false])]);

        $token = $this->staffWithRole('manager');

        $this->push($token, [$this->priceChange(1)])->assertJsonPath('results.0.status', 'rejected');
    }

    /** Signup creates no role rows; the built-in list stands in for them. */
    public function test_a_shop_with_no_saved_roles_falls_back_to_the_built_in_ones(): void
    {
        $this->seed();
        PosRole::query()->where('organization_id', $this->demoCoffee()->id)->delete();

        $this->push($this->staffWithRole('cashier', 'cash1'), [$this->priceChange(1)])
            ->assertJsonPath('results.0.status', 'rejected');

        $this->push($this->staffWithRole('manager', 'mgr1'), [$this->priceChange(14000)])
            ->assertJsonPath('results.0.status', 'applied');
    }

    public function test_the_owner_is_never_refused(): void
    {
        $this->seed();
        PosRole::query()->where('organization_id', $this->demoCoffee()->id)->delete();

        $this->push($this->staffToken(), [$this->priceChange(16000)])
            ->assertJsonPath('results.0.status', 'applied');
    }
}
