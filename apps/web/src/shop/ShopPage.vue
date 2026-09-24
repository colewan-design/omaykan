<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowLeft,
  Bike,
  Check,
  ChevronRight,
  CircleDollarSign,
  HeartHandshake,
  Layers,
  Leaf,
  MapPin,
  MessageCircle,
  Package,
  Search,
  Share2,
  ShieldCheck,
  SlidersHorizontal,
  ShoppingBag,
  ShoppingCart,
  Star,
  Store,
  Truck,
  Wallet,
  X,
} from '@lucide/vue'
import { formatCurrency, storeCheckoutPath, storefrontUrl, type Product } from '@pos/shared/index'
import type { StoreSummary } from '@pos/web/commerce/api'
import { useStorefrontCart, type CartLine } from '@pos/web/commerce/cart'
import { retryStorefrontCatalog, useStockedCategories, useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { DELIVERY_BASE_FEE_CENTS, haversineKm, quoteDelivery } from '@pos/web/commerce/delivery'
import { BASKET_PARAM, encodeBasketHandoff } from '@pos/web/commerce/basketHandoff'
import { useDeliveryLocation } from '@pos/web/commerce/deliveryLocation'
import { useSavedProducts } from '@pos/web/commerce/favorites'
import { SHOP_ROOT_DOMAIN, mainSiteOrigin } from '@pos/web/commerce/shopDomain'
import MenuCard from './MenuCard.vue'
import ShopCart from './ShopCart.vue'

const props = defineProps<{
  slug: string
  store: StoreSummary | null
  directoryFailed: boolean
}>()

const ALL = 'all'
/** Above this, the page gets its own cart column and the floating bar goes away. */
const WIDE = '(min-width: 1200px)'
const catalog = useStorefrontCatalog()
const categories = useStockedCategories()
const cart = useStorefrontCart()
const delivery = useDeliveryLocation()
const mainSite = mainSiteOrigin()

const shopName = computed(() => catalog.shop?.name || props.store?.name || 'This shop')
const shopKind = computed(() => catalog.shop?.businessTypeLabel || props.store?.businessTypeLabel || '')
const shopOwner = computed(() => catalog.shop?.ownerName || '')
const shopAddress = computed(() => catalog.shop?.address || props.store?.address || '')
const shopPhoto = computed(() => catalog.shop?.imageUrl || props.store?.imageUrl || '')
const coverPhoto = computed(() => shopPhoto.value || '/storefront/seller-hero-v2.webp')
const initials = computed(() =>
  shopName.value
    .split(/\s+/)
    .filter((word) => /^[\p{L}\p{N}]/u.test(word))
    .slice(0, 2)
    .map((word) => word[0]!.toUpperCase())
    .join(''),
)
const shopSummary = computed(() => {
  const kind = shopKind.value ? shopKind.value.toLowerCase() : 'local shop'
  return `Shop everyday favorites from this ${kind}. Fresh, local, and made for the community.`
})

watch(shopName, (name) => (document.title = `${name} — Omaykan`), { immediate: true })

const shareUrl = computed(() =>
  storefrontUrl(props.slug, { rootDomain: SHOP_ROOT_DOMAIN, origin: mainSite || window.location.origin }),
)
const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | null = null

async function share() {
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
    // The address bar still contains the shareable storefront URL.
  }
}

function goBack() {
  if (window.history.length > 1) window.history.back()
  else window.location.href = `${mainSite}/#shops`
}

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
  if (!quote.serviceable) return 'Pickup only from your location'
  return `${formatCurrency(quote.feeCents)} delivery · ${quote.distanceKm} km`
})
const deliveryDetail = computed(() => {
  const quote = deliveryQuote.value
  if (quote === null) return 'No minimum order'
  if (!quote.serviceable) return 'Pickup is still available'
  return `${quote.distanceKm} km from this store`
})
const storeOpen = computed(() => !catalog.closed && !catalog.shop?.orderingPausedMessage)

/**
 * The line under the name. Only what the shop's own record says: it is taking
 * orders, it has paused (in its own words, "back at 4:00 PM"), or it is closed.
 * There are no business hours or preparation times in the schema, so nothing
 * here claims either (see documentation/merchant-features.md §3).
 */
