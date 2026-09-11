<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { discountPercent } from '@pos/shared/index'
import { retryStorefrontCatalog, useStockedCategories, useStorefrontCatalog } from '@pos/web/commerce/catalog'
import FdHeader from './FdHeader.vue'
import DeliveryBand from './DeliveryBand.vue'
import FdHero from './FdHero.vue'
import CategoryTiles from './CategoryTiles.vue'
import HowItWorks from './HowItWorks.vue'
import FdFooter from './FdFooter.vue'
import ProductRow from './ProductRow.vue'
import CategoryListing from './CategoryListing.vue'
import HighlandBanner from './HighlandBanner.vue'
import ProductDetail from './ProductDetail.vue'
import PartnerDialog from './PartnerDialog.vue'
import ShopDirectory from './ShopDirectory.vue'
import StoriesBand from './StoriesBand.vue'
import WhyOmaykan from './WhyOmaykan.vue'
import { ALL_AISLES, emptyListing, readListing, townOf, writeListing, type ListingFilters } from './listing'

// The storefront, in the highland redesign: a forest-green bar, a full-bleed
// photographic hero, the aisles as picture tiles, then shops and shelves on a
// cream ground, broken once by a woven-edged band about the people behind the
// counters.
//
// The page has three faces, and the URL says which one is showing:
//   - nothing picked: the front page — hero, tiles, shops, shelves, editorial;
//   - ?category=: the listing — one aisle, several, or `all` — with its
//     filters, sort and page alongside it in the URL (see listing.ts);
//   - ?product=: one product at full size, with a quantity stepper.
// Everything on all three is real catalog data, filtered in place, so nothing
// here navigates away: search, aisle and product all stay on this document.

const catalog = useStorefrontCatalog()
const stockedCategories = useStockedCategories()

const loading = computed(() => catalog.loading)
const catalogError = computed(() => catalog.error)

function retryCatalog() {
  retryStorefrontCatalog()
}
const products = computed(() => catalog.products.filter((p) => !p.outOfStock))

// Search, listing and product all live in the URL: arriving from the about
// page is a real navigation back here (?q= from its search box, ?category=
// from its menu), a product card is a real link anyone can copy or open in a
// new tab, and keeping all of it there afterwards is what makes Back walk the
// browsing back out rather than leaving the site.
function readUrl(): { q: string; product: string; listing: ListingFilters | null } {
  try {
    const params = new URLSearchParams(window.location.search)
    return {
      q: params.get('q')?.trim() ?? '',
      product: params.get('product')?.trim() ?? '',
      listing: readListing(params),
    }
  } catch {
    return { q: '', product: '', listing: null }
  }
}

const initialUrl = readUrl()
const activeSearch = ref(initialUrl.q)
const activeProduct = ref(initialUrl.product)
/** Null on the front page; the listing's aisles, filters, sort and page otherwise. */
const listing = ref<ListingFilters | null>(initialUrl.listing)

function syncUrl() {
  const params = new URLSearchParams()
  if (listing.value) writeListing(params, listing.value)
  if (activeSearch.value) params.set('q', activeSearch.value)
  if (activeProduct.value) params.set('product', activeProduct.value)
  const query = params.toString()
  window.history.pushState({}, '', query ? `${window.location.pathname}?${query}` : window.location.pathname)
}

function applyUrl() {
  const next = readUrl()
  activeSearch.value = next.q
  activeProduct.value = next.product
  listing.value = next.listing
  if (!next.q) header.value?.clear()
}

onMounted(() => window.addEventListener('popstate', applyUrl))
onBeforeUnmount(() => window.removeEventListener('popstate', applyUrl))

const visibleProducts = computed(() => {
  const needle = activeSearch.value.toLowerCase()
  if (!needle) return products.value
  return products.value.filter((p) => p.name.toLowerCase().includes(needle))
})

/**
 * Driven by the raw URL state, not by the resolved records: the listing and
 * the detail have to be on screen from the first frame of a ?category= or
 * ?product= arrival, or the front page would flash its hero and shelves for as
 * long as the catalog takes to load.
 */
