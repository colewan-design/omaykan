<script setup lang="ts">
import { ChefHat, LayoutGrid, PackageSearch } from '@lucide/vue'
import { computed, reactive } from 'vue'
import { categoryIcon } from '@pos/core/utils/categoryIcons'
import { categoryTagVar, formatCurrency } from '@pos/shared/index'
import { usePosStore } from '@pos/core/stores/pos'
import { haptic, ImpactStyle } from '@pos/core/utils/haptics'

const store = usePosStore()

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

const cartQuantities = computed(() => {
  const map = new Map<string, number>()
  for (const line of store.cartLines) {
    map.set(line.product.id, line.quantity)
  }
  return map
})

function quantityFor(productId: string) {
  return cartQuantities.value.get(productId) ?? 0
}

// The register's own aisle glyphs come from the shared name-keyed table, so
// the storefront and the register can't drift into drawing different icons for
// the same shelf. Anything the table doesn't know falls back to ChefHat.
//
// `categoryNameFor` is the bridge: everything here addresses a category by id,
// but ids off the API are uuids, and it is the name the table is keyed on.
function iconFor(categoryId: string) {
  if (categoryId === 'all') return LayoutGrid
  return categoryIcon(categoryNameFor(categoryId)) ?? ChefHat
}

function categoryNameFor(categoryId: string) {
  return store.categories.find((category) => category.id === categoryId)?.name ?? categoryId
}

const failedImages = reactive<Record<string, boolean>>({})

function markImageFailed(productId: string) {
  failedImages[productId] = true
}
</script>

<template>
  <section class="surface-panel reg-catalog-panel">
    <div class="reg-category-tabs">
      <button
        v-for="category in visibleCategories"
        :key="category.id"
        class="reg-category-tab"
        :class="{ active: store.selectedCategoryId === category.id }"
        type="button"
        @click="selectCategory(category.id)"
      >
        <span class="reg-category-tab__icon">
          <component :is="iconFor(category.id)" :size="20" />
        </span>
        <span class="reg-category-tab__label">{{ category.name }}</span>
        <span class="reg-category-tab__count">{{ countFor(category.id) }} Items</span>
      </button>
    </div>

    <label class="search-field reg-search-field">
      <span class="reg-search-field__icon">
        <PackageSearch :size="16" />
      </span>
      <input
        :value="store.search"
        placeholder="Search menu items"
        type="search"
        @input="store.setSearch(($event.target as HTMLInputElement).value)"
      />
    </label>

    <div class="product-grid">
      <button
        v-for="product in store.filteredProducts"
        :key="product.id"
        class="product-card"
        :disabled="product.outOfStock"
        type="button"
        @click="store.addProduct(product.id)"
      >
        <div class="product-card__art">
          <span v-if="quantityFor(product.id) > 0" class="product-card__qty">{{ quantityFor(product.id) }}</span>
          <img
            v-if="product.imageUrl && !failedImages[product.id]"
            :src="product.imageUrl"
            :alt="product.name"
            loading="lazy"
            @error="markImageFailed(product.id)"
          />
          <component :is="iconFor(product.categoryId)" v-else :size="32" />
        </div>
        <div class="product-card__meta">
          <p class="product-card__name">{{ product.name }}</p>
          <div class="product-card__meta-row">
            <span class="product-card__tag" :style="{ '--tag': categoryTagVar(product.categoryId) }">
              {{ categoryNameFor(product.categoryId) }}
            </span>
            <p class="product-card__price">{{ formatCurrency(product.priceCents) }}</p>
          </div>
        </div>
      </button>
    </div>
  </section>
</template>
