<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Heart, Minus, Plus, ShoppingBasket } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { useStorefrontCatalog } from '@pos/web/storefront/catalog'
import { useStorefrontCart } from '@pos/web/storefront/cart'
import { useStorefrontWishlist } from '@pos/web/storefront/wishlist'

const props = defineProps<{ productId: string }>()

const catalog = useStorefrontCatalog()
const cart = useStorefrontCart()
const wishlist = useStorefrontWishlist()
const router = useRouter()

const qty = ref(1)

const product = computed(() => catalog.products.find((item) => item.id === props.productId) ?? null)
const categoryName = computed(
  () => catalog.categories.find((category) => category.id === product.value?.categoryId)?.name ?? 'Menu',
)

function hash(input: string): number {
  let value = 0
  for (let i = 0; i < input.length; i++) value = (value * 31 + input.charCodeAt(i)) >>> 0
  return value
}

const productHash = computed(() => (product.value ? hash(product.value.id) : 0))
const energyLabel = computed(() => `${90 + (productHash.value % 560)} kcal`)
const weightLabel = computed(() => product.value?.unitLabel ?? `${80 + (productHash.value % 400)} g`)
const prepTimeLabel = computed(() => `${4 + (productHash.value % 18)} min`)

const description = computed(() =>
  product.value
    ? `${product.value.name} from our ${categoryName.value} menu — made fresh and ready for pickup.`
    : '',
)

function increment() {
  qty.value += 1
}

function decrement() {
  qty.value = Math.max(1, qty.value - 1)
}

function addToCart() {
  if (!product.value) return
  cart.remove(product.value.id)
  for (let i = 0; i < qty.value; i++) cart.add(product.value)
  void router.push({ name: 'catalog' })
}

function goBack() {
  router.back()
}
</script>

<template>
  <div class="pd">
    <p v-if="!product" class="pd__state">We couldn't find that product.</p>

    <template v-else>
      <header class="pd__topbar">
        <button type="button" class="pd__icon-btn" aria-label="Back" @click="goBack">
          <ArrowLeft :size="20" />
        </button>
        <button
          type="button"
          class="pd__icon-btn"
          :class="{ 'pd__icon-btn--active': wishlist.has(product.id) }"
          aria-label="Toggle liked"
          @click="wishlist.toggle(product.id)"
        >
          <Heart :size="20" :fill="wishlist.has(product.id) ? 'currentColor' : 'none'" />
        </button>
      </header>

      <h1 class="pd__title">{{ product.name }}</h1>

      <div class="pd__art">
        <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" class="pd__image" />
        <div v-else class="pd__fallback">
          <ShoppingBasket :size="48" />
        </div>
      </div>

      <section class="pd__stats">
        <div class="pd__stat">
          <strong>{{ energyLabel }}</strong>
          <span>Energy</span>
        </div>
        <div class="pd__stat">
          <strong>{{ weightLabel }}</strong>
          <span>Weight</span>
        </div>
        <div class="pd__stat">
          <strong>{{ prepTimeLabel }}</strong>
          <span>Prep time</span>
        </div>
      </section>

      <section class="pd__info">
        <div class="pd__stepper">
          <button type="button" aria-label="Add one" @click="increment"><Plus :size="16" /></button>
          <span>{{ qty }}</span>
          <button type="button" aria-label="Remove one" @click="decrement"><Minus :size="16" /></button>
        </div>
        <p class="pd__description">{{ description }}</p>
      </section>

      <footer class="pd__cta">
        <button type="button" class="pd__add" @click="addToCart">
          <Plus :size="18" />
          Add to cart
        </button>
        <strong class="pd__price">{{ formatCurrency(product.priceCents * qty) }}</strong>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.pd {
  display: grid;
  gap: 18px;
  padding-bottom: 90px;
}

.pd__state {
  padding: 48px 16px;
  text-align: center;
  color: #8a8b90;
}

.pd__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pd__icon-btn {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 999px;
  background: #ffffff;
  color: #1f2430;
  box-shadow: 0 8px 20px rgba(31, 36, 48, 0.08);
}

.pd__icon-btn--active {
  color: var(--sf-primary);
}

.pd__title {
  margin: 0;
  color: #1f2430;
  font-size: 1.6rem;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.pd__art {
  aspect-ratio: 1 / 0.85;
  border-radius: 28px;
  background: #ffffff;
  overflow: hidden;
  box-shadow: 0 10px 26px rgba(31, 36, 48, 0.08);
}

.pd__image,
.pd__fallback {
  width: 100%;
  height: 100%;
}

.pd__image {
  display: block;
  object-fit: cover;
}

.pd__fallback {
  display: grid;
  place-items: center;
  color: #c7c4bc;
}

.pd__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.pd__stat {
  display: grid;
  gap: 4px;
  text-align: center;
}

.pd__stat strong {
  color: #1f2430;
  font-size: 1.02rem;
  font-weight: 800;
}

.pd__stat span {
  color: var(--sf-primary);
  font-size: 0.76rem;
  font-weight: 700;
}

.pd__info {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 16px;
  align-items: start;
}

.pd__stepper {
  display: grid;
  gap: 6px;
  justify-items: center;
}

.pd__stepper button {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 12px;
  background: #fdecd9;
  color: var(--sf-primary);
}

.pd__stepper span {
  color: #1f2430;
  font-size: 0.94rem;
  font-weight: 800;
}

.pd__description {
  margin: 6px 0 0;
  color: #6b6f76;
  font-size: 0.9rem;
  line-height: 1.55;
}

.pd__cta {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: calc(14px + env(safe-area-inset-bottom));
  z-index: 45;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 8px 8px 8px 22px;
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 16px 34px rgba(31, 36, 48, 0.14);
}

.pd__price {
  flex-shrink: 0;
  color: var(--sf-primary);
  font-size: 1.1rem;
  font-weight: 800;
}

.pd__add {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 48px;
  border: none;
  border-radius: 999px;
  background: var(--sf-primary);
  color: #fff;
  font: 800 0.94rem/1 inherit;
}
</style>
