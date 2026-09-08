<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The end of pairing: merchant clients sign a person in, not a terminal.
 *
 * ## The two columns that go
 *
 * `public_store_code` and `pairing_code_hash` were one value doing two jobs —
 * the shop's public identifier and the secret a till proved to join it. Both
 * are gone. A shopper reaches a shop through the directory and its
 * org-slug/branch-code URL; staff reach theirs through `/staff/sign-in`, which
 * lists the shops their account can act for. `stores.code` stays: that is the
 * branch slug ("main"), and it was never a credential.
 *
 * ## What happens to the device columns
 *
 * The `devices` table and every `device_id` on a row stay. Deleting them would
 * throw away the answer to "which terminal rang this up" for every sale already
 * taken, to tidy a column that costs nothing. Nothing pairs any more, so no new
 * row gets one — they are history, and history is the only thing they were ever
 * good for.
 *
 * That makes the two sync tables' columns nullable and gives each a `user_id`
 * to record instead. (`orders.device_id` was already nullable: storefront
 * orders have no till either.) The sync cursor is the interesting one:
 * it was unique per device, because a device was a till and each till tracked
 * its own pull position. Its replacement is per person *per store*, so a
 * manager who works two branches keeps a separate position in each, and the
 * same person on the phone and on the counter tablet shares one — which is
 * correct, because what the cursor tracks is how far that *account* has read.
 */
return new class extends Migration
{
    public function up(): void
    {
        // `orders.device_id` is already nullable — 2026_08_25_000000 made it so
        // when storefront orders arrived, which have no till either.

        Schema::table('sync_events', function (Blueprint $table) {
            $table->foreignUuid('device_id')->nullable()->change();
            $table->foreignUuid('user_id')->nullable()->after('device_id')
                ->constrained()->nullOnDelete();
        });

        Schema::table('sync_cursors', function (Blueprint $table) {
            $table->dropUnique(['device_id', 'cursor_name']);
            $table->foreignUuid('device_id')->nullable()->change();
            $table->foreignUuid('user_id')->nullable()->after('device_id')
                ->constrained()->nullOnDelete();
            $table->unique(['store_id', 'user_id', 'cursor_name']);
        });

        Schema::table('stores', function (Blueprint $table) {
            $table->dropUnique(['public_store_code']);
            $table->dropColumn(['public_store_code', 'pairing_code_hash']);
        });
    }

    public function down(): void
    {
        Schema::table('stores', function (Blueprint $table) {
            // Nullable on the way back, unlike the original: the codes
            // themselves are not recoverable — the hash was one-way and the
            // public half has been dropped — so every store comes back without
            // one and reissues from Settings.
            $table->string('public_store_code', 64)->nullable()->unique();
            $table->string('pairing_code_hash')->nullable();
        });

        Schema::table('sync_cursors', function (Blueprint $table) {
            $table->dropUnique(['store_id', 'user_id', 'cursor_name']);
            $table->dropConstrainedForeignId('user_id');
        });

        Schema::table('sync_events', function (Blueprint $table) {
            $table->dropConstrainedForeignId('user_id');
        });

        // The three device_id columns stay nullable. Making them required again
        // would fail on every row written since this migration ran, and those
        // rows are real sales.
    }
};
