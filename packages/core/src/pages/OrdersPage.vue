<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Ban, Banknote, BarChart3, Bike, CheckCircle2, ChevronLeft, ChevronRight, Clock3,
  CreditCard, Ellipsis, FileDown, PackageCheck, Printer, ReceiptText, Search,
  ShoppingBag, Tags, Utensils, WalletCards, X, type LucideIcon,
} from '@lucide/vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import RangeSelector, { type Range } from '@pos/core/components/RangeSelector.vue'
import { useAuthStore } from '@pos/core/stores/auth'
import { usePosStore } from '@pos/core/stores/pos'
import { printReceipt } from '@pos/core/utils/receipt'
import { businessModeLabel, formatCurrency, type OrderStatus, type OrderSummary, type PaymentMethod } from '@pos/shared/index'

type StatusFilter = 'all' | OrderStatus | 'pending'
type ModeFilter = 'all' | 'delivery' | 'pickup' | 'dine_in' | 'takeaway'
type DisplayStatus = 'preparing' | 'pending' | 'ready' | 'completed' | 'voided'

const PAGE_SIZE = 6
const store = usePosStore()
const auth = useAuthStore()
const range = ref<Range>('week')
const searchQuery = ref('')
const statusFilter = ref<StatusFilter>('all')
const paymentFilter = ref<'all' | PaymentMethod>('all')
const modeFilter = ref<ModeFilter>('all')
const currentPage = ref(1)
const menuOrderId = ref<string | null>(null)
const detailOrderId = ref<string | null>(null)
const isVoiding = ref(false)
const now = new Date()

onMounted(() => { if (!store.isReady) void store.initialize() })

const statusFilterOptions: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: 'All statuses' }, { value: 'pending', label: 'Pending' },
  { value: 'preparing', label: 'Preparing' }, { value: 'ready', label: 'Ready' },
  { value: 'served', label: 'Completed' },
]
const paymentFilterOptions: { value: 'all' | PaymentMethod; label: string }[] = [
  { value: 'all', label: 'All payments' }, { value: 'cash', label: 'Cash' },
  { value: 'ewallet', label: 'GCash / e-wallet' }, { value: 'card', label: 'Card' },
]
const modeFilterOptions: { value: ModeFilter; label: string }[] = [
  { value: 'all', label: 'All modes' }, { value: 'delivery', label: 'Delivery' },
  { value: 'pickup', label: 'Pickup' }, { value: 'dine_in', label: 'Dine-in' },
  { value: 'takeaway', label: 'POS / takeaway' },
]

interface Bounds { start: Date; end: Date }
function startOfDay(value: Date) { return new Date(value.getFullYear(), value.getMonth(), value.getDate()) }
function getMonday(value: Date) {
  const day = value.getDay()
  return new Date(startOfDay(value).getTime() + (day === 0 ? -6 : 1 - day) * 86400000)
}
function getBounds(value: Range): Bounds {
  const today = startOfDay(now)
  if (value === 'today') return { start: today, end: new Date(today.getTime() + 86400000) }
  if (value === 'week') {
    const start = getMonday(now)
    return { start, end: new Date(start.getTime() + 7 * 86400000) }
  }
  if (value === 'month') return { start: new Date(today.getFullYear(), today.getMonth(), 1), end: new Date(today.getFullYear(), today.getMonth() + 1, 1) }
  return { start: new Date(0), end: new Date(8640000000000000) }
}
function previousBounds(value: Range, current: Bounds): Bounds | null {
  if (value === 'all') return null
  const duration = current.end.getTime() - current.start.getTime()
  return { start: new Date(current.start.getTime() - duration), end: new Date(current.end.getTime() - duration) }
}
function inBounds(order: OrderSummary, value: Bounds) {
  const createdAt = new Date(order.createdAt)
  return createdAt >= value.start && createdAt < value.end
}
function paymentLabel(method: PaymentMethod) { return method === 'cash' ? 'Cash' : method === 'card' ? 'Card' : 'GCash' }
function orderMode(order: OrderSummary): Exclude<ModeFilter, 'all'> {
  if (order.fulfillmentMethod === 'delivery') return 'delivery'
  if (order.fulfillmentMethod === 'pickup') return 'pickup'
  return order.orderType
}
function modeLabel(order: OrderSummary) {
  const mode = orderMode(order)
  return mode === 'delivery' ? 'Delivery' : mode === 'pickup' ? 'Pickup' : mode === 'dine_in' ? 'Dine-in' : 'POS'
}
function modeIcon(mode: Exclude<ModeFilter, 'all'>): LucideIcon {
  return mode === 'delivery' ? Bike : mode === 'pickup' ? ShoppingBag : Utensils
}
function displayStatus(order: OrderSummary): DisplayStatus {
  if (order.voidedAt) return 'voided'
  if (order.paymentStatus === 'unpaid') return 'pending'
  return order.status === 'served' ? 'completed' : order.status
}
function statusLabel(order: OrderSummary) {
  const status = displayStatus(order)
  return status.charAt(0).toUpperCase() + status.slice(1)
}
function statusIcon(status: DisplayStatus): LucideIcon {
  return status === 'completed' ? CheckCircle2 : status === 'ready' ? PackageCheck : status === 'voided' ? Ban : Clock3
}
function paymentIcon(method: PaymentMethod): LucideIcon {
  return method === 'cash' ? Banknote : method === 'card' ? CreditCard : WalletCards
}
function formatFullDate(value: string) {
  return new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}
