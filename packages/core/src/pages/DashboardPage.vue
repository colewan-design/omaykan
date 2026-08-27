<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ChevronRight, MessageSquare, Phone, RefreshCw } from '@lucide/vue'
import {
  deliveryStageLabel,
  formatCurrency,
  guestCustomerName,
  nextDeliveryStage,
  nextOrderStatus,
  orderStatusLabel,
  type OrderSummary,
} from '@pos/shared/index'
import { usePosStore } from '@pos/core/stores/pos'
import { useAuthStore } from '@pos/core/stores/auth'
import { getPosRepository } from '@pos/core/services/runtime'
import { subscribeToStoreOrders } from '@pos/core/realtime/orderChannel'
import MetricCard from '@pos/core/components/MetricCard.vue'
import ChartCard from '@pos/core/components/ChartCard.vue'
import RangeSelector, { type Range } from '@pos/core/components/RangeSelector.vue'
import SettleOnlinePaymentSheet from '@pos/core/components/SettleOnlinePaymentSheet.vue'

const store = usePosStore()
const auth = useAuthStore()
const darkModeEnabled = ref(false)
let themeMediaQuery: MediaQueryList | null = null
let themeObserver: MutationObserver | null = null
// Live order feed teardown (Reverb). Null until subscribed; a no-op when
// realtime is disabled or this device has no backend store session.
let stopOrderFeed: (() => void) | null = null

// Same resolution as apps/web/src/main.ts: same-origin in production, an env
// override for split-host dev.
const apiBase = (import.meta.env.VITE_API_BASE ?? import.meta.env.VITE_ONLINE_ORDER_API_BASE ?? '') as string

function syncDarkMode() {
  if (typeof window === 'undefined') return
  const explicitTheme = document.documentElement.dataset.theme
  darkModeEnabled.value = explicitTheme === 'dark'
    || (!explicitTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)
}

onMounted(async () => {
  if (!store.isReady) {
    await store.initialize()
  }
  // Storefront orders arrive server-side while this screen is open, so pull a
  // fresh set on entry rather than trusting whatever initialize() cached.
  void refreshOrders()
  void startOrderFeed()

  syncDarkMode()
  if (typeof window !== 'undefined') {
    themeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    themeMediaQuery.addEventListener('change', syncDarkMode)
    themeObserver = new MutationObserver(syncDarkMode)
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme'],
    })
  }
})

onBeforeUnmount(() => {
  themeMediaQuery?.removeEventListener('change', syncDarkMode)
  themeObserver?.disconnect()
  stopOrderFeed?.()
  stopOrderFeed = null
})

// Push, not poll: while the dashboard is open, live order events over Reverb
// refresh the in-flight/delivery lists. Falls back silently to the manual
// refresh when realtime is off or this device isn't paired to a backend store.
async function startOrderFeed() {
  try {
    const storeId = await getPosRepository().getSyncStoreId()
    const token = auth.session?.authToken
    if (!storeId || !token) return
    stopOrderFeed = subscribeToStoreOrders({
      apiBaseUrl: apiBase,
      storeId,
      token,
      onOrderEvent: () => { void refreshOrders() },
    })
  } catch {
    // No live feed is fine — the page still refreshes on entry and on demand.
  }
}

// ── Operations ─────────────────────────────────────────────────────────────
// The top half of this page is the day's work: orders still moving, deliveries
// with nobody carrying them, stock about to run out, and the state of the
// store itself. The analytics below answer "how did we do"; this answers
// "what needs me now".

const opsError = ref('')
const busyOrderId = ref('')
const refreshing = ref(false)
const settlingOrder = ref<OrderSummary | null>(null)

/** Per-order rider drafts, keyed by order id so two cards can't share a name. */
const riderDrafts = reactive<Record<string, { name: string; phone: string }>>({})

function riderDraft(orderId: string) {
  if (!riderDrafts[orderId]) {
    riderDrafts[orderId] = { name: '', phone: '' }
  }
  return riderDrafts[orderId]
}

function isOnlineOrder(order: OrderSummary) {
  return (order.channel ?? 'in_person') === 'online'
}

function isUnpaid(order: OrderSummary) {
  return (order.paymentStatus ?? 'paid') === 'unpaid'
}

/**
 * Anything not finished with: still being made, or made but not paid for.
 * Online and register orders together, because the counter works one queue.
 */
const ordersInFlight = computed(() => {
  const online = store.onlineOrders.filter(
    (order) => !order.voidedAt && (order.status !== 'served' || isUnpaid(order)),
  )
  const inPerson = store.orders.filter((order) => !order.voidedAt && order.status !== 'served')
  return [...online, ...inPerson]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 8)
})

/** Deliveries still on the shop's hands — anything not yet dropped off. */
const activeDeliveries = computed(() =>
  store.onlineOrders
    .filter(
      (order) =>
        !order.voidedAt &&
        order.fulfillmentMethod === 'delivery' &&
        (order.deliveryStage ?? 'pending') !== 'delivered',
    )
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
)

const awaitingRider = computed(() => activeDeliveries.value.filter((order) => !order.riderName))

const unpaidOnlineOrders = computed(() =>
  store.onlineOrders.filter((order) => !order.voidedAt && isUnpaid(order)),
)

/** Out of stock first — a shelf at zero is losing sales right now. */
const stockAlerts = computed(() =>
  [...store.outOfStockProducts, ...store.lowStockProducts].slice(0, 6),
)

const storeFacts = computed(() => [
  { label: 'Business', value: store.settings.businessName || 'Unnamed store' },
  { label: 'Store code', value: store.settings.pairingCode || 'Not issued' },
  { label: 'Sync', value: store.settings.syncMode === 'online-sync' ? 'Online' : 'Local only' },
  {
    label: 'Shift',
    value: store.activeShift ? `Open · ${formatCurrency(store.activeShift.expectedCashCents)} expected` : 'Closed',
  },
])

async function runOrderAction(orderId: string, action: () => Promise<unknown>) {
  if (busyOrderId.value) return
  busyOrderId.value = orderId
  opsError.value = ''
  try {
    await action()
  } catch (error) {
    // These all cross the network — a storefront order isn't in the offline
    // outbox — so say so plainly instead of leaving a button that did nothing.
    opsError.value = error instanceof Error ? error.message : 'That did not go through. Try again.'
  } finally {
    busyOrderId.value = ''
  }
}

async function refreshOrders() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await store.refreshOrders()
    await store.refreshOnlineOrders()
  } catch {
    // Both loaders already degrade safely to local state or an empty list.
  } finally {
    refreshing.value = false
  }
}

