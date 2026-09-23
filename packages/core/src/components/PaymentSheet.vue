<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { formatCurrency, paymentMethodOptions } from '@pos/shared/index'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import NumericKeypad from '@pos/core/components/NumericKeypad.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { useSheetDrag } from '@pos/core/utils/sheetDrag'

const emit = defineEmits<{ close: []; confirm: [] }>()
const store = usePosStore()

const { dragOffset, isDragging, isClosing, onPointerDown, onPointerMove, onPointerUp, onPointerCancel } = useSheetDrag({
  onDismiss: () => emit('close'),
})

// Prevents a double-tap from firing two orders. Reset on unmount (dialog close),
// and when the sale is refused, so the cashier can fix it and try again.
const confirming = ref(false)
const panelRef = ref<HTMLElement | null>(null)
const creatingCustomer = ref(false)
const customerName = ref('')
const customerPhone = ref('')
const customerEmail = ref('')
const customerError = ref('')

// Philippine peso bill denominations in centavos
const BILLS = [10000, 20000, 50000, 100000, 200000, 500000]

// Show up to 3 bills that are >= total so the cashier can tap common denominations
const quickCashAmounts = computed(() =>
  BILLS.filter((b) => b >= store.totalCents).slice(0, 3),
)

const tenderedDisplay = computed(() => {
  if (!store.tenderedInput) {
    return formatCurrency(0)
  }

  const [wholePartRaw, fractionPart = ''] = store.tenderedInput.split('.')
  const wholePart = Number(wholePartRaw || '0')
  const wholeDisplay = new Intl.NumberFormat('en-PH', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(wholePart)

  return store.tenderedInput.includes('.') ? `₱${wholeDisplay}.${fractionPart}` : `₱${wholeDisplay}`
})

// Change only visible when tendered fully covers the total
const showChange = computed(
  () => store.tenderedCents > 0 && store.tenderedCents >= store.totalCents,
)

const confirmLabel = computed(() => {
  if (confirming.value) return 'Processing…'
  if (store.paymentMethod === 'cash') {
    if (store.tenderedCents === 0) return 'Enter amount to confirm'
    if (store.tenderedCents < store.totalCents) return 'Insufficient amount'
  }
  return 'Confirm payment'
})

watch(() => store.checkoutError, (message) => {
  if (message) confirming.value = false
})

function resetCustomerDraft() {
  customerName.value = ''
  customerPhone.value = ''
  customerEmail.value = ''
  customerError.value = ''
}

async function handleCreateCustomer() {
  const name = customerName.value.trim()
  if (!name) {
    customerError.value = 'Enter a customer name or keep the sale as Guest.'
    return
  }

  customerError.value = ''
  await store.createCustomer({
    name,
    phone: customerPhone.value,
    email: customerEmail.value,
  })
  creatingCustomer.value = false
  resetCustomerDraft()
}

function cancelCreateCustomer() {
  creatingCustomer.value = false
  resetCustomerDraft()
}

function handleConfirm() {
  if (!store.canCheckout || confirming.value) return
  confirming.value = true
  emit('confirm')
}

function handleKeydown(event: KeyboardEvent) {
  const target = event.target
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
    return
  }

  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }

  if (event.key === 'Enter' && store.canCheckout && !confirming.value) {
    event.preventDefault()
    handleConfirm()
    return
  }

  if (store.paymentMethod !== 'cash') return

  if (
    (event.key >= '0' && event.key <= '9')
    || (event.code.startsWith('Numpad') && /\d$/.test(event.code))
  ) {
    event.preventDefault()
    const rawDigit = event.key >= '0' && event.key <= '9'
      ? event.key
      : event.code.slice('Numpad'.length)
    store.appendTenderDigit(Number(rawDigit))
    return
  }

  if (event.key === '.' || event.key === ',' || event.code === 'NumpadDecimal') {
    event.preventDefault()
    store.appendTenderDecimal()
    return
  }

  if (event.key === 'Backspace' || event.key === 'Delete') {
    event.preventDefault()
    if (event.key === 'Delete') {
      store.clearTendered()
      return
    }

    store.backspaceTendered()
    return
  }

  if (event.key.toLowerCase() === 'c') {
    event.preventDefault()
    store.clearTendered()
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown)
  await nextTick()
  panelRef.value?.focus()
})
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <div
      class="sheet-overlay"
      :style="isDragging || isClosing ? { '--sheet-backdrop-opacity': String(Math.max(0, 1 - dragOffset / 400)) } : undefined"
      @click.self="emit('close')"
    >
      <div
        ref="panelRef"
        class="sheet-panel payment-sheet"
        :class="{ 'sheet-panel--dragging': isDragging }"
        :style="dragOffset ? { transform: `translateY(${dragOffset}px)` } : undefined"
        aria-label="Payment dialog"
        role="dialog"
        aria-modal="true"
        tabindex="-1"
      >
        <button
          class="sheet-grabber-handle"
          type="button"
          aria-label="Drag down to close"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerCancel"
        >
          <span class="sheet-grabber" />
        </button>

        <div class="sheet-scroll">
          <div class="payment-sheet__columns">
            <div class="payment-sheet__col">
              <!-- Total due -->
              <div class="panel-section">
                <p class="section-label">Total due</p>
                <strong class="total-amount">{{ formatCurrency(store.totalCents) }}</strong>
              </div>

              <div class="panel-section customer-panel">
                <div class="customer-panel__header">
                  <div>
                    <p class="section-label">Customer</p>
                    <strong class="customer-panel__name">{{ store.selectedCustomerName }}</strong>
                  </div>
                  <button
                    class="secondary-button customer-panel__action"
                    type="button"
                    @click="creatingCustomer ? cancelCreateCustomer() : (creatingCustomer = true)"
                  >
                    {{ creatingCustomer ? 'Close form' : 'New customer' }}
                  </button>
                </div>

                <AutocompleteSelect
                  :model-value="store.selectedCustomerId ?? ''"
                  label="Select customer"
                  :options="store.customerOptions"
                  @update:model-value="store.setSelectedCustomer($event || null)"
                />

                <div v-if="creatingCustomer" class="customer-panel__form">
                  <input
                    v-model="customerName"
                    class="sheet-input"
                    type="text"
                    placeholder="Customer name"
                  />
                  <input
                    v-model="customerPhone"
                    class="sheet-input"
                    type="tel"
                    placeholder="Phone number (optional)"
                  />
                  <input
                    v-model="customerEmail"
                    class="sheet-input"
                    type="email"
                    placeholder="Email (optional)"
                  />
                  <p v-if="customerError" class="customer-panel__error">{{ customerError }}</p>
                  <div class="customer-panel__actions">
                    <button class="secondary-button" type="button" @click="cancelCreateCustomer">
                      Cancel
                    </button>
                    <button class="primary-button" type="button" @click="handleCreateCustomer">
                      Save customer
                    </button>
                  </div>
                </div>
              </div>

              <!-- Payment method tabs -->
              <div class="segmented-control">
                <button
                  v-for="method in paymentMethodOptions"
                  :key="method.value"
                  class="segment-button"
                  :class="{ active: store.paymentMethod === method.value }"
                  type="button"
                  @click="store.setPaymentMethod(method.value)"
                >
                  {{ method.label }}
                </button>
              </div>
            </div>

            <div class="payment-sheet__col">
              <!-- ── Cash ───────────────────────────────────────────────── -->
              <div v-if="store.paymentMethod === 'cash'" class="cash-panel">
                <!-- Tendered display -->
                <div class="cash-display">
                  <span class="cash-display__label">Tendered</span>
                  <strong>{{ tenderedDisplay }}</strong>
                </div>

                <!-- Quick-cash denomination shortcuts -->
                <div class="quick-cash" role="group" aria-label="Quick cash amounts">
                  <button
                    class="quick-cash__btn"
                    type="button"
                    @click="store.setTendered(store.totalCents)"
                  >Exact</button>
                  <button
                    v-for="amount in quickCashAmounts"
                    :key="amount"
                    class="quick-cash__btn"
                    type="button"
                    @click="store.setTendered(amount)"
                  >{{ formatCurrency(amount) }}</button>
                </div>

                <NumericKeypad
                  @digit="store.appendTenderDigit"
                  @decimal="store.appendTenderDecimal"
                  @clear="store.clearTendered"
                  @backspace="store.backspaceTendered"
                />

                <!-- Change — only shown when tendered ≥ total -->
                <div v-if="showChange" class="cash-change-row">
                  <span class="cash-change-row__label">Change</span>
                  <strong class="cash-change-row__amount">{{ formatCurrency(store.changeCents) }}</strong>
                </div>
              </div>

              <!-- ── Card ───────────────────────────────────────────────── -->
              <div v-else-if="store.paymentMethod === 'card'" class="payment-prompt">
                <div class="payment-prompt__icon" aria-hidden="true">💳</div>
                <p class="payment-prompt__text">Insert, tap, or swipe card</p>
                <p class="payment-prompt__sub">Waiting for terminal…</p>
              </div>

              <!-- ── E-wallet ────────────────────────────────────────────── -->
              <div v-else-if="store.paymentMethod === 'ewallet'" class="payment-prompt">
                <div class="payment-prompt__icon" aria-hidden="true">📱</div>
                <p class="payment-prompt__text">Scan QR or enter reference</p>
                <input
                  class="sheet-input"
                  type="text"
                  placeholder="Reference number (optional)"
                />
              </div>
            </div>
          </div>

          <p v-if="store.checkoutError" class="payment-sheet__error" role="alert">{{ store.checkoutError }}</p>

          <!-- Confirm button -->
          <button
            class="primary-button checkout-button"
            :disabled="!store.canCheckout || confirming"
            type="button"
            @click="handleConfirm"
          >
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.payment-sheet {
  max-width: 700px;
}

