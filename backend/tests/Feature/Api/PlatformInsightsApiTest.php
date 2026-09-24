<?php

namespace Tests\Feature\Api;

use App\Models\Category;
use App\Models\CustomerAccount;
use App\Models\InventoryLevel;
use App\Models\Order;
use App\Models\OrderItem;
use App\Models\PlatformAdmin;
use App\Models\Product;
use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * The operator's view of the marketplace as a whole: the dashboard, the order
 * table, the catalog, the analytics window and the marketplace's own record.
 */
class PlatformInsightsApiTest extends TestCase
{
    use RefreshDatabase;

    private string $operatorToken;

    private Store $store;

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

        $this->store = Store::query()->firstOrFail();
    }

    private function asOperator(): self
    {
        return $this->withToken($this->operatorToken);
    }

    // ── The guard ────────────────────────────────────────────────────────

    public function test_every_screen_requires_an_operator(): void
    {
        foreach ([
            '/api/platform-admin/overview',
            '/api/platform-admin/orders',
            '/api/platform-admin/products',
            '/api/platform-admin/analytics',
            '/api/platform-admin/reports',
            '/api/platform-admin/settings',
        ] as $path) {
            $this->getJson($path)->assertStatus(401);
        }
    }

    public function test_a_shoppers_token_cannot_read_the_marketplace(): void
    {
        $customer = CustomerAccount::query()->create([
            'name' => 'Ana Reyes',
            'email' => 'ana@example.test',
            'password' => 'shopper-password',
        ]);

        $this->withToken($customer->createToken('portal', ['customer'])->plainTextToken)
            ->getJson('/api/platform-admin/overview')
            ->assertStatus(401);
    }

    // ── The dashboard ────────────────────────────────────────────────────

    /**
     * The headline is the number an operator repeats to other people. An
     * unpaid order is a promise, and counting promises as takings is the one
     * mistake that makes every other number on the screen suspect.
     */
    public function test_only_paid_orders_count_as_sales(): void
    {
        $this->makeOrder(paymentStatus: 'paid', totalCents: 25000);
        $this->makeOrder(paymentStatus: 'unpaid', totalCents: 99900);

        $response = $this->asOperator()->getJson('/api/platform-admin/overview')->assertOk();

        $response->assertJsonPath('headline.salesCents', 25000);
        // Volume counts both: two orders were placed, whatever was settled.
        $response->assertJsonPath('headline.orders', 2);
    }

    public function test_a_voided_order_is_not_revenue(): void
    {
        $order = $this->makeOrder(paymentStatus: 'paid', totalCents: 50000);
        $this->makeOrder(paymentStatus: 'paid', totalCents: 10000);
        $order->delete();

        $this->asOperator()->getJson('/api/platform-admin/overview')->assertOk()
            ->assertJsonPath('headline.salesCents', 10000);
    }

    /**
     * A chart drawn only from the days that had orders misrepresents the shape
     * of the business — four points spread across a month read as steady
     * trade. Every day in the window must be present, including the zeroes.
     */
    public function test_the_series_has_a_point_for_every_day_including_empty_ones(): void
    {
        $this->makeOrder(paymentStatus: 'paid', totalCents: 1000);

        $response = $this->asOperator()->getJson('/api/platform-admin/overview')->assertOk();

        $response->assertJsonCount(30, 'series');
        $response->assertJsonPath('series.29.salesCents', 1000);
        $response->assertJsonPath('series.0.salesCents', 0);
    }

    public function test_the_dashboard_accepts_only_supported_date_ranges(): void
    {
        $this->asOperator()->getJson('/api/platform-admin/overview?days=7')->assertOk()
            ->assertJsonPath('window.days', 7)
            ->assertJsonCount(7, 'series');

        $this->asOperator()->getJson('/api/platform-admin/overview?days=90')->assertOk()
            ->assertJsonPath('window.days', 90)
            ->assertJsonCount(90, 'series');

        $this->asOperator()->getJson('/api/platform-admin/overview?days=14')->assertUnprocessable();
    }

    public function test_no_change_percentage_is_reported_when_there_is_nothing_to_compare_with(): void
    {
        $this->makeOrder(paymentStatus: 'paid', totalCents: 1000);

        $this->asOperator()->getJson('/api/platform-admin/overview')->assertOk()
            ->assertJsonPath('headline.salesChangePercent', null);
    }

    // ── Where an order has got to ────────────────────────────────────────

    /**
     * The four stages have to partition the table: every order lands in
     * exactly one, or the tabs do not add up to the total and an operator
     * starts counting rows by hand.
     */
    public function test_the_four_stages_account_for_every_order_exactly_once(): void
    {
        $this->makeOrder(orderStatus: 'preparing');
        $this->makeOrder(deliveryStage: 'picked_up');
        $this->makeOrder(orderStatus: 'served');
        $this->makeOrder(deliveryStage: 'delivered');
        $this->makeOrder()->delete();

        $counts = $this->asOperator()->getJson('/api/platform-admin/overview')->assertOk()
            ->json('byProgress');

        $this->assertSame(1, $counts['processing']);
        $this->assertSame(1, $counts['shipped']);
        $this->assertSame(2, $counts['completed']);
        $this->assertSame(1, $counts['cancelled']);
        $this->assertSame(5, array_sum($counts));
    }

    /**
     * Voiding does not rewind the delivery columns, so an order cancelled
     * after a rider collected it still says `picked_up`. It is cancelled.
     */
    public function test_a_voided_order_reads_as_cancelled_whatever_else_it_says(): void
    {
        $this->makeOrder(deliveryStage: 'picked_up')->delete();

        $counts = $this->asOperator()->getJson('/api/platform-admin/overview')->assertOk()
            ->json('byProgress');

        $this->assertSame(0, $counts['shipped']);
        $this->assertSame(1, $counts['cancelled']);
    }

    // ── The order table ──────────────────────────────────────────────────

    public function test_the_order_list_filters_by_stage(): void
    {
        $this->makeOrder(orderStatus: 'preparing', ticket: 'T-0001');
        $this->makeOrder(orderStatus: 'served', ticket: 'T-0002');

        $this->asOperator()->getJson('/api/platform-admin/orders?progress=completed')->assertOk()
            ->assertJsonCount(1, 'orders')
            ->assertJsonPath('orders.0.ticketNumber', 'T-0002');
    }

    /**
     * "All orders" that quietly drops the cancelled ones is a total an
     * operator will later fail to reconcile against a shop's own.
     */
    public function test_cancelled_orders_are_in_the_unfiltered_list(): void
    {
        $this->makeOrder(ticket: 'T-0001')->delete();

        $this->asOperator()->getJson('/api/platform-admin/orders')->assertOk()
            ->assertJsonCount(1, 'orders')
            ->assertJsonPath('orders.0.progress', 'cancelled');
    }

    public function test_the_order_search_matches_ticket_customer_and_shop(): void
    {
        $customer = CustomerAccount::query()->create([
            'name' => 'Maria Santos',
            'email' => 'maria@example.test',
            'password' => 'shopper-password',
        ]);

        $this->makeOrder(ticket: 'T-1001', customer: $customer);
        $this->makeOrder(ticket: 'T-2002');

        foreach (['T-1001', 'Maria'] as $term) {
            $this->asOperator()
                ->getJson('/api/platform-admin/orders?q='.urlencode($term))
                ->assertOk()
                ->assertJsonCount(1, 'orders')
                ->assertJsonPath('orders.0.ticketNumber', 'T-1001');
        }

        // The shop's name matches both, since both were placed with it.
        $this->asOperator()
            ->getJson('/api/platform-admin/orders?q='.urlencode($this->store->name))
            ->assertOk()
            ->assertJsonCount(2, 'orders');
    }

    /**
     * A tab count computed over the filtered query would read "Cancelled (0)"
     * while you are searching for something else, which is a tab that lies.
     */
    public function test_tab_counts_ignore_the_current_search(): void
    {
        $this->makeOrder(ticket: 'T-0001')->delete();
        $this->makeOrder(ticket: 'T-0002');

        $response = $this->asOperator()->getJson('/api/platform-admin/orders?q=T-0002')->assertOk();

        $response->assertJsonCount(1, 'orders');
        $response->assertJsonPath('counts.cancelled', 1);
    }

    /**
     * A tab has to promise what clicking it will show. Counting all time under
     * a list filtered to a month puts "All orders 206" above "Showing 1-25 of
     * 108", which reads as a bug even though both numbers are true.
     */
    public function test_tab_counts_follow_the_date_range(): void
    {
        $recent = $this->makeOrder(ticket: 'T-0001');
        $old = $this->makeOrder(ticket: 'T-0002');
        $old->forceFill(['created_at' => now()->subDays(60)])->save();

        $this->asOperator()->getJson('/api/platform-admin/orders?days=30')->assertOk()
            ->assertJsonCount(1, 'orders')
            ->assertJsonPath('counts.processing', 1);

        $this->asOperator()->getJson('/api/platform-admin/orders?days=0')->assertOk()
            ->assertJsonCount(2, 'orders')
            ->assertJsonPath('counts.processing', 2);

        $this->assertNotNull($recent->id);
    }

    public function test_a_guest_order_is_named_from_its_contact_details(): void
    {
        $this->makeOrder(ticket: 'T-0001', guest: ['name' => 'Paolo Dizon', 'phone' => '09170000000']);

        $this->asOperator()->getJson('/api/platform-admin/orders')->assertOk()
            ->assertJsonPath('orders.0.customerName', 'Paolo Dizon');
    }

    // ── The catalog ──────────────────────────────────────────────────────

    /**
     * "Untracked" and "sold out" are different facts about a product, and
     * reporting the first as the second starts a wrong conversation with a
     * merchant about a shelf that is actually full.
     */
    public function test_an_untracked_product_reports_no_stock_figure_rather_than_zero(): void
    {
        $tracked = $this->makeProduct('Arabica Coffee Beans', trackInventory: true);
        $this->makeProduct('Mountain Honey', trackInventory: false);

        InventoryLevel::query()->create([
            'organization_id' => $tracked->organization_id,
            'store_id' => $this->store->id,
            'product_id' => $tracked->id,
            'qty_on_hand' => 120,
            'reorder_level' => 10,
            'updated_at' => now(),
        ]);

        $products = collect($this->asOperator()->getJson('/api/platform-admin/products')->assertOk()
            ->json('products'))->keyBy('name');

        // Loosely compared: the sum arrives as a JSON number and whether it
        // decodes as int or float is the driver's business, not this claim's.
        $this->assertEquals(120, $products['Arabica Coffee Beans']['stockOnHand']);
        $this->assertNull($products['Mountain Honey']['stockOnHand']);
    }

    public function test_the_catalog_filters_by_category_and_counts_the_tabs(): void
    {
        $coffee = Category::query()->create([
            'organization_id' => $this->store->organization_id,
            'name' => 'Coffee',
            'sort_order' => 1,
        ]);

        $this->makeProduct('Arabica Coffee Beans', category: $coffee);
        $this->makeProduct('Mountain Honey');

        $response = $this->asOperator()
            ->getJson('/api/platform-admin/products?categoryId='.$coffee->id)
            ->assertOk();

        $response->assertJsonCount(1, 'products');
        $response->assertJsonPath('products.0.name', 'Arabica Coffee Beans');

        $tab = collect($response->json('categories'))->firstWhere('name', 'Coffee');
        $this->assertSame(1, $tab['products']);
    }

    // ── Analytics ────────────────────────────────────────────────────────

    /**
     * An order spans categories, so its total cannot be attributed to one of
     * them — the split has to come from the line items or every multi-category
     * basket overstates whichever category was first in it.
     */
    public function test_category_takings_are_split_across_the_lines_of_an_order(): void
    {
        $coffee = Category::query()->create([
            'organization_id' => $this->store->organization_id,
            'name' => 'Coffee',
            'sort_order' => 1,
        ]);
        $produce = Category::query()->create([
            'organization_id' => $this->store->organization_id,
            'name' => 'Farm Produce',
            'sort_order' => 2,
        ]);

        $order = $this->makeOrder(paymentStatus: 'paid', totalCents: 100000);
        $this->makeItem($order, $this->makeProduct('Beans', category: $coffee), 70000);
        $this->makeItem($order, $this->makeProduct('Carrots', category: $produce), 30000);

        $rows = collect($this->asOperator()->getJson('/api/platform-admin/analytics')->assertOk()
            ->json('salesByCategory'))->keyBy('label');

        $this->assertSame(70000, $rows['Coffee']['value']);
        $this->assertSame(30000, $rows['Farm Produce']['value']);
    }

    public function test_the_analytics_window_is_the_one_that_was_asked_for(): void
    {
        $this->asOperator()->getJson('/api/platform-admin/analytics?days=7')->assertOk()
            ->assertJsonPath('window.days', 7)
            ->assertJsonCount(7, 'series');

        $this->asOperator()->getJson('/api/platform-admin/analytics?days=5')->assertStatus(422);
    }

    public function test_analytics_includes_top_products_repeat_customers_and_every_hour(): void
    {
        $product = $this->makeProduct('Highland Strawberries');
        $order = $this->makeOrder(paymentStatus: 'paid', totalCents: 49800);
        $this->makeItem($order, $product, 49800);

        $response = $this->asOperator()->getJson('/api/platform-admin/analytics')->assertOk()
            ->assertJsonPath('topProducts.0.label', 'Highland Strawberries')
            ->assertJsonPath('topProducts.0.revenueCents', 49800)
            ->assertJsonCount(24, 'ordersByHour')
            ->assertJsonStructure(['returningCustomers' => ['customers', 'total', 'percent']]);

        $this->assertSame(1, collect($response->json('ordersByHour'))->sum('orders'));
    }

    // ── Settings ─────────────────────────────────────────────────────────

    public function test_settings_start_from_the_row_the_migration_wrote(): void
    {
        $this->asOperator()->getJson('/api/platform-admin/settings')->assertOk()
            ->assertJsonPath('settings.name', 'Omaykan')
            ->assertJsonPath('settings.delivery.baseFeeCents', 4900)
            ->assertJsonPath('settings.notifications.newOrder', true);
    }

    /**
     * Each tab saves on its own, so saving the identity block must not blank
     * the delivery policy that is not on screen at the time.
     */
    public function test_saving_one_block_leaves_the_others_alone(): void
    {
        $this->asOperator()->putJson('/api/platform-admin/settings', [
            'name' => 'Cordillera Highlands Marketplace',
            'tagline' => 'Local Products. Stronger Communities.',
        ])->assertOk()->assertJsonPath('settings.name', 'Cordillera Highlands Marketplace');

        $this->asOperator()->getJson('/api/platform-admin/settings')->assertOk()
            ->assertJsonPath('settings.name', 'Cordillera Highlands Marketplace')
            ->assertJsonPath('settings.delivery.baseFeeCents', 4900);
    }

    public function test_a_slipped_decimal_point_in_the_delivery_fee_is_rejected(): void
    {
        $this->asOperator()->putJson('/api/platform-admin/settings', [
            'delivery' => ['baseFeeCents' => -1, 'freeDeliveryOverCents' => 0, 'maxDistanceKm' => 12],
        ])->assertStatus(422);

        $this->asOperator()->putJson('/api/platform-admin/settings', [
            'website' => 'not-a-url',
        ])->assertStatus(422);
    }

    public function test_reports_reconcile_sales_discounts_tax_and_payment_methods(): void
    {
        $cash = $this->makeOrder('paid', 11800, ticket: 'R-1001');
        $cash->update([
            'subtotal_cents' => 11000,
            'discount_cents' => 1000,
            'tax_cents' => 1800,
            'payment_method' => 'cash',
            'business_mode' => 'delivery',
        ]);

        $gcash = $this->makeOrder('paid', 5600, ticket: 'R-1002');
        $gcash->update([
            'subtotal_cents' => 5000,
            'discount_cents' => 0,
            'tax_cents' => 600,
            'payment_method' => 'gcash',
            'business_mode' => 'pickup',
        ]);

        $response = $this->asOperator()->getJson('/api/platform-admin/reports?range=today')->assertOk();

        $response
            ->assertJsonPath('headline.grossSalesCents', 16000)
            ->assertJsonPath('headline.netSalesCents', 15000)
            ->assertJsonPath('headline.taxCents', 2400)
            ->assertJsonPath('headline.discountCents', 1000)
            ->assertJsonPath('headline.orders', 2)
            ->assertJsonPath('paymentsByMethod.0.label', 'Cash')
            ->assertJsonPath('paymentsByMethod.0.value', 11800)
            ->assertJsonPath('paymentsByMethod.1.label', 'GCash')
            ->assertJsonCount(2, 'recentTransactions');
    }

    public function test_reports_accept_only_the_ranges_the_screen_offers(): void
    {
        $this->asOperator()->getJson('/api/platform-admin/reports?range=7')->assertOk();
        $this->asOperator()->getJson('/api/platform-admin/reports?range=quarter')->assertStatus(422);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    private function makeOrder(
        string $paymentStatus = 'unpaid',
        int $totalCents = 1000,
        string $orderStatus = 'preparing',
        ?string $deliveryStage = null,
        ?string $ticket = null,
        ?CustomerAccount $customer = null,
        ?array $guest = null,
    ): Order {
        return Order::query()->create([
            'organization_id' => $this->store->organization_id,
            'store_id' => $this->store->id,
            'customer_account_id' => $customer?->id,
            'ticket_number' => $ticket ?? 'T-'.str_pad((string) (Order::withTrashed()->count() + 1), 4, '0', STR_PAD_LEFT),
            'channel' => 'online',
            'order_type' => 'takeaway',
            'order_status' => $orderStatus,
            'delivery_stage' => $deliveryStage,
            'payment_status' => $paymentStatus,
            'subtotal_cents' => $totalCents,
            'tax_cents' => 0,
            'total_cents' => $totalCents,
            'business_date' => now()->toDateString(),
            'guest_contact' => $guest,
        ]);
    }

    private function makeProduct(string $name, ?Category $category = null, bool $trackInventory = false): Product
    {
        return Product::query()->create([
            'organization_id' => $this->store->organization_id,
            'category_id' => $category?->id,
            'name' => $name,
            'sku' => strtolower(str_replace(' ', '-', $name)),
            'product_type' => 'standard',
            'price_cents' => 35000,
            'tax_rate' => 0,
            'track_inventory' => $trackInventory,
            'is_active' => true,
        ]);
    }

    private function makeItem(Order $order, Product $product, int $lineTotalCents): OrderItem
    {
        return OrderItem::query()->create([
            'organization_id' => $order->organization_id,
            'store_id' => $order->store_id,
            'order_id' => $order->id,
            'product_id' => $product->id,
            'product_name' => $product->name,
            'quantity' => 1,
            'unit_price_cents' => $lineTotalCents,
            'line_total_cents' => $lineTotalCents,
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
