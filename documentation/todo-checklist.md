# Not done yet — checklist

**Compiled 2026-09-18** from [feature-audit.md](./feature-audit.md),
[e2e-findings.md](./e2e-findings.md), [deployment.md](./deployment.md),
[mobile-plan.md](./mobile-plan.md), [live-delivery-tracking.md](./live-delivery-tracking.md)
and the seller/rider READMEs, then re-checked against the code on `main` plus the
uncommitted working tree. Items the docs still list as open but the code has since
closed are under [Already done](#already-done--docs-still-say-otherwise) at the bottom.

Tick an item when it ships, and add the date.

---

## Business-critical

- [ ] **BIR compliance verified** — still blocks charging any merchant ([positioning.md](./positioning.md), BIR row)
- [ ] **Subscription enforcement switched on** — `Organization::accessVerdict()` is consulted at every gate that matters — staff sign-in, `StoreContextResolver` on each till request, `POST /online-orders`, the storefront catalog and the shop directory. A verdict method at each gate rather than middleware, deliberately: whether a subscription is current depends on dates, a grace window and a config flag, so a list query filters its results after loading rather than trying to say it in SQL (`scopeTradable` is the SQL half — suspension and status only). ([feature-audit.md §3.1](./feature-audit.md) still says nothing checks a subscription and an unpaid org has full access; that was true on 2026-09-18 and has not been since — see the stale-documentation section.) Plan: [subscription-and-suspension.md §3–4](./subscription-and-suspension.md). Mechanism built 2026-09-19 and shipping **off** (`BILLING_ENFORCE`), and **kept off deliberately as of 2026-09-20** — collection and cut-off stay a manual act while there is no gateway. Nothing blocks a merchant automatically for non-payment; an operator suspending an organization does, and always has, independently of this flag. Ticks when `BILLING_ENFORCE=true` in production, which wants a real price and BIR clearance first
- [x] **Real subscription price** — ₱499 (`PlatformSetting::DEFAULT_PLAN`, `amountCents` 49900) is a placeholder. **What is left is the number and telling merchants — there is no engineering in it**, and it is blocked on the BIR item above. Ticks when a real price is set and merchants have been told
  - The method is settled: **PayMongo hosted GCash checkout**, added 2026-09-20, with the manual transfer kept beside it for a shop that prefers it. This reverses the no-gateway decision recorded earlier the same day — see [plan.md §4a](./plan.md). Taking a payment and cutting somebody off for not making one stayed separate; the second is still off
  - Everything around the number is built, and re-checked against the code 2026-09-20. The price is a setting rather than a constant — an operator edits it in Settings → Subscription, and `SignupController`, `billing:backfill-trials` and the merchant's own subscription screen all read it live, so changing it there reaches them. Collection is a transfer the merchant records in the till, which an operator accepts with the period it covers (the only thing that ever sets a paid-to date) or rejects with a reason the merchant sees (`SubscriptionPayment`). `billing:advance-subscriptions` runs daily at 06:00, lapses a subscription to `past_due` and sends the three notices
  - It cannot bite anyone in the meantime: `BILLING_ENFORCE` and `BILLING_DUNNING` both default to false, and the 365-day trial every organization carries suppresses the chase besides. Switching either on is the [Subscription enforced](#business-critical) item above, not this one. Plan: [subscription-and-suspension.md §6](./subscription-and-suspension.md), collection in [§6.3](./subscription-and-suspension.md)
- [ ] **Organization suspension respected downstream** — staff sign-in checks store and user status, not whether the organization is suspended; only the shop directory filters on `suspended`. Wider than it reads: `POST /online-orders` and the storefront catalog check neither. Plan: [subscription-and-suspension.md §7](./subscription-and-suspension.md). **Built 2026-09-19, not yet deployed** — tick when it ships

## Merchant (POS / seller)

Plan for all eight: [merchant-features.md](./merchant-features.md). **All eight built 2026-09-19, not yet deployed** — see its Status section, and [deployment.md §6b](./deployment.md) for the first-deploy steps. Tick each when it ships. Senior citizen / PWD discounts are left out pending the BIR item.

- [ ] **Order discounts** — no discount column on `orders`, nothing in the POS store. Only the shelf "was" price exists ([feature-audit.md §2.1](./feature-audit.md)). The server also stores the till's totals unchecked, and senior/PWD discounts (a legal requirement, VAT-exempt) should shape the design — confirm with the BIR item. Plan: [merchant-features.md §7](./merchant-features.md)
- [ ] **Promo / voucher codes** — the inert box was removed; needs the discount engine first. Plan: [merchant-features.md §8](./merchant-features.md)
- [ ] **Loyalty** — no points, balance or table. Tiers exist only as a per-device calculation in `CustomersPage.vue` ([feature-audit.md §2.2](./feature-audit.md)). Blocked on customers syncing to the server: today they live only on the till that created them. Plan: [merchant-features.md §9](./merchant-features.md)
- [ ] **Server-side guard on catalog writes** — `POST /api/sync/push` applies product and stock events for any signed-in staff, with no manager check. A cashier's token can edit the catalog ([seller/README.md](../apps/mobile-android/seller/README.md), "Next" #5). Needs a terminal `rejected` sync status, or a refused event retries forever. Plan: [merchant-features.md §2](./merchant-features.md)
- [ ] **Product photo upload endpoint** — only `PUT /seller/store-image` exists. The seller app's product form cannot add a photo, unit or description (`products` has no description column at all). Meanwhile the till already syncs photos as base64 inside `products.image_url`. Plan: [merchant-features.md §4](./merchant-features.md)
- [ ] **Shop open/closed switch** — nothing on the server takes a shop off the storefront temporarily. Plan: [merchant-features.md §3](./merchant-features.md)
- [ ] **Seller app on the Reverb websocket** — it still polls every 15s. Channel auth is ready, but this is **not** close to a no-op: the app has no Pusher client, and production Reverb sits at `/reverb/`, a path `pusher-websocket-java` cannot reach ([mobile-plan.md §12.1](./mobile-plan.md)). Blocked on the same host decision as the customer app's live tracking. Plan: [merchant-features.md §6](./merchant-features.md)
- [ ] **Distinct order-alert sound** for the seller app. Needs a new notification channel, because Android fixes a channel's sound at creation. Plan: [merchant-features.md §5](./merchant-features.md)
- [ ] **Voids reach the server** — found 2026-09-19. The till marks an order voided and returns its stock locally; the server's copy stays a completed sale, and counts in promo redemptions and loyalty earning. **Fixed 2026-09-19, not yet deployed:** the till voids through `POST /api/register/orders/{id}/void` before voiding locally; the server soft-deletes the order, puts the stock back, takes back its points, records who and why (`orders.voided_by_user_id`, `void_reason`, new migration) and refuses voids from anyone but the owner. Tick when it ships
- [ ] **Online checkout charges the branch price** — found 2026-09-19. The storefront catalog shows `product_store_overrides.price_cents`; `OnlineOrderController::priceLines` charges the organization's price. **Fixed 2026-09-19, not yet deployed:** `priceLines` now charges the branch price and refuses a product the branch has switched off, for both the quote and the order. Tick when it ships
- [ ] **Online-only register checkout** — decided 2026-09-19 (the till was offline-first: sales queued in an outbox, and the server flagged bad ones in `integrity_flags` after the money had changed hands). **Built 2026-09-19, not yet deployed:** the till sends each sale to `POST /api/register/orders` before completing it; the server refuses bad totals, a discount beyond the role's limit, a used-up promo code and overdrawn points, and the payment sheet shows the reason. Also fixes register sales taking stock off the server twice (once with the order, once as a separate `sale` adjustment). Tick when it ships
  - Same release: the "Online sync" switch is gone from Settings and Integrations (a till configured with a server always uses it), `/api/sync/push` no longer records sales, and `orders.integrity_flags` is dropped. An `order` event still queued on an old till fails and stays on that till rather than being recorded unchecked or thrown away — after deploying, `sync_events` rows with `entity_type = 'order'` and a `failed_at` show which tills still hold one

## Realtime and notifications

- [ ] **Realtime beyond the dashboard** — among staff screens, only `DashboardPage.vue` subscribes to `store.{id}`. The register, the orders page and the rider portal still poll ([feature-audit.md §3.2](./feature-audit.md))
- [ ] **Customer order push (FCM)** — `PushSender`, `OrderPushToken` and a migration exist but are **uncommitted and undeployed**. Also needs `FIREBASE_CREDENTIALS` on the server
- [ ] **Rider push for new jobs** — needs a "job posted" event (none exists) plus a device-token registry. Today there is only an opt-in 60s polling watcher
- [ ] **Seller push for new orders** — same token registry, sent off `OrderPlaced`
- [ ] **Stale-order sweep** — a scheduled job so an order's status can never sit still forever ([mobile-plan.md §8a.3](./mobile-plan.md)). Not in `app/Console`. The scheduler it needs now exists (`routes/console.php`; cron line in [deployment.md §6b](./deployment.md))

## Delivery and riders

- [ ] **Store pins in production** — stores without `lat`/`lng` fall back to a flat ₱49, and **the 15 km service-area cap never applies** ([feature-audit.md §3.4](./feature-audit.md)). Check every live store, including the three seeded 2026-09-13
- [ ] **Geocoder** — typed addresses can't become coordinates; only browser geolocation gives a pin
- [ ] **Rider payout / remittance record** — earnings are now summed (`RiderEarningsController`), but nothing records that a rider was *paid*
- [ ] **Rider ETA** — needs a Directions API duration per leg, and a decision on what to show when traffic makes it wrong ([live-delivery-tracking.md §8](./live-delivery-tracking.md))
- [ ] **Auto-assignment / dispatch** — deliberately absent for now. Blocked on trustworthy rider positions

## Customer (web + Android `:app`)

- [ ] **Address-filtered catalog on Android** — delivery-address chip plus a "nothing reaches you" state ([mobile-plan.md Phase 5](./mobile-plan.md))
- [ ] **Android customer app live tracking over Reverb** — no Pusher client in `:app` yet. The `/reverb/` question is answered: the seller app's `PusherProtocol` + `OrderRealtime` (OkHttp, no new library, no new host) reach it, and `:app` can copy them ([merchant-features.md §6](./merchant-features.md), [mobile-plan.md §12.1](./mobile-plan.md))
- [ ] **Decide: GCash preference** — mobile still asks for it and web does not; the two clients should agree ([mobile-plan.md §12.4](./mobile-plan.md))
- [ ] **Decide: wishlist** — Android-only (`SavedProductsStore.kt`), with no server side. Keep it local or drop it ([mobile-plan.md §12.5](./mobile-plan.md))
- [ ] **Compose UI tests over checkout**

## Web audit — 2026-09-22

Found by a live crawl of omaykan.com and all five shop subdomains, at 1440px
and 390px, signed out. It checked 141 links and clicked each kind of button on
a fresh page load, skipping anything that would place, send, pay for or create
something. Pages behind a sign-in were checked in the code only.

### Broken now (confirmed live)

- [ ] **Shop page delivery card and cart "Set address" do nothing** — on all 5 shops, at both widths. `delivery.openDialog()` sets a flag that only `AddressDialog` in `FdHeader.vue` reads, and the shop page redesign removed that header. Shoppers on a shop page cannot set a location or get a delivery quote. Fix: mount `AddressDialog` in `ShopPage.vue`
- [ ] **Shop page "Message" button is a dead end** — it links to `/account?section=messages`, but production builds with `VITE_FEATURE_MESSAGES=false`, so the section does not exist and the shopper lands on the account dashboard. `ShopPage.vue` never checks `MESSAGING_ENABLED` (`ProductDetail.vue` does). Hide it behind the flag, or ship messaging
- [ ] **`omaykan.com/shop/<slug>` shows the general homepage** — the main site's nginx has no `/shop/` route, so the path falls through to `index.html`. Low exposure, because links use the subdomain, but `storefrontUrl()` falls back to this path for a slug that cannot be a subdomain. Fix: `location /shop/ { try_files /shop.html =404; }`. It must be a plain prefix, not `^~`, so the `/shop/<slug>/checkout` regex still wins
- [ ] **`omaykan.com/customers` returns 404** — nginx maps it to `customers.html`, which no build contains. Nothing links to it. Remove the line or build the page

### Account features that promise things that don't exist

Checked in the code; the audit could not open these pages signed out.

- [ ] **Store credit** — always ₱0, yet the page says "Credit comes off your total automatically at checkout". Checkout applies no credit and there is no backend for it. Hide the page or build a ledger
- [ ] **Gift cards** — codes are saved only in the browser (`sf_gift_cards`) and labelled "applies at checkout". Checkout never reads them and there is no backend. Hide the page or build redemption
- [ ] **Refer a friend** — the code is a hash of the email, made in the browser. The page says "their first order carries your code", but nothing reads `?ref=` and there is no backend for it, so no referral is ever recorded. Hide the page or build tracking
- [ ] **Help, Privacy and Terms all link to `/about`** — in `MerchantHeader.vue` and `MerchantFooter.vue`. There are no privacy-policy or terms pages
- [ ] **Shopper homepage tab title reads "The POS for Modern Hospitality"** — `index.html` serves the shopper storefront under the seller pitch's title, and `/shop/<slug>` gets the same title

### Order process — seller confirms and sets the delivery fee

The intended flow (2026-09-22): the customer checks out; the seller confirms the
order and sets the delivery fee; the customer is told the final amount by email
and in the account page.

- [ ] **Seller confirm step** — an online order goes straight to `order_status = 'preparing'` (`OnlineOrderController`); there is no awaiting-confirmation state and no confirm action in `SellerOrderController`
- [ ] **Seller sets the delivery fee** — today `DeliveryQuoter` sets it from distance when the order is placed, and that is what is charged. Needs an endpoint, the till's Orders page, and the seller Android app
- [ ] **"Final amount" email to the customer** — the only email is `OnlineOrderConfirmationMail`, sent at checkout with the automatic fee and a subject that already says "confirmed". Nothing is sent after that
- [ ] **Final amount in the account page** — order status already updates live on the `order.{id}` channel; the account page needs a "waiting for the shop" state and the confirmed total
- [ ] **Checkout copy and the Android checkout** — both web and Android show an automatic fee as if it were final. Web's fine print already says "the shop confirms prices and the delivery fee", which is true only once this ships
- [ ] **Decide: must the customer accept the final amount** before the shop proceeds, or is the email a notice?
- [ ] **Decide: fee shown at checkout** — an estimate (a range, "confirmed by the shop") or none until the shop sets it?
- [ ] **Decide: do pickup orders need the confirm step?** — there is no fee to set

### Not covered yet

- [ ] **Signed-in audit** — the till (`/app`), rider, platform admin, support inbox, the shopper account pages, and placing an order, plus both Android apps. Needs a test shopper account and a test seller account

## Analytics

- [ ] **Something reads the telemetry** — `trackAppEvent` events are flushed to the backend and never consumed. No PostHog, no report ([feature-audit.md §3.5](./feature-audit.md), [analytics.md](./analytics.md))

## Engineering

- [ ] **Frontend component, Pinia store and sync-outbox tests** — `apps/web/test/` has 8 spec files of pure-logic tests only
- [ ] **Shared Android module** — `StaticMap`, the theme, `ApiCaller` and the Google OAuth flow are copied three times across `:app`, `:seller` and `:rider`. The three baseline profile modules added 2026-09-20 repeat a managed-device block as well, though those have to stay separate projects — a profile is a list of methods in one APK
- [ ] **Android CI** — releases are built by hand
- [ ] **Android baseline profiles recorded and committed** — added 2026-09-20. Three test modules (`:baselineprofile`, `:baselineprofile-seller`, `:baselineprofile-rider`) are wired into the three apps, along with `profileinstaller` and the two root-build repairs the synthesised `nonMinifiedRelease` variant needs. `:app`'s profile was recorded 2026-09-20 (29,832 rules, plus a startup profile) and is **uncommitted**; the seller and rider profiles are still to be recorded. Ticks when all three are in the repository — a release packages what is committed and generates nothing itself, so an uncommitted profile is no profile. Recording one needs a rooted AOSP emulator, ~8 GB free disk and enough memory for it to be the only VM on the machine — a second emulator running alongside killed three attempts. See [baselineprofile/README.md](../apps/mobile-android/baselineprofile/README.md)
- [ ] **`RiderAvatar` drops the caller's modifier when a rider has no photo** — found 2026-09-20. The no-photo early return calls `Avatar(...)` without passing `modifier`, so padding, a size override or a layout weight from the call site vanishes on exactly the common case, while the photo path applies them. Predates the change that surfaced it ([RiderAvatar.kt](../apps/mobile-android/rider/src/main/java/com/omaykan/rider/core/designsystem/RiderAvatar.kt))
- [ ] **Relax API throttles under `APP_ENV=local`** ([e2e-findings.md §5](./e2e-findings.md))

## Operations

- [ ] **Monitoring** — nothing alerts if `omaykan-queue` or `omaykan-reverb` stops ([deployment.md §6](./deployment.md))
- [ ] **Automated backups** — every dump so far was taken by hand before a deploy
- [ ] **Off-box backups** — backups sit on the same disk as the database
- [ ] **Staging environment** — every deploy goes straight to production
- [ ] **Clean up nginx `sites-enabled`** — two stray `omaykan.bak-*` copies are loaded
- [ ] **Production schema drift** — orphaned `platform_audit_logs` and `orders.payment_confirmed_by_role` ([deployment.md](./deployment.md))
- [ ] **Stock the empty shop** — `colewan-market` had 0 products at the last audit
- [ ] **A real rider end to end in production** — at the last audit, the `riders` table was empty

## Documentation that is stale

- [ ] [plan.md](./plan.md) — still describes Firestore as live, says Echo isn't installed, the backend isn't deployed and there's no outbox ([feature-audit.md §6](./feature-audit.md))
- [ ] [feature-log.md](./feature-log.md) — "backend not yet deployed"
- [ ] `documentation/apps/mobile/README.md` — still describes deleted Capacitor shells
- [ ] [feature-audit.md](./feature-audit.md) §2.4 — says the storefront is single-tenant. It isn't any more (see below)
- [ ] [rider/README.md](../apps/mobile-android/rider/README.md) — lists earnings and customer ratings as missing. Both now exist
- [ ] README "Status" — still says "no earnings or payout record" for riders

---

## Already done — docs still say otherwise

These are here so nobody re-opens them.

- [x] Multi-vendor storefront — `GET /api/stores` directory, `?shop=` and `<slug>.omaykan.com` (2026-08-28 → 2026-09-16)
- [x] Rider earnings totals — `RiderEarningsController`
- [x] Customer ratings of riders — `RiderRatingController`, `rider_ratings` table (2026-09-10)
- [x] Rider live location — `RiderPositionController` plus a foreground share service
- [x] Staff sign-in as a person (password or Google), replacing device pairing
- [x] Promo box removed, seeded roles fixed, dead pages deleted, "under ₱100" shelf filtered (2026-08-27)
- [x] Double settlement, POS-listed products missing online, and the uncategorised-product 500 — all fixed with regression tests (2026-08-27)
