<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

/**
 * Someone who operates the platform, across every tenant on it.
 *
 * Authenticatable in its own right, behind the `platform` guard, so a platform
 * token can never satisfy `auth:sanctum`, `auth:customer` or `auth:rider` — and
 * just as importantly, none of those can ever satisfy `auth:platform`. This is
 * the only identity in the system that is not scoped to an organization; see
 * the migration for why it is its own table.
 *
 * The email is the login handle, so it is lowercased on the way in: two
 * accounts differing only in case would be two accounts to the database and
 * one account to the person typing.
 */
class PlatformAdmin extends Authenticatable
{
    use HasApiTokens, HasUuids, Notifiable;

    /** The ability every platform token is minted with. */
    public const ABILITY = 'platform-admin';

    protected $fillable = [
        'name',
        'email',
        'password',
        'disabled_at',
    ];

    protected $hidden = [
        'password',
        'remember_token',
    ];

    protected function casts(): array
    {
        return [
            'password' => 'hashed',
            'disabled_at' => 'datetime',
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

    /**
     * The operator who has already signed in with this Google account.
     *
     * Matched on Google's `sub`, which does not change when the address on the
     * Google account does. Kept out of `$fillable` on purpose: this column is
     * what makes a Google mailbox equal to cross-tenant access, so it is
     * written in exactly one place — [PlatformAdminAuthController::google],
     * through `forceFill` — and never from a request body.
     */
    public static function findByGoogleSub(string $sub): ?self
    {
        return static::query()->where('google_sub', $sub)->first();
    }

    public function isDisabled(): bool
    {
        return $this->disabled_at !== null;
    }

    /**
     * What the dashboard shows about the signed-in operator.
     *
     * @return array<string, mixed>
     */
    public function toSessionArray(): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'email' => $this->email,
            'lastLoginAt' => $this->last_login_at?->toIso8601String(),
        ];
    }
}
