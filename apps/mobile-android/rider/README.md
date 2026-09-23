# Omaykan Rider

The third side of an order. A job board that spans every shop on the platform,
and the road between the shop and the door.

This is the app [mobile-plan.md §11](../../../documentation/mobile-plan.md)
called *"the strongest native candidate on the platform"* and had not been
built in any form. The API for it was already the fullest on the product —
register with licence uploads, board, accept, stage, release — and had only ever
been driven by the web portal at `apps/web/src/rider`.

```
apps/mobile-android/
  app/       com.omaykan.storefront   the shopper's app
  seller/    com.omaykan.seller       the merchant's order phone
  rider/     com.omaykan.rider        this one
```

Three applications in one Gradle build, sharing the version catalog and nothing
else. All three can sit on one phone — a rider who also buys coffee, a shop
owner who rides at weekends — which is why the launcher label is "Omaykan
Rider".

## What it does

| | |
|---|---|
| **Know what it is** | Three pages on the way in, once: what work there is, what it pays, and what you have to do to be paid. Skipped counts as read. |
| **Apply to ride** | Eight fields and two photographs — licence and plate — through the platform photo picker, which needs no permission. |
| **Wait, and be told why** | Pending, rejected and suspended are three different screens, and the operator's review note is on two of them. |
| **The board** | Every unclaimed delivery on the platform, newest first, refreshing every 15s. What it pays, where from, roughly where to, and what there is to collect. |
| **Take a job** | One tap. Two riders tapping the same row is the normal case and is handled as one, not as an error. |
| **Carry it** | The full address, the customer's name and number, and the basket to check the bag against. |
| **See the trip** | A picture at the top of every job card: the shop, the door, and the road between them once the job is claimed. A still image, not a map — see below. |
| **Navigate and call** | One tap into whichever map app the rider already uses, and one into the dialler. |
| **Advance or hand back** | assigned → picked up → delivered, and a release button that disappears the moment the food is in the bag. |
| **Be on the map** | A switch at the top of the home screen puts the rider's live position on the shop's dashboard and the customer's tracking page. Off by default, and off means erased. |
| **See the shift** | A home screen: what today has paid, how many drops today, what cash is in the rider's hands that belongs to a shop, and the jobs in hand. Earnings has the week, the month, all time and the finished list by day. |
| **Be recognised** | An optional photograph and a description of the bike — type, colour, make, model. The shop reads it at the counter, the customer reads it at the kerb, and both stop after the handover. |
| **Be told about work** | An opt-in background watcher that checks the board every minute and posts a notification per new job, so a rider is not obliged to keep the app open. |
| **See what customers said** | A star average and the remarks behind it, with the customer's name deliberately absent. |
| **Reach a human** | A support number and address, served from the API rather than compiled in, and readable by a rejected rider — the person most likely to need one. |

## Sign-in: this app signs in a person

This app has always done it this way, and as of 2026-09 the other two do too —
`:seller` used to pair with a shop's code and now signs a member of staff in.
The difference that remains is the guard: every route under `/api/rider`
resolves a `Rider` on its own Laravel guard, so the work is scoped to *that
rider* rather than to a tenant, and a rider token can never reach a shop's
endpoints however it was minted.

There was never a code to pair with here and there should not be: a job board
that any phone holding a shop's code could read would be a job board any shop
could raid.

```
POST /api/rider/register   multipart, 4/min   → 201 + token, status "pending"
POST /api/rider/login      10/min             → token
GET  /api/rider/me                            → the account, outside the gate
```

The token is a personal credential, and what it reaches is a live list of
strangers' home addresses and phone numbers — so it lives in
`EncryptedSharedPreferences` behind an Android keystore key, with the backup
and device-transfer exclusions in `data_extraction_rules.xml`. There is a
server-side revoke: `POST /api/rider/logout` retires this one token and leaves
the rider's other phones signed in, so signing out calls it and then clears
locally whether or not it answered. (`:seller` had no such endpoint while it
paired, and gained one with staff sign-in.)

