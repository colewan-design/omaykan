<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Bike,
  Check,
  HandCoins,
  HeartHandshake,
  LayoutGrid,
  MapPin,
  Search,
  Share2,
  ShoppingBag,
  ShoppingBasket,
  Store,
  X,
} from '@lucide/vue'
import { formatCurrency, storefrontUrl, type Product } from '@pos/shared/index'
import { categoryIcon } from '@pos/core/utils/categoryIcons'
import type { StoreSummary } from '@pos/web/commerce/api'
import { useStorefrontCart, type CartLine } from '@pos/web/commerce/cart'
import { retryStorefrontCatalog, useStockedCategories, useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { DELIVERY_BASE_FEE_CENTS, haversineKm, quoteDelivery } from '@pos/web/commerce/delivery'
import { BASKET_PARAM, encodeBasketHandoff } from '@pos/web/commerce/basketHandoff'
import { useDeliveryLocation } from '@pos/web/commerce/deliveryLocation'
import { SHOP_ROOT_DOMAIN, mainSiteOrigin } from '@pos/web/commerce/shopDomain'
import FdFooter from '@pos/web/landing/FdFooter.vue'
import FdHeader from '@pos/web/landing/FdHeader.vue'
import MenuCard from './MenuCard.vue'
import ShopCart from './ShopCart.vue'

// A shop's own page: the link a seller shares, at <slug>.omaykan.com (or
// /shop/<slug> on a build with no shop domain).
//
// The landing page with `?shop=` is the marketplace wearing one shop's shelf;
// this is the shop itself — its name and photo at the top, its menu in its own
// aisles, and the basket open beside it so a customer arriving from a
// Facebook post can order without learning the rest of the site first.
// Checkout is still /cart, so there is one checkout to keep right. On a
// subdomain that is the main site's /cart, with the basket handed over in the
// link — see commerce/basketHandoff.ts.

const props = defineProps<{
  slug: string
  store: StoreSummary | null
  directoryFailed: boolean
}>()

const ALL = 'all'
/** How much of an aisle the "All items" view shows before "See all". */
const PREVIEW_COUNT = 6

const catalog = useStorefrontCatalog()
const categories = useStockedCategories()
const cart = useStorefrontCart()
const delivery = useDeliveryLocation()

// ── Who this is ───────────────────────────────────────────────────────────

const shopName = computed(() => catalog.shop?.name || props.store?.name || 'This shop')
const shopKind = computed(() => catalog.shop?.businessTypeLabel || props.store?.businessTypeLabel || '')
const shopAddress = computed(() => catalog.shop?.address || props.store?.address || '')
/** The owner's own upload wins; the directory's is a photo off the shelf. */
const shopPhoto = computed(() => catalog.shop?.imageUrl || props.store?.imageUrl || '')
const coverPhoto = computed(() => shopPhoto.value || '/storefront/listing-highland.webp')

const initials = computed(() =>
  shopName.value
    .split(/\s+/)
    .filter((word) => /^[\p{L}\p{N}]/u.test(word))
    .slice(0, 2)
    .map((word) => word[0]!.toUpperCase())
    .join(''),
)

watch(shopName, (name) => (document.title = `${name} — Omaykan`), { immediate: true })

// ── Sharing ───────────────────────────────────────────────────────────────

/** '' on the main site; `https://omaykan.com` on a shop's subdomain. */
const mainSite = mainSiteOrigin()

const shareUrl = computed(() =>
  storefrontUrl(props.slug, { rootDomain: SHOP_ROOT_DOMAIN, origin: mainSite || window.location.origin }),
)
const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | null = null

async function share() {
  // The phone's own share sheet where there is one: that is where Messenger
  // and Viber live. A desktop gets the link on its clipboard instead.
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title: shopName.value, text: `Order from ${shopName.value} on Omaykan`, url: shareUrl.value })
      return
    } catch (error) {
      if ((error as DOMException)?.name === 'AbortError') return
    }
  }
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    copied.value = true
    if (copiedTimer) clearTimeout(copiedTimer)
    copiedTimer = setTimeout(() => (copied.value = false), 2000)
  } catch {
    // Clipboard refused. The link is the page's own address bar.
  }
}

