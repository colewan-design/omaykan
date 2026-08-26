# Mobile Plan — Native Android Customer App (Kotlin)

> The rebuild of `apps/mobile` as a native Kotlin app, talking to the Laravel API like any other client.
> Read [plan.md](./plan.md) §2–§3 first for the platform's shape, and [feature-log.md](./feature-log.md) §2 for what the current mobile storefront does.
> Written August 26, 2026. Supersedes the Capacitor storefront in `apps/mobile`.

---

## 1. Why native, and why now

The Capacitor app is not just tired — it is **broken and knowingly parked**. `apps/mobile/src` imports from `@pos/web/storefront`, a directory that no longer exists (the web storefront was folded into `apps/web/src/commerce` + `landing`), and `pairing.ts` and `updateCheck.ts` still reach for the Firebase SDK, which the Laravel migration is retiring. Nothing in it compiles today. So the choice is not "rewrite a working app" — it is "what do we rebuild it as".

The case for Kotlin rather than fixing the shell:

- **The web already owns the no-install path.** [plan.md §1](./plan.md) is explicit: no install between a customer and an order. The storefront at `omaykan.com` is the front door. An Android app that is only a wrapper around that page has no reason to exist, and a wrapper is exactly what Capacitor gives us. The app earns its place only by doing what the web page cannot — and all of those are native: notifications for "your rider is on the way", a proper location fix for delivery, a real offline catalog, a QR scan instead of a typed store code, an install the customer sees on their home screen.
- **One WebView, two web codebases.** Keeping the Capacitor build alive means keeping a second Vue storefront alive next to `apps/web/src/commerce`. They already drifted apart once — mobile had pickup/delivery and the GCash preference before the web did; the web now has address-filtered catalogs mobile never got. Two implementations of the same screens is the drift engine. A Kotlin client is a *different kind* of client: it shares the API contract, not screen code, so nobody expects it to stay in lockstep.
- **The backend is finally client-agnostic.** The reason a shared Vue codebase looked attractive was Firestore — the client needed the SDK, so the client had to be JS. `GET /api/storefront/catalog` killed that. Everything the customer app needs is now plain HTTP plus a WebSocket, which Kotlin speaks as well as anything.

**What this plan does not do:** it does not touch the merchant register. The till stays a PWA. See §11.

---

## 2. Current state, honestly

| Piece | State |
|---|---|
| `apps/mobile`, `apps/mobile-admin` | **Deleted** on 2026-08-26. Neither built: stale `@pos/web/storefront` imports, Firebase-dependent pairing, and the merchant shell was an empty `src/main.ts` |
| Signed release keystore | Rescued out of the deleted tree to `C:\Users\ASUS\omaykan-mobile-keystore-backup\`, alias `colepos` — still **the only copy**, and still not in git |
| Shipped identity | `applicationId com.omaykan.storefront`, `versionCode 1`, `minSdk 24`, `targetSdk 36` |
| Laravel customer API | Built and live: catalog, orders, tracking, customer accounts, addresses, order history |
| Reverb broadcasting | Server side wired (`OrderPlaced`, `OrderStatusChanged`, `routes/channels.php`). **No client anywhere subscribes yet** — this app would be the first |
| Update check (`appReleases` doc) | Firestore-backed, so it dies with Firestore. Needs a Laravel endpoint — see §9 |

---

## 3. Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.x, JDK 21 toolchain | Matches the JDK the existing `gradlew` already needs here — see §9 |
| UI | Jetpack Compose + Material 3 | No XML layouts, no Fragments |
| Navigation | Navigation Compose, type-safe `@Serializable` destinations | Mirrors the six routes in `storefront/router.ts` |
| DI | Hilt | |
| HTTP | Retrofit + OkHttp + `kotlinx.serialization` | One Retrofit instance, base URL from `BuildConfig` |
| Local store | Room (catalog cache, cart, order history) + DataStore Preferences (pairing, settings) | |
| Secrets | `EncryptedSharedPreferences` for the Sanctum token | DataStore Preferences is plaintext on disk; a bearer token is not a setting |
| Images | Coil 3 | |
| Realtime | `pusher-websocket-java` against Reverb | Reverb speaks the Pusher protocol; Echo is a JS convenience, not the wire format |
| Background | WorkManager (order-status refresh); FCM if push is adopted (§8) | |
| Location | Play Services Location (`FusedLocationProviderClient`) | Delivery pin for catalog filtering and fee quoting |
| QR | ML Kit barcode scanning + CameraX (Phase 4) | Scan a store code instead of typing it |
| Testing | JUnit + Turbine + MockWebServer; Compose UI tests over checkout | |

**Deliberately absent:** Firestore (retired), any payment SDK ([plan.md §4a](./plan.md) — payment is COD on purpose), and any offline write queue for orders (§7).

---

## 4. Module layout

A new Gradle project at `apps/mobile-android/`. The Capacitor apps it replaces are already gone, so there is nothing to keep it beside.

```
apps/mobile-android/
  app/                    # Application, MainActivity, nav graph, DI wiring
  core/
    network/              # Retrofit service, DTOs, auth interceptor, error mapping
    database/             # Room entities, DAOs
    data/                 # Repositories — the only thing features depend on
    designsystem/         # Theme, tokens, shared composables
    model/                # Domain types (no serialization annotations)
  feature/
    pairing/              # Store-code entry, first launch
    catalog/              # Product grid, categories, search, product detail
    cart/                 # Cart sheet, quantities
    checkout/             # Fulfillment, address, payment preference, place order
    orders/               # Order status (live), order history
    account/              # Register, sign in, verification gate, profile, addresses
    settings/             # Store, theme, update check, unpair
