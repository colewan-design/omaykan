<?php

namespace Tests\Feature\Api;

use App\Mail\SellerApplicationAlertMail;
use App\Mail\SellerApplicationReceivedMail;
use App\Models\SellerApplication;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

/**
 * The founding-seller campaign endpoints behind `/seller/founding`.
 *
 * DatabaseMigrations rather than RefreshDatabase, matching SignupMailTest:
 * these queue mail, and a test wrapped in a transaction that never commits
 * would make those assertions pass or fail for the wrong reason.
 */
class FoundingSellerApiTest extends TestCase
{
    use DatabaseMigrations;

    private function payload(array $overrides = []): array
    {
        return array_merge([
            'businessName' => 'Aling Rosa Pasalubong',
            'ownerName' => 'Maria Santos',
            'category' => 'Handicrafts / souvenirs',
            'mobile' => '0917 123 4567',
            'email' => 'Maria@AlingRosa.test',
            'socialUrl' => '@alingrosa',
            'address' => 'Poblacion, La Trinidad, Benguet',
            'productsDescription' => 'Woven bags, strawberry jam, peanut brittle.',
            'offersDelivery' => false,
            'wantsFounding' => true,
        ], $overrides);
    }

    public function test_status_reports_an_open_campaign_and_the_categories_the_form_offers(): void
    {
        $response = $this->getJson('/api/founding-sellers/status')->assertOk();

        $response->assertJsonPath('limit', SellerApplication::FOUNDING_LIMIT);
        $response->assertJsonPath('claimed', 0);
        $response->assertJsonPath('remaining', SellerApplication::FOUNDING_LIMIT);
        $response->assertJsonPath('open', true);

        // The form's category dropdown is built from this, and `store` rejects
        // anything outside it — an empty list would be a page that cannot be
        // submitted at all.
        $this->assertNotEmpty($response->json('categories'));
        $this->assertContains('Sari-sari / grocery', $response->json('categories'));
    }

    public function test_an_application_is_recorded_and_normalised(): void
    {
        $response = $this->postJson('/api/founding-sellers/apply', $this->payload())
            ->assertCreated();

        $response->assertJsonPath('businessName', 'Aling Rosa Pasalubong');
        $response->assertJsonPath('foundingOpen', true);
        $response->assertJsonPath('remaining', SellerApplication::FOUNDING_LIMIT);

        $application = SellerApplication::query()->firstOrFail();
        $this->assertSame('maria@alingrosa.test', $application->email);
        $this->assertSame('Maria Santos', $application->owner_name);
        $this->assertSame('@alingrosa', $application->social_url);
        $this->assertTrue($application->wants_founding);
        $this->assertFalse($application->offers_delivery);

        // Nothing is set up by applying: the campaign promises a person does
        // that afterwards, so this must stay an enquiry and nothing more.
        $this->assertSame(SellerApplication::STATUS_PENDING, $application->status);
        $this->assertNull($application->founding_number);
        $this->assertDatabaseCount('organizations', 0);
        $this->assertDatabaseCount('users', 0);
    }

    /**
     * The box is a reason to apply, not a condition of applying. This used to
     * be dropped silently: the controller wrote the camelCase key, which is
     * not fillable, so every application was stored as wanting a place.
     */
    public function test_an_applicant_who_skips_the_founding_box_is_recorded_as_having_skipped_it(): void
    {
        $this->postJson('/api/founding-sellers/apply', $this->payload(['wantsFounding' => false]))
            ->assertCreated();

        $this->assertFalse(SellerApplication::query()->firstOrFail()->wants_founding);
    }

    public function test_a_blank_social_handle_is_stored_as_nothing_rather_than_an_empty_string(): void
    {
        $this->postJson('/api/founding-sellers/apply', $this->payload(['socialUrl' => '   ']))
            ->assertCreated();

        $this->assertNull(SellerApplication::query()->firstOrFail()->social_url);
    }

    public function test_a_category_the_form_does_not_offer_is_refused(): void
    {
        $this->postJson('/api/founding-sellers/apply', $this->payload(['category' => 'Crypto mining']))
            ->assertStatus(422)
            ->assertJsonValidationErrors('category');

        $this->assertDatabaseCount('seller_applications', 0);
    }

    public function test_the_description_is_held_to_the_length_the_form_counts_down_from(): void
    {
        $this->postJson('/api/founding-sellers/apply', $this->payload([
            'productsDescription' => str_repeat('a', 501),
        ]))->assertStatus(422)->assertJsonValidationErrors('productsDescription');
    }

    /**
     * Numbers arrive as 0917…, +63 917…, with spaces and dashes. Whatever the
     * applicant typed is what an operator dials, so it is kept verbatim.
     */
    public function test_a_mobile_number_is_kept_the_way_it_was_typed(): void
    {
        $this->postJson('/api/founding-sellers/apply', $this->payload(['mobile' => '+63 917-123-4567']))
            ->assertCreated();

        $this->assertSame('+63 917-123-4567', SellerApplication::query()->firstOrFail()->mobile);
    }

