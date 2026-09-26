# Deployment and operations

**Written 2026-08-27**, after the cutover that moved production onto `main`.
Before this file, none of it was written down anywhere — the deploy procedure
lived in ad-hoc shell scripts in `/tmp` on the server, and the production
build's environment lived only on one laptop.

---

## 1. The server

| | |
|---|---|
| Host | `187.124.138.58`, root over SSH |
| Serves | `omaykan.com`, `www.omaykan.com`, and every shop at `<slug>.omaykan.com` (§6a) |
| OS | Ubuntu 24.04, nginx 1.24, PHP 8.3-FPM, PostgreSQL 16, Redis |
| Static build | `/var/www/omaykan/web` |
| Laravel app | `/var/www/omaykan/backend` (front controller `public/index.php`) |
| Database | PostgreSQL, database `omaykan`, user `omaykan`, local socket only |
| nginx site | `/etc/nginx/sites-available/omaykan`, and `omaykan-shops` for the shop subdomains |
| Certificates | `omaykan.com` (apex + www, nginx authenticator) and `omaykan-shops` (`*.omaykan.com`, DNS-01, §6a) |

**The old `baguioonlinemarket.salidumay.com` address was removed on
2026-09-16.** It had been an alias of this site, and the main certificate was
named after it. The alias left `server_name`, `omaykan.com` + `www` got their
own certificate, and the old one was deleted. Two weeks of logs showed 62 of
about 14,000 requests arriving through it. Its DNS record still points here,
so it now lands on another tenant's default server with a certificate error.
Pre-change `/etc/nginx` and `/etc/letsencrypt`:
`/root/pre-salidumay-removal-20260916-144425.tgz`.
`sites-available/baguioonlinemarket` is a disabled leftover from before the
move to `/var/www/omaykan` and is not loaded.

It is a **shared** box. Other projects (`vault`, `project-tracker`, `portfolio`,
`uniglobal`, `metaclean`, `xponent-global`, `goodboy-care-app`, `colewan-drive`)
run their own nginx sites and node processes on ports 3000–3004, plus a MariaDB
instance nothing here uses. Do not assume a service belongs to Omaykan because
it is running.

### Long-running daemons

Both are systemd units, `Restart=always`, running as `www-data`:

```
omaykan-queue    php8.3 artisan queue:work database --sleep=3 --tries=3 --backoff=10 --max-time=3600
omaykan-reverb   php8.3 artisan reverb:start --host=127.0.0.1 --port=8081
```

`queue:work` is **not optional** — broadcasts are dispatched through the queue,
so with the worker down, no order event ever reaches a merchant's dashboard.

Reverb binds to localhost only and is reached through the nginx `/reverb/`
proxy, not the Pusher default `/app` (that path already serves the POS SPA).
The browser therefore needs `wsPath: '/reverb'`, which is what
`VITE_REVERB_PATH` sets.

---

## 2. Access

Key auth, no password:

```bash
ssh omaykan          # ~/.ssh/config alias → root@187.124.138.58
```

**The alias is `omaykan`.** This page claimed the opposite between 2026-08-28
and 2026-09-08 — that the only working alias was `xponent`, that no `omaykan`
`Host` block existed, and that the key it names does not exist. All three were
wrong for the machine deploys are actually run from, where `~/.ssh/config` holds
exactly one relevant block and no `xponent` block at all, so the corrected
command was the one that could not work:

```
Host omaykan
    HostName 187.124.138.58
    User root
    IdentityFile ~/.ssh/omaykan_vps_ed25519
    IdentitiesOnly yes
```

The box is still shared with xponent-global — that part was true, and §1's
warning about not assuming a running service belongs to Omaykan still stands.
`vault_vps_ed25519` and `project-tracker-deploy` sit in the same directory for
the other tenants; nothing in the deploy path reaches for them.

The commands in §3 have always said `ssh omaykan`, so a deploy run by copying
them worked throughout. Only §2 disagreed.

---

## 3. Deploying

There is no CI. No git remote deploys anything — pushing to GitHub does
nothing to the server. Deploys are build-locally, upload, atomic directory swap.

The server holds no checkout: `/var/www/omaykan/backend` has no `.git`.

### 3.1 Frontend

```bash
npm install
npm run build:web                     # vue-tsc + vite → apps/web/dist
tar czf - -C apps/web/dist . | ssh omaykan "
  mkdir -p /var/www/omaykan/web.release-$TS &&
  tar xzf - -C /var/www/omaykan/web.release-$TS &&
  chown -R www-data:www-data /var/www/omaykan/web.release-$TS"

ssh omaykan "cd /var/www/omaykan &&
  mv web web.bak-$TS-<label> &&
  mv web.release-$TS web &&
  chown -R www-data:www-data web &&
  systemctl reload nginx"
```

**That command needs a reachable catalog API** — the prerender at the end of it
does. See §4; with no local backend, `PRERENDER_API_BASE=https://omaykan.com`
is the usual answer. Two things about failing it that are easy to misread:

- An aborted prerender **still leaves a populated `dist/`** from `vite build`,
  holding unprerendered shells. `dist/index.html` around 6 KB instead of ~196 KB
  is the tell. Check that size before uploading — the shells work, and ship no
  markup to a crawler.
- Its preview server binds port 4178, so a run that died without releasing it
  fails the next build with `Port 4178 is already in use`. Nothing is listening;
  those are TIME_WAIT sockets from the previous run. Run it again.

**The build bakes in the environment.** See §4 — this is the step that has now
broken production twice.

**A moved public path needs its nginx block before the swap, not after.** The
21:26 deploy moved the seller signup form from `/signup` to `/seller/signup`,
and the nginx edit went in first *on purpose*: the new bundle's "Sell with us"
links all point at the new path, so swapping the tree first would have left
them broken for as long as the edit took. The reverse order is free — the old
bundle never links to `/seller/signup`, so the block sits unused until the
swap. See §3.3.

**An unmapped path does not 404 here, it returns 200.** `location / { try_files
$uri $uri/ /index.html; }` catches everything, so a missing entry block serves
the storefront landing page under the wrong URL, with a 200 and no error
anywhere. `/seller/signup` was checked before the deploy and returned exactly
that. **Curl the new path and read the `<title>`** — the status code will not
tell you.

#### 3.1.1 Image variants, and the two nginx blocks they need

`npm run build:web` now runs `apps/web/scripts/optimize-images.mjs` before
Vite. It mirrors every raster under `apps/web/public/` into `public/_opt/` at
480, 960 and 1440 wide, in both WebP and AVIF, and Vite copies that tree into
`dist/` with everything else. Nothing about the upload or the swap changes —
`dist` is just larger on disk and much smaller over the wire.

