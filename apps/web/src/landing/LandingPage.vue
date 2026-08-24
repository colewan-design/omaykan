<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { discountPercent, formatCurrency, type Product } from '@pos/shared/index'
import { loadStorefrontCatalog } from '@pos/web/storefront/catalog'
import ParticleField from './ParticleField.vue'
import { vReveal } from './reveal'

const ctaParticles = ref<InstanceType<typeof ParticleField> | null>(null)
function onCtaPointerMove(e: PointerEvent) {
  ctaParticles.value?.setPointerFromEvent(e)
}
function onCtaPointerLeave() {
  ctaParticles.value?.clearPointer()
}

// ── Delivery section content ────────────────────────────────────────
// Carried over from the Baguio Delivery landing page. The deals are the
// same illustrative set that site shipped — the storefront app is where
// a store's real catalog and prices are shown.
const deliveryCategories = [
  { slug: 'food', label: 'Food' },
  { slug: 'groceries', label: 'Groceries' },
  { slug: 'pharmacy', label: 'Pharmacy' },
  { slug: 'errands', label: 'Errands' },
  { slug: 'bakery', label: 'Bakery' },
  { slug: 'beverages', label: 'Beverages' },
  { slug: 'laundry', label: 'Laundry' },
  { slug: 'medicine', label: 'Medicine' },
]

// Featured Picks are the store's real products, not marketing placeholders —
// each card deep-links to /store/product/<id> where add-to-cart lives, so the
// ids have to be ones create-online-order will actually accept.
const featuredProducts = ref<Product[]>([])

onMounted(() => {
  loadStorefrontCatalog()
    .then((catalog) => {
      featuredProducts.value = catalog.products.filter((p) => !p.outOfStock).slice(0, 8)
    })
    // The section is v-if'd on a non-empty list, so a failure here just hides
    // it rather than showing items nobody can buy.
    .catch(() => { featuredProducts.value = [] })
})

const deliveryWhy = [
  {
    title: 'Free Delivery',
    desc: 'No delivery fees on qualifying orders from participating local vendors.',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2"/><path d="M15 18H9"/><path d="M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.62l-3.48-4.35A1 1 0 0 0 17.52 8H14"/><circle cx="17" cy="18" r="2"/><circle cx="7" cy="18" r="2"/></svg>',
  },
  {
    title: 'Fresh Guarantee',
    desc: 'Produce sourced same-day from local markets and stores around the city.',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/><path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/></svg>',
  },
  {
    title: 'Easy Returns',
    desc: 'Not happy with an item? The rider or vendor sorts it out on the spot.',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7v6h6"/><path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"/></svg>',
  },
  {
    title: '24/7 Support',
    desc: 'Reach our team anytime if an order needs a hand.',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 18v-6a9 9 0 0 1 18 0v6"/><path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"/></svg>',
  },
]
</script>

