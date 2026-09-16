<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, Ban, CreditCard, Printer, ReceiptText, Search, Wallet, X } from '@lucide/vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import ChartCard from '@pos/core/components/ChartCard.vue'
import DeliveryBoard from '@pos/core/components/DeliveryBoard.vue'
import MetricCard from '@pos/core/components/MetricCard.vue'
import RangeSelector, { type Range } from '@pos/core/components/RangeSelector.vue'
import { useAuthStore } from '@pos/core/stores/auth'
import { usePosStore } from '@pos/core/stores/pos'
import { printReceipt } from '@pos/core/utils/receipt'
import {
  businessModeLabel,
  formatCompactDate,
  formatCurrency,
  type BusinessMode,
  type OrderSummary,
  type PaymentMethod,
} from '@pos/shared/index'

const store = usePosStore()
const auth = useAuthStore()

onMounted(() => {
  if (!store.isReady) {
    void store.initialize()
  }
})

const paymentFilterOptions: { value: 'all' | PaymentMethod; label: string }[] = [
  { value: 'all',     label: 'All payments' },
  { value: 'cash',    label: 'Cash' },
  { value: 'card',    label: 'Card' },
  { value: 'ewallet', label: 'E-wallet' },
]

const modeFilterOptions: { value: 'all' | BusinessMode; label: string }[] = [
  { value: 'all',           label: 'All modes' },
  { value: 'coffee-shop',   label: 'Coffee shop' },
  { value: 'grocery',       label: 'Grocery store' },
  { value: 'restaurant',    label: 'Restaurant' },
  { value: 'nail-salon',    label: 'Nail salon' },
]

const range = ref<Range>('week')
const searchQuery = ref('')
const paymentFilter = ref<'all' | PaymentMethod>('all')
const modeFilter = ref<'all' | BusinessMode>('all')
const selectedOrderId = ref<string | null>(null)
const now = new Date()

interface Bounds {
  start: Date
  end: Date
}

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate())
}

function getMonday(value: Date) {
  const day = value.getDay()
  const diff = day === 0 ? -6 : 1 - day
  return new Date(startOfDay(value).getTime() + diff * 86400000)
}

function getBounds(value: Range): Bounds {
  const today = startOfDay(now)

  switch (value) {
    case 'today':
      return { start: today, end: new Date(today.getTime() + 86400000) }
    case 'week': {
      const start = getMonday(now)
      return { start, end: new Date(start.getTime() + 7 * 86400000) }
    }
    case 'month':
      return {
        start: new Date(today.getFullYear(), today.getMonth(), 1),
        end: new Date(today.getFullYear(), today.getMonth() + 1, 1),
      }
    case 'all':
      return { start: new Date(0), end: new Date(8640000000000000) }
  }
}

function inBounds(order: OrderSummary, bounds: Bounds) {
  const createdAt = new Date(order.createdAt)
  return createdAt >= bounds.start && createdAt < bounds.end
}

function paymentLabel(method: PaymentMethod) {
  switch (method) {
    case 'cash':
      return 'Cash'
    case 'card':
      return 'Card'
    case 'ewallet':
      return 'E-wallet'
  }
}

function orderTypeLabel(type: OrderSummary['orderType']) {
  return type === 'dine_in' ? 'Dine in' : 'Takeaway'
}

function userNameFor(userId: string | null | undefined): string | null {
  if (!userId) return null
  return auth.users.find((user) => user.id === userId)?.fullName ?? null
}

function createdByLabel(order: OrderSummary) {
  return userNameFor(order.createdByUserId) ?? null
}

function formatFullDate(value: string) {
  return new Intl.DateTimeFormat('en-PH', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value))
}

function handlePrintReceipt(order: OrderSummary) {
  printReceipt(order, {
    name: store.settings.businessName,
    imageUrl: store.settings.businessImageUrl,
  })
}

const isVoiding = ref(false)

async function handleVoidOrder(order: OrderSummary) {
  if (!auth.isOwner || order.voidedAt || isVoiding.value) return

  const reason = window.prompt(
    `Void ticket ${order.ticketNumber}? This restores inventory and removes it from revenue. This can't be undone.\n\nOptional reason:`,
  )
  if (reason === null) return

  isVoiding.value = true
  try {
    await store.voidOrder(order.id, { userId: auth.currentUser?.id ?? null, reason: reason || null })
  } finally {
    isVoiding.value = false
  }
}

