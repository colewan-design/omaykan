<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { discountPercent, formatCurrency, type Product } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/commerce/cart'

// One product tile: photo, sale flag, add button, name, price, savings.
//
// Pulled out of ProductRow so the horizontal shelves on the front page and the
// category grid render the identical card — the two surfaces sit one click
// apart, and a card that changed size or shape between them would read as two
// different products.
//
// The card is an <a href="/?product=id"> whose click the landing page
// intercepts to show the detail in place. The href is real rather than
// decorative so middle-click, ⌘-click and "copy link address" all work and
// land on the same product; only the plain left-click is handled in page.
// The + button still adds one straight from the card, without the detour.

const props = defineProps<{ product: Product }>()

const emit = defineEmits<{ select: [productId: string] }>()

const cart = useStorefrontCart()
const added = ref(false)
let resetTimer = 0

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

function addToCart(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  cart.add(props.product)
  added.value = true
  window.clearTimeout(resetTimer)
  resetTimer = window.setTimeout(() => { added.value = false }, 1600)
}

onBeforeUnmount(() => window.clearTimeout(resetTimer))
</script>

<template>
  <a :href="href" class="fdcard" @click="open">
    <div class="fdcard__art">
      <span v-if="discount !== null" class="fdcard__flag">SALE</span>
      <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" loading="lazy" />
      <div v-else class="fdcard__placeholder" aria-hidden="true">🛒</div>

      <button
        type="button"
        class="fdcard__add"
        :class="{ 'fdcard__add--done': added }"
        :aria-label="`Add ${product.name} to cart`"
        @click="addToCart"
      >
        <svg v-if="added" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
      </button>
    </div>

    <p class="fdcard__name">{{ product.name }}</p>

    <p class="fdcard__price">
      <span :class="{ 'fdcard__price--sale': discount !== null }">
        {{ formatCurrency(product.priceCents) }}
      </span>
      <span v-if="discount !== null" class="fdcard__was">
        {{ formatCurrency(product.compareAtPriceCents!) }}
      </span>
    </p>

    <span v-if="discount !== null" class="fdcard__save">Save {{ discount }}%</span>
    <span v-else-if="product.unitLabel" class="fdcard__unit">{{ product.unitLabel }}</span>
  </a>
</template>

<style scoped>
/* Grid rather than block so the name row absorbs the slack: prices and save
   chips then line up across a shelf or a grid row whatever the name lengths. */
.fdcard {
  scroll-snap-align: start;
  display: grid;
  grid-template-rows: auto 1fr auto auto;
  align-content: start;
  color: inherit;
  text-decoration: none;
}

.fdcard__art {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  background: #f7f8f7;
  overflow: hidden;
  margin-bottom: 10px;
}

.fdcard:focus-visible {
  outline: 2px solid #1a6b3c;
  outline-offset: 4px;
  border-radius: 10px;
}

/* The whole card is a link now, so it has to answer the pointer. */
.fdcard__art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 240ms ease;
}
.fdcard:hover .fdcard__art img { transform: scale(1.05); }
.fdcard__placeholder { display: grid; place-items: center; height: 100%; font-size: 30px; }

.fdcard__flag {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  padding: 3px 8px;
  border-radius: 0 0 6px 0;
  background: #16a34a;
  color: #fff;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

/* Round + button, bottom-right of the artwork. */
.fdcard__add {
  position: absolute;
  right: 8px;
  bottom: 8px;
  z-index: 2;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 999px;
  background: #22c55e;
  color: #06240f;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(0,0,0,0.18);
  transition: transform 150ms, background 150ms;
}
.fdcard__add:hover { transform: scale(1.08); }
.fdcard__add--done { background: #1a6b3c; color: #fff; }

.fdcard__name {
  margin: 0 0 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.35;
  color: #1a1a1a;
}
.fdcard:hover .fdcard__name { text-decoration: underline; }

.fdcard__price { margin: 0 0 4px; display: flex; align-items: baseline; gap: 6px; }
.fdcard__price > span:first-child { font-size: 15.5px; font-weight: 800; color: #1a1a1a; }
.fdcard__price--sale { color: #c2410c !important; }
.fdcard__was { font-size: 12.5px; color: #9ca3af; text-decoration: line-through; }

/* Savings chip — outlined, warm, so it reads apart from the green brand. */
.fdcard__save {
  display: inline-block;
  justify-self: start;
  padding: 2px 7px;
  border: 1px solid #c2410c;
  border-radius: 4px;
  color: #c2410c;
  font-size: 12px;
  font-weight: 600;
}

.fdcard__unit { font-size: 12px; color: #9ca3af; }
</style>
