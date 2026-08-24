<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, Bike, Search, Sparkles } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { useStorefrontCatalog } from '@pos/web/storefront/catalog'
import { useStorefrontSearch } from '@pos/web/storefront/search'
import { useStorefrontWishlist } from '@pos/web/storefront/wishlist'
import { storefrontCopy } from '@pos/web/storefront/copy'
import { iconForCategory } from '@pos/web/storefront/icons'
import { BUSINESS_MODE, STORE_ADDRESS } from '@pos/web/storefront/firebase'
import { SERVICE_CATEGORIES, serviceCategoryFor, type ServiceCategory } from '@pos/web/storefront/delivery'
import ProductCard from '../components/ProductCard.vue'

const catalog = useStorefrontCatalog()
const { query } = useStorefrontSearch()
const wishlist = useStorefrontWishlist()
const copy = storefrontCopy(BUSINESS_MODE)

const selectedCategoryId = ref<string | null>(null)
// Baguio Delivery's four service categories, layered over the store's own
// catalog categories — a store only shows the ones its catalog actually maps to.
const selectedService = ref<ServiceCategory | null>(null)

function categoryNameFor(categoryId: string): string {
  return catalog.categories.find((category) => category.id === categoryId)?.name ?? 'Mixed Picks'
}

const categories = computed(() =>
  catalog.categories.filter((category) => catalog.products.some((product) => product.categoryId === category.id)),
)

/** Only the service categories this store's catalog actually covers. */
const availableServices = computed(() => {
  const present = new Set(
    categories.value.map((category) => serviceCategoryFor(category.name)).filter((id): id is ServiceCategory => id !== null),
  )
  return SERVICE_CATEGORIES.filter((service) => present.has(service.id))
})

const filteredProducts = computed(() => {
  let items = catalog.products
  const needle = query.value.trim().toLowerCase()
  if (needle) {
    items = items.filter((product) =>
      [product.name, categoryNameFor(product.categoryId)].some((value) => value.toLowerCase().includes(needle)),
    )
  }
  if (wishlist.showLovedOnly.value) {
    items = items.filter((product) => wishlist.has(product.id))
  }
  if (selectedService.value) {
    items = items.filter((product) => serviceCategoryFor(categoryNameFor(product.categoryId)) === selectedService.value)
  }
  if (selectedCategoryId.value) {
    items = items.filter((product) => product.categoryId === selectedCategoryId.value)
  }
  return items
})

const featuredProduct = computed(() => catalog.products[0] ?? null)
const promo = computed(() => copy.promoCards[0])

// "Featured Deals" strip, carried over from the Baguio landing page. The POS
// catalog has no compare-at price, so rather than invent a fake discount this
// simply surfaces the store's lowest-priced items as today's best picks.
const deals = computed(() =>
  [...catalog.products].sort((a, b) => a.priceCents - b.priceCents).slice(0, 6),
)

function toggleCategory(categoryId: string) {
  selectedCategoryId.value = selectedCategoryId.value === categoryId ? null : categoryId
}

function toggleService(service: ServiceCategory) {
  selectedService.value = selectedService.value === service ? null : service
  selectedCategoryId.value = null
}
</script>