function advanceStatus(order: OrderSummary) {
  const next = nextOrderStatus(order.status)
  return runOrderAction(order.id, () =>
    isOnlineOrder(order)
      ? store.updateOnlineOrderStatus(order.id, next)
      : store.updateOrderStatus(order.id, next),
  )
}

function notifyRider(order: OrderSummary) {
  const draft = riderDraft(order.id)
  const name = draft.name.trim()
  if (!name) {
    opsError.value = 'Give the rider a name first — the customer sees it on their tracking page.'
    return Promise.resolve()
  }

  return runOrderAction(order.id, async () => {
    await store.notifyRider(order.id, { riderName: name, riderPhone: draft.phone.trim() || null })
    delete riderDrafts[order.id]
  })
}

function advanceDelivery(order: OrderSummary) {
  const next = nextDeliveryStage(order.deliveryStage ?? 'pending')
  if (!next) return Promise.resolve()
  return runOrderAction(order.id, () => store.advanceDelivery(order.id, next))
}

function restock(productId: string) {
  return runOrderAction(productId, () => store.restockProduct(productId, 10))
}

/** Digits only — a saved number like "0917 000 0000" won't dial as typed. */
function telHref(phone: string) {
  return `tel:${phone.replace(/[^\d+]/g, '')}`
}

function smsHref(order: OrderSummary) {
  const phone = (order.riderPhone ?? '').replace(/[^\d+]/g, '')
  const body = `Omaykan order ${order.ticketNumber} for ${order.deliveryAddress ?? 'pickup at the shop'}`
  return `sms:${phone}?body=${encodeURIComponent(body)}`
}

const range = ref<Range>('month')
const now = new Date()

type Channel = 'Dine-in' | 'Takeaway' | 'Online Pickup' | 'Online Delivery'

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function getMonday(d: Date) {
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  return new Date(startOfDay(d).getTime() + diff * 86400000)
}

function customerNameFor(order: OrderSummary) {
  return order.customerName || guestCustomerName
}

// A missing `channel` means an older/local in-person order (see the field's
// doc comment in shared/index.ts) — online orders always set it explicitly.
function channelFor(order: OrderSummary): Channel {
  if ((order.channel ?? 'in_person') === 'online') {
    return order.fulfillmentMethod === 'delivery' ? 'Online Delivery' : 'Online Pickup'
  }
  return order.orderType === 'dine_in' ? 'Dine-in' : 'Takeaway'
}

// A missing `paymentStatus` means 'paid' (same fallback as shared/index.ts).
function paymentStatusFor(order: OrderSummary) {
  return (order.paymentStatus ?? 'paid') === 'paid' ? 'Completed' : 'Processing'
}

interface Bounds {
  start: Date
  end: Date
  prevStart: Date
  prevEnd: Date
}

function getBounds(value: Range): Bounds {
  const today = startOfDay(now)
  switch (value) {
    case 'today': {
      const start = today
      const end = new Date(today.getTime() + 86400000)
      return { start, end, prevStart: new Date(start.getTime() - 86400000), prevEnd: start }
    }
    case 'week': {
      const start = getMonday(now)
      const end = new Date(start.getTime() + 7 * 86400000)
      return { start, end, prevStart: new Date(start.getTime() - 7 * 86400000), prevEnd: start }
    }
    case 'month': {
      const start = new Date(today.getFullYear(), today.getMonth(), 1)
      const end = new Date(today.getFullYear(), today.getMonth() + 1, 1)
      const prevStart = new Date(today.getFullYear(), today.getMonth() - 1, 1)
      return { start, end, prevStart, prevEnd: start }
    }
    case 'all': {
      const epoch = new Date(0)
      const far = new Date(8640000000000000)
      return { start: epoch, end: far, prevStart: epoch, prevEnd: epoch }
    }
  }
}

function inBounds(order: OrderSummary, start: Date, end: Date) {
  const createdAt = new Date(order.createdAt)
  return createdAt >= start && createdAt < end
}

const bounds = computed(() => getBounds(range.value))
const activeOrders = computed(() => store.orders.filter((order) => !order.voidedAt))
const periodOrders = computed(() => activeOrders.value.filter((order) => inBounds(order, bounds.value.start, bounds.value.end)))
const priorOrders = computed(() =>
  range.value === 'all'
    ? []
    : activeOrders.value.filter((order) => inBounds(order, bounds.value.prevStart, bounds.value.prevEnd)),
)

function delta(current: number, previous: number) {
  if (range.value === 'all' || previous === 0) {
    return null
  }

  const percentage = ((current - previous) / previous) * 100
  return {
    value: `${Math.abs(percentage).toFixed(1)}%`,
    positive: percentage >= 0,
  }
}

const totalRevenue = computed(() => periodOrders.value.reduce((sum, order) => sum + order.totalCents, 0))
const previousRevenue = computed(() => priorOrders.value.reduce((sum, order) => sum + order.totalCents, 0))
const transactions = computed(() => periodOrders.value.length)
const previousTransactions = computed(() => priorOrders.value.length)
const averageOrderValue = computed(() => transactions.value > 0 ? Math.round(totalRevenue.value / transactions.value) : 0)
const previousAverageOrderValue = computed(() =>
  previousTransactions.value > 0 ? Math.round(previousRevenue.value / previousTransactions.value) : 0,
)

function repeatCustomerRate(orders: OrderSummary[]) {
  const namedOrders = orders.filter((order) => customerNameFor(order) !== guestCustomerName)
  if (namedOrders.length === 0) {
    return 0
  }

  const counts = new Map<string, number>()
  for (const order of namedOrders) {
    const customer = customerNameFor(order)
    counts.set(customer, (counts.get(customer) ?? 0) + 1)
  }

  let repeatOrders = 0
  for (const order of namedOrders) {
    if ((counts.get(customerNameFor(order)) ?? 0) > 1) {
      repeatOrders += 1
    }
  }

  return (repeatOrders / namedOrders.length) * 100
}

const returnCustomers = computed(() => repeatCustomerRate(periodOrders.value))
const previousReturnCustomers = computed(() => repeatCustomerRate(priorOrders.value))

const rangeCaption = computed(() => {
  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
  if (range.value === 'today') {
    return formatter.format(now)
  }

  const endForCaption = new Date(bounds.value.end.getTime() - 86400000)
  return `${formatter.format(bounds.value.start)} - ${formatter.format(endForCaption)}`
})

const previousRangeCaption = computed(() => {
  if (range.value === 'all') {
    return ''
  }

  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric' })
  if (range.value === 'today') {
    return `vs ${formatter.format(bounds.value.prevStart)}`
  }

  const endForCaption = new Date(bounds.value.prevEnd.getTime() - 86400000)
  return `vs ${formatter.format(bounds.value.prevStart)} - ${formatter.format(endForCaption)}`
})

