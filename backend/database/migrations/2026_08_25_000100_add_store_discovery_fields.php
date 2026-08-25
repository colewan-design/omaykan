<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * What a customer needs to find a store by typing its code.
 *
 * The store code does two jobs, inherited from the Firestore schema, and they
 * pull in opposite directions:
 *
 *  1. It is a PUBLIC IDENTIFIER. The owner reads it off Settings > Online Store
 *     and hands it to customers; the platform admin lists it. It has to be
 *     displayable, which means recoverable, which means stored in the clear.
 *  2. It is the SECRET a till proves to pair itself (DeviceSessionController).
 *
 * Those cannot be the same column. `public_store_code` is the identifier —
 * indexed, unique, plaintext, and deliberately not a credential.
 * `pairing_code_hash` stays the bcrypt secret and is the only thing device
 * pairing authenticates against.
 *
 * Today signup seeds both from one generated value, preserving existing
 * behaviour. They are now separable: the pairing secret can be rotated without
 * changing the code customers already have.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('stores', function (Blueprint $table) {
            // Normalised (uppercased, trimmed) so lookups are exact-match.
            $table->string('public_store_code', 64)->nullable()->unique();

            // Which register layout the store runs, and therefore whether it
            // can sell online at all — a nail salon has nothing to put in a
            // cart. Previously a Firestore field on the store document.
            $table->string('business_mode')->nullable();

            // Shown on the storefront under the store name.
            $table->string('address')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('stores', function (Blueprint $table) {
            $table->dropUnique(['public_store_code']);
            $table->dropColumn(['public_store_code', 'business_mode', 'address']);
        });
    }
};
