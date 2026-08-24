<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowLeft, Check, Minus, Plus, ShoppingBasket } from '@lucide/vue'
import { discountPercent, formatCurrency } from '@pos/shared/index'
import { useStorefrontCatalog } from '@pos/web/storefront/catalog'
import { useStorefrontCart } from '@pos/web/storefront/cart'
import { useStorefrontWishlist } from '@pos/web/storefront/wishlist'

const props = defineProps<{ productId: string }>()

const catalog = useStorefrontCatalog()
const cart = useStorefrontCart()
const wishlist = useStorefrontWishlist()
const router = useRouter()

const qty = ref(1)
const justAdded = ref(false)

const product = computed(() => catalog.products.find((item) => item.id === props.productId) ?? null)

const categoryName = computed(
  () => catalog.categories.find((category) => category.id === product.value?.categoryId)?.name ?? 'Catalog',
)

/** How many of this product are already in the cart. */
const inCart = computed(
  () => cart.cartLines.value.find((line) => line.product.id === props.productId)?.quantity ?? 0,
)

const discount = computed(() => (product.value ? discountPercent(product.value) : null))

const related = computed(() =>
  catalog.products
    .filter((item) => item.categoryId === product.value?.categoryId && item.id !== props.productId)
    .slice(0, 4),
)

function increment() {
  qty.value += 1
}

function decrement() {
  qty.value = Math.max(1, qty.value - 1)
}

function addToCart() {
  if (!product.value) return
  // cart.add() appends one line-item at a time, so add the chosen quantity by
  // repeating it — this keeps the existing cart quantity rather than replacing it.
  for (let i = 0; i < qty.value; i++) cart.add(product.value)
  qty.value = 1
  justAdded.value = true
  window.setTimeout(() => { justAdded.value = false }, 2000)
}

function buyNow() {
  addToCart()
  void router.push({ name: 'checkout' })
}
</script>

<template>
  <div class="pd">
    <p v-if="catalog.loading" class="pd__state">Loading product…</p>
    <p v-else-if="!product" class="pd__state">
      We couldn't find that product. <RouterLink to="/">Back to the store</RouterLink>
    </p>

    <template v-else>
      <button type="button" class="pd__back" @click="router.back()">
        <ArrowLeft :size="16" />
        Back
      </button>

      <div class="pd__layout">
        <div class="pd__art">
          <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" class="pd__image" />
          <div v-else class="pd__fallback"><ShoppingBasket :size="48" /></div>
        </div>

        <div class="pd__body">
          <span class="pd__category">{{ categoryName }}</span>
          <h1 class="pd__title">{{ product.name }}</h1>

          <div class="pd__price-row">
            <strong class="pd__price">{{ formatCurrency(product.priceCents) }}</strong>
            <template v-if="discount !== null">
              <span class="pd__was">{{ formatCurrency(product.compareAtPriceCents!) }}</span>
              <span class="pd__badge">-{{ discount }}%</span>
            </template>
            <span v-if="product.unitLabel" class="pd__unit">per {{ product.unitLabel }}</span>
          </div>

          <p class="pd__desc">
            {{ product.name }} from our {{ categoryName }} range — sourced fresh and delivered by a rider near you,
            or ready to collect in store.
          </p>

          <dl class="pd__facts">
            <div v-if="product.sku"><dt>SKU</dt><dd>{{ product.sku }}</dd></div>
            <div><dt>Category</dt><dd>{{ categoryName }}</dd></div>
            <div>
              <dt>Availability</dt>
              <dd :class="{ 'pd__out': product.outOfStock }">
                {{ product.outOfStock ? 'Out of stock' : 'In stock' }}
              </dd>
            </div>
          </dl>

          <div v-if="!product.outOfStock" class="pd__buy">
            <div class="pd__stepper">
              <button type="button" aria-label="Decrease quantity" @click="decrement"><Minus :size="16" /></button>
              <span>{{ qty }}</span>
              <button type="button" aria-label="Increase quantity" @click="increment"><Plus :size="16" /></button>
            </div>

            <button type="button" class="pd__add" @click="addToCart">
              <Check v-if="justAdded" :size="16" />
              {{ justAdded ? 'Added to cart' : `Add to cart — ${formatCurrency(product.priceCents * qty)}` }}
            </button>
          </div>
          <p v-else class="pd__state pd__state--inline">This item is currently unavailable.</p>

          <div class="pd__secondary">
            <button v-if="!product.outOfStock" type="button" class="pd__buynow" @click="buyNow">Buy now</button>
            <button
              type="button"
              class="pd__love"
              :class="{ 'pd__love--active': wishlist.has(product.id) }"
              @click="wishlist.toggle(product.id)"
            >
              {{ wishlist.has(product.id) ? '♥ Saved' : '♡ Save for later' }}
            </button>
          </div>

          <p v-if="inCart > 0" class="pd__incart">
            {{ inCart }} in your cart · <RouterLink :to="{ name: 'checkout' }">View cart</RouterLink>
          </p>
        </div>
      </div>

      <section v-if="related.length > 0" class="pd__related">
        <h2>More from {{ categoryName }}</h2>
        <div class="pd__related-grid">
          <RouterLink
            v-for="item in related"
            :key="item.id"
            :to="{ name: 'product', params: { productId: item.id } }"
            class="pd__related-card"
          >
            <div class="pd__related-art">
              <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" loading="lazy" />
              <div v-else class="pd__fallback"><ShoppingBasket :size="24" /></div>
            </div>
            <span class="pd__related-name">{{ item.name }}</span>
            <strong class="pd__related-price">{{ formatCurrency(item.priceCents) }}</strong>
          </RouterLink>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.pd {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4) var(--space-7);
}

