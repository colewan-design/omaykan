# Feature audit — missing and unfinished

**Audited 2026-08-27** against `main` at `d13d273` and against the live database
on the production VPS. Every claim below was checked in the code, and the ones
that touch data were checked a second time against production rather than
against a local seed.

This exists because [feature-log.md](./feature-log.md) and [plan.md](./plan.md)
had both drifted far enough to be misleading — see [§6](#6-documentation-that-is-now-wrong).
Where this file and those two disagree, this one was written later and against
the running system.

---

## 1. Summary

| Area | State |
|---|---|
| Merchant POS (14 pages) | Built |
| Customer storefront (web) | Built, **single-tenant only** |
| Rider portal + operator approval | Built, **no earnings or payout record** |
| Platform admin + support inbox | Built |
| Realtime (Reverb) | Built, **subscribed on one page only** |
| Offline outbox + sync | Built |
| Order discounts | **Not built** — no column, no API |
| Promo / voucher box in the till | ~~Inert in all four order panels~~ — **removed 2026-08-27** |
| Loyalty | **Not built** — two lines of UI copy, nothing behind them |
| Subscription billing | Recorded, **never enforced** |
| Rider payouts | **Not built** |
| Multi-vendor marketplace | **Not built** — the landing page sells one |
| Frontend tests | ~~None~~ — **28 added 2026-08-27**; still no component or store coverage |
| BIR compliance | Unverified — blocks charging anyone |

Two ten-case end-to-end passes are in [e2e-findings.md](./e2e-findings.md).
They found three high-severity defects not listed here, none with any test
coverage: a product a merchant creates in their own POS could never be sold
online, settling an order twice double-recorded the payment and corrupted cash
reconciliation, and one uncategorised product would have 500'd the whole
storefront on PostgreSQL. **All three are fixed**, with regression tests and a
new PostgreSQL test lane — the SQLite suite could not catch the third.

Operational gaps — deploy process, backups, monitoring, and the fact that the
production schema no longer matches `main`'s migrations — are in
[deployment.md](./deployment.md), not here.

### Fixed since the audit (2026-08-27)

| | Was |
|---|---|
| §2.1b promo box | Removed from all four order panels, with its CSS |
| §2.5 frontend tests | 28 tests across money, delivery and permissions |
| §3.3 seeded roles | Now writes all 14 keys and mirrors `defaultRoles`; unknown keys throw |
| §3.6 "under ₱100" shelf | Now filters on price |
| §4 dead pages | `WorkspacePage.vue` and `AnalyticsPage.vue` deleted |
| — | `ownerPageKeys` corrected and wired to the router (found by the new tests — see below) |
| — | `vite build` now refuses a production bundle with a blank tenant |

**`ownerPageKeys` was wrong, and dead.** It listed `employees` as owner-only,
contradicting both the router (which marked only `integrations` and
`diagnostics` as `ownerOnly`) and `defaultRoles` (Manager holds `employees`
through `canManageStaff`). Nothing read it, so nothing broke — but the router
duplicated the same intent in route meta, free to drift. The list is now
correct, `isOwnerPage()` derives from it, the guard in `createPosApp.ts` calls
that, and the duplicated `ownerOnly` meta flags are gone. One source of truth.

Still open, and needing a product decision rather than a patch: §2.1 (the
discount engine itself), §2.2, §2.3, §2.4, §3.1, §3.2, §3.4, §3.5.

---

## 2. Missing outright

Nothing in the codebase implements these. They are not partial; they are absent.

### 2.1 Order discounts

`orders` has `subtotal_cents`, `tax_cents`, `delivery_fee_cents` and
`total_cents` and **no discount column of any kind** (verified against the
production schema, not the migration files). There is no cart-level or
line-level discount in `packages/core/src/stores/pos.ts`.

What does exist is `compareAtPriceCents` — a "was" price that renders a
strikethrough and a "Save 21%" badge via `discountPercent()`
([index.ts:1007](../packages/shared/src/index.ts#L1007)). That is a *display*
of a shelf price the merchant already set. A cashier cannot take ₱20 off a
sale, and the schema has nowhere to record it if they could.

### 2.1b The promo code box is live in the till and does nothing — FIXED

> **Resolved 2026-08-27.** The input and its clear button are gone from all
> four panels, along with their CSS. The payment-method selector that shared
> the row stayed. Wiring it instead would have needed §2.1 first. What follows
> is the original finding.

All four order panels — `OrderPanel`, `GroceryOrderPanel`,
`RestaurantOrderPanel`, `NailSalonOrderPanel` — render an input placeholdered
**"Add Promo or Voucher"** with a clear button beside it.

In every one of the four, `promoCode` is declared as a `ref('')`, bound with
`v-model`, and set back to `''` by `clearPromo()`. It is read by nothing. It
reaches no total, no order payload, and no endpoint. A cashier can type a code
in front of a customer, see it sit there, and charge full price.

This is worse than the missing engine above: the absent feature is invisible,
but this one is a control staff can use and be misled by. Either wire it to
§2.1 or take the input out.

### 2.2 Loyalty

No implementation at all. The only occurrences of the word are two pieces of
UI copy in [CustomersPage.vue:438](../packages/core/src/pages/CustomersPage.vue#L438)
and :511 describing what named customers would be *for*. No points, no tiers,
no balance, no table.

### 2.3 Rider earnings and payouts

This is the gap that sits closest to the product's own promise. The pitch is
**"100% of the delivery fee to the rider"**, and the delivery fee is computed
and stored per order (`orders.delivery_fee_cents`). But the `riders` table is:

```
id  name  email  phone  password  status
license_number  license_image_path  plate_number  plate_image_path
review_note  reviewed_at  last_seen_at  remember_token  created_at  updated_at
```

There is no earnings total, no payout record, no remittance state, no ledger.
Nothing tells a rider what they are owed this week, and nothing records that
they were paid. The promise is currently kept by hand, off-system.

There is also no customer rating of riders — `RiderReviewController` is the
*operator* reviewing a rider's licence and plate photos at signup, not a
shopper rating a delivery.

### 2.4 Multi-vendor marketplace

The landing page sells a marketplace: *"Order from the carinderias, sari-sari
stores, and market stalls around you"*, with a delivery-address field and a
search box. The code serves exactly one store.

The tenant is fixed at **build time** by `VITE_POS_ORGANIZATION_SLUG` /
`VITE_POS_STORE_CODE` ([context.ts](../apps/web/src/commerce/context.ts)), and
`fetchCatalog()` sends those two values and nothing else. `tenantBinding.ts`
does resolve a tenant at runtime, but only for the staff app (`app.html`) —
the storefront has no equivalent path.

Consequence in production today: org `sm` has 464 active products and is the
one the build is pinned to; org `colewan-market` exists, is active, and is
unreachable as a shop.

### 2.5 Frontend tests — PARTLY ADDRESSED

> **Partly resolved 2026-08-27.** `apps/web/test/` now holds 28 vitest cases
> over money (tax rounding, discount badge, currency), delivery (fee banding,
> the service-area limit, haversine) and permissions. `npm test` runs them.
> Still uncovered: components, the Pinia stores, and the sync outbox.

There is no `vitest.config`, no `*.spec.ts`, and no `*.test.ts` anywhere in
`apps/` or `packages/`. The backend has 17 API feature-test files (150 tests,
819 assertions, all passing). The entire Vue side — cart maths, permission
gating, the sync outbox — has no automated coverage at all.

---

## 3. Built but unfinished

### 3.1 Subscription is recorded and never enforced

`Subscription` is referenced in exactly four places: the model, the
`Organization` relation, `SignupController` (creates one) and
`PlatformAdminController` (displays and verifies one). It is checked by **no
middleware and no controller**. There is no gate on `/api/sync/*`, on
`/api/staff/sign-in`, or on order placement.

An organization whose subscription is unpaid, unverified, or absent has exactly
the same access as one that paid. The ₱499 figure is a placeholder with a
comment saying so ([SignupController.php:38](../backend/app/Http/Controllers/Api/SignupController.php#L38)).

Org status is likewise not checked at sign-in: `DeviceSessionController` and
`StaffSessionController` **set** `status => 'active'` on the session rows they
create, but neither reads the organization's or store's own status first. The
operator portal can suspend a tenant; nothing downstream acts on it.

### 3.2 Realtime is wired to one surface

`subscribeToStoreOrders` ([orderChannel.ts](../packages/core/src/realtime/orderChannel.ts))
is imported by exactly one file: [DashboardPage.vue:16](../packages/core/src/pages/DashboardPage.vue#L16).

The backend broadcasts `order.placed`, `order.status-changed` and
`order.delivery-updated`. The register, the orders page, the rider portal and
the customer's order-tracking page all still rely on polling or a manual
refresh. The infrastructure is deployed and healthy — `omaykan-reverb` and
`omaykan-queue` both run under systemd in production, and a queued `OrderPlaced`
broadcast was observed completing in 293ms on a local instance. It is simply
only listened to in one place.

### 3.3 Seeded roles disagree with the app's own page keys — FIXED

> **Resolved 2026-08-27.** The seeder now writes all 14 keys for every role and
> mirrors `defaultRoles`, and `permissionsFor()` throws on a key that is not a
> real page, so this cannot silently return.

[DatabaseSeeder.php:20](../backend/database/seeders/DatabaseSeeder.php#L20)
grants permissions over:

```php
['register', 'orders', 'products', 'analytics', 'settings', 'diagnostics']
```

`analytics` **is not a page key**. The real list is the 14 in `appPageKeys`, and
the seeder omits nine of them (`dashboard`, `sales`, `customers`, `suppliers`,
`employees`, `inventory`, `tables`, `reports`, `integrations`).

Missing keys read as denied — `withAllPermissionKeys` coerces anything absent
to `false` ([auth.ts:25](../packages/core/src/stores/auth.ts#L25)). The blast
radius is limited because `admin` and `guest` are in `lockedRoleIds` and are
always overwritten from `defaultRoles`, so a seeded owner is unaffected. But a
seeded **Manager** or **Cashier** gets a materially narrower app than
`defaultRoles` intends.

Only dev seeds are affected: the production `roles` table is empty, so the
client falls back to `defaultRoles`, which are correct.

### 3.4 Delivery fees fall back to a flat rate in production

`DeliveryQuoter` prices by distance from the store's pin
(`BASE_FEE_CENTS = 4900`, `BASE_KM = 2`, `PER_KM_CENTS = 1500`, `MAX_KM = 15`),
and the checkout promises *"Flat ₱49 for the first 2 km, then ₱15/km."*

Both production stores have `lat` and `lng` **null**. `quote()` returns the
flat base fee the moment either pin is missing, so in production today:

- the per-km component never applies — every delivery is ₱49 regardless of distance
- **the 15 km service-area cap never applies either.** `OutsideDeliveryAreaException`
  is thrown only on the distance branch, which is unreachable without a pin. An
  order to an address 40 km away is accepted, at ₱49.

This is missing *data*, not missing code — but the second consequence is the
one worth fixing first, because it silently accepts deliveries no rider can make.

### 3.5 Telemetry is collected and never read

`trackAppEvent` writes nine event types, queues them in the outbox and flushes
them to the backend, marking `sentAt` on success
([index.ts:2470](../packages/data/src/index.ts#L2470)). The count of *pending*
events is surfaced on the Diagnostics and Integrations pages.

Nothing consumes the events after they arrive. There is no reporting surface
over them and no PostHog, which [analytics.md](./analytics.md) names as the
intended sink. The pipe is built at both ends and empty in the middle.

---

### 3.6 The "under ₱100" shelf does not filter by price (latent) — FIXED

> **Resolved 2026-08-27.** The shelf filters on `priceCents < 10000` before
> sorting. `ProductRow` already self-hides when empty, so a shop with nothing
> under ₱100 simply shows one row fewer.

[LandingPage.vue:146](../apps/web/src/landing/LandingPage.vue#L146) builds the
`cheapest` shelf by sorting on price and taking the first 12. It applies no
price predicate. The shelf it feeds is titled **"Everyday essentials under
₱100"** ([:350](../apps/web/src/landing/LandingPage.vue#L350)).

Not currently visible in production: SM's 464-product catalog puts the cheapest
twelve between ₱7.50 and ₱21.50, so the title happens to hold. It breaks the
moment a shop's twelve cheapest items are not all under ₱100 — a thin catalog,
or a shop selling nothing cheap. Seen for real against the one-product demo
tenant, where the shelf advertised a ₱120 espresso as under ₱100.

Either filter on `priceCents < 10000` or retitle the shelf.

---

## 4. Dead code — REMOVED

> **Resolved 2026-08-27.** Both files deleted; `pages/` now holds 15 components
> and the router routes all 15.

Two page components are neither routed nor imported anywhere:

- `packages/core/src/pages/WorkspacePage.vue` — an explicit placeholder whose
  own copy reads *"We can turn this placeholder into a full management surface
  next"*, with a "Coming online" badge
- `packages/core/src/pages/AnalyticsPage.vue`

There are 17 files in `pages/`, 15 components routed, and 14 real page keys.
The README's "17 pages" counts both dead files.

---

## 5. Production-only gaps

Checked against the live database, not a local seed.

| Finding | Detail |
|---|---|
| One shop has no catalog | `colewan-market` is active with 0 products |
| No store pins | Both stores have `lat`/`lng` null — see §3.4 |
| No riders | `riders` table is empty; the rider flow has never run end to end in production |
| Almost no traffic | 1 customer account, 1 order |
| Orphaned branch schema | `platform_audit_logs` (12 rows) and `orders.payment_confirmed_by_role` still exist and hold data, but `main` has no code reading either — left in place deliberately during the 2026-08-27 cutover |

Mail and the support inbox **are** live: the PHP `imap` extension is installed
and `MAIL_MAILER=smtp` points at Hostinger with `SUPPORT_INBOX_HOST` configured.
This contradicts the local `.env.example`, which suggests leaving it blank.

---

## 6. Documentation that is now wrong

Corrected in the README on 2026-08-27; **not yet corrected in these two**:

- [plan.md:45](./plan.md#L45), [:65](./plan.md#L65), [:105-106](./plan.md#L105)
  describe Firestore and the `api/*.ts` handlers as "the live path today" and
  say "Laravel Echo is not installed". All of it was deleted in `d13d273`;
  Echo is installed and running.
- [plan.md:44](./plan.md#L44) and [feature-log.md:122](./feature-log.md#L122)
  say the Laravel backend is **"Not yet deployed"**. It has been serving
  omaykan.com for some time.
- [plan.md:113](./plan.md#L113) says "there is no outbox". There is —
  `pos.sync.outbox`, with `flushOutbox()` and per-event `sentAt`.
- [plan.md:195-199](./plan.md#L195) lists Phase 0 provisioning steps
  (PostgreSQL, PHP-FPM, nginx, TLS, supervisor units) as to-do. All are done.
- `documentation/apps/mobile/README.md` still describes a "Capacitor shell
  placeholder" for apps deleted in `16c08ba`.

BIR remains genuinely unverified and genuinely blocking, exactly as
[positioning.md:156](./positioning.md#L156) says.

---

## 7. Suggested order

0. **Remove or wire the promo box**, and **give the stores a pin.** Both are
   small, both are live in front of customers right now, and both currently
   mislead someone. (§2.1b, §3.4)
1. **Enforce the subscription**, or stop calling it a business model. One
   middleware on the sync and session routes. (§3.1)
2. **Rider payout ledger** — the "100%" promise is unbacked by any record. (§2.3)
3. **Store pins** — a data fix that makes delivery pricing behave as advertised. (§3.4)
4. **Realtime beyond the dashboard** — the expensive part already runs. (§3.2)
5. **Multi-tenant storefront** — the largest piece, and the one the landing
   page has already promised. (§2.4)
6. **Frontend tests**, starting with cart totals and permission gating. (§2.5)
7. Fix the seeder's page keys and delete the two dead pages. (§3.3, §4)
