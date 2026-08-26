<?php

namespace App\Notifications;

use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;
use Illuminate\Support\Facades\URL;

class CustomerEmailVerification extends Notification implements ShouldQueue
{
    use Queueable;

    /** @return array<int, string> */
    public function via(object $notifiable): array
    {
        return ['mail'];
    }

    public function toMail(object $notifiable): MailMessage
    {
        return (new MailMessage)
            ->from($this->supportAddress(), $this->supportName())
            ->subject('Verify your Omaykan email')
            ->greeting('Hi '.$notifiable->name.',')
            ->line('Please verify this email address before signing in to your Omaykan customer account.')
            ->action('Verify email', $this->verificationUrl($notifiable))
            ->line('This link expires in 60 minutes.')
            ->line("If you didn't create this account, you can ignore this email.");
    }

    private function verificationUrl(object $notifiable): string
    {
        return URL::temporarySignedRoute(
            'verification.customer.verify',
            now()->addMinutes((int) config('auth.verification.expire', 60)),
            [
                'id' => $notifiable->getKey(),
                'hash' => sha1($notifiable->getEmailForVerification()),
            ],
        );
    }

    private function supportAddress(): string
    {
        return (string) (config('mail.reply_to.address') ?: 'support@omaykan.com');
    }

    private function supportName(): string
    {
        return (string) (config('mail.reply_to.name') ?: config('app.name', 'Omaykan'));
    }
}
