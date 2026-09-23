<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, ImageOff, Search } from '@lucide/vue'
import { api, type CategoryTab, type Pagination, type ProductRow } from '../api'
import { count, pesos } from '../format'
import PageHero from '../PageHero.vue'

// The whole platform's catalog, with the category tabs across the top.
//
// Read-only — a product belongs to the shop that listed it, and an operator
// editing one from here would change a merchant's shelf without them knowing.
// See PlatformProductController.

const props = defineProps<{ search: string }>()

const rows = ref<ProductRow[]>([])
const categories = ref<CategoryTab[]>([])
const pagination = ref<Pagination>({ page: 1, perPage: 30, total: 0, lastPage: 1 })
const loading = ref(true)
const error = ref('')

const categoryId = ref('')
const term = ref('')
const page = ref(1)

watch(
  () => props.search,
  (next) => {
    term.value = next
    page.value = 1
    load()
  },
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await api.products({ q: term.value, categoryId: categoryId.value, page: page.value })
    rows.value = data.products
    categories.value = data.categories
    pagination.value = data.pagination
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load the catalog.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function setCategory(next: string) {
  categoryId.value = next
  page.value = 1
  load()
}

function goPage(next: number) {
  page.value = Math.min(Math.max(1, next), pagination.value.lastPage)
  load()
}

const totalProducts = computed(() => categories.value.reduce((sum, category) => sum + category.products, 0))

/**
 * Low stock is the shop's own threshold where it set one, and otherwise a flat
 * five — enough to flag "nearly out" without inventing a policy for a shop
 * that has not expressed one.
 */
function stockState(product: ProductRow): 'none' | 'low' | 'ok' {
  if (!product.trackInventory || product.stockOnHand === null) return 'none'
  return product.stockOnHand <= (product.lowStockThreshold ?? 5) ? 'low' : 'ok'
}
</script>

<template>
  <PageHero title="Products" subtitle="Everything listed for sale across every shop on the marketplace." />

  <div class="page">
    <div class="adm-card prod__panel">
      <nav class="adm-tabs" aria-label="Filter products by category">
        <button type="button" class="adm-tab" :class="{ 'adm-tab--on': categoryId === '' }" @click="setCategory('')">
          All products <span class="adm-tab__count">{{ count(totalProducts) }}</span>
        </button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="adm-tab"
          :class="{ 'adm-tab--on': categoryId === category.id }"
          @click="setCategory(category.id)"
        >
          {{ category.name }} <span class="adm-tab__count">{{ count(category.products) }}</span>
        </button>
      </nav>

      <div class="prod__toolbar">
        <form class="adm-field prod__search" role="search" @submit.prevent="page = 1; load()">
          <Search :size="15" aria-hidden="true" />
          <input v-model="term" type="search" placeholder="Search name, SKU or barcode" aria-label="Search products" />
        </form>
        <p class="prod__showing">{{ count(pagination.total) }} shown</p>
      </div>

      <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>
      <p v-else-if="loading" class="adm-note">Loading the catalog…</p>

      <template v-else-if="rows.length > 0">
        <div class="adm-tablewrap">
          <table class="adm-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Category</th>
                <th>Seller</th>
                <th class="adm-right">Price</th>
                <th class="adm-right">Stock</th>
                <th>Listing</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="product in rows" :key="product.id">
                <td>
                  <div class="prod__item">
                    <span class="prod__thumb">
                      <img v-if="product.imageUrl" :src="product.imageUrl" alt="" loading="lazy" />
                      <ImageOff v-else :size="15" aria-hidden="true" />
                    </span>
                    <span class="prod__name">
                      <strong>{{ product.name }}</strong>
                      <small v-if="product.unitLabel || product.sku">
                        {{ [product.unitLabel, product.sku].filter(Boolean).join(' · ') }}
                      </small>
                    </span>
                  </div>
                </td>
                <td class="prod__muted">{{ product.categoryName ?? 'Uncategorised' }}</td>
                <td class="prod__muted">{{ product.sellerName ?? '—' }}</td>
                <td class="adm-right adm-num">{{ pesos(product.priceCents, { exact: true }) }}</td>
                <td class="adm-right adm-num">
                  <!-- "Untracked" and "sold out" are different facts; the dash
                       says the shop does not count this one. -->
                  <span v-if="stockState(product) === 'none'" class="prod__muted">—</span>
                  <span v-else :class="{ 'prod__low': stockState(product) === 'low' }">
                    {{ count(product.stockOnHand ?? 0) }}
                  </span>
                </td>
                <td>
                  <span class="adm-pill" :class="product.isActive ? 'adm-pill--completed' : 'adm-pill--neutral'">
                    {{ product.isActive ? 'Active' : 'Hidden' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="pagination.lastPage > 1" class="prod__pager">
          <button type="button" class="adm-btn adm-btn--quiet" :disabled="pagination.page <= 1" @click="goPage(pagination.page - 1)">
            <ChevronLeft :size="15" aria-hidden="true" />
            Previous
          </button>
          <span class="prod__pageno">Page {{ pagination.page }} of {{ pagination.lastPage }}</span>
          <button
            type="button"
            class="adm-btn adm-btn--quiet"
            :disabled="pagination.page >= pagination.lastPage"
            @click="goPage(pagination.page + 1)"
          >
            Next
            <ChevronRight :size="15" aria-hidden="true" />
          </button>
        </div>
      </template>

      <div v-else class="adm-empty">
        <p class="adm-empty__title">Nothing listed here</p>
        <p class="adm-empty__copy">
          <template v-if="term">No product matches “{{ term }}”.</template>
          <template v-else>Products appear here as shops add them to their own catalogs.</template>
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 16px var(--adm-gutter) 0;
}

.prod__panel {
  padding: 0 0 6px;
}

.adm-tabs {
  padding: 4px 14px 0;
}

.prod__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px;
}

.prod__search {
  flex: 1;
  min-width: 220px;
  max-width: 380px;
}

.prod__showing {
  margin: 0;
  color: var(--sf-muted);
  font-size: 13px;
}

.prod__item {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.prod__thumb {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 9px;
  background: var(--sf-sand);
  color: var(--sf-faint);
  overflow: hidden;
  flex: none;
}

.prod__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.prod__name {
  display: grid;
  min-width: 0;
  line-height: 1.3;
}

.prod__name strong {
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prod__name small {
  color: var(--sf-faint);
  font-size: 11.5px;
}

.prod__muted {
  color: var(--sf-muted);
}

.prod__low {
  color: #9c2626;
  font-weight: 700;
}

.prod__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 14px;
}

.prod__pageno {
  color: var(--sf-muted);
  font-size: 13px;
}

@media (max-width: 560px) {
  .page {
    padding: 14px 16px 0;
  }
}
</style>
