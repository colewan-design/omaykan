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

    /**
     * What a rider can be riding.
     *
     * A closed set because the app draws an icon per entry and a shop reads it
     * as a category ("a car is coming, not a bike"), not because the world is
     * only six shapes — everything specific about the vehicle goes in the free
     * text of make, model and colour beside it.
     *
     * `ebike` is separate from `bicycle` deliberately: to a shop deciding what
     * fits in a top box and how far it will go, a pedal bicycle and an electric
     * one are different vehicles.
     */
    public const VEHICLE_TYPES = [
        'motorcycle',
        'scooter',
        'tricycle',
        'bicycle',
        'ebike',
        'car',
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
        'avatar_path',
        'vehicle_type',
        'vehicle_make',
        'vehicle_model',
        'vehicle_color',
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
        'avatar_path',
    ];

    /**
     * The column default again, in PHP.
     *
     * The migration defaults `vehicle_type` so existing rows are motorcycles,
     * but a database default only applies at INSERT — a model built in memory
     * and read back before it is refreshed has a null there, which is how
     * vehicleLabel() first met one. Declaring it here means a Rider is never
     * observed without a vehicle type, whatever route created it.
     */
    protected $attributes = [
        'vehicle_type' => 'motorcycle',
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

    /** Every score a customer has left this rider. */
    public function ratings(): HasMany
    {
        return $this->hasMany(RiderRating::class);
    }

    /**
     * The average, and how many it is made of.
     *
     * Returns a null average rather than a zero for a rider nobody has rated:
     * a fresh rider has *no* score, and rendering that as 0.0 out of 5 would
     * put the worst possible number on the screen of the person least able to
     * have earned it.
     *
     * Below [RATING_CONFIDENCE_THRESHOLD] the count travels with it so the app
     * can say "2 ratings" instead of implying an average means something yet.
     *
     * @return array{average: float|null, count: int}
     */
    public function ratingSummary(): array
    {
        // One aggregate query, not two: this is read on every profile load and
        // on every order payload that carries a rider.
        $row = $this->ratings()
            ->selectRaw('avg(score) as avg_score, count(*) as total')
            ->first();

        $count = (int) ($row->total ?? 0);

        return [
            'average' => $count === 0 ? null : round((float) $row->avg_score, 2),
            'count' => $count,
        ];
    }

    /** Under this many ratings, an average is an anecdote. */
    public const RATING_CONFIDENCE_THRESHOLD = 5;

    /**
     * The bike, as a customer at a window would describe it.
     *
     * Colour then make then model — "red Honda Click" — because colour is the
     * only one of the three readable at fifty metres in the dark, and the one
     * a customer scanning a street actually filters on. Missing parts are
     * simply left out rather than filled with a placeholder, so a rider who
     * gave nothing but a type still produces a usable "motorcycle".
     */
    public function vehicleLabel(): string
    {
        $parts = array_filter([
            $this->vehicle_color,
            $this->vehicle_make,
            $this->vehicle_model,
        ], fn (?string $p) => $p !== null && trim($p) !== '');

        // With nothing specific, the type is the description. With something,
        // the type is already implied by the make and model and would only
        // read as noise ("red Honda Click motorcycle").
        return $parts === [] ? $this->vehicle_type : implode(' ', $parts);
    }

    /**
     * The bike as structured data, for a client that wants to draw the icon
     * itself rather than take our sentence.
     *
     * @return array<string, mixed>
     */
    public function vehicleArray(): array
    {
        return [
            'type' => $this->vehicle_type,
            'make' => $this->vehicle_make,
            'model' => $this->vehicle_model,
            'color' => $this->vehicle_color,
            'label' => $this->vehicleLabel(),
            'plateNumber' => $this->plate_number,
        ];
    }

    /**
     * What the two parties to a delivery are told about the person carrying it.
     *
     * The narrowest of the three payloads on this model, and the only one that
     * leaves the platform's own surfaces: a face, a first-class description of
     * the bike, and a score. No email, no licence number, no status, no
     * counts — none of which help anybody identify a rider at a door, and all
     * of which would be handed to a stranger for every order.
     *
     * @return array<string, mixed>
     */
    public function toPublicArray(): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'photoUrl' => \App\Http\Controllers\Api\RiderAvatarController::urlFor($this),
            'vehicle' => $this->vehicleArray(),
            'rating' => $this->ratingSummary(),
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
            'photoUrl' => \App\Http\Controllers\Api\RiderAvatarController::urlFor($this),
            'vehicle' => $this->vehicleArray(),
            'rating' => $this->ratingSummary(),
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