const browsingProduct = computed(() => activeProduct.value !== '')
const browsingListing = computed(() => !browsingProduct.value && listing.value !== null)
const onFrontPage = computed(() => !browsingListing.value && !browsingProduct.value)

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

/**
 * The aisle the header's menu lights: on a product, that product's own aisle —
 * you are still in it; on the listing, "All products" or the one aisle
 * showing. Several aisles at once is not one menu item, so it lights none.
 */
const navCategory = computed(() => {
  if (browsingProduct.value) return activeProductInfo.value?.categoryId ?? ''
  const aisles = listing.value?.categories
  if (!aisles) return ''
  if (aisles.length === 0) return ALL_AISLES
  return aisles.length === 1 ? aisles[0] : ''
})

/** The rest of the aisle, so the detail is a place to keep shopping from. */
const relatedProducts = computed(() => {
  const current = activeProductInfo.value
  if (!current) return []
  return products.value.filter((p) => p.categoryId === current.categoryId && p.id !== current.id).slice(0, 12)
})

/** The town on the shop's sign: every product on the listing comes off its shelf. */
const shopTown = computed(() => townOf(catalog.shop?.address))

/**
 * Every shelf on this page is one shop's stock — the catalog is fetched for a
 * single tenant — so the shop's own name is the truest thing a shelf can be
 * titled with. The redesign calls this row "Featured Products"; nothing here
 * features anything, so it keeps the title that is true.
 */
const shelfTitle = computed(() =>
  catalog.shop?.name ? `On the shelves at ${catalog.shop.name}` : 'On the shelves now',
)

/**
 * The first twelve, not the best-selling twelve: nothing here counts orders.
 * It was titled "Popular near you", which claimed both a ranking and a
 * locality this list does not have.
 */
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

/** What a merchant actually gets — each one is a thing that already ships. */
const sellerBenefits = [
  'Your own online storefront',
  'Product and inventory management',
  'Order management',
  'Local customer discovery',
  'No marketplace commission',
]

const header = ref<InstanceType<typeof FdHeader> | null>(null)

