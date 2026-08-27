<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { discountPercent } from '@pos/shared/index'
import { useStockedCategories, useStorefrontCatalog } from '@pos/web/commerce/catalog'
import FdHeader from './FdHeader.vue'
import FdHero from './FdHero.vue'
import FdFooter from './FdFooter.vue'
import ProductRow from './ProductRow.vue'
import ProductGrid from './ProductGrid.vue'
import ProductDetail from './ProductDetail.vue'

// Grocery-marketplace landing, modelled on the FreshDirect reference: a dark
// green utility bar with the search front and centre, then a stack of
// horizontally-scrolling product shelves interleaved with editorial blocks.
//
// The page has three faces, and the URL says which one is showing:
//   - nothing picked: the front page — hero, curated shelves, editorial;
//   - ?category=: that aisle's full product listing, as a grid;
//   - ?product=: one product at full size, with a quantity stepper.
// Everything on all three is real catalog data, filtered in place. The /store
// entry has been removed, so nothing here navigates away: search, aisle and
// product all stay on this document. The cart still collects items
// (localStorage) but has no checkout until the storefront comes back.

/** Rows of the category grid are 6-8 cards wide, so this is 3-4 rows a click. */
const PAGE_SIZE = 24

const catalog = useStorefrontCatalog()
const stockedCategories = useStockedCategories()

const loading = computed(() => catalog.loading)
const products = computed(() => catalog.products.filter((p) => !p.outOfStock))

// Search, aisle and product all live in the URL: arriving from the about page
// is a real navigation back here (?q= from its search box, ?category= from its
// nav), a product card is a real link anyone can copy or open in a new tab, and
// keeping all three there afterwards is what makes Back walk the browsing back
// out rather than leaving the site.
function readUrl(): { q: string; category: string; product: string } {
  try {
    const params = new URLSearchParams(window.location.search)
    return {
      q: params.get('q')?.trim() ?? '',
      category: params.get('category')?.trim() ?? '',
      product: params.get('product')?.trim() ?? '',
    }
  } catch {
    return { q: '', category: '', product: '' }
  }
}

const initialUrl = readUrl()
const activeSearch = ref(initialUrl.q)
const activeCategory = ref(initialUrl.category)
const activeProduct = ref(initialUrl.product)
const shownCount = ref(PAGE_SIZE)

function syncUrl() {
  const params = new URLSearchParams()
  if (activeCategory.value) params.set('category', activeCategory.value)
  if (activeSearch.value) params.set('q', activeSearch.value)
  if (activeProduct.value) params.set('product', activeProduct.value)
  const query = params.toString()
  window.history.pushState({}, '', query ? `${window.location.pathname}?${query}` : window.location.pathname)
}

function applyUrl() {
  const next = readUrl()
  activeSearch.value = next.q
  activeCategory.value = next.category
  activeProduct.value = next.product
  shownCount.value = PAGE_SIZE
  if (!next.q) header.value?.clear()
}

onMounted(() => window.addEventListener('popstate', applyUrl))
onBeforeUnmount(() => window.removeEventListener('popstate', applyUrl))

const visibleProducts = computed(() => {
  const needle = activeSearch.value.toLowerCase()
  if (!needle) return products.value
  return products.value.filter((p) => p.name.toLowerCase().includes(needle))
})

/** The picked category, once the catalog is in — null while it is still loading. */
const activeCategoryInfo = computed(
  () => stockedCategories.value.find((c) => c.id === activeCategory.value) ?? null,
)

/**
 * Driven by the raw ids, not by the resolved records: the listing and the
 * detail have to be on screen from the first frame of a ?category= or
 * ?product= arrival, or the front page would flash its hero and shelves for as
 * long as the catalog takes to load.
 */
const browsingProduct = computed(() => activeProduct.value !== '')
const browsingCategory = computed(() => !browsingProduct.value && activeCategory.value !== '')

/**
 * Looked up in the unfiltered catalog, unlike everything else on the page: a
 * link to something that has since sold out should still open and say so,
 * rather than reading as a dead link.
 */
const activeProductInfo = computed(
  () => catalog.products.find((p) => p.id === activeProduct.value) ?? null,
)

const activeProductCategory = computed(() => {
  const categoryId = activeProductInfo.value?.categoryId
  if (!categoryId) return null
  return catalog.categories.find((c) => c.id === categoryId) ?? null
})

/** On a product, the nav lights that product's own aisle — you are still in it. */
const navCategory = computed(() =>
  browsingProduct.value ? activeProductInfo.value?.categoryId ?? '' : activeCategory.value,
)

/** The rest of the aisle, so the detail is a place to keep shopping from. */
const relatedProducts = computed(() => {
  const current = activeProductInfo.value
  if (!current) return []
  return products.value.filter((p) => p.categoryId === current.categoryId && p.id !== current.id).slice(0, 12)
})

