# Positioning — Commission-Free Local Commerce

> What this product is, who it is for, and the rules that make it different.
> Read this before [plan.md](./plan.md). The build plan only makes sense downstream of this.
> Last updated: August 23, 2026.

---

## 1. The problem we are attacking

Delivery aggregators sit between a merchant and a customer who already know each other, and take from every side of the transaction:

- **The merchant** pays roughly 20–30% commission on every order — including on regulars who would have bought anyway.
- **The rider** absorbs fuel, maintenance, phone data, and long unpaid waiting time, on per-drop rates that get quietly revised downward, with no benefits and no leverage.
- **The customer** pays a menu that has been inflated to survive the commission, plus service fees, delivery fees, and small-order fees on top. A ₱30 Coke gets listed at ₱65 before fees.

The merchant raises prices to survive the cut. The customer pays the raised price. The rider carries the bag for a shrinking share. The value extracted is far larger than the value provided.

**We are not building a point-of-sale product. We are building the merchant's own sales channel** — the one they should have had before the aggregators arrived — with the POS as the operational engine behind it.

---

## 2. What we are and are not

**We are:** a commission-free ordering channel for local merchants — the merchant's own storefront, their own link, their own customers, their own prices — backed by a POS that keeps catalog, inventory, and orders in one place.

**We are not:**

- **A marketplace.** We do not aggregate merchants into a directory and compete for consumer attention. That is the incumbents' game, played with their budget.
- **A delivery network.** We do not employ or dispatch riders at scale. See §7.
- **A commission business.** We never take a percentage of an order. Ever. See §4.
- **A payment processor.** Customers pay cash at handover; money never passes through us. See [plan.md §4a](./plan.md).

The distinction in the first bullet matters most: we do not supply demand. The merchant brings their own customers; we make that channel work and stop the bleeding. Any plan that quietly assumes we will generate orders for the merchant is wrong and will fail.

---

## 3. The three sides

| Side | What they get | What they give |
|---|---|---|
| **Merchant** | 0% commission, their own storefront link, their customer list, one system for walk-in + online, real sales reporting | A flat monthly subscription — our only revenue |
| **Rider** | 100% of the delivery fee, transparent per-drop earnings, no algorithmic rate cuts | Nothing. We take no cut from riders |
| **Customer** | In-store prices — no inflated menu — plus honest total-cost display, and they pay on handover | Nothing. No consumer fees beyond the stated delivery fee |

Every side has to be visibly better off, and each side must be able to *verify* it: the rider sees exactly what they earned per drop, the merchant sees what they kept, the customer sees the in-store price.

---

## 4. The price-parity covenant (non-negotiable)

**The price listed on the storefront must equal the in-store price.**

This is the entire differentiation. The moment a customer finds a marked-up item on our platform, the story collapses and we are just a worse aggregator. So it is a platform rule, not an aspiration:

- Storefront prices and register prices come from **one product record**. Divergence should be structurally impossible, not merely discouraged.
- Items display an **"in-store price"** badge.
- For basic necessities and prime commodities, DTI publishes SRP bulletins — where a published SRP exists, it is an external reference we can point at. *(Verify the current bulletin and how often it is updated.)*
- Violations are an existential issue, not a support ticket. Define an enforcement path before the first merchant is onboarded.

Corollary: **we never take a percentage.** A commission would force merchants to inflate prices to absorb it, which would recreate exactly the thing we are replacing. The flat subscription is not just a pricing preference; it is what makes parity survivable for the merchant.

---

## 5. Business model

Flat monthly subscription per merchant, unlimited orders. No commission, no per-order fee, no consumer fee.

The sales pitch is one arithmetic line:

> *"₱X per month, versus the ₱28,000 in commissions you paid last month."*

Build that comparison into two places:
1. **The landing page** — an ROI calculator: monthly order volume × average ticket × their current commission rate.
2. **The merchant dashboard** — a live *"you saved ₱X this month"* counter. This is the single most important retention surface in the product. The month a merchant cannot see that number is the month they cancel.

Consider a free tier below a monthly order threshold to remove the signup objection.

**Open:** exact price point — ₱499/month is currently a placeholder with no cost basis. It must cover infrastructure, support time, and founder time at realistic merchant counts. A fair platform that goes broke helps nobody. See §9.

---

## 6. Beachhead — convenience and grocery first

**Target first: sari-sari stores, mini-groceries, convenience shops, water stations, pharmacies.**

The reasoning is the Coke test. Nobody knows what a shop's adobo *should* cost, so an inflated cooked dish reads as normal. But everyone knows what a Coke, a Lucky Me, a shampoo sachet, or a bottle of water costs. **On branded, standardized goods the markup is visible and indefensible on sight** — the pitch needs no explanation, just two prices side by side.

This also matches what is already built: the POS ships a grocery business mode with barcode-centric flow and weighted items.

Secondary targets, in order: **home-based food sellers and cloud kitchens** (already running on Messenger, no aggregator dependency to break, acute order chaos), then small F&B with an existing regular customer base.

Deliberately *not* first: established aggregator-dependent restaurants. Hardest sell, highest switching risk, and the most likely to blame us for lost volume.

### Geographic density beats spread

Ten merchants in one barangay is worth more than a hundred scattered across Luzon. Density is what makes word-of-mouth work, what makes rider batching viable (§7), and what makes any future local discovery feature possible. **Pick one city — then one barangay inside it — and saturate.**

---

## 7. Delivery — sequenced, not built

