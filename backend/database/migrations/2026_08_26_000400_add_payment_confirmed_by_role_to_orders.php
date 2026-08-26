<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Which side of the counter said the money changed hands.
 *
 * Online orders are cash on arrival — there is no gateway, so "paid" is
 * somebody's word for it. Until now only the seller could give that word, from
 * the dashboard, which is the wrong shape for a delivery: the cash is handed
 * to a rider at the customer's door, nowhere near the till. The customer can
 * now confirm it too, from their own order page.
 *
 * Either confirmation marks the order paid — but they are not the same claim,
 * so the order records which one it was. `payment_confirmed_by_user_id` stays
 * the staff member who settled it and is null for a customer confirmation,
 * because a shopper is not a user of the merchant's organization.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            // 'seller' | 'customer'. Null on orders settled before this
            // existed, and on every register sale — a sale rung up at the till
            // was paid at the till, and has nobody to attribute.
            $table->string('payment_confirmed_by_role', 16)
                ->nullable()
                ->after('payment_confirmed_by_user_id');
        });
    }

    public function down(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->dropColumn('payment_confirmed_by_role');
        });
    }
};
