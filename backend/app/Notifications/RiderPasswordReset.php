<?php

namespace App\Notifications;

use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

/**
 * The rider's way back into their own account.
 *
 * A near-copy of CustomerPasswordReset, and separate from it for the reason
 * the whole rider stack is separate: the link has to land on the rider portal,
 * not /account. A reset token minted on the `riders` broker cannot be spent by
 * the shopper page, so a shared notification would send half the platform's
 * riders to a form that answers "that reset link has expired".
 *
 * Queued, like every other mail here: nobody waits on an SMTP handshake, and a
 * mailbox that refuses must not turn a valid request into a failed one.
 */
class RiderPasswordReset extends Notification implements ShouldQueue
{
    use Queueable;

    public function __construct(#[\SensitiveParameter] private readonly string $token)
    {
    }

    /** @return array<int, string> */
    public function via(object $notifiable): array
    {
        return ['mail'];
    }

    public function toMail(object $notifiable): MailMessage
    {
        $expiresInMinutes = config('auth.passwords.riders.expire', 60);

        return (new MailMessage)
            ->subject('Reset your Omaykan Rider password')
            ->greeting('Hi '.$notifiable->name.',')
            ->line('Someone asked to reset the password on your Omaykan Rider account.')
            ->action('Choose a new password', $this->resetUrl($notifiable))
            ->line("This link stops working in {$expiresInMinutes} minutes.")
            // Same restraint as the shopper's: an unrequested reset mail is
            // almost always a typo'd address, and nothing has changed until
            // the link is used.
            ->line("If you didn't ask for this, you can ignore this email — nothing has changed.");
    }

    private function resetUrl(object $notifiable): string
    {
        $base = rtrim((string) config('app.rider_portal_url'), '/');

        return $base.'?'.http_build_query([
            'token' => $this->token,
            'email' => $notifiable->email,
        ]);
    }
}
