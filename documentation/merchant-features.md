# Merchant features: the plan for the eight open items

The **Merchant (POS / seller)** section of [todo-checklist.md](./todo-checklist.md)
lists eight items. They look like eight features. In the code they fall into
four groups of very different size, and three of them depend on work that is
not on the list:

| # | Item | Size | Depends on | Section |
|---|---|---|---|---|
| 1 | Server-side guard on catalog writes | **S** | — | [§2](#2-server-side-guard-on-catalog-writes) |
| 2 | Shop open/closed switch | **S** | — | [§3](#3-shop-openclosed-switch) |
| 3 | Product photo upload endpoint | **M** | — | [§4](#4-product-photos-unit-and-description) |
| 4 | Distinct order-alert sound | **XS** | a sound asset | [§5](#5-distinct-order-alert-sound) |
| 5 | Seller app on the Reverb websocket | **M** | the `/reverb/` path decision | [§6](#6-seller-app-on-the-reverb-websocket) |
| 6 | Order discounts | **L** | BIR answer on senior/PWD | [§7](#7-order-discounts) |
| 7 | Promo / voucher codes | **M** | §7 | [§8](#8-promo--voucher-codes) |
| 8 | Loyalty | **L** | §7, and customers on the server | [§9](#9-loyalty) |

Written against `main` on 2026-09-19, after the
[subscription and suspension](./subscription-and-suspension.md) work. Items 2
and 6 build directly on what that work added.

---

## Status

**All eight built 2026-09-19, not yet deployed.** First-deploy steps are in
[deployment.md §6b](./deployment.md): seven migrations, two one-off backfill
commands, and a scheduler cron line the server has never had.

| § | Where it lives | Tests |
|---|---|---|
| 2 Catalog guard | `RolePermissions`, `StoreContext::can`, `SyncController::refusalFor`; till and seller app handle `rejected` | `CatalogWriteGuardTest` (8) |
| 3 Open/closed | `StoreOrderingController`, `stores.ordering_*`; till Dashboard switch, seller Home card, storefront banner and disabled checkout, directory badge | `StoreOrderingApiTest` (7) |
| 4 Photos | `ImageStore`, `ProductImageController`, `SyncController::withStoredImages`, `products.description`; `products:extract-inline-images`, weekly `products:sweep-images`; seller photo picker, unit, description; till description field | `ProductImageApiTest` (8), seller `ProductEventsTest` |
| 5 Sound | `res/raw/order_alert.wav` (synthesised, no licence), channel `new_orders_v2`, "keep ringing" switch | — (manual) |
| 6 Websocket | `core/realtime/PusherProtocol.kt`, `OrderRealtime.kt`, inside `OrderFeed` | seller `PusherProtocolTest` (8) |
| 7 Discounts | `priceOrder` (shared) / `OrderPricing` (PHP), `order_discounts`, `DiscountControl.vue`, role limits in Roles | `OrderDiscountApiTest` (9), `priceOrder.spec.ts` (7) |
| 8 Promo codes | `PromoCode`, `PromoCodeController`, `OnlineOrderController::quote`; till Products → Promotions, storefront cart field, seller Promotions screen | `PromoCodeApiTest` (11) |
| 9 Loyalty | `pos_customers`, `loyalty_programs`, `loyalty_entries`, `Loyalty` service, `LoyaltyController`, daily `loyalty:expire`; till Customers page, "Points" in the discount control | `LoyaltyApiTest` (11), `customerSync.spec.ts` (3) |

**Where the build departs from this plan, and why:**

- **§6 needed no new host.** Rather than adopt `pusher-websocket-java` and
  move Reverb to `ws.omaykan.com`, the seller app speaks the few Pusher frames
  it needs over OkHttp, which lets it put `/reverb` in front of the path. No
  DNS, nginx or certificate change. The customer app's live tracking can reuse
  `PusherProtocol`.
- **§7 flags rather than rejects.** The plan had the server refuse a sale
  whose totals disagree. A sale the till has rung up has already taken the
  money, and refusing its record loses the only trace of it, so the server
  keeps it and writes `orders.integrity_flags` (`total_mismatch`,
  `discount_over_limit`, `promo_not_valid`, `loyalty_overdrawn`, …).
- **§7 has no manager-PIN approval.** Over the role's limit, the till refuses
  and says to ask a manager, who signs in to give it. There is no
  `approved_by` column.
- **§7 senior citizen / PWD discounts are not built.** `order_discounts.kind`
  has room for them. Their VAT arithmetic waits on the BIR answer.
- **§9 leaves tiers on the device.** Customers, their orders and their points
  are on the server now, but the VIP/Regular tiers on the Customers page are
  still computed from the till's own history. Loyalty is counter-only:
  storefront accounts are not linked to counter customers.

**Bugs found and fixed on the way** — each would have corrupted the new
features' numbers:

- The till recorded **tax as a flat 12% of the subtotal** whatever the cart
  held, so a zero-rated product's sale was stored with tax the customer never
  paid. It now records exactly what the cart showed (`priceOrder`).
- **`products.tax_rate` meant two things.** Seeders and online checkout used a
  percentage (12); the till wrote a fraction (0.12). A seeded product showed
  1,200% VAT in the storefront cart and at the till. The column is a
  percentage everywhere now; the edges convert, and migration `000500`
  rewrites the fractions.
- **The till dropped photos, unit, brand, "was" price and description** when
  it loaded products from the server, then synced them back as null — so a
  price change at the counter wiped them. And an edit that changed *only*
  those fields never synced at all.
- **Every till sale had `orders.user_id` null** (finding 4).

**Found and not fixed** — outside these eight items:

- **Voids never reach the server.** The till marks an order voided locally and
  returns its stock; the server's order stays a sale.
- **Online checkout ignores a branch's own price.** The storefront catalog
  shows `product_store_overrides.price_cents`; `OnlineOrderController::priceLines`
  charges the organization's price.

---

## 1. What reading the code turned up

These change the plan, so they come first.

1. **The websocket item is not "close to a no-op".** The checklist says it
   should be, because `store.{id}` authorizes on a membership the app's token
   has. That half is true. But the seller app has no Pusher client at all, and
   production Reverb sits behind nginx at `/reverb/`, a path
   `pusher-websocket-java` cannot reach
   ([mobile-plan.md §8](./mobile-plan.md), "Landmine"). The customer app is
   blocked on the same question. See §6.

2. **Product photos are already reaching the server, as base64 inside the
   product row.** The till reads a picked photo with `FileReader.readAsDataURL`
   ([`ProductSheet.vue:168`](../packages/core/src/components/ProductSheet.vue#L168))
   and syncs the data URL as `imageUrl`. On 2026-09-13 `products.image_url` was
   widened to `text` so it would fit
   ([migration](../backend/database/migrations/2026_09_13_000100_add_product_gallery_and_pack_fields.php)).
   So every photographed product carries tens of kilobytes of base64 in its
   row, and in every `/sync/bootstrap` and `/storefront/catalog` response that
   includes it. The upload endpoint is needed for the till's sake as much as the
   seller app's. See §4.

3. **The till's order totals are trusted as sent.** `SyncController::applyOrderEvent`
   stores `subtotalCents`, `taxCents` and `totalCents` from the payload without
   recomputing them
   ([`SyncController.php`](../backend/app/Http/Controllers/Api/SyncController.php)).
   A discount column added to that would be one more number nobody checks. See §7.

4. **`applyOrderEvent` sets `user_id` twice.** The array literal has
   `'user_id' => $context->user->id` and then `'user_id' => $orderData['userId'] ?? null`.
   PHP keeps the last one, so the signed-in person is silently replaced by
   whatever the payload says, or by null. Harmless until a discount needs to
   say who gave it. Fix it in §7.

5. **A refused sync event is retried forever.** The till drops an outbox entry
   only on `applied` or `duplicate`
   ([`index.ts` `flushOutbox`](../packages/data/src/index.ts)). A `failed` one is
   re-sent on every flush, and the server re-attempts it because
   `applied_at` is still null. That is tolerable for a transient error. For a
   permission refusal (§2) it is an infinite loop.

6. **Signup creates no `pos_roles` rows.** Roles and their page permissions
   exist per organization, but only once an owner saves them from the till
   ([`StaffRoleController::sync`](../backend/app/Http/Controllers/Api/StaffRoleController.php)).
   Seeded shops have them; a fresh signup does not. Anything on the server that
   reads permissions needs the built-in defaults as a fallback.

7. **Customers never leave the till.** `saveCustomer` writes to the device's
   own store and nowhere else
   ([`index.ts`](../packages/data/src/index.ts)). Orders carry a `customerId`
   locally, but `orders` has no column for it and the server drops it. The
   loyalty *tiers* on `CustomersPage` (VIP / Regular / Occasional / New) do
   exist, computed on the device from that device's own orders
   ([`CustomersPage.vue:118`](../packages/core/src/pages/CustomersPage.vue#L118)).
   The audit's "no tiers" means none that are stored or shared. See §9.

8. **No `description` column on `products`.** The checklist says the seller
   app's form can't add "a photo, unit or description". Unit exists
   (`unit_label`) and photos exist (as above). Description exists nowhere, on
   any client or on the server.

---

## 2. Server-side guard on catalog writes

### Today

`POST /api/sync/push` applies `product`, `category` and `inventory_adjustment`
events for any signed-in staff member. The store photo, staff and role
endpoints check `StoreContext::isManager()`. The sync push checks nothing. The
seller app hides the product form's Save button from cashiers
([seller/README.md](../apps/mobile-android/seller/README.md), "Next" #5). That
hides the button. It does not stop a cashier's token from writing the catalog.

### Design

**Check per event, not per batch.** One push carries a sale, a stock movement
and perhaps a price change together. Refusing the whole batch because of the
price change would also refuse the sale.

| Event | Allowed for |
|---|---|
| `order` | anyone signed in, as today |
| `inventory_adjustment` with `adjustmentType: 'sale'` | anyone. It is the sale's own stock movement |
| `inventory_adjustment`, `restock` or `manual_correction` | roles with the `inventory` page permission |
| `product`, `category` | roles with the `products` page permission |
| `app_event` | anyone |

**Read the permission from the role, not a hard-coded rank.** Owners can
already build custom roles with page permissions in the till, and `pos_roles`
stores them. A guard that says "managers only" would overrule a shop that gave
its head cashier the Products page on purpose. Add a
`StoreContext::can(string $page): bool` method:

1. `admin` → always true.
2. Otherwise, the `pos_roles` row for `(organization_id, membership_role)` if
   there is one.
3. Otherwise, the built-in defaults, mirrored from `defaultRoles` in
   [`packages/shared`](../packages/shared/src/index.ts) (finding 6). Keep the
   PHP copy next to a comment naming the TS one, the way `RESERVED_SLUGS`
   already does.

**A new terminal status, `rejected`.** A refused event gets
`status: 'rejected'` with a message. The till's `flushOutbox` drops `rejected`
exactly as it drops `applied`, and the `pullCatalogChanges()` that follows puts
the server's version of the product back on the device. Without this, finding 5
turns every refusal into a retry loop. The seller app's `CatalogRepository`
needs the same handling.

**Fail closed for unknown entity types.** This already happens:
`match` throws on anything unlisted.

### Tests

A cashier's push with one sale and one price change applies the sale and
rejects the price change. A custom role granted `products` may edit the
catalog. A fresh signup with no `pos_roles` rows falls back to the defaults. A
`rejected` event is not re-attempted.

---

## 3. Shop open/closed switch

### Today

Two things close a storefront, and neither belongs to the merchant:
`stores.status` (structural, set at creation) and, since 2026-09-19, the
organization's `TenantAccess` verdict (suspended or unpaid). A shop that has
run out of rice, or whose only cook went home, has no way to stop orders for
the evening.

### Design

**A pause, not a status.** Two nullable columns on `stores`:

```php
$table->timestamp('ordering_paused_at')->nullable();   // null = open
$table->timestamp('ordering_resumes_at')->nullable();  // null = until reopened by hand
$table->foreignUuid('ordering_paused_by')->nullable(); // who, for the log
```

A resume time is what makes "closed until tomorrow morning" a single tap. It
also means a shop that forgot to reopen is not closed for a week. Business
hours (a weekly schedule) are deliberately out of scope. A resume time covers
the common case, and a schedule is a feature of its own.

**Where it bites:**

- `OnlineOrderController::store` refuses with 422 and a sentence naming the
  resume time: "This shop isn't taking orders right now — back at 4:00 PM."
  It is a 422, not the `TenantAccessDenied` 404, because the shop exists, is
  trading, and is simply paused.
- `StorefrontCatalogController` still serves the menu, with
  `store.orderingPausedUntil` in the payload. Shoppers can browse and plan;
  checkout is disabled with the same sentence. A paused shop showing the
  "not taking orders" empty state would feel like it had gone.
- `StoreDirectoryController` keeps the shop listed, with a "Closed now" badge
  and sorted after open shops.

**Who can pause:** any staff role with the `orders` permission, which includes
cashiers. The person at the counter during a rush is the one who knows the
kitchen is overwhelmed. `ordering_paused_by` records who did it.

**Endpoint:** `PUT /api/seller/ordering` with `{ paused: bool, resumesAt?: iso8601 }`,
behind `storeContext` (not `writableStoreContext`: pausing is always allowed,
even for an unpaid shop).

**Clients:** a toggle on the till's Dashboard and the seller app's Home, each
with preset resume times (1 hour, tonight, tomorrow morning, until I reopen).
The storefront's checkout reads `orderingPausedUntil`.

---

## 4. Product photos, unit and description

### Today

See findings 2 and 8. `StoreImageController` already does the hard part for
one image: it decodes a data URL, checks the declared type against
`getimagesizefromstring`, caps the size, stores the file on disk and serves it
from a public route.

### Design

**Extract that into a service, then use it in two places.**
`App\Services\ImageStore` with `store(string $dataUrl, string $directory): string`
(returns a path) and `forget(string $path)`. `StoreImageController` becomes a
caller.

1. **`applyProductEvent` extracts inline images on arrival.** If `imageUrl` or
   any `photoUrls` entry is a `data:` URL, store it through `ImageStore` and
   write the resulting `/api/product-images/{id}` URL into the row instead.
   Doing this on the server means an offline till needs no change at all: it
   keeps syncing data URLs, and they stop reaching the database as base64.
2. **`POST /api/seller/product-images`** for clients that are online when they
   pick a photo, which is always true of the seller app. It takes one data URL
   and returns `{ url }`. The client then puts that URL in the product event.
   Gated on the `products` permission (§2).

**A public read route:** `GET /api/product-images/{id}`, streamed like the
store image and throttled the same way. The id is a UUID filename, never a path
taken from the request.

**Backfill:** `php artisan products:extract-inline-images [--dry-run]` moves
existing data URLs out of `image_url` and `photo_urls`. Same shape as
`billing:backfill-trials`: dry run first, idempotent, reports counts.

**Orphans:** a replaced or removed photo leaves a file behind. A weekly
scheduled sweep deletes files under `product-images/` that no product
references and that are older than a day. This is the third item waiting on a
scheduler, after the stale-order sweep and the `past_due` job; build the
scheduler once, for all three.

**`description`:** a new nullable `text` column, a `description` field in the
product event (kept-if-absent, like `brand`), served by the storefront catalog
and shown on the product page. The till's `ProductSheet` gets a textarea.

**Seller app:** `ProductDraft` gains `imageUrl`, `photoUrls`, `unitLabel` and
`description`. Photos come from Android's system photo picker and go through
the existing `PhotoEncoder`, which the store photo already uses: 1280px on the
long edge, JPEG at quality 85, as a data URL ready to upload. `productEvents()` already carries every
existing field through on edit, so the new ones slot into the same rule.

### Tests

A data URL in a pushed product is stored as a file and the row holds a URL. A
disguised non-image is refused by the upload endpoint and, in a push, is dropped
without failing the rest of the product. The backfill command is idempotent.
A cashier cannot upload (§2).

---

## 5. Distinct order-alert sound

### Today

[`NewOrderNotifier`](../apps/mobile-android/seller/src/main/java/com/omaykan/seller/core/notify/NewOrderNotifier.kt)
creates channel `new_orders` at `IMPORTANCE_HIGH` with the system default
notification sound.

### Design

**It has to be a new channel.** Android fixes a channel's sound when the
channel is created, and `setSound` on an existing channel is ignored. So:

- Ship `res/raw/order_alert.ogg`, under two seconds, audible over a kitchen.
- Create `new_orders_v2` with that sound, and delete `new_orders` so the
  merchant's settings screen doesn't show two.
- Any volume or Do Not Disturb setting a merchant made on the old channel is
  lost, and they may need to set it again. Say so in the release note.

**Optional, in the app's own settings:** "Keep ringing until I open it", which
sets `FLAG_INSISTENT` on the notification. A kitchen with its back to the phone
is exactly who asked for a distinct sound.

**The sound itself** must be original or CC0 and recorded as such in the repo.
A notification tone lifted from another app is a licensing problem.

---

## 6. Seller app on the Reverb websocket

### Today

`OrderFeed` polls `GET /seller/online-orders` every 15 seconds while a screen
is open. The opt-in `OrderWatchService` polls in the background. The server
side is ready: `OrderPlaced`, `OrderStatusChanged` and `OrderDeliveryUpdated`
broadcast on `private-store.{storeId}`, and `POST /api/broadcasting/auth`
accepts a staff bearer token. Since 2026-09-19 that channel also refuses a
suspended shop.

### What is actually blocking it

Production Reverb is reached through nginx at `/reverb/`, because `/app` is
taken by the till's SPA. The web client handles this with Echo's `wsPath`
([`orderChannel.ts`](../packages/core/src/realtime/orderChannel.ts)).
`pusher-websocket-java` hardcodes `/app/{key}` and has no path option.
[mobile-plan.md §12.1](./mobile-plan.md) sets out the three ways out and
prefers the first.

**Recommendation: give Reverb its own host, `ws.omaykan.com`.** It frees the
default path, and every client then uses its library's defaults. `ws` and
`reverb` are already in `SignupController::RESERVED_SLUGS`, so no shop can take
the name. What needs checking is nginx: since every shop got `<slug>.omaykan.com`,
there is a wildcard server block, and `ws.omaykan.com` must be matched before
it. The wildcard certificate should already cover the name.

This one decision also unblocks the customer Android app's live tracking,
which the checklist lists separately under Customer.

### Design, once the host exists

- Add `com.pusher:pusher-java-client` and connect with `HttpChannelAuthorizer`
  pointed at `/api/broadcasting/auth`, with the staff token in the headers.
- **Events are signals, not state.** Each event sends a tick into `OrderFeed`'s
  existing `ticks` channel, which triggers the refetch the pull-to-refresh
  already does. The payloads are thin on purpose (mobile-plan §8), and
  refetching keeps one code path for what an order looks like.
- **Keep polling as a fallback, slower:** 60 seconds while the socket is
  connected, 15 seconds while it is not. A socket that silently died must not
  leave a merchant with a stale list.
- `OrderWatchService` holds the socket instead of polling in the background.
  It is already a foreground service, which is what Android requires to keep a
  connection alive.
- A 403 from channel auth (a suspended shop) is shown as such, and the token is
  kept. See [subscription-and-suspension.md §5](./subscription-and-suspension.md).

---

## 7. Order discounts

### Today

See findings 3 and 4. `orders` has no discount column. The till computes tax
as a flat 12% of the subtotal in `saveOrder`. The server stores whatever totals
the till sends. Online orders are priced server-side in
`OnlineOrderController::priceLines`. What exists is display only:
`compareAtPriceCents` and the "Save 21%" badge.

### The discount that matters most is a legal one

**Senior citizen and PWD discounts should be designed before anything else.**
Philippine law (RA 9994 for seniors, RA 10754 for PWDs) requires most
restaurants and many retailers to give qualifying customers 20% off and VAT
exemption on their purchases, and to record the ID presented. For a Philippine
counter this is likely the most common discount, and it is not optional. It
also changes the tax arithmetic: VAT is removed first and the 20% is taken off
the VAT-exclusive price, so a generic "percent off, then tax" engine gets it
wrong. **Confirm the exact rules together with the BIR item** at the top of the
checklist. The same enquiry covers both, and a POS that issues receipts needs
them right.

### Design

**A table of applied discounts, plus a total on the order.**

```php
Schema::create('order_discounts', function (Blueprint $table) {
    $table->uuid('id')->primary();
    $table->foreignUuid('order_id')->constrained()->cascadeOnDelete();
    $table->string('kind');            // manual | senior | pwd | promo | loyalty
    $table->integer('amount_cents');
    $table->decimal('percent', 5, 2)->nullable();
    $table->string('reference')->nullable(); // ID number, promo code, ledger entry
    $table->foreignUuid('applied_by')->nullable(); // the staff member
    $table->foreignUuid('approved_by')->nullable(); // when over the cashier's limit
    $table->timestamps();
});
// orders: + discount_cents (denormalised sum), + vat_exempt_cents
```

A table rather than one column, because promo codes (§8) and loyalty (§9) are
both discounts with a reference to something else. With one column there is
nowhere to record which code or which points. The total on `orders` stays
because reports and receipts read it constantly.

**Order-level first.** A cashier takes an amount or a percentage off the whole
order. Line-level discounts ("this item is bruised, ₱10 off") come second and
add `order_items.discount_cents`. They don't need to wait for anything else.

**Limits by role.** Two new role fields, alongside the existing
`canManageStaff`: `maxDiscountPercent` (cashier default 0, manager 100) and
`canApproveDiscounts`. Over the limit, the till asks for a manager's
PIN or sign-in on the spot and records them in `approved_by`.

**The server recomputes.** `applyOrderEvent` stops trusting the till's totals.
It recomputes subtotal from the lines, applies the recorded discounts in their
defined order, recomputes VAT, and marks the event `rejected` (§2) if its
totals disagree by more than rounding. That closes finding 3 whether or not a
discount is involved, and it is the only way the discount figures in reports
mean anything. While in there, fix finding 4's `user_id`.

**Where it shows:** receipts (itemised: gross, each discount with its kind,
VAT-exempt amount, net), Sales and Reports pages (gross, discounts, net), and
the order detail in the seller app.

**Online orders:** not in this step. Promo codes bring discounts to the
storefront (§8).

---

## 8. Promo / voucher codes

After §7, which provides the `order_discounts` row a code produces. The till's
old inert "Add Promo or Voucher" box was removed on 2026-08-27
([feature-audit.md §2.1b](./feature-audit.md)). It comes back only when it
works.

```php
Schema::create('promo_codes', function (Blueprint $table) {
    $table->uuid('id')->primary();
    $table->foreignUuid('organization_id')->constrained()->cascadeOnDelete();
    $table->string('code');                     // stored upper-case; unique per org
    $table->string('kind');                     // percent | amount
    $table->integer('value');                   // basis points or centavos
    $table->integer('min_subtotal_cents')->default(0);
    $table->integer('max_discount_cents')->nullable();
    $table->string('channel');                  // online | counter | both
    $table->timestamp('starts_at')->nullable();
    $table->timestamp('ends_at')->nullable();
    $table->integer('max_redemptions')->nullable();
    $table->integer('per_customer_limit')->nullable();
    $table->timestamps();
    $table->softDeletes();
    $table->unique(['organization_id', 'code']);
});
```

**Online is where codes are useful first.** Checkout sends `promoCode`, and
`OnlineOrderController` validates it inside the order's transaction, applies
it, and records the redemption. The order total the shopper sees is always the
server's. The storefront's cart shows the discount from a quote endpoint
(`POST /api/online-orders/quote`), which is worth having anyway because the
delivery fee is also only known server-side.

**At the counter, online-only in v1.** A code checked by an offline till can't
enforce `max_redemptions`, because two offline tills would both honour the last
use. Rather than accept that silently, the till applies a code only when it can
reach the server, and says so when it can't.

**Management:** a Promotions list in the back office (behind the `products`
permission) and in the seller app. Redemption counts come from
`order_discounts` rows with `kind = 'promo'`.

---

## 9. Loyalty

The largest item. Most of the work is a prerequisite the checklist doesn't list.

### Prerequisite: customers on the server

Finding 7: a named customer exists only on the till that created them. Two
tills in the same shop don't share customers, a reinstall loses them, and
the tiers on `CustomersPage` are computed from one device's history. Points
built on that would be points you can lose by clearing a browser.

1. A `pos_customers` table (organization-scoped), synced as a new `customer`
   entity type in `/sync/push` and returned by `/sync/bootstrap` and
   `/sync/pull`.
2. `orders.customer_id`, nullable, written by `applyOrderEvent`.
3. The tier computation moves server-side, over the organization's whole
   history, and the till reads it.

This is worth doing on its own, before any points exist: it fixes
customers for every multi-till shop.

### Loyalty itself

**A ledger, not a balance.** A `loyalty_entries` table with customer, order,
points (positive or negative), reason (earn, redeem, expire, adjust) and who.
The balance is the sum. An adjustment is an entry, never an edit, so a
disputed balance can always be explained.

**Rules per organization:** earn rate (points per ₱), redemption value (₱ per
point), minimum to redeem, and expiry. They live in the shop's settings, and a
shop can leave loyalty off.

**Redeeming is a discount.** It creates an `order_discounts` row with
`kind = 'loyalty'` and a matching negative ledger entry, in one transaction.
That is the reason §7 comes first.

### Decisions this needs before building

- **Identity across channels.** Is a storefront `CustomerAccount` the same
  person as a counter `pos_customer` with the same phone number? Linking by
  phone is the obvious answer, and a phone number typed by a cashier is not
  verified. Linking by phone without a verification step lets anyone spend
  someone else's points.
- **Consent.** Recording named customers' phone numbers for a points scheme is
  personal data processing under the Data Privacy Act (RA 10173). The till
  needs a consent line at enrolment. Confirm the requirement properly; don't
  take it from this document.
- **Per shop or per organization.** Points earned at one branch spendable at
  another is the expected behaviour for a chain, and it is the reason the
  ledger is organization-scoped.

---

## 10. Order of work

```
§2 catalog guard ──► §4 photos (upload endpoint reuses the permission check)
§3 open/closed         (independent)
§5 sound               (independent; needs an asset)
§6 websocket ◄── ws.omaykan.com decision (also unblocks customer app tracking)
§7 discounts ◄── BIR / senior-PWD answer
   └─► §8 promo codes
   └─► §9 loyalty ◄── customers on the server
```

Recommended sequence:

1. **§2 catalog guard.** A security gap, small, no decisions outstanding.
   Includes the `rejected` status, which §4 and §7 both use.
2. **§3 open/closed switch.** Small, and merchants will use it every day.
3. **§5 alert sound.** A day's work once there is a sound file.
4. **§4 photos.** Also removes the base64 from every catalog response, which
   the storefront feels on mobile data.
5. **§6 websocket.** As soon as the Reverb host decision is made. Start the DNS
   and nginx change now, because it has lead time.
6. **Customers on the server** (the §9 prerequisite). Worth doing on its own.
7. **§7 discounts**, once the senior/PWD rules are confirmed.
8. **§8 promo codes**, then **§9 loyalty**.

---

## 11. Open decisions

1. **Reverb host.** `ws.omaykan.com`, a `buildUrl()` override, or a different
   Android library (§6). Recommendation: the subdomain.
2. **Senior citizen / PWD discount rules** (§7). To confirm with the BIR item.
3. **Discount limits.** What a cashier may give without a manager, per shop
   (§7).
4. **Paused shops in the directory.** Listed with a badge (recommended) or
   hidden (§3).
5. **Promo codes at an offline till.** Refused (recommended for v1) or accepted
   with the over-redemption risk (§8).
6. **Loyalty identity.** Whether phone numbers link counter customers to
   storefront accounts, and how that link is verified (§9).
7. **The alert sound** itself, and its licence (§5).