The markup only ever names the **`.webp`** variants. `srcset` does no format
negotiation — every candidate in it has to be decodable by whichever browser
reads it — so AVIF is nginx's job, swapped in for the same URL when the request
says it can take one. Both blocks below go in the **main** site's server block
(and, if shop subdomains are to get the same treatment, in §6a's too).

```nginx
# /_opt/…/x-960.webp → /_opt/…/x-960.avif, for requests that accept AVIF.
# Two maps because the second reads the first; nginx resolves them lazily.
map $uri $uri_avif {
    default                        $uri;
    "~^(?<stem>/_opt/.+)\.webp$"   "${stem}.avif";
}
map $http_accept $image_candidate {
    default                        $uri;
    "~*image/avif"                 $uri_avif;
}
```

```nginx
location ^~ /_opt/ {
    # Vary is not optional: without it a proxy or CDN will hand AVIF bytes to
    # a browser that asked for WebP, and the image renders as nothing.
    add_header Vary "Accept";
    add_header Cache-Control "public, stale-while-revalidate=86400";
    expires 7d;
    # The .avif first, the .webp if that file is not there.
    try_files $image_candidate $uri =404;
}
```

**`image/avif` may not be in your `mime.types`.** nginx takes the response's
content type from the extension of the file `try_files` lands on, and older
builds do not know `.avif`, so it goes out as `application/octet-stream` and
the browser refuses it. Check, and add it in `http {}` if it is missing:

```bash
grep -r avif /etc/nginx/mime.types || echo 'types { image/avif avif; }  # add to http {}'
```

**Seven days, not a year, and not `immutable`.** `/assets/` can be immutable
because Vite content-hashes those filenames. These are not hashed — the whole
point of the named-slot photographs (FdHero, HighlandBanner, StoriesBand) is
that swapping the file at `/storefront/hero.webp` changes the picture with no
code change, and `_opt` names follow the source. An immutable year would strand
everyone who had already loaded the old one. Seven days with
`stale-while-revalidate` keeps repeat visits instant and lets a replaced
photograph propagate on its own.

**After the swap, check a variant actually serves both ways:**

```bash
curl -sI https://omaykan.com/_opt/storefront/hero-960.webp | grep -i 'content-type\|content-length'
curl -sI -H 'Accept: image/avif,image/webp,*/*' \
  https://omaykan.com/_opt/storefront/hero-960.webp | grep -i 'content-type\|content-length\|vary'
```

The second should come back `image/avif`, `Vary: Accept`, and noticeably
smaller. If it returns the same bytes as the first, the maps are not in the
right server block; if it returns `application/octet-stream`, it is the
`mime.types` line above.

### 3.2 Backend

```bash
git archive --format=tar main backend | ssh omaykan "tar xf - -C /root/staging"
ssh omaykan "cd /root/staging/backend &&
  COMPOSER_ALLOW_SUPERUSER=1 composer install --no-dev --optimize-autoloader"
```

Then, on the server, before swapping:

- copy the live `.env` into the release — **never overwrite the server's `.env`**
  with a local one. Adding a key it lacks is a different thing and sometimes
  required: a release whose code reads a new variable ships against a server
  that has never heard of it. The 2026-09-08 deploy had to append
  `GOOGLE_CLIENT_ID`, without which the Google button the bundle renders would
  have been visible on every sign-in screen and rejected every token. Compare
  the two before swapping:

  ```bash
  comm -13 <(grep -oE '^[A-Z_0-9]+' /var/www/omaykan/backend/.env | sort -u)            <(grep -rhoE "env\('[A-Z0-9_]+'" backend/config/ | sed "s/env('//;s/'//" | sort -u)
  ```

  Most of what that prints is Laravel's own defaults and can be ignored. What
  matters is any key this release's own code added.
- `rsync -a` the live `storage/app/` across. **This is now load-bearing.** The
  private disk holds rider licence and plate photos (`rider-documents/`) and,
  since 2026-08-28, the photo each shop owner uploads for their own shop
  (`store-images/`, written by `StoreImageController`), and since 2026-09-19
  product photos (`product-images/`, see §6b). Skipping this step
  leaves every store record pointing at a file that is no longer there, and the
  shop directory silently falls back to a product photo.

  **Run it again immediately after the swap.** The window between the rsync and
  the `mv` is one an owner can upload into, and what they upload lands in the
  release that is still live — the one about to become the backup. That is not
  hypothetical: on 2026-09-24 a shop replaced its photo at 03:01, between an
  rsync and a swap minutes apart, and the new backend went live with a row
  pointing at a file only the backup held. `GET /api/stores/{id}/image` 404s,
  the directory card falls back to a product photo, and nothing in the deploy
  output says a word about it. The second pass costs nothing and closes it:

  ```bash
  rsync -a /var/www/omaykan/backend.bak-<ts>-<label>/storage/app/ \
           /var/www/omaykan/backend/storage/app/
  chown -R www-data:www-data /var/www/omaykan/backend/storage
  diff <(cd <bak>/storage/app && find . -type f | sort) \
       <(cd /var/www/omaykan/backend/storage/app && find . -type f | sort)
  ```
- create `storage/framework/{cache/data,sessions,views}`, `storage/logs`,
  `bootstrap/cache`
- delete any `bootstrap/cache/config.php` carried over from a build

Swap, then:

```bash
php artisan migrate --force
php artisan optimize:clear
php artisan config:cache && php artisan route:cache && php artisan view:cache
chown -R www-data:www-data storage bootstrap/cache
systemctl restart omaykan-queue omaykan-reverb
systemctl reload nginx
```

Artisan runs as `www-data` and needs a writable `HOME` — without it Tinker dies
on `Writing to directory /var/www/.config/psysh is not allowed`:

```bash
sudo -u www-data env HOME=/tmp php artisan ...
```

### 3.3 Rollback

Directory swaps are reversible.

**Updated 2026-09-26, 15:45 UTC.** Backend plus nginx: town and category
landing pages, `/baguio`, `/baguio/baked-goods`, `/la-trinidad/vegetables` and
the rest, and `/sitemap-towns.xml` listing the ones with shops (§6c). Released
from `e3ed3a7` on top of `3e3fdde`; the live backend was checked first and
matched `3e3fdde` file for file. No migration, no new `.env` key.

**The frontend half of that commit did not go out** — the footer's "Shops in
Baguio" links and the second `Sitemap:` line in `robots.txt`. A build of
`3e3fdde` from a clean checkout, with or without this change, renders no
products on `/`: the prerender refuses it ("rendered 0 products (need 10)")
and a headless browser finds 0 `?product=` links where the live page has 82,
against the same API. The live build also has the shop-messages link, which
`VITE_FEATURE_MESSAGES=false` compiles out. So the 2026-09-24 frontend was
built from a tree or a `.env.production` that is not in `main`, and shipping
`main` would have taken the shelves off the home page. Reconcile that first;
then this commit's frontend is an ordinary build (§3.1). Until then, submit
`/sitemap-towns.xml` in Search Console by hand.

| | Roll back to | Holds |
|---|---|---|
| Backend | `backend.bak-20260926-154257-town-pages` | the `3e3fdde` backend |
| nginx | `/root/nginx-omaykan.bak-20260926-154257-town-pages` | the config without the two town-page blocks |

Either rolls back alone. With only the backend rolled back, nginx hands the
town paths to a Laravel that has no route for them and they 404; with only
nginx rolled back, they fall through to `index.html` with a 200.

Verified after the swap, from the server: `/baguio`, `/baguio/`,
`/baguio?utm_source=x`, `/la-trinidad` and three category pages serve their own
titles; `/baguio/hardware` is a 404; no `Set-Cookie`, and `Cache-Control:
max-age=300, public`; an empty page carries `noindex, follow`. `/`, `/about`,
`/account`, `/cart`, `/seller/signup`, `/seller/founding`, `/rider`, `/app`,
`/api/stores`, `/sitemap.xml`, `/robots.txt`, a shop checkout and a shop
subdomain all still answer as before; storage matched the backup file for
file after the second rsync; the queue and Reverb restarted active; nginx's
error log is clean. A headless browser outside the box opened `/baguio` at
1280px and `/la-trinidad` at 390px: no console errors, no broken images, no
horizontal overflow. Live placement at release: Baguio 3 shops, La Trinidad 1;
`colewan-market` is in neither town (its address names neither and its pin is
outside both radii).

**Updated 2026-09-23, 16:45 UTC+8.** Sign in with Google, on every login form.
Backend first, then frontend — the new cards call two endpoints that did not
exist.

Two separate faults, fixed together:

- **The button had vanished from the three cards that already had it.** Nothing
  was removed from the code. `apps/web/.env.production` is untracked, and the
  14:52 build went out with `VITE_GOOGLE_CLIENT_ID=` blank — carrying
  `.env.production.example`'s comment verbatim, so it looks regenerated rather
  than edited. A blank id makes `googleSignInAvailable()` false, which ships no
  GIS script at all and hides every button silently. The id is back in
  `.env.production` and in `.env` (which had never had the line, so the dev
  server never showed the button either). **This is the failure mode §4 warns
  about, and it is now the second time it has bitten.**
- **Riders and the operator console never had it.** Both now do, through
  `POST /api/rider/auth/google` and `POST /api/platform-admin/auth/google`.

Both new endpoints **sign in only and never register**, which is the staff
policy, not the shopper one:

- A rider account is earned with a licence photo and a plate photo an operator
  has reviewed; no Google credential carries either. A Google account with no
  rider row is told to register. There is deliberately no Google button on the
  rider *registration* form, where it would look like a way past the documents.
- An operator account is made from the console with `platform-admin:create`. A
  Google identity with no row is refused and logged. Worth stating plainly,
  because this is the identity that acts across every tenant: it makes control
  of that Google mailbox equivalent to the operator's password, so an operator's
  Google account wants 2FA on it, and removing an operator means disabling the
  row here — not only revoking their Google access.

Schema: `google_sub`, nullable and unique, on `riders` and `platform_admins`.
No `avatar_url` on either — a rider's `avatar_path` is a private-disk key and a
remote URL in it would break the picture — and `password` stays NOT NULL on
both, because neither door can create a row that has never had one.

| | Roll back to | Holds |
|---|---|---|
| Backend | `backend-prev-20260923-164500-google-rider-admin` | the build before the two Google endpoints |
| Frontend | `web.bak-20260923-164500-google-rider-admin` | the 16:18 build: Google on three cards, not five |
| Database | `/root/db-backups/omaykan-predeploy-20260923-google-rider-admin.dump` | taken immediately before `migrate` |

Rolling the backend back needs the migration reversed first (`migrate:rollback
--step=1`); the column is additive, so a frontend-only rollback is safe on its
own.

Verified after the swap: 544 backend tests and 113 frontend tests pass, and
`vue-tsc` is clean. On the live host, `/account`, `/seller/signup`, `/app`,
`/rider` and `/platform-admin` all serve 200 and all five reach
`assets/google-*.js` with the client id in it. Both new endpoints answer 422 to
an absent credential and 422 to a junk one — the two `GoogleIdentityException`
lines in `laravel-2026-09-23.log` at 08:41:51 UTC are those probes. The staff
and customer Google endpoints still answer, `/api/stores` returns 200, and
nginx's error log is clean.

**Updated 2026-09-22, 13:19 UTC.** Frontend only, finishing the 13:08 shop page
rework:
- With "All" selected, every aisle now shows, 3 items each. Before, only the
  first aisle did, so most of the menu could only be reached through the pills.
- "See all" on the favourites opens the whole menu.
- Product-card hearts now save to the wishlist in `commerce/favorites.ts` and
  stay across reloads.
- The shop-cover heart, which saved nothing, is gone.
- The invented "Open today · Usually prepares orders in 15–30 min" is replaced
  by the shop's real ordering state.
- At phone width, "Store information" and "Why shop here?" stack one per row.
- The fallback cover is now `seller-hero-v2.webp` (155 KB, down from a 2.5 MB PNG).

No backend, no migration, no nginx change.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260922-131920-shop-page-finish` | the 13:08 shop-page-rework build |

Verified after the swap: the main pages and the shop checkout serve their own
titles, and the new cover returns 200. A headless browser opened
`nenas-market-stall.omaykan.com` at 1440px and 390px. It saw all five aisles
(15 cards, and 20 after "See all") and "Taking orders now". A heart survived a
reload and was then removed again. Adding an item and pressing checkout landed
on `/shop/nenas-market-stall/checkout`. No console errors.

**Updated 2026-09-22, 13:08 UTC.** Frontend only, a full build from the
working tree: the shop page rework (`ShopPage.vue`, `MenuCard.vue`,
`store-menu.css`, and the new fallback cover `public/storefront/seller-hero-v2.png`),
all uncommitted. The 12:32 store-checkout work is unchanged. No backend, no
migration, no nginx change.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260922-130838-shop-page-rework` | the 12:32 store-checkout build |

Verified after the swap: `/`, `/cart`, `/account`, `/app`, `/seller/signup`,
the shop subdomain and `/shop/nenas-market-stall/checkout` serve their own
titles, and the new cover image returns 200. A headless browser, at 1440px and
390px, opened `nenas-market-stall.omaykan.com`, saw the shop's name as the
heading and no horizontal overflow, added an item and pressed checkout. It
landed on `/shop/nenas-market-stall/checkout` with no console errors.

**Updated 2026-09-22, 12:32 UTC.** Frontend plus one nginx line: each shop's
own checkout at `/shop/<slug>/checkout` (§6a). The shop page's checkout button
and `/cart`'s "Proceed to checkout" now go there; `/cart?step=checkout`
forwards. Built from the working tree, whose other uncommitted files were last
touched before the 2026-09-21 14:01 release, so nothing else new went out. No
backend, no migration.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260922-123208-store-checkout` | the 2026-09-21 14:01 sales-redesign build |
| nginx | `/root/nginx-omaykan.bak-20260922-123208-store-checkout` | the config without the checkout line |

The nginx line went in before the swap and is harmless with the old tree:
`checkout.html` is missing there, so the path 404s. Verified after the swap:
`/`, `/cart`, `/account`, `/app`, `/seller/signup`, the shop subdomain and
`/shop/nenas-market-stall/checkout` (with a trailing slash and a query too)
serve their own titles, and `/api/stores` returns 200. A headless browser, at
1440px and 390px, added an item on `nenas-market-stall.omaykan.com`, pressed
checkout, and landed on the shop's checkout signed out. The page was titled
"Checkout — Aling Nena Market Stall", showed the line and the sign-in prompt,
had no horizontal overflow and logged no console errors. No order was placed.

**Updated 2026-09-21, 14:01 UTC.** Frontend only, a full build from the
working tree again: the Sales page redesign (KPI cards, a trend chart, payment
and order-type breakdowns, peak hours, a recent-sales table), adding a
category from the product sheet's category picker, and whatever else was
uncommitted in the tree at the time, including in-progress `OrdersPage.vue`
work. No backend, no migration, no nginx change.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260921-140115-sales-redesign` | the 13:36 demo-catalog build below |

Verified after the swap: the catalog and `/api/stores` calls return 200, the
five public pages serve their own titles, `/app` serves the new bundle, and a
headless browser found no errors on the landing page, a shop page or `/app`
(apart from the signed-out 401s noted below).

**Updated 2026-09-21, 13:36 UTC.** A full frontend build from the working tree,
plus one backend config file.

- **Frontend.** A till signed in to a shop no longer shows the bundled demo
  catalog. Before this, a failed sync filled an empty cache with the 550 demo
  products, and a later pull kept them, so a merchant with no products on the
  server saw a full Products page while their shop page said "We couldn't find
  that shop". `readShopCatalog()` in `packages/data` now drops demo ids from
  the cache. The build also carries the uncommitted Products-page filter work
  (`ProductsPage.vue`, `FilterDropdown.vue`, `app.css`).
- **Backend.** Only `config/paymongo.php` (the default payment methods are now
  `qrph` alone, from `dc91931`) and `.env.example` were copied in, and the
  config was re-cached. Every other backend file already matched `HEAD` by
  md5. The live `.env` does not set `PAYMONGO_PAYMENT_METHODS`, so the
  subscription checkout now offers QRPh only. No migration.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260921-133551-demo-catalog-strip` | the 2026-09-20 PayMongo build |
| Backend config | `/root/backend-file-bak-20260921-133551/` | the previous `paymongo.php` and `.env.example`; copy back, then `config:cache` |

Verified after the swap: the catalog and `/api/stores` calls return 200;
`/`, `/app`, `/cart`, `/seller/signup` and `nenas-market-stall.omaykan.com`
serve their own titles; `config('paymongo.payment_methods')` reads `qrph`;
and a headless browser found no console errors on the landing or shop page.
A signed-out `/app` logs 401s from its sync and staff calls before it
redirects to sign-in. No changed code makes those calls, but they were not
compared against the previous build.

**Updated 2026-09-19, 00:55 UTC.** Frontend only, signup page only again:
signing in on `/seller/signup` now lands in `/app` signed in. Until now it
bound the browser to the shop and sent it to `/app` with no session, so the
register bounced to `/app/auth?redirect=/dashboard` and asked for the same
password again. That looked like the first sign-in had failed.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260919-005555-signup-sign-in-handoff` | the 00:42 build: redesigned page, sign-in asks twice |
| Backend | `backend.bak-20260916-150348-shop-subdomains` | unmoved |

**`/app` was not redeployed, and does not need to be.** The signup page now
finishes the sign-in itself. It claims the local cache for the shop (the same
wipe `/app` does at boot, now shared as `claimLocalCacheFor` in
`tenantBinding.ts`), trades the sign-in token at `/api/staff/session-store`,
and writes the session where `/app` reads it at boot. The live `/app` bundle
(`app-DHslhQYz.js`) picks it up unchanged. That was tested end to end against
production with the two sign-in calls stubbed: `/app/dashboard`, signed in as
the stubbed user. A refused `session-store` (e.g. a suspended shop) leaves the
person on the signup page with the server's message.

`/app` could not have been rebuilt cleanly anyway. The live bundle contains
changes that are in neither `HEAD` nor separable from the unreleased work in
the tree. Vue's scoped-style ids hash each component's source, so a component
that differs shows up as a different `data-v-*`, and the dashboard's does.

Same method as 00:42: live tree plus `signup.html` and three new files
(`signup-*.js/css`, `tenantBinding-*.js`). `signup.html` was pointed back at
the live `app-7AsGTDy6.css`. The rebuilt one differed only by an unreleased
`.ps-textarea` rule. The new `tenantBinding` chunk bundles the working tree's
`packages/data`, but the page runs only the cache wipe, the repository
constructor and the store open from it. The one extra write, a tenant-access
status key, is ignored by the live `/app`.

**The 2026-09-19 00:42 UTC release, for reference.** Frontend only, and only one page: the
seller signup redesign at `/seller/signup` — a new pitch (`MerchantPitch.vue`),
header and footer, and the form split into two steps (account, then store).
It posts the same fields to the same `POST /api/signup`. No backend, no
migration, no nginx change.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260919-004201-seller-signup-redesign` | the 2026-09-18 white-storefront build, old signup page |
| Backend | `backend.bak-20260916-150348-shop-subdomains` | unmoved |

**This was not a full-tree build, on purpose.** The working tree also held that
morning's unreleased work — tenant access, the shop ordering pause, plan
pricing — whose backend (three new migrations) is not deployed. A normal build
would have shipped all of it. Instead the release is a copy of the live tree
plus exactly what the signup page needs: the new `signup.html`, the seven
chunks its import graph reaches that were not already live (`signup-*.js/css`,
`tenantBinding-*`, and four lucide icon chunks), and
`hero-merchant-redesign.webp`. `diff -rq` against the live tree showed nothing
else. The one shared chunk that changed, `tenantBinding`, differs from the live
one only in which icons it bundles. Every other entry still serves its
2026-09-18 bytes, so **the next full build will still ship everything else in
the working tree as new**.

Verified after the swap: `/seller/signup` and `/signup` (301) serve the new
title and bundle, the catalog call returns 200, and a headless browser at 1440
and 390px found no console errors, failed requests, broken images or
horizontal overflow, and walked both form steps (it did not submit).

**The 2026-09-18 13:03 UTC release, for reference.** Frontend only: the storefront's cream
ground and sand surfaces became white and neutral grey — landing, shop pages,
account, cart and checkout. Platform admin, rider portal and the POS themes are
untouched. No backend, no migration, no nginx change.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260918-210330-white-storefront` | the 2026-09-17 15:10 shop desktop layout build, cream ground |
| Backend | `backend.bak-20260916-150348-shop-subdomains` | unmoved — see below |

Verified after the swap: the catalog call returns 200, `/`, `/cart`, `/account`
and `nenas-market-stall.omaykan.com` serve their own titles, and the served
CSS has `body { background: #fff }` with no cream values left in it.

**The 2026-09-17 15:10 release, for reference.** Frontend only: the shop page's desktop
layout. Until now the page was the same phone-width shell at every size; at
`min-width: 1000px` it now has a site header with search and nav, a two-column
body with a sticky right rail (cart, store information, "Why shop here?"), a
Best Sellers row, an All Products grid with a sort control, and a footer. CSS
only for the split — the mobile shell is untouched below 1000px. No backend,
no migration, no nginx change.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260917-151008-shop-desktop-layout` | the 14:37 redesign build, phone-width at every size |
| Backend | `backend.bak-20260916-150348-shop-subdomains` | the 2026-09-13 09:26 release, no slug rules |
| Database | `/root/db-backups/omaykan-predeploy-20260916-150348-shop-subdomains.sql.gz` | taken before the 2026-09-16 swap; unaffected since |

**One defect was caught before this shipped, and is worth remembering.**
`.shop-profile__heading` is lifted onto the cover with `top: -36px`, and the
desktop cover is only 178px tall, so the `<h1>` landed on top of
`.shop-hero__sign` — the painted sign that already carries the shop's name.
Both shops' names printed over each other, 242–335px of overlap on all four
live shops at every width from 1100px up. The fix hides the sign at desktop
and lets the real heading carry the name. **Measure it, do not look at it:**
the page has no console error and no layout overflow in either state, so
nothing but geometry tells the two apart. Comparing the heading's rectangle
against the sign's at 1100/1240/1440px is what found it.

Verified after the swap, at 1440px and 420px on all four shops: the sign is
hidden at desktop and still drawn on mobile, the heading collides with
nothing, no horizontal overflow, no console errors, every title its shop's
name, and no broken images (SM's slow-loading `smmarkets.ph` hotlinks are
third-party and predate this work).

**The 14:37 release, for reference.** Frontend only: the shop page redesign —
`ShopPage.vue` rebuilt around a single phone-width shell with its own
`store-menu.css`, a new default cover photo, and no `FdHeader`/`FdFooter`.
No backend, no migration, no nginx change: the page is served by the
`omaykan-shops` block that has been in place since 2026-09-16 (§6a), and the
new cover is an ordinary file under the web root that `location /` already
serves. The backend rollback point below is therefore unmoved from the
2026-09-16 deploy, and still matches what is running.

Its rollback point was `web.bak-20260917-143737-shop-page-redesign`, which
holds the 2026-09-16 shop-subdomains build and the pre-redesign shop page;
that directory is still on the box.

Verified after the swap: the catalog call returns 200, and
`nenas-market-stall.omaykan.com` renders in a headless browser with its title
set to the shop's name, all images loading and no console or page errors.

**The 2026-09-16 release, for reference.** Frontend and backend: shop
subdomains (`ccf0abb`). No migration — production already had all 30. nginx
gained the `omaykan-shops` site and lost the
`baguioonlinemarket.salidumay.com` alias earlier the same day (§1, §6a);
neither is undone by a directory swap. Its frontend rollback point was
`web.bak-20260916-230147-shop-subdomains`, which held the 2026-09-13 21:26
build and no `shop.html`; that directory is still on the box. Its frontend
label used the deploying laptop's clock, UTC+8, while the backend's used the
server's, UTC — they were the same deploy.

**The first backend swap served 500s for about a minute.** The live `.env`
was copied into the release with a plain `cp` as root, which left it
`root:root 0640`. PHP-FPM runs as `www-data`, could not read it, and Laravel
fell back to its defaults — SQLite at `database/database.sqlite`, which does
not exist — so every request failed on the cache table. `migrate --force` ran
against that same missing file and touched nothing. The release was swapped
back out, the file chowned to `www-data:www-data`, a `tinker` check confirmed
it read `pgsql`/`omaykan` before the second swap, and that one went in with an
automatic swap-back if any artisan step or `/api/stores` failed.

**Copy the `.env` with `cp -p`, or chown it, and check the release reads it
before swapping:**

```bash
sudo -u www-data env HOME=/tmp php artisan tinker --execute='echo config("database.default");'   # expect pgsql
```

Also left out of this release on purpose: order push notifications
(`OrderPushToken`, `PushSender`, `NotifyCustomerOfRider`, migration
`2026_09_16_000100_create_order_push_tokens_table`), which were being written
in the same working tree at the time and are uncommitted.

**Updated 2026-09-14, 13:28.** nginx-only. `/app.html` now 301s to `/app`, so
the staff app has one public URL instead of two. **No directory swap, no
build** — the frontend and backend releases below are still what is running,
and there is nothing to roll a tree back to.

Pre-edit config: `/root/nginx-omaykan.bak-20260914-132808-app-html-redirect`.
Restore it and `nginx -t && systemctl reload nginx` to undo. Four lines
replaced the two `/app` blocks:

```nginx
location = /app.html       { return 301 /app; }
location = /app            { try_files /app.html =404; }
location ^~ /app/          { try_files $uri @staff_app; }
location @staff_app        { try_files /app.html =404; }
```

**The named location is the whole trick, and it is not cosmetic.** `/app/`'s
old fallback was `try_files $uri /app.html`, where `/app.html` is the last
parameter and therefore an *internal redirect*, not a file read — it re-enters
location matching. Adding the 301 without restructuring would have sent every
deep link through it: `/app/auth` finds no file, internally redirects to
`/app.html`, matches the new exact block and 301s the browser to `/app`,
dropping the route. The router is `createWebHistory('/app/')`
([router.ts:31](../packages/core/src/app/router.ts#L31)) and the landing and
merchant footers both link `/app/auth`, so that would have broken merchant
sign-in from the public site. A named location is a file read, not a rematch.

Nothing in any bundle or app linked to `/app.html` — the Vue entries use `/app`
and `/app/auth`, the seller Android app's `REGISTER_URL` is already
`$WEB_ORIGIN/app`, and the dev server has aliased `/app` since
[vite.config.ts:26](../apps/web/vite.config.ts#L26). The old path was only ever
reachable because `location /`'s `try_files $uri` served the raw file. The
redirect is therefore a courtesy to bookmarks, not load-bearing the way
`/signup` is.

The other entries are still reachable at their raw `.html` paths
(`/account.html`, `/cart.html`, `/landing.html`, ...). Same one-line fix each,
but only `/app.html` was asked for.

**Updated 2026-09-13, 21:26.** Frontend-only, and the fifth deploy of the day:
the seller signup form moved from `/signup` to `/seller/signup`. No backend, no
migration, no schema — the 09:26 backend release below is still what is
running.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260913-212608-seller-signup-route` | the 18:20 build, signup links pointing at `/signup` |
| Backend | `backend.bak-20260913-092614-product-gallery` | unchanged since 09:26 |

**This deploy also edited nginx, which the directory swap does not undo.** The
pre-edit config is at `/root/nginx-omaykan.bak-20260913-212608-seller-signup`.
Four lines changed, replacing the two `/signup` blocks:

```nginx
location = /seller/signup  { try_files /signup.html =404; }
location ^~ /seller/signup/ { try_files $uri /signup.html; }
location = /signup         { return 301 /seller/signup; }
location ^~ /signup/       { return 301 /seller/signup; }
```

**Rolling the frontend back alone is safe and needs no nginx change.** The
entry is still built as `signup.html` in every generation, so `/seller/signup`
serves the old build's form and `/signup` still reaches it one redirect later.
Restoring the old config alongside an old tree is also fine. The one
combination that breaks is the old config with the *new* tree: `/signup` then
serves a form whose own links point at `/seller/signup`, which nothing maps.

`/signup` redirects rather than 404s because `SIGNUP_URL` in the seller Android
app (`ExternalLinks.kt`) opens it and installed copies cannot be re-pointed.
The constant is updated in the repo, but that only reaches users who update the
app — **the redirect is load-bearing indefinitely, not a transitional
courtesy.**

The API endpoint is untouched and still `POST /api/signup`; only the page moved.

**Updated 2026-09-13, 18:20.** Frontend-only, and the fourth deploy of the
day: the Google button on the staff sign-in card. No backend, no migration, no
schema — the 09:26 backend release below is still what is running.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260913-180226-staff-google-signin` | the 09:43 build, sign-in with no Google button |
| Backend | `backend.bak-20260913-092614-product-gallery` | unchanged since 09:26 |

**Nothing needed deploying on the backend, and that is the point of this
release.** `POST /api/staff/auth/google` has been served since the operator
portal went out on 2026-09-12 (`1900ec8`), and `GOOGLE_CLIENT_ID` has been in
the server's `.env` since 2026-09-08 — §3.2 records appending it. The endpoint
verified tokens and refused them for want of anyone pressing a button. The only
thing missing was the button, which is bundle, which is why a directory swap is
the whole deploy. Both preconditions were checked against production before the
build rather than assumed:

```bash
curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"credential":"not.a.jwt"}' https://omaykan.com/api/staff/auth/google
```

A 404 would mean the route is not deployed. *"Google sign-in is not configured
on this server."* would mean the `.env` key is missing. What it must say is
*"That Google sign-in could not be read."* — the verifier got past its config
check and rejected the junk on its merits.

**Two swaps went out for this, 18:02 and 18:20, and the bundles are
byte-identical** — verified by checksumming both trees, not by comparing
filenames. The second was run without knowing the first had happened. It was
harmless because the bytes matched, but it cost a generation:
`web.bak-20260913-182017-staff-google-signin` holds a copy of what is live and
is worth nothing as a rollback point. **The name is not the content.** These
directories are named for the deploy that *displaced* them, so a backup labelled
`staff-google-signin` is the build from *before* that release — which is why the
table above rolls back to `...-180226-staff-google-signin` and not to the
later-numbered one. Check for the chunk rather than reading the label:

```bash
ls /var/www/omaykan/<generation>/assets/google-*.js   # absent = pre-Google build
```

Rolling the frontend back alone is safe and is the whole procedure —
`mv web web.bad && mv web.bak-20260913-180226-staff-google-signin web` plus
`systemctl reload nginx`, no daemon restart and no dump. The endpoint simply
goes back to being unreachable, which is what it was for its first day alive.

This is also the deploy that made the **staff** Google endpoint's first tests
exist. It had none: the customer one was covered from the day it shipped, and
the staff one went out inside a release named for something else and was never
pressed. Twelve tests now sign real JWTs against the fixture key in
`backend/tests/Fixtures`, so the production verifier runs its true signature
check. They ship with the backend but are inert there — `composer install
--no-dev` per §3.2 — so they changed nothing about what is running.

**Updated 2026-09-13, 09:43.** Frontend-only, and the second deploy of the
day: the product page's own width. No backend, no migration, no schema — the
09:26 backend release below is still what is running.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260913-094313-pdp-width` | the 09:26 build, same page at 1140px |
| Backend | `backend.bak-20260913-092614-product-gallery` | unchanged since 09:26 |

