<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The riders a shop keeps.
 *
 * Two dispatch models live side by side on this platform and both are correct:
 *
 *   - The **board**. An order is posted platform-wide and whichever approved
 *     rider taps first carries it. This is what an aggregator does, and it is
 *     what a shop with no rider of their own needs at 7pm on a Friday.
 *   - The shop's **own rider**. A carinderia in Baguio has a nephew with a
 *     tricycle. They are not going to put their orders on a board for a
 *     stranger, and a platform that forces them to is a platform they leave.
 *
 * Until now the second case was two strings typed fresh into the dashboard for
 * every single order — the same nephew's name and number, retyped four times a
 * day, with a typo in the phone number roughly as often as you would expect.
 * This table is the fix: name the rider once, then pick them.
 *
 * A saved rider is one of two things, and the nullable `rider_id` is what tells
 * them apart:
 *
 *   - `rider_id` set — a real platform account. Assigning them writes a genuine
 *     `orders.rider_id`, so the order lands in that person's app, they can
 *     release it back to the board, and their position feeds the live map.
 *   - `rider_id` null — somebody off-platform. Name and number only, exactly
 *     what the free-text box recorded, just remembered. No app, no map, no
 *     release; the shop rings them. This case must keep working: it is most of
 *     the beachhead.
 *
 * Scoped per store, not per organization. Two branches of the same chain are in
 * different barangays and do not share a tricycle.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('store_saved_riders', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('store_id')->constrained('stores')->cascadeOnDelete();

            // Null for an off-platform rider. Set for a platform account, and
            // nulled rather than deleted if that account ever goes away — the
            // shop still knows the name and number of the person they ring.
            $table->foreignUuid('rider_id')->nullable()
                ->constrained('riders')->nullOnDelete();

            // Denormalised on purpose, and not a cache of the rider row. For an
            // off-platform rider this *is* the record. For a platform one it is
            // what the shop chose to call them — "Kuya Jun" beats the legal
            // name on the licence when you are shouting across a kitchen.
            $table->string('name');
            $table->string('phone')->nullable();
            // "Weekday mornings", "own tricycle", "only Sto. Tomas". The shop's
            // note to itself, shown under the name in the picker.
            $table->string('note')->nullable();

            // What sorts the picker. The rider a shop used twice today should
            // be the first one they see tonight, without anyone ranking
            // anything by hand.
            $table->unsignedInteger('times_used')->default(0);
            $table->timestamp('last_used_at')->nullable();

            $table->timestamps();

            // One entry per platform rider per store: picking the same account
            // twice must update the row, not grow a second one. Off-platform
            // riders fall outside this (rider_id is null and Postgres treats
            // nulls as distinct), which is right — two different people can
            // both be "Kuya Jun" and only the shop can say.
            $table->unique(['store_id', 'rider_id']);
            $table->index(['store_id', 'last_used_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('store_saved_riders');
    }
};
