<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  MessageSquare,
  ShoppingCart,
  Tag,
  Truck,
  Wallet,
} from '@lucide/vue'
import {
  calculateTax,
  discountPercent,
  formatCurrency,
  type Category,
  type Product,
} from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { ORG_SLUG, STORE_ADDRESS, STORE_CODE } from '@pos/web/commerce/context'
import {
  DELIVERY_BASE_FEE_CENTS,
  DELIVERY_BASE_KM,
  DELIVERY_PER_KM_CENTS,
} from '@pos/web/commerce/delivery'
import { MESSAGING_ENABLED } from '@pos/web/commerce/features'
import { useSavedProducts } from '@pos/web/commerce/favorites'
import { ALL_AISLES } from './listing'
import ProductArt from './ProductArt.vue'
import ProductRow from './ProductRow.vue'

// The product detail face of the landing page: the pack at full size with its
// other views under it, everything a shopper needs to commit, and the rest of
// the aisle underneath.
//
// It is a face of the landing page rather than its own Vite entry because the
// whole storefront is one document — search, aisle and product are all URL
// state on it (?q=, ?category=, ?product=), so opening a product costs no
// reload and Back walks straight out of it.
//
// The right column is four stacked cards: buy, why buying here is safe, who
// you are buying from, and what the thing is. That is the order of the
// questions a shopper actually asks on a marketplace where they hand cash to a
// rider for goods from a shop they have never been to.

const props = defineProps<{
  product: Product
  /** The product's aisle, for the breadcrumb — null if the catalog dropped it. */
  category: Category | null
  /** The rest of the aisle, minus this product. */
  related: Product[]
}>()

const emit = defineEmits<{
  back: []
  category: [categoryId: string]
  select: [productId: string]
}>()

const cart = useStorefrontCart()
const saved = useSavedProducts()

/**
 * Who the shopper is buying from. Read off the catalog rather than passed in
 * as a prop, the same way the cart is: one storefront is one counter, so the
 * shop is a property of the shelf and not of the product sitting on it.
 */
const catalog = useStorefrontCatalog()
const shop = computed(() => catalog.shop)

/**
 * The address the API knows beats the one baked into the build: a store that
 * moves updates Settings, not a redeploy. The env var stays as the fallback
 * for the demo shelf, which has no shop record behind it.
 */
const shopAddress = computed(() => shop.value?.address ?? (STORE_ADDRESS || undefined))

/** Its own initials when it has no photo — not a grey disc. Mirrors ShopDirectory. */
const shopInitials = computed(() =>
  (shop.value?.name ?? '')
    .split(/\s+/)
    .filter((word) => /[a-z0-9]/i.test(word))
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join(''),
)

/**
 * "Everything else this shop sells" — which on a single-tenant storefront is
 * this same page's listing face, not another site. So no new tab, and no
 * external-link glyph promising one.
 */
const shopHref = computed(() => `${window.location.pathname}?category=${ALL_AISLES}`)

/**
 * "Do you have this in a bigger size?" is a question for the shop. It goes
 * through the portal, which signs a shopper in first if they need to and keeps
 * the shop in the URL while they do. Absent on the demo shelf, which has no
 * shop to answer.
 */
const messageShopHref = computed(() => {
  if (!MESSAGING_ENABLED || !shop.value || !ORG_SLUG || !STORE_CODE) return ''
  const params = new URLSearchParams({
    section: 'messages',
    shop: ORG_SLUG,
    store: STORE_CODE,
    name: shop.value.name,
  })
  return `/account?${params.toString()}`
})

const quantity = ref(1)
const justAdded = ref(false)
let resetTimer = 0

const discount = computed(() => discountPercent(props.product))
const soldOut = computed(() => props.product.outOfStock === true)
const isSaved = computed(() => saved.isSaved(props.product.id))

/** Weighted goods are priced by the unit they're sold in, not per piece. */
const priceSuffix = computed(() =>
  props.product.kind === 'weighted' ? props.product.unitLabel ?? '' : '',
)

/**
 * Prices in the catalog are tax-exclusive — the cart adds VAT on top, and the
 * API recomputes it server-side — so the detail says so rather than letting
 * the total surprise anyone at checkout.
 */
const taxCents = computed(() => calculateTax(props.product.priceCents, props.product.taxRate))

/** Undefined stock means the store doesn't track it, which is not "none left". */
const lowStock = computed(() => {
  const qty = props.product.stockQty
  const threshold = props.product.lowStockThreshold
  if (soldOut.value || qty === undefined || threshold === undefined) return false
  return qty > 0 && qty <= threshold
})

