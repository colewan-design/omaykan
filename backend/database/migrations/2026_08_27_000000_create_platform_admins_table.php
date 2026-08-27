<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Accounts for the people who operate the platform itself.
 *
 * Until now the superadmin dashboard was gated by one shared secret in the
 * server env (PLATFORM_ADMIN_SECRET). That works while the operator is one
 * person, and stops working the moment it is two: there is no way to tell who
 * verified a payment or deleted a tenant, no way to remove one person's access
 * without rotating everybody's, and the credential lives in a chat log
 * somewhere by the second week.
 *
 * A fourth table rather than a role on `users`, for the same reason `riders`
 * and `customer_accounts` are separate: `users` is store staff and
 * `auth:sanctum` resolves it on every seller and sync route, and every role in
 * this system is scoped to an organization (see the roles and membership
 * tables). A platform admin is the one identity that is deliberately *not*
 * scoped to a tenant, so it must never satisfy a tenant-scoped guard. Separate
 * table, separate provider, separate guard (`platform`) — see config/auth.php.
 *
 * There is no self-serve registration and no password reset endpoint. Accounts
 * are created from the console (`php artisan platform-admin:create`), because
 * the set of people who can act across every tenant should only ever change
 * through a deliberate act on the server.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('platform_admins', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->string('name');
            // Lowercased before every write and read — see the PlatformAdmin
            // model. Unique because it is the login handle, not a contact field.
            $table->string('email')->unique();
            $table->string('password');

            // Revoking access without deleting the row, so the audit log still
            // resolves to a name. Nullable timestamp rather than a boolean:
            // "when" is the part you want during an incident.
            $table->timestamp('disabled_at')->nullable();
            $table->timestamp('last_login_at')->nullable();

            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('platform_admins');
    }
};