/** Falls back to the id so an unknown ?category= still names what it looked for. */
const categoryTitle = computed(
  () => activeCategoryInfo.value?.name ?? activeCategory.value.replace(/-/g, ' '),
)

/** Search narrows within the aisle rather than dropping you out of it. */
const scopedProducts = computed(() =>
  browsingCategory.value
    ? visibleProducts.value.filter((p) => p.categoryId === activeCategory.value)
    : visibleProducts.value,
)

const pagedProducts = computed(() => scopedProducts.value.slice(0, shownCount.value))
const hasMore = computed(() => scopedProducts.value.length > shownCount.value)

/** Shelves are v-if'd on non-empty, so a thin catalog just shows fewer rows. */
const popular = computed(() => visibleProducts.value.slice(0, 12))

const deals = computed(() => visibleProducts.value.filter((p) => discountPercent(p) !== null))

/**
 * The shelf is titled "under ₱100", so it has to filter on that and not just
 * take the twelve cheapest — a shop whose cheapest line is ₱120 would
 * otherwise have the row advertise it as under ₱100.
 */
const CHEAP_MAX_CENTS = 10000

const cheapest = computed(() =>
  visibleProducts.value
    .filter((p) => p.priceCents < CHEAP_MAX_CENTS)
    .sort((a, b) => a.priceCents - b.priceCents)
    .slice(0, 12),
)

/** Products from the largest category, for the editorial block. */
const featureCategory = computed(() => {
  if (catalog.categories.length === 0) return null
  const counts = catalog.categories.map((c) => ({
    category: c,
    items: products.value.filter((p) => p.categoryId === c.id),
  }))
  return counts.sort((a, b) => b.items.length - a.items.length)[0] ?? null
})

const header = ref<InstanceType<typeof FdHeader> | null>(null)

