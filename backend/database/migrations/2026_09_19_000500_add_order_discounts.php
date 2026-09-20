<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/**
 * Discounts, and the tax-rate unit they exposed.
 *
 * ## Discounts
 *
 * One row per discount on an order, plus the total on the order itself.
 * A table rather than a single column because a discount is more than an
 * amount: who gave it, why, and — for the kinds still to come — which promo
 * code or which loyalty redemption it was. `orders.discount_cents` is the
 * denormalised sum, because receipts and reports read it constantly.
 *
 * `roles.max_discount_percent` is each role's limit; null falls back to
 * RolePermissions::DEFAULT_DISCOUNT_LIMITS.
 *
 * ## Tax rate
 *
 * `products.tax_rate` is a percentage (12.00): OnlineOrderController charges
 * with it that way and every seeder writes it so. The till, though, synced a
 * fraction (0.12), which the server then read as 0.12% — and read the other
 * way, the till charged a seeded product's 12 as 1,200%. From here the column
 * is a percentage everywhere and the edges convert. Rows the till wrote are
 * rewritten: a value above zero and at most one is a fraction, because no
 * real VAT rate is 1% or less.
 *
 * `order_items.tax_rate` keeps the rate a line was sold at, in the same unit.
 *
 * See documentation/merchant-features.md §7.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('order_discounts', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('order_id')->constrained()->cascadeOnDelete();
            // manual today; promo, loyalty, and the statutory senior / PWD
            // kinds once their VAT treatment is confirmed.
            $table->string('kind');
            $table->integer('amount_cents');
            // Set when the cashier asked for a percentage.
            $table->decimal('percent', 5, 2)->nullable();
            $table->string('reason', 120)->nullable();
            $table->foreignUuid('applied_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamps();
        });

        Schema::table('orders', function (Blueprint $table) {
            $table->integer('discount_cents')->default(0);
        });

        Schema::table('order_items', function (Blueprint $table) {
            $table->decimal('tax_rate', 5, 2)->nullable();
        });

        Schema::table('roles', function (Blueprint $table) {
            $table->unsignedTinyInteger('max_discount_percent')->nullable();
        });

        DB::table('products')
            ->where('tax_rate', '>', 0)
            ->where('tax_rate', '<=', 1)
            ->update(['tax_rate' => DB::raw('tax_rate * 100')]);
    }

    public function down(): void
    {
        Schema::table('roles', function (Blueprint $table) {
            $table->dropColumn('max_discount_percent');
        });

        Schema::table('order_items', function (Blueprint $table) {
            $table->dropColumn('tax_rate');
        });

        Schema::table('orders', function (Blueprint $table) {
            $table->dropColumn('discount_cents');
        });

        Schema::dropIfExists('order_discounts');

        // The tax-rate rewrite is not undone: which rows were fractions is not
        // recoverable, and percentages are what the column meant all along.
    }
};
