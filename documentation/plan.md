# Build Plan — Commission-Free Local Commerce Platform

> The merchant's own sales channel: their storefront, their customers, their prices — with a POS as the operational engine behind it.
> **Read [positioning.md](./positioning.md) first.** This document is the build sequence; that one is the reason for it.
> Last updated: August 23, 2026. Supersedes the original June 2026 "offline-first POS" plan.

---

## 1. Principles

- **The storefront is the product; the POS is the moat.** A storefront alone is replaceable by Facebook Shops or a Google Form. One catalog, one inventory count, walk-in and online orders in one queue, one end-of-day number — that is what a merchant cannot walk away from. We sell the storefront and retain with the POS.
- **One product record, one price.** Storefront price and register price are the same field. Price parity is enforced by the data model, not by policy. This is the covenant in [positioning.md §4](./positioning.md).
- **No install between a customer and an order.** The customer's entry point is a URL, never an app download and never a typed code.
- **Offline-first for the merchant.** The register is the source of truth for its own transactions and never blocks on the network. A dropped connection must never lose a sale — a till that stops when the internet does is worthless in a PH sari-sari store.
- **Online-first for the customer.** The storefront is a web page; it does not need offline support.
- **One codebase, two targets.** A single Vue 3 application ships as an installable web app (PWA) and an Android app (Capacitor). The PWA is also the big-screen till.
- **One engine, many businesses.** Business-type config drives the differences (grocery / coffee shop / restaurant / nail salon) rather than forked apps.

---

## 2. Where the code actually is today

Honest current state, so the phases below start from reality.

**Built and working:**

- **POS core** (`packages/core`) — Register, Orders, Products, Inventory, Customers, Employees, Suppliers, Tables, Reports, Settings, plus a shift panel. Four business modes with mode-specific product grids and order panels.
- **Customer storefront** — on web (`apps/web/src/storefront`) and mobile (`apps/mobile/src/storefront`). Catalog, search, cart, wishlist, pickup/delivery, GCash preference, order history.
- **Store pairing** — customer enters a **store code** to resolve org + store (`api/resolve-store-code.ts`). One shared mobile app, not per-merchant builds.
- **Platform admin** — superadmin dashboard for managing stores and owner accounts (`apps/web/src/platform-admin`, `api/platform-admin.ts`).
- **Landing site** (`apps/web/src/landing`), **onboarding/signup** (`api/signup.ts`), **staff accounts** (`api/staff-create.ts`).
- **Order flow into the register** — online orders land and surface on the Register's Track Order panel.

- **Signup and subscription record** (`api/signup.ts`) — creates org, store, owner, pairing code, and a subscription at `status: 'pending_verification'` on a `standard-monthly` plan. Price is a **₱499 placeholder** collected by manual GCash transfer to a placeholder number; there is no gateway, no recurring billing, and no enforcement against non-payers.

**Not built:** rider side (entirely), loyalty, discounts, customer list/broadcast, price comparison, savings counter, automated subscription billing. Online payment collection is not built and is **not planned** — see §4a.

Full page-by-page status is in [feature-log.md](./feature-log.md).

**Backend: decided — Laravel + PostgreSQL, self-hosted on a VPS.** Firestore is being retired. See §3a for what that costs and §6 Phase 0 for the migration.

| Backend | State |
|---|---|
| **Laravel + PostgreSQL** (`backend/`) | **The target.** Laravel 12, Sanctum, models, sync/shift/staff controllers, the schema in [backend-multistore-sync.md](./backend-multistore-sync.md), plus queues and Reverb (§3a). Not yet deployed. |
| **Firebase (Firestore) + the `api/*.ts` handlers** (self-hosted via `server/`) | Still the live path today. To be migrated off and deleted. |
| **Firebase Functions** (`functions/src/index.ts`) | Dead. Delete with the rest. |

Self-hosting removes the Spark-plan ceiling entirely — the blocker that shaped Phase 0 — and replaces it with VPS operations work.

Two customer storefronts exist and have **drifted apart**: the mobile one supports pickup/delivery, cash/GCash, product detail, and order history; the web one is pickup-only with no payment choice, despite the API already accepting both. Worth reconciling in Phase 1.

---

## 3. Stack