function formatRangeDate(value: Date) {
  return new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' }).format(value)
}
function escapeCsv(value: string | number) { return `"${String(value).replace(/"/g, '""')}"` }
function handleExport() {
  const header = ['Order #', 'Customer', 'Mode', 'Payment', 'Total', 'Status', 'Time']
  const rows = filteredOrders.value.map((order) => [order.ticketNumber, order.customerName, modeLabel(order), paymentLabel(order.paymentMethod), (order.totalCents / 100).toFixed(2), statusLabel(order), formatFullDate(order.createdAt)])
  const csv = [header, ...rows].map((row) => row.map(escapeCsv).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `orders-${range.value}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
function handlePrintReceipt(order: OrderSummary) {
  menuOrderId.value = null
  printReceipt(order, { name: store.settings.businessName, imageUrl: store.settings.businessImageUrl })
}
async function handleVoidOrder(order: OrderSummary) {
  if (!auth.isOwner || order.voidedAt || isVoiding.value) return
  const reason = window.prompt(`Void ticket ${order.ticketNumber}? This restores inventory and removes it from revenue. This can't be undone.\n\nOptional reason:`)
  if (reason === null) return
  isVoiding.value = true
  try {
    await store.voidOrder(order.id, { userId: auth.currentUser?.id ?? null, reason: reason || null })
    menuOrderId.value = null
  } catch (error) {
    window.alert(error instanceof Error && error.message ? error.message : 'This sale could not be voided. Try again.')
  } finally { isVoiding.value = false }
}
function openDetails(order: OrderSummary) { detailOrderId.value = order.id; menuOrderId.value = null }

const allOrders = computed(() => {
  const merged = new Map<string, OrderSummary>()
  for (const order of store.orders) merged.set(order.id, order)
  for (const order of store.onlineOrders) merged.set(order.id, order)
  return [...merged.values()]
})
const bounds = computed(() => getBounds(range.value))
const periodOrders = computed(() => allOrders.value.filter((order) => inBounds(order, bounds.value)))
const previousPeriodOrders = computed(() => {
  const previous = previousBounds(range.value, bounds.value)
  return previous ? allOrders.value.filter((order) => inBounds(order, previous)) : []
})
function matchesStatus(order: OrderSummary) {
  if (statusFilter.value === 'all') return true
  if (statusFilter.value === 'pending') return order.paymentStatus === 'unpaid'
  return order.status === statusFilter.value && order.paymentStatus !== 'unpaid'
}
const filteredOrders = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()
  return periodOrders.value.filter((order) => {
    if (!matchesStatus(order)) return false
    if (paymentFilter.value !== 'all' && order.paymentMethod !== paymentFilter.value) return false
    if (modeFilter.value !== 'all' && orderMode(order) !== modeFilter.value) return false
    if (!needle) return true
    return [order.ticketNumber, order.customerName, paymentLabel(order.paymentMethod), modeLabel(order), ...order.items.map((item) => item.name)].some((value) => value.toLowerCase().includes(needle))
  }).sort((a, b) => b.createdAt.localeCompare(a.createdAt))
})
const pageCount = computed(() => Math.max(1, Math.ceil(filteredOrders.value.length / PAGE_SIZE)))
const paginatedOrders = computed(() => filteredOrders.value.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE))
const pageStart = computed(() => filteredOrders.value.length ? (currentPage.value - 1) * PAGE_SIZE + 1 : 0)
const pageEnd = computed(() => Math.min(currentPage.value * PAGE_SIZE, filteredOrders.value.length))
const visiblePages = computed(() => {
  if (pageCount.value <= 5) return Array.from({ length: pageCount.value }, (_, index) => index + 1)
  const start = Math.min(Math.max(1, currentPage.value - 2), pageCount.value - 4)
  return Array.from({ length: 5 }, (_, index) => start + index)
})
watch([range, searchQuery, statusFilter, paymentFilter, modeFilter], () => { currentPage.value = 1; menuOrderId.value = null })
watch(pageCount, (count) => { if (currentPage.value > count) currentPage.value = count })

const activeOrders = computed(() => periodOrders.value.filter((order) => !order.voidedAt))
const previousActiveOrders = computed(() => previousPeriodOrders.value.filter((order) => !order.voidedAt))
const grossSales = computed(() => activeOrders.value.reduce((sum, order) => sum + order.totalCents, 0))
const previousGrossSales = computed(() => previousActiveOrders.value.reduce((sum, order) => sum + order.totalCents, 0))
const averageOrder = computed(() => activeOrders.value.length ? Math.round(grossSales.value / activeOrders.value.length) : 0)
const previousAverageOrder = computed(() => previousActiveOrders.value.length ? Math.round(previousGrossSales.value / previousActiveOrders.value.length) : 0)
const pendingOrders = computed(() => activeOrders.value.filter((order) => order.paymentStatus === 'unpaid' || order.status !== 'served').length)
function percentDelta(current: number, previous: number) {
  if (range.value === 'all' || previous === 0) return null
  const value = Math.round(((current - previous) / previous) * 100)
  return { value: Math.abs(value), positive: value >= 0 }
}
const grossDelta = computed(() => percentDelta(grossSales.value, previousGrossSales.value))
const ordersDelta = computed(() => percentDelta(activeOrders.value.length, previousActiveOrders.value.length))
const averageDelta = computed(() => percentDelta(averageOrder.value, previousAverageOrder.value))
const comparisonLabel = computed(() => {
  if (range.value === 'today') return 'vs. previous day'
  if (range.value === 'week') return 'vs. previous week'
  if (range.value === 'month') return 'vs. previous month'
  return 'Across all orders'
})
const rangeCaption = computed(() => {
  if (range.value === 'all') return 'All order history'
  const end = new Date(bounds.value.end.getTime() - 86400000)
  return range.value === 'today' ? formatRangeDate(bounds.value.start) : `${formatRangeDate(bounds.value.start)} - ${formatRangeDate(end)}`
})
const paymentTotals = computed(() => {
  const total = grossSales.value || 1
  return (['cash', 'ewallet', 'card'] as PaymentMethod[]).map((method) => {
    const orders = activeOrders.value.filter((order) => order.paymentMethod === method)
    const amount = orders.reduce((sum, order) => sum + order.totalCents, 0)
    return { method, label: paymentLabel(method), amount, percentage: Math.round((amount / total) * 100) }
  })
})
const orderTypeTotals = computed(() => {
  const definitions: { mode: Exclude<ModeFilter, 'all'>; label: string }[] = [
    { mode: 'delivery', label: 'Delivery' }, { mode: 'pickup', label: 'Pickup' },
    { mode: 'dine_in', label: 'Dine-in' }, { mode: 'takeaway', label: 'POS' },
  ]
  const total = activeOrders.value.length || 1
  return definitions.map((definition) => {
    const count = activeOrders.value.filter((order) => orderMode(order) === definition.mode).length
    return { ...definition, count, percentage: Math.round((count / total) * 100) }
  }).filter((entry) => entry.count > 0)
})
const deliveryQueue = computed(() => {
  const online = periodOrders.value.filter((order) => order.channel === 'online' && !order.voidedAt)
  return [
    { label: 'Preparing', hint: 'Being prepared', count: online.filter((order) => order.status === 'preparing').length, tone: 'blue' },
    { label: 'Awaiting rider', hint: 'Ready for pickup', count: online.filter((order) => order.fulfillmentMethod === 'delivery' && (order.deliveryStage ?? 'pending') === 'pending').length, tone: 'amber' },
    { label: 'Pickup ready', hint: 'For customer', count: online.filter((order) => order.fulfillmentMethod === 'pickup' && order.status === 'ready').length, tone: 'green' },
  ]
})
const selectedOrder = computed(() => allOrders.value.find((order) => order.id === detailOrderId.value) ?? null)
</script>

