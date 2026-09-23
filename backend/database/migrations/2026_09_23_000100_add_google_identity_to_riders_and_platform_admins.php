<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Sign in with Google, for riders and for the platform operator.
 *
 * One column each, and deliberately fewer than customer_accounts and users got
 * in 2026_08_28_000100 and 2026_09_02_000200:
 *
 * - **No `avatar_url`.** A rider already has `avatar_path`, which is a key on
 *   the private disk that `Storage` resolves. Putting a remote Google URL in
 *   the same column would hand a disk path to whatever renders it and break the
 *   picture rather than change it. A platform admin has no avatar at all.
 *
 * - **`password` stays NOT NULL.** On the two tables that came before, Google
 *   could *create* an account, so a row could legitimately exist with no
 *   password. Neither of these doors creates anything — see
 *   [RiderAuthController::google] and [PlatformAdminAuthController::google],
 *   both of which refuse an identity with no account already behind it. A rider
 *   account is made by registering with a licence and a plate that an operator
 *   then reviews; an operator account is made from the console. Google is an
 *   additional door onto an existing account, never a way to mint one, so every
 *   row still has the password it was created with.
 *
 * `google_sub` is unique for the same reason it is on the other two tables: it
 * is Google's stable id for a person, an account is matched on it and never on
 * the email, and two rows claiming one Google identity is a state no sign-in
 * could resolve correctly.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('riders', function (Blueprint $table) {
            $table->string('google_sub')->nullable()->unique()->after('email');
        });

        Schema::table('platform_admins', function (Blueprint $table) {
            $table->string('google_sub')->nullable()->unique()->after('email');
        });
    }

    public function down(): void
    {
        // Nothing to put back before dropping: no row was ever created from a
        // Google identity alone, so dropping the link leaves every account with
        // the password it has always had and signs in the way it always did.
        Schema::table('riders', function (Blueprint $table) {
            $table->dropUnique(['google_sub']);
            $table->dropColumn('google_sub');
        });

        Schema::table('platform_admins', function (Blueprint $table) {
            $table->dropUnique(['google_sub']);
            $table->dropColumn('google_sub');
        });
    }
};
