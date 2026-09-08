<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\Subscription;
use App\Models\User;
use App\Models\Store;
use App\Notifications\SellerEmailVerification;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Notification;
use Tests\TestCase;

/**
 * Covers the replacement for api/signup.ts.
 *
 * DatabaseMigrations rather than RefreshDatabase because signup queues mail
 * and verification after commit, and those callbacks must actually fire here.
 */
class SignupApiTest extends TestCase
{
    use DatabaseMigrations;

    private function payload(array $overrides = []): array
    {
        $merged = array_merge([
            'businessName' => 'Hill Station Cafe',
            'ownerFullName' => 'Ana Reyes',
            'username' => 'AnaReyes',
            'password' => 'secret123',
            'businessTypeLabel' => 'Coffee shop',
            'businessMode' => 'coffee-shop',
            'gcashReference' => 'GC-99881',
        ], $overrides);

        return $merged + ['email' => strtolower($merged['username']).'@example.test'];
    }

    public function test_signup_creates_a_working_store_in_one_shot(): void
    {
        $response = $this->postJson('/api/signup', $this->payload())->assertCreated();

        $response->assertJsonStructure(['organizationSlug', 'storeCode']);
        // Signup used to hand back a shop-wide code that was both the
        // customer's way in and the till's pairing secret. There is no such
        // value any more, and the reply must not grow one back.
        $response->assertJsonMissingPath('pairingCode');
        $response->assertJsonPath('verificationRequired', true);
        $response->assertJsonPath('message', 'Check your email for a verification link before signing in.');
        $this->assertSame('hill-station-cafe', $response->json('organizationSlug'));
        $this->assertSame('main', $response->json('storeCode'));

        $organization = Organization::query()->where('slug', 'hill-station-cafe')->firstOrFail();
        $store = Store::query()->where('organization_id', $organization->id)->firstOrFail();

        $this->assertSame('coffee-shop', $store->business_mode);
        $this->assertSame('Coffee shop', $store->business_type_label);
        $this->assertFalse($organization->suspended);

        $owner = User::query()->where('username', 'anareyes')->firstOrFail();
        $this->assertNull($owner->email_verified_at);
        $this->assertDatabaseHas('organization_memberships', [
            'organization_id' => $organization->id,
            'user_id' => $owner->id,
            'membership_role' => 'admin',
        ]);
        $this->assertDatabaseHas('store_memberships', [
            'store_id' => $store->id,
            'user_id' => $owner->id,
            'membership_role' => 'admin',
        ]);

        $subscription = Subscription::query()->where('organization_id', $organization->id)->firstOrFail();
        $this->assertSame(Subscription::STATUS_PENDING, $subscription->status);
        $this->assertSame(49900, $subscription->amount_cents);
        $this->assertSame('GC-99881', $subscription->gcash_reference);
        $this->assertNull($subscription->verified_at);
    }

    public function test_the_new_owner_must_verify_email_before_signing_in(): void
    {
        Notification::fake();
        config([
            'app.url' => 'http://omaykan.test',
            'mail.reply_to.address' => 'support@omaykan.com',
        ]);

        $created = $this->postJson('/api/signup', $this->payload())->assertCreated()->json();

        // Not yet in the shop directory, and rightly so: it has nothing on the
        // shelf, and StoreDirectoryController leaves an empty shop out rather
        // than listing a dead click. That rule has its own test.
        $this->assertDatabaseHas('stores', [
            'name' => 'Hill Station Cafe',
            'code' => 'main',
            'status' => 'active',
        ]);

        $this->postJson('/api/staff/sign-in', [
            'identifier' => 'anareyes',
            'password' => 'secret123',
        ])->assertForbidden()->assertJsonPath(
            'message',
            'Please verify your email first. We just sent you another verification link.'
        );

        $owner = User::query()->where('username', 'anareyes')->firstOrFail();
        $verificationUrl = null;

        Notification::assertSentTo($owner, SellerEmailVerification::class, function (SellerEmailVerification $notification) use ($owner, &$verificationUrl) {
            $mail = $notification->toMail($owner);
            $verificationUrl = $mail->actionUrl;

            $this->assertSame('support@omaykan.com', $mail->from[0] ?? null);
            $this->assertStringContainsString('/email/verify/seller/', $verificationUrl);

            return true;
        });

        $this->get($verificationUrl)->assertRedirect('http://omaykan.test/app?verified=1');

        $this->assertNotNull($owner->fresh()->email_verified_at);

        $this->postJson('/api/staff/sign-in', [
            'identifier' => 'anareyes',
            'password' => 'secret123',
        ])->assertOk()->assertJsonPath('stores.0.role', 'admin');
    }

