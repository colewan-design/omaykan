# Mobile Plan — Native Android Customer App (Kotlin)

> The rebuild of `apps/mobile` as a native Kotlin app, talking to the Laravel API like any other client.
> Read [plan.md](./plan.md) §2–§3 first for the platform's shape, and [feature-log.md](./feature-log.md) §2 for what the current mobile storefront does.
> Written August 26, 2026. Supersedes the Capacitor storefront in `apps/mobile`.

---

## 1. Why native, and why now

The Capacitor app was not just tired — it was **broken**, and it has now been deleted. `apps/mobile/src` imported from `@pos/web/storefront`, a directory that no longer exists (the web storefront was folded into `apps/web/src/commerce` + `landing`), and `pairing.ts` and `updateCheck.ts` still reached for the Firebase SDK, which the Laravel migration retires. None of it compiled. So the choice was never "rewrite a working app" — it is "what do we rebuild it as".

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
| Update check (`appReleases` doc) | Firestore-backed, so it dies with Firestore. Moves to a Laravel endpoint — see §8a.1 |
| Queue worker | `database` driver, `jobs` table migrated, `ShouldBroadcast` events dispatched after commit. Needs supervisor on the VPS — §8a |

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
| Realtime | `pusher-websocket-java` against Reverb | Reverb speaks the Pusher protocol; Echo is a JS convenience, not the wire format. **The only push channel — see §8** |
| Background | WorkManager (order-status refresh) + a bounded foreground service while an order is live (§8) | No FCM, no Google Play Services messaging |
| Location | Play Services Location (`FusedLocationProviderClient`) | Delivery pin for catalog filtering and fee quoting |
| QR | ML Kit barcode scanning + CameraX (Phase 4) | Scan a store code instead of typing it |
| Testing | JUnit + Turbine + MockWebServer; Compose UI tests over checkout | |

**Deliberately absent — and this is the rule the rest of the plan is written against: no Firebase, in any form.** Not Firestore, not Cloud Messaging, not Analytics, not Crashlytics. Everything the server does for this app is Laravel: HTTP for reads and writes, **Reverb** for live updates, **queues and jobs** for anything that must not block a request, the **scheduler** for anything periodic. Also absent: any payment SDK ([plan.md §4a](./plan.md) — payment is COD on purpose), and any offline write queue for orders (§7).

The one thing this costs is the easy path to notifications on a closed app, which normally means FCM. §8 says what we do instead.

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

## 8. Live order status — Reverb, and nothing but Reverb

### The channel

The customer's channel is **`order.{uuid}`** — singular, public, keyed on the order's UUID. Do not confuse it with `orders.{orderId}` in `routes/channels.php`, which is the *staff* private channel authorized by store membership. Three events ride the customer channel today, all already implemented server-side:

| Event | `broadcastAs` | Carries |
|---|---|---|
| `OrderStatusChanged` | `order.status-changed` | id, ticket number, new status, previous status, timestamp |
| `OrderDeliveryUpdated` | `order.delivery-updated` | rider name and number, delivery stage — "food is ready" and "a rider has it" are different questions, which is why it is a separate event |
| `OrderPlaced` | `order.placed` | store channel only — staff, not this app |

`pusher-websocket-java` connects straight to Reverb with `REVERB_APP_KEY`, host, port 443, TLS on. Reverb speaks the Pusher protocol on the wire, so no Laravel-specific client is needed and Echo never enters the picture. Payloads are thin on purpose, so **every event triggers a refetch of `GET /api/online-orders/{uuid}`** rather than being trusted as state.

Note for later: `OrderStatusChanged`'s docblock says "if customer accounts ever land, make this private." They have landed. **Recommendation: leave it public anyway** — the UUID link is meant to be forwarded to whoever is actually standing at the door, and making it private would break tracking for exactly the person who needs it. Nothing sensitive rides the channel by design. If it is ever made private, the app must then hit `/broadcasting/auth` with its Sanctum token on the `customer` guard, and that endpoint does not exist yet.

### The three tiers of "the customer finds out"

Without FCM there is no free ride from Google's socket, so delivery is layered by how alive the app is:

1. **Foreground — Reverb, plus a 20s poll.** The socket is primary; the poll is not a crutch but the thing that makes the screen trustworthy, because mobile sockets die silently. Same 20s the web storefront already uses.
2. **Order live, app backgrounded — a bounded foreground service.** When an order is placed, start a service that holds the Reverb connection and posts a low-priority ongoing notification ("Tracking order #1234"), upgrading it in place as statuses arrive. It stops itself the moment the order reaches a terminal status, and hard-stops on a timeout. This is what replaces FCM, and it is honest about its cost: a persistent notification and some battery for the twenty minutes an order is actually in flight — not a daemon that lives forever. Direct-APK distribution helps here: no Play listing means no Play foreground-service policy review.
3. **Process killed — WorkManager.** A chain of one-shot workers with widening backoff over the order's expected life, each hitting `GET /api/online-orders/{uuid}` and raising a local notification on a change. Periodic work has a 15-minute floor, which is too coarse to be the primary path but fine as the catch-all.

