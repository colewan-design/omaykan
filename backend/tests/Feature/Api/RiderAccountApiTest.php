<?php

namespace Tests\Feature\Api;

use App\Models\Rider;
use App\Notifications\RiderPasswordReset;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Notification;
use Tests\TestCase;

/**
 * The account behind the token: what a rider can change about themselves, and
 * the way back in when they cannot sign in at all.
 *
 * The recovery half is the one worth having tests for. Before it existed a
 * forgotten password was terminal — no reset endpoint, no operator screen that
 * sets one, and no way to register again because the address is already taken
 * by the account they are locked out of.
 */
class RiderAccountApiTest extends TestCase
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

    public function test_a_rider_can_change_their_name_phone_and_plate(): void
    {
        $this->withToken($this->token)
            ->patchJson('/api/rider/me', [
                'name' => 'Jun D. Cruz',
                'phone' => '0917 555 0199',
                'plateNumber' => 'bgo 5678',
            ])
            ->assertOk()
            ->assertJsonPath('rider.name', 'Jun D. Cruz')
            ->assertJsonPath('rider.phone', '0917 555 0199')
            // Upper-cased on the way in, so the shop's screen and the rider's
            // own never disagree about the same plate typed two ways.
            ->assertJsonPath('rider.plateNumber', 'BGO 5678');
    }

    public function test_the_email_and_licence_number_cannot_be_changed_here(): void
    {
        $this->withToken($this->token)
            ->patchJson('/api/rider/me', [
                'email' => 'someone-else@example.com',
                'licenseNumber' => 'FAKE-000',
            ])
            ->assertOk();

        $this->rider->refresh();

        // Silently ignored rather than refused: they are not in the validated
        // set, so they never reach the model. The portal does not offer them.
        $this->assertSame('jun@example.com', $this->rider->email);
        $this->assertSame('N01-23-456789', $this->rider->license_number);
    }

    public function test_a_pending_rider_can_still_fix_their_details(): void
    {
        $this->rider->forceFill(['status' => Rider::STATUS_PENDING])->save();

        // The point of putting this route outside `rider.approved`: the rider
        // most likely to be correcting a mistyped phone number is the one still
        // waiting to be approved on it.
        $this->withToken($this->token)
            ->patchJson('/api/rider/me', ['phone' => '0917 555 0102'])
            ->assertOk()
            ->assertJsonPath('rider.phone', '0917 555 0102');
    }

    public function test_changing_a_password_needs_the_current_one(): void
    {
        $this->withToken($this->token)
            ->patchJson('/api/rider/password', [
                'currentPassword' => 'not-the-password',
                'password' => 'a-brand-new-one-1',
                'password_confirmation' => 'a-brand-new-one-1',
            ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('currentPassword');
    }

    public function test_changing_a_password_signs_out_the_other_phones_but_not_this_one(): void
    {
        $otherPhone = $this->rider->createToken('rider-portal', ['rider'])->plainTextToken;

        $this->withToken($this->token)
            ->patchJson('/api/rider/password', [
                'currentPassword' => 'ride-with-me-1',
                'password' => 'a-brand-new-one-1',
                'password_confirmation' => 'a-brand-new-one-1',
            ])
            ->assertOk();

        $this->assertTrue(Hash::check('a-brand-new-one-1', $this->rider->fresh()->password));

        // The one that made the change keeps working — a rider mid-delivery
        // must not be dropped onto the sign-in screen for tightening their own
        // security.
        $this->withToken($this->token)->getJson('/api/rider/me')->assertOk();

        // Every other one is gone.
        $this->withToken($otherPhone)->getJson('/api/rider/me')->assertUnauthorized();
    }

    public function test_forgot_password_emails_a_link(): void
    {
        Notification::fake();

        $this->postJson('/api/rider/forgot-password', ['email' => 'JUN@example.com'])
            ->assertOk();

        Notification::assertSentTo($this->rider, RiderPasswordReset::class);

        // Its own table, not the shared one — see the migration.
        $this->assertDatabaseCount('rider_password_reset_tokens', 1);
    }

    public function test_forgot_password_says_the_same_thing_for_an_unknown_address(): void
    {
        Notification::fake();

        $known = $this->postJson('/api/rider/forgot-password', ['email' => 'jun@example.com']);
        $unknown = $this->postJson('/api/rider/forgot-password', ['email' => 'nobody@example.com']);

        // The platform's riders are a small, knowable set. A reply that
        // distinguished them would be a way to enumerate it.
        $unknown->assertOk();
        $this->assertSame($known->json('message'), $unknown->json('message'));

        Notification::assertSentTimes(RiderPasswordReset::class, 1);
    }

    public function test_a_reset_sets_the_password_and_kills_every_token(): void
    {
        $token = $this->resetTokenFor('jun@example.com');

        $this->postJson('/api/rider/reset-password', [
            'token' => $token,
            'email' => 'jun@example.com',
            'password' => 'chosen-after-a-reset-1',
            'password_confirmation' => 'chosen-after-a-reset-1',
        ])->assertOk();

        $this->assertTrue(Hash::check('chosen-after-a-reset-1', $this->rider->fresh()->password));

        // Everything, including the phone that asked. Somebody resetting a
        // password may be doing it because someone else has the old one.
        $this->withToken($this->token)->getJson('/api/rider/me')->assertUnauthorized();
    }

    public function test_a_reset_token_cannot_be_spent_twice(): void
    {
        $token = $this->resetTokenFor('jun@example.com');

        $payload = [
            'token' => $token,
            'email' => 'jun@example.com',
            'password' => 'chosen-after-a-reset-1',
            'password_confirmation' => 'chosen-after-a-reset-1',
        ];

        $this->postJson('/api/rider/reset-password', $payload)->assertOk();

        $this->postJson('/api/rider/reset-password', $payload)
            ->assertStatus(422)
            ->assertJsonValidationErrors('token');
    }

    /**
     * The plaintext token, captured on its way into the mail.
     *
     * The table stores a hash, so it cannot be read back out — this listens for
     * the notification the same way the rider's mailbox would.
     */
    private function resetTokenFor(string $email): string
    {
        $captured = '';

        Notification::fake();
        $this->postJson('/api/rider/forgot-password', ['email' => $email])->assertOk();

        Notification::assertSentTo(
            $this->rider,
            function (RiderPasswordReset $notification) use (&$captured) {
                $captured = (string) (new \ReflectionProperty($notification, 'token'))
                    ->getValue($notification);

                return true;
            },
        );

        $this->assertNotSame('', $captured);
        $this->assertSame(1, DB::table('rider_password_reset_tokens')->count());

        return $captured;
    }
}
