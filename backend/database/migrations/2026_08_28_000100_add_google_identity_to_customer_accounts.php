<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

/**
 * Sign in with Google, for the storefront and the Android app.
 *
 * Three changes, and the third is the one with teeth.
 *
 * `google_sub` is Google's stable subject id for the person. It is what an
 * account is matched on, not the email: Google lets someone change the address
 * on an account, and matching on email would hand the shopper's order history
 * to whoever inherits the old address afterwards. Unique, because two local
 * accounts claiming one Google identity is the same bug as two accounts
 * claiming one email.
 *
 * `avatar_url` is the picture Google hands over. Stored rather than fetched,
 * so the portal can greet someone without a second round trip, and nullable
 * because a Google account need not have one.
 *
 * `password` becomes nullable. Someone who has only ever pressed the Google
 * button has no password here and never typed one — writing a random hash into
 * the column instead would be a lie the "forgot password" flow would then
 * happily send mail about. Everything that reads the column now has to cope
 * with null; see CustomerAccount::hasPassword() and the two controllers.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('customer_accounts', function (Blueprint $table) {
            $table->string('google_sub')->nullable()->unique()->after('email');
            $table->string('avatar_url')->nullable()->after('phone');
            $table->string('password')->nullable()->change();
        });
    }

    public function down(): void
    {
        // Google-only accounts have no password to restore, and the column is
        // about to stop allowing null. Give them one nobody holds rather than
        // failing the rollback — they can no longer sign in either way, which
        // is what dropping the feature means.
        DB::table('customer_accounts')
            ->whereNull('password')
            ->update(['password' => bcrypt(Str::random(64))]);

        Schema::table('customer_accounts', function (Blueprint $table) {
            $table->dropUnique(['google_sub']);
            $table->dropColumn(['google_sub', 'avatar_url']);
            $table->string('password')->nullable(false)->change();
        });
    }
};
