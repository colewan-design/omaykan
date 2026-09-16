<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowRight,
  Calendar,
  ChevronDown,
  Clock,
  Copy,
  ExternalLink,
  Link2,
  Monitor,
  Check,
  Settings,
  ShoppingBag,
  ShoppingCart,
  Store,
  TriangleAlert,
} from '@lucide/vue'
import {
  formatCurrency,
  guestCustomerName,
  nextOrderStatus,
  orderStatusLabel,
  storefrontUrl as storefrontUrlFor,
  type OrderSummary,
} from '@pos/shared/index'
import { usePosStore } from '@pos/core/stores/pos'
import { useAuthStore } from '@pos/core/stores/auth'
import { getPosRepository } from '@pos/core/services/runtime'
import { subscribeToStoreOrders } from '@pos/core/realtime/orderChannel'
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
    window.addEventListener('keydown', closeMenusOnEscape)
  }
})

onBeforeUnmount(() => {
  themeMediaQuery?.removeEventListener('change', syncDarkMode)
  themeObserver?.disconnect()
  stopOrderFeed?.()
  stopOrderFeed = null
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', closeMenusOnEscape)
  }
  if (copyResetTimer) clearTimeout(copyResetTimer)
})

// Push, not poll: while the dashboard is open, live order events over Reverb
// refresh the queue. Falls back silently to the manual refresh when realtime
// is off or this device isn't paired to a backend store.
async function startOrderFeed() {
  try {
    const storeId = await getPosRepository().getSyncStoreId()
    const token = auth.session?.authToken
    if (!storeId || !token) return
    stopOrderFeed = subscribeToStoreOrders({
      apiBaseUrl: apiBase,
      storeId,
      token,
      onOrderEvent: (_payload, event) => {
        // Rider position fixes land ten seconds apart and only move a map this
        // page no longer draws — the delivery board moved to Orders.
        if (event === 'rider.position') return
        void refreshOrders()
      },
    })
  } catch {
    // No live feed is fine — the page still refreshes on entry and on demand.
  }
}

// ── Today's work ───────────────────────────────────────────────────────────

const opsError = ref('')
const busyOrderId = ref('')
const refreshing = ref(false)
const settlingOrder = ref<OrderSummary | null>(null)

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
const ordersNeedingAction = computed(() => {
  const online = store.onlineOrders.filter(
    (order) => !order.voidedAt && (order.status !== 'served' || isUnpaid(order)),
  )
  const inPerson = store.orders.filter((order) => !order.voidedAt && order.status !== 'served')
  return [...online, ...inPerson].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
})

// ── The queue's filter tabs ────────────────────────────────────────────────
//
// Two jobs share this list and they are not the same job: the person at the
// till settling payments, and the person on the pass making food. The counts
// are on the tabs so neither has to open the other's filter to see whether it
// is empty.

type QueueFilter = 'all' | 'payment' | 'prepare'

const queueFilter = ref<QueueFilter>('all')

const paymentPendingOrders = computed(() => ordersNeedingAction.value.filter(isUnpaid))
const prepareOrders = computed(() =>
  ordersNeedingAction.value.filter((order) => !isUnpaid(order) && order.status !== 'served'),
)

const queueTabs = computed(() => [
  { key: 'all' as const, label: 'All', count: ordersNeedingAction.value.length },
  { key: 'payment' as const, label: 'Payment pending', count: paymentPendingOrders.value.length },
  { key: 'prepare' as const, label: 'Prepare order', count: prepareOrders.value.length },
])

const visibleQueue = computed(() => {
  const source = queueFilter.value === 'payment'
    ? paymentPendingOrders.value
    : queueFilter.value === 'prepare'
      ? prepareOrders.value
      : ordersNeedingAction.value
  return source.slice(0, 6)
})

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

/**
 * "12 mins ago". Coarse on purpose — a shop reading this wants to know which
 * ticket has been sitting longest, not the second it was rung up.
 */
function relativeTime(iso: string): string {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min${minutes === 1 ? '' : 's'} ago`

  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`

  const days = Math.round(hours / 24)
  return `${days} day${days === 1 ? '' : 's'} ago`
}

// ── Stock ──────────────────────────────────────────────────────────────────

/**
 * Out of stock first — a shelf at zero is losing sales right now.
 *
 * `lowStockProducts` counts anything at or under its threshold, zero included,
 * so the out-of-stock rows would otherwise appear twice.
 */
const stockAlerts = computed(() => {
  const out = store.outOfStockProducts
  const outIds = new Set(out.map((product) => product.id))
  const low = store.lowStockProducts.filter((product) => !outIds.has(product.id))
  return [...out, ...low].slice(0, 4)
})

const stockToWatchCount = computed(() => {
  const ids = new Set(store.outOfStockProducts.map((product) => product.id))
  for (const product of store.lowStockProducts) ids.add(product.id)
  return ids.size
})

/** Red at zero, amber below the reorder point. Nothing else reaches this list. */
function stockSeverity(stockQty: number | undefined): 'out' | 'low' {
  return (stockQty ?? 0) <= 0 ? 'out' : 'low'
}

/**
 * Products whose photo would not load.
 *
 * Half the catalog points at a supplier's CDN, and an empty grey square reads
 * as a broken page rather than a product without a picture. Falling back to
 * the initial gives every row something deliberate in the same footprint.
 */
const brokenThumbs = ref(new Set<string>())

function markThumbBroken(productId: string) {
  // A new Set, not a mutation: Vue tracks the ref, not the Set's internals.
  brokenThumbs.value = new Set(brokenThumbs.value).add(productId)
}

function thumbFor(productId: string, imageUrl?: string | null) {
  return imageUrl && !brokenThumbs.value.has(productId) ? imageUrl : ''
}

function initialFor(name: string) {
  return name.trim().charAt(0).toUpperCase() || '?'
}

// Six identical "Restock +10" buttons down a card is six times the same word.
// The split keeps the common amount one tap away and moves the rest behind a
// caret, which is also where a shop that buys by the case goes.
const RESTOCK_QUANTITIES = [5, 10, 25, 50] as const
const QUICK_RESTOCK = 10
const openRestockMenu = ref('')

function toggleRestockMenu(productId: string) {
  openRestockMenu.value = openRestockMenu.value === productId ? '' : productId
}

function closeMenusOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') openRestockMenu.value = ''
}

function restock(productId: string, quantity: number = QUICK_RESTOCK) {
  openRestockMenu.value = ''
  return runOrderAction(productId, () => store.restockProduct(productId, quantity))
}

// ── Store status ───────────────────────────────────────────────────────────

type StatusTone = 'good' | 'warn' | 'idle'

interface StatusRow {
  key: string
  label: string
  value: string
  caption: string
  tone: StatusTone
  icon: typeof Monitor
}

const storeStatuses = computed<StatusRow[]>(() => {
  const synced = store.settings.syncMode === 'online-sync'
  const shift = store.activeShift
  const shiftOpenedAt = shift
    ? new Intl.DateTimeFormat('en-PH', { hour: 'numeric', minute: '2-digit' }).format(new Date(shift.openedAt))
    : ''

  return [
    {
      key: 'sync',
      label: 'POS Sync',
      value: synced ? 'Online' : 'Local only',
      caption: synced ? 'Syncing with your storefront' : 'This device keeps its own copy',
      tone: synced ? 'good' : 'warn',
      icon: Monitor,
    },
    {
      key: 'register',
      label: 'Register',
      value: shift ? 'Open' : 'Closed',
      caption: shift ? `Active since ${shiftOpenedAt}` : 'No one has opened the till',
      tone: shift ? 'good' : 'idle',
      icon: Store,
    },
    {
      key: 'shift',
      label: 'Current Shift',
      value: shift ? 'In progress' : 'None',
      caption: shift
        ? `Started at ${shiftOpenedAt} · ${formatCurrency(shift.expectedCashCents)} expected`
        : 'Open the register to start one',
      tone: shift ? 'good' : 'idle',
      icon: Clock,
    },
  ]
})

