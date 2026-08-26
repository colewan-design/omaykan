# Facebook page cover — image generation prompts

Reference: the GrowSari cover (flat vector, cartoon Filipino cast, sari-sari store
backdrop, centered sign, icon-and-label benefit strip, top slogan ribbon).
These prompts hit that format with Omaykan's palette and positioning.

**Canvas:** 1640 × 624 px (Facebook page cover, 2× of 820 × 312).
Keep the logo, tagline and benefit strip inside the **central 1130 px** — mobile
crops the sides. Leave the **bottom-left ~360 × 360 px** quiet; the profile
picture sits there on desktop.

**Brand lock (paste into any prompt):**

> Palette: deep forest green `#1a6b3c`, bright green `#22c55e`, warm lime
> `#bbf451`, gold amber `#d9a13b`, off-white `#f5f9f6`, near-black ink `#1a1a1a`.
> Logo: the lowercase wordmark "omaykan" in a heavy rounded geometric sans —
> "oma" in deep green, "kan" in gold amber, the descender of the "y" curling into
> a small green leaf sprout. Mark: a green woven bayong basket with a white
> woven-pineapple pattern across its face.

---

## Prompt A — Sari-sari hero (closest to the reference)

```
Flat vector illustration, Facebook cover banner, 1640x624, wide horizontal
composition, clean bold shapes, no gradients, no photorealism.

Scene: a warm Philippine neighborhood storefront seen straight on — a sari-sari
store window framed by a scalloped awning across the top, shelves behind it
stacked with sachets, canned goods, softdrink bottles and rice sacks drawn as
simple flat silhouettes in muted green and cream, so the background reads as
texture rather than detail.

Cast: on the left, a smiling Filipina tindera in a deep green polo shirt with a
small gold logo on the chest, one arm open in a welcoming gesture. On the right,
a delivery rider in a green helmet and green jacket holding a paper bag of
groceries, and beside him a young customer looking at a phone that shows a green
storefront app screen. Rounded, friendly, modern cartoon style — large eyes,
simple hands, no outlines heavier than the shapes themselves.

Center: a clean white signboard with soft rounded corners, hanging from the
awning, carrying the wordmark "omaykan" — heavy rounded lowercase, "oma" in
deep forest green #1a6b3c, "kan" in gold amber #d9a13b, the y descender curling
into a small green leaf. Beneath it, a slim white line of text: "Presyong
tindahan. Hatid sa bahay."

Below the signboard, a deep green rounded panel holding five evenly spaced
badges, each a white circle icon above a short bold white uppercase caption:
(1) a peso coin with a slash — "0% KOMISYON"; (2) a price tag — "PRESYONG
TINDAHAN"; (3) a motorbike — "BUONG BAYAD SA RIDER"; (4) a phone showing a shop
front — "SARILING TINDAHAN ONLINE"; (5) an open palm holding cash — "BAYAD SA
HANDOVER".

Across the very top, a full-width gold amber ribbon with centered bold white
text: "Sarili mong tindahan. Sarili mong presyo."

At the bottom center, small and tidy: the green woven bayong basket mark with a
white woven-pineapple pattern, next to the text "omaykan.com".

Palette: deep forest green #1a6b3c, bright green #22c55e, warm lime #bbf451,
gold amber #d9a13b, off-white #f5f9f6, near-black ink #1a1a1a. Bright, optimistic,
high contrast, generous white space around the sign. Nothing important in the
bottom-left corner.
```

## Prompt B — Three sides (merchant, rider, customer)

Leads with the argument instead of the storefront. Better if the page's first
job is explaining what Omaykan *is*.

```
Flat vector illustration, Facebook cover banner, 1640x624, off-white #f5f9f6
background, three soft-rounded green panels side by side with airy gaps.

Left panel: a Filipina merchant behind a small counter with a tablet POS showing
a green order list; big bold number "0%" above her in deep green #1a6b3c, caption
below in dark ink: "komisyon sa tindera".

Center panel, slightly taller and forward: a delivery rider on a motorbike, green
helmet and jacket, holding a paper bag; big bold "100%" in gold amber #d9a13b,
caption: "ng delivery fee, sa rider".

Right panel: a customer at their door receiving the bag, phone in hand showing a
green storefront; a price tag icon with a check mark, caption: "presyong tindahan,
walang dagdag".

Across the top center, the wordmark "omaykan" in heavy rounded lowercase — "oma"
in deep forest green #1a6b3c, "kan" in gold amber #d9a13b, the y descender curling
into a small green leaf — with a thin dark-ink line under it: "Commission-free
local commerce."

Bottom right, small: "omaykan.com". Clean modern cartoon style, bold flat shapes,
no gradients, no drop shadows, high contrast, plenty of breathing room. Nothing
important in the bottom-left corner.
```

## Prompt C — Typographic, no cast

Cheapest to keep on-brand and the safest for legible text. Good as the default
cover while the illustrated ones are being iterated.

```
Flat vector Facebook cover banner, 1640x624, deep forest green #1a6b3c
background with a subtle darker green woven-basket pattern at 8% opacity across
the full width.

Centered: the wordmark "omaykan" in heavy rounded lowercase, "oma" in off-white
#f5f9f6 and "kan" in gold amber #d9a13b, the y descender curling into a warm lime
#bbf451 leaf. Directly beneath, three short lines in clean white sans, tightly
stacked and centered:
"0% commission from the merchant"
"100% of the delivery fee to the rider"
"In-store prices for the customer"

Bottom center, small warm-lime text: "omaykan.com". No people, no photographs,
no gradients, generous margins, everything inside the middle 70% of the width.
```

---

## Negative prompt

```
photorealistic, 3d render, stock photo, gradient mesh, drop shadows, bevels,
glossy plastic, cluttered background, watermark, gibberish text, misspelled
words, extra fingers, distorted faces, western suburban houses, generic corporate
blue, purple, teal, red-and-yellow fast-food palette, busy borders, dense small
text
```

## Working notes

- **Text is the failure mode.** Image models mangle Filipino copy and the split
  green/gold wordmark. Generate each prompt with `[NO TEXT — leave the signboard
  blank and the badge captions empty]` appended, then set the wordmark and all
  captions in Figma or Canva over the art. The real logo files are already in the
  repo: `apps/web/public/logo-wordmark.png` and `logo-mark.png`.
- **Copy swaps** for the top ribbon, depending on the campaign:
  - "Sarili mong tindahan. Sarili mong presyo."
  - "Zero komisyon. Buong tubo, sa'yo."
  - "Presyong tindahan, kahit naka-deliver."
- **Do not** claim Omaykan supplies customers or riders — the positioning is the
  merchant's *own* channel, not a marketplace. Keep the art on the merchant's own
  shop, never a directory of shops.
- Aspect-ratio flag for models that need it: `--ar 41:16` (Midjourney), or set
  1640×624 explicitly.
