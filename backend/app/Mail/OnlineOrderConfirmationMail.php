<?php

namespace App\Mail;

use App\Models\Order;
use App\Models\Store;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

/**
 * The receipt a storefront customer gets for an order they just placed.
 *
 * Only sent when they gave an email — checkout accepts a phone number instead
 * (OnlineOrderController requires one or the other), and a phone-only order
 * simply has nowhere to send this.
 *
 * The tracking link carries the order's UUID, which is the same capability the
 * public tracking endpoint is built on: unguessable, and safe in the hands of
 * the person who placed the order. Nothing else in here is a secret.
 */
class OnlineOrderConfirmationMail extends OmaykanMailable
{
    public function __construct(
        public Order $order,
        public Store $store,
    ) {
    }

    public function envelope(): Envelope
    {
        return new Envelope(
            subject: 'Order '.$this->order->ticket_number.' confirmed — '.$this->store->name,
            replyTo: $this->defaultReplyTo(),
        );
    }

    public function content(): Content
    {
        $this->order->loadMissing('items');

        return new Content(
            view: 'mail.order-confirmation',
            with: [
                'customerName' => $this->order->guest_contact['name'] ?? 'there',
                'trackUrl' => $this->trackUrl(),
                'isDelivery' => $this->order->fulfillment_method === 'delivery',
                'lines' => $this->order->items->map(fn ($item) => [
                    'name' => $item->product_name,
                    // Quantities are decimal so a grocery can sell 1.5 kg, but
                    // most orders are whole units and "2" reads better than
                    // "2.00". Trim only when there is nothing to lose.
                    'quantity' => rtrim(rtrim(number_format((float) $item->quantity, 2), '0'), '.'),
                    'lineTotal' => $this->peso($item->line_total_cents),
                ])->all(),
                'subtotal' => $this->peso($this->order->subtotal_cents),
                'discount' => (int) $this->order->discount_cents > 0
                    ? '−'.$this->peso($this->order->discount_cents)
                    : null,
                'discountLabel' => $this->order->discountLabel(),
                'tax' => $this->peso($this->order->tax_cents),
                'deliveryFee' => $this->peso($this->order->delivery_fee_cents),
                'total' => $this->peso($this->order->total_cents),
            ],
        );
    }

    /**
     * Where the storefront renders this order's status. Mirrors the SPA route
     * in apps/mobile/src/storefront/router.ts.
     */
    private function trackUrl(): string
    {
        return rtrim((string) config('app.storefront_url'), '/').'/order/'.$this->order->id;
    }

    /** Matches formatCurrency() in packages/shared, which every screen uses. */
    private function peso(int $cents): string
    {
        return '₱'.number_format($cents / 100, 2);
    }
}
