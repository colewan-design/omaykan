<script setup lang="ts">
import { ImagePlus, Trash2, X } from '@lucide/vue'
import { computed, reactive, ref, watch } from 'vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import ToggleSwitch from '@pos/core/components/ToggleSwitch.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { businessModeLabel, type BusinessMode, type Product } from '@pos/shared/index'

const props = defineProps<{
  product?: Product
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const store = usePosStore()
const confirmDelete = ref(false)
const saving = ref(false)

const form = reactive({
  name: '',
  categoryId: '',
  priceText: '',
  compareAtText: '',
  kind: 'standard' as 'standard' | 'weighted',
  unitLabel: '/ kg',
  businessModes: [] as BusinessMode[],
  imageUrl: '',
  barcode: '',
  outOfStock: false,
  taxRate: 0.12,
  trackInventory: false,
  stockQty: 0,
  lowStockThreshold: 5,
})

const imagePreview = ref('')
const imageSizeWarning = ref('')

watch(
  () => props.product,
  (p) => {
    if (p) {
      form.name = p.name
      form.categoryId = p.categoryId
      form.priceText = (p.priceCents / 100).toFixed(2)
      form.compareAtText = p.compareAtPriceCents ? (p.compareAtPriceCents / 100).toFixed(2) : ''
      form.kind = p.kind
      form.unitLabel = p.unitLabel ?? '/ kg'
      form.businessModes = [...p.businessModes]
      form.imageUrl = p.imageUrl ?? ''
      form.barcode = p.barcode
      form.outOfStock = p.outOfStock ?? false
      form.taxRate = p.taxRate
      form.trackInventory = p.stockQty !== undefined
      form.stockQty = p.stockQty ?? 0
      form.lowStockThreshold = p.lowStockThreshold ?? 5
      imagePreview.value = p.imageUrl ?? ''
    } else {
      form.name = ''
      form.categoryId = store.categories[0]?.id ?? ''
      form.priceText = ''
      form.compareAtText = ''
      form.kind = 'standard'
      form.unitLabel = '/ kg'
      form.businessModes = [store.settings.businessMode]
      form.imageUrl = ''
      form.barcode = ''
      form.outOfStock = false
      form.taxRate = 0.12
      form.trackInventory = false
      form.stockQty = 0
      form.lowStockThreshold = 5
      imagePreview.value = ''
    }
    confirmDelete.value = false
    imageSizeWarning.value = ''
  },
  { immediate: true },
)

/** Live "-N%" preview under the Compare at field; null when it isn't a discount. */
const compareAtPreview = computed(() => {
  const price = Math.round(parseFloat(form.priceText) * 100)
  const was = Math.round(parseFloat(form.compareAtText) * 100)
  if (!Number.isFinite(price) || !Number.isFinite(was) || was <= price) return null
  return `${Math.round(((was - price) / was) * 100)}%`
})

const isEdit = computed(() => Boolean(props.product))
const title = computed(() => (isEdit.value ? 'Edit Product' : 'Add Product'))
const categoryOptions = computed(() => store.categories.map((cat) => ({ value: cat.id, label: cat.name })))

const isValid = computed(
  () =>
    form.name.trim().length > 0 &&
    form.categoryId.length > 0 &&
    // v-model on <input type="number"> auto-casts to a JS number once the
    // value parses (Vue's built-in behavior, independent of any .number
    // modifier) — so priceText can be a number at runtime despite its string
    // initial value. String(...) here handles both.
    String(form.priceText).trim().length > 0 &&
    !isNaN(parseFloat(String(form.priceText))) &&
    form.businessModes.length > 0,
)

function toggleMode(mode: BusinessMode) {
  const index = form.businessModes.indexOf(mode)
  if (index === -1) {
    form.businessModes.push(mode)
  } else if (form.businessModes.length > 1) {
    form.businessModes.splice(index, 1)
  }
}

function handleImageFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  imageSizeWarning.value = ''

  if (file.size > 200_000) {
    imageSizeWarning.value = `Image is ${(file.size / 1024).toFixed(0)} KB — large images reduce localStorage space.`
  }

  const reader = new FileReader()
  reader.onload = (e) => {
    const dataUrl = e.target?.result as string
    form.imageUrl = dataUrl
    imagePreview.value = dataUrl
  }
  reader.readAsDataURL(file)
}

