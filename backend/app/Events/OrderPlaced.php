<?php

namespace App\Events;

use App\Models\Order;
use Illuminate\Broadcasting\InteractsWithSockets;
use Illuminate\Broadcasting\PrivateChannel;
use Illuminate\Contracts\Broadcasting\ShouldBroadcast;
use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * A new order arrived for a store — typically placed by a customer through the
 * storefront. Drives the register's Track Order strip in place of the Firestore
 * listener that used to push these.
 *
 * ShouldBroadcast (not ShouldBroadcastNow) so the websocket push is handed to
 * the queue: a slow or down Reverb must never block the request that recorded
 * the order.
 */
class OrderPlaced implements ShouldBroadcast
{
    use Dispatchable, InteractsWithSockets, SerializesModels;

    public function __construct(public Order $order)
    {
    }

    /**
     * @return array<int, PrivateChannel>
     */
    public function broadcastOn(): array
    {
        return [
            new PrivateChannel('store.'.$this->order->store_id),
        ];
    }

    public function broadcastAs(): string
    {
        return 'order.placed';
    }

    /**
     * Kept deliberately small — enough for the register to render the strip and
     * decide whether to refetch. The full order is pulled over the API.
     *
     * @return array<string, mixed>
     */
    public function broadcastWith(): array
    {
        return [
            'id' => $this->order->id,
            'ticketNumber' => $this->order->ticket_number,
            'orderStatus' => $this->order->order_status,
            'orderType' => $this->order->order_type,
            'paymentStatus' => $this->order->payment_status,
            'totalCents' => $this->order->total_cents,
            'createdAt' => $this->order->created_at?->toIso8601String(),
        ];
    }
}