// ── The delivery fee ──────────────────────────────────────────────────────

const deliveryQuote = computed(() => {
  const { lat, lng } = delivery.location.value
  const store = props.store
  if (lat === null || lng === null || store?.lat == null || store.lng == null) return null
  return quoteDelivery(haversineKm(store.lat, store.lng, lat, lng))
})

const deliveryFeeCents = computed(() => deliveryQuote.value?.feeCents ?? null)
const deliverable = computed(() => deliveryQuote.value?.serviceable ?? true)

const deliveryLabel = computed(() => {
  const quote = deliveryQuote.value
  if (quote === null) return `Delivery from ${formatCurrency(DELIVERY_BASE_FEE_CENTS)}`
  if (!quote.serviceable) return 'Too far to deliver — pickup only'
  return `${formatCurrency(quote.feeCents)} delivery · ${quote.distanceKm} km`
})

// ── The menu ──────────────────────────────────────────────────────────────

const activeCategory = ref(ALL)
const searchTerm = ref('')
const header = ref<InstanceType<typeof FdHeader> | null>(null)

function pillIcon(name: string) {
  return categoryIcon(name) ?? ShoppingBasket
}

const needle = computed(() => searchTerm.value.trim().toLowerCase())

const sections = computed(() => {
  const matches = (product: Product) =>
    needle.value === '' ||
    product.name.toLowerCase().includes(needle.value) ||
    (product.brand ?? '').toLowerCase().includes(needle.value)

  return categories.value
    .filter((category) => activeCategory.value === ALL || category.id === activeCategory.value)
    .map((category) => {
      const products = catalog.products.filter((product) => product.categoryId === category.id && matches(product))
      // A search shows every hit; so does a picked aisle.
      const capped = activeCategory.value === ALL && needle.value === ''
      return {
        category,
        total: products.length,
        products: capped ? products.slice(0, PREVIEW_COUNT) : products,
      }
    })
    .filter((section) => section.total > 0)
})

const menuTop = ref<HTMLElement | null>(null)

