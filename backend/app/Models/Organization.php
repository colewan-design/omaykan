<?php

namespace App\Models;

use App\Services\Billing\TenantAccess;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Organization extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    protected $fillable = [
        'id',
        'name',
        'slug',
        'status',
        'suspended',
    ];

    protected function casts(): array
    {
        return [
            'suspended' => 'boolean',
        ];
    }

    public function subscription()
    {
        return $this->hasOne(Subscription::class);
    }

    /**
     * Whether this organization may trade right now, and if not, why not.
     *
     * The one answer to that question in the codebase — every chokepoint that
     * cares asks here rather than reading `suspended` or the subscription
     * itself, so the operator portal, the till and the storefront cannot
     * disagree about which fact is the reason.
     *
     * Suspension first. It is the precedence SellersView already shows
     * operators ("Suspension outranks the subscription"), and it is the one
     * that is a judgement rather than a fact about money. An organization whose
     * own `status` is anything but active is treated the same way: the shop
     * directory has always hidden those, and this keeps the directory and the
     * verdict saying one thing.
     *
     * Unpaid only when `billing.enforce` is on. Off, the subscription is not
     * consulted at all — that is how the mechanism ships before there is a
     * price to enforce. See config/billing.php.
     *
     * Reads the `subscription` relation, so a caller doing this across many
     * organizations should eager-load it.
     */
    public function accessVerdict(): TenantAccess
    {
        // `?? 'active'`: the column defaults to active in the database, but a
        // model created without it holds null until it is refreshed, and that
        // must not read as a switched-off tenant.
        if ($this->suspended || ($this->status ?? 'active') !== 'active') {
            return TenantAccess::Suspended;
        }

        if (! config('billing.enforce')) {
            return TenantAccess::Allowed;
        }

        $subscription = $this->subscription;

        return $subscription !== null && $subscription->isCurrent()
            ? TenantAccess::Allowed
            : TenantAccess::Unpaid;
    }

    /**
     * Organizations the public can buy from — the SQL half of accessVerdict.
     *
     * Suspension and status only. Whether a subscription is current depends on
     * dates, a grace window and a config flag, which is a PHP question; a list
     * query that needs it filters its results through `accessVerdict()` after
     * loading them, the way StoreDirectoryController does.
     *
     * `orWhereNull` because the column arrived by migration onto rows that
     * already existed.
     */
    public function scopeTradable(Builder $query): Builder
    {
        return $query
            ->where('status', 'active')
            ->where(fn (Builder $inner) => $inner->where('suspended', false)->orWhereNull('suspended'));
    }

    public function memberships()
    {
        return $this->hasMany(OrganizationMembership::class);
    }

    public function stores()
    {
        return $this->hasMany(Store::class);
    }

    public function devices()
    {
        return $this->hasMany(Device::class);
    }

    public function categories()
    {
        return $this->hasMany(Category::class);
    }

    public function products()
    {
        return $this->hasMany(Product::class);
    }
}
