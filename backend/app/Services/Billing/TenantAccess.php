<?php

namespace App\Services\Billing;

/**
 * Whether an organization may trade, and if not, which kind of "not".
 *
 * Three cases rather than a boolean, because the two refusals are different
 * sentences to a merchant — *pay this* and *call us* — and a client that
 * cannot tell them apart shows the wrong one. This is the shape
 * EnsureRiderIsApproved already uses: a 403 carrying a machine-readable status
 * beside the prose, so the portal can show a pending rider a different screen
 * from a suspended one.
 *
 * Built only by Organization::accessVerdict(). See
 * documentation/subscription-and-suspension.md §3.1.
 */
enum TenantAccess: string
{
    /** Trading normally. */
    case Allowed = 'allowed';

    /**
     * An operator switched this tenant off. Nobody acts for it — not the
     * storefront, not staff, not the till. Suspension is a judgement about a
     * tenant rather than a fact about money, and it outranks everything below.
     */
    case Suspended = 'suspended';

    /**
     * No subscription, a rejected one, or one past its grace date.
     *
     * Deliberately softer than Suspended: staff keep read access to their own
     * records and lose the ability to write. A shop that owes us money still
     * owns its sales history, and withholding it is both ugly and, for
     * anything the BIR has an opinion about, probably not ours to withhold.
     */
    case Unpaid = 'unpaid';

    public function allowsWrites(): bool
    {
        return $this === self::Allowed;
    }

    /**
     * Whether staff may reach this tenant at all. False only for Suspended —
     * an unpaid tenant gets in and reads.
     */
    public function allowsStaffAccess(): bool
    {
        return $this !== self::Suspended;
    }

    /**
     * Whether the public may see this shop and order from it.
     *
     * Both refusals close the storefront. The read-only concession in
     * `allowsStaffAccess` is for the merchant's own records; it is not a reason
     * to keep taking a customer's money for a shop that cannot be paid for.
     */
    public function allowsStorefront(): bool
    {
        return $this === self::Allowed;
    }

    /** What the client shows. Null for Allowed — there is nothing to say. */
    public function message(): ?string
    {
        return match ($this) {
            self::Allowed => null,
            self::Suspended => 'This shop has been suspended. Contact '
                .config('support.email').' to sort it out.',
            self::Unpaid => 'This shop\'s subscription is not up to date. Renew it to '
                .'start taking orders again.',
        };
    }
}
