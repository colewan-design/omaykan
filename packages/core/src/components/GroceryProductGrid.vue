<script setup lang="ts">
import { ChevronRight, LayoutGrid, Plus, ScanLine, Search, Scale, ShoppingBasket } from '@lucide/vue'
import { computed, reactive, ref } from 'vue'
import { categoryIcon } from '@pos/core/utils/categoryIcons'
import { categoryTagVar, formatCurrency } from '@pos/shared/index'
import { usePosStore } from '@pos/core/stores/pos'
import { haptic, ImpactStyle } from '@pos/core/utils/haptics'

const store = usePosStore()
const categoryRail = ref<HTMLElement | null>(null)

function selectCategory(categoryId: string) {
  store.setCategory(categoryId)
  haptic(ImpactStyle.Light)
}

const modeProducts = computed(() =>
  store.products.filter((product) => product.businessModes.includes(store.settings.businessMode)),
)

const categoryCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const product of modeProducts.value) {
    counts.set(product.categoryId, (counts.get(product.categoryId) ?? 0) + 1)
  }
  return counts
})

function countFor(categoryId: string) {
  return categoryId === 'all' ? modeProducts.value.length : categoryCounts.value.get(categoryId) ?? 0
}

const visibleCategories = computed(() => [
  { id: 'all', name: 'All' },
  ...store.categories.filter((category) =>
    store.products.some(
      (product) =>
        product.categoryId === category.id &&
        product.businessModes.includes(store.settings.businessMode),
    ),
  ),
])

function scrollCategories() {
  categoryRail.value?.scrollBy({ left: 360, behavior: 'smooth' })
}

const failedImages = reactive<Record<string, boolean>>({})

function markImageFailed(productId: string) {
  failedImages[productId] = true
}

// The register's own aisle glyphs come from the shared name-keyed table, so
// the storefront and the register can't drift into drawing different icons for
// the same shelf. Anything the table doesn't know falls back to ShoppingBasket.
//
// `categoryNameFor` is the bridge: everything here addresses a category by id,
// but ids off the API are uuids, and it is the name the table is keyed on.
function iconFor(categoryId: string) {
  if (categoryId === 'all') return LayoutGrid
  return categoryIcon(categoryNameFor(categoryId)) ?? ShoppingBasket
}

function categoryNameFor(categoryId: string) {
  return store.categories.find((category) => category.id === categoryId)?.name ?? categoryId
}
</script>

<template>
  <section class="surface-panel reg-catalog-panel">
    <div class="reg-category-row">
      <div ref="categoryRail" class="reg-category-tabs">
        <button
          v-for="category in visibleCategories"
          :key="category.id"
          class="reg-category-tab"
          :class="{ active: store.selectedCategoryId === category.id }"
          type="button"
          @click="selectCategory(category.id)"
        >
          <span class="reg-category-tab__icon"><component :is="iconFor(category.id)" :size="25" /></span>
          <span class="reg-category-tab__copy">
            <span class="reg-category-tab__label">{{ category.name }}</span>
            <span class="reg-category-tab__count">{{ countFor(category.id) }} items</span>
          </span>
        </button>
      </div>
      <button class="reg-category-next" type="button" aria-label="Show more categories" @click="scrollCategories">
        <ChevronRight :size="20" />
      </button>
    </div>

    <div class="reg-search-row">
      <label class="barcode-field">
        <Search :size="21" />
        <input
          :value="store.search"
          placeholder="Search product name, SKU, or barcode..."
          type="search"
          @input="store.setSearch(($event.target as HTMLInputElement).value)"
        />
      </label>
      <button class="reg-scan-button" type="button" aria-label="Scan barcode" @click="store.setSearch('')">
        <ScanLine :size="24" />
      </button>
    </div>

    <div class="product-grid">
      <button
        v-for="product in store.filteredProducts"
        :key="product.id"
        class="product-card"
        :disabled="product.outOfStock"
        type="button"
        @click="store.addProduct(product.id)"
      >
        <div class="product-card__art grocery-art">
          <img
            v-if="product.imageUrl && !failedImages[product.id]"
            :src="product.imageUrl"
            :alt="product.name"
            loading="lazy"
            @error="markImageFailed(product.id)"
          />
          <component :is="iconFor(product.categoryId)" v-else :size="32" />
          <span v-if="product.kind === 'weighted'" class="product-card__badge">
            <Scale :size="14" />
          </span>
        </div>
        <div class="product-card__meta grocery-meta">
          <p class="product-card__name">{{ product.name }}</p>
          <span class="product-card__tag" :style="{ '--tag': categoryTagVar(product.categoryId) }">
            {{ categoryNameFor(product.categoryId) }}
          </span>
          <div class="product-card__price-row">
            <p class="product-card__price">
              {{ formatCurrency(product.priceCents) }}
              <span v-if="product.unitLabel">{{ product.unitLabel }}</span>
            </p>
            <span class="product-card__add" aria-hidden="true"><Plus :size="19" /></span>
          </div>
        </div>
      </button>
    </div>
  </section>