const maxQuantity = computed(() => {
  const qty = props.product.stockQty
  return qty === undefined || qty <= 0 ? 99 : Math.min(qty, 99)
})

const inCart = computed(
  () => cart.cartLines.value.find((line) => line.product.id === props.product.id)?.quantity ?? 0,
)

// ── The gallery ───────────────────────────────────────────────────────────
// The primary shot has always lived on its own field, because every card,
// order line and directory tile reads it; the extra views come after it.

const photos = computed(() => {
  const all = [props.product.imageUrl, ...(props.product.photoUrls ?? [])]
    .map((url) => (typeof url === 'string' ? url.trim() : ''))
    .filter((url) => url !== '')
  // A merchant who uploads the same shot twice gets one thumbnail, not two
  // identical ones with an arrow between them.
  return Array.from(new Set(all))
})

/**
 * Photos whose url does not load, dropped from the strip rather than left as
 * the browser's broken-image glyph. A product whose only photo is dead then
 * falls through to the drawn stand-in the aisle listing uses, instead of to a
 * blank white frame.
 */
const brokenPhotos = ref(new Set<string>())
const livePhotos = computed(() => photos.value.filter((url) => !brokenPhotos.value.has(url)))

const activeIndex = ref(0)
const activePhoto = computed(() => livePhotos.value[activeIndex.value] ?? '')
const hasGallery = computed(() => livePhotos.value.length > 1)

function showPhoto(index: number) {
  if (index < 0 || index >= livePhotos.value.length) return
  activeIndex.value = index
  endZoom()
}

function stepPhoto(direction: -1 | 1) {
  showPhoto(activeIndex.value + direction)
}

function notePhotoBroken(url: string) {
  brokenPhotos.value = new Set(brokenPhotos.value).add(url)
  // Whatever slid into this slot is showing now; if the strip ran out, back up.
  if (activeIndex.value >= livePhotos.value.length) {
    activeIndex.value = Math.max(0, livePhotos.value.length - 1)
  }
}

// Opening a second product from the related shelf reuses this component, so the
// stepper and the gallery reset rather than carry the last product's state.
watch(
  () => props.product.id,
  () => {
    quantity.value = 1
    justAdded.value = false
    activeIndex.value = 0
    brokenPhotos.value = new Set()
    endZoom()
    window.clearTimeout(resetTimer)
  },
)

// ── Hover-to-zoom ─────────────────────────────────────────────────────────
/**
 * The pack photo is the only place a shopper can read a label they'd otherwise
 * pick up and turn over, so pointing at a corner of it magnifies that corner
 * in place rather than opening a lightbox. The frame already clips, so scaling
 * the <img> about the cursor is the whole trick — no second copy to download.
 */
const ZOOM_SCALE = 2.6

const zoomImage = ref<HTMLImageElement | null>(null)
const zooming = ref(false)
const zoomOrigin = ref('50% 50%')

// Touch and pen have no hover to track, and a phone's tap would leave the photo
// stuck at 2.6x with no way out, so the whole behaviour is mouse-only.
const pointerFine = ref(false)
const hoverQuery =
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(hover: hover) and (pointer: fine)')
    : null

function readPointer() {
  pointerFine.value = hoverQuery?.matches ?? false
}

onMounted(() => {
  readPointer()
  hoverQuery?.addEventListener('change', readPointer)
})
onBeforeUnmount(() => {
  hoverQuery?.removeEventListener('change', readPointer)
  window.clearTimeout(resetTimer)
})

const canZoom = computed(() => pointerFine.value && activePhoto.value !== '')

const zoomStyle = computed(() =>
  zooming.value
    ? { transform: `scale(${ZOOM_SCALE})`, transformOrigin: zoomOrigin.value }
    : undefined,
)

function trackZoom(event: MouseEvent) {
  const img = zoomImage.value
  if (!canZoom.value || !img) return
  // Measure against the <img>, not the frame: object-fit leaves letterboxing
  // and the frame has padding, so frame-relative coordinates would drift the
  // magnified point away from whatever the cursor is actually over.
  const rect = img.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) return
  const x = ((event.clientX - rect.left) / rect.width) * 100
  const y = ((event.clientY - rect.top) / rect.height) * 100
  // Clamped so the padding around the photo pins the origin to the nearest
  // edge instead of pushing the image out of its own frame.
  zoomOrigin.value = `${clamp(x)}% ${clamp(y)}%`
  zooming.value = true
}

