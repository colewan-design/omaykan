<?php

namespace Tests\Feature\Api;

use App\Models\AppRelease;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * The update check, and the command that feeds it.
 *
 * Both are tested together because the failure worth catching spans them: a
 * build recorded wrongly serves silently, and the endpoint has no way to know.
 */
class AppReleaseApiTest extends TestCase
{
    use RefreshDatabase;

    private function record(
        int $code,
        string $name,
        string $slug = AppRelease::STOREFRONT_ANDROID,
        bool $published = true,
        ?string $publishedAt = null,
    ): AppRelease {
        return AppRelease::query()->create([
            'slug' => $slug,
            'version_code' => $code,
            'version_name' => $name,
            'apk_url' => "https://omaykan.com/apk/{$slug}-{$name}.apk",
            'notes' => "What changed in {$name}.",
            'published_at' => $published ? ($publishedAt ?? now()) : null,
        ]);
    }

    public function test_it_serves_the_published_build(): void
    {
        $this->record(2, '2.0.0');

        $this->getJson('/api/app-releases/storefront-android')
            ->assertOk()
            ->assertJson([
                'slug' => 'storefront-android',
                'versionCode' => 2,
                'versionName' => '2.0.0',
                'apkUrl' => 'https://omaykan.com/apk/storefront-android-2.0.0.apk',
                'notes' => 'What changed in 2.0.0.',
            ]);
    }

    /** The client treats this as "up to date", so it must not be a 500. */
    public function test_nothing_published_is_a_404(): void
    {
        $this->getJson('/api/app-releases/storefront-android')->assertNotFound();
    }

    public function test_an_unknown_app_is_a_404(): void
    {
        $this->record(2, '2.0.0');

        $this->getJson('/api/app-releases/rider-android')->assertNotFound();
    }

    public function test_a_junk_slug_is_a_404_not_an_error(): void
    {
        $this->getJson('/api/app-releases/' . urlencode('../../etc/passwd'))->assertNotFound();
    }

    /** A draft row exists but is not announced. */
    public function test_a_draft_is_not_served(): void
    {
        $this->record(2, '2.0.0', published: false);

        $this->getJson('/api/app-releases/storefront-android')->assertNotFound();
    }

    public function test_a_future_publication_is_not_served_yet(): void
    {
        $this->record(2, '2.0.0', publishedAt: now()->addDay()->toDateTimeString());
        $this->record(1, '1.0.0');

        $this->getJson('/api/app-releases/storefront-android')
            ->assertOk()
            ->assertJsonPath('versionCode', 1);
    }

    /**
     * Highest code, not most recently published — which is what makes an
     * unpublish roll back rather than leave the withdrawn build newest.
     */
    public function test_the_highest_published_code_wins(): void
    {
        $this->record(3, '3.0.0', publishedAt: now()->subDay()->toDateTimeString());
        $this->record(2, '2.0.0', publishedAt: now()->toDateTimeString());

        $this->getJson('/api/app-releases/storefront-android')
            ->assertOk()
            ->assertJsonPath('versionCode', 3);
    }

    public function test_apps_do_not_see_each_others_builds(): void
    {
        $this->record(9, '9.0.0', slug: 'rider-android');
        $this->record(2, '2.0.0');

        $this->getJson('/api/app-releases/storefront-android')
            ->assertOk()
            ->assertJsonPath('versionCode', 2);
    }

    public function test_the_command_records_and_publishes(): void
    {
        $this->artisan('app:release', [
            'slug' => 'storefront-android',
            '--code' => '2',
            '--name' => '2.0.0',
            '--apk' => 'https://omaykan.com/apk/storefront-2.0.0.apk',
            '--notes' => 'Live order tracking.',
        ])->assertSuccessful();

        $this->getJson('/api/app-releases/storefront-android')
            ->assertOk()
            ->assertJson([
                'versionCode' => 2,
                'versionName' => '2.0.0',
                'notes' => 'Live order tracking.',
            ]);
    }

    public function test_the_command_defaults_to_the_storefront_app(): void
    {
        $this->artisan('app:release', [
            '--code' => '2',
            '--name' => '2.0.0',
            '--apk' => 'https://omaykan.com/apk/storefront-2.0.0.apk',
        ])->assertSuccessful();

        $this->assertSame(2, AppRelease::current(AppRelease::STOREFRONT_ANDROID)?->version_code);
    }

