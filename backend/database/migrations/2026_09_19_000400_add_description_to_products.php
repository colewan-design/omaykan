<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * What a product is, in the shop's own words.
 *
 * The storefront's product page had a name, a price, photos and a unit, and
 * nowhere to say "hand-rolled every morning" or "good for six". No client
 * collected it and no column held it. Plain text, shown as written, capped at
 * 2,000 characters by SyncController.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->text('description')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->dropColumn('description');
        });
    }
};
