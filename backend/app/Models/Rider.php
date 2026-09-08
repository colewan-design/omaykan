<?php

namespace App\Models;

use App\Notifications\RiderPasswordReset;
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
            'position_updated_at' => 'datetime',
            'last_lat' => 'float',
            'last_lng' => 'float',
            'last_heading_deg' => 'float',
            'last_speed_kph' => 'float',
            'last_accuracy_m' => 'float',
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

    /**
     * The framework's default links at a named `web` route this application
     * does not have — there is no Blade password page anywhere, only the Vue
     * portals — so the notification builds its own URL. Overridden here rather
     * than configured, exactly as CustomerAccount does it.
     */
    public function sendPasswordResetNotification(#[\SensitiveParameter] $token): void
    {
        $this->notify(new RiderPasswordReset($token));
    }

    /** Orders this rider has claimed, at any stage. */
    public function orders(): HasMany
    {
        return $this->hasMany(Order::class);
    }

    /**
     * A fix older than this is not a position, it is a memory.
     *
     * The app pings every ten seconds while it is carrying something, so two
     * minutes is a dozen missed pings — a phone in a dead spot, a killed
     * process, a flat battery. Past that the map stops drawing a moving rider
     * and says when it last heard from them, which is the honest thing to show
     * a customer watching a marker that has stopped.
     */
    public const POSITION_STALE_AFTER_SECONDS = 120;

    /** Orders this rider is physically carrying right now. */
    public function activeOrders(): HasMany
    {
        return $this->orders()->whereIn('delivery_stage', ['assigned', 'picked_up']);
    }

    /** Saved-rider rows across every shop that keeps this rider on file. */
    public function savedBy(): HasMany
    {
        return $this->hasMany(StoreSavedRider::class);
    }

    /** Whether there is a fix recent enough to draw. */
    public function isReportingPosition(): bool
    {
        return $this->last_lat !== null
            && $this->last_lng !== null
            && $this->position_updated_at !== null
            && $this->position_updated_at->gt(now()->subSeconds(self::POSITION_STALE_AFTER_SECONDS));
    }

    /**
     * The last fix, or null if there has never been one.
     *
     * Deliberately returns a stale fix rather than hiding it, with `stale` set
     * and `ageSeconds` alongside — the caller decides. A customer whose rider
     * dropped off the network eight minutes ago is far better served by "last
     * seen here, 8 minutes ago" than by an empty map, and a shop deciding
     * whether to ring needs exactly that number.
     *
     * This is never reached through the rider's own account. Only an order they
     * are carrying discloses it, and only to that order's two parties.
     *
     * @return array<string, mixed>|null
     */
    public function positionArray(): ?array
    {
        if ($this->last_lat === null || $this->last_lng === null || $this->position_updated_at === null) {
            return null;
        }

        $age = $this->position_updated_at->diffInSeconds(now());

        return [
            'lat' => (float) $this->last_lat,
            'lng' => (float) $this->last_lng,
            'headingDeg' => $this->last_heading_deg !== null ? (float) $this->last_heading_deg : null,
            'speedKph' => $this->last_speed_kph !== null ? (float) $this->last_speed_kph : null,
            'accuracyM' => $this->last_accuracy_m !== null ? (float) $this->last_accuracy_m : null,
            'at' => $this->position_updated_at->toIso8601String(),
            'ageSeconds' => (int) $age,
            'stale' => $age > self::POSITION_STALE_AFTER_SECONDS,
        ];
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
            'reportingPosition' => $this->isReportingPosition(),
        ];
    }
}
