<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * What version of a sideloaded app is current, and where to get it.
 *
 * Replaces the Firestore `appReleases` document the Capacitor app read on
 * launch, which dies with Firestore. The Android customer app is distributed as
 * a direct APK — there is no Play listing to notice an update on the shopper's
 * behalf — so this table is the only thing standing between a published build
 * and a phone that never hears about it.
 *
 * One row per published build rather than one mutable row per app: a release is
 * a historical fact, and keeping the previous rows means `--unpublish` can roll
 * back to the build before rather than needing the old values typed in again
 * from memory. The endpoint only ever serves the highest published version_code
 * for a slug, so the rest are archive.
 *
 * The APK itself is a file on the VPS served by nginx. Only its URL lives here;
 * a binary in a database column is a backup nobody wants and a download nobody
 * can range-request.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('app_releases', function (Blueprint $table) {
            $table->uuid('id')->primary();

            // Which app. `storefront-android` today; a rider or merchant build
            // would be its own slug against the same endpoint.
            $table->string('slug', 64);

            // Compared against PackageInfo.longVersionCode on the device, so it
            // is the integer Android actually orders builds by — never the
            // human version string, which sorts wrong the moment 2.10 follows
            // 2.9. Unsigned big: Android allows up to 2100000000, and a
            // date-derived scheme runs past a 32-bit int.
            $table->unsignedBigInteger('version_code');

            // What the update prompt shows the shopper. Display only.
            $table->string('version_name', 32);

            $table->string('apk_url', 2048);

            // Release notes, shown under the version in the update prompt.
            $table->text('notes')->nullable();

            // Null means built but not announced — the row exists, the endpoint
            // ignores it. A timestamp rather than a boolean because "when did
            // this go out" is the question asked when a bad build ships, and
            // because a future value is a scheduled release for free.
            $table->timestamp('published_at')->nullable();

            $table->timestamps();

            // One row per build of an app. Re-running the publish command with
            // the same code updates that row rather than stacking duplicates
            // the endpoint would have to tie-break between.
            $table->unique(['slug', 'version_code']);

            // The endpoint's only query: newest published build for one slug.
            $table->index(['slug', 'published_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('app_releases');
    }
};
