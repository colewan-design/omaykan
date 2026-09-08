# Live delivery tracking, and the shop's own riders

Two features that arrived together because they are the same conversation from
two sides: *who* carries an order, and *where they are* while they carry it.

Written against `main` on 2026-09-02, when both shipped.

---

## 1. What changed, in one page

| | Before | Now |
|---|---|---|
| **Dispatch** | An order sat on a platform-wide board until any approved rider tapped it. A shop could instead type a rider's name and number, fresh, for every single order. | The board is unchanged and is still the default. A shop can also keep riders on file and assign one in a tap — and a typed-in rider can be saved on the way past. |
| **A shop's own rider** | Two strings, retyped four times a day, with a typo in the phone number about as often as you would expect. | A row in `store_saved_riders`, ranked by use. If their number matches a platform account the row links to it, and the order lands in that person's app. |
| **Where the rider is** | Nowhere. No column, no endpoint, no consumer. The customer's tracking page read the *stage* — "on the way" — and nothing else. | A live position on a Mapbox map for the customer and the shop, updated every ten seconds while the rider has sharing switched on. |
| **Undo** | None. A shop that assigned the wrong person typed over them. | `DELETE …/rider` puts the order back on the board, refused once the food is picked up. |

Nothing here is compulsory. A shop with no rider of its own uses the board
exactly as before; a rider who never turns sharing on is still a working rider;
a deployment with no Mapbox token shows every screen the way it looked last
week. Each of those is a supported state with a test behind it, not a
degradation.

---

## 2. Why the shop gets to choose

The aggregator model — post every order to an open pool, let the fastest tap
win — is the right default and the wrong mandate.

A carinderia in Baguio has a nephew with a tricycle. They are not going to hand
their orders to a stranger, they are not going to pay for the privilege, and a
platform that makes them is a platform they leave in the first week. That shop's
delivery problem is not "find me a rider", it is "stop making me retype Kuya
Jun's number".

So the platform supports both and forces neither:

- **The board.** Do nothing. Any approved rider claims it
  (`RiderDeliveryController::accept`), and the claim is a conditional `UPDATE`
  so two riders tapping the same row a second apart resolve to exactly one.
- **The shop's own rider.** Pick from a saved list. The order comes *off* the
  board the moment it is assigned, so a shop that has chosen its rider does not
  have the job taken out from under them mid-decision.

### There is no rider directory, on purpose

The obvious way to build "pick your rider" is a searchable list of every
approved rider. That endpoint is a phone book of every delivery rider in the
city, readable by anyone who ever paired a till — including anyone who signs up
as a merchant to get it. The riders never agreed to that.

A shop can reach exactly two sets of riders:

1. **The ones it saved.** Deliberate, one at a time.
2. **The ones who have already delivered for it.** The shop learned that name
   and number when the rider turned up at the counter. Showing it back to them
   discloses nothing new, and it is the list they actually want.

Adding anyone else is **by phone number**, not by browsing. The shop types the
number of somebody they already know; if it belongs to an approved account the
row links to it, and if it does not the row is saved off-platform and the shop
rings them. Either way the shop had to already know the number, which is the
property that makes it safe. Numbers are matched on their last ten digits, so
`0917 555 0101` and `+639175550101` are one person.

See `SellerRiderController` — the argument is written at the top of the file,
where somebody about to add a `?search=` parameter will read it.

### On-platform and off-platform riders

`store_saved_riders.rider_id` is nullable, and that nullability is the whole
model:

| | `rider_id` set | `rider_id` null |
|---|---|---|
| Who | An approved platform account | Somebody's cousin with a tricycle |
| Assigning writes | `orders.rider_id` — the job appears in their app | `rider_name` / `rider_phone` only |
| They can | Release it back to the board, advance stages from their phone | Nothing; the shop moves the stage |
| Live map | Yes, when they are sharing | No — there is no app to report from |
| The shop | Assigns and forgets | Assigns and rings them |

The second column is most of the beachhead and must keep working. It does.

`name` and `phone` on the saved row are what *this shop* calls them, and are
never overwritten from the account: "Kuya Jun" beats the legal name on a licence
when you are shouting across a kitchen.

---

## 3. Where the rider is

### The shape of the feature

```
rider's phone                    backend                     watchers
─────────────                    ───────                     ────────
LocationShareService
  (foreground, typed
   `location`, opt-in)
      │
      │  POST /api/rider/position          riders.last_lat/lng/…
      │  {lat, lng, headingDeg,     ──▶    overwritten in place
      │   speedKph, accuracyM}             (no history kept)
      │                                          │
      │  ◀── {nextPingSeconds}                   │ one event per
      │      10 carrying / 60 idle               │ ACTIVE ORDER
      │                                          ▼
      │                              RiderPositionUpdated
      │                                ├─ private store.{storeId}  ──▶ POS dashboard
      │                                └─ public  order.{orderId}  ──▶ customer's page
```

