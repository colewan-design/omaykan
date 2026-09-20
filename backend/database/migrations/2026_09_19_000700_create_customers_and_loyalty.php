<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Named counter customers on the server, and a points programme built on them.
 *
 * ## Customers
 *
 * Until now a customer a cashier named at the register lived on that till and
 * nowhere else: two tills in one shop did not share them, a reinstall lost
 * them, and the loyalty tiers on the Customers page were a calculation over
 * one device's history. `pos_customers` is the shared copy, synced as a
 * `customer` event like products are. The id is the till's own UUID, so a
 * customer made offline keeps its identity when it arrives.
 *
 * These are counter customers — not storefront `customer_accounts`. Linking
 * the two by phone number is left undone on purpose: a number a cashier typed
 * has not been verified, and linking on it would let anyone spend someone
 * else's points. See documentation/merchant-features.md §9.
 *
 * `loyalty_consent_at`: a customer earns points only once they have agreed to
 * be enrolled. Keeping a named person's phone number for a points scheme is
 * personal data under the Data Privacy Act; the till asks at enrolment.
 *
 * ## Loyalty
 *
 * `loyalty_programs` — one per organization, off until the owner turns it on.
 * `loyalty_entries` — a ledger, never a balance: earn, redeem, expire, adjust,
 * each a row with a sign. The balance is the sum. An adjustment is a new row,
 * never an edit, so a disputed balance can always be explained.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('pos_customers', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('organization_id')->constrained()->cascadeOnDelete();
            $table->string('name');
            $table->string('phone', 40)->nullable();
            $table->string('email', 190)->nullable();
            $table->text('notes')->nullable();
            $table->timestamp('loyalty_consent_at')->nullable();
            $table->timestamps();
            $table->softDeletes();

            $table->index(['organization_id', 'phone']);
        });

        Schema::table('orders', function (Blueprint $table) {
            $table->foreignUuid('pos_customer_id')->nullable()->constrained('pos_customers')->nullOnDelete();
        });

        Schema::create('loyalty_programs', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('organization_id')->unique()->constrained()->cascadeOnDelete();
            $table->boolean('enabled')->default(false);
            // One point for every this-many centavos spent (before VAT): 10000 = ₱100.
            $table->integer('spend_cents_per_point')->default(10000);
            // What one point is worth when spent.
            $table->integer('point_value_cents')->default(100);
            $table->integer('min_redeem_points')->default(10);
            // Points expire this many months after they were earned; null never.
            $table->unsignedSmallInteger('expiry_months')->nullable();
            $table->timestamps();
        });

        Schema::create('loyalty_entries', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('organization_id')->constrained()->cascadeOnDelete();
            $table->foreignUuid('pos_customer_id')->constrained('pos_customers')->cascadeOnDelete();
            $table->foreignUuid('order_id')->nullable()->constrained()->nullOnDelete();
            // earn | redeem | expire | adjust
            $table->string('reason', 20);
            $table->integer('points');
            $table->string('note', 200)->nullable();
            $table->foreignUuid('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamp('expires_at')->nullable();
            $table->timestamps();

            $table->index(['pos_customer_id', 'created_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('loyalty_entries');
        Schema::dropIfExists('loyalty_programs');

        Schema::table('orders', function (Blueprint $table) {
            $table->dropConstrainedForeignId('pos_customer_id');
        });

        Schema::dropIfExists('pos_customers');
    }
};
