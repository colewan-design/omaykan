# Analytics Plan

> Two tracks: business/sales reporting (shop owner value) and product/app telemetry (developer visibility).
> Both ride the same offline-first outbox pattern already in the architecture (`sync_outbox` from plan.md §6) —
> events are written locally first and flushed when online, exactly like a sale is.

---

## 1. Track 1 — Business/sales reporting

Already covered by the existing data model: `orders`, `order_items`, `payments`, `shifts`, `cash_movements`.

### Schema additions needed

| Table / column | Purpose |
|---|---|
| `voids` / `refunds` (id, order_id, item_id?, reason, amount, voided_by, created_at) | Nothing currently tracks voids or refunds |
| `discounts` table, or `discount_cents` on `order_items` | Promo/markdown reporting |
| `cost_cents` on `products` | Without it you get revenue, not margin |
| `low_stock_events` (product_id, qty_on_hand, threshold, triggered_at) | Feeds a reorder report |

### Reports to build

| Report | Why |
|---|---|
| X report (mid-shift snapshot) / Z report (shift close) | Already in roadmap §7 — cash reconciliation is the most-checked number in any POS |
| Sales by category/product, top & bottom sellers | Drives reordering and menu/catalog decisions |
| Payment method mix (cash/card/e-wallet split) | Cash flow planning |
| Hourly/daily sales heatmap | Staffing decisions |
| Average order value, items per order | Upsell effectiveness |
| Tax collected (per `calculateTax` in `packages/shared/src/index.ts`) | BIR filing in PH |
| Cash variance (expected vs counted at shift close) | Shrinkage/theft signal |
| Void/refund rate by cashier | Fraud/training signal |

### Where it lives

- On-device SQL views for instant X/Z reports — must work with zero connectivity.
- Laravel back office aggregates across devices/stores once synced — the "online half of the sync story" named in plan.md §10.

---

## 2. Track 2 — Product/app telemetry

Nothing in the current architecture captures this yet.

| Signal | Tool/approach | Why |
|---|---|---|
| Crashes/errors | Sentry (self-hostable, works across Capacitor/web) | One SDK, both platforms; offline-queues and retries automatically |
| Feature usage (payment method choice, search usage, catalog size in practice) | Self-hosted PostHog | Matches the "avoid 3rd-party dependency" philosophy in plan.md; one VPS, no per-event billing surprise |
| Sync health (outbox depth, last-synced time, failure rate) | Custom table + Laravel endpoint | plan.md §5 already calls for sync status to be visible — this is the backend half of that |
| Performance (cold start time, DB query latency, time-to-first-paint) | Lightweight custom timers → same outbox | Matters most on the cheap Android tablets targeted in plan.md §1 |
| License/activation events (activate, renew, grace-period entered, churn) | Custom, tied to licensing table | Feeds the monetization model in plan.md §9 |

### Schema addition

An `app_events` table mirroring `sync_outbox`'s shape:

```
app_events (id, event_type, payload_json, device_id, app_version, created_at, sent_at)
```

Same push-when-online discipline, kept separate from sales data so a "local-only" shop can opt out of telemetry without losing sales sync, or vice versa.

---

## 3. Sequencing against the existing roadmap (plan.md §13)

1. **Wk 1–2 (core engine):** instrument order/cart events as they're built — cheapest time to add tracking is while writing the code that produces the data.
2. **Wk 3 (receipts/shifts):** ship X/Z reports — first real analytics output.
3. **Wk 5 (sync tier 1):** stand up the `app_events` outbox alongside `sync_outbox`; wire Sentry.
4. **Wk 7–8 (website/licensing):** activation/churn analytics, once the licensing flow exists to instrument.

---

## 4. Privacy note

Targeting PH shops means telemetry collection should respect the Philippine Data Privacy Act:

- Keep a visible toggle — can piggyback on the existing local-only/online-sync setting, or be separate.
- Never bundle customer-identifying data into `app_events`.
