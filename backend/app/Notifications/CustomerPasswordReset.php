<?php

namespace App\Notifications;

use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

/**
 * The one mail a shopper gets that they did not ask a person for.
 *
 * The framework's built-in ResetPassword notification links at a named `web`
 * route that this application does not have — there is no Blade password page,
 * only the Vue portal. So this builds the link itself, pointing at the portal
 * with the token and the address it was issued for.
 *
 * Queued for the same reason every OmaykanMailable is: nobody should wait on
 * an SMTP handshake, and a refusing mailbox must not turn a valid request into
 * a failed one. It renders through the framework's notification template
 * rather than a Blade view of ours, so it has no view file to go missing.
 */
class CustomerPasswordReset extends Notification implements ShouldQueue
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
        $expiresInMinutes = config('auth.passwords.customers.expire', 60);

        return (new MailMessage)
            ->subject('Reset your Omaykan password')
            ->greeting('Hi '.$notifiable->name.',')
            ->line('Someone asked to reset the password on your Omaykan account.')
            ->action('Choose a new password', $this->resetUrl($notifiable))
            ->line("This link stops working in {$expiresInMinutes} minutes.")
            // No "your account may be compromised" alarm: an unrequested reset
            // mail almost always means a typo'd address, and the account is
            // untouched until the link is used.
            ->line("If you didn't ask for this, you can ignore this email — nothing has changed.");
    }

    private function resetUrl(object $notifiable): string
    {
        $base = rtrim((string) config('app.customer_account_url'), '/');

        return $base.'?'.http_build_query([
            'token' => $this->token,
            'email' => $notifiable->email,
        ]);
    }
}
