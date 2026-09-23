<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Who voided a sale, and why.
 *
 * A void is still the order's soft delete — every report, the promo
 * redemption count and the portal's "cancelled" tab already read
 * `deleted_at` that way. These two say what the till knew at the time:
 * the owner who voided it and the reason they typed.
 *
 * `voided_by_user_id` is not a foreign key for the same reason
 * `order_discounts.applied_by` falls back to the pusher: a till signed in
 * with a local-only user has an id the server has never seen, and the void
 * must not fail over it. RegisterSales resolves unknown ids to the person
 * whose token pushed the void.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->uuid('voided_by_user_id')->nullable();
            $table->string('void_reason', 240)->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->dropColumn(['voided_by_user_id', 'void_reason']);
        });
    }
};