function clamp(percent: number) {
  return Math.min(100, Math.max(0, percent)).toFixed(2)
}

function endZoom() {
  zooming.value = false
  zoomOrigin.value = '50% 50%'
}

// ── The trust strip ───────────────────────────────────────────────────────
// Read off the delivery module rather than written out as prose. The footer
// used to hard-code "₱49", which is a promise that goes stale the day the rate
// changes. Whole pesos, because these are round figures and "₱49.00" reads
// like a receipt rather than a price.
const peso = (cents: number) => `₱${cents / 100}`
const deliveryFrom = peso(DELIVERY_BASE_FEE_CENTS)
const deliveryDetail = `Flat ${deliveryFrom} for the first ${DELIVERY_BASE_KM} km, then ${peso(DELIVERY_PER_KM_CENTS)}/km.`

// ── Actions ───────────────────────────────────────────────────────────────

function step(direction: -1 | 1) {
  const next = quantity.value + direction
  if (next < 1 || next > maxQuantity.value) return
  quantity.value = next
}

function addToCart() {
  if (soldOut.value) return
  cart.add(props.product, quantity.value)
  justAdded.value = true
  window.clearTimeout(resetTimer)
  resetTimer = window.setTimeout(() => { justAdded.value = false }, 2200)
}
</script>