const storeName = computed(() => store.settings.businessName || 'Unnamed store')
const storeCode = computed(() => store.settings.pairingCode || 'Not issued')
const syncSummary = computed(() => store.settings.syncMode === 'online-sync' ? 'Online' : 'Local only')
const shiftSummary = computed(() =>
  store.activeShift ? `Open - ${formatCurrency(store.activeShift.expectedCashCents)} expected` : 'Closed',
)

const salesSeries = computed(() => {
  const days: { key: string; label: string; totalCents: number }[] = []
  const cursor = new Date(bounds.value.start)
  const dayFormat = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric' })

  while (cursor < bounds.value.end && days.length < 31) {
    days.push({
      key: cursor.toISOString(),
      label: dayFormat.format(cursor),
      totalCents: 0,
    })
    cursor.setDate(cursor.getDate() + 1)
  }

  if (range.value === 'all') {
    const grouped = new Map<string, number>()
    for (const order of periodOrders.value) {
      const key = order.createdAt.slice(0, 10)
      grouped.set(key, (grouped.get(key) ?? 0) + order.totalCents)
    }
    return Array.from(grouped.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .slice(-14)
      .map(([key, total]) => ({
        key,
        label: dayFormat.format(new Date(key)),
        totalCents: total,
      }))
  }

  for (const order of periodOrders.value) {
    const key = order.createdAt.slice(0, 10)
    const match = days.find((entry) => entry.key.slice(0, 10) === key)
    if (match) {
      match.totalCents += order.totalCents
    }
  }

  return days
})

const maxSalesPoint = computed(() => Math.max(...salesSeries.value.map((point) => point.totalCents), 1))

function niceCeil(value: number) {
  if (value <= 0) {
    return 1
  }

  const exponent = Math.floor(Math.log10(value))
  const fraction = value / 10 ** exponent
  const niceFraction = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10
  return niceFraction * 10 ** exponent
}

const salesAxisMax = computed(() => niceCeil(maxSalesPoint.value))

function formatAxisValue(cents: number) {
  const pesos = cents / 100
  if (pesos >= 1000) {
    return `₱${(pesos / 1000).toFixed(pesos % 1000 === 0 ? 0 : 1)}k`
  }
  return `₱${Math.round(pesos)}`
}

const salesYAxisTicks = computed(() =>
  [4, 3, 2, 1, 0].map((step) => formatAxisValue((salesAxisMax.value * step) / 4)),
)

const peakSalesIndex = computed(() => {
  let peak = 0
  for (let i = 1; i < salesSeries.value.length; i += 1) {
    if (salesSeries.value[i].totalCents > salesSeries.value[peak].totalCents) {
      peak = i
    }
  }
  return salesSeries.value[peak]?.totalCents > 0 ? peak : -1
})

const salesLabelStep = computed(() => Math.max(1, Math.ceil(salesSeries.value.length / 8)))

const channelBreakdown = computed(() => {
  const totals = new Map<Channel, number>([
    ['Dine-in', 0],
    ['Takeaway', 0],
    ['Online Pickup', 0],
    ['Online Delivery', 0],
  ])

  for (const order of periodOrders.value) {
    const channel = channelFor(order)
    totals.set(channel, (totals.get(channel) ?? 0) + order.totalCents)
  }

  const total = Array.from(totals.values()).reduce((sum, value) => sum + value, 0)
  const colors: Record<Channel, string> = {
    'Dine-in': 'var(--success)',
    'Takeaway': 'var(--accent)',
    'Online Pickup': 'var(--warning)',
    'Online Delivery': 'var(--danger)',
  }

  return Array.from(totals.entries())
    .map(([label, value]) => ({
      label,
      value,
      percentage: total > 0 ? Math.round((value / total) * 100) : 0,
      stroke: colors[label],
    }))
    .filter((entry) => entry.value > 0)
    .sort((a, b) => b.value - a.value)
})

const topProducts = computed(() => {
  const map = new Map<string, { id: string; name: string; unitsSold: number; revenue: number }>()
  for (const order of periodOrders.value) {
    for (const item of order.items) {
      const existing = map.get(item.productId) ?? { id: item.productId, name: item.name, unitsSold: 0, revenue: 0 }
      existing.unitsSold += item.quantity
      existing.revenue += item.lineTotalCents
      map.set(item.productId, existing)
    }
  }

  return Array.from(map.values())
    .sort((a, b) => b.revenue - a.revenue)
    .slice(0, 5)
    .map((product) => ({
      ...product,
      imageUrl: store.products.find((p) => p.id === product.id)?.imageUrl,
    }))
})

const recentOrders = computed(() =>
  periodOrders.value
    .slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 6)
    .map((order) => ({
      id: `#${order.ticketNumber}`,
      customerName: customerNameFor(order),
      amount: order.totalCents,
      status: paymentStatusFor(order),
    })),
)
</script>

