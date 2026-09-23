<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

/**
 * Sign in with Google, for staff.
 *
 * The same three columns customer_accounts got in
 * 2026_08_28_000100, and for the same reasons — see that migration's docblock
 * for why an account is matched on `google_sub` and never on the email.
 *
 * The one difference worth stating: a staff account may have no email at all.
 * `staff-register` has always created users from a username alone, and those
 * rows still have to be able to sign in, so password login stays and accepts a
 * username *or* an email. Google is an additional door, not a replacement for
 * the only one some of these accounts have.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('google_sub')->nullable()->unique()->after('email');
            $table->string('avatar_url')->nullable()->after('google_sub');
            $table->string('password')->nullable()->change();
        });
    }

    public function down(): void
    {
        // Same reasoning as the customer_accounts rollback: an account that has
        // only ever pressed the Google button has no password to put back, and
        // the column is about to stop allowing null. Give it one nobody holds
        // rather than failing the rollback.
        DB::table('users')
            ->whereNull('password')
            ->update(['password' => bcrypt(Str::random(64))]);

        Schema::table('users', function (Blueprint $table) {
            $table->dropUnique(['google_sub']);
            $table->dropColumn(['google_sub', 'avatar_url']);
            $table->string('password')->nullable(false)->change();
        });
    }
};