/** Green only when nothing in the list is asking for someone. */
const allSystemsOperational = computed(() =>
  storeStatuses.value.every((status) => status.tone === 'good'),
)

/** The shop's own page — its subdomain where there is a shop domain. */
const storefrontUrl = computed(() => {
  if (!store.settings.storefrontSlug) return ''
  const origin = typeof window === 'undefined' ? '' : window.location.origin
  return storefrontUrlFor(store.settings.storefrontSlug, {
    rootDomain: import.meta.env.VITE_SHOP_ROOT_DOMAIN,
    origin,
  })
})

/**
 * The shop's public handle: the link without its scheme, `nenas.omaykan.com`.
 *
 * Was the store code customers typed to find the shop, which is gone with the
 * pairing it doubled as. The full link lives in Settings > Online Store; this
 * row only has room for the handle, and that is the part worth recognising.
 */
const storefrontHandle = computed(() =>
  storefrontUrl.value ? storefrontUrl.value.replace(/^https?:\/\//, '') : 'Not published',
)

const linkCopied = ref(false)
let copyResetTimer: ReturnType<typeof setTimeout> | null = null

async function copyStorefrontLink() {
  if (!storefrontUrl.value) return
  try {
    await navigator.clipboard.writeText(storefrontUrl.value)
    linkCopied.value = true
    if (copyResetTimer) clearTimeout(copyResetTimer)
    copyResetTimer = setTimeout(() => { linkCopied.value = false }, 1800)
  } catch {
    // Clipboard access can be denied outright. The link is on screen either
    // way, so say nothing rather than raise an error over a convenience.
  }
}

// ── The period ─────────────────────────────────────────────────────────────

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
  return (order.paymentStatus ?? 'paid') === 'paid' ? 'Completed' : 'Payment pending'
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

/**
 * Counter and storefront takings as one list, deduped by id in case a sync has
 * landed the same order in both.
 *
 * Both halves, deliberately. This used to read `store.orders` alone — the
 * till's own list — which meant a shop selling mostly online saw ₱0 across
 * every analytics card while its storefront was busy. Channel is a breakdown
 * of revenue here, not a filter on it.
 */
const allLiveOrders = computed(() => {
  const byId = new Map<string, OrderSummary>()
  for (const order of [...store.orders, ...store.onlineOrders]) {
    if (!order.voidedAt) byId.set(order.id, order)
  }
  return Array.from(byId.values())
})

const periodOrders = computed(() =>
  allLiveOrders.value.filter((order) => inBounds(order, bounds.value.start, bounds.value.end)),
)
const priorOrders = computed(() =>
  range.value === 'all'
    ? []
    : allLiveOrders.value.filter((order) => inBounds(order, bounds.value.prevStart, bounds.value.prevEnd)),
)

function percentDelta(current: number, previous: number) {
  if (previous === 0) {
    return null
  }

  const percentage = ((current - previous) / previous) * 100
  return {
    value: `${Math.abs(Math.round(percentage))}%`,
    positive: percentage >= 0,
  }
}

function delta(current: number, previous: number) {
  return range.value === 'all' ? null : percentDelta(current, previous)
}

function sumCents(orders: OrderSummary[]) {
  return orders.reduce((total, order) => total + order.totalCents, 0)
}

const totalRevenue = computed(() => sumCents(periodOrders.value))
const previousRevenue = computed(() => sumCents(priorOrders.value))

// ── Today, whatever the range selector says ────────────────────────────────
//
// The four cards at the top answer "what is happening right now", so their
// numbers are always today's. The selector governs everything below them.

const todayStart = computed(() => startOfDay(new Date()))

const todaysOrders = computed(() =>
  allLiveOrders.value.filter((order) =>
    inBounds(order, todayStart.value, new Date(todayStart.value.getTime() + 86400000)),
  ),
)

/**
 * Yesterday up to this hour, not all of yesterday — comparing 10am against a
 * full trading day would show every morning as a collapse.
 */
const yesterdaySoFar = computed(() => {
  const start = new Date(todayStart.value.getTime() - 86400000)
  const elapsed = Date.now() - todayStart.value.getTime()
  return allLiveOrders.value.filter((order) => inBounds(order, start, new Date(start.getTime() + elapsed)))
})

const todaysSales = computed(() => sumCents(todaysOrders.value))
const yesterdaysSalesSoFar = computed(() => sumCents(yesterdaySoFar.value))

const rangeCaption = computed(() => {
  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
  if (range.value === 'today') {
    return formatter.format(now)
  }
  if (range.value === 'all') {
    return 'All time'
  }

  const endForCaption = new Date(bounds.value.end.getTime() - 86400000)
  return `${formatter.format(bounds.value.start)} – ${formatter.format(endForCaption)}`
})

const storeName = computed(() => store.settings.businessName || 'Unnamed store')

/**
 * Fixed at mount rather than live: a shop that leaves this page open all day
 * does not need the heading to change under it at noon.
 */
const greeting = (() => {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning,'
  if (hour < 18) return 'Good afternoon,'
  return 'Good evening,'
})()

// ── Sales Overview ─────────────────────────────────────────────────────────

const salesSeries = computed(() => {
  const days: { key: string; label: string; totalCents: number }[] = []
  const cursor = new Date(bounds.value.start)
  const dayFormat = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric' })

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

  // Stop at today, not at the end of the period. A month-to-date line that
  // drops to zero on the 15th and stays flat to the 30th reads as a shop that
  // stopped trading, rather than one whose month simply is not over.
  const endOfToday = startOfDay(new Date()).getTime() + 86400000
  const lastDay = Math.min(bounds.value.end.getTime(), endOfToday)

  while (cursor.getTime() < lastDay && days.length < 31) {
    days.push({
      key: cursor.toISOString(),
      label: dayFormat.format(cursor),
      totalCents: 0,
    })
    cursor.setDate(cursor.getDate() + 1)
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

function niceCeil(value: number) {
  if (value <= 0) {
    return 1
  }

  const exponent = Math.floor(Math.log10(value))
  const fraction = value / 10 ** exponent
  const niceFraction = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10
  return niceFraction * 10 ** exponent
}

const salesAxisMax = computed(() =>
  niceCeil(Math.max(...salesSeries.value.map((point) => point.totalCents), 1)),
)

function formatAxisValue(cents: number) {
  const pesos = cents / 100
  if (pesos >= 1000) {
    return `₱${(pesos / 1000).toFixed(pesos % 1000 === 0 ? 0 : 1)}K`
  }
  return `₱${Math.round(pesos)}`
}

const salesYAxisTicks = computed(() =>
  [4, 3, 2, 1, 0].map((step) => formatAxisValue((salesAxisMax.value * step) / 4)),
)

// ── The line chart ──────────────────────────────────────────────────────────
//
// Drawn in a 100×100 viewBox with a non-uniform preserveAspectRatio, so the
// path stretches to whatever width the card gets without any measuring in JS.

const CHART_VIEWBOX = 100

const chartPoints = computed(() => {
  const series = salesSeries.value
  if (series.length === 0) return []

  const step = series.length === 1 ? 0 : CHART_VIEWBOX / (series.length - 1)
  return series.map((point, index) => ({
    ...point,
    x: series.length === 1 ? CHART_VIEWBOX / 2 : index * step,
    // SVG y grows downward, so a bigger number sits closer to zero.
    y: CHART_VIEWBOX - (point.totalCents / salesAxisMax.value) * CHART_VIEWBOX,
  }))
})

const chartLinePath = computed(() =>
  chartPoints.value.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(2)},${point.y.toFixed(2)}`).join(' '),
)

/** The same line, closed along the baseline, for the tint under it. */
const chartAreaPath = computed(() => {
  const points = chartPoints.value
  if (points.length === 0) return ''
  const last = points[points.length - 1]
  return `${chartLinePath.value} L${last.x.toFixed(2)},${CHART_VIEWBOX} L${points[0].x.toFixed(2)},${CHART_VIEWBOX} Z`
})

/** Enough labels to read the axis, never so many that they collide. */
const chartLabels = computed(() => {
  const series = salesSeries.value
  if (series.length === 0) return []
  const step = Math.max(1, Math.ceil(series.length / 8))
  return series
    .map((point, index) => ({ label: point.label, index }))
    .filter((entry) => entry.index % step === 0)
})

// ── Sales by Channel ───────────────────────────────────────────────────────

const DONUT_CIRCUMFERENCE = 100

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
  // Darkest first: the ring reads as one shape shading outward, rather than
  // four unrelated colours competing at the same weight.
  const shades = ['var(--donut-1)', 'var(--donut-2)', 'var(--donut-3)', 'var(--donut-4)']

  const rows = Array.from(totals.entries())
    .map(([label, value]) => ({
      label,
      value,
      percentage: total > 0 ? Math.round((value / total) * 100) : 0,
    }))
    .filter((entry) => entry.value > 0)
    .sort((a, b) => b.value - a.value)

  // Each arc is a dash the length of its share, offset past everything before
  // it. One circle element per segment, no arc maths.
  let consumed = 0
  return rows.map((row, index) => {
    const length = total > 0 ? (row.value / total) * DONUT_CIRCUMFERENCE : 0
    const segment = {
      ...row,
      color: shades[index % shades.length],
      dash: `${length.toFixed(3)} ${(DONUT_CIRCUMFERENCE - length).toFixed(3)}`,
      offset: (DONUT_CIRCUMFERENCE - consumed).toFixed(3),
    }
    consumed += length
    return segment
  })
})

/** Compact enough for the middle of a ring — "₱12,450", never "₱12,450.00". */
const donutTotal = computed(() => {
  const pesos = Math.round(totalRevenue.value / 100)
  return `₱${pesos.toLocaleString('en-PH')}`
})

// ── The tables ─────────────────────────────────────────────────────────────

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
    .map((product, index) => ({
      ...product,
      rank: index + 1,
      imageUrl: store.products.find((p) => p.id === product.id)?.imageUrl,
    }))
})

const recentOrders = computed(() =>
  periodOrders.value
    .slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 5)
    .map((order) => ({
      key: order.id,
      id: `#${order.ticketNumber}`,
      customerName: customerNameFor(order),
      items: order.items.reduce((count, item) => count + item.quantity, 0),
      amount: order.totalCents,
      status: order.status !== 'served' ? orderStatusLabel(order.status) : paymentStatusFor(order),
      settled: order.status === 'served' && !isUnpaid(order),
      time: relativeTime(order.createdAt),
    })),
)
</script>