<template>
  <div class="orders-page" @click="menuOrderId = null">
    <section class="orders-hero">
      <div><h1>Orders</h1><p>Review tickets, inspect line items, and reprint receipts from one searchable history.</p></div>
      <div class="orders-range"><span>{{ rangeCaption }}</span><RangeSelector v-model="range" /></div>
    </section>

    <section class="orders-kpis" aria-label="Order summary">
      <article class="orders-kpi">
        <span class="orders-kpi__icon"><BarChart3 :size="26" /></span>
        <div><p>Gross sales</p><div class="orders-kpi__value"><strong>{{ formatCurrency(grossSales) }}</strong><span v-if="grossDelta" :class="{ 'is-down': !grossDelta.positive }">{{ grossDelta.positive ? '↑' : '↓' }} {{ grossDelta.value }}%</span></div><small>{{ comparisonLabel }}</small></div>
      </article>
      <article class="orders-kpi">
        <span class="orders-kpi__icon"><ReceiptText :size="26" /></span>
        <div><p>Orders</p><div class="orders-kpi__value"><strong>{{ activeOrders.length }}</strong><span v-if="ordersDelta" :class="{ 'is-down': !ordersDelta.positive }">{{ ordersDelta.positive ? '↑' : '↓' }} {{ ordersDelta.value }}%</span></div><small>{{ comparisonLabel }}</small></div>
      </article>
      <article class="orders-kpi">
        <span class="orders-kpi__icon"><Tags :size="26" /></span>
        <div><p>Avg. order value</p><div class="orders-kpi__value"><strong>{{ formatCurrency(averageOrder) }}</strong><span v-if="averageDelta" :class="{ 'is-down': !averageDelta.positive }">{{ averageDelta.positive ? '↑' : '↓' }} {{ averageDelta.value }}%</span></div><small>{{ comparisonLabel }}</small></div>
      </article>
      <article class="orders-kpi">
        <span class="orders-kpi__icon"><Clock3 :size="26" /></span>
        <div><p>Pending orders</p><div class="orders-kpi__value"><strong>{{ pendingOrders }}</strong><span class="orders-kpi__muted">—</span></div><small>Needs attention</small></div>
      </article>
    </section>

    <section class="orders-history">
      <header class="orders-history__head">
        <div><h2>Order History</h2><p>{{ periodOrders.length }} orders • {{ rangeCaption }}</p></div>
        <button class="orders-export" type="button" @click="handleExport"><FileDown :size="18" />Export</button>
      </header>
      <div class="orders-filters">
        <label class="orders-search"><Search :size="18" /><input v-model="searchQuery" type="search" placeholder="Search order #, customer, item, or payment..." /><button v-if="searchQuery" type="button" aria-label="Clear search" @click="searchQuery = ''"><X :size="15" /></button></label>
        <AutocompleteSelect v-model="statusFilter" class="orders-select" label="Filter by status" :options="statusFilterOptions" />
        <AutocompleteSelect v-model="paymentFilter" class="orders-select" label="Filter by payment" :options="paymentFilterOptions" />
        <AutocompleteSelect v-model="modeFilter" class="orders-select" label="Filter by mode" :options="modeFilterOptions" />
      </div>

      <div class="orders-table-wrap"><div class="orders-table" role="table" aria-label="Order history">
        <div class="orders-table__header" role="row"><span role="columnheader">Order #</span><span role="columnheader">Customer</span><span role="columnheader">Mode</span><span role="columnheader">Payment</span><span role="columnheader">Total</span><span role="columnheader">Status</span><span role="columnheader">Time</span><span role="columnheader" class="sr-only">Actions</span></div>
        <div v-for="order in paginatedOrders" :key="order.id" class="orders-table__row" :class="{ 'is-voided': order.voidedAt }" role="row" tabindex="0" @click="openDetails(order)" @keydown.enter="openDetails(order)">
          <strong role="cell" data-label="Order #">#{{ order.ticketNumber }}</strong>
          <span role="cell" data-label="Customer">{{ order.customerName }}</span>
          <span role="cell" data-label="Mode" class="order-mode" :class="`order-mode--${orderMode(order)}`"><component :is="modeIcon(orderMode(order))" :size="16" />{{ modeLabel(order) }}</span>
          <span role="cell" data-label="Payment" class="order-payment"><component :is="paymentIcon(order.paymentMethod)" :size="16" />{{ paymentLabel(order.paymentMethod) }}</span>
          <strong role="cell" data-label="Total">{{ formatCurrency(order.totalCents) }}</strong>
          <span role="cell" data-label="Status" class="order-status" :class="`order-status--${displayStatus(order)}`"><component :is="statusIcon(displayStatus(order))" :size="14" />{{ statusLabel(order) }}</span>
          <time role="cell" data-label="Time" :datetime="order.createdAt">{{ formatFullDate(order.createdAt) }}</time>
          <span class="order-actions" role="cell" @click.stop>
            <button class="order-actions__trigger" type="button" :aria-label="`Actions for order ${order.ticketNumber}`" :aria-expanded="menuOrderId === order.id" @click="menuOrderId = menuOrderId === order.id ? null : order.id"><Ellipsis :size="19" /></button>
            <span v-if="menuOrderId === order.id" class="order-actions__menu">
              <button type="button" @click="openDetails(order)"><ReceiptText :size="15" />View details</button>
              <button type="button" @click="handlePrintReceipt(order)"><Printer :size="15" />Print receipt</button>
              <button v-if="auth.isOwner && !order.voidedAt" class="is-danger" type="button" :disabled="isVoiding" @click="handleVoidOrder(order)"><Ban :size="15" />Void order</button>
            </span>
          </span>
        </div>
        <div v-if="!paginatedOrders.length" class="orders-empty"><Search :size="24" /><strong>No orders found</strong><span>Try changing your search or filters.</span></div>
      </div></div>

      <footer class="orders-pagination">
        <span>Showing {{ pageStart }}–{{ pageEnd }} of {{ filteredOrders.length }} orders</span>
        <nav v-if="filteredOrders.length" aria-label="Order pages">
          <button type="button" aria-label="Previous page" :disabled="currentPage === 1" @click="currentPage--"><ChevronLeft :size="17" /></button>
          <button v-for="page in visiblePages" :key="page" type="button" :class="{ 'is-active': currentPage === page }" :aria-current="currentPage === page ? 'page' : undefined" @click="currentPage = page">{{ page }}</button>
          <span v-if="visiblePages.at(-1)! < pageCount">…</span>
          <button v-if="visiblePages.at(-1)! < pageCount" type="button" :class="{ 'is-active': currentPage === pageCount }" @click="currentPage = pageCount">{{ pageCount }}</button>
          <button type="button" aria-label="Next page" :disabled="currentPage === pageCount" @click="currentPage++"><ChevronRight :size="17" /></button>
        </nav>
      </footer>
    </section>

    <section class="orders-insights">
      <article class="insight-card delivery-summary">
        <header><h2>Delivery Queue</h2><RouterLink to="/messages">View all <ChevronRight :size="15" /></RouterLink></header>
        <div class="delivery-summary__items"><div v-for="item in deliveryQueue" :key="item.label" class="delivery-summary__item"><div><span :class="`queue-dot queue-dot--${item.tone}`" /><strong>{{ item.count }}</strong></div><p>{{ item.label }}</p><small>{{ item.hint }}</small></div></div>
      </article>
      <article class="insight-card">
        <header><h2>Payment Breakdown</h2><span>{{ range === 'all' ? 'All time' : range === 'today' ? 'Today' : `This ${range}` }}</span></header>
        <div class="breakdown-list"><div v-for="entry in paymentTotals" :key="entry.method" class="breakdown-row"><span class="breakdown-row__label"><component :is="paymentIcon(entry.method)" :size="18" />{{ entry.label }}</span><span class="breakdown-row__metric"><strong>{{ formatCurrency(entry.amount) }}</strong><small>{{ entry.percentage }}%</small></span><span class="breakdown-row__track"><i :style="{ width: `${entry.percentage}%` }" /></span></div></div>
      </article>
      <article class="insight-card">
        <header><h2>Order Type</h2><span>{{ range === 'all' ? 'All time' : range === 'today' ? 'Today' : `This ${range}` }}</span></header>
        <div class="breakdown-list"><div v-for="entry in orderTypeTotals" :key="entry.mode" class="type-row"><span class="type-row__label"><component :is="modeIcon(entry.mode)" :size="18" />{{ entry.label }}</span><strong>{{ entry.count }}</strong><small>{{ entry.percentage }}%</small><span class="type-row__track"><i :style="{ width: `${entry.percentage}%` }" /></span></div><p v-if="!orderTypeTotals.length" class="insight-empty">Order types appear here once orders match the range.</p></div>
      </article>
    </section>

    <div v-if="selectedOrder" class="order-drawer-backdrop" @click.self="detailOrderId = null">
      <aside class="order-drawer" aria-labelledby="order-drawer-title">
        <header class="order-drawer__head"><div><p>Order details</p><h2 id="order-drawer-title">#{{ selectedOrder.ticketNumber }}</h2></div><button type="button" aria-label="Close order details" @click="detailOrderId = null"><X :size="19" /></button></header>
        <div class="order-drawer__meta"><div><span>Customer</span><strong>{{ selectedOrder.customerName }}</strong></div><div><span>Placed</span><strong>{{ formatFullDate(selectedOrder.createdAt) }}</strong></div><div><span>Mode</span><strong>{{ modeLabel(selectedOrder) }}</strong></div><div><span>Payment</span><strong>{{ paymentLabel(selectedOrder.paymentMethod) }}</strong></div><div><span>Business</span><strong>{{ businessModeLabel(selectedOrder.businessMode) }}</strong></div><div><span>Status</span><strong>{{ statusLabel(selectedOrder) }}</strong></div></div>
        <div class="order-drawer__lines"><div v-for="item in selectedOrder.items" :key="item.productId"><span><strong>{{ item.name }}</strong><small>{{ item.quantity }} × {{ formatCurrency(item.unitPriceCents) }}</small></span><strong>{{ formatCurrency(item.lineTotalCents) }}</strong></div></div>
        <div class="order-drawer__totals"><div><span>Subtotal</span><strong>{{ formatCurrency(selectedOrder.subtotalCents) }}</strong></div><div v-if="selectedOrder.discountCents"><span>Discount</span><strong>−{{ formatCurrency(selectedOrder.discountCents) }}</strong></div><div><span>Tax</span><strong>{{ formatCurrency(selectedOrder.taxCents) }}</strong></div><div class="is-grand"><span>Total</span><strong>{{ formatCurrency(selectedOrder.totalCents) }}</strong></div></div>
        <footer><button type="button" @click="handlePrintReceipt(selectedOrder)"><Printer :size="17" />Print receipt</button><button v-if="auth.isOwner && !selectedOrder.voidedAt" class="is-danger" type="button" :disabled="isVoiding" @click="handleVoidOrder(selectedOrder)"><Ban :size="17" />Void order</button></footer>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* Every colour this page paints with is a variable, declared light here and
   re-declared once for dark at the bottom of the file. The page used to hard-
   code its light values inline — white card fills, a pale table header, pastel
   status chips — so dark mode kept most of them and the page came back as
   light panels floating on a black app. */