<template>
  <div class="catalog">
    <p v-if="catalog.loading" class="catalog__state">Loading products...</p>
    <p v-else-if="catalog.error" class="catalog__state catalog__state--error">{{ catalog.error }}</p>
    <p v-else-if="catalog.products.length === 0" class="catalog__state">No products are available right now.</p>

    <template v-else>
      <!-- Hero, ported from the Baguio Delivery storefront: watermark wordmark
           over the brand gradient, with the store name as the wordmark. -->
      <section class="hero">
        <h2 aria-hidden="true" class="hero__watermark">Delivery</h2>
        <div class="hero__inner">
          <p class="hero__script">
            <Sparkles :size="14" />
            Same-day delivery
          </p>
          <p class="hero__copy">{{ copy.heroSub }}</p>
          <p v-if="STORE_ADDRESS" class="hero__store">
            <Bike :size="14" />
            Delivering from {{ STORE_ADDRESS }}
          </p>
        </div>
      </section>

      <label class="catalog__search">
        <Search :size="20" />
        <input v-model="query" type="search" placeholder="What are you looking for?" />
      </label>

      <section v-if="availableServices.length > 0" class="services">
        <button
          v-for="service in availableServices"
          :key="service.id"
          type="button"
          class="service"
          :class="{ 'service--active': selectedService === service.id }"
          @click="toggleService(service.id)"
        >
          <span class="service__circle">
            <component :is="service.icon" :size="22" />
          </span>
          <span class="service__label">{{ service.label }}</span>
        </button>
      </section>

      <section v-if="promo" class="catalog__promo">
        <div class="catalog__promo-copy">
          <p class="catalog__promo-title">{{ promo.title }}</p>
          <p class="catalog__promo-body">{{ promo.body }}</p>
        </div>
        <img
          v-if="featuredProduct?.imageUrl"
          :src="featuredProduct.imageUrl"
          :alt="featuredProduct.name"
          class="catalog__promo-image"
        />
      </section>

      <section v-if="categories.length > 0" class="catalog__categories">
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="cat-icon"
          :class="{ 'cat-icon--active': selectedCategoryId === category.id }"
          @click="toggleCategory(category.id)"
        >
          <span class="cat-icon__circle">
            <component :is="iconForCategory(category.name)" :size="24" />
          </span>
          <span class="cat-icon__label">{{ category.name }}</span>
        </button>
      </section>

      <!-- Featured Deals, from the Baguio landing page. -->
      <section v-if="deals.length > 0 && !query.trim() && !wishlist.showLovedOnly.value" class="deals">
        <div class="deals__head">
          <h2 class="section-title">Featured Deals</h2>
          <span class="deals__hint">Today's best prices <ArrowRight :size="14" /></span>
        </div>
        <div class="deals__row">
          <article v-for="product in deals" :key="product.id" class="deal">
            <div class="deal__art">
              <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" loading="lazy" />
              <div v-else class="deal__fallback"><Sparkles :size="20" /></div>
            </div>
            <p class="deal__name">{{ product.name }}</p>
            <p class="deal__price">{{ formatCurrency(product.priceCents) }}</p>
          </article>
        </div>
      </section>

      <p v-if="wishlist.showLovedOnly.value" class="catalog__result-note">Showing your liked products only.</p>
      <p v-else-if="query.trim()" class="catalog__result-note">
        {{ filteredProducts.length }} result{{ filteredProducts.length === 1 ? '' : 's' }} for "{{ query.trim() }}"
      </p>

      <section class="catalog__products">
        <h2 class="section-title">Top Products</h2>
        <p v-if="filteredProducts.length === 0" class="catalog__state">No products match your filters.</p>
        <div v-else class="catalog__grid">
          <ProductCard
            v-for="product in filteredProducts"
            :key="product.id"
            :product="product"
            :category-name="categoryNameFor(product.categoryId)"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.catalog {
  display: grid;
  gap: 18px;
}

.catalog__state {
  padding: 48px 16px;
  text-align: center;
  color: var(--sf-text-muted);
}

.catalog__state--error {
  color: var(--sf-danger);
}

/* ── Hero ──────────────────────────────────────────────────────────── */
.hero {
  position: relative;
  overflow: hidden;
  padding: 26px 20px 24px;
  border-radius: var(--sf-radius-lg);
  background: var(--sf-hero-gradient);
  color: #fff;
}

.hero__watermark {
  position: absolute;
  inset-inline: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1;
  margin: 0;
  font-family: var(--sf-font-display);
  font-size: 17vw;
  font-weight: 800;
  line-height: 1;
  text-align: center;
  text-transform: uppercase;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.15);
  pointer-events: none;
  user-select: none;
}

.hero__inner {
  position: relative;
  z-index: 3;
  display: grid;
  gap: 10px;
}

.hero__script {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-family: var(--sf-font-script);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--sf-accent-lime);
}

.hero__copy {
  margin: 0;
  max-width: 22rem;
  font-size: 0.95rem;
  font-weight: 500;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.9);
}

.hero__store {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.75);
}

