<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * A business asking to join Omaykan, from the founding-seller campaign page.
 *
 * Deliberately not an account. The campaign promises "we set up your store",
 * so this is the enquiry an operator reads and acts on — no organization, no
 * user, no store until somebody has looked. `POST /api/signup` remains the
 * self-serve door for anyone who wants to skip the queue.
 *
 * `founding_number` is the badge the campaign offers ("Omaykan Founding Seller
 * #07"). Null until an operator accepts the application, and unique once set:
 * the number is a claim the business can point at afterwards, so two of them
 * must never carry the same one. It is assigned, not derived from a row count
 * — a count shifts the moment a declined application is deleted, and a seller
 * whose badge silently renumbered would rightly be annoyed.
 *
 * There is no unique key on email or mobile. The same owner may well apply for
 * a second stall, and a typo corrected on a second attempt is a duplicate an
 * operator can merge, not an error worth refusing at the door.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('seller_applications', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->string('business_name');
            $table->string('owner_name');
            // One of the business modes the app can actually be set up as, so
            // an operator does not have to guess which counter this becomes.
            $table->string('business_category');
            $table->string('mobile');
            $table->string('email');
            $table->string('social_url')->nullable();
            $table->string('address');
            $table->text('products_description');
            $table->boolean('offers_delivery')->default(false);
            /*
             * Whether they ticked the founding-seller box. An application that
             * did not is still an application: the campaign is a reason to
             * apply, not a condition of applying, and turning those away would
             * lose the business once the first thirty are gone.
             */
            $table->boolean('wants_founding')->default(true);
            // pending | accepted | declined
            $table->string('status')->default('pending');
            $table->unsignedSmallInteger('founding_number')->nullable()->unique();
            $table->text('operator_note')->nullable();
            $table->timestamp('reviewed_at')->nullable();
            $table->timestamps();

            $table->index(['status', 'created_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('seller_applications');
    }
};
