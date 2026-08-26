<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Accounts for the people who run the platform, and the record of what they did.
 *
 * Until now the operator dashboard was gated by one shared secret
 * (PLATFORM_ADMIN_SECRET) typed into a form. That is a single credential
 * standing in for every operator: it cannot be revoked for one person, it
 * cannot say who suspended a tenant, and the only trace an action left was an
 * anonymous line in the application log. `deleteOrg` is irreversible, so
 * "somebody with the secret did this" is not an acceptable audit trail.
 *
 * A fourth table rather than a role on `users`, for the same reason `riders`
 * and `customer_accounts` are separate from it and from each other: `users` is
 * store staff and `auth:sanctum` resolves it on every seller and sync route.
 * A platform operator acts across every tenant and must never satisfy those,
 * whatever abilities their token was minted with. Separate table, separate
 * provider, separate guard (`platform`) — see config/auth.php.
 *
 * There is no signup. Accounts exist only because an owner made one, either
 * from the portal or with `php artisan platform:admin-create` over SSH.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('platform_admins', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->string('name');
            // Lowercased before every write and read — see the model. Unique
            // because it is the login handle, not just a contact field.
            $table->string('email')->unique();
            $table->string('password');

            // owner | operator. The portal's one irreversible action —
            // deleting a tenant — and operator management are owner-only. An
            // operator can work the verification and rider queues all day
            // without ever being able to destroy a merchant's data.
            $table->string('role')->default('operator');

            // active | disabled. Disabling is how access is taken away
            // without losing the audit trail that points at the account.
            $table->string('status')->default('active')->index();

            $table->timestamp('last_seen_at')->nullable();
            $table->timestamp('last_login_at')->nullable();
            $table->rememberToken();
            $table->timestamps();
        });

        /*
         * Append-only. Nothing in the application updates or deletes a row
         * here, and no endpoint exposes a way to — a log an operator can edit
         * is not a log. Hence `created_at` alone rather than timestamps().
         */
        Schema::create('platform_audit_logs', function (Blueprint $table) {
            $table->uuid('id')->primary();

            // Nulled rather than cascaded on delete: the account can go, the
            // record of what it did must not.
            $table->foreignUuid('platform_admin_id')->nullable()
                ->constrained('platform_admins')->nullOnDelete();
            // Snapshot of who it was, so the row still names an actor after
            // the account above is gone.
            $table->string('actor_email');

            // Dotted and stable — 'organization.suspended', 'rider.decided'.
            // Filtered by prefix in the audit view.
            $table->string('action')->index();

            // What it was done to. Loose rather than a polymorphic relation:
            // the subject is sometimes a row that no longer exists (a deleted
            // organization is the single most important thing in this table).
            $table->string('subject_type')->nullable();
            $table->string('subject_id')->nullable();

            // The details worth keeping: the before/after, the rejection
            // reason, the slug that was retyped to confirm a deletion.
            $table->json('context')->nullable();

            $table->string('ip_address')->nullable();
            $table->text('user_agent')->nullable();

            $table->timestamp('created_at')->useCurrent()->index();

            $table->index(['subject_type', 'subject_id']);
        });

        Schema::table('orders', function (Blueprint $table) {
            // Platform-wide volume by date. The existing index leads on
            // store_id (see add_online_order_support), so it cannot serve a
            // cross-tenant date range — which is exactly what the operator
            // dashboard asks for.
            $table->index(['channel', 'business_date']);
        });
    }

    public function down(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->dropIndex(['channel', 'business_date']);
        });

        Schema::dropIfExists('platform_audit_logs');
        Schema::dropIfExists('platform_admins');
    }
};
