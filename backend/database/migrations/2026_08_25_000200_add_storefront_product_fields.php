<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Product fields the storefront renders that the register never needed.
 *
 * apps/web/src/storefront/catalog.ts reads all four straight off the Firestore
 * product document; without them the Laravel catalog endpoint cannot produce
 * the shape the storefront already expects.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('products', function (Blueprint $table) {
            // Struck-through "was ₱X" price. Null means not on offer.
            $table->integer('compare_at_price_cents')->nullable();
            $table->string('image_url')->nullable();
            // "per kg", "per piece" — shown beside weighted items.
            $table->string('unit_label')->nullable();
            $table->integer('low_stock_threshold')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->dropColumn([
                'compare_at_price_cents',
                'image_url',
                'unit_label',
                'low_stock_threshold',
            ]);
        });
    }
};
