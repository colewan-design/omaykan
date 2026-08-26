<?php

namespace Tests\Feature\Api;

use App\Events\OrderPlaced;
use App\Mail\OnlineOrderConfirmationMail;
use App\Models\Product;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Tests\Concerns\ActsAsShopper;
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
    use ActsAsShopper, DatabaseMigrations;

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

        $created = $this->asShopper()->postJson('/api/online-orders', $this->payload([
            'guest' => ['email' => 'maria@example.test'],
        ]))->assertCreated()->json();

        Mail::assertQueued(
            OnlineOrderConfirmationMail::class,
            fn (OnlineOrderConfirmationMail $mail) => $mail->hasTo('maria@example.test')
                && $mail->order->id === $created['orderId']
        );
    }

    /**
     * There is no longer such a thing as an order with nowhere to send a
     * receipt: ordering needs an account, and an account has a verified email.
     * A form that names someone else's phone still leaves the account's
     * address on the order, so the person who placed it gets the confirmation.
     */
    public function test_an_order_with_only_a_phone_on_the_form_still_confirms_to_the_account(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);
        Mail::fake();

        $this->asShopper()
            ->postJson('/api/online-orders', $this->payload([
                'guest' => ['name' => 'Ana Reyes', 'phone' => '09998887777'],
            ]))
            ->assertCreated();

        Mail::assertQueued(
            OnlineOrderConfirmationMail::class,
            fn (OnlineOrderConfirmationMail $mail) => $mail->hasTo('maria@example.com'),
        );
    }

    public function test_the_confirmation_carries_the_order_number_and_a_tracking_link(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);
        Mail::fake();
        config(['app.storefront_url' => 'https://omaykan.test']);

        $created = $this->asShopper()->postJson('/api/online-orders', $this->payload([
            'guest' => ['email' => 'maria@example.test'],
        ]))->assertCreated()->json();

        $body = null;

        Mail::assertQueued(OnlineOrderConfirmationMail::class, function ($mail) use (&$body) {
            $body = $mail->render();

            return true;
        });

        $this->assertStringContainsString($created['ticketNumber'], (string) $body);
        // The storefront root with ?order=, not a /order/ path: the shop is a
        // single page that reads the tracking view out of the query string.
        $this->assertStringContainsString(
            'https://omaykan.test/?order='.$created['orderId'],
            (string) $body,
        );
        // Espresso is ₱120.00 at 12% tax; two of them, no delivery fee.
        $this->assertStringContainsString('₱268.80', (string) $body);
        // Nothing to say about delivery on a pickup order.
        $this->assertStringNotContainsString('Delivering to', (string) $body);
    }
}
