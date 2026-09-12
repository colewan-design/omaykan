<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * A face and a bike.
 *
 * Until now a rider arriving at a door was a name and a phone number. Both of
 * those are things the customer reads *after* they have already opened it. The
 * two columns that matter here are the ones that answer "is that them?" from a
 * window: what they look like, and what they are riding.
 *
 * ## Why the photo sits on the private disk with the licence
 *
 * `avatar_path` is a path on the **private** disk, exactly like
 * `license_image_path` beside it, and it is streamed through a route rather
 * than published under `/storage`. The licence is private because it is an
 * identity document; this one is because a directory-listable folder of every
 * rider's face is a scraping target, and because a rider who removes their
 * photo needs it to actually stop resolving.
 *
 * The route follows StoreImageController::show — keyed by the rider's UUID,
 * which is unguessable, and handed out only inside an order payload the two
 * parties to that delivery already receive. The bytes are not secret from the
 * customer holding the order; they are simply not enumerable by anyone else.
 *
 * It is nullable and stays nullable. A rider who does not want their face on a
 * stranger's phone keeps the initial-letter avatar the app already draws, and
 * nothing in the product is allowed to require this.
 *
 * ## Why the vehicle is four columns rather than one string
 *
 * "Red Honda Click" typed into one box cannot be searched, cannot be shown as
 * a coloured dot, and cannot be validated. Split, `vehicle_type` is a small
 * closed set the app renders as an icon, and the other three are free text
 * because the long tail of what people actually ride in this market — a
 * rebuilt tricycle, an unbadged e-bike — does not fit a dropdown.
 *
 * `vehicle_type` is **not** nullable and defaults to motorcycle: every existing
 * rider signed up through a form that said "your bike", so backfilling them as
 * motorcycles is the truth for effectively all of them, and a nullable type
 * would push a null check into every screen that draws the icon.
 */
return new class extends Migration
{
    /** What a rider can be riding. Mirrored in Rider::VEHICLE_TYPES. */
    private const TYPES = ['motorcycle', 'scooter', 'tricycle', 'bicycle', 'ebike', 'car'];

    public function up(): void
    {
        Schema::table('riders', function (Blueprint $table) {
            $table->string('avatar_path')->nullable()->after('plate_image_path');

            $table->string('vehicle_type')->default('motorcycle')->after('avatar_path');
            $table->string('vehicle_make', 60)->nullable()->after('vehicle_type');
            $table->string('vehicle_model', 60)->nullable()->after('vehicle_make');
            $table->string('vehicle_color', 40)->nullable()->after('vehicle_model');
        });
    }

    public function down(): void
    {
        Schema::table('riders', function (Blueprint $table) {
            $table->dropColumn([
                'avatar_path',
                'vehicle_type',
                'vehicle_make',
                'vehicle_model',
                'vehicle_color',
            ]);
        });
    }
};
