<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * A third reset-token table, for the same reason there is a second.
     *
     * `password_reset_tokens` and `customer_password_reset_tokens` are both
     * keyed by email alone, so a rider who also shops — or who owns the shop
     * they ride for, which is a real arrangement here — would overwrite their
     * own token every time they asked for a reset on the other account, and the
     * first link to arrive would be the one that had already stopped working.
     *
     * Until this table existed a rider who forgot their password had no way
     * back into the account at all: the rider routes carried no forgot-password
     * endpoint, and there is no rider-side operator tool that can set one.
     */
    public function up(): void
    {
        Schema::create('rider_password_reset_tokens', function (Blueprint $table) {
            $table->string('email')->primary();
            $table->string('token');
            $table->timestamp('created_at')->nullable();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('rider_password_reset_tokens');
    }
};
