<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Where the rider actually is.
 *
 * The rider app shipped without location of any kind, for a reason recorded in
 * its README: the API had nowhere to put a coordinate, no endpoint to receive
 * one and no consumer for it. Asking a rider for background location and then
 * discarding it would have been the worst of both. This is the other half —
 * the backend can now receive it, so the app can now ask for it.
 *
 * Six columns on `riders` rather than a `rider_positions` history table.
 * A live map wants exactly one thing: where this rider is *now*. History is a
 * different feature with different retention questions attached to it (how long
 * do we keep a trace of a person's movements, and who may read it), and the
 * cheapest honest answer today is that we keep no trace at all — each ping
 * overwrites the last. A breadcrumb table can be added the day something
 * actually needs to read a breadcrumb.
 *
 * Nothing here is served by the rider's own account. Position is only ever
 * disclosed through an *order*, to that order's shop and that order's customer,
 * and only while the delivery is in flight — see Order::riderPositionForCustomer.
 * A rider who is signed in but carrying nothing is not on anybody's map.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('riders', function (Blueprint $table) {
            // 10,7 is ~1cm of precision, far past what a phone GPS gives and
            // past what a delivery needs. Decimal rather than float because
            // Postgres float comparisons on coordinates are a trap.
            $table->decimal('last_lat', 10, 7)->nullable()->after('last_seen_at');
            $table->decimal('last_lng', 10, 7)->nullable()->after('last_lat');
            // Which way the bike is pointing, so the marker on the map can be
            // an arrow rather than a dot. Null when the phone is stationary and
            // has no bearing to give.
            $table->decimal('last_heading_deg', 5, 2)->nullable()->after('last_lng');
            $table->decimal('last_speed_kph', 6, 2)->nullable()->after('last_heading_deg');
            // The GPS's own opinion of itself, in metres. A 500m fix is a
            // cell-tower guess and the map should not draw it as a position.
            $table->decimal('last_accuracy_m', 8, 2)->nullable()->after('last_speed_kph');
            // Separate from last_seen_at: that one moves on every API call the
            // rider makes, this one only when a real fix arrives. A map needs
            // to know the difference between "signed in" and "reporting".
            $table->timestamp('position_updated_at')->nullable()->after('last_accuracy_m');
        });
    }

    public function down(): void
    {
        Schema::table('riders', function (Blueprint $table) {
            $table->dropColumn([
                'last_lat',
                'last_lng',
                'last_heading_deg',
                'last_speed_kph',
                'last_accuracy_m',
                'position_updated_at',
            ]);
        });
    }
};