`mv web web.bad && mv web.bak-20260913-094313-pdp-width web` plus
`systemctl reload nginx` — no daemon restart and no dump. One step further
back, `web.bak-20260913-092614-product-gallery`, is the 2026-09-12 portal
reskin, which predates the rebuilt product page entirely.

**Updated 2026-09-13, 09:26.** Backend and frontend both, for the product page
rebuild and the gallery behind it. One migration,
`2026_09_13_000100_add_product_gallery_and_pack_fields`: three nullable columns
on `products` (`photo_urls` json, `brand`, `packaging_type`) and `image_url`
widened from `varchar(255)` to `text`.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260913-092614-product-gallery` | the 2026-09-12 09:59 portal-reskin build |
| Backend | `backend.bak-20260913-092614-product-gallery` | the 2026-09-12 operator portal release |
| Database | `/root/db-backups/omaykan-predeploy-20260913-092614.dump` | taken immediately before the swap |

**Neither half needs the dump.** The three columns are new and nullable and the
2026-09-12 code selects named columns, so it never sees them; the widening is
in the safe direction, and at deploy time the longest `image_url` in 528 rows
was 236 characters, so nothing had grown past what the old column could hold.
Rolling back is a directory swap plus the two daemon restarts.

Rolling the **frontend** back alone is safe. The new fields are additive on
`GET /api/storefront/catalog`, and the old bundle ignores keys it does not read.
Rolling the **backend** back alone is also safe: the new bundle reads
`photoUrls` through `?? []`, so a catalog that stops sending it renders one
photo and no thumbnail strip, and the Brand and Packaging rows hide themselves.
That is the same graceful path a product with no gallery already takes.

`storage/app/` held only its three `.gitignore` files at this deploy — no
`store-images/`, no `rider-documents/`, and no store row with an `image_path`.
The rsync in §3.2 still ran and still matters for the next one.

This release also fixes a **silent data-loss bug on the sync seam**, which is
worth knowing about when reading older rows: `applyProductEvent` wrote none of
a product's presentation fields and `enqueueProductEvent` sent none of them, so
`image_url`, `unit_label` and `compare_at_price_cents` set at a till never
reached the server. Products created in the POS before 2026-09-13 have none of
those server-side even where the merchant filled them in, and no backfill was
run — the till's own copy is the only place that data ever existed. Same seam
and same shape as the `business_modes` bug of 2026-08-27.

**Updated 2026-09-12, 09:59.** The first **backend** deploy since 2026-09-09,
and the first release of the day that is not frontend-only. The operator
portal at `/platform-admin` was rebuilt around nine screens and given five new
read endpoints plus one writable one; four migrations went with it. Rollback
is no longer split:

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260912-095912-portal-reskin` | the 09:54 build — same portal, two screens unstyled |
| Backend | `backend.bak-20260912-095050-operator-portal` | the 2026-09-09 storefront landing rework |
| Database | `/root/db-backups/omaykan-predeploy-20260912-095050.dump` | taken immediately before the swap |

