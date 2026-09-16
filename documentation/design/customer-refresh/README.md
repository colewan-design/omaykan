# Customer app refresh

The shopper's Android app (`:app`, `com.omaykan.storefront`) redrawn to a
six-screen storefront reference supplied in chat on 2026-09-11: onboarding,
home, product detail, shop by category, stories, and cart. The reference is
branded "KADAYAW", a Cordilleran crafts shop. It was pasted into the
conversation rather than saved, so it is not in this folder.

## Decisions taken with the product owner

1. **Omaykan's name, the reference's look.** Every screen takes the reference's
   layout, palette, type and ornament. The app name, launcher label, wordmark
   and copy stay Omaykan's.
2. **Same layout, real data.** Every slot the reference fills with something
   the API does not have holds a fact the API does have. No ratings, no review
   counts, no artisan claims.
3. **The reference's tabs.** Home, Shop, Stories, Favorites, Account. Orders
   left the tab bar and is now its own route (`OrdersRoute`), opened from the
   Account page's order tiles and from the menu.

## Design system

- **Palette** (`core/designsystem/Color.kt`): forest green `#1E3A2B` for the
  frame (headers, tab bar, splash, menu), cream `#F7F2EA` page, white cards,
  terracotta `#B0512E` for the one action a screen wants pressed (`cta`), peach
  behind the aisle circles, gold for the sun in the mark. It has a dark
  variant. Terracotta is Material's `primary`, so stock controls such as text
  buttons and spinners match the hand-drawn buttons.
- **Type** (`Type.kt`): the platform sans for everything a shopper acts on.
  Lora (serif) for storytelling: aisle cards, Stories, the welcome copy, the
  quote. Cinzel for the `OMAYKAN` wordmark only. Both are bundled variable
  fonts in `res/font` (SIL Open Font License, from google/fonts). They are not
  downloadable fonts, because those need Play Services. Each weight sets the
  `wght` axis explicitly; the plain `Font(res, weight)` overload does not.
- **Pieces** (`Brand.kt`): `ForestTopBar` (paints under the status bar),
  `Wordmark`, `MountainMark` (the terraced peaks and sun, drawn on a canvas),
  `MountainScene` (the cart footer), `WovenBand` (the loom edge), `CtaButton`,
  `QuantityBox`, `HeartToggle`, `SectionHeader`, `AccordionRow`, `SearchPill`
  and `Modifier.softCard()`.
- **Status bar**: light icons everywhere, because every screen now opens under
  a forest bar or a darkened photograph.
- **Photographs**: `hero_market.webp` (welcome, Stories) and
  `hero_shop_owner.webp` (Home banner) are the about page's own images from
  `apps/web/public/about/`.

## Where real data replaced the reference's content

| Reference | This app |
| --- | --- |
| KADAYAW, "Rooted in our mountains…" | OMAYKAN, "Your neighbourhood shops at their own counter prices" |
| "Likha. Kultura. Kabuklan." | "Lokal. Tapat. Abot-kaya." (Local. Honest. Affordable.) |
| "Tradition Lives On" campaign banner | "Local Shops, Honest Prices" → the front-page shop |
| Category photos (bag, basket, beads) | A real photograph from each aisle's own shelf; else the seeded glyph; else the aisle's initial |
| Category taglines ("Woven with identity") | "71 items · from ₱7.50" |
| ★ 4.8 (32 reviews) | ✓ In-store price · Per 155g |
| Handwoven-by-artisans description | Whose shelf it comes from, and that the shop confirms the total |
| Authentic Handmade / Supports Local Artisans / Sustainable Materials | Same Price as In-Store / Supports a Local Shop / Rider Keeps the Whole Fee. These are platform promises, so they are true of any product. |
| Product Details / Shipping Information / Care Instructions | Product Details (unit, stock, item code, barcode) / Delivery Information / Sold By |
| Artisan articles on Stories | The shops themselves (photo, name, type, address, shelf size), plus a link to the web's about page |
| Shipping ₱100 | Delivery "Quoted at checkout". The fee is priced server-side, so the total is labelled an estimate. |
| "Local hands, brighter horizons" | "Local shops, honest prices" |

The reference's hamburger opens a real menu: the tabs, Your Orders, and About
Omaykan. Its pager dots are left out, because a product has one photograph.
The first-run welcome is shown once (`WelcomeStore`). It is skipped for a phone
that has already been inside a shop, and for an App Link launch.

## Verification

Built with `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`,
both passing. The debug build was then run on the Pixel 6 Pro emulator against
the live API (`https://omaykan.com`), and each screen was compared with the
reference from screenshots: welcome, home, shop by category, stories,
favorites, signed-out account, the menu, a shop's catalog, product detail with
the add-to-cart note, cart, checkout and the order list. The welcome was
checked on a fresh install. Dark theme was checked on a shop's catalog and a
product page. The six screenshots in this folder come from that run. The
signed-in account page, the account sections and the order screens were
restyled but not driven signed in.
