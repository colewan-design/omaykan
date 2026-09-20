<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Promo and voucher codes: a discount a shopper or a cashier types in.
 *
 * A redemption is an `order_discounts` row with `kind = 'promo'` pointing
 * here. There is deliberately no redemption counter on this table: a counter
 * drifts the moment an order is re-synced or voided, and the rows are already
 * the truth. Checkout locks the code's row while it counts them, so two
 * shoppers cannot both take the last use.
 *
 * `channel` says where it may be used — online, at the counter, or both. At
 * the counter a code is only accepted while the till can reach the server;
 * see PromoCodeController::check.
 *
 * See documentation/merchant-features.md §8.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('promo_codes', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('organization_id')->constrained()->cascadeOnDelete();
            // Stored upper-case; unique per organization.
            $table->string('code', 40);
            // percent | amount
            $table->string('kind');
            // Basis points for a percentage (1000 = 10%), centavos for an amount.
            $table->integer('value');
            $table->integer('min_subtotal_cents')->default(0);
            // Caps a percentage code: "20% off, up to ₱100".
            $table->integer('max_discount_cents')->nullable();
            // online | counter | both
            $table->string('channel')->default('online');
            $table->timestamp('starts_at')->nullable();
            $table->timestamp('ends_at')->nullable();
            $table->integer('max_redemptions')->nullable();
            $table->integer('per_customer_limit')->nullable();
            $table->boolean('is_active')->default(true);
            $table->foreignUuid('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamps();
            $table->softDeletes();

            $table->unique(['organization_id', 'code']);
        });

        Schema::table('order_discounts', function (Blueprint $table) {
            $table->foreignUuid('promo_code_id')->nullable()->constrained('promo_codes')->nullOnDelete();
        });
    }

    public function down(): void
    {
        Schema::table('order_discounts', function (Blueprint $table) {
            $table->dropConstrainedForeignId('promo_code_id');
        });

        Schema::dropIfExists('promo_codes');
    }
};