    public function test_the_command_records_a_draft_without_publishing(): void
    {
        $this->artisan('app:release', [
            '--code' => '2',
            '--name' => '2.0.0',
            '--apk' => 'https://omaykan.com/apk/storefront-2.0.0.apk',
            '--draft' => true,
        ])->assertSuccessful();

        $this->getJson('/api/app-releases/storefront-android')->assertNotFound();

        $this->artisan('app:release', ['--code' => '2', '--publish' => true])->assertSuccessful();

        $this->getJson('/api/app-releases/storefront-android')->assertOk();
    }

    /** Re-running with a fixed URL is how a typo gets corrected. */
    public function test_the_command_updates_an_existing_build(): void
    {
        $this->record(2, '2.0.0');

        $this->artisan('app:release', [
            '--code' => '2',
            '--apk' => 'https://omaykan.com/apk/storefront-2.0.0-fixed.apk',
        ])->assertSuccessful();

        $this->assertSame(1, AppRelease::query()->count());
        $this->getJson('/api/app-releases/storefront-android')
            ->assertJsonPath('apkUrl', 'https://omaykan.com/apk/storefront-2.0.0-fixed.apk');
    }

    public function test_the_command_requires_a_name_and_apk_for_a_new_build(): void
    {
        $this->artisan('app:release', ['--code' => '2'])->assertFailed();

        $this->assertSame(0, AppRelease::query()->count());
    }

    public function test_the_command_rejects_a_version_name_in_the_code(): void
    {
        $this->artisan('app:release', [
            '--code' => '2.0.0',
            '--name' => '2.0.0',
            '--apk' => 'https://omaykan.com/apk/storefront-2.0.0.apk',
        ])->assertFailed();

        $this->assertSame(0, AppRelease::query()->count());
    }

    public function test_the_command_rejects_an_apk_that_is_not_a_url(): void
    {
        $this->artisan('app:release', [
            '--code' => '2',
            '--name' => '2.0.0',
            '--apk' => '/var/www/apk/storefront.apk',
        ])->assertFailed();

        $this->assertSame(0, AppRelease::query()->count());
    }

    /**
     * The whole reason this is a command: a code below the live one publishes
     * cleanly and then does nothing on every phone already past it.
     */
    public function test_the_command_refuses_to_go_backwards_without_force(): void
    {
        $this->record(3, '3.0.0');

        $this->artisan('app:release', [
            '--code' => '2',
            '--name' => '2.0.0',
            '--apk' => 'https://omaykan.com/apk/storefront-2.0.0.apk',
        ])->assertFailed();

        $this->getJson('/api/app-releases/storefront-android')->assertJsonPath('versionCode', 3);

        $this->artisan('app:release', [
            '--code' => '2',
            '--name' => '2.0.0',
            '--apk' => 'https://omaykan.com/apk/storefront-2.0.0.apk',
            '--force' => true,
        ])->assertSuccessful();

        $this->assertSame(2, AppRelease::query()->count());
    }

    public function test_unpublishing_rolls_clients_back(): void
    {
        $this->record(1, '1.0.0');
        $this->record(2, '2.0.0');

        $this->artisan('app:release', ['--code' => '2', '--unpublish' => true])->assertSuccessful();

        $this->getJson('/api/app-releases/storefront-android')
            ->assertOk()
            ->assertJsonPath('versionCode', 1);
    }

    public function test_unpublishing_the_only_build_turns_the_check_off(): void
    {
        $this->record(2, '2.0.0');

        $this->artisan('app:release', ['--code' => '2', '--unpublish' => true])->assertSuccessful();

        $this->getJson('/api/app-releases/storefront-android')->assertNotFound();
    }

    public function test_unpublishing_a_build_that_was_never_recorded_fails(): void
    {
        $this->artisan('app:release', ['--code' => '7', '--unpublish' => true])->assertFailed();
    }

    public function test_the_command_lists_what_is_recorded(): void
    {
        $this->record(1, '1.0.0');
        $this->record(2, '2.0.0', published: false);

        $this->artisan('app:release', ['--list' => true])->assertSuccessful();
    }
}
