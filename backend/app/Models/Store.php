<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Store extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    /**
     * The register layouts that can put something in a cart.
     *
     * A nail salon runs on this platform and has nothing to sell online, so it
     * is never listed in the shop directory and never accepts a storefront
     * order. This lived on StoreCodeController until store codes were retired,
     * and OnlineOrderController kept a second copy of it; both read this now,
     * because two lists of the same rule drift.
     */
    public const ONLINE_MODES = ['coffee-shop', 'grocery', 'restaurant'];

    protected $fillable = [
        'organization_id',
        'name',
        'code',
        'timezone',
        'currency_code',
        'status',
        'business_mode',
        'business_type_label',
        'address',
        'lat',
        'lng',
        'image_path',
        'ordering_paused_at',
        'ordering_resumes_at',
        'ordering_paused_by',
    ];

    protected function casts(): array
    {
        return [
            'lat' => 'float',
            'lng' => 'float',
            'ordering_paused_at' => 'datetime',
            'ordering_resumes_at' => 'datetime',
        ];
    }

    /**
     * Whether the shop has paused its own online ordering right now.
     *
     * A pause whose resume time has passed is over, whether or not anything has
     * cleared the columns: nothing needs to run at 4pm for a shop that said
     * "back at 4pm" to be open at 4pm. The columns are cleared the next time
     * someone touches the switch.
     */
    public function isOrderingPaused(): bool
    {
        if ($this->ordering_paused_at === null) {
            return false;
        }

        return $this->ordering_resumes_at === null || $this->ordering_resumes_at->isFuture();
    }

    /**
     * What to tell a shopper, in the shop's own time zone. Null when open.
     */
    public function orderingPausedMessage(): ?string
    {
        if (! $this->isOrderingPaused()) {
            return null;
        }

        if ($this->ordering_resumes_at === null) {
            return "This shop isn't taking orders right now.";
        }

        $resumes = $this->ordering_resumes_at->copy()->setTimezone($this->timezone ?: 'Asia/Manila');
        $when = $resumes->isSameDay(now($resumes->timezone))
            ? $resumes->format('g:i A')
            : $resumes->format('D g:i A');

        return "This shop isn't taking orders right now — back at {$when}.";
    }

    /** Both coordinates are needed before a delivery distance can be quoted. */
    public function hasPin(): bool
    {
        return $this->lat !== null && $this->lng !== null;
    }

    public function organization()
    {
        return $this->belongsTo(Organization::class);
    }

    /**
     * Terminals that paired with this shop before staff sign-in replaced
     * pairing. History: nothing creates a Device any more.
     */
    public function devices()
    {
        return $this->hasMany(Device::class);
    }

    public function overrides()
    {
        return $this->hasMany(ProductStoreOverride::class);
    }

    public function inventoryLevels()
    {
        return $this->hasMany(InventoryLevel::class);
    }
}