    /**
     * Signup issues no code of any kind now — the owner's own account is the
     * way in, and it reaches exactly the one store the signup created.
     */
    public function test_the_new_owner_can_open_their_store_with_their_own_account(): void
    {
        $created = $this->postJson('/api/signup', $this->payload())->assertCreated()->json();

        $owner = User::query()->where('username', 'anareyes')->firstOrFail();
        $owner->forceFill(['email_verified_at' => now()])->save();

        $signIn = $this->postJson('/api/staff/sign-in', [
            'identifier' => 'anareyes',
            'password' => 'secret123',
        ])->assertOk();

        $this->assertCount(1, $signIn->json('stores'));

        $token = $this->withToken($signIn->json('token'))
            ->postJson('/api/staff/session-store', ['storeId' => $signIn->json('stores.0.id')])
            ->assertOk()
            ->assertJsonPath('store.organizationSlug', $created['organizationSlug'])
            ->json('token');

        $this->withToken($token)->getJson('/api/seller/online-orders')->assertOk();
    }

    public function test_duplicate_business_names_get_distinct_slugs(): void
    {
        $first = $this->postJson('/api/signup', $this->payload())->assertCreated()->json();
        $second = $this->postJson('/api/signup', $this->payload(['username' => 'someoneelse']))
            ->assertCreated()->json();

        $this->assertSame('hill-station-cafe', $first['organizationSlug']);
        $this->assertSame('hill-station-cafe-2', $second['organizationSlug']);
    }

    public function test_a_custom_business_type_label_can_differ_from_the_starter_setup(): void
    {
        $response = $this->postJson('/api/signup', $this->payload([
            'businessTypeLabel' => 'Bakery',
            'businessMode' => 'coffee-shop',
        ]))->assertCreated();

        $organization = Organization::query()->where('slug', $response->json('organizationSlug'))->firstOrFail();
        $store = Store::query()->where('organization_id', $organization->id)->firstOrFail();

        $this->assertSame('coffee-shop', $store->business_mode);
        $this->assertSame('Bakery', $store->business_type_label);
    }

    public function test_taken_username_is_rejected(): void
    {
        $this->postJson('/api/signup', $this->payload())->assertCreated();

        $this->postJson('/api/signup', $this->payload(['businessName' => 'Another Cafe']))
            ->assertStatus(422)
            ->assertJsonValidationErrors('username');
    }

    public function test_username_is_matched_case_insensitively(): void
    {
        $this->postJson('/api/signup', $this->payload())->assertCreated();

        $this->postJson('/api/signup', $this->payload([
            'businessName' => 'Another Cafe',
            'username' => 'ANAREYES',
        ]))->assertStatus(422)->assertJsonValidationErrors('username');
    }

    public function test_short_password_and_bad_business_mode_are_rejected(): void
    {
        $this->postJson('/api/signup', $this->payload(['password' => 'abc']))
            ->assertStatus(422)
            ->assertJsonValidationErrors('password');

        $this->postJson('/api/signup', $this->payload(['businessMode' => 'laundromat']))
            ->assertStatus(422)
            ->assertJsonValidationErrors('businessMode');
    }

    public function test_the_form_can_sign_up_without_a_payment_reference(): void
    {
        $payload = $this->payload();
        unset($payload['gcashReference']);

        $response = $this->postJson('/api/signup', $payload)->assertCreated();

        $organization = Organization::query()->where('slug', $response->json('organizationSlug'))->firstOrFail();
        $subscription = Subscription::query()->where('organization_id', $organization->id)->firstOrFail();

        $this->assertSame(Subscription::STATUS_PENDING, $subscription->status);
        $this->assertSame('', $subscription->gcash_reference);
    }

    public function test_a_failed_signup_leaves_nothing_behind(): void
    {
        $this->postJson('/api/signup', $this->payload(['businessName' => '']))
            ->assertStatus(422);

        $this->assertDatabaseCount('organizations', 0);
        $this->assertDatabaseCount('users', 0);
        $this->assertDatabaseCount('subscriptions', 0);
    }

    public function test_a_nail_salon_can_sign_up_but_is_not_listed_for_customers(): void
    {
        $this->postJson('/api/signup', $this->payload(['businessMode' => 'nail-salon']))
            ->assertCreated();

        // Its owner still gets in — the sign-in door has no business-mode gate,
        // deliberately.
        $owner = User::query()->where('username', 'anareyes')->firstOrFail();
        $owner->forceFill(['email_verified_at' => now()])->save();

        $this->postJson('/api/staff/sign-in', [
            'identifier' => 'anareyes',
            'password' => 'secret123',
        ])->assertOk();

        // The shop is not somewhere a customer can order from, so it is not in
        // the list of places to order from.
        $this->getJson('/api/stores')->assertOk()->assertJsonCount(0, 'stores');
    }
}
