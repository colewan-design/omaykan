<?php

namespace App\Listeners;

use App\Events\OrderDeliveryUpdated;
use App\Models\OrderPushToken;
use App\Services\PushSender;
use Illuminate\Contracts\Queue\ShouldQueue;

/**
 * "Rider assigned", on the customer's phone, with the app closed.
 *
 * Queued, so the rider's "Take this job" never waits on Google. Fires for a
 * platform rider accepting and for a shop assigning its own rider alike — both
 * dispatch the same event.
 *
 * The event's order is re-read from the database when this runs. If the rider
 * has already moved it along by then, the push is skipped rather than sent
 * late with words that are no longer true.
 */
class NotifyCustomerOfRider implements ShouldQueue
{
    /** A cancelled (soft-deleted) order has nobody left to tell. */
    public bool $deleteWhenMissingModels = true;

    public function __construct(private PushSender $push)
    {
    }

    public function handle(OrderDeliveryUpdated $event): void
    {
        $order = $event->order;

        if ($order->delivery_stage !== 'assigned' || $event->previousStage === 'assigned') {
            return;
        }

        if (! $this->push->configured()) {
            return;
        }

        $tokens = OrderPushToken::query()->where('order_id', $order->id)->get();
        if ($tokens->isEmpty()) {
            return;
        }

        $rider = trim((string) $order->rider_name) ?: 'A rider';
        $store = $order->store?->name ?: 'the shop';
        $ticket = $order->ticket_number ? " for order #{$order->ticket_number}" : '';

        foreach ($tokens as $token) {
            $result = $this->push->send(
                token: $token->token,
                title: 'Rider assigned',
                body: "{$rider} is heading to {$store}{$ticket}.",
                data: ['orderId' => $order->id, 'deliveryStage' => 'assigned'],
                tag: $order->id,
            );

            if ($result === PushSender::INVALID) {
                $token->delete();
            }
        }
    }
}