const statusLabel = computed(() => {
  if (catalog.closed) return 'Not taking orders'
  return catalog.shop?.orderingPausedMessage ? 'Paused for now' : 'Taking orders now'
})
/** Blank while paused: the banner over the menu already says until when. */
const statusDetail = computed(() => {
  if (catalog.closed) return 'Check back soon'
  return catalog.shop?.orderingPausedMessage ? '' : 'Pay cash or GCash when it arrives'
})

/**
 * The fulfilment strip under the name — one row on desktop, the two cards this
 * page has always shown on a phone. Every line is something the shop's own
 * record or the platform's fee table says; none of it is decoration.
 *
 * `extra` marks the two that only appear once there is a row wide enough to
 * hold them without pushing the menu further down a phone screen.
 */
const facts = computed(() => [
  {
    key: 'delivery',
    icon: Truck,
    title: deliveryLabel.value,
    note: deliveryDetail.value,
    open: () => delivery.openDialog(),
    extra: false,
  },
  { key: 'pickup', icon: ShoppingBag, title: 'Pickup available', note: 'Collect at the store', open: null, extra: false },
  { key: 'payment', icon: CircleDollarSign, title: 'Cash / GCash', note: 'Pay when it arrives', open: null, extra: true },
  { key: 'minimum', icon: Wallet, title: 'No minimum order', note: 'Order any amount', open: null, extra: true },
])

// ── Saved items ────────────────────────────────────────────────────────────
//
// The heart on each card is the shopper's wishlist (commerce/favorites.ts),
// the same one the account page lists. It is kept in the browser, so on a
// shop's own subdomain it is that subdomain's list; on omaykan.com it is the
// account page's.
const savedItems = useSavedProducts()
const savedNotice = ref('')
let savedTimer: ReturnType<typeof setTimeout> | null = null

function toggleSave(product: Product) {
  const nowSaved = savedItems.toggle(product)
  savedNotice.value = nowSaved ? `${product.name} saved for later` : `${product.name} removed from saved items`
  if (savedTimer) clearTimeout(savedTimer)
  savedTimer = setTimeout(() => (savedNotice.value = ''), 2400)
}

// ── How many cards are in a row ────────────────────────────────────────────
//
// A shelf's preview is exactly one full row, whatever the window is: three
// cards in a four-card row leave a hole, and four in a three-card row wrap to
// a single orphan. These widths are the ones store-menu.css switches the grid
// at, and the two have to stay in step.
const COLUMN_STEPS = [
  { query: WIDE, columns: 4 },
  { query: '(min-width: 380px)', columns: 3 },
]
const columns = ref(3)
const watchedQueries: { list: MediaQueryList; onChange: () => void }[] = []

function measureColumns() {
  columns.value = COLUMN_STEPS.find((step) => window.matchMedia(step.query).matches)?.columns ?? 2
}

const activeCategory = ref(ALL)
/** "See all" on the favourites: every aisle in full, not one row from each. */
const expanded = ref(false)
const searchTerm = ref('')
const menuTop = ref<HTMLElement | null>(null)
const needle = computed(() => searchTerm.value.trim().toLowerCase())

// ── Sorting and filtering ──────────────────────────────────────────────────
//
// "Featured" is the order the shop's own catalog comes back in, which is the
// order its owner put the shelf in. There is no sales history or listing date
// on a product, so there is no "Popular" or "New" to offer that would not be
// invented.
type SortKey = 'featured' | 'price-asc' | 'price-desc' | 'name'
const SORTS: { value: SortKey; label: string }[] = [
  { value: 'featured', label: 'Featured' },
  { value: 'price-asc', label: 'Price: low to high' },
  { value: 'price-desc', label: 'Price: high to low' },
  { value: 'name', label: 'Name: A to Z' },
]
const sortKey = ref<SortKey>('featured')
const saleOnly = ref(false)
const savedOnly = ref(false)
const filtersOpen = ref(false)
const filterMenu = ref<HTMLElement | null>(null)

function isOnSale(product: Product): boolean {
  return (product.compareAtPriceCents ?? 0) > product.priceCents
}

const saleTotal = computed(() => catalog.products.filter(isOnSale).length)
const savedTotal = computed(() => catalog.products.filter((product) => savedItems.isSaved(product.id)).length)
const activeFilters = computed(() => Number(saleOnly.value) + Number(savedOnly.value))
/** Any narrowing at all — the point at which shelves stop being previews. */
const narrowed = computed(() => needle.value !== '' || activeFilters.value > 0)

