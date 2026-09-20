<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * A shop's own "we're not taking orders right now".
 *
 * Everything that could close a storefront until now belonged to somebody
 * else: `stores.status` is structural, and the organization's TenantAccess
 * verdict is the platform's. A shop that has run out of rice, or whose only cook
 * has gone home, had no way to stop the orders for the evening.
 *
 * A pause rather than a status, and with an end. "Closed until tomorrow
 * morning" is one tap, and a shop that forgets to reopen is not quietly closed
 * for a week. A null resume time is "until I reopen it by hand".
 *
 * Business hours — a weekly schedule — are deliberately not this. See
 * documentation/merchant-features.md §3.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('stores', function (Blueprint $table) {
            // Null is open.
            $table->timestamp('ordering_paused_at')->nullable();
            // Null while paused is "until reopened by hand".
            $table->timestamp('ordering_resumes_at')->nullable();
            // Who, so "why is the shop closed?" has an answer.
            $table->foreignUuid('ordering_paused_by')->nullable()->constrained('users')->nullOnDelete();
        });
    }

    public function down(): void
    {
        Schema::table('stores', function (Blueprint $table) {
            $table->dropConstrainedForeignId('ordering_paused_by');
            $table->dropColumn(['ordering_paused_at', 'ordering_resumes_at']);
        });
    }
};
