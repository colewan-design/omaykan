<?php

namespace Tests\Feature\Api;

use App\Models\Device;
use App\Models\Order;
use App\Models\Rider;
use App\Models\Store;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * What the rider has earned, aggregated in the database.
 *
 * The thing being tested is mostly arithmetic about *when*: which day a job
 * counts against, and which jobs count at all. Both were previously decided in
 * the browser over whatever thirty rows the history endpoint had returned.
 */
class RiderEarningsApiTest extends TestCase
{
    use RefreshDatabase;

    private Rider $rider;

    private string $token;

    private Store $store;

    private Device $device;

    /** Orders carry a per-shop ticket number and the column is NOT NULL. */
    private static int $ticket = 1;

    protected function setUp(): void
    {
        parent::setUp();

        // Orders need a shop to belong to, and the seeder's is the one every
        // other rider test uses. Nothing here reads the catalog.
        $this->seed();
        $this->store = Store::query()->firstOrFail();
        // The seeders create no device, and `orders.device_id` is NOT NULL —
        // every order in this application was rung up on something.
        $this->device = Device::query()->create([
            'organization_id' => $this->store->organization_id,
            'store_id' => $this->store->id,
            'device_name' => 'Earnings test till',
            'platform' => 'web',
        ]);

        $this->rider = Rider::query()->create([
            'name' => 'Jun Dela Cruz',
            'email' => 'jun@example.com',
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/licence.png',
            'plate_image_path' => 'rider-documents/plate.png',
            'status' => Rider::STATUS_APPROVED,
            'reviewed_at' => now(),
        ]);

        $this->token = $this->rider->createToken('rider-portal', ['rider'])->plainTextToken;
    }

    /** A delivery on this rider's account, at a stage and a moment. */
    private function delivery(string $stage, CarbonImmutable $acceptedAt, int $feeCents): Order
    {
        return $this->deliveryFor($this->rider->id, $stage, $acceptedAt, $feeCents);
    }

    /**
     * The columns `orders` insists on, and nothing more.
     *
     * Written straight rather than placed through the online-order endpoint:
     * these tests are about summing rows at particular moments, and driving a
     * checkout to get one would make the timestamp the hard part.
     */
    private function deliveryFor(
        string $riderId,
        string $stage,
        CarbonImmutable $acceptedAt,
        int $feeCents,
    ): Order {
        return Order::query()->forceCreate([
            'organization_id' => $this->store->organization_id,
            'store_id' => $this->store->id,
            'device_id' => $this->device->id,
            'ticket_number' => (string) self::$ticket++,
            'order_type' => 'online',
            'order_status' => 'held',
            'channel' => 'online',
            'business_date' => $acceptedAt->toDateString(),
            'completed_at' => $acceptedAt->utc(),
            'rider_id' => $riderId,
            /*
             * Stored in UTC, which is what the application writes. Eloquent
             * formats a Carbon without converting it, so handing this a Manila
             * time would put a Manila wall-clock reading in a UTC column — and
             * the test would then be asserting against a bug it had introduced
             * itself rather than against the controller.
             */
            'rider_accepted_at' => $acceptedAt->utc(),
            'delivery_stage' => $stage,
            'delivery_fee_cents' => $feeCents,
            'fulfillment_method' => 'delivery',
            'subtotal_cents' => 0,
            'tax_cents' => 0,
            'total_cents' => $feeCents,
        ]);
    }

    public function test_only_delivered_jobs_are_counted(): void
    {
        $manilaNoon = CarbonImmutable::now('Asia/Manila')->startOfDay()->addHours(12);

        $this->delivery('delivered', $manilaNoon, 5000);
        // Money the rider is about to make, not money they have made. Counting
        // it would make the total go *down* when a job is handed back.
        $this->delivery('picked_up', $manilaNoon, 9900);
        $this->delivery('assigned', $manilaNoon, 9900);

        $this->withToken($this->token)
            ->getJson('/api/rider/earnings')
            ->assertOk()
            ->assertJsonPath('today.jobs', 1)
            ->assertJsonPath('today.feeCents', 5000)
            ->assertJsonPath('allTime.feeCents', 5000);
    }

    public function test_another_riders_work_is_not_counted(): void
    {
        $other = Rider::query()->create([
            'name' => 'Bea Tolentino',
            'email' => 'bea@example.com',
            'phone' => '0917 555 0102',
            'password' => 'ride-with-me-2',
            'license_number' => 'N02-34-567890',
            'plate_number' => 'BGO 5678',
            'license_image_path' => 'rider-documents/licence-2.png',
            'plate_image_path' => 'rider-documents/plate-2.png',
            'status' => Rider::STATUS_APPROVED,
        ]);

        $this->deliveryFor($other->id, 'delivered', CarbonImmutable::now('Asia/Manila'), 7500);

        $this->withToken($this->token)
            ->getJson('/api/rider/earnings')
            ->assertOk()
            ->assertJsonPath('allTime.jobs', 0)
            ->assertJsonPath('allTime.feeCents', 0);
    }

    /**
     * The application runs in UTC, which puts a day boundary at 8am in Baguio.
     * A job taken at 9pm Manila is 1pm UTC *the same day*, and a job taken at
     * 7am Manila is 11pm UTC the day before — bucketed in UTC, a night shift
     * would land on two different days and a breakfast run on yesterday.
     */
    public function test_a_day_is_a_manila_day_not_a_utc_one(): void
    {
        $lateLastNight = CarbonImmutable::now('Asia/Manila')->startOfDay()->subHours(2);
        $thisMorning = CarbonImmutable::now('Asia/Manila')->startOfDay()->addHours(7);

        $this->delivery('delivered', $lateLastNight, 4000);
        $this->delivery('delivered', $thisMorning, 6000);

        $this->withToken($this->token)
            ->getJson('/api/rider/earnings')
            ->assertOk()
            // 7am Manila is yesterday in UTC, and belongs to today.
            ->assertJsonPath('today.feeCents', 6000)
            ->assertJsonPath('allTime.feeCents', 10000);
    }

    public function test_the_trend_carries_every_day_including_the_empty_ones(): void
    {
        $this->delivery('delivered', CarbonImmutable::now('Asia/Manila')->startOfDay()->addHours(10), 4500);

        $days = $this->withToken($this->token)
            ->getJson('/api/rider/earnings')
            ->assertOk()
            ->json('days');

        // A chart with the quiet days missing lies about how busy a week was.
        $this->assertCount(14, $days);
        $this->assertSame(0, $days[0]['feeCents']);
        $this->assertSame(4500, $days[13]['feeCents']);
    }

    public function test_the_gate_still_applies(): void
    {
        $this->rider->forceFill(['status' => Rider::STATUS_SUSPENDED])->save();

        // Behind `rider.approved` with the rest of the work routes: earnings
        // are a fact about work, and the gate answers with the status so the
        // client can move the whole screen rather than show an error.
        $this->withToken($this->token)
            ->getJson('/api/rider/earnings')
            ->assertForbidden()
            ->assertJsonPath('riderStatus', Rider::STATUS_SUSPENDED);
    }
}
