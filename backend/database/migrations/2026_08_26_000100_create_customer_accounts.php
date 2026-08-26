<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Accounts for the people who buy, as opposed to the people who sell.
 *
 * Until now the storefront had no identity at all: an order carried a
 * `guest_contact` blob and was addressable only by its own UUID, which is why
 * the customer portal had to keep addresses in localStorage and why order
 * history could not survive a new phone.
 *
 * Deliberately a separate table from `users` rather than a role on it.
 * `users` is staff — it authenticates by username against a store's roster,
 * and `auth:sanctum` on every seller route resolves it. A shopper who ended up
 * in that table would be one forgotten ability check away from the seller API,
 * and the two identities have almost no columns in common. Separate table,
 * separate provider, separate guard (`customer`) — see config/auth.php.
 *
 * Orders gain a nullable link rather than a required one: guest checkout stays
 * exactly as it was, and an account is an upgrade over it, not a gate in front
 * of it.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('customer_accounts', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->string('name');
            // Lowercased before every write and read — see CustomerAccount.
            // Unique because it is the login handle, not just a contact field.
            $table->string('email')->unique();
            $table->string('phone')->nullable();
            $table->string('password');
            $table->timestamp('email_verified_at')->nullable();
            // How to reach them and what a rider should do with a missing item.
            // JSON rather than four columns: it is a bag of client preferences
            // that will grow, and nothing queries across it.
            $table->json('preferences')->nullable();
            $table->rememberToken();
            $table->timestamps();
        });

        Schema::create('customer_addresses', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('customer_account_id')->constrained()->cascadeOnDelete();
            // The customer's own name for the place — "Home", "Mum's".
            $table->string('label');
            $table->string('line1');
            $table->string('barangay')->nullable();
            $table->string('city');
            // Gate colour, landmark, which door. What the rider actually reads.
            $table->text('notes')->nullable();
            // Optional drop-off pin, so checkout can quote the distance fee
            // from a saved address without asking for location again.
            $table->double('lat')->nullable();
            $table->double('lng')->nullable();
            $table->boolean('is_default')->default(false);
            $table->timestamps();

            $table->index(['customer_account_id', 'is_default']);
        });

        Schema::create('customer_payment_methods', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('customer_account_id')->constrained()->cascadeOnDelete();
            // 'cash' | 'ewallet' — the only two values POST /api/online-orders
            // accepts. Nothing is charged here; both settle on arrival, so
            // there is no card number and no processor token to store.
            $table->string('kind');
            // The e-wallet number to pay from. Empty for cash.
            $table->string('detail')->nullable();
            $table->boolean('is_default')->default(false);
            $table->timestamps();

            $table->index(['customer_account_id', 'is_default']);
        });

        // Its own table rather than sharing `password_reset_tokens` with staff:
        // that table is keyed by email alone, so one shop owner and one shopper
        // with the same address would silently overwrite each other's token.
        Schema::create('customer_password_reset_tokens', function (Blueprint $table) {
            $table->string('email')->primary();
            $table->string('token');
            $table->timestamp('created_at')->nullable();
        });

        Schema::table('orders', function (Blueprint $table) {
            // Nullable, and null for every order placed before accounts existed
            // or placed as a guest afterwards. nullOnDelete rather than cascade:
            // deleting a person must never delete the shop's sales record.
            $table->foreignUuid('customer_account_id')
                ->nullable()
                ->after('user_id')
                ->constrained('customer_accounts')
                ->nullOnDelete();
        });
    }

    public function down(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->dropConstrainedForeignId('customer_account_id');
        });

        Schema::dropIfExists('customer_password_reset_tokens');
        Schema::dropIfExists('customer_payment_methods');
        Schema::dropIfExists('customer_addresses');
        Schema::dropIfExists('customer_accounts');
    }
};