The pleasing part of the constraint: **tiers 1–3 need zero new backend infrastructure.** The socket is Reverb, which exists; the poll is a public endpoint, which exists. No device-token table, no push credentials, no third-party service to keep an account with.

If notification quality later proves insufficient, the Laravel-only escalation is **self-hosted UnifiedPush** (an `ntfy` instance on the same VPS, driven by a custom Laravel notification channel from a queued job). That buys true wake-from-dead delivery without Google — at the price of another daemon to keep alive, and a push server whose downtime is invisible until someone misses an order. Not recommended for v1; recorded so the option is not rediscovered from scratch.

---

## 8a. What the Laravel side must provide

Everything below is ordinary Laravel — no new services, no new vendors.

### Already built, and the app just uses it

| Piece | Where |
|---|---|
| Broadcast events on the customer channel | `app/Events/OrderStatusChanged.php`, `OrderDeliveryUpdated.php` |
| Queued broadcasting (`ShouldBroadcast`, dispatched inside `DB::afterCommit()`) | `SyncController::applyOrderEvent()`, `OnlineOrderController` |
| Queue on the `database` driver, `jobs` table migrated | `config/queue.php`, `0001_01_01_000002_create_jobs_table.php` |
| Order confirmation mail | `app/Mail/OnlineOrderConfirmationMail.php` |
| Customer verification and password-reset notifications | `app/Notifications/Customer*.php` |

### To build — the app's actual backend prerequisites

1. **`GET /api/app-releases/{slug}`** — the update check, replacing the Firestore `appReleases` doc. An `app_releases` table (`slug`, `version_code`, `version_name`, `apk_url`, `notes`, `published_at`), a thin public controller, and an artisan command (`php artisan app:release storefront-android --code=2 --name=2.0.0 --apk=…`) so publishing a build is one command rather than a hand-edited row. The APK itself is a file on the VPS served by nginx; it does not belong in the database.
2. **A queued job for the order-confirmation mail**, if it is not already dispatched rather than sent inline. Placing an order is the one request in this app a customer waits on with money decided — an SMTP round-trip to Hostinger does not belong inside it. `ShouldQueue` on the mailable, dispatched after commit.
3. **A scheduled sweep for stale orders** (`routes/console.php` + `schedule:work`): an order left `pending` overnight because nobody at the shop touched it should be closed out and broadcast, so the customer's tracking screen stops saying "preparing" forever. This is a real hole today, and the app makes it visible — a phone that shows a live status is much less forgiving of a status that never moves than a web page nobody left open.

### Operations, because the app's realtime is only as alive as these

```bash
php artisan reverb:start     # WebSocket server
php artisan queue:work       # REQUIRED — broadcasts are queued
php artisan schedule:work    # for §8a.3
```

All three are long-running daemons and all three need supervisor (or systemd) on the VPS, per [plan.md §3a](./plan.md). The failure mode worth writing on the wall: **if `queue:work` is dead, no event ever reaches any client** — the HTTP requests all still succeed, orders still record, and the only symptom is that every phone silently degrades to its 20s poll. The app cannot detect the difference. Monitor the worker, not just the web server.

Set `REVERB_HOST` to the public hostname with `REVERB_PORT=443` and `REVERB_SCHEME=https`, keep `REVERB_SERVER_HOST=0.0.0.0`, and make sure nginx proxies the WebSocket upgrade. The Android client needs `REVERB_APP_KEY` and that host — it is a public key by design, so it can live in `BuildConfig`.

---

## 9. Build, signing, and getting it onto phones