function matches(product: Product): boolean {
  if (saleOnly.value && !isOnSale(product)) return false
  if (savedOnly.value && !savedItems.isSaved(product.id)) return false
  if (needle.value === '') return true
  return product.name.toLowerCase().includes(needle.value) || (product.brand ?? '').toLowerCase().includes(needle.value)
}

function inOrder(products: Product[]): Product[] {
  if (sortKey.value === 'featured') return products
  const list = [...products]
  if (sortKey.value === 'name') return list.sort((a, b) => a.name.localeCompare(b.name))
  const direction = sortKey.value === 'price-asc' ? 1 : -1
  return list.sort((a, b) => (a.priceCents - b.priceCents) * direction)
}

/**
 * How many products each aisle has that the search and filters allow — the
 * number on its chip. Deliberately blind to which chip is pressed, so the
 * chips say what switching to them would find.
 */
const countByCategory = computed(() => {
  const counts = new Map<string, number>()
  for (const product of catalog.products) {
    if (!matches(product)) continue
    counts.set(product.categoryId, (counts.get(product.categoryId) ?? 0) + 1)
  }
  return counts
})
const matchTotal = computed(() => catalog.products.filter(matches).length)

const sections = computed(() =>
  categories.value
    .filter((category) => activeCategory.value === ALL || category.id === activeCategory.value)
    .map((category) => {
      const products = inOrder(catalog.products.filter((product) => product.categoryId === category.id && matches(product)))
      const capped = activeCategory.value === ALL && !narrowed.value && !expanded.value
      return { category, total: products.length, products: capped ? products.slice(0, columns.value) : products }
    })
    .filter((section) => section.total > 0),
)

/**
 * The opening shelf: one row that is a taste of the whole shop rather than a
 * repeat of the first aisle. It takes the leading product from each category
 * in turn, and only tops the row up from the front of the catalog when the
 * shop has fewer categories than the row has places.
 *
 * It is left out entirely for a shop with no more than a row of products: the
 * aisle below it would be the same four cards again.
 */
const featuredProducts = computed(() => {
  if (narrowed.value || activeCategory.value !== ALL) return []
  if (catalog.products.length <= columns.value) return []

  const picked: Product[] = []
  const taken = new Set<string>()
  for (const category of categories.value) {
    if (picked.length >= columns.value) break
    const first = catalog.products.find((product) => product.categoryId === category.id)
    if (!first || taken.has(first.id)) continue
    picked.push(first)
    taken.add(first.id)
  }
  for (const product of catalog.products) {
    if (picked.length >= columns.value) break
    if (taken.has(product.id)) continue
    picked.push(product)
    taken.add(product.id)
  }
  return picked
})

const activeCategoryName = computed(() => categories.value.find((category) => category.id === activeCategory.value)?.name ?? '')

