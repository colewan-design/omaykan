<?php

namespace App\Mail;

use App\Models\Organization;
use App\Models\Store;
use App\Models\User;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

class PlatformAdminReplyMail extends OmaykanMailable
{
    public function __construct(
        public User $recipient,
        public Organization $organization,
        public ?Store $store,
        public string $subjectLine,
        public string $messageBody,
    ) {
    }

    public function envelope(): Envelope
    {
        return new Envelope(
            subject: $this->subjectLine,
            replyTo: $this->defaultReplyTo(),
        );
    }

    public function content(): Content
    {
        return new Content(
            view: 'mail.platform-admin-reply',
            with: [
                'recipient' => $this->recipient,
                'organization' => $this->organization,
                'store' => $this->store,
                'messageBody' => $this->messageBody,
            ],
        );
    }
}