## Signed in and allowed to work are two questions

`EnsureRiderIsApproved` gates everything past `/me` and answers **403 with the
account's status in the body**. That shapes three things in this app:

1. **`SessionState` splits the signed-in half in two** — `Working` and `Gated` —
   rather than carrying a flag, because they are two different apps to the
   person holding the phone: a job board, and a letter about an application.
2. **`ApiCaller` tells two kinds of 403 apart** on the presence of
   `riderStatus`, not on the message text. The gate always sends it;
   `abort_unless` on somebody else's delivery never does. Getting this wrong
   either signs a working rider out of their board or leaves a suspended one
   tapping Accept into a wall.
3. **The interceptor clears the token on 401 and never on 403.** A pending
   rider's token is perfectly good and their account may be approved an hour
   from now; clearing it would sign them out every time they checked.

A suspension that lands mid-shift therefore moves the whole screen: every
repository hands a `Gated` to `SessionRepository.applyGate`, the session flow
turns, and `MainActivity` swaps the board for the status screen with the
operator's note on it.

## The board is polled, not pushed

For the same reason `:seller` polls, and one more.

- `routes/channels.php` authorizes `store.{storeId}` on a row in
  `store_memberships` **keyed by user id**. A rider has no membership row
  anywhere — they are not attached to a shop at all.
- There is no `riders` channel and no "new job" broadcast to put on one. A board
  is a query, not an event.

So `WorkFeed` polls every 15 seconds. One loop, shared,
`SharingStarted.WhileSubscribed`, so it runs while a screen is watching and
stops five seconds after the last one goes.

### The generation counter

The one piece of real concurrency here, and it is worth reading before changing
anything in `WorkFeed`.

A tap that takes a job has to show at once, so `accept` writes the server's
answer straight into the feed. The hazard is a fetch that was **already in
flight** when it did: that fetch started before the claim, its answer is older,
and publishing it would put a job the rider now holds back on the board for up
to fifteen seconds — long enough for a second rider to tap it and lose.

Every write bumps a counter. The loop reads it before fetching and discards any
answer that returns into a changed one, then refetches immediately rather than
waiting out the interval. Cost of being wrong: one extra request. Cost of not
doing it: a board that lies. `WorkFeedTest` holds a fetch open with a
`CompletableDeferred` and asserts exactly this.

## What it looks like

As of 2026-09-11 the app wears the highland look `:app` and `:seller` were
redrawn to the same week, from the "KADAYAW Rider" reference board: welcome,
home, delivery details, on the way, earnings and profile. The three rules are
the other two refreshes' rules — **Omaykan's name, the reference's look, and a
fact the API actually has in every slot**. The slot-by-slot decisions are in
[documentation/design/rider-refresh](../../../documentation/design/rider-refresh/README.md).

One vocabulary, defined in `core/designsystem/` (`Color.kt`, `Type.kt`,
`Brand.kt`, `Components.kt`). None of it is Material's defaults left where they
fell:

- **Three colours, three roles.** Forest `#1E3A2B` is the frame — every header,
  the tab bar, the welcome screen. Terracotta `#B0512E` is the one thing on a
  screen to press: Log in, Take this job, I have picked up the order. The green
  accent (`primary`, `#2E5E43`) is *state* — a switch that is on, the stop a
  rider is heading to, the step a delivery is on. Everything a rider reads sits
  on a cream page or a white card. This is where `:rider` parts from `:seller`,
  which makes terracotta `primary`: here the stock controls that reach for
  `primary` on their own are all state, not actions.
