<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  calculateTax,
  discountPercent,
  formatCurrency,
  type Category,
} from '@pos/shared/index'
import type { StorefrontProduct } from '@pos/web/commerce/api'
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
  product: StorefrontProduct
  /** The product's aisle, for the breadcrumb — null if the catalog dropped it. */
  category: Category | null
  /** The rest of the aisle, minus this product. */
  related: StorefrontProduct[]
}>()

const emit = defineEmits<{
  back: []
  category: [categoryId: string]
  select: [productId: string]
}>()

const cart = useStorefrontCart()
const catalog = useStorefrontCatalog()

const quantity = ref(1)
const justAdded = ref(false)
let resetTimer = 0

/**
 * Hover zoom on the pack shot. The frame already clips (overflow:hidden) and
 * the photo already fits inside it, so magnifying is just a scale on the img
 * with the transform origin pinned under the cursor — the part you point at is
 * the part that stays put, which is what makes it read as a magnifier rather
 * than as the picture lurching.
 *
 * Mouse only: on a touchscreen there is no hover to track, and a tap that
 * silently zoomed would eat the tap. Pointer coordinates are read against the
 * frame's own box so scrolling mid-hover can't drift the origin.
 */
const ZOOM = 2.2

const photo = ref<HTMLImageElement | null>(null)
const zooming = ref(false)
const zoomOrigin = ref('50% 50%')

function trackZoom(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || !photo.value) return

  // Measured against the photo, not the frame around it: the frame is padded
  // and the shot is letterboxed inside it, so frame coordinates would put the
  // origin off the spot being pointed at everywhere but dead centre. Read
  // fresh each move so a scroll mid-hover can't leave a stale box behind.
  const box = photo.value.getBoundingClientRect()
  if (!box.width || !box.height) return

  const x = ((event.clientX - box.left) / box.width) * 100
  const y = ((event.clientY - box.top) / box.height) * 100

  zoomOrigin.value = `${clampPercent(x)}% ${clampPercent(y)}%`
  zooming.value = true
}

/** The pointer can sit in the frame's padding, which is off the photo. */
function clampPercent(value: number) {
  return Math.min(100, Math.max(0, Math.round(value * 100) / 100))
}

function endZoom() {
  zooming.value = false
}

// A new product in the same face keeps the frame mounted, so the old zoom
// would otherwise still be applied when the next photo swaps in.
watch(() => props.product.id, endZoom)

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

/**
 * The branch this item would actually come off. With a delivery address set
 * that is whichever branch is nearest and stocks it, so it is a property of
 * the product rather than of the storefront.
 */
const servingStore = computed(() =>
  catalog.delivery.stores.find((store) => store.code === props.product.storeCode) ?? null,
)

const storeName = computed(() => props.product.storeName || servingStore.value?.name || '')

/**
 * Where to go and get it. The product carries the branch's address, but an
 * older API answers without that field and the demo shelves have no branch at
 * all, so the delivery block and this storefront's own configured address are
 * read in turn before giving up.
 */
const storeAddress = computed(
  () => props.product.storeAddress || servingStore.value?.address || STORE_ADDRESS || '',
)

/** Never blank: a store that never filled its address in still gets a line. */
const storeAddressLabel = computed(() => storeAddress.value || 'Address not specified')

const storeDistance = computed(() => {
  const km = props.product.distanceKm ?? servingStore.value?.distanceKm ?? null
  return km === null ? '' : `${km.toFixed(1)} km away`
})

// Opening a second product from the related shelf reuses this component, so the
// stepper has to fall back to 1 rather than carry the last product's count.
watch(
  () => props.product.id,
  () => {
    quantity.value = 1
    justAdded.value = false
    window.clearTimeout(resetTimer)
  },
)

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
        :class="{ 'fdpdp__art--zoomable': product.imageUrl, 'fdpdp__art--zooming': zooming }"
        @pointermove="product.imageUrl && trackZoom($event)"
        @pointerleave="endZoom"
        @pointercancel="endZoom"
      >
        <span v-if="discount !== null" class="fdpdp__flag">SALE — save {{ discount }}%</span>
        <img
          v-if="product.imageUrl"
          ref="photo"
          :src="product.imageUrl"
          :alt="product.name"
          :style="{ transformOrigin: zoomOrigin, transform: zooming ? `scale(${ZOOM})` : undefined }"
          draggable="false"
        />
        <div v-else class="fdpdp__placeholder" aria-hidden="true">🛒</div>
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

        <p class="fdpdp__store">
          <span class="fdpdp__store-pin" aria-hidden="true">📍</span>
          <span>
            <span v-if="storeName" class="fdpdp__store-name">{{ storeName }}</span>
            <span class="fdpdp__store-addr" :class="{ 'fdpdp__store-addr--none': !storeAddress }">
              {{ storeAddressLabel }}
            </span>
            <span v-if="storeDistance" class="fdpdp__store-far">{{ storeDistance }}</span>
          </span>
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
          <p>Cash when the rider arrives — nothing is charged online.</p>
        </div>

        <dl class="fdpdp__facts">
          <div>
            <dt>Item code</dt>
            <dd>{{ product.sku }}</dd>
          </div>
          <div v-if="product.barcode">
            <dt>Barcode</dt>
            <dd>{{ product.barcode }}</dd>
          </div>
          <div>
            <dt>Store location</dt>
            <dd>{{ storeAddressLabel }}</dd>
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
  /* The origin comes from the pointer, so only the scale is animated here. */
  transition: transform 220ms ease-out;
  will-change: transform;
}

.fdpdp__placeholder { font-size: 72px; }

/* The scale itself is bound inline, and only a mouse ever sets it; this is the
   affordance that says the photo will do something when you point at it. */
@media (hover: hover) and (pointer: fine) {
  .fdpdp__art--zoomable { cursor: zoom-in; }
}

/* The zoom is a way of reading the label, not decoration, so it stays — what
   goes is the glide into it. */
@media (prefers-reduced-motion: reduce) {
  .fdpdp__art img { transition: none; }
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

/* Sits between the stock line and the buy row: whoever is about to add this
   to a cart is also deciding whether they want it coming from that counter. */
.fdpdp__store {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 14px 0 0;
  padding: 12px 14px;
  border: 1px solid #edf0ee;
  border-radius: 10px;
  background: #fbfcfb;
  font-size: 13.5px;
  line-height: 1.5;
  color: #4b5563;
}
.fdpdp__store-pin { font-size: 14px; line-height: 1.45; }
.fdpdp__store-name { font-weight: 700; color: #1a1a1a; }
.fdpdp__store-name::after { content: ' · '; color: #9ca3af; font-weight: 400; }
.fdpdp__store-addr--none { color: #9ca3af; font-style: italic; }
.fdpdp__store-far { color: #9ca3af; }
.fdpdp__store-far::before { content: ' · '; }

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
