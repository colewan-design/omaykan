<script setup lang="ts">
import {
  AlertTriangle, ArrowUpDown, Boxes, Check, ChevronDown, ChevronLeft, ChevronRight,
  Circle, Layers3, MoreHorizontal, Package, Pencil, Plus, Search, Store, Tag,
  Trash2, TrendingDown, X,
} from '@lucide/vue'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import ProductSheet from '@pos/core/components/ProductSheet.vue'
import PromotionsPanel from '@pos/core/components/PromotionsPanel.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { haptic, ImpactStyle } from '@pos/core/utils/haptics'
import { formatCurrency, type BusinessMode, type Category, type Product } from '@pos/shared/index'

const store = usePosStore()
const activeTab = ref<'products' | 'categories' | 'promotions'>('products')
const modeFilter = ref<BusinessMode | 'all'>('all')
const categoryFilter = ref('all')
const statusFilter = ref<'all' | 'active' | 'inactive'>('all')
const stockFilter = ref<'all' | 'in-stock' | 'low-stock' | 'out-of-stock'>('all')
const sortBy = ref<'name-asc' | 'name-desc' | 'price-asc' | 'price-desc' | 'low-stock' | 'recently-added'>('name-asc')
const searchQuery = ref('')
const searchInputEl = ref<HTMLInputElement | null>(null)
const currentPage = ref(1)
const rowsPerPage = ref(8)
const selectedIds = ref(new Set<string>())
const collapsedCategoryIds = ref(new Set<string>())
const moreMenuId = ref<string | null>(null)

const rowsPerPageOptions = [8, 16, 24]
const storefrontOptions: { value: BusinessMode | 'all'; label: string }[] = [
  { value: 'all', label: 'All storefronts' },
  { value: 'coffee-shop', label: 'Coffee shop' },
  { value: 'grocery', label: 'Grocery' },
  { value: 'restaurant', label: 'Restaurant' },
  { value: 'nail-salon', label: 'Nail salon' },
]

function setActiveTab(tab: 'products' | 'categories' | 'promotions') {
  if (activeTab.value === tab) return
  activeTab.value = tab
  haptic(ImpactStyle.Light)
}

function isOut(product: Product): boolean {
  return product.stockQty !== undefined ? product.stockQty === 0 : product.outOfStock === true
}

function isLow(product: Product): boolean {
  return product.stockQty !== undefined && product.stockQty > 0 && product.stockQty <= (product.lowStockThreshold ?? 5)
}

function isHealthy(product: Product): boolean {
  return !isOut(product) && !isLow(product)
}

const totalCount = computed(() => store.products.length)
const outCount = computed(() => store.products.filter(isOut).length)
const lowCount = computed(() => store.products.filter(isLow).length)
const availableCount = computed(() => store.products.filter(isHealthy).length)

function filterBySummary(filter: typeof stockFilter.value) {
  activeTab.value = 'products'
  stockFilter.value = filter
  currentPage.value = 1
  nextTick(() => document.querySelector('.product-workspace')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

const processedProducts = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()
  let products = store.products.filter((product) => {
    if (modeFilter.value !== 'all' && !product.businessModes.includes(modeFilter.value)) return false
    if (categoryFilter.value !== 'all' && product.categoryId !== categoryFilter.value) return false
    if (statusFilter.value === 'active' && product.outOfStock) return false
    if (statusFilter.value === 'inactive' && !product.outOfStock) return false
    if (stockFilter.value === 'in-stock' && !isHealthy(product)) return false
    if (stockFilter.value === 'low-stock' && !isLow(product)) return false
    if (stockFilter.value === 'out-of-stock' && !isOut(product)) return false
    if (needle && !product.name.toLowerCase().includes(needle) && !product.sku.toLowerCase().includes(needle) && !product.barcode.includes(needle) && !(product.brand ?? '').toLowerCase().includes(needle)) return false
    return true
  })

  switch (sortBy.value) {
    case 'name-desc': products = products.slice().sort((a, b) => b.name.localeCompare(a.name)); break
    case 'price-asc': products = products.slice().sort((a, b) => a.priceCents - b.priceCents); break
    case 'price-desc': products = products.slice().sort((a, b) => b.priceCents - a.priceCents); break
    case 'low-stock': products = products.slice().sort((a, b) => (a.stockQty ?? Infinity) - (b.stockQty ?? Infinity)); break
    case 'recently-added': products = products.slice().reverse(); break
    default: products = products.slice().sort((a, b) => a.name.localeCompare(b.name))
  }
  return products
})