<template>
  <section class="pdp">
    <nav class="pdp__crumbs" aria-label="Breadcrumb">
      <a href="/" @click.prevent="emit('back')">All categories</a>
      <span aria-hidden="true">›</span>
      <template v-if="category">
        <a :href="`/?category=${category.id}`" @click.prevent="emit('category', category.id)">
          {{ category.name }}
        </a>
        <span aria-hidden="true">›</span>
      </template>
      <span class="pdp__here">{{ product.name }}</span>
    </nav>

    <div class="pdp__body">
      <!-- ── The pack ──────────────────────────────────────────────────── -->
      <div class="pdp__gallery">
        <!-- Contain, not cover: a detail shot has to show the whole pack — the
             weight, the variant — where a card only needs to be recognisable. -->
        <div
          class="pdp__frame"
          :class="{ 'pdp__frame--zoomable': canZoom, 'pdp__frame--zooming': zooming }"
          @mousemove="trackZoom"
          @mouseleave="endZoom"
        >
          <span v-if="discount !== null" class="pdp__flag">SALE — save {{ discount }}%</span>

          <img
            v-if="activePhoto"
            ref="zoomImage"
            :key="activePhoto"
            :src="activePhoto"
            :alt="product.name"
            :style="zoomStyle"
            draggable="false"
            @error="notePhotoBroken(activePhoto)"
          />
          <!-- No photograph at all: the aisle's own glyph, the same stand-in
               the listing draws, rather than an empty white square. -->
          <ProductArt
            v-else
            :product="{ ...product, imageUrl: undefined }"
            :category-name="category?.name ?? ''"
            :merchant-image-url="shop?.imageUrl ?? ''"
            :size="96"
          />

          <button
            type="button"
            class="pdp__save"
            :class="{ 'pdp__save--on': isSaved }"
            :aria-pressed="isSaved"
            :aria-label="isSaved ? `Remove ${product.name} from your wishlist` : `Save ${product.name} to your wishlist`"
            @click="saved.toggle(product)"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" :fill="isSaved ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/></svg>
          </button>

          <span v-if="canZoom" class="pdp__zoomhint" aria-hidden="true">Hover to zoom</span>
        </div>

        <!-- The other views. Only when there is more than one — a lone
             thumbnail under its own photo is a control that does nothing. -->
        <div v-if="hasGallery" class="pdp__thumbs">
          <button
            type="button"
            class="pdp__nav"
            aria-label="Previous photo"
            :disabled="activeIndex === 0"
            @click="stepPhoto(-1)"
          >
            <ChevronLeft :size="18" :stroke-width="2" />
          </button>

          <ul class="pdp__thumblist">
            <li v-for="(photo, index) in livePhotos" :key="photo">
              <button
                type="button"
                class="pdp__thumb"
                :class="{ 'pdp__thumb--on': index === activeIndex }"
                :aria-current="index === activeIndex"
                :aria-label="`Show photo ${index + 1} of ${livePhotos.length}`"
                @click="showPhoto(index)"
                @keydown.left.prevent="stepPhoto(-1)"
                @keydown.right.prevent="stepPhoto(1)"
              >
                <img :src="photo" alt="" loading="lazy" @error="notePhotoBroken(photo)" />
              </button>
            </li>
          </ul>

          <button
            type="button"
            class="pdp__nav"
            aria-label="Next photo"
            :disabled="activeIndex >= livePhotos.length - 1"
            @click="stepPhoto(1)"
          >
            <ChevronRight :size="18" :stroke-width="2" />
          </button>
        </div>
      </div>

      <!-- ── The buy column ────────────────────────────────────────────── -->
      <div class="pdp__info">
        <a
          v-if="category"
          class="pdp__eyebrow"
          :href="`/?category=${category.id}`"
          @click.prevent="emit('category', category.id)"
        >{{ category.name }}</a>

        <h1 class="pdp__title">{{ product.name }}</h1>

        <p v-if="product.unitLabel" class="pdp__unit">{{ product.unitLabel }}</p>

        <div class="pdp__prices">
          <span class="pdp__price" :class="{ 'pdp__price--sale': discount !== null }">
            {{ formatCurrency(product.priceCents) }}<span v-if="priceSuffix" class="pdp__per"> {{ priceSuffix }}</span>
          </span>
          <span v-if="discount !== null" class="pdp__was">
            {{ formatCurrency(product.compareAtPriceCents!) }}
          </span>
          <span v-if="discount !== null" class="pdp__savetag">Save {{ discount }}%</span>
        </div>

        <p v-if="product.taxRate > 0" class="pdp__tax">
          + {{ formatCurrency(taxCents) }} VAT at checkout
        </p>

        <p class="pdp__stock" :class="{ 'pdp__stock--out': soldOut, 'pdp__stock--low': lowStock }">
          <span class="pdp__dot" aria-hidden="true"></span>
          <template v-if="soldOut">Sold out today</template>
          <template v-else-if="lowStock">Only {{ product.stockQty }} left today</template>
          <template v-else>In stock</template>
        </p>

        <div class="pdp__buy">
          <div class="pdp__stepper" role="group" aria-label="Quantity">
            <button
              type="button"
              aria-label="One fewer"
              :disabled="soldOut || quantity <= 1"
              @click="step(-1)"
            >−</button>
            <span class="pdp__qty" aria-live="polite">{{ quantity }}</span>
            <button
              type="button"
              aria-label="One more"
              :disabled="soldOut || quantity >= maxQuantity"
              @click="step(1)"
            >+</button>
          </div>

          <button type="button" class="pdp__add" :disabled="soldOut" @click="addToCart">
            <ShoppingCart v-if="!soldOut" :size="18" :stroke-width="2" aria-hidden="true" />
            <template v-if="soldOut">Sold out</template>
            <template v-else-if="justAdded">Added to cart ✓</template>
            <template v-else>
              Add {{ quantity }} to cart — {{ formatCurrency(product.priceCents * quantity) }}
            </template>
          </button>
        </div>

        <p v-if="inCart > 0" class="pdp__incart">{{ inCart }} in your cart</p>

        <!-- Why buying from a counter you have never seen is safe. The fee is
             read off the delivery module, not written out as prose: a
             hard-coded "₱49" is a promise that goes stale the day the rate
             changes. -->
        <ul class="pdp__trust">
          <li>
            <span class="pdp__trusticon"><Tag :size="17" :stroke-width="1.9" aria-hidden="true" /></span>
            <div>
              <p class="pdp__trusttitle">Shop-set price</p>
              <p class="pdp__trustnote">This shop sets its own price, so there are no extra fees.</p>
            </div>
          </li>
          <li>
            <span class="pdp__trusticon"><Wallet :size="17" :stroke-width="1.9" aria-hidden="true" /></span>
            <div>
              <p class="pdp__trusttitle">Cash or GCash on delivery</p>
              <p class="pdp__trustnote">Pay when the rider arrives — no online payment needed.</p>
            </div>
          </li>
          <li>
            <span class="pdp__trusticon"><Truck :size="17" :stroke-width="1.9" aria-hidden="true" /></span>
            <div>
              <p class="pdp__trusttitle">Delivery starts at {{ deliveryFrom }}</p>
              <p class="pdp__trustnote">{{ deliveryDetail }}</p>
            </div>
          </li>
        </ul>

        <!-- The last two cards sit side by side while the column is wide
             enough for them. Stacking everything left a tall ladder of cards
             down one side of the page and nothing down the other. -->
        <div class="pdp__pair">

        <!-- Who you are buying from. A card and not a fact row: a shopper who
             has never been to this counter is handing cash to a rider on its
             behalf, so who and where it is has to read as a statement about
             the seller rather than as metadata next to a barcode. -->
        <section v-if="shop" class="pdp__shop">
          <div class="pdp__shopmark" aria-hidden="true">
            <img v-if="shop.imageUrl" :src="shop.imageUrl" alt="" />
            <span v-else>{{ shopInitials }}</span>
          </div>

          <div class="pdp__shopwho">
            <p class="pdp__shoplabel">Sold by</p>
            <p class="pdp__shopname">{{ shop.name }}</p>
            <p v-if="shop.businessTypeLabel" class="pdp__shopkind">{{ shop.businessTypeLabel }}</p>
            <dl v-if="shop.ownerName || shopAddress" class="pdp__shopfacts">
              <div v-if="shop.ownerName">
                <dt>Store owner</dt>
                <dd>{{ shop.ownerName }}</dd>
              </div>
              <div v-if="shopAddress">
                <dt>Store location</dt>
                <dd>{{ shopAddress }}</dd>
              </div>
            </dl>
          </div>

          <div class="pdp__shopacts">
            <a class="pdp__shopbtn" :href="shopHref" @click.prevent="emit('category', ALL_AISLES)">
              View shop
              <ArrowUpRight :size="15" :stroke-width="2" aria-hidden="true" />
            </a>
            <a v-if="messageShopHref" class="pdp__shoplink" :href="messageShopHref">
              <MessageSquare :size="14" :stroke-width="2" aria-hidden="true" />
              Message
            </a>
          </div>
        </section>

        <!-- What the thing is. Every row is something the merchant actually
             filed; a blank one is left out rather than printed as "—". -->
        <section class="pdp__facts">
          <h2 class="pdp__factstitle">Product details</h2>
          <dl>
            <div v-if="product.brand">
              <dt>Brand</dt>
              <dd>{{ product.brand }}</dd>
            </div>
            <div v-if="category">
              <dt>Category</dt>
              <dd>{{ category.name }}</dd>
            </div>
            <div v-if="product.sku">
              <dt>Item code</dt>
              <dd>{{ product.sku }}</dd>
            </div>
            <div v-if="product.barcode">
              <dt>Barcode</dt>
              <dd>{{ product.barcode }}</dd>
            </div>
            <div v-if="product.packagingType">
              <dt>Packaging type</dt>
              <dd>{{ product.packagingType }}</dd>
            </div>
            <div v-if="product.kind === 'weighted'">
              <dt>Sold by</dt>
              <dd>Weight — priced {{ product.unitLabel || 'per unit' }}</dd>
            </div>
          </dl>
        </section>

        </div>
      </div>
    </div>

    <ProductRow
      v-if="related.length > 0"
      :title="category ? `More from ${category.name}` : 'More to shop'"
      :products="related"
      :view-all-href="category ? `/?category=${category.id}` : ''"
      :category-names="category ? { [category.id]: category.name } : {}"
      :merchant-image-url="shop?.imageUrl ?? ''"
      @select="emit('select', $event)"
      @view-all="category && emit('category', category.id)"
    />
  </section>