<template>
  <div class="landing">

    <!-- ── Nav ───────────────────────────────────────────────────────── -->
    <nav class="lp-nav">
      <a href="#" class="lp-nav-logo">
        <img src="/icon.png" alt="" width="28" height="28" style="border-radius:7px;" />
        Baguio Online Market
      </a>
      <ul class="lp-nav-links">
        <li><a href="#lpd-cats">Categories</a></li>
        <li><a href="#lpd-deals">Deals</a></li>
        <li><a href="#lpd-fresh">Fresh Produce</a></li>
        <li><a href="/signup">For merchants</a></li>
      </ul>
      <div class="lp-nav-right">
        <a href="/app/auth" class="lp-nav-login">Log in</a>
        <a href="/signup" class="lp-nav-cta">Sell with us</a>
      </div>
    </nav>

    <!-- ── Delivery ──────────────────────────────────────────────────────
         The Baguio Delivery storefront design, brought over now that Baguio
         Online Market has absorbed it. Its green brand is scoped to `.lp-delivery`
         so the marketing gold stays the page's primary accent.          -->
    <section id="delivery" class="lp-delivery">

      <!-- Hero band -->
      <div class="lpd-hero">
        <h2 aria-hidden="true" class="lpd-watermark">Delivery</h2>
        <div class="lpd-hero-grid">
          <div class="lpd-hero-copy">
            <p v-reveal class="lpd-eyebrow">Baguio Online Market Delivery</p>
            <p v-reveal="80" class="lpd-hero-sub">
              Every Baguio Online Market store gets a customer app, free. Shoppers browse farm-fresh fruits,
              vegetables, dairy and daily essentials — ordered through the app, delivered by riders
              in your neighbourhood.
            </p>
            <a v-reveal="160" href="#lpd-deals" class="lpd-shop-btn">
              Shop Now
              <span>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
              </span>
            </a>
          </div>

          <div class="lpd-hero-art">
            <span class="lpd-script">
              Same-Day Delivery
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
            </span>
            <img src="/delivery/hero-rider.webp" alt="Delivery rider carrying two bags of fresh groceries" width="900" height="1125" />
            <div class="lpd-float">
              <img src="/delivery/fruit-basket.webp" alt="" width="172" height="172" loading="lazy" />
              <div class="lpd-float-name">Fresh Vegetables</div>
              <div><span class="lpd-float-price">₱180.00</span><span class="lpd-float-was">₱240.00</span></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Popular categories -->
      <div id="lpd-cats" class="lp-container lpd-block">
        <div class="lpd-head">
          <h2 v-reveal class="lpd-title">Popular Categories</h2>
          <a v-reveal="60" href="#lpd-deals" class="lpd-showall">
            Show All
            <span>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
            </span>
          </a>
        </div>
        <div class="lpd-cats">
          <a v-for="(cat, i) in deliveryCategories" :key="cat.slug" v-reveal="i * 40" :href="'#lpd-deals'" class="lpd-cat reveal--scale">
            <div class="lpd-cat-art">
              <img :src="`/delivery/landing-cards/category-${cat.slug}.webp`" alt="" width="512" height="512" loading="lazy" />
            </div>
            <div class="lpd-cat-label">{{ cat.label }}</div>
          </a>
        </div>
      </div>

      <!-- Featured deals — real products from the store's catalog. Each card
           deep-links into the storefront's product page, where add-to-cart
           and checkout live. Hidden entirely if the catalog can't be read,
           rather than showing prices nobody can actually buy. -->
      <div v-if="featuredProducts.length > 0" id="lpd-deals" class="lp-container lpd-block">
        <div class="lpd-head">
          <div>
            <h2 v-reveal class="lpd-title">Featured Picks</h2>
            <p v-reveal="60" class="lpd-sub">Fresh from the store today — tap any item to see it and add it to your cart.</p>
          </div>
          <a v-reveal="60" href="/store" class="lpd-showall">
            Shop all
            <span>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
            </span>
          </a>
        </div>
        <div class="lpd-deals">
          <a
            v-for="(product, i) in featuredProducts"
            :key="product.id"
            v-reveal="i * 40"
            :href="`/store/product/${product.id}`"
            class="lpd-deal reveal--scale"
          >
            <span v-if="discountPercent(product) !== null" class="lpd-badge">
              -{{ discountPercent(product) }}%
            </span>
            <div class="lpd-deal-art">
              <img
                v-if="product.imageUrl"
                :src="product.imageUrl"
                :alt="product.name"
                width="512"
                height="512"
                loading="lazy"
              />
              <div v-else class="lpd-deal-fallback" aria-hidden="true">🛒</div>
            </div>
            <div class="lpd-deal-name">{{ product.name }}</div>
            <div class="lpd-price-row">
              <span class="lpd-price">{{ formatCurrency(product.priceCents) }}</span>
              <span v-if="discountPercent(product) !== null" class="lpd-was">
                {{ formatCurrency(product.compareAtPriceCents!) }}
              </span>
              <span v-else-if="product.unitLabel" class="lpd-unit">per {{ product.unitLabel }}</span>
            </div>
          </a>
        </div>
      </div>

      <!-- Fresh from local markets -->
      <div id="lpd-fresh" class="lp-container lpd-block">
        <div class="lpd-fresh">
          <div v-reveal class="lpd-fresh-art reveal--scale">
            <span class="lpd-chip lpd-chip--top">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
              100% Fresh Guarantee
            </span>
            <img src="/delivery/fruit-basket.webp" alt="Basket of fresh fruit and vegetables" width="900" height="1125" loading="lazy" />
            <span class="lpd-chip lpd-chip--bot">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18"/><path d="M5 21V7l8-4v18"/><path d="M19 21V11l-6-4"/></svg>
              200+ local vendors
            </span>
          </div>

          <div>
            <p v-reveal class="lpd-eyebrow lpd-eyebrow--dark">Fresh From Local Markets</p>
            <h2 v-reveal="60" class="lpd-fresh-title">Fresh Fruits &amp; Vegetables.<br>Delivered Daily.</h2>
            <p v-reveal="120" class="lpd-sub">
              We deliver everything your kitchen needs — straight from your local markets and
              neighbourhood stores to your doorstep. No commissions, no markup, just fair prices.
            </p>

            <div class="lpd-promos">
              <div v-reveal="160" class="lpd-promo lpd-promo--green">
                <div class="lpd-promo-kicker">New Here?</div>
                <div class="lpd-promo-big">10% Off</div>
                <p class="lpd-promo-desc">Your first order from any vendor.</p>
              </div>
              <div v-reveal="200" class="lpd-promo lpd-promo--gold">
                <div class="lpd-promo-kicker">Free Delivery</div>
                <div class="lpd-promo-big">On All Orders</div>
                <p class="lpd-promo-desc">Riders come to you — no hidden fees.</p>
              </div>
              <div v-reveal="240" class="lpd-promo lpd-promo--mint">
                <div class="lpd-promo-kicker">Fresh Groceries</div>
                <div class="lpd-promo-big">For Your Family</div>
                <p class="lpd-promo-desc">Sourced daily from trusted local vendors.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Why choose us -->
      <div class="lp-container lpd-block">
        <h2 v-reveal class="lpd-title lpd-title--standalone">Why Choose Us</h2>
        <div class="lpd-why">
          <div v-for="(item, i) in deliveryWhy" :key="item.title" v-reveal="i * 60">
            <span class="lpd-why-icon" v-html="item.icon"></span>
            <h3 class="lpd-why-title">{{ item.title }}</h3>
            <p class="lpd-why-desc">{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ── CTA ───────────────────────────────────────────────────────── -->
    <section id="cta" class="lp-cta" @pointermove="onCtaPointerMove" @pointerleave="onCtaPointerLeave">
      <span class="lp-blob lp-blob--cta" aria-hidden="true"></span>
      <div v-reveal class="lp-particle-zone lp-particle-zone--full" aria-hidden="true">
        <ParticleField ref="ctaParticles" variant="dark" :density="55" :repel-radius="0.45" :repel-strength="0.35" />
      </div>
      <div class="lp-container lp-cta-inner">
        <!-- The merchant bridge: this page is now customer-facing, so the one
             seller-facing moment on it points at the registration page. -->
        <p v-reveal="0" class="lp-eyebrow-label lp-eyebrow-label--cta">For merchants</p>
        <h2 v-reveal="0" class="lp-cta-title">Run a store? Sell with us.</h2>
        <p v-reveal="0" class="lp-cta-stat">List your shop for free and get a full point-of-sale with it — <strong>no commissions, ever</strong>.</p>

        <a v-reveal="140" href="/signup" class="lp-btn-accent">Become a vendor</a>

        <p v-reveal="260" class="lp-cta-reassure">Your store is ready the moment you sign up.</p>
        <p v-reveal="260" class="lp-cta-secondary">Already selling with us? <a href="/app/auth">Sign in</a>.</p>
      </div>
    </section>

    <!-- ── Footer ────────────────────────────────────────────────────── -->
    <footer class="lp-footer">
      <div class="lp-footer-top">
        <div class="lp-footer-brand">
          <span class="lp-footer-logo">
            <img src="/icon.png" alt="" width="24" height="24" style="border-radius:6px;" />
            Baguio Online Market
          </span>
          <p class="lp-footer-tagline">Local vendors, local riders, cash on delivery — no commissions, no middlemen.</p>
        </div>

        <div class="lp-footer-col">
          <p class="lp-footer-col__title">Shop</p>
          <a href="#lpd-cats">Categories</a>
          <a href="#lpd-deals">Deals</a>
          <a href="#lpd-fresh">Fresh Produce</a>
        </div>

        <div class="lp-footer-col">
          <p class="lp-footer-col__title">Company</p>
          <a href="/signup">Become a vendor</a>
          <a href="/app/auth">Sign in</a>
        </div>
      </div>
      <div class="lp-footer-bottom">
        <span class="lp-footer-copy">© 2026 Baguio Online Market. All rights reserved.</span>
      </div>
    </footer>

  </div>
</template>

<style scoped>
/* Marketing brand tokens — scoped to .landing (not :root) so the
   Teleport-to-body PaymentSheet/ProductSheet dialogs aren't affected.
   See landing.css for the full rationale. */
/* ── Nav — permanently dark, matches the hero/CTA/footer panels ───────── */
/* White bar: the page now opens on the delivery hero and its light sections,
   so the nav is light too rather than transparent over a dark ground. */
.lp-nav {
  position: sticky; top: 0; z-index: 100;
  display: grid; grid-template-columns: auto 1fr auto; align-items: center; column-gap: 24px;
  padding: 0 40px; height: 68px;
  background: rgba(255,255,255,0.92);
  backdrop-filter: saturate(160%) blur(20px);
  -webkit-backdrop-filter: saturate(160%) blur(20px);
  border-bottom: 1px solid #eef1ef;
}
.lp-nav-logo { display: flex; align-items: center; gap: 9px; font-size: 17px; font-weight: 800; letter-spacing: -0.02em; color: #1a1a1a; }
.lp-nav-links { justify-self: center; display: flex; align-items: center; gap: 36px; list-style: none; margin: 0; padding: 0; }
.lp-nav-links a { font-size: 14.5px; font-weight: 500; color: #4b5563; transition: color 150ms; }
.lp-nav-links a:hover { color: #1a1a1a; }
.lp-nav-right { justify-self: end; display: flex; align-items: center; gap: 14px; }
.lp-nav-login {
  display: inline-flex; align-items: center; padding: 9px 22px;
  background: transparent; color: #1a1a1a; font-size: 14px; font-weight: 700;
  border: 1.5px solid #dfe3e0; border-radius: 980px; transition: background 150ms, border-color 150ms, color 150ms;
}
.lp-nav-login:hover { background: #f4f6f5; border-color: #c9cfcb; }
.lp-nav-cta {
  display: inline-flex; align-items: center; padding: 9px 24px;
  background: linear-gradient(135deg, #5bbf8a, var(--accent) 45%, var(--accent-pressed));
  color: var(--accent-ink); font-size: 14px; font-weight: 700;
  border: 1.5px solid transparent; border-radius: 980px; transition: filter 150ms, transform 150ms;
}
.lp-nav-cta:hover { filter: brightness(1.08); transform: translateY(-1px); }



/* ── CTA ───────────────────────────────────────────────────────────── */
.lp-cta { position: relative; overflow: hidden; background: var(--marketing-dark); color: #fff; text-align: center; padding: 96px 40px; }
.lp-cta-inner { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; }
.lp-cta-title { font-size: clamp(1.9rem, 3.5vw, 2.75rem); font-weight: 800; letter-spacing: -0.04em; line-height: 1.06; color: #fff; margin: 0 0 12px; }
.lp-cta-stat { font-size: 13px; color: rgba(255,255,255,0.45); margin: 0 0 32px; }
.lp-cta-stat strong { color: rgba(255,255,255,0.75); font-weight: 600; }
.lp-email-form { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; margin-bottom: 16px; }
.lp-email-input {
  padding: 14px 20px; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.16);
  border-radius: 12px; color: #fff; font-size: 15px; font-family: var(--font-sans); width: 300px; outline: none;
}
.lp-email-input::placeholder { color: rgba(255,255,255,0.3); }
.lp-email-input:focus { border-color: rgba(255,255,255,0.35); background: rgba(255,255,255,0.12); }
.lp-btn-accent { display: inline-flex; align-items: center; padding: 14px 28px; background: var(--accent); color: var(--accent-ink); font-size: 15px; font-weight: 700; border-radius: 12px; border: none; cursor: pointer; }
.lp-btn-accent:hover { background: var(--accent-pressed); }
.lp-cta-reassure { font-size: 12.5px; color: rgba(255,255,255,0.35); margin: 0 0 20px; }
.lp-cta-secondary { font-size: 14px; color: rgba(255,255,255,0.45); }
.lp-cta-secondary a { color: var(--accent); text-decoration: underline; text-underline-offset: 3px; }
.lp-cta-secondary a:hover { color: #fff; }
.lp-form-thanks { font-size: 16px; color: var(--accent); margin: 0; }

/* ── Footer ────────────────────────────────────────────────────────── */
.lp-footer { padding: 64px 40px 32px; background: var(--marketing-dark); border-top: 1px solid rgba(255,255,255,0.06); }
.lp-footer-top {
  max-width: 1080px; margin: 0 auto 40px;
  display: grid; grid-template-columns: 1.6fr 1fr 1fr; gap: 32px;
}
.lp-footer-brand { display: flex; flex-direction: column; gap: 10px; }
.lp-footer-logo { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 700; letter-spacing: -0.015em; color: rgba(255,255,255,0.8); }
.lp-footer-tagline { font-size: 13px; color: rgba(255,255,255,0.4); margin: 0; max-width: 220px; line-height: 1.55; }
.lp-footer-col { display: flex; flex-direction: column; gap: 12px; }
.lp-footer-col__title { font-size: 11px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: rgba(255,255,255,0.35); margin: 0 0 2px; }
.lp-footer-col a { font-size: 13.5px; color: rgba(255,255,255,0.55); }
.lp-footer-col a:hover { color: #fff; }
.lp-footer-bottom { max-width: 1080px; margin: 0 auto; padding-top: 28px; border-top: 1px solid rgba(255,255,255,0.06); }
.lp-footer-copy { font-size: 13px; color: rgba(255,255,255,0.3); }

/* ── Delivery ───────────────────────────────────────────────────────
   The Baguio Delivery landing design. Its structure, imagery, type and
   green brand are kept; the light card surfaces it used are rendered on
   this page's dark ground instead, so the section reads as Baguio
   without breaking the surrounding dark/gold marketing page.          */
.lp-delivery {
  --g: #22c55e;
  --g-dark: #16a34a;
  --g-deep: #1a6b3c;
  --lime: #bbf451;
  /* Baguio's own light surfaces — the section sits on white between the dark
     nav/hero above it and the dark CTA/footer below, exactly as on that site. */
  --d-card: #ffffff;
  --d-line: #eef1ef;
  --d-text: #6b7280;
  --d-ink: #1a1a1a;
  --d-art: #e8f0f2;
  background: #ffffff;
}
.lp-delivery h2,
.lp-delivery h3 { font-family: var(--font-sans); }

/* Hero band */
.lpd-hero {
  position: relative;
  overflow: hidden;
  padding: 92px 40px 76px;
  background: radial-gradient(120% 120% at 15% 0, #5bbf8a 0, #1a6b3c 55%, #103f23);
  color: #fff;
}
.lpd-watermark {
  position: absolute;
  inset-inline: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1;
  margin: 0;
  font-size: 17vw;
  font-weight: 800;
  line-height: 1;
  text-align: center;
  text-transform: uppercase;
  letter-spacing: -0.03em;
  white-space: nowrap;
  color: rgba(255,255,255,0.10);
  pointer-events: none;
  user-select: none;
}
.lpd-hero-grid {
  position: relative;
  z-index: 3;
  max-width: 1080px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 40px;
}
.lpd-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--lime);
  margin: 0 0 14px;
}
.lpd-eyebrow--dark { color: var(--g); }
.lpd-hero-sub {
  font-size: 16px;
  font-weight: 500;
  line-height: 1.6;
  color: rgba(255,255,255,0.9);
  max-width: 22rem;
  margin: 0;
}
/* "Shop Now" pill with the circular arrow chip. */
.lpd-shop-btn {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  padding: 8px 8px 8px 24px;
  border-radius: 980px;
  background: #111;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  transition: transform 200ms cubic-bezier(0.16,1,0.3,1);
}
.lpd-shop-btn:hover { transform: scale(1.03); }
.lpd-shop-btn span {
  width: 32px; height: 32px; border-radius: 999px;
  background: #fff; color: #111;
  display: flex; align-items: center; justify-content: center;
}

.lpd-hero-art { position: relative; display: flex; justify-content: center; }
.lpd-hero-art > img {
  width: 100%; max-width: 420px; height: auto;
  filter: drop-shadow(0 24px 48px rgba(0,0,0,0.35));
}
.lpd-script {
  position: absolute;
  top: -6px; right: 4px; z-index: 4;
  transform: rotate(-15deg);
  display: inline-flex; align-items: center; gap: 4px;
  font-family: 'Caveat', cursive;
  font-size: 30px; font-weight: 700;
  color: var(--lime);
  text-shadow: 0 1px 3px rgba(0,0,0,0.35);
}
/* Vertical product card overlapping the rider's lower right. */
.lpd-float {
  position: absolute;
  right: -8px; bottom: 8%; z-index: 4;
  width: 172px; padding: 12px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(0,0,0,0.35);
  color: #1a1a1a;
}
.lpd-float img {
  width: 100%; aspect-ratio: 1/1;
  border-radius: 10px; object-fit: cover;
  background: #f5f9f6; display: block; margin-bottom: 10px;
}
.lpd-float-name { font-size: 13px; font-weight: 600; margin-bottom: 3px; }
.lpd-float-price { font-size: 15.5px; font-weight: 800; }
.lpd-float-was { font-size: 12px; color: #6b7280; text-decoration: line-through; margin-left: 6px; }

/* Blocks */
.lpd-block { padding: 76px 0; }
.lpd-block + .lpd-block { border-top: 1px solid var(--d-line); }
.lpd-head {
  display: flex; align-items: baseline; justify-content: space-between;
  gap: 20px; margin-bottom: 36px;
}
.lpd-title {
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 800; letter-spacing: -0.03em; color: var(--d-ink); margin: 0;
}
.lpd-title--standalone { margin-bottom: 36px; }
.lpd-sub { font-size: 15px; color: var(--d-text); line-height: 1.6; margin: 8px 0 0; max-width: 560px; }

.lpd-showall {
  display: inline-flex; align-items: center; gap: 10px;
  padding: 7px 7px 7px 20px;
  border: 1px solid var(--d-line); border-radius: 980px;
  background: transparent;
  font-size: 14px; font-weight: 600; color: var(--d-ink); white-space: nowrap;
  transition: transform 200ms cubic-bezier(0.16,1,0.3,1), border-color 200ms;
}
.lpd-showall:hover { transform: scale(1.03); border-color: var(--g); }
.lpd-showall span {
  width: 28px; height: 28px; border-radius: 999px;
  background: var(--d-ink); color: #fff;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}

/* Popular categories — rounded-square photo tiles */
.lpd-cats { display: grid; grid-template-columns: repeat(8, 1fr); gap: 16px; }
.lpd-cat { text-align: center; }
.lpd-cat-art {
  aspect-ratio: 1/1; border-radius: 14px; overflow: hidden;
  background: var(--d-card); border: 1px solid var(--d-line);
  box-shadow: 0 4px 14px rgba(15,23,42,0.05);
  margin-bottom: 10px;
  transition: transform 250ms cubic-bezier(0.16,1,0.3,1), border-color 250ms;
}
.lpd-cat:hover .lpd-cat-art { transform: translateY(-4px); border-color: var(--g); }
.lpd-cat-art img { width: 100%; height: 100%; object-fit: cover; display: block; }
.lpd-cat-label { font-size: 13.5px; font-weight: 600; color: var(--d-ink); }

/* Featured deals */
.lpd-deals { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; }
.lpd-deal {
  position: relative; display: block; padding: 16px; border-radius: 20px;
  background: var(--d-card); border: 1px solid var(--d-line);
  box-shadow: 0 4px 14px rgba(15,23,42,0.05);
  text-decoration: none; color: inherit;
  transition: transform 250ms cubic-bezier(0.16,1,0.3,1), border-color 250ms, box-shadow 250ms;
}
.lpd-deal:hover {
  transform: translateY(-4px);
  border-color: var(--g);
  box-shadow: 0 12px 28px rgba(15,23,42,0.10);
}
.lpd-deal:hover .lpd-deal-name { color: var(--g-dark); }
.lpd-deal-fallback {
  display: grid; place-items: center; width: 100%; height: 100%; font-size: 28px;
}
.lpd-unit { font-size: 12px; color: var(--d-text); }
.lpd-badge {
  position: absolute; top: 14px; left: 14px; z-index: 2;
  padding: 3px 9px; border-radius: 980px;
  background: var(--g); color: #fff;
  font-size: 11.5px; font-weight: 800;
}
.lpd-deal-art {
  aspect-ratio: 1/1; border-radius: 14px; overflow: hidden;
  background: var(--d-art); margin-bottom: 14px;
}
.lpd-deal-art img { width: 100%; height: 100%; object-fit: cover; display: block; }
.lpd-deal-name { font-size: 14.5px; font-weight: 600; color: var(--d-ink); margin-bottom: 6px; }
.lpd-price-row { display: flex; align-items: baseline; gap: 8px; }
.lpd-price { font-size: 17px; font-weight: 800; color: var(--d-ink); }
.lpd-was { font-size: 13px; color: var(--d-text); text-decoration: line-through; }

/* Fresh produce split */
.lpd-fresh { display: grid; grid-template-columns: 0.85fr 1.15fr; gap: 48px; align-items: center; }
.lpd-fresh-art { position: relative; display: flex; justify-content: center; }
.lpd-fresh-art > img { width: 100%; max-width: 340px; height: auto; }
.lpd-chip {
  position: absolute;
  display: inline-flex; align-items: center; gap: 7px;
  padding: 9px 15px; border-radius: 980px;
  background: #fff; box-shadow: 0 12px 30px rgba(15,23,42,0.14);
  font-size: 12.5px; font-weight: 700; color: var(--d-ink); white-space: nowrap;
}
.lpd-chip svg { color: var(--g-dark); flex-shrink: 0; }
.lpd-chip--top { top: 14px; left: -8px; }
.lpd-chip--bot { bottom: 18px; right: -8px; }

.lpd-fresh-title {
  font-size: clamp(1.75rem, 3.5vw, 2.5rem);
  font-weight: 800; line-height: 1.08; letter-spacing: -0.03em;
  color: var(--d-ink); margin: 0 0 16px;
}
.lpd-promos { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 32px; }
.lpd-promo { padding: 18px; border-radius: 18px; border: 1px solid var(--d-line); background: var(--d-card); }
.lpd-promo--green { background: #dcfce7; border-color: #bbf7d0; }
.lpd-promo--gold  { background: #fef9c3; border-color: #fde68a; }
.lpd-promo--mint  { background: #fce7f3; border-color: #fbcfe8; }
.lpd-promo-kicker { font-size: 12px; font-weight: 600; color: var(--d-text); }
.lpd-promo-big { font-size: 19px; font-weight: 800; letter-spacing: -0.02em; color: var(--d-ink); margin: 2px 0 6px; }
.lpd-promo-desc { font-size: 12.5px; color: var(--d-text); line-height: 1.5; margin: 0; }

/* Why choose us */
.lpd-why { display: grid; grid-template-columns: repeat(4, 1fr); gap: 28px; }
.lpd-why-icon {
  width: 48px; height: 48px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  background: #dcfce7; color: var(--g-dark);
  margin-bottom: 16px;
}
.lpd-why-title { font-size: 16px; font-weight: 700; letter-spacing: -0.015em; color: var(--d-ink); margin: 0 0 6px; }
.lpd-why-desc { font-size: 13.5px; color: var(--d-text); line-height: 1.6; margin: 0; }

/* ── Responsive ────────────────────────────────────────────────────── */
@media (max-width: 1020px) {
  .lp-footer-top { grid-template-columns: 1.4fr 1fr 1fr; }
}
@media (max-width: 880px) {
  .lpd-hero-grid { grid-template-columns: 1fr; }
  .lpd-hero-copy { order: 2; text-align: center; }
  .lpd-hero-sub { margin-inline: auto; }
  .lpd-hero-art { order: 1; }
  .lpd-cats { grid-template-columns: repeat(4, 1fr); }
  .lpd-deals { grid-template-columns: repeat(2, 1fr); }
  .lpd-fresh { grid-template-columns: 1fr; gap: 40px; }
  .lpd-why { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 720px) {
  .lp-nav { padding: 0 20px; }
  .lp-nav-links { display: none; }
  .lp-footer { padding: 48px 20px 24px; }
  .lp-footer-top { grid-template-columns: 1fr 1fr; gap: 28px; text-align: left; }
  .lp-footer-bottom { text-align: center; }
  .lpd-hero { padding: 72px 20px 56px; }
  .lpd-block { padding: 56px 0; }
  .lpd-promos { grid-template-columns: 1fr; }
}
@media (max-width: 480px) {
  .lp-email-form { flex-direction: column; align-items: center; }
  .lp-email-input { width: 100%; max-width: 340px; }
  .lp-footer-top { grid-template-columns: 1fr; }
  .lpd-cats { grid-template-columns: repeat(3, 1fr); gap: 12px; }
  .lpd-why { grid-template-columns: 1fr; gap: 24px; }
  .lpd-float { right: -4px; width: 148px; }
  .lpd-script { font-size: 24px; }
  /* The chips overhang the basket art by design; at this width that pushes
     past the viewport, so tuck them back inside it. */
  .lpd-chip--top { left: 0; }
  .lpd-chip--bot { right: 0; }
}
</style>
