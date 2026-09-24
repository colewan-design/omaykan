<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The landmark: how a rider actually finds a door.
 *
 * A street address alone is frequently not enough to deliver to here — an
 * address like "Loakan, Baguio City" names a district, not a gate. What closes
 * the gap is the thing beside it: the green gate, the sari-sari store, the
 * third house past the basketball court.
 *
 * Until now the only place to put that was the saved address's `notes`, which
 * checkout concatenated onto the end of the address line with an em dash before
 * sending it. That made one field out of two different things: a rider reading
 * `delivery_address` got directions and an address mixed together, and a
 * one-off address typed at checkout had nowhere to put a landmark at all.
 *
 * Nullable, and stays nullable: a landmark is genuinely optional, pickup orders
 * have no use for one, and every order placed before this column existed has
 * none. 200 characters is a landmark, not a second address — the address column
 * beside it takes 500.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->string('delivery_landmark', 200)->nullable()->after('delivery_address');
        });
    }

    public function down(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->dropColumn('delivery_landmark');
        });
    }
};
