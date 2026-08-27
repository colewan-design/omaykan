<script setup lang="ts">
import { Minus, Pencil, Plus, ReceiptText, Trash2 } from '@lucide/vue'
import { computed, reactive, ref } from 'vue'
import { formatCurrency, paymentMethodOptions } from '@pos/shared/index'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import PaymentSheet from '@pos/core/components/PaymentSheet.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { haptic, ImpactStyle } from '@pos/core/utils/haptics'

const store = usePosStore()
const showPayment = ref(false)
const editingCustomer = ref(false)
const failedThumbs = reactive<Record<string, boolean>>({})
const emit = defineEmits<{ 'payment-open': [] }>()


function thumbInitial(name: string) {
  return name.trim().charAt(0).toUpperCase() || '?'
}

const taxRateLabel = computed(() => {
  if (store.subtotalCents === 0) return '0%'
  return `${((store.taxCents / store.subtotalCents) * 100).toFixed(2)}%`
})

const nextOrderNumber = computed(() => String((store.activeShift?.orderCount ?? 0) + 1).padStart(3, '0'))

function onSelectCustomer(customerId: string) {
  store.setSelectedCustomer(customerId || null)
  editingCustomer.value = false
}

function adjustQty(productId: string, delta: 1 | -1) {
  if (delta > 0) store.increment(productId)
  else store.decrement(productId)
  haptic(ImpactStyle.Light)
}

async function confirmPayment() {
  await store.completeOrder()
  showPayment.value = false
  haptic(ImpactStyle.Medium)
}

async function openPayment() {
  if (store.cartLines.length === 0) return
  await store.notePaymentSheetOpened()
  showPayment.value = true
  emit('payment-open')
}
</script>

<template>
  <aside class="order-panel">
    <div class="order-panel__header">
      <span class="order-panel__header-icon" aria-hidden="true"><ReceiptText :size="18" /></span>

      <div class="order-panel__customer">
        <AutocompleteSelect
          v-if="editingCustomer"
          :model-value="store.selectedCustomerId ?? ''"
          label="Select customer"
          :options="store.customerOptions"
          @update:model-value="onSelectCustomer"
        />
        <template v-else>
          <strong class="order-panel__customer-name" :class="{ 'order-panel__customer-name--placeholder': !store.selectedCustomerId }">{{ store.selectedCustomerName }}</strong>
          <span class="order-panel__order-number">Order Number: #{{ nextOrderNumber }}</span>
        </template>
      </div>

      <div class="order-panel__header-actions">
        <button
          class="icon-button icon-button--sm"
          type="button"
          :aria-label="editingCustomer ? 'Done editing customer' : 'Edit customer'"
          @click="editingCustomer = !editingCustomer"
        >
          <Pencil :size="16" />
        </button>
        <button
          class="icon-button icon-button--sm"
          type="button"
          :disabled="store.cartLines.length === 0"
          aria-label="Clear order"
          @click="store.clearCart"
        >
          <Trash2 :size="16" />
        </button>
      </div>
    </div>

    <div class="order-lines">
      <div v-if="store.cartLines.length === 0" class="empty-state">
        Select products to start the order.
      </div>

      <article v-for="line in store.cartLines" :key="line.product.id" class="order-line">
        <div class="order-line__thumb">
          <img
            v-if="line.product.imageUrl && !failedThumbs[line.product.id]"
            :src="line.product.imageUrl"
            :alt="line.product.name"
            loading="lazy"
            @error="failedThumbs[line.product.id] = true"
          />
          <span v-else class="order-line__thumb-fallback">{{ thumbInitial(line.product.name) }}</span>
        </div>

        <div class="order-line__body">
          <p class="order-line__name">{{ line.product.name }}</p>
          <p v-if="line.product.kind === 'weighted'" class="order-line__meta">
            {{ line.quantity }} kg &times; {{ formatCurrency(line.product.priceCents) }}/kg
          </p>
        </div>

        <div v-if="line.product.kind !== 'weighted'" class="stepper">
          <button type="button" @click="adjustQty(line.product.id, -1)">
            <Minus :size="14" />
          </button>
          <span>{{ line.quantity }}</span>
          <button type="button" @click="adjustQty(line.product.id, 1)">
            <Plus :size="14" />
          </button>
        </div>

        <div class="order-line__total">{{ formatCurrency(line.subtotalCents) }}</div>
      </article>
    </div>

    <hr class="order-panel__tear" />

    <div class="totals-card">
      <div class="totals-row">
        <span>Subtotal</span>
        <strong>{{ formatCurrency(store.subtotalCents) }}</strong>
      </div>
      <div class="totals-row">
        <span>Tax ({{ taxRateLabel }})</span>
        <strong>{{ formatCurrency(store.taxCents) }}</strong>
      </div>
      <div class="totals-row totals-row--grand">
        <span>TOTAL</span>
        <strong class="total-amount">{{ formatCurrency(store.totalCents) }}</strong>
      </div>
    </div>

    <div class="order-panel__footer">
      <div class="order-panel__footer-row">
        <AutocompleteSelect
          flat
          class="order-panel__payment-method"
          :model-value="store.paymentMethod"
          label="Payment method"
          :options="paymentMethodOptions"
          @update:model-value="store.setPaymentMethod"
        />
      </div>

      <button
        class="primary-button checkout-button"
        :disabled="store.cartLines.length === 0"
        type="button"
        @click="openPayment"
      >
        Place Order · {{ formatCurrency(store.totalCents) }}
      </button>
    </div>

    <PaymentSheet v-if="showPayment" @close="showPayment = false" @confirm="confirmPayment" />
  </aside>
</template>

<style scoped>
.order-line {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 0.5px solid var(--separator);
}

.order-line:last-child {
  border-bottom: none;
}

.order-line__thumb {
  display: grid;
  place-items: center;
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  overflow: hidden;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 12%, white), color-mix(in srgb, var(--fill) 70%, white)),
    var(--bg-elevated);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 8%, var(--separator));
}

.order-line__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.order-line__thumb-fallback {
  color: var(--accent);
  font: var(--type-headline);
  font-weight: 700;
}

.order-line__body {
  flex: 1;
  min-width: 0;
}

.order-line__name {
  margin: 0;
  font: var(--type-subhead);
  font-weight: 600;
}

.order-line__meta {
  margin: 2px 0 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.order-line__total {
  flex: none;
  font: var(--type-headline);
  font-variant-numeric: tabular-nums;
}
</style>