.orders-page{--orders-green:#08783f;--orders-green-ink:#08783f;--orders-on-green:#fff;--orders-green-soft:#e8f5ee;--orders-surface:rgba(255,255,255,.88);--orders-border:rgba(68,83,104,.12);--orders-card-border:rgba(255,255,255,.75);--orders-shadow:0 7px 24px rgba(63,80,103,.045);--orders-control:#fff;--orders-control-hover:#f3f5f6;--orders-ghost-hover:rgba(8,120,63,.08);--orders-field:rgba(245,247,249,.92);--orders-select:rgba(255,255,255,.9);--orders-table-bg:rgba(255,255,255,.72);--orders-thead:linear-gradient(180deg,rgba(243,245,247,.92),rgba(237,239,242,.84));--orders-thead-ink:#45506a;--orders-row-hover:rgba(8,120,63,.035);--orders-glyph:#3f536c;--orders-glyph-strong:#324158;--orders-menu-shadow:0 12px 30px rgba(42,55,72,.14);--orders-tile:linear-gradient(145deg,rgba(247,249,251,.96),rgba(241,244,247,.84));--orders-track:#e5e8ea;--orders-scrim:rgba(15,23,42,.24);--orders-drawer-shadow:-16px 0 46px rgba(30,41,59,.15);--orders-dot-blue:#2483e7;--orders-dot-amber:#ffb000;--chip-delivery:#0b7642;--chip-delivery-bg:#e7f4ec;--chip-pickup:#d97805;--chip-pickup-bg:#fff0df;--chip-dine:#6443cb;--chip-dine-bg:#f0eafd;--chip-takeaway:#3d608c;--chip-takeaway-bg:#eaf0f8;--chip-preparing:#2476c9;--chip-preparing-bg:#e8f2fd;--chip-pending:#d68a00;--chip-pending-bg:#fff5d9;--chip-done:#087843;--chip-done-bg:#e4f3eb;--chip-voided:#b93b33;--chip-voided-bg:#fde8e7;display:grid;gap:16px;padding:10px 0 26px}.orders-hero{display:flex;align-items:end;justify-content:space-between;gap:24px}.orders-hero h1,.orders-hero p,.orders-history h2,.orders-history p,.insight-card h2,.delivery-summary p,.delivery-summary small{margin:0}.orders-hero h1{font-size:29px;line-height:36px;letter-spacing:-.025em}.orders-hero p{color:var(--text-secondary);font-size:15px}.orders-range{display:flex;align-items:center;justify-content:flex-end;gap:14px}.orders-range>span{color:var(--text-secondary);font-size:12px;white-space:nowrap}
.orders-kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.orders-kpi{min-height:104px;display:flex;align-items:center;gap:18px;padding:18px 20px;border:1px solid var(--orders-card-border);border-radius:14px;background:var(--orders-surface);box-shadow:var(--orders-shadow)}.orders-kpi__icon{width:56px;height:56px;display:grid;flex:none;place-items:center;border-radius:14px;background:var(--orders-green-soft);color:var(--orders-green-ink)}.orders-kpi>div{min-width:0}.orders-kpi p{margin:0 0 2px;color:var(--text-secondary);font-size:12px}.orders-kpi small{color:var(--text-secondary);font-size:12px}.orders-kpi__value{display:flex;align-items:baseline;gap:14px;white-space:nowrap}.orders-kpi__value strong{font-size:23px;line-height:29px;letter-spacing:-.025em}.orders-kpi__value span{color:var(--orders-green-ink);font-size:13px;font-weight:700}.orders-kpi__value span.is-down{color:var(--danger)}.orders-kpi__value .orders-kpi__muted{color:var(--text-tertiary);font-weight:500}
.orders-history,.insight-card{border:1px solid var(--orders-card-border);background:var(--orders-surface);box-shadow:var(--orders-shadow)}.orders-history{padding:16px 18px 12px;border-radius:16px}.orders-history__head,.insight-card>header{display:flex;align-items:center;justify-content:space-between;gap:16px}.orders-history__head h2,.insight-card h2{font-size:17px;line-height:23px;letter-spacing:-.012em}.orders-history__head p{color:var(--text-secondary);font-size:12px}.orders-export{display:inline-flex;align-items:center;gap:8px;min-height:42px;padding:0 16px;border:1px solid var(--orders-border);border-radius:10px;background:var(--orders-control);color:var(--text-primary);font-size:13px;font-weight:600}.orders-export:hover{border-color:color-mix(in srgb,var(--orders-green-ink) 35%,transparent);color:var(--orders-green-ink)}
.orders-filters{display:grid;grid-template-columns:minmax(300px,1fr) 180px 194px 190px;gap:10px;margin-top:14px}.orders-search{min-height:40px;display:flex;align-items:center;gap:10px;padding:0 12px;border:1px solid var(--orders-border);border-radius:10px;background:var(--orders-field);color:var(--text-secondary)}.orders-search input{width:100%;min-width:0;border:0;outline:0;background:transparent;color:var(--text-primary);font-size:13px}.orders-search button{width:28px;height:28px;display:grid;flex:none;place-items:center;border:0;border-radius:8px;background:transparent;color:var(--text-tertiary)}.orders-select{min-width:0;width:100%}.orders-select :deep(.acselect__trigger){height:40px;min-height:40px;border:1px solid var(--orders-border);border-radius:10px;background:var(--orders-select);font-size:13px}
.orders-table-wrap{margin-top:12px;overflow:visible}.orders-table{min-width:940px;overflow:visible;border:1px solid var(--orders-border);border-radius:10px;background:var(--orders-table-bg)}.orders-table__header,.orders-table__row{display:grid;grid-template-columns:125px minmax(160px,1.25fr) 158px 145px 135px 170px minmax(210px,1fr) 42px;align-items:center}.orders-table__header{min-height:35px;padding:0 10px;border-radius:9px 9px 0 0;background:var(--orders-thead);color:var(--orders-thead-ink);font-size:11px;font-weight:600}.orders-table__row{position:relative;width:100%;min-height:37px;padding:0 10px;border:0;border-top:1px solid var(--orders-border);background:transparent;color:var(--text-primary);text-align:left;font-size:12px}.orders-table__row:hover{background:var(--orders-row-hover)}.orders-table__row.is-voided{opacity:.55}.orders-table__row>span,.orders-table__row>strong,.orders-table__row>time{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding-right:10px}.orders-table__row>strong{font-weight:700}.orders-table__row>time{color:var(--text-secondary)}
.order-mode,.order-status,.order-payment{display:inline-flex;align-items:center;justify-self:start;gap:6px}.order-mode{width:max-content;padding:3px 9px 3px 7px!important;border-radius:999px;font-weight:500}.order-mode--delivery{background:var(--chip-delivery-bg);color:var(--chip-delivery)}.order-mode--pickup{background:var(--chip-pickup-bg);color:var(--chip-pickup)}.order-mode--dine_in{background:var(--chip-dine-bg);color:var(--chip-dine)}.order-mode--takeaway{background:var(--chip-takeaway-bg);color:var(--chip-takeaway)}.order-payment svg{color:var(--orders-glyph)}.order-status{width:max-content;padding:3px 9px 3px 7px!important;border-radius:999px;font-weight:600}.order-status--preparing{background:var(--chip-preparing-bg);color:var(--chip-preparing)}.order-status--pending{background:var(--chip-pending-bg);color:var(--chip-pending)}.order-status--ready,.order-status--completed{background:var(--chip-done-bg);color:var(--chip-done)}.order-status--voided{background:var(--chip-voided-bg);color:var(--chip-voided)}
.order-actions{position:relative;overflow:visible!important;padding-right:0!important;justify-self:end}.order-actions__trigger{width:32px;height:32px;display:grid;place-items:center;border:0;border-radius:8px;background:transparent;color:var(--orders-glyph-strong)}.order-actions__trigger:hover{background:var(--orders-ghost-hover)}.order-actions__menu{position:absolute;z-index:20;top:calc(100% - 2px);right:0;width:168px;padding:6px;border:1px solid var(--orders-border);border-radius:11px;background:var(--orders-control);box-shadow:var(--orders-menu-shadow)}.order-actions__menu button{width:100%;min-height:34px;display:flex;align-items:center;gap:8px;padding:0 9px;border:0;border-radius:7px;background:transparent;color:var(--text-primary);font-size:12px;text-align:left}.order-actions__menu button:hover{background:var(--orders-control-hover)}.order-actions__menu button.is-danger{color:var(--danger)}
.orders-empty{min-height:220px;display:grid;place-items:center;align-content:center;gap:8px;color:var(--text-tertiary);font-size:13px}.orders-empty strong{color:var(--text-primary)}.orders-pagination{min-height:50px;display:flex;align-items:end;justify-content:space-between;gap:20px;color:var(--text-secondary);font-size:12px}.orders-pagination nav{display:flex;align-items:center;gap:7px}.orders-pagination nav button{min-width:34px;height:34px;display:grid;place-items:center;padding:0 9px;border:1px solid var(--orders-border);border-radius:9px;background:var(--orders-control);color:var(--text-primary);font-size:12px}.orders-pagination nav button:hover:not(:disabled){border-color:var(--orders-green-ink);color:var(--orders-green-ink)}.orders-pagination nav button.is-active{border-color:var(--orders-green);background:var(--orders-green);color:var(--orders-on-green)}.orders-pagination nav button:disabled{opacity:.38;cursor:default}
.orders-insights{display:grid;grid-template-columns:1.05fr 1.05fr 1.1fr;gap:14px}.insight-card{min-width:0;padding:16px 18px 18px;border-radius:16px}.insight-card>header>span,.insight-card>header a{display:inline-flex;align-items:center;gap:3px;color:var(--text-secondary);font-size:11px;text-decoration:none}.insight-card>header a{color:var(--orders-green-ink);font-weight:600}.delivery-summary__items{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px;margin-top:14px}.delivery-summary__item{min-height:94px;padding:14px 12px;border-radius:11px;background:var(--orders-tile)}.delivery-summary__item>div{display:flex;align-items:center;gap:10px}.delivery-summary__item strong{font-size:20px;line-height:24px}.delivery-summary__item p{margin-top:7px;font-size:12px;font-weight:600}.delivery-summary__item small{color:var(--text-secondary);font-size:10px}.queue-dot{width:13px;height:13px;border-radius:999px}.queue-dot--blue{background:var(--orders-dot-blue)}.queue-dot--amber{background:var(--orders-dot-amber)}.queue-dot--green{background:var(--orders-green)}
.breakdown-list{display:grid;gap:11px;margin-top:14px}.breakdown-row{display:grid;grid-template-columns:105px minmax(0,1fr);align-items:center;gap:2px 12px}.breakdown-row__label,.type-row__label{display:flex;align-items:center;gap:9px;font-size:12px}.breakdown-row__label svg,.type-row__label svg{color:var(--orders-green-ink)}.breakdown-row__metric{display:grid;grid-template-columns:1fr 40px;align-items:center;gap:10px;text-align:right}.breakdown-row__metric strong{font-size:12px}.breakdown-row__metric small,.type-row small{color:var(--text-secondary);font-size:11px}.breakdown-row__track{grid-column:2;height:5px;margin-right:52px;border-radius:999px;background:var(--orders-track);overflow:hidden}.breakdown-row__track i,.type-row__track i{display:block;height:100%;min-width:3px;border-radius:inherit;background:var(--orders-green)}.type-row{display:grid;grid-template-columns:125px 28px 38px minmax(70px,1fr);align-items:center;gap:10px}.type-row>strong{font-size:12px;font-weight:500}.type-row__track{height:10px;border-radius:999px;background:var(--orders-track);overflow:hidden}.type-row:nth-child(2) .type-row__track i{opacity:.72}.type-row:nth-child(3) .type-row__track i,.type-row:nth-child(4) .type-row__track i{opacity:.52}.insight-empty{margin:20px 0;color:var(--text-secondary);font-size:12px}
.order-drawer-backdrop{position:fixed;inset:0;z-index:80;display:grid;justify-items:end;background:var(--orders-scrim);backdrop-filter:blur(2px)}.order-drawer{width:min(440px,94vw);height:100%;display:flex;flex-direction:column;gap:22px;padding:24px;overflow-y:auto;background:var(--bg-elevated);box-shadow:var(--orders-drawer-shadow)}.order-drawer__head{display:flex;align-items:center;justify-content:space-between}.order-drawer__head p{margin:0;color:var(--text-secondary);font-size:12px}.order-drawer__head h2{margin:2px 0 0;font-size:25px}.order-drawer__head button{width:38px;height:38px;display:grid;place-items:center;border:1px solid var(--separator);border-radius:10px;background:transparent;color:var(--text-primary)}.order-drawer__meta{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;padding:16px;border-radius:14px;background:var(--fill)}.order-drawer__meta div{display:grid;gap:3px}.order-drawer__meta span{color:var(--text-secondary);font-size:11px}.order-drawer__meta strong{font-size:13px}.order-drawer__lines{display:grid;gap:14px;padding:18px 0;border-block:1px solid var(--separator)}.order-drawer__lines>div,.order-drawer__totals>div{display:flex;align-items:center;justify-content:space-between;gap:16px}.order-drawer__lines span{display:grid;gap:2px}.order-drawer__lines small{color:var(--text-secondary)}.order-drawer__totals{display:grid;gap:12px}.order-drawer__totals span{color:var(--text-secondary)}.order-drawer__totals .is-grand{margin-top:4px;padding-top:16px;border-top:1px solid var(--separator);color:var(--text-primary);font-size:18px}.order-drawer footer{display:flex;gap:10px;margin-top:auto}.order-drawer footer button{min-height:42px;display:inline-flex;flex:1;align-items:center;justify-content:center;gap:8px;border:1px solid var(--separator);border-radius:10px;background:transparent;color:var(--text-primary);font-size:13px;font-weight:600}.order-drawer footer button:first-child{border-color:var(--orders-green);background:var(--orders-green);color:var(--orders-on-green)}.order-drawer footer button.is-danger{color:var(--danger)}
@media(max-width:1380px){.orders-filters{grid-template-columns:minmax(260px,1fr) repeat(3,minmax(145px,.34fr))}.orders-table-wrap{overflow-x:auto;padding-bottom:2px}.orders-table{overflow:hidden}.orders-kpi{gap:13px;padding-inline:15px}.orders-kpi__icon{width:48px;height:48px}.orders-kpi__value strong{font-size:20px}}
@media(max-width:1080px){.orders-kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.orders-insights{grid-template-columns:repeat(2,minmax(0,1fr))}.delivery-summary{grid-column:1/-1}}
@media(max-width:760px){.orders-page{gap:14px;padding-top:2px}.orders-hero{align-items:start;flex-direction:column;gap:14px}.orders-hero h1{font-size:25px}.orders-hero p{font-size:13px}.orders-range{width:100%;align-items:start;flex-direction:column;gap:7px}.orders-range :deep(.range-selector){width:100%;display:grid;grid-template-columns:repeat(4,minmax(0,1fr))}.orders-range :deep(.range-btn){padding-inline:8px;font-size:12px}.orders-kpis{gap:10px}.orders-kpi{min-height:96px;gap:10px;padding:13px}.orders-kpi__icon{width:38px;height:38px;border-radius:11px}.orders-kpi__icon :deep(svg){width:20px;height:20px}.orders-kpi__value{gap:5px;flex-wrap:wrap}.orders-kpi__value strong{font-size:17px;line-height:21px}.orders-kpi__value span{font-size:10px}.orders-kpi p,.orders-kpi small{font-size:10px}.orders-history{padding:14px 12px 10px}.orders-history__head{align-items:start}.orders-export{min-height:38px;padding-inline:12px}.orders-filters{grid-template-columns:repeat(2,minmax(0,1fr))}.orders-search{grid-column:1/-1}.orders-filters .orders-select:last-child{grid-column:1/-1}.orders-table-wrap{overflow:visible}.orders-table{min-width:0;border:0;background:transparent;display:grid;gap:8px}.orders-table__header{display:none}.orders-table__row{min-height:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr)) 32px;gap:11px 14px;padding:14px;border:1px solid var(--orders-border);border-radius:12px;background:var(--orders-table-bg)}.orders-table__row>span,.orders-table__row>strong,.orders-table__row>time{display:grid;gap:3px;padding-right:0;white-space:normal}.orders-table__row>[data-label]::before{content:attr(data-label);color:var(--text-tertiary);font-size:9px;font-weight:500;text-transform:uppercase;letter-spacing:.04em}.orders-table__row>strong:first-child{grid-column:1;grid-row:1}.orders-table__row>span:nth-child(2){grid-column:2;grid-row:1}.orders-table__row>.order-mode,.orders-table__row>.order-status{display:inline-flex;align-self:end}.orders-table__row>.order-actions{grid-column:3;grid-row:1}.order-actions__menu{top:100%}.orders-pagination{align-items:start;flex-direction:column}.orders-pagination nav{width:100%;overflow-x:auto;padding-bottom:4px}.orders-insights{grid-template-columns:minmax(0,1fr)}.delivery-summary{grid-column:auto}}
@media(max-width:430px){.orders-kpis{grid-template-columns:minmax(0,1fr)}.orders-kpi{min-height:82px}.delivery-summary__items{grid-template-columns:minmax(0,1fr)}.delivery-summary__item{min-height:78px}.orders-filters{grid-template-columns:minmax(0,1fr)}.orders-search,.orders-filters .orders-select:last-child{grid-column:auto}.order-drawer__meta{grid-template-columns:minmax(0,1fr)}.order-drawer footer{flex-direction:column}}
/*
 * The dark palette. It has to be written twice because dark is reached three
 * different ways, and a selector list cannot straddle a media query:
 *
 *   - Appearance = Dark stamps [data-theme='dark'] on <html>;
 *   - Appearance = System (the default) stamps nothing at all and leans on
 *     prefers-color-scheme, the same way tokens.css does;
 *   - nocturne/reserve/harbor/mono are single fixed dark moods, dark whatever
 *     Appearance says — so they are matched outside the media query, and
 *     without the :not([data-theme='light']) guard the other two need.
 *
 * These ancestors are written plainly, NOT as :global(...). Vue's scoped
 * transform keeps only what is inside :global() and throws the rest of the
 * selector away, so `:global([data-theme='dark']) .orders-page` compiled down
 * to `[data-theme='dark']` — a rule on <html>. The variables it set were then
 * beaten by the light ones declared directly on .orders-page, which is why
 * the page stayed light in dark mode however Appearance was set. Written
 * plainly, the scope attribute lands on .orders-page where it belongs.
 *
 * Keep the two blocks in sync.
 */