</template>

<style scoped>
/* The one capped, centred face of the storefront. Everywhere else runs
   full-bleed inside the gutter, which suits a shelf — more columns is more
   goods. This page is a single decision, and a 1900px-wide row of facts about
   one tin of kimchi is mostly whitespace with a sentence at each end. The cap
   is on the whole section, so the related shelf underneath lines up with the
   product above it rather than running out past both edges of it. */
.pdp {
  max-width: 1440px;
  margin: 0 auto 56px;
}

.pdp__crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 22px;
  font-size: 13.5px;
  color: var(--sf-faint);
}
.pdp__crumbs a { color: var(--sf-leaf); font-weight: 600; text-decoration: underline; text-underline-offset: 3px; }
.pdp__crumbs a:hover { color: var(--sf-forest); }
.pdp__here { color: var(--sf-muted); font-weight: 600; }

/* 42/58. The photograph is a square and stops being more informative once it
   is big enough to read a label off, so the spare width goes to the column
   that has rows to lay out — which is what lets the three benefit cards sit
   three across at a comfortable width instead of wrapping every heading. */
.pdp__body {
  display: grid;
  grid-template-columns: minmax(0, 42fr) minmax(0, 58fr);
  gap: 44px;
  align-items: start;
  margin-bottom: 64px;
}

/* ── The pack ─────────────────────────────────────────────────────────── */

