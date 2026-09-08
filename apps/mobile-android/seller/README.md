# Omaykan Seller

The merchant's phone. Incoming storefront orders, and the four decisions a shop
makes about one while standing away from the till.

This is the app [mobile-plan.md §11](../../../documentation/mobile-plan.md)
describes: *"small and specific — new-order alerts and order acceptance, not the
whole register."* The register stays a PWA. Nothing here syncs a catalog, opens
a shift, counts a drawer or sells to somebody at the counter.

```
apps/mobile-android/
  app/       com.omaykan.storefront   the shopper's app
  seller/    com.omaykan.seller       this one
```

Two applications in one Gradle build, sharing the version catalog and nothing
else. They are two products with two audiences and two listings, and both can
sit on one phone — a shop owner who also buys coffee — which is why the launcher
label is "Omaykan Seller" and not "Omaykan".

## What it does

| | |
|---|---|
| **Live orders** | The store's storefront orders, newest first, refreshing every 15s. Two tabs: **Live** (anything still owed to a customer) and **All**. |
| **Advance an order** | preparing → ready → completed, one button on the card. |
| **Delivery** | Assign a rider — one of the shop's own in a tap, or a name and number typed in — then walk assigned → picked up → delivered. |
| **The shop's riders** | A list the shop keeps. A dot says whether that rider's app is reporting right now, so the merchant can tell "this reaches a phone" from "I am about to make a call". |
| **Where the rider is** | A map in the assign sheet, when a platform rider is carrying the order and has sharing switched on. |
| **Settle payment** | Mark an unpaid order paid: cash, e-wallet or card. |
| **Today's takings** | Gross, order count and what is still unpaid — arithmetic over the list already fetched, not a second endpoint. |
| **New-order alert** | A sound and a heads-up notification when an order arrives, with an opt-in watcher that keeps working while the app is in the background. |

## Sign-in: a person, then a shop

This app used to pair. One code, typed once, off paperwork the shop already had:
no username to remember and no password to type at six in the morning. It was a
good sign-in for a counter, and it is gone for the reason it was good — a secret
everybody at the shop shares cannot put a name on a settled payment, and taking
one leaver's access away meant rotating it and re-pairing every device.

```
POST /api/staff/sign-in       {identifier, password} → pending token + stores[]
POST /api/staff/auth/google   {credential}           → the same
POST /api/staff/session-store {storeId}              → the token everything uses
```

Two calls, because proving who you are does not say which shop you are standing
in. The pending token from the first reaches the store picker and nothing else;
the second mints one carrying `store:{uuid}` in its abilities, which is where
`StoreContextResolver` reads the shop from. Somebody who works at one shop never
sees the picker — the app takes the single answer and goes.

The Google button is the same Custom Tabs + PKCE flow `:app` uses, ported here
(`core/auth/GoogleAuthFlow.kt`) — no Play Services, no `google-services.json`.
It needs its own Android OAuth client, because the console keys one on the
package name and signing certificate and this is a different package: set
`OMAYKAN_SELLER_GOOGLE_ANDROID_CLIENT_ID` and list the id in the backend's
`GOOGLE_ANDROID_CLIENT_ID` / `GOOGLE_EXTRA_CLIENT_IDS`. Blank is a supported
state — the button simply is not drawn, and password sign-in is untouched.

**What this bought, stated because it was the point:** the app knows which
*person* is using it. Settling a payment records them. Removing somebody is one
membership row, and it takes effect on their next request rather than at their
next sign-in — `StoreContextResolver` re-checks on every call. Signing out
actually retires the token, which pairing had no endpoint to do at all.

**What it cost:** a barista now has something to remember at six in the morning.
That is a real cost and the Google button is most of the answer to it. If per-staff attribution is
ever wanted, that is a backend change — accepting a staff `User` on the seller
routes — not an app one.

## Live orders are polled, not pushed

The backend broadcasts every order event on a private `store.{id}` Reverb
channel and the till listens to exactly that. **This app cannot**, and the
reason is worth writing down because it looks like an oversight:

- `routes/channels.php` authorizes `store.{storeId}` on a row in
  `store_memberships` **keyed by user id**.
- This app holds a *device* token. A `Device` has no membership row.
- A device token is what the order endpoints require; a user token is what the
  channel requires. No single credential satisfies both today.