<template>
  <div class="dashboard-page" :class="{ 'dashboard-page--dark': darkModeEnabled }">
    <section class="dashboard-hero">
      <div class="dashboard-header">
        <p class="dashboard-store">{{ storeName }}</p>
        <h1 class="dashboard-title">Seller dashboard</h1>
        <p class="dashboard-copy">
          Orders, deliveries, stock, and revenue for {{ rangeCaption }} in one clear control center.
        </p>

        <div class="dashboard-facts" aria-label="Store status">
          <span class="dashboard-fact">
            <strong>Store code</strong>
            {{ storeCode }}
          </span>
          <span class="dashboard-fact">
            <strong>Sync</strong>
            {{ syncSummary }}
          </span>
          <span class="dashboard-fact">
            <strong>Shift</strong>
            {{ shiftSummary }}
          </span>
        </div>
      </div>

      <div class="dashboard-toolbar">
        <div class="dashboard-range">
          <span class="dashboard-range__text">{{ rangeCaption }}</span>
          <RangeSelector v-model="range" />
        </div>

        <div class="dashboard-hero__actions">
          <RouterLink v-if="auth.canAccess('orders')" class="dashboard-cta dashboard-cta--ghost" to="/orders">
            View orders
          </RouterLink>
          <RouterLink v-if="auth.canAccess('register')" class="dashboard-cta" to="/register">
            Open register
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- ── Today's work ──────────────────────────────────────────────── -->
    <p v-if="opsError" class="ops-error" role="alert">{{ opsError }}</p>

    <section class="ops-counts">
      <div class="ops-count">
        <span class="ops-count__value">{{ ordersInFlight.length }}</span>
        <span class="ops-count__label">Orders in flight</span>
      </div>
      <div class="ops-count" :class="{ 'ops-count--alert': awaitingRider.length > 0 }">
        <span class="ops-count__value">{{ awaitingRider.length }}</span>
        <span class="ops-count__label">Waiting on a rider</span>
      </div>
      <div class="ops-count" :class="{ 'ops-count--alert': unpaidOnlineOrders.length > 0 }">
        <span class="ops-count__value">{{ unpaidOnlineOrders.length }}</span>
        <span class="ops-count__label">Unpaid online</span>
      </div>
      <div class="ops-count" :class="{ 'ops-count--alert': store.outOfStockProducts.length > 0 }">
        <span class="ops-count__value">{{ store.outOfStockProducts.length + store.lowStockProducts.length }}</span>
        <span class="ops-count__label">Stock to watch</span>
      </div>
    </section>

    <section class="dashboard-grid dashboard-grid--ops">
      <ChartCard title="Orders in flight" summary="Everything still being made, or made but not paid for.">
        <div class="dashboard-table__head">
          <button class="ops-ghost" type="button" :disabled="refreshing" @click="refreshOrders">
            <RefreshCw :size="14" />
            <span>{{ refreshing ? 'Refreshing…' : 'Refresh' }}</span>
          </button>
          <RouterLink v-if="auth.canAccess('orders')" class="dashboard-link" to="/orders">
            <span>View all</span>
            <ChevronRight :size="14" />
          </RouterLink>
        </div>

        <p v-if="ordersInFlight.length === 0" class="dashboard-empty">Nothing waiting. Every order is served and settled.</p>

        <ul v-else class="ops-list">
          <li v-for="order in ordersInFlight" :key="order.id" class="ops-row">
            <div class="ops-row__main">
              <div class="ops-row__head">
                <strong>{{ order.ticketNumber }}</strong>
                <span class="ops-chip">{{ channelFor(order) }}</span>
                <span class="ops-chip ops-chip--quiet">{{ orderStatusLabel(order.status) }}</span>
                <span v-if="isUnpaid(order)" class="ops-chip ops-chip--warn">Unpaid</span>
              </div>
              <p class="ops-row__meta">
                {{ customerNameFor(order) }} · {{ formatCurrency(order.totalCents) }} ·
                {{ order.items.length }} item{{ order.items.length === 1 ? '' : 's' }}
              </p>
            </div>

            <div class="ops-row__actions">
              <button
                v-if="order.status !== 'served'"
                class="ops-action"
                type="button"
                :disabled="busyOrderId === order.id"
                @click="advanceStatus(order)"
              >
                Mark {{ nextOrderStatus(order.status) === 'ready' ? 'ready' : 'served' }}
              </button>
              <button
                v-if="isOnlineOrder(order) && isUnpaid(order)"
                class="ops-action ops-action--primary"
                type="button"
                @click="settlingOrder = order"
              >
                Settle payment
              </button>
            </div>
          </li>
        </ul>
      </ChartCard>

      <ChartCard title="Deliveries" summary="Who is carrying what, and how far along they are.">
        <p v-if="activeDeliveries.length === 0" class="dashboard-empty">No deliveries out. Pickup orders don't need a rider.</p>

        <ul v-else class="ops-list">
          <li v-for="order in activeDeliveries" :key="order.id" class="ops-row ops-row--stack">
            <div class="ops-row__head">
              <strong>{{ order.ticketNumber }}</strong>
              <span
                class="ops-chip"
                :class="order.riderName ? 'ops-chip--good' : 'ops-chip--warn'"
              >{{ deliveryStageLabel(order.deliveryStage ?? 'pending') }}</span>
            </div>
            <p class="ops-row__meta">{{ order.deliveryAddress || 'No address on the order' }}</p>

            <!-- No rider yet: name whoever is taking it. There are no rider
                 accounts, so this is the seller writing down who left with the
                 bag — which is what the customer's tracking page then shows. -->
            <form v-if="!order.riderName" class="ops-rider" @submit.prevent="notifyRider(order)">
              <input
                v-model="riderDraft(order.id).name"
                class="ops-input"
                type="text"
                placeholder="Rider name"
                autocomplete="off"
              >
              <input
                v-model="riderDraft(order.id).phone"
                class="ops-input"
                type="tel"
                placeholder="Mobile number"
                autocomplete="off"
              >
              <button class="ops-action ops-action--primary" type="submit" :disabled="busyOrderId === order.id">
                {{ busyOrderId === order.id ? 'Sending…' : 'Notify rider' }}
              </button>
            </form>

            <div v-else class="ops-rider ops-rider--assigned">
              <span class="ops-rider__name">{{ order.riderName }}</span>
              <template v-if="order.riderPhone">
                <a class="ops-action" :href="telHref(order.riderPhone)">
                  <Phone :size="14" />
                  <span>Call</span>
                </a>
                <a class="ops-action" :href="smsHref(order)">
                  <MessageSquare :size="14" />
                  <span>Text</span>
                </a>
              </template>
              <button
                v-if="nextDeliveryStage(order.deliveryStage ?? 'pending')"
                class="ops-action ops-action--primary"
                type="button"
                :disabled="busyOrderId === order.id"
                @click="advanceDelivery(order)"
              >
                {{ deliveryStageLabel(nextDeliveryStage(order.deliveryStage ?? 'pending')!) }}
              </button>
            </div>
          </li>
        </ul>
      </ChartCard>
    </section>

    <section class="dashboard-grid dashboard-grid--ops">
      <ChartCard title="Stock to watch" summary="Out of stock first, then anything under its reorder point.">
        <div class="dashboard-table__head">
          <span>Restock adds 10 to the shelf count</span>
          <RouterLink v-if="auth.canAccess('inventory')" class="dashboard-link" to="/inventory">
            <span>Inventory</span>
            <ChevronRight :size="14" />
          </RouterLink>
        </div>

        <p v-if="stockAlerts.length === 0" class="dashboard-empty">Every tracked product is above its reorder point.</p>

        <ul v-else class="ops-list">
          <li v-for="product in stockAlerts" :key="product.id" class="ops-row">
            <div class="ops-row__main">
              <div class="ops-row__head">
                <strong>{{ product.name }}</strong>
                <span class="ops-chip" :class="(product.stockQty ?? 0) <= 0 ? 'ops-chip--warn' : 'ops-chip--quiet'">
                  {{ (product.stockQty ?? 0) <= 0 ? 'Out of stock' : `${product.stockQty} left` }}
                </span>
              </div>
              <p class="ops-row__meta">{{ formatCurrency(product.priceCents) }}{{ product.unitLabel ? ` · ${product.unitLabel}` : '' }}</p>
            </div>
            <div class="ops-row__actions">
              <button
                class="ops-action"
                type="button"
                :disabled="busyOrderId === product.id"
                @click="restock(product.id)"
              >
                Restock +10
              </button>
            </div>
          </li>
        </ul>
      </ChartCard>

      <ChartCard title="Your store" summary="What customers see, and where this register stands.">
        <dl class="ops-facts">
          <div v-for="fact in storeFacts" :key="fact.label">
            <dt>{{ fact.label }}</dt>
            <dd>{{ fact.value }}</dd>
          </div>
        </dl>

        <div class="ops-links">
          <RouterLink v-if="auth.canAccess('settings')" class="ops-action" to="/settings">Store settings</RouterLink>
          <RouterLink v-if="auth.canAccess('products')" class="ops-action" to="/products">Products</RouterLink>
          <RouterLink v-if="auth.canAccess('employees')" class="ops-action" to="/employees">Staff</RouterLink>
          <RouterLink v-if="auth.canAccess('register')" class="ops-action ops-action--primary" to="/register">Open register</RouterLink>
        </div>
      </ChartCard>
    </section>

    <SettleOnlinePaymentSheet
      v-if="settlingOrder"
      :order="settlingOrder"
      @close="settlingOrder = null"
      @settled="settlingOrder = null"
    />

    <section class="dashboard-kpis">
      <MetricCard label="Total Revenue" :value="formatCurrency(totalRevenue)" :delta="delta(totalRevenue, previousRevenue)" :compare-label="previousRangeCaption" />
      <MetricCard label="Transactions" :value="transactions.toLocaleString('en-PH')" :delta="delta(transactions, previousTransactions)" :compare-label="previousRangeCaption" />
      <MetricCard label="Average Order Value" :value="formatCurrency(averageOrderValue)" :delta="delta(averageOrderValue, previousAverageOrderValue)" :compare-label="previousRangeCaption" />
      <MetricCard label="Return Customers" :value="`${returnCustomers.toFixed(1)}%`" :delta="delta(returnCustomers, previousReturnCustomers)" :compare-label="previousRangeCaption" />
    </section>

    <section class="dashboard-grid">
      <ChartCard title="Sales Overview" summary="Daily revenue trend for the selected period.">
        <div class="dashboard-chart__header">
          <span class="dashboard-chart__badge">Daily</span>
        </div>
        <div class="dashboard-bar-chart">
          <div class="dashboard-bar-chart__plot">
            <div class="dashboard-bar-chart__yaxis">
              <span v-for="tick in salesYAxisTicks" :key="tick">{{ tick }}</span>
            </div>
            <div class="dashboard-bar-chart__grid">
              <div class="dashboard-bar-chart__gridlines" aria-hidden="true">
                <span v-for="row in 5" :key="row" />
              </div>
              <div class="dashboard-bar-chart__bars">
                <div v-for="(point, index) in salesSeries" :key="point.key" class="dashboard-bar-chart__col">
                  <span v-if="index === peakSalesIndex" class="dashboard-bar-chart__value">{{ formatCurrency(point.totalCents) }}</span>
                  <div
                    class="dashboard-bar-chart__bar"
                    :class="{ 'is-peak': index === peakSalesIndex }"
                    :style="{ height: `${(point.totalCents / salesAxisMax) * 100}%` }"
                  />
                </div>
              </div>
            </div>
          </div>
          <div class="dashboard-bar-chart__labels">
            <span v-for="(point, index) in salesSeries" :key="point.key">{{ index % salesLabelStep === 0 ? point.label : '' }}</span>
          </div>
        </div>
      </ChartCard>

      <ChartCard title="Sales by Channel" summary="Revenue distribution across sales channels.">
        <div class="dashboard-channels">
          <div class="dashboard-channels__total">
            <span>Total revenue</span>
            <strong>{{ formatCurrency(totalRevenue) }}</strong>
          </div>
          <p v-if="channelBreakdown.length === 0" class="dashboard-empty">No sales recorded for this period yet.</p>
          <div v-else class="dashboard-channels__list">
            <div v-for="segment in channelBreakdown" :key="segment.label" class="dashboard-channels__row">
              <div class="dashboard-channels__row-head">
                <span class="dashboard-channels__label">
                  <i :style="{ background: segment.stroke }" />
                  {{ segment.label }}
                </span>
                <span class="dashboard-channels__stats">{{ formatCurrency(segment.value) }} · {{ segment.percentage }}%</span>
              </div>
              <div class="dashboard-channels__track">
                <div class="dashboard-channels__fill" :style="{ width: `${segment.percentage}%`, background: segment.stroke }" />
              </div>
            </div>
          </div>
        </div>
      </ChartCard>
    </section>

    <section class="dashboard-grid dashboard-grid--tables">
      <ChartCard title="Top Products" summary="Best-performing products ranked by revenue.">
        <div class="dashboard-table__head">
          <span>Units sold and revenue</span>
          <RouterLink class="dashboard-link" to="/products">
            <span>View all</span>
            <ChevronRight :size="14" />
          </RouterLink>
        </div>
        <p v-if="topProducts.length === 0" class="dashboard-empty">No products sold for this period yet.</p>
        <table v-else class="dashboard-table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Units Sold</th>
              <th>Revenue</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="product in topProducts" :key="product.id">
              <td>
                <div class="dashboard-product-cell">
                  <span class="dashboard-product-thumb">
                    <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" loading="lazy">
                  </span>
                  <span>{{ product.name }}</span>
                </div>
              </td>
              <td>{{ product.unitsSold.toLocaleString('en-PH') }}</td>
              <td>{{ formatCurrency(product.revenue) }}</td>
            </tr>
          </tbody>
        </table>
      </ChartCard>

      <ChartCard title="Recent Orders" summary="Latest transactions for the selected date range.">
        <div class="dashboard-table__head">
          <span>Latest order activity</span>
          <RouterLink class="dashboard-link" to="/orders">
            <span>View all</span>
            <ChevronRight :size="14" />
          </RouterLink>
        </div>
        <p v-if="recentOrders.length === 0" class="dashboard-empty">No orders placed for this period yet.</p>
        <table v-else class="dashboard-table">
          <thead>
            <tr>
              <th>Order ID</th>
              <th>Customer</th>
              <th>Amount</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in recentOrders" :key="order.id">
              <td class="dashboard-table__id">{{ order.id }}</td>
              <td>{{ order.customerName }}</td>
              <td>{{ formatCurrency(order.amount) }}</td>
              <td>
                <span class="dashboard-status" :class="order.status === 'Completed' ? 'dashboard-status--done' : 'dashboard-status--processing'">
                  {{ order.status }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </ChartCard>
    </section>
  </div>
</template>

<style scoped>
.dashboard-page {
  --accent: #1a6b3c;
  --accent-pressed: #155530;
  --accent-text-on: #ffffff;
  --success: #22c55e;
  --warning: #f5a623;
  --danger: #c85c3c;
  --bg-elevated: #ffffff;
  --fill: rgba(26, 107, 60, 0.08);
  --text-primary: #1a1a1a;
  --text-secondary: #4a5b52;
  --text-tertiary: #74837a;
  --separator: #e7ece8;
  --dashboard-card-bg: #ffffff;
  --dashboard-card-gradient: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(245, 249, 246, 0.96));
  --dashboard-card-alert-gradient: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 248, 235, 0.98));
  --dashboard-row-bg: rgba(245, 249, 246, 0.9);
  --dashboard-button-bg: #ffffff;
  --dashboard-input-bg: #ffffff;
  --dashboard-chip-bg: rgba(26, 107, 60, 0.08);
  --dashboard-chip-quiet-bg: #ffffff;
  --dashboard-chip-warn-bg: rgba(245, 166, 35, 0.16);
  --dashboard-chip-warn-text: #8a5a0a;
  --dashboard-chip-good-bg: rgba(34, 197, 94, 0.14);
  --dashboard-chip-good-text: #1a6b3c;
  --dashboard-gridline: rgba(26, 107, 60, 0.1);
  --dashboard-bar-bg: rgba(26, 107, 60, 0.18);
  --dashboard-bar-peak: linear-gradient(180deg, #22c55e 0%, #1a6b3c 100%);
  --dashboard-track-bg: rgba(26, 107, 60, 0.08);
  --dashboard-chart-badge-bg: rgba(26, 107, 60, 0.08);
  --dashboard-chart-badge-text: var(--accent);
  --dashboard-hero-bg:
    radial-gradient(circle at top right, rgba(187, 244, 81, 0.22), transparent 34%),
    linear-gradient(135deg, #1a6b3c 0%, #155530 100%);
  --dashboard-hero-shadow: 0 18px 44px rgba(6, 36, 15, 0.18);
  --dashboard-range-bg: rgba(255, 255, 255, 0.08);
  --dashboard-range-border: rgba(255, 255, 255, 0.14);
  --dashboard-range-text: rgba(255, 255, 255, 0.78);
  --dashboard-fact-bg: rgba(255, 255, 255, 0.08);
  --dashboard-fact-border: rgba(255, 255, 255, 0.16);
  --dashboard-fact-label: rgba(255, 255, 255, 0.72);
  --dashboard-cta-bg: #bbf451;
  --dashboard-cta-color: #06240f;
  --dashboard-cta-hover: #ffffff;
  --dashboard-cta-ghost-bg: rgba(255, 255, 255, 0.08);
  --dashboard-cta-ghost-border: rgba(255, 255, 255, 0.18);
  --dashboard-cta-ghost-hover: rgba(255, 255, 255, 0.16);
  --dashboard-shadow: 0 10px 28px rgba(20, 53, 28, 0.06);
  --dashboard-shadow-strong: 0 0 0 3px rgba(26, 107, 60, 0.12);
  --dashboard-status-done-bg: rgba(34, 197, 94, 0.14);
  --dashboard-status-done-text: #1a6b3c;
  --dashboard-status-processing-bg: rgba(245, 166, 35, 0.16);
  --dashboard-status-processing-text: #8a5a0a;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 24px;
}

.dashboard-page--dark {
  --accent: #92dd73;
  --accent-pressed: #7dcb60;
  --accent-text-on: #08200f;
  --success: #6ee787;
  --warning: #f6c56b;
  --danger: #ff8f70;
  --bg-elevated: #151c1a;
  --fill: rgba(187, 244, 81, 0.08);
  --text-primary: #edf5ef;
  --text-secondary: #a7b6aa;
  --text-tertiary: #768477;
  --separator: rgba(235, 245, 238, 0.08);
  --dashboard-card-bg: #151c1a;
  --dashboard-card-gradient: linear-gradient(180deg, rgba(24, 31, 28, 0.98), rgba(18, 24, 22, 0.98));
  --dashboard-card-alert-gradient: linear-gradient(180deg, rgba(36, 31, 22, 0.98), rgba(26, 22, 17, 0.98));
  --dashboard-row-bg: rgba(24, 31, 28, 0.92);
  --dashboard-button-bg: #1b2421;
  --dashboard-input-bg: #101614;
  --dashboard-chip-bg: rgba(187, 244, 81, 0.1);
  --dashboard-chip-quiet-bg: rgba(255, 255, 255, 0.03);
  --dashboard-chip-warn-bg: rgba(245, 166, 35, 0.18);
  --dashboard-chip-warn-text: #ffd482;
  --dashboard-chip-good-bg: rgba(34, 197, 94, 0.18);
  --dashboard-chip-good-text: #a3efb5;
  --dashboard-gridline: rgba(255, 255, 255, 0.08);
  --dashboard-bar-bg: rgba(187, 244, 81, 0.18);
  --dashboard-bar-peak: linear-gradient(180deg, #bbf451 0%, #22c55e 100%);
  --dashboard-track-bg: rgba(255, 255, 255, 0.06);
  --dashboard-chart-badge-bg: rgba(187, 244, 81, 0.12);
  --dashboard-chart-badge-text: #bff56b;
  --dashboard-hero-bg:
    radial-gradient(circle at top right, rgba(187, 244, 81, 0.16), transparent 34%),
    linear-gradient(135deg, #0f4828 0%, #0a2f19 100%);
  --dashboard-hero-shadow: 0 22px 60px rgba(0, 0, 0, 0.4);
  --dashboard-range-bg: rgba(255, 255, 255, 0.06);
  --dashboard-range-border: rgba(255, 255, 255, 0.1);
  --dashboard-range-text: rgba(255, 255, 255, 0.78);
  --dashboard-fact-bg: rgba(255, 255, 255, 0.06);
  --dashboard-fact-border: rgba(255, 255, 255, 0.1);
  --dashboard-fact-label: rgba(255, 255, 255, 0.66);
  --dashboard-cta-bg: #bbf451;
  --dashboard-cta-color: #08200f;
  --dashboard-cta-hover: #d3ff7d;
  --dashboard-cta-ghost-bg: rgba(255, 255, 255, 0.04);
  --dashboard-cta-ghost-border: rgba(255, 255, 255, 0.1);
  --dashboard-cta-ghost-hover: rgba(255, 255, 255, 0.08);
  --dashboard-shadow: 0 18px 44px rgba(0, 0, 0, 0.32);
  --dashboard-shadow-strong: 0 0 0 3px rgba(146, 221, 115, 0.18);
  --dashboard-status-done-bg: rgba(34, 197, 94, 0.18);
  --dashboard-status-done-text: #a3efb5;
  --dashboard-status-processing-bg: rgba(245, 166, 35, 0.18);
  --dashboard-status-processing-text: #ffd482;
}

.dashboard-hero,
.dashboard-header,
.dashboard-kpis,
.dashboard-grid {
  display: grid;
  gap: 16px;
}

.dashboard-hero {
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.85fr);
  gap: 24px;
  padding: 28px;
  border-radius: 18px;
  background: var(--dashboard-hero-bg);
  color: #ffffff;
  box-shadow: var(--dashboard-hero-shadow);
}

.dashboard-store {
  margin: 0;
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

/* ── Operations ─────────────────────────────────────────────────────────── */

.ops-error {
  margin: 0;
  padding: 12px 16px;
  border: 1px solid rgba(200, 92, 60, 0.16);
  border-radius: 14px;
  background: rgba(200, 92, 60, 0.08);
  color: var(--danger);
  font: var(--type-subhead);
}

.ops-counts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.ops-count {
  display: grid;
  gap: 6px;
  min-height: 132px;
  padding: 20px;
  border: 1px solid var(--separator);
  border-radius: 18px;
  background: var(--dashboard-card-gradient);
  box-shadow: var(--dashboard-shadow);
}

/* Only a count that needs someone gets the warm border — four highlighted
   tiles would highlight nothing. */
.ops-count--alert {
  border-color: rgba(245, 166, 35, 0.4);
  background: var(--dashboard-card-alert-gradient);
}

.ops-count__value {
  font-size: clamp(2rem, 3vw, 2.35rem);
  font-weight: 800;
  letter-spacing: -0.04em;
}

.ops-count__label {
  max-width: 14ch;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

/* Equal halves: neither the order queue nor the delivery board is the
   secondary one, unlike the analytics grid below. */
.dashboard-grid--ops {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
}

.ops-list {
  display: grid;
  gap: 12px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.ops-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 16px;
  border: 1px solid var(--separator);
  border-radius: 14px;
  background: var(--dashboard-row-bg);
}

.ops-row--stack {
  display: grid;
  gap: 10px;
}

.ops-row__main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.ops-row__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ops-row__meta {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.ops-row__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ops-chip {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--dashboard-chip-bg);
  color: var(--accent);
  font: var(--type-caption);
  font-weight: 600;
}

.ops-chip--quiet {
  border: 1px solid var(--separator);
  background: var(--dashboard-chip-quiet-bg);
  color: var(--text-secondary);
}

.ops-chip--warn {
  background: var(--dashboard-chip-warn-bg);
  color: var(--dashboard-chip-warn-text);
}

.ops-chip--good {
  background: var(--dashboard-chip-good-bg);
  color: var(--dashboard-chip-good-text);
}

.ops-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-pill);
  background: var(--dashboard-button-bg);
  color: var(--text-primary);
  font: var(--type-caption);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.ops-action:hover:not(:disabled) {
  border-color: rgba(26, 107, 60, 0.28);
  color: var(--accent);
}

.ops-action--primary {
  border-color: transparent;
  background: var(--accent);
  color: var(--accent-text-on);
}

.ops-action--primary:hover:not(:disabled) {
  background: var(--accent-pressed);
  color: var(--accent-text-on);
}

.ops-action:disabled {
  opacity: 0.5;
}

.ops-ghost {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: none;
  color: var(--accent);
  font: var(--type-caption);
  font-weight: 700;
}

.ops-ghost:hover:not(:disabled) {
  color: var(--accent-pressed);
}

/* The rider row wraps to its own line on a narrow card rather than squeezing
   two inputs and a button into a strip too small to type in. */
.ops-rider {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ops-rider__name {
  font: var(--type-subhead);
  font-weight: 700;
}

.ops-input {
  flex: 1 1 130px;
  min-width: 0;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--separator);
  border-radius: 999px;
  background: var(--dashboard-input-bg);
  color: var(--text-primary);
  font: var(--type-subhead);
}

.ops-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: var(--dashboard-shadow-strong);
}

.ops-facts {
  display: grid;
  gap: 12px;
  margin: 12px 0 0;
}

.ops-facts > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--separator);
}