<template>
  <div class="dashboard-page" :class="{ 'dashboard-page--dark': darkModeEnabled }">
    <!-- ── Hero ───────────────────────────────────────────────────────────
         Greeting, the period, and the one button a shop opens this page to
         press. The shop's own illustration sits at the far right where it
         costs nothing — the copy beside it is the only thing there that has
         to stay readable when the panel narrows. -->
    <section class="hero">
      <div class="hero__intro">
        <p class="hero__greeting">{{ greeting }}</p>
        <h1 class="hero__title">{{ storeName }}</h1>
        <p class="hero__copy">Here's what's happening with your store today.</p>

        <div class="hero__actions">
          <RouterLink v-if="auth.canAccess('orders')" class="hero__cta hero__cta--ghost" to="/orders">
            <span>View orders</span>
            <ArrowRight :size="16" />
          </RouterLink>
          <RouterLink v-if="auth.canAccess('register')" class="hero__cta" to="/register">
            <ShoppingCart :size="16" />
            <span>Open register</span>
          </RouterLink>
        </div>
      </div>

      <div class="hero__period">
        <div class="hero__range">
          <Calendar :size="16" />
          <span>{{ rangeCaption }}</span>
          <ChevronDown :size="16" class="hero__range-caret" />
        </div>
        <RangeSelector v-model="range" />
      </div>

      <!-- Inline SVG rather than the source PNG: the artwork was supplied on a
           black ground, which cannot sit on the green hero, and drawing it
           keeps it crisp at any density with no extra request. -->
      <div class="hero__art" aria-hidden="true">
        <div class="hero__art-copy">
          <svg class="hero__sprout" viewBox="0 0 34 30" fill="none">
            <!-- The sprout: a centre leaf between two lower ones, with the
                 four short rays the source art sets around it. -->
            <path d="M17 24v-7" stroke="#1b7a45" stroke-width="1.8" stroke-linecap="round" />
            <path
              d="M17 6.5c2.6 2.4 3.4 5.6 2.7 8.1-.5 1.8-1.6 2.9-2.7 3.6-1.1-.7-2.2-1.8-2.7-3.6-.7-2.5.1-5.7 2.7-8.1Z"
              fill="url(#heroLeafA)"
              stroke="#1b7a45"
              stroke-width="1.2"
              stroke-linejoin="round"
            />
            <path
              d="M14.4 18.6c-2.6.9-5.2.4-6.8-.8-1.2-.9-1.8-2-2-3 1.9-.9 4.4-1.3 6.6-.3 1.4.7 2.2 2.1 2.2 4.1Z"
              fill="url(#heroLeafB)"
              stroke="#1b7a45"
              stroke-width="1.2"
              stroke-linejoin="round"
            />
            <path
              d="M19.6 18.6c2.6.9 5.2.4 6.8-.8 1.2-.9 1.8-2 2-3-1.9-.9-4.4-1.3-6.6-.3-1.4.7-2.2 2.1-2.2 4.1Z"
              fill="url(#heroLeafB)"
              stroke="#1b7a45"
              stroke-width="1.2"
              stroke-linejoin="round"
            />
            <!-- Short and thin on purpose: this renders at 34px, where a ray
                 any heavier reads as a fourth leaf rather than a flourish. -->
            <path
              d="M7.5 9 9.4 10.9M26.5 9 24.6 10.9M6 20.5l2-.8M28 20.5l-2-.8"
              stroke="#bbf451"
              stroke-width="1.5"
              stroke-linecap="round"
            />
            <defs>
              <linearGradient id="heroLeafA" x1="17" y1="6.5" x2="17" y2="18.2" gradientUnits="userSpaceOnUse">
                <stop stop-color="#b6f5cd" />
                <stop offset="1" stop-color="#4cd989" />
              </linearGradient>
              <linearGradient id="heroLeafB" x1="17" y1="13" x2="17" y2="19" gradientUnits="userSpaceOnUse">
                <stop stop-color="#a5f0c1" />
                <stop offset="1" stop-color="#46cf80" />
              </linearGradient>
            </defs>
          </svg>

          <p class="hero__art-text">
            Keep selling,<br>
            keep <span>growing!</span>
          </p>
        </div>

        <svg class="hero__shop" viewBox="0 0 132 104" fill="none">
          <!-- Sparkle, top right -->
          <path
            d="M114 22 117 8M123 24l7-9M125 32l8-3"
            stroke="#ffd43b"
            stroke-width="4"
            stroke-linecap="round"
          />

          <!-- Wall first: the awning's tabs hang over it. -->
          <rect x="22" y="33" width="80" height="45" fill="url(#heroWall)" />
          <rect x="26" y="10" width="72" height="10" rx="5" fill="url(#heroHeader)" />

          <!-- Striped awning. One stripe per scallop, clipped to the shape so
               the scalloped edge and the stripes cannot drift apart. -->
          <g clip-path="url(#heroAwning)">
            <rect x="16" y="20" width="11.5" height="21" fill="#f0fbf4" />
            <rect x="27.5" y="20" width="11.5" height="21" fill="#3cc47a" />
            <rect x="39" y="20" width="11.5" height="21" fill="#f0fbf4" />
            <rect x="50.5" y="20" width="11.5" height="21" fill="#3cc47a" />
            <rect x="62" y="20" width="11.5" height="21" fill="#f0fbf4" />
            <rect x="73.5" y="20" width="11.5" height="21" fill="#3cc47a" />
            <rect x="85" y="20" width="11.5" height="21" fill="#f0fbf4" />
            <rect x="96.5" y="20" width="11.5" height="21" fill="#3cc47a" />
          </g>

          <!-- Doorway -->
          <path d="M48 78V60a14 14 0 0 1 28 0v18Z" fill="url(#heroDoor)" />

          <!-- Plinth -->
          <rect x="18" y="76" width="88" height="13" rx="6.5" fill="url(#heroBase)" />

          <defs>
            <!-- Eight tabs hanging off the bottom edge. Drawn right-to-left
                 with sweep-flag 1, which is the direction that bulges
                 downward in SVG's y-down coordinates. -->
            <clipPath id="heroAwning">
              <path
                d="M16 20h92v14a5.75 5.75 0 0 1-11.5 0 5.75 5.75 0 0 1-11.5 0 5.75 5.75 0 0 1-11.5 0 5.75 5.75 0 0 1-11.5 0 5.75 5.75 0 0 1-11.5 0 5.75 5.75 0 0 1-11.5 0 5.75 5.75 0 0 1-11.5 0 5.75 5.75 0 0 1-11.5 0Z"
              />
            </clipPath>
            <linearGradient id="heroWall" x1="62" y1="33" x2="62" y2="78" gradientUnits="userSpaceOnUse">
              <stop stop-color="#f2fcf6" />
              <stop offset="1" stop-color="#d9f3e3" />
            </linearGradient>
            <linearGradient id="heroHeader" x1="62" y1="10" x2="62" y2="20" gradientUnits="userSpaceOnUse">
              <stop stop-color="#5fdb96" />
              <stop offset="1" stop-color="#25a862" />
            </linearGradient>
            <linearGradient id="heroDoor" x1="62" y1="46" x2="62" y2="78" gradientUnits="userSpaceOnUse">
              <stop stop-color="#1aa15a" />
              <stop offset="1" stop-color="#0c7a41" />
            </linearGradient>
            <linearGradient id="heroBase" x1="62" y1="76" x2="62" y2="89" gradientUnits="userSpaceOnUse">
              <stop stop-color="#38bd76" />
              <stop offset="1" stop-color="#1c9155" />
            </linearGradient>
          </defs>
        </svg>
      </div>
    </section>

    <p v-if="opsError" class="ops-error" role="alert">{{ opsError }}</p>

    <!-- ── Row 1: the day at a glance ─────────────────────────────────────
         One money metric and three states. Always today, never the selected
         period — a shop checking in at 3pm is asking about 3pm. -->
    <section class="kpis" aria-label="Today at a glance">
      <article class="kpi kpi--good">
        <span class="kpi__icon"><ShoppingBag :size="20" /></span>
        <div class="kpi__body">
          <p class="kpi__label">Today's Sales</p>
          <div class="kpi__value-row">
            <strong class="kpi__value">{{ formatCurrency(todaysSales) }}</strong>
            <span
              v-if="percentDelta(todaysSales, yesterdaysSalesSoFar)"
              class="kpi__delta"
              :class="percentDelta(todaysSales, yesterdaysSalesSoFar)!.positive ? 'kpi__delta--up' : 'kpi__delta--down'"
            >
              {{ percentDelta(todaysSales, yesterdaysSalesSoFar)!.positive ? '↑' : '↓' }}
              {{ percentDelta(todaysSales, yesterdaysSalesSoFar)!.value }}
            </span>
          </div>
          <p class="kpi__caption">vs. same time yesterday</p>
        </div>
      </article>

      <article class="kpi kpi--good">
        <span class="kpi__icon"><ShoppingCart :size="20" /></span>
        <div class="kpi__body">
          <p class="kpi__label">Orders Today</p>
          <div class="kpi__value-row">
            <strong class="kpi__value">{{ todaysOrders.length.toLocaleString('en-PH') }}</strong>
            <span
              v-if="percentDelta(todaysOrders.length, yesterdaySoFar.length)"
              class="kpi__delta"
              :class="percentDelta(todaysOrders.length, yesterdaySoFar.length)!.positive ? 'kpi__delta--up' : 'kpi__delta--down'"
            >
              {{ percentDelta(todaysOrders.length, yesterdaySoFar.length)!.positive ? '↑' : '↓' }}
              {{ percentDelta(todaysOrders.length, yesterdaySoFar.length)!.value }}
            </span>
          </div>
          <p class="kpi__caption">vs. yesterday</p>
        </div>
      </article>

      <article class="kpi" :class="ordersNeedingAction.length > 0 ? 'kpi--warn' : 'kpi--good'">
        <span class="kpi__icon"><Clock :size="20" /></span>
        <div class="kpi__body">
          <p class="kpi__label">Pending Orders</p>
          <div class="kpi__value-row">
            <strong class="kpi__value">{{ ordersNeedingAction.length.toLocaleString('en-PH') }}</strong>
            <span v-if="ordersNeedingAction.length > 0" class="kpi__badge kpi__badge--warn">Needs action</span>
          </div>
          <p class="kpi__caption">
            {{ ordersNeedingAction.length > 0 ? 'Awaiting preparation' : 'Everything is served and settled' }}
          </p>
        </div>
      </article>

      <article class="kpi" :class="stockToWatchCount > 0 ? 'kpi--danger' : 'kpi--good'">
        <span class="kpi__icon"><TriangleAlert :size="20" /></span>
        <div class="kpi__body">
          <p class="kpi__label">Low Stock Items</p>
          <div class="kpi__value-row">
            <strong class="kpi__value">{{ stockToWatchCount.toLocaleString('en-PH') }}</strong>
            <span v-if="store.outOfStockProducts.length > 0" class="kpi__badge kpi__badge--danger">Attention needed</span>
          </div>
          <p class="kpi__caption">
            {{ stockToWatchCount > 0 ? 'Items below reorder level' : 'Every tracked product is stocked' }}
          </p>
        </div>
      </article>
    </section>

    <!-- ── Row 2: the queue, and the state of the shop ─────────────────── -->
    <section class="grid grid--queue">
      <ChartCard
        class="card"
        title="Orders needing action"
        summary="Everything still being made, or made but not paid for."
      >
        <template #action>
          <RouterLink v-if="auth.canAccess('orders')" class="card__link" to="/orders">
            <span>View all orders</span>
            <ArrowRight :size="14" />
          </RouterLink>
        </template>

        <!-- Two jobs share this list: settling payments and making food. The
             counts sit on the tabs so neither has to open the other's filter
             to find out whether it is empty. -->
        <div class="queue-tabs" role="tablist" aria-label="Filter the queue">
          <button
            v-for="tab in queueTabs"
            :key="tab.key"
            class="queue-tab"
            :class="{ 'queue-tab--active': queueFilter === tab.key }"
            type="button"
            role="tab"
            :aria-selected="queueFilter === tab.key"
            @click="queueFilter = tab.key"
          >
            {{ tab.label }} ({{ tab.count }})
          </button>
        </div>

        <p v-if="visibleQueue.length === 0" class="empty">
          {{ ordersNeedingAction.length === 0
            ? 'Nothing waiting. Every order is served and settled.'
            : 'Nothing in this filter right now.' }}
        </p>

        <ul v-else class="queue">
          <li v-for="order in visibleQueue" :key="order.id" class="queue-row">
            <span class="queue-row__icon"><ShoppingBag :size="16" /></span>

            <div class="queue-row__who">
              <strong class="queue-row__ticket">#{{ order.ticketNumber }}</strong>
              <span class="queue-row__customer">{{ customerNameFor(order) }}</span>
            </div>

            <p class="queue-row__basket">
              {{ order.items.length }} item{{ order.items.length === 1 ? '' : 's' }}
              <i aria-hidden="true">·</i>
              {{ formatCurrency(order.totalCents) }}
            </p>

            <div class="queue-row__state">
              <span
                class="chip"
                :class="isUnpaid(order) ? 'chip--danger' : 'chip--warn'"
              >{{ isUnpaid(order) ? 'Payment pending' : orderStatusLabel(order.status) }}</span>
              <span class="queue-row__time">{{ relativeTime(order.createdAt) }}</span>
            </div>

            <div class="queue-row__actions">
              <button
                v-if="order.status !== 'served'"
                class="btn"
                type="button"
                :disabled="busyOrderId === order.id"
                @click="advanceStatus(order)"
              >
                Mark {{ nextOrderStatus(order.status) === 'ready' ? 'ready' : 'served' }}
              </button>
              <button
                v-if="isOnlineOrder(order) && isUnpaid(order)"
                class="btn btn--primary"
                type="button"
                @click="settlingOrder = order"
              >
                Settle payment
              </button>
            </div>
          </li>
        </ul>
      </ChartCard>

      <ChartCard class="card" title="Store Status" summary="Sync, register, shift and the link customers use.">
        <template #action>
          <span class="status-pill" :class="allSystemsOperational ? 'status-pill--good' : 'status-pill--warn'">
            <i aria-hidden="true" />
            {{ allSystemsOperational ? 'All systems operational' : 'Needs attention' }}
          </span>
        </template>

        <ul class="status">
          <li v-for="status in storeStatuses" :key="status.key" class="status-row">
            <span class="status-row__label">
              <component :is="status.icon" :size="16" />
              <span>{{ status.label }}</span>
            </span>
            <span class="chip" :class="`chip--${status.tone}`">
              <i class="chip__dot" aria-hidden="true" />
              {{ status.value }}
            </span>
            <span class="status-row__caption">{{ status.caption }}</span>
          </li>

          <li class="status-row">
            <span class="status-row__label">
              <Link2 :size="16" />
              <span>Store Link</span>
            </span>
            <code class="status-row__link">{{ storefrontHandle }}</code>
            <span class="status-row__actions">
              <template v-if="storefrontUrl">
                <button class="btn btn--compact" type="button" @click="copyStorefrontLink">
                  <component :is="linkCopied ? Check : Copy" :size="14" />
                  <span>{{ linkCopied ? 'Copied' : 'Copy' }}</span>
                </button>
                <a class="btn btn--compact btn--icon" :href="storefrontUrl" target="_blank" rel="noopener">
                  <ExternalLink :size="14" />
                  <span class="sr-only">Open your storefront</span>
                </a>
              </template>
            </span>
          </li>
        </ul>

        <div class="status-foot">
          <RouterLink v-if="auth.canAccess('settings')" class="btn" to="/settings">
            <Settings :size="14" />
            <span>Store settings</span>
          </RouterLink>
        </div>
      </ChartCard>
    </section>

    <!-- ── Row 3: stock, the trend, and where the money came from ─────── -->
    <section class="grid grid--analytics">
      <ChartCard
        class="card"
        title="Low stock / Stock to watch"
        summary="Out of stock first, then anything under its reorder point."
      >
        <template #action>
          <RouterLink v-if="auth.canAccess('inventory')" class="card__link" to="/inventory">
            <span>View all inventory</span>
            <ArrowRight :size="14" />
          </RouterLink>
        </template>

        <p v-if="stockAlerts.length === 0" class="empty">Every tracked product is above its reorder point.</p>

        <ul v-else class="stock">
          <li v-for="product in stockAlerts" :key="product.id" class="stock-row">
            <span class="stock-row__thumb">
              <img
                v-if="thumbFor(product.id, product.imageUrl)"
                :src="thumbFor(product.id, product.imageUrl)"
                :alt="product.name"
                loading="lazy"
                @error="markThumbBroken(product.id)"
              >
              <span v-else class="thumb-letter" aria-hidden="true">{{ initialFor(product.name) }}</span>
            </span>

            <div class="stock-row__who">
              <strong class="stock-row__name">{{ product.name }}</strong>
              <span class="stock-row__price">{{ formatCurrency(product.priceCents) }}</span>
            </div>

            <!-- Red at zero, amber below the reorder point. A shelf at zero is
                 losing sales right now, and one amber chip said that no louder
                 than "three left". -->
            <span
              class="chip"
              :class="stockSeverity(product.stockQty) === 'out' ? 'chip--danger' : 'chip--warn'"
            >
              <i v-if="stockSeverity(product.stockQty) !== 'out'" class="chip__dot" aria-hidden="true" />
              {{ stockSeverity(product.stockQty) === 'out' ? 'Out of stock' : `Low stock (${product.stockQty} left)` }}
            </span>

            <!-- Split button: the amount a shop almost always adds, plus a
                 caret for the one that buys by the case. -->
            <div class="restock">
              <button
                class="restock__quick"
                type="button"
                :disabled="busyOrderId === product.id"
                @click="restock(product.id)"
              >
                Restock +{{ QUICK_RESTOCK }}
              </button>
              <button
                class="restock__more"
                type="button"
                :disabled="busyOrderId === product.id"
                :aria-expanded="openRestockMenu === product.id"
                :aria-label="`Other restock amounts for ${product.name}`"
                @click="toggleRestockMenu(product.id)"
              >
                <ChevronDown :size="14" />
              </button>

              <div v-if="openRestockMenu === product.id" class="restock__menu" role="menu">
                <button
                  v-for="quantity in RESTOCK_QUANTITIES"
                  :key="quantity"
                  type="button"
                  role="menuitem"
                  class="restock__option"
                  :disabled="busyOrderId === product.id"
                  @click="restock(product.id, quantity)"
                >
                  Restock +{{ quantity }}
                </button>
              </div>
            </div>
          </li>
        </ul>

        <!-- Click-away for the open menu. A transparent sheet costs nothing
             and beats a document listener that has to guess at its own edges. -->
        <div v-if="openRestockMenu" class="restock__backdrop" @click="openRestockMenu = ''" />
      </ChartCard>

      <ChartCard class="card" title="Sales Overview" summary="Daily revenue trend for the selected period.">
        <template #action>
          <span class="card__badge">Daily</span>
        </template>

        <div class="sales">
          <div class="sales__headline">
            <strong class="sales__total">{{ formatCurrency(totalRevenue) }}</strong>
            <span
              v-if="delta(totalRevenue, previousRevenue)"
              class="kpi__delta"
              :class="delta(totalRevenue, previousRevenue)!.positive ? 'kpi__delta--up' : 'kpi__delta--down'"
            >
              {{ delta(totalRevenue, previousRevenue)!.positive ? '↑' : '↓' }}
              {{ delta(totalRevenue, previousRevenue)!.value }}
            </span>
          </div>
          <p class="sales__caption">Total sales for this period</p>

          <div class="sales__plot">
            <div class="sales__yaxis">
              <span v-for="tick in salesYAxisTicks" :key="tick">{{ tick }}</span>
            </div>

            <div class="sales__canvas">
              <div class="sales__gridlines" aria-hidden="true">
                <span v-for="row in 5" :key="row" />
              </div>

              <!-- Non-uniform preserveAspectRatio: the 100×100 path stretches
                   to whatever width the card gets, so nothing is measured in
                   JS and a resize costs no reflow. -->
              <svg
                v-if="chartPoints.length > 0"
                class="sales__svg"
                viewBox="0 0 100 100"
                preserveAspectRatio="none"
                role="img"
                :aria-label="`Daily sales for ${rangeCaption}`"
              >
                <path class="sales__area" :d="chartAreaPath" />
                <path class="sales__line" :d="chartLinePath" vector-effect="non-scaling-stroke" />
              </svg>

              <!-- The dots ride outside the stretched SVG so they stay round
                   at any card width. -->
              <span
                v-for="point in chartPoints"
                :key="point.key"
                class="sales__dot"
                :style="{ left: `${point.x}%`, top: `${point.y}%` }"
                aria-hidden="true"
              />
            </div>
          </div>

          <div class="sales__xaxis">
            <span
              v-for="entry in chartLabels"
              :key="entry.index"
              :style="{ left: `${salesSeries.length <= 1 ? 50 : (entry.index / (salesSeries.length - 1)) * 100}%` }"
            >{{ entry.label }}</span>
          </div>
        </div>
      </ChartCard>

      <ChartCard class="card" title="Sales by Channel" summary="Revenue distribution across sales channels.">
        <p v-if="channelBreakdown.length === 0" class="empty">No sales recorded for this period yet.</p>

        <div v-else class="donut">
          <div class="donut__ring">
            <svg viewBox="0 0 42 42" role="img" :aria-label="`Sales by channel for ${rangeCaption}`">
              <!-- r chosen so the circumference is 100, which makes every dash
                   length a percentage and drops the arc maths entirely. -->
              <circle class="donut__track" cx="21" cy="21" r="15.915" />
              <circle
                v-for="segment in channelBreakdown"
                :key="segment.label"
                class="donut__segment"
                cx="21"
                cy="21"
                r="15.915"
                :stroke="segment.color"
                :stroke-dasharray="segment.dash"
                :stroke-dashoffset="segment.offset"
              />
            </svg>
            <div class="donut__center">
              <strong>{{ donutTotal }}</strong>
              <span>Total sales</span>
            </div>
          </div>

          <ul class="donut__legend">
            <li v-for="segment in channelBreakdown" :key="segment.label">
              <span class="donut__key">
                <i :style="{ background: segment.color }" aria-hidden="true" />
                {{ segment.label }}
              </span>
              <span class="donut__figure">
                {{ formatCurrency(segment.value) }} ({{ segment.percentage }}%)
              </span>
            </li>
          </ul>
        </div>
      </ChartCard>
    </section>

    <!-- ── Row 4: the record ──────────────────────────────────────────── -->
    <section class="grid grid--tables">
      <ChartCard class="card" title="Recent Orders" summary="Latest transactions for the selected date range.">
        <template #action>
          <RouterLink class="card__link" to="/orders">
            <span>View all orders</span>
            <ArrowRight :size="14" />
          </RouterLink>
        </template>

        <p v-if="recentOrders.length === 0" class="empty">No orders placed for this period yet.</p>
        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Order #</th>
                <th>Customer</th>
                <th>Items</th>
                <th>Total</th>
                <th>Status</th>
                <th>Time</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in recentOrders" :key="order.key">
                <td class="table__id">{{ order.id }}</td>
                <td>{{ order.customerName }}</td>
                <td>{{ order.items }}</td>
                <td>{{ formatCurrency(order.amount) }}</td>
                <td>
                  <span
                    class="chip"
                    :class="order.settled ? 'chip--good' : order.status === 'Payment pending' ? 'chip--danger' : 'chip--warn'"
                  >{{ order.settled ? 'Completed' : order.status }}</span>
                </td>
                <td class="table__time">{{ order.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </ChartCard>

      <ChartCard class="card" title="Top Products" summary="Best-performing products ranked by revenue.">
        <template #action>
          <RouterLink class="card__link" to="/products">
            <span>View all products</span>
            <ArrowRight :size="14" />
          </RouterLink>
        </template>

        <p v-if="topProducts.length === 0" class="empty">No products sold for this period yet.</p>
        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>#</th>
                <th>Product</th>
                <th>Units Sold</th>
                <th>Revenue</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="product in topProducts" :key="product.id">
                <td class="table__rank">{{ product.rank }}</td>
                <td>
                  <div class="table__product">
                    <span class="table__thumb">
                      <img
                        v-if="thumbFor(product.id, product.imageUrl)"
                        :src="thumbFor(product.id, product.imageUrl)"
                        :alt="product.name"
                        loading="lazy"
                        @error="markThumbBroken(product.id)"
                      >
                      <span v-else class="thumb-letter" aria-hidden="true">{{ initialFor(product.name) }}</span>
                    </span>
                    <span>{{ product.name }}</span>
                  </div>
                </td>
                <td>{{ product.unitsSold.toLocaleString('en-PH') }}</td>
                <td>{{ formatCurrency(product.revenue) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </ChartCard>
    </section>

    <SettleOnlinePaymentSheet
      v-if="settlingOrder"
      :order="settlingOrder"
      @close="settlingOrder = null"
      @settled="settlingOrder = null"
    />
  </div>
</template>

<style scoped>
.dashboard-page {
  --accent: #1a6b3c;
  --accent-pressed: #155530;
  --accent-text-on: #ffffff;
  --success: #22c55e;
  --warning: #f5a623;
  --danger: #d64545;

  --page-text: #16211b;
  --page-muted: #5c6b62;
  --page-faint: #8a978f;
  --page-line: #e8ece9;
  --card-bg: #ffffff;
  --card-shadow: 0 1px 2px rgba(16, 40, 26, 0.04), 0 8px 24px rgba(16, 40, 26, 0.04);
  --row-bg: #f7faf8;
  --btn-bg: #ffffff;

  --chip-good-bg: #e6f6ec;
  --chip-good-text: #1a6b3c;
  --chip-warn-bg: #fdf0dc;
  --chip-warn-text: #9a6410;
  --chip-danger-bg: #fdeaea;
  --chip-danger-text: #b03636;
  --chip-idle-bg: #f1f4f2;
  --chip-idle-text: #5c6b62;

  --kpi-good-bg: #e9f7ee;
  --kpi-good-text: #1f7a46;
  --kpi-warn-bg: #fdf1de;
  --kpi-warn-text: #b47216;
  --kpi-danger-bg: #fdeaea;
  --kpi-danger-text: #c04141;

  /* Darkest first, so the ring reads as one shape shading outward rather than
     four colours competing at the same weight. */
  --donut-1: #1a6b3c;
  --donut-2: #35a866;
  --donut-3: #7fd4a1;
  --donut-4: #d8e6dd;

  /* The supplied banner, with the gradient kept behind it — that is what
     paints while the image is still loading, and what shows if it 404s. */
  --hero-image: url('/hero/dashboard-hero.jpg');
  --hero-bg:
    radial-gradient(circle at 88% 12%, rgba(255, 255, 255, 0.1), transparent 42%),
    linear-gradient(118deg, #1f7a46 0%, #146436 62%, #0f5a2f 100%);
  --hero-cta: #bbf451;
  --hero-cta-text: #0a2f19;

  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  color: var(--page-text);
}

.dashboard-page--dark {
  --accent: #92dd73;
  --accent-pressed: #7dcb60;
  --accent-text-on: #08200f;
  --success: #6ee787;
  --warning: #f6c56b;
  --danger: #ff8f70;

  --page-text: #edf5ef;
  --page-muted: #a7b6aa;
  --page-faint: #76847a;
  --page-line: rgba(235, 245, 238, 0.09);
  --card-bg: #151c1a;
  --card-shadow: 0 18px 44px rgba(0, 0, 0, 0.32);
  --row-bg: rgba(255, 255, 255, 0.03);
  --btn-bg: #1b2421;

  --chip-good-bg: rgba(34, 197, 94, 0.18);
  --chip-good-text: #a3efb5;
  --chip-warn-bg: rgba(245, 166, 35, 0.18);
  --chip-warn-text: #ffd482;
  --chip-danger-bg: rgba(255, 143, 112, 0.18);
  --chip-danger-text: #ffb5a0;
  --chip-idle-bg: rgba(255, 255, 255, 0.05);
  --chip-idle-text: #a7b6aa;

  --kpi-good-bg: rgba(34, 197, 94, 0.14);
  --kpi-good-text: #7fe0a0;
  --kpi-warn-bg: rgba(246, 197, 107, 0.14);
  --kpi-warn-text: #f6c56b;
  --kpi-danger-bg: rgba(255, 143, 112, 0.14);
  --kpi-danger-text: #ff9d82;

  --donut-1: #bbf451;
  --donut-2: #5fc98a;
  --donut-3: #2f8f5a;
  --donut-4: rgba(255, 255, 255, 0.12);

  /* The banner is a fixed bright green that would glare against a dark page,
     so dark mode keeps the gradient it can actually tune. */
  --hero-image: none;
  --hero-bg:
    radial-gradient(circle at 88% 12%, rgba(255, 255, 255, 0.06), transparent 42%),
    linear-gradient(118deg, #124f2c 0%, #0d3f23 62%, #09301b 100%);
}

/* ── Hero ─────────────────────────────────────────────────────────────── */

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 28px;
  padding: 26px 28px;
  border-radius: 16px;
  /* Stretched, not cropped: the banner is an abstract gradient whose whole
     composition — dark at the left where the copy sits, bright at the right
     behind the shopfront — has to map onto the card. `cover` on a panel this
     wide would crop straight through the middle of it. */
  background:
    var(--hero-image) center / 100% 100% no-repeat,
    var(--hero-bg);
  color: #ffffff;
  overflow: hidden;
}

.hero__greeting {
  margin: 0 0 2px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 15px;
  font-weight: 500;
}

.hero__title {
  margin: 0;
  font-size: clamp(1.7rem, 2.6vw, 2.1rem);
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.hero__copy {
  margin: 6px 0 0;
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
}

.hero__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 18px;
}

.hero__cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 20px;
  border: none;
  border-radius: 999px;
  background: var(--hero-cta);
  color: var(--hero-cta-text);
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  transition: background var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.hero__cta:hover {
  background: #cdfc72;
}

.hero__cta--ghost {
  background: #ffffff;
  color: #16211b;
}

.hero__cta--ghost:hover {
  background: rgba(255, 255, 255, 0.9);
}

.hero__period {
  display: grid;
  gap: 10px;
  justify-items: stretch;
}

.hero__range {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

/* Decorative: the pill row below is the control, and a second opener would be
   two ways to do one thing. */
.hero__range-caret {
  margin-left: auto;
  opacity: 0.7;
}

.hero__art {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hero__art-copy {
  display: grid;
  justify-items: center;
  gap: 6px;
}

.hero__sprout {
  width: 34px;
  height: 30px;
}

.hero__art-text {
  margin: 0;
  color: #ffffff;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.01em;
  text-align: center;
}

/* The one word the source art picks out in lime. */
.hero__art-text span {
  color: var(--hero-cta);
}

.hero__shop {
  width: 132px;
  height: 104px;
  flex: none;
  /* The banner is at its brightest right here, and the shopfront's own walls
     are near-white — without a shadow the two dissolve into each other. */
  filter: drop-shadow(0 6px 14px rgba(4, 38, 20, 0.28));
}

/* ── Shared card furniture ────────────────────────────────────────────── */

.ops-error {
  margin: 0;
  padding: 12px 16px;
  border: 1px solid var(--chip-danger-bg);
  border-radius: 14px;
  background: var(--chip-danger-bg);
  color: var(--chip-danger-text);
  font-size: 14px;
}

.card__link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--accent);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.card__link:hover {
  color: var(--accent-pressed);
}

.card__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 14px;
  border: 1px solid var(--page-line);
  border-radius: 999px;
  background: var(--btn-bg);
  color: var(--page-muted);
  font-size: 13px;
  font-weight: 600;
}

.empty {
  margin: 0;
  padding: 22px 0;
  color: var(--page-faint);
  font-size: 14px;
  text-align: center;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.chip__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: none;
}

.chip--good   { background: var(--chip-good-bg);   color: var(--chip-good-text); }
.chip--warn   { background: var(--chip-warn-bg);   color: var(--chip-warn-text); }
.chip--danger { background: var(--chip-danger-bg); color: var(--chip-danger-text); }
.chip--idle   { background: var(--chip-idle-bg);   color: var(--chip-idle-text); }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 36px;
  padding: 0 15px;
  border: 1px solid var(--page-line);
  border-radius: 999px;
  background: var(--btn-bg);
  color: var(--page-text);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--accent) 34%, transparent);
  color: var(--accent);
}

.btn--primary {
  border-color: transparent;
  background: var(--accent);
  color: var(--accent-text-on);
}

.btn--primary:hover:not(:disabled) {
  background: var(--accent-pressed);
  color: var(--accent-text-on);
}

.btn--compact {
  min-height: 32px;
  padding: 0 12px;
}

.btn--icon {
  padding: 0 10px;
}

.btn:disabled {
  opacity: 0.5;
  cursor: default;
}

/* ── Row 1: KPIs ──────────────────────────────────────────────────────── */

.kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px;
  border-radius: 14px;
  background: var(--card-bg);
  box-shadow: var(--card-shadow);
}

/* The tinted disc is the card's whole category signal, so the surface itself
   stays white — four tinted cards would highlight nothing. */
.kpi__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: var(--kpi-good-bg);
  color: var(--kpi-good-text);
}