</template>

<style scoped>
.reg-catalog-panel {
  /*
   * The shelf paints with the till's tokens — RegisterPage declares a
   * green-tinted set on .register-page-stack for light and hands them back to
   * the app's own for dark. This file used to hold those light values
   * literally, which is what kept the catalog a white sheet on a black page.
   *
   * What is left are the three shadows: tinted green for a light card, plain
   * black for a dark one. Dark values at the foot of the file.
   */
  --grid-shadow: 0 8px 28px rgba(6, 63, 52, 0.045);
  --grid-button-shadow: 0 5px 12px rgba(7, 93, 73, 0.15);
  --card-shadow: 0 4px 12px rgba(10, 54, 46, 0.025);
  gap: 0;
  margin: 0;
  padding: 0;
  border: 1px solid var(--separator);
  border-radius: 17px;
  background: var(--bg-surface);
  box-shadow: var(--grid-shadow);
}

.reg-category-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px 10px;
}

.reg-category-tabs {
  display: flex;
  flex: 1;
  min-width: 0;
  gap: 8px;
  margin: 0;
  padding: 0;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  scroll-snap-type: inline proximity;
  scrollbar-width: none;
}

.reg-category-tabs::-webkit-scrollbar {
  display: none;
}

.reg-category-tab {
  width: 124px;
  min-width: 124px;
  min-height: 104px;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding: 9px 8px;
  border: 1px solid var(--separator);
  border-radius: 12px;
  background: var(--fill);
  text-align: center;
  scroll-snap-align: start;
}

.reg-category-tab__icon {
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
}

.reg-category-tab__copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.reg-category-tab__label {
  font-size: 13px;
  font-weight: 700;
}

.reg-category-tab__count {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 500;
  text-transform: lowercase;
}

.reg-category-tab.active {
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  background: color-mix(in srgb, var(--accent) 7%, transparent);
}

.reg-category-tab.active .reg-category-tab__icon {
  background: color-mix(in srgb, var(--accent) 16%, transparent);
  color: var(--accent);
  box-shadow: none;
}

.reg-category-tab.active .reg-category-tab__label {
  color: var(--text-primary);
}

.reg-category-next {
  display: inline-grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  padding: 0;
  border: 1px solid var(--separator);
  border-radius: 50%;
  background: var(--bg-elevated);
  color: var(--accent);
}

.reg-category-next:hover {
  background: var(--fill);
  color: var(--accent);
}

.reg-search-row {
  display: flex;
  gap: 0;
  padding: 10px 16px 14px;
}

.barcode-field {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 1;
  min-height: 56px;
  margin: 0;
  padding: 0 17px;
  border: 1px solid var(--separator);
  border-right: 0;
  border-radius: 13px 0 0 13px;
  background: var(--bg-elevated);
  color: var(--text-secondary);
  cursor: text;
}

.barcode-field input {
  flex: 1;
  border: none;
  outline: none;
  color: var(--text-primary);
  background: transparent;
  font-size: 15px;
}

.reg-scan-button {
  display: inline-flex;
  width: 64px;
  min-width: 64px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--separator);
  border-left: 1px solid var(--separator);
  border-radius: 0 13px 13px 0;
  background: linear-gradient(145deg, var(--accent), var(--accent-pressed));
  color: var(--accent-text-on);
  box-shadow: var(--grid-button-shadow);
}