const bounds = computed(() => getBounds(range.value))
const rangeOrders = computed(() => store.orders.filter((order) => inBounds(order, bounds.value)))

const filteredOrders = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()

  return rangeOrders.value
    .filter((order) => {
      if (paymentFilter.value !== 'all' && order.paymentMethod !== paymentFilter.value) {
        return false
      }

      if (modeFilter.value !== 'all' && order.businessMode !== modeFilter.value) {
        return false
      }

      if (!needle) {
        return true
      }

      return (
        order.ticketNumber.toLowerCase().includes(needle)
        || order.customerName.toLowerCase().includes(needle)
        || order.paymentMethod.toLowerCase().includes(needle)
        || order.items.some((item) => item.name.toLowerCase().includes(needle))
      )
    })
    .slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
})

const selectedOrder = computed(() =>
  filteredOrders.value.find((order) => order.id === selectedOrderId.value) ?? filteredOrders.value[0] ?? null,
)

watch(
  filteredOrders,
  (orders) => {
    if (!orders.length) {
      selectedOrderId.value = null
      return
    }

    if (!orders.some((order) => order.id === selectedOrderId.value)) {
      selectedOrderId.value = orders[0].id
    }
  },
  { immediate: true },
)

// Voided orders stay visible in the list/search below for record-keeping,
// but drop out of every revenue rollup on this page.
const activeOrders = computed(() => filteredOrders.value.filter((order) => !order.voidedAt))

const grossSales = computed(() => activeOrders.value.reduce((sum, order) => sum + order.totalCents, 0))
const taxCollected = computed(() => activeOrders.value.reduce((sum, order) => sum + order.taxCents, 0))
const averageTicket = computed(() =>
  activeOrders.value.length > 0 ? Math.round(grossSales.value / activeOrders.value.length) : 0,
)
const itemCount = computed(() =>
  activeOrders.value.reduce(
    (sum, order) => sum + order.items.reduce((itemSum, item) => itemSum + item.quantity, 0),
    0,
  ),
)

const paymentTotals = computed(() => {
  const total = grossSales.value || 1
  const methods: PaymentMethod[] = ['cash', 'card', 'ewallet']

  return methods.map((method) => {
    const orders = activeOrders.value.filter((order) => order.paymentMethod === method)
    const amount = orders.reduce((sum, order) => sum + order.totalCents, 0)

    return {
      method,
      label: paymentLabel(method),
      count: orders.length,
      amount,
      percentage: Math.round((amount / total) * 100),
    }
  })
})

const modeTotals = computed(() => {
  const modes: BusinessMode[] = ['coffee-shop', 'grocery', 'restaurant', 'nail-salon']

  return modes
    .map((mode) => {
      const orders = activeOrders.value.filter((order) => order.businessMode === mode)
      return {
        mode,
        label: businessModeLabel(mode),
        count: orders.length,
        amount: orders.reduce((sum, order) => sum + order.totalCents, 0),
      }
    })
    .filter((entry) => entry.count > 0)
    .sort((a, b) => b.amount - a.amount)
})

const rangeCaption = computed(() => {
  if (range.value === 'all') {
    return 'All completed orders'
  }

  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
  if (range.value === 'today') {
    return formatter.format(now)
  }

  const endForCaption = new Date(bounds.value.end.getTime() - 86400000)
  return `${formatter.format(bounds.value.start)} - ${formatter.format(endForCaption)}`
})
</script>

