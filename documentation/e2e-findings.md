# End-to-end test run — 2026-08-27

Ten runs of the whole chain: **seller lists a product → customer orders → rider
delivers → order completes.** Driven through the same HTTP API the apps use,
nothing stubbed. Harness: [`scripts/e2e-order-flow.mjs`](../scripts/e2e-order-flow.mjs).

Run against a **local** instance on a freshly seeded database. Deliberately not
production: ten fake orders, two fake riders and three fake products in a live
merchant's books would be real damage, and the orders table is what they get
paid on.

---

## 1. Result

**7 of 10 correct outcomes. 3 failures, all one root cause.**

| # | Scenario | Outcome |
|---|---|---|
| 1 | POS-listed product, delivery | **FAIL** — order refused, "not available" |
| 2 | POS-listed product, pickup | **FAIL** — same |
| 3 | POS-listed product, delivery without a customer pin | **FAIL** — same |
| 4 | Seeded product, delivery ~0.5 km | PASS — ₱183.40, fee ₱49.00 |
| 5 | Seeded product, delivery ~10 km | PASS — ₱452.80, fee ₱184.00 |
| 6 | Seeded product, delivery with no customer pin | PASS — ₱183.40, flat ₱49.00 |
| 7 | Seeded product, pickup, qty 3 | PASS — ₱403.20, no fee |
| 8 | Seeded product, delivery qty 5 | PASS — ₱721.00 |
| 9 | Seeded product, second rider | PASS — ₱183.40 |
| 10 | Seeded product, ~35 km away | PASS\* — **correctly refused**, outside the delivery area |

\* Expected refusal. The 15 km service-area guard fired exactly as it should.
This only works because the store has a pin; see [feature-audit §3.4](./feature-audit.md).

Final database state — every completed order reached `served` **and** `paid`,
and every delivery reached `delivered` with a named rider:

```
ticket    status   pay   fulfil    stage      rider           total
8AFF589E  served   paid  delivery  delivered  Test Rider 1    183.40
773C26C8  served   paid  delivery  delivered  Test Rider 1    452.80
2F072CAE  served   paid  delivery  delivered  Test Rider 2    183.40
AF8D7D71  served   paid  pickup    -          -               403.20
FE8280F0  served   paid  delivery  delivered  Test Rider 2    721.00
BFE356E2  served   paid  delivery  delivered  Test Rider 2    183.40
```

---

## 2. The defect: a product a merchant lists in their own POS cannot be sold online

**Severity: high.** It breaks the product's core loop for every new item.

Scenarios 1–3 all died the same way:

```
storefront listed it: false
order refused:        'E2E Product 1' is not available.
```

Confirmed in the data afterwards: **0 of 3** products created through the POS
had a business mode.

### Root cause

`business_modes` is what makes a product sellable, and the POS sync has no way
to set it.

