<?php

namespace Tests\Feature\Api;

use App\Models\Order;
use App\Models\OrderPushToken;
use App\Models\Product;
use App\Models\Rider;
use App\Services\PushSender;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Http\Client\Request as HttpRequest;
use Illuminate\Support\Facades\Http;
use Mockery;
use Tests\TestCase;

/**
 * "Rider assigned" on the customer's phone.
 *
 * DatabaseMigrations for the reason RiderDeliveryApiTest gives: the event rides
 * DB::afterCommit. The queue is `sync` under test, so the listener runs inside
 * the accept request.
 */
class OrderPushNotificationTest extends TestCase
{
    use DatabaseMigrations;

    private function placeOrder(string $method = 'delivery'): string
    {
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        return $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 1]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => $method === 'delivery'
                ? ['method' => 'delivery', 'address' => '12 Session Road, Sto. Tomas, Baguio City']
                : ['method' => 'pickup'],
        ])->assertCreated()->json('orderId');
    }

    private function riderToken(): string
    {
        return Rider::query()->create([
            'name' => 'Jun Dela Cruz',
            'email' => 'jun@example.com',
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/license.jpg',
            'plate_image_path' => 'rider-documents/plate.jpg',
            'status' => Rider::STATUS_APPROVED,
        ])->createToken('rider-portal', ['rider'])->plainTextToken;
    }

    private function accept(string $orderId): void
    {
        $this->app['auth']->forgetGuards();
        $this->withHeader('Authorization', 'Bearer '.$this->riderToken())
            ->postJson("/api/rider/deliveries/{$orderId}/accept")
            ->assertOk();
    }

    public function test_a_phone_can_register_for_a_delivery_order_once(): void
    {
        $this->seed();
        $orderId = $this->placeOrder();

        $this->postJson("/api/online-orders/{$orderId}/push-token", ['token' => 'phone-a'])->assertNoContent();
        $this->postJson("/api/online-orders/{$orderId}/push-token", ['token' => 'phone-a'])->assertNoContent();

        $this->assertSame(1, OrderPushToken::query()->where('order_id', $orderId)->count());
    }

    public function test_pickup_orders_and_unknown_orders_are_refused(): void
    {
        $this->seed();

        $this->postJson('/api/online-orders/'.$this->placeOrder('pickup').'/push-token', ['token' => 'phone-a'])
            ->assertStatus(422);
        $this->postJson('/api/online-orders/9b0f3b6e-0000-4000-8000-000000000000/push-token', ['token' => 'phone-a'])
            ->assertNotFound();
    }

    public function test_one_order_takes_a_handful_of_phones_and_no_more(): void
    {
        $this->seed();
        $orderId = $this->placeOrder();

        foreach (range(1, 5) as $i) {
            $this->postJson("/api/online-orders/{$orderId}/push-token", ['token' => "phone-{$i}"])->assertNoContent();
        }

        $this->postJson("/api/online-orders/{$orderId}/push-token", ['token' => 'phone-6'])->assertStatus(422);
    }

    public function test_accepting_tells_every_registered_phone_and_forgets_dead_ones(): void
    {
        $this->seed();
        $orderId = $this->placeOrder();
        OrderPushToken::query()->create(['order_id' => $orderId, 'token' => 'alive']);
        OrderPushToken::query()->create(['order_id' => $orderId, 'token' => 'uninstalled']);

        $sent = [];
        $push = Mockery::mock(PushSender::class);
        $push->shouldReceive('configured')->andReturnTrue();
        $push->shouldReceive('send')->twice()->andReturnUsing(
            function (string $token, string $title, string $body, array $data, ?string $tag) use (&$sent, $orderId) {
                $sent[] = compact('token', 'title', 'body', 'data', 'tag');

                return $token === 'uninstalled' ? PushSender::INVALID : PushSender::SENT;
            },
        );
        $this->app->instance(PushSender::class, $push);

        $this->accept($orderId);

        $ticket = Order::query()->findOrFail($orderId)->ticket_number;
        $this->assertSame('Rider assigned', $sent[0]['title']);
        $this->assertSame("Jun Dela Cruz is heading to Main Branch for order #{$ticket}.", $sent[0]['body']);
        $this->assertSame(['orderId' => $orderId, 'deliveryStage' => 'assigned'], $sent[0]['data']);
        $this->assertSame($orderId, $sent[0]['tag']);

        $this->assertSame(['alive'], OrderPushToken::query()->where('order_id', $orderId)->pluck('token')->all());
    }

    public function test_a_server_without_firebase_sends_nothing_and_still_accepts(): void
    {
        $this->seed();
        $orderId = $this->placeOrder();
        OrderPushToken::query()->create(['order_id' => $orderId, 'token' => 'alive']);
        config(['services.firebase.credentials' => null]);
        Http::fake();

        $this->accept($orderId);

        Http::assertNothingSent();
    }

    public function test_the_sender_signs_in_to_google_and_posts_an_fcm_v1_message(): void
    {
        $key = @openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
        if ($key === false) {
            $this->markTestSkipped('This PHP cannot generate an RSA key (no openssl.cnf).');
        }
        openssl_pkey_export($key, $pem);

        $path = tempnam(sys_get_temp_dir(), 'fcm');
        file_put_contents($path, json_encode([
            'type' => 'service_account',
            'project_id' => 'omaykan-test',
            'client_email' => 'push@omaykan-test.iam.gserviceaccount.com',
            'private_key' => $pem,
        ]));
        config(['services.firebase.credentials' => $path]);

        Http::fake([
            'oauth2.googleapis.com/*' => Http::response(['access_token' => 'ya29.test', 'expires_in' => 3600]),
            'fcm.googleapis.com/*' => Http::sequence()
                ->push(['name' => 'projects/omaykan-test/messages/1'])
                ->push(['error' => ['status' => 'NOT_FOUND', 'details' => [['errorCode' => 'UNREGISTERED']]]], 404),
        ]);

        $sender = app(PushSender::class);
        $this->assertTrue($sender->configured());
        $this->assertSame(PushSender::SENT, $sender->send('tok', 'Rider assigned', 'Hi', ['orderId' => 'o1'], 'o1'));
        $this->assertSame(PushSender::INVALID, $sender->send('gone', 'Rider assigned', 'Hi'));

        Http::assertSent(fn (HttpRequest $request) => $request->url() === 'https://fcm.googleapis.com/v1/projects/omaykan-test/messages:send'
            && $request->hasHeader('Authorization', 'Bearer ya29.test')
            && $request['message']['token'] === 'tok'
            && $request['message']['android']['notification']['channel_id'] === 'order_updates');

        // The access token is reused, not fetched per message.
        Http::assertSentCount(3);

        unlink($path);
    }
}