/* ── Search ────────────────────────────────────────────────────────── */
.catalog__search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  height: 52px;
  border-radius: var(--sf-radius-pill);
  background: var(--sf-surface);
  color: var(--sf-text-muted);
  box-shadow: var(--sf-shadow-sm);
}

.catalog__search input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: var(--sf-text);
  font: 500 0.96rem/1.2 inherit;
}

.catalog__search input::placeholder {
  color: var(--sf-text-muted);
}

/* ── Service categories (Food / Groceries / Pharmacy / Errands) ────── */
.services {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  gap: 10px;
}

.service {
  display: grid;
  justify-items: center;
  gap: 7px;
  padding: 12px 4px;
  border: 1px solid var(--sf-border);
  border-radius: var(--sf-radius);
  background: var(--sf-surface);
}

.service__circle {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 999px;
  background: var(--sf-banner-green);
  color: var(--sf-primary-deep);
}

.service--active {
  border-color: var(--sf-primary);
  background: var(--sf-banner-green);
}

.service--active .service__circle {
  background: var(--sf-primary);
  color: #fff;
}

.service__label {
  color: var(--sf-text);
  font-size: 0.74rem;
  font-weight: 700;
}

/* ── Promo ─────────────────────────────────────────────────────────── */
.catalog__promo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(135deg, var(--sf-primary-light) 0%, var(--sf-primary-deep) 100%);
  color: #fff;
  overflow: hidden;
}

.catalog__promo-copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.catalog__promo-title {
  margin: 0;
  font-family: var(--sf-font-display);
  font-size: 1.05rem;
  font-weight: 800;
  line-height: 1.25;
}

.catalog__promo-body {
  margin: 0;
  color: rgba(255, 255, 255, 0.86);
  font-size: 0.82rem;
  line-height: 1.4;
}

.catalog__promo-image {
  flex-shrink: 0;
  width: 88px;
  height: 88px;
  border-radius: 18px;
  object-fit: cover;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.2);
}

/* ── Store's own categories ────────────────────────────────────────── */
.catalog__categories {
  display: flex;
  gap: 18px;
  overflow-x: auto;
  padding: 2px 2px 4px;
}

.catalog__categories::-webkit-scrollbar {
  display: none;
}

.cat-icon {
  display: grid;
  justify-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  flex-shrink: 0;
}

.cat-icon__circle {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 999px;
  background: var(--sf-chip);
  color: var(--sf-primary-dark);
}

.cat-icon--active .cat-icon__circle {
  background: var(--sf-primary);
  color: #fff;
}

.cat-icon__label {
  color: var(--sf-text-gray);
  font-size: 0.76rem;
  font-weight: 600;
  white-space: nowrap;
}

.cat-icon--active .cat-icon__label {
  color: var(--sf-text);
  font-weight: 800;
}

/* ── Deals ─────────────────────────────────────────────────────────── */
.deals {
  display: grid;
  gap: 12px;
}

.deals__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.deals__hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--sf-primary-dark);
  font-size: 0.78rem;
  font-weight: 700;
}

.deals__row {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.deals__row::-webkit-scrollbar {
  display: none;
}

.deal {
  flex: 0 0 132px;
  display: grid;
  gap: 6px;
  padding: 10px;
  border-radius: var(--sf-radius);
  background: var(--sf-surface);
  box-shadow: var(--sf-shadow-sm);
}

.deal__art {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: 12px;
  overflow: hidden;
  background: var(--sf-card);
}

.deal__art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.deal__fallback {
  display: grid;
  place-items: center;
  height: 100%;
  color: var(--sf-primary-light);
}

.deal__name {
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--sf-text);
  font-size: 0.82rem;
  font-weight: 600;
}

.deal__price {
  margin: 0;
  color: var(--sf-primary-dark);
  font-size: 0.92rem;
  font-weight: 800;
}

/* ── Products ──────────────────────────────────────────────────────── */
.catalog__result-note {
  margin: -8px 2px 0;
  color: var(--sf-text-muted);
  font-size: 0.86rem;
  font-weight: 600;
}

.catalog__products {
  display: grid;
  gap: 14px;
}

.section-title {
  margin: 0;
  font-family: var(--sf-font-display);
  color: var(--sf-text);
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.catalog__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
</style>
