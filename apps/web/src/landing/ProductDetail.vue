<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  calculateTax,
  discountPercent,
  formatCurrency,
  type Category,
  type Product,
} from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { STORE_ADDRESS } from '@pos/web/commerce/context'
import ProductRow from './ProductRow.vue'

// The product detail face of the landing page: one product at full size, with
// the quantity control the cards don't have room for, and the rest of its aisle
// underneath.
//
// It is a face of the landing page rather than its own Vite entry because the
// whole storefront is one document now — search, aisle and product are all URL
// state on it (?q=, ?category=, ?product=), so opening a product costs no
// reload and Back walks straight out of it.

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

const quantity = ref(1)
const justAdded = ref(false)
let resetTimer = 0

const discount = computed(() => discountPercent(props.product))
const soldOut = computed(() => props.product.outOfStock === true)

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

// Opening a second product from the related shelf reuses this component, so the
// stepper has to fall back to 1 rather than carry the last product's count.
watch(
  () => props.product.id,
  () => {
    quantity.value = 1
    justAdded.value = false
    endZoom()
    window.clearTimeout(resetTimer)
  },
)

/**
 * Hover-to-zoom on the detail shot: the pack photo is the only place a shopper
 * can read a label they'd otherwise pick up and turn over, so pointing at a
 * corner of it magnifies that corner in place rather than opening a lightbox.
 * The frame already clips (overflow: hidden), so scaling the <img> about the
 * cursor is the whole trick — no second copy of the image to download.
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
onBeforeUnmount(() => hoverQuery?.removeEventListener('change', readPointer))

const canZoom = computed(() => pointerFine.value && Boolean(props.product.imageUrl))

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
  <section class="fdpdp">
    <nav class="fdpdp__crumbs" aria-label="Breadcrumb">
      <a href="/" @click.prevent="emit('back')">All categories</a>
      <span aria-hidden="true">›</span>
      <template v-if="category">
        <a :href="`/?category=${category.id}`" @click.prevent="emit('category', category.id)">
          {{ category.name }}
        </a>
        <span aria-hidden="true">›</span>
      </template>
      <span class="fdpdp__here">{{ product.name }}</span>
    </nav>

    <div class="fdpdp__body">
      <!-- Contain, not cover: a detail shot has to show the whole pack — the
           weight, the variant — where a card only needs to be recognisable. -->
      <div
        class="fdpdp__art"
        :class="{ 'fdpdp__art--zoomable': canZoom, 'fdpdp__art--zooming': zooming }"
        @mousemove="trackZoom"
        @mouseleave="endZoom"
      >
        <span v-if="discount !== null" class="fdpdp__flag">SALE — save {{ discount }}%</span>
        <img
          v-if="product.imageUrl"
          ref="zoomImage"
          :src="product.imageUrl"
          :alt="product.name"
          :style="zoomStyle"
          draggable="false"
        />
        <div v-else class="fdpdp__placeholder" aria-hidden="true">🛒</div>
        <span v-if="canZoom" class="fdpdp__zoomhint" aria-hidden="true">Hover to zoom</span>
      </div>

      <div class="fdpdp__info">
        <a
          v-if="category"
          class="fdpdp__eyebrow"
          :href="`/?category=${category.id}`"
          @click.prevent="emit('category', category.id)"
        >{{ category.name }}</a>

        <h1 class="fdpdp__title">{{ product.name }}</h1>

        <p v-if="product.unitLabel" class="fdpdp__unit">{{ product.unitLabel }}</p>

        <div class="fdpdp__prices">
          <span class="fdpdp__price" :class="{ 'fdpdp__price--sale': discount !== null }">
            {{ formatCurrency(product.priceCents) }}<span v-if="priceSuffix" class="fdpdp__per"> {{ priceSuffix }}</span>
          </span>
          <span v-if="discount !== null" class="fdpdp__was">
            {{ formatCurrency(product.compareAtPriceCents!) }}
          </span>
          <span v-if="discount !== null" class="fdpdp__save">Save {{ discount }}%</span>
        </div>

        <p v-if="product.taxRate > 0" class="fdpdp__tax">
          + {{ formatCurrency(taxCents) }} VAT at checkout
        </p>

        <p class="fdpdp__stock" :class="{ 'fdpdp__stock--out': soldOut, 'fdpdp__stock--low': lowStock }">
          <template v-if="soldOut">Sold out today</template>
          <template v-else-if="lowStock">Only {{ product.stockQty }} left today</template>
          <template v-else>In stock</template>
        </p>

        <div class="fdpdp__buy">
          <div class="fdpdp__stepper" role="group" aria-label="Quantity">
            <button
              type="button"
              aria-label="One fewer"
              :disabled="soldOut || quantity <= 1"
              @click="step(-1)"
            >−</button>
            <span class="fdpdp__qty" aria-live="polite">{{ quantity }}</span>
            <button
              type="button"
              aria-label="One more"
              :disabled="soldOut || quantity >= maxQuantity"
              @click="step(1)"
            >+</button>
          </div>

          <button type="button" class="fdpdp__add" :disabled="soldOut" @click="addToCart">
            <template v-if="soldOut">Sold out</template>
            <template v-else-if="justAdded">Added to cart ✓</template>
            <template v-else>
              Add {{ quantity }} to cart — {{ formatCurrency(product.priceCents * quantity) }}
            </template>
          </button>
        </div>

        <p v-if="inCart > 0" class="fdpdp__incart">
          {{ inCart }} in your cart
        </p>

        <div class="fdpdp__notes">
          <p>
            Sold at the counter price. Omaykan takes no commission, so nothing here is
            marked up to pay for the app.
          </p>
          <p>Cash or GCash when the rider arrives — nothing is charged online.</p>
        </div>

        <section v-if="shop" class="fdpdp__shop">
          <h2 class="fdpdp__shoplabel">Sold by</h2>
          <p class="fdpdp__shopname">{{ shop.name }}</p>
          <p v-if="shop.businessTypeLabel" class="fdpdp__shopkind">{{ shop.businessTypeLabel }}</p>
          <dl v-if="shop.ownerName || shopAddress" class="fdpdp__shopfacts">
            <div v-if="shop.ownerName">
              <dt>Store owner</dt>
              <dd>{{ shop.ownerName }}</dd>
            </div>
            <div v-if="shopAddress">
              <dt>Store location</dt>
              <dd>{{ shopAddress }}</dd>
            </div>
          </dl>
        </section>

        <dl class="fdpdp__facts">
          <div>
            <dt>Item code</dt>
            <dd>{{ product.sku }}</dd>
          </div>
          <div v-if="product.barcode">
            <dt>Barcode</dt>
            <dd>{{ product.barcode }}</dd>
          </div>
          <div v-if="product.kind === 'weighted'">
            <dt>Sold by</dt>
            <dd>Weight — priced {{ product.unitLabel || 'per unit' }}</dd>
          </div>
        </dl>
      </div>
    </div>

    <ProductRow
      v-if="related.length > 0"
      :title="category ? `More from ${category.name}` : 'More to shop'"
      :products="related"
      @select="emit('select', $event)"
    />
  </section>
</template>

<style scoped>
.fdpdp { margin-bottom: 56px; }

.fdpdp__crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 22px;
  font-size: 13.5px;
  color: #9ca3af;
}
.fdpdp__crumbs a { color: #1a6b3c; font-weight: 600; text-decoration: underline; text-underline-offset: 3px; }
.fdpdp__crumbs a:hover { color: #16a34a; }
.fdpdp__here { color: #4b5563; font-weight: 600; }

/* Capped rather than full-bleed: the artwork is a square, so on a wide screen
   two free-growing columns give a 660px photo with the buy controls stranded
   at the top of an equally tall column of nothing. */