.ops-facts > div:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.ops-facts dt {
  color: var(--text-secondary);
  font: var(--type-caption);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.ops-facts dd {
  margin: 0;
  font: var(--type-subhead);
  font-weight: 600;
  text-align: right;
}

.ops-links {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.dashboard-header {
  align-content: start;
}

.dashboard-title {
  margin: 0;
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 800;
  line-height: 0.98;
  letter-spacing: -0.05em;
  color: #ffffff;
}

.dashboard-copy {
  max-width: 58ch;
  margin: 0;
  color: rgba(255, 255, 255, 0.84);
  font-size: 15px;
  line-height: 1.65;
}

.dashboard-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dashboard-fact {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--dashboard-fact-border);
  border-radius: 999px;
  background: var(--dashboard-fact-bg);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
}

.dashboard-fact strong {
  color: var(--dashboard-fact-label);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.dashboard-toolbar,
.dashboard-range,
.dashboard-chart__header,
.dashboard-table__head,
.dashboard-link {
  display: flex;
  align-items: center;
}

.dashboard-toolbar,
.dashboard-range {
  gap: 12px;
}

.dashboard-toolbar {
  display: grid;
  align-content: space-between;
  justify-items: end;
}

.dashboard-range {
  display: grid;
  width: 100%;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid var(--dashboard-range-border);
  background: var(--dashboard-range-bg);
}

.dashboard-range__text {
  color: var(--dashboard-range-text);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.dashboard-hero__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: end;
  width: 100%;
}

.dashboard-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 20px;
  border-radius: 999px;
  background: var(--dashboard-cta-bg);
  color: var(--dashboard-cta-color);
  font-size: 14px;
  font-weight: 800;
  text-decoration: none;
  transition:
    background var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out),
    border-color var(--dur-fast) var(--ease-out);
}