We do not build a rider network. But the incumbents' rider networks are rented, not loyal: riders already multi-app, and a rider who keeps 100% of the fee needs no persuasion — only enough volume to justify sitting in our queue.

**Density, not recruitment, is the constraint.** A rider doing three drops a day at a good rate still earns less than one doing twenty at a bad rate. Fairness does not survive low volume.

So, in strict order:

1. **Pickup first.** The price saving alone justifies the walk. Zero logistics required. Already built.
2. **Merchant-affiliated riders.** Many shops already have one person. Register them, merchant sets the fee, rider is paid directly, we take nothing. Works on day one at any volume.
3. **Barangay-level rider pool.** Only once enough merchants sit in one radius that a rider can stack orders.
4. **Scheduled and batched routes** before on-demand dispatch. Five drops on one route pays real money; idling for on-demand pings on a thin network does not.

Deliberately not building: real-time dispatch optimization, rider insurance products, a national network. Those are a different company with different funding.

### Basket-size constraint

Convenience goods mean small baskets. A ₱150 order carrying a ₱60 delivery fee is bad math for the customer no matter how fairly it is split — which is exactly why nobody has solved sari-sari delivery. Mitigations: lead with pickup, set minimum order values for delivery, batch per barangay, and **always display the honest total** (parity item prices + a plainly stated delivery fee that goes entirely to the rider). Even with the fee the comparison wins; we just show it as a total instead of hiding it.

---

## 8. Growth and retention

Since we do not supply demand, the product must help the merchant activate their own. Priority order:

1. **Shareable storefront link.** A URL, not a code — see [plan.md](./plan.md) Phase 1. Goes in the Facebook page bio, the Messenger auto-reply, a QR sticker on the counter and on packaging, and the printed receipt.
2. **Price-comparison view.** Our price vs. the aggregator's, same item, same shop. Screenshot-able, and it spreads itself through Facebook groups. This is marketing we do not pay for.
3. **The customer list the merchant owns.** Aggregators hide this from them and it is a genuine, frequently voiced grievance.
4. **Loyalty / points.** The single best tool for pulling regulars off aggregators — "10th order free" changes where someone chooses to order.
5. **Discount codes and repeat-order nudges** over Messenger/SMS.

### The message to lead with

Not *"leave Grab."* That asks a merchant to risk their business on day one, and they will say no.

> **"Keep Grab for discovery. Move your suki to your own link. You keep 100% of those."**

New customers are genuinely expensive to acquire and the aggregators earn something there. Regulars would have ordered anyway — that is pure bleed. Land on the regulars, and the merchant's own channel grows on its own.

For customers, lead with self-interest, not sympathy: *"you are paying double for a Coke."* People switch to save money and then discover they are also not squeezing anybody. That order matters — "support your local merchant" is a weak ask.

### Distribution

Merchant-side and rider-side Facebook communities are large, vocal, and deeply resentful of the incumbents. The three-line promise — **0% merchant commission, 100% of the delivery fee to the rider, in-store prices for the customer** — is clean, verifiable, and repeated for free by both sides.

---

## 9. Risks and open items

Ordered by how likely each is to kill the business.

| Risk | Why it matters | Status |
|---|---|---|
| **Demand generation** | We do not supply customers. If a merchant's own channel does not grow, they churn regardless of how fair we are. §8 is the entire mitigation. | Unresolved by design — mitigate, cannot eliminate |
| **Self-hosted operations** | Moving to a VPS (Laravel + PostgreSQL + Reverb) removes the Firestore cost meter and its free-tier ceiling, and makes infra a fixed, priceable monthly bill. The cost is that uptime, backups, TLS, and daemon supervision become ours — a till that stops at 7am is now our phone ringing. | **Blocking for Phase 0.** Migration + ops runbook required before charging anyone |
| **BIR compliance** | POS machines issuing official receipts generally require BIR registration / Permit to Use, with accreditation requirements on the vendor and e-invoicing rules on sales transmission. Any merchant who issues ORs will ask on the first call. | **Verify directly with BIR.** Either a wall or a moat — it decides which merchants we can sell to |
| **COD-only exposure** | Customers pay at handover; we never touch the money. That avoids gateway fees, chargebacks, and float — but exposes merchants to fake and no-show orders, and puts cash reconciliation on the rider. | Deliberate. Mitigations owed in [plan.md §4a](./plan.md) |
| **Collecting from merchants** | COD covers customer orders, but the ₱499/month subscription is collected by manual GCash transfer to a placeholder number. That does not scale past a handful of merchants. | Unsolved — the one place a gateway is still needed |
| **Support load** | Offline POS + thermal printers + Bluetooth pairing generates phone calls. A solo operator saturates somewhere around 20–40 merchants. This caps growth harder than sales does. | Plan for it before it arrives |
| **Price-parity violations** | If merchants inflate on our platform too, the differentiation evaporates overnight. | Enforcement path undefined — see §4 |
| **Unit economics** | Revenue is only the merchant subscription. It must cover infra + gateway fees + support + founder time. | Price point not set — see §5 |

### Also open

- ~~**Product name.** "ColePOS" describes the least important part of what this is.~~ Resolved — renamed to **Omaykan** on 2026-08-24, dropping the city so the name doesn't cap the product at one market.
- **Multi-branch tenancy.** The single-store-per-org gap should close before selling to any owner with two locations — multi-branch owners are the ones most able to pay.
- **Table-stakes POS gaps** that merchants treat as non-negotiable: shift open/close with cash reconciliation, X/Z reports, voids and refunds. "How do I know my cashier isn't stealing?" is usually the first question asked, and it matters to them more than anything on the storefront.
