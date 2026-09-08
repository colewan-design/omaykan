# Omaykan Storefront — Android

The customer app, in Kotlin. It replaces the two Capacitor shells that were
deleted on 2026-08-26 and talks to the Laravel API like any other client.

The plan behind it is `documentation/mobile-plan.md` on the
`docs/commission-free-positioning-and-reverb` branch — it has never been merged
to `main`, so read it with
`git show origin/docs/commission-free-positioning-and-reverb:documentation/mobile-plan.md`.

**No Firebase, in any form.** Not Firestore, not Cloud Messaging, not Analytics,
not Crashlytics. There is no `google-services.json` and no Google Services Gradle
plugin, so nobody can re-add Firebase by dropping a file in. Everything the
server does for this app is Laravel: HTTP for reads and writes, Reverb for live
updates, queues for anything that must not block a request.

## Running it

```bash
cd apps/mobile-android
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM tests
```

You need a JDK — 17 or newer, either works — and an Android SDK with platform 36.
`local.properties` points at the SDK and is not in git; create it with
`sdk.dir=C\:/path/to/Android/Sdk` (forward slashes, or escaped backslashes — it
is a Java properties file, and `C:\Users\…` silently becomes `C:UsersA…`).

The debug build points at `http://10.0.2.2:8000`, which is the emulator's view of
`php artisan serve` on the host. For a physical phone on the same LAN, set
`OMAYKAN_API_BASE_URL` in your environment before building. Release builds point
at `https://omaykan.com` and cleartext is refused everywhere except loopback —
see `res/xml/network_security_config.xml`.

## Layout

One Gradle module, with the package tree the plan's module split describes:

```
com.omaykan.storefront
  core/model          domain types — no serialization, no Room annotations
  core/network        Retrofit, DTOs, ApiException, error mapping
  core/database       Room entities, DAO, the catalog cache
  core/data           repositories — the only thing features depend on
  core/designsystem   theme, tokens, shared composables
  feature/shops       the market: which shops there are
  feature/catalog     shelf, search, product detail
  navigation          type-safe destinations
```

The rule the split exists for holds regardless of module count: **features never
see a DTO.** Repositories map network and Room into `core/model`, so a catalog
shape change stays one file's problem. Splitting into eleven Gradle modules buys
parallel compilation that a project this size does not yet need; the seam is the
package boundary until it does.

## What is built (Phase 1)

- The market — `GET /api/stores`, searchable, debounced against the 60/min limit
- Catalog — `GET /api/storefront/catalog`, with categories, search, and a Room
  cache that renders instantly and refreshes behind itself
- Product detail, read straight from that cache
- The last shop visited is reopened on launch, pushed onto the market so Back is
  always a way out

## What is deliberately absent

- **Cart, accounts, checkout** — Phase 2. There is no disabled "Add to cart"
  button in the meantime: a control that cannot be pressed reads as broken
  rather than unfinished.
- **An auth interceptor.** Nothing here authenticates yet. It arrives with the
  Sanctum token in Phase 2, and the token belongs in `EncryptedSharedPreferences`
  — not in the DataStore that holds the paired shop, which is plaintext on disk.
- **Location.** `GET /api/stores` sorts by distance when given a pin and fills in
  `distanceKm`; the card renders it when present and hides it otherwise. Phase 1
  sends no pin.
- **An offline write queue.** There is nothing to queue yet, and when there is,
  there still will not be — see §7 of the plan.

## Two things the plan assumes that `main` does not have

Both were true on the branch the plan was written against. Check before building
against them:

1. **`GET /api/storefront/catalog` takes no `lat`/`lng` on `main`** and returns no
   `delivery` block or nearest-branch list. The Room cache is keyed on
   `orgSlug/storeCode` alone for that reason. Adding the pin later means a new
   cache key, which the destructive-migration fallback already handles.
2. **`POST /api/online-orders` does not require an account.** The controller reads
   `$request->user('customer')` optionally and fills contact details from the
   account when there is one. Checkout in Phase 2 must therefore be guest-first,
   with sign-in offered as a convenience — not the login wall §5 of the plan
   describes.

`GET /api/app-releases/{slug}` still does not exist. The client is written
against its spec and treats a 404 as "nothing published", so it is safe to ship
ahead of the endpoint.

## Signing

Package names, certificate fingerprints, and which Google OAuth client goes with
which build are in [SIGNING.md](SIGNING.md).


Release signing reads an untracked `keystore.properties` beside
`settings.gradle.kts` (or wherever `KEYSTORE_PROPERTIES` points), with
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it the release
variant builds unsigned rather than failing, so a fresh checkout can still run
the whole build.

Nothing has been published to Google Play, so the `applicationId` and the signing
key are both still free choices. The keystore rescued from the deleted Capacitor
app sits at `C:\Users\ASUS\omaykan-mobile-keystore-backup\` (alias `colepos`) —
inherit it or generate a fresh one, but back up whichever, because it becomes
irreplaceable the day it reaches Play.
