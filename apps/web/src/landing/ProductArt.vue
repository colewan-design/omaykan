<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import MountainMark from './MountainMark.vue'
import { productArt } from './productArt'
import { SIZES, srcSet } from '@pos/web/ui/responsiveImg'
import type { Product } from '@pos/shared/index'

// The art slot of a product tile: the photograph when there is one, and a
// drawn stand-in when there isn't. See productArt.ts for what falls back to
// what, and why there is no generated photograph among the options.

const props = withDefaults(
  defineProps<{
    product: Product
    /** The product's aisle, as the catalog names it. Blank when unknown. */
    categoryName?: string
    /** The shop's own photo, if its owner uploaded one. */
    merchantImageUrl?: string
    /** How big the mark is drawn — a list row's art slot is smaller. */
    size?: number
  }>(),
  { categoryName: '', merchantImageUrl: '', size: 44 },
)

/**
 * A photo url that 404s used to leave the browser's own broken-image glyph in
 * the tile, which is worse than the placeholder this component already knows
 * how to draw. Reset when the url changes, or a recycled tile would stay
 * broken after scrolling onto a product whose photo is fine.
 */
const broken = ref(false)
watch(() => props.product.imageUrl, () => { broken.value = false })

const art = computed(() =>
  productArt(
    { id: props.product.id, imageUrl: broken.value ? undefined : props.product.imageUrl },
    props.categoryName,
    props.merchantImageUrl,
  ),
)
</script>

<template>
  <img
    v-if="art.kind === 'photo'"
    class="part__photo"
    :src="art.src"
    :srcset="srcSet(art.src)"
    :sizes="SIZES.card"
    :alt="product.name"
    loading="lazy"
    @error="broken = true"
  />

  <!-- Decorative: the name, the price and the aisle are all already on the
       card, so announcing the stand-in only repeats them. -->
  <div v-else class="part" :class="`part--t${art.tone}`" aria-hidden="true">
    <img
      v-if="art.backdrop"
      class="part__shop"
      :src="art.backdrop"
      :srcset="srcSet(art.backdrop)"
      :sizes="SIZES.thumb"
      alt=""
      loading="lazy"
    />
    <component
      :is="art.kind === 'aisle' ? art.icon : MountainMark"
      v-bind="art.kind === 'aisle' ? { size, strokeWidth: 1.4 } : { size: Math.round(size * 1.4), sun: false }"
      class="part__mark"
    />
  </div>
</template>

<style scoped>
/* Both faces fill the card's art slot, which every caller gives a size and
   `position: relative` — absolute rather than `height: 100%` so the slot can
   take its height from a flex row (the list view) as readily as from an
   aspect-ratio (the grid). */
.part__photo {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* The branded panel. A wash of the forest green over the cream the cards are
   already made of, so a shelf of stand-ins still belongs to the page rather
   than punching four grey holes in it. */
.part {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  background:
    radial-gradient(120% 100% at 50% 0%, rgba(255, 253, 249, 0.85) 0%, rgba(255, 253, 249, 0) 62%),
    linear-gradient(160deg, var(--sf-cream) 0%, var(--sf-sand) 100%);
  color: var(--sf-forest-soft);
}

/* Four tints of the same wash, so a page of stand-ins reads as a row of items
   and not one flat block. Kept close together: this is texture, not colour
   coding, and nothing about a product is being said by which one it gets. */
.part--t1 { background: radial-gradient(120% 100% at 50% 0%, rgba(255, 253, 249, 0.9) 0%, rgba(255, 253, 249, 0) 60%), linear-gradient(160deg, #f1efe4 0%, #e6e2d2 100%); }
.part--t2 { background: radial-gradient(120% 100% at 50% 0%, rgba(255, 253, 249, 0.82) 0%, rgba(255, 253, 249, 0) 64%), linear-gradient(160deg, #f4f1e7 0%, #e4ddcd 100%); }
.part--t3 { background: radial-gradient(120% 100% at 50% 0%, rgba(255, 253, 249, 0.88) 0%, rgba(255, 253, 249, 0) 58%), linear-gradient(160deg, #eff0e6 0%, #e2e0d0 100%); }

/* A faint ruled edge, so the panel is a drawn thing and not a blank. */
.part::after {
  content: '';
  position: absolute;
  inset: 9px;
  border: 1px solid rgba(31, 46, 37, 0.09);
  border-radius: 7px;
  pointer-events: none;
}

/* The shop's own photo, if it has one: far enough back to be texture, never
   far enough forward to be read as a picture of the product. */
.part__shop {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.14;
  filter: grayscale(1) contrast(0.85);
}

.part__mark {
  position: relative;
  opacity: 0.72;
}

@media (prefers-reduced-motion: no-preference) {
  .part__mark { transition: opacity 180ms ease, transform 180ms ease; }
}
</style>
