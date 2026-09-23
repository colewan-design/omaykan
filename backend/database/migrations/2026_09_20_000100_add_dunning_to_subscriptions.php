<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * What we have already told this merchant about their subscription.
 *
 * The scheduled command in §6.4 runs daily and asks the same question every
 * time: is a notice due? Without somewhere to record the answer it would mail
 * the same warning every morning for the length of the grace window, which is
 * how a system that is trying to be helpful becomes the reason somebody
 * filters your domain into spam.
 *
 * Two columns rather than a `subscription_notices` table: there are three
 * notices, they are strictly ordered, and only the most recent one matters for
 * deciding what comes next. A table would be the right shape the day we want
 * a delivery history per merchant, and that day is not this one.
 *
 * `dunning_stage` holds one of SubscriptionBilling's STAGE_* values, or null
 * for a subscription nobody has had to chase. It is cleared on payment, so a
 * merchant who renews and later lapses again gets the full sequence a second
 * time rather than resuming halfway through it.
 *
 * See documentation/subscription-and-suspension.md §6.4.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('subscriptions', function (Blueprint $table) {
            // The last notice sent, not the notice due now. Null means we have
            // never had to chase this subscription.
            $table->string('dunning_stage')->nullable();

            // When that notice went out. Kept beside the stage so an operator
            // looking at a complaint can see what was sent and when, without
            // going to the mail log.
            $table->timestamp('dunned_at')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('subscriptions', function (Blueprint $table) {
            $table->dropColumn(['dunning_stage', 'dunned_at']);
        });
    }
};