[data-theme='dark'] .orders-page,[data-color-theme='nocturne'] .orders-page,[data-color-theme='reserve'] .orders-page,[data-color-theme='harbor'] .orders-page,[data-color-theme='mono'] .orders-page{--orders-green:#3fbd77;--orders-green-ink:#6fdc9c;--orders-on-green:#062b16;--orders-green-soft:rgba(63,189,119,.16);--orders-surface:color-mix(in srgb,var(--bg-surface) 94%,transparent);--orders-border:var(--separator);--orders-card-border:var(--separator);--orders-shadow:0 7px 24px rgba(0,0,0,.34);--orders-control:var(--bg-elevated);--orders-control-hover:rgba(255,255,255,.08);--orders-ghost-hover:rgba(255,255,255,.1);--orders-field:rgba(255,255,255,.06);--orders-select:var(--bg-elevated);--orders-table-bg:var(--bg-elevated);--orders-thead:linear-gradient(180deg,rgba(255,255,255,.07),rgba(255,255,255,.04));--orders-thead-ink:var(--text-secondary);--orders-row-hover:rgba(255,255,255,.05);--orders-glyph:var(--text-secondary);--orders-glyph-strong:var(--text-secondary);--orders-menu-shadow:0 12px 30px rgba(0,0,0,.45);--orders-tile:rgba(255,255,255,.05);--orders-track:rgba(255,255,255,.12);--orders-scrim:rgba(0,0,0,.55);--orders-drawer-shadow:-16px 0 46px rgba(0,0,0,.5);--orders-dot-blue:#5aa8f2;--orders-dot-amber:#ffc44d;--chip-delivery:#5fd699;--chip-delivery-bg:rgba(95,214,153,.16);--chip-pickup:#f0a94a;--chip-pickup-bg:rgba(240,169,74,.16);--chip-dine:#c0aaff;--chip-dine-bg:rgba(192,170,255,.18);--chip-takeaway:#9cc0e8;--chip-takeaway-bg:rgba(156,192,232,.16);--chip-preparing:#6bb2f0;--chip-preparing-bg:rgba(107,178,240,.16);--chip-pending:#edb64a;--chip-pending-bg:rgba(237,182,74,.16);--chip-done:#5fd699;--chip-done-bg:rgba(95,214,153,.16);--chip-voided:#ff9d96;--chip-voided-bg:rgba(255,157,150,.18)}
@media(prefers-color-scheme:dark){html:not([data-theme='light']) .orders-page{--orders-green:#3fbd77;--orders-green-ink:#6fdc9c;--orders-on-green:#062b16;--orders-green-soft:rgba(63,189,119,.16);--orders-surface:color-mix(in srgb,var(--bg-surface) 94%,transparent);--orders-border:var(--separator);--orders-card-border:var(--separator);--orders-shadow:0 7px 24px rgba(0,0,0,.34);--orders-control:var(--bg-elevated);--orders-control-hover:rgba(255,255,255,.08);--orders-ghost-hover:rgba(255,255,255,.1);--orders-field:rgba(255,255,255,.06);--orders-select:var(--bg-elevated);--orders-table-bg:var(--bg-elevated);--orders-thead:linear-gradient(180deg,rgba(255,255,255,.07),rgba(255,255,255,.04));--orders-thead-ink:var(--text-secondary);--orders-row-hover:rgba(255,255,255,.05);--orders-glyph:var(--text-secondary);--orders-glyph-strong:var(--text-secondary);--orders-menu-shadow:0 12px 30px rgba(0,0,0,.45);--orders-tile:rgba(255,255,255,.05);--orders-track:rgba(255,255,255,.12);--orders-scrim:rgba(0,0,0,.55);--orders-drawer-shadow:-16px 0 46px rgba(0,0,0,.5);--orders-dot-blue:#5aa8f2;--orders-dot-amber:#ffc44d;--chip-delivery:#5fd699;--chip-delivery-bg:rgba(95,214,153,.16);--chip-pickup:#f0a94a;--chip-pickup-bg:rgba(240,169,74,.16);--chip-dine:#c0aaff;--chip-dine-bg:rgba(192,170,255,.18);--chip-takeaway:#9cc0e8;--chip-takeaway-bg:rgba(156,192,232,.16);--chip-preparing:#6bb2f0;--chip-preparing-bg:rgba(107,178,240,.16);--chip-pending:#edb64a;--chip-pending-bg:rgba(237,182,74,.16);--chip-done:#5fd699;--chip-done-bg:rgba(95,214,153,.16);--chip-voided:#ff9d96;--chip-voided-bg:rgba(255,157,150,.18)}}
.orders-page{gap:15px;padding:2px 0 0}.orders-table__row{cursor:pointer}
</style>
