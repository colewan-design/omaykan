<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The two dates that make "is this organization paid up?" answerable.
 *
 * Until now a subscription had a status and nothing else — no sense of *until
 * when*. That is why enforcement was impossible to switch on: every row in the
 * database says `pending_verification`, because only a human clicking Verify
 * has ever moved it, and nothing has depended on the click.
 *
 * `trial_ends_at` is early access written down. Four surfaces promise merchants
 * that Omaykan is free for now and that we will say before that changes; with
 * this column that promise is a date the server can read, rather than a
 * sentence on a marketing page and a deploy somebody has to remember not to
 * make. The backfill in `billing:backfill-trials` sets it for every existing
 * organization, which is what makes enforcement safe to enable later.
 *
 * `current_period_ends_at` is what a real billing cycle writes. There is no
 * billing cycle yet, so it stays null and the trial date carries everything.
 *
 * See documentation/subscription-and-suspension.md §3.3.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('subscriptions', function (Blueprint $table) {
            // Free until. Null means "no trial" — which, once enforcement is
            // on, is a subscription that has to stand on its paid period
            // alone. Every row that exists today gets a date by backfill.
            $table->timestamp('trial_ends_at')->nullable();

            // Paid through. Null until something collects money.
            $table->timestamp('current_period_ends_at')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('subscriptions', function (Blueprint $table) {
            $table->dropColumn(['trial_ends_at', 'current_period_ends_at']);
        });
    }
};
