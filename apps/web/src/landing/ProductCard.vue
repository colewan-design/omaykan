<script setup lang="ts">
import { computed } from 'vue'
import { discountPercent, formatCurrency, type Product } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useSavedProducts } from '@pos/web/commerce/favorites'
import ProductArt from './ProductArt.vue'

// One product tile: photo, sale flag, add button, name, price, savings.
//
// Pulled out of ProductRow so every shelf on the front page renders the
// identical card. The aisle listing draws its own framed card to the listing
// redesign (ListingCard.vue); the two keep the same behaviour — a real link,
// the heart, an Add button that becomes a stepper — so a product acts the same
// wherever it is picked up.
//
// The card is an <a href="/?product=id"> whose click the landing page
// intercepts to show the detail in place. The href is real rather than
// decorative so middle-click, ⌘-click and "copy link address" all work and
// land on the same product; only the plain left-click is handled in page.
// The + button still adds one straight from the card, without the detour.

const props = withDefaults(
  defineProps<{
    product: Product
    /** The product's aisle, so a product with no photo gets its aisle's glyph. */
    categoryName?: string
    /** The shop's own photo, if its owner uploaded one. See ProductArt. */
    merchantImageUrl?: string
  }>(),
  { categoryName: '', merchantImageUrl: '' },
)

const emit = defineEmits<{ select: [productId: string] }>()

const cart = useStorefrontCart()

// The basket itself is the state now. The card used to flash a tick for 1.6s
// and then forget, so a shelf gave no way to tell what was already in the
// basket without opening it — and no way to change your mind but to open it.
const quantity = computed(() => cart.quantityOf(props.product.id))

const discount = computed(() => discountPercent(props.product))

const href = computed(() => `/?product=${encodeURIComponent(props.product.id)}`)

function open(event: MouseEvent) {
  // Anything but a plain left-click is the browser's to handle: a new tab, a
  // new window, a saved link.
  if (event.defaultPrevented || event.button !== 0) return
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  emit('select', props.product.id)
}

// Every one of these sits inside the card's <a>, so each has to call off the
// navigation as well as the card's own click handler.
function addToCart(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  cart.add(props.product)
}

function removeOne(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  cart.decrement(props.product.id)
}

// The heart. Kept in this browser (commerce/favorites.ts) and listed on the
// account page's Wishlist.
const saved = useSavedProducts()
const isSaved = computed(() => saved.isSaved(props.product.id))

function toggleSaved(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  saved.toggle(props.product)
}
</script>

<template>
  <a :href="href" class="fdcard" @click="open">
    <div class="fdcard__art">
      <!-- One badge, not two. "SALE" on the photo and "Save 21%" under the
           price said the same thing twice, in two different treatments, and
           the second one sat where the eye was looking for the price. -->
      <span v-if="discount !== null" class="fdcard__flag">SALE {{ discount }}%</span>
      <ProductArt
        :product="product"
        :category-name="categoryName"
        :merchant-image-url="merchantImageUrl"
        :size="40"
      />
      <button
        type="button"
        class="fdcard__save"
        :class="{ 'fdcard__save--on': isSaved }"
        :aria-pressed="isSaved"
        :aria-label="isSaved ? `Remove ${product.name} from your wishlist` : `Save ${product.name} to your wishlist`"
        @click="toggleSaved"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" :fill="isSaved ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/></svg>
      </button>
    </div>

    <!-- Name and unit share one row so the price and the button below stay on
         the same line across a shelf, whatever the name length and whether or
         not the product is sold by weight. -->
    <div class="fdcard__meta">
      <p class="fdcard__name">{{ product.name }}</p>
      <p v-if="product.unitLabel" class="fdcard__unit">{{ product.unitLabel }}</p>
    </div>

    <p class="fdcard__price">
      <span :class="{ 'fdcard__price--sale': discount !== null }">
        {{ formatCurrency(product.priceCents) }}
      </span>
      <span v-if="discount !== null" class="fdcard__was">
        {{ formatCurrency(product.compareAtPriceCents!) }}
      </span>
    </p>

    <button
      v-if="quantity === 0"
      type="button"
      class="fdcard__add"
      :aria-label="`Add ${product.name} to cart`"
      @click="addToCart"
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
      <span>Add</span>
    </button>

    <!-- Same height and width as the Add button it replaces, so adding an item
         does not make the shelf jump under the pointer. -->
    <span v-else class="fdcard__qty" role="group" :aria-label="`Quantity of ${product.name}`">
      <button
        type="button"
        class="fdcard__step"
        :aria-label="quantity === 1 ? `Remove ${product.name} from cart` : `One fewer ${product.name}`"
        @click="removeOne"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="M5 12h14"/></svg>
      </button>

      <span class="fdcard__qty-n" aria-live="polite">{{ quantity }}</span>

      <button
        type="button"
        class="fdcard__step"
        :aria-label="`One more ${product.name}`"
        @click="addToCart"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
      </button>
    </span>
  </a>
</template>

<style scoped>
/* Four rows, and the name/unit block is the one that stretches: prices and
   Add buttons then line up across a shelf or a grid row whatever the names do. */
.fdcard {
  scroll-snap-align: start;
  display: grid;
  grid-template-rows: auto 1fr auto auto;
  align-content: start;
  height: 100%;
  padding: 8px;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: #fff;
  color: inherit;
  text-decoration: none;
  box-shadow: 0 6px 18px rgba(20, 56, 39, 0.035);
  transition: transform 150ms ease, box-shadow 150ms ease, border-color 150ms ease;
}