function pick(categoryId: string) {
  activeCategory.value = categoryId
  searchTerm.value = ''
  header.value?.clear()
  void nextTick(() => menuTop.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function onSearch(term: string) {
  searchTerm.value = term
  activeCategory.value = ALL
  void nextTick(() => menuTop.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function clearSearch() {
  searchTerm.value = ''
  header.value?.clear()
}

/** A product picked from the header's suggestions: show its aisle, then it. */
function onProduct(productId: string) {
  const product = catalog.products.find((candidate) => candidate.id === productId)
  if (!product) return
  activeCategory.value = product.categoryId
  searchTerm.value = ''
  void nextTick(() => {
    const card = document.getElementById(`item-${productId}`)
    card?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    card?.classList.add('shop-item--flash')
    setTimeout(() => card?.classList.remove('shop-item--flash'), 1600)
  })
}

// ── The basket ────────────────────────────────────────────────────────────
//
// There is one basket for the whole site, and an order goes to one shop. A
// basket filled somewhere else is not this page's to show or to add to
// silently: adding would re-label those lines as this shop's, and checkout
// would then fail on every one of them.

const basketShop = computed(() => cart.cartShop.value)
const hasLines = computed(() => cart.cartLines.value.length > 0)

const ownsBasket = computed(
  () => !hasLines.value || (basketShop.value?.orgSlug === props.store?.orgSlug && basketShop.value?.storeCode === props.store?.storeCode),
)

const lines = computed<CartLine[]>(() => (ownsBasket.value ? cart.cartLines.value : []))
const itemCount = computed(() => lines.value.reduce((sum, line) => sum + line.quantity, 0))

function quantityOf(productId: string): number {
  return ownsBasket.value ? cart.quantityOf(productId) : 0
}

// Keep the basket's copies of these products at today's prices.
watch(
  () => catalog.products,
  (products) => {
    if (products.length > 0 && ownsBasket.value) cart.refresh(products)
  },
  { immediate: true },
)

const pending = ref<Product | null>(null)

function add(product: Product) {
  if (!ownsBasket.value) {
    pending.value = product
    return
  }
  cart.add(product)
}

function startNewBasket() {
  const product = pending.value
  pending.value = null
  cart.clear()
  if (product) cart.add(product)
}

const itemsTotalCents = computed(() =>
  lines.value.reduce((sum, line) => sum + line.product.priceCents * line.quantity, 0),
)

/**
 * The main site's cart. From a subdomain the basket goes along in the link,
 * because the main site cannot read this origin's copy of it.
 */
function cartUrl(step: 'cart' | 'checkout'): string {
  const params = new URLSearchParams()
  if (step === 'checkout') params.set('step', 'checkout')
  if (mainSite && props.store && lines.value.length > 0) {
    params.set(
      BASKET_PARAM,
      encodeBasketHandoff({
        orgSlug: props.store.orgSlug,
        storeCode: props.store.storeCode,
        lines: lines.value.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
      }),
    )
  }
  const query = params.toString()
  return `${mainSite}/cart${query ? `?${query}` : ''}`
}

const cartHref = computed(() => cartUrl('cart'))

function checkout() {
  window.location.href = cartUrl('checkout')
}

function reload() {
  window.location.reload()
}

// ── The phone's basket sheet ──────────────────────────────────────────────

const sheetOpen = ref(false)

function onKey(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (pending.value) pending.value = null
  else if (sheetOpen.value) sheetOpen.value = false
}

watch(sheetOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

watch(itemCount, (count) => {
  if (count === 0) sheetOpen.value = false
})

onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
  if (copiedTimer) clearTimeout(copiedTimer)
})

const promises = [
  { icon: HeartHandshake, title: 'Support local', text: 'Your order goes straight to the shop.' },
  { icon: HandCoins, title: 'Pay when it arrives', text: 'Cash or GCash, on delivery or pickup.' },
  { icon: Bike, title: 'Local riders', text: 'The rider keeps the whole delivery fee.' },
]
</script>

<template>
  <div class="fd shop">
    <FdHeader
      ref="header"
      shop-href="#menu"
      :cart-href="cartHref"
      :category-nav="false"
      :account-banner="false"
      @search="onSearch"
      @product="onProduct"
    />

    <!-- A link to a shop that is not on Omaykan, or a directory that did not
         answer. Never another shop's menu in its place. -->
    <main v-if="!store" class="shop-missing">
      <Store :size="44" :stroke-width="1.4" aria-hidden="true" />
      <template v-if="directoryFailed">
        <h1 class="sf-h2">We couldn't open this shop just now</h1>
        <p>Check your connection and try again.</p>
        <button type="button" class="shop-btn" @click="reload">Try again</button>
      </template>
      <template v-else>
        <h1 class="sf-h2">We couldn't find that shop</h1>
        <p>The link may be mistyped, or the shop may no longer be taking orders on Omaykan.</p>
        <a class="shop-btn" :href="`${mainSite}/#shops`">Browse shops</a>
      </template>
    </main>

    <main v-else id="menu" class="shop-main">
      <div class="shop-content">
        <!-- ── The shop ──────────────────────────────────────────────── -->
        <section class="shop-hero" aria-labelledby="shop-name">
          <div class="shop-hero__cover">
            <img :src="coverPhoto" alt="" />
          </div>

          <div class="shop-hero__body">
            <div class="shop-hero__logo" :class="{ 'shop-hero__logo--photo': shopPhoto }">
              <img v-if="shopPhoto" :src="shopPhoto" :alt="`${shopName}`" />
              <span v-else aria-hidden="true">{{ initials || 'O' }}</span>
            </div>

            <div class="shop-hero__text">
              <p v-if="shopKind" class="shop-hero__kind">{{ shopKind }}</p>
              <h1 id="shop-name" class="shop-hero__name">{{ shopName }}</h1>
              <ul class="shop-hero__meta">
                <li v-if="shopAddress">
                  <MapPin :size="16" :stroke-width="2" aria-hidden="true" />
                  {{ shopAddress }}
                </li>
                <li>
                  <Bike :size="16" :stroke-width="2" aria-hidden="true" />
                  <button type="button" class="shop-hero__fee" @click="delivery.openDialog()">
                    {{ deliveryLabel }}
                  </button>
                </li>
                <li class="shop-hero__pill">
                  <ShoppingBag :size="15" :stroke-width="2" aria-hidden="true" />
                  Pickup available
                </li>
              </ul>
            </div>

            <button type="button" class="shop-hero__share" @click="share">
              <Check v-if="copied" :size="17" :stroke-width="2.2" aria-hidden="true" />
              <Share2 v-else :size="17" :stroke-width="2" aria-hidden="true" />
              {{ copied ? 'Link copied' : 'Share' }}
            </button>
          </div>
        </section>

        <!-- ── The aisles ────────────────────────────────────────────── -->
        <nav v-if="categories.length > 0" class="shop-pills" aria-label="Menu sections">
          <button
            type="button"
            class="shop-pill"
            :class="{ 'shop-pill--on': activeCategory === ALL }"
            :aria-pressed="activeCategory === ALL"
            @click="pick(ALL)"
          >
            <LayoutGrid :size="20" :stroke-width="1.8" aria-hidden="true" />
            All items
          </button>
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            class="shop-pill"
            :class="{ 'shop-pill--on': activeCategory === category.id }"
            :aria-pressed="activeCategory === category.id"
            @click="pick(category.id)"
          >
            <component :is="pillIcon(category.name)" :size="20" :stroke-width="1.8" aria-hidden="true" />
            {{ category.name }}
          </button>
        </nav>

        <div ref="menuTop" class="shop-anchor" />

        <p v-if="needle" class="shop-searching">
          <Search :size="16" aria-hidden="true" />
          Results for “{{ searchTerm.trim() }}”
          <button type="button" class="shop-searching__clear" @click="clearSearch">
            <X :size="14" aria-hidden="true" /> Clear
          </button>
        </p>

        <!-- ── The menu ──────────────────────────────────────────────── -->
        <div v-if="catalog.loading" class="shop-grid" aria-busy="true" aria-label="Loading the menu">
          <div v-for="n in 6" :key="n" class="shop-skeleton" />
        </div>

        <div v-else-if="catalog.error" class="shop-empty">
          <p>{{ catalog.error }}</p>
          <button type="button" class="shop-btn" @click="retryStorefrontCatalog()">Try again</button>
        </div>

        <div v-else-if="sections.length === 0" class="shop-empty">
          <p v-if="needle">Nothing on this menu matches “{{ searchTerm.trim() }}”.</p>
          <p v-else>{{ shopName }} hasn't put anything on its menu yet. Check back soon.</p>
        </div>

        <template v-else>
        <section
          v-for="(section, index) in sections"
          :key="section.category.id"
          class="shop-section"
          :aria-labelledby="`sec-${section.category.id}`"
        >
          <header class="shop-section__head">
            <h2 :id="`sec-${section.category.id}`" class="sf-h2">{{ section.category.name }}</h2>
            <span class="shop-section__count">{{ section.total }} {{ section.total === 1 ? 'item' : 'items' }}</span>
            <button
              v-if="section.products.length < section.total"
              type="button"
              class="sf-more"
              @click="pick(section.category.id)"
            >
              See all →
            </button>
          </header>

          <div class="shop-grid">
            <MenuCard
              v-for="product in section.products"
              :id="`item-${product.id}`"
              :key="product.id"
              class="shop-item"
              :product="product"
              :quantity="quantityOf(product.id)"
              :category-name="section.category.name"
              :merchant-image-url="shopPhoto"
              :featured="index === 0 && activeCategory === ALL && !needle"
              @add="add"
              @decrement="cart.decrement"
            />
          </div>
        </section>
        </template>
      </div>

      <!-- ── The basket, beside the menu on a wide screen ────────────── -->
      <aside class="shop-aside" aria-label="Your cart">
        <div class="shop-aside__card">
          <div v-if="!ownsBasket" class="shop-other">
            <p>
              Your cart has items from <strong>{{ basketShop?.name || 'another shop' }}</strong>.
              An order can only come from one shop.
            </p>
            <div class="shop-other__actions">
              <a class="shop-btn shop-btn--ghost" :href="cartHref">View that cart</a>
              <button type="button" class="shop-btn" @click="cart.clear()">Empty it</button>
            </div>
          </div>
          <ShopCart
            v-else
            :lines="lines"
            :merchant-image-url="shopPhoto"
            :delivery-fee-cents="deliveryFeeCents"
            :base-fee-cents="DELIVERY_BASE_FEE_CENTS"
            :deliverable="deliverable"
            @add="add"
            @decrement="cart.decrement"
            @remove="cart.remove"
            @clear="cart.clear()"
            @checkout="checkout"
            @set-address="delivery.openDialog()"
          />
        </div>

        <ul class="shop-promises">
          <li v-for="item in promises" :key="item.title">
            <span class="shop-promises__icon"><component :is="item.icon" :size="20" :stroke-width="1.8" /></span>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.text }}</small>
            </span>
          </li>
        </ul>

        <p class="shop-script" aria-hidden="true">Order local. Eat local. Live local.</p>
      </aside>
    </main>

    <!-- ── The phone's basket bar and sheet ─────────────────────────────── -->
    <button
      v-if="store && itemCount > 0"
      type="button"
      class="shop-bar"
      @click="sheetOpen = true"
    >
      <span class="shop-bar__count">{{ itemCount }}</span>
      View cart
      <span class="shop-bar__total">{{ formatCurrency(itemsTotalCents) }}</span>
    </button>

    <div v-if="sheetOpen" class="shop-sheet" role="dialog" aria-modal="true" aria-label="Your cart">
      <div class="shop-sheet__scrim" @click="sheetOpen = false" />
      <div class="shop-sheet__panel">
        <button type="button" class="shop-sheet__close" aria-label="Close cart" @click="sheetOpen = false">
          <X :size="20" />
        </button>
        <ShopCart
          :lines="lines"
          :merchant-image-url="shopPhoto"
          :delivery-fee-cents="deliveryFeeCents"
          :base-fee-cents="DELIVERY_BASE_FEE_CENTS"
          :deliverable="deliverable"
          @add="add"
          @decrement="cart.decrement"
          @remove="cart.remove"
          @clear="cart.clear()"
          @checkout="checkout"
          @set-address="sheetOpen = false; delivery.openDialog()"
        />
      </div>
    </div>

    <!-- ── Adding here would replace another shop's basket ─────────────── -->
    <div v-if="pending" class="shop-sheet shop-sheet--center" role="alertdialog" aria-modal="true" aria-labelledby="swap-title">
      <div class="shop-sheet__scrim" @click="pending = null" />
      <div class="shop-confirm">
        <h2 id="swap-title" class="shop-confirm__title">Start a new cart?</h2>
        <p>
          Your cart has items from <strong>{{ basketShop?.name || 'another shop' }}</strong>.
          Adding {{ pending.name }} will remove them, because an order can only come from one shop.
        </p>
        <div class="shop-other__actions">
          <button type="button" class="shop-btn shop-btn--ghost" @click="pending = null">Keep my cart</button>
          <button type="button" class="shop-btn" @click="startNewBasket">Start new cart</button>
        </div>
      </div>
    </div>

    <FdFooter shop-href="#menu" :show-back-to-top="false" />
  </div>
</template>

<style scoped>
.shop {
  min-height: 100vh;
  background: var(--sf-cream);
}

.shop-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  align-items: start;
  gap: 28px;
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px var(--fd-gutter) 64px;
}

