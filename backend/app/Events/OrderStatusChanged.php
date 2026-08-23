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
 * An order moved status — preparing, ready, completed, cancelled.
 *
 * Broadcast twice over: to the store's private channel so every register and
 * back-office screen stays in step, and to a per-order channel the customer is
 * watching on the storefront.
 */
class OrderStatusChanged implements ShouldBroadcast
{
    use Dispatchable, InteractsWithSockets, SerializesModels;

    public function __construct(
        public Order $order,
        public ?string $previousStatus = null,
    ) {
    }

    /**
     * The customer-facing channel is public but keyed on the order's UUID: a
     * storefront customer has no account to authenticate with, so the
     * unguessable id is the capability. Nothing sensitive goes over it — see
     * broadcastWith(). If customer accounts ever land, make this private.
     *
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
        return 'order.status-changed';
    }

    /**
     * @return array<string, mixed>
     */
    public function broadcastWith(): array
    {
        return [
            'id' => $this->order->id,
            'ticketNumber' => $this->order->ticket_number,
            'orderStatus' => $this->order->order_status,
            'previousStatus' => $this->previousStatus,
            'updatedAt' => $this->order->updated_at?->toIso8601String(),
        ];
    }
}