.kpi--warn .kpi__icon   { background: var(--kpi-warn-bg);   color: var(--kpi-warn-text); }
.kpi--danger .kpi__icon { background: var(--kpi-danger-bg); color: var(--kpi-danger-text); }

.kpi__body {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.kpi__label {
  margin: 0;
  color: var(--page-muted);
  font-size: 13px;
  font-weight: 500;
}

.kpi__value-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.kpi__value {
  font-size: clamp(1.3rem, 1.7vw, 1.55rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.kpi__delta {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.kpi__delta--up   { background: var(--chip-good-bg);   color: var(--chip-good-text); }
.kpi__delta--down { background: var(--chip-danger-bg); color: var(--chip-danger-text); }

.kpi__badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.kpi__badge--warn   { background: var(--chip-warn-bg);   color: var(--chip-warn-text); }
.kpi__badge--danger { background: var(--chip-danger-bg); color: var(--chip-danger-text); }

.kpi__caption {
  margin: 0;
  color: var(--page-faint);
  font-size: 12px;
}

/* ── Grids ────────────────────────────────────────────────────────────── */

.grid {
  display: grid;
  gap: 16px;
  align-items: start;
}

.grid--queue     { grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr); }
.grid--analytics { grid-template-columns: minmax(0, 1.05fr) minmax(0, 1.15fr) minmax(0, 0.8fr); }
.grid--tables    { grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); }

/* ── Row 2: the queue ─────────────────────────────────────────────────── */

.queue-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--page-line);
}