.shop-content {
  min-width: 0;
}

/* ── The shop ─────────────────────────────────────────────────────────── */

.shop-hero {
  overflow: hidden;
  border: 1px solid var(--sf-rule);
  border-radius: 18px;
  background: #fff;
}

.shop-hero__cover {
  position: relative;
  height: clamp(150px, 22vw, 220px);
  background: var(--sf-forest);
}

.shop-hero__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.shop-hero__cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(23, 35, 28, 0) 40%, rgba(23, 35, 28, 0.45) 100%);
}

.shop-hero__body {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 20px;
  padding: 0 24px 22px 196px;
  min-height: 110px;
}

.shop-hero__logo {
  position: absolute;
  left: 24px;
  bottom: 18px;
  display: grid;
  width: 152px;
  height: 152px;
  place-items: center;
  overflow: hidden;
  border: 5px solid #fff;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 35%, var(--sf-paper), var(--sf-sand));
  box-shadow: 0 6px 18px rgba(35, 29, 24, 0.14);
  font-family: var(--sf-serif);
  font-size: 52px;
  font-weight: 700;
  color: var(--sf-forest);
}

.shop-hero__logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.shop-hero__text {
  flex: 1 1 320px;
  min-width: 0;
  padding-top: 16px;
}

.shop-hero__kind {
  margin: 0 0 2px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--sf-leaf);
}

