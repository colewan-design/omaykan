<?php

namespace Tests\Feature\Api;

use App\Models\Rider;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

/**
 * The face and the bike: what a rider can put on their own profile, and who
 * is allowed to see it afterwards.
 *
 * The disclosure half is the one worth having tests for. A photograph of a
 * rider's face is the most personal thing this application stores about
 * anybody, and the rules that keep it off a forwarded tracking link are three
 * lines in one method — exactly the kind of thing a later refactor removes by
 * accident.
 */
class RiderProfileApiTest extends TestCase
{
    use RefreshDatabase;

    private Rider $rider;

    private string $token;

    protected function setUp(): void
    {
        parent::setUp();

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

    public function test_a_rider_uploads_and_removes_their_photo(): void
    {
        Storage::fake('local');

        $this->withToken($this->token)
            ->post('/api/rider/avatar', ['photo' => UploadedFile::fake()->image('me.jpg')])
            ->assertOk()
            // The path never travels; a URL does.
            ->assertJsonPath('rider.photoUrl', fn (?string $url) => is_string($url) && str_contains($url, $this->rider->id));

        $stored = $this->rider->fresh()->avatar_path;
        $this->assertNotNull($stored);
        Storage::disk('local')->assertExists($stored);

        $this->withToken($this->token)
            ->deleteJson('/api/rider/avatar')
            ->assertOk()
            ->assertJsonPath('rider.photoUrl', null);

        $this->assertNull($this->rider->fresh()->avatar_path);
        Storage::disk('local')->assertMissing($stored);
    }

    public function test_replacing_a_photo_deletes_the_one_it_replaced(): void
    {
        Storage::fake('local');

        $this->withToken($this->token)
            ->post('/api/rider/avatar', ['photo' => UploadedFile::fake()->image('first.jpg')])
            ->assertOk();

        $first = $this->rider->fresh()->avatar_path;

        $this->withToken($this->token)
            ->post('/api/rider/avatar', ['photo' => UploadedFile::fake()->image('second.jpg')])
            ->assertOk();

        $second = $this->rider->fresh()->avatar_path;

        $this->assertNotSame($first, $second);
        Storage::disk('local')->assertMissing($first);
        Storage::disk('local')->assertExists($second);
    }

    public function test_a_file_that_is_not_an_image_is_refused(): void
    {
        Storage::fake('local');

        $this->withToken($this->token)
            ->post('/api/rider/avatar', ['photo' => UploadedFile::fake()->create('payload.php', 16)])
            ->assertStatus(422)
            ->assertJsonValidationErrors('photo');

        $this->assertNull($this->rider->fresh()->avatar_path);
    }

    public function test_a_rider_with_no_photo_serves_a_404_rather_than_a_placeholder(): void
    {
        $this->getJson('/api/riders/'.$this->rider->id.'/avatar')->assertNotFound();
    }

    public function test_a_rider_edits_their_bike_and_can_clear_a_field(): void
    {
        $this->withToken($this->token)
            ->patchJson('/api/rider/me', [
                'vehicleType' => 'scooter',
                'vehicleMake' => 'Honda',
                'vehicleModel' => 'Click 125',
                'vehicleColor' => 'red',
            ])
            ->assertOk()
            ->assertJsonPath('rider.vehicle.type', 'scooter')
            // Colour first: it is the only part readable at fifty metres.
            ->assertJsonPath('rider.vehicle.label', 'red Honda Click 125');

        // An explicit null clears, rather than being ignored as "not sent".
        $this->withToken($this->token)
            ->patchJson('/api/rider/me', ['vehicleColor' => null])
            ->assertOk()
            ->assertJsonPath('rider.vehicle.color', null)
            ->assertJsonPath('rider.vehicle.label', 'Honda Click 125');
    }

    public function test_a_vehicle_type_outside_the_set_is_refused(): void
    {
        $this->withToken($this->token)
            ->patchJson('/api/rider/me', ['vehicleType' => 'helicopter'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('vehicleType');
    }

    public function test_a_rider_who_describes_nothing_is_described_by_their_type(): void
    {
        $this->assertSame('motorcycle', $this->rider->vehicleLabel());
    }

    public function test_registration_without_a_vehicle_type_still_works(): void
    {
        Storage::fake('local');

        // The web portal predates the column. It must not start failing.
        $this->postJson('/api/rider/register', [
            'name' => 'Ana Reyes',
            'email' => 'ana@example.com',
            'phone' => '0917 555 0102',
            'password' => 'ride-with-me-2',
            'password_confirmation' => 'ride-with-me-2',
            'licenseNumber' => 'N01-23-456780',
            'plateNumber' => 'abc 1111',
            'licenseImage' => UploadedFile::fake()->image('licence.jpg'),
            'plateImage' => UploadedFile::fake()->image('plate.jpg'),
        ])->assertCreated()->assertJsonPath('rider.vehicle.type', 'motorcycle');
    }

    public function test_support_details_are_readable_without_approval(): void
    {
        $pending = Rider::query()->create([
            'name' => 'Waiting Wendel',
            'email' => 'wendel@example.com',
            'phone' => '0917 555 0103',
            'password' => 'ride-with-me-3',
            'license_number' => 'N01-23-456781',
            'plate_number' => 'XYZ 9999',
            'license_image_path' => 'rider-documents/l.png',
            'plate_image_path' => 'rider-documents/p.png',
            'status' => Rider::STATUS_REJECTED,
        ]);

        // A rejected rider is the person most in need of somebody to ask.
        $this->withToken($pending->createToken('rider-portal', ['rider'])->plainTextToken)
            ->getJson('/api/rider/support')
            ->assertOk()
            ->assertJsonPath('email', config('support.email'));
    }
}