.payment-sheet__error {
  margin: 0 0 var(--space-2);
  color: var(--danger-600, #b42318);
  font: var(--type-caption);
}

@media (max-width: 720px) {
  .sheet-panel.payment-sheet {
    width: 100%;
    height: 100vh;
    height: 100dvh;
    max-width: none;
    max-height: none;
    border-radius: 0;
    padding-top: max(var(--space-3), env(safe-area-inset-top));
    padding-bottom: env(safe-area-inset-bottom);
  }
}

.payment-sheet__columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5);
  align-items: start;
}

.payment-sheet__col {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

@media (max-width: 720px) {
  .payment-sheet__columns {
    grid-template-columns: minmax(0, 1fr);
  }
}

.customer-panel {
  display: grid;
  gap: var(--space-3);
}

.customer-panel__header,
.customer-panel__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.customer-panel__header {
  align-items: flex-end;
}

.customer-panel__name {
  display: block;
  font: var(--type-body);
  font-weight: 600;
}

.customer-panel__action {
  min-height: 36px;
  padding: 0 var(--space-3);
}

.customer-panel__form {
  display: grid;
  gap: var(--space-2);
}

.customer-panel__error {
  margin: 0;
  color: var(--danger-600, #b42318);
  font: var(--type-caption);
}
</style>
