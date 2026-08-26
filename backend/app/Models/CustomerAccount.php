<?php

namespace App\Models;

use App\Notifications\CustomerEmailVerification;
use App\Notifications\CustomerPasswordReset;
use Illuminate\Auth\MustVerifyEmail as MustVerifyEmailTrait;
use Illuminate\Contracts\Auth\MustVerifyEmail;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

/**
 * A shopper.
 *
 * Authenticatable in its own right, behind the `customer` guard, so a customer
 * token can never satisfy `auth:sanctum` on a seller route no matter what
 * abilities it carries. See config/auth.php and the migration that made the
 * table for why this is not a flag on User.
 *
 * The email is the login handle, so it is lowercased on the way in: two
 * accounts differing only in case would be two accounts to the database and
 * one account to the person typing.
 */
class CustomerAccount extends Authenticatable implements MustVerifyEmail
{
    use HasApiTokens, HasUuids, MustVerifyEmailTrait, Notifiable;

    protected $fillable = [
        'name',
        'email',
        'phone',
        'password',
        'preferences',
    ];

    protected $hidden = [
        'password',
        'remember_token',
    ];

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
            'preferences' => 'array',
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

    public function addresses(): HasMany
    {
        return $this->hasMany(CustomerAddress::class);
    }

    public function paymentMethods(): HasMany
    {
        return $this->hasMany(CustomerPaymentMethod::class);
    }

    public function orders(): HasMany
    {
        return $this->hasMany(Order::class);
    }

    /**
     * Defaults for an account that has never opened the preferences page.
     * Merged on read rather than written at registration, so adding a
     * preference later doesn't need a backfill.
     *
     * @return array<string, mixed>
     */
    public function resolvedPreferences(): array
    {
        return array_merge([
            'emailUpdates' => true,
            'smsUpdates' => false,
            'marketingEmails' => false,
            'substitutions' => 'call',
        ], $this->preferences ?? []);
    }

    /**
     * The whole account in one payload — profile, addresses and payment
     * methods together.
     *
     * One shape, returned by register, login, me and every mutation, so the
     * portal never has to reconcile a partial update against what it already
     * had. Defaults sort first because that is the one checkout opens on.
     *
     * @return array<string, mixed>
     */
    public function toStorefrontArray(): array
    {
        $this->loadMissing(['addresses', 'paymentMethods']);

        return [
            'id' => $this->id,
            'name' => $this->name,
            'email' => $this->email,
            'phone' => $this->phone ?? '',
            'preferences' => $this->resolvedPreferences(),
            // Oldest first, then the default lifted to the top. In that order:
            // PHP's sort is stable, so the last sort applied is the primary
            // one and `created_at` survives as the tiebreak.
            'addresses' => $this->addresses
                ->sortBy('created_at')
                ->sortByDesc('is_default')
                ->values()
                ->map(fn (CustomerAddress $address) => $address->toStorefrontArray())
                ->all(),
            'paymentMethods' => $this->paymentMethods
                ->sortBy('created_at')
                ->sortByDesc('is_default')
                ->values()
                ->map(fn (CustomerPaymentMethod $method) => $method->toStorefrontArray())
                ->all(),
        ];
    }

    /**
     * The framework's default sends a link into the *staff* password reset
     * route, which does not exist. This one points at the customer portal.
     */
    public function sendPasswordResetNotification(#[\SensitiveParameter] $token): void
    {
        $this->notify(new CustomerPasswordReset($token));
    }

    public function sendEmailVerificationNotification(): void
    {
        $this->notify(new CustomerEmailVerification());
    }
}