    public function test_the_applicant_is_thanked_and_the_operators_are_alerted(): void
    {
        Mail::fake();
        config(['mail.alerts_to' => 'info@omaykan.com']);

        $this->postJson('/api/founding-sellers/apply', $this->payload())->assertCreated();

        Mail::assertQueued(
            SellerApplicationReceivedMail::class,
            fn (SellerApplicationReceivedMail $mail) => $mail->hasTo('maria@alingrosa.test')
                && $mail->application->business_name === 'Aling Rosa Pasalubong'
        );

        Mail::assertQueued(
            SellerApplicationAlertMail::class,
            fn (SellerApplicationAlertMail $mail) => $mail->hasTo('info@omaykan.com')
                // Replying to the alert has to reach the shop, not support.
                && $mail->envelope()->replyTo[0]->address === 'maria@alingrosa.test'
        );
    }

    /**
     * Mail::fake never renders a view, so a broken Blade in either message
     * would pass every assertion above and only surface on the live queue.
     * These render both for real.
     */
    public function test_both_messages_render(): void
    {
        $this->postJson('/api/founding-sellers/apply', $this->payload())->assertCreated();
        $application = SellerApplication::query()->firstOrFail();

        $receipt = (new SellerApplicationReceivedMail($application))->render();
        $this->assertStringContainsString('Aling Rosa Pasalubong', $receipt);
        $this->assertStringContainsString('0917 123 4567', $receipt);
        // The receipt must never name a number: none has been issued yet.
        $this->assertStringNotContainsString('Founding Seller #', $receipt);

        $alert = (new SellerApplicationAlertMail($application, claimed: 3))->render();
        $this->assertStringContainsString('Aling Rosa Pasalubong', $alert);
        $this->assertStringContainsString('Woven bags', $alert);
        $this->assertStringContainsString('maria@alingrosa.test', $alert);
        // 30 - 3 issued.
        $this->assertStringContainsString('27 of 30', $alert);
    }

    /**
     * The receipt's founding paragraph belongs only to the people who asked
     * for a place. Showing it to everyone would promise a badge to a business
     * that explicitly did not want one.
     */
    public function test_the_receipt_only_mentions_the_badge_when_it_was_asked_for(): void
    {
        $wanted = SellerApplication::query()->create($this->stored(['wants_founding' => true]));
        $skipped = SellerApplication::query()->create($this->stored(['wants_founding' => false]));

        $this->assertStringContainsString(
            'founding-seller number',
            (new SellerApplicationReceivedMail($wanted))->render(),
        );
        $this->assertStringNotContainsString(
            'founding-seller number',
            (new SellerApplicationReceivedMail($skipped))->render(),
        );
    }

    /**
     * A mailbox refusing connections must not turn a recorded application into
     * a failed request — the row is already written, and an error here would
     * have somebody filling the form in a second time.
     */
    public function test_a_failing_mailer_does_not_fail_the_application(): void
    {
        config(['mail.alerts_to' => 'info@omaykan.com']);
        Mail::shouldReceive('to')->andThrow(new \RuntimeException('smtp is down'));

        $this->postJson('/api/founding-sellers/apply', $this->payload())->assertCreated();

        $this->assertDatabaseCount('seller_applications', 1);
    }

    /**
     * The counter is badges issued, not applications received. Thirty people
     * filling the form in does not close a campaign nobody has accepted anyone
     * into — and the page must not tell the thirty-first that it has.
     */
    public function test_the_counter_moves_on_issued_badges_not_on_applications(): void
    {
        SellerApplication::query()->create($this->stored(['email' => 'one@example.test']));
        SellerApplication::query()->create($this->stored(['email' => 'two@example.test']));

        $this->getJson('/api/founding-sellers/status')
            ->assertOk()
            ->assertJsonPath('claimed', 0)
            ->assertJsonPath('remaining', SellerApplication::FOUNDING_LIMIT);

        $accepted = SellerApplication::query()->firstOrFail();
        $accepted->forceFill(['founding_number' => 7, 'status' => SellerApplication::STATUS_ACCEPTED])->save();

        $this->getJson('/api/founding-sellers/status')
            ->assertOk()
            ->assertJsonPath('claimed', 1)
            ->assertJsonPath('remaining', SellerApplication::FOUNDING_LIMIT - 1)
            ->assertJsonPath('open', true);
    }

    public function test_a_full_campaign_closes_without_refusing_the_application(): void
    {
        for ($number = 1; $number <= SellerApplication::FOUNDING_LIMIT; $number++) {
            SellerApplication::query()->create($this->stored([
                'email' => "seller{$number}@example.test",
            ]))->forceFill([
                'founding_number' => $number,
                'status' => SellerApplication::STATUS_ACCEPTED,
            ])->save();
        }

        $this->getJson('/api/founding-sellers/status')
            ->assertOk()
            ->assertJsonPath('remaining', 0)
            ->assertJsonPath('open', false);

        // Still accepted, and told honestly that the campaign is full. Turning
        // these away would lose the business once the first thirty are gone.
        $this->postJson('/api/founding-sellers/apply', $this->payload())
            ->assertCreated()
            ->assertJsonPath('foundingOpen', false)
            ->assertJsonPath('remaining', 0);
    }

    /** The column names, for the rows this test seeds directly. */
    private function stored(array $overrides = []): array
    {
        return array_merge([
            'business_name' => 'A Shop',
            'owner_name' => 'An Owner',
            'business_category' => 'Sari-sari / grocery',
            'mobile' => '0917 000 0000',
            'email' => 'shop@example.test',
            'address' => 'Baguio City',
            'products_description' => 'Everyday goods.',
            'offers_delivery' => false,
            'wants_founding' => true,
        ], $overrides);
    }
}