- **Three voices.** The platform sans for everything a rider acts on; Lora for
  the lines meant to be felt (the greeting over the mountains, the sign-off, the
  line under a rider's name); Cinzel for the `OMAYKAN` wordmark only. Both are
  bundled variable fonts in `res/font`, not downloadable ones — those need Play
  Services.
- **The forest bar.** `ForestTopBar` paints under the status bar: back on the
  left when there is somewhere to go back to, a centred title, actions on the
  right. It is flat. The curved green canopy it replaced read as an
  illustration rather than as the app's frame.
- **Cards.** White, 14dp radius, a hairline instead of a shadow. Elevation in
  Material is drawn as a tonal shift, which on the dark ground turns a card grey
  and washes out the money on it.
- **Buttons.** Soft 10dp rectangles, 52dp tall, full width. The one exception is
  the job screen's advance button — the reference's terracotta capsule with a
  white disc at its head. It looks like a slider and is a tap: a swipe would be
  a new gesture to learn on a moving bike, and the server already refuses a
  stage that skips a step.
- **Tinted bands, never coloured text.** Cash to collect is amber on amber, a
  prepaid order is green on green, a failure is red on red, the next thing to do
  is terracotta on peach. Coloured text on white is a contrast problem at small
  sizes in daylight; a soft ground carries the meaning and leaves the words at
  full contrast.
- **The tab bar.** Forest with rounded shoulders, the same bar the other two
  apps wear. All four tabs keep their labels; the current one gets a filled
  icon, a brighter label and a short ember bar above it — a shape, which can be
  seen from a handlebar mount.

### Four tabs, and still no NavHost

Home, Jobs, Earnings, Profile. They are not a stack — they are four views of one
signed-in session — so they are a `rememberSaveable` enum over a tab bar, and
Back from any of them returns to Home rather than walking out to a sign-in
screen the rider is not on. The same argument `MainActivity` makes one level up.

An **open job** is held by `RiderShell` rather than by the Jobs tab, because
Home's active-order cards open jobs too now, and it covers the tab bar while it
is open: a rider in the middle of a job has one thing on screen. The job screen
has two faces over one view model — the details while the food is at the shop,
the map once it is in the bag, either one tap from the other.

`JobDetailViewModel` is scoped to the activity (there is no NavHost to scope it
shorter), so the screen calls `close()` when it leaves composition. Before that
existed, closing a job left the location stream — and the GPS — running until
the app was killed, and opening a second job left the first job's feed
collector writing into the state.

### What fills the reference's slots

| Reference | This app |
| --- | --- |
| "You're Online" toggle | The job-alert switch and the location switch, in one card. There is no online state on this platform, and sharing stays at the top of the first screen so the off switch is always findable. |
| Completion rate | Cash to collect — nothing records a handed-back job as a failure, so there is no rate. |
| Inbox (2) | My jobs, with the count of jobs in hand. Riders have no inbox. |
| COD / Paid online split, filter chips | Deliveries and average per delivery. A finished job's payment status can change after the fact, so a split by it would not be a record. |
| Order item photos and prices | Names and quantities — all the rider's copy of an order carries. |
| "Near You" step | "Arriving", lit only when the phone has a fix within 300 m of the door. |
| Rider ID | The plate. |
| App settings, Safety guidelines | Left out: the only settings are the two switches on Home, and there is no rider code to link to. |

### Looking at it without a backend

The board is empty unless a real shop has a real order out, which makes "does an
offer card look right" a question you otherwise cannot ask without a shop and a
customer. `GalleryActivity` — **debug source set only, so it cannot ship** —
draws every card with made-up jobs on one scrolling screen:

```bash
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen signin
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen register
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen onboarding
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen account
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen home
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen earnings
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen job
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen jobmap
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen finished
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen delivery   # a whole job, played through; --ei speed 12 to hurry
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen forgot
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen map
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen drive
adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen nav
```

`home`, `earnings` and `account` draw the tab bar, because they draw a
signed-in shell — which makes them the ones to reach for when a change touches
navigation rather than a card. `home` can walk to the other tabs from its bar.

`home`, `earnings`, `job`, `jobmap` and `finished` render the production layouts
(`HomeContent`, `EarningsContent`, `JobDetailContent`) with fixture state. They
cannot draw the screens' view models: those need a signed-in session and live
calls, and a gallery has neither. Run any of them under
`adb shell cmd uimode night yes` to check the dark palette. The job samples ask
Mapbox Directions for the real road between the fixture shop and door, so the
map face shows the same route line, turn banner and ETA a live job does.

## Building

```bash
cd apps/mobile-android
./gradlew :rider:assembleDebug          # → rider/build/outputs/apk/debug/
./gradlew :rider:testDebugUnitTest
```

### Tokens

Two Mapbox tokens, and they are not interchangeable.

| | what it is | where it goes |
|---|---|---|
| `pk.…` | public, **runtime** — static map images and Directions | `OMAYKAN_MAPBOX_TOKEN` in the environment, or in `~/.gradle/gradle.properties` → `BuildConfig.MAPBOX_TOKEN` |
| `sk.…` with `DOWNLOADS:READ` | secret, **build-time** — the password on Mapbox's Maven repository | `OMAYKAN_MAPBOX_DOWNLOADS_TOKEN` in the environment, or `MAPBOX_DOWNLOADS_TOKEN` in `~/.gradle/gradle.properties` |

The secret one never reaches an APK — it exists so Gradle can download the Maps
SDK artifacts. It is read in `settings.gradle.kts`, which declares the Mapbox
repository **only when a token is present**: a checkout without one builds
exactly as it did before, rather than meeting a 401 against a host it is not
trying to use. `RepositoriesMode.FAIL_ON_PROJECT_REPOS` is why that declaration
lives in the settings file and not in `rider/build.gradle.kts`.

Put the secret token in `~/.gradle/gradle.properties`, never in this project's
`gradle.properties` — that file is tracked, and a secret in it is a secret in
the history.

The public one belongs there too. Until 2026-09-08 the build read it *only* from
the environment (`providers.environmentVariable`), which meant it lived exactly
as long as the shell that exported it: every new terminal built an APK with a
blank token and no map on any card, and nothing failed to say so. `buildSecret()`
in each module's `build.gradle.kts` now reads the environment first and falls
back to a Gradle property of the same name, so one line in
`~/.gradle/gradle.properties` settles it for good:

```properties
OMAYKAN_MAPBOX_TOKEN=pk.…
```

The environment still wins, so `OMAYKAN_MAPBOX_TOKEN=pk.other ./gradlew …` and CI
are unchanged.

Debug talks to `https://omaykan.com` by default, so a debug build is useful on
any phone. Point it elsewhere with `OMAYKAN_API_BASE_URL` —
`http://10.0.2.2:8000` is `php artisan serve` as the emulator sees the host
loopback; cleartext to either needs a matching entry in
`network_security_config.xml`. Release signing reads the same
`keystore.properties` as the other two, and goes unsigned if there is none.

To try it against real data you need a rider the operator has approved and an
order on the board. Seed `DemoSellerSeeder`, register through the app, then
approve the account from the platform admin's rider review — and place a
**delivery** order from a storefront, since the board only ever carries
`fulfillment_method = delivery` at `delivery_stage = pending`.

## A face, a bike, and a score

Added 2026-09. Three features that all answer one question the app could not
answer before — *who is this person on my doorstep* — plus the one that answers
it back.

```
riders.avatar_path        private disk, streamed via /api/riders/{id}/avatar
riders.vehicle_type       closed set: motorcycle scooter tricycle bicycle ebike car
riders.vehicle_{make,model,color}   free text; the long tail does not fit a dropdown
rider_ratings             one row per delivered order, unique on order_id
```

**The photograph is optional and stays optional.** Most riders will never upload
one, and the initial-letter disc the app has always drawn is the supported
fallback rather than a placeholder waiting to be replaced. Nothing in the
product requires a face.

**It is on the private disk, with the licence, for a different reason.** The
licence is private because it is an identity document. This is private because
a directory-listable folder of every rider's face is a scraping target, and
because a rider who removes their photo needs it to actually stop resolving. It
is streamed through a route keyed by the rider's UUID — the same arrangement
`StoreImageController` uses for a shop's photo.

**The customer's copy is stage-gated; the shop's is not.** `Order::riderProfileForCustomer`
withholds the whole block outside `assigned` and `picked_up`, for the reason the
phone number is withheld: the tracking view is public by UUID and that link is
*meant* to be forwarded, so anything left in the payload afterwards is readable
by everyone who was ever sent it. A photograph of somebody's face is the most
personal thing this application could leave there. The shop's copy is ungated
because a merchant is a party to the delivery for its whole life.

That the gate also makes the feature *work* is a coincidence worth noting: a
customer needs to know what the rider looks like in exactly the window where the
answer is on its way to them.

**A rating is a receipt, not an opinion.** `order_id` is unique, there is no
update route, and the four conditions are checked in
`RiderRatingController::store` — the order is the customer's, it was delivered,
it had a rider with an account, and nobody has rated it. An unrated rider's
average is **null and never 0.0**: rendering a first-day rider as zero out of
five would put the worst possible number on the screen of the person least able
to have earned it. Under five ratings the app says so rather than implying the
average means anything yet.

The rider reads their scores back without the customer attached. A rider seeing
"3 stars — slow" against a name and an address they delivered to an hour ago is
the setup for a confrontation this feature should not create.

## Sharing a position

The omission this README used to open its "deliberately not here" list with. The
backend can receive a coordinate now (`POST /api/rider/position`), so the app
can ask for one.

```
LocationShareService     foreground, typed `location`, opt-in
      │
      │  every 10s while carrying, 60s while not — the *server* says which
      ▼
POST /api/rider/position  →  {recorded, activeDeliveries, nextPingSeconds}
```

**It is a switch, not a consequence of taking a job.** The tempting design
starts sharing when a rider accepts something: fewer taps, and the map always
works. It is also the design where a person finds out their employer's app has
been reporting their position all afternoon because of a button they pressed for
a different reason. So the switch sits at the top of the work screen, starts
off, and the line under it makes the platform's case rather than assuming it.

Four things keep that honest, and none of them should be quietly removed:

- The service is typed **`location`**. On API 34+ that is what grants the sensor
  in the background *and* what makes Android show the location indicator in the
  status bar. The rider's own phone tells them this app is reading their
  position, and this app cannot turn that off.
- The notification carries **Stop**, so it can be ended from wherever the rider
  is without finding the app.
- `START_NOT_STICKY`. A service the system killed stays dead. Silently resuming
  a location feed nobody re-enabled is what makes people distrust an app.
- Switching off calls `DELETE /api/rider/position`, which nulls the columns.
  **Off means gone, not frozen.**

`LocationShareController.sharing` is reported by the service's own lifecycle
rather than by whoever flipped the switch — the same argument `:seller`'s
`OrderWatchController` makes, and it matters more here, because the person
misled by a wrong switch is not the one holding the phone.

`PositionReporter` is where the three rules that keep this cheap live — the
server sets the cadence, a fix that says nothing new is not sent, and a fix
worse than 200m is not sent at all. `PositionReporterTest` holds each one.

`LocationManager`, not `play-services-location`, for the same reason there is no
Maps SDK: a rider's phone is often a cheap phone with no Google services on it,
and an app that will not report a position on those does not work for a chunk of
the people it is for.

**Nothing is retained server-side.** Six columns on `riders`, overwritten every
ping. There is no trail.

## Deliberately not here

- **No background location.** Location itself is now here — see below — but
  `ACCESS_BACKGROUND_LOCATION` is not requested anywhere and should not be. The
  feed runs from a foreground service with a visible, stoppable notification,
  which is the arrangement a rider can actually see and end. The all-day
  background grant is a much bigger ask and this feature does not need it.
- **No embedded map, still.** `geo:` handed to whatever the rider already uses
  beats a Maps SDK that needs an API key, a billing account and a
  Google-flavoured phone — and beats it worst on a rider's own phone, which
  already has offline tiles for a city they know better than we do.

  What job cards got in 2026-09 is a *picture*, not a map: one Mapbox static
  image per card, drawn from the same reasoning `:seller` wrote down first (see
  `core/map/StaticMap.kt`). It does not pan, zoom or rotate, because a rider
  deciding whether to take a job is not doing any of those — they are asking
  "is this on my way", and a still answers that in the time it takes to glance.
  Navigation is still a handoff and should stay one.

  The board's card deliberately draws less than the claimed job's: the shop and
  the rider, with no route and no door, because `RiderDeliveryController::asOffer`
  withholds the drop-off coordinates until a job is claimed. Drawing a line to a
  guessed point would put a precise-looking answer on screen exactly where the
  server chose to be vague.

  Blank `OMAYKAN_MAPBOX_TOKEN` is a supported build: no image, and every card is
  the card that shipped before there was one.
- **No push, and the watcher is the cheap version.** As of 2026-09 there *is* a
  background watcher — an opt-in foreground service that polls the board every
  60s and posts a notification per new job (`core/alerts/`). It is deliberately
  not push: FCM needs a device-token registry the backend does not have and a
  "job posted" event that does not exist, since today there is only
  `OrderPlaced` on a store channel that no rider is subscribed to.

  Two honest limits come with that. Alerts stop when the service does, and
  `START_NOT_STICKY` means a system kill or a reboot ends them silently — the
  same choice `LocationShareService` makes, and for the same reason. This
  narrows the gap between "app open" and "nothing at all"; it does not close
  it.
- **No cancellation count, and no company balance.** Both are on every
  competitor's rider dashboard and neither has anything behind it here. A rider
  who hands a job back is not recorded as having failed — the row goes to
  somebody else, which is the system working — and there is no platform cut, so
  there is no balance to owe and nothing to withdraw. The home screen's second
  card is cash *to collect at doors*, summed over the jobs in hand, which is a
  physical fact about a pocket rather than a ledger. It offers no Settle button
  for the same reason: there is no endpoint that could honour one.

- **No earnings report beyond `/earnings`, and no statistics screen.** The completed list is the
  server's last 30 and is history, not accounting. Totals, week-on-week
  comparisons, active days and an average ETA all need an aggregates endpoint
  that does not exist, and a customer rating needs a feature that does not
  exist at all — `RiderReviewController` is the operator approving licences,
  not customers scoring rides.
- **Nothing shared with `:app` or `:seller`.** The theme tokens, `ApiCaller` and
  the session store are ported, not imported — the third copy of each, which is
  the point at which factoring them into a library module stops being a guess.
  That refactor is now worth doing, and it was not before.
- **No local database.** A board is the server's answer to "what is available
  right now", and a cached one sends a rider to a shop for an order somebody
  else took twenty minutes ago.

## Next, in the order it is worth doing

1. ~~**A watcher**~~, then **push**. The watcher shipped in 2026-09 — an opt-in
   `dataSync` foreground service over the same board feed, with the switch
   beside the location one on the home screen. What is left is FCM, which still
   needs a device-token registry on the backend and an event to send: there is
   no "job posted" event today, only `OrderPlaced` on a store channel.
2. ~~**Location, once the backend can take it.**~~ Done — see *Sharing a
   position*, below, and
   [live-delivery-tracking.md](../../../documentation/live-delivery-tracking.md).
   What is left of it is an **ETA**, which needs a routing call and a decision
   about what to show when traffic makes the number wrong.
3. **Factor out `core:network` and the theme.** Three copies is the signal.
4. **Earnings.** A day and a week total, which needs the API to sum a rider's
   delivered fees — the app must not, or two riders will compute different
   answers from different page sizes.
