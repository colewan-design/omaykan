<?php

namespace Tests\Feature\Api;

use App\Mail\SellerSignupAlertMail;
use App\Mail\SellerWelcomeMail;
use App\Models\User;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

/**
 * What a merchant and the operators hear when a store is created.
 *
 * DatabaseMigrations rather than RefreshDatabase, for the reason spelled out in
 * OnlineOrderApiTest: both mails are queued from DB::afterCommit, and a test
 * wrapped in a transaction that never commits would never fire them — the
 * assertions would pass or fail for the wrong reason.
 */
class SignupMailTest extends TestCase
{
    use DatabaseMigrations;

    private function payload(array $overrides = []): array
    {
        return array_merge([
            'businessName' => 'Hill Station Cafe',
            'ownerFullName' => 'Ana Reyes',
            'email' => 'ana@hillstation.test',
            'username' => 'anareyes',
            'password' => 'secret123',
            'businessTypeLabel' => 'Coffee shop',
            'businessMode' => 'coffee-shop',
        ], $overrides);
    }

    public function test_the_owner_is_welcomed_and_the_operators_are_alerted(): void
    {
        Mail::fake();
        config(['mail.alerts_to' => 'info@omaykan.com']);

        $this->postJson('/api/signup', $this->payload())->assertCreated();

        $owner = User::query()->where('username', 'anareyes')->firstOrFail();
        $this->assertSame('ana@hillstation.test', $owner->email);

        Mail::assertQueued(
            SellerWelcomeMail::class,
            fn (SellerWelcomeMail $mail) => $mail->hasTo('ana@hillstation.test')
                && $mail->store->name === 'Hill Station Cafe'
        );

        Mail::assertQueued(
            SellerSignupAlertMail::class,
            fn (SellerSignupAlertMail $mail) => $mail->hasTo('info@omaykan.com')
                && $mail->owner->is($owner)
        );
    }

    public function test_the_merchants_mail_replies_to_the_published_support_address(): void
    {
        Mail::fake();
        config(['mail.reply_to.address' => 'support@omaykan.com']);

        $this->postJson('/api/signup', $this->payload())->assertCreated();

        // Sent from info@ (the global From), but a reply has to reach the
        // address the app and the marketing site actually print.
        Mail::assertQueued(SellerWelcomeMail::class, function (SellerWelcomeMail $mail) {
            $replyTo = collect($mail->envelope()->replyTo)->map(fn ($address) => $address->address);

            return $replyTo->contains('support@omaykan.com');
        });

        // The alert is the exception: replying to it should reach the merchant.
        Mail::assertQueued(SellerSignupAlertMail::class, function (SellerSignupAlertMail $mail) {
            $replyTo = collect($mail->envelope()->replyTo)->map(fn ($address) => $address->address);

            return $replyTo->contains('ana@hillstation.test');
        });
    }

    public function test_the_welcome_mail_never_carries_the_store_code(): void
    {
        Mail::fake();

        $created = $this->postJson('/api/signup', $this->payload())->assertCreated()->json();

        $body = null;

        Mail::assertQueued(SellerWelcomeMail::class, function (SellerWelcomeMail $mail) use (&$body) {
            $body = $mail->render();

            return true;
        });

        // At signup the store code is also the secret a till pairs with, and
        // email is a durable, forwardable channel. The owner is pointed at
        // Settings > Online Store instead. See SellerWelcomeMail.
        $this->assertNotEmpty($created['pairingCode']);
        $this->assertStringNotContainsString($created['pairingCode'], (string) $body);

        // It is still a useful mail, though.
        $this->assertStringContainsString('Hill Station Cafe', (string) $body);
        $this->assertStringContainsString('anareyes', (string) $body);
    }

    public function test_the_welcome_mail_uses_the_typed_business_label(): void
    {
        Mail::fake();

        $this->postJson('/api/signup', $this->payload([
            'businessTypeLabel' => 'Bakery',
            'businessMode' => 'coffee-shop',
        ]))->assertCreated();

        Mail::assertQueued(SellerWelcomeMail::class, function (SellerWelcomeMail $mail) {
            return str_contains($mail->render(), 'Bakery');
        });
    }

    public function test_the_operator_alert_can_be_switched_off(): void
    {
        Mail::fake();
        config(['mail.alerts_to' => '']);

        $this->postJson('/api/signup', $this->payload())->assertCreated();

        Mail::assertQueued(SellerWelcomeMail::class);
        Mail::assertNotQueued(SellerSignupAlertMail::class);
    }

    public function test_an_email_is_required_and_cannot_be_reused(): void
    {
        $missing = $this->payload();
        unset($missing['email']);

        $this->postJson('/api/signup', $missing)
            ->assertStatus(422)
            ->assertJsonValidationErrors('email');

        $this->postJson('/api/signup', $this->payload())->assertCreated();

        // A 422 rather than the 500 the unique index would have produced.
        $this->postJson('/api/signup', $this->payload([
            'businessName' => 'Another Cafe',
            'username' => 'someoneelse',
        ]))->assertStatus(422)->assertJsonValidationErrors('email');
    }

    public function test_a_signup_that_fails_tells_nobody(): void
    {
        Mail::fake();

        $this->postJson('/api/signup', $this->payload(['businessName' => '']))
            ->assertStatus(422);

        Mail::assertNothingQueued();
    }
}
