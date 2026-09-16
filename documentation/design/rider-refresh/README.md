# Rider app refresh

The rider's Android app (`:rider`, `com.omaykan.rider`) redrawn to the
"KADAYAW Rider" reference board supplied in chat on 2026-09-11: welcome/login,
home, delivery details, on the way, earnings (two states) and profile. It is the
same reference family `:app` (`../customer-refresh`) and `:seller`
(`../seller-refresh`) were redrawn to the same day. The board was pasted into
the conversation rather than saved, so it is not in this folder.

This supersedes the 2026-09-10 pass (flat green headers on white), whose notes
and screenshots are kept in `2026-09-10/`.

## Decisions

The three the other two refreshes took, applied here without change:

1. **Omaykan's name, the reference's look.** Layout, palette, type and ornament
   are the reference's; the name, wordmark, launcher label and copy are
   Omaykan's. "KADAYAW Rider" is set as `OMAYKAN Rider`.
2. **Same layout, real data.** Every slot the reference fills with something
   the API does not have holds a fact the API does have. No completion rate, no
   inbox, no payout split, no rider ID.
3. **The existing app's behaviour is untouched.** Sign-in, the board, claiming,
   stages, release, location sharing, job alerts, the account panels and their
   view models work exactly as before. What moved is layout and where things
   are reached from.

## Design system

Ported from `:seller` (itself ported from `:app`), not shared — see the rider
README's "Nothing shared with :app or :seller".

- **Palette** (`core/designsystem/Color.kt`): forest `#1E3A2B` frame, cream
  `#F7F2EA` page, white cards, terracotta `#B0512E` for the one action a screen
  wants pressed, peach behind figure icons, gold for the sun in the mark. Unlike
  `:seller`, Material's `primary` is a green accent (`#2E5E43`, sage `#9CCBA8`
  in dark) rather than terracotta, because in this app the controls that reach
  for `primary` by themselves — the checked switch, the delivery stepper, a
  spinner — are all *state*, not actions. Full dark variant.
- **Type** (`Type.kt`): platform sans for everything acted on; Lora for the
  greeting, quotes and sign-off; Cinzel for the wordmark only. Bundled variable
  fonts (`res/font`, SIL OFL), not downloadable ones.
- **Pieces** (`Brand.kt`): `Wordmark`, `RiderLockup`, `MountainMark`,
  `MountainBackdrop`, `WovenBand`, `ForestTopBar`, `SIGN_OFF`. `Components.kt`
  keeps its old vocabulary with new shapes (14dp cards, 10dp buttons) and loses
  the money cards, overview tiles and canopy stats that nothing draws any more.
- **Photographs** — four generated assets supplied on 2026-09-11 (sources in
  `Downloads/omaykan rider images`), encoded to webp in `res/drawable-nodpi`:

  | File | What it is | Where |
  | --- | --- | --- |
  | `hero_rider.webp` | A rider with a green top box stopped on a mountain road at sunrise, from behind (2:3) | Welcome screen, full bleed, cropped left of centre |
  | `hero_highland.webp` | Rice terraces falling into a misty valley at sunrise (3:2) | Behind the home greeting (`MountainBackdrop`) |
  | `banner_weave.webp` | A hand-woven cloth draped over a wooden bench against a dark green wall | The "Deliver local" banner on Home |
  | `weave_band.webp` | The border-and-diamond rows cut from the woven pattern tile | `WovenBand`, repeated sideways, at the foot of the welcome screen |

  They replace a crop of the about page's `deliver-locally` photograph, a copy
  of `:seller`'s `highland_banner.webp`, and the canvas-drawn weave.
- **Launch window and launcher background** are forest in both themes, as in
  the other two apps.

## Where real data replaced the reference's content

