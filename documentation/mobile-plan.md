# Mobile Plan — Native Android Customer App (Kotlin)

> The rebuild of `apps/mobile` as a native Kotlin app, talking to the Laravel API like any other client.
> Read [plan.md](./plan.md) §2–§3 first for the platform's shape, and [feature-log.md](./feature-log.md) §2 for what the current mobile storefront does.
> Written August 26, 2026. Supersedes the Capacitor storefront in `apps/mobile`.
> **Re-verified against `main` on August 28, 2026.** The plan was written against a branch that has since fallen 17 commits behind `main`, and several of its stated facts no longer hold. §2, §5, §6, §7, §8, §8a, §10, §11 and §12 are corrected below. Anything marked **not on `main`** is work that must land before the phase depending on it.

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
| Reverb broadcasting | Wired on both sides. `packages/core/src/realtime/orderChannel.ts` is a working Laravel Echo + `pusher-js` client, consumed by `packages/core/src/pages/DashboardPage.vue:16`. **Read it before writing the Kotlin socket** — it is the reference implementation and it carries the env shape the client needs |
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
| QR | ~~ML Kit barcode scanning + CameraX~~ | Dropped 2026-09-03 with the codes it would have scanned. A shop's QR should encode its storefront link, which any camera app already opens |
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
| ~~`POST /api/store-codes/resolve`~~ | — | **Gone, 2026-09-03.** Resolving a shop from a typed code was retired with the pairing the same code did double duty as. A shopper reaches a shop through `GET /api/stores` and its org-slug/branch-code pair |
| `GET /api/storefront/catalog?orgSlug&storeCode` | none (60/min) | Catalog. On `main` this validates **`orgSlug` and `storeCode` and nothing else** — no `lat`, no `lng`, no branch selection, no `delivery` block. The address-filtered version is real but unmerged; **not on `main`** — see §8a.2 |
| `POST /api/customer/register` | none (5/min) | Sign up. Replies `verificationRequired: true`; no token is minted here |
| `POST /api/customer/login` | none (10/min) | Sign in. **Refuses unverified emails** and re-sends the link — that is a state, not a failure toast |
| `POST /api/customer/forgot-password`, `POST /api/customer/reset-password` | none (5/min) | Password reset |
| `GET /api/customer/me`, `POST /api/customer/logout` | `auth:customer` | Session restore, sign out (this device only) |
| `PATCH /api/customer/account`, `/account/email`, `/account/password` | `auth:customer` | Profile |
| `POST/PATCH/DELETE /api/customer/addresses[/{id}]` | `auth:customer` | Saved delivery addresses |
| `GET /api/customer/orders`, `GET /api/customer/orders/{id}` | `auth:customer` | Order history |
| `POST /api/online-orders` | **none** (20/min) — guest orders are the norm | Place order: `orgSlug`, `storeCode`, `businessMode`, `items[{productId, quantity}]`, `guest{name, phone?, email?}`, `fulfillment{method, address?, lat?, lng?}`, `paymentMethod: cash` or `ewallet` |
| `GET /api/online-orders/{uuid}` | none (60/min) | Tracking. Public by unguessable UUID — the forwardable link |
| ~~`POST /api/online-orders/{uuid}/confirm-payment`~~ | — | **Does not exist.** Struck 2026-08-28: there is no such route in `routes/api.php`, no such method on `OnlineOrderController`, and the web storefront does not call one either. It was in this plan from the start and was never real. Either build it or stop planning around it — the "I've paid" button in §6 is blocked on that decision |

Three contract facts that shape the UI more than they look like they should:

