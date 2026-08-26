<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Accounts for the people who carry the orders.
 *
 * Until now a rider was two strings on the order — `rider_name`, `rider_phone`
 * — typed in by whoever was at the till, because there was no rider app and no
 * rider accounts (see SellerOrderController::assignRider). That works exactly
 * as long as the shop knows a rider personally. It does not scale to "local
 * riders" as a platform promise: nobody can sign up, nobody can see what is
 * waiting, and the shop has to chase someone by phone for every delivery.
 *
 * A third table rather than a role on `users` or `customer_accounts`, for the
 * same reason those two are separate from each other: `users` is store staff
 * and `auth:sanctum` resolves it on every seller and sync route; a rider works
 * across every shop on the platform and must never satisfy those. Separate
 * table, separate provider, separate guard (`rider`) — see config/auth.php.
 *
 * Riders arrive unvetted and stay that way until a human looks at their
 * licence, so `status` gates everything: a pending rider can sign in and see
 * that they are pending, and nothing else.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('riders', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->string('name');
            // Lowercased before every write and read — see the Rider model.
            // Unique because it is the login handle, not just a contact field.
            $table->string('email')->unique();
            // Not nullable, unlike a customer's: this is the number a shop
            // rings when an order goes quiet, and the one the customer meets
            // at the door. A rider without a phone is not dispatchable.
            $table->string('phone');
            $table->string('password');

            // What a human reviews before this account can take work.
            $table->string('license_number');
            $table->string('plate_number');
            // Paths on the *private* disk, never public URLs. A driver's
            // licence photo is identity-document material: it is served only
            // to the platform operator, through an authenticated endpoint, and
            // never to a shop or a customer. See RiderDocumentController.
            $table->string('license_image_path');
            $table->string('plate_image_path');

            // pending → approved | rejected, and approved → suspended.
            $table->string('status')->default('pending')->index();
            // Shown to the rider on their own status screen, so a rejection is
            // something they can act on rather than a dead end.
            $table->text('review_note')->nullable();
            $table->timestamp('reviewed_at')->nullable();

            $table->timestamp('last_seen_at')->nullable();
            $table->rememberToken();
            $table->timestamps();
        });

        Schema::table('orders', function (Blueprint $table) {
            // Nullable, and the old free-text columns stay: a shop that hands
            // an order to a rider it knows still types a name, and that order
            // simply has no rider_id. The portal fills all three.
            $table->foreignUuid('rider_id')->nullable()->after('rider_phone')
                ->constrained('riders')->nullOnDelete();
            // The claim: how the job board knows what is still unclaimed, and
            // how a rider's own list is read back.
            $table->timestamp('rider_accepted_at')->nullable()->after('rider_id');
        });
    }

    public function down(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->dropConstrainedForeignId('rider_id');
            $table->dropColumn('rider_accepted_at');
        });

        Schema::dropIfExists('riders');
    }
};