| Reference | This app |
| --- | --- |
| KADAYAW Rider · "Delivering good things to higher places" | OMAYKAN Rider, same tagline |
| Local products, stronger communities / You deliver change / Mountains move with people like you | Jobs from every shop on Omaykan / The whole delivery fee is yours / Take the jobs that suit your route |
| "Likha. Kultura. Kabuklan." | "Likha. Kultura. Kabuhayan." — the correction `:seller` made; confirm before release |
| Home: ☰ menu, 🔔 bell | The rider's photo (opens the profile) and refresh. There is no notification feed. |
| "You're Online" switch | Job alerts and location sharing, two rows in one card. There is no online state. |
| 3 deliveries · ₱1,250 · 96% completion | Today's deliveries · today's earnings (server aggregate) · cash to collect (sum over jobs in hand). Nothing records a completion rate. |
| Deliveries · Earnings · Inbox (2) · More | Job board (board count) · Earnings · My jobs (jobs in hand) · More (profile) |
| Active order, "New" badge | Up to two jobs in hand, "To pick up" / "On the way", with the cash banner when there is cash |
| "Deliver local. Support our artisans." | "Deliver local. Support your neighbourhood shops." → the board |
| Delivery details: one sheet, stops joined by a line, items with photo and price, message button, landmark note, headset | The same sheet and connected stops. Items are names and quantities with the item's initial on a peach tile (no photo or price is on the rider's copy of an order). Call, no message (riders have no messaging). The note under the drop-off is the cash to collect, since no landmark field exists. The headset rings the support line from `GET /api/rider/support`, or says there is none. |
| "I Have Picked up the Order" slider | Same capsule, a tap rather than a swipe; "I have delivered the order" after pickup |
| On the way: Picked up → On the way → Near you → Delivered | Accepted → Picked up → Arriving → Delivered; Arriving lights only with a GPS fix within 300 m of the door |
| Earnings: COD / Paid online split, All · Completed · COD · Online chips | Deliveries and per-delivery average; no chips. A finished job's payment status can change after the fact, so a split by it is not a record. |
| Earnings bars Mon–Sun, "+12%" | The last 7 of the server's 14 days, today lit, "↑ n% vs the 7 days before" (both windows from the same fourteen) |
| Profile: Rider ID KD-R-0427, ★ 4.9 (128 deliveries) | Plate; ★ average with the *rating* count, "No ratings yet" rather than 0.0 |
| Vehicle information · My documents (All verified) · App settings · Help & support · Safety guidelines | Vehicle information · Personal details · My documents (Verified — only approved riders reach this screen) · Your photo · Ratings · Password · Help & support. No settings screen or rider code exists to link to. |

## Structural changes

- The open job moved from the Jobs tab to `RiderShell`, so Home can open one,
  and it now covers the tab bar.
- The job screen has two faces — details and map — over one view model; it
  opens on the details before pickup and on the map after.
- `JobDetailViewModel.close()` stops the feed and location collectors when the
  screen leaves. The view model is activity-scoped, so before this the GPS kept
  running after a job was closed, and opening a second job left the first one's
  collector writing into the state.
- The finished-jobs list moved from Home to Earnings, grouped by Manila day with
  the server's day totals.
- The job-board card lost a doubled bottom gap (an `offset` overlap that was
  also padded for).

## Verification

`./gradlew :rider:assembleDebug` and `./gradlew :rider:testDebugUnitTest`, both
passing. The debug build was installed on the Pixel 6 Pro emulator and every
redrawn screen was rendered through `GalleryActivity` with fixture data —
`signin`, `home`, `job`, `jobmap`, `earnings`, `account`, and the board cards —
and compared against the reference from screenshots, in light and dark theme.
The screenshots in this folder are from that run.

Not verified: any of it signed in against the live API. The gallery renders the
production layouts (`HomeContent`, `EarningsContent`, `JobDetailContent`,
`AccountScreen`) but not their view models, so the new navigation — Home's
shortcuts and order cards into the Jobs tab and the job screen, and the job
screen's close-on-leave — was compiled and reasoned about, not driven end to
end. Onboarding, registration, the forgotten-password form and the status
screen took the new palette and components but were not otherwise redrawn.
