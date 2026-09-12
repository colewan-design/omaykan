<?php

namespace Tests\Feature\Api;

use App\Models\CustomerAccount;
use App\Models\Order;
use App\Models\Product;
use App\Models\Rider;
use App\Models\RiderRating;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Tests\TestCase;

/**
 * Scoring the ride, and the disclosure that makes it possible to know who to
 * score.
 *
 * DatabaseMigrations rather than RefreshDatabase for RiderDeliveryApiTest's
 * reason: the delivery stage changes broadcast on DB::afterCommit, which never
 * fires inside a transaction that is rolled back.
 */
class RiderRatingApiTest extends TestCase
{
    use DatabaseMigrations;

    private function rider(): Rider
    {
        return Rider::query()->create([
            'name' => 'Jun Dela Cruz',
            'email' => 'jun@example.com',
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/l.jpg',
            'plate_image_path' => 'rider-documents/p.jpg',
            'status' => Rider::STATUS_APPROVED,
            'vehicle_type' => 'scooter',
            'vehicle_make' => 'Honda',
            'vehicle_model' => 'Click',
            'vehicle_color' => 'red',
        ]);
    }

    private function customer(): CustomerAccount
    {
        return CustomerAccount::query()->create([
            'name' => 'Maria Santos',
            'email' => 'maria@example.com',
            'phone' => '09171234567',
            'password' => 'shop-with-me-1',
            'email_verified_at' => now(),
        ]);
    }

    /** An order placed by that customer, carried by that rider, at a stage. */
    private function order(CustomerAccount $customer, Rider $rider, string $stage): Order
    {
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        $orderId = $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 1]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'delivery', 'address' => '12 Session Road, Baguio City'],
        ])->assertCreated()->json('orderId');

        $order = Order::query()->findOrFail($orderId);
        $order->forceFill([
            'customer_account_id' => $customer->getKey(),
            'rider_id' => $rider->getKey(),
            'rider_name' => $rider->name,
            'rider_phone' => $rider->phone,
            'rider_accepted_at' => now(),
            'delivery_stage' => $stage,
        ])->save();

        return $order->fresh();
    }

    private function withCustomer(CustomerAccount $account): self
    {
        $this->app['auth']->forgetGuards();

        return $this->withHeader(
            'Authorization',
            'Bearer '.$account->createToken('customer-portal', ['customer'])->plainTextToken
        );
    }

    // -- Disclosure ---------------------------------------------------------

    public function test_the_customer_sees_the_rider_while_the_order_is_on_its_way(): void
    {
        $this->seed();
        $customer = $this->customer();
        $rider = $this->rider();
        $order = $this->order($customer, $rider, 'picked_up');

        $this->withCustomer($customer)
            ->getJson('/api/customer/orders/'.$order->id)
            ->assertOk()
            ->assertJsonPath('riderProfile.name', 'Jun Dela Cruz')
            ->assertJsonPath('riderProfile.vehicle.label', 'red Honda Click')
            ->assertJsonPath('riderProfile.vehicle.plateNumber', 'NBC 1234');
    }

    public function test_the_rider_disappears_from_the_payload_once_the_food_is_delivered(): void
    {
        $this->seed();
        $customer = $this->customer();
        $rider = $this->rider();
        $order = $this->order($customer, $rider, 'delivered');

        // The tracking link is public by UUID and meant to be forwarded. A face
        // that stays readable afterwards is readable by everyone ever sent it.
        $this->withCustomer($customer)
            ->getJson('/api/customer/orders/'.$order->id)
            ->assertOk()
            ->assertJsonPath('riderProfile', null);
    }

    // -- Rating -------------------------------------------------------------

    public function test_a_customer_rates_a_delivered_order_once(): void
    {
        $this->seed();
        $customer = $this->customer();
        $rider = $this->rider();
        $order = $this->order($customer, $rider, 'delivered');

        $this->withCustomer($customer)
            ->postJson('/api/customer/orders/'.$order->id.'/rider-rating', [
                'score' => 5,
                'comment' => 'Rang once, waited, very polite.',
            ])
            ->assertCreated()
            ->assertJsonPath('rating.score', 5);

        $this->assertSame(1, RiderRating::query()->count());
        $this->assertSame(5.0, $rider->fresh()->ratingSummary()['average']);

        // Second time is refused: a rating is a receipt, not an opinion.
        $this->withCustomer($customer)
            ->postJson('/api/customer/orders/'.$order->id.'/rider-rating', ['score' => 1])
            ->assertStatus(422);

        $this->assertSame(1, RiderRating::query()->count());
    }

    public function test_an_order_still_on_the_bike_cannot_be_rated(): void
    {
        $this->seed();
        $customer = $this->customer();
        $order = $this->order($customer, $this->rider(), 'picked_up');

        $this->withCustomer($customer)
            ->postJson('/api/customer/orders/'.$order->id.'/rider-rating', ['score' => 5])
            ->assertStatus(422);
    }

    public function test_a_customer_cannot_rate_somebody_elses_delivery(): void
    {
        $this->seed();
        $mine = $this->customer();
        $order = $this->order($mine, $this->rider(), 'delivered');

        $stranger = CustomerAccount::query()->create([
            'name' => 'Someone Else',
            'email' => 'else@example.com',
            'phone' => '09170000000',
            'password' => 'shop-with-me-2',
            'email_verified_at' => now(),
        ]);

        // A 404, not a 403: a wrong id and somebody else's id must be
        // indistinguishable, or this becomes a way to probe for orders.
        $this->withCustomer($stranger)
            ->postJson('/api/customer/orders/'.$order->id.'/rider-rating', ['score' => 1])
            ->assertNotFound();
    }

    public function test_a_score_outside_one_to_five_is_refused(): void
    {
        $this->seed();
        $customer = $this->customer();
        $order = $this->order($customer, $this->rider(), 'delivered');

        $this->withCustomer($customer)
            ->postJson('/api/customer/orders/'.$order->id.'/rider-rating', ['score' => 9])
            ->assertStatus(422)
            ->assertJsonValidationErrors('score');
    }

    public function test_an_unrated_rider_has_no_average_rather_than_a_zero(): void
    {
        $this->seed();

        $summary = $this->rider()->ratingSummary();

        $this->assertNull($summary['average']);
        $this->assertSame(0, $summary['count']);
    }

    public function test_a_rider_reads_their_own_scores_without_the_customers_name(): void
    {
        $this->seed();
        $customer = $this->customer();
        $rider = $this->rider();
        $order = $this->order($customer, $rider, 'delivered');

        $this->withCustomer($customer)
            ->postJson('/api/customer/orders/'.$order->id.'/rider-rating', ['score' => 4, 'comment' => 'Quick.'])
            ->assertCreated();

        $this->app['auth']->forgetGuards();

        $body = $this->withHeader('Authorization', 'Bearer '.$rider->createToken('rider-portal', ['rider'])->plainTextToken)
            ->getJson('/api/rider/ratings')
            ->assertOk()
            ->assertJsonPath('summary.count', 1)
            ->assertJsonPath('ratings.0.score', 4)
            ->json();

        // The person who left it is not in the payload anywhere.
        $this->assertStringNotContainsString('Maria', json_encode($body));
        $this->assertStringNotContainsString('maria@example.com', json_encode($body));
    }
}