So `OrderFeed` polls every 15 seconds — well inside the API's 60/min, and fast
enough that a shop hears about an order while the customer is still putting
their phone away. It is one loop, shared: `SharingStarted.WhileSubscribed` means
the screen and the watcher service between them run exactly one, and new-order
detection happens once inside it, so no arrangement of the two can double up an
alert or drop one.

## The watcher, and what it is not

An alert that only fires while the merchant is already looking at the list is
worth nothing, and Android stops an activity — and its polling — the moment
another app comes forward. So the notification bell in the top bar starts a
`dataSync` foreground service that holds the feed open.

It is **opt-in**, because polling all day costs battery and a merchant who only
wants to glance at the app should not pay for a watch they did not ask for. The
switch reports the *service's* lifecycle rather than the request, so it turns
itself off when the service ends for any of the reasons it can:

- the Stop action on its own notification
- Android 15's ~6-hour-per-day cap on `dataSync` foreground services
- the system reclaiming memory (the service is `START_NOT_STICKY` — it stays
  stopped rather than resuming behind the merchant's back)
- signing out, or the device token being refused

**This is not push.** Nothing wakes the app when it is closed. Real push needs
FCM, a device-token registry on the backend, and a Google project — see below.

## Building

```bash
cd apps/mobile-android
./gradlew :seller:assembleDebug          # → seller/build/outputs/apk/debug/
./gradlew :seller:testDebugUnitTest
```

Debug talks to `https://omaykan.com` by default, so a debug build is useful on
any phone. Point it elsewhere with `OMAYKAN_API_BASE_URL` —
`http://10.0.2.2:8000` is `php artisan serve` as the emulator sees the host
loopback; cleartext to either needs a matching entry in
`network_security_config.xml`. Release signing reads the same
`keystore.properties` as `:app`, and goes unsigned if there is none.

To try it against the demo data: seed `DemoSellerSeeder` and sign in as one of
its owners — `admin` (demo-coffee), `grocery`, `restaurant` or `salon` — all with
the password `password`. The salon signs in like the rest: sign-in has no
business-mode gate, deliberately.

## Deliberately not here

- **Nothing shared with `:app`.** The theme tokens, `ApiCaller`, the token store
  and now the Google OAuth flow are ported, not imported. Factoring them into a
  library module is a refactor of a shipped app, and doing it before this one has
  settled would mean designing the shared layer from a single example. When the
  second app stops moving, that is the time.
- **No local database.** An order list is the server's answer to "what is
  happening right now", and a cached one is a lie a merchant would act on.
- **No product photos.** The seller knows what they sell. Coil *is* here now,
  for exactly one image — the static delivery map in the assign sheet — and for
  nothing else.
- **No embedded map.** The Mapbox Android SDK adds megabytes and a secret
  download token to every build and every CI run, to draw a pannable map nobody
  wants while standing at a counter. The question here is "is he close", and one
  fetched PNG answers it. The *web* dashboard gets a real interactive map,
  because that screen is where somebody sits down to run the day. See
  `core/map/StaticMap.kt` and
  [live-delivery-tracking.md](../../../documentation/live-delivery-tracking.md).
- **No rider directory.** A shop reaches its own saved riders and the ones who
  have delivered for it, and adds anyone else by a phone number it already has.
  A searchable list of every rider on the platform would be a phone book of
  every rider in the city, readable by anyone who paired a till.
- **No counter sales.** Those ride the till's offline-first outbox; a phone
  showing them would be showing a partial, stale copy of the register's ledger.
  The takings strip says so on screen.

## Next, in the order it is worth doing

1. **Real push (FCM).** The one thing that would let a merchant close the app.
   Needs a device-token registry on the backend, a sender keyed off the existing
   `OrderPlaced` event, and a Google project. It replaces the watcher service;
   it does not sit beside it.
2. **Or, cheaper: let this app hold the websocket.** This got much easier when
   pairing went: `store.{storeId}` in `routes/channels.php` authorizes on a
   `StoreMembership`, and this app now holds a token for a user who has one. It
   should be close to a no-op, and it removes the polling entirely — the endpoint
   it needs, `POST /api/broadcasting/auth`, already exists.
3. ~~**Per-staff attribution.**~~ Done: signing in as a person is what this
   section used to be waiting for.
4. **A sound of its own.** The default notification tone is right for a first
   version and wrong for a busy kitchen.
5. **Factor out the map, the theme, `ApiCaller` and the Google flow.**
   `StaticMap` is the third near-copy across these three apps and the OAuth flow
   is now the second. Three copies is the point at which a shared module stops
   being a guess, and one of these has reached it.
