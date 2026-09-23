<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, ChevronLeft, ChevronRight } from '@lucide/vue'
import type { Product } from '@pos/shared/index'
import ProductCard from './ProductCard.vue'

// A horizontally-scrolling shelf of product cards: serif heading and "See all"
// on one line, then the cards, with round arrows riding the shelf's two edges
// the way the redesign draws them.
//
// The card itself lives in ProductCard.vue, shared with the category grid.

const props = withDefaults(
  defineProps<{
    title: string
    products: Product[]
    /** Optional line under the heading. */
    blurb?: string
    /** Empty hides the link. */
    viewAllHref?: string
    /** Anchor, so a footer or a link can point at one shelf. */
    anchor?: string
    /**
     * Aisle names by id, so a product with no photograph gets its aisle's
     * glyph. A shelf here can be mixed — "Under ₱100" is every aisle at once —
     * so it is a lookup and not one name.
     */
    categoryNames?: Record<string, string>
    /** The shop's own photo, if its owner uploaded one. See ProductArt. */
    merchantImageUrl?: string
  }>(),
  { blurb: '', viewAllHref: '', anchor: '', categoryNames: () => ({}), merchantImageUrl: '' },
)

const emit = defineEmits<{ select: [productId: string]; viewAll: [] }>()

/**
 * "See all" is a real link to a real URL, so a middle-click or a modifier
 * opens it in a tab like any other. A plain left-click is handled in page —
 * the shelf, the listing and the product are all faces of one document, and
 * reloading to change which one is showing is the thing this storefront
 * deliberately does not do.
 */
function openAll(event: MouseEvent) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  emit('viewAll')
}

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
        <h2 class="sf-h2">{{ title }}</h2>
        <p v-if="blurb" class="fdrow__blurb">{{ blurb }}</p>
      </div>
      <a v-if="viewAllHref" :href="viewAllHref" class="sf-more" @click="openAll">
        See all
        <ArrowRight :size="15" :stroke-width="2" />
      </a>
    </div>

    <div class="fdrow__track">
      <button type="button" class="fdrow__arrow fdrow__arrow--prev" aria-label="Scroll left" @click="scrollBy(-1)">
        <ChevronLeft :size="20" :stroke-width="2" />
      </button>

      <div ref="shelf" class="fdrow__shelf">
        <ProductCard
          v-for="product in products"
          :key="product.id"
          :product="product"
          :category-name="categoryNames[product.categoryId] ?? ''"
          :merchant-image-url="merchantImageUrl"
          @select="emit('select', $event)"
        />
      </div>

      <button type="button" class="fdrow__arrow fdrow__arrow--next" aria-label="Scroll right" @click="scrollBy(1)">
        <ChevronRight :size="20" :stroke-width="2" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.fdrow { margin-bottom: 56px; }

.fdrow__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.fdrow__headline { min-width: 0; }

.fdrow__blurb {
  margin: 6px 0 0;
  max-width: 780px;
  font-size: 14.5px;
  line-height: 1.6;
  color: var(--sf-muted);
}

.fdrow__track { position: relative; }

/* Fixed track, not minmax(..., 1fr): a shelf short enough to fit without
   scrolling would otherwise stretch its cards across the full width. Every
   shelf uses one card size and short rows simply end early. */
.fdrow__shelf {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 180px;
  gap: 22px;
  overflow-x: auto;
  scroll-snap-type: x proximity;
  scrollbar-width: none;
  padding-bottom: 4px;
}
.fdrow__shelf::-webkit-scrollbar { display: none; }

/* Centred on the photo row, not the whole card: the photo is 180px square. */
.fdrow__arrow {
  position: absolute;
  top: 90px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--sf-rule);
  border-radius: 50%;
  background: var(--sf-sand);
  color: var(--sf-ink);
  box-shadow: 0 6px 16px rgba(23, 35, 28, 0.14);
  transform: translateY(-50%);
  cursor: pointer;
  transition: background 150ms, color 150ms;
}
.fdrow__arrow:hover { background: var(--sf-forest); color: var(--sf-paper); }
.fdrow__arrow:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }
.fdrow__arrow--prev { left: -14px; }
.fdrow__arrow--next { right: -14px; }

@media (max-width: 720px) {
  .fdrow__shelf { grid-auto-columns: 148px; gap: 14px; }
  /* Swiping is how a phone scrolls a shelf; the arrows would only cover cards. */
  .fdrow__arrow { display: none; }
}
</style>
