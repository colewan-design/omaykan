<script setup lang="ts">
import { computed } from 'vue'
import { Heart, Minus, Plus, ShoppingCart } from '@lucide/vue'
import { formatCurrency, type Product } from '@pos/shared/index'
import ProductArt from '@pos/web/landing/ProductArt.vue'

// One line of a shop's menu: photo on the left, name and price on the right,
// and an Add button that turns into a stepper once the item is in the basket.
//
// The basket is the parent's to change, not this card's: adding from a shop
// page can mean asking first whether to empty a basket filled at another shop,
// and that question belongs to the page. The heart is the same: whether a
// product is saved lives in commerce/favorites.ts, which the page reads.

const props = withDefaults(
  defineProps<{
    product: Product
    quantity: number
    categoryName?: string
    merchantImageUrl?: string
    /** The first shelf's cards are bigger, as the menu's opening course. */
    featured?: boolean
    /** Whether the product is in the shopper's saved items. */
    saved?: boolean
  }>(),
  { categoryName: '', merchantImageUrl: '', featured: false, saved: false },
)

const emit = defineEmits<{
  add: [product: Product]
  decrement: [productId: string]
  toggleSave: [product: Product]
}>()

/** What sits under the name: the pack and the brand, whichever the shop filled in. */
const detail = computed(() =>
  [props.product.brand, props.product.unitLabel, props.product.packagingType]
    .filter((part): part is string => typeof part === 'string' && part.trim() !== '')
    .join(' · '),
)

const onSale = computed(
  () => (props.product.compareAtPriceCents ?? 0) > props.product.priceCents,
)

/** How much off, rounded down so the badge never overstates the saving. */
const discount = computed(() => {
  const was = props.product.compareAtPriceCents ?? 0
  if (!onSale.value) return 0
  return Math.floor(((was - props.product.priceCents) / was) * 100)
})

/**
 * "Only 3 left", when the shop has said what counts as low for this item.
 * Nothing is inferred: both the count and the threshold come off the product,
 * and anything already at zero never reaches the storefront at all.
 */
const lowStock = computed(() => {
  const { stockQty, lowStockThreshold } = props.product
  if (stockQty == null || lowStockThreshold == null) return 0
  return stockQty > 0 && stockQty <= lowStockThreshold ? stockQty : 0
})
</script>