/* Sticky, because the right column is four cards tall and the thing being
   bought should not scroll away from the price of it. */
.pdp__gallery { position: sticky; top: 96px; }

.pdp__frame {
  position: relative;
  aspect-ratio: 1 / 1;
  display: grid;
  place-items: center;
  padding: 30px;
  border: 1px solid var(--sf-rule);
  border-radius: 14px;
  /* White, not the page's cream: most catalog photos are cut out on white
     already, and a tinted mat makes them read as a picture of a picture. */
  background: #fff;
  overflow: hidden;
}
.pdp__frame img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  /* Short, so the photo tracks the cursor rather than lagging behind it, but
     not zero — an instant jump on the first pixel of movement reads as a
     glitch. Only the scale-in and scale-out are eased; while zoomed the
     transform-origin is unanimated so tracking stays 1:1. */
  transition: transform 180ms ease-out;
  will-change: transform;
}
.pdp__frame--zooming img { transition: transform 90ms ease-out; }
.pdp__frame--zoomable { cursor: zoom-in; }

.pdp__flag {
  position: absolute;
  top: 0;
  left: 0;
  padding: 7px 14px;
  border-radius: 0 0 10px 0;
  background: var(--sf-clay);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  /* Above the magnified photo, which otherwise slides out from under it. */
  z-index: 2;
}

.pdp__save {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 253, 249, 0.95);
  color: var(--sf-ink);
  box-shadow: 0 2px 10px rgba(23, 35, 28, 0.16);
  cursor: pointer;
  transition: color 150ms, transform 150ms;
}
.pdp__save:hover { color: var(--sf-clay); transform: scale(1.07); }
.pdp__save--on { color: var(--sf-clay); }
.pdp__save:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }

.pdp__zoomhint {
  position: absolute;
  right: 14px;
  bottom: 12px;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(23, 35, 28, 0.62);
  color: #fff;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.03em;
  opacity: 0;
  transition: opacity 160ms;
  pointer-events: none;
}
.pdp__frame:hover .pdp__zoomhint { opacity: 1; }
.pdp__frame--zooming .pdp__zoomhint { opacity: 0; }

/* The strip. Arrows step the selection rather than scrolling the row: with a
   handful of thumbnails "next photo" is what an arrow beside them means, and
   a scroller that will not move because everything already fits reads as
   broken. */
.pdp__thumbs {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.pdp__thumblist {
  flex: 1;
  display: flex;
  justify-content: center;
  gap: 10px;
  min-width: 0;
  margin: 0;
  padding: 2px;
  overflow-x: auto;
  list-style: none;
  scrollbar-width: none;
}
.pdp__thumblist::-webkit-scrollbar { display: none; }

.pdp__thumb {
  display: block;
  width: 74px;
  height: 74px;
  padding: 5px;
  border: 1.5px solid var(--sf-rule);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color 150ms;
}
.pdp__thumb img { display: block; width: 100%; height: 100%; object-fit: contain; }
.pdp__thumb:hover { border-color: var(--sf-sand-deep); }
.pdp__thumb--on { border-color: var(--sf-forest); }
.pdp__thumb:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }

.pdp__nav {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--sf-rule);
  border-radius: 50%;
  background: var(--sf-paper);
  color: var(--sf-ink);
  cursor: pointer;
  transition: border-color 150ms, color 150ms;
}
.pdp__nav:hover:not(:disabled) { border-color: var(--sf-forest); }
.pdp__nav:disabled { color: var(--sf-rule); cursor: not-allowed; }
.pdp__nav:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }

/* ── The buy column ───────────────────────────────────────────────────── */

/* A query container, so the cards inside answer to the width of this column
   and not to the window's. The two are no longer the same thing: at 58% of a
   1440px page this column is ~810px on a wide screen and ~400px on a laptop,
   and the trust strip has to fold on the second while the window is still
   nowhere near a phone. */
.pdp__info {
  container-type: inline-size;
  padding-top: 4px;
}

.pdp__eyebrow {
  display: inline-block;
  margin-bottom: 10px;
  color: var(--sf-leaf);
  font-size: 12.5px;
  font-weight: 800;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  text-decoration: none;
}
.pdp__eyebrow:hover { text-decoration: underline; text-underline-offset: 3px; }

.pdp__title {
  margin: 0 0 6px;
  font-family: var(--sf-serif);
  font-size: clamp(1.7rem, 3.2vw, 2.4rem);
  font-weight: 700;
  line-height: 1.14;
  color: var(--sf-ink);
}

