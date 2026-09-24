<script setup lang="ts">
import { Banknote, EllipsisVertical, Minus, Pencil, Plus, ReceiptText, ShoppingBag, Trash2 } from '@lucide/vue'
import { computed, reactive, ref } from 'vue'
import { formatCurrency, paymentMethodOptions } from '@pos/shared/index'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import PaymentSheet from '@pos/core/components/PaymentSheet.vue'
import DiscountControl from '@pos/core/components/DiscountControl.vue'
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
  return `${((store.taxCents / Math.max(1, store.subtotalCents - store.discountCents)) * 100).toFixed(2)}%`
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
  // Refused or unreachable: the sheet stays open with the reason.
  if (!(await store.completeOrder())) return
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
      <button class="order-panel__identity" type="button" @click="editingCustomer = !editingCustomer">
        <span class="order-panel__header-icon" aria-hidden="true"><ReceiptText :size="21" /></span>
        <span class="order-panel__identity-copy">
          <strong>{{ store.selectedCustomerId ? store.selectedCustomerName : 'Guest' }}</strong>
          <small>Order Number: #{{ nextOrderNumber }}</small>
        </span>
      </button>

      <div class="order-panel__header-actions">
        <button type="button" aria-label="Edit customer" @click="editingCustomer = !editingCustomer">
          <Pencil :size="18" />
        </button>
        <button type="button" aria-label="Clear order" :disabled="store.cartLines.length === 0" @click="store.clearCart">
          <Trash2 :size="18" />
        </button>
      </div>
    </div>

    <div v-if="editingCustomer" class="order-panel__customer-editor">
      <AutocompleteSelect
        :model-value="store.selectedCustomerId ?? ''"
        label="Select customer"
        placeholder="Search customers..."
        :options="store.customerOptions"
        @update:model-value="onSelectCustomer"
      />
      <button type="button" @click="editingCustomer = false">Done</button>
    </div>

    <div class="order-lines">
      <div v-if="store.cartLines.length === 0" class="empty-state">
        <ShoppingBag :size="28" />
        <strong>Your order is empty</strong>
        <span>Select a product to get started.</span>
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

        <div class="order-line__prices">
          <span>{{ formatCurrency(line.product.priceCents) }}</span>
          <strong>{{ formatCurrency(line.subtotalCents) }}</strong>
        </div>
        <button
          class="order-line__remove"
          type="button"
          :aria-label="`Remove ${line.product.name}`"
          :title="`Remove ${line.product.name}`"
          @click="store.removeLine(line.product.id)"
        >
          <EllipsisVertical :size="18" />
        </button>
      </article>
    </div>

    <div class="totals-card">
      <div class="totals-row">
        <span>Subtotal ({{ store.itemCount }} {{ store.itemCount === 1 ? 'item' : 'items' }})</span>
        <strong>{{ formatCurrency(store.subtotalCents) }}</strong>
      </div>
      <DiscountControl />
      <div class="totals-row">
        <span>Tax ({{ taxRateLabel }})</span>
        <strong>{{ formatCurrency(store.taxCents) }}</strong>
      </div>
      <div class="totals-row totals-row--grand">
        <span>Total</span>
        <strong class="total-amount">{{ formatCurrency(store.totalCents) }}</strong>
      </div>
    </div>

    <div class="order-panel__footer">
      <div class="order-panel__footer-row">
        <Banknote :size="18" />
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
        <ShoppingBag :size="19" />
        Place Order&nbsp;&nbsp;•&nbsp;&nbsp;{{ formatCurrency(store.totalCents) }}
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
  /* Mixed into the card rather than into white: this plate sits behind a
     product photo, and a white mix keeps it pale whatever the scheme says. */
  background:
    linear-gradient(
      135deg,
      color-mix(in srgb, var(--accent) 12%, var(--bg-elevated)),
      color-mix(in srgb, var(--fill) 70%, var(--bg-elevated))
    ),
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