.pd__state {
  padding: var(--space-7) var(--space-4);
  text-align: center;
  color: var(--text-secondary);
}

.pd__state--inline {
  padding: var(--space-4) 0;
  text-align: left;
}

.pd__state a {
  color: var(--accent);
  font-weight: 700;
}

.pd__back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--space-4);
  padding: 8px 14px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-pill);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font: 600 13px/1 inherit;
  cursor: pointer;
}

.pd__back:hover { color: var(--text-primary); }

.pd__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-6);
  align-items: start;
}

.pd__art {
  aspect-ratio: 1 / 1;
  border-radius: var(--radius-xl);
  background: var(--fill);
  border: 1px solid var(--separator);
  overflow: hidden;
}

.pd__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.pd__fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--text-tertiary);
}

.pd__body { display: grid; gap: var(--space-3); }

.pd__category {
  color: var(--text-tertiary);
  font-size: 11.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.pd__title {
  margin: 0;
  color: var(--text-primary);
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.pd__price-row { display: flex; align-items: baseline; gap: 8px; }
.pd__price { color: var(--accent); font-size: 1.6rem; font-weight: 800; }
.pd__unit { color: var(--text-tertiary); font-size: 13px; }
.pd__was { color: var(--text-tertiary); font-size: 15px; text-decoration: line-through; }
.pd__badge {
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--accent);
  color: var(--accent-text-on);
  font-size: 12px;
  font-weight: 800;
}

.pd__desc { margin: 0; color: var(--text-secondary); line-height: 1.6; font-size: 14.5px; }

.pd__facts {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: var(--space-3) 0;
  border-top: 1px solid var(--separator);
  border-bottom: 1px solid var(--separator);
}

.pd__facts > div { display: flex; justify-content: space-between; gap: 12px; }
.pd__facts dt { color: var(--text-tertiary); font-size: 13px; }
.pd__facts dd { margin: 0; color: var(--text-primary); font-size: 13px; font-weight: 600; }
.pd__out { color: var(--danger); }

.pd__buy { display: flex; gap: 10px; align-items: stretch; }

.pd__stepper {
  display: grid;
  grid-template-columns: 38px 44px 38px;
  align-items: center;
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  flex-shrink: 0;
}

.pd__stepper button {
  display: grid;
  place-items: center;
  height: 44px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
}

.pd__stepper span { text-align: center; font-size: 15px; font-weight: 700; color: var(--text-primary); }

.pd__add {
  flex: 1;
  min-height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: var(--accent-text-on);
  font: 800 14.5px/1 inherit;
  cursor: pointer;
}

.pd__add:hover { background: var(--accent-pressed); }

.pd__secondary { display: flex; gap: 10px; flex-wrap: wrap; }

.pd__buynow,
.pd__love {
  min-height: 40px;
  padding: 0 18px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  color: var(--text-primary);
  font: 700 13.5px/1 inherit;
  cursor: pointer;
}

.pd__buynow:hover,
.pd__love:hover { border-color: var(--accent); color: var(--accent); }
.pd__love--active { color: var(--danger); border-color: var(--danger); }

.pd__incart { margin: 0; color: var(--text-secondary); font-size: 13px; }
.pd__incart a { color: var(--accent); font-weight: 700; }

.pd__related { margin-top: var(--space-7); }
.pd__related h2 {
  margin: 0 0 var(--space-4);
  color: var(--text-primary);
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.pd__related-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-3); }

.pd__related-card {
  display: grid;
  gap: 6px;
  padding: 10px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-lg);
  background: var(--bg-elevated);
  text-decoration: none;
  transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

.pd__related-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }

.pd__related-art {
  aspect-ratio: 1 / 1;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--fill);
}

.pd__related-art img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pd__related-name { color: var(--text-primary); font-size: 13px; font-weight: 600; }
.pd__related-price { color: var(--accent); font-size: 14px; font-weight: 800; }

@media (max-width: 820px) {
  .pd__layout { grid-template-columns: 1fr; gap: var(--space-4); }
  .pd__related-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
