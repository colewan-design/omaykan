<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

/**
 * A business that asked to join, from the founding-seller campaign page.
 * See the seller_applications migration for why this is not an account.
 */
class SellerApplication extends Model
{
    use HasUuids;

    /** How many businesses the founding-seller campaign is open to. */
    public const FOUNDING_LIMIT = 30;

    public const STATUS_PENDING = 'pending';

    public const STATUS_ACCEPTED = 'accepted';

    public const STATUS_DECLINED = 'declined';

    protected $fillable = [
        'business_name',
        'owner_name',
        'business_category',
        'mobile',
        'email',
        'social_url',
        'address',
        'products_description',
        'offers_delivery',
        'wants_founding',
    ];

    protected function casts(): array
    {
        return [
            'offers_delivery' => 'boolean',
            'wants_founding' => 'boolean',
            'founding_number' => 'integer',
            'reviewed_at' => 'datetime',
        ];
    }

    /**
     * Badges handed out so far.
     *
     * Counts numbers issued rather than accepted rows: those are the same
     * thing today, and if an operator ever accepts a business outside the
     * campaign it is the badge count the page must not overstate.
     */
    public static function foundingClaimed(): int
    {
        return static::query()->whereNotNull('founding_number')->count();
    }
}