.shop-hero__name {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: clamp(1.7rem, 3.4vw, 2.4rem);
  line-height: 1.15;
  color: var(--sf-ink);
  overflow-wrap: anywhere;
}

.shop-hero__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 20px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  font-size: 14px;
  color: var(--sf-muted);
}

.shop-hero__meta li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.shop-hero__fee {
  padding: 0;
  border: none;
  background: none;
  color: var(--sf-ink);
  font: inherit;
  font-weight: 600;
  text-decoration: underline dotted;
  text-underline-offset: 3px;
  cursor: pointer;
}

.shop-hero__pill {
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--sf-leaf-wash);
  color: var(--sf-leaf);
  font-weight: 600;
}

.shop-hero__share {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 18px;
  border: 1px solid var(--sf-rule);
  border-radius: 999px;
  background: #fff;
  color: var(--sf-forest);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.shop-hero__share:hover {
  background: var(--sf-paper);
}

/* ── The aisles ───────────────────────────────────────────────────────── */

.shop-pills {
  display: flex;
  gap: 10px;
  margin: 22px 0 6px;
  padding-bottom: 6px;
  overflow-x: auto;
  scrollbar-width: none;
}

.shop-pills::-webkit-scrollbar {
  display: none;
}

.shop-pill {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 0 22px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--sf-sand);
  color: var(--sf-ink);
  font: inherit;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease;
}

