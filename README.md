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
  /core            # Vue POS app - pages, components, stores
  /data            # Repository + Firestore sync layer
  /shared          # Types and shared domain logic
/apps
  /web             # PWA: merchant till + customer storefront + platform admin
  /mobile          # Capacitor Android app (customer storefront)
  /mobile-admin    # Capacitor Android app (merchant)
/backend           # Laravel 12 + PostgreSQL + Reverb (the target backend)
/api               # HTTP handlers (legacy, being retired) — served by /server
/server            # Self-hosted Node runner for /api on the VPS
/firebase          # Firestore rules and schema (legacy)
```

The marketing site is not a separate app — it is `apps/web/src/landing`, built into
`landing.html`.

## Running

```bash
npm install
npm run dev:web      # merchant POS + storefront
npm run build:web
```

`vite dev` does not serve `/api/*.ts`. To exercise those locally, run the same
self-hosted runner the VPS uses, with Firebase Admin credentials in the environment:

```bash
cd server
npm install && npm run build
npm start            # listens on 127.0.0.1:3005, /api/health for a liveness probe
```

### Static hosting

`apps/web` builds to multiple HTML entries, so whatever serves `apps/web/dist`
needs these rewrites (nginx `try_files`, or equivalent):

| Path | Serves |
| --- | --- |
| `/` | `index.html` |
| `/landing` | `landing.html` |
| `/app`, `/app/*` | `app.html` |
| `/store`, `/store/*` | `store.html` |
| `/signup` | `signup.html` |
| `/platform-admin` | `platform-admin.html` |

`/api/*` proxies to the runner above. The client build also needs the
`VITE_FIREBASE_*` / `VITE_POS_*` variables set at build time — see `apps/web/.env`.

## Status

Working: merchant POS (17 pages, four business modes, shifts with cash reconciliation, full-order voids, inventory), customer storefront on web and mobile, store pairing by code, signup, and the platform admin dashboard.

Customer payment is **COD only** — cash at handover, no online collection, by design.

Not built: the rider side, loyalty, and discounts. The merchant subscription is a ₱499 placeholder collected by manual GCash transfer.

See [feature-log.md](documentation/feature-log.md) for audited detail and [plan.md](documentation/plan.md) for the phase order. Phase 0 — migrating off Firestore onto the Laravel VPS backend and verifying BIR requirements — blocks charging anyone.

## Backend

Laravel 12 + PostgreSQL, self-hosted on a VPS. Realtime runs on **Laravel Reverb**, with broadcasts dispatched through the queue.

```bash
cd backend
php artisan serve
php artisan queue:work      # required — broadcasts are queued
php artisan reverb:start    # websocket server
```

Firestore and the handlers in `/api` are the legacy path, still live and being retired.
