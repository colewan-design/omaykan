<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Who marked an online order paid, and when.
 *
 * Online orders are cash or GCash on arrival — there is no payment gateway, so
 * "paid" is a staff member's word for it, taken on the seller dashboard. The
 * Firestore version of the storefront already recorded both of these
 * (paymentConfirmedAt / paymentConfirmedByUserId on the order document, and
 * they are on the shared OrderSummary type); the MySQL schema never grew the
 * columns because nothing on this side could settle a payment yet. It can now.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->timestamp('payment_confirmed_at')->nullable()->after('payment_method');
            $table->uuid('payment_confirmed_by_user_id')->nullable()->after('payment_confirmed_at');
        });
    }

    public function down(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->dropColumn(['payment_confirmed_at', 'payment_confirmed_by_user_id']);
        });
    }
};
