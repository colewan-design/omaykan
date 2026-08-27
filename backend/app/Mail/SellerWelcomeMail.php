<?php

namespace App\Mail;

use App\Models\Organization;
use App\Models\Store;
use App\Models\User;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

/**
 * Sent to a merchant the moment their store exists, from the info@ mailbox.
 *
 * Deliberately does not carry the store's code. At signup that value is both
 * the code customers type to find the shop *and* the secret a till pairs with
 * (Store::setPairingCode writes one to public_store_code and hashes the other),
 * and email is a durable, forwardable, frequently-breached channel. The owner
 * has already been shown it on the confirmation step, and it stays readable in
 * Settings > Online Store, so the mail points there instead.
 */
class SellerWelcomeMail extends OmaykanMailable
{
    public function __construct(
        public User $owner,
        public Organization $organization,
        public Store $store,
    ) {
    }

    public function envelope(): Envelope
    {
        return new Envelope(
            subject: 'Your Omaykan store is ready, '.$this->store->name,
            replyTo: $this->defaultReplyTo(),
        );
    }

    public function content(): Content
    {
        return new Content(
            view: 'mail.seller-welcome',
            with: [
                'firstName' => explode(' ', trim($this->owner->name))[0],
                'businessTypeLabel' => $this->store->business_type_label
                    ?: (self::BUSINESS_MODE_LABELS[$this->store->business_mode] ?? $this->store->business_mode),
                'appUrl' => rtrim((string) config('app.url'), '/').'/app',
            ],
        );
    }

    /**
     * Mirrors businessModeLabel() in packages/shared — the merchant should see
     * their business type written the same way the app writes it.
     */
    private const BUSINESS_MODE_LABELS = [
        'coffee-shop' => 'Coffee shop',
        'grocery' => 'Grocery store',
        'restaurant' => 'Restaurant',
        'nail-salon' => 'Nail Salon',
    ];
}