### One event per order, never per rider

That single line is the entire privacy model.

A rider carrying two orders for two shops produces two events on two pairs of
channels. Neither shop learns about the other's delivery. A rider who is signed
in but carrying nothing produces no events at all — the fix is still recorded,
so they are placeable the instant they take a job, but nobody is told and nobody
can see them.

No new channel and no new authorization rule were needed: `store.{id}` and
`order.{uuid}` already exist and are already scoped correctly, because the
position is scoped by *which order it is attached to*.

### Who may see a position

| Watcher | While assigned / picked up | After delivery |
|---|---|---|
| The customer, via the tracking link | Yes | **No** — `Order::riderPositionForCustomer` returns null |
| The shop, via the dashboard | Yes | Last known fix, frozen |
| Anyone else | No | No |

The customer's gate is the same one that already governs the rider's phone
number, for a stronger version of the same reason. The tracking view is public
by UUID and that link is *meant* to be forwarded — to a flatmate, to whoever is
actually home. A phone number readable forever afterwards is a nuisance; a live
position readable forever afterwards is a person's movements for the rest of
their shift, handed to everyone who ever saw the link.

The shop's copy is ungated because the merchant is the other party to the
delivery for its whole life and needs the last position when chasing a late
order. There is no ongoing exposure in that: a rider only reports while carrying
something, so "after delivery" is a frozen fix, not a live trace.

### Nothing is retained

Six columns on `riders`, overwritten by every ping. Not a `rider_positions`
history table.

