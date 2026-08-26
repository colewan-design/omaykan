<?php

namespace Tests\Feature\Api;

use App\Models\PlatformAdmin;
use App\Models\Rider;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

/**
 * Rider registration, sign-in, and the operator review that stands between the
 * two and any actual work.
 */
class RiderAuthApiTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        // Documents are written to the private disk; faking it keeps real
        // files out of storage/ and lets the assertions look at what landed.
        Storage::fake('local');
    }

    /**
     * The reviewer's side of these tests is a signed-in platform operator, not
     * a shared secret — see the platform guard in config/auth.php.
     *
     * @return array<string, string>
     */
    private function operatorHeader(): array
    {
        $admin = PlatformAdmin::query()->create([
            'name' => 'Platform Operator',
            'email' => 'ops@omaykan.test',
            'password' => 'operator-password-1',
            'role' => PlatformAdmin::ROLE_OWNER,
            'status' => PlatformAdmin::STATUS_ACTIVE,
        ]);

        return ['Authorization' => 'Bearer '.$admin
            ->createToken(PlatformAdmin::TOKEN_NAME, ['platform'])->plainTextToken];
    }

    /** @return array<string, mixed> */
    private function payload(array $overrides = []): array
    {
        return array_merge([
            'name' => 'Jun Dela Cruz',
            'email' => 'jun@example.com',
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'password_confirmation' => 'ride-with-me-1',
            'licenseNumber' => 'n01-23-456789',
            'plateNumber' => 'nbc 1234',
            'licenseImage' => UploadedFile::fake()->image('license.jpg', 900, 600),
            'plateImage' => UploadedFile::fake()->image('plate.jpg', 900, 600),
        ], $overrides);
    }

    /**
     * A multipart POST, so `postJson` is out — it would send the files as
     * JSON. Without an explicit Accept, a validation failure redirects (302)
     * instead of answering 422.
     *
     * @return \Illuminate\Testing\TestResponse
     */
    private function submit(array $overrides = [])
    {
        return $this->post('/api/rider/register', $this->payload($overrides), [
            'Accept' => 'application/json',
        ]);
    }

    private function register(array $overrides = []): array
    {
        return $this->submit($overrides)->assertCreated()->json();
    }

    public function test_a_rider_registers_with_documents_and_starts_pending(): void
    {
        $body = $this->register();

        $this->assertNotEmpty($body['token']);
        $this->assertSame(Rider::STATUS_PENDING, $body['rider']['status']);
        // Normalised on the way in, so a licence typed in lower case and a
        // plate typed with stray spacing still match what an operator reads.
        $this->assertSame('N01-23-456789', $body['rider']['licenseNumber']);
        $this->assertSame('NBC 1234', $body['rider']['plateNumber']);

        $rider = Rider::findByEmail('jun@example.com');
        $this->assertNotNull($rider);
        Storage::disk('local')->assertExists($rider->license_image_path);
        Storage::disk('local')->assertExists($rider->plate_image_path);

        // The uploader's filename never reaches the disk.
        $this->assertStringNotContainsString('license.jpg', $rider->license_image_path);
    }

    public function test_the_documents_are_required_and_must_be_images(): void
    {
        $this->submit(['licenseImage' => null])
            ->assertStatus(422)
            ->assertJsonValidationErrors('licenseImage');

        // A PHP file wearing a .jpg name is what `image` is there to stop.
        $this->submit([
            'plateImage' => UploadedFile::fake()->create('payload.jpg', 12, 'application/x-php'),
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('plateImage');
    }

    public function test_email_is_taken_case_insensitively(): void
    {
        $this->register();

        $this->submit(['email' => 'JUN@Example.com'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('email');
    }

    public function test_a_pending_rider_can_read_their_status_but_not_the_board(): void
    {
        $token = $this->register()['token'];

        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/rider/me')
            ->assertOk()
            ->assertJsonPath('rider.status', Rider::STATUS_PENDING);

        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/rider/board')
            ->assertStatus(403)
            ->assertJsonPath('riderStatus', Rider::STATUS_PENDING);
    }

    public function test_a_rider_token_cannot_reach_the_seller_api(): void
    {
        $token = $this->register()['token'];

        // A rider token gets nothing from the seller API. It is refused at the
        // controller's `instanceof Device` check rather than by the guard —
        // `auth:sanctum` has to resolve both Users and Devices, so it cannot
        // be pinned to one provider the way `rider` and `customer` are.
        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/seller/online-orders')
            ->assertForbidden();
    }

    public function test_login_is_the_same_answer_for_unknown_email_and_wrong_password(): void
    {
        $this->register();

        $unknown = $this->postJson('/api/rider/login', [
            'email' => 'nobody@example.com',
            'password' => 'ride-with-me-1',
        ])->assertStatus(422);

        $wrong = $this->postJson('/api/rider/login', [
            'email' => 'jun@example.com',
            'password' => 'not-the-password',
        ])->assertStatus(422);

        $this->assertSame(
            $unknown->json('errors.email'),
            $wrong->json('errors.email'),
        );
    }

    public function test_the_operator_reviews_the_queue_and_approves(): void
    {
        $this->register();

        $headers = $this->operatorHeader();

        $queue = $this->getJson('/api/platform/riders', $headers)
            ->assertOk()
            ->json('riders');

        $this->assertCount(1, $queue);
        $this->assertSame(Rider::STATUS_PENDING, $queue[0]['status']);
        $this->assertSame(0, $queue[0]['deliveriesCompleted']);
        // Nothing in the review payload addresses the files on disk.
        $this->assertArrayNotHasKey('license_image_path', $queue[0]);

        $riderId = $queue[0]['id'];

        $this->postJson("/api/platform/riders/{$riderId}/decision", [
            'status' => Rider::STATUS_APPROVED,
        ], $headers)->assertOk()->assertJsonPath('rider.status', Rider::STATUS_APPROVED);

        $token = $this->postJson('/api/rider/login', [
            'email' => 'jun@example.com',
            'password' => 'ride-with-me-1',
        ])->assertOk()->json('token');

        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/rider/board')
            ->assertOk();
    }

    public function test_suspending_a_rider_kills_their_live_tokens(): void
    {
        $token = $this->register()['token'];
        $rider = Rider::findByEmail('jun@example.com');
        $rider->forceFill(['status' => Rider::STATUS_APPROVED])->save();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/rider/board')
            ->assertOk();

        $this->postJson("/api/platform/riders/{$rider->id}/decision", [
            'status' => Rider::STATUS_SUSPENDED,
            'note' => 'Repeated no-shows.',
        ], $this->operatorHeader())->assertOk();

        $this->assertSame(0, $rider->fresh()->tokens()->count());

        // A test reuses one container across requests, so the guard still
        // holds the rider it resolved a moment ago. Production gets a fresh
        // container per request; this is what that looks like.
        $this->app['auth']->forgetGuards();

        // Not merely blocked at the gate — the token itself is gone, so a
        // suspension takes effect on the next request rather than at expiry.
        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/rider/board')
            ->assertUnauthorized();

        // And they can still sign in to read why.
        $this->postJson('/api/rider/login', [
            'email' => 'jun@example.com',
            'password' => 'ride-with-me-1',
        ])->assertOk()->assertJsonPath('rider.reviewNote', 'Repeated no-shows.');
    }

    public function test_documents_are_only_served_to_the_operator(): void
    {
        $riderToken = $this->register()['token'];
        $rider = Rider::findByEmail('jun@example.com');
        $headers = $this->operatorHeader();

        // No token at all, and the rider's own token — a licence photo is
        // operator-only, and the rider is not an exception to that.
        //
        // getJson rather than get for the refusals: Laravel's Authenticate
        // middleware redirects a non-JSON unauthenticated request to the
        // `login` route, which this API does not have. The portal always sends
        // Accept: application/json for the same reason, including on the blob
        // fetch that pulls these images.
        $this->getJson("/api/platform/riders/{$rider->id}/document/license")
            ->assertUnauthorized();

        $this->withHeader('Authorization', "Bearer {$riderToken}")
            ->getJson("/api/platform/riders/{$rider->id}/document/license")
            ->assertUnauthorized();

        $this->get("/api/platform/riders/{$rider->id}/document/license", $headers)->assertOk();

        // Laravel reorders and adds to the directive list, so the assertion is
        // on the directive that matters, not the whole header string.
        $this->assertStringContainsString(
            'no-store',
            $this->get("/api/platform/riders/{$rider->id}/document/license", $headers)
                ->headers->get('Cache-Control'),
        );

        // The document name is a fixed set, not a path off the request.
        $this->get("/api/platform/riders/{$rider->id}/document/passport", $headers)
            ->assertNotFound();
    }
}
