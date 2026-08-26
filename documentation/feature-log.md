# Feature Log

Status of what is actually built, audited against the codebase on **August 23, 2026**.

For what the product is and why, see [positioning.md](./positioning.md). For the build sequence, see [plan.md](./plan.md). This document is descriptive only — it records what exists, not what should.

---

## Surfaces

| Surface | Location | State |
|---|---|---|
| Merchant POS / back office | `packages/core` → `apps/web` (`/app/`) | Working, broad |
| Customer storefront | `apps/web/src/landing` + `apps/web/src/commerce` | Working — see §3 |
| Android apps | — | To be written natively in Kotlin; the Capacitor wrappers are deleted |
| Onboarding / signup | `apps/web/src/onboarding` | Working, with placeholder pricing |
| Platform admin (superadmin) | `apps/web/src/platform-admin` | Working — named operator accounts, audit log |
| Landing site | `apps/web/src/landing` | Working |
| **Rider** | — | **Does not exist** |

Stack in use: Vue 3, Vite, TypeScript, Pinia, Vue Router, Capacitor 8. Roughly 30k lines across `packages/` `apps/` `api/`.

---

## 1. Merchant POS

Seventeen routed pages (`packages/core/src/app/router.ts`), permission-gated per role:

`/dashboard` · `/sales` · `/orders` · `/products` · `/customers` · `/suppliers` · `/employees` · `/inventory` · `/tables` · `/reports` · `/integrations` (owner) · `/register` · `/settings` · `/diagnostics` (owner) · `/auth`

> `AnalyticsPage.vue` exists in the codebase but is **not routed** — it is orphaned. Reporting lives on `ReportsPage.vue` at `/reports`.

### Register / checkout

- Four business modes with mode-specific product grids and order panels: `coffee-shop`, `grocery`, `restaurant`, `nail-salon`
- Search by name, SKU, or barcode; category tabs; weighted items; out-of-stock and low-stock handling
- Cart with quantity stepper, line removal, clear-cart
- Order types (dine-in / takeaway), table number for restaurant mode
- Payment sheet: cash, card, e-wallet; numeric keypad; change calculation
- Tax calculation and live subtotal/tax/total
- Customer attachment to a sale (guest default)
- **Track Order panel** — live status strip for online orders arriving from the storefront
- **Settle Online Payment sheet** — takes payment at the register for an order placed online

### Orders and voids

- Order list with ticket number, time, payment method, items, mode badge, total
- Order status transitions (`updateOrderStatus`)
- **Full-order void** — reverses the whole sale, restores inventory via adjustment records, excludes from revenue, emits `order_voided`, owner/admin gated
- **No partial or line-item refund.** Deliberate and documented in `pos.ts:618`

### Shifts and cash

- Open shift with opening cash float
- Cash movements (pay-in / pay-out with reason)
- Close shift with counted cash
- **Cash reconciliation** — counted-cash variance per closed shift, surfaced in Reports
- Shift history

### Reports (`/reports`)

Report Summary · Business Mode Totals · Payment Breakdown · Recent Orders · Shift Snapshot (live cash position + movements) · Shift History with variance · Top Selling Lines, over a selectable range.

> Not formally an X or Z report. The page itself describes its format as "closer to an X report than a live dashboard."

### Catalog, inventory, and people

- Products and categories: full CRUD, images, SKU, barcode, tax rate, unit label, stock quantity, low-stock threshold, per-mode assignment
- Inventory adjustments typed as `sale` / `restock` / `manual_correction`, with reorder marks against suppliers
- Low-stock alerts (toast + `low_stock_alert` event)
- Customers, suppliers, restaurant tables: full CRUD
- Employees: staff accounts, role definitions with per-page permissions, `canManageStaff` flag, owner-only escalation guards

### Settings

Business mode, business name and image, **pairing code** (the customer-facing store code), sync mode, appearance/theme, total animation toggle, telemetry toggle.

