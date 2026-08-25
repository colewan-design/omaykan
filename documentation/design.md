# POS Design System — Apple-Style, Minimal & Modern

> Visual language for the offline-first POS (Vue 3 · Capacitor · PWA).
> Direction: Apple HIG-derived. Clarity over decoration, content over chrome, precision in every detail.
> All values ship as CSS custom properties so the same tokens drive mobile and web at every screen size.

---

## 1. Philosophy

Three rules, in priority order:

1. **Clarity.** The interface is legible at a glance. Type, spacing, and contrast do the work; nothing is decorative.
2. **Deference.** The chrome recedes so the content leads. Translucent materials, hairline separators, and restraint instead of borders and boxes.
3. **Depth.** Hierarchy is expressed through layering, blur, and motion — not heavy shadows or color.

Minimal is not empty. It is precise. Every margin is on the grid, every number is tabular, every transition is the same curve.

---

## 2. Signature Element — the Total

Spend boldness in exactly one place: **the order total.** It is the only number a cashier and customer truly care about, so it gets the largest type, the heaviest weight, and tabular figures. Everything else stays quiet — body text, hairline rules, muted grays. When the total updates, it animates with a single confident transition. This is the thing the product is remembered by.

```css
.total-amount {
  font: var(--type-display);            /* 48px / 700 / tabular */
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
```

---

## 3. Color

Semantic tokens, not raw hues. Components reference roles (`--text-primary`, `--bg-surface`), never hex. Dark mode swaps the values, not the names.

### Light

| Token | Value | Use |
|---|---|---|
| `--accent` | `#007AFF` | Primary actions, selected state, links |
| `--accent-pressed` | `#0062CC` | Pressed primary |
| `--success` | `#34C759` | Paid, in stock, confirmations |
| `--warning` | `#FF9500` | Low stock, attention |
| `--danger` | `#FF3B30` | Void, delete, errors |
| `--bg-base` | `#F2F2F7` | App canvas |
| `--bg-surface` | `#FFFFFF` | Cards, sheets, rows |
| `--bg-elevated` | `#FFFFFF` | Popovers, menus |
| `--fill` | `rgba(120,120,128,0.12)` | Input fields, inactive controls |
| `--text-primary` | `#1D1D1F` | Headlines, key labels, amounts |
| `--text-secondary` | `#6E6E73` | Supporting text |
| `--text-tertiary` | `#8E8E93` | Placeholders, captions |
| `--separator` | `rgba(60,60,67,0.18)` | Hairline rules |

### Dark

| Token | Value |
|---|---|
| `--accent` | `#0A84FF` |
| `--accent-pressed` | `#0A84FF` @ 0.85 |
| `--success` | `#30D158` |
| `--warning` | `#FF9F0A` |
| `--danger` | `#FF453A` |
| `--bg-base` | `#000000` |
| `--bg-surface` | `#1C1C1E` |
| `--bg-elevated` | `#2C2C2E` |
| `--fill` | `rgba(120,120,128,0.24)` |
| `--text-primary` | `#F5F5F7` |
| `--text-secondary` | `#AEAEB2` |
| `--text-tertiary` | `#8E8E93` |
| `--separator` | `rgba(84,84,88,0.55)` |

**Dark-mode rule:** raise elevation by lightening the surface (`#000` → `#1C1C1E` → `#2C2C2E`), never by adding shadow.

---

## 4. Typography

System font everywhere — it *is* the Apple look, and it costs nothing to load.

```css
--font-sans: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display",
             system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
```

All prices, quantities, and totals use **tabular figures** so columns align and digits don't jitter on update: `font-variant-numeric: tabular-nums;`

### Type scale

| Role | Size / Line | Weight | Tracking | Use |
|---|---|---|---|---|
| Display | 48 / 52 | 700 | -0.02em | The Total only |
| Title 1 | 28 / 34 | 700 | -0.015em | Screen titles |
| Title 2 | 22 / 28 | 700 | -0.01em | Section headers |
| Title 3 | 20 / 25 | 600 | -0.01em | Card titles |
| Headline | 17 / 22 | 600 | -0.01em | Emphasised rows, prices |
| Body | 17 / 22 | 400 | -0.01em | Default text |
| Callout | 16 / 21 | 400 | 0 | Secondary body |
| Subhead | 15 / 20 | 400 | 0 | Product names in grid |
| Footnote | 13 / 18 | 400 | 0 | Metadata |
| Caption | 12 / 16 | 400 | 0 | Tab labels, fine print |