| Layer | Choice | Notes |
|---|---|---|
| UI core | Vue 3 + Vite + TypeScript | Single codebase for every surface |
| State | Pinia | Cart, session, shift, settings |
| Web | PWA (service worker + installable) | Merchant till and customer storefront |
| Mobile shell | Capacitor | Android-first; iOS optional (App Store only) |
| Backend | Laravel 12 + PostgreSQL on a **VPS** | Sanctum auth; replaces Firestore |
| Realtime | **Laravel Reverb** (WebSockets) | Replaces Firestore listeners — see §3a |
| Queue | Laravel queue, `database` driver | Broadcasts and background work; Redis if load demands |
| Legacy backend | Firebase / Firestore + `api/*.ts` on `server/` | Live today, being retired |
| On-device DB (merchant) | IndexedDB (mirrored to `localStorage`) today; SQLite is the target | See §4 |
| Customer payments | **COD only** — settled at handover | No gateway, deliberately. See §4a |
| Merchant subscription | Manual GCash transfer | Placeholder; needs a real collection method |

---

## 3a. Realtime — queue + Reverb

Firestore gave realtime for free: the register's Track Order strip and the customer's order-status page were both live because they held listeners on a Firestore collection. Self-hosting means providing that ourselves. **Laravel Reverb** (first-party WebSocket server) plus the **queue** replaces it.

### What is wired today

| Piece | Location | Purpose |
|---|---|---|
| `OrderPlaced` | `backend/app/Events/OrderPlaced.php` | New order arrived at a store — drives the Track Order strip |
| `OrderStatusChanged` | `backend/app/Events/OrderStatusChanged.php` | Status moved; carries the previous status |
| Channel auth | `backend/routes/channels.php` | `store.{storeId}` and `orders.{orderId}`, gated on `StoreMembership` |
| Dispatch point | `SyncController::applyOrderEvent()` | Fires inside `DB::afterCommit()` |
| Config | `config/reverb.php`, `config/broadcasting.php` | `BROADCAST_CONNECTION=reverb` |

### Design decisions worth keeping

- **Events are `ShouldBroadcast`, not `ShouldBroadcastNow`.** The WebSocket push goes through the queue, so a slow or dead Reverb can never block the HTTP request that recorded a sale. This matters more here than in most apps: that request is a merchant's till.
- **Dispatch happens in `DB::afterCommit()`.** Sync ingestion runs inside a transaction; broadcasting before commit lets a listener race ahead and query a row that is not visible yet, or hear about an order a rollback then removed.
- **Payloads are deliberately thin** — ids, ticket number, status, total. The client refetches over the API. Keeps money and customer details off the wire and keeps the socket cheap.
- **The store channel is private**, authorized by store membership: one merchant must never hear another merchant's orders.
- **The customer's order channel is public but keyed on the order UUID.** Storefront customers have no account to authenticate with, so the unguessable id is the capability. Nothing sensitive rides it. If customer accounts ever land, make it private.

### Running it

```bash
php artisan reverb:start          # WebSocket server (port 8080 by default)
php artisan queue:work            # required — broadcasts are queued
```

Both are long-running daemons. On the VPS they need **supervisor** (or systemd) to keep them alive, and nginx must proxy the WebSocket upgrade to Reverb behind TLS. In production set `REVERB_HOST` to the public hostname with `REVERB_PORT=443` and `REVERB_SCHEME=https`, while `REVERB_SERVER_HOST=0.0.0.0` keeps the daemon bound locally.

### Still to do

- **Front-end subscription.** Nothing on the Vue side listens yet — Laravel Echo is not installed, and the register still gets online orders through Firestore. That lands with the migration.
- **A storefront order endpoint in Laravel.** `api/create-online-order.ts` is the only thing that creates customer orders today, and it writes to Firestore. Until it is ported, `OrderPlaced` only ever fires for orders arriving through device sync.
- **Presence channels** for "which staff are on the till" if that is ever wanted.

---

## 4. Offline-first storage (merchant side)

Today the app persists to **IndexedDB with a `localStorage` mirror**, behind a simple key/value `DataStore` (`read`/`write`), and syncs directly to Firestore when `syncMode` is `online-sync` — there is no outbox. The target below is not yet implemented; treat it as the destination, not a description of the code.

The target is real SQLite behind one interface, so the register survives a dead connection and a page reload mid-shift:

```ts
interface DataStore {
  query<T>(sql: string, params?: any[]): Promise<T[]>;
  exec(sql: string, params?: any[]): Promise<void>;
  transaction(fn: (tx: DataStore) => Promise<void>): Promise<void>;
}
```

| Target | Implementation |
|---|---|
| Web (PWA) | `wa-sqlite` + OPFS (or Dexie/IndexedDB for simplicity) |
| Mobile (Capacitor) | `@capacitor-community/sqlite` |

Sync should move to the **outbox pattern**: every sale and stock change is written locally with a client-generated UUID and appended to an outbox; a background task pushes to the API whenever a connection exists, with idempotent endpoints keyed on UUID so retries are safe. Catalog and price updates pull down by `updated_at`. Conflicts are near-nonexistent — the register owns its sales, the back office owns the catalog.

Storefront orders arrive through the server and are pulled into the register, which is why the customer side needs no offline story.

