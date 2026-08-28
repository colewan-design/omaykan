<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The photo a shop owner uploads for their own shop.
 *
 * Settings > Business image has existed in the register for a while, but it
 * never left the device: `businessImageUrl` is an AppSettings field, held in
 * local storage as a data URL and read only by the receipt header and the
 * settings avatar. Nothing customer-facing could show it, because the server
 * had never heard of it.
 *
 * A path, not the image. The bytes go to the private disk under
 * `store-images/`, the same arrangement rider documents use — a data URL in a
 * column would put megabytes of base64 into every query that selects a store,
 * including the public directory listing.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('stores', function (Blueprint $table) {
            $table->string('image_path')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('stores', function (Blueprint $table) {
            $table->dropColumn('image_path');
        });
    }
};
