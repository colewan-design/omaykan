<?php

namespace App\Mail;

use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;
use Symfony\Component\Mime\Email;

class SupportInboxReplyMail extends OmaykanMailable
{
    public function __construct(
        public string $subjectLine,
        public string $messageBody,
        public string $inReplyTo = '',
        public string $references = '',
    ) {
    }

    public function envelope(): Envelope
    {
        return new Envelope(
            subject: $this->subjectLine,
            replyTo: $this->defaultReplyTo(),
            using: [
                function (Email $message): void {
                    if ($this->inReplyTo !== '') {
                        $message->getHeaders()->addTextHeader('In-Reply-To', $this->inReplyTo);
                    }

                    if ($this->references !== '') {
                        $message->getHeaders()->addTextHeader('References', $this->references);
                    }
                },
            ],
        );
    }

    public function content(): Content
    {
        return new Content(
            view: 'mail.support-inbox-reply',
            with: [
                'messageBody' => $this->messageBody,
            ],
        );
    }
}