---

## 4a. Payments — COD only

**Customers pay at handover. Nothing is collected online.** Cash on delivery for delivery orders, cash on collection for pickup. Money moves directly between the customer and the merchant (or the rider carrying the bag) — it never passes through us.

The storefront's **cash / GCash toggle stays**, because GCash-at-handover is still COD: the customer pays on collection, by transfer instead of notes, and we process nothing. It saves the merchant from breaking change and the rider from carrying cash. The toggle records an intent; the register settles it.

This is already how the code behaves: there is no gateway anywhere in the repo, and the storefront's GCash toggle is a stated *preference* that the merchant settles at the register via the Settle Online Payment sheet. The decision makes that deliberate rather than a gap waiting to be filled.

Why it holds for now:

- **COD is the PH default.** Most customers of a sari-sari store or a home kitchen expect to pay at handover and many have no card at all. Requiring prepayment removes customers.
- **We never touch customer money.** No float, no settlement schedule, no chargebacks, no holding merchant funds — and no reason for anyone to wonder whether we skim. That matters for a platform whose entire pitch is that it does not take a cut.
- **No gateway fees.** A percentage cut to a processor on every order is the thing we are supposed to be removing.
- **It removes a whole compliance surface** while BIR questions are still open (see [positioning.md §9](./positioning.md)).

What COD costs us, and must be designed for in Phase 3:

- **Fake and no-show orders.** The merchant prepares food nobody collects. Mitigate with phone verification on first order, an order-confirmation step, and a per-customer no-show record.
- **Change handling.** The rider must know the exact amount due and carry change. Show change-due prominently.
- **Cash reconciliation across a rider.** Money collected in the field has to land against the shift — the shift and cash-movement model already exists to hang this on.

**Not COD:** the merchant's own subscription payment to us. That is a separate problem and still needs a real collection method (§7).

Revisit COD-only when order volume makes prepayment worth the fees and the compliance work — not before.

---

## 5. The three surfaces

| Surface | Who | State |
|---|---|---|
| **Register / back office** | Merchant and staff | Built |
| **Storefront** | Customer | Built — but entered by code, not link (Phase 1) |
| **Rider app** | Rider | **Not started** — Phase 4 |

Business-mode config drives merchant differences:

| Capability | Grocery / convenience | Coffee shop | Restaurant | Nail salon |
|---|---|---|---|---|
| Barcode-centric flow | Primary | Secondary | Secondary | Off |
| Weighted items | On | Off | Off | Off |
| Modifiers | Off | On | On | On |
| Tables / dine-in | Off | Off | On | Off |
| Appointments | Off | Off | Off | On |

Shared by all: cart, cash and e-wallet payment, order history, shifts, reporting, and the storefront.

---

## 6. Phases

Ordered by what unblocks revenue, not by what is most interesting to build.

### Phase 0 — Make it chargeable *(blocking, do first)*

Nothing else matters if the first paying merchant breaks the app.

**Migrate off Firestore onto the VPS.** The Spark-plan ceiling disappears with it, and cost becomes a fixed monthly VPS bill instead of a per-read meter — far easier to price against.

1. **Provision the VPS**: PostgreSQL, PHP-FPM, nginx, Redis (optional), TLS. Supervisor units for `queue:work` and `reverb:start`; nginx WebSocket proxy for Reverb.
2. **Port the `api/*.ts` handlers to Laravel controllers** — `signup`, `resolve-store-code`, `resolve-staff-store-code`, `staff-create`, `platform-admin`, and `create-online-order`. That last one is what makes `OrderPlaced` fire for real customer orders (§3a).
3. **Replace `firebase-sync.ts`** (~1k lines) with a repository implementation talking to the Laravel API, and add **Laravel Echo** on the client so the register subscribes to `store.{storeId}` instead of a Firestore listener.
4. **Point the storefronts at the API** — both currently read the catalog straight from Firestore.
5. **Migrate live data**, then delete `functions/`, the Firebase config, and `api/*.ts`.
6. **Backups and monitoring.** Self-hosting means nightly `pg_dump` off-box, restore tested, and something watching the daemons. This is now your job, not Google's.

Alongside the migration:

- Verify **BIR** requirements for POS/OR issuance and vendor accreditation — this decides which merchants are sellable to.
- Set the subscription price point against the real VPS bill + support time — ₱499 is currently a placeholder with no cost basis behind it.

### Phase 1 — The front door *(the highest-leverage change in this document)*

The store code has to become a link. A customer who must install an app and type a code is a customer who is already gone.

