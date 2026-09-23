<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Tell a payment the merchant *claims* apart from one PayMongo *confirms*.
 *
 * `subscription_payments` was built for one provenance: a merchant says they
 * transferred money and an operator agrees. A gateway payment is a different
 * claim — nobody typed it, and nobody needs to believe the merchant for it to
 * be true — so it must not land in the operator's queue looking like work.
 * `source` is what keeps the two apart, and it defaults to `manual` so every
 * row that already exists stays exactly what it was.
 *
 * The provider ids are the idempotency. PayMongo retries a webhook until it
 * gets a 2xx, and the same payment arriving twice must not buy two months, so
 * both are unique and the settlement writes them before it extends anything.
 *
 * See documentation/subscription-and-suspension.md §6.3.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('subscription_payments', function (Blueprint $table) {
            // manual | paymongo. Not an enum: the next provider should be a
            // deploy, not a migration.
            $table->string('source', 20)->default('manual');

            // The checkout session we sent the merchant to. Written when the
            // session is created, before any money moves, so an abandoned
            // checkout is still something we can look up and reconcile.
            $table->string('provider_session_id', 120)->nullable()->unique();

            // The payment itself, written only once PayMongo says it is paid.
            $table->string('provider_payment_id', 120)->nullable()->unique();
        });
    }

    /**
     * Indexes first, in their own statement.
     *
     * SQLite rebuilds the table for each dropped column and re-validates
     * every index as it goes, so dropping a column that a unique index still
     * names fails with "no such column" — which surfaces as the whole test
     * suite collapsing rather than as anything to do with this migration.
     */
    public function down(): void
    {
        Schema::table('subscription_payments', function (Blueprint $table) {
            $table->dropUnique(['provider_session_id']);
            $table->dropUnique(['provider_payment_id']);
        });

        Schema::table('subscription_payments', function (Blueprint $table) {
            $table->dropColumn(['source', 'provider_session_id', 'provider_payment_id']);
        });
    }
};
