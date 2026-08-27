<?php

namespace Tests\Feature\Api;

use App\Models\CustomerAccount;
use App\Models\Order;
use App\Models\PlatformAdmin;
use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * The operator's customer list: who shops here, and what have they spent.
 */
class PlatformCustomerApiTest extends TestCase
{
    use RefreshDatabase;

    private string $operatorToken;

    protected function setUp(): void
    {
        parent::setUp();

        $operator = PlatformAdmin::query()->create([
            'name' => 'Platform Operator',
            'email' => 'operator@example.test',
            'password' => 'operator-password-1234',
        ]);

        $this->operatorToken = $operator
            ->createToken('platform-admin', [PlatformAdmin::ABILITY])
            ->plainTextToken;
    }

    private function asOperator(): self
    {
        return $this->withToken($this->operatorToken);
    }

    private function makeCustomer(array $overrides = []): CustomerAccount
    {
        return CustomerAccount::query()->create(array_merge([
            'name' => 'Ana Reyes',
            'email' => 'ana@example.test',
            'phone' => '09171234567',
            'password' => 'shopper-password',
        ], $overrides));
    }

    public function test_sign_in_is_required(): void
    {
        $this->getJson('/api/platform-admin/customers')->assertStatus(401);
    }

    public function test_a_customers_own_token_cannot_read_the_customer_list(): void
    {
        $customer = $this->makeCustomer();
        $token = $customer->createToken('portal', ['customer'])->plainTextToken;

        $this->withToken($token)
            ->getJson('/api/platform-admin/customers')
            ->assertStatus(401);
    }

    public function test_the_list_returns_customers_newest_first(): void
    {
        $this->makeCustomer(['email' => 'older@example.test', 'name' => 'Older']);
        $this->travel(1)->minutes();
        $this->makeCustomer(['email' => 'newer@example.test', 'name' => 'Newer']);

        $response = $this->asOperator()->getJson('/api/platform-admin/customers')->assertOk();

        $response->assertJsonPath('customers.0.name', 'Newer');
        $response->assertJsonPath('customers.1.name', 'Older');
        $response->assertJsonPath('pagination.total', 2);
    }

    public function test_search_matches_name_email_and_phone(): void
    {
        $this->makeCustomer(['name' => 'Ana Reyes', 'email' => 'ana@example.test', 'phone' => '09171111111']);
        $this->makeCustomer(['name' => 'Ben Cruz', 'email' => 'ben@example.test', 'phone' => '09172222222']);

        foreach (['Ana', 'ana@example', '09171111111'] as $term) {
            $this->asOperator()
                ->getJson('/api/platform-admin/customers?q='.urlencode($term))
                ->assertOk()
                ->assertJsonCount(1, 'customers')
                ->assertJsonPath('customers.0.email', 'ana@example.test');
        }

        $this->asOperator()
            ->getJson('/api/platform-admin/customers?q=nobody')
            ->assertOk()
            ->assertJsonCount(0, 'customers');
    }

    /**
     * Spend is the number an operator reads out loud, so an unpaid order must
     * never be counted in it — that would overstate what someone has actually
     * given the business.
     */
    public function test_only_paid_orders_count_toward_spend(): void
    {
        $customer = $this->makeCustomer();
        $store = Store::query()->firstOrFail();

        $this->makeOrder($customer, $store, 'paid', 15000);
        $this->makeOrder($customer, $store, 'unpaid', 99900);

        $response = $this->asOperator()->getJson('/api/platform-admin/customers')->assertOk();

        $response->assertJsonPath('customers.0.ordersCount', 2);
        $response->assertJsonPath('customers.0.totalSpentCents', 15000);
    }

    public function test_the_detail_screen_returns_orders_and_addresses(): void
    {
        $customer = $this->makeCustomer();
        $store = Store::query()->firstOrFail();
        $this->makeOrder($customer, $store, 'paid', 25000);

        $customer->addresses()->create([
            'label' => 'Home',
            'line1' => '12 Session Road',
            'city' => 'Baguio City',
            'is_default' => true,
        ]);

        $response = $this->asOperator()
            ->getJson("/api/platform-admin/customers/{$customer->id}")
            ->assertOk();

        $response->assertJsonPath('customer.email', 'ana@example.test');
        $response->assertJsonPath('customer.totalSpentCents', 25000);
        $response->assertJsonCount(1, 'customer.addresses');
        $response->assertJsonPath('customer.addresses.0.label', 'Home');
        $response->assertJsonCount(1, 'customer.orders');
    }

    /** Nothing here should ever serialise a password hash. */
    public function test_the_password_hash_is_never_returned(): void
    {
        $customer = $this->makeCustomer();

        $list = $this->asOperator()->getJson('/api/platform-admin/customers')->assertOk();
        $detail = $this->asOperator()->getJson("/api/platform-admin/customers/{$customer->id}")->assertOk();

        $this->assertStringNotContainsString('password', $list->getContent());
        $this->assertStringNotContainsString('password', $detail->getContent());
    }

    private function makeOrder(CustomerAccount $customer, Store $store, string $paymentStatus, int $totalCents): Order
    {
        return Order::query()->create([
            'organization_id' => $store->organization_id,
            'store_id' => $store->id,
            'customer_account_id' => $customer->id,
            // Unique per store in the real flow; the counter is irrelevant here,
            // it just cannot be null.
            'ticket_number' => 'T-'.str_pad((string) (Order::query()->count() + 1), 4, '0', STR_PAD_LEFT),
            'channel' => 'online',
            'order_type' => 'takeaway',
            'order_status' => 'pending',
            'payment_status' => $paymentStatus,
            'subtotal_cents' => $totalCents,
            'tax_cents' => 0,
            'total_cents' => $totalCents,
            'business_date' => now()->toDateString(),
        ]);
    }

    protected function setUpTraits(): array
    {
        $uses = parent::setUpTraits();

        // The order tests need a real store; the seeder builds the demo tenant.
        $this->seed();

        return $uses;
    }
}
