<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowLeft,
  Bike,
  Check,
  ChevronRight,
  CircleDollarSign,
  Clock3,
  Heart,
  HeartHandshake,
  Leaf,
  MapPin,
  MessageCircle,
  Minus,
  Phone,
  Plus,
  Search,
  Share2,
  ShieldCheck,
  SlidersHorizontal,
  ShoppingBag,
  ShoppingBasket,
  ShoppingCart,
  Star,
  Store,
  Truck,
  UserCircle,
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
import ProductArt from '@pos/web/landing/ProductArt.vue'
import MenuCard from './MenuCard.vue'
import ShopCart from './ShopCart.vue'

const props = defineProps<{
  slug: string
  store: StoreSummary | null
  directoryFailed: boolean
}>()

const ALL = 'all'
const PREVIEW_COUNT = 3
const catalog = useStorefrontCatalog()
const categories = useStockedCategories()
const cart = useStorefrontCart()
const delivery = useDeliveryLocation()
const mainSite = mainSiteOrigin()

const shopName = computed(() => catalog.shop?.name || props.store?.name || 'This shop')
const shopKind = computed(() => catalog.shop?.businessTypeLabel || props.store?.businessTypeLabel || '')
const shopAddress = computed(() => catalog.shop?.address || props.store?.address || '')
const shopPhoto = computed(() => catalog.shop?.imageUrl || props.store?.imageUrl || '')
const coverPhoto = computed(() => shopPhoto.value || '/storefront/market-stall-cover.jpg')
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
  const place = shopAddress.value ? ` in ${shopAddress.value}` : ''
  return `Shop everyday favorites from this ${kind}${place}. Order for local delivery or convenient pickup.`
})

watch(shopName, (name) => (document.title = `${name} — Omaykan`), { immediate: true })

const shareUrl = computed(() =>
  storefrontUrl(props.slug, { rootDomain: SHOP_ROOT_DOMAIN, origin: mainSite || window.location.origin }),
)
const copied = ref(false)
const favorite = ref(false)
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

const activeCategory = ref(ALL)
const searchTerm = ref('')
const menuTop = ref<HTMLElement | null>(null)
const needle = computed(() => searchTerm.value.trim().toLowerCase())

function pillIcon(name: string) {
  return categoryIcon(name) ?? ShoppingBasket
}

const sections = computed(() => {
  const matches = (product: Product) =>
    needle.value === '' ||
    product.name.toLowerCase().includes(needle.value) ||
    (product.brand ?? '').toLowerCase().includes(needle.value)

  return categories.value
    .filter((category) => activeCategory.value === ALL || category.id === activeCategory.value)
    .map((category) => {
      const products = catalog.products.filter((product) => product.categoryId === category.id && matches(product))
      const capped = activeCategory.value === ALL && needle.value === ''
      return { category, total: products.length, products: capped ? products.slice(0, PREVIEW_COUNT) : products }
    })
    .filter((section) => section.total > 0)
})

const featuredProducts = computed(() => catalog.products.slice(0, 3))
const sortMode = ref<'popular' | 'name' | 'price-low' | 'price-high'>('popular')
const desktopProducts = computed(() => {
  const products = catalog.products.filter((product) => {
    const categoryMatches = activeCategory.value === ALL || product.categoryId === activeCategory.value
    const searchMatches =
      needle.value === '' ||
      product.name.toLowerCase().includes(needle.value) ||
      (product.brand ?? '').toLowerCase().includes(needle.value)
    return categoryMatches && searchMatches
  })
  const sorted = [...products]
  if (sortMode.value === 'name') sorted.sort((a, b) => a.name.localeCompare(b.name))
  if (sortMode.value === 'price-low') sorted.sort((a, b) => a.priceCents - b.priceCents)
  if (sortMode.value === 'price-high') sorted.sort((a, b) => b.priceCents - a.priceCents)
  return sorted.slice(0, 12)
})