.product-grid {
  grid-template-columns: repeat(auto-fill, minmax(145px, 1fr));
  grid-auto-rows: clamp(218px, 27vh, 254px);
  gap: 10px;
  padding: 0 16px 16px;
  scrollbar-width: thin;
  scrollbar-color: var(--text-tertiary) transparent;
}

.product-card {
  height: 100%;
  min-height: 0;
  border: 1px solid var(--separator);
  border-radius: 13px;
  background: var(--bg-elevated);
  box-shadow: var(--card-shadow);
}

.product-card__art.grocery-art {
  aspect-ratio: auto;
  height: 49%;
  min-height: 108px;
  background: linear-gradient(180deg, var(--bg-elevated) 0%, var(--fill) 100%);
}

.product-card__art img {
  width: calc(100% - 18px);
  height: calc(100% - 10px);
  object-fit: contain;
  mix-blend-mode: multiply;
}

.product-card:hover {
  transform: translateY(-2px);
}

.product-card__qty {
  top: 7px;
  left: 7px;
  background: var(--accent);
}

.grocery-meta {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  padding: 10px 11px 11px;
}

.product-card__name {
  display: -webkit-box;
  min-height: 35px;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.25;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-card__tag {
  max-width: 100%;
  padding: 3px 9px;
  /* Mixed into the card, not into white: against a dark card a white mix is a
     pale chip with a dark-theme tag colour on it, which is the one combination
     neither palette was tuned for. */
  background: color-mix(in srgb, var(--tag) 14%, var(--bg-elevated));
  color: var(--tag);
  font-size: 11px;
  font-weight: 700;
}

.product-card__price-row {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
}

.product-card__price {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 800;
}

.product-card__price span {
  display: block;
  font-size: 8px;
}

.product-card__add {
  display: grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
  box-shadow: none;
}

.grocery-art {
  position: relative;
  aspect-ratio: 1;
}

.product-card__badge {
  position: absolute;
  bottom: var(--space-2);
  right: var(--space-2);
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--warning) 88%, white);
  color: white;
  box-shadow: var(--shadow-sm);
}

@media (max-width: 720px) {
  .reg-category-row { padding-inline: 12px; }
  .reg-category-tab { width: 104px; min-width: 104px; min-height: 94px; }
  .reg-category-next { display: none; }
  .reg-search-row { padding-inline: 12px; }
  .reg-scan-button { min-width: 50px; }
  .product-grid { grid-auto-rows: 220px; padding-inline: 12px; }
}

/*
 * Dark: the shadows, and the one thing on this page that a palette cannot fix.
 *
 * Product photos are blended `multiply` so the white box most of them are shot
 * on drops into the card. Multiply against a dark card does the opposite — it
 * drives the whole photo towards black and the shelf goes blank. Normal blend
 * puts the white box back, which is worse-looking and far better than an empty
 * grid.
 *
 * Three selectors for the three ways dark is reached, written plainly rather
 * than through :global(); RegisterPage.vue's dark block carries the full note.
 * Keep the two blocks here in step.
 */
[data-theme='dark'] .reg-catalog-panel,
[data-color-theme='nocturne'] .reg-catalog-panel,
[data-color-theme='reserve'] .reg-catalog-panel,
[data-color-theme='harbor'] .reg-catalog-panel,
[data-color-theme='mono'] .reg-catalog-panel {
  --grid-shadow: 0 8px 28px rgba(0, 0, 0, 0.45);
  --grid-button-shadow: 0 5px 12px rgba(0, 0, 0, 0.5);
  --card-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
}

[data-theme='dark'] .product-card__art img,
[data-color-theme='nocturne'] .product-card__art img,
[data-color-theme='reserve'] .product-card__art img,
[data-color-theme='harbor'] .product-card__art img,
[data-color-theme='mono'] .product-card__art img {
  mix-blend-mode: normal;
}

@media (prefers-color-scheme: dark) {
  html:not([data-theme='light']) .reg-catalog-panel {
    --grid-shadow: 0 8px 28px rgba(0, 0, 0, 0.45);
    --grid-button-shadow: 0 5px 12px rgba(0, 0, 0, 0.5);
    --card-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
  }

  html:not([data-theme='light']) .product-card__art img {
    mix-blend-mode: normal;
  }
}
</style>