.queue-tab {
  min-height: 32px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: none;
  color: var(--page-muted);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.queue-tab:hover {
  color: var(--page-text);
}

.queue-tab--active {
  border-color: var(--page-line);
  background: var(--card-bg);
  box-shadow: 0 1px 2px rgba(16, 40, 26, 0.06);
  color: var(--page-text);
}

.queue {
  display: grid;
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.queue-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1.1fr) minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--row-bg);
}

.queue-row__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--chip-good-bg);
  color: var(--chip-good-text);
}

.queue-row__who {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.queue-row__ticket {
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.queue-row__customer,
.queue-row__basket {
  margin: 0;
  color: var(--page-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-row__basket i {
  font-style: normal;
  opacity: 0.6;
}

.queue-row__state {
  display: grid;
  gap: 3px;
  justify-items: start;
}

.queue-row__time {
  color: var(--page-faint);
  font-size: 11px;
}

.queue-row__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* ── Row 2: store status ──────────────────────────────────────────────── */

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.status-pill i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.status-pill--good { background: var(--chip-good-bg); color: var(--chip-good-text); }
.status-pill--warn { background: var(--chip-warn-bg); color: var(--chip-warn-text); }

.status {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.status-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1.1fr);
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--page-line);
}

.status-row:last-child {
  border-bottom: none;
}

.status-row__label {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--page-text);
  font-size: 14px;
  font-weight: 600;
}

.status-row__label svg {
  color: var(--page-faint);
  flex: none;
}

.status-row__caption {
  color: var(--page-muted);
  font-size: 12px;
  text-align: right;
}

.status-row__link {
  min-width: 0;
  overflow: hidden;
  color: var(--page-text);
  font-family: var(--font-mono, ui-monospace, monospace);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-row__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
}

.status-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* ── Row 3: stock ─────────────────────────────────────────────────────── */

.stock {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.stock-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--page-line);
}

