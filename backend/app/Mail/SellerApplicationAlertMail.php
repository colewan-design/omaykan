<?php

namespace App\Mail;

use App\Models\SellerApplication;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

/**
 * The inward-facing half of an application: tells the operators a business is
 * waiting to be set up, with everything they need to act on it in the body so
 * nobody has to open the admin to decide whether it is worth a call.
 *
 * Goes to config('mail.alerts_to'), never to the applicant. Reply-To is the
 * applicant's own address, the same call SellerSignupAlertMail makes — this
 * campaign is answered by a person ringing a shop back, and replying to the
 * alert should reach them.
 */
class SellerApplicationAlertMail extends OmaykanMailable
{
    public function __construct(
        public SellerApplication $application,
        /** Badges issued so far, so the alert says how much of the 30 is gone. */
        public int $claimed,
    ) {
    }

    public function envelope(): Envelope
    {
        return new Envelope(
            subject: 'Seller application: '.$this->application->business_name,
            replyTo: array_filter([$this->application->email]),
        );
    }

    public function content(): Content
    {
        return new Content(
            view: 'mail.seller-application-alert',
            with: [
                'limit' => SellerApplication::FOUNDING_LIMIT,
                'remaining' => max(SellerApplication::FOUNDING_LIMIT - $this->claimed, 0),
            ],
        );
    }
}
