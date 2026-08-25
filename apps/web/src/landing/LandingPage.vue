<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { discountPercent, type Product } from '@pos/shared/index'
import { loadStorefrontCatalog } from '@pos/web/commerce/catalog'
import FdHeader from './FdHeader.vue'
import FdFooter from './FdFooter.vue'
import ProductRow from './ProductRow.vue'
import { serviceCategories } from './categories'
import { vReveal } from './reveal'

// Grocery-marketplace landing, modelled on the FreshDirect reference: a dark
// green utility bar with the search front and centre, then a stack of
// horizontally-scrolling product shelves interleaved with editorial blocks.
//
// Everything on the page is real catalog data. The /store entry has been
// removed, so nothing here navigates away any more: search filters the
// shelves in place, and every former /store link points at the shelves.
// The cart still collects items (localStorage) but has no checkout until the
// storefront comes back.

const products = ref<Product[]>([])
const categories = ref<{ id: string; name: string }[]>([])
const loading = ref(true)

// Search used to be a real navigation into /store?q=. With the store gone it
// filters the shelves on this page instead. Seeded from ?q= because searching
// from the about page is a real navigation back here.
function initialSearch(): string {
  try {
    return new URLSearchParams(window.location.search).get('q')?.trim() ?? ''
  } catch {
    return ''
  }
}

const activeSearch = ref(initialSearch())

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

const visibleProducts = computed(() => {
  const needle = activeSearch.value.toLowerCase()
  if (!needle) return products.value
  return products.value.filter((p) => p.name.toLowerCase().includes(needle))
})

/** Shelves are v-if'd on non-empty, so a thin catalog just shows fewer rows. */
const popular = computed(() => visibleProducts.value.slice(0, 12))

const deals = computed(() => visibleProducts.value.filter((p) => discountPercent(p) !== null))

const cheapest = computed(() =>
  [...visibleProducts.value].sort((a, b) => a.priceCents - b.priceCents).slice(0, 12),
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


const header = ref<InstanceType<typeof FdHeader> | null>(null)

function onSearch(term: string) {
  activeSearch.value = term
  document.getElementById('shop')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function clearSearch() {
  activeSearch.value = ''
  header.value?.clear()
}
</script>

<template>
  <div class="landing fd">

    <FdHeader ref="header" @search="onSearch" />

    <main class="fd-main">

      <div class="fd-wrap">

        <!-- ── Shop by category ──────────────────────────────────────── -->
        <section id="shop" class="fd-cats-block">
          <div class="fdrow-head">
            <h2 class="fd-h2">Shop by category</h2>
          </div>
          <div class="fd-cats">
            <a
              v-for="(cat, i) in serviceCategories"
              :key="cat.slug"
              v-reveal="i * 30"
              href="#shop"
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

        <!-- Without this the shelves would simply vanish on a no-match search,
             leaving a blank page with no explanation. -->
        <p v-else-if="activeSearch" class="fd-searchnote">
          <template v-if="visibleProducts.length > 0">
            {{ visibleProducts.length }} match{{ visibleProducts.length === 1 ? '' : 'es' }} for
            &ldquo;{{ activeSearch }}&rdquo;
          </template>
          <template v-else>Nothing matches &ldquo;{{ activeSearch }}&rdquo;.</template>
          <button type="button" class="fd-searchnote__clear" @click="clearSearch">Clear search</button>
        </p>

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

    <FdFooter />

  </div>
</template>

<style scoped>
.fd-searchnote {
  margin: 0 0 4px;
  color: #4a5b52;
  font-size: 14px;
  font-weight: 600;
}

.fd-searchnote__clear {
  margin-left: 10px;
  border: none;
  background: none;
  padding: 0;
  color: #1a6b3c;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

/* ── Layout ──────────────────────────────────────────────────────── */
.fd-main { background: #fff; }
.fd-wrap { padding: 52px var(--fd-gutter) 72px; }

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

/* ── Responsive ──────────────────────────────────────────────────── */
@media (max-width: 1080px) {
  .fd-cats { grid-template-columns: repeat(4, 1fr); }
  .fd-editorial { grid-template-columns: 1fr; gap: 28px; }
}
@media (max-width: 760px) {
  .fd-wrap { padding: 32px var(--fd-gutter) 56px; }
  .fd-cats { grid-template-columns: repeat(3, 1fr); gap: 12px; }
  .fd-editorial { padding: 24px; }
  .fd-merchant { padding: 26px 22px; }
}
</style>
