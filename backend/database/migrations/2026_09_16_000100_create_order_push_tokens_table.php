<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Which phones want to hear about an order.
 *
 * Keyed to the order rather than to a customer account, because a guest
 * checkout has no account and is still waiting on a rider. The phone registers
 * its Firebase token against the order it just placed, the same way it already
 * tracks the order: the order's UUID is the capability.
 *
 * Rows go with the order. A token is only ever useful while its order is on
 * the road, and a push about a purged order has nothing to open.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('order_push_tokens', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('order_id')->constrained('orders')->cascadeOnDelete();
            // FCM registration tokens run to about 160 characters today;
            // Google does not promise a length, so leave room.
            $table->string('token', 512);
            $table->timestamps();

            $table->unique(['order_id', 'token']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('order_push_tokens');
    }
};