<template>
  <article class="mc" :class="{ 'mc--featured': featured }">
    <div class="mc__art">
      <ProductArt
        :product="product"
        :category-name="categoryName"
        :merchant-image-url="merchantImageUrl"
        :size="featured ? 44 : 36"
      />
      <p v-if="discount > 0 || lowStock > 0" class="mc__badges">
        <span v-if="discount > 0" class="mc__badge mc__badge--sale">−{{ discount }}%</span>
        <span v-if="lowStock > 0" class="mc__badge mc__badge--stock">Only {{ lowStock }} left</span>
      </p>
      <button
        type="button"
        class="mc__favorite"
        :class="{ 'is-liked': saved }"
        :aria-label="saved ? `Remove ${product.name} from your saved items` : `Save ${product.name} for later`"
        :aria-pressed="saved"
        @click="emit('toggleSave', product)"
      >
        <Heart :size="17" :stroke-width="2.2" :fill="saved ? 'currentColor' : 'none'" />
      </button>
    </div>

    <div class="mc__body">
      <h3 class="mc__name">{{ product.name }}</h3>
      <p v-if="detail" class="mc__detail">{{ detail }}</p>

      <div class="mc__foot">
        <p class="mc__price">
          <span>{{ formatCurrency(product.priceCents) }}</span>
          <s v-if="onSale" class="mc__was">{{ formatCurrency(product.compareAtPriceCents ?? 0) }}</s>
        </p>

        <button
          v-if="quantity === 0"
          type="button"
          class="mc__add"
          :aria-label="`Add ${product.name} to cart`"
          @click="emit('add', product)"
        >
          <ShoppingCart class="mc__add-icon" :size="15" :stroke-width="2.2" aria-hidden="true" />
          Add
        </button>

        <div v-else class="mc__step" role="group" :aria-label="`${product.name} in cart`">
          <button
            type="button"
            class="mc__step-btn"
            :aria-label="`Remove one ${product.name}`"
            @click="emit('decrement', product.id)"
          >
            <Minus :size="16" :stroke-width="2.4" />
          </button>
          <span class="mc__qty" aria-live="polite">{{ quantity }}</span>
          <button
            type="button"
            class="mc__step-btn"
            :aria-label="`Add one more ${product.name}`"
            @click="emit('add', product)"
          >
            <Plus :size="16" :stroke-width="2.4" />
          </button>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.mc {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  border: 1px solid #ece7dd;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 7px 18px rgba(32, 45, 37, 0.08);
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.mc__art {
  position: relative;
  width: 100%;
  aspect-ratio: 1.58 / 1;
  overflow: hidden;
  border-radius: 12px 12px 8px 8px;
  background: var(--sf-sand);
}

.mc__favorite {
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  color: #17251d;
  box-shadow: 0 3px 9px rgba(22, 35, 27, 0.14);
  cursor: pointer;
  backdrop-filter: blur(6px);
}

.mc__favorite.is-liked { color: #bd4637; }

.mc__badges {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  flex-wrap: wrap;
  max-width: calc(100% - 52px);
  margin: 0;
  gap: 5px;
}

.mc__badge {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.mc__badge--sale { background: #bd4637; color: #fff; }
.mc__badge--stock { background: rgba(255, 255, 255, 0.95); color: #8a5a12; }
.mc__add-icon { flex: 0 0 auto; }

.mc__body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  padding: 9px 10px 10px;
}

.mc__name {
  margin: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.25;
  color: var(--sf-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mc__detail {
  margin: 3px 0 0;
  overflow: hidden;
  font-size: 10px;
  line-height: 1.3;
  color: var(--sf-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mc__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: auto;
  padding-top: 8px;
}

.mc__price {
  display: flex;
  flex-direction: column;
  margin: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 800;
  color: var(--sf-ink);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.mc__was {
  font-size: 12px;
  font-weight: 500;
  color: var(--sf-faint);
}

.mc__add {
  display: inline-flex;
  min-width: 72px;
  min-height: 34px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 14px;
  border: 1px solid #e4ddd2;
  border-radius: 12px;
  background: #fff;
  color: #da6a18;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition: background 150ms ease, transform 150ms ease;
}

.mc__add:hover {
  background: #fff8ed;
}

.mc__add:active {
  transform: scale(0.97);
}

.mc__step {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  border: 1px solid #e4ddd2;
  border-radius: 12px;
  background: var(--sf-paper);
}

.mc__step-btn {
  display: grid;
  width: 30px;
  height: 32px;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: none;
  color: #d96a18;
  cursor: pointer;
}

.mc__step-btn:hover {
  background: var(--sf-sand);
}

.mc__qty {
  min-width: 18px;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.mc__add:focus-visible,
.mc__step-btn:focus-visible,
.mc__favorite:focus-visible {
  outline: 2px solid var(--sf-forest);
  outline-offset: 2px;
}

/* Desktop: the products are what the page is for, so the cards carry their
   own weight — a taller photo, readable names, and a filled Add button. */
@media (min-width: 900px) {
  .mc { border-radius: 16px; }
  .mc:hover { border-color: #dbd2c3; box-shadow: 0 12px 26px rgba(32, 45, 37, 0.12); transform: translateY(-2px); }
  .mc__art { aspect-ratio: 1.42 / 1; border-radius: 15px 15px 8px 8px; }
  .mc__favorite { top: 10px; right: 10px; width: 34px; height: 34px; }
  .mc__badges { top: 10px; left: 10px; }
  .mc__badge { padding: 5px 9px; font-size: 11px; }
  .mc__body { padding: 13px 14px 14px; }
  .mc__name { font-size: 15px; white-space: normal; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; min-height: 2.5em; }
  .mc__detail { margin-top: 5px; font-size: 11.5px; }
  .mc__foot { gap: 10px; padding-top: 12px; }
  .mc__price { font-size: 17px; }
  .mc__was { font-size: 12.5px; }
  .mc__add { min-width: 86px; min-height: 40px; border-color: #e7c9a5; background: #fff8ee; font-size: 13px; }
  .mc__add:hover { border-color: #da6a18; background: #da6a18; color: #fff; }
  .mc__step { min-height: 40px; border-radius: 12px; }
  .mc__step-btn { width: 36px; height: 38px; }
  .mc__qty { min-width: 22px; font-size: 14px; }
}

@media (prefers-reduced-motion: reduce) {
  .mc,
  .mc__add { transition: none; }
  .mc:hover { transform: none; }
}

@media (max-width: 520px) {
  .mc__body { padding: 7px 7px 8px; }
  .mc__name { font-size: 12px; }
  .mc__foot { gap: 3px; }
  .mc__price { font-size: 11px; }
  .mc__detail { display: none; }
  .mc__favorite { top: 5px; right: 5px; width: 28px; height: 28px; }
  .mc__add { min-width: 52px; min-height: 30px; padding-inline: 8px; font-size: 11px; }
  .mc__add-icon { display: none; }
  .mc__badge { padding: 3px 7px; font-size: 9.5px; }
  .mc__step { min-height: 30px; }
  .mc__step-btn { width: 22px; height: 28px; }
  .mc__qty { min-width: 12px; font-size: 11px; }
}
</style>