.stock-row:last-child {
  border-bottom: none;
}

.stock-row__thumb {
  display: block;
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--row-bg);
}

.stock-row__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Stands in for a photo that is missing or would not load, in the same
   footprint so the rows stay aligned either way. */
.thumb-letter {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  background: var(--chip-good-bg);
  color: var(--chip-good-text);
  font-size: 13px;
  font-weight: 700;
}

.stock-row__who {
  display: grid;
  gap: 1px;
  min-width: 0;
}

/* Wraps to a second line rather than truncating. Grocery names run long
   ("MANG TOMAS LECHON SAUCE - REGULAR 550G") and "MANG TOMAS LEC…" is not a
   product anyone can pick off a shelf. */
.stock-row__name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}

.stock-row__price {
  color: var(--page-muted);
  font-size: 12px;
}

/* The restock split button */

.restock {
  position: relative;
  display: inline-flex;
  flex: none;
  border: 1px solid var(--page-line);
  border-radius: 999px;
  background: var(--btn-bg);
}

.restock__quick,
.restock__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  border: none;
  background: none;
  color: var(--page-text);
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.restock__quick {
  padding: 0 12px;
  border-radius: 999px 0 0 999px;
  white-space: nowrap;
}

.restock__more {
  padding: 0 8px 0 6px;
  border-left: 1px solid var(--page-line);
  border-radius: 0 999px 999px 0;
  color: var(--page-muted);
}

