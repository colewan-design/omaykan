# Omaykan

Commission-free local commerce for Philippine merchants.

Delivery aggregators take 20–30% from the merchant, squeeze the rider's per-drop rate, and leave the customer paying an inflated menu on top of service and delivery fees — a ₱30 Coke listed at ₱65. This project gives a local merchant their **own** ordering channel instead: their storefront, their customers, their prices, with a POS behind it that keeps catalog, inventory, and orders in one place.

**The promise, three lines:**

- **0%** commission from the merchant — flat monthly subscription, that's all we ever take
- **100%** of the delivery fee to the rider
- **In-store prices** for the customer, with honest totals

## Documentation

| Doc | What it covers |
|---|---|
| [positioning.md](documentation/positioning.md) | What this is, who it's for, the price-parity covenant, business model, beachhead, risks. **Start here.** |
| [plan.md](documentation/plan.md) | The phased build sequence. Stale in places — see feature-audit.md §6 |
| [mobile-plan.md](documentation/mobile-plan.md) | The native Android customer app (Kotlin) — API contract, realtime, phases. Re-verified against `main` 2026-08-28 |
| [google-sign-in.md](documentation/google-sign-in.md) | Sign in with Google — the Google Cloud console setup, the three OAuth clients, account linking, and the Android PKCE flow |
| [feature-audit.md](documentation/feature-audit.md) | What is missing, unfinished, or inert. Audited against the code and the live database |
| [e2e-findings.md](documentation/e2e-findings.md) | A ten-run pass of seller → customer → rider → completion, what failed, and what to improve |
| [deployment.md](documentation/deployment.md) | The VPS, the deploy procedure, rollback, and why production's schema differs from `main` |
| [feature-log.md](documentation/feature-log.md) | Page-by-page status of what is built. Stale in places — see feature-audit.md §6 |
| [backend-multistore-sync.md](documentation/backend-multistore-sync.md) | Multi-store tenancy model and sync contract |
| [design.md](documentation/design.md) | Visual language and design tokens |
| [analytics.md](documentation/analytics.md) | Reporting and telemetry plan |

## Structure

```
/packages
  /core            # Vue POS app - pages, components, stores, the Reverb order channel
  /shared          # Types and shared domain logic
  /data            # Browser repository: IndexedDB with a localStorage mirror,
                   # syncing to the Laravel API over /api/sync/*
/apps
  /web             # PWA: merchant till + customer storefront + rider + platform admin
/backend           # Laravel 12 + PostgreSQL + Reverb — the backend
```

The marketing site is not a separate app — it is `apps/web/src/landing`, built into
`landing.html`.

There is no mobile app in the tree. The two Capacitor shells were deleted — neither
built, and both reached for the Firebase SDK that the Laravel migration removed. A
native Kotlin app replaces them and has not landed yet — see
[mobile-plan.md](documentation/mobile-plan.md).

## Running

Two halves, both needed: the Laravel API and the Vite dev server.

### From a fresh clone

```bash
npm install                        # workspaces: root, apps/web, packages/*
cp apps/web/.env.example apps/web/.env

cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate
php artisan db:seed                # demo tenant — see below
php artisan storage:link           # rider licence and plate photos
```

`db:seed` is `firstOrCreate` throughout, so it is safe to re-run. On its own it
leaves the minimum the test suite asserts against: org `demo-coffee` with store
`main`, pairing code **123456**, one product, and an owner signing in as
`admin` / `password`.

For something you can actually browse, follow it with the demo sellers — one
per business mode, each with store `main`, an owner whose password is
`password`, and the shelf its mode declares:

```bash
php artisan db:seed --class=DemoSellerSeeder
```

| Organization | Mode | Pairing code | Sign in as | Products |
| --- | --- | --- | --- | --- |
| `demo-coffee` | coffee-shop | **123456** | `admin` | 32 |
| `baguio-fresh-market` | grocery | **234567** | `grocery` | 475 |
| `session-road-grill` | restaurant | **345678** | `restaurant` | 24 |
| `polished-nail-lounge` | nail-salon | **456789** | `salon` | 21 |

Staff sign-in takes the **username**, not the email — see
`POST /api/staff-sessions`. The salon answers `store-codes/resolve` with a 409
by design: `ONLINE_MODES` keeps appointment businesses out of the cart.

The catalog is not written into the seeder. It is read from
`backend/database/seeders/data/demo-catalog.json`, which is projected out of
`packages/shared` — the TypeScript stays the source of truth for what the demo
store sells:

```bash
node scripts/export-demo-catalog.mjs   # after changing demoProducts
```

Products whose catalog entry names no stock quantity — salon services, mostly —
are seeded with `track_inventory` off, because the storefront drops any tracked
product sitting at zero.

### Which shop the landing page shows

`GET /api/stores` is the public shop directory, and the landing page browses it
under "Shops near you". It lists only what a customer could order from: an
active store, an unsuspended org, a business mode in `ONLINE_MODES`, and at
least one product that mode can sell — a shop with an empty shelf is a dead
click, so it is left out rather than listed as unavailable. It takes `?q=` to
search name, address and business type, and `?lat=&lng=` to sort nearest first
(both coordinates or neither; half a pair is a 422).

