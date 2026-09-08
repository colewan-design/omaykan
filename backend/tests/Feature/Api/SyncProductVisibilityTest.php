<?php

namespace Tests\Feature\Api;

use App\Models\Product;
use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Testing\TestResponse;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * A product a merchant lists in their own till has to be sellable online.
 *
 * It was not, until 2026-08-27. `applyProductEvent` never wrote
 * `business_modes`, and that field is what both the storefront catalog and
 * OnlineOrderController gate on — so a product created in the POS saved fine,
 * synced fine, listed fine in the till, and was invisible and unorderable in
 * the shop, with nothing anywhere saying so. Found by an end-to-end run
 * (documentation/e2e-findings.md §2); no test covered the seam, which is how it
 * survived a full suite.
 */
class SyncProductVisibilityTest extends TestCase
{
    use SignsInStaff, RefreshDatabase;

    /** @param array<string, mixed> $payload */
    private function pushProduct(string $token, string $productId, array $payload): TestResponse
    {
        $store = Store::query()->firstOrFail();

        return $this->withToken($token)->postJson('/api/sync/push', [
            'organizationId' => $store->organization_id,
            'storeId' => $store->id,
            'events' => [[
                'id' => (string) str()->uuid(),
                'entityType' => 'product',
                'entityId' => $productId,
                'operation' => 'upsert',
                'occurredAt' => now()->toIso8601String(),
                'payload' => $payload,
            ]],
        ]);
    }

    public function test_a_product_pushed_from_the_till_inherits_the_stores_business_mode(): void
    {
        $this->seed();
        $id = (string) str()->uuid();

        $this->pushProduct($this->staffToken(), $id, [
            'sku' => 'TILL-1',
            'name' => 'Listed at the counter',
            'priceCents' => 15000,
            'trackInventory' => true,
            'stockQty' => 10,
        ])->assertOk();

        $this->assertSame(['coffee-shop'], Product::query()->findOrFail($id)->business_modes);
    }

    public function test_that_product_is_then_visible_in_the_storefront_catalog(): void
    {
        $this->seed();
        $id = (string) str()->uuid();

        $this->pushProduct($this->staffToken(), $id, [
            'sku' => 'TILL-2',
            'name' => 'Sellable online',
            'priceCents' => 15000,
            'trackInventory' => true,
            'stockQty' => 10,
        ])->assertOk();

        $ids = $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')
            ->assertOk()
            ->json('products.*.id');

        $this->assertContains($id, $ids);
    }

    public function test_a_customer_can_actually_order_it(): void
    {
        $this->seed();
        $id = (string) str()->uuid();

        $this->pushProduct($this->staffToken(), $id, [
            'sku' => 'TILL-3',
            'name' => 'Orderable',
            'priceCents' => 15000,
            'trackInventory' => true,
            'stockQty' => 10,
        ])->assertOk();

        $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $id, 'quantity' => 2]],
            'guest' => ['name' => 'Buyer', 'phone' => '09170000000'],
            'fulfillment' => ['method' => 'pickup'],
            'paymentMethod' => 'cash',
        ])->assertCreated();
    }

    public function test_an_explicit_mode_from_the_client_wins_over_the_stores(): void
    {
        $this->seed();
        $id = (string) str()->uuid();

        $this->pushProduct($this->staffToken(), $id, [
            'sku' => 'TILL-4',
            'name' => 'Multi-mode',
            'priceCents' => 15000,
            'businessModes' => ['grocery', 'restaurant'],
        ])->assertOk();

        $this->assertSame(['grocery', 'restaurant'], Product::query()->findOrFail($id)->business_modes);
    }

    public function test_an_update_that_omits_the_field_does_not_un_list_the_product(): void
    {
        $this->seed();
        $id = (string) str()->uuid();
        $token = $this->staffToken();

        $this->pushProduct($token, $id, [
            'sku' => 'TILL-5',
            'name' => 'Multi-mode',
            'priceCents' => 15000,
            'businessModes' => ['grocery', 'restaurant'],
        ])->assertOk();

        // A price edit from the till carries no modes. It must not silently
        // narrow the product to the one store's mode.
        $this->pushProduct($token, $id, [
            'sku' => 'TILL-5',
            'name' => 'Multi-mode',
            'priceCents' => 16000,
        ])->assertOk();

        $product = Product::query()->findOrFail($id);
        $this->assertSame(['grocery', 'restaurant'], $product->business_modes);
        $this->assertSame(16000, $product->price_cents);
    }

    /**
     * A product with no category must not take the storefront down with it.
     *
     * `categoryId` falls back to the string 'uncategorized', and that used to
     * reach a `whereIn` against the uuid `categories.id`. PostgreSQL rejects
     * that comparison outright (22P02) rather than not matching, so one
     * uncategorised product returned a 500 for the entire catalog — every
     * product, every shopper. SQLite tolerated it, which is exactly why it
     * needs a test rather than a local click-through.
     */
    public function test_an_uncategorised_product_does_not_break_the_catalog(): void
    {
        $this->seed();
        $id = (string) str()->uuid();

        $this->pushProduct($this->staffToken(), $id, [
            'sku' => 'NO-CAT',
            'name' => 'Filed under nothing',
            'priceCents' => 15000,
            'trackInventory' => true,
            'stockQty' => 5,
        ])->assertOk();

        $response = $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')
            ->assertOk();

        $this->assertContains($id, $response->json('products.*.id'));
        $this->assertNotContains(
            'uncategorized',
            $response->json('categories.*.id'),
            'The sentinel is not a real category and must not be offered as a tab.',
        );
    }
}
