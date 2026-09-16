<script setup lang="ts">
import { computed } from 'vue'
import { Minus, Plus } from '@lucide/vue'
import { formatCurrency, type Product } from '@pos/shared/index'
import ProductArt from '@pos/web/landing/ProductArt.vue'

// One line of a shop's menu: photo on the left, name and price on the right,
// and an Add button that turns into a stepper once the item is in the basket.
//
// The basket is the parent's to change, not this card's: adding from a shop
// page can mean asking first whether to empty a basket filled at another shop,
// and that question belongs to the page.

const props = withDefaults(
  defineProps<{
    product: Product
    quantity: number
    categoryName?: string
    merchantImageUrl?: string
    /** The first shelf's cards are bigger, as the menu's opening course. */
    featured?: boolean
  }>(),
  { categoryName: '', merchantImageUrl: '', featured: false },
)

const emit = defineEmits<{
  add: [product: Product]
  decrement: [productId: string]
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
  gap: 16px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--sf-rule);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(35, 29, 24, 0.04);
}

.mc__art {
  position: relative;
  flex: 0 0 auto;
  width: 112px;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 10px;
  background: var(--sf-sand);
}

.mc--featured .mc__art {
  width: 132px;
}

.mc__body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  padding: 4px 4px 2px 0;
}

.mc__name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--sf-ink);
  overflow-wrap: anywhere;
}

.mc__detail {
  display: -webkit-box;
  margin: 6px 0 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.45;
  color: var(--sf-muted);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.mc__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 12px;
}

.mc__price {
  display: flex;
  flex-direction: column;
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: var(--sf-ink);
  font-variant-numeric: tabular-nums;
}

.mc__was {
  font-size: 12px;
  font-weight: 500;
  color: var(--sf-faint);
}

.mc__add {
  min-width: 96px;
  min-height: 40px;
  padding: 0 20px;
  border: none;
  border-radius: 999px;
  background: var(--sf-clay);
  color: #fff;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: background 150ms ease, transform 150ms ease;
}

.mc__add:hover {
  background: var(--sf-clay-deep);
}

.mc__add:active {
  transform: scale(0.97);
}

.mc__step {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  border: 1px solid var(--sf-rule);
  border-radius: 999px;
  background: var(--sf-paper);
}

.mc__step-btn {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: none;
  color: var(--sf-clay);
  cursor: pointer;
}

.mc__step-btn:hover {
  background: var(--sf-sand);
}

.mc__qty {
  min-width: 26px;
  font-weight: 700;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.mc__add:focus-visible,
.mc__step-btn:focus-visible {
  outline: 2px solid var(--sf-forest);
  outline-offset: 2px;
}

@media (max-width: 420px) {
  .mc {
    gap: 12px;
  }

  .mc__art,
  .mc--featured .mc__art {
    width: 92px;
  }

  .mc__add {
    min-width: 80px;
  }
}
</style>
