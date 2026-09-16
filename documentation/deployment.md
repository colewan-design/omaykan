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

**The build bakes in the environment.** See §4 — this is the step that has
already broken production once.

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
  (`store-images/`, written by `StoreImageController`). Skipping this step
  leaves every store record pointing at a file that is no longer there, and the
  shop directory silently falls back to a product photo.
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

**Updated 2026-09-16, 15:05 UTC.** Frontend and backend: shop subdomains
(`ccf0abb`). No migration — production already had all 30. nginx gained the
`omaykan-shops` site and lost the `baguioonlinemarket.salidumay.com` alias
earlier the same day (§1, §6a); neither is undone by a directory swap.

| | Roll back to | Holds |
|---|---|---|
| Frontend | `web.bak-20260916-230147-shop-subdomains` | the 2026-09-13 21:26 build, no `shop.html` |
| Backend | `backend.bak-20260916-150348-shop-subdomains` | the 2026-09-13 09:26 release, no slug rules |
| Database | `/root/db-backups/omaykan-predeploy-20260916-150348-shop-subdomains.sql.gz` | taken before the swap; nothing changed it |

(The frontend label uses the deploying laptop's clock, UTC+8; the backend's
uses the server's, UTC. They are the same deploy.)

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
from the host. It serves nothing else of the site: every other page, the cart
included, redirects to `omaykan.com`, because sign-in, Google sign-in (Google
refuses wildcard origins) and checkout live there. Checkout carries the basket
across in the link (`/cart?basket=…`, see `apps/web/src/commerce/basketHandoff.ts`).
The build needs `VITE_SHOP_ROOT_DOMAIN=omaykan.com` in `.env.production`.
Signup never gives out a slug that cannot be a subdomain (reserved names such as
`app` and `api`, or longer than 63 characters); see `SignupController::RESERVED_SLUGS`.

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

**3. Build** with `VITE_SHOP_ROOT_DOMAIN=omaykan.com` in `.env.production`, and
after any frontend deploy check a shop subdomain's `<title>` names the shop
once the page has loaded — the static HTML's own title is just "Shop".

---

## 7. Related

- [feature-audit.md](./feature-audit.md) — what is missing and unfinished in the product
- [e2e-findings.md](./e2e-findings.md) — a ten-run pass of the whole order chain, and what it found
- [plan.md](./plan.md) — stale in places; see feature-audit §6
