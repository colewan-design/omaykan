<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { discountPercent, type Product } from '@pos/shared/index'
import { loadStorefrontCatalog } from '@pos/web/storefront/catalog'
import { useStorefrontCart } from '@pos/web/storefront/cart'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import ProductRow from './ProductRow.vue'
import { vReveal } from './reveal'

// Grocery-marketplace landing, modelled on the FreshDirect reference: a dark
// green utility bar with the search front and centre, then a stack of
// horizontally-scrolling product shelves interleaved with editorial blocks.
//
// Everything on the page is real catalog data — each card deep-links to
// /store/product/<id>, and the + button writes to the shared cart, which now
// persists to localStorage so it survives the cross-entry hop to /store.

const cart = useStorefrontCart()

const products = ref<Product[]>([])
const categories = ref<{ id: string; name: string }[]>([])
const loading = ref(true)

const searchTerm = ref('')

onMounted(() => {
  loadStorefrontCatalog()
    .then((catalog) => {
      products.value = catalog.products.filter((p) => !p.outOfStock)
      categories.value = catalog.categories
    })
    .catch(() => {
      products.value = []
      categories.value = []
    })
    .finally(() => { loading.value = false })
})

/** Shelves are v-if'd on non-empty, so a thin catalog just shows fewer rows. */
const popular = computed(() => products.value.slice(0, 12))

const deals = computed(() => products.value.filter((p) => discountPercent(p) !== null))

const cheapest = computed(() =>
  [...products.value].sort((a, b) => a.priceCents - b.priceCents).slice(0, 12),
)

/** Products from the largest category, for the editorial block. */
const featureCategory = computed(() => {
  if (categories.value.length === 0) return null
  const counts = categories.value.map((c) => ({
    category: c,
    items: products.value.filter((p) => p.categoryId === c.id),
  }))
  return counts.sort((a, b) => b.items.length - a.items.length)[0] ?? null
})

const serviceCategories = [
  { slug: 'food', label: 'Food' },
  { slug: 'groceries', label: 'Groceries' },
  { slug: 'pharmacy', label: 'Pharmacy' },
  { slug: 'errands', label: 'Errands' },
  { slug: 'bakery', label: 'Bakery' },
  { slug: 'beverages', label: 'Beverages' },
  { slug: 'laundry', label: 'Laundry' },
  { slug: 'medicine', label: 'Medicine' },
]

function submitSearch() {
  const q = searchTerm.value.trim()
  // The store is a separate Vite entry, so this is a real navigation; the
  // query rides along in ?q= and search.ts picks it up on the other side.
  window.location.href = q ? `/store?q=${encodeURIComponent(q)}` : '/store'
}

function backToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div class="landing fd">

    <!-- ── Utility bar ───────────────────────────────────────────────── -->
    <header class="fd-bar">
      <a href="/" class="fd-brand" aria-label="Omaykan — home">
        <BrandLogo variant="dark" :size="20" />
      </a>

      <div class="fd-bar__delivery">
        <span>Delivery</span>
        <a href="/store">Enter your address</a>
      </div>

      <form class="fd-search" @submit.prevent="submitSearch">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        <input
          v-model="searchTerm"
          type="search"
          placeholder="What's on your shopping list?"
          aria-label="Search products"
        />
        <button type="submit" class="fd-search__go">Search</button>
      </form>

      <div class="fd-bar__account">
        <span class="fd-bar__label">Merchants</span>
        <a href="/signup">Sell with us</a>
      </div>

      <div class="fd-bar__account">
        <span class="fd-bar__label">Account</span>
        <a href="/app/auth">Sign in</a>
      </div>

      <a href="/store/checkout" class="fd-cart" aria-label="Cart">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
        <span v-if="cart.itemCount.value > 0" class="fd-cart__count">{{ cart.itemCount.value }}</span>
      </a>
    </header>

    <main class="fd-main">

      <!-- ── Lead promo banner ───────────────────────────────────────── -->
      <section class="fd-promo">
        <h1 v-reveal class="fd-promo__title">Skip the trip to the market</h1>
        <p v-reveal="60" class="fd-promo__sub">
          Order from the shops you already know in Baguio. Riders in your neighbourhood bring it
          over the same day — cash or GCash on arrival, and vendors keep every peso.
        </p>
        <a v-reveal="60" href="/store" class="fd-promo__link">Start shopping — no commissions, ever</a>
        <div v-reveal="120" class="fd-promo__art">
          <img src="/delivery/hero-rider.webp" alt="Rider carrying two bags of fresh groceries" width="900" height="1125" />
          <div class="fd-promo__art-copy">
            <span class="fd-promo__kicker">Same-day delivery</span>
            <span class="fd-promo__fee">Flat ₱49 · first 2&nbsp;km</span>
          </div>
        </div>
      </section>

      <div class="fd-wrap">

        <!-- ── Shop by category ──────────────────────────────────────── -->
        <section class="fd-cats-block">
          <div class="fdrow-head">
            <h2 class="fd-h2">Shop by category</h2>
            <a href="/store" class="fd-viewall">View all</a>
          </div>
          <div class="fd-cats">
            <a
              v-for="(cat, i) in serviceCategories"
              :key="cat.slug"
              v-reveal="i * 30"
              href="/store"
              class="fd-cat"
            >
              <div class="fd-cat__art">
                <img :src="`/delivery/landing-cards/category-${cat.slug}.webp`" alt="" width="512" height="512" loading="lazy" />
              </div>
              <span>{{ cat.label }}</span>
            </a>
          </div>
        </section>

        <p v-if="loading" class="fd-loading">Loading today's catalog…</p>

        <ProductRow title="Popular now" :products="popular" />

        <ProductRow
          title="Fresh deals: this week's best for less"
          blurb="Marked down by the shops themselves — no commission taken out, so the discount reaches you whole."
          :products="deals"
        />

        <!-- ── Editorial ─────────────────────────────────────────────── -->
        <section v-if="featureCategory && featureCategory.items.length > 0" class="fd-editorial">
          <div class="fd-editorial__body">
            <div class="fdrow-head">
              <h2 class="fd-h2">Meet your local vendors</h2>
              <a href="/signup" class="fd-viewall">Become one</a>
            </div>
            <p class="fd-editorial__copy">
              Every shop here is a real counter somewhere in the city — a carinderia, a sari-sari
              store, a market stall. They run their day on Omaykan's point of sale, and
              the same catalog they ring up in person is the one you're browsing now. Nothing is
              marked up for the privilege.
            </p>
            <a href="/signup" class="fd-btn">List your shop for free</a>
          </div>
          <div class="fd-editorial__art">
            <img src="/delivery/fruit-basket.webp" alt="Basket of fresh fruit and vegetables" width="900" height="1125" loading="lazy" />
          </div>
        </section>

        <ProductRow
          title="Everyday essentials under ₱100"
          :products="cheapest"
        />

        <!-- ── Merchant strip ────────────────────────────────────────── -->
        <section class="fd-merchant">
          <div>
            <h2 class="fd-merchant__title">Run a shop? Sell with us.</h2>
            <p class="fd-merchant__sub">
              A full point-of-sale, a storefront, and riders — free while we're in early access.
              You keep 100% of every sale.
            </p>
          </div>
          <a href="/signup" class="fd-merchant__cta">Get started</a>
        </section>
      </div>
    </main>

    <!-- ── Footer ────────────────────────────────────────────────────── -->
    <footer class="fd-footer">
      <button type="button" class="fd-totop" @click="backToTop">Back to Top</button>

      <div class="fd-footer__cols">
        <div>
          <BrandLogo variant="light" :size="17" class="fd-footer__logo" />
          <a href="/store">Shop</a>
          <a href="/signup">Become a vendor</a>
          <a href="/signup">Become a rider</a>
          <a href="/app/auth">Sign in</a>
        </div>
        <div>
          <p class="fd-footer__title">Help</p>
          <a href="/store">Delivery information</a>
          <a href="/store">Track an order</a>
          <a href="/signup">Contact us</a>
        </div>
        <div>
          <p class="fd-footer__title">Categories</p>
          <a href="/store">Food</a>
          <a href="/store">Groceries</a>
          <a href="/store">Pharmacy</a>
          <a href="/store">Errands</a>
        </div>
        <div>
          <p class="fd-footer__title">How you pay</p>
          <p class="fd-footer__note">Cash or GCash on arrival. Nothing is charged online.</p>
          <p class="fd-footer__note">Flat ₱49 delivery for the first 2&nbsp;km, then ₱15/km.</p>
        </div>
      </div>

      <div class="fd-footer__bottom">
        <span>© 2026 Omaykan. Local vendors, local riders, no commissions.</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* ── Utility bar ─────────────────────────────────────────────────────
   Dark green, full width, search dominant — the reference's signature. */
.fd-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 0 28px;
  min-height: 76px;
  background: #1a6b3c;
  color: #fff;
}

.fd-brand { display: flex; align-items: center; flex-shrink: 0; }

.fd-footer__logo { margin-bottom: 6px; }

