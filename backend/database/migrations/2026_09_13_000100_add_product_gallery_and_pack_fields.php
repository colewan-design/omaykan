<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * What the storefront's product page shows that a till receipt never needed:
 * more than one photograph of the pack, who makes it, and what it comes in.
 *
 * A gallery is a json array of urls rather than its own table. The register
 * already holds a product photo as a plain string — a hosted url for a seeded
 * line, a data url for one a merchant photographed at the counter — and the
 * whole catalog travels between till and server as whole product rows through
 * SyncController. A second table would need its own ordering column, its own
 * sync events and its own orphan cleanup to express what "the merchant dragged
 * photo three to the front" already means in an ordered array.
 *
 * `image_url` keeps its meaning — the primary shot, the one every card, order
 * line and directory tile reads — and `photo_urls` holds the rest, in the
 * order the merchant put them in. That way nothing that reads a product today
 * has to learn about galleries.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('products', function (Blueprint $table) {
            // The extra shots, after image_url. Null and [] both mean "just
            // the one photo", which is what almost every row is.
            $table->json('photo_urls')->nullable();
            // "Capri", "Marby" — the name on the pack, which is not the
            // product name a merchant types ("Tomatoes Peeled 400g").
            $table->string('brand')->nullable();
            // "Can", "Sachet", "Bottle" — free text, because the taxonomy of
            // Philippine sari-sari packaging is not ours to close.
            $table->string('packaging_type')->nullable();
        });

        // A varchar(255) could never have held what the register actually puts
        // in this column: a photo taken at the counter is a base64 data url
        // tens of kilobytes long. Nothing had noticed because the register's
        // product image was never written server-side at all — see
        // SyncController::applyProductEvent, which this release fixes.
        Schema::table('products', function (Blueprint $table) {
            $table->text('image_url')->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->dropColumn(['photo_urls', 'brand', 'packaging_type']);
        });

        Schema::table('products', function (Blueprint $table) {
            $table->string('image_url')->nullable()->change();
        });
    }
};