- **Per-merchant public URLs** — `/{merchant-slug}`, browsable with no account and no install.
- Storefront works unauthenticated; identity is only collected at checkout.
- **QR code generation** for the counter, packaging, and printed receipts.
- Merchant gets a copy-paste link kit for their Facebook bio and Messenger auto-reply.
- Keep the installable app for repeat customers who opt in — never as the entry point.

### Phase 2 — Prove the saving

The two features that carry the entire story.

- **Price-parity enforcement** — storefront and register read one price field; "in-store price" badge on every item; a violation report for the platform admin.
- **Price-comparison view** — our price vs. the aggregator price, same item, same shop. Designed to be screenshotted.
- **Merchant savings counter** — "you saved ₱X this month" in the dashboard, and an ROI calculator on the landing page.
- **Honest totals** at checkout — parity prices, delivery fee stated plainly, no hidden add-ons.

### Phase 3 — Money

- **COD is the only customer payment path** (§4a) — no gateway work here. What this phase owes it: order confirmation the merchant can trust, a no-show/cancellation path, and change-due visibility for the rider.
- **Real subscription billing** — replace the manual-GCash-to-a-placeholder-number flow and the `pending_verification` status with automated collection, a real price, and a grace window so a failed charge never shuts off a working till mid-day.
- Minimum order values for delivery (basket-size constraint — [positioning.md §7](./positioning.md)).

### Phase 4 — Riders

Strictly in this order; each step needs the density the previous one builds.

1. **Pickup** — already built, lead with it.
2. **Merchant-affiliated riders** — merchant registers their own rider, sets the fee, rider is paid directly, platform takes nothing. Needs a rider record, an assignment flow, and a per-drop earnings view.
3. **Barangay rider pool** — only once merchant density in one radius supports stacking.
4. **Scheduled / batched routes** — before any on-demand dispatch.

Rider app scope is deliberately small: accept a job, navigate, mark delivered, see earnings per drop and per day, transparently.

### Phase 5 — Make the merchant's channel grow

Since we do not supply demand, this is what keeps merchants alive and subscribed.

- **Customer list owned by the merchant** — exportable, theirs.
- **Loyalty / points** — the strongest tool for pulling regulars off aggregators.
- **Discount codes.**
- **Repeat-order nudges** via Messenger / SMS.

### Phase 6 — Density and scale

- Close the **single-store-per-org** gap so multi-branch owners can be sold to.
- Per-barangay merchant density reporting for the platform admin.
- Only *after* density exists in a real area: consider a local discovery surface. Not before — a directory with no traffic is a marketplace we would lose at.

### Table stakes, slot in wherever they block a sale

Merchants treat these as non-negotiable, and "how do I know my cashier isn't stealing?" is usually the first question asked. Audited status:

- ✅ Shift open/close, cash movements, **cash reconciliation** with counted-cash variance
- ✅ **Full-order voids** — inventory restored, excluded from revenue, owner/admin gated
- ⚠️ **No formal X/Z report.** Reports covers the same ground but is not that format
- ❌ **No discounts anywhere in the codebase**
- ❌ No partial / line-item refunds (a deliberate omission — decide whether it stays)
- ❌ No product cost or margin reporting; no printable report export

---

## 7. Open technical decisions

- ~~**Which backend.**~~ **Decided: Laravel + PostgreSQL on a VPS.** Firestore retires with Phase 0.
- **VPS sizing and the ops floor.** Reverb and the queue are long-running daemons; PostgreSQL, backups, TLS renewal, and daemon supervision are now self-managed. Decide who gets paged when the till stops at 7am.
- **Queue driver.** `database` is fine at low volume and needs no extra service. Move to Redis when broadcast latency or job throughput says so, not before.
- **How merchants pay their subscription.** COD covers customer orders, but collecting ₱499/month from merchants by manual GCash transfer does not scale past a handful of them. This is the only place a gateway is still needed.
- **SQLite migration** for the register — how much of the offline promise is actually delivered today versus claimed.
- **Storefront rendering.** Public merchant pages benefit from server rendering for link previews when a merchant shares them on Facebook. Worth checking against the current Vite SPA setup.
- **Android distribution.** Direct APK requires "unknown sources" and triggers Play Protect warnings — a real conversion loss with non-technical merchants. Play Store presence is probably worth the friction.
- ~~**Product name.** "ColePOS" names the least important part of the product.~~ Resolved — renamed to "Baguio Online Market", then to **Omaykan** (omaykan.com) on 2026-08-24.

---

## 8. What we are deliberately not building

Recorded so these do not creep back in:

- A marketplace or consumer discovery app.
- A national rider network, real-time dispatch optimization, or rider insurance.
- Online payment collection of any kind — COD only, see §4a.
- Any commission or per-order fee — see [positioning.md §4](./positioning.md).
- A native desktop shell (removed; the PWA is the big-screen till).
- iOS, until Android and web are working businesses.