/** Waits a tick: picking a category swaps the hero out from above the anchor. */
async function scrollToShop() {
  await nextTick()
  document.getElementById('shop')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/**
 * The listing opens under its banner and its row of aisles, so arriving there
 * goes to the top of the page; the #shop anchor would scroll both out of sight.
 */
function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/**
 * Only when the results have scrolled up out of view: a filter applied with
 * the grid already on screen should not move the page under the pointer.
 */
async function scrollToResults() {
  await nextTick()
  const results = document.getElementById('listing-results')
  if (results && results.getBoundingClientRect().top < 100) {
    results.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

function onSearch(term: string) {
  activeSearch.value = term
  // Searching is a request for results, so it steps out of a single product.
  activeProduct.value = ''
  // Search narrows within the listing; a page number from before it could
  // point past the end of what is left.
  if (listing.value) listing.value = { ...listing.value, page: 1 }
  syncUrl()
  void scrollToShop()
}

/**
 * An aisle from the header's menu or a product's breadcrumb: that aisle alone,
 * with the filters starting fresh. Picking the aisle you are already in steps
 * back out to the front page.
 */
function onCategory(categoryId: string) {
  const leavingProduct = browsingProduct.value
  activeProduct.value = ''
  const target = categoryId === ALL_AISLES ? [] : [categoryId]
  const current = listing.value?.categories
  // Picking the aisle you were already in, from a product, means "back to the
  // listing" rather than "back to the front page".
  const alreadyHere = !leavingProduct
    && current !== undefined
    && current.length === target.length
    && current.every((id, i) => id === target[i])
  listing.value = alreadyHere ? null : emptyListing(target)
  syncUrl()
  if (listing.value) scrollToTop()
  else void scrollToShop()
}

/** The listing's own controls: its aisle tiles, filters, sort and pages. */
function onListingChange(next: ListingFilters) {
  listing.value = next
  syncUrl()
  void scrollToResults()
}

function openProduct(productId: string) {
  activeProduct.value = productId
  syncUrl()
  void scrollToShop()
}

/** The detail's breadcrumb root: out of the product and out of the listing. */
function backToEverything() {
  activeProduct.value = ''
  listing.value = null
  syncUrl()
  void scrollToShop()
}

function clearSearch() {
  activeSearch.value = ''
  if (listing.value) listing.value = { ...listing.value, page: 1 }
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
      @product="openProduct"
      @category="onCategory"
    />

    <main class="fd-main">

      <!-- Full-bleed, above the padded column. The hero sells the service; the
           listing gets the redesign's strip of highland instead, and on a
           product the goods are the point, so both stand down. -->
      <FdHero v-if="onFrontPage" />
      <HighlandBanner v-else-if="browsingListing" />

      <div class="fd-wrap">

        <!-- Directly under the banner, so "what is this?" and "is it near me?"
             are answered in the same glance. It is also the only delivery
             control on a phone. -->
        <DeliveryBand v-if="onFrontPage" />

        <!-- The aisles, straight after the hero as the redesign has them. Not
             while searching: they are not an answer to the query. -->
        <CategoryTiles v-if="onFrontPage && !activeSearch" @category="onCategory" />

        <!-- The anchor every "shop" link, the search submit and the category
             menu scroll to. -->
        <div id="shop">

          <!-- ── Shops, then goods ─────────────────────────────────────
               The merchants come before any shelf: this is a marketplace of
               counters, and which shop you are buying from is the choice that
               frames every other one.

               While searching it answers the same query — "SMJ Grocery" has
               to be able to return a shop — and hides itself when the term
               matches no shop. -->
          <ShopDirectory v-if="onFrontPage" :query="activeSearch" />

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

            <p v-else class="fd-missing">
              That product isn't on the shelf any more.
              <button type="button" class="fd-linkbtn" @click="backToEverything">Browse everything</button>
            </p>
          </template>

          <!-- ── The listing ──────────────────────────────────────────── -->
          <CategoryListing
            v-else-if="browsingListing && listing"
            :filters="listing"
            :products="visibleProducts"
            :categories="stockedCategories"
            :loading="loading"
            :error="catalogError"
            :search="activeSearch"
            :place="shopTown"
            @change="onListingChange"
            @select="openProduct"
            @retry="retryCatalog"
            @clear-search="clearSearch"
          />

          <!-- ── The front page ───────────────────────────────────────── -->
          <template v-else>

            <p v-if="loading" class="fd-loading">Loading today's catalog…</p>

            <!-- A catalog that failed to load would otherwise drop every shelf
                 and leave the front page blank, with nothing said. -->
            <p v-else-if="catalogError" class="fd-catalog-error">
              {{ catalogError }}
              <button type="button" class="fd-linkbtn" @click="retryCatalog">Try again</button>
            </p>

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

            <!-- After the shops, before the first shelf: a first-time visitor
                 sees that the market is real, then learns how to use it. -->
            <HowItWorks v-if="!activeSearch" />

            <ProductRow :title="shelfTitle" :products="popular" @select="openProduct" />

            <!-- The redesign's band, right after the first shelf. Not while
                 searching: it is editorial, and a results page is not the
                 place for it. -->
            <StoriesBand v-if="!activeSearch" />

            <WhyOmaykan />

            <ProductRow
              anchor="deals"
              title="Marked down at the counter"
              blurb="The shops set these markdowns themselves, and Omaykan takes no percentage of a sale — so the discount reaches you whole."
              :products="deals"
              @select="openProduct"
            />

            <!-- ── Seller acquisition ────────────────────────────────
                 Asks the question directly and answers what you get. -->
            <section class="fd-seller">
              <div class="fd-seller__body">
                <p class="sf-eyebrow fd-seller__eyebrow">For shop owners</p>
                <h2 class="fd-seller__title">Own a store in Baguio or La Trinidad?</h2>

                <p class="fd-seller__lead">
                  Put your products online and start accepting local orders through Omaykan.
                </p>

                <p class="fd-seller__terms">
                  No sales commission. Keep 100% of your product sales.
                </p>

                <a href="/signup" class="fd-seller__cta">List your store</a>
                <p class="fd-seller__note">Free while we're in early access.</p>

                <ul class="fd-seller__list">
                  <li v-for="item in sellerBenefits" :key="item">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>

              <div class="fd-seller__art">
                <img src="/delivery/fruit-basket.webp" alt="" width="900" height="1125" loading="lazy" />
              </div>
            </section>

            <ProductRow
              title="Everyday essentials under ₱100"
              :products="cheapest"
              @select="openProduct"
            />

          </template>

        </div>

      </div>
    </main>

    <FdFooter />

    <!-- Teleports to body and opens itself once per visitor. -->
    <PartnerDialog />

  </div>
</template>

<style scoped>
.fd-searchnote {
  margin: 0 0 4px;
  color: var(--sf-muted);
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
  color: var(--sf-clay);
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

/* ── Layout ──────────────────────────────────────────────────────── */
.fd-main { background: var(--sf-cream); }
.fd-wrap { padding: 40px var(--fd-gutter) 72px; }

/* The header is sticky and one row tall on a desktop (74px), three once it
   wraps on a phone, so an un-offset scroll parks the heading underneath it. */
#shop { scroll-margin-top: 96px; }

.fd-loading { margin: 0 0 40px; color: var(--sf-faint); font-size: 15px; }
.fd-catalog-error { margin: 0 0 40px; color: #b3261e; font-size: 15px; }
.fd-missing { margin: 0 0 40px; color: var(--sf-muted); font-size: 15px; }

/* ── Seller acquisition ──────────────────────────────────────────── */
.fd-seller {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 44px;
  align-items: center;
  margin-bottom: 56px;
  padding: 40px;
  border-radius: 10px;
  background: var(--sf-sand);
}

.fd-seller__eyebrow { color: var(--sf-clay); }

.fd-seller__title {
  margin: 0 0 12px;
  font-family: var(--sf-serif);
  font-size: clamp(1.5rem, 2.3vw, 2rem);
  font-weight: 700;
  line-height: 1.15;
  color: var(--sf-ink);
  text-wrap: balance;
}

.fd-seller__lead {
  margin: 0 0 12px;
  max-width: 46ch;
  font-size: 15.5px;
  line-height: 1.6;
  color: var(--sf-muted);
}

/* The one line a shop owner is deciding on. */
.fd-seller__terms {
  margin: 0 0 22px;
  font-size: 15.5px;
  font-weight: 800;
  line-height: 1.5;
  color: var(--sf-forest);
}

.fd-seller__cta {
  display: inline-block;
  padding: 13px 30px;
  border-radius: 6px;
  background: var(--sf-clay);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}
.fd-seller__cta:hover { background: var(--sf-clay-deep); }
.fd-seller__cta:focus-visible { outline: 2px solid var(--sf-forest); outline-offset: 3px; }

.fd-seller__note {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--sf-muted);
}

.fd-seller__list {
  margin: 24px 0 0;
  padding: 22px 0 0;
  border-top: 1px solid var(--sf-sand-deep);
  list-style: none;
  display: grid;
  gap: 10px;
}
.fd-seller__list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14.5px;
  font-weight: 600;
  color: var(--sf-ink);
}
.fd-seller__list svg { flex-shrink: 0; color: var(--sf-forest); }

.fd-seller__art { display: flex; justify-content: center; }
.fd-seller__art img { width: 100%; max-width: 300px; height: auto; }

/* ── Responsive ──────────────────────────────────────────────────── */
@media (max-width: 1080px) {
  /* The art goes under the pitch rather than beside it; the checklist keeps
     its own column so it does not become one long ladder on a tablet. */
  .fd-seller { grid-template-columns: 1fr; gap: 28px; }
  .fd-seller__list { grid-template-columns: 1fr 1fr; column-gap: 24px; }
}
@media (max-width: 980px) {
  #shop { scroll-margin-top: 180px; }
}
@media (max-width: 760px) {
  .fd-wrap { padding: 28px var(--fd-gutter) 56px; }
  .fd-seller { padding: 24px; }
  .fd-seller__list { grid-template-columns: 1fr; }
  .fd-seller__cta { display: block; text-align: center; }
}
</style>
