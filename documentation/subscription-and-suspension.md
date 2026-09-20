# Subscription, suspension, and getting paid

Three open items on [todo-checklist.md](./todo-checklist.md) are really one
piece of work seen from three sides:

- **Subscription enforced** — a `Subscription` row exists and nothing reads it.
- **Real subscription price and collection** — ₱499 is a constant in a
  controller, collected by a GCash transfer somebody eyeballs.
- **Organization suspension respected downstream** — the operator portal can
  suspend a tenant and almost nothing downstream notices.

All three are the same missing sentence: *is this organization allowed to trade
right now?* Nothing in the codebase can answer it, so nothing asks.

Written against `main` on 2026-09-19.

---

## Status

**Built 2026-09-19, not yet deployed** — rollout steps 1–6 of §8. Suspension
bites everywhere as soon as this ships. Payment bites nowhere until
`BILLING_ENFORCE=true`, which stays off.

| Piece | Where |
|---|---|
| The verdict | `App\Services\Billing\TenantAccess`, `Organization::accessVerdict()`, `Subscription::isCurrent()` |
| The switch | `config/billing.php` — `enforce`, `grace_days`, `trial_days` |
| Schema | `2026_09_19_000100` (trial / period dates), `2026_09_19_000200` (price on `platform_settings`) |
| Merchant API | `StoreContextResolver` (every request), `StaffAuthController::selectStore` (the door), `ActsForAStore::writableStoreContext` (the nine actions an unpaid shop cannot take) |
| Public side | `StorefrontCatalogController`, `OnlineOrderController::store`, `StoreDirectoryController` via `Organization::scopeTradable` |
| Websocket | `routes/channels.php` |
| Backfill | `php artisan billing:backfill-trials [--until=YYYY-MM-DD] [--dry-run]` |
| Lifecycle | `App\Services\Billing\SubscriptionBilling` — the status machine and the notice sequence (2026-09-20) |
| Collection | `SubscriptionPayment` + `SellerSubscriptionController`; `SubscriptionPanel.vue` (merchant), `SubscriptionPaymentsQueue.vue` (operator); schema `2026_09_20_000200` |
| Dunning | `billing:advance-subscriptions [--dry-run]`, daily at 06:00; `SubscriptionDunningMail` + `mail.subscription-dunning`; schema `2026_09_20_000100` |
| Till | `packages/data` learns and persists the verdict and refuses a local sale on it; `TenantAccessNotice.vue` in the shell |
| Storefront | `ApiRequestError.shopClosed`; the shop page shows "not taking orders" |
| Operator portal | Settings → Subscription (price, enforcement read-only); Sellers shows "Free until" and an unpaid flag |
| Tests | `TenantAccessApiTest.php` (28), `SubscriptionLifecycleTest.php` (29), `SubscriptionPaymentApiTest.php` (18), `apps/web/test/tenantAccess.spec.ts` (6) |

**Before deploying**: run the migrations, then `billing:backfill-trials --dry-run`
against production and read its output, then run it for real. Enforcement is
off, so skipping the backfill breaks nothing today — but it is the step that
makes turning enforcement on safe later, and it is easiest to do while nothing
depends on it.

Then `billing:advance-subscriptions --dry-run`, which should report **nothing
to do**: every organization is inside a backfilled trial, and no row has a
`current_period_ends_at` at all. Anything else in that output means the
backfill did not do what it was supposed to, and is worth understanding before
the scheduler runs on its own. The daily schedule also needs the standard
Laravel cron line — see deployment.md, "Scheduler".

**The lifecycle landed 2026-09-20** — the `past_due` scheduler and the three
dunning notices (§6.4). Both ship inert: the status machine runs regardless of
`BILLING_ENFORCE`, because the dates must already be right on the day
enforcement is switched on, but the mail is gated separately on
`BILLING_DUNNING`, which is also off. Nothing emails a merchant who was
promised free early access, and every organization's trial suppresses the
chase anyway.

**Not happening**: a payment gateway. Decided 2026-09-20 — collection stays a
manual GCash transfer an operator verifies, under the same no-gateway decision
as customer payments in [plan.md §4a](./plan.md). §6.3 has the reasoning.