```

The split exists for one reason worth stating plainly: **features never see a DTO**. Repositories map network and Room into `core:model` types, so a catalog shape change is one file's problem. Everything else here is conventional and can collapse into fewer modules if the build cost turns out not to be worth it.

---

## 5. API contract

Every endpoint the app uses, from `backend/routes/api.php`. Base URL is `https://omaykan.com` in release, `BuildConfig.API_BASE_URL` per build type so a debug build can point at a laptop.

| Call | Auth | Used by |
|---|---|---|
| `POST /api/store-codes/resolve` | none (10/min) | Pairing. Returns `orgSlug`, `storeCode`, `businessMode`, `storeName`, `storeAddress`, `storeLat`, `storeLng`. **409** means the store cannot sell online — surface that message, do not treat it as a bad code |
| `GET /api/storefront/catalog?orgSlug&storeCode[&lat&lng]` | none (60/min) | Catalog. With a pin it answers what branches within range can actually deliver, nearest first, plus a `delivery` block. **An empty `products` list is a real answer, not a failure** — say "nothing reaches this address"; never fall back to a cached or demo catalog |
| `POST /api/customer/register` | none (5/min) | Sign up. Replies `verificationRequired: true`; no token is minted here |
| `POST /api/customer/login` | none (10/min) | Sign in. **Refuses unverified emails** and re-sends the link — that is a state, not a failure toast |
| `POST /api/customer/forgot-password`, `POST /api/customer/reset-password` | none (5/min) | Password reset |
| `GET /api/customer/me`, `POST /api/customer/logout` | `auth:customer` | Session restore, sign out (this device only) |
| `PATCH /api/customer/account`, `/account/email`, `/account/password` | `auth:customer` | Profile |
| `POST/PATCH/DELETE /api/customer/addresses[/{id}]` | `auth:customer` | Saved delivery addresses |
| `GET /api/customer/orders`, `GET /api/customer/orders/{id}` | `auth:customer` | Order history |
| `POST /api/online-orders` | **`auth:customer`** (20/min) | Place order: `orgSlug`, `storeCode`, `businessMode`, `items[{productId, quantity}]`, `guest{name, phone?, email?}`, `fulfillment{method, address?, lat?, lng?}`, `paymentMethod: cash` or `ewallet` |
| `GET /api/online-orders/{uuid}` | none (60/min) | Tracking. Public by unguessable UUID — the forwardable link |
| `POST /api/online-orders/{uuid}/confirm-payment` | none (20/min) | "I handed the cash over" |

Three contract facts that shape the UI more than they look like they should:

1. **Nothing the client says about money is trusted.** Prices, tax and the delivery fee are recomputed server-side from the merchant's own records. The cart total is therefore an *estimate* until the order comes back, and the confirmation screen must render the server's numbers rather than the ones the phone added up.
2. **`fulfillment.lat`/`lng` are all-or-nothing**, and a delivery outside the quoted area fails validation on `fulfillment.address`. Map that 422 back onto the address field, not into a generic error.
3. **The order gate is checkout, and only checkout.** Browsing and filling a cart are open. Do not put a login wall at launch — put it on the Place Order button, and route back into checkout with the cart intact once the customer is in.