1. **Nothing the client says about money is trusted.** Prices, tax and the delivery fee are recomputed server-side from the merchant's own records. The cart total is therefore an *estimate* until the order comes back, and the confirmation screen must render the server's numbers rather than the ones the phone added up.
2. **`fulfillment.lat`/`lng` are all-or-nothing**, and a delivery outside the quoted area fails validation on `fulfillment.address`. Map that 422 back onto the address field, not into a generic error — the rule is right, but *build it blind*, because the 422 is currently unreachable. `DeliveryQuoter::quote()` short-circuits to the flat ₱49 base fee the moment **either** pin is missing, and both production stores have `lat`/`lng` null ([feature-audit.md §3.4](./feature-audit.md)), so `OutsideDeliveryAreaException` never throws in production and a 40 km delivery is accepted at ₱49. Locally it *is* reachable — the seeded stores carry pins — so test it there. Fixing production is a data problem (pin the stores), not a code one.
3. **There is no order gate, and the app must not invent one.** `POST /api/online-orders` carries `throttle:20,1` and no auth middleware (`routes/api.php:43`); the controller reads `$request->user('customer')` as *optional* and says so in its own comment — "no token at all is still a perfectly good guest order". A login wall on Place Order would make the native app strictly worse than the web storefront, which takes guest orders today. Sign-in is a **convenience, never a condition**: when a token is present the server back-fills `guest.name`, `guest.phone` and `guest.email` from the account, so sell it as "we'll fill this in and keep your order history", offered beside the checkout form and skippable in one tap.

Errors: Laravel answers 422 with `{message, errors: {field: [msg]}}`, 401 on a dead token, 429 with `Retry-After`. One `ApiException` hierarchy in `core:network` maps those three, and the 401 handler clears the token and drops to the sign-in sheet **without losing the cart**.

---

## 6. Screens

One-to-one with the routes the Vue app had, plus what customer accounts added since.

| Screen | Contents |
|---|---|
| **First launch** | The market — `GET /api/stores`, searchable. Typed store-code entry was built and then removed with the codes themselves (2026-09-03); the directory is the whole of how a shop is found |
| **Catalog** | Category rail, search, product grid, cart badge. The delivery-address chip that drives a `lat`/`lng` catalog call is **Phase 5**, and depends on §8a.2 landing first |
| **Product detail** | Image, price, description, quantity, add to cart |
| **Cart** | Bottom sheet — lines, quantities, running estimate, "prices confirmed at checkout" |
| **Checkout** | Pickup or delivery; cash or GCash preference; contact details prefilled *if* signed in; total with the quoted delivery fee. Address comes from device location, a saved address, or typed — but **only the first two ever yield coordinates**: there is no geocoder anywhere in this project (`apps/web/src/commerce/deliveryLocation.ts:14`), so typed text is a delivery note, not a pin. Treat the typed field and the map fix as separate values, the way the web already does |
| **Sign in / register** | Offered at checkout and from Settings, **never required to place an order** (§5, fact 3). The "verify your email" state is a first-class screen, not an error banner |
| **Order status** | Live status, ticket number, items, server totals, shareable tracking link. The "I've paid" button needs an endpoint that does not exist — see §5 |
| **Order history** | `GET /api/customer/orders`, with the local cache as the offline view. Filter chips are the four stages the account page counts — Preparing, Ready, On the way, Completed, plus All and Active — so a stage tapped there lands on exactly the orders it said were in it |
| **Account** | **Built 2026-08-28.** The tab is a portal, not a profile card: a tinted identity header carrying the address an order would go to right now, a row of counts (orders / saved / addresses), the order lifecycle as four tappable stages, and a grid into the sections below. Laid out after the marketplace apps this market already shops on — but without the half of that shape that is loyalty theatre. No coins, no tier, no voucher wallet, no games rail: this platform has none of those, and every number on the page counts something real. Signed out it still shows the destinations that need no account, because a login wall would contradict §5 fact 3 |
| **Account → sections** | Your details (name, phone, notification toggles, substitutions), Delivery addresses (full CRUD, default, pin state), Payment, Sign-in details (email and password, each behind the current password; a Google-only account is told what opens the email field). All four wired to `/api/customer/*` — see the §8a table |
| **Settings** | ~~Store name and change-store, theme, account, update check, sign out~~ Mostly absorbed by **Account** above: the sections, the shop-code shortcut and sign out live there, and the update check now prompts on launch rather than waiting to be opened (§8a.1). What is still missing is a theme override — the app follows the system only |

Visual design comes from the existing mobile storefront and the food-delivery mockup it was built against. The Compose theme in `core:designsystem` should port the tokens in `packages/core/src/styles/tokens.css` rather than inventing a second palette.

---

## 7. Offline, cache, and the one thing we deliberately do not queue

- **Catalog** is cached in Room per `(orgSlug, storeCode)` bucket with a fetched-at timestamp — `lat`/`lng` join the key only when §8a.2 lands, since until then the endpoint ignores them and a four-part key would just fragment the cache into identical copies. Stale-while-revalidate: render the cache instantly, refresh behind it, and show a quiet "showing saved prices" line when the refresh failed. Never let a cached catalog stand in for a live empty answer (§5).
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