.restock__quick:hover:not(:disabled),
.restock__more:hover:not(:disabled) {
  background: var(--chip-good-bg);
  color: var(--accent);
}

.restock__quick:disabled,
.restock__more:disabled {
  opacity: 0.5;
  cursor: default;
}

.restock__menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 3;
  display: grid;
  min-width: 150px;
  padding: 6px;
  border: 1px solid var(--page-line);
  border-radius: 14px;
  background: var(--card-bg);
  box-shadow: 0 16px 36px rgba(6, 36, 15, 0.18);
}

.restock__option {
  padding: 9px 12px;
  border: none;
  border-radius: 9px;
  background: none;
  color: var(--page-text);
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.restock__option:hover:not(:disabled) {
  background: var(--chip-good-bg);
  color: var(--accent);
}

.restock__backdrop {
  position: fixed;
  inset: 0;
  z-index: 2;
}

/* ── Row 3: the line chart ────────────────────────────────────────────── */

.sales {
  display: grid;
  gap: 2px;
}

.sales__headline {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.sales__total {
  font-size: clamp(1.35rem, 1.9vw, 1.6rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.sales__caption {
  margin: 0 0 14px;
  color: var(--page-muted);
  font-size: 12px;
}

.sales__plot {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  height: 168px;
}

.sales__yaxis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  color: var(--page-faint);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.sales__canvas {
  position: relative;
  min-width: 0;
}

.sales__gridlines {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.sales__gridlines span {
  display: block;
  height: 0;
  border-top: 1px solid var(--page-line);
}

.sales__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.sales__area {
  fill: color-mix(in srgb, var(--donut-2) 18%, transparent);
}

.sales__line {
  fill: none;
  stroke: var(--donut-2);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.sales__dot {
  position: absolute;
  width: 5px;
  height: 5px;
  margin: -2.5px 0 0 -2.5px;
  border-radius: 50%;
  background: var(--donut-2);
  pointer-events: none;
}

.sales__xaxis {
  position: relative;
  height: 16px;
  margin-left: calc(2.6em + 10px);
  color: var(--page-faint);
  font-size: 11px;
}

.sales__xaxis span {
  position: absolute;
  top: 4px;
  transform: translateX(-50%);
  white-space: nowrap;
}

/* ── Row 3: the donut ─────────────────────────────────────────────────── */

.donut {
  display: grid;
  gap: 18px;
  justify-items: center;
}

.donut__ring {
  position: relative;
  width: 100%;
  max-width: 168px;
  aspect-ratio: 1;
}

.donut__ring svg {
  width: 100%;
  height: 100%;
  /* Start the first arc at twelve o'clock rather than three. */
  transform: rotate(-90deg);
}

.donut__track {
  fill: none;
  stroke: var(--page-line);
  stroke-width: 5;
}

.donut__segment {
  fill: none;
  stroke-width: 5;
  stroke-linecap: butt;
}

.donut__center {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 2px;
  text-align: center;
}

.donut__center strong {
  font-size: 1.1rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.donut__center span {
  color: var(--page-muted);
  font-size: 11px;
}

.donut__legend {
  display: grid;
  gap: 9px;
  width: 100%;
  margin: 0;
  padding: 0;
  list-style: none;
}

.donut__legend li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.donut__key {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--page-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.donut__key i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex: none;
}

.donut__figure {
  color: var(--page-text);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ── Row 4: the tables ────────────────────────────────────────────────── */

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th {
  padding: 0 0 10px;
  border-bottom: 1px solid var(--page-line);
  color: var(--page-faint);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-align: left;
  text-transform: uppercase;
  white-space: nowrap;
}

.table td {
  padding: 13px 0;
  border-bottom: 1px solid var(--page-line);
  color: var(--page-text);
  font-size: 13px;
  text-align: left;
  vertical-align: middle;
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.table th + th,
.table td + td {
  padding-left: 14px;
}

.table__id {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.table__time {
  color: var(--page-muted);
  white-space: nowrap;
}

.table__rank {
  width: 1%;
  color: var(--page-faint);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.table__product {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.table__thumb {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--row-bg);
}

.table__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ── The shared ChartCard shell ───────────────────────────────────────── */

:deep(.chart-card) {
  gap: 14px;
  padding: 20px;
  border-radius: 14px;
  background: var(--card-bg);
  box-shadow: var(--card-shadow);
}

:deep(.chart-card__title) {
  font-size: 1.02rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--page-text);
}

:deep(.chart-card__body) {
  justify-content: flex-start;
  min-height: 0;
}

/* The hero's own pills, which sit on dark green rather than on a card. */
:deep(.range-selector) {
  gap: 4px;
  padding: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
}

:deep(.range-btn) {
  min-height: 32px;
  padding: 0 14px;
  color: rgba(255, 255, 255, 0.84);
  font-size: 13px;
  font-weight: 600;
}

:deep(.range-btn--active) {
  background: #ffffff;
  color: #16211b;
  box-shadow: none;
}

:deep(.range-btn:not(.range-btn--active):hover) {
  color: #ffffff;
}

:deep(.range-btn:focus-visible) {
  outline: 2px solid #bbf451;
  outline-offset: 2px;
}

/* ── Narrower ─────────────────────────────────────────────────────────── */

@media (max-width: 1320px) {
  .grid--analytics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  /* The donut is the narrowest card, so it takes the full row rather than
     leaving a half-width gap beside the chart. */
  .grid--analytics > :last-child {
    grid-column: 1 / -1;
  }

  .donut {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    justify-items: stretch;
  }
}

@media (max-width: 1180px) {
  .kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  /* First to go: it is the only thing in the hero that says nothing. */
  .hero__art {
    display: none;
  }

  .queue-row {
    grid-template-columns: auto minmax(0, 1fr) auto;
    row-gap: 10px;
  }

  .queue-row__basket {
    grid-column: 2 / -1;
  }

  .queue-row__state {
    grid-column: 2;
  }

  .queue-row__actions {
    grid-column: 3;
  }
}

@media (max-width: 900px) {
  .grid--queue,
  .grid--analytics,
  .grid--tables,
  .grid--analytics > :last-child {
    grid-column: auto;
    grid-template-columns: minmax(0, 1fr);
  }

  .hero {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
    padding: 22px 20px;
  }

  .hero__period {
    justify-items: start;
  }
}

@media (max-width: 620px) {
  .kpis {
    grid-template-columns: minmax(0, 1fr);
  }

  .queue-row {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .queue-row__state,
  .queue-row__actions {
    grid-column: 2;
    justify-content: flex-start;
  }

  .stock-row {
    grid-template-columns: auto minmax(0, 1fr);
    row-gap: 10px;
  }

  .stock-row > .chip,
  .stock-row > .restock {
    grid-column: 2;
    justify-self: start;
  }

  /* Opens left of the caret, where a right-aligned popover would run off the
     screen edge. */
  .restock__menu {
    right: auto;
    left: 0;
  }

  .status-row {
    grid-template-columns: minmax(0, 1fr) auto;
    row-gap: 6px;
  }

  .status-row__caption,
  .status-row__actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
    text-align: left;
  }

  .donut {
    grid-template-columns: minmax(0, 1fr);
    justify-items: center;
  }
}
</style>
