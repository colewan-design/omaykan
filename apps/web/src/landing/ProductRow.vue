<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Product } from '@pos/shared/index'
import ProductCard from './ProductCard.vue'

// A horizontally-scrolling shelf of product cards — the repeating unit of the
// reference grocery layout: heading + "View all", arrow controls on the right,
// then a row of cards you can add to the cart without leaving the page.
//
// The card itself lives in ProductCard.vue, shared with the category grid.

const props = withDefaults(
  defineProps<{
    title: string
    products: Product[]
    /** Optional paragraph between the heading and the shelf. */
    blurb?: string
    /** Empty hides the link. */
    viewAllHref?: string
    /** Anchor, so a footer or a link can point at one shelf. */
    anchor?: string
  }>(),
  { blurb: '', viewAllHref: '', anchor: '' },
)

defineEmits<{ select: [productId: string] }>()

const shelf = ref<HTMLElement | null>(null)

const hasProducts = computed(() => props.products.length > 0)

function scrollBy(direction: -1 | 1) {
  const el = shelf.value
  if (!el) return
  // One "page" is whatever is visible, less a sliver so the next card peeks in.
  el.scrollBy({ left: direction * (el.clientWidth - 80), behavior: 'smooth' })
}
</script>

<template>
  <section v-if="hasProducts" :id="props.anchor || undefined" class="fdrow">
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
      <ProductCard
        v-for="product in products"
        :key="product.id"
        :product="product"
        @select="$emit('select', $event)"
      />
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
/* Fixed track, not minmax(..., 1fr): a shelf short enough to fit without
   scrolling would otherwise stretch its cards across the full width, so a
   three-item row like "Fresh deals" rendered cards 2.5x the size of every
   other card on the page. Every shelf now uses one card size and short rows
   simply end early. */
.fdrow__shelf {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 178px;
  gap: 20px;
  margin-top: 18px;
  overflow-x: auto;
  scroll-snap-type: x proximity;
  scrollbar-width: none;
  padding-bottom: 4px;
}
.fdrow__shelf::-webkit-scrollbar { display: none; }

@media (max-width: 720px) {
  .fdrow__shelf { grid-auto-columns: 148px; gap: 14px; }
  .fdrow__arrows { display: none; }
}
</style>
