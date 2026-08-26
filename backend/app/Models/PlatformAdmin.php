<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

/**
 * Someone who runs the platform, across every tenant on it.
 *
 * Authenticatable in its own right, behind the `platform` guard, so an
 * operator token can never satisfy `auth:sanctum` on a seller or sync route
 * and a staff token can never reach a cross-tenant one — the same separation
 * `customer` and `rider` get, and for the same reason. See config/auth.php and
 * the migration for the full argument.
 *
 * The email is the login handle, so it is lowercased on the way in: two
 * accounts differing only in case would be two accounts to the database and
 * one account to the person typing.
 */
class PlatformAdmin extends Authenticatable
{
    use HasApiTokens, HasUuids, Notifiable;

    /** Can do everything, including delete a tenant and manage operators. */
    public const ROLE_OWNER = 'owner';

    /** Works the queues. Cannot destroy anything or create an account. */
    public const ROLE_OPERATOR = 'operator';

    public const ROLES = [self::ROLE_OWNER, self::ROLE_OPERATOR];

    public const STATUS_ACTIVE = 'active';

    /** Access revoked; the account stays so the audit trail still names it. */
    public const STATUS_DISABLED = 'disabled';

    public const STATUSES = [self::STATUS_ACTIVE, self::STATUS_DISABLED];

    /** The name every portal token is minted under. */
    public const TOKEN_NAME = 'platform-portal';

    protected $fillable = [
        'name',
        'email',
        'password',
        'role',
        'status',
    ];

    protected $hidden = [
        'password',
        'remember_token',
    ];

    protected function casts(): array
    {
        return [
            'password' => 'hashed',
            'last_seen_at' => 'datetime',
            'last_login_at' => 'datetime',
        ];
    }

    public function setEmailAttribute(?string $value): void
    {
        $this->attributes['email'] = $value === null ? null : strtolower(trim($value));
    }

    /** Every lookup goes through here, so casing can't create a second account. */
    public static function findByEmail(string $email): ?self
    {
        return static::query()->where('email', strtolower(trim($email)))->first();
    }

    public function isActive(): bool
    {
        return $this->status === self::STATUS_ACTIVE;
    }

    public function isOwner(): bool
    {
        return $this->role === self::ROLE_OWNER;
    }

    /** Everything this operator has done, newest first. */
    public function auditLogs(): HasMany
    {
        return $this->hasMany(PlatformAuditLog::class);
    }

    /**
     * What the portal knows about the signed-in operator. `role` is in here
     * because the shell hides the owner-only sections with it — the server
     * still enforces them, this only saves showing a door that won't open.
     *
     * @return array<string, mixed>
     */
    public function toPortalArray(): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'email' => $this->email,
            'role' => $this->role,
            'status' => $this->status,
            'lastLoginAt' => $this->last_login_at?->toIso8601String(),
            'createdAt' => $this->created_at?->toIso8601String(),
        ];
    }
}