function pick(categoryId: string) {
  activeCategory.value = categoryId
  expanded.value = false
  void nextTick(() => menuTop.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function clearSearch() {
  searchTerm.value = ''
}

function showWholeMenu() {
  activeCategory.value = ALL
  searchTerm.value = ''
  expanded.value = true
  void nextTick(() => menuTop.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function resetMenuFilters() {
  activeCategory.value = ALL
  expanded.value = false
  searchTerm.value = ''
  sortKey.value = 'featured'
  saleOnly.value = false
  savedOnly.value = false
  filtersOpen.value = false
  void nextTick(() => menuTop.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function onDocumentClick(event: MouseEvent) {
  if (!filtersOpen.value) return
  if (filterMenu.value?.contains(event.target as Node)) return
  filtersOpen.value = false
}

// ── The store's own navigation ─────────────────────────────────────────────
//
// A shop subdomain is a small site of its own, so it gets a bar that names its
// parts and says which one you are looking at. Reviews are not among them:
// nothing in the schema records a review of a shop.
const NAV = [
  { id: 'shop-top', label: 'Home' },
  { id: 'shop-products', label: 'Products' },
  { id: 'shop-categories', label: 'Categories' },
  { id: 'shop-about', label: 'About' },
]
const activeNav = ref('shop-top')
let navFrame = 0

function trackNav() {
  if (navFrame !== 0) return
  navFrame = requestAnimationFrame(() => {
    navFrame = 0
    // A short shop runs out of page before its last section clears the line,
    // so the end of the scroll is the end of the nav, whatever the maths says.
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) {
      activeNav.value = NAV[NAV.length - 1]!.id
      return
    }
    // Otherwise the one nearest above the line, by where it actually is on the
    // page — the labels are not in document order, so their order says nothing.
    let current = NAV[0]!.id
    let nearest = -Infinity
    for (const item of NAV) {
      const top = document.getElementById(item.id)?.getBoundingClientRect().top
      if (top === undefined || top > 140 || top <= nearest) continue
      nearest = top
      current = item.id
    }
    activeNav.value = current
  })
}

function goTo(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const basketShop = computed(() => cart.cartShop.value)
const hasLines = computed(() => cart.cartLines.value.length > 0)
const ownsBasket = computed(
  () => !hasLines.value || (basketShop.value?.orgSlug === props.store?.orgSlug && basketShop.value?.storeCode === props.store?.storeCode),
)
const lines = computed<CartLine[]>(() => (ownsBasket.value ? cart.cartLines.value : []))
const itemCount = computed(() => lines.value.reduce((sum, line) => sum + line.quantity, 0))
const itemsTotalCents = computed(() => lines.value.reduce((sum, line) => sum + line.product.priceCents * line.quantity, 0))

function quantityOf(productId: string): number {
  return ownsBasket.value ? cart.quantityOf(productId) : 0
}

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

/**
 * The cart, or this shop's own checkout (/shop/<slug>/checkout). Both are on
 * the main site; from a subdomain the basket travels in the link.
 */
function cartUrl(step: 'cart' | 'checkout'): string {
  const params = new URLSearchParams()
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
  const path = step === 'checkout' && props.store ? storeCheckoutPath(props.store.orgSlug) : '/cart'
  return `${mainSite}${path}${query ? `?${query}` : ''}`
}

const checkoutHref = computed(() => cartUrl('checkout'))
const messageHref = computed(() => {
  const params = new URLSearchParams({ section: 'messages', shop: props.slug })
  if (props.store?.storeCode) params.set('store', props.store.storeCode)
  params.set('name', shopName.value)
  return `${mainSite}/account?${params.toString()}`
})
const sheetOpen = ref(false)

/**
 * Where the cart is depends on the window: its own column beside the menu on a
 * wide screen, a sheet over the menu on anything narrower.
 */
function openCart() {
  if (window.matchMedia(WIDE).matches) {
    document.getElementById('shop-cart')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    return
  }
  sheetOpen.value = true
}

function checkout() {
  window.location.href = checkoutHref.value
}
function reload() {
  window.location.reload()
}
function onKey(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (pending.value) pending.value = null
  else if (sheetOpen.value) sheetOpen.value = false
  else if (filtersOpen.value) filtersOpen.value = false
}

watch(sheetOpen, (open) => (document.body.style.overflow = open ? 'hidden' : ''))
watch(itemCount, (count) => {
  if (count === 0) sheetOpen.value = false
})
onMounted(() => {
  measureColumns()
  for (const step of COLUMN_STEPS) {
    const list = window.matchMedia(step.query)
    const onChange = () => measureColumns()
    list.addEventListener('change', onChange)
    watchedQueries.push({ list, onChange })
  }
  document.addEventListener('keydown', onKey)
  document.addEventListener('click', onDocumentClick)
  window.addEventListener('scroll', trackNav, { passive: true })
  trackNav()
})
onBeforeUnmount(() => {
  for (const { list, onChange } of watchedQueries) list.removeEventListener('change', onChange)
  watchedQueries.length = 0
  document.removeEventListener('keydown', onKey)
  document.removeEventListener('click', onDocumentClick)
  window.removeEventListener('scroll', trackNav)
  if (navFrame !== 0) cancelAnimationFrame(navFrame)
  document.body.style.overflow = ''
  if (copiedTimer) clearTimeout(copiedTimer)
  if (savedTimer) clearTimeout(savedTimer)
})

/**
 * The reasons to order here, in this shop's own terms rather than the
 * platform's. Only the delivery line changes, and only because the shopper's
 * own pin has already told us it is out of range.
 */
const highlights = computed(() => [
  { icon: HeartHandshake, title: 'Direct from the seller', text: `Your order goes straight to ${shopName.value}, not a warehouse.` },
  { icon: ShoppingBag, title: 'Pickup ready', text: 'Collect it at the store when it works for you.' },
  deliverable.value
    ? { icon: Bike, title: 'Local delivery', text: 'Brought over by riders in your area.' }
    : { icon: Bike, title: 'Pickup only here', text: 'Your address is outside this shop’s delivery area.' },
  { icon: ShieldCheck, title: 'Secure checkout', text: 'Payment and support run through Omaykan.' },
])
</script>

<template>
  <div class="fd shop">
    <main v-if="!store" class="shop-missing">
      <Store :size="44" :stroke-width="1.4" aria-hidden="true" />
      <template v-if="directoryFailed">
        <h1>We couldn't open this shop just now</h1>
        <p>Check your connection and try again.</p>
        <button type="button" class="shop-btn" @click="reload">Try again</button>
      </template>
      <template v-else>
        <h1>We couldn't find that shop</h1>
        <p>The link may be mistyped, or the shop may no longer be taking orders on Omaykan.</p>
        <a class="shop-btn" :href="`${mainSite}/#shops`">Browse shops</a>
      </template>
    </main>

    <main v-else id="menu" class="shop-shell">
      <section id="shop-top" class="shop-hero" aria-labelledby="shop-name">
        <div class="shop-hero__cover">
          <img :src="coverPhoto" alt="" />
          <button type="button" class="shop-float shop-float--back" aria-label="Go back" @click="goBack">
            <ArrowLeft :size="21" :stroke-width="2" />
          </button>
          <div class="shop-hero__tools">
            <button type="button" class="shop-float" :aria-label="copied ? 'Store link copied' : 'Share store'" @click="share">
              <Check v-if="copied" :size="20" :stroke-width="2.2" />
              <Share2 v-else :size="20" :stroke-width="2" />
            </button>
          </div>
          <div class="shop-hero__motto" aria-hidden="true">
            <p>Good food,<br />brighter days</p>
            <span>Local flavors.<br />Real people.</span>
          </div>
          <div class="shop-hero__sign" aria-hidden="true">
            <strong>{{ shopName }}</strong>
            <span>{{ shopKind || 'Local store' }}</span>
          </div>
        </div>

        <div class="shop-profile">
          <div class="shop-profile__logo" :class="{ 'shop-profile__logo--photo': shopPhoto }">
            <img v-if="shopPhoto" :src="shopPhoto" :alt="shopName" />
            <template v-else>
              <Leaf :size="29" :stroke-width="1.8" aria-hidden="true" />
              <strong aria-hidden="true">{{ initials || 'O' }}</strong>
            </template>
          </div>

          <div class="shop-profile__heading">
            <h1 id="shop-name">{{ shopName }}</h1>
            <p class="shop-profile__badges">
              <span class="shop-profile__verified"><ShieldCheck :size="16" fill="currentColor" /> Local seller</span>
              <template v-if="!catalog.loading">
                <span class="shop-profile__state" :class="{ 'is-open': storeOpen }"><i />{{ statusLabel }}</span>
                <span v-if="statusDetail" class="shop-profile__note">{{ statusDetail }}</span>
              </template>
              <span v-if="shopAddress" class="shop-profile__where"><MapPin :size="15" /> {{ shopAddress }}</span>
            </p>
          </div>

          <p class="shop-profile__summary">{{ shopSummary }}</p>

          <div class="shop-profile__actions">
            <a class="shop-action shop-action--primary" :href="messageHref"><MessageCircle :size="22" /> Message</a>
            <button type="button" class="shop-action shop-action--secondary" @click="share">
              <Check v-if="copied" :size="21" />
              <Share2 v-else :size="21" />
              {{ copied ? 'Link copied' : 'Share' }}
            </button>
            <button v-if="itemCount > 0" type="button" class="shop-action shop-action--cart" @click="openCart">
              <ShoppingCart :size="21" /> View cart ({{ itemCount }})
            </button>
          </div>

          <ul class="shop-facts" aria-label="How this store fulfils orders">
            <li v-for="fact in facts" :key="fact.key" :class="{ 'shop-fact--extra': fact.extra }">
              <component
                :is="fact.open ? 'button' : 'div'"
                class="shop-fact"
                :type="fact.open ? 'button' : undefined"
                @click="fact.open?.()"
              >
                <component :is="fact.icon" :size="22" :stroke-width="1.9" aria-hidden="true" />
                <span><strong>{{ fact.title }}</strong><small>{{ fact.note }}</small></span>
              </component>
            </li>
          </ul>
        </div>
      </section>

      <nav class="shop-nav" aria-label="Sections of this store">
        <ul>
          <li v-for="item in NAV" :key="item.id">
            <button type="button" :class="{ 'is-on': activeNav === item.id }" @click="goTo(item.id)">{{ item.label }}</button>
          </li>
        </ul>
        <button v-if="itemCount > 0" type="button" class="shop-nav__cart" @click="openCart">
          <ShoppingCart :size="18" /> Cart · {{ itemCount }} {{ itemCount === 1 ? 'item' : 'items' }} · {{ formatCurrency(itemsTotalCents) }}
        </button>
      </nav>

      <div class="shop-body">
        <div class="shop-content">
          <div class="shop-toolbar">
            <div class="shop-search">
              <Search :size="19" :stroke-width="2" />
              <input v-model="searchTerm" type="search" :placeholder="`Search ${shopName}`" :aria-label="`Search ${shopName}`" />
              <button v-if="searchTerm" type="button" aria-label="Clear search" @click="clearSearch"><X :size="16" /></button>
            </div>

            <label class="shop-sort">
              <span>Sort</span>
              <select v-model="sortKey">
                <option v-for="option in SORTS" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>

            <div ref="filterMenu" class="shop-filters">
              <button
                type="button"
                class="shop-filter"
                :class="{ 'shop-filter--on': activeFilters > 0 }"
                aria-haspopup="dialog"
                :aria-expanded="filtersOpen"
                @click="filtersOpen = !filtersOpen"
              >
                <SlidersHorizontal :size="19" />
                <span class="shop-filter__label">Filters</span>
                <b v-if="activeFilters > 0">{{ activeFilters }}</b>
              </button>
              <div v-if="filtersOpen" class="shop-filter__menu" role="dialog" aria-label="Filter this store">
                <label><input v-model="saleOnly" type="checkbox" /> On sale <small>{{ saleTotal }}</small></label>
                <label><input v-model="savedOnly" type="checkbox" /> Saved items <small>{{ savedTotal }}</small></label>
                <button type="button" class="shop-filter__reset" @click="resetMenuFilters">Reset everything</button>
              </div>
            </div>
          </div>

          <nav v-if="categories.length" id="shop-categories" class="shop-pills" aria-label="Categories in this store">
            <button type="button" class="shop-pill" :class="{ 'shop-pill--on': activeCategory === ALL }" :aria-pressed="activeCategory === ALL" @click="pick(ALL)">
              All <b>{{ matchTotal }}</b>
            </button>
            <button v-for="category in categories" :key="category.id" type="button" class="shop-pill" :class="{ 'shop-pill--on': activeCategory === category.id }" :aria-pressed="activeCategory === category.id" @click="pick(category.id)">
              {{ category.name }} <b>{{ countByCategory.get(category.id) ?? 0 }}</b>
            </button>
          </nav>
          <div ref="menuTop" class="shop-anchor" />

          <!-- The shop's own pause: the menu stays up so people can plan, and
               this says why checkout will not take the order yet. -->
          <p v-if="catalog.shop?.orderingPausedMessage" class="shop-paused" role="status">{{ catalog.shop.orderingPausedMessage }}</p>
          <p v-if="narrowed" class="shop-searching">
            <Search :size="16" />
            <span v-if="needle">Results for “{{ searchTerm.trim() }}”</span>
            <span v-else>Filtered</span>
            <span v-if="activeCategory !== ALL"> in {{ activeCategoryName }}</span>
            <button type="button" @click="resetMenuFilters"><X :size="14" /> Clear</button>
          </p>

          <!-- Whatever the shelf is doing — loading, empty, closed, stocked —
               this is the "Products" the nav points at. -->
          <div id="shop-products" class="shop-shelves">
            <div v-if="catalog.loading" class="shop-grid" aria-busy="true" aria-label="Loading the menu"><div v-for="n in columns * 2" :key="n" class="shop-skeleton" /></div>
            <!-- Suspended or unpaid, and deliberately not told apart: to a shopper
                 the shop is simply closed. No "Try again" — nothing a retry can fix. -->
            <div v-else-if="catalog.closed" class="shop-empty" role="status"><p>This shop isn't taking orders right now.</p></div>
            <div v-else-if="catalog.error" class="shop-empty"><p>{{ catalog.error }}</p><button type="button" class="shop-btn" @click="retryStorefrontCatalog()">Try again</button></div>
            <div v-else-if="sections.length === 0" class="shop-empty">
              <p v-if="narrowed && activeCategory !== ALL">Nothing in {{ activeCategoryName }} matches what you're after.</p>
              <p v-else-if="narrowed">Nothing in this store matches what you're after.</p>
              <p v-else>{{ shopName }} hasn't put anything on its menu yet. Check back soon.</p>
              <button v-if="narrowed" type="button" class="shop-btn" @click="resetMenuFilters">Show everything</button>
            </div>

            <template v-else>
              <section v-if="featuredProducts.length" class="shop-section shop-section--featured" aria-labelledby="featured-title">
                <header class="shop-section__head">
                  <span class="shop-section__icon"><Star :size="20" fill="currentColor" /></span>
                  <div>
                    <h2 id="featured-title">Shop favorites</h2>
                    <p>Our most loved items, chosen by the community.</p>
                  </div>
                  <button v-if="!expanded" type="button" class="shop-section__more" @click="showWholeMenu">See all <ChevronRight :size="15" /></button>
                </header>
                <div class="shop-grid">
                  <MenuCard v-for="product in featuredProducts" :id="`featured-${product.id}`" :key="product.id" :product="product" :quantity="quantityOf(product.id)" :category-name="categories.find((category) => category.id === product.categoryId)?.name" :merchant-image-url="shopPhoto" :saved="savedItems.isSaved(product.id)" featured @add="add" @decrement="cart.decrement" @toggle-save="toggleSave" />
                </div>
              </section>

              <section v-for="section in sections" :key="section.category.id" class="shop-section" :aria-labelledby="`sec-${section.category.id}`">
                <header class="shop-section__head">
                  <div>
                    <h2 :id="`sec-${section.category.id}`">{{ section.category.name }}</h2>
                    <p>{{ section.total }} {{ section.total === 1 ? 'item' : 'items' }}</p>
                  </div>
                  <button v-if="section.products.length < section.total" type="button" class="shop-section__more" @click="pick(section.category.id)">See all <ChevronRight :size="15" /></button>
                </header>
                <div class="shop-grid">
                  <MenuCard v-for="product in section.products" :id="`item-${product.id}`" :key="product.id" class="shop-item" :product="product" :quantity="quantityOf(product.id)" :category-name="section.category.name" :merchant-image-url="shopPhoto" :saved="savedItems.isSaved(product.id)" @add="add" @decrement="cart.decrement" @toggle-save="toggleSave" />
                </div>
              </section>
            </template>
          </div>

          <section id="shop-about" class="shop-about" aria-labelledby="shop-about-title">
            <h2 id="shop-about-title">About {{ shopName }}</h2>
            <div class="shop-about__grid">
              <div class="shop-about__story">
                <p class="shop-about__lede">{{ shopSummary }}</p>
                <p v-if="shopOwner" class="shop-about__owner">Run by <strong>{{ shopOwner }}</strong>.</p>
                <ul class="shop-about__tags">
                  <li v-if="shopKind"><Store :size="14" /> {{ shopKind }}</li>
                  <li v-if="catalog.products.length"><Package :size="14" /> {{ catalog.products.length }} {{ catalog.products.length === 1 ? 'product' : 'products' }}</li>
                  <li v-if="categories.length"><Layers :size="14" /> {{ categories.length }} {{ categories.length === 1 ? 'category' : 'categories' }}</li>
                </ul>
              </div>
              <dl class="shop-about__facts">
                <div>
                  <dt><span class="shop-about__icon"><MapPin :size="19" /></span> Location</dt>
                  <dd>{{ shopAddress || 'Address available at checkout' }}</dd>
                </div>
                <div>
                  <dt><span class="shop-about__icon"><Truck :size="19" /></span> Delivery</dt>
                  <dd>{{ deliveryLabel }}<br /><small>{{ deliveryDetail }}</small></dd>
                </div>
                <div>
                  <dt><span class="shop-about__icon"><CircleDollarSign :size="19" /></span> Payment</dt>
                  <dd>Cash · GCash<br /><small>No minimum order</small></dd>
                </div>
                <div>
                  <dt><span class="shop-about__icon"><ShoppingBag :size="19" /></span> Ordering</dt>
                  <dd>{{ statusLabel }}<br /><small>Delivery or pickup, chosen at checkout</small></dd>
                </div>
              </dl>
            </div>
          </section>

          <section class="shop-why" aria-labelledby="shop-why-title">
            <h2 id="shop-why-title">Why customers order here</h2>
            <ul class="shop-promises">
              <li v-for="item in highlights" :key="item.title"><span class="shop-promises__icon"><component :is="item.icon" :size="20" /></span><span><strong>{{ item.title }}</strong><small>{{ item.text }}</small></span></li>
            </ul>
          </section>

          <footer class="shop-footer">
            <div><strong>{{ shopName }}</strong><small>{{ shopKind || 'Local seller' }} · Powered by <b>omaykan</b></small></div>
            <p class="shop-script">Good food, brighter days <Leaf :size="18" fill="currentColor" /></p>
          </footer>
        </div>

        <aside class="shop-aside" aria-label="Your order">
          <div class="shop-aside__inner">
            <div id="shop-cart" class="shop-aside__card">
              <ShopCart v-if="lines.length > 0" :lines="lines" :merchant-image-url="shopPhoto" :delivery-fee-cents="deliveryFeeCents" :base-fee-cents="DELIVERY_BASE_FEE_CENTS" :deliverable="deliverable" @add="add" @decrement="cart.decrement" @remove="cart.remove" @clear="cart.clear()" @checkout="checkout" @set-address="delivery.openDialog()" />
              <div v-else class="shop-aside__empty">
                <span><ShoppingCart :size="24" :stroke-width="1.7" /></span>
                <strong>Your cart is empty</strong>
                <p>Add anything from the menu and it will show up here, with the delivery fee.</p>
              </div>
            </div>

            <div class="shop-aside__help">
              <span><MessageCircle :size="21" /></span>
              <strong>Need help with your order?</strong>
              <p>Message {{ shopName }} directly for special requests or questions.</p>
              <a :href="messageHref">Message seller</a>
            </div>

            <!-- A shelf this long is quicker to jump around than to scroll, so
                 the chips get a standing list beside the menu as well. -->
            <nav v-if="categories.length >= 6" class="shop-aside__cats" aria-label="Jump to a category">
              <h2>Browse categories</h2>
              <ul>
                <li>
                  <button type="button" :class="{ 'is-on': activeCategory === ALL }" @click="pick(ALL)">All <b>{{ matchTotal }}</b></button>
                </li>
                <li v-for="category in categories" :key="category.id">
                  <button type="button" :class="{ 'is-on': activeCategory === category.id }" @click="pick(category.id)">
                    {{ category.name }} <b>{{ countByCategory.get(category.id) ?? 0 }}</b>
                  </button>
                </li>
              </ul>
            </nav>
          </div>
        </aside>
      </div>
    </main>

    <p class="shop-toast" :class="{ 'shop-toast--on': savedNotice, 'shop-toast--raised': store && itemCount > 0 }" role="status" aria-live="polite">{{ savedNotice }}</p>

    <button v-if="store && itemCount > 0" type="button" class="shop-bar" @click="sheetOpen = true">
      <span class="shop-bar__basket"><ShoppingCart :size="23" /><b>{{ itemCount }}</b></span>
      <span>{{ itemCount }} {{ itemCount === 1 ? 'item' : 'items' }} · {{ formatCurrency(itemsTotalCents) }}</span>
      <strong>View Cart <ChevronRight :size="18" /></strong>
    </button>

    <div v-if="sheetOpen" class="shop-sheet" role="dialog" aria-modal="true" aria-label="Your cart">
      <div class="shop-sheet__scrim" @click="sheetOpen = false" />
      <div class="shop-sheet__panel">
        <button type="button" class="shop-sheet__close" aria-label="Close cart" @click="sheetOpen = false"><X :size="20" /></button>
        <ShopCart :lines="lines" :merchant-image-url="shopPhoto" :delivery-fee-cents="deliveryFeeCents" :base-fee-cents="DELIVERY_BASE_FEE_CENTS" :deliverable="deliverable" @add="add" @decrement="cart.decrement" @remove="cart.remove" @clear="cart.clear()" @checkout="checkout" @set-address="sheetOpen = false; delivery.openDialog()" />
      </div>
    </div>

    <div v-if="pending" class="shop-sheet shop-sheet--center" role="alertdialog" aria-modal="true" aria-labelledby="swap-title">
      <div class="shop-sheet__scrim" @click="pending = null" />
      <div class="shop-confirm">
        <h2 id="swap-title">Start a new cart?</h2>
        <p>Your cart has items from <strong>{{ basketShop?.name || 'another shop' }}</strong>. Adding {{ pending.name }} will remove them, because an order can only come from one shop.</p>
        <div class="shop-confirm__actions"><button type="button" class="shop-btn shop-btn--ghost" @click="pending = null">Keep my cart</button><button type="button" class="shop-btn" @click="startNewBasket">Start new cart</button></div>
      </div>
    </div>
  </div>
</template>

<style scoped src="./store-menu.css"></style>
