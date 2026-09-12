<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

/**
 * What the marketplace calls itself, and the two policies it applies across
 * every shop on it.
 *
 * ## Why a table at all
 *
 * Everything the operator portal showed until now belonged to somebody — an
 * organization, a store, a customer. The marketplace itself owned nothing:
 * its name lived in `index.html`, its delivery fee in whichever controller
 * charged one, and its support address in a `.env` key. None of that is
 * editable by the person who actually answers for it, and the operator portal
 * is where that person already is.
 *
 * ## One row, enforced
 *
 * There is exactly one marketplace, so there is exactly one row — `singleton`
 * is a constant column with a unique index, which is the cheapest way to make
 * a second row impossible at the database rather than by convention. A
 * settings table that quietly grows a second row is a bug that presents as
 * "my change did not save", and it is worth one column to rule out.
 *
 * The row is created by this migration rather than lazily on first read, so
 * every reader can assume it exists and no request path has to write.
 *
 * ## Why delivery and notifications are JSON, and identity is not
 *
 * The identity columns are read by name, one at a time, by things that are not
 * this portal — a page title, a footer, an email signature — and each wants a
 * real column with a real type. The two policy blobs are read and written
 * whole, only ever by the screen that edits them, and their shape is still
 * moving; a JSON column lets that shape change without a migration per field.
 * The trade is deliberate: no query will ever filter on them.
 *
 * Money is in centavos, like every other amount in this schema (`total_cents`,
 * `price_cents`). Storing pesos here would make this the one table where a
 * float rounds someone's delivery fee.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('platform_settings', function (Blueprint $table) {
            $table->uuid('id')->primary();

            // Always 1. See the class comment: this is what makes a second row
            // a database error rather than a support ticket.
            $table->unsignedTinyInteger('singleton')->default(1)->unique();

            $table->string('name');
            $table->string('tagline')->nullable();
            $table->text('description')->nullable();
            $table->string('website')->nullable();
            $table->string('address')->nullable();
            $table->string('contact_email')->nullable();
            $table->string('contact_phone')->nullable();

            // Shapes are validated in PlatformSettingsController, which is the
            // only writer. Defaults are set below rather than in the column so
            // the seeded row and a reset row agree.
            $table->json('delivery')->nullable();
            $table->json('notifications')->nullable();

            $table->timestamps();
        });

        // The row every reader assumes. Values match what the storefront says
        // about itself today, so nothing changes visibly on deploy.
        DB::table('platform_settings')->insert([
            'id' => (string) Str::uuid(),
            'singleton' => 1,
            'name' => 'Omaykan',
            'tagline' => 'Your neighbourhood market, online.',
            'description' => 'Groceries, sari-sari stores, wet market sellers and local shops near you — '
                .'delivered at the price they charge at the counter.',
            'website' => 'https://omaykan.com',
            'address' => 'Baguio City, Benguet, Philippines',
            'contact_email' => 'support@omaykan.com',
            'contact_phone' => null,
            'delivery' => json_encode([
                'baseFeeCents' => 4900,
                'freeDeliveryOverCents' => 0,
                'maxDistanceKm' => 12,
            ]),
            'notifications' => json_encode([
                'newOrder' => true,
                'newSeller' => true,
                'lowStock' => false,
                'weeklySummary' => false,
            ]),
            'created_at' => now(),
            'updated_at' => now(),
        ]);
    }

    public function down(): void
    {
        Schema::dropIfExists('platform_settings');
    }
};