.shop-pill:hover {
  background: var(--sf-sand-deep);
}

.shop-pill--on,
.shop-pill--on:hover {
  background: var(--sf-forest);
  color: #fff;
}

.shop-anchor {
  scroll-margin-top: 96px;
}

.shop-searching {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 0 0;
  font-weight: 600;
}

.shop-searching__clear {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--sf-rule);
  border-radius: 999px;
  background: #fff;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

/* ── The menu ─────────────────────────────────────────────────────────── */

.shop-section {
  margin-top: 28px;
}

.shop-section__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 12px;
  margin-bottom: 14px;
}

.shop-section__count {
  flex: 1;
  font-size: 14px;
  color: var(--sf-muted);
}

.shop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  gap: 14px;
}

.shop-item {
  scroll-margin-top: 120px;
  transition: box-shadow 300ms ease;
}

.shop-item--flash {
  box-shadow: 0 0 0 3px var(--sf-gold);
}

.shop-skeleton {
  height: 134px;
  border-radius: 14px;
  background: linear-gradient(90deg, var(--sf-sand) 0%, var(--sf-paper) 50%, var(--sf-sand) 100%);
  background-size: 200% 100%;
  animation: shop-shimmer 1.4s ease-in-out infinite;
}

@keyframes shop-shimmer {
  from { background-position: 100% 0; }
  to { background-position: -100% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .shop-skeleton { animation: none; }
}

.shop-empty,
.shop-missing {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  padding: 40px 20px;
  border: 1px dashed var(--sf-rule);
  border-radius: 16px;
  color: var(--sf-muted);
  text-align: center;
}

.shop-empty p,
.shop-missing p {
  margin: 0;
}

.shop-missing {
  max-width: 560px;
  margin: 64px auto;
  border-style: solid;
  background: #fff;
  color: var(--sf-forest-soft);
}

.shop-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 18px;
  border: none;
  border-radius: 10px;
  background: var(--sf-clay);
  color: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.shop-btn:hover {
  background: var(--sf-clay-deep);
}

.shop-btn--ghost {
  border: 1px solid var(--sf-rule);
  background: #fff;
  color: var(--sf-ink);
}

.shop-btn--ghost:hover {
  background: var(--sf-paper);
}

/* ── The basket ───────────────────────────────────────────────────────── */

.shop-aside {
  position: sticky;
  top: 88px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.shop-aside__card {
  padding: 22px;
  border: 1px solid var(--sf-rule);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(35, 29, 24, 0.06);
}

.shop-other p {
  margin: 0 0 14px;
  line-height: 1.5;
}

.shop-other__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.shop-promises {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 18px;
  border-radius: 16px;
  background: var(--sf-leaf-wash);
  list-style: none;
}

.shop-promises li {
  display: flex;
  align-items: center;
  gap: 12px;
}

.shop-promises__icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: #fff;
  color: var(--sf-leaf);
}

.shop-promises strong {
  display: block;
  font-size: 14px;
  color: var(--sf-ink);
}

.shop-promises small {
  font-size: 13px;
  color: var(--sf-muted);
}

.shop-script {
  margin: 0;
  font-family: var(--sf-script);
  font-size: 22px;
  color: var(--sf-forest-soft);
  text-align: center;
  transform: rotate(-3deg);
}

/* ── The phone's basket ───────────────────────────────────────────────── */

.shop-bar {
  display: none;
}

.shop-sheet {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.shop-sheet--center {
  align-items: center;
  padding: 16px;
}

.shop-sheet__scrim {
  position: absolute;
  inset: 0;
  background: rgba(23, 35, 28, 0.5);
}

.shop-sheet__panel {
  position: relative;
  width: 100%;
  max-width: 560px;
  max-height: 88vh;
  overflow-y: auto;
  padding: 24px 20px calc(20px + env(safe-area-inset-bottom));
  border-radius: 20px 20px 0 0;
  background: #fff;
}

.shop-sheet__close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: var(--sf-sand);
  cursor: pointer;
}

.shop-sheet__panel :deep(.sc__head) {
  padding-right: 44px;
}

.shop-confirm {
  position: relative;
  width: 100%;
  max-width: 420px;
  padding: 24px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.2);
}