async function save() {
  if (!isValid.value || saving.value) return
  saving.value = true

  const priceCents = Math.round(parseFloat(form.priceText) * 100)

  // "Compare at" only means anything above the selling price — anything else
  // (blank, unparseable, or at/below price) stores nothing, so the storefront
  // never renders a 0% or negative discount badge.
  const parsedCompareAt = Math.round(parseFloat(form.compareAtText) * 100)
  const compareAtPriceCents =
    Number.isFinite(parsedCompareAt) && parsedCompareAt > priceCents ? parsedCompareAt : undefined

  const input = {
    name: form.name.trim(),
    categoryId: form.categoryId,
    sku: '',
    barcode: form.barcode.trim(),
    priceCents,
    compareAtPriceCents,
    taxRate: form.taxRate,
    kind: form.kind,
    unitLabel: form.kind === 'weighted' ? form.unitLabel.trim() : undefined,
    imageUrl: form.imageUrl || undefined,
    outOfStock: form.trackInventory ? form.stockQty === 0 : form.outOfStock,
    stockQty: form.trackInventory ? form.stockQty : undefined,
    lowStockThreshold: form.trackInventory ? form.lowStockThreshold : undefined,
    businessModes: form.businessModes,
  }

  try {
    if (isEdit.value && props.product) {
      await store.editProduct({ ...props.product, ...input, sku: props.product.sku })
    } else {
      await store.createProduct(input)
    }
    emit('saved')
  } finally {
    saving.value = false
  }
}

async function destroy() {
  if (!props.product) return
  await store.removeProduct(props.product.id)
  emit('saved')
}