.order-panel {
  /*
   * The basket paints with the till's tokens. RegisterPage declares a
   * green-tinted set of them on .register-page-stack for light and hands them
   * back to the app's own for dark, so everything below follows the scheme
   * without this file knowing which one is on.
   *
   * It used to hold the light values literally — white card, mint chips,
   * deep-green ink — which is what left the basket a white column on a black
   * page. What is left here are the values no token carries: two shadows that
   * have to go black rather than green-tinted in dark, and the two reds this
   * panel uses for its remove affordances. Dark values at the foot of the file.
   */
  --panel-shadow: 0 8px 28px rgba(6, 63, 52, 0.045);
  --checkout-shadow: 0 8px 16px rgba(5, 96, 73, 0.17);
  --panel-danger: #ef5e53;
  --panel-danger-muted: #ba4b4b;
  gap: 11px;
  padding: 16px 18px 18px;
  border: 1px solid var(--separator);
  border-radius: 18px;
  background: var(--bg-surface);
  color: var(--text-primary);
  box-shadow: var(--panel-shadow);
}

.order-panel__header {
  align-items: flex-start;
  gap: 12px;
}

.order-panel__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.order-panel__header-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}

.order-panel__title h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 750;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.order-panel__title p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.order-panel__customer-button {
  display: inline-flex;
  min-width: 190px;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 9px;
  padding: 0 12px;
  border: 1px solid var(--separator);
  border-radius: 11px;
  background: var(--fill);
  color: var(--accent);
  font-size: 12px;
}

.order-panel__customer-button strong {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-panel__customer-editor {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--separator);
  border-radius: 11px;
  background: var(--fill);
}

.order-panel__customer-editor :deep(.acselect) {
  flex: 1;
}

.order-panel__customer-editor > button {
  min-height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: 9px;
  background: var(--accent);
  color: var(--accent-text-on);
  font-size: 12px;
  font-weight: 700;
}

.order-panel__order-number {
  display: flex;
  min-height: 22px;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.order-panel__order-number button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--panel-danger-muted);
  font-size: 11px;
}

.order-lines {
  gap: 8px;
  padding-right: 2px;
}

.order-lines .empty-state {
  display: grid;
  min-height: 220px;
  place-items: center;
  align-content: center;
  gap: 8px;
  margin: auto 0;
  border: 1px dashed var(--separator);
  background: var(--fill);
  color: var(--text-secondary);
}

.order-lines .empty-state svg {
  color: var(--accent);
}

.order-lines .empty-state strong {
  color: var(--text-primary);
  font-size: 14px;
}

.order-lines .empty-state span {
  font-size: 12px;
}

.order-line {
  display: grid;
  grid-template-columns: 58px minmax(96px, 1fr) auto 70px 32px;
  gap: 10px;
  min-height: 80px;
  align-items: center;
  padding: 8px;
  border: 1px solid var(--separator);
  border-radius: 12px;
  background: var(--bg-elevated);
}

.order-line:last-child {
  border-bottom: 1px solid var(--separator);
}

.order-line__thumb {
  width: 58px;
  height: 58px;
  border-radius: 10px;
  background: var(--fill);
  box-shadow: inset 0 0 0 1px var(--separator);
}

.order-line__thumb img {
  width: calc(100% - 5px);
  height: calc(100% - 5px);
  object-fit: contain;
  mix-blend-mode: multiply;
}