.dashboard-cta:hover {
  background: var(--dashboard-cta-hover);
}

.dashboard-cta--ghost {
  border: 1px solid var(--dashboard-cta-ghost-border);
  background: var(--dashboard-cta-ghost-bg);
  color: #ffffff;
}

.dashboard-cta--ghost:hover {
  border-color: var(--dashboard-cta-ghost-border);
  background: var(--dashboard-cta-ghost-hover);
  color: #ffffff;
}

.dashboard-kpis {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard-grid {
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr);
}

.dashboard-grid--tables {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard-chart__header,
.dashboard-table__head {
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.dashboard-chart__badge {
  min-height: 28px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  background: var(--dashboard-chart-badge-bg);
  color: var(--dashboard-chart-badge-text);
  font-weight: 700;
}

.dashboard-bar-chart {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.dashboard-bar-chart__plot {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  height: 260px;
}

.dashboard-bar-chart__yaxis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  color: var(--text-tertiary);
  font: var(--type-caption);
  font-variant-numeric: tabular-nums;
}

.dashboard-bar-chart__grid {
  position: relative;
  height: 100%;
}

.dashboard-bar-chart__gridlines {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.dashboard-bar-chart__gridlines span {
  display: block;
  height: 0;
  border-top: 1px solid var(--dashboard-gridline);
}

.dashboard-bar-chart__bars {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding-top: 28px;
}

.dashboard-bar-chart__col {
  flex: 1;
  height: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  gap: var(--space-1);
}

.dashboard-bar-chart__bar {
  width: 100%;
  max-width: 28px;
  border-radius: 999px 999px 0 0;
  background: var(--dashboard-bar-bg);
}

.dashboard-bar-chart__bar.is-peak {
  background: var(--dashboard-bar-peak);
}

.dashboard-bar-chart__value {
  color: var(--text-primary);
  font: var(--type-caption);
  font-weight: 600;
  white-space: nowrap;
}

.dashboard-bar-chart__labels {
  display: flex;
  gap: 8px;
  padding-left: calc(2.5em + 12px);
  color: var(--text-tertiary);
  font: var(--type-caption);
}

.dashboard-bar-chart__labels span {
  flex: 1;
  min-width: 0;
  text-align: center;
  white-space: nowrap;
  overflow: visible;
}

.dashboard-channels {
  display: grid;
  gap: 16px;
}

.dashboard-channels__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--separator);
}

.dashboard-channels__total span {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.dashboard-channels__total strong {
  font: var(--type-headline);
}

.dashboard-channels__list {
  display: grid;
  gap: 16px;
}

.dashboard-channels__row {
  display: grid;
  gap: 8px;
}

.dashboard-channels__row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font: var(--type-caption);
}

.dashboard-channels__label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
}