.fdcard:hover {
  transform: translateY(-2px);
  border-color: #cbd9d0;
  box-shadow: 0 12px 26px rgba(20, 56, 39, 0.09);
}

.fdcard__art {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  background: var(--sf-sand);
  overflow: hidden;
  margin-bottom: 10px;
}

.fdcard:focus-visible {
  outline: 2px solid var(--sf-forest);
  outline-offset: 4px;
  border-radius: 10px;
}

/* The whole card is a link now, so it has to answer the pointer. */
/* :deep, because the art is ProductArt's element now — the photo and the
   stand-in alike. Only the photograph zooms on hover: a glyph is line art at
   a fixed weight and scaling it just softens it. */
.fdcard__art :deep(.part__photo) { transition: transform 240ms ease; }
.fdcard:hover .fdcard__art :deep(.part__photo) { transform: scale(1.05); }
.fdcard:hover .fdcard__art :deep(.part__mark) { opacity: 0.9; transform: translateY(-1px); }

.fdcard__flag {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  padding: 4px 9px;
  border-radius: 0 0 6px 0;
  background: var(--sf-clay);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

/* Above the photo, which scales on hover and would otherwise slide over it. */
.fdcard__save {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 253, 249, 0.94);
  color: var(--sf-ink);
  box-shadow: 0 2px 8px rgba(23, 35, 28, 0.16);
  cursor: pointer;
  transition: color 150ms, transform 150ms;
}
.fdcard__save:hover { color: var(--sf-clay); transform: scale(1.08); }
.fdcard__save:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }
.fdcard__save--on { color: var(--sf-clay); }

.fdcard__meta { min-width: 0; }

/* Up from 14/500. The name is one of the four things the card is for, and at
   14px regular it was losing to the price beneath it. */
.fdcard__name {
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--sf-ink);
}
.fdcard:hover .fdcard__name { text-decoration: underline; }

/* Secondary by design: the weight qualifies the name, it is not a third thing
   to read. Shown on sale items too — it used to be dropped for the savings
   chip, so exactly the products worth comparing lost the number you compare on. */
.fdcard__unit {
  margin: 2px 0 0;
  font-size: 12.5px;
  line-height: 1.3;
  color: var(--sf-muted);
}

.fdcard__price {
  margin: 8px 0 0;
  display: flex;
  align-items: baseline;
  gap: 7px;
}
.fdcard__price > span:first-child { font-size: 18px; font-weight: 800; color: var(--sf-ink); }
.fdcard__price--sale { color: var(--sf-clay) !important; }
.fdcard__was { font-size: 13px; color: var(--sf-faint); text-decoration: line-through; }

/* A labelled button on its own row rather than a 34px circle floating over the
   photo: "Add" is one of the four things this card exists to do, and an icon
   with no word was the quietest element on it. */
.fdcard__add {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 9px 12px;
  border: 1.5px solid var(--sf-forest);
  border-radius: 999px;
  background: var(--sf-paper);
  color: var(--sf-forest);
  /* Longhands: `inherit` is only legal as the shorthand's entire value, so
     `font: 800 13.5px/1.2 inherit` is dropped whole and the element renders at
     the inherited 17px/400 instead. Same trap as .fd-totop in FdFooter. */
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 800;
  line-height: 1.2;
  cursor: pointer;
  transition: background 150ms, color 150ms, border-color 150ms;
}
.fdcard__add:hover { background: var(--sf-forest); color: #fff; }
.fdcard__add:focus-visible { outline: 2px solid var(--sf-forest); outline-offset: 2px; }

/* In the basket: filled rather than outlined, so a glance down a shelf says
   which items are already in it without reading a single number. The metrics
   match .fdcard__add exactly — same height, same radius, same top margin —
   because this swaps in where that button was. */
.fdcard__qty {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0;
  border: 1.5px solid var(--sf-forest);
  border-radius: 999px;
  background: var(--sf-forest);
  color: #fff;
  overflow: hidden;
}

.fdcard__step {
  display: grid;
  place-items: center;
  width: 40px;
  /* 9px padding + 1.2 line-height on 13.5px type, to the pixel: the stepper
     must not be a hair taller than the Add button or the row reflows. */
  height: calc(9px * 2 + 13.5px * 1.2);
  border: none;
  background: none;
  color: inherit;
  cursor: pointer;
  transition: background 150ms;
}
.fdcard__step:hover { background: rgba(255, 255, 255, 0.18); }
.fdcard__step:focus-visible { outline: 2px solid var(--sf-gold); outline-offset: -3px; }

.fdcard__qty-n {
  flex: 1;
  text-align: center;
  font-size: 14px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

/* Touch: the stepper is tapped repeatedly and 40x34 is under the 44px floor,
   so both states grow together — they have to stay the same height or the
   shelf reflows the moment something is added. */
@media (max-width: 720px) {
  .fdcard__add { padding: 14px 12px; }
  .fdcard__step {
    width: 46px;
    height: calc(14px * 2 + 13.5px * 1.2);
  }
}

@media (prefers-reduced-motion: reduce) {
  .fdcard, .fdcard:hover { transition: none; transform: none; }
  .fdcard__art :deep(.part__photo),
  .fdcard:hover .fdcard__art :deep(.part__photo),
  .fdcard:hover .fdcard__art :deep(.part__mark) { transition: none; transform: none; }
}
</style>
