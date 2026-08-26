<?php

namespace Tests\Feature\Api;

use App\Events\OrderPlaced;
use App\Mail\OnlineOrderConfirmationMail;
use App\Models\Product;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Event;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

/**
 * The receipt a storefront customer gets, and the half of orders that get none.
 *
 * DatabaseMigrations for the same reason OnlineOrderApiTest uses it: the mail
 * is queued from DB::afterCommit, which never runs inside a transaction that
 * is rolled back.
 */
class OnlineOrderMailTest extends TestCase
{
    use DatabaseMigrations;

    private function payload(array $overrides = []): array
    {
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        return array_replace_recursive([
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [
                ['productId' => $product->id, 'quantity' => 2],
            ],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'pickup'],
        ], $overrides);
    }

    public function test_a_customer_who_leaves_an_email_gets_a_confirmation(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);
        Mail::fake();

        $created = $this->postJson('/api/online-orders', $this->payload([
            'guest' => ['email' => 'maria@example.test'],
        ]))->assertCreated()->json();

        Mail::assertQueued(
            OnlineOrderConfirmationMail::class,
            fn (OnlineOrderConfirmationMail $mail) => $mail->hasTo('maria@example.test')
                && $mail->order->id === $created['orderId']
        );
    }

    public function test_a_phone_only_order_sends_nothing(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);
        Mail::fake();

        // Checkout takes a phone number *or* an email, so this is a normal
        // order and not an error — there is simply nowhere to send a receipt.
        $this->postJson('/api/online-orders', $this->payload())->assertCreated();

        Mail::assertNothingQueued();
    }

    public function test_the_confirmation_carries_the_order_number_and_a_tracking_link(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);
        Mail::fake();
        config(['app.storefront_url' => 'https://omaykan.test/store']);

        $created = $this->postJson('/api/online-orders', $this->payload([
            'guest' => ['email' => 'maria@example.test'],
        ]))->assertCreated()->json();

        $body = null;

        Mail::assertQueued(OnlineOrderConfirmationMail::class, function ($mail) use (&$body) {
            $body = $mail->render();

            return true;
        });

        $this->assertStringContainsString($created['ticketNumber'], (string) $body);
        $this->assertStringContainsString(
            'https://omaykan.test/store/order/'.$created['orderId'],
            (string) $body,
        );
        // Espresso is ₱120.00 at 12% tax; two of them, no delivery fee.
        $this->assertStringContainsString('₱268.80', (string) $body);
        // Nothing to say about delivery on a pickup order.
        $this->assertStringNotContainsString('Delivering to', (string) $body);
    }
}