/** Waits a tick: picking a category swaps the hero out from above the anchor. */
async function scrollToShop() {
  await nextTick()
  document.getElementById('shop')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onSearch(term: string) {
  activeSearch.value = term
  // Searching is a request for results, so it steps out of a single product.
  activeProduct.value = ''
  shownCount.value = PAGE_SIZE
  syncUrl()
  void scrollToShop()
}

/** Clicking the aisle you are already in steps back out to the front page. */
function onCategory(categoryId: string) {
  const leavingProduct = browsingProduct.value
  activeProduct.value = ''
  // Picking the aisle you were already in, from a product, means "back to the
  // listing" rather than "back to the front page".
  activeCategory.value = !leavingProduct && activeCategory.value === categoryId ? '' : categoryId
  shownCount.value = PAGE_SIZE
  syncUrl()
  void scrollToShop()
}

function openProduct(productId: string) {
  activeProduct.value = productId
  syncUrl()
  void scrollToShop()
}

/** The detail's breadcrumb root: out of the product and out of the aisle. */
function backToEverything() {
  activeProduct.value = ''
  activeCategory.value = ''
  shownCount.value = PAGE_SIZE
  syncUrl()
  void scrollToShop()
}

function clearCategory() {
  activeCategory.value = ''
  shownCount.value = PAGE_SIZE
  syncUrl()
}

function clearSearch() {
  activeSearch.value = ''
  shownCount.value = PAGE_SIZE
  header.value?.clear()
  syncUrl()
}
</script>

<template>
  <div class="landing fd">

    <FdHeader
      ref="header"
      :active-category="navCategory"
      @search="onSearch"
      @category="onCategory"
    />

    <main class="fd-main">

      <div class="fd-wrap">

        <!-- The hero sells the service; inside an aisle or on a product the
             goods are the point, so it stands down. -->
        <FdHero v-if="!browsingCategory && !browsingProduct" />

        <!-- The anchor every "shop" link, the search submit and the category
             nav scroll to. It used to sit on a "Shop by category" block that
             listed a fixed taxonomy, linked to the same place, and filtered
             nothing — the block went, the nav took over the job, and the id
             moved here onto the products people were being sent to. -->
        <div id="shop">

          <!-- ── One product ──────────────────────────────────────────── -->
          <template v-if="browsingProduct">
            <ProductDetail
              v-if="activeProductInfo"
              :product="activeProductInfo"
              :category="activeProductCategory"
              :related="relatedProducts"
              @back="backToEverything"
              @category="onCategory"
              @select="openProduct"
            />

            <p v-else-if="loading" class="fd-loading">Loading this product…</p>

            <p v-else class="fd-cat__empty">
              That product isn't on the shelf any more.
              <button type="button" class="fd-linkbtn" @click="backToEverything">Browse everything</button>
            </p>
          </template>

          <!-- ── One aisle ────────────────────────────────────────────── -->
          <section v-else-if="browsingCategory" class="fd-cat">
            <nav class="fd-cat__crumbs" aria-label="Breadcrumb">
              <a href="#shop" @click.prevent="clearCategory">All categories</a>
              <span aria-hidden="true">›</span>
              <span class="fd-cat__here">{{ categoryTitle }}</span>
            </nav>

            <div class="fd-cat__head">
              <h1 class="fd-cat__title">{{ categoryTitle }}</h1>
              <p v-if="!loading" class="fd-cat__count">
                {{ scopedProducts.length }} item{{ scopedProducts.length === 1 ? '' : 's' }}<template
                  v-if="activeSearch"
                > matching &ldquo;{{ activeSearch }}&rdquo;</template>
              </p>
            </div>

            <p v-if="loading" class="fd-loading">Loading today's catalog…</p>

            <template v-else-if="scopedProducts.length > 0">
              <ProductGrid :products="pagedProducts" @select="openProduct" />

              <div v-if="hasMore" class="fd-cat__more">
                <button type="button" class="fd-btn" @click="shownCount += PAGE_SIZE">
                  Show more
                </button>
              </div>
            </template>

            <p v-else class="fd-cat__empty">
              <template v-if="activeSearch">
                Nothing in {{ categoryTitle }} matches &ldquo;{{ activeSearch }}&rdquo;.
                <button type="button" class="fd-linkbtn" @click="clearSearch">Clear search</button>
              </template>
              <template v-else>
                Nothing in {{ categoryTitle }} today.
                <button type="button" class="fd-linkbtn" @click="clearCategory">Browse everything</button>
              </template>
            </p>
          </section>

          <!-- ── The front page ───────────────────────────────────────── -->
          <template v-else>

            <p v-if="loading" class="fd-loading">Loading today's catalog…</p>

            <!-- Without this the shelves would simply vanish on a no-match search,
                 leaving a blank page with no explanation. -->
            <p v-else-if="activeSearch" class="fd-searchnote">
              <template v-if="visibleProducts.length > 0">
                {{ visibleProducts.length }} match{{ visibleProducts.length === 1 ? '' : 'es' }} for
                &ldquo;{{ activeSearch }}&rdquo;
              </template>
              <template v-else>Nothing matches &ldquo;{{ activeSearch }}&rdquo;.</template>
              <button type="button" class="fd-linkbtn" @click="clearSearch">Clear search</button>
            </p>

            <ProductRow title="Popular now" :products="popular" @select="openProduct" />

            <ProductRow
              title="Fresh deals: this week's best for less"
              blurb="Marked down by the shops themselves — no commission taken out, so the discount reaches you whole."
              :products="deals"
              @select="openProduct"
            />

            <!-- ── Editorial ─────────────────────────────────────────── -->
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
              @select="openProduct"
            />

          </template>

        </div>

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

/* A button that reads as a link — clearing a search or an aisle is a state
   change on this page, never a navigation. */
.fd-linkbtn {
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

/* The header is sticky and two rows tall (162px measured, 198px once the bar
   wraps below 760px), so an un-offset scroll parks the heading underneath it. */
#shop { scroll-margin-top: 178px; }

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

/* ── Category listing ────────────────────────────────────────────── */
.fd-cat { margin-bottom: 56px; }

.fd-cat__crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13.5px;
  color: #9ca3af;
}
.fd-cat__crumbs a { color: #1a6b3c; font-weight: 600; text-decoration: underline; text-underline-offset: 3px; }
.fd-cat__crumbs a:hover { color: #16a34a; }
.fd-cat__here { color: #4b5563; font-weight: 600; text-transform: capitalize; }

.fd-cat__head {
  display: flex;
  align-items: baseline;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 26px;
  padding-bottom: 18px;
  border-bottom: 1px solid #e7eae8;
}

.fd-cat__title {
  margin: 0;
  font-size: clamp(1.6rem, 3.4vw, 2.25rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #1a1a1a;
  text-transform: capitalize;
}

.fd-cat__count { margin: 0; font-size: 14px; color: #6b7280; }

.fd-cat__more { display: flex; justify-content: center; margin-top: 40px; }

.fd-cat__empty { margin: 0; color: #6b7280; font-size: 15px; }

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
  border: none;
  border-radius: 999px;
  background: #1a1a1a;
  color: #fff;
  font: 700 14.5px/1.2 inherit;
  cursor: pointer;
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
  .fd-editorial { grid-template-columns: 1fr; gap: 28px; }
}
@media (max-width: 760px) {
  .fd-wrap { padding: 32px var(--fd-gutter) 56px; }
  .fd-editorial { padding: 24px; }
  .fd-merchant { padding: 26px 22px; }
  #shop { scroll-margin-top: 214px; }
}
</style>
