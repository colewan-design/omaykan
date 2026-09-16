# /seller/signup — image prompts

Every image slot on the merchant signup page, as it stands after the ecommerce
restructure. The page is `apps/web/src/onboarding/OnboardingPage.vue`; the pitch and all
six slots live in `apps/web/src/onboarding/MerchantPitch.vue`.

The old images were shot for the POS-only product: a cashier, a counter, a tablet.
Nothing in them says a customer bought this from home and a rider took it away. The page
now sells a storefront, a register, and riders — so the images have to carry all three.

## The style spine

Paste this block into every prompt (slot 1 excepted — it's the illustration). It's what
makes the set read as one shoot rather than six stock photos:

> Photorealistic editorial photograph. Small independent shop in the Philippines,
> Baguio-highlands feel. Warm natural daylight from a large side window, soft shadows,
> no harsh flash. 35mm lens, f/2.0, shallow depth of field, subject sharp and background
> falling off. Neutral-warm color grade, true-to-life Filipino skin tones. One deliberate
> green accent (#22c55e) somewhere small — a UI glow, an apron trim, a sticker on a bag.
> Uncluttered, real, lived-in; not a stock-photo set. No brand logos.

**Negative prompt for all of them:** `legible text, readable UI copy, words on screens,
watermark, logo, extra fingers, deformed hands, warped tablet edges, plastic skin,
HDR halo, heavy vignette, clutter, blown highlights, tilted horizon`

Screens must stay **abstract** — soft rectangles, a colored chart shape, a green
confirmation glow. Generators produce gibberish text at this size and it's the fastest
way to make the page look fake. Real UI can be composited in later.

## Crop points matter

Five of the six are CSS `background-image: cover` with a non-centered
`background-position`. The prompts below place each subject to match. If a generated
image fights its crop, the position is a one-line change in `MerchantPitch.vue` — the
alternative is a subject sliced in half.

| File | Section | Ratio | `background-position` |
|---|---|---|---|
| `/hero-character.png` | Hero | 1448×1086 | — (`object-fit: contain`) |
| `/solution-checkout.png` | Your shop, online | 16:11 | `58% 62%` |
| `/pos-counter-wide.png` | A register for the counter | 16:11 | `45% 55%` |
| `/delivery/hero-rider.webp` | Riders on demand | 16:11 | `50% 45%` |
| `/checkout-counter.png` | Stock that counts itself | 4:5 portrait | `62% 68%` |
| `/solution-analytics.png` | Today's numbers, live | 16:10 | `38% 58%` |

---

## Slot 1 — `/hero-character.png`

**Hero, right column · 1448 × 1086 · transparent or clean soft-gradient background**

The current asset has a hard dark rectangle baked in, which reads as a floating box
against the white hero. Whatever replaces it needs a transparent or very soft edge.

> Stylized 3D character illustration, modern animated-feature look — soft subsurface
> skin, rounded forms, cinematic rim light — of a young Filipina shop owner behind a
> small café-grocery counter. Her right hand taps a tablet point-of-sale on a stand; her
> left hand sets a neatly packed paper bag onto the counter with a small green order tag
> clipped to it. Just behind her shoulder, slightly out of focus, a delivery rider in a
> green jacket reaches for a second bag at the pickup end of the counter. Warm amber
> shop lighting, shelves of jars and produce softly blurred behind. Green (#22c55e)
> reads as the shop's accent on the tag, the rider's jacket, and the tablet's glow.
> Friendly, confident, mid-action. 4:3, character right of center, generous clean space
> on the left, no hard background edge. No text anywhere.

**Notes:** the only illustrated asset — keep it illustrated, it's what separates the hero
from the five photographs below. If you regenerate it, lock the seed and reuse the same
character; she's the closest thing the page has to a brand face.

---

## Slot 2 — `/solution-checkout.png`

**"Your shop, online" · 16:11 · 1600 × 1100 · crop holds right-of-center, slightly low**

The card that has to prove the storefront exists. This is the only slot where an online
order is the subject rather than a detail.

> [style spine] A restaurant server stands at a wooden pass-counter tapping a large
> tablet point-of-sale angled toward her — a grid of warm-toned menu tiles and a green
> confirm button, shapes only, no readable text. Mounted on the wall beside the pass, a
> second small screen shows a vertical queue of pale order cards, the top one glowing
> green as it arrives. Steam rising from a plated dish on the pass in the foreground,
> softly out of focus. Kitchen warmth behind, dining-room light in front. Subject right
> of center, lower half of the frame. Horizontal 16:11.

---

## Slot 3 — `/pos-counter-wide.png`

**"A register for the counter" · 16:11 · 1600 × 1100 · crop holds slightly left, slightly low**

Speed is the claim here — under 10 seconds. Show hands mid-tap, not a posed portrait.

> [style spine] Close, slightly over-the-shoulder view of a barista's hands ringing up an
> order on a tablet point-of-sale mounted on a small brass stand at a café counter. On
> the tablet, a grid of soft product tiles and a green total bar — shapes only, no
> readable text. A card terminal sits beside it, a customer's hand entering frame from
> the right to tap it. Espresso machine and pastry case warmly blurred behind. Motion in
> the hands, everything else still. Subject slightly left of center. Horizontal 16:11.

---

## Slot 4 — `/delivery/hero-rider.webp`

**"Riders on demand" · 16:11 · 1600 × 1100 · crop holds center, slightly high**

The current file is a white-background product cutout borrowed from the storefront. Next
to two warm shop interiors it reads as a different website. Replace it.

> [style spine] A delivery rider in a green jacket and helmet stands at the pickup end of
> a small shop counter, taking a packed paper bag with a green order tag from the
> shopkeeper's hands. An insulated delivery box is visible at the edge of frame. Both
> faces partly visible, mid-handover, unposed. Shop shelves and daylight from the street
> door behind them. Subject centered, framed from the chest up. Horizontal 16:11.

---

## Slot 5 — `/checkout-counter.png`

**"Stock that counts itself" · 4:5 portrait · 1200 × 1500 · crop holds right-of-center, low**

The tall card in the asymmetric grid. Portrait, not landscape — a 16:9 image cropped into
this slot loses most of itself.

> [style spine] A grocer in a green apron restocks a wooden shelf of vegetables and
> packaged goods with one hand while glancing at a tablet propped at the end of the
> aisle. The tablet shows an abstract stock list — rows of soft gray bars, two of them
> highlighted amber-red as if low — no readable text. On the floor beside him sit two
> already-packed paper bags with green order tags, waiting for pickup. Daylight from the
> shop window rakes across the produce. Action in the lower-right third. Vertical 4:5.

---

## Slot 6 — `/solution-analytics.png`

**"Today's numbers, live" · 16:10 · 1600 × 1000 · crop holds left-of-center, slightly low**

Fills the right half of the wide card, so the subject must survive a fairly tight crop.

> [style spine] A nail salon owner sits at a small side desk at the end of the workday,
> reviewing a tablet propped on a stand. The screen shows an abstract dashboard — a green
> ascending line, two stacked bar groups side by side as if comparing two sources, and
> three small stat blocks — pure shapes, no readable numbers or text. A phone beside her
> mirrors one small green figure. Polish bottles and a folded towel on the desk, salon
> chairs warmly blurred behind. Late-afternoon window light, calm and quiet. Subject left
> of center. Horizontal 16:10.

---

## Not image slots

Two tiles in the "Behind the counter" grid — "Always on, even offline" and "Roles for
your whole team" — are icon + text by design. They should stay that way; filling every
cell with a photo flattens the asymmetry the grid is built on.

## Copy status

The headlines no longer contradict the pictures. The page leads with "Sell online. Ring
up in store.", the three cards name the storefront, the register, and the riders, and the
closing section says early access is free — which is what the landing page and footer
have been promising all along.