**Still open**: the rider-board question (§7, last row), a dedicated suspended
screen in the seller Android app (it already shows the server's sentence and
keeps its token — see §5), and the decisions in §10.

---

## 1. What is true today

| | Where it is decided | Where it is read |
|---|---|---|
| **Subscription exists** | [`SignupController.php`](../backend/app/Http/Controllers/Api/SignupController.php) creates one `pending_verification` per org | [`PlatformAdminController::listOrgs`](../backend/app/Http/Controllers/Api/PlatformAdminController.php) displays it |
| **Subscription verified** | `PlatformAdminController` `verify` / `reject`, by hand | **Nowhere** |
| **Price** | `SignupController::PLAN_AMOUNT_CENTS = 49900`, a `private const` | Written onto the row at signup; shown in the operator portal |
| **Payment** | A GCash reference the owner types, stored as a plain string | An operator reads it and clicks Verify |
| **Org suspended** | `PlatformAdminController` `suspendOrg` / `reactivateOrg` | [`StoreDirectoryController::query`](../backend/app/Http/Controllers/Api/StoreDirectoryController.php) — and nothing else |

So a suspended organization, or one whose subscription was explicitly
**rejected**, keeps: staff sign-in, the whole till and back office, `/sync/push`
writes, the seller order queue, the live `store.{id}` websocket, its
`<slug>.omaykan.com` storefront, and the ability to take new customer orders. It
loses one thing — a row in the shop directory — which is the least of them,
because since the subdomain release every shop has a direct URL that does not go
through the directory at all.

