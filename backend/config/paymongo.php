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
     * What a merchant may pay with. QRPh only, by default.
     *
     * The PayMongo account behind this install is an individual one, not a
     * registered business, and QRPh is the method that account type is meant
     * to take. It also covers everyone anyway: a QRPh code scans from GCash,
     * Maya and practically every Philippine bank app, so a merchant who pays
     * with GCash today still pays with GCash — by scanning, not by being
     * redirected. And it is the cheapest method to receive, which matters
     * because the fee comes out of the subscription revenue.
     *
     * A method the account has not enabled is accepted when the session is
     * created and then simply does not appear on the hosted page, which is a
     * confusing way to find out. So widen this only against a checkout you
     * have actually opened — `gcash,paymaya,grab_pay,card,dob` all render on
     * a business account. `billease` and `atome` are buy-now-pay-later and
     * should stay out: instalment debt for a ₱499 subscription is not a thing
     * to offer a shop owner.
     */
    'payment_methods' => array_values(array_filter(array_map(
        'trim',
        explode(',', (string) env('PAYMONGO_PAYMENT_METHODS', 'qrph')),
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