.order-line__name {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.25;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.order-line__meta {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 10px;
}

.stepper {
  border: 1px solid var(--separator);
  border-radius: 9px;
}

.stepper button {
  width: 30px;
  min-height: 31px;
  color: var(--text-primary);
}

.stepper span {
  min-width: 28px;
  min-height: 31px;
  border-color: var(--separator);
  color: var(--text-primary);
  font-size: 12px;
}

.order-line__total {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 750;
  text-align: right;
}

.order-line__remove {
  display: grid;
  width: 31px;
  height: 31px;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: color-mix(in srgb, var(--panel-danger) 12%, transparent);
  color: var(--panel-danger);
}

.discount-row {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  padding: 0 13px;
  border: 0;
  border-radius: 11px;
  background: var(--fill);
  color: var(--text-primary);
  font-size: 13px;
}

.discount-row > span {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.discount-row i {
  display: grid;
  width: 31px;
  height: 31px;
  place-items: center;
  border-radius: 9px;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  font-style: normal;
}

.totals-card {
  gap: 8px;
  padding: 9px 0 0;
  border-radius: 0;
  background: transparent;
}

.totals-row {
  color: var(--text-secondary);
  font-size: 13px;
}

.totals-row strong {
  color: var(--text-primary);
  font-size: 13px;
}

.totals-row--grand {
  min-height: 56px;
  align-items: center;
  margin-top: 3px;
  padding-top: 8px;
  border-top: 1px solid var(--separator);
}

.totals-row--grand span {
  color: var(--text-primary);
  font-size: 17px;
  font-weight: 750;
  text-transform: none;
}

.total-amount {
  color: var(--accent) !important;
  font-size: 31px !important;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.order-panel__footer {
  gap: 8px;
}

.order-panel__payment-label {
  color: var(--text-secondary);
  font-size: 12px;
}

.order-panel__footer-row {
  flex-wrap: nowrap;
  min-height: 47px;
  gap: 9px;
  padding: 0 11px;
  border: 1px solid var(--separator);
  border-radius: 11px;
  color: var(--accent);
}

.order-panel__payment-method {
  flex: 1;
}

.order-panel__payment-method :deep(.acselect__trigger) {
  height: 44px;
  min-height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.checkout-button {
  display: inline-flex;
  height: 58px;
  align-items: center;
  gap: 8px;
  border-radius: 11px;
  /* Pressed first, then the accent: that is the direction the hand-picked pair
     ran in, dark green into lighter. It holds when the tokens flip, because
     dark's accent is the lighter of its pair too. */
  background: linear-gradient(135deg, var(--accent-pressed), var(--accent));
  color: var(--accent-text-on);
  font-size: 16px;
  font-weight: 750;
  box-shadow: var(--checkout-shadow);
}

@media (max-width: 1420px) {
  .order-panel__customer-button { min-width: 150px; }
  .order-line { grid-template-columns: 48px minmax(80px, 1fr) auto 62px 30px; gap: 7px; }
  .order-line__thumb { width: 48px; height: 48px; }
  .stepper button { width: 27px; }
  .stepper span { min-width: 24px; }
}

@media (max-width: 560px) {
  .order-panel { padding: 12px; }
  .order-panel__header { flex-direction: column; }
  .order-panel__customer-button { width: 100%; }
  .order-line { grid-template-columns: 46px minmax(0, 1fr) auto; }
  .order-line__thumb { width: 46px; height: 46px; }
  .stepper { grid-column: 2; justify-self: start; }
  .order-line__total { grid-column: 3; grid-row: 1; }
  .order-line__remove { grid-column: 3; }
}

/*
 * Dark. Only the four this panel owns: two shadows whose green tint is
 * invisible against a dark page and has to be plain black, and the two reds,
 * which are muted for a white card and too dim on a dark one — both become the
 * app's own danger colour there.
 *
 * Three selectors for the three ways dark is reached, written plainly rather
 * than through :global(); RegisterPage.vue's dark block carries the full note.
 * Keep the two blocks here in step.
 */
[data-theme='dark'] .order-panel,
[data-color-theme='nocturne'] .order-panel,
[data-color-theme='reserve'] .order-panel,
[data-color-theme='harbor'] .order-panel,
[data-color-theme='mono'] .order-panel {
  --panel-shadow: 0 8px 28px rgba(0, 0, 0, 0.45);
  --checkout-shadow: 0 8px 16px rgba(0, 0, 0, 0.5);
  --panel-danger: var(--danger);
  --panel-danger-muted: var(--danger);
}

/* Line thumbnails are blended `multiply` so a product's white backdrop drops
   into the row. On a dark row that drives the photo to black instead — see the
   longer note in GroceryProductGrid.vue. */
[data-theme='dark'] .order-line__thumb img,
[data-color-theme='nocturne'] .order-line__thumb img,
[data-color-theme='reserve'] .order-line__thumb img,
[data-color-theme='harbor'] .order-line__thumb img,
[data-color-theme='mono'] .order-line__thumb img {
  mix-blend-mode: normal;
}

@media (prefers-color-scheme: dark) {
  html:not([data-theme='light']) .order-panel {
    --panel-shadow: 0 8px 28px rgba(0, 0, 0, 0.45);
    --checkout-shadow: 0 8px 16px rgba(0, 0, 0, 0.5);
    --panel-danger: var(--danger);
    --panel-danger-muted: var(--danger);
  }

  html:not([data-theme='light']) .order-line__thumb img {
    mix-blend-mode: normal;
  }
}
</style>
