<?php

namespace Tests\Feature\Api;

use App\Models\Conversation;
use App\Models\ConversationMessage;
use App\Models\CustomerAccount;
use App\Models\Product;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Messages between a shopper and a shop.
 *
 * DatabaseMigrations for the reason the other order tests give: placing an
 * order broadcasts on DB::afterCommit, which never fires inside a rolled-back
 * transaction.
 */
class ConversationApiTest extends TestCase
{
    use DatabaseMigrations, SignsInStaff;

    private function customer(string $email = 'maria@example.com', string $name = 'Maria Santos'): CustomerAccount
    {
        return CustomerAccount::query()->create([
            'name' => $name,
            'email' => $email,
            'phone' => '09171234567',
            'password' => 'shop-with-me-1',
            'email_verified_at' => now(),
        ]);
    }

    private function asCustomer(CustomerAccount $account): self
    {
        $this->app['auth']->forgetGuards();

        return $this->withToken($account->createToken('customer-portal', ['customer'])->plainTextToken);
    }

    private function asStaff(string $token): self
    {
        $this->app['auth']->forgetGuards();

        return $this->withToken($token);
    }

    /** A guest order at the seeded coffee shop. */
    private function placeOrder(): string
    {
        $this->app['auth']->forgetGuards();
        $this->withoutToken();

        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        return $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 1]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'delivery', 'address' => '12 Session Road, Baguio City'],
        ])->assertCreated()->json('orderId');
    }

    private function startWithShop(CustomerAccount $customer, string $body = 'Do you have oat milk?'): string
    {
        return $this->asCustomer($customer)
            ->postJson('/api/customer/conversations', [
                'orgSlug' => 'demo-coffee',
                'storeCode' => 'main',
                'body' => $body,
            ])
            ->assertCreated()
            ->json('conversation.id');
    }

    public function test_a_customer_and_the_shop_trade_messages_with_unread_counts_on_both_sides(): void
    {
        $this->seed();
        $staff = $this->staffToken();
        $customer = $this->customer();

        $id = $this->startWithShop($customer);

        // The shop sees it, unread.
        $this->asStaff($staff)
            ->getJson('/api/seller/conversations')
            ->assertOk()
            ->assertJsonPath('conversations.0.id', $id)
            ->assertJsonPath('conversations.0.customer.name', 'Maria Santos')
            ->assertJsonPath('conversations.0.lastMessage.body', 'Do you have oat milk?')
            ->assertJsonPath('conversations.0.unreadCount', 1);

        $this->asStaff($staff)->getJson('/api/seller/conversations/unread')->assertJsonPath('unread', 1);

        // Opening it is reading it.
        $this->asStaff($staff)
            ->getJson("/api/seller/conversations/{$id}")
            ->assertOk()
            ->assertJsonPath('messages.0.from', 'customer')
            ->assertJsonPath('conversation.unreadCount', 0);

        $this->asStaff($staff)->getJson('/api/seller/conversations/unread')->assertJsonPath('unread', 0);

        // The shop answers.
        $this->asStaff($staff)
            ->postJson("/api/seller/conversations/{$id}/messages", ['body' => 'We do — 20 pesos extra.'])
            ->assertCreated()
            ->assertJsonPath('messages.1.from', 'store')
            ->assertJsonPath('messages.1.body', 'We do — 20 pesos extra.');

        // Now it is the customer who has something unread.
        $this->asCustomer($customer)->getJson('/api/customer/conversations/unread')->assertJsonPath('unread', 1);

        $this->asCustomer($customer)
            ->getJson('/api/customer/conversations')
            ->assertOk()
            ->assertJsonPath('conversations.0.store.orgSlug', 'demo-coffee')
            ->assertJsonPath('conversations.0.lastMessage.from', 'store')
            ->assertJsonPath('conversations.0.unreadCount', 1);

        $this->asCustomer($customer)
            ->getJson("/api/customer/conversations/{$id}")
            ->assertOk()
            ->assertJsonCount(2, 'messages');

        $this->asCustomer($customer)->getJson('/api/customer/conversations/unread')->assertJsonPath('unread', 0);
    }

    public function test_messaging_the_same_shop_again_lands_in_the_same_conversation(): void
    {
        $this->seed();
        $customer = $this->customer();

        $first = $this->startWithShop($customer, 'Hello?');
        $second = $this->startWithShop($customer, 'Hello again.');

        $this->assertSame($first, $second);
        $this->assertSame(1, Conversation::query()->count());
        $this->assertSame(2, ConversationMessage::query()->count());
        // Both of them unread by the shop, not reset by the second.
        $this->assertSame(2, Conversation::query()->first()->store_unread);
    }

    public function test_a_customer_can_message_about_an_order_and_the_shop_sees_which_one(): void
    {
        $this->seed();
        $staff = $this->staffToken();
        $orderId = $this->placeOrder();
        $customer = $this->customer();

        // A guest order: the UUID is the capability, the same one the public
        // tracking link runs on.
        $id = $this->asCustomer($customer)
            ->postJson('/api/customer/conversations', [
                'orderId' => $orderId,
                'body' => 'Can you leave it with the guard?',
            ])
            ->assertCreated()
            ->assertJsonPath('messages.0.order.id', $orderId)
            ->json('conversation.id');

        $this->asStaff($staff)
            ->getJson("/api/seller/conversations/{$id}")
            ->assertOk()
            ->assertJsonPath('messages.0.order.id', $orderId);
    }

    public function test_the_shop_sees_who_on_staff_replied_and_the_customer_does_not(): void
    {
        $this->seed();
        $staff = $this->staffToken();
        $customer = $this->customer();
        $id = $this->startWithShop($customer);

        $this->asStaff($staff)
            ->postJson("/api/seller/conversations/{$id}/messages", ['body' => 'Yes.'])
            ->assertCreated();

        $shopView = $this->asStaff($staff)->getJson("/api/seller/conversations/{$id}")->json('messages.1');
        $this->assertNotNull($shopView['authorName']);

        $customerView = $this->asCustomer($customer)->getJson("/api/customer/conversations/{$id}")->json('messages.1');
        $this->assertArrayNotHasKey('authorName', $customerView);
    }

    public function test_one_customer_cannot_read_anothers_conversation(): void
    {
        $this->seed();
        $id = $this->startWithShop($this->customer());
        $stranger = $this->customer('else@example.com', 'Someone Else');

        $this->asCustomer($stranger)->getJson("/api/customer/conversations/{$id}")->assertNotFound();
        $this->asCustomer($stranger)
            ->postJson("/api/customer/conversations/{$id}/messages", ['body' => 'Hi'])
            ->assertNotFound();
    }

    public function test_another_shop_cannot_read_or_answer_the_conversation(): void
    {
        $this->seed();
        $id = $this->startWithShop($this->customer());
        $rival = $this->staffTokenForNewTenant();

        $this->asStaff($rival)->getJson('/api/seller/conversations')->assertOk()->assertJsonCount(0, 'conversations');
        $this->asStaff($rival)->getJson("/api/seller/conversations/{$id}")->assertNotFound();
        $this->asStaff($rival)
            ->postJson("/api/seller/conversations/{$id}/messages", ['body' => 'Try us instead'])
            ->assertNotFound();
    }

    public function test_a_blank_message_or_an_unknown_shop_is_refused(): void
    {
        $this->seed();
        $customer = $this->customer();

        $this->asCustomer($customer)
            ->postJson('/api/customer/conversations', ['orgSlug' => 'demo-coffee', 'storeCode' => 'main', 'body' => '   '])
            ->assertStatus(422)
            ->assertJsonValidationErrors('body');

        $this->asCustomer($customer)
            ->postJson('/api/customer/conversations', ['orgSlug' => 'nobody', 'storeCode' => 'main', 'body' => 'Hi'])
            ->assertNotFound();

        $this->assertSame(0, Conversation::query()->count());
    }

    public function test_a_customer_cannot_attach_another_shops_order_to_a_reply(): void
    {
        $this->seed();
        $customer = $this->customer();
        $id = $this->startWithShop($customer);

        $this->asCustomer($customer)
            ->postJson("/api/customer/conversations/{$id}/messages", [
                'body' => 'About this one',
                'orderId' => (string) str()->uuid(),
            ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('orderId');
    }

    public function test_messaging_needs_an_account(): void
    {
        $this->seed();

        $this->postJson('/api/customer/conversations', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'body' => 'Hi',
        ])->assertUnauthorized();
    }
}
