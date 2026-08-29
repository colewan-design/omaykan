<?php

namespace App\Events;

use App\Models\Order;
use Illuminate\Broadcasting\Channel;
use Illuminate\Broadcasting\InteractsWithSockets;
use Illuminate\Broadcasting\PrivateChannel;
use Illuminate\Contracts\Broadcasting\ShouldBroadcast;
use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * A delivery order got a rider, or moved along the road.
 *
 * Separate from OrderStatusChanged because the two answer different questions:
 * that one is how far the shop has got with making the order, this one is where
 * the order is once it has left. Both matter to the customer at once — food can
 * be ready while no rider has taken it yet.
 *
 * Broadcast the same two ways OrderStatusChanged is: the store's private
 * channel, so every register and back-office screen stays in step, and the
 * per-order public channel the customer's tracking page is watching.
 */
class OrderDeliveryUpdated implements ShouldBroadcast
{
    use Dispatchable, InteractsWithSockets, SerializesModels;

    public function __construct(
        public Order $order,
        public ?string $previousStage = null,
    ) {
    }

    /**
     * @return array<int, Channel|PrivateChannel>
     */
    public function broadcastOn(): array
    {
        return [
            new PrivateChannel('store.'.$this->order->store_id),
            new Channel('order.'.$this->order->id),
        ];
    }

    public function broadcastAs(): string
    {
        return 'order.delivery-updated';
    }

    /**
     * The rider's name and number go to the customer on purpose — they are
     * meeting this person at the door, and the storefront's tracking view
     * already has fields for both. Nothing else about the rider is sent.
     *
     * The number is gated by Order::riderPhoneForCustomer(): live while the
     * delivery is, withheld once it is done. This payload rides the *public*
     * `order.{uuid}` channel as well as the store's private one, and that UUID
     * is the forwardable tracking link — so a number left in here after the
     * handover is a rider's mobile readable by anyone who ever held the link.
     * The same gate is on the tracking endpoint, because either one alone
     * would just be theatre while the other still served it.
     *
     * The store channel is gated identically rather than being split into its
     * own event: the merchant needs to phone the rider *during* a delivery,
     * which this still allows, and their own register holds the rider details
     * they entered anyway.
     *
     * @return array<string, mixed>
     */
    public function broadcastWith(): array
    {
        return [
            'id' => $this->order->id,
            'ticketNumber' => $this->order->ticket_number,
            'deliveryStage' => $this->order->delivery_stage,
            'previousStage' => $this->previousStage,
            'riderName' => $this->order->rider_name,
            'riderPhone' => $this->order->riderPhoneForCustomer(),
            'updatedAt' => $this->order->updated_at?->toIso8601String(),
        ];
    }
}