.dashboard-channels__label i {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  display: inline-block;
  flex: none;
}

.dashboard-channels__stats {
  color: var(--text-primary);
  font-weight: 600;
}

.dashboard-channels__track {
  height: 10px;
  border-radius: var(--radius-pill);
  background: var(--dashboard-track-bg);
  overflow: hidden;
}

.dashboard-channels__fill {
  height: 100%;
  border-radius: var(--radius-pill);
}

.dashboard-link {
  gap: 2px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 700;
}

.dashboard-empty {
  margin: 0;
  padding: 20px 0;
  color: var(--text-tertiary);
  font: var(--type-subhead);
  text-align: center;
}

.dashboard-table {
  width: 100%;
  border-collapse: collapse;
}

.dashboard-table th,
.dashboard-table td {
  padding: 14px 0;
  border-bottom: 1px solid var(--separator);
  text-align: left;
}

.dashboard-table th {
  color: var(--text-tertiary);
  font: var(--type-caption);
  font-weight: 600;
}

.dashboard-table td {
  color: var(--text-primary);
  font: var(--type-subhead);
}

.dashboard-table__id {
  color: var(--accent);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.dashboard-product-cell {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.dashboard-product-thumb {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--dashboard-chip-bg);
}

.dashboard-product-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dashboard-status {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-pill);
  font: var(--type-caption);
  font-weight: 600;
}

