<script setup lang="ts">
import { computed, ref } from 'vue'
import { discountPercent, formatCurrency, type Product } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/commerce/cart'

// A horizontally-scrolling shelf of product cards — the repeating unit of the
// reference grocery layout: heading + "View all", arrow controls on the right,
// then a row of cards you can add to the cart without leaving the page.
//
// Cards are plain divs rather than links: they used to deep-link into
// /store/product/<id>, but the storefront entry is gone, so there is nowhere
// to send a click. The + button is the only interaction left on a card.

const props = withDefaults(
  defineProps<{
    title: string
    products: Product[]
    /** Optional paragraph between the heading and the shelf. */
    blurb?: string
    /** Empty hides the link — there is no store page to send anyone to. */
    viewAllHref?: string
  }>(),
  { blurb: '', viewAllHref: '' },
)

const cart = useStorefrontCart()
const shelf = ref<HTMLElement | null>(null)
const added = ref<Record<string, boolean>>({})

const hasProducts = computed(() => props.products.length > 0)

function scrollBy(direction: -1 | 1) {
  const el = shelf.value
  if (!el) return
  // One "page" is whatever is visible, less a sliver so the next card peeks in.
  el.scrollBy({ left: direction * (el.clientWidth - 80), behavior: 'smooth' })
}

function addToCart(product: Product, event: Event) {
  event.preventDefault()
  event.stopPropagation()
  cart.add(product)
  added.value = { ...added.value, [product.id]: true }
  window.setTimeout(() => {
    const next = { ...added.value }
    delete next[product.id]
    added.value = next
  }, 1600)
}
</script>

<template>
  <section v-if="hasProducts" class="fdrow">
    <div class="fdrow__head">
      <div class="fdrow__headline">
        <h2 class="fdrow__title">{{ title }}</h2>
        <a v-if="viewAllHref" :href="viewAllHref" class="fdrow__viewall">View all</a>
      </div>
      <div class="fdrow__arrows">
        <button type="button" aria-label="Scroll left" @click="scrollBy(-1)">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
        </button>
        <button type="button" aria-label="Scroll right" @click="scrollBy(1)">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>
        </button>
      </div>
    </div>

    <p v-if="blurb" class="fdrow__blurb">{{ blurb }}</p>

    <div ref="shelf" class="fdrow__shelf">
      <div v-for="product in products" :key="product.id" class="fdcard">
        <div class="fdcard__art">
          <span v-if="discountPercent(product) !== null" class="fdcard__flag">SALE</span>
          <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" loading="lazy" />
          <div v-else class="fdcard__placeholder" aria-hidden="true">🛒</div>

          <button
            type="button"
            class="fdcard__add"
            :class="{ 'fdcard__add--done': added[product.id] }"
            :aria-label="`Add ${product.name} to cart`"
            @click="addToCart(product, $event)"
          >
            <svg v-if="added[product.id]" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
          </button>
        </div>

        <p class="fdcard__name">{{ product.name }}</p>

        <p class="fdcard__price">
          <span :class="{ 'fdcard__price--sale': discountPercent(product) !== null }">
            {{ formatCurrency(product.priceCents) }}
          </span>
          <span v-if="discountPercent(product) !== null" class="fdcard__was">
            {{ formatCurrency(product.compareAtPriceCents!) }}
          </span>
        </p>

        <span v-if="discountPercent(product) !== null" class="fdcard__save">
          Save {{ discountPercent(product) }}%
        </span>
        <span v-else-if="product.unitLabel" class="fdcard__unit">{{ product.unitLabel }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.fdrow { margin-bottom: 56px; }

.fdrow__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 6px;
}

.fdrow__headline { display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; }

.fdrow__title {
  margin: 0;
  font-size: clamp(1.25rem, 2.4vw, 1.75rem);
  font-weight: 800;
  letter-spacing: -0.025em;
  color: #1a1a1a;
}

.fdrow__viewall {
  font-size: 14px;
  font-weight: 600;
  color: #16a34a;
  text-decoration: underline;
  text-underline-offset: 3px;
  white-space: nowrap;
}
.fdrow__viewall:hover { color: #1a6b3c; }

.fdrow__arrows { display: flex; gap: 6px; flex-shrink: 0; }
.fdrow__arrows button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
}
.fdrow__arrows button:hover { background: #f4f6f5; color: #1a1a1a; }

.fdrow__blurb {
  margin: 0 0 20px;
  max-width: 780px;
  font-size: 15px;
  line-height: 1.6;
  color: #6b7280;
}

/* Horizontal shelf — scrolls, snaps, hides its scrollbar. */
.fdrow__shelf {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(178px, 1fr);
  gap: 20px;
  margin-top: 18px;
  overflow-x: auto;
  scroll-snap-type: x proximity;
  scrollbar-width: none;
  padding-bottom: 4px;
}
.fdrow__shelf::-webkit-scrollbar { display: none; }

/* ── Card ─────────────────────────────────────────────────────────── */
/* Grid rather than block so the name row absorbs the slack: prices and save
   chips then line up across the shelf whatever the name lengths. */
.fdcard {
  scroll-snap-align: start;
  display: grid;
  grid-template-rows: auto 1fr auto auto;
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

.fdcard__art img { width: 100%; height: 100%; object-fit: cover; display: block; }
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
  padding: 2px 7px;
  border: 1px solid #c2410c;
  border-radius: 4px;
  color: #c2410c;
  font-size: 12px;
  font-weight: 600;
}

.fdcard__unit { font-size: 12px; color: #9ca3af; }

@media (max-width: 720px) {
  .fdrow__shelf { grid-auto-columns: minmax(148px, 1fr); gap: 14px; }
  .fdrow__arrows { display: none; }
}
</style>