---

## 2. Android apps — to be written

Both Capacitor wrappers (`apps/mobile`, `apps/mobile-admin`) were deleted on
2026-08-26; the Android apps are being rewritten natively in Kotlin. What they
did that the new apps will need to do again: the customer entered a **store
code** at first launch to resolve org + store, and checkout supported pickup or
delivery with cash or GCash.

The release signing key for the old builds still exists and is not in this
repo — see the deletion commit for where it went. Publishing the Kotlin apps
under the same package names (`com.omaykan.storefront`, `com.omaykan.app`)
requires it.

## 3. Customer storefront — web (`apps/web/src/landing` + `apps/web/src/commerce`)

The marketing site and the shop are one document. `apps/web/src/storefront` is
gone; `landing.html` now has five faces, and the URL says which is showing:
the front page, an aisle (`?category=`), a product (`?product=`), checkout
(`?checkout=1`) and a placed order (`?order=<id>`). The shared pieces — cart,
catalog, delivery address, customer session, API client — live in
`apps/web/src/commerce` and are used by the account portal too.

Catalog, category nav, search, product detail, cart drawer, delivery-address
filtering, checkout, order confirmation with live status, customer accounts.

- **Delivery address** is set in the header and filters the shelves: the API
  answers with what a branch within 15 km of that address can actually send,
  and says plainly when nothing reaches it (`GET /api/storefront/catalog`
  with `lat`/`lng`). Placed from the device's location, one of the customer's
  saved addresses, or a picked service area — there is no geocoder, so a
  free-typed street alone filters nothing and says so.
- **Checkout** supports pickup or delivery, as a guest or signed in, and is
  **cash only** — there is no method to choose, and no gateway anywhere. The
  mobile storefront still offers the GCash preference (§2); the web one has
  stopped asking.
- **Payment confirmation** is manual and comes from either side. The seller
  settles it from the till (`POST /api/seller/online-orders/{id}/settle-payment`,
  the Settle Online Payment sheet); the customer marks it paid from their order
  page or their order history (`POST /api/online-orders/{id}/confirm-payment`,
  public by the same unguessable id as tracking). Whichever comes first wins,
  the order records which side said so (`payment_confirmed_by_role`), and both
  write the same row into the merchant's cash ledger. This matters most for a
  delivery: the cash goes to a rider at the door and the till never sees it.
- **Order status** is polled every 20s from `GET /api/online-orders/{id}`,
  which is public by unguessable id — so the confirmation URL is a link the
  customer can forward. Reverb already broadcasts `OrderStatusChanged`, but
  no Echo client is wired up in `apps/web` yet.
- No wishlist.

---

## 4. Platform, onboarding, and billing

- **Signup** (`api/signup.ts`) creates the organization, store, owner account, and pairing code, plus a subscription document at `status: 'pending_verification'`
- **Plan**: `standard-monthly`, **₱499/month — placeholder**, paid by manual GCash transfer to a placeholder number (`pricingConstants.ts`, mirrored in `api/signup.ts`; both carry TODOs)
- **No payment gateway, no recurring billing, no automated verification, no license enforcement.** Nothing currently stops or charges a non-paying merchant
- **Platform admin** — operator portal on its own guard (`auth:platform`): dashboard, tenant list and detail, subscription verification, suspend/delete, rider review, audit log, operator management
- **Integrations page** — connection settings and a readiness checklist per register
- Staff store-code resolution (`api/resolve-staff-store-code.ts`) and staff creation (`api/staff-create.ts`)

---

## 5. Data, persistence, and sync

`PosRepository` (`packages/data/src/index.ts`) is the single interface the app talks to — catalog, orders, online orders, voids, settlement, customers, tables, suppliers, reorder marks, shifts, cash movements, inventory adjustments, settings, users, roles, sessions, telemetry.

