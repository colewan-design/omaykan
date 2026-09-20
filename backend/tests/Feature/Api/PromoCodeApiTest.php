<?php

namespace Tests\Feature\Api;

use App\Models\Order;
use App\Models\OrderDiscount;
use App\Models\Organization;
use App\Models\Product;
use App\Models\PromoCode;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Promo and voucher codes, online and at the counter.
 * See documentation/merchant-features.md §8.
 */
class PromoCodeApiTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    private function org(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
    }

    private function code(array $attributes = []): PromoCode
    {
        return PromoCode::query()->create(array_merge([
            'organization_id' => $this->org()->id,
            'code' => 'WELCOME10',
            'kind' => PromoCode::KIND_PERCENT,
            'value' => 1000,
            'channel' => PromoCode::CHANNEL_BOTH,
        ], $attributes));
    }

    /** Two espressos, ₱240 before VAT. */
    private function basket(array $overrides = []): array
    {
        return array_replace_recursive([
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => Product::query()->where('sku', 'ESP-0001')->firstOrFail()->id, 'quantity' => 2]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'pickup'],
        ], $overrides);
    }

    private function cashierToken(): string
    {
        $user = User::query()->create(['name' => 'C', 'username' => 'cashier1', 'password' => 'password', 'status' => 'active']);
        StoreMembership::query()->create([
            'store_id' => $this->org()->stores()->firstOrFail()->id, 'user_id' => $user->id, 'membership_role' => 'cashier',
        ]);

        return $this->staffToken('cashier1');
    }

    // -- Online ---------------------------------------------------------

    public function test_a_code_at_checkout_takes_its_share_off_before_vat(): void
    {
        $this->seed();
        $promo = $this->code();

        $response = $this->postJson('/api/online-orders', $this->basket(['promoCode' => ' welcome10 ']))
            ->assertCreated();

        // 24000 − 2400 = 21600; VAT 12% = 2592; total 24192.
        $response->assertJsonPath('totalCents', 24192);

        $order = Order::query()->findOrFail($response->json('orderId'));
        $this->assertSame(2400, $order->discount_cents);
        $this->assertSame(2592, $order->tax_cents);

        $discount = OrderDiscount::query()->where('order_id', $order->id)->sole();
        $this->assertSame('promo', $discount->kind);
        $this->assertSame($promo->id, $discount->promo_code_id);
        $this->assertSame(1, $promo->redemptionCount());
    }

    public function test_the_quote_prices_the_code_and_explains_one_that_does_not_apply(): void
    {
        $this->seed();
        $this->code();
        $this->code(['code' => 'BIG500', 'kind' => PromoCode::KIND_AMOUNT, 'value' => 5000, 'min_subtotal_cents' => 50000]);

        $this->postJson('/api/online-orders/quote', $this->basket(['promoCode' => 'WELCOME10']))
            ->assertOk()
            ->assertJsonPath('subtotalCents', 24000)
            ->assertJsonPath('discountCents', 2400)
            ->assertJsonPath('taxCents', 2592)
            ->assertJsonPath('totalCents', 24192)
            ->assertJsonPath('promo.ok', true)
            ->assertJsonPath('promo.description', '10% off');

        $this->postJson('/api/online-orders/quote', $this->basket(['promoCode' => 'BIG500']))
            ->assertOk()
            ->assertJsonPath('discountCents', 0)
            ->assertJsonPath('totalCents', 26880)
            ->assertJsonPath('promo.ok', false)
            ->assertJsonPath('promo.message', 'BIG500 needs an order of at least ₱500.00.');
    }

    /** A shopper who typed a code and was charged in full would be right to feel cheated. */
    public function test_a_code_that_does_not_apply_refuses_the_order_rather_than_charging_full_price(): void
    {
        $this->seed();
        $this->code(['ends_at' => now()->subDay()]);

        $this->postJson('/api/online-orders', $this->basket(['promoCode' => 'WELCOME10']))
            ->assertUnprocessable()
            ->assertJsonPath('errors.promoCode.0', 'WELCOME10 has expired.');

        $this->postJson('/api/online-orders', $this->basket(['promoCode' => 'NOPE']))
            ->assertUnprocessable()
            ->assertJsonPath('errors.promoCode.0', "That code isn't valid for this shop.");

        $this->assertSame(0, Order::query()->count());
    }

    public function test_a_counter_only_code_is_refused_online(): void
    {
        $this->seed();
        $this->code(['channel' => PromoCode::CHANNEL_COUNTER]);

        $this->postJson('/api/online-orders', $this->basket(['promoCode' => 'WELCOME10']))
            ->assertUnprocessable()
            ->assertJsonPath('errors.promoCode.0', 'WELCOME10 can only be used at the counter.');
    }

    public function test_the_last_use_goes_once_and_a_guest_is_known_by_phone(): void
    {
        $this->seed();
        $this->code(['max_redemptions' => 1]);
        $this->code(['code' => 'ONCEEACH', 'per_customer_limit' => 1]);

        $this->postJson('/api/online-orders', $this->basket(['promoCode' => 'WELCOME10']))->assertCreated();
        $this->postJson('/api/online-orders', $this->basket(['promoCode' => 'WELCOME10', 'guest' => ['phone' => '09990000000']]))
            ->assertUnprocessable()
            ->assertJsonPath('errors.promoCode.0', 'WELCOME10 has been used up.');

        $this->postJson('/api/online-orders', $this->basket(['promoCode' => 'ONCEEACH']))->assertCreated();
        $this->postJson('/api/online-orders', $this->basket(['promoCode' => 'ONCEEACH']))
            ->assertUnprocessable()
            ->assertJsonPath('errors.promoCode.0', "You've already used ONCEEACH.");
        $this->postJson('/api/online-orders', $this->basket(['promoCode' => 'ONCEEACH', 'guest' => ['phone' => '09990000000']]))
            ->assertCreated();
    }

    public function test_a_percentage_code_can_be_capped(): void
    {
        $this->seed();
        $this->code(['value' => 5000, 'max_discount_cents' => 1000]); // 50% off, up to ₱10

        $this->postJson('/api/online-orders/quote', $this->basket(['promoCode' => 'WELCOME10']))
            ->assertJsonPath('discountCents', 1000)
            ->assertJsonPath('promo.description', '50% off, up to ₱10.00');
    }

    // -- At the counter -------------------------------------------------

    public function test_a_cashier_can_check_a_code_but_not_manage_them(): void
    {
        $this->seed();
        $this->code();
        $token = $this->cashierToken();

        $this->withToken($token)
            ->postJson('/api/seller/promo-codes/check', ['code' => 'welcome10', 'subtotalCents' => 24000])
            ->assertOk()
            ->assertJsonPath('discountCents', 2400)
            ->assertJsonPath('percent', 10);

        $this->withToken($token)->getJson('/api/seller/promo-codes')->assertForbidden();
        $this->withToken($token)
            ->postJson('/api/seller/promo-codes', ['code' => 'MINE', 'kind' => 'percent', 'percent' => 100])
            ->assertForbidden();
    }

    public function test_an_online_only_code_is_refused_at_the_counter(): void
    {
        $this->seed();
        $this->code(['channel' => PromoCode::CHANNEL_ONLINE]);

        $this->withToken($this->staffToken())
            ->postJson('/api/seller/promo-codes/check', ['code' => 'WELCOME10', 'subtotalCents' => 24000])
            ->assertUnprocessable()
            ->assertJsonPath('errors.code.0', 'WELCOME10 can only be used when ordering online.');
    }

    public function test_a_counter_redemption_is_recorded_against_its_code(): void
    {
        $this->seed();
        $promo = $this->code(['max_redemptions' => 1]);
        $token = $this->staffToken();

        $push = function () use ($token, $promo) {
            $orderId = (string) Str::uuid();

            return $this->withToken($token)->postJson('/api/register/orders', [
                'order' => [
                    'id' => $orderId, 'ticketNumber' => 'TKT-'.Str::random(6), 'orderType' => 'takeaway',
                    'subtotalCents' => 24000, 'discountCents' => 2400, 'taxCents' => 2592, 'totalCents' => 24192,
                ],
                'items' => [[
                    'productId' => Product::query()->where('sku', 'ESP-0001')->firstOrFail()->id,
                    'productName' => 'Espresso', 'quantity' => 2, 'unitPriceCents' => 12000, 'lineTotalCents' => 24000,
                ]],
                'discounts' => [['kind' => 'promo', 'promoCodeId' => $promo->id, 'amountCents' => 2400, 'percent' => 10]],
                'payments' => [],
            ]);
        };

        $first = Order::query()->findOrFail($push()->assertCreated()->json('orderId'));
        $this->assertSame('WELCOME10', $first->discounts()->sole()->reason);
        $this->assertSame(1, $promo->redemptionCount());

        // Used up between the till's check and this sale: refused, with the
        // reason, before the cashier completes it.
        $push()
            ->assertUnprocessable()
            ->assertJsonPath('errors.order.0', 'WELCOME10 has been used up.');
        $this->assertSame(1, $promo->redemptionCount());
    }

    // -- Managing -------------------------------------------------------

    public function test_a_manager_creates_lists_edits_and_retires_codes(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $id = $this->withToken($token)
            ->postJson('/api/seller/promo-codes', [
                'code' => 'rainy-day', 'kind' => 'amount', 'amountCents' => 5000,
                'minSubtotalCents' => 20000, 'channel' => 'online', 'maxRedemptions' => 50,
            ])
            ->assertCreated()
            ->assertJsonPath('promoCode.code', 'RAINY-DAY')
            ->assertJsonPath('promoCode.description', '₱50.00 off')
            ->json('promoCode.id');

        $this->withToken($token)->getJson('/api/seller/promo-codes')
            ->assertOk()
            ->assertJsonPath('promoCodes.0.code', 'RAINY-DAY')
            ->assertJsonPath('promoCodes.0.redemptions', 0);

        $this->withToken($token)->patchJson("/api/seller/promo-codes/{$id}", ['isActive' => false])
            ->assertOk()
            ->assertJsonPath('promoCode.isActive', false);

        $this->withToken($token)->patchJson("/api/seller/promo-codes/{$id}", ['code' => 'OTHER'])
            ->assertUnprocessable();

        $this->withToken($token)->deleteJson("/api/seller/promo-codes/{$id}")->assertOk();

        // A retired code's name is not handed out again.
        $this->withToken($token)
            ->postJson('/api/seller/promo-codes', ['code' => 'RAINY-DAY', 'kind' => 'percent', 'percent' => 20])
            ->assertUnprocessable()
            ->assertJsonPath('errors.code.0', 'RAINY-DAY has been used before. Pick another code.');
    }

    public function test_one_shop_cannot_touch_anothers_codes(): void
    {
        $this->seed();
        $promo = $this->code();

        $this->withToken($this->staffTokenForNewTenant())
            ->patchJson("/api/seller/promo-codes/{$promo->id}", ['isActive' => false])
            ->assertNotFound();

        $this->assertTrue($promo->fresh()->is_active);
    }
}
