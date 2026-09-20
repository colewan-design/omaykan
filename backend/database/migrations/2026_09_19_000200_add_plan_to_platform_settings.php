<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The price of Omaykan, moved out of the controller that creates accounts.
 *
 * It was `SignupController::PLAN_AMOUNT_CENTS`, a private constant — so the
 * price of the product was defined by the file that handles signups, and
 * changing it was a deploy. It is now a block on the marketplace's own record,
 * beside the delivery policy, edited from the same screen.
 *
 * JSON for the reason `delivery` is JSON: read and written whole, only by the
 * settings screen and by signup, and its shape will move the day there is a
 * second tier. Null is fine — PlatformSetting::planSettings() merges over the
 * defaults, which are what the constant used to say, so nothing changes on
 * deploy.
 *
 * A price change here re-prices **new** signups only. Each subscription keeps
 * its own `amount_cents`, which is what that organization agreed to.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('platform_settings', function (Blueprint $table) {
            $table->json('plan')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('platform_settings', function (Blueprint $table) {
            $table->dropColumn('plan');
        });
    }
};