<template>
  <div class="orders-page">
    <section class="orders-header">
      <div>
        <h1 class="orders-title">Orders</h1>
        <p class="orders-copy">Review tickets, inspect line items, and reprint receipts from one searchable history.</p>
      </div>
      <div class="orders-toolbar">
        <div class="orders-range">
          <span class="orders-range__text">{{ rangeCaption }}</span>
          <RangeSelector v-model="range" />
        </div>
      </div>
    </section>

    <!-- Above the history, because a delivery still out is the one thing on
         this page that is about right now rather than about the record. -->
    <section class="orders-deliveries">
      <DeliveryBoard />
    </section>

    <section class="orders-kpis">
      <MetricCard label="Gross sales" :value="formatCurrency(grossSales)" />
      <MetricCard label="Completed orders" :value="String(activeOrders.length)" />
      <MetricCard label="Average ticket" :value="formatCurrency(averageTicket)" />
      <MetricCard label="Items sold" :value="String(itemCount)" />
    </section>

    <section class="orders-grid">
      <ChartCard title="Payment Split" summary="Order volume and revenue by payment method.">
        <div v-if="filteredOrders.length === 0" class="empty-state">
          Completed orders appear here after checkout.
        </div>
        <div v-else class="orders-payment-list">
          <div v-for="entry in paymentTotals" :key="entry.method" class="orders-payment-row">
            <div class="orders-payment-row__head">
              <span class="orders-payment-row__label">
                <Wallet v-if="entry.method === 'cash'" :size="15" />
                <CreditCard v-else :size="15" />
                {{ entry.label }}
              </span>
              <strong>{{ formatCurrency(entry.amount) }}</strong>
            </div>
            <div class="orders-payment-row__track">
              <div class="orders-payment-row__fill" :style="{ width: `${entry.percentage}%` }" />
            </div>
            <p>{{ entry.count }} orders, {{ entry.percentage }}% of revenue</p>
          </div>
        </div>
      </ChartCard>

      <ChartCard title="Business Modes" summary="Orders grouped by the active business mode when each ticket was completed.">
        <div v-if="modeTotals.length === 0" class="empty-state">
          Mode totals appear once orders match the filters.
        </div>
        <div v-else class="orders-mode-list">
          <div v-for="entry in modeTotals" :key="entry.mode" class="orders-mode-row">
            <div>
              <strong>{{ entry.label }}</strong>
              <p>{{ entry.count }} orders</p>
            </div>
            <span>{{ formatCurrency(entry.amount) }}</span>
          </div>
        </div>
      </ChartCard>
    </section>

    <section class="orders-workspace">
      <article class="orders-list-card">
        <div class="orders-list-card__head">
          <div>
            <h2>Order History</h2>
            <p>{{ filteredOrders.length }} of {{ rangeOrders.length }} orders in this range</p>
          </div>
          <span class="orders-tax">Tax {{ formatCurrency(taxCollected) }}</span>
        </div>

        <div class="orders-filters">
          <label class="orders-search">
            <Search :size="16" />
            <input v-model="searchQuery" type="search" placeholder="Search ticket, customer, item, or payment" />
            <button v-if="searchQuery" class="icon-button icon-button--sm" type="button" aria-label="Clear search" @click="searchQuery = ''">
              <X :size="14" />
            </button>
          </label>
          <AutocompleteSelect
            v-model="paymentFilter"
            class="orders-select"
            label="Filter by payment method"
            :options="paymentFilterOptions"
          />
          <AutocompleteSelect
            v-model="modeFilter"
            class="orders-select"
            label="Filter by business mode"
            :options="modeFilterOptions"
          />
        </div>

        <div v-if="filteredOrders.length === 0" class="orders-empty">
          No orders match the current filters.
        </div>

        <div v-else class="orders-list">
          <button
            v-for="order in filteredOrders"
            :key="order.id"
            class="orders-row"
            :class="{ 'orders-row--active': selectedOrder?.id === order.id, 'orders-row--voided': order.voidedAt }"
            type="button"
            @click="selectedOrderId = order.id"
          >
            <span class="orders-row__icon">
              <ReceiptText :size="17" />
            </span>
            <span class="orders-row__body">
              <strong>Ticket {{ order.ticketNumber }}<span v-if="order.voidedAt" class="orders-voided-tag">Voided</span></strong>
              <span>{{ order.customerName }} / {{ formatCompactDate(order.createdAt) }} / {{ paymentLabel(order.paymentMethod) }} / {{ orderTypeLabel(order.orderType) }}</span>
            </span>
            <span class="orders-row__amount">{{ formatCurrency(order.totalCents) }}</span>
            <ArrowRight class="orders-row__arrow" :size="16" />
          </button>
        </div>
      </article>

      <aside v-if="filteredOrders.length > 0" class="orders-detail">
        <template v-if="selectedOrder">
          <div class="orders-detail__head">
            <div>
              <p class="orders-detail__eyebrow">Selected ticket</p>
              <h2>Ticket {{ selectedOrder.ticketNumber }}</h2>
              <span>{{ formatFullDate(selectedOrder.createdAt) }}</span>
            </div>
            <div class="orders-detail__actions">
              <button
                v-if="auth.isOwner && !selectedOrder.voidedAt"
                class="icon-button"
                type="button"
                :disabled="isVoiding"
                :aria-label="`Void ticket ${selectedOrder.ticketNumber}`"
                @click="handleVoidOrder(selectedOrder)"
              >
                <Ban :size="18" />
              </button>
              <button
                class="icon-button"
                type="button"
                :aria-label="`Print receipt for ticket ${selectedOrder.ticketNumber}`"
                @click="handlePrintReceipt(selectedOrder)"
              >
                <Printer :size="18" />
              </button>
            </div>
          </div>

          <div class="orders-detail__chips">
            <span v-if="selectedOrder.voidedAt" class="orders-detail__chip--voided">Voided</span>
            <span>{{ selectedOrder.customerName }}</span>
            <span>{{ businessModeLabel(selectedOrder.businessMode) }}</span>
            <span>{{ orderTypeLabel(selectedOrder.orderType) }}</span>
            <span>{{ paymentLabel(selectedOrder.paymentMethod) }}</span>
            <span v-if="createdByLabel(selectedOrder)">By {{ createdByLabel(selectedOrder) }}</span>
          </div>
          <p v-if="selectedOrder.voidedAt" class="orders-detail__void-note">
            Voided {{ formatFullDate(selectedOrder.voidedAt) }}<template v-if="userNameFor(selectedOrder.voidedByUserId)"> by {{ userNameFor(selectedOrder.voidedByUserId) }}</template><template v-if="selectedOrder.voidReason">: {{ selectedOrder.voidReason }}</template>
          </p>
          <p v-if="selectedOrder.paymentConfirmedAt" class="orders-detail__paid-note">
            Payment confirmed {{ formatFullDate(selectedOrder.paymentConfirmedAt) }}<template v-if="userNameFor(selectedOrder.paymentConfirmedByUserId)"> by {{ userNameFor(selectedOrder.paymentConfirmedByUserId) }}</template>
          </p>

          <div class="orders-lines">
            <div v-for="item in selectedOrder.items" :key="`${selectedOrder.id}-${item.productId}`" class="orders-line">
              <div>
                <strong>{{ item.name }}</strong>
                <span>{{ item.quantity }} x {{ formatCurrency(item.unitPriceCents) }}</span>
              </div>
              <strong>{{ formatCurrency(item.lineTotalCents) }}</strong>
            </div>
          </div>

          <div class="orders-totals">
            <div>
              <span>Subtotal</span>
              <strong>{{ formatCurrency(selectedOrder.subtotalCents) }}</strong>
            </div>
            <div>
              <span>Tax</span>
              <strong>{{ formatCurrency(selectedOrder.taxCents) }}</strong>
            </div>
            <div class="orders-totals__grand">
              <span>Total</span>
              <strong>{{ formatCurrency(selectedOrder.totalCents) }}</strong>
            </div>
            <div v-if="selectedOrder.paymentMethod === 'cash'">
              <span>Tendered</span>
              <strong>{{ formatCurrency(selectedOrder.tenderedCents) }}</strong>
            </div>
            <div v-if="selectedOrder.paymentMethod === 'cash'">
              <span>Change</span>
              <strong>{{ formatCurrency(selectedOrder.changeCents) }}</strong>
            </div>
          </div>
        </template>

        <div v-else class="orders-empty orders-empty--detail">
          Select an order to view receipt details.
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.orders-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-5);
  padding: var(--space-4) 0 var(--space-8);
}