**The backend can be rolled back without the dump.** All four migrations are
additive — three new tables (`platform_settings`, `rider_ratings`,
`conversations` + `conversation_messages`) and five nullable columns on
`riders`, which had no rows. The 2026-09-09 code ignores every one of them, so
swapping the directory back and restarting the two daemons is the whole
procedure. The dump is there for the case where something has *written* to the
new tables and you want the state before that; restoring it would lose every
order taken since, so prefer fixing forward.

Rolling the frontend back alone is safe in either direction: the portal's
bundle is the only thing that calls the new endpoints, and the endpoints
ignore a client that never arrives.

### What made this release necessary

The portal could not sign anyone in, and had not been able to since it was
written. It called `/api/platform/*` throughout; the backend has only ever
served `/api/platform-admin/*`, so production answered *"The route
api/platform/login could not be found"* on every attempt. Two screens also
used `GET` where the route takes `POST`. The same bug sat in the standalone
`/support-inbox` page, which could not get past its session check.

Nothing would have reported this. There is no monitoring (§6), the portal is
linked from nowhere, and a 404 on a fetch renders as an error message inside a
page that otherwise looks fine. It was found by opening the page.

### 3.3.1 The earlier frontend deploys of 2026-09-12

Two **frontend-only** deploys that morning, both
the same change: the listing banner became a photograph. 08:47 shipped
`HighlandBanner.vue` — its generated SVG ridges replaced by an `<img>` — plus
the new `apps/web/public/storefront/listing-highland.webp` it points at. 08:52
swapped that asset for a different photograph and moved the crop from 45% to
60%. Neither touched the backend, so the rollback point stays split:

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260912-085248-listing-hero-terraces` | the 08:47 build, same code, first photograph |
| Backend | `backend.bak-20260909-224036-storefront-landing-rework` | unchanged since 2026-09-09 |

Reverting the frontend alone is `mv web web.bad && mv web.bak-20260912-085248-listing-hero-terraces web`
plus `systemctl reload nginx` — **no daemon restart and no dump**: no backend
code, no migration, no schema. It needed no nginx change: the banner is part of
the landing page's `?category=` face, already served by `index.html`. One step
further back, `web.bak-20260912-084722-listing-hero-photo`, is the 2026-09-11
23:02 aisle-listing build — the last one without a photograph at all.

**The banner asset is not content-hashed.** It lives in `public/`, so it ships
under its own name and a second photograph reuses the path the first one had —
only the `main` chunk's hash moves. nginx serves `/storefront/` with an ETag
and no `Cache-Control`, so browsers revalidate and pick the new file up; had
that path been given a long `max-age`, the 08:52 deploy would have left the old
photograph on every screen that had already seen it. Check the bytes on the
server rather than trusting the filename:

```bash
ssh omaykan "sha256sum /var/www/omaykan/web/storefront/listing-highland.webp"
```

Both builds went out from an **uncommitted working tree** — `HighlandBanner.vue`
and the `.webp` are unstaged in `main` as of 08:52. That is the state `e497096`
was meant to end (see below); until they are committed, nothing in git
describes what is live, and the two deploys are indistinguishable in history.

As of 2026-09-13, 18:20 there are **thirteen** web generations on disk and four
backend ones, 767M across the lot, against the "keep one" rule below: four were
made on 2026-09-12 and four more on 2026-09-13, because that many separate
swaps went out on each of those days. Two of the four from 2026-09-13 hold the
same bytes — see the 18:20 entry. The box is at
9% of 96G so nothing forces the issue, but nothing prunes them either — and
`*-20260908-230835-rider-map-staff-auth` is kept deliberately, for the reason
given further down. `ls -d /var/www/omaykan/*.bak-*` is the inventory; trust it
over this paragraph, which is a snapshot.

`e497096` is also the first commit to hold the web as it ships: the 2026-09-10
and 16:30 builds went out from an uncommitted working tree. The aisle listing
needed no nginx change — it is the landing page's `?category=` face, served
by `index.html`.

The 16:30 deploy also added the one nginx line the new `/cart` entry needs —
`location = /cart { try_files /cart.html =404; }`, beside the `/account` rules.
The pre-edit config is at `/root/nginx-omaykan.bak-20260911-163000`. Rolling
the web back leaves that line harmless: `/cart` then 404s instead of opening a
page the old build does not have.

The frontend now runs **ahead of the backend**. The web tree carries shopper
messaging and rider ratings, whose endpoints are only in the undeployed
backend, so `.env.production` builds with `VITE_FEATURE_MESSAGES=false` and
`VITE_FEATURE_RIDER_RATING=false` (see `apps/web/src/commerce/features.ts`).
When the backend that serves them goes out, delete both lines and rebuild —
otherwise the features stay hidden behind a backend that supports them.

To roll the backend back as well, swap its directory too and restart the two
daemons. A database dump taken immediately before the 2026-09-09 deploy is at
`/root/db-backups/omaykan-predeploy-20260909-224036.dump` (`pg_restore` format).

That release was frontend-heavy — a landing page rework — plus one controller
returning two extra fields on `GET /api/stores` (`categories`, `isNew`). It
added **no migration**, so `migrate --force` reported "Nothing to migrate" and
rolling back to it is a directory swap alone, with no dump to restore.

The generation before it, `*-20260908-230835-rider-map-staff-auth`, is still on
disk (the box is at 9% of 96G). §3.3's "keep one generation" says to prune it;
it was left deliberately, because rolling back *past* 2026-09-08 needs the dump
restored as well and having the directory costs nothing while disk is this free.

The 2026-08-28 pair this section named until now **was already gone** when the
2026-09-08 deploy went out — pruned by hand at some point, leaving only a 4K
`web.failed-20260828-125957`. For eleven days this page named a rollback point
that did not exist, and nothing would have said so until someone needed it.
Check that the directory is there before trusting the name written here.

**Rolling back past 2026-09-08 is not a directory swap alone.** That release
retired device pairing: `stores.public_store_code` and `pairing_code_hash` are
dropped, and `sync_cursors` is now unique per store/user/cursor rather than per
device/cursor. The older code needs those columns and cannot sign a till in
without them, so a rollback means restoring the dump as well — and the dump
predates every order taken since. Prefer fixing forward.

Keep one generation. Older ones were deleted on 2026-08-27 after they had
accumulated to 525M across 23 directories, and by 2026-08-28 six web
generations had built up again — the pruning is not automatic and nothing
prompts for it.

---

## 4. The build environment is the sharp edge

`VITE_*` variables are **compile-time constants**, inlined into the bundle. A
wrong value cannot be fixed on the server; it needs a rebuild and redeploy.

This has already cost a live outage. Production was built with
`VITE_POS_ORGANIZATION_SLUG` and `VITE_POS_STORE_CODE` blank, so every visitor
requested:

```
GET /api/storefront/catalog?orgSlug=&storeCode=   →  422
```

and the storefront rendered with **zero products** while the database held 464.
The failure is silent by design: `loadStorefrontCatalog` catches the error and
falls back to the demo catalog, which is itself empty unless
`VITE_POS_DEMO_ORG_SLUG` is set. Nothing on the page said anything was wrong.

`apps/web/.env.production` holds the correct values, and **`.gitignore` excludes
every `.env*`**, so it is not in the repository. `apps/web/.env.production.example`
is committed alongside it as the reproducible record — keep the two in step, and
treat a blank tenant slug as a release blocker.

**It has now happened twice.** On 2026-09-23 the 14:52 build went out with
`VITE_GOOGLE_CLIENT_ID=` blank, and the Google button disappeared from the
shopper, seller and staff sign-in cards. Same shape as the outage above: a blank
value is a supported configuration — it means "this build ships no Google
button" — so nothing failed, nothing logged, and the pages rendered correctly
with a feature missing. The file carried `.env.production.example`'s comment
verbatim, which is what regenerating it from the example looks like.

Because the file is untracked, **git shows nothing when this happens**, and the
loss is invisible in a diff and in review. Two habits catch it:

- Diff the live values against the example before a build:
  `comm -13 <(grep -oE '^VITE_[A-Z0-9_]+' apps/web/.env.production | sort) <(grep -oE '^VITE_[A-Z0-9_]+' apps/web/.env.production.example | sort)`
  finds keys the example has and your file does not. Keys present in both but
  *blank* in yours are the dangerous case — read those by eye.
- Grep the finished bundle for what must be in it. A feature switched on by a
  `VITE_*` value leaves that value in `dist/assets/`; if it is not there, the
  build shipped the feature off.

### The build now renders the public pages, and needs an API to do it

`npm run build` runs `scripts/prerender.mjs` after `vite build`. It serves the
fresh `dist` with `vite preview`, loads the public pages in headless Chromium,
and writes the rendered DOM back over the HTML entries — `/`, `/landing`,
`/about`, `/seller/signup` and `/rider`. Without it those files are empty
shells: a crawler that does not run JavaScript sees no content and no links on
any page of the site. It also writes `dist/sitemap.xml`, with a `/shop/<slug>`
entry per shop read from `/api/stores`.

This makes the build depend on a reachable API holding the tenant baked into
the bundle:

```bash
PRERENDER_API_BASE=https://omaykan.com npm run build --workspace web
```

It defaults to `http://127.0.0.1:8000`. **The prerender refuses to write a page
that rendered an empty shelf.** It has to: `landing/main.ts` installs a demo
repository and `loadStorefrontCatalog` swallows a failed catalog request, so a
storefront pointed at a tenant the API does not have renders a complete,
convincing page with no real products in it — and prerendering would bake that
into a file and ship it. A build that stops with

```
rendered 0 products (need 10) from an otherwise complete page
```

is that check working; the tenant in `.env.production` does not match a shop the
API knows. `npm run build:no-prerender` ships the shells if a release must go
out without it.

The prerendered listing is a snapshot taken at build time and goes stale until
the next deploy. That is the trade: it is what puts the shelves in front of a
crawler. Live visitors see current data the moment Vue mounts over it.


After any frontend deploy, confirm the call actually resolves:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://omaykan.com/api/storefront/catalog?orgSlug=sm&storeCode=main"    # expect 200
```

A 422 there means the build is wrong, not the server.

---

## 5. Production schema no longer matches `main`

**This is the most important thing on this page.**

Until 2026-08-27, production ran a hand-assembled hybrid: the operator-portal
work from `origin/docs/commission-free-positioning-and-reverb` (never merged),
plus one migration from `main`. It matched no git ref.

The cutover moved the code to `main` and reconciled the ledger — but not the
schema. Production still carries, and `main`'s migrations do **not** create:

| Left in place | Why |
|---|---|
| `platform_audit_logs` (12 rows) | branch feature; `main` has no code reading it |
| `orders.payment_confirmed_by_role` | branch feature; holds real data |
| `platform_admins.role`, `.status`, `.last_seen_at`, `.remember_token` | branch's column set |

And one column was added by hand because `main`'s code requires it:

```sql
ALTER TABLE platform_admins ADD COLUMN disabled_at timestamp(0) NULL;
```

The ledger row `2026_08_26_000300_create_platform_admins_table` was renamed to
`2026_08_27_000000_create_platform_admins_table` so `main`'s migration is
recorded as applied rather than trying to recreate an existing table. The row
for `2026_08_26_000400_add_payment_confirmed_by_role_to_orders` still points at
a file that no longer exists — left deliberately, because the column it
describes is still there.

**Updated 2026-09-12.** Four more migrations applied cleanly with the operator
portal: `platform_settings`, `rider_ratings`, the two `conversations` tables,
and five nullable columns on `riders`. All additive; none of them touches
`platform_admins` or `orders`, so **everything below is still true** — the
drift is untouched, only re-confirmed for the fourth time.

One observation that makes the drift concrete rather than theoretical.
`platform-admin:create --disable` writes `disabled_at` and leaves
`platform_admins.status` alone, so after disabling an operator the table reads
`status = 'active'` for an account that cannot sign in. `status` is the
branch's column, which `main` has no code for; `disabled_at` is the one that
was added by hand because `main` requires it. **Check `disabled_at`, not
`status`** — reading the wrong one says the opposite of the truth.

**Updated 2026-09-08.** That deploy found production two releases behind, not
one: the whole customer Google sign-in commit had never gone out, so seven
migrations were pending rather than the expected five. They applied cleanly and
production now carries every migration in `main` — but the drift described
above is untouched. `platform_audit_logs`, `orders.payment_confirmed_by_role`
and the extra `platform_admins` columns are all still there, and the two ledger
rows still point at files that do not exist. Nothing in this section has been
fixed; it has only been re-confirmed.

**Consequences:**

- `migrate:rollback` is unsafe. Two recorded migrations have no file.
- A fresh `migrate` on an empty database produces a **different** schema than
  production. Do not assume a local database matches.
- Before writing any migration touching `platform_admins` or `orders`, check the
  live schema, not the migration files.

The clean fix is one reconciliation migration that drops the orphaned columns
and table, or ports their features forward. Until then this section is the only
thing standing between the next developer and a confusing failure.

---

## 6. Known operational issues

- **`sites-enabled` holds two stray config copies.** `omaykan.bak-` and
  `omaykan.bak-20260827-051258` are regular files, not symlinks, so nginx loads
  them: `nginx -t` warns about conflicting `omaykan.com` server names on three
  lines. Harmless today because duplicates are ignored, but an edit to the wrong
  copy will be baffling. They belong outside `sites-enabled/`.
- **No monitoring.** Nothing alerts if `omaykan-queue` or `omaykan-reverb` stops.
  `Restart=always` covers a crash, not a boot loop or a wedged worker.
- **No automated backups.** Every dump in `/root/db-backups` was taken by hand
  before a deploy. There is no schedule and nothing is copied off the box.
- **Backups live on the same disk as the thing they back up.**
- **No staging.** Every deploy has been straight to production.
- ~~Local SQLite locks under a running queue worker.~~ **Resolved 2026-08-27**:
  local development moved to PostgreSQL 16.15, the same major the VPS runs, so
  there is no longer a dev/prod database split. The lock described in
  [e2e-findings.md §3.3](./e2e-findings.md) cannot recur; the ten-run suite was
  replayed on PostgreSQL with identical results and no flake.

---

## 6a. Shop subdomains — `<slug>.omaykan.com`

**Live since 2026-09-16, 15:05 UTC.** Every shop's shareable page is its own
subdomain. The wildcard `*` A record points at this box, and the certificate,
server block and `shop.html` build below are all in place. An unknown slug
answers 200 with the page's own "We couldn't find that shop"; `/api/` works
on every subdomain, and every other path 302s to the main site. Pre-edit nginx: `/root/nginx-backup-20260916-143527-shop-subdomains.tgz`;
to undo, remove the `sites-enabled/omaykan-shops` symlink and reload.

**How it works.** A subdomain serves `shop.html` at `/`, which reads the shop
from the host — through Laravel since §6a.1, so that the file carries the shop's
own name and photo for the crawlers that never run it. It serves nothing else of the site: every other page, the cart
included, redirects to `omaykan.com`, because sign-in, Google sign-in (Google
refuses wildcard origins) and checkout live there. Checkout carries the basket
across in the link (`/cart?basket=…`, see `apps/web/src/commerce/basketHandoff.ts`).
The build needs `VITE_SHOP_ROOT_DOMAIN=omaykan.com` in `.env.production`.
Signup never gives out a slug that cannot be a subdomain (reserved names such as
`app` and `api`, or longer than 63 characters); see `App\Services\ShopSubdomain`,
which is also what reads a slug back off a request's hostname.

**1. Certificate.** A wildcard needs a DNS-01 challenge, so it cannot be issued
by the `nginx`/`webroot` plugins this box uses today. DNS is on Hostinger
(`dns-parking.com` nameservers). A cert issued with `--manual` does not renew on
its own; use a DNS plugin with an API token so renewal stays automatic.

```bash
certbot certonly --cert-name omaykan-shops -d 'omaykan.com' -d '*.omaykan.com' \
  --preferred-challenges dns <DNS plugin flags>
```

**2. nginx.** A second `server` block beside the existing one. The exact names
in the existing block (`omaykan.com`, `www.omaykan.com`) take precedence over
the regex, so the main site is unaffected.

```nginx
server {
    listen 443 ssl; listen [::]:443 ssl;
    server_name "~^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.omaykan\.com$";
    ssl_certificate     /etc/letsencrypt/live/omaykan-shops/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/omaykan-shops/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;

    root /var/www/omaykan/web;

    # The shop's page, and the public API it reads the shop and menu from.
    location = /            { try_files /shop.html =404; }
    location ^~ /api/       { root /var/www/omaykan/backend/public; try_files $uri /index.php$is_args$args; }
    location ~ ^/index\.php(/|$) {
        root /var/www/omaykan/backend/public;
        include snippets/fastcgi-php.conf;
        fastcgi_pass unix:/run/php/php8.3-fpm.sock;
        fastcgi_param SCRIPT_FILENAME $realpath_root$fastcgi_script_name;
        fastcgi_param DOCUMENT_ROOT $realpath_root;
        internal;
    }
    location ^~ /assets/    { expires 1y; add_header Cache-Control "public, immutable"; try_files $uri =404; }

    # Other pages' entry files are the main site's business.
    location ~ \.html$      { return 302 https://omaykan.com$request_uri; }
    # Images and icons the page uses are served here; anything else goes home.
    location /              { try_files $uri @main_site; }
    location @main_site     { return 302 https://omaykan.com$request_uri; }
}

server {
    listen 80; listen [::]:80;
    server_name "~^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.omaykan\.com$";
    location ^~ /.well-known/acme-challenge/ { root /var/www/html; }
    location / { return 301 https://$host$request_uri; }
}
```

**Each shop's own checkout — `omaykan.com/shop/<slug>/checkout`.** Checkout is
dressed as the shop it is for (its photo, name, address, and a way back to its
menu) instead of every shop finishing on the same `/cart` page. It is its own
entry, `checkout.html`, and it lives on the **main** host for the reason above:
the shop page's checkout button carries the basket across as
`/shop/<slug>/checkout?basket=…`. `/cart` is still the basket; its "Proceed to
checkout" goes to the shop's checkout, carrying unticked lines as `?skip=`, and
an old `/cart?step=checkout` link forwards there. The main site's server block
needs one line before this frontend goes out, or the path falls through to
`index.html` with a 200 (see §3.1 — read the `<title>`, it should be
"Checkout — Omaykan" before the page loads):

```nginx
location ~ ^/shop/[^/]+/checkout/?$ { try_files /checkout.html =404; }
```

**3. Build** with `VITE_SHOP_ROOT_DOMAIN=omaykan.com` in `.env.production`, and
after any frontend deploy check a shop subdomain's `<title>` names the shop
once the page has loaded — the static HTML's own title is just "Shop".

### 6a.1 Link previews — a shop's card when its address is pasted

**Live since 2026-09-24, 03:20 UTC.** All five shops answer their own address
with their own card; an unknown slug still returns the plain shell at 200, and
`/index.php` on a shop host is a 404 with no source leaked. Pre-change nginx:
`/root/pre-shop-previews-20260924-105401.tgz`, and the server block alone at
`/root/omaykan-shops.before-20260924-105401`. To undo, restore that file and
reload — the release's own `SHOP_ROOT_DOMAIN` can stay, since nothing routes `/`
to Laravel without the nginx half.

A shop subdomain served one static file, identical for every shop: title "Shop
— Omaykan", a generic description, no `og:` tags. The shop's name and photo
only appear once Vue has run and asked `GET /api/stores` which shop this host
is. Every link-preview crawler — facebookexternalhit, Twitterbot, Slackbot,
Discordbot, LinkedInBot, WhatsApp, Viber — reads the HTML and runs nothing, so
pasting a shop's link produced no card, or the same blank card for every shop
on the platform.

`ShopShellController` now answers `/` on a shop host: it reads the slug off the
hostname, looks the shop up under the same conditions `GET /stores` applies, and
stamps that shop's title, description and photo into the same built `shop.html`
before sending it. The page a visitor gets is unchanged — same bundle, and Vue
still resolves the shop itself. **An unknown slug, a suspended shop, or a shop
with an empty shelf gets the file untouched**, exactly what nginx served before,
so nothing here can turn a working storefront into a redirect or a 404.

**1. nginx.** One location in `sites-available/omaykan-shops`, plus a fallback:

```nginx
# Was: location = / { try_files /shop.html =404; }
location = / {
    root /var/www/omaykan/backend/public;
    fastcgi_intercept_errors on;
    error_page 500 502 503 504 = @static_shell;
    try_files /__shop_shell /index.php$is_args$args;
}

# If PHP is down or misconfigured, shops keep working the way they did
# before this section existed: the static file, with a generic preview.
location @static_shell {
    root /var/www/omaykan/web;
    try_files /shop.html =404;
}
```

`/__shop_shell` is a path that never exists, so try_files falls through to its
last argument, which is a URI and therefore an internal redirect into the
`location ~ ^/index\.php(/|$)` block already in that file.

**Do not write `try_files /index.php =404;`.** A try_files argument that is not
the last one and matches a real file is served as *static content* — that hands
out the PHP source of the front controller. The PHP path must be last.

**2. Environment.** Two new settings in `backend/.env` (`config/shops.php`):

```
SHOP_ROOT_DOMAIN=omaykan.com
# WEB_ROOT=/var/www/omaykan/web   # the default is ../web, already correct here
```

`SHOP_ROOT_DOMAIN` blank — the default — means no host is read as a shop and
every request to `/` gets the framework's welcome page, which is what this route
did before. So **set it in the same deploy that changes nginx**, or shop
subdomains serve the Laravel welcome page instead of the storefront. Run
`php8.3 artisan config:clear` after editing.

**3. Order.** Backend first (route + config), then nginx. Between the two, the
new route exists and nothing reaches it.

**4. Check**, with the shop's name in place of the example:

```bash
# What Facebook actually receives. Expect the shop's name in <title> and
# og:title, one <meta name="description">, and the bundle's script tag intact.
curl -sA 'facebookexternalhit/1.1' https://nenas-market-stall.omaykan.com/ \
  | grep -E 'og:|twitter:|<title>|shop-.*\.js'

# The image it will then fetch: 200, an image/* type, sane dimensions.
curl -sI 'https://omaykan.com/api/stores/12/image?v=ab12cd34'

# An unknown shop still gets the page, not an error.
curl -so /dev/null -w '%{http_code}\n' https://nobody-here.omaykan.com/
```

Then the validators, which are the only proof that counts:
[Facebook's debugger](https://developers.facebook.com/tools/debug),
[Twitter/X](https://cards-dev.twitter.com/validator),
[LinkedIn](https://www.linkedin.com/post-inspector/), and a paste into Viber or
Messenger.

**Cards are cached twice over.** Laravel keeps the stamped HTML for six hours
per shop, keyed on the shop's row, the shell's mtime and the controller's own —
so a shop that changes its name or photo refreshes by itself. Facebook is the
slow one: it keeps a scraped page for days, and no header here shortens that. An
owner who renames their shop needs someone to re-scrape the URL in the Sharing
Debugger before the old card goes away.

**What is deliberately not in a card:** whether the shop is open. A paused shop
still gets its preview, because Facebook would go on showing "not taking orders"
for days after it reopened.

---

## 6b. Subscription, suspension and merchant features — first deploy

Built 2026-09-19 ([subscription-and-suspension.md](./subscription-and-suspension.md),
[merchant-features.md](./merchant-features.md)). A normal deploy runs the
eight new migrations (`2026_09_19_000100` … `000800`). These are the steps a
normal deploy does **not** do. Do them once, in this order.

0. **Online-only checkout.** The till is online-only from this
   release: sales go to `POST /api/register/orders`, and `/api/sync/push`
   no longer records them. A sale still queued on a till from before is
   refused as `failed` and stays on that till; afterwards,
   `select store_id, user_id, count(*) from sync_events where entity_type = 'order'
   and failed_at is not null group by 1, 2` shows which tills hold one.
   **Deploy the backend before the web build** — a new till against an old
   backend fails every sale.

1. **Run the migrations.** `000500` also rewrites `products.tax_rate` values
   between 0 and 1 (a fraction the till wrote) into percentages. Nothing to
   choose; the rewrite is logged in the migration.
2. **Trials.** `php artisan billing:backfill-trials --dry-run`, read the
   output, then run it without `--dry-run`. Enforcement stays off
   (`BILLING_ENFORCE` unset) either way.
3. **Product photos.** `php artisan products:extract-inline-images --dry-run`,
   then for real. Moves base64 photos out of product rows into
   `storage/app/private/product-images/`. Include that directory in whatever
   backs up `storage/`.
4. **The scheduler — new, and required.** Nothing ran on a schedule before.
   Artisan runs as `www-data` (§3.2), so the line goes in that user's crontab
   (`crontab -u www-data -e`):

   ```cron
   * * * * * cd /var/www/omaykan/backend && php8.3 artisan schedule:run >> /dev/null 2>&1
   ```

   It runs `products:sweep-images` weekly, and `loyalty:expire` and
   `billing:advance-subscriptions` daily. `php artisan schedule:list` shows all
   three. Without it, orphaned photos accumulate, expired points are never
   written off and no subscription ever lapses — nothing breaks.

   Before letting the billing one run on its own, check it by hand:
   `php artisan billing:advance-subscriptions --dry-run` should report nothing
   to do, because step 2 put every organization inside a trial and no row has a
   billing period yet. Anything else means the backfill did not take.
   Dunning mail is off separately (`BILLING_DUNNING` unset) — see
   subscription-and-suspension.md §6.4.
5. **Seller app build.** Set `OMAYKAN_REVERB_APP_KEY` (the backend's
   `REVERB_APP_KEY`, public) in the environment of the build that produces the
   seller APK. Without it the app still works and polls every 15 seconds, as
   before. Host, port and path default to production's (`omaykan.com`, 443,
   `/reverb`).

Everything new is reachable on the existing nginx configuration; no new host
or path is needed.

---

## 6c. Town and category landing pages — `/baguio/restaurants`

**Live since 2026-09-26, 15:45 UTC** (backend and nginx; the footer links and
the `robots.txt` line are still waiting on a frontend release, see §3.3). One
page per town (`/baguio`, `/la-trinidad`) and per town and category
(`/baguio/restaurants`, `/la-trinidad/strawberries`, …), plus
`/sitemap-towns.xml` listing the ones with shops on them. They are for
customers browsing and for search, so **Laravel renders them as HTML**
(`DiscoveryPageController`, views in `resources/views/discovery/`); they are
not part of the Vue build and do not need the prerender. The towns and
categories, and the rules that place a shop in them, are in
`backend/config/discovery.php`. Nothing is stored: a shop's town is the last
one its address names, or failing that the town whose radius its pin falls in,
and its categories come from its till mode, its business type and its shelf.

A town or category with no shops still answers 200, marked `noindex` and left
out of the sitemap. Any other slug is a 404 from Laravel — but only because
nginx sends the town paths there.

**Its own sitemap, not `/sitemap.xml`.** That one is a static file the
prerender writes at build time. These pages change as shops join, so Laravel
lists them, and `robots.txt` names both.

Shop cards link to `<slug>.omaykan.com` through `ShopSubdomain`, from the
`SHOP_ROOT_DOMAIN` the backend already has. Canonical links and the sitemap
use `APP_URL`; `DISCOVERY_SITE_URL` overrides it and is unset in production.

**nginx**, in the main site's server block, before `location /`. The town list
is the same as `config/discovery.php` `localities` and
`apps/web/src/landing/towns.ts`; adding a town means editing all three.

```nginx
location ~ ^/(baguio|la-trinidad)(/[a-z0-9-]+)?/?$ {
    root /var/www/omaykan/backend/public;
    try_files $uri /index.php$is_args$args;
}
location = /sitemap-towns.xml {
    root /var/www/omaykan/backend/public;
    try_files $uri /index.php$is_args$args;
}
```

**Checking it** — the status code alone proves nothing (§3.1):

```bash
curl -s https://omaykan.com/baguio | grep -o '<title>[^<]*'      # "Shop local in Baguio …"
curl -s https://omaykan.com/baguio/hardware -o /dev/null -w '%{http_code}\n'   # 404
curl -sI https://omaykan.com/baguio/restaurants | grep -i 'set-cookie'          # nothing
curl -s https://omaykan.com/sitemap-towns.xml | head
```

---

## 7. Related

- [feature-audit.md](./feature-audit.md) — what is missing and unfinished in the product
- [e2e-findings.md](./e2e-findings.md) — a ten-run pass of the whole order chain, and what it found
- [plan.md](./plan.md) — stale in places; see feature-audit §6