.dashboard-status--done {
  background: var(--dashboard-status-done-bg);
  color: var(--dashboard-status-done-text);
}

.dashboard-status--processing {
  background: var(--dashboard-status-processing-bg);
  color: var(--dashboard-status-processing-text);
}

:deep(.chart-card),
:deep(.metric-card) {
  border: 1px solid var(--separator);
  border-radius: 18px;
  background: var(--dashboard-card-bg);
  box-shadow: var(--dashboard-shadow);
}

:deep(.chart-card) {
  gap: 16px;
  padding: 22px;
}

:deep(.metric-card) {
  gap: 8px;
  min-height: 148px;
  padding: 20px;
  background: var(--dashboard-card-gradient);
}

:deep(.chart-card__title) {
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--text-primary);
}

:deep(.metric-card__label) {
  color: var(--text-secondary);
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

:deep(.metric-card__value) {
  font-size: clamp(1.9rem, 3vw, 2.4rem);
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.05em;
}

:deep(.metric-card__delta--up) {
  color: var(--dashboard-status-done-text);
}

:deep(.metric-card__delta--down) {
  color: var(--danger);
}

:deep(.range-selector) {
  gap: 6px;
  padding: 6px;
  border-radius: 999px;
  background: var(--dashboard-range-bg);
}

:deep(.range-btn) {
  min-height: 34px;
  padding: 0 14px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 13px;
  font-weight: 700;
}

:deep(.range-btn--active) {
  background: #ffffff;
  color: var(--accent);
  box-shadow: none;
}

:deep(.range-btn:not(.range-btn--active):hover) {
  color: #ffffff;
}

:deep(.range-btn:focus-visible) {
  outline: 2px solid #bbf451;
  outline-offset: 2px;
}

@media (max-width: 1100px) {
  .dashboard-hero,
  .dashboard-kpis,
  .dashboard-grid,
  .dashboard-grid--tables,
  .dashboard-grid--ops,
  .ops-counts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-header {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-toolbar {
    justify-items: start;
  }
}

@media (max-width: 720px) {
  .dashboard-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid,
  .dashboard-grid--tables,
  .dashboard-grid--ops {
    grid-template-columns: minmax(0, 1fr);
  }

  .ops-row {
    align-items: stretch;
    flex-direction: column;
  }

  .dashboard-toolbar {
    justify-items: stretch;
  }

  .dashboard-table {
    display: block;
    overflow-x: auto;
  }

  .dashboard-hero {
    grid-template-columns: minmax(0, 1fr);
    padding: 22px 18px;
    gap: 18px;
  }

  .dashboard-hero__actions {
    justify-content: flex-start;
  }

  .dashboard-cta {
    flex: 1 1 160px;
  }

  .dashboard-facts {
    gap: 8px;
  }

  .dashboard-fact {
    width: 100%;
    justify-content: space-between;
  }

  .ops-count {
    min-height: 0;
  }

  :deep(.metric-card) {
    min-height: 0;
  }
}
</style>