`pusher-websocket-java` connects straight to Reverb with `REVERB_APP_KEY`, host, port 443, TLS on. Reverb speaks the Pusher protocol on the wire, so no Laravel-specific client is needed and Echo never enters the picture.

> **Landmine — settle this before Phase 3 starts.** Production Reverb does **not** sit on the Pusher default path. It binds to `127.0.0.1:8081` and is reached through an nginx proxy at **`/reverb/`**, because `/app` already serves the POS SPA ([deployment.md](./deployment.md) §1). The web client handles this with Echo's `wsPath: '/reverb'` (`orderChannel.ts:58`, fed by `VITE_REVERB_PATH`). **`pusher-websocket-java` has no `wsPath` equivalent** — `PusherOptions` gives you host, port and TLS, and hardcodes `/app/{key}`. Three ways out, in order of preference: give Reverb its own subdomain (`ws.omaykan.com`) so the default path is free and every client simplifies; override `PusherOptions.buildUrl()`; or drop `pusher-websocket-java` for a client that exposes the path. The first is an nginx and DNS change, costs the Android client nothing, and is the only one that does not leave a custom transport to maintain. **Verify against the live server before committing to a library.** Payloads are thin on purpose, so **every event triggers a refetch of `GET /api/online-orders/{uuid}`** rather than being trusted as state.

Note for later: `OrderStatusChanged`'s docblock says "if customer accounts ever land, make this private." They have landed. **Recommendation: leave `OrderStatusChanged` public** — the UUID link is meant to be forwarded to whoever is actually standing at the door, and making it private would break tracking for exactly the person who needs it. Should it ever go private, `POST /api/broadcasting/auth` **already exists** (`routes/api.php:233`, `auth:sanctum`, resolving the staff guard); a customer-guard channel would need the guard added, not the endpoint built.

> **`OrderDeliveryUpdated` was a different question — now fixed on `main` (2026-08-28).** It broadcasts on both `PrivateChannel('store.{id}')` *and* the public `Channel('order.{id}')`, and `broadcastWith()` shipped `riderName` and `riderPhone` unconditionally. Since the `order.{uuid}` link is *meant* to be forwarded, that left a real person's mobile readable by everyone who ever held the link, permanently.
>
> The channel was never the whole leak, and the first fix proposed here was wrong because of it: `GET /api/online-orders/{uuid}` is **public**, not authenticated, and `Order::toTrackedArray()` served both fields too — so stripping only the broadcast would have been theatre. The fix gates the *number* on the delivery being in flight, in both places: `Order::riderPhoneForCustomer()` returns it during `assigned` and `picked_up`, null afterwards. `riderName` is untouched — much weaker alone, and order history reasonably says who brought the order.
>
> **What the app should expect:** `riderPhone` is populated exactly while a rider is holding the order, and null once delivered. Drive the call button off its presence rather than off `deliveryStage`, and do not cache it past a delivery.

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
| Order confirmation mail, **already queued** | `app/Mail/OnlineOrderConfirmationMail.php` — it extends `OmaykanMailable`, which `implements ShouldQueue` (`app/Mail/OmaykanMailable.php:28`). All five mailables inherit it, so no SMTP round-trip sits inside any request |
| Customer verification and password-reset notifications | `app/Notifications/Customer*.php` |

### To build — the app's actual backend prerequisites

