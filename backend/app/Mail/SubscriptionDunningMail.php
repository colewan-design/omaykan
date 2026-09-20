<?php

namespace App\Mail;

use App\Models\Organization;
use App\Models\Subscription;
use App\Models\User;
use App\Services\Billing\SubscriptionBilling;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

/**
 * The three notices a lapsing subscription earns, in one class.
 *
 * One mailable with a stage rather than three classes: they share a recipient,
 * a layout, an amount and a shop name, and differ only in how alarmed they
 * are. Three near-identical files would make it easy to fix a wrong number in
 * one of them and not the other two.
 *
 * Only sent when `billing.dunning` is on — see the command, which is where
 * that is checked. Nothing here decides whether a merchant should be chased;
 * it only writes what we say when they are.
 *
 * See documentation/subscription-and-suspension.md §6.4.
 */
class SubscriptionDunningMail extends OmaykanMailable
{
    public function __construct(
        public User $owner,
        public Organization $organization,
        public Subscription $subscription,
        public string $stage,
    ) {
    }

    public function envelope(): Envelope
    {
        return new Envelope(
            subject: $this->subjectLine(),
            replyTo: $this->defaultReplyTo(),
        );
    }

    /**
     * Not `subject()`: `Mailable` already declares one as a public builder
     * method, and redeclaring it private is a fatal error. Same trap as
     * `defaultReplyTo` in the parent.
     */
    private function subjectLine(): string
    {
        $shop = $this->organization->name;

        return match ($this->stage) {
            SubscriptionBilling::STAGE_RENEWAL => "Your Omaykan subscription renews soon — {$shop}",
            SubscriptionBilling::STAGE_PAST_DUE => "Your Omaykan subscription needs renewing — {$shop}",
            // Says what happens and when, in the subject line, because this is
            // the one a merchant may only ever see the subject of.
            SubscriptionBilling::STAGE_FINAL => "Action needed: {$shop} stops taking orders soon",
            default => "Your Omaykan subscription — {$shop}",
        };
    }

    public function content(): Content
    {
        $billing = app(SubscriptionBilling::class);

        return new Content(
            view: 'mail.subscription-dunning',
            with: [
                'firstName' => explode(' ', trim($this->owner->name))[0],
                'shopName' => $this->organization->name,
                'amount' => number_format(((int) $this->subscription->amount_cents) / 100, 2),
                'periodEndsAt' => $this->subscription->current_period_ends_at,
                'graceEndsAt' => $billing->graceEndsAt($this->subscription),
                'supportEmail' => config('support.email'),
                'stage' => $this->stage,
            ],
        );
    }
}
