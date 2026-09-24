<?php

namespace App\Mail;

use App\Models\SellerApplication;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

/**
 * The receipt for a founding-seller application.
 *
 * Deliberately not a welcome. Nothing has been set up yet — the campaign
 * promises "we set up your store", so this only confirms the enquiry arrived
 * and says what happens next. SellerWelcomeMail is the one that goes out when
 * there is actually a store to open.
 *
 * It names no badge number either. `founding_number` is null until an operator
 * accepts the application, and telling somebody they are #7 before anyone has
 * looked is a promise this side of the process cannot keep.
 */
class SellerApplicationReceivedMail extends OmaykanMailable
{
    public function __construct(public SellerApplication $application)
    {
    }

    public function envelope(): Envelope
    {
        return new Envelope(
            subject: 'We got your Omaykan application',
            replyTo: $this->defaultReplyTo(),
        );
    }

    public function content(): Content
    {
        return new Content(
            view: 'mail.seller-application-received',
            with: [
                // The owner gave a full name; the greeting wants one word.
                'firstName' => explode(' ', trim($this->application->owner_name))[0] ?: 'there',
            ],
        );
    }
}