.pdp__unit { margin: 0 0 20px; color: var(--sf-muted); font-size: 14.5px; }

.pdp__prices { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.pdp__price {
  font-size: 2.15rem;
  font-weight: 800;
  color: var(--sf-ink);
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.pdp__price--sale { color: var(--sf-clay); }
.pdp__per { font-size: 1rem; font-weight: 600; color: var(--sf-muted); }
.pdp__was { font-size: 16px; color: var(--sf-faint); text-decoration: line-through; }
.pdp__savetag {
  padding: 3px 9px;
  border: 1px solid var(--sf-clay);
  border-radius: 5px;
  color: var(--sf-clay);
  font-size: 13px;
  font-weight: 700;
}

.pdp__tax { margin: 8px 0 0; color: var(--sf-faint); font-size: 13px; }

/* A pill, not a line of text: "is it actually there" is the one fact on this
   page a shopper checks before anything else, and it has to survive a glance. */
.pdp__stock {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 16px 0 0;
  padding: 5px 12px 5px 10px;
  border-radius: 999px;
  background: var(--sf-leaf-wash);
  color: var(--sf-leaf);
  font-size: 13.5px;
  font-weight: 700;
}
.pdp__dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.pdp__stock--low { background: #fbeee7; color: var(--sf-clay); }
.pdp__stock--out { background: var(--sf-sand); color: var(--sf-muted); }

.pdp__buy {
  display: flex;
  align-items: stretch;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 22px;
}

.pdp__stepper {
  display: flex;
  align-items: center;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: var(--sf-paper);
  overflow: hidden;
}
.pdp__stepper button {
  width: 44px;
  height: 54px;
  border: none;
  background: none;
  color: var(--sf-ink);
  font-family: inherit;
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
}
.pdp__stepper button:hover:not(:disabled) { background: var(--sf-cream); color: var(--sf-forest); }
.pdp__stepper button:disabled { color: var(--sf-rule); cursor: not-allowed; }
.pdp__qty {
  min-width: 34px;
  text-align: center;
  font-size: 16px;
  font-weight: 800;
  color: var(--sf-ink);
  font-variant-numeric: tabular-nums;
}

.pdp__add {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-width: 240px;
  height: 54px;
  padding: 0 28px;
  border: none;
  border-radius: 10px;
  background: var(--sf-forest);
  color: #fff;
  /* Longhands: `inherit` is only legal as the shorthand's entire value, so
     `font: 800 15px/1 inherit` is dropped whole and the element renders at
     the inherited 17px/400 instead. Same trap as .fd-totop in FdFooter. */
  font-family: inherit;
  font-size: 15.5px;
  font-weight: 800;
  line-height: 1;
  cursor: pointer;
  transition: background 160ms;
}
.pdp__add:hover:not(:disabled) { background: var(--sf-clay); }
.pdp__add:disabled { background: var(--sf-sand); color: var(--sf-faint); cursor: not-allowed; }
.pdp__add:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }

.pdp__incart { margin: 12px 0 0; color: var(--sf-leaf); font-size: 13.5px; font-weight: 700; }

/* ── The cards down the column ────────────────────────────────────────── */

.pdp__trust,
.pdp__shop,
.pdp__facts {
  margin-top: 20px;
  border: 1px solid var(--sf-rule);
  border-radius: 12px;
  background: var(--sf-paper);
}

/* Three across on a wide column, folding as it narrows. The rules between
   them are borders on the items, so they follow the columns rather than
   being drawn where a column no longer is. */
.pdp__trust {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 26px;
  padding: 0;
  list-style: none;
}
.pdp__trust li {
  display: flex;
  gap: 11px;
  padding: 16px 15px;
  border-left: 1px solid var(--sf-rule);
}
.pdp__trust li:first-child { border-left: none; }

.pdp__trusticon {
  flex: none;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--sf-leaf-wash);
  color: var(--sf-leaf);
}
.pdp__trusttitle { margin: 0; font-size: 13.5px; font-weight: 800; line-height: 1.3; color: var(--sf-ink); }
.pdp__trustnote { margin: 3px 0 0; font-size: 12.5px; line-height: 1.45; color: var(--sf-muted); }

/* Side by side while both fit, one under the other when they don't. auto-fit
   rather than a breakpoint, because what decides it is whether a 320px card
   still fits in this column — not how wide the window happens to be. 320
   because a 1280px laptop leaves this column 684px, and 360 missed that by a
   hair and dropped the commonest desktop width back to a stacked ladder. */
.pdp__pair {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 20px;
  align-items: start;
}
/* The cards carry their own top margin from the shared rule above, which
   inside a gapped grid would double the space between the rows. The pair
   owns the spacing now. */
.pdp__pair { margin-top: 20px; }
.pdp__pair > * { margin-top: 0; }

.pdp__shop {
  /* Its own container, so the row inside answers to the card's width: in the
     pair it is half a column, which is narrow while the column is still wide.
     Note an element cannot query the container it establishes, so the wrap
     below is unconditional and only the children are queried. */
  container-type: inline-size;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 14px;
  padding: 18px;
}

.pdp__shopmark {
  flex: none;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  overflow: hidden;
  border-radius: 50%;
  background: var(--sf-forest);
  color: var(--sf-cream);
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.02em;
}
.pdp__shopmark img { width: 100%; height: 100%; object-fit: cover; }

/* Enough to hold "Store location" and its value on one line; below that the
   actions take their own row rather than crushing the address to two words. */
.pdp__shopwho { flex: 1 1 190px; min-width: 0; }

.pdp__shoplabel {
  margin: 0;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--sf-faint);
}
.pdp__shopname { margin: 3px 0 0; font-size: 17.5px; font-weight: 800; color: var(--sf-ink); }
.pdp__shopkind { margin: 2px 0 0; font-size: 13.5px; color: var(--sf-muted); }

