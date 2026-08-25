<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * One merchant subscription per organization.
 *
 * Replaces the Firestore document at organizations/{slug}/private/subscription.
 * Payment is a manual GCash transfer: the owner submits a reference number at
 * signup, the platform operator verifies it by hand later. Nothing here talks
 * to a payment gateway, because there isn't one yet.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('subscriptions', function (Blueprint $table) {
            $table->uuid('id')->primary();
            // One per org: the unique constraint is the business rule.
            $table->foreignUuid('organization_id')->unique()->constrained()->cascadeOnDelete();

            // pending_verification | active | rejected
            $table->string('status')->default('pending_verification');
            $table->string('plan');
            $table->integer('amount_cents');

            // The reference the owner typed off their GCash receipt. Not
            // validated against anything — the operator eyeballs it.
            $table->string('gcash_reference');

            $table->timestamp('submitted_at')->nullable();
            $table->timestamp('verified_at')->nullable();
            // Why the operator rejected it, shown back to the owner.
            $table->text('rejection_reason')->nullable();

            $table->timestamps();
        });

        Schema::table('organizations', function (Blueprint $table) {
            // Suspending an org keeps its data but locks everyone out; it is
            // reversible, unlike deletion.
            $table->boolean('suspended')->default(false);
        });
    }

    public function down(): void
    {
        Schema::table('organizations', function (Blueprint $table) {
            $table->dropColumn('suspended');
        });

        Schema::dropIfExists('subscriptions');
    }
};
