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
| [plan.md](documentation/plan.md) | Current state of the code and the phased build sequence |
| [feature-log.md](documentation/feature-log.md) | Page-by-page status of what is actually built, audited against the code |
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
native Kotlin app replaces them and has not landed yet.

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

`db:seed` is `firstOrCreate` throughout, so it is safe to re-run. It leaves an
org `demo-coffee` with store `main`, pairing code **123456**, one product, and a
merchant login of `admin@example.com` / `password`.

Then join the two halves. In `apps/web/.env`, `VITE_API_BASE` and
`VITE_ONLINE_ORDER_API_BASE` point at the Laravel origin — `http://127.0.0.1:8000`
locally, blank only when the API is served from the same host as the app. The
`VITE_REVERB_*` values must match the backend's own `REVERB_*`: a blank
`VITE_REVERB_APP_KEY` switches realtime off and the app falls back to polling.
Generate the `REVERB_*` secrets per environment — never reuse them.

The backend targets **PostgreSQL**, and that is what the VPS runs. For local work
`DB_CONNECTION=sqlite` is enough — no migration uses Postgres-specific SQL, and
the suite passes on it.

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
