<?php

namespace App\Models;

use App\Notifications\SellerEmailVerification;
use Database\Factories\UserFactory;
use Illuminate\Auth\MustVerifyEmail as MustVerifyEmailTrait;
use Illuminate\Contracts\Auth\MustVerifyEmail;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\SoftDeletes;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Laravel\Sanctum\HasApiTokens;
use Illuminate\Notifications\Notifiable;

class User extends Authenticatable implements MustVerifyEmail
{
    /** @use HasFactory<UserFactory> */
    use HasApiTokens, HasFactory, HasUuids, MustVerifyEmailTrait, Notifiable, SoftDeletes;

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'name',
        'email',
        'google_sub',
        'avatar_url',
        'username',
        'password',
        'status',
    ];

    /**
     * The attributes that should be hidden for serialization.
     *
     * @var list<string>
     */
    protected $hidden = [
        'password',
        'remember_token',
        // Google's subject id. Not a secret, but it is a stable cross-service
        // identifier for a person and no client here has a use for it.
        'google_sub',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
        ];
    }

    /**
     * The staff account behind a Google identity, matched on Google's subject
     * id rather than the email.
     *
     * Deliberately not the email, for the reason CustomerAccount states: Google
     * lets someone change the address on their account, and matching on it
     * would eventually hand one person's till access to whoever inherits their
     * old address.
     */
    public static function findByGoogleSub(string $sub): ?self
    {
        return static::query()->where('google_sub', $sub)->first();
    }

    /**
     * False for someone who has only ever pressed the Google button.
     *
     * The column is nullable now, so every caller that was reaching for
     * `$user->password` has to ask this first — `Hash::check()` against null is
     * a TypeError, and against a random hash it is a silent lie.
     */
    public function hasPassword(): bool
    {
        return $this->password !== null && $this->password !== '';
    }

    public function usesGoogle(): bool
    {
        return $this->google_sub !== null;
    }

    public function organizationMemberships()
    {
        return $this->hasMany(OrganizationMembership::class);
    }

    public function storeMemberships()
    {
        return $this->hasMany(StoreMembership::class);
    }

    public function sendEmailVerificationNotification(): void
    {
        $this->notify(new SellerEmailVerification());
    }
}