Sentence case for everything — buttons, titles, labels. No ALL-CAPS.

---

## 5. Spacing & Layout

Strict **8pt grid** (4pt for fine adjustments). Nothing lands off-grid.

```css
--space-1: 4px;   --space-2: 8px;   --space-3: 12px;  --space-4: 16px;
--space-5: 20px;  --space-6: 24px;  --space-8: 32px;  --space-10: 40px;
--space-12: 48px; --space-16: 64px;
```

- Screen margins: `16px` on phone, `24px` on tablet/desktop.
- Min touch target: **44 × 44px**, always.
- Layout: phone is single-column (grid → cart as a sheet); tablet/desktop is two-pane (product grid left, persistent order panel right).

---

## 6. Shape & Material

Continuous, generous corners. Translucency instead of borders.

```css
--radius-sm: 8px;    /* chips, small controls */
--radius-md: 12px;   /* buttons, inputs */
--radius-lg: 16px;   /* cards */
--radius-xl: 22px;   /* sheets, large surfaces */
--radius-pill: 980px;
```

**Materials** (toolbars, tab bars, sheet backdrops) use blur + saturation, the defining Apple texture:

```css
--material-bar: saturate(180%) blur(20px);
--material-bar-bg-light: rgba(255,255,255,0.72);
--material-bar-bg-dark:  rgba(30,30,30,0.72);
/* usage: background: var(--material-bar-bg-*); backdrop-filter: var(--material-bar); */
```

Shadows are subtle and rare — only for floating layers:

```css
--shadow-sm: 0 1px 2px rgba(0,0,0,0.04);
--shadow-md: 0 4px 16px rgba(0,0,0,0.08);
--shadow-lg: 0 12px 40px rgba(0,0,0,0.14);
```

Prefer a hairline `--separator` over a 1px border. On Retina, render separators at `0.5px`.

---

## 7. Motion

One curve, three durations. Consistency reads as quality.

```css
--ease-out:    cubic-bezier(0.16, 1, 0.3, 1);    /* default: reveals, presses */
--ease-spring: cubic-bezier(0.32, 0.72, 0, 1);   /* sheets, the Total update */
--dur-fast: 150ms;  --dur-base: 250ms;  --dur-slow: 400ms;
```

- Press feedback: scale to `0.97` + slight opacity, `--dur-fast`.
- Sheets: slide up with `--ease-spring`, `--dur-slow`.
- Always honour `@media (prefers-reduced-motion: reduce)` → disable transforms, keep opacity only.

---

## 8. Iconography

SF Symbols are the reference look but **can't be used off Apple platforms**, so ship a monoline set that matches the language — **Lucide** or **Phosphor**, drawn at `1.5–2px` stroke, rounded caps and joins. Sizes: `20px` inline, `24px` toolbar/tab, `28px` keypad. Icons inherit `currentColor`; active state is `--accent`.

---

## 9. Components

**Primary button** — height `50px` (mobile) / `44px` (desktop), `--radius-md`, `background: var(--accent)`, white Headline text, full-width in checkout flows. Pressed → `--accent-pressed` + scale `0.97`.

**Tinted button** — `background: color-mix(in srgb, var(--accent) 12%, transparent)`, text `--accent`. For secondary actions.

**Plain button** — text-only in `--accent`. For tertiary/inline actions. Destructive variants use `--danger`.

**Input** — height `44px`, `--radius-md`, `background: var(--fill)`, Body text, `--text-tertiary` placeholder, no border. Focus → `2px` `--accent` ring, offset `2px`.

**Card** — `--bg-surface`, `--radius-lg`, `--space-4` padding, `--shadow-sm` (or none with separators).

**Inset list** — rows min-height `44px`, `--space-4` horizontal padding, grouped in a `--radius-lg` `--bg-surface` container, hairline `--separator` between rows, chevron (`--text-tertiary`) for navigation.

**Segmented control** — pill on `--fill`; selected segment is a `--bg-surface` pill with `--shadow-sm`; Subhead/medium labels. Used for payment method.

**Sheet** — bottom sheet on mobile (`--radius-xl` top corners, drag grabber, `--material-bar` dimmed backdrop, spring in); centered modal on desktop with `--shadow-lg`.

**Toolbar / nav bar** — translucent `--material-bar`, hairline bottom `--separator`, centered Headline title (Title 1 when scrolled to top, collapsing on scroll).