The signup form, meanwhile, collects no payment and says in as many words that
early access is free ([`OnboardingPage.vue:373`](../apps/web/src/onboarding/OnboardingPage.vue#L373),
[`MerchantPitch.vue`](../apps/web/src/onboarding/MerchantPitch.vue),
[`LandingPage.vue:436`](../apps/web/src/landing/LandingPage.vue#L436),
[`AuthPage.vue:189`](../packages/core/src/pages/AuthPage.vue#L189)).

---

## 2. The trap: enforcing this today locks out every merchant

`SignupController` writes `STATUS_PENDING` on every signup and nothing moves it
without an operator clicking Verify. Nobody has been clicking, because the
status has never meant anything. **Every organization in the database is
`pending_verification`.**

Turn on "subscription must be `active`" as written and every existing merchant
is locked out of their own till on deploy, having been promised for months that
early access is free. That is not a rollout, it is an outage with a moral hazard
attached.

So the work splits in two, and the order matters:

1. **Build the mechanism and ship it inert.** One question, asked at the right
   chokepoints, answered "yes" for everybody until the price is real. This
   closes the *suspension* hole immediately, which is the half that is a genuine
   security gap today.
2. **Turn on the money later**, behind BIR clearance and a real price, with
   notice to merchants who were told it was free.

Nothing in sections 3–5 charges anybody. Sections 6–7 are what happens when
there is something to charge.

---

## 3. One question, one place to ask it

### 3.1 The verdict

Add to `App\Models\Organization`:

```php
public function accessVerdict(): TenantAccess
```

returning a small enum in `App\Services\Billing`:

| Verdict | Meaning | Who can still act |
|---|---|---|
| `Allowed` | Trading normally | everyone |
| `Suspended` | An operator switched it off | nobody, staff included |
| `Unpaid` | Subscription absent, rejected, or past its grace date | read-only staff access; storefront closed |

Precedence is suspension first — it already is in the operator portal's own UI
([`SellersView.vue:57`](../apps/web/src/platform-admin/views/SellersView.vue#L57):
*"Suspension outranks the subscription"*), and the two views must not disagree
about which fact is the reason.

`Unpaid` and `Suspended` are deliberately separate verdicts rather than one
boolean. They are different sentences to a merchant — *"pay this"* versus *"call
us"* — and a client that cannot tell them apart shows the wrong one. This is the
same reasoning as
[`EnsureRiderIsApproved`](../backend/app/Http/Middleware/EnsureRiderIsApproved.php),
which already returns `riderStatus` alongside its 403 precisely so the portal can
show a pending rider a different screen from a suspended one. Follow that shape
exactly.

### 3.2 The enforcement switch

```php
// config/billing.php
'enforce' => env('BILLING_ENFORCE', false),
```

When false, `accessVerdict()` never returns `Unpaid` — suspension still bites,
payment does not. This is what lets step 1 ship on its own, and it is what
staging runs with while the real thing is tested.

It is a config flag and not a feature-flag table on purpose: there is exactly one
platform, the decision is made once, and a row somebody can toggle from a web UI
is a row somebody can toggle by accident across every tenant at once.

### 3.3 Schema

Two nullable columns on `subscriptions`, one migration:

```php
$table->timestamp('trial_ends_at')->nullable();           // free until
$table->timestamp('current_period_ends_at')->nullable();  // paid through
```

`trial_ends_at` is what makes early access expressible as data rather than as a
deploy: every existing row gets backfilled with a date comfortably in the future,
and the promise "we'll tell you well before that changes" becomes a column you
can query rather than a sentence on a marketing page.

`current_period_ends_at` is what a real billing cycle writes. Until there is one,
it stays null and `trial_ends_at` carries everything.

The status vocabulary gains one value:

```php
public const STATUS_PAST_DUE = 'past_due';
```

`pending_verification` keeps its current meaning — *submitted, nobody has looked*
— which is **not** the same as unpaid, and must not be treated as such while the
operator queue is manual.

---

## 4. Where it is enforced

### 4.1 The merchant API — `StoreContextResolver` is already the chokepoint

Every merchant route that touches a shop's data resolves its tenant through
[`StoreContextResolver::resolve`](../backend/app/Services/StoreContextResolver.php),
via the [`ActsForAStore`](../backend/app/Http/Controllers/Concerns/ActsForAStore.php)
trait — `SyncController`, `ShiftController`, `SellerOrderController`,
`SellerRiderController`, `SellerConversationController`, `StaffRoleController`,
`StaffUserController`, `StoreImageController`. There is no store-scoped merchant
endpoint that does not.

That class already re-checks membership on every single request, and its own
docblock says why:

> Sanctum tokens do not expire. If access were proved only at sign-in, sacking
> someone would leave their phone working until they chose to sign out.

A suspension is the same problem with a bigger blast radius. The check belongs in
the same method, for the same reason, at the same cost — one more indexed read,
or none at all if the store is loaded as
`Store::query()->with('organization.subscription')->find($storeId)`, which
replaces the bare `find` currently there.

**Why not middleware.** The house pattern for "this whole group requires X" is a
middleware, and `EnsureRiderIsApproved` is the model. It does not fit here: the
merchant group in [`routes/api.php:341`](../backend/routes/api.php#L341) also
contains `/staff/stores`, `/staff/session-store` and `/staff/sign-out`, which are
reached with the *unscoped* token minted at sign-in. A middleware over the group
would have to resolve a store those three requests have not chosen yet, and the
resolver aborts with "Choose a store for this session first." A nested sub-group
would work, but it reintroduces exactly the failure the rider middleware's
docblock warns about — a future route added to the wrong group is silently
unguarded — whereas the resolver cannot be forgotten, because a controller that
skips it has no store to work with at all.

**Finish, don't start.** `Unpaid` should not be a blanket 403 on the merchant
API. A shop that owes us money still has its own sales history, and holding it
hostage is both ugly and, for anything BIR-adjacent, probably not ours to do.
Reads go through. So does progressing an order a customer has already placed —
status, delivery stage, rider assignment, settling payment — because refusing
it strands somebody's dinner halfway to their door, and the storefront is
already closed, so no new ones arrive. What is refused is *starting* something:
a sale (`POST /sync/push`), a shift (`POST /shifts/open`), a catalog or photo
change, a saved rider, a member of staff or a role. `grep writableStoreContext`
is that list. `Suspended` refuses everything: an operator suspends a tenant for
a reason that is not about money, and there is no version of that where the till
keeps taking orders.

*(The draft of this section refused the seller order mutations too. Building it
showed that would strand in-flight deliveries, so it was narrowed.)*

The verdict therefore rides on `StoreContext` rather than aborting inside the
resolver for every case:

```php
final readonly class StoreContext
{
    public function __construct(
        public User $user,
        public Store $store,
        public string $role,
        public TenantAccess $access,
    ) {}

    public function canWrite(): bool
    {
        return $this->access === TenantAccess::Allowed;
    }
}
```

`Suspended` aborts in the resolver. `Unpaid` reaches the controller, and each
write action asks `canWrite()`. That is a check a future endpoint can forget — so
the sync push, which is the one that matters, gets a test that proves it.

### 4.2 The door: `selectStore`

[`StaffAuthController::selectStore`](../backend/app/Http/Controllers/Api/StaffAuthController.php)
mints the store-scoped token and already refuses an inactive store. It must
refuse a suspended organization there too, with the machine-readable body from
3.1. Enforcing only in the resolver would mint a perfectly good token and then
403 every call made with it — technically safe, and it tells the merchant nothing
they can act on.

`storesFor()` — which backs both the sign-in response and `GET /staff/stores` —
should mark suspended orgs' stores rather than hide them. A manager whose shop
vanished from the picker files a bug; one whose shop says *Suspended — contact
support* calls support, which is the outcome we want.

**Sign-in itself stays open.** Someone who cannot get in cannot read the message
explaining why, cannot reset their password, and cannot reach support. The same
argument [`RiderAuthController:142`](../backend/app/Http/Controllers/Api/RiderAuthController.php#L142)
already makes for rejected and suspended riders.

### 4.3 The customer-facing side

This is the larger hole, and the checklist understates it. Two public endpoints
resolve a tenant and check only `store.status`:

- [`StorefrontCatalogController:44`](../backend/app/Http/Controllers/Api/StorefrontCatalogController.php#L44)
  — `abort_if($store === null || $store->status !== 'active', 404, …)`
- [`OnlineOrderController::store`](../backend/app/Http/Controllers/Api/OnlineOrderController.php)
  — looks the organization up by slug and checks neither `status` nor
  `suspended`, then takes the order

Both need the org check that `StoreDirectoryController` already does. A suspended
shop's `<slug>.omaykan.com` must serve the closed state, not a menu; and the order
endpoint must refuse independently of the catalog, because the storefront mirrors
its cart into `localStorage` and a checkout can arrive long after the page that
built it.

Factor the directory's existing predicate into one scope so there is a single
definition of "an organization the public can buy from":

```php
// App\Models\Organization
public function scopeTradable($query) { … }
```

and use it in all three places.

### 4.4 The websocket

[`routes/channels.php`](../backend/routes/channels.php) authorizes `store.{id}`
on a `StoreMembership` row alone. A suspended tenant's dashboard keeps its live
feed until the tab is closed. Add the same org check to both channel callbacks —
cheap, and otherwise "suspended" has a visible exception sitting on screen.

### 4.5 What stays open, deliberately

| Stays open | Why |
|---|---|
| Staff sign-in, password reset, `/staff/stores`, `/staff/sign-out` | You cannot read the refusal from behind it |
| `GET /online-orders/{order}` tracking | The order was placed while the shop was trading. The customer is owed the outcome |
| Rider endpoints for an already-claimed delivery | The rider is mid-journey and is not a party to the dispute |
| Customer ↔ shop conversations, for an **unpaid** shop | A customer with an order in flight is owed answers. (A suspended shop's staff lose these with everything else behind the resolver; its customers can still write, and see no reply.) |
| Everything under `auth:platform` | It is the tool that unsuspends |

---

## 5. The refusal, and what clients do with it

One body shape everywhere, matching the rider middleware:

```json
{
  "message": "This shop is suspended. Contact support@omaykan.com.",
  "tenantAccess": "suspended",
  "reason": "…"
}
```

| Client | Behaviour |
|---|---|
| Till / back office ([`packages/core`](../packages/core/src)) | Full-screen blocking state on `suspended`; a persistent banner on `unpaid`. Both re-check every minute and from a button |
| Seller Android app | Keeps its token (`StaffAuthInterceptor` clears only on 401) and shows the server's sentence in the order feed's error banner. No dedicated screen yet |
| Storefront `<slug>.omaykan.com` | "This shop isn't taking orders right now." No menu, no demo shelf, no "Try again" |
| Operator portal | Sellers shows the trial date and flags a shop the verdict blocks; Settings edits the price |

**The till is offline-first**, which the draft of this section missed. A sale is
rung up locally and pushed later, so the server refusing `/sync/push` cannot by
itself stop an unpaid till selling — it would take the customer's money and
queue a sale the server will never accept. So `packages/data` keeps the last
verdict it heard (from sign-in, from any refusal carrying `tenantAccess`, or
from asking `GET /api/staff/stores`, which reports it per store), persists it
so a till reopened offline still knows, and refuses `saveOrder` locally while it
is anything but `allowed`. Nothing already in the outbox is lost; it sends once
the shop is let back in.

The one thing to get right in the clients: **403 with `tenantAccess` is not a
sign-out.** The existing clients treat 401 as "clear the token" — see the
`withExceptions` note in [`bootstrap/app.php`](../backend/bootstrap/app.php)
about the rider 500 loop, which is the same class of mistake. A merchant whose
app silently signs them out on suspension day will report it as a broken login,
and we will debug the wrong thing.

---

## 6. A real price, and actually collecting it

### 6.1 What blocks it

BIR compliance ([positioning.md](./positioning.md), BIR row) still gates charging
any merchant at all. Nothing in sections 3–5 depends on that clearing; everything
in this section does. Build the mechanism, leave `BILLING_ENFORCE` false, and
revisit.

### 6.2 The price stops being a constant

`SignupController::PLAN_AMOUNT_CENTS` is a `private const` on a signup
controller, which means the price of the product is defined by the file that
creates accounts. Move it:

- **Short version** — a `plans` block on `PlatformSetting`, which already holds
  the delivery policy and is already editable from
  [`PlatformSettingsController`](../backend/app/Http/Controllers/Api/PlatformSettingsController.php).
  One plan, one amount, one currency.
- **Longer version** — a `plans` table, if there will ever be more than one tier.
  Do not build it for a tier list that does not exist.

Either way `subscriptions.amount_cents` keeps recording what *this* org agreed to
pay, so a price change does not silently re-price existing merchants. That is
already how the column behaves; the point is to keep it that way.

### 6.3 How the money arrives

| Option | What it costs us | What it costs the merchant | Verdict |
|---|---|---|---|
| **Manual GCash** (today) | An operator's attention per merchant per month; reconciliation by eye; a `gcash_reference` validated against nothing | A transfer and a typed reference, monthly | Fine for tens of merchants. Falls over well before hundreds, and it cannot dun anybody |
| **PayMongo / Xendit recurring** | Integration, webhooks, a sandbox, PCI-adjacent care, per-transaction fee | A card or GCash e-wallet authorization, once | **Not planned** — see the decision below |
| **Invoice + bank transfer** | Same manual reconciliation, slower | Familiar to a business that already has a bookkeeper | Worth offering alongside, not instead |

**Decided 2026-09-20: manual GCash, and no gateway.** Not "not yet" — the
platform is cash-based end to end, and the merchant subscription now sits under
the same decision as customer payments in [plan.md §4a](./plan.md). A
recurring-billing integration is not on the roadmap, so nothing in this
document should be read as waiting for one.

What that buys: no processor taking a percentage on a platform whose pitch is
that it does not take a cut, no PCI-adjacent surface, and no compliance
questions opened while BIR is unresolved. What it costs: an operator's
attention per merchant per month, reconciliation by eye, and a
`gcash_reference` validated against nothing. That is fine at tens of merchants
and falls over well before hundreds — which is the signal to revisit, not a
date.

The §6.4 status machine was still built to be driven from outside, because
that is worth having whether or not a gateway ever arrives: it is the same
seam an operator's Verify click uses.

**Built 2026-09-20.** Before this, "manual collection" was thinner than the
word suggests: `subscriptions.gcash_reference` was written only at signup by a
form that had stopped sending it, nothing displayed it, and a merchant had no
way at all to tell us about a transfer. The real path was an out-of-band email
and an operator clicking Verify.

`gcash_reference` is superseded by a row per transfer —
`subscription_payments` (migration `2026_09_20_000200`). The column stays for
the signup rows that have one; nothing writes it any more.

| Side | Where |
|---|---|
| Merchant API | `SellerSubscriptionController` — `GET /api/seller/subscription`, `POST /api/seller/subscription/payments` |
| Merchant UI | `SubscriptionPanel.vue`, in the till under Settings → Subscription |
| Operator API | `PlatformAdminController` — `listPayments`, `acceptPayment`, `rejectPayment` |
| Operator UI | `SubscriptionPaymentsQueue.vue`, above the seller list |
| Tests | `backend/tests/Feature/Api/SubscriptionPaymentApiTest.php` (18) |

Three things worth knowing about the design:

- **The pay-us screen is not behind `writableStoreContext`.** Every other
  merchant write is refused for an unpaid tenant. If this one were too, the
  only way to stop being unpaid would be to already not be unpaid. It is on
  plain `storeContext`, so a suspended org still cannot reach it — a
  suspension is a conversation with a person, not a transfer.
- **The operator sets the period; the amount does not imply it.** A merchant
  who pays for two months at once, or pays short, is a conversation. Accepting
  a payment is the only thing in the product that ever writes
  `current_period_ends_at` — which is exactly why the placeholder price is
  survivable: until an operator accepts one, every shop rests on its trial.
- **A rejection changes nothing about the subscription.** The shop stays
  wherever it was, inside whatever grace it had. Rejecting an unmatched
  reference is not a penalty, and a reason is required because the merchant
  reads it.

The merchant is quoted the **current** platform price, not the one frozen on
their row at signup — which is what makes Settings → Subscription (§6.2) a
control that actually reaches somebody. Where the two differ, the panel shows
both rather than letting a merchant be surprised by it.

**As built (2026-09-20):** the seam is one method,
`SubscriptionBilling::recordPayment($subscription, $paidThrough)`. An operator
clicking Verify goes through it, and it is the only way a subscription becomes
paid — so if a gateway is ever reconsidered, that is the single place it
attaches. There is deliberately **no `PaymentGateway` interface** — it would
have one implementation and one caller, which is the speculative layer this document
warns against for `plans` two sections up. Extract it the day there are two.

**Do not** auto-charge a stored instrument the first time this ships. Every
merchant on the platform signed up under "free during early access · no card
required". The first paid cycle has to be something they actively agree to.

### 6.4 The lifecycle

```
pending_verification ──verify──► active ──period ends──► past_due
        │                          ▲                        │
      reject                       └────── payment ─────────┘
        ▼                                                   │
     rejected                                      grace elapsed
                                                            ▼
                                                     (Unpaid verdict)
```

**Built 2026-09-20.** `billing:advance-subscriptions`, daily at 06:00.

- **`active` → `past_due`** is a scheduled command, not a request-time
  computation, so there is a log line and a timestamp for when a merchant's state
  changed. `SubscriptionBilling::advance()`, idempotent, and it only moves
  `active` — `pending_verification` is our backlog and `rejected` is an
  operator's decision a cron job has no business revisiting.
- **Grace** is a fixed window on `past_due` before the verdict becomes `Unpaid`.
  Days, not hours. A failed payment is usually a card, not a decision.
- **Dunning**: mail before the period ends (`renewal_due`), when it lapses
  (`past_due`), and before grace expires (`final_notice`), through the existing
  `Mail` + queue path that `SignupController::announce` uses. A merchant who
  loses write access without three emails first will say they were never told,
  and they will be right.
  - The stage is recorded on the row (`dunning_stage`, `dunned_at`), so a daily
    command sends each notice once rather than the same warning every morning.
    It is cleared on payment, so a merchant who renews and later lapses again
    gets the sequence from the start.
  - A grace window of a day or less gets **one** notice, not two: there is no
    moment that is both after the past-due mail and before trading stops, and
    that mail already carries the date.
  - Gated on `billing.dunning`, separately from `billing.enforce`. The status
    machine is harmless with enforcement off; an email is not.
- **Suspension stays manual.** Non-payment produces `Unpaid`, never `suspended`.
  Suspension is an operator's judgement about a tenant, and conflating the two
  makes the audit log unreadable.

---

## 7. Suspension, end to end

The checklist item in full. Each row is a separate small change; none depends on
the billing work.

| Surface | Today | Change |
|---|---|---|
| Shop directory | Filters on `suspended` | — |
| `GET /storefront/catalog` | Store status only | Org check (4.3) |
| `POST /online-orders` | No org check at all | Org check (4.3) |
| `POST /staff/session-store` | Store status only | Refuse suspended org (4.2) |
| `GET /staff/stores`, sign-in response | Lists the store normally | Mark it suspended (4.2) |
| Every store-scoped merchant route | Nothing | `StoreContextResolver` (4.1) |
| `store.{id}` / `orders.{id}` channels | Membership only | Org check (4.4) |
| Rider board | Untested against suspension | Confirm a suspended shop's orders stop being posted |

That last row is an open question rather than a decision — see section 10.

---

## 8. Rollout order

1. Migration: `trial_ends_at`, `current_period_ends_at`, `STATUS_PAST_DUE`.
2. Backfill every existing subscription with a far-future `trial_ends_at`. One
   command, run once, and it is the thing that makes step 4 safe.
3. `TenantAccess`, `Organization::accessVerdict()`, `scopeTradable`,
   `config/billing.php` with `enforce => false`.
4. Wire the chokepoints: resolver, `selectStore`, catalog, online orders,
   channels. **Suspension is live from here on; payment is not.**
5. Clients: the two blocking states, and the "403 is not a sign-out" fix.
6. Price out of the controller and into settings (6.2).
7. Stop. Everything above ships without charging anybody.
8. — BIR clears — price set, merchants notified, gateway integrated,
   `BILLING_ENFORCE=true`.

Steps 1–5 are the ones worth doing now; step 4 closes a hole that is open today.
Steps 1–6 were built on 2026-09-19, and the §6.4 lifecycle on 2026-09-20 — see
Status at the top. Step 7 still holds: none of it charges anybody.

---

## 9. Tests

As built, in one file per side rather than spread across the existing suites,
so the whole rule reads in one place:

**`backend/tests/Feature/Api/TenantAccessApiTest.php`** (28)

- The verdict table — suspension over a paid subscription, an inactive org,
  enforcement off never producing `Unpaid`, pending is not unpaid, rejected is
  unpaid even in a trial, period end, grace, a trial covering a lapsed period,
  and a not-yet-loaded status not reading as suspended.
- Suspension end to end — a token minted *before* the suspension stops working;
  the store picker marks and refuses the shop with a reason; the catalog closes;
  no new orders (and none for an inactive store either, which was never
  checked); an existing order stays trackable; the directory drops it; the
  websocket channel refuses it.
- Unpaid with enforcement on — let in and told so; reads work, `sync/push` and
  `shifts/open` refused; storefront closed and unlisted. And the same shop with
  enforcement off trading exactly as before.
- Price and trial — signup records the settings price and a trial date; the
  operator changes the price without re-pricing anyone.
- The backfill — dry run writes nothing; creates missing subscriptions;
  idempotent; never shortens a trial; leaves rejections alone; refuses a past
  date.

**`apps/web/test/tenantAccess.spec.ts`** (6) — the till learns a suspension from
a refusal and keeps its session, refuses a local sale without touching the
network, learns it was let back in, keeps the last answer offline, and ignores
a reason it does not recognise.

---

## 10. Open decisions

1. **Does a suspended shop's in-flight order still get delivered?** For: the
   customer and the rider are not parties to it. Against: the shop is off, and
   someone has to answer for the food. Current lean — finish what is in flight,
   accept nothing new. Not yet reflected anywhere in code.
2. **Read-only or nothing for `Unpaid`?** Section 4.1 argues read-only. It is a
   product call as much as a technical one, and it is easier to loosen later than
   to tighten.
3. **Does `pending_verification` ever become `Unpaid`?** Only if verification
   stops being manual. While an operator has to click, a merchant cannot be
   penalised for our queue.
4. **Trial length** for merchants who sign up after enforcement is on — and
   whether the signup form starts asking for payment details again, which it was
   built to do and stopped doing.
5. **Per-store or per-org?** Everything here is per-organization. Multi-branch
   pricing is a real question the day a merchant opens a second branch, and
   `stores.code = 'main'` says that day has not come.
