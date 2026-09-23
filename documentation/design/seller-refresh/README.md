# Seller UI refresh

Reference: the "KADAYAW Seller" eight-screen board supplied in conversation on
2026-09-11 (welcome, dashboard, products, add product, orders, earnings, store
profile, messages). It was not saved to the repository.

## Design brief

Bring `apps/mobile-android/seller` into the highland look the shopper app
already wears: forest-green frames, a cream page, terracotta for the one thing
to press, Lora for the lines meant to be felt, Cinzel for the wordmark. Grow
the app from a single orders screen into the reference's five tabs — Home,
Products, Orders, Sales, Account — plus Messages, using only what the backend
actually has.

## Implementation

- **Palette, type, brand** are ported from `:app` (`Color.kt`, `Type.kt`,
  `Brand.kt`), not shared — see the seller README on why. Lora and Cinzel are
  bundled in `res/font`. The launch window is forest in both themes.
- **Welcome / sign-in** is the reference's welcome screen over
  `hero_shop_owner.webp` (the same photograph `:app` ships). "Log In" opens the
  existing two-step sign-in on a card; "Create a Seller Account" opens
  `omaykan.com/seller/signup`.
- **Home** reads the order feed (today's sales, orders, what is still owed,
  top sellers over 7 days), the catalog (low-stock count, the till's rule:
  tracked stock at or under its threshold, default 5) and the inbox (unread).
- **Products** reads `GET /sync/bootstrap` and writes through
  `POST /sync/push`, the register's own catalog door. Edits send every field
  back because `applyProductEvent` resets whatever it is not sent; a changed
  count is sent as an `inventory_adjustment` of the difference. Add and edit
  are admin/manager only, mirroring `StoreContext::isManager()`.
- **Orders** keeps every action the old card had; the card now itemises the
  basket and offers a call button. Chips are Live / Preparing / Ready / All.
- **Sales** is arithmetic over the last 100 orders the feed already holds, and
  says so when a busy week runs past them.
- **Messages** uses the existing seller conversation endpoints; replies post
  as the signed-in person.
- **Account** holds the order-alert switch, the inbox, the storefront and
  register links, and sign-out.

## Intentional departures from the reference

- **Brand:** "KADAYAW" is the reference's placeholder; the product is Omaykan.
- **No payouts.** Customers pay the shop directly and the platform never holds
  the money, so "Recent Payouts", "Payout Settings" and the Payouts shortcut
  are replaced by recent orders with paid/unpaid, and a Messages shortcut.
- **No "Online" store switch.** Nothing on the server takes a shop off the
  storefront; the card holds the order-alert switch instead.
- **No "Accept Order".** Online orders arrive already the shop's (`preparing`);
  the card's buttons are the real next steps.
- **Product form:** no photo upload, unit picker or description — there is no
  endpoint or column for them. An existing photo and unit are shown read-only.
- **No "System" messages tab** — the platform sends shops none.
- **Photographs:** no portraits of customers; initials and the shop's own
  uploaded photo stand in. Three generated assets (2026-09-11, sources in
  `Downloads/omaykan seller portal image`) are wired: the welcome photograph
  of a fictional Cordillera vendor (`hero_vendor.webp`), the highland panorama
  behind the greeting and the profile cover (`highland_banner.webp`), and the
  eight category illustrations (`category_art_*.webp`). The illustrations came
  on a dark glowing ground rather than transparent, so they are shown as round
  badges, ringed when chosen, and only for category names they depict —
  everything else keeps its Material icon.
- **Sign-off line:** set as "Likha. Kultura. Kabuhayan." — the reference's
  "Kabuklan" reads as a typo for *kabuhayan* (livelihood). Confirm before
  release.
