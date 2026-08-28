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
| Serves | `omaykan.com`, `www.omaykan.com`, `baguioonlinemarket.salidumay.com` |
| OS | Ubuntu 24.04, nginx 1.24, PHP 8.3-FPM, PostgreSQL 16, Redis |
| Static build | `/var/www/omaykan/web` |
| Laravel app | `/var/www/omaykan/backend` (front controller `public/index.php`) |
| Database | PostgreSQL, database `omaykan`, user `omaykan`, local socket only |
| nginx site | `/etc/nginx/sites-available/omaykan` |

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
ssh xponent          # ~/.ssh/config alias → root@187.124.138.58
```

**The alias is `xponent`, not `omaykan`** — this page said `omaykan` until
2026-08-28, and there is no such `Host` block in `~/.ssh/config`, so the
command as written could only ever have failed. The box is shared with
xponent-global, and Omaykan is a tenant on someone else's alias.

The identity that alias uses is `~/.ssh/xponent-global-deploy`. There are also
`omaykan-deploy` and `omaykan_vps` keypairs in the same directory (this page
previously named a third, `omaykan_vps_ed25519`, which does not exist); nothing
in the deploy path reaches for them.

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

### 3.2 Backend

```bash
git archive --format=tar main backend | ssh omaykan "tar xf - -C /root/staging"
ssh omaykan "cd /root/staging/backend &&
  COMPOSER_ALLOW_SUPERUSER=1 composer install --no-dev --optimize-autoloader"
```

Then, on the server, before swapping:

- copy the live `.env` into the release — **never overwrite the server's `.env`**
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

Directory swaps are reversible. The current rollback point is
`backend.bak-20260828-113306-store-image` and
`web.bak-20260828-113306-store-image`. Swap them back and restart the two
daemons. A database dump taken immediately before that deploy is at
`/root/db-backups/omaykan-predeploy-20260828-113306.dump` (`pg_restore` format).

Rolling that one back also means dropping `stores.image_path`, which the
release before it does not know about. An extra nullable column is harmless to
older code, so prefer leaving it.

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

## 7. Related

- [feature-audit.md](./feature-audit.md) — what is missing and unfinished in the product
- [e2e-findings.md](./e2e-findings.md) — a ten-run pass of the whole order chain, and what it found
- [plan.md](./plan.md) — stale in places; see feature-audit §6