- **`applicationId` and the signing key are both free choices.** The Capacitor app was never published to Google Play, so there is no listing to preserve and no installed base to keep updatable — the two things that would otherwise pin them. `com.omaykan.storefront` is still the sensible name, but nothing breaks if it changes. The one case that still bites: a phone with the old sideloaded APK on it has to uninstall before a differently-signed build will install, which is a one-line instruction to a handful of test devices rather than a constraint on the rewrite.
- **Generate a fresh keystore for the Kotlin project.** The old one (alias `colepos`) was rescued out of `apps/mobile` before that folder was deleted and sits at `C:\Users\ASUS\omaykan-mobile-keystore-backup\`, but with nothing published there is no reason to inherit it. Keep the new one out of git the same way — an untracked `keystore.properties` read by `signingConfigs.release` — and back it up somewhere that is not one laptop, because the day it *does* reach Play is the day it becomes irreplaceable.
- **`minSdk 26`** (up from 24) — what `EncryptedSharedPreferences` and modern notification channels want, and API 24–25 is a rounding error on any device someone is shopping from in 2026. `targetSdk 36`, `compileSdk 36`, as now.
- **Distribution is a direct APK**; there is no Play listing. So the in-app update check has to keep working, and it currently reads a Firestore `appReleases` doc. It moves to Laravel — `GET /api/app-releases/storefront-android`, compared against `PackageInfo.longVersionCode`; spec in §8a.1. This is the one piece of backend work the Kotlin app strictly requires.
- **No `google-services.json`, and no Google Services Gradle plugin.** The old `build.gradle` applied it conditionally if the file appeared; the new project should not have the hook at all, so nobody re-adds Firebase by dropping a file in.
- **JDK 21.** The existing `gradlew` here needs an explicit JDK 21 override; assume the same trap and pin the toolchain in the new project rather than rediscovering it.
- **CI is out of scope** for now — releases are `./gradlew assembleRelease` by hand, same as today.

---

## 10. Phases

Each phase ends with something installable.

**Phase 0 — Laravel prerequisites (small, but blocking).**
The `app_releases` table, endpoint and artisan command (§8a.1). Queue the order-confirmation mail (§8a.2). Confirm Reverb is reachable from outside over TLS, that `queue:work` is under supervisor, and note `REVERB_APP_KEY` + host for the client. Nothing else on the backend changes for Phases 1–4 — that is the dividend from having migrated to Laravel first.

**Phase 1 — Skeleton and catalog.**
Project, modules, theme, DI, Retrofit and error mapping, pairing screen, catalog + search + product detail, Room cache. Signed release build that installs over the Capacitor app. *Deliverable: a customer can pair to a store and browse it offline.*

**Phase 2 — Cart, accounts, checkout.**
Cart with persistence, register/login/verification gate, checkout with pickup and delivery, address entry with a device-location fix, cash/GCash preference, order placement, confirmation. *Deliverable: an order placed from the phone lands on the merchant's Track Order strip.*

**Phase 3 — Order status and history.**
Tracking screen, Reverb subscription (`order.{uuid}`, all three events) with the 20s foreground poll, "I've paid" confirmation, order history, settings and update check against the new endpoint. *Deliverable: parity with the deleted Capacitor app, plus accounts, plus live tracking nothing in this codebase has ever had — the app is the first Reverb client on the platform.*

**Phase 4 — Notifications without Google** (§8 tiers 2 and 3).
Notification channels, the bounded foreground service that holds the socket while an order is live, the WorkManager fallback chain, deep links from a notification into the order screen, and the stale-order sweep on the Laravel side (§8a.3) so a status can never sit still forever.

**Phase 5 — The native dividend.**
QR scan for store codes, saved-address management, address-filtered catalog with the "nothing reaches you" state, list and image polish, Compose UI tests over checkout.

---

## 11. The other two Android surfaces

Named here so this plan is not mistaken for covering them.

- **Merchant app** — the `apps/mobile-admin` shell was empty and is now deleted with the rest. The till is a PWA and [plan.md](./plan.md) keeps it that way: the merchant's offline-first, SQLite-backed register is a far larger native lift than the customer app and gains much less from being native. If a merchant phone app is ever wanted, the useful version is small and specific — new-order alerts off the `store.{id}` private channel and order acceptance, not the whole register — and that is its own plan. Note it would need `/broadcasting/auth` for the staff guard, which the customer app deliberately avoids needing (§8).
- **Rider app** — not built in any form, though the API for it already exists and is fuller than the customer's (`/api/rider/*`: register with licence uploads, board, accept, stage, release). It is the strongest native candidate on the platform: background location, persistent notifications, a screen used one-handed on a motorbike. It deserves its own plan and its own module tree, sharing `core:network` conventions but not code.

---

## 12. Open decisions

1. **How hard do we push tier 2?** (§8) — the foreground service is the whole no-Firebase bet. If a persistent "tracking your order" notification turns out to annoy customers more than a missed status update does, the fallback is tier 3 alone (coarser, quieter) or the UnifiedPush escalation (better, more ops). Decide with a real order in hand during Phase 4, not on paper now.
2. **Does the app keep the GCash preference?** The web storefront stopped asking; mobile still does. Parity says drop it; the merchants who liked it say keep it. Either is defensible — but the two clients should stop disagreeing with each other.
3. **Wishlist** — mobile-only today, with nothing server-side behind it. Port as a local-only feature, or drop it with the rewrite?
4. **Multi-store pairing.** Today: one code, one store, remembered forever. Now that customers have accounts, "my stores" is cheap to add and changes the pairing screen's shape. Worth deciding before Phase 1 rather than retrofitting after.
