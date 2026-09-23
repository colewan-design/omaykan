<?php

namespace App\Console\Commands;

use App\Models\AppRelease;
use Illuminate\Console\Command;

/**
 * Publishing a build, in one command.
 *
 * The alternative is a hand-edited row, which is how a version_code ends up
 * lower than the one already installed and every phone quietly stops offering
 * the update with no error anywhere. So the checks that matter live here:
 * the code must be an integer, the URL must be one, and going backwards has to
 * be asked for explicitly.
 *
 * Uploading the APK is not this command's job — the file is put on the VPS and
 * served by nginx, and this records where it landed.
 */
class PublishAppRelease extends Command
{
    protected $signature = 'app:release
        {slug=storefront-android : Which app, e.g. storefront-android}
        {--code= : versionCode, the integer Android compares builds by}
        {--name= : versionName shown to the shopper, e.g. 2.0.0}
        {--apk= : Public URL of the APK}
        {--notes= : Release notes shown under the version}
        {--draft : Record the build without announcing it}
        {--publish : Announce a build already recorded as a draft}
        {--unpublish : Withdraw a build, rolling clients back to the one before}
        {--force : Allow publishing a version_code below the current one}
        {--list : Show every recorded build for this app and exit}';

    protected $description = 'Record or withdraw a build of a sideloaded app for the in-app update check';

    public function handle(): int
    {
        $slug = strtolower(trim((string) $this->argument('slug')));

        if (preg_match('/^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/', $slug) !== 1) {
            $this->error("\"{$slug}\" is not a valid app slug (lowercase letters, digits and hyphens).");

            return self::FAILURE;
        }

        if ($this->option('list')) {
            return $this->list($slug);
        }

        $rawCode = trim((string) $this->option('code'));

        if ($rawCode === '') {
            $this->error('--code is required: the versionCode of the build being recorded.');

            return self::FAILURE;
        }

        if (preg_match('/^\d+$/', $rawCode) !== 1) {
            $this->error("--code must be a whole number, not \"{$rawCode}\" — it is Android's versionCode, not the version name.");

            return self::FAILURE;
        }

        $code = (int) $rawCode;

        if ($this->option('unpublish')) {
            return $this->setPublished($slug, $code, false);
        }

        if ($this->option('publish')) {
            return $this->setPublished($slug, $code, true);
        }

        return $this->record($slug, $code);
    }

    private function record(string $slug, int $code): int
    {
        $name = trim((string) $this->option('name'));
        $apk = trim((string) $this->option('apk'));

        $existing = AppRelease::query()
            ->where('slug', $slug)
            ->where('version_code', $code)
            ->first();

        // Re-running against an existing build is how a typo in the URL gets
        // fixed, so the two required options are only required the first time.
        if ($existing === null) {
            if ($name === '' || $apk === '') {
                $this->error('--name and --apk are both required for a build that has not been recorded yet.');

                return self::FAILURE;
            }
        }

        if ($apk !== '' && filter_var($apk, FILTER_VALIDATE_URL) === false) {
            $this->error("--apk is not a URL: {$apk}");

            return self::FAILURE;
        }

        $current = AppRelease::current($slug);

        // The failure this command exists to prevent. A lower code publishes
        // fine and then does nothing at all on every phone already past it,
        // with no error to notice — so it has to be deliberate.
        if (! $this->option('draft') && $current !== null && $code < $current->version_code && ! $this->option('force')) {
            $this->error("Build {$code} is below the published {$current->version_code} ({$current->version_name}).");
            $this->line('Phones already on the newer build would never see it. Pass --force if that is intended.');

            return self::FAILURE;
        }

        $release = $existing ?? new AppRelease(['slug' => $slug, 'version_code' => $code]);

        $release->fill(array_filter([
            'version_name' => $name === '' ? null : $name,
            'apk_url' => $apk === '' ? null : $apk,
            'notes' => $this->option('notes') === null ? null : trim((string) $this->option('notes')),
        ], static fn ($value) => $value !== null));

        // A draft leaves an existing publication alone rather than retracting
        // it: --draft is "do not announce this yet", not "withdraw".
        if (! $this->option('draft') && $release->published_at === null) {
            $release->published_at = now();
        }

        $release->save();

        $this->newLine();
        $this->info($existing === null ? 'Build recorded.' : 'Build updated.');
        $this->summarise($release);

        if ($release->published_at === null) {
            $this->warn("Not published. Run app:release {$slug} --code={$code} --publish when it is ready.");
        }

        $this->newLine();

        return self::SUCCESS;
    }

    private function setPublished(string $slug, int $code, bool $published): int
    {
        $release = AppRelease::query()
            ->where('slug', $slug)
            ->where('version_code', $code)
            ->first();

        if ($release === null) {
            $this->error("No build {$code} recorded for {$slug}.");

            return self::FAILURE;
        }

        $release->published_at = $published ? now() : null;
        $release->save();

        if ($published) {
            $this->info("Published {$slug} {$release->version_name} ({$code}).");
            $this->newLine();

            return self::SUCCESS;
        }

        // Saying what clients fall back to matters more than saying what was
        // withdrawn: an unpublish with nothing behind it silently turns the
        // update check off, and that is worth seeing on the way out.
        $fallback = AppRelease::current($slug);

        $this->info("Withdrew {$slug} {$release->version_name} ({$code}).");
        $this->line($fallback === null
            ? 'Nothing else is published — the update check now answers 404.'
            : "Clients now see {$fallback->version_name} ({$fallback->version_code}).");
        $this->newLine();

        return self::SUCCESS;
    }

    private function list(string $slug): int
    {
        $releases = AppRelease::query()
            ->where('slug', $slug)
            ->orderByDesc('version_code')
            ->get();

        if ($releases->isEmpty()) {
            $this->warn("No builds recorded for {$slug}.");

            return self::SUCCESS;
        }

        $current = AppRelease::current($slug);

        $this->newLine();
        $this->table(
            ['', 'Code', 'Version', 'Published', 'APK'],
            $releases->map(fn (AppRelease $release) => [
                $current !== null && $release->is($current) ? '→' : '',
                $release->version_code,
                $release->version_name,
                $release->published_at?->toDayDateTimeString() ?? 'draft',
                $release->apk_url,
            ])->all(),
        );
        $this->line('→ is what the update check serves.');
        $this->newLine();

        return self::SUCCESS;
    }

    private function summarise(AppRelease $release): void
    {
        $this->table(['Field', 'Value'], [
            ['App', $release->slug],
            ['Version code', $release->version_code],
            ['Version name', $release->version_name],
            ['APK', $release->apk_url],
            ['Notes', $release->notes ?: '—'],
            ['Published', $release->published_at?->toDayDateTimeString() ?? 'no'],
        ]);
    }
}