A live map wants exactly one thing — where this rider is *now*. History is a
different feature with different retention questions attached (how long do we
keep a trace of a person's movements, and who may read it), and the cheapest
honest answer today is that we keep no trace at all. A breadcrumb table can be
added the day something actually needs to read a breadcrumb.

`DELETE /api/rider/position` nulls the columns, and the rider app calls it when
sharing is switched off and on the way out of sign-out. **Off means gone, not
frozen.**

### Why sharing is a switch

The tempting design starts sharing automatically when a rider accepts a job.
Fewer taps, and the map always works. It is also the design where a person finds
out their employer's app has been reporting their position all afternoon because
of a button they pressed for a different reason.

So: a switch on the work screen, off by default, above the job list rather than
buried in settings. What the platform can fairly do is make the case, which is
the line under it — sharing is what stops the shop ringing to ask where you are.

The three things that keep that promise honest:

1. The foreground service is typed **`location`**, not `dataSync`. On API 34+
   that is what grants sensor access in the background, and it is what makes
   Android show the location indicator in the status bar. The rider's own phone
   tells them this app is reading their position, continuously, and the app
   cannot turn that off.
2. The notification carries a **Stop** action, so a rider can end it from
   wherever they are without finding the app first.
3. `START_NOT_STICKY`. A service the system killed stays dead. Silently resuming
   a location feed the rider did not re-enable — after a reboot, an OOM kill —
   is the behaviour that makes people distrust an app like this.
4. `ACCESS_BACKGROUND_LOCATION` is **not** requested anywhere. The foreground
   service with its visible notification is the arrangement a rider can actually
   see and stop.

The switch reads its state from the *service's* lifecycle, not from whoever
flipped it (`LocationShareController`) — a foreground service can end without
anybody asking, and a switch wired to the request would say "sharing" while a
customer watched a marker that had not moved in twenty minutes.

### What keeps it from flattening a battery

`PositionReporter`, three rules, each with a test in `PositionReporterTest`:

1. **The server sets the pace.** Every ack carries `nextPingSeconds` — ten while
   carrying, sixty while not. Cadence is a platform decision and can be changed
   for a large fleet without shipping an APK.
2. **A fix that says nothing new is not sent.** Standing at a shop waiting for
   an order is most of some deliveries. Movement past ~10m is the test.
3. **A vague fix is not sent at all.** Accuracy worse than 200m is a cell tower;
   drawing it puts a rider's marker in someone else's barangay, which is worse
   for everyone than a map that says "last seen four minutes ago".

Failure is quiet. A phone goes through a tunnel on every delivery in this city;
three consecutive failures is the threshold for telling the rider anything.

### `LocationManager`, not the fused provider

`play-services-location` is a better provider and is not used. A rider's phone
is a cheap phone, often with no Google services on it at all, and an app that
will not report a position on those is an app that does not work for a chunk of
the people it is for. The platform `LocationManager` is on every Android device
ever made. Both GPS and NETWORK providers are requested and whichever answers is
used — a 100m network fix in a basement beats nothing.

This is the same reasoning that kept the Maps SDK out of this app.

---

## 4. The map

### Two implementations, on purpose

| Surface | What it draws | Why |
|---|---|---|
| Web — customer tracking, POS dashboard | A real interactive Mapbox GL map: pins, an eased rider marker, the driving route from the Directions API | These are pages somebody sits at. The dashboard is where a merchant runs the day. |
| Android — `:app` order screen, `:seller` rider sheet | One fetched PNG from the Mapbox Static Images API | The question at a counter or in a kitchen is "is he close", and the honest shape of that answer is one glanceable picture. The Maps SDK adds megabytes and a secret download token for pan-and-zoom nobody wants there. |

`packages/core/src/components/LiveDeliveryMap.vue` is the web one, and it is
shared between customer and merchant deliberately: they want the same three
things on screen, and two versions would drift until the two parties to one
delivery were looking at different pictures while on the phone to each other.
What differs is only what is passed in — and the privacy rule that decides that
lives in the API, not in a Vue component.

### The details that make it read as live

- **The marker eases between fixes.** Positions arrive every ten seconds;
  snapping makes a rider look like they teleport once per breath. An ease-out
  over ~900ms makes the same data read as a vehicle on a road. This is the
  single change that makes the map feel like the one people already know.
- **A jump over 2km snaps instead.** That is a phone coming out of a dead spot,
  not a bike, and animating it draws a straight line across the city at an
  impossible speed.
- **The route is the real road.** A straight line between shop and door is a lie
  in a city built on a mountain: it crosses ravines, and the "five minutes away"
  it implies is fifteen. The Directions API is asked once per pair of endpoints
  and cached; a failure falls back to a straight line drawn **dashed**, because
  solid would read as a route the rider is following.
- **The map does not refit on every ping.** A map that reframes itself every ten
  seconds cannot be panned by the person watching it. It follows the rider only
  when they leave the view.
- **A stale rider stops pulsing and goes grey.** A stopped marker must never
  look like a moving one, and the caption says "last seen 8 minutes ago" rather
  than showing an empty map — which is the far more useful answer to somebody
  waiting.
- **Distances are labelled "in a straight line".** Quoting road distance from a
  haversine is how "2 minutes away" becomes a complaint.

### Setting up Mapbox

The web build needs a **public** (`pk.*`) token:

```
# apps/web/.env.production
VITE_MAPBOX_TOKEN=pk.…
VITE_MAPBOX_STYLE=mapbox://styles/mapbox/streets-v12   # optional
```

It is compiled into the browser bundle, which is what public tokens are for —
the protection is the **URL restriction** set on the token in the Mapbox
account, not secrecy. Add the deployment's domains there. A secret (`sk.*`)
token must never go in a `VITE_` variable.

The Android builds take theirs at build time:

```bash
OMAYKAN_MAPBOX_TOKEN=pk.… ./gradlew :app:assembleRelease :seller:assembleRelease
```

**Use a different token for mobile.** A native app cannot be protected by the
URL restrictions that guard the web build, so the sensible arrangement is a
second token scoped to styles and static images and nothing else, which can be
rotated without touching the website.

Blank is a supported build everywhere. No token means no map and the stage in
words, which is what every one of these screens showed before this feature
existed.

`mapbox-gl` is behind a dynamic import and lands in its own chunk — the
register, the catalog and the whole storefront never pay for it.

### What it costs

The web map is tiles and one Directions call per endpoint pair. The Android
maps are one static-image request per distinct picture, fetched **only while
the sheet or screen showing it is open**, with Coil's cache absorbing a rider
who has not moved. A map that refreshed in the background for every delivery on
a list would spend a month's free tier in a week.

---

## 5. The endpoints

```
# Rider — behind auth:rider + rider.approved
POST   /api/rider/position          60/min   {lat, lng, headingDeg?, speedKph?, accuracyM?}
                                           → {recorded, activeDeliveries, nextPingSeconds}
DELETE /api/rider/position                  → forgets the last fix

# Seller — behind auth:sanctum + merchant.token, scoped to the device's store
GET    /api/seller/riders                   → {saved: [...], recent: [...]}
POST   /api/seller/riders                   {name, phone?, note?, riderId?}
PATCH  /api/seller/riders/{savedRider}
DELETE /api/seller/riders/{savedRider}
POST   /api/seller/online-orders/{order}/rider
                                            {savedRiderId} | {riderName, riderPhone?, saveRider?, saveNote?}
DELETE /api/seller/online-orders/{order}/rider   → back on the board

# Customer — unchanged route, two new fields
GET    /api/online-orders/{order}           → … riderPosition, route
```

`riderPosition` carries `stale` and `ageSeconds` **computed server-side**, so a
phone with the wrong clock cannot declare a live rider missing or a missing one
live.

### Realtime

`RiderPositionUpdated` is the one event in this codebase that implements
`ShouldBroadcastNow` rather than queueing. Two reasons, both from the fact that
it fires every ten seconds per active delivery rather than a handful of times
per order:

- `QUEUE_CONNECTION=database`, so queueing would write and delete a `jobs` row
  for every ping of every rider on the platform — a table churning at the rate
  of the whole fleet to move two floats.
- A position is only interesting while it is current. A ping that waits behind a
  slow job is worse than one that is dropped, because the map draws it as where
  the rider is *now*.

The customer's web page now subscribes to the public `order.{uuid}` channel
(`packages/core/src/realtime/publicOrderChannel.ts`). The backend had always
broadcast there; nothing on the web had ever listened, which is why the tracking
view only ever knew what it knew at page load. Where Reverb is not configured
for a build, it polls every twelve seconds instead — slower, still moving.

The POS dashboard's existing store-channel subscription now passes the **event
name** to its handler, because `rider.position` must patch one field rather than
trigger a full order reload. Reloading the list six times a minute per rider
would rebuild every card while the merchant was reading one.

---

## 6. Tests

| File | What it protects |
|---|---|
| `RiderPositionApiTest` | The gates, all of them negative: only while carrying, only to the two parties, nothing after handover, nothing for a typed-in rider, off means erased. Plus that a stale fix is served *as stale* rather than hidden. |
| `SellerSavedRiderApiTest` | That a shop cannot bind a rider it has never worked with by id; that another shop's rows are invisible and undeletable; that a saved platform rider's order actually reaches their app and leaves the board; that a suspended rider is refused with a reason. |
| `PositionReporterTest` | The three battery rules, each one separately, plus the two below. |
| `WorkFeedTest` | Unchanged — the board's generation counter, which the position feed does not touch. |

---

## 7. Two things only a device found

Both shipped green on unit tests and both were wrong on a real phone. They are
recorded here because neither would have been caught by any test that did not
have a sensor and a merchant's thumb behind it.

**The reporter always sent the cached fix.** `LocationSource.fixes()` emitted
the system's last-known location as the flow's first value — meant as "put a
marker up while the GPS warms" — and `PositionReporter` takes one fix per tick
with `.first()`. So the cached value won every race and the app reported
wherever the phone was last seen, forever, looking entirely correct while doing
it. On the emulator that meant a rider in Baguio reporting from Mountain View.

The fix splits the two questions: `fixes()` is live updates only, `lastKnown()`
is the cache, and the reporter waits `FRESH_FIX_WAIT_MS` for a real fix before
falling back. Two tests now hold it — a live fix beating a cached one, and a
silent sensor still reporting something rather than nothing.

**The seller could not reach the map.** The rider sheet — which is where the map
and the *put it back on the board* button live — opened from an "Assign rider"
action that only exists while `order.needsRider`. The moment a rider was
assigned the button became the delivery-stage button, and the sheet was
unreachable for the rest of the order's life. The web dashboard was fine, since
its Track button sits on the row; the Android app had stranded both controls
exactly when a merchant would want them.

A **Track** action now sits beside the stage button for any assigned delivery,
labelled *Change rider* when there is no position to show.

## 8. What is deliberately not here

- **No breadcrumb trail.** See §3. Nothing to subpoena, sell, or leak.
- **No ETA.** An arrival time is a promise, and a promise computed from a
  straight line over Baguio's terrain is a promise the platform will break. It
  needs the Directions API's own duration, per leg, and a decision about what to
  show when traffic makes it wrong. The map says where the rider is and how far;
  that is a fact rather than a forecast.
- **No push to the rider.** A rider still has to have the app open to hear about
  a new job — the standing item at the top of `rider/README.md`'s list, and
  unchanged by this work.
- **No auto-assignment.** Nothing picks a rider for a shop. The board is
  first-tap; the saved list is the shop's choice. Distance-based dispatch needs
  rider positions to be trustworthy at rest, which they are not while sharing is
  opt-in — and making it compulsory to get there is the trade this document
  spends §3 arguing against.
- **No shared map module across the three Android apps.** `StaticMap` is now
  the third copy of a small idea, after the theme tokens and `ApiCaller`. That
  is the signal `rider/README.md` already names: at three copies a shared
  module stops being a guess. It is on the list, with them.
