<?php

namespace App\Events;

use App\Models\Order;
use Illuminate\Broadcasting\Channel;
use Illuminate\Broadcasting\InteractsWithSockets;
use Illuminate\Broadcasting\PrivateChannel;
use Illuminate\Contracts\Broadcasting\ShouldBroadcastNow;
use Illuminate\Foundation\Events\Dispatchable;

/**
 * The rider moved.
 *
 * One event per *order*, not per rider, and that is the whole privacy model in
 * one line. A rider carrying two orders from two shops produces two events on
 * two pairs of channels; neither shop learns about the other's delivery, and
 * the position stops being broadcast anywhere the moment the last order is
 * handed over.
 *
 * `ShouldBroadcastNow`, unlike every other event on this codebase, which all
 * queue. Two reasons, both about the fact that this fires every ten seconds per
 * active delivery rather than a handful of times per order:
 *
 *   - `QUEUE_CONNECTION=database`, so queueing would write a `jobs` row for
 *     every ping of every rider on the platform, then delete it. That is a
 *     table churning at the rate of the whole delivery fleet to move a pair of
 *     floats.
 *   - A position is only interesting while it is current. A ping that waits
 *     behind a slow job is worse than one that is dropped, because the map
 *     draws it as where the rider is now.
 *
 * Deliberately *not* SerializesModels — the payload is six scalars assembled by
 * the caller. There is nothing here worth a re-query on the other side.
 */
class RiderPositionUpdated implements ShouldBroadcastNow
{
    use Dispatchable, InteractsWithSockets;

    /**
     * @param  array<string, mixed>  $position  As built by Rider::positionArray().
     */
    public function __construct(
        public string $orderId,
        public string $storeId,
        public string $deliveryStage,
        public array $position,
    ) {
    }

    public static function forOrder(Order $order, array $position): self
    {
        return new self(
            orderId: $order->id,
            storeId: (string) $order->store_id,
            deliveryStage: (string) $order->delivery_stage,
            position: $position,
        );
    }

    /**
     * The same two channels OrderDeliveryUpdated rides: the shop's private one
     * so the dashboard and the seller app follow along, and the per-order
     * public one the customer's tracking page is already subscribed to. No new
     * channel and no new authorization rule — the position is scoped by which
     * order it is attached to, and that scoping already exists.
     *
     * @return array<int, Channel|PrivateChannel>
     */
    public function broadcastOn(): array
    {
        return [
            new PrivateChannel('store.'.$this->storeId),
            new Channel('order.'.$this->orderId),
        ];
    }

    public function broadcastAs(): string
    {
        return 'rider.position';
    }

    /**
     * @return array<string, mixed>
     */
    public function broadcastWith(): array
    {
        return [
            'orderId' => $this->orderId,
            'deliveryStage' => $this->deliveryStage,
        ] + $this->position;
    }
}