function pick(categoryId: string) {
  activeCategory.value = categoryId
  searchTerm.value = ''
  void nextTick(() => menuTop.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function clearSearch() {
  searchTerm.value = ''
}

function resetMenuFilters() {
  activeCategory.value = ALL
  searchTerm.value = ''
  void nextTick(() => menuTop.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
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
const messageHref = computed(() => {
  const params = new URLSearchParams({ section: 'messages', shop: props.slug })
  if (props.store?.storeCode) params.set('store', props.store.storeCode)
  params.set('name', shopName.value)
  return `${mainSite}/account?${params.toString()}`
})
const sheetOpen = ref(false)

function checkout() {
  window.location.href = cartUrl('checkout')
}
function reload() {
  window.location.reload()
}
function onKey(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (pending.value) pending.value = null
  else if (sheetOpen.value) sheetOpen.value = false
}

watch(sheetOpen, (open) => (document.body.style.overflow = open ? 'hidden' : ''))
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
  { icon: HeartHandshake, title: 'Shop direct', text: 'Your order goes straight to the seller.' },
  { icon: ShoppingBag, title: 'Pickup ready', text: 'Collect it when it works for you.' },
  { icon: Bike, title: 'Local delivery', text: 'Delivered by riders in your area.' },
]
</script>

<template>
  <div class="fd shop">
    <header v-if="store" class="shop-desktop-header">
      <a class="shop-desktop-brand" :href="`${mainSite}/`" aria-label="Omaykan home">
        <img class="shop-desktop-brand__mark" src="/logo-mark.png" alt="" />
        <span><img src="/logo-wordmark.png" alt="Omaykan" /><small>Good food brings us closer</small></span>
      </a>
      <label class="shop-desktop-search">
        <Search :size="19" :stroke-width="2" />
        <input v-model="searchTerm" type="search" placeholder="Search for fresh food, stores, or categories..." aria-label="Search the store" @input="activeCategory = ALL" />
      </label>
      <nav class="shop-desktop-nav" aria-label="Main navigation">
        <a :href="`${mainSite}/`">Home</a>
        <a href="#catalog">Categories</a>
        <a href="#featured-title">Fresh Picks</a>
        <a :href="`${mainSite}/seller/signup`">For Sellers</a>
        <a :href="`${mainSite}/about`">About</a>
      </nav>
      <div class="shop-desktop-account">
        <a :href="`${mainSite}/account`"><UserCircle :size="28" :stroke-width="1.8" /> Sign In</a>
        <span aria-hidden="true" />
        <a class="shop-desktop-cart-link" :href="cartHref" aria-label="Open cart">
          <ShoppingCart :size="27" :stroke-width="1.8" />
          <b v-if="itemCount">{{ itemCount }}</b>
        </a>
      </div>
    </header>

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
      <div class="shop-main-column">
      <section class="shop-hero" aria-labelledby="shop-name">
        <div class="shop-hero__cover">
          <img :src="coverPhoto" alt="" />
          <button type="button" class="shop-float shop-float--back" aria-label="Go back" @click="goBack">
            <ArrowLeft :size="21" :stroke-width="2" />
          </button>
          <div class="shop-hero__tools">
            <button type="button" class="shop-float" :class="{ 'shop-float--active': favorite }" :aria-label="favorite ? 'Remove store from favorites' : 'Add store to favorites'" :aria-pressed="favorite" @click="favorite = !favorite">
              <Heart :size="20" :stroke-width="2" :fill="favorite ? 'currentColor' : 'none'" />
            </button>
            <button type="button" class="shop-float" :aria-label="copied ? 'Store link copied' : 'Share store'" @click="share">
              <Check v-if="copied" :size="20" :stroke-width="2.2" />
              <Share2 v-else :size="20" :stroke-width="2" />
            </button>
            <a class="shop-float shop-float--cart" :href="cartHref" aria-label="Open cart">
              <ShoppingCart :size="21" :stroke-width="2" />
              <span v-if="itemCount" class="shop-float__badge">{{ itemCount }}</span>
            </a>
          </div>
          <p class="shop-hero__motto" aria-hidden="true">Good food,<br />brighter days</p>
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
            <span><ShieldCheck :size="14" /> Local seller</span>
          </div>
          <p v-if="shopAddress" class="shop-profile__address"><MapPin :size="16" :stroke-width="2.2" /> {{ shopAddress }}</p>
          <div class="shop-service-row" aria-label="Order options">
            <button type="button" class="shop-service" @click="delivery.openDialog()"><Truck :size="16" /> {{ deliveryLabel }}</button>
            <span class="shop-service"><ShoppingBag :size="16" /> Pickup available</span>
          </div>
          <div class="shop-profile__actions">
            <a class="shop-action shop-action--primary" :href="messageHref"><MessageCircle :size="18" /> Message</a>
            <button type="button" class="shop-action shop-action--secondary" @click="share">
              <Check v-if="copied" :size="17" />
              <Share2 v-else :size="17" />
              {{ copied ? 'Link copied' : 'Share Store' }}
            </button>
          </div>
          <p class="shop-profile__summary">{{ shopSummary }}</p>
          <div class="shop-payments">
            <span>We accept:</span>
            <strong><CircleDollarSign :size="16" /> Cash</strong>
            <strong><span class="shop-payments__g">G</span> GCash</strong>
          </div>
          <nav v-if="categories.length" class="shop-desktop-pills" aria-label="Product categories">
            <button type="button" :class="{ 'is-active': activeCategory === ALL }" @click="pick(ALL)">All</button>
            <button v-for="category in categories" :key="category.id" type="button" :class="{ 'is-active': activeCategory === category.id }" @click="pick(category.id)">
              <component :is="pillIcon(category.name)" :size="16" :stroke-width="1.9" /> {{ category.name }}
            </button>
          </nav>
        </div>
      </section>

      <div class="shop-content">
        <div class="shop-search">
          <Search :size="19" :stroke-width="2" />
          <input v-model="searchTerm" type="search" placeholder="Search this store" aria-label="Search this store" @input="activeCategory = ALL" />
          <button v-if="searchTerm" type="button" aria-label="Clear search" @click="clearSearch"><X :size="16" /></button>
        </div>
        <button type="button" class="shop-filter" aria-label="Reset menu filters" @click="resetMenuFilters"><SlidersHorizontal :size="20" /></button>

        <nav v-if="categories.length" class="shop-pills" aria-label="Menu sections">
          <button type="button" class="shop-pill" :class="{ 'shop-pill--on': activeCategory === ALL }" :aria-pressed="activeCategory === ALL" @click="pick(ALL)">All</button>
          <button v-for="category in categories" :key="category.id" type="button" class="shop-pill" :class="{ 'shop-pill--on': activeCategory === category.id }" :aria-pressed="activeCategory === category.id" @click="pick(category.id)">
            <component :is="pillIcon(category.name)" :size="16" :stroke-width="1.9" /> {{ category.name }}
          </button>
        </nav>
        <div ref="menuTop" class="shop-anchor" />

        <!-- The shop's own pause: the menu stays up so people can plan, and
             this says why checkout will not take the order yet. -->
        <p v-if="catalog.shop?.orderingPausedMessage" class="shop-paused" role="status">{{ catalog.shop.orderingPausedMessage }}</p>
        <p v-if="needle" class="shop-searching"><Search :size="16" /> Results for “{{ searchTerm.trim() }}” <button type="button" @click="clearSearch"><X :size="14" /> Clear</button></p>

        <div v-if="catalog.loading" class="shop-grid" aria-busy="true" aria-label="Loading the menu"><div v-for="n in 6" :key="n" class="shop-skeleton" /></div>
        <!-- Suspended or unpaid, and deliberately not told apart: to a shopper
             the shop is simply closed. No "Try again" — nothing a retry can fix. -->
        <div v-else-if="catalog.closed" class="shop-empty" role="status"><p>This shop isn't taking orders right now.</p></div>
        <div v-else-if="catalog.error" class="shop-empty"><p>{{ catalog.error }}</p><button type="button" class="shop-btn" @click="retryStorefrontCatalog()">Try again</button></div>
        <div v-else-if="sections.length === 0" class="shop-empty"><p v-if="needle">Nothing on this menu matches “{{ searchTerm.trim() }}”.</p><p v-else>{{ shopName }} hasn't put anything on its menu yet. Check back soon.</p></div>

        <template v-else>
          <section v-if="activeCategory === ALL && !needle && featuredProducts.length" class="shop-featured" aria-labelledby="featured-title">
            <header class="shop-section__head">
              <span class="shop-section__icon shop-section__icon--star"><Star :size="20" fill="currentColor" /></span>
              <div><h2 id="featured-title"><span class="shop-mobile-only">Shop favorites</span><span class="shop-desktop-only">Best Sellers</span></h2><p><span class="shop-mobile-only">Featured from this store</span><span class="shop-desktop-only">Our most loved items</span></p></div>
              <button type="button" class="shop-featured__all" @click="resetMenuFilters">See All <ChevronRight :size="15" /></button>
            </header>
            <div class="shop-featured__grid">
              <MenuCard v-for="product in featuredProducts" :id="`featured-${product.id}`" :key="product.id" :product="product" :quantity="quantityOf(product.id)" :category-name="categories.find((category) => category.id === product.categoryId)?.name" :merchant-image-url="shopPhoto" featured @add="add" @decrement="cart.decrement" />
            </div>
          </section>

          <section v-for="section in sections" :key="section.category.id" class="shop-section shop-section--mobile" :aria-labelledby="`sec-${section.category.id}`">
            <header class="shop-section__head">
              <span class="shop-section__icon"><component :is="pillIcon(section.category.name)" :size="20" :stroke-width="1.9" /></span>
              <h2 :id="`sec-${section.category.id}`">{{ section.category.name }}</h2>
              <button v-if="section.products.length < section.total" type="button" class="shop-section__more" @click="pick(section.category.id)">See all <ChevronRight :size="15" /></button>
            </header>
            <div class="shop-grid">
              <MenuCard v-for="product in section.products" :id="`item-${product.id}`" :key="product.id" class="shop-item" :product="product" :quantity="quantityOf(product.id)" :category-name="section.category.name" :merchant-image-url="shopPhoto" @add="add" @decrement="cart.decrement" />
            </div>
          </section>

          <section id="catalog" class="shop-products-desktop" aria-labelledby="all-products-title">
            <header>
              <h2 id="all-products-title">All Products</h2>
              <label>Sort by:
                <select v-model="sortMode" aria-label="Sort products">
                  <option value="popular">Popular</option>
                  <option value="name">Name</option>
                  <option value="price-low">Price: Low to high</option>
                  <option value="price-high">Price: High to low</option>
                </select>
              </label>
            </header>
            <div class="shop-products-desktop__grid">
              <MenuCard v-for="product in desktopProducts" :id="`desktop-item-${product.id}`" :key="product.id" :product="product" :quantity="quantityOf(product.id)" :category-name="categories.find((category) => category.id === product.categoryId)?.name" :merchant-image-url="shopPhoto" @add="add" @decrement="cart.decrement" />
            </div>
          </section>
        </template>

        <section class="shop-details shop-details--mobile" aria-label="Store details">
          <div><span class="shop-details__icon"><MapPin :size="19" /></span><p><strong>Location</strong><small>{{ shopAddress || 'Address available at checkout' }}</small></p></div>
          <div><span class="shop-details__icon"><Truck :size="19" /></span><p><strong>Delivery</strong><small>{{ deliveryLabel }}</small></p></div>
          <div><span class="shop-details__icon"><CircleDollarSign :size="19" /></span><p><strong>Payment</strong><small>Cash or GCash</small></p></div>
        </section>

        <section class="shop-why shop-why--mobile" aria-labelledby="shop-why-title">
          <h2 id="shop-why-title">Why shop here?</h2>
          <ul class="shop-promises">
            <li v-for="item in promises" :key="item.title"><span class="shop-promises__icon"><component :is="item.icon" :size="20" /></span><span><strong>{{ item.title }}</strong><small>{{ item.text }}</small></span></li>
          </ul>
        </section>

        <footer class="shop-footer shop-footer--mobile">
          <p class="shop-script">Good food, brighter days</p>
          <div><strong>{{ shopName }}</strong><small>{{ shopKind || 'Local seller' }} · Powered by <b>omaykan</b></small></div>
        </footer>
      </div>
      </div>

      <aside class="shop-desktop-aside" aria-label="Store order summary and information">
        <section class="shop-desktop-card shop-desktop-cart">
          <header>
            <h2>Your Cart ({{ lines.length }})</h2>
            <a :href="cartHref">View Cart <ChevronRight :size="16" /></a>
          </header>
          <p v-if="lines.length === 0" class="shop-desktop-cart__empty">Your cart is ready for something fresh.</p>
          <ul v-else>
            <li v-for="line in lines" :key="line.product.id">
              <div class="shop-desktop-cart__thumb"><ProductArt :product="line.product" :merchant-image-url="shopPhoto" :size="22" /></div>
              <div class="shop-desktop-cart__item"><strong>{{ line.product.name }}</strong><small>{{ line.quantity }} {{ line.product.unitLabel || 'item' }}</small></div>
              <b>{{ formatCurrency(line.product.priceCents * line.quantity) }}</b>
              <div class="shop-desktop-cart__step" role="group" :aria-label="`${line.product.name} quantity`">
                <button type="button" :aria-label="`Remove one ${line.product.name}`" @click="cart.decrement(line.product.id)"><Minus :size="14" /></button>
                <span>{{ line.quantity }}</span>
                <button type="button" :aria-label="`Add one more ${line.product.name}`" @click="add(line.product)"><Plus :size="14" /></button>
              </div>
            </li>
          </ul>
          <div v-if="lines.length" class="shop-desktop-cart__subtotal"><span>Subtotal</span><strong>{{ formatCurrency(itemsTotalCents) }}</strong></div>
          <a class="shop-desktop-cart__button" :href="cartHref">View Cart <ChevronRight :size="18" /></a>
        </section>

        <section class="shop-desktop-card shop-desktop-info">
          <h2>Store Information</h2>
          <div class="shop-desktop-info__grid">
            <div><span><MapPin :size="20" /></span><p><strong>Location</strong><small>{{ shopAddress || 'Address available at checkout' }}</small></p></div>
            <div><span><Clock3 :size="20" /></span><p><strong>Operating Hours</strong><small>Contact the store for today's hours</small></p></div>
            <div><span><Truck :size="20" /></span><p><strong>Delivery</strong><small>{{ deliveryLabel }}</small></p></div>
            <div><span><Phone :size="20" /></span><p><strong>Contact</strong><small><a :href="messageHref">Message this store</a></small></p></div>
          </div>
        </section>

        <section class="shop-desktop-card shop-desktop-why">
          <h2>Why shop here?</h2>
          <ul class="shop-promises">
            <li v-for="item in promises" :key="item.title"><span class="shop-promises__icon"><component :is="item.icon" :size="20" /></span><span><strong>{{ item.title }}</strong><small>{{ item.text }}</small></span></li>
          </ul>
          <div class="shop-desktop-signoff">
            <p class="shop-script">Good food, brighter days</p>
            <div><strong>{{ shopName }}</strong><small>Proud to be part of</small><img src="/logo-wordmark.png" alt="Omaykan" /></div>
          </div>
        </section>
      </aside>
    </main>

    <footer v-if="store" class="shop-desktop-footer">
      <a :href="`${mainSite}/`"><img src="/logo-wordmark.png" alt="Omaykan" /><span>Good food brings us closer</span></a>
      <nav aria-label="Footer navigation"><a :href="`${mainSite}/about`">About</a><a :href="`${mainSite}/seller/signup`">For Sellers</a><a :href="`${mainSite}/about`">Help</a><a :href="`${mainSite}/about`">Terms</a><a :href="`${mainSite}/about`">Privacy</a></nav>
      <small>Powered by <strong>Omaykan</strong></small>
    </footer>

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
