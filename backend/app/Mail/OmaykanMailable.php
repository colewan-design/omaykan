<?php

namespace App\Mail;

use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Address;
use Illuminate\Queue\SerializesModels;

/**
 * The shape every Omaykan message shares.
 *
 * Queued, always. A merchant signing up or a customer checking out must not
 * wait on an SMTP handshake, and a mailbox that is refusing connections must
 * never turn a recorded order into a failed request.
 *
 * That queueing is also why callers dispatch these from DB::afterCommit rather
 * than inline: QUEUE_CONNECTION is `database`, so a job pushed while the
 * surrounding transaction is still open can be picked up by a worker before
 * the rows it describes are visible — and would be sent for a signup that
 * ended up rolled back. Same reasoning as the OrderPlaced broadcast.
 *
 * Reply-To is applied here rather than per-message so that every mail replies
 * to the address the product actually publishes, whichever mailbox it happened
 * to be sent from. See config/mail.php.
 */
abstract class OmaykanMailable extends Mailable implements ShouldQueue
{
    use Queueable, SerializesModels;

    /**
     * Note the name: Mailable::replyTo() already exists as a builder method,
     * and redeclaring it with a different signature is a fatal error.
     *
     * @return array<int, Address>
     */
    protected function defaultReplyTo(): array
    {
        $address = config('mail.reply_to.address');

        if (! is_string($address) || trim($address) === '') {
            return [];
        }

        return [new Address($address, config('mail.reply_to.name'))];
    }
}
