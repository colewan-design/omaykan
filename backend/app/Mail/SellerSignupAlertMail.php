<?php

namespace App\Mail;

use App\Models\Organization;
use App\Models\Store;
use App\Models\User;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

/**
 * The inward-facing half of a signup: tells the operators that a merchant just
 * created a store, so somebody knows to look at the pending subscription.
 *
 * Goes to config('mail.alerts_to'), never to the merchant. Reply-To is set to
 * the merchant's own address so that answering the alert reaches them directly
 * — the one place the shared reply-to would be actively unhelpful.
 */
class SellerSignupAlertMail extends OmaykanMailable
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
            subject: 'New Omaykan signup: '.$this->store->name,
            replyTo: array_filter([$this->owner->email]),
        );
    }

    public function content(): Content
    {
        return new Content(view: 'mail.seller-signup-alert');
    }
}
