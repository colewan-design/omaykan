<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * What the customer thought of the ride.
 *
 * The first thing on this platform that scores a person rather than a shop, so
 * the shape is deliberately narrower than the store review it sits beside.
 *
 * ## One rating per order, not per rider
 *
 * `order_id` is unique. A rating is a fact about a delivery that happened, not
 * an opinion a customer can revise or repeat — which makes the row its own
 * receipt: it can only exist if that order exists, was delivered, and was
 * carried by that rider. Rating "a rider" in the abstract would let anyone who
 * ever met them file an unlimited number of them.
 *
 * `rider_id` is denormalised alongside it rather than read through the order,
 * because the average has to survive the order being soft-deleted and the
 * rider being unassigned. `nullOnDelete` on the order keeps the score when the
 * receipt is finally purged; `cascadeOnDelete` on the rider does not, because
 * a deleted rider's average is nobody's business.
 *
 * ## Why a comment is nullable and short
 *
 * Most people will tap a number and nothing else, and the number is what the
 * average is made of. The comment is capped at 500 so it stays a remark rather
 * than a complaint form — there is no moderation tooling behind this, and a
 * long free-text field pointed at a named individual with none is a mistake to
 * make on purpose.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('rider_ratings', function (Blueprint $table) {
            $table->uuid('id')->primary();

            $table->foreignUuid('rider_id')->constrained('riders')->cascadeOnDelete();
            $table->foreignUuid('order_id')->nullable()
                ->constrained('orders')->nullOnDelete();
            // Who left it. Nullable because a guest checkout has no account,
            // and those orders are still delivered by a rider.
            $table->foreignUuid('customer_account_id')->nullable()
                ->constrained('customer_accounts')->nullOnDelete();

            // 1..5, checked in the request rather than by the column so the
            // failure is a validation message and not a driver-level error.
            $table->unsignedTinyInteger('score');
            $table->string('comment', 500)->nullable();

            $table->timestamps();

            // The claim that makes this a receipt: one rating per delivery.
            $table->unique('order_id');
            // The average is read per rider, on every profile load.
            $table->index(['rider_id', 'score']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('rider_ratings');
    }
};