.fd-bar__delivery {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  padding-left: 22px;
  border-left: 1px solid rgba(255,255,255,0.22);
  white-space: nowrap;
}
.fd-bar__delivery span { font-size: 13.5px; font-weight: 700; }
.fd-bar__delivery a {
  font-size: 13px;
  color: rgba(255,255,255,0.85);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.fd-bar__delivery a:hover { color: #fff; }

.fd-search {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 6px 0 16px;
  border-radius: 999px;
  background: #fff;
  color: #6b7280;
}
.fd-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font: 500 15px/1 inherit;
  color: #1a1a1a;
}
.fd-search input::placeholder { color: #9ca3af; }
.fd-search__go {
  flex-shrink: 0;
  height: 34px;
  padding: 0 18px;
  border: none;
  border-radius: 999px;
  background: #22c55e;
  color: #06240f;
  font: 700 13.5px/1 inherit;
  cursor: pointer;
}
.fd-search__go:hover { background: #16a34a; color: #fff; }

.fd-bar__account {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  white-space: nowrap;
}
.fd-bar__label { font-size: 13.5px; font-weight: 700; }
.fd-bar__account a {
  font-size: 13px;
  color: rgba(255,255,255,0.85);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.fd-bar__account a:hover { color: #fff; }

.fd-cart { position: relative; display: grid; place-items: center; color: #fff; }
.fd-cart__count {
  position: absolute;
  top: -6px;
  right: -8px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: #bbf451;
  color: #06240f;
  font-size: 11px;
  font-weight: 800;
}

/* ── Layout ──────────────────────────────────────────────────────── */
.fd-main { background: #fff; }
.fd-wrap { max-width: 1180px; margin: 0 auto; padding: 52px 28px 72px; }

.fd-h2 {
  margin: 0;
  font-size: clamp(1.25rem, 2.4vw, 1.75rem);
  font-weight: 800;
  letter-spacing: -0.025em;
  color: #1a1a1a;
}

.fdrow-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.fd-viewall {
  font-size: 14px;
  font-weight: 600;
  color: #16a34a;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.fd-viewall:hover { color: #1a6b3c; }

.fd-loading { margin: 0 0 40px; color: #9ca3af; font-size: 15px; }

/* ── Lead promo ──────────────────────────────────────────────────── */
.fd-promo {
  max-width: 1180px;
  margin: 0 auto;
  padding: 44px 28px 8px;
}
.fd-promo__title {
  margin: 0 0 10px;
  font-size: clamp(2rem, 4.6vw, 3.25rem);
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1.04;
  color: #1a1a1a;
}
.fd-promo__sub {
  margin: 0 0 8px;
  max-width: 640px;
  font-size: 16px;
  line-height: 1.6;
  color: #6b7280;
}
.fd-promo__link {
  display: inline-block;
  margin-bottom: 24px;
  font-size: 15px;
  font-weight: 600;
  color: #16a34a;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.fd-promo__link:hover { color: #1a6b3c; }

/* Wide banner image, as in the reference's full-bleed promo. */
.fd-promo__art {
  position: relative;
  height: 320px;
  border-radius: 12px;
  overflow: hidden;
  background: radial-gradient(120% 140% at 78% 10%, #5bbf8a 0, #1a6b3c 55%, #103f23);
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
}
/* Bottom-anchored and capped at the banner height: the source is a cutout
   figure, so anything over 100% crops the rider's head. */
.fd-promo__art img {
  height: 100%;
  max-height: 100%;
  width: auto;
  object-fit: contain;
  object-position: bottom;
  margin-right: 7%;
  align-self: flex-end;
}
/* Scrim so the script line stays legible over the lighter part of the
   gradient, and the left half doesn't read as dead space. */
.fd-promo__art::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(6,36,15,0.45) 0%, rgba(6,36,15,0.12) 42%, transparent 65%);
  z-index: 1;
}
.fd-promo__art-copy { z-index: 2; }
.fd-promo__art-copy {
  position: absolute;
  left: 40px;
  bottom: 40px;
  display: grid;
  gap: 8px;
}
.fd-promo__kicker {
  font-family: 'Caveat', cursive;
  font-size: 34px;
  font-weight: 700;
  color: #bbf451;
  line-height: 1;
}
.fd-promo__fee {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
}

/* ── Categories ──────────────────────────────────────────────────── */
.fd-cats-block { margin-bottom: 56px; }
.fd-cats { display: grid; grid-template-columns: repeat(8, 1fr); gap: 16px; }
.fd-cat { text-align: center; color: inherit; }
.fd-cat__art {
  aspect-ratio: 1 / 1;
  border-radius: 999px;
  overflow: hidden;
  background: #f7f8f7;
  margin-bottom: 9px;
  transition: transform 200ms cubic-bezier(0.16,1,0.3,1), box-shadow 200ms;
}
.fd-cat:hover .fd-cat__art { transform: translateY(-3px); box-shadow: 0 8px 18px rgba(15,23,42,0.10); }
.fd-cat__art img { width: 100%; height: 100%; object-fit: cover; display: block; }
.fd-cat span { font-size: 13.5px; font-weight: 600; color: #1a1a1a; }

/* ── Editorial ───────────────────────────────────────────────────── */
.fd-editorial {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 44px;
  align-items: center;
  margin-bottom: 56px;
  padding: 36px;
  border-radius: 14px;
  background: #f5f9f6;
}
.fd-editorial__copy {
  margin: 0 0 22px;
  font-size: 15px;
  line-height: 1.7;
  color: #4b5563;
}
.fd-editorial__art { display: flex; justify-content: center; }
.fd-editorial__art img { width: 100%; max-width: 300px; height: auto; }

.fd-btn {
  display: inline-block;
  padding: 12px 24px;
  border-radius: 999px;
  background: #1a1a1a;
  color: #fff;
  font-size: 14.5px;
  font-weight: 700;
}
.fd-btn:hover { background: #1a6b3c; }

/* ── Merchant strip ──────────────────────────────────────────────── */
.fd-merchant {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  flex-wrap: wrap;
  padding: 34px 36px;
  border-radius: 14px;
  background: #1a6b3c;
  color: #fff;
}
.fd-merchant__title { margin: 0 0 6px; font-size: 1.6rem; font-weight: 800; letter-spacing: -0.03em; }
.fd-merchant__sub { margin: 0; max-width: 560px; font-size: 14.5px; line-height: 1.6; color: rgba(255,255,255,0.85); }
.fd-merchant__cta {
  flex-shrink: 0;
  padding: 13px 30px;
  border-radius: 999px;
  background: #bbf451;
  color: #06240f;
  font-size: 15px;
  font-weight: 800;
}
.fd-merchant__cta:hover { background: #fff; }

/* ── Footer ──────────────────────────────────────────────────────── */
.fd-footer { background: #f7f8f7; padding: 0 28px 40px; }
.fd-totop {
  display: block;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto 40px;
  padding: 15px;
  border: 1px solid #dfe3e0;
  border-radius: 999px;
  background: #fff;
  color: #1a1a1a;
  font: 600 14.5px/1 inherit;
  cursor: pointer;
}
.fd-totop:hover { border-color: #1a6b3c; color: #1a6b3c; }

.fd-footer__cols {
  max-width: 1180px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
}
.fd-footer__cols > div { display: flex; flex-direction: column; gap: 11px; }
.fd-footer__title { margin: 0 0 3px; font-size: 14.5px; font-weight: 800; color: #1a1a1a; }
.fd-footer__cols a { font-size: 13.5px; color: #4b5563; }
.fd-footer__cols a:hover { color: #1a6b3c; text-decoration: underline; }
.fd-footer__note { margin: 0; font-size: 13px; line-height: 1.55; color: #6b7280; }

.fd-footer__bottom {
  max-width: 1180px;
  margin: 34px auto 0;
  padding-top: 22px;
  border-top: 1px solid #e5e7eb;
  font-size: 13px;
  color: #9ca3af;
}

/* ── Responsive ──────────────────────────────────────────────────── */
@media (max-width: 1080px) {
  .fd-bar__delivery { display: none; }
  .fd-cats { grid-template-columns: repeat(4, 1fr); }
  .fd-editorial { grid-template-columns: 1fr; gap: 28px; }
  .fd-footer__cols { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 760px) {
  .fd-bar {
    flex-wrap: wrap;
    gap: 12px;
    padding: 12px 16px;
    min-height: 0;
  }
  .fd-search { order: 3; flex-basis: 100%; height: 42px; }
  .fd-bar__account { display: none; }
  .fd-brand { margin-right: auto; }
  .fd-wrap { padding: 32px 16px 56px; }
  .fd-promo { padding: 28px 16px 8px; }
  .fd-promo__art { height: 240px; }
  .fd-promo__art-copy { left: 20px; bottom: 20px; }
  .fd-promo__kicker { font-size: 26px; }
  .fd-cats { grid-template-columns: repeat(3, 1fr); gap: 12px; }
  .fd-editorial { padding: 24px; }
  .fd-merchant { padding: 26px 22px; }
  .fd-footer { padding: 0 16px 32px; }
}
@media (max-width: 460px) {
  .fd-footer__cols { grid-template-columns: 1fr; }
  .fd-search__go { padding: 0 13px; }
}
</style>