**Tab bar (mobile)** — translucent `--material-bar`, hairline top separator, icon + Caption label, active in `--accent`. Respect `env(safe-area-inset-bottom)`.

---

## 10. POS-Specific Patterns

**Product grid** — responsive auto-fill, card min-width `~140px`, `--radius-lg`, image on top, Subhead name, Headline tabular price. Press → scale `0.97`. Out-of-stock → `opacity 0.4`, non-interactive.

**Order panel** — persistent right pane (tablet/desktop) or sheet (phone). Each line: product name (Body) + quantity stepper + price (Headline, tabular, right-aligned), hairline-separated. Footer stacks subtotal / tax (Subhead, `--text-secondary`) then the **Total** (Display token — the signature). Sticky primary "Charge" button below.

**Numeric keypad** — 3 × 4 grid, keys `--radius-md`, height `64–72px`, `28px` digits, `--space-3` gaps, press fills with `--fill`. Drives cash tendered, quantity, and weight entry.

**Payment** — segmented control (Cash · Card · E-wallet), amount field, change displayed in `--success` when positive. Big "Confirm payment" primary button. Success → checkmark animation in `--success`, then auto-clear.

**Receipt preview** — narrow thermal-width column, centered store header, line items with tabular amounts, totals block, system font throughout. Same layout drives the on-screen preview and the ESC/POS print.

---

## 11. Quality Floor (non-negotiable)

- Contrast meets WCAG AA; `--accent` text only on light fills, never on color.
- Every interactive element ≥ `44 × 44px` with a visible keyboard focus ring.
- Full light/dark support driven by `prefers-color-scheme`, overridable in settings.
- `prefers-reduced-motion` respected everywhere.
- Safe areas handled on mobile via `env(safe-area-inset-*)`.
- Type scales with the OS via `rem` units (Dynamic Type friendly).

---

## 12. Token Starter (drop into the Vue core)

```css
:root {
  --font-sans: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display",
               system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;

  --type-display:  700 48px/52px var(--font-sans);
  --type-title1:   700 28px/34px var(--font-sans);
  --type-title2:   700 22px/28px var(--font-sans);
  --type-title3:   600 20px/25px var(--font-sans);
  --type-headline: 600 17px/22px var(--font-sans);
  --type-body:     400 17px/22px var(--font-sans);
  --type-subhead:  400 15px/20px var(--font-sans);
  --type-caption:  400 12px/16px var(--font-sans);

  --accent:#007AFF; --accent-pressed:#0062CC;
  --success:#34C759; --warning:#FF9500; --danger:#FF3B30;
  --bg-base:#F2F2F7; --bg-surface:#FFFFFF; --bg-elevated:#FFFFFF;
  --fill:rgba(120,120,128,0.12);
  --text-primary:#1D1D1F; --text-secondary:#6E6E73; --text-tertiary:#8E8E93;
  --separator:rgba(60,60,67,0.18);

  --radius-sm:8px; --radius-md:12px; --radius-lg:16px; --radius-xl:22px; --radius-pill:980px;
  --space-1:4px; --space-2:8px; --space-3:12px; --space-4:16px; --space-5:20px;
  --space-6:24px; --space-8:32px; --space-10:40px; --space-12:48px; --space-16:64px;

  --material-bar:saturate(180%) blur(20px);
  --material-bar-bg:rgba(255,255,255,0.72);
  --shadow-sm:0 1px 2px rgba(0,0,0,0.04);
  --shadow-md:0 4px 16px rgba(0,0,0,0.08);
  --shadow-lg:0 12px 40px rgba(0,0,0,0.14);

  --ease-out:cubic-bezier(0.16,1,0.3,1);
  --ease-spring:cubic-bezier(0.32,0.72,0,1);
  --dur-fast:150ms; --dur-base:250ms; --dur-slow:400ms;
}

@media (prefers-color-scheme: dark) {
  :root {
    --accent:#0A84FF; --success:#30D158; --warning:#FF9F0A; --danger:#FF453A;
    --bg-base:#000000; --bg-surface:#1C1C1E; --bg-elevated:#2C2C2E;
    --fill:rgba(120,120,128,0.24);
    --text-primary:#F5F5F7; --text-secondary:#AEAEB2; --text-tertiary:#8E8E93;
    --separator:rgba(84,84,88,0.55);
    --material-bar-bg:rgba(30,30,30,0.72);
  }
}
```