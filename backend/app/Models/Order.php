<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Order extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    protected $fillable = [
        'id',
        'organization_id',
        'store_id',
        'device_id',
        'user_id',
        'customer_account_id',
        'ticket_number',
        'order_status',
        'order_type',
        'payment_status',
        'subtotal_cents',
        'tax_cents',
        'total_cents',
        'business_date',
        'completed_at',
        'channel',
        'business_mode',
        'table_number',
        'payment_method',
        'fulfillment_method',
        'delivery_address',
        'delivery_lat',
        'delivery_lng',
        'delivery_distance_km',
        'delivery_fee_cents',
        'delivery_stage',
        'rider_name',
        'rider_phone',
        'rider_id',
        'rider_accepted_at',
        'payment_confirmed_at',
        'payment_confirmed_by_user_id',
        'guest_contact',
    ];

    protected function casts(): array
    {
        return [
            'business_date' => 'date',
            'completed_at' => 'datetime',
            'payment_confirmed_at' => 'datetime',
            'rider_accepted_at' => 'datetime',
            'guest_contact' => 'array',
            'delivery_lat' => 'float',
            'delivery_lng' => 'float',
            'delivery_distance_km' => 'float',
        ];
    }

    /** Placed by a customer on the storefront rather than rung up on a till. */
    public function isOnline(): bool
    {
        return $this->channel === 'online';
    }

    public function scopeOnline($query)
    {
        return $query->where('channel', 'online');
    }

    public function items()
    {
        return $this->hasMany(OrderItem::class);
    }

    public function payments()
    {
        return $this->hasMany(Payment::class);
    }

    public function device()
    {
        return $this->belongsTo(Device::class);
    }

    public function store()
    {
        return $this->belongsTo(Store::class);
    }

    /**
     * Set when a rider claims the order in the rider portal. Null on an order
     * whose rider was typed in at the till — `rider_name` carries those, and
     * always has.
     */
    public function rider()
    {
        return $this->belongsTo(Rider::class);
    }

    /** Null for guest checkout, which is still the default way to order. */
    public function customerAccount()
    {
        return $this->belongsTo(CustomerAccount::class);
    }

    /**
     * The delivery stages during which a rider is physically holding the order
     * and the customer is about to meet them.
     *
     * `pending` never has rider contact to begin with — releasing a delivery
     * nulls both columns (RiderDeliveryController::release) — so this is really
     * about everything after `delivered`.
     */
    private const RIDER_CONTACTABLE_STAGES = ['assigned', 'picked_up'];

    /**
     * The rider's phone number, but only while the delivery is in flight.
     *
     * The number exists so the customer can reach the person walking up to
     * their door. That need ends when the order is handed over; the exposure
     * does not, because the tracking view is public by UUID and that link is
     * *meant* to be forwarded — to a flatmate, to whoever is actually home. A
     * number that stays readable forever afterwards is a real person's mobile
     * handed to everyone who ever saw the link, and the rider never agreed to
     * that.
     *
     * So it is served during `assigned` and `picked_up` and withheld after.
     * The name is left alone: far weaker on its own, and the customer's order
     * history reasonably says who brought it.
     */
    public function riderPhoneForCustomer(): ?string
    {
        return in_array($this->delivery_stage, self::RIDER_CONTACTABLE_STAGES, true)
            ? $this->rider_phone
            : null;
    }

    /**
     * Where the rider is, but only while they are carrying this order.
     *
     * The same gate as the phone number and for a stronger version of the same
     * reason. A phone number leaked after the handover is a nuisance; a live
     * position leaked after the handover is a person's movements for the rest
     * of their shift, readable by anyone who was ever forwarded this tracking
     * link. So the disclosure is bounded twice over — by the stage, and by the
     * fact that a rider only ever reports at all while an order is open.
     *
     * A rider typed in at the till has no `rider_id` and therefore no position.
     * The map falls back to shop and door, which is all it ever had for them.
     *
     * @return array<string, mixed>|null
     */
    public function riderPositionForCustomer(): ?array
    {
        if (! in_array($this->delivery_stage, self::RIDER_CONTACTABLE_STAGES, true)) {
            return null;
        }

        $rider = $this->relationLoaded('rider') ? $this->getRelation('rider') : $this->rider;

        return $rider?->positionArray();
    }

    /**
     * The two fixed ends of the trip: the shop, and the door.
     *
     * Both go to the customer. The shop's address and coordinates are already
     * public on its storefront, and the destination is the customer's own
     * address — neither is a disclosure, and without them a live map is a dot
     * moving across an empty city.
     *
     * @return array<string, mixed>
     */
    public function routeEndpointsArray(): array
    {
        $store = $this->relationLoaded('store') ? $this->getRelation('store') : $this->store;

        return [
            'pickup' => [
                'name' => $store?->name,
                'address' => $store?->address,
                'lat' => $store?->lat !== null ? (float) $store->lat : null,
                'lng' => $store?->lng !== null ? (float) $store->lng : null,
            ],
            'dropoff' => [
                'address' => $this->delivery_address,
                'lat' => $this->delivery_lat !== null ? (float) $this->delivery_lat : null,
                'lng' => $this->delivery_lng !== null ? (float) $this->delivery_lng : null,
            ],
        ];
    }

    /**
     * The customer's own view of an order: enough to track it, nothing that
     * would matter if the link were forwarded.
     *
     * Lives on the model because two endpoints return it — the public
     * order-tracking page, keyed on the unguessable UUID, and the signed-in
     * customer's order list. Two copies of this would drift, and the one that
     * drifted would be leaking something.
     *
     * Notably absent: guest_contact. The customer already has their own phone
     * number, and nobody else should get it from here.
     *
     * @return array<string, mixed>
     */
    public function toTrackedArray(): array
    {
        // The rider is loaded for the position, the store for the pickup pin.
        // Both are needed by the map on the tracking page and neither is worth
        // a second round trip to fetch.
        $this->loadMissing('items', 'rider', 'store');

        return [
            'orderId' => $this->id,
            'ticketNumber' => $this->ticket_number,
            'status' => $this->order_status,
            'paymentStatus' => $this->payment_status,
            'paymentMethod' => $this->payment_method,
            'subtotalCents' => $this->subtotal_cents,
            'taxCents' => $this->tax_cents,
            'deliveryFeeCents' => $this->delivery_fee_cents,
            'totalCents' => $this->total_cents,
            'fulfillmentMethod' => $this->fulfillment_method,
            'deliveryAddress' => $this->delivery_address,
            'deliveryStage' => $this->delivery_stage,
            'riderName' => $this->rider_name,
            'riderPhone' => $this->riderPhoneForCustomer(),
            'riderPosition' => $this->riderPositionForCustomer(),
            'route' => $this->routeEndpointsArray(),
            'placedAt' => $this->created_at?->toIso8601String(),
            'items' => $this->items->map(fn ($item) => [
                'productId' => $item->product_id ?? '',
                'name' => $item->product_name,
                'quantity' => (float) $item->quantity,
                'unitPriceCents' => $item->unit_price_cents,
                'lineTotalCents' => $item->line_total_cents,
            ])->values(),
        ];
    }
}