.orders-header,
.orders-grid,
.orders-workspace {
  display: grid;
  gap: var(--space-4);
}

.orders-header {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
}

.orders-title {
  margin: 0;
  font: var(--type-title1);
}

.orders-copy {
  max-width: 68ch;
  margin: var(--space-2) 0 0;
  color: var(--text-secondary);
}

.orders-toolbar,
.orders-range,
.orders-list-card__head,
.orders-payment-row__head,
.orders-mode-row,
.orders-detail__head,
.orders-line,
.orders-totals div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.orders-range {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.orders-range__text,
.orders-list-card__head p,
.orders-payment-row p,
.orders-mode-row p,
.orders-detail__head span,
.orders-line span,
.orders-totals span {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.orders-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.orders-grid {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.orders-payment-list,
.orders-mode-list,
.orders-list,
.orders-lines,
.orders-totals {
  display: grid;
  gap: var(--space-3);
}

.orders-payment-row {
  display: grid;
  gap: var(--space-2);
}

.orders-payment-row__label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-primary);
  font: var(--type-subhead);
  font-weight: 600;
}

.orders-payment-row__track {
  height: 8px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--fill);
}

.orders-payment-row__fill {
  height: 100%;
  min-width: 4px;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent), var(--success));
}

.orders-mode-row {
  padding: var(--space-3) 0;
  border-bottom: 0.5px solid var(--separator);
}