1. ~~**`GET /api/app-releases/{slug}`** — the update check~~ **Built 2026-08-28.** `app_releases` table, `AppRelease` model, `AppReleaseController`, and `php artisan app:release storefront-android --code=2 --name=2.0.0 --apk=…` (plus `--draft`, `--publish`, `--unpublish`, `--list`, `--force`). History is kept rather than one mutable row, so `--unpublish` rolls back to the build before instead of needing the old values retyped; the endpoint serves the **highest published `version_code`**, which is what makes that rollback work. The command refuses a code below the live one without `--force` — the failure it exists to prevent, because a lower code publishes cleanly and then does nothing on every phone already past it, with no error anywhere. Client side: `UpdateRepository` compares against `PackageInfo.longVersionCode` and `UpdatePrompt` offers the APK to the browser once per version. Covered by `AppReleaseApiTest` (20 cases) and `UpdateRepositoryTest`.
2. **Port the address-filtered catalog forward.** *(Was "queue the order-confirmation mail" — that is already done; see the table above.)* `GET /api/storefront/catalog` on `main` is 180 lines and takes `orgSlug` + `storeCode` only. A ~312-line version that accepts `lat`/`lng`, picks every branch of the organization within delivery range, sorts nearest-first and returns a `delivery` block already exists on `origin/docs/commission-free-positioning-and-reverb` — the same stale branch this plan came from, now 17 commits behind `main`. **Do not cherry-pick it blind**: diff it against `main`'s controller first, because 17 commits of divergence sit between them. Once merged, the contract adds `[&lat&lng]` (`required_with` each other, so half a pair is a 422) and the rule that makes the feature honest: **an empty `products` list is a real answer, not a failure** — say "nothing reaches this address", and never let a cached or demo catalog stand in for it. Nothing in Phases 1–4 needs this; Phase 5 does not start without it.
3. **A scheduled sweep for stale orders** (`routes/console.php` + `schedule:work`): an order left `pending` overnight because nobody at the shop touched it should be closed out and broadcast, so the customer's tracking screen stops saying "preparing" forever. This is a real hole today, and the app makes it visible — a phone that shows a live status is much less forgiving of a status that never moves than a web page nobody left open.

### Operations, because the app's realtime is only as alive as these

```bash
php artisan reverb:start     # WebSocket server
php artisan queue:work       # REQUIRED — broadcasts are queued
php artisan schedule:work    # for §8a.3
```

All three are long-running daemons and all three need supervisor (or systemd) on the VPS, per [plan.md §3a](./plan.md). The failure mode worth writing on the wall: **if `queue:work` is dead, no event ever reaches any client** — the HTTP requests all still succeed, orders still record, and the only symptom is that every phone silently degrades to its 20s poll. The app cannot detect the difference. Monitor the worker, not just the web server.

Set `REVERB_HOST` to the public hostname with `REVERB_PORT=443` and `REVERB_SCHEME=https`, and make sure nginx proxies the WebSocket upgrade. Note that production today runs `reverb:start --host=127.0.0.1 --port=8081` behind the `/reverb/` proxy ([deployment.md](./deployment.md) §1), **not** `REVERB_SERVER_HOST=0.0.0.0` on the default path — which is the whole of the §8 landmine. The Android client needs `REVERB_APP_KEY` and that host — it is a public key by design, so it can live in `BuildConfig`.

---

## 9. Build, signing, and getting it onto phones

- **`applicationId` and the signing key are both free choices.** The Capacitor app was never published to Google Play, so there is no listing to preserve and no installed base to keep updatable — the two things that would otherwise pin them. `com.omaykan.storefront` is still the sensible name, but nothing breaks if it changes. The one case that still bites: a phone with the old sideloaded APK on it has to uninstall before a differently-signed build will install, which is a one-line instruction to a handful of test devices rather than a constraint on the rewrite.
- **Generate a fresh keystore for the Kotlin project.** The old one (alias `colepos`) was rescued out of `apps/mobile` before that folder was deleted and sits at `C:\Users\ASUS\omaykan-mobile-keystore-backup\`, but with nothing published there is no reason to inherit it. Keep the new one out of git the same way — an untracked `keystore.properties` read by `signingConfigs.release` — and back it up somewhere that is not one laptop, because the day it *does* reach Play is the day it becomes irreplaceable.
- **`minSdk 26`** (up from 24) — what `EncryptedSharedPreferences` and modern notification channels want, and API 24–25 is a rounding error on any device someone is shopping from in 2026. `targetSdk 36`, `compileSdk 36`, as now.
- **Distribution is a direct APK**; there is no Play listing. So the in-app update check has to keep working, and it currently reads a Firestore `appReleases` doc. It moves to Laravel — `GET /api/app-releases/storefront-android`, compared against `PackageInfo.longVersionCode`; spec in §8a.1. This is the one piece of backend work the Kotlin app strictly requires.
- **Android App Links, for the password-reset email.** Added 2026-08-28. The reset mail points at `https://omaykan.com/account?token=&email=`, the same URL the web portal honours, and `MainActivity` declares an `autoVerify` filter on `/account` so the link opens the app instead of the browser. Both route arguments are required, so a plain visit to `/account` matches no destination and the app simply opens at the market. Verification needs `/.well-known/assetlinks.json`, now served by `AssetLinksController` from `ANDROID_APP_PACKAGE` and `ANDROID_APP_SHA256_FINGERPRINTS` — **fill those in on the VPS**, with both the debug and release certificate fingerprints, or Android falls back to asking which app should open the link. `MainActivity` is `singleTask` and forwards `onNewIntent` to the nav controller, so a link tapped while the app is running reaches the running nav host.
- **No `google-services.json`, and no Google Services Gradle plugin.** The old `build.gradle` applied it conditionally if the file appeared; the new project should not have the hook at all, so nobody re-adds Firebase by dropping a file in.
- **JDK 21.** The existing `gradlew` here needs an explicit JDK 21 override; assume the same trap and pin the toolchain in the new project rather than rediscovering it.
- **CI is out of scope** for now — releases are `./gradlew assembleRelease` by hand, same as today.

