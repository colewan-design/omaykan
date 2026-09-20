<?php

/*
|--------------------------------------------------------------------------
| PayMongo
|--------------------------------------------------------------------------
|
| Credentials only, and the switch that follows from them: with a secret key
| the merchant gets a Pay button, without one the install behaves exactly as
| it did before the gateway existed.
|
| Taking a payment is settled (documentation/plan.md §4a, 2026-09-20).
| Cutting somebody off for not making one is a different decision and has not
| been made — that stays behind BILLING_ENFORCE in config/billing.php.
|
| Both values are secrets and belong in the server's untracked .env, never in
| .env.example with a value and never in a client bundle. PayMongo's own
| dashboard states the rule: secret keys are server-side only, never in a
| browser or an app — which includes :app, :seller and :rider.
|
*/

return [
    /*
     * The API secret key — `sk_test_…` while building, `sk_live_…` once real
     * money is in play. Used as the username of HTTP Basic auth against
     * api.paymongo.com, with an empty password.
     */
    'secret_key' => env('PAYMONGO_SECRET_KEY'),

    /*
     * What a merchant may pay with.
     *
     * Verified against the account rather than guessed: every one of these
     * renders on the hosted checkout — QRPh, cards, the three e-wallets, and
     * BPI under online banking. A method the account has not enabled is
     * accepted when the session is created and then simply does not appear,
     * which is a confusing way to find out, so change this list only against
     * a checkout you have actually opened.
     *
     * `billease` and `atome` are deliberately absent. They are buy-now-pay-
     * later, and putting a shop owner into instalment debt for a ₱499
     * subscription is not a thing to offer by default. Both work if you add
     * them — that is what the environment variable is for.
     *
     * Fees differ per method and come out of the subscription revenue: cards
     * cost more than e-wallets, which cost more than QRPh. Worth a look at
     * PayMongo's pricing before widening this.
     */
    'payment_methods' => array_values(array_filter(array_map(
        'trim',
        explode(',', (string) env('PAYMONGO_PAYMENT_METHODS', 'gcash,paymaya,grab_pay,card,qrph,dob')),
    ))),

    /*
     * The webhook endpoint's own secret, which is a different thing from the
     * key above and does not appear on the dashboard's API keys page. It is
     * shown once, when the endpoint is created under Developers → Webhooks,
     * and it is per-endpoint: test and live have different ones.
     *
     * Without it the webhook cannot be trusted, and the webhook is the only
     * thing that ever says a payment happened — so an unverified endpoint is
     * a URL that lets anyone mark any subscription paid.
     */
    'webhook_secret' => env('PAYMONGO_WEBHOOK_SECRET'),

    /*
     * Which half of the signature header to trust, derived from the key in
     * use rather than set by hand so the two cannot disagree.
     *
     * The header carries a test signature and a live signature side by side.
     * Comparing against whichever one happens to match would mean a test-mode
     * event — which anyone with a test key can produce — being accepted as a
     * live payment. So the mode is pinned, and only its half is read.
     */
    'mode' => str_starts_with((string) env('PAYMONGO_SECRET_KEY'), 'sk_live_') ? 'live' : 'test',

    /*
     * How old a signed request may be, in seconds.
     *
     * Ours, not PayMongo's: their documentation gives no tolerance, and
     * without one a captured request stays replayable forever. Five minutes
     * is enough for a retry after a slow response and short enough that a
     * stolen body is stale by the time it is useful.
     */
    'signature_tolerance' => (int) env('PAYMONGO_SIGNATURE_TOLERANCE', 300),
];
