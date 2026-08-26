<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

/**
 * Someone who carries orders, across every shop on the platform.
 *
 * Authenticatable in its own right, behind the `rider` guard, so a rider token
 * can never satisfy `auth:sanctum` on a seller or sync route no matter what
 * abilities it carries — the same separation `customer` gets, and for the same
 * reason. See config/auth.php and the migration for the full argument.
 *
 * The email is the login handle, so it is lowercased on the way in: two
 * accounts differing only in case would be two accounts to the database and
 * one account to the person typing.
 */
class Rider extends Authenticatable
{
    use HasApiTokens, HasUuids, Notifiable;

    /** Signed up, waiting on a human to look at the licence. */
    public const STATUS_PENDING = 'pending';

    /** Cleared to see the job board and take work. */
    public const STATUS_APPROVED = 'approved';

    /** Turned down at review. Terminal unless an operator reopens it. */
    public const STATUS_REJECTED = 'rejected';

    /** Was approved, then stopped. Same access as rejected, different story. */
    public const STATUS_SUSPENDED = 'suspended';

    public const STATUSES = [
        self::STATUS_PENDING,
        self::STATUS_APPROVED,
        self::STATUS_REJECTED,
        self::STATUS_SUSPENDED,
    ];

    protected $fillable = [
        'name',
        'email',
        'phone',
        'password',
        'license_number',
        'plate_number',
        'license_image_path',
        'plate_image_path',
        'status',
        'review_note',
        'reviewed_at',
    ];

    /**
     * The document paths are hidden as well as the password. They are not
     * secret from the operator, but nothing that serialises a rider should be
     * able to leak the location of an identity document by accident — the one
     * endpoint that serves them names them explicitly.
     */
    protected $hidden = [
        'password',
        'remember_token',
        'license_image_path',
        'plate_image_path',
    ];

    protected function casts(): array
    {
        return [
            'password' => 'hashed',
            'reviewed_at' => 'datetime',
            'last_seen_at' => 'datetime',
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

    public function isApproved(): bool
    {
        return $this->status === self::STATUS_APPROVED;
    }

    /** Orders this rider has claimed, at any stage. */
    public function orders(): HasMany
    {
        return $this->hasMany(Order::class);
    }

    /**
     * What the rider sees about themselves. Deliberately does not include the
     * document paths — the rider already knows what they uploaded, and the
     * portal has no reason to be able to address the files.
     *
     * @return array<string, mixed>
     */
    public function toPortalArray(): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'email' => $this->email,
            'phone' => $this->phone,
            'licenseNumber' => $this->license_number,
            'plateNumber' => $this->plate_number,
            'status' => $this->status,
            'reviewNote' => $this->review_note,
            'reviewedAt' => $this->reviewed_at?->toIso8601String(),
            'createdAt' => $this->created_at?->toIso8601String(),
        ];
    }

    /**
     * What the operator sees in the review queue. Adds the counts that make a
     * suspend decision possible and flags that the documents are fetchable,
     * without putting their paths in the payload.
     *
     * @return array<string, mixed>
     */
    public function toReviewArray(): array
    {
        return $this->toPortalArray() + [
            'deliveriesCompleted' => $this->orders()
                ->where('delivery_stage', 'delivered')
                ->count(),
            'lastSeenAt' => $this->last_seen_at?->toIso8601String(),
        ];
    }
}
