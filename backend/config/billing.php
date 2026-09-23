<?php

/*
|--------------------------------------------------------------------------
| Whether a subscription is allowed to stop anybody
|--------------------------------------------------------------------------
|
| Off by default, and that is the whole point of the file.
|
| Every organization in the database carries a `pending_verification`
| subscription, because that is what SignupController writes and only an
| operator clicking Verify has ever moved it. Meanwhile the landing page, the
| merchant pitch, the signup form and the till's own sign-in screen all promise
| that early access is free. Enabling enforcement without the backfill would
| therefore lock every existing merchant out of their own till on deploy,
| having told them for months it was free.
|
| So the mechanism ships inert. Suspension — an operator's deliberate act —
| bites from the moment it lands. Payment does not bite until there is a real
| price, BIR clearance, and merchants who have been told.
|
| A config flag rather than a row in a table somebody can toggle from a web UI:
| there is one platform, the decision is made once, and a toggle is a way to
| switch every tenant off by accident.
|
| See documentation/subscription-and-suspension.md §3.2.
|
*/

return [
    'enforce' => (bool) env('BILLING_ENFORCE', false),

    /*
     * How long a subscription stays usable after its paid period ends, before
     * the verdict turns to Unpaid.
     *
     * Days, not hours. A lapsed payment is usually a card that expired, not a
     * merchant deciding to leave, and the cost of being wrong is somebody's
     * till refusing to ring up a queue of customers.
     */
    'grace_days' => (int) env('BILLING_GRACE_DAYS', 7),

    /*
     * Early access, as a length of time.
     *
     * What a new signup's `trial_ends_at` is set to, and what
     * `billing:backfill-trials` grants an existing organization when it is not
     * given an explicit date. Long enough that nobody is surprised, and a date
     * rather than "forever" so the question comes back around instead of
     * being lost.
     *
     * Every surface that promises "free during early access" is promising this
     * number. Shortening it is a decision to start charging people, and wants
     * the notice that the signup form promised them.
     */
    'trial_days' => (int) env('BILLING_TRIAL_DAYS', 365),

    /*
     * How many days before a paid period ends the merchant is told it is
     * about to renew.
     *
     * The first of the three notices in §6.4, and the only one that arrives
     * while everything is still fine. It exists so that the *second* notice is
     * never the first a merchant has heard of it.
     */
    'renewal_notice_days' => (int) env('BILLING_RENEWAL_NOTICE_DAYS', 3),

    /*
     * How many days before the grace window elapses the final notice goes out.
     *
     * Must be less than `grace_days`, or the final warning arrives at the same
     * time as the past-due notice and the merchant gets two emails in one
     * morning saying different things. SubscriptionBilling clamps it rather
     * than trusting the pair to stay sane across two environment variables.
     */
    'final_notice_days' => (int) env('BILLING_FINAL_NOTICE_DAYS', 2),

    /*
     * Whether the scheduled command is allowed to send dunning mail at all.
     *
     * Separate from `enforce`, and off by default for the same reason it is:
     * every merchant on the platform was told early access is free, and a
     * "your subscription is past due" email to somebody who was never asked
     * for money is worse than useless — it is a support ticket and a reason to
     * distrust the next one.
     *
     * The status machine runs regardless. Moving a subscription to `past_due`
     * costs a merchant nothing while `enforce` is false, and it means the
     * dates are already right on the day enforcement is switched on.
     *
     * See documentation/subscription-and-suspension.md §6.4.
     */
    'dunning' => (bool) env('BILLING_DUNNING', false),
];