.pdp__shopfacts { margin: 12px 0 0; display: grid; gap: 6px; }
.pdp__shopfacts > div { display: flex; gap: 10px; font-size: 13px; }
.pdp__shopfacts dt { min-width: 96px; flex: none; color: var(--sf-faint); }
.pdp__shopfacts dd { margin: 0; min-width: 0; color: var(--sf-ink); font-weight: 600; }

.pdp__shopacts {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  margin-left: auto;
}

.pdp__shopbtn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 15px;
  border: 1.5px solid var(--sf-forest);
  border-radius: 8px;
  color: var(--sf-forest);
  font-size: 13.5px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}
.pdp__shopbtn:hover { background: var(--sf-forest); color: var(--sf-paper); }

.pdp__shoplink {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--sf-muted);
  font-size: 12.5px;
  font-weight: 600;
  text-decoration: none;
}
.pdp__shoplink:hover { color: var(--sf-clay); text-decoration: underline; text-underline-offset: 3px; }

.pdp__facts { padding: 18px; }
.pdp__factstitle {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 800;
  color: var(--sf-ink);
}
.pdp__facts dl { margin: 0; display: grid; gap: 9px; }
.pdp__facts dl > div { display: flex; gap: 14px; font-size: 13.5px; }
.pdp__facts dt { min-width: 132px; flex: none; color: var(--sf-muted); }
.pdp__facts dd { margin: 0; min-width: 0; color: var(--sf-ink); font-weight: 600; overflow-wrap: anywhere; }

/* ── Narrower columns and screens ─────────────────────────────────────── */

/* Keyed on the buy column, not the window. */
@container (max-width: 720px) {
  .pdp__trust { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  /* Three into two leaves a hole in the second row. The last one takes the
     whole row instead: a full-width cell reads as the end of the list, an
     empty half reads as a card that failed to load. It also loses the rule to
     its left and takes one above. */
  .pdp__trust li:nth-child(3) {
    grid-column: 1 / -1;
    border-left: none;
    border-top: 1px solid var(--sf-rule);
  }
}

@container (max-width: 440px) {
  .pdp__trust { grid-template-columns: minmax(0, 1fr); }
  .pdp__trust li { border-left: none; border-top: 1px solid var(--sf-rule); }
  .pdp__trust li:first-child { border-top: none; }
  .pdp__facts dt { min-width: 104px; }
}

/* Children of the shop card, on the card's own width — it is half the buy
   column once the pair is side by side. */
@container (max-width: 430px) {
  .pdp__shopacts { flex-direction: row; align-items: center; width: 100%; margin-left: 0; }
}

@media (max-width: 900px) {
  .pdp__body { grid-template-columns: minmax(0, 1fr); gap: 28px; margin-bottom: 48px; }
  /* Sticky only pays when there is a second column to stay level with, and
     unpinned the photo should not grow to the width of the whole page. */
  .pdp__gallery { position: static; max-width: 460px; }
  .pdp__add { min-width: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .pdp__frame img,
  .pdp__frame--zooming img,
  .pdp__save:hover { transition: none; transform: none; }
}
</style>