Picking a shop loads `?shop=<orgSlug>`, which `landing/main.ts` resolves through
the directory and hands to `setStorefrontContext` **before mount** — the catalog
composable loads once on first use, so setting it later fetches the wrong shelf
and never corrects it. Without the parameter the build-time
`VITE_POS_ORGANIZATION_SLUG` tenant stands, so a single-shop install is
unchanged.

"Enter your address" in the header stores a delivery location in `localStorage`
(`sf_delivery_location`) and re-sorts the directory by distance. The typed
address and the map pin are separate on purpose: **no geocoder is configured
anywhere in this project**, so typed text cannot become coordinates. The pin
comes from the browser's own geolocation, the same way checkout already gets
one, and either half can exist without the other.

Then join the two halves. In `apps/web/.env`, `VITE_API_BASE` and
`VITE_ONLINE_ORDER_API_BASE` point at the Laravel origin — `http://127.0.0.1:8000`
locally, blank only when the API is served from the same host as the app. The
`VITE_REVERB_*` values must match the backend's own `REVERB_*`: a blank
`VITE_REVERB_APP_KEY` switches realtime off and the app falls back to polling.
Generate the `REVERB_*` secrets per environment — never reuse them.

The backend runs **PostgreSQL 16** in every environment, local included. Install
the same major the VPS runs rather than substituting another engine: a local
SQLite database takes a single writer and deadlocks against `queue:work`, which
looks exactly like an application bug and is not one.

On Windows, `winget install PostgreSQL.PostgreSQL.16`, then uncomment
`extension=pdo_pgsql` in `php.ini` — the DLL ships with XAMPP but is off by
default, which is why the toolchain can look MySQL-shaped at a glance.

The only other database here is the **SQLite in-memory** one `phpunit.xml` uses
for the test suite. That is deliberate — it is what lets a fresh clone run
`php artisan test` with no database setup at all. It is also blind to anything
PostgreSQL-specific, so there is a second lane that runs the same tests against
a real database:

```bash
createdb -O omaykan omaykan_test        # once
composer test:pgsql                     # php artisan test -c phpunit.pgsql.xml
```

Use it for anything touching queries, casts or migrations. It exists because a
regression test once passed on SQLite while the bug it covered would have 500'd
the live storefront — see [e2e-findings.md §7.2](documentation/e2e-findings.md).

### Day to day

```bash
npm run dev:web      # merchant POS + storefront on :5173
npm run build:web    # vue-tsc, then vite build into apps/web/dist
```

```bash
cd backend
php artisan serve           # :8000
php artisan queue:work      # required — broadcasts are queued
php artisan reverb:start    # websocket server on :8080
php artisan test
```

An operator account for `/platform-admin` is made on the server rather than
through any endpoint — that is deliberate, and it is why there is no
password-reset route:

```bash
php artisan platform-admin:create you@example.com
```

### Static hosting

`apps/web` builds to multiple HTML entries, so whatever serves `apps/web/dist`
needs these rewrites (nginx `try_files`, or equivalent):

| Path | Serves |
| --- | --- |
| `/` | `index.html` |
| `/landing` | `landing.html` |
| `/app`, `/app/*` | `app.html` |
| `/about` | `about.html` |
| `/signup` | `signup.html` |
| `/account` | `account.html` |
| `/rider` | `rider.html` |
| `/platform-admin` | `platform-admin.html` |
| `/support-inbox` | `support-inbox.html` |

`/api/*` proxies to the Laravel backend. The client build also needs the
`VITE_API_BASE` / `VITE_REVERB_*` / `VITE_POS_*` variables set at build time — see
`apps/web/.env.example`.

## Status

Working: merchant POS (17 pages, four business modes, shifts with cash reconciliation, full-order voids, inventory), the customer storefront on web, store pairing by code, signup, and the platform admin dashboard.

The rider side is now built too: riders apply at `/rider` with their licence and
plate — number and photo of each — and can do nothing until an operator has
approved them from the **Riders** tab of `/platform-admin`. Once approved they
see every unclaimed delivery across all shops, take one, and move it from
pickup to the door. The licence and plate photos are held on the private disk
and are readable only by the operator, never by a shop or a customer.

Customer payment is **COD only** — cash at handover, no online collection, by design.

Not built: loyalty, and discounts. The merchant subscription is a ₱499 placeholder collected by manual GCash transfer.

See [feature-log.md](documentation/feature-log.md) for audited detail and [plan.md](documentation/plan.md) for the phase order. Phase 0's migration off Firestore has landed in the code; verifying BIR requirements has not, and it still blocks charging anyone.

## Backend

Laravel 12 + PostgreSQL, self-hosted on a VPS. Realtime runs on **Laravel Reverb**, with broadcasts dispatched through the queue — an order placed on the storefront is written, queued, and pushed to the merchant's `store.{id}` channel, so `queue:work` is not optional.

Firebase is gone: the Firestore sync layer, the `api/*.ts` handlers, their Node
runner, and the Cloud Functions were all deleted. The Laravel API is the only
backend.
