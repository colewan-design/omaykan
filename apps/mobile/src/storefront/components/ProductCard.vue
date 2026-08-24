<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Plus, ShoppingBasket } from '@lucide/vue'
import type { Product } from '@pos/shared/index'
import { formatCurrency } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/storefront/cart'

const props = defineProps<{
  product: Product
  categoryName: string
}>()

const cart = useStorefrontCart()
const router = useRouter()

function quantity(): number {
  return cart.cartLines.value.find((line) => line.product.id === props.product.id)?.quantity ?? 0
}

function openDetail() {
  void router.push({ name: 'product', params: { productId: props.product.id } })
}

function addOne(event: Event) {
  event.stopPropagation()
  cart.add(props.product)
}

function removeOne(event: Event) {
  event.stopPropagation()
  cart.decrement(props.product.id)
}
</script>

<template>
  <article class="pc" @click="openDetail">
    <div class="pc__art">
      <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" loading="lazy" class="pc__image" />
      <div v-else class="pc__fallback">
        <ShoppingBasket :size="28" />
      </div>
    </div>

    <div class="pc__body">
      <p class="pc__title">{{ product.name }}</p>
      <div class="pc__row">
        <strong class="pc__price">{{ formatCurrency(product.priceCents) }}</strong>

        <button v-if="quantity() === 0" type="button" class="pc__add" aria-label="Add to cart" @click="addOne">
          <Plus :size="16" />
        </button>
        <div v-else class="pc__stepper" @click.stop>
          <button type="button" aria-label="Remove one" @click="removeOne">-</button>
          <span>{{ quantity() }}</span>
          <button type="button" aria-label="Add one" @click="addOne">+</button>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.pc {
  display: grid;
  gap: 10px;
  cursor: pointer;
}

.pc__art {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: 20px;
  background: #ffffff;
  overflow: hidden;
  box-shadow: 0 8px 20px rgba(31, 36, 48, 0.06);
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
  color: #c7c4bc;
}

.pc__body {
  display: grid;
  gap: 6px;
  padding: 0 2px;
}

.pc__title {
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #1f2430;
  font-size: 0.9rem;
  font-weight: 600;
}

.pc__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.pc__price {
  color: #1f2430;
  font-size: 1rem;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.pc__add,
.pc__stepper button {
  border: none;
  font-family: inherit;
}

.pc__add {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--sf-primary);
  color: #fff;
}

.pc__stepper {
  display: grid;
  grid-template-columns: 24px 1fr 24px;
  align-items: center;
  gap: 6px;
  padding: 3px;
  border-radius: 999px;
  background: var(--sf-banner-green);
}

.pc__stepper button {
  height: 24px;
  border-radius: 999px;
  background: var(--sf-primary);
  color: #fff;
  font-size: 0.9rem;
  font-weight: 800;
}

.pc__stepper span {
  text-align: center;
  color: var(--sf-primary-dark);
  font-size: 0.82rem;
  font-weight: 800;
}
</style>