Errors: Laravel answers 422 with `{message, errors: {field: [msg]}}`, 401 on a dead token, 429 with `Retry-After`. One `ApiException` hierarchy in `core:network` maps those three, and the 401 handler clears the token and drops to the sign-in sheet **without losing the cart**.

---

## 6. Screens

One-to-one with the routes the Vue app had, plus what customer accounts added since.

| Screen | Contents |
|---|---|
| **First launch / pairing** | Store-code entry (QR scan in Phase 4), explained in one line: this is the code your store gave you |
| **Catalog** | Category rail, search, product grid, cart badge, delivery-address chip in the header (drives the `lat`/`lng` catalog call) |
| **Product detail** | Image, price, description, quantity, add to cart |
| **Cart** | Bottom sheet — lines, quantities, running estimate, "prices confirmed at checkout" |
| **Checkout** | Pickup or delivery; address from device location, a saved address, or typed; cash or GCash preference; contact details prefilled from the account; total with the quoted delivery fee |
| **Sign in / register** | Reached from Place Order. The "verify your email" state is a first-class screen, not an error banner |
| **Order status** | Live status, ticket number, items, server totals, "I've paid" button, shareable tracking link |
| **Order history** | `GET /api/customer/orders`, with the local cache as the offline view |
| **Settings** | Store name and change-store, theme, account, update check, sign out |

Visual design comes from the existing mobile storefront and the food-delivery mockup it was built against. The Compose theme in `core:designsystem` should port the tokens in `packages/core/styles/tokens.css` rather than inventing a second palette.

---

## 7. Offline, cache, and the one thing we deliberately do not queue

- **Catalog** is cached in Room per `(orgSlug, storeCode, lat, lng)` bucket with a fetched-at timestamp. Stale-while-revalidate: render the cache instantly, refresh behind it, and show a quiet "showing saved prices" line when the refresh failed. Never let a cached catalog stand in for a live empty answer (§5).
- **Cart** is local and survives process death — Room, not `SavedStateHandle`.
- **Order history** is cached; the detail view always refetches.
- **Orders are never queued offline.** Placing one decrements real stock and mints a ticket the merchant's till will see. An outbox would let someone "place" an order on the train and discover twenty minutes later that the item sold out, the price moved, or the store closed. Checkout requires connectivity and says so. This is the opposite of the merchant-side rule in [plan.md §4](./plan.md) — the till must never block on the network — and the asymmetry is the point: the till is the source of truth for its own sales, and the phone is not.

---

## 8. Live order status

Two paths, in this order:

1. **Reverb.** The customer's channel is `orders.{orderId}`, public and keyed on the UUID (`routes/channels.php`, [plan.md §3a](./plan.md)). `pusher-websocket-java` connects to the Reverb host with the app key. Payloads are thin by design, so on any event the app refetches `GET /api/online-orders/{uuid}`.
2. **Polling fallback**, 20s while the order screen is foregrounded — exactly what the web storefront does today. Not a temporary crutch: sockets die on mobile networks, and the fallback is what makes the screen trustworthy.

**Push is a real decision, not a detail.** "Your order is being prepared" arriving while the app is closed is one of the few things that justifies the install at all — and it needs FCM, which means a Firebase project, right as we are deleting Firestore. FCM is free on the Spark plan, so the cost is conceptual (one more dependency we just declared dead) rather than financial. It also needs backend work that does not exist: a device-token table and a listener on `OrderStatusChanged` that sends. **Recommendation: keep it, scope it to Phase 5, and be explicit that "we use Firebase" now means Cloud Messaging and nothing else.** Until then the app is silent when closed, which is at least the honest state.

---

## 9. Build, signing, and getting it onto phones

