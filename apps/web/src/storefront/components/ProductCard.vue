<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ShoppingBasket } from '@lucide/vue'
import { computed } from 'vue'
import type { Product } from '@pos/shared/index'
import { discountPercent, formatCurrency } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/storefront/cart'
import { useStorefrontWishlist } from '@pos/web/storefront/wishlist'

const props = defineProps<{
  product: Product
  categoryName: string
  featured?: boolean
}>()

const cart = useStorefrontCart()
const wishlist = useStorefrontWishlist()

const discount = computed(() => discountPercent(props.product))

function quantity(): number {
  return cart.cartLines.value.find((line) => line.product.id === props.product.id)?.quantity ?? 0
}
</script>

<template>
  <article class="pc" :class="{ 'pc--featured': featured }">
    <div class="pc__art">
      <span v-if="discount !== null" class="pc__badge">-{{ discount }}%</span>
      <!-- Only the artwork and the title link through to the detail page; the
           wishlist toggle and the cart controls stay outside the link so they
           act in place instead of navigating. -->
      <RouterLink :to="{ name: 'product', params: { productId: product.id } }" class="pc__art-link">
        <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" loading="lazy" class="pc__image" />
        <div v-else class="pc__fallback">
          <ShoppingBasket :size="28" />
        </div>
      </RouterLink>

      <button
        type="button"
        class="pc__love"
        :class="{ 'pc__love--active': wishlist.has(product.id) }"
        :title="wishlist.has(product.id) ? 'Remove from wishlist' : 'Add to wishlist'"
        @click="wishlist.toggle(product.id)"
      >
        <svg viewBox="0 0 24 24" width="15" height="15" :fill="wishlist.has(product.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
          <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.8 1-1a5.5 5.5 0 0 0 0-7.8z" />
        </svg>
      </button>
    </div>

    <div class="pc__body">
      <span class="pc__category">{{ categoryName }}</span>
      <RouterLink :to="{ name: 'product', params: { productId: product.id } }" class="pc__title-link">
        <p class="pc__title">{{ product.name }}</p>
      </RouterLink>
      <div class="pc__price-row">
        <strong class="pc__price">{{ formatCurrency(product.priceCents) }}</strong>
        <span v-if="discount !== null" class="pc__was">{{ formatCurrency(product.compareAtPriceCents!) }}</span>
        <span v-if="product.unitLabel" class="pc__unit">{{ product.unitLabel }}</span>
      </div>
    </div>

    <div class="pc__actions">
      <button v-if="quantity() === 0" type="button" class="pc__add" @click="cart.add(product)">Add to cart</button>
      <div v-else class="pc__stepper">
        <button type="button" aria-label="Remove one" @click="cart.decrement(product.id)">−</button>
        <span>{{ quantity() }}</span>
        <button type="button" aria-label="Add one" @click="cart.add(product)">+</button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.pc {
  display: grid;
  grid-template-rows: auto 1fr auto;
  border-radius: var(--radius-lg);
  background: var(--bg-elevated);
  border: 1px solid var(--separator);
  overflow: hidden;
  transition: box-shadow var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.pc:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.pc__art {
  position: relative;
  aspect-ratio: 1 / 1;
  background: var(--fill);
  overflow: hidden;
}

.pc__art-link {
  display: block;
  width: 100%;
  height: 100%;
}

.pc__image,
.pc__fallback {
  width: 100%;
  height: 100%;
}

.pc__image {
  display: block;
  object-fit: cover;
}

.pc__fallback {
  display: grid;
  place-items: center;
  color: var(--text-tertiary);
}

.pc__love {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-elevated);
  color: var(--text-tertiary);
  box-shadow: var(--shadow-sm);
}

.pc__love--active {
  color: var(--danger);
}

.pc__body {
  display: grid;
  gap: 4px;
  padding: var(--space-3) var(--space-3) var(--space-2);
}

.pc__category {
  color: var(--text-tertiary);
  font-size: 11.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.pc__title-link {
  text-decoration: none;
  color: inherit;
}

.pc__title-link:hover .pc__title {
  color: var(--accent);
}

.pc__title {
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.6em;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
}

.pc__price-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 2px;
}

.pc__price {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.pc__unit {
  color: var(--text-tertiary);
  font-size: 12px;
}

.pc__was {
  color: var(--text-tertiary);
  font-size: 12.5px;
  text-decoration: line-through;
}

.pc__badge {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  background: var(--accent);
  color: var(--accent-text-on);
  font-size: 11.5px;
  font-weight: 800;
}

.pc__actions {
  padding: 0 var(--space-3) var(--space-3);
}

.pc__add,
.pc__stepper button {
  border: none;
  font-family: inherit;
  cursor: pointer;
}

.pc__add {
  width: 100%;
  min-height: 36px;
  border-radius: var(--radius-md);
  background: var(--fill);
  color: var(--accent);
  font-size: 13px;
  font-weight: 700;
}

.pc__add:hover {
  background: var(--accent);
  color: var(--accent-text-on);
}

.pc__stepper {
  display: grid;
  grid-template-columns: 32px 1fr 32px;
  align-items: center;
  gap: 6px;
  padding: 3px;
  border-radius: var(--radius-md);
  background: var(--fill);
}

.pc__stepper button {
  height: 30px;
  border-radius: calc(var(--radius-md) - 3px);
  background: var(--accent);
  color: var(--accent-text-on);
  font-size: 15px;
  font-weight: 700;
}

.pc__stepper span {
  text-align: center;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 700;
}
</style>
