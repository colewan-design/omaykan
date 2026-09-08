<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

class SyncApiTest extends TestCase
{
    use SignsInStaff, RefreshDatabase;

    public function test_staff_can_sign_in_choose_a_store_and_receive_a_token(): void
    {
        $this->seed();

        $signIn = $this->postJson('/api/staff/sign-in', [
            'identifier' => 'admin',
            'password' => 'password',
        ])->assertOk();

        $response = $this->withToken($signIn->json('token'))
            ->postJson('/api/staff/session-store', [
                'storeId' => $signIn->json('stores.0.id'),
            ]);

        $response
            ->assertOk()
            ->assertJsonStructure([
                'token',
                'user' => ['id', 'fullName', 'roleId'],
                'store' => ['id', 'name', 'code', 'organizationSlug'],
            ])
            ->assertJsonPath('store.code', 'main')
            ->assertJsonPath('user.roleId', 'admin');
    }

    public function test_authenticated_device_can_bootstrap_and_push_orders(): void
    {
        $this->seed();

        $token = $this->staffToken();

        $organization = Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
        $store = Store::query()->where('organization_id', $organization->id)->where('code', 'main')->firstOrFail();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/sync/bootstrap')
            ->assertOk()
            ->assertJsonPath('organization.slug', 'demo-coffee')
            ->assertJsonStructure([
                'catalog' => [
                    'categories',
                    'products',
                    'overrides',
                    'inventoryLevels',
                ],
                'cursor',
            ]);

        $bootstrap = $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/sync/bootstrap')
            ->assertOk()
            ->json();

        $productId = $bootstrap['catalog']['products'][0]['id'];

        $payload = [
            'organizationId' => $organization->id,
            'storeId' => $store->id,
            'events' => [[
                'id' => '11111111-1111-7111-8111-111111111111',
                'entityType' => 'order',
                'entityId' => '22222222-2222-7222-8222-222222222222',
                'operation' => 'upsert',
                'occurredAt' => now()->toIso8601String(),
                'payload' => [
                    'order' => [
                        'id' => '22222222-2222-7222-8222-222222222222',
                        'ticketNumber' => 'TKT-0001',
                        'orderType' => 'takeaway',
                        'paymentStatus' => 'paid',
                        'subtotalCents' => 12000,
                        'taxCents' => 1440,
                        'totalCents' => 13440,
                        'businessDate' => now()->toDateString(),
                        'completedAt' => now()->toIso8601String(),
                    ],
                    'items' => [[
                        'id' => '33333333-3333-7333-8333-333333333333',
                        'productId' => $productId,
                        'productName' => 'Espresso',
                        'quantity' => 1,
                        'unitPriceCents' => 12000,
                        'lineTotalCents' => 12000,
                    ]],
                    'payments' => [[
                        'id' => '44444444-4444-7444-8444-444444444444',
                        'paymentMethod' => 'cash',
                        'amountCents' => 13440,
                        'tenderedCents' => 14000,
                        'changeCents' => 560,
                    ]],
                ],
            ]],
        ];

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson('/api/sync/push', $payload)
            ->assertOk()
            ->assertJsonPath('results.0.status', 'applied');

        $this->assertDatabaseHas('orders', [
            'id' => '22222222-2222-7222-8222-222222222222',
            'ticket_number' => 'TKT-0001',
        ]);

        $this->assertDatabaseHas('inventory_adjustments', [
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            'order_id' => '22222222-2222-7222-8222-222222222222',
            'adjustment_type' => 'sale',
        ]);
    }

    public function test_authenticated_device_can_push_inventory_adjustments(): void
    {
        $this->seed();

        $token = $this->staffToken();

        $organization = Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
        $store = Store::query()->where('organization_id', $organization->id)->where('code', 'main')->firstOrFail();

        $bootstrap = $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/sync/bootstrap')
            ->assertOk()
            ->json();

        $productId = $bootstrap['catalog']['products'][0]['id'];

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson('/api/sync/push', [
                'organizationId' => $organization->id,
                'storeId' => $store->id,
                'events' => [[
                    'id' => '55555555-5555-7555-8555-555555555555',
                    'entityType' => 'inventory_adjustment',
                    'entityId' => '66666666-6666-7666-8666-666666666666',
                    'operation' => 'upsert',
                    'occurredAt' => now()->toIso8601String(),
                    'payload' => [
                        'productId' => $productId,
                        'quantityDelta' => 5,
                        'adjustmentType' => 'restock',
                        'reason' => 'restock',
                    ],
                ]],
            ])
            ->assertOk()
            ->assertJsonPath('results.0.status', 'applied');

        $this->assertDatabaseHas('inventory_adjustments', [
            'id' => '66666666-6666-7666-8666-666666666666',
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            'product_id' => $productId,
            'adjustment_type' => 'restock',
        ]);

        $this->assertDatabaseHas('inventory_levels', [
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            'product_id' => $productId,
        ]);
    }
}
