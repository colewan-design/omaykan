<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\Product;
use App\Models\Store;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

class ShiftApiTest extends TestCase
{
    use SignsInStaff, RefreshDatabase;

    public function test_signed_in_staff_can_open_update_and_close_a_shift(): void
    {
        $this->seed();

        $token = $this->staffToken();

        $organization = Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
        $store = Store::query()
            ->where('organization_id', $organization->id)
            ->where('code', 'main')
            ->firstOrFail();
        $admin = User::query()->where('username', 'admin')->firstOrFail();
        $product = Product::query()->where('organization_id', $organization->id)->firstOrFail();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson('/api/shifts/open', [
                'openingCashCents' => 50000,
                'userId' => $admin->id,
            ])
            ->assertOk()
            ->assertJsonPath('shift.openingCashCents', 50000)
            ->assertJsonPath('shift.cashSalesCents', 0);

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson('/api/sync/push', [
                'organizationId' => $organization->id,
                'storeId' => $store->id,
                'events' => [[
                    'id' => '10101010-1111-7111-8111-111111111111',
                    'entityType' => 'order',
                    'entityId' => '20202020-2222-7222-8222-222222222222',
                    'operation' => 'upsert',
                    'occurredAt' => now()->toIso8601String(),
                    'payload' => [
                        'order' => [
                            'id' => '20202020-2222-7222-8222-222222222222',
                            'ticketNumber' => 'SHIFT-001',
                            'orderType' => 'takeaway',
                            'paymentStatus' => 'paid',
                            'subtotalCents' => 12000,
                            'taxCents' => 1440,
                            'totalCents' => 13440,
                            'businessDate' => now()->toDateString(),
                            'completedAt' => now()->toIso8601String(),
                        ],
                        'items' => [[
                            'id' => '30303030-3333-7333-8333-333333333333',
                            'productId' => $product->id,
                            'productName' => $product->name,
                            'quantity' => 1,
                            'unitPriceCents' => 12000,
                            'lineTotalCents' => 12000,
                        ]],
                        'payments' => [[
                            'id' => '40404040-4444-7444-8444-444444444444',
                            'paymentMethod' => 'cash',
                            'amountCents' => 13440,
                            'tenderedCents' => 14000,
                            'changeCents' => 560,
                        ]],
                    ],
                ]],
            ])
            ->assertOk()
            ->assertJsonPath('results.0.status', 'applied');

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson('/api/shifts/current/movements', [
                'movementType' => 'pay_out',
                'amountCents' => 5000,
                'reason' => 'Petty cash',
                'userId' => $admin->id,
            ])
            ->assertOk()
            ->assertJsonPath('shift.payOutsCents', 5000)
            ->assertJsonPath('shift.cashSalesCents', 13440)
            ->assertJsonPath('shift.totalSalesCents', 13440)
            ->assertJsonPath('shift.orderCount', 1)
            ->assertJsonPath('shift.expectedCashCents', 58440);

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson('/api/shifts/current/close', [
                'countedCashCents' => 58000,
                'userId' => $admin->id,
            ])
            ->assertOk()
            ->assertJsonPath('shift.closingCashCents', 58000)
            ->assertJsonPath('shift.expectedCashCents', 58440)
            ->assertJsonPath('shift.varianceCashCents', -440);

        $this->assertDatabaseHas('shifts', [
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            'opening_cash_cents' => 50000,
            'closing_cash_cents' => 58000,
            'expected_cash_cents' => 58440,
            'variance_cash_cents' => -440,
        ]);

        $this->assertDatabaseHas('cash_movements', [
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            'movement_type' => 'pay_out',
            'amount_cents' => 5000,
            'reason' => 'Petty cash',
        ]);

        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/shifts/history')
            ->assertOk()
            ->assertJsonCount(1, 'shifts')
            ->assertJsonPath('shifts.0.closingCashCents', 58000)
            ->assertJsonPath('shifts.0.varianceCashCents', -440)
            ->assertJsonPath('shifts.0.totalSalesCents', 13440);
    }
}