const pageCount = computed(() => Math.max(1, Math.ceil(processedProducts.value.length / rowsPerPage.value)))
const pageStart = computed(() => (currentPage.value - 1) * rowsPerPage.value)
const pageProducts = computed(() => processedProducts.value.slice(pageStart.value, pageStart.value + rowsPerPage.value))
const pageGroups = computed(() => {
  const byId = new Map<string, Product[]>()
  for (const product of pageProducts.value) {
    const group = byId.get(product.categoryId) ?? []
    group.push(product)
    byId.set(product.categoryId, group)
  }
  const groups: { category: Category; products: Product[] }[] = []
  for (const category of store.categories) {
    const products = byId.get(category.id)
    if (products?.length) groups.push({ category, products })
  }
  for (const [id, products] of byId) {
    if (!store.categories.some((category) => category.id === id)) groups.push({ category: { id, name: id }, products })
  }
  return groups
})

const visibleRangeStart = computed(() => processedProducts.value.length === 0 ? 0 : pageStart.value + 1)
const visibleRangeEnd = computed(() => Math.min(pageStart.value + rowsPerPage.value, processedProducts.value.length))
const allPageSelected = computed(() => pageProducts.value.length > 0 && pageProducts.value.every((product) => selectedIds.value.has(product.id)))

watch([searchQuery, modeFilter, categoryFilter, statusFilter, stockFilter, sortBy, rowsPerPage], () => { currentPage.value = 1 })
watch(pageCount, (count) => { if (currentPage.value > count) currentPage.value = count })