- **Local persistence: IndexedDB, mirrored into `localStorage`.** Not SQLite. The `DataStore` interface here is a simple key/value `read`/`write`, not the SQL interface described in [plan.md §4](./plan.md)
- **Sync**: `createFirebaseSync` (~1k lines) against Firestore, gated by the `syncMode` setting (`local-only` / `online-sync`)
- **No outbox table.** Sync is direct, not the queued-outbox pattern the plan describes
- **Backend, today**: the HTTP handlers in `api/` against Firestore, self-hosted on the VPS via `server/`. `functions/src/index.ts` is dead
- **Backend, target**: `backend/` — Laravel 12 + PostgreSQL on a VPS, with models, sync/shift/staff controllers, **queues, and Reverb broadcasting** (`OrderPlaced`, `OrderStatusChanged`, channel auth in `routes/channels.php`). Not yet deployed, and nothing on the Vue side subscribes to it yet — see [plan.md §3a](./plan.md)

---

## 6. Telemetry

Events captured: `cart_search_used`, `product_added`, `cart_cleared`, `payment_sheet_opened`, `payment_method_selected`, `order_completed`, `order_voided`, `settings_saved`, `low_stock_alert`.

Stored locally with device ID and app version; surfaced on the Diagnostics page. No Sentry, no PostHog, no backend event pipeline.

---

## 7. Hardware

- **Barcode**: text-entry field ("Scan or enter barcode") — an HID/Bluetooth scanner works as a keyboard with zero code. **No camera scanning**; `@capacitor-mlkit/barcode-scanning` is not installed
- **Receipt**: `receipt.ts` builds a receipt and `printer.ts` detects capabilities (secure context, WebUSB, Web Bluetooth availability, granted devices). **Browser printing only — no ESC/POS command generation, no printer driver**
- **Cash drawer**: none
- **Capacitor plugins installed**: `app`, `browser`, `core`, `haptics`. No SQLite plugin, no scanner plugin, no printer plugin

---

## 8. Gaps

Mapped to the phases in [plan.md §6](./plan.md).

**Phase 0 — blocking revenue**
- Firebase still on the free **Spark** plan
- Two backends in the repo, neither retired
- BIR requirements unverified
- Subscription price is a placeholder with no cost basis

**Phase 1 — the front door**
- Store entry is a **typed code**, not a shareable per-merchant URL
- No QR generation, no link kit, no unauthenticated public merchant page

**Phase 2 — prove the saving**
- No price-parity enforcement or "in-store price" badge
- No price-comparison view
- No merchant savings counter, no ROI calculator

**Phase 3 — money**
- Customer payment is **COD by decision** ([plan.md §4a](./plan.md)) — no gateway is planned. What is missing are the COD safeguards: no order-confirmation or phone-verification step, no no-show tracking, no change-due display, no reconciliation of rider-collected cash against a shift
- No recurring billing, no automated subscription verification, no grace-window logic
- No minimum order value for delivery

**Phase 4 — riders**
- Nothing exists. No rider record, assignment flow, earnings view, or delivery fee model

**Phase 5 — merchant growth**
- No loyalty or points
- No discount codes (**no discount support anywhere in the codebase**)
- No customer export, no Messenger/SMS broadcast, no repeat-order nudges

**Phase 6 — density and scale**
- Single-store-per-org gap still open
- No density reporting

**Other**
- Partial/line-item refunds (deliberate omission — decide whether it stays)
- No formal X/Z report
- No product cost or margin reporting
- No printable/exportable report output
- SQLite migration not started; no outbox
- Web storefront lagging mobile (§3)
- `AnalyticsPage.vue` orphaned — route it or delete it

---

## Known documentation drift

- [plan.md §4](./plan.md) describes a SQL `DataStore` and an outbox sync pattern. Neither is implemented; the real interface is key/value over IndexedDB and sync is direct to Firestore.
- [backend-multistore-sync.md](./backend-multistore-sync.md) specifies the Laravel + PostgreSQL model, which is built but not the live path.