- [`SyncController::applyProductEvent`](../backend/app/Http/Controllers/Api/SyncController.php#L346)
  writes `sku`, `name`, `product_type`, `tax_rate`, `price_cents`,
  `track_inventory`, `is_active` and stock — and **never `business_modes`**. It
  is not in the payload contract, so the till cannot send it even if it wanted to.
- [`StorefrontCatalogController`](../backend/app/Http/Controllers/Api/StorefrontCatalogController.php#L53)
  filters `whereJsonContains('business_modes', $businessMode)`, so the product
  never appears on the storefront.
- [`OnlineOrderController`](../backend/app/Http/Controllers/Api/OnlineOrderController.php#L354)
  independently re-checks the same field and rejects the line with *"'X' is not
  available."*

Both gates are correct in isolation. The gap is that the only writer that
populates the field is the **seeder**. Every product on omaykan.com today was
imported, not created in the POS — which is why this has never been hit in
production.

### Why nobody noticed

The failure is invisible from the merchant's side. The product saves fine, syncs
fine, and shows in the POS product list. It is simply absent from the storefront,
with no warning anywhere that it will be. A merchant would reasonably conclude
the storefront is broken, not that their product is missing one field.

### Fix options

1. **Default it at write time** — in `applyProductEvent`, fall back to the
   store's `business_mode` when the payload omits it. One line, fixes every
   existing and future product, and matches what a single-mode merchant means.
2. **Add it to the sync contract** and have the POS send it — correct for a
   multi-mode organization, but needs client work and leaves old rows broken.
3. Both: (1) as the default, (2) for merchants who genuinely run several modes.

(1) is the one to ship first. It is also worth a backfill for any product
already created this way.

---

## 3. Lapses in how this test was run

Recorded because these cost time and will cost it again.

### 3.1 I assumed the API shape instead of reading it

The first run failed all ten scenarios on
`"The organization id field is required"`. My harness read
`dev.data.organizationId`; the endpoint returns
`dev.data.organization.id`. **A whole run wasted on a field name I guessed
rather than checked.**

*Improvement:* probe one endpoint with `curl` and read the response before
writing anything that consumes it. Ten identical failures is the signature of a
harness bug, not ten app bugs — treat a 100% failure rate as suspect first.

### 3.2 Rate limits are strict and I kept tripping them

Repeated runs hit `429 Too Many Attempts` on three separate routes:

| Route | Limit |
|---|---|
| `POST /api/rider/register` | 4/min |
| `POST /api/platform-admin/login` | 5/min |
| `POST /api/rider/login` | 10/min |
| `POST /api/online-orders` | 20/min |

One of these cascaded confusingly: the rider-register throttle meant rider B
never got a token, so two *later* scenarios failed with `"Unauthenticated"` at
the rider board — a symptom three steps from its cause.

The limits are correct and should stay. The lapse is that there is no
test-friendly way around them, so the harness now calls `php artisan
cache:clear` between phases.

*Improvement:* consider raising throttles when `APP_ENV=local`, or a documented
way to reset the limiter. Also: when a step fails with `Unauthenticated`, check
whether *setup* failed before assuming the step did.

### 3.3 SQLite locked under the queue worker — RESOLVED

One run failed scenario 7 with `SQLSTATE[HY000]: General error: 5 database is
locked` while inserting an order. SQLite allows a single writer, and the queue
worker holds it briefly after each broadcast.

Local-only — production is PostgreSQL and does not have this constraint. But it
makes local runs flaky and could easily be misread as an application bug. The
harness now retries a locked write up to three times.

*Improvement:* **done.** Local development moved to PostgreSQL 16.15 the same
day — the same major the VPS runs — so the dev/prod database split is gone and
this lock cannot recur. The whole ten-run suite was replayed on PostgreSQL:
**identical results, 7 of 10, no flake.** That replay also confirms the §2
defect is a genuine application bug rather than a SQLite artifact, which is
worth more than the lock fix itself.

### 3.4 I wrote a test that asserted the wrong thing

Scenario 10 sends a delivery 35 km away. I first scored its rejection as a
**failure**, when refusing it is the whole point of the service-area guard. It
took a second look to notice the suite was reporting correct behaviour as a bug.

*Improvement:* a test for a guard has to assert the refusal, not the success.
Encoded now as `expectRefusal`, reported as `PASS*`.

### 3.5 A fragile shortcut in the harness

To work around §2 I initially shelled out to `php artisan tinker --execute="..."`
to set `business_modes` directly. It broke on Windows quoting and crashed the
run mid-suite. Replaced by using the seeded product for the downstream
scenarios — which is also more honest, since it exercises the only supported
path a product gets a business mode.

*Improvement:* an end-to-end test should not reach past the API to patch state.
If it has to, that is itself the finding.

---

## 4. What held up

Worth recording, because it is the part that works.

- **Pricing is exact.** Every total checked out by hand: ₱120 × 1 + 12% tax
  (₱14.40) + ₱49 delivery = ₱183.40. Qty 3 pickup: ₱360 + ₱43.20 = ₱403.20.
  Qty 5: ₱600 + ₱72 + ₱49 = ₱721.00.
- **Distance pricing bands correctly** — ₱49 at 0.5 km, ₱184 at ~10 km, which is
  ₱49 + 9 × ₱15.
- **The service-area cap works** now the store has a pin (scenario 10).
- **The rider lifecycle is sound** end to end: register with documents →
  operator approval → login → see the board → accept → picked_up → delivered.
  Stage transitions are properly ordered, and a rider cannot touch another
  rider's delivery.
- **Two riders worked concurrently** without collisions.
- **Rate limiting genuinely works.** Annoying in a test; correct in production.
- **Rider document upload validates real images**, not just extensions.

---

## 5. Recommended next steps

1. Default `business_modes` from the store in `applyProductEvent`, and backfill
   any product already created through the POS. (§2)
2. Add a regression test that lists a product through `/api/sync/push` and
   asserts it appears in the storefront catalog — the gap this run found had no
   test covering it.
3. Consider relaxing throttles under `APP_ENV=local`. (§3.2)
4. Note the local SQLite + `queue:work` locking behaviour in deployment.md. (§3.3)