.shop-confirm__title {
  margin: 0 0 10px;
  font-family: var(--sf-serif);
  font-size: 1.3rem;
}

.shop-confirm p {
  margin: 0 0 18px;
  line-height: 1.5;
  color: var(--sf-muted);
}

.shop-pill:focus-visible,
.shop-btn:focus-visible,
.shop-hero__share:focus-visible,
.shop-hero__fee:focus-visible,
.shop-bar:focus-visible,
.shop-sheet__close:focus-visible {
  outline: 2px solid var(--sf-forest);
  outline-offset: 2px;
}

@media (max-width: 1080px) {
  .shop-main {
    grid-template-columns: minmax(0, 1fr);
    padding-bottom: 110px;
  }

  .shop-aside {
    position: static;
  }

  /* The basket moves into the sheet; the promises stay on the page. */
  .shop-aside__card {
    display: none;
  }

  .shop-bar {
    position: fixed;
    right: var(--fd-gutter);
    bottom: calc(16px + env(safe-area-inset-bottom));
    left: var(--fd-gutter);
    z-index: 50;
    display: flex;
    align-items: center;
    gap: 12px;
    max-width: 560px;
    min-height: 56px;
    margin: 0 auto;
    padding: 0 18px;
    border: none;
    border-radius: 14px;
    background: var(--sf-clay);
    color: #fff;
    font: inherit;
    font-size: 16px;
    font-weight: 700;
    box-shadow: 0 10px 24px rgba(147, 64, 29, 0.35);
    cursor: pointer;
  }

  .shop-bar__count {
    display: grid;
    min-width: 28px;
    height: 28px;
    place-items: center;
    padding: 0 6px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.22);
    font-size: 14px;
  }

  .shop-bar__total {
    margin-left: auto;
    font-variant-numeric: tabular-nums;
  }
}

@media (max-width: 640px) {
  .shop-hero__body {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
    padding: 0 16px 18px;
  }

  .shop-hero__logo {
    position: relative;
    left: auto;
    bottom: auto;
    width: 96px;
    height: 96px;
    margin-top: -52px;
    border-width: 4px;
    font-size: 34px;
  }

  .shop-hero__text {
    flex-basis: auto;
    padding-top: 0;
  }

  .shop-hero__share {
    align-self: flex-start;
  }

  .shop-pill {
    min-height: 44px;
    padding: 0 16px;
    font-size: 14px;
  }
}
</style>