---

## 10. Phases

Each phase ends with something installable.

**Phase 0 — Laravel prerequisites (small, but blocking).** *(Update check: **done**, 2026-08-28.)*
~~The `app_releases` table, endpoint and artisan command (§8a.1)~~ — built; see §8a.1. What remains of this phase is operational. Confirm `queue:work` is under supervisor, and note `REVERB_APP_KEY` + host for the client. **Settle the Reverb path question (§8) here, not in Phase 3**: connect any Pusher-protocol client to production and find out whether the Android library can reach `/reverb/` at all, because the answer may be an nginx and DNS change with its own lead time. *(Queuing the order-confirmation mail has been struck — it was already done before this plan was written.)* Nothing else on the backend changes for Phases 1–4; §8a.2 is a Phase 5 prerequisite, not a Phase 0 one.

**Phase 1 — Skeleton and catalog.**
Project, modules, theme, DI, Retrofit and error mapping, store-code entry, catalog + search + product detail, Room cache. Signed release build that installs over the Capacitor app. *Deliverable: a customer can open a store and browse it offline.* (The code-entry screen shipped and was later deleted — see §7's table.)

**Phase 2 — Cart, accounts, checkout.**
Cart with persistence, **guest checkout first** — pickup and delivery, address entry with a device-location fix, cash/GCash preference, order placement, confirmation. Register/login/verification lands in the same phase but strictly beside checkout, never in front of it (§5, fact 3): a signed-in customer gets their contact details prefilled and their order in history, a guest gets the identical order. *Deliverable: an order placed from the phone — signed in or not — lands on the merchant's Track Order strip.*

**Phase 3 — Order status and history.**
Tracking screen, Reverb subscription (`order.{uuid}`, all three events) with the 20s foreground poll, "I've paid" confirmation, order history, settings and update check against the new endpoint. Port the connection logic from `orderChannel.ts` rather than deriving it — it already solves the env shape, the reconnect behaviour and the sub-path. *Deliverable: parity with the deleted Capacitor app, plus accounts, plus live order tracking on a phone.* **Precondition: the §8 path question answered in Phase 0.**

**Phase 4 — Notifications without Google** (§8 tiers 2 and 3).
Notification channels, the bounded foreground service that holds the socket while an order is live, the WorkManager fallback chain, deep links from a notification into the order screen, and the stale-order sweep on the Laravel side (§8a.3) so a status can never sit still forever.

**Phase 5 — The native dividend.**
Saved-address management, the delivery-address chip in the catalog header, address-filtered catalog with the "nothing reaches you" state, list and image polish, Compose UI tests over checkout. **Precondition: §8a.2 merged** — without it the endpoint ignores `lat`/`lng` and the whole feature is a chip that changes nothing.

---

## 11. The other two Android surfaces

Named here so this plan is not mistaken for covering them.

- **Merchant app** — **built, 2026-08-29**, as `:seller` (`com.omaykan.seller`) alongside the customer app in `apps/mobile-android`. It is the small, specific version this section called for: live orders, status advance, rider assignment, payment settlement, today's takings. The till stays a PWA and [plan.md](./plan.md) keeps it that way. See [apps/mobile-android/seller/README.md](../apps/mobile-android/seller/README.md) for the design record.

  Two things this section predicted turned out differently, and both are worth reading before touching it:

  1. ~~**It pairs as a device, not as staff.**~~ **Reversed 2026-09-03.** It did, and every merchant client did: `SellerOrderController` aborted unless `$request->user()` was a `Device`, and sign-in was `store-codes/resolve-staff` then `device-sessions` — one code, off the shop's own paperwork. Pairing has since been retired platform-wide. The order endpoints resolve a signed-in staff user and the store their token names (`StoreContextResolver`), and `payment_confirmed_by_user_id` records the person who tapped. What that cost is a password at six in the morning, which is what the Google button is there to answer.
  2. **It does not use the `store.{id}` channel** — but the reason it could not has gone. That channel authorizes on a `store_memberships` row keyed by **user id** (`routes/channels.php`), and a `Device` had no such row, so the credential the order endpoints demanded was precisely the one the channel refused. The app now holds a token for a user who *does* have that row. It still polls every 15s, with an opt-in `dataSync` foreground service holding the loop open in the background, and switching it to the websocket should now be close to a no-op.
- **Rider app** — **built, 2026-08-29**, as `:rider` (`com.omaykan.rider`), the third application in `apps/mobile-android`. Registration with the two licence uploads, the approval gate as three screens rather than an error, the platform-wide board, accept/advance/release, and map and dial handoff. See [apps/mobile-android/rider/README.md](../apps/mobile-android/rider/README.md) for the design record.

  Three things this section assumed are worth correcting, since they shaped the app:

  1. **No background location, and it is not an omission of nerve.** The API has nowhere to put a coordinate — no column, no endpoint, no consumer. The customer's tracking page reads the delivery *stage*, not a position. Asking a rider for location all day and discarding it would be the worst of both, so `ACCESS_FINE_LOCATION` is not in the manifest. It goes in when the backend can receive it, and that is a backend change first.
  2. **No persistent notifications either, for now.** There is no channel a rider can subscribe to: `store.{id}` authorizes on a `store_memberships` row keyed by user id and a rider has no membership row anywhere, and there is no "job posted" event to broadcast — a board is a query, not an event. So `WorkFeed` polls at 15s, like `:seller`'s. Real alerts need FCM *and* a new backend event, not just a service.
  3. **`core:network` conventions were shared by being ported a third time**, not by a library module. Three copies is the signal that the shared layer can now be designed from examples rather than guessed at; doing it before this app existed would have meant designing it from one.

  The one piece of genuinely new machinery is the **generation counter** in `WorkFeed`: a fetch already in flight when a rider claims a job would, if published, put that job back on a board it has left. Every write bumps a counter, and any fetch that returns into a changed one is discarded and reissued. Neither of the other two apps needs this, because neither has a shared resource two users race for.

---

## 12. Open decisions

1. **Can `pusher-websocket-java` reach Reverb at `/reverb/` at all?** (§8) — and if not, do we move Reverb to its own subdomain, override `buildUrl()`, or change library? This blocks Phase 3 and the subdomain option has DNS and certificate lead time, so it is answered in Phase 0. It is the only item here that can stall a phase.
2. ~~**Do the rider's name and number come off the public order channel?**~~ **Settled 2026-08-28** (§8): the number is served only while the delivery is in flight, on both the broadcast and the public tracking endpoint; the name stays. Kept here so the reasoning is not relitigated.
3. **How hard do we push tier 2?** (§8) — the foreground service is the whole no-Firebase bet. If a persistent "tracking your order" notification turns out to annoy customers more than a missed status update does, the fallback is tier 3 alone (coarser, quieter) or the UnifiedPush escalation (better, more ops). Decide with a real order in hand during Phase 4, not on paper now.
4. **Does the app keep the GCash preference?** The web storefront stopped asking; mobile still does. Parity says drop it; the merchants who liked it say keep it. Either is defensible — but the two clients should stop disagreeing with each other.
5. **Wishlist** — mobile-only today, with nothing server-side behind it. Port as a local-only feature, or drop it with the rewrite?
6. ~~**Multi-store pairing.**~~ **Settled 2026-09-03** by the move off pairing: `/api/staff/sign-in` answers with every shop the account can act for, and the seller app shows a picker when there is more than one. The shopper app never needed it — it browses a directory rather than remembering one shop.