- **`applicationId` must stay `com.omaykan.storefront`** and `versionCode` must start above the installed `1`, or the new APK will not install over the old one — Android treats a changed package as a different app, and there is no Play listing to migrate people through. The `namespace` is free to change; only `applicationId` is identity.
- **Sign with the existing keystore.** It now lives at `C:\Users\ASUS\omaykan-mobile-keystore-backup\` (alias `colepos`, credentials in `keystore.properties` beside it), having been pulled out of `apps/mobile` before that folder was deleted — it was gitignored, so the deletion would otherwise have destroyed it. It is still the **only copy** and still not in git: a regenerated key means every installed user has to uninstall first. Copy it into the new project, keep the same `keystore.properties` pattern, and get it into a password manager — a folder on one laptop is not a backup.
- **`minSdk 26`** (up from 24) — what `EncryptedSharedPreferences` and modern notification channels want, and API 24–25 is a rounding error on any device someone is shopping from in 2026. `targetSdk 36`, `compileSdk 36`, as now.
- **Distribution is a direct APK**; there is no Play listing. So the in-app update check has to keep working, and it currently reads a Firestore `appReleases` doc. Port it: `GET /api/app-releases/storefront-android` returning `{versionCode, versionName, apkUrl, notes}`, compared against `PackageInfo.longVersionCode`. This is the one piece of backend work the Kotlin app strictly requires.
- **JDK 21.** The existing `gradlew` here needs an explicit JDK 21 override; assume the same trap and pin the toolchain in the new project rather than rediscovering it.
- **CI is out of scope** for now — releases are `./gradlew assembleRelease` by hand, same as today.

---

## 10. Phases

Each phase ends with something installable.

**Phase 0 — Backend prerequisites (small, but blocking).**
`GET /api/app-releases/{slug}`. Confirm Reverb is reachable from outside over TLS and note the app key and host for the client. Nothing else on the backend changes for Phases 1–4 — that is the dividend from migrating to Laravel first.

**Phase 1 — Skeleton and catalog.**
Project, modules, theme, DI, Retrofit and error mapping, pairing screen, catalog + search + product detail, Room cache. Signed release build that installs over the Capacitor app. *Deliverable: a customer can pair to a store and browse it offline.*

**Phase 2 — Cart, accounts, checkout.**
Cart with persistence, register/login/verification gate, checkout with pickup and delivery, address entry with a device-location fix, cash/GCash preference, order placement, confirmation. *Deliverable: an order placed from the phone lands on the merchant's Track Order strip.*

**Phase 3 — Order status and history.**
Tracking screen, Reverb subscription with the 20s fallback, "I've paid" confirmation, order history, settings and update check. *Deliverable: parity with the old Capacitor app, plus accounts. Delete `apps/mobile` here.*

**Phase 4 — The native dividend.**
QR scan for store codes, saved-address management, address-filtered catalog with the "nothing reaches you" state, list and image polish, Compose UI tests over checkout.

**Phase 5 — Push** (needs the §8 decision).
Device-token registration, an `OrderStatusChanged` listener that sends, notification channels, deep links from a notification into the order screen.

---

## 11. The other two Android surfaces

Named here so this plan is not mistaken for covering them.

- **Merchant app (`apps/mobile-admin`)** — an empty Capacitor shell. The till is a PWA and [plan.md](./plan.md) keeps it that way; the merchant's offline-first, SQLite-backed register is a far larger native lift than the customer app and gains much less from being native. **Recommendation: delete the empty shell.** If a merchant phone app is ever wanted, the useful version is small and specific — new-order alerts and order acceptance, not the whole register — and that is its own plan.
- **Rider app** — not built in any form, though the API for it already exists and is fuller than the customer's (`/api/rider/*`: register with licence uploads, board, accept, stage, release). It is the strongest native candidate on the platform: background location, persistent notifications, a screen used one-handed on a motorbike. It deserves its own plan and its own module tree, sharing `core:network` conventions but not code.

---

## 12. Open decisions

1. **FCM or silence?** (§8) — recommendation: adopt in Phase 5, scoped to Cloud Messaging only.
2. **Does the app keep the GCash preference?** The web storefront stopped asking; mobile still does. Parity says drop it; the merchants who liked it say keep it. Either is defensible — but the two clients should stop disagreeing with each other.
3. **Wishlist** — mobile-only today, with nothing server-side behind it. Port as a local-only feature, or drop it with the rewrite?
4. **Multi-store pairing.** Today: one code, one store, remembered forever. Now that customers have accounts, "my stores" is cheap to add and changes the pairing screen's shape. Worth deciding before Phase 1 rather than retrofitting after.