.fdpdp__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 52px;
  align-items: start;
  max-width: 1080px;
  margin-bottom: 64px;
}

.fdpdp__art {
  position: relative;
  aspect-ratio: 1 / 1;
  display: grid;
  place-items: center;
  padding: 28px;
  border: 1px solid #edf0ee;
  border-radius: 14px;
  /* White, not the card's grey: most catalog photos are cut out on white
     already, and a grey mat makes them read as a picture of a picture. */
  background: #fff;
  overflow: hidden;
}
.fdpdp__art img {
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
.fdpdp__art--zooming img { transition: transform 90ms ease-out; }
.fdpdp__art--zoomable { cursor: zoom-in; }
.fdpdp__placeholder { font-size: 72px; }

.fdpdp__zoomhint {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(26, 26, 26, 0.62);
  color: #fff;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.03em;
  opacity: 0;
  transition: opacity 160ms;
  pointer-events: none;
}
.fdpdp__art:hover .fdpdp__zoomhint { opacity: 1; }
.fdpdp__art--zooming .fdpdp__zoomhint { opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .fdpdp__art img,
  .fdpdp__art--zooming img { transition: none; }
}

.fdpdp__flag {
  position: absolute;
  top: 0;
  left: 0;
  padding: 7px 14px;
  border-radius: 0 0 10px 0;
  background: #16a34a;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  /* Above the magnified photo, which otherwise slides out from under it. */
  z-index: 2;
}

.fdpdp__info { padding-top: 6px; }

.fdpdp__eyebrow {
  display: inline-block;
  margin-bottom: 10px;
  color: #16a34a;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.fdpdp__eyebrow:hover { color: #1a6b3c; text-decoration: underline; text-underline-offset: 3px; }

.fdpdp__title {
  margin: 0 0 6px;
  font-size: clamp(1.6rem, 3.2vw, 2.35rem);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.03em;
  color: #1a1a1a;
}

.fdpdp__unit { margin: 0 0 18px; color: #6b7280; font-size: 14.5px; }

.fdpdp__prices { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.fdpdp__price { font-size: 2rem; font-weight: 800; color: #1a1a1a; letter-spacing: -0.02em; }
.fdpdp__price--sale { color: #c2410c; }
.fdpdp__per { font-size: 1rem; font-weight: 600; color: #6b7280; }
.fdpdp__was { font-size: 16px; color: #9ca3af; text-decoration: line-through; }
.fdpdp__save {
  padding: 3px 9px;
  border: 1px solid #c2410c;
  border-radius: 5px;
  color: #c2410c;
  font-size: 13px;
  font-weight: 700;
}

.fdpdp__tax { margin: 8px 0 0; color: #9ca3af; font-size: 13px; }

.fdpdp__stock { margin: 16px 0 0; font-size: 14px; font-weight: 600; color: #16a34a; }
.fdpdp__stock--low { color: #c2410c; }
.fdpdp__stock--out { color: #9ca3af; }

.fdpdp__buy {
  display: flex;
  align-items: stretch;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 22px;
}

.fdpdp__stepper {
  display: flex;
  align-items: center;
  border: 1px solid #d7ddd9;
  border-radius: 999px;
  overflow: hidden;
}
.fdpdp__stepper button {
  width: 44px;
  height: 52px;
  border: none;
  background: #fff;
  color: #1a1a1a;
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
}
.fdpdp__stepper button:hover:not(:disabled) { background: #f2f6f3; color: #1a6b3c; }
.fdpdp__stepper button:disabled { color: #cbd5d0; cursor: not-allowed; }
.fdpdp__qty {
  min-width: 34px;
  text-align: center;
  font-size: 16px;
  font-weight: 800;
  color: #1a1a1a;
}

.fdpdp__add {
  flex: 1;
  min-width: 240px;
  height: 52px;
  padding: 0 28px;
  border: none;
  border-radius: 999px;
  background: #1a6b3c;
  color: #fff;
  font: 800 15px/1 inherit;
  cursor: pointer;
  transition: background 160ms;
}
.fdpdp__add:hover:not(:disabled) { background: #16a34a; }
.fdpdp__add:disabled { background: #e5e9e7; color: #9ca3af; cursor: not-allowed; }

.fdpdp__incart { margin: 12px 0 0; color: #1a6b3c; font-size: 13.5px; font-weight: 600; }

.fdpdp__notes {
  margin-top: 26px;
  padding: 18px 20px;
  border-radius: 12px;
  background: #f5f9f6;
}
.fdpdp__notes p { margin: 0; font-size: 14px; line-height: 1.6; color: #4b5563; }
.fdpdp__notes p + p { margin-top: 8px; }

/* The shop card, not a fact row: a shopper who has never been to this counter
   is handing cash to a rider on its behalf, so who and where it is has to read
   as a statement about the seller rather than as metadata next to a barcode. */
.fdpdp__shop {
  margin-top: 24px;
  padding: 18px 20px;
  border: 1px solid #e3e8e5;
  border-radius: 12px;
}
.fdpdp__shoplabel {
  margin: 0 0 6px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: #9ca3af;
}
.fdpdp__shopname { margin: 0; font-size: 17px; font-weight: 800; color: #1a1a1a; }
.fdpdp__shopkind { margin: 3px 0 0; font-size: 13.5px; color: #6b7280; }

.fdpdp__shopfacts { margin: 14px 0 0; display: grid; gap: 8px; }
.fdpdp__shopfacts > div { display: flex; gap: 10px; font-size: 13.5px; }
.fdpdp__shopfacts dt { min-width: 96px; flex: none; color: #9ca3af; }
.fdpdp__shopfacts dd { margin: 0; color: #4b5563; font-weight: 600; }

.fdpdp__facts { margin: 24px 0 0; display: grid; gap: 10px; }
.fdpdp__facts > div { display: flex; gap: 10px; font-size: 13.5px; }
.fdpdp__facts dt { min-width: 96px; color: #9ca3af; }
.fdpdp__facts dd { margin: 0; color: #4b5563; font-weight: 600; }

@media (max-width: 900px) {
  .fdpdp__body { grid-template-columns: 1fr; gap: 28px; margin-bottom: 48px; }
  .fdpdp__art { max-width: 460px; }
  .fdpdp__add { min-width: 0; }
}
</style>