function handleOverlayClick(e: MouseEvent) {
  if (e.target === e.currentTarget) emit('close')
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div
      class="sheet-overlay"
      role="dialog"
      :aria-label="title"
      @click="handleOverlayClick"
      @keydown="handleKeydown"
    >
      <div class="sheet-panel product-sheet">
        <div class="sheet-grabber" />

        <div class="sheet-scroll product-sheet__scroll">

        <!-- Header -->
        <div class="product-sheet__header">
          <h2 class="panel-title">{{ title }}</h2>
          <button class="icon-button" type="button" aria-label="Close" @click="emit('close')">
            <X :size="18" />
          </button>
        </div>

          <!-- Two-column body -->
          <div class="product-sheet__cols">

            <!-- ── Left column: core details ─────────────────────────── -->
            <div class="product-sheet__col">

              <!-- Image -->
              <div class="ps-field">
                <p class="section-label">Image</p>
                <div class="product-sheet__image-row">
                  <div class="product-sheet__preview">
                    <img v-if="imagePreview" :src="imagePreview" alt="Product preview" />
                    <ImagePlus v-else :size="24" />
                  </div>
                  <div class="product-sheet__image-actions">
                    <label class="segment-button product-sheet__upload-label">
                      Upload
                      <input type="file" accept="image/*" class="sr-only" @change="handleImageFile" />
                    </label>
                    <button
                      v-if="imagePreview"
                      class="plain-danger"
                      type="button"
                      aria-label="Remove image"
                      @click="form.imageUrl = ''; imagePreview = ''"
                    >
                      <Trash2 :size="14" />
                    </button>
                  </div>
                </div>
                <p v-if="imageSizeWarning" class="product-sheet__size-warning">{{ imageSizeWarning }}</p>
              </div>

              <!-- Name -->
              <div class="ps-field">
                <p class="section-label">Name</p>
                <input
                  v-model="form.name"
                  class="sheet-input"
                  type="text"
                  placeholder="Product name"
                  required
                />
              </div>

              <!-- Category -->
              <div class="ps-field">
                <p class="section-label">Category</p>
                <AutocompleteSelect
                  v-model="form.categoryId"
                  label="Category"
                  :options="categoryOptions"
                />
              </div>

              <!-- Price -->
              <div class="ps-field">
                <p class="section-label">Price</p>
                <input
                  v-model="form.priceText"
                  class="sheet-input"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="0.00"
                />
              </div>

              <!-- Compare at (optional) -->
              <div class="ps-field">
                <p class="section-label">Compare at <span class="section-label--optional">optional</span></p>
                <input
                  v-model="form.compareAtText"
                  class="sheet-input"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="0.00"
                />
                <p class="ps-hint">
                  <template v-if="compareAtPreview">
                    Shows as <strong>{{ compareAtPreview }}</strong> off in the online store.
                  </template>
                  <template v-else>
                    The original price. Set it above the selling price to show a discount badge online.
                  </template>
                </p>
              </div>

              <!-- Product type -->
              <div class="ps-field">
                <p class="section-label">Product type</p>
                <div class="segmented-control">
                  <button
                    class="segment-button"
                    :class="{ active: form.kind === 'standard' }"
                    type="button"
                    @click="form.kind = 'standard'"
                  >Unit</button>
                  <button
                    class="segment-button"
                    :class="{ active: form.kind === 'weighted' }"
                    type="button"
                    @click="form.kind = 'weighted'"
                  >By weight</button>
                </div>
              </div>

              <!-- Unit label -->
              <div v-if="form.kind === 'weighted'" class="ps-field">
                <p class="section-label">Unit label</p>
                <input v-model="form.unitLabel" class="sheet-input" type="text" placeholder="/ kg" />
              </div>
            </div>

            <!-- ── Right column: settings ─────────────────────────────── -->
            <div class="product-sheet__col product-sheet__col--right">

              <!-- Available in -->
              <div class="ps-field">
                <p class="section-label">Available in</p>
                <div class="product-sheet__modes">
                  <label
                    v-for="mode in (['coffee-shop', 'grocery', 'restaurant', 'nail-salon'] as BusinessMode[])"
                    :key="mode"
                    class="product-sheet__mode-label"
                  >
                    <input
                      type="checkbox"
                      :checked="form.businessModes.includes(mode)"
                      @change="toggleMode(mode)"
                    />
                    <span>{{ businessModeLabel(mode) }}</span>
                  </label>
                </div>
              </div>

              <!-- Barcode -->
              <div class="ps-field">
                <p class="section-label">Barcode <span class="section-label--optional">(optional)</span></p>
                <input v-model="form.barcode" class="sheet-input" type="text" placeholder="Auto-generated" />
              </div>

              <!-- Divider -->
              <div class="ps-divider" />

              <!-- Track inventory -->
              <div class="ps-field ps-toggle-row">
                <div>
                  <p class="ps-toggle-label">Track inventory</p>
                  <p class="ps-toggle-hint">Auto-update stock on each sale</p>
                </div>
                <ToggleSwitch v-model="form.trackInventory" :ariaLabel="'Track inventory'" />
              </div>

              <!-- Inventory fields -->
              <template v-if="form.trackInventory">
                <div class="ps-field ps-stock-row">
                  <div class="ps-field">
                    <p class="section-label">Stock qty</p>
                    <input
                      v-model.number="form.stockQty"
                      class="sheet-input"
                      type="number"
                      min="0"
                      step="1"
                      placeholder="0"
                    />
                  </div>
                  <div class="ps-field">
                    <p class="section-label">Low stock alert</p>
                    <input
                      v-model.number="form.lowStockThreshold"
                      class="sheet-input"
                      type="number"
                      min="1"
                      step="1"
                      placeholder="5"
                    />
                  </div>
                </div>
              </template>

              <!-- Out of stock (manual, only when not tracking) -->
              <div v-if="isEdit && !form.trackInventory" class="ps-field ps-toggle-row">
                <p class="ps-toggle-label">Out of stock</p>
                <ToggleSwitch v-model="form.outOfStock" :ariaLabel="'Out of stock'" />
              </div>

            </div>
          </div>

          <!-- ── Footer ──────────────────────────────────────────────── -->
          <div class="product-sheet__footer">
            <button
              class="primary-button product-sheet__save-btn"
              type="button"
              :disabled="!isValid || saving"
              @click="save"
            >
              {{ isEdit ? 'Update Product' : 'Save Product' }}
            </button>

            <template v-if="isEdit">
              <div v-if="!confirmDelete" class="product-sheet__delete-trigger">
                <button class="plain-danger product-sheet__delete-btn" type="button" @click="confirmDelete = true">
                  <Trash2 :size="15" />
                  Delete product
                </button>
              </div>
              <div v-else class="product-sheet__delete-confirm">
                <p>Delete <strong>{{ product?.name }}</strong>? This cannot be undone.</p>
                <div class="product-sheet__delete-actions">
                  <button class="segment-button" type="button" @click="confirmDelete = false">Cancel</button>
                  <button class="danger-button" type="button" @click="destroy">Yes, delete</button>
                </div>
              </div>
            </template>
          </div>

        </div><!-- /sheet-scroll -->
      </div>
    </div>
  </Teleport>
</template>
