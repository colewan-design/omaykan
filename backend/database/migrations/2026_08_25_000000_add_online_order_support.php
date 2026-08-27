<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Everything the orders table needs before a customer order placed on the
 * storefront can be represented in MySQL at all.
 *
 * The original schema assumed every order came from a register: `device_id`
 * and `completed_at` were both NOT NULL, and there was nowhere to record who
 * the customer was, how they wanted the order fulfilled, or what a rider
 * should be paid. An online order has no device, is not complete when it is
 * created, and belongs to someone with no account.
 *
 * Ports the shape written by api/create-online-order.ts so the Firestore path
 * can be retired — see documentation/plan.md §Phase 0.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            // An online order is placed by a customer, not rung up on a till.
            $table->uuid('device_id')->nullable()->change();
            // ...and is not finished at the moment it is created.
            $table->timestamp('completed_at')->nullable()->change();

            // 'pos' | 'online'. Defaulted so every existing row — all of which
            // came from a register — keeps its current meaning.
            $table->string('channel')->default('pos');
            $table->string('business_mode')->nullable();
            $table->string('table_number')->nullable();

            // Recorded on the order itself because an unpaid online order has
            // no payments row yet, so payments.payment_method cannot answer
            // "how does this customer intend to pay?".
            $table->string('payment_method')->nullable();

            // 'pickup' | 'delivery'; null for register sales.
            $table->string('fulfillment_method')->nullable();
            $table->text('delivery_address')->nullable();
            $table->decimal('delivery_lat', 10, 7)->nullable();
            $table->decimal('delivery_lng', 10, 7)->nullable();
            $table->decimal('delivery_distance_km', 6, 2)->nullable();
            $table->integer('delivery_fee_cents')->default(0);

            // Finer-grained than order_status: staff advance preparing/ready/
            // served, while this is the rider-aware stage the customer sees.
            // Null for pickup. Merged in from Baguio Delivery's lifecycle.
            $table->string('delivery_stage')->nullable();
            $table->string('rider_name')->nullable();
            $table->string('rider_phone')->nullable();

            // {name, phone?, email?} — the customer has no account to point a
            // foreign key at. user_id stays reserved for the staff member who
            // rang the sale.
            $table->json('guest_contact')->nullable();

            // The storefront lists a store's live online orders by recency.
            $table->index(['store_id', 'channel', 'created_at']);
        });

        Schema::table('stores', function (Blueprint $table) {
            // Origin pin for the delivery distance quote. Without both, the
            // quote falls back to the flat base fee rather than refusing.
            $table->decimal('lat', 10, 7)->nullable();
            $table->decimal('lng', 10, 7)->nullable();
        });

        Schema::table('products', function (Blueprint $table) {
            // Which business modes show this product in the storefront. The
            // register derives its catalog from the store's own mode, but one
            // organization can run several.
            $table->json('business_modes')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->dropColumn('business_modes');
        });

        Schema::table('stores', function (Blueprint $table) {
            $table->dropColumn(['lat', 'lng']);
        });

        Schema::table('orders', function (Blueprint $table) {
            $table->dropIndex(['store_id', 'channel', 'created_at']);
            $table->dropColumn([
                'channel',
                'business_mode',
                'table_number',
                'payment_method',
                'fulfillment_method',
                'delivery_address',
                'delivery_lat',
                'delivery_lng',
                'delivery_distance_km',
                'delivery_fee_cents',
                'delivery_stage',
                'rider_name',
                'rider_phone',
                'guest_contact',
            ]);

            // Restoring NOT NULL would fail against any online order already
            // recorded, so the rollback deliberately leaves both nullable.
        });
    }
};