function toggleSelect(id: string) {
  const next = new Set(selectedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedIds.value = next
}

function togglePageSelection() {
  const next = new Set(selectedIds.value)
  if (allPageSelected.value) pageProducts.value.forEach((product) => next.delete(product.id))
  else pageProducts.value.forEach((product) => next.add(product.id))
  selectedIds.value = next
}

function clearSelection() { selectedIds.value = new Set() }

function toggleCategory(id: string) {
  const next = new Set(collapsedCategoryIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  collapsedCategoryIds.value = next
}

async function bulkSetAvailability(available: boolean) {
  await Promise.all([...selectedIds.value].map((id) => {
    const product = store.products.find((item) => item.id === id)
    return product ? store.editProduct({ ...product, outOfStock: !available }) : Promise.resolve()
  }))
  clearSelection()
}

async function bulkDelete() {
  await Promise.all([...selectedIds.value].map((id) => store.removeProduct(id)))
  clearSelection()
}

const sheetProduct = ref<Product | undefined>()
const sheetOpen = ref(false)

function openAdd() { sheetProduct.value = undefined; sheetOpen.value = true }
function openEdit(product: Product) { moreMenuId.value = null; sheetProduct.value = product; sheetOpen.value = true }
function closeSheet() { sheetOpen.value = false }
function onSaved() { sheetOpen.value = false }

async function toggleAvailability(product: Product) {
  moreMenuId.value = null
  await store.editProduct({ ...product, outOfStock: !product.outOfStock })
}

async function submitRestock(id: string, event: Event) {
  event.stopPropagation()
  moreMenuId.value = null
  await store.restockProduct(id, 1)
  haptic(ImpactStyle.Medium)
}

function stockTone(product: Product): 'success' | 'warning' | 'danger' {
  if (isOut(product)) return 'danger'
  if (isLow(product)) return 'warning'
  return 'success'
}

function stockLabel(product: Product): string {
  if (product.stockQty !== undefined) {
    if (product.stockQty === 0) return 'Out of stock'
    if (isLow(product)) return `${product.stockQty} left`
    return `${product.stockQty} in stock`
  }
  return product.outOfStock ? 'Out of stock' : 'In stock'
}

function productDescriptor(product: Product): string {
  const details = [product.brand, product.packagingType].filter(Boolean)
  if (details.length) return details.join(' · ')
  if (product.kind === 'weighted') return product.unitLabel ? `Sold ${product.unitLabel}` : 'Sold by weight'
  return product.barcode ? `Barcode ${product.barcode}` : 'Standard product'
}

const failedImages = ref<Record<string, boolean>>({})
const THUMB_PALETTE = ['#16794b', '#2a78d6', '#c57b12', '#7557b7', '#cc4d5b', '#168f9f', '#d76d32', '#418346']
function thumbColor(categoryId: string): string { const index = store.categories.findIndex((category) => category.id === categoryId); return THUMB_PALETTE[(index < 0 ? 0 : index) % THUMB_PALETTE.length] }
function thumbInitial(name: string): string { return name.charAt(0).toUpperCase() }

const newCategoryName = ref('')
const addingCategory = ref(false)
const editingCategoryId = ref<string | null>(null)
const editingCategoryName = ref('')
const confirmDeleteCategoryId = ref<string | null>(null)
const savingCategory = ref(false)
const deletingCategory = ref(false)
const categoryActionError = ref('')
function productCountForCategory(id: string) { return store.products.filter((product) => product.categoryId === id).length }

async function submitNewCategory() {
  const name = newCategoryName.value.trim()
  if (!name) return
  savingCategory.value = true; categoryActionError.value = ''
  try { await store.createCategory(name); newCategoryName.value = ''; addingCategory.value = false }
  catch { categoryActionError.value = 'Could not add category. Please try again.' }
  finally { savingCategory.value = false }
}

function startEditCategory(category: Category) { editingCategoryId.value = category.id; editingCategoryName.value = category.name }
async function submitEditCategory(category: Category) {
  const name = editingCategoryName.value.trim()
  if (!name) return
  savingCategory.value = true; categoryActionError.value = ''
  try { await store.editCategory({ ...category, name }); editingCategoryId.value = null }
  catch { categoryActionError.value = 'Could not save changes. Please try again.' }
  finally { savingCategory.value = false }
}

async function confirmDeleteCategory(id: string) {
  deletingCategory.value = true; categoryActionError.value = ''
  try { await store.removeCategory(id); confirmDeleteCategoryId.value = null }
  catch { categoryActionError.value = 'Could not delete category. Please try again.' }
  finally { deletingCategory.value = false }
}

function handleDocumentClick(event: MouseEvent) {
  const target = event.target
  if (moreMenuId.value && target instanceof Element && !target.closest('.ptable__more-wrap')) moreMenuId.value = null
}
onMounted(() => document.addEventListener('click', handleDocumentClick))
onUnmounted(() => document.removeEventListener('click', handleDocumentClick))
</script>

<template>
  <div class="products-page">
    <header class="p-header">
      <div><h1 class="p-title">Products</h1><p class="p-copy">Manage product details, pricing, and stock across your storefronts.</p></div>
      <button v-if="activeTab === 'products'" class="primary-button p-add-button" type="button" @click="openAdd"><Plus :size="17" /><span>Add product</span></button>
    </header>

    <section class="p-kpis" aria-label="Product summary">
      <button class="p-kpi" :class="{ 'p-kpi--active': stockFilter === 'all' }" type="button" @click="filterBySummary('all')"><span class="p-kpi__icon p-kpi__icon--green"><Package :size="20" /></span><span class="p-kpi__body"><span class="p-kpi__label">Total Products</span><strong class="p-kpi__value">{{ totalCount }}</strong></span><ChevronRight :size="17" class="p-kpi__chevron" /></button>
      <button class="p-kpi" :class="{ 'p-kpi--active': stockFilter === 'in-stock' }" type="button" @click="filterBySummary('in-stock')"><span class="p-kpi__icon p-kpi__icon--green"><Check :size="20" /></span><span class="p-kpi__body"><span class="p-kpi__label">Available</span><strong class="p-kpi__value p-kpi__value--success">{{ availableCount }}</strong></span><ChevronRight :size="17" class="p-kpi__chevron" /></button>
      <button class="p-kpi" :class="{ 'p-kpi--active': stockFilter === 'low-stock' }" type="button" @click="filterBySummary('low-stock')"><span class="p-kpi__icon p-kpi__icon--orange"><TrendingDown :size="20" /></span><span class="p-kpi__body"><span class="p-kpi__label">Low Stock</span><strong class="p-kpi__value p-kpi__value--warning">{{ lowCount }}</strong></span><ChevronRight :size="17" class="p-kpi__chevron" /></button>
      <button class="p-kpi" :class="{ 'p-kpi--active': stockFilter === 'out-of-stock' }" type="button" @click="filterBySummary('out-of-stock')"><span class="p-kpi__icon p-kpi__icon--red"><AlertTriangle :size="20" /></span><span class="p-kpi__body"><span class="p-kpi__label">Out of Stock</span><strong class="p-kpi__value p-kpi__value--danger">{{ outCount }}</strong></span><ChevronRight :size="17" class="p-kpi__chevron" /></button>
    </section>

    <section class="product-workspace">
      <div class="p-tabs" role="tablist" aria-label="Product management view">
        <button v-for="tab in [{ value: 'products', label: 'Products' }, { value: 'categories', label: 'Categories' }, { value: 'promotions', label: 'Promotions' }]" :key="tab.value" class="p-tab" :class="{ 'p-tab--active': activeTab === tab.value }" type="button" role="tab" :aria-selected="activeTab === tab.value" @click="setActiveTab(tab.value as 'products' | 'categories' | 'promotions')">{{ tab.label }}</button>
      </div>

      <template v-if="activeTab === 'products'">
        <div class="ptoolbar">
          <label class="pfilter pfilter--search"><Search :size="17" aria-hidden="true" /><input ref="searchInputEl" v-model="searchQuery" type="search" placeholder="Search products, SKU, barcode…" aria-label="Search products" /><button v-if="searchQuery" type="button" aria-label="Clear search" @click.prevent="searchQuery = ''; searchInputEl?.focus()"><X :size="14" /></button></label>

          <label class="pfilter"><Store :size="17" aria-hidden="true" /><span><small>Storefront</small><select v-model="modeFilter" aria-label="Storefront"><option v-for="option in storefrontOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></span><ChevronDown :size="15" aria-hidden="true" /></label>
          <label class="pfilter"><Tag :size="17" aria-hidden="true" /><span><small>Category</small><select v-model="categoryFilter" aria-label="Category"><option value="all">All categories</option><option v-for="category in store.categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></span><ChevronDown :size="15" aria-hidden="true" /></label>
          <label class="pfilter"><Circle :size="17" aria-hidden="true" /><span><small>Status</small><select v-model="statusFilter" aria-label="Status"><option value="all">All status</option><option value="active">Active</option><option value="inactive">Inactive</option></select></span><ChevronDown :size="15" aria-hidden="true" /></label>
          <label class="pfilter"><Layers3 :size="17" aria-hidden="true" /><span><small>Stock</small><select v-model="stockFilter" aria-label="Stock"><option value="all">All stock</option><option value="in-stock">In stock</option><option value="low-stock">Low stock</option><option value="out-of-stock">Out of stock</option></select></span><ChevronDown :size="15" aria-hidden="true" /></label>
          <label class="pfilter pfilter--sort"><ArrowUpDown :size="17" aria-hidden="true" /><span><small>Sort by</small><select v-model="sortBy" aria-label="Sort by"><option value="name-asc">Name (A–Z)</option><option value="name-desc">Name (Z–A)</option><option value="price-asc">Price (low–high)</option><option value="price-desc">Price (high–low)</option><option value="low-stock">Lowest stock</option><option value="recently-added">Recently added</option></select></span><ChevronDown :size="15" aria-hidden="true" /></label>
          <button class="pfilter pfilter--selection" type="button" :aria-label="allPageSelected ? 'Deselect page' : 'Select page'" @click="togglePageSelection"><span class="pcheck" :class="{ 'pcheck--checked': allPageSelected || selectedIds.size > 0 }"><Check v-if="allPageSelected || selectedIds.size > 0" :size="12" /></span><span><small>Selection</small><strong>{{ selectedIds.size }} selected</strong></span><ChevronDown :size="15" aria-hidden="true" /></button>
        </div>

        <Transition name="bulk-bar"><div v-if="selectedIds.size > 0" class="bulk-bar" role="toolbar" aria-label="Bulk actions"><div class="bulk-bar__left"><span class="bulk-bar__count">{{ selectedIds.size }} selected</span><button class="bulk-bar__link" type="button" @click="selectedIds = new Set(processedProducts.map((product) => product.id))">Select all {{ processedProducts.length }}</button><button class="bulk-bar__link" type="button" @click="clearSelection">Clear</button></div><div class="bulk-bar__actions"><button class="table-action" type="button" @click="bulkSetAvailability(true)">Mark active</button><button class="table-action" type="button" @click="bulkSetAvailability(false)">Mark inactive</button><button class="danger-button bulk-bar__delete" type="button" @click="bulkDelete"><Trash2 :size="14" /> Delete</button></div></div></Transition>

        <div v-if="processedProducts.length === 0" class="empty-state p-empty"><Boxes :size="30" /><strong>No matching products</strong><span>Try changing your search or filter selection.</span></div>

        <div v-else class="ptable" role="table" aria-label="Products">
          <div class="ptable__head ptable__grid" role="row"><button class="pcheck" :class="{ 'pcheck--checked': allPageSelected }" type="button" role="checkbox" :aria-checked="allPageSelected" aria-label="Select products on this page" @click="togglePageSelection"><Check v-if="allPageSelected" :size="12" /></button><span role="columnheader">Product</span><span role="columnheader">SKU</span><span role="columnheader">Category</span><span role="columnheader">Stock</span><span role="columnheader">Price</span><span role="columnheader">Status</span><span role="columnheader">Actions</span></div>

          <section v-for="group in pageGroups" :key="group.category.id" class="ptable__group" :aria-label="group.category.name">
            <button class="ptable__group-header" type="button" :aria-expanded="!collapsedCategoryIds.has(group.category.id)" @click="toggleCategory(group.category.id)"><ChevronDown :size="16" :class="{ 'is-collapsed': collapsedCategoryIds.has(group.category.id) }" /><strong>{{ group.category.name }}</strong><span>· {{ group.products.length }} {{ group.products.length === 1 ? 'product' : 'products' }}</span></button>
            <div v-if="!collapsedCategoryIds.has(group.category.id)">
              <div v-for="product in group.products" :key="product.id" class="ptable__row ptable__grid" :class="{ 'ptable__row--selected': selectedIds.has(product.id) }" role="row">
                <button class="pcheck" :class="{ 'pcheck--checked': selectedIds.has(product.id) }" type="button" role="checkbox" :aria-checked="selectedIds.has(product.id)" :aria-label="`${selectedIds.has(product.id) ? 'Deselect' : 'Select'} ${product.name}`" @click="toggleSelect(product.id)"><Check v-if="selectedIds.has(product.id)" :size="12" /></button>
                <button class="ptable__product" type="button" role="cell" @click="openEdit(product)"><span class="ptable__thumb" :style="{ '--thumb-c': thumbColor(product.categoryId) }"><img v-if="product.imageUrl && !failedImages[product.id]" :src="product.imageUrl" :alt="product.name" loading="lazy" @error="failedImages[product.id] = true" /><span v-else>{{ thumbInitial(product.name) }}</span></span><span class="ptable__product-copy"><strong>{{ product.name }}</strong><small>{{ productDescriptor(product) }}</small></span></button>
                <span class="ptable__sku" role="cell">{{ product.sku || '—' }}</span><span class="ptable__category" role="cell">{{ group.category.name }}</span><span class="ptable__stock" :class="`ptable__stock--${stockTone(product)}`" role="cell"><i aria-hidden="true" />{{ stockLabel(product) }}</span><strong class="ptable__price" role="cell">{{ formatCurrency(product.priceCents) }}<small v-if="product.unitLabel">{{ product.unitLabel }}</small></strong><span role="cell"><span class="status-pill" :class="product.outOfStock ? 'status-pill--inactive' : 'status-pill--active'">{{ product.outOfStock ? 'Inactive' : 'Active' }}</span></span>
                <span class="ptable__actions" role="cell"><button class="table-action" type="button" @click="openEdit(product)"><Pencil :size="14" /> Edit</button><button v-if="product.stockQty !== undefined" class="table-action" type="button" @click="submitRestock(product.id, $event)"><Layers3 :size="14" /> Restock</button><button v-else class="table-action" type="button" @click="toggleAvailability(product)">{{ product.outOfStock ? 'Enable' : 'Disable' }}</button><span class="ptable__more-wrap"><button class="table-action table-action--icon" type="button" :aria-label="`More actions for ${product.name}`" :aria-expanded="moreMenuId === product.id" @click.stop="moreMenuId = moreMenuId === product.id ? null : product.id"><MoreHorizontal :size="17" /></button><span v-if="moreMenuId === product.id" class="ptable__menu"><button type="button" @click="openEdit(product)">Open details</button><button type="button" @click="toggleAvailability(product)">{{ product.outOfStock ? 'Mark active' : 'Mark inactive' }}</button></span></span></span>
              </div>
            </div>
          </section>

          <footer class="ptable__footer"><span>Showing {{ visibleRangeStart }}–{{ visibleRangeEnd }} of {{ processedProducts.length }} products</span><div class="ptable__pagination"><label>Rows per page <select v-model.number="rowsPerPage"><option v-for="option in rowsPerPageOptions" :key="option" :value="option">{{ option }}</option></select></label><button type="button" :disabled="currentPage === 1" aria-label="Previous page" @click="currentPage--"><ChevronLeft :size="16" /> Previous</button><span>Page {{ currentPage }} of {{ pageCount }}</span><button class="ptable__next" type="button" :disabled="currentPage === pageCount" aria-label="Next page" @click="currentPage++">Next <ChevronRight :size="16" /></button></div></footer>
        </div>
      </template>

      <!-- Promo codes set prices, so they sit with the products and share the
           Products page's permission. -->
      <PromotionsPanel v-else-if="activeTab === 'promotions'" />

      <template v-else>
        <div class="category-manager">
          <div class="category-manager__header"><div><h2>Categories</h2><p>Organize products for faster browsing and reporting.</p></div><button v-if="!addingCategory" class="table-action" type="button" @click="addingCategory = true"><Plus :size="15" /> Add category</button></div>
          <div v-for="category in store.categories" :key="category.id" class="category-row">
            <template v-if="editingCategoryId === category.id"><input v-model="editingCategoryName" class="sheet-input category-row__edit-input" type="text" :disabled="savingCategory" @keydown.enter="submitEditCategory(category)" @keydown.escape="editingCategoryId = null" /><button class="table-action" type="button" :disabled="savingCategory" @click="submitEditCategory(category)">{{ savingCategory ? 'Saving…' : 'Save' }}</button><button class="table-action table-action--icon" type="button" @click="editingCategoryId = null"><X :size="15" /></button></template>
            <template v-else-if="confirmDeleteCategoryId === category.id"><p class="category-row__confirm-text">Delete <strong>{{ category.name }}</strong>? {{ productCountForCategory(category.id) > 0 ? `${productCountForCategory(category.id)} products will be reassigned.` : '' }}</p><button class="danger-button" type="button" :disabled="deletingCategory" @click="confirmDeleteCategory(category.id)">{{ deletingCategory ? 'Deleting…' : 'Delete' }}</button><button class="table-action" type="button" @click="confirmDeleteCategoryId = null">Cancel</button></template>
            <template v-else><span class="category-row__icon"><Tag :size="17" /></span><div class="category-row__body"><p class="category-row__name">{{ category.name }}</p><p class="category-row__count">{{ productCountForCategory(category.id) }} products</p></div><button class="table-action table-action--icon" type="button" :aria-label="`Edit ${category.name}`" @click="startEditCategory(category)"><Pencil :size="15" /></button><button class="table-action table-action--icon" type="button" :aria-label="`Delete ${category.name}`" @click="confirmDeleteCategoryId = category.id"><Trash2 :size="15" /></button></template>
          </div>
          <div v-if="addingCategory" class="category-row"><input v-model="newCategoryName" class="sheet-input category-row__edit-input" type="text" placeholder="Category name" :disabled="savingCategory" autofocus @keydown.enter="submitNewCategory" @keydown.escape="addingCategory = false" /><button class="table-action" type="button" :disabled="savingCategory" @click="submitNewCategory">{{ savingCategory ? 'Saving…' : 'Add' }}</button><button class="table-action table-action--icon" type="button" @click="addingCategory = false; newCategoryName = ''"><X :size="15" /></button></div>
          <p v-if="categoryActionError" class="category-row__error">{{ categoryActionError }}</p>
        </div>
      </template>
    </section>
  </div>
  <ProductSheet v-if="sheetOpen" :product="sheetProduct" @close="closeSheet" @saved="onSaved" />
</template>
