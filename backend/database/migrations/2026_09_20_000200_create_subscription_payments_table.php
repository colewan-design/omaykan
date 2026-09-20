<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * What a merchant says they paid, and what an operator did about it.
 *
 * Until now the only record of a payment was `subscriptions.gcash_reference`:
 * one string, written once at signup, by a form that no longer sends it. That
 * shape cannot hold a *recurring* manual transfer — the second month
 * overwrites the first, and there is nowhere to say which period a payment was
 * for.
 *
 * So: a row per transfer. It is the merchant's side of the conversation that
 * was missing entirely — there has been no way for a shop to tell us it has
 * paid — and it doubles as the operator's audit trail, which the single column
 * never was.
 *
 * `period_start` / `period_end` are filled by the **operator**, not the
 * merchant. A merchant says "I sent ₱499, reference 1234"; deciding what that
 * buys is ours, and it is the value that drives
 * `SubscriptionBilling::recordPayment()`.
 *
 * `amount_cents` is what the merchant says they sent, recorded as submitted
 * even when it disagrees with the plan price — a short payment is a fact about
 * the conversation, and rounding it to what we expected would hide it.
 *
 * See documentation/subscription-and-suspension.md §6.3.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('subscription_payments', function (Blueprint $table) {
            $table->uuid('id')->primary();

            $table->foreignUuid('subscription_id')->constrained()->cascadeOnDelete();

            // Denormalised from the subscription so the operator portal can
            // list payments across tenants without a join, and so a payment
            // outlives a subscription row being rebuilt.
            $table->foreignUuid('organization_id')->constrained()->cascadeOnDelete();

            /*
             * submitted → accepted | rejected. No "pending_verification" here:
             * that word already means something else on `subscriptions`, and
             * reusing it would make two different queues read as one.
             */
            $table->string('status')->default('submitted');

            // The GCash / bank reference the merchant typed. Not validated
            // against anything — there is no gateway to validate it with, and
            // pretending otherwise is the trap the old column fell into.
            $table->string('reference', 120);

            $table->unsignedBigInteger('amount_cents');

            // The merchant's own note: "paid for Oct + Nov", "sent from my
            // wife's number". Free text because the useful version of this is
            // always the one we did not anticipate.
            $table->text('note')->nullable();

            // Who submitted it, for a shop where several people have the app.
            $table->foreignUuid('submitted_by_user_id')->nullable()->constrained('users')->nullOnDelete();

            // What the operator decided this payment covers. Null until
            // reviewed, and null on a rejection.
            $table->timestamp('period_start')->nullable();
            $table->timestamp('period_end')->nullable();

            $table->foreignUuid('reviewed_by_platform_admin_id')->nullable()
                ->constrained('platform_admins')->nullOnDelete();
            $table->timestamp('reviewed_at')->nullable();

            // Why an operator said no, shown to the merchant. A rejection
            // without a reason is a support ticket.
            $table->text('rejection_reason')->nullable();

            $table->timestamps();

            // The operator's queue: everything awaiting review, oldest first.
            $table->index(['status', 'created_at']);

            // The merchant's own history.
            $table->index(['organization_id', 'created_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('subscription_payments');
    }
};
