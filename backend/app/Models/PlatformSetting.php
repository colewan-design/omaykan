<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

/**
 * The marketplace's own record — see the `platform_settings` migration for why
 * it exists and why there is only ever one row of it.
 *
 * Read it through `current()`, never `find()`: callers want "the marketplace",
 * and none of them has an id to hand.
 */
class PlatformSetting extends Model
{
    use HasUuids;

    protected $fillable = [
        'name',
        'tagline',
        'description',
        'website',
        'address',
        'contact_email',
        'contact_phone',
        'delivery',
        'notifications',
        'plan',
    ];

    protected function casts(): array
    {
        return [
            'delivery' => 'array',
            'notifications' => 'array',
            'plan' => 'array',
        ];
    }

    /**
     * The one row.
     *
     * `firstOrCreate` rather than `firstOrFail` so a database that predates the
     * migration's insert — a test that truncated it, a restored dump taken
     * before this shipped — still answers rather than 500s. The defaults here
     * are the same ones the migration writes; they are duplicated deliberately,
     * because a migration is not re-run and this is the only other place that
     * can put the row back.
     */
    public static function current(): self
    {
        return static::query()->firstOrCreate(
            ['singleton' => 1],
            [
                'name' => 'Omaykan',
                'tagline' => 'Your neighbourhood market, online.',
                'delivery' => self::DEFAULT_DELIVERY,
                'notifications' => self::DEFAULT_NOTIFICATIONS,
            ],
        );
    }

    public const DEFAULT_DELIVERY = [
        'baseFeeCents' => 4900,
        'freeDeliveryOverCents' => 0,
        'maxDistanceKm' => 12,
    ];

    public const DEFAULT_NOTIFICATIONS = [
        'newOrder' => true,
        'newSeller' => true,
        'lowStock' => false,
        'weeklySummary' => false,
    ];

    /**
     * The stored blob merged over the defaults, so a key added to the defaults
     * after a row was last saved reads as its default instead of null.
     */
    public function deliverySettings(): array
    {
        return array_merge(self::DEFAULT_DELIVERY, $this->delivery ?? []);
    }

    public function notificationSettings(): array
    {
        return array_merge(self::DEFAULT_NOTIFICATIONS, $this->notifications ?? []);
    }

    /**
     * What a merchant subscription costs — what `SignupController` used to
     * hard-code as PLAN_ID and PLAN_AMOUNT_CENTS.
     *
     * Still a placeholder: there is no billing cycle and nothing charges it.
     * It is here so that when there is a real price, setting it is a save on
     * the operator's settings screen rather than a deploy. See
     * documentation/subscription-and-suspension.md §6.2.
     */
    public const DEFAULT_PLAN = [
        'id' => 'standard-monthly',
        'amountCents' => 49900,
    ];

    public function planSettings(): array
    {
        return array_merge(self::DEFAULT_PLAN, $this->plan ?? []);
    }
}