.orders-mode-row:last-child {
  border-bottom: none;
}

.orders-mode-row strong,
.orders-mode-row span,
.orders-list-card__head h2,
.orders-detail__head h2,
.orders-line strong,
.orders-totals strong {
  margin: 0;
  color: var(--text-primary);
}

.orders-workspace {
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.75fr);
  align-items: start;
}

.orders-list-card,
.orders-detail {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-xl);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.orders-list-card__head h2,
.orders-detail__head h2 {
  font: var(--type-headline);
}

.orders-tax {
  flex: none;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.orders-filters {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  gap: var(--space-2);
}

.orders-search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  background: var(--fill);
  color: var(--text-tertiary);
}

.orders-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font: var(--type-subhead);
}

.orders-select {
  width: 100%;
  min-width: 142px;
}

.orders-list {
  max-height: 620px;
  overflow: auto;
  padding-right: var(--space-1);
}

.orders-row {
  width: 100%;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 0.5px solid transparent;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  transition:
    background var(--dur-fast) var(--ease-out),
    border-color var(--dur-fast) var(--ease-out);
}

.orders-row:hover,
.orders-row--active {
  border-color: var(--separator);
  background: var(--fill);
}

.orders-row--voided {
  opacity: 0.6;
}

.orders-voided-tag {
  display: inline-block;
  margin-left: var(--space-2);
  padding: 1px var(--space-2);
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--danger) 15%, transparent);
  color: var(--danger);
  font: var(--type-caption);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  vertical-align: middle;
}

.orders-row__icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}

.orders-row__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.orders-row__body strong,
.orders-row__body span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.orders-row__body strong {
  font: var(--type-subhead);
  font-weight: 600;
}

.orders-row__body span {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.orders-row__amount {
  font: var(--type-subhead);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.orders-row__arrow {
  color: var(--text-tertiary);
}

.orders-detail {
  position: sticky;
  top: 20px;
}

.orders-detail__eyebrow {
  margin: 0 0 var(--space-1);
  color: var(--text-secondary);
  font: var(--type-caption);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.orders-detail__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.orders-detail__chips span {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  padding: 0 var(--space-3);
  border-radius: var(--radius-pill);
  background: var(--fill);
  color: var(--text-secondary);
  font: var(--type-caption);
  font-weight: 600;
}

.orders-detail__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.orders-detail__chips .orders-detail__chip--voided {
  background: color-mix(in srgb, var(--danger) 15%, transparent);
  color: var(--danger);
}

.orders-detail__void-note {
  margin: calc(var(--space-2) * -1) 0 0;
  color: var(--danger);
  font: var(--type-caption);
}

.orders-detail__paid-note {
  margin: calc(var(--space-2) * -1) 0 0;
  color: var(--success);
  font: var(--type-caption);
}

.orders-lines {
  padding: var(--space-3) 0;
  border-top: 0.5px solid var(--separator);
  border-bottom: 0.5px solid var(--separator);
}

.orders-line div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.orders-line strong:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.orders-totals__grand {
  padding-top: var(--space-3);
  border-top: 0.5px solid var(--separator);
}

.orders-totals__grand strong {
  color: var(--accent);
  font: var(--type-title2);
}

.orders-empty {
  padding: var(--space-6);
  border-radius: var(--radius-lg);
  background: var(--fill);
  color: var(--text-secondary);
  text-align: center;
}

.orders-empty--detail {
  align-self: stretch;
  display: grid;
  place-items: center;
  min-height: 260px;
}

@media (max-width: 1180px) {
  .orders-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .orders-grid,
  .orders-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .orders-detail {
    position: static;
  }
}

@media (max-width: 760px) {
  .orders-header {
    grid-template-columns: minmax(0, 1fr);
  }

  .orders-copy {
    display: none;
  }

  .orders-range {
    justify-content: flex-start;
  }

  .orders-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .orders-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .orders-search {
    grid-column: 1 / -1;
  }

  .orders-select {
    width: 100%;
  }

  .orders-row {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .orders-row__arrow {
    display: none;
  }
}
</style>
