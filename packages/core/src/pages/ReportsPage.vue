<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  ArrowRight, Ban, Banknote, CalendarDays, Clock3, Coffee, CreditCard, FileDown, Hash, Info,
  Landmark, Layers, Percent, PiggyBank, ReceiptText, Search, ShoppingBasket, ShoppingCart,
  Sparkles, Store, Tags, TrendingUp, UserRound, Utensils, Wallet, WalletCards, X,
  type LucideIcon,
} from '@lucide/vue'
import ColumnChart from '@pos/core/components/charts/ColumnChart.vue'
import DonutChart from '@pos/core/components/charts/DonutChart.vue'
import TrendArea from '@pos/core/components/charts/TrendArea.vue'
import RangeSelector, { type Range } from '@pos/core/components/RangeSelector.vue'
import { useAuthStore } from '@pos/core/stores/auth'
import { usePosStore } from '@pos/core/stores/pos'
import {
  businessModeLabel, formatCurrency,
  type BusinessMode, type OrderSummary, type PaymentMethod, type ShiftSummary,
} from '@pos/shared/index'

const store = usePosStore()
const auth = useAuthStore()

onMounted(() => {
  if (!store.isReady) void store.initialize()
  void store.refreshShiftHistory()
})

type ReportView = 'summary' | 'payments' | 'shift'
type Grain = 'hour' | 'day' | 'week' | 'month'
type Metric = 'sales' | 'orders' | 'average'

const range = ref<Range>('today')
const reportView = ref<ReportView>('summary')
const lineQuery = ref('')
const txQuery = ref('')
const metric = ref<Metric>('sales')
const now = new Date()

// ── The window, and the one before it ──────────────────────────────────────

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
  if (value === 'month') {
    return { start: new Date(today.getFullYear(), today.getMonth(), 1), end: new Date(today.getFullYear(), today.getMonth() + 1, 1) }
  }
  return { start: new Date(0), end: new Date(8640000000000000) }
}
/** The same length of time immediately before, or null for "All" — which has no before. */
function previousBounds(value: Range, current: Bounds): Bounds | null {
  if (value === 'all') return null
  const duration = current.end.getTime() - current.start.getTime()
  return { start: new Date(current.start.getTime() - duration), end: new Date(current.end.getTime() - duration) }
}
function inBounds(order: OrderSummary, value: Bounds) {
  const createdAt = new Date(order.createdAt)
  return createdAt >= value.start && createdAt < value.end
}

const bounds = computed(() => getBounds(range.value))
const priorBounds = computed(() => previousBounds(range.value, bounds.value))

/** Everything that happened, voids included — the audit view. */
const periodOrders = computed(() => store.orders.filter((order) => inBounds(order, bounds.value)))
/** What counts as revenue: a void reverses the whole order. */
const reportOrders = computed(() => periodOrders.value.filter((order) => !order.voidedAt))
const voidedOrders = computed(() => periodOrders.value.filter((order) => order.voidedAt))
const priorOrders = computed(() => {
  const window = priorBounds.value
  if (!window) return []
  return store.orders.filter((order) => !order.voidedAt && inBounds(order, window))
})

function sumOf(orders: OrderSummary[], pick: (order: OrderSummary) => number) {
  return orders.reduce((total, order) => total + pick(order), 0)
}

const grossSales = computed(() => sumOf(reportOrders.value, (order) => order.totalCents))
const taxCollected = computed(() => sumOf(reportOrders.value, (order) => order.taxCents))
// What came off, over the period. A discount nobody sees at the end of the day
// is indistinguishable from money gone missing.
const discountsGiven = computed(() => sumOf(reportOrders.value, (order) => order.discountCents ?? 0))
const netSales = computed(() => grossSales.value - taxCollected.value)
const orderCount = computed(() => reportOrders.value.length)
const averageOrder = computed(() => (orderCount.value > 0 ? Math.round(grossSales.value / orderCount.value) : 0))

const comparisonLabel = computed(() =>
  range.value === 'today' ? 'vs. yesterday'
  : range.value === 'week' ? 'vs. last week'
  : range.value === 'month' ? 'vs. last month'
  : '',
)

/**
 * Movement against the same length of time before this one. Null when there is
 * nothing to compare with — "All", or a previous period that took nothing, off
 * which a percentage would be a division by zero dressed up as growth.
 */
function deltaOf(current: number, prior: number): { value: string; positive: boolean } | null {
  if (priorBounds.value === null || prior <= 0) return null
  const change = ((current - prior) / prior) * 100
  if (!Number.isFinite(change)) return null
  return { value: `${change >= 0 ? '+' : '−'}${Math.abs(change).toFixed(1)}%`, positive: change >= 0 }
}

const priorGross = computed(() => sumOf(priorOrders.value, (order) => order.totalCents))
const priorTax = computed(() => sumOf(priorOrders.value, (order) => order.taxCents))
const priorDiscounts = computed(() => sumOf(priorOrders.value, (order) => order.discountCents ?? 0))

const kpis = computed(() => [
  {
    key: 'gross', icon: ShoppingCart, tone: 'leaf', label: 'Gross sales',
    value: formatCurrency(grossSales.value), delta: deltaOf(grossSales.value, priorGross.value),
    hint: 'Everything rung up in this period, tax included. Voided sales are left out.',
  },
  {
    key: 'net', icon: Layers, tone: 'mint', label: 'Net sales',
    value: formatCurrency(netSales.value), delta: deltaOf(netSales.value, priorGross.value - priorTax.value),
    hint: 'Gross sales less the VAT collected on them.',
  },
  {
    key: 'tax', icon: Percent, tone: 'amber', label: 'Tax collected',
    value: formatCurrency(taxCollected.value), delta: deltaOf(taxCollected.value, priorTax.value),
    hint: 'VAT charged at the rate each line carried when it was sold.',
  },
  {
    key: 'discounts', icon: Tags, tone: 'rose', label: 'Discounts given',
    value: formatCurrency(discountsGiven.value), delta: deltaOf(discountsGiven.value, priorDiscounts.value),
    hint: 'Taken off subtotals before tax, across every order in the period.',
  },
  {
    key: 'orders', icon: ReceiptText, tone: 'violet', label: 'Orders / Avg ticket',
    value: `${orderCount.value} / ${formatCurrency(averageOrder.value)}`,
    delta: deltaOf(orderCount.value, priorOrders.value.length),
    hint: 'Completed orders in the period, and the average value of one.',
  },
])

// ── Revenue trend ──────────────────────────────────────────────────────────
//
// The grain is mostly settled by the range — an hour is the only useful slice
// of one day, a day the only useful slice of one week — so the picker appears
// only where there is a real choice to make.

const GRAIN_LABEL: Record<Grain, string> = { hour: 'Hourly', day: 'Daily', week: 'Weekly', month: 'Monthly' }
const METRICS: { value: Metric; label: string }[] = [
  { value: 'sales', label: 'Sales' },
  { value: 'orders', label: 'Orders' },
  { value: 'average', label: 'Avg ticket' },
]

function grainsFor(value: Range): Grain[] {
  if (value === 'today') return ['hour']
  if (value === 'week') return ['day']
  if (value === 'month') return ['day', 'week']
  return ['day', 'week', 'month']
}

/**
 * "All" is whatever the shop's history happens to be — a fortnight for a shop
 * that opened this month, years for one that did not — so its opening grain is
 * read off that span rather than fixed. A month of trade plotted by month is
 * two points and a straight line between them.
 */
function defaultGrain(value: Range): Grain {
  if (value !== 'all') return grainsFor(value)[0]!
  const days = (trendSpan.value.end.getTime() - trendSpan.value.start.getTime()) / 86400000
  return days <= 70 ? 'day' : days <= 730 ? 'week' : 'month'
}

/**
 * What the chart actually spans.
 *
 * Never past today: a week or a month that has not finished yet would other-
 * wise trail a flat line along zero for the days still to come, which reads as
 * a shop that stopped trading rather than one whose month is not over.
 *
 * "All" has no bounds worth plotting — it runs from the epoch — so it takes
 * the span of the orders themselves instead.
 */
const trendSpan = computed<Bounds>(() => {
  const endOfToday = new Date(startOfDay(now).getTime() + 86400000)
  if (range.value !== 'all') {
    const { start, end } = bounds.value
    return { start, end: end > endOfToday ? endOfToday : end }
  }
  const times = reportOrders.value.map((order) => new Date(order.createdAt).getTime())
  if (times.length === 0) {
    const today = startOfDay(now)
    return { start: today, end: new Date(today.getTime() + 86400000) }
  }
  const first = startOfDay(new Date(Math.min(...times)))
  const last = startOfDay(new Date(Math.max(...times)))
  return { start: first, end: new Date(last.getTime() + 86400000) }
})

// Declared after the span it reads, so the immediate watcher below never sees
// `trendSpan` before it exists.
const grain = ref<Grain>('hour')
watch(range, (value) => { grain.value = defaultGrain(value) }, { immediate: true })
const grainOptions = computed(() => grainsFor(range.value))

function stepFrom(date: Date, step: Grain): Date {
  if (step === 'hour') return new Date(date.getTime() + 3600000)
  if (step === 'day') return new Date(date.getTime() + 86400000)
  if (step === 'week') return new Date(date.getTime() + 7 * 86400000)
  return new Date(date.getFullYear(), date.getMonth() + 1, 1)
}

function bucketStart(date: Date, step: Grain): Date {
  if (step === 'hour') return new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours())
  if (step === 'week') return getMonday(date)
  if (step === 'month') return new Date(date.getFullYear(), date.getMonth(), 1)
  return startOfDay(date)
}

function bucketLabel(date: Date, step: Grain): string {
  if (step === 'hour') return new Intl.DateTimeFormat('en-PH', { hour: 'numeric' }).format(date)
  if (step === 'month') return new Intl.DateTimeFormat('en-PH', { month: 'short', year: '2-digit' }).format(date)
  return new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric' }).format(date)
}

interface Bucket { key: string; label: string; start: number; end: number }

const trendBuckets = computed<Bucket[]>(() => {
  const span = trendSpan.value
  // A span of years sliced by week is thousands of points nobody can read, and
  // the axis would be a smear. Coarsen rather than draw it.
  let step = grain.value
  const listFor = (chosen: Grain) => {
    const list: Bucket[] = []
    let cursor = bucketStart(span.start, chosen)
    while (cursor < span.end && list.length < 400) {
      const next = stepFrom(cursor, chosen)
      list.push({ key: String(cursor.getTime()), label: bucketLabel(cursor, chosen), start: cursor.getTime(), end: next.getTime() })
      cursor = next
    }
    return list
  }
  let list = listFor(step)
  if (list.length > 160 && step !== 'month') {
    step = 'month'
    list = listFor(step)
  }
  return list
})

function measure(orders: OrderSummary[]): number {
  if (metric.value === 'orders') return orders.length
  const total = orders.reduce((sum, order) => sum + order.totalCents, 0)
  if (metric.value === 'average') return orders.length > 0 ? Math.round(total / orders.length) : 0
  return total
}

const trendSeries = computed(() => {
  // Buckets are aligned to the grain, so an order's own aligned start is the
  // key of the bucket it belongs to — no scanning the list for each one.
  const step = trendBuckets.value.length > 160 ? 'month' : grain.value
  const slots = new Map(trendBuckets.value.map((bucket) => [bucket.start, [] as OrderSummary[]]))
  for (const order of reportOrders.value) {
    slots.get(bucketStart(new Date(order.createdAt), step).getTime())?.push(order)
  }
  return trendBuckets.value.map((bucket) => ({ label: bucket.label, value: measure(slots.get(bucket.start) ?? []) }))
})

/**
 * A day that opened at eight reads badly with eight flat hours in front of it,
 * so the quiet ends are trimmed away — but only around takings that exist. A
 * day with none is shown whole rather than cropped to an invented shop window.
 */
function trimQuiet<T extends { value: number }>(series: T[]): T[] {
  const first = series.findIndex((point) => point.value > 0)
  if (first === -1) return series
  let last = series.length - 1
  while (last > first && series[last]!.value === 0) last -= 1
  return series.slice(Math.max(first - 1, 0), Math.min(last + 2, series.length))
}

const trendPoints = computed(() => (grain.value === 'hour' ? trimQuiet(trendSeries.value) : trendSeries.value))

function formatMetric(value: number): string {
  return metric.value === 'orders' ? String(Math.round(value)) : formatCurrency(value)
}

const trendCaption = computed(() => {
  const what = METRICS.find((option) => option.value === metric.value)?.label ?? 'Sales'
  return `${GRAIN_LABEL[grain.value]} ${what.toLowerCase()} for ${rangeCaption.value}`
})

// ── Peak hours ─────────────────────────────────────────────────────────────

const peakHours = computed(() => {
  const totals = Array.from({ length: 24 }, () => 0)
  for (const order of reportOrders.value) totals[new Date(order.createdAt).getHours()] += order.totalCents
  const hours = totals.map((value, hour) => ({
    key: String(hour),
    label: new Intl.DateTimeFormat('en-PH', { hour: 'numeric' }).format(new Date(2000, 0, 1, hour)),
    value,
  }))
  return trimQuiet(hours)
})

const busiestHour = computed(() => {
  const best = peakHours.value.reduce<{ label: string; value: number } | null>(
    (top, hour) => (hour.value > (top?.value ?? 0) ? hour : top),
    null,
  )
  return best && best.value > 0 ? best : null
})

// ── Payments ───────────────────────────────────────────────────────────────
//
// Three methods, because three is what an order can carry. GCash and the rest
// of the e-wallets are one bucket in the schema, and the label says so.

const PAYMENTS: { method: PaymentMethod; label: string; icon: LucideIcon; color: string }[] = [
  { method: 'cash', label: 'Cash', icon: Banknote, color: 'var(--rp-pay-cash)' },
  { method: 'ewallet', label: 'GCash / e-wallet', icon: WalletCards, color: 'var(--rp-pay-wallet)' },
  { method: 'card', label: 'Card', icon: CreditCard, color: 'var(--rp-pay-card)' },
]

const paymentTotals = computed(() =>
  PAYMENTS.map((entry) => {
    const orders = reportOrders.value.filter((order) => order.paymentMethod === entry.method)
    const total = sumOf(orders, (order) => order.totalCents)
    return {
      ...entry,
      key: entry.method,
      value: total,
      total,
      count: orders.length,
      average: orders.length > 0 ? Math.round(total / orders.length) : 0,
      share: grossSales.value > 0 ? (total / grossSales.value) * 100 : 0,
    }
  }),
)

const channelSplit = computed(() => {
  const online = reportOrders.value.filter((order) => order.channel === 'online')
  const counter = reportOrders.value.filter((order) => order.channel !== 'online')
  const unpaid = reportOrders.value.filter((order) => order.paymentStatus === 'unpaid')
  return [
    { key: 'counter', label: 'At the counter', icon: Store, count: counter.length, total: sumOf(counter, (order) => order.totalCents) },
    { key: 'online', label: 'Online orders', icon: ShoppingCart, count: online.length, total: sumOf(online, (order) => order.totalCents) },
    { key: 'unpaid', label: 'Awaiting payment', icon: Clock3, count: unpaid.length, total: sumOf(unpaid, (order) => order.totalCents) },
  ]
})

// ── Business modes ─────────────────────────────────────────────────────────

const MODE_ICON: Record<BusinessMode, LucideIcon> = {
  'coffee-shop': Coffee,
  grocery: ShoppingBasket,
  restaurant: Utensils,
  'nail-salon': Sparkles,
}

const businessModeTotals = computed(() => {
  const modes: BusinessMode[] = ['coffee-shop', 'grocery', 'restaurant', 'nail-salon']
  return modes
    .map((mode) => {
      const orders = reportOrders.value.filter((order) => order.businessMode === mode)
      const total = sumOf(orders, (order) => order.totalCents)
      return {
        mode,
        label: businessModeLabel(mode),
        icon: MODE_ICON[mode],
        orders: orders.length,
        total,
        share: grossSales.value > 0 ? (total / grossSales.value) * 100 : 0,
      }
    })
    .filter((entry) => entry.orders > 0)
    .sort((a, b) => b.total - a.total)
})

// ── Lines and tickets ──────────────────────────────────────────────────────

const allLines = computed(() => {
  const map = new Map<string, { name: string; quantity: number; salesCents: number }>()
  for (const order of reportOrders.value) {
    for (const item of order.items) {
      const existing = map.get(item.productId) ?? { name: item.name, quantity: 0, salesCents: 0 }
      existing.quantity += item.quantity
      existing.salesCents += item.lineTotalCents
      map.set(item.productId, existing)
    }
  }
  return Array.from(map.values()).sort((a, b) => b.salesCents - a.salesCents)
})

const topLines = computed(() => {
  const needle = lineQuery.value.trim().toLowerCase()
  const matched = allLines.value.filter((line) => needle === '' || line.name.toLowerCase().includes(needle))
  const peak = matched[0]?.salesCents ?? 0
  return matched.slice(0, 6).map((line, index) => ({
    ...line,
    rank: index + 1,
    /** Against the best-selling line, so the bars read as a ranking. */
    percent: peak > 0 ? Math.max((line.salesCents / peak) * 100, 4) : 0,
  }))
})

function formatTime(value: string) {
  return new Intl.DateTimeFormat('en-PH', { hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}
function formatFullDate(value: string) {
  return new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}
function itemCount(order: OrderSummary) {
  return order.items.reduce((sum, item) => sum + item.quantity, 0)
}
function paymentLabel(method: PaymentMethod) {
  return PAYMENTS.find((entry) => entry.method === method)?.label ?? method
}

const sortedOrders = computed(() => periodOrders.value.slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt)))
const recentTransactions = computed(() => sortedOrders.value.filter((order) => !order.voidedAt).slice(0, 6))

const transactionList = computed(() => {
  const needle = txQuery.value.trim().toLowerCase()
  if (needle === '') return sortedOrders.value.slice(0, 40)
  return sortedOrders.value
    .filter((order) =>
      order.ticketNumber.toLowerCase().includes(needle)
      || order.customerName.toLowerCase().includes(needle)
      || paymentLabel(order.paymentMethod).toLowerCase().includes(needle),
    )
    .slice(0, 40)
})

// ── Shift ──────────────────────────────────────────────────────────────────

function userName(userId?: string | null): string {
  if (!userId) return 'Not recorded'
  return auth.users.find((user) => user.id === userId)?.fullName ?? 'Not recorded'
}

const activeShift = computed(() => store.activeShift)
/** What the drawer should hold: the float plus cash taken, less what left it. */
const lastClosedShift = computed<ShiftSummary | null>(() => store.shiftHistory[0] ?? null)
const shiftForCash = computed(() => activeShift.value ?? lastClosedShift.value)

const cashTakings = computed(() => paymentTotals.value.find((entry) => entry.method === 'cash')?.total ?? 0)

const summaryRows = computed(() => {
  const shift = shiftForCash.value
  const closing = activeShift.value
    ? { label: 'Cash in drawer (expected)', value: formatCurrency(activeShift.value.expectedCashCents) }
    : shift?.closingCashCents != null
      ? { label: 'Closing cash (counted)', value: formatCurrency(shift.closingCashCents) }
      : { label: 'Closing cash', value: 'Not counted yet' }
  return [
    { key: 'period', icon: CalendarDays, label: 'Report period', value: rangeCaption.value, scope: '' },
    { key: 'completed', icon: ReceiptText, label: 'Completed orders', value: String(orderCount.value), scope: '' },
    {
      key: 'voided', icon: Ban, label: 'Voided orders',
      value: voidedOrders.value.length === 0 ? 'None' : `${voidedOrders.value.length} · ${formatCurrency(sumOf(voidedOrders.value, (order) => order.totalCents))}`,
      scope: '',
    },
    { key: 'cash', icon: Banknote, label: 'Cash takings', value: formatCurrency(cashTakings.value), scope: '' },
    {
      key: 'float', icon: PiggyBank, label: 'Opening float',
      value: shift ? formatCurrency(shift.openingCashCents) : 'No shift on record', scope: 'current shift',
    },
    { key: 'closing', icon: Landmark, label: closing.label, value: closing.value, scope: 'current shift' },
    {
      key: 'cashier', icon: UserRound, label: 'Cashier / Shift',
      value: shift ? userName(shift.openedByUserId) : 'No shift on record',
      badge: activeShift.value ? 'Open' : shift ? 'Closed' : '',
      scope: '',
    },
  ]
})

const shiftReport = computed(() => {
  const shift = activeShift.value
  if (!shift) return null
  return {
    openedAt: shift.openedAt,
    openedBy: userName(shift.openedByUserId),
    stats: [
      { key: 'opened', label: 'Opened', value: formatFullDate(shift.openedAt) },
      { key: 'sales', label: 'Total sales', value: formatCurrency(shift.totalSalesCents) },
      { key: 'orders', label: 'Orders', value: String(shift.orderCount) },
      { key: 'float', label: 'Opening float', value: formatCurrency(shift.openingCashCents) },
      { key: 'cash', label: 'Cash sales', value: formatCurrency(shift.cashSalesCents) },
      { key: 'in', label: 'Paid in', value: formatCurrency(shift.payInsCents) },
      { key: 'out', label: 'Paid out', value: formatCurrency(shift.payOutsCents) },
      { key: 'expected', label: 'Expected cash', value: formatCurrency(shift.expectedCashCents) },
    ],
    movements: shift.movements.slice(0, 6),
  }
})

function varianceClass(varianceCents?: number | null): string {
  if (!varianceCents) return 'is-even'
  return varianceCents < 0 ? 'is-short' : 'is-over'
}
function varianceLabel(varianceCents?: number | null): string {
  if (!varianceCents) return 'Exact'
  return varianceCents < 0 ? `${formatCurrency(Math.abs(varianceCents))} short` : `${formatCurrency(varianceCents)} over`
}

// ── Header ─────────────────────────────────────────────────────────────────

const rangeCaption = computed(() => {
  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
  if (range.value === 'today') return formatter.format(now)
  if (range.value === 'all') {
    return reportOrders.value.length === 0
      ? 'All time'
      : `All time · ${formatter.format(trendSpan.value.start)} – ${formatter.format(new Date(trendSpan.value.end.getTime() - 86400000))}`
  }
  return `${formatter.format(bounds.value.start)} – ${formatter.format(new Date(bounds.value.end.getTime() - 86400000))}`
})

function escapeCsv(value: string | number) { return `"${String(value).replace(/"/g, '""')}"` }

/**
 * The whole report as one file — the totals, then each breakdown under its own
 * heading, then every ticket. One download rather than four, because the thing
 * being filed at the end of a day is the report, not a table from it.
 */
function handleExport() {
  const rows: (string | number)[][] = [
    ['Omaykan report', store.settings.businessName],
    ['Period', rangeCaption.value],
    [],
    ['Totals'],
    ['Gross sales', (grossSales.value / 100).toFixed(2)],
    ['Net sales', (netSales.value / 100).toFixed(2)],
    ['Tax collected', (taxCollected.value / 100).toFixed(2)],
    ['Discounts given', (discountsGiven.value / 100).toFixed(2)],
    ['Completed orders', orderCount.value],
    ['Voided orders', voidedOrders.value.length],
    ['Average ticket', (averageOrder.value / 100).toFixed(2)],
    [],
    ['Payments', 'Orders', 'Share %', 'Total'],
    ...paymentTotals.value.map((entry) => [entry.label, entry.count, entry.share.toFixed(1), (entry.total / 100).toFixed(2)]),
    [],
    ['Business mode', 'Orders', 'Share %', 'Total'],
    ...businessModeTotals.value.map((entry) => [entry.label, entry.orders, entry.share.toFixed(1), (entry.total / 100).toFixed(2)]),
    [],
    ['Item', 'Qty sold', 'Revenue'],
    ...allLines.value.map((line) => [line.name, line.quantity, (line.salesCents / 100).toFixed(2)]),
    [],
    ['Time', 'Receipt', 'Items', 'Payment', 'Total', 'Status'],
    ...sortedOrders.value.map((order) => [
      formatFullDate(order.createdAt), order.ticketNumber, itemCount(order),
      paymentLabel(order.paymentMethod), (order.totalCents / 100).toFixed(2),
      order.voidedAt ? 'Voided' : 'Completed',
    ]),
  ]
  const csv = rows.map((row) => row.map(escapeCsv).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `report-${range.value}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

const VIEWS: { value: ReportView; label: string; icon: LucideIcon }[] = [
  { value: 'summary', label: 'Summary', icon: TrendingUp },
  { value: 'payments', label: 'Payments', icon: Wallet },
  { value: 'shift', label: 'Shift', icon: Landmark },
]
</script>

<template>
  <div class="reports-page">
    <section class="rp-hero">
      <div class="rp-hero__title">
        <span class="rp-hero__mark"><ReceiptText :size="24" /></span>
        <div>
          <h1>Reports</h1>
          <p>
            Review audit-friendly daily summaries, payment totals, shift cash position, and top-selling lines
            in a format that’s closer to an X report than a live dashboard.
          </p>
        </div>
      </div>
      <div class="rp-hero__tools">
        <span class="rp-datechip"><CalendarDays :size="16" />{{ rangeCaption }}</span>
        <RangeSelector v-model="range" />
        <button class="rp-export" type="button" @click="handleExport"><FileDown :size="17" />Export report</button>
      </div>
    </section>

    <section class="rp-kpis" aria-label="Report totals">
      <article v-for="kpi in kpis" :key="kpi.key" class="rp-kpi">
        <span class="rp-kpi__icon" :class="`rp-kpi__icon--${kpi.tone}`"><component :is="kpi.icon" :size="22" /></span>
        <div>
          <p>{{ kpi.label }} <span class="rp-kpi__info" :title="kpi.hint"><Info :size="12" /></span><span class="sr-only">{{ kpi.hint }}</span></p>
          <strong>{{ kpi.value }}</strong>
          <small v-if="kpi.delta" :class="{ 'is-down': !kpi.delta.positive }">
            {{ kpi.delta.positive ? '↑' : '↓' }} {{ kpi.delta.value }} <b>{{ comparisonLabel }}</b>
          </small>
          <small v-else class="is-muted">No earlier period to compare</small>
        </div>
      </article>
    </section>

    <div class="rp-tabs" role="tablist" aria-label="Report sections">
      <button
        v-for="view in VIEWS"
        :key="view.value"
        class="rp-tab"
        :class="{ 'is-on': reportView === view.value }"
        type="button"
        role="tab"
        :aria-selected="reportView === view.value"
        @click="reportView = view.value"
      >
        <component :is="view.icon" :size="16" />{{ view.label }}
      </button>
    </div>

    <!-- ── Summary ─────────────────────────────────────────────────────── -->
    <template v-if="reportView === 'summary'">
      <section class="rp-row rp-row--trend">
        <article class="rp-card rp-card--trend">
          <header>
            <div>
              <span class="rp-card__mark"><TrendingUp :size="17" /></span>
              <div><h2>Revenue trend</h2><p>{{ trendCaption }}</p></div>
            </div>
            <div class="rp-selects">
              <label class="rp-select">
                <span class="sr-only">Measure</span>
                <select v-model="metric">
                  <option v-for="option in METRICS" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </label>
              <label v-if="grainOptions.length > 1" class="rp-select">
                <span class="sr-only">Group by</span>
                <select v-model="grain">
                  <option v-for="option in grainOptions" :key="option" :value="option">{{ GRAIN_LABEL[option] }}</option>
                </select>
              </label>
            </div>
          </header>
          <TrendArea v-if="trendPoints.length" :points="trendPoints" :format-value="formatMetric" label="Revenue trend" />
          <p v-else class="rp-empty">Nothing was rung up in this period.</p>
        </article>

        <div class="rp-stack">
          <article class="rp-card">
            <header>
              <div><span class="rp-card__mark"><Wallet :size="17" /></span><div><h2>Payment breakdown</h2></div></div>
              <button class="rp-link" type="button" @click="reportView = 'payments'">View details <ArrowRight :size="14" /></button>
            </header>
            <div class="rp-donut">
              <DonutChart :slices="paymentTotals" :center-value="formatCurrency(grossSales)" center-label="Total payments" />
              <ul class="rp-legend">
                <li v-for="entry in paymentTotals" :key="entry.method">
                  <i :style="{ background: entry.color }" />
                  <span>{{ entry.label }}</span>
                  <em>{{ entry.share.toFixed(1) }} %</em>
                  <strong>{{ formatCurrency(entry.total) }}</strong>
                </li>
              </ul>
            </div>
          </article>

          <article class="rp-card">
            <header>
              <div><span class="rp-card__mark"><Store :size="17" /></span><div><h2>Sales by business mode</h2></div></div>
            </header>
            <p v-if="businessModeTotals.length === 0" class="rp-empty">No sales recorded in this period.</p>
            <div v-else class="rp-modes">
              <div v-for="entry in businessModeTotals" :key="entry.mode" class="rp-mode">
                <span class="rp-mode__icon"><component :is="entry.icon" :size="17" /></span>
                <p>{{ entry.label }}</p>
                <strong>{{ formatCurrency(entry.total) }}</strong>
                <small>{{ entry.share.toFixed(1) }} % · {{ entry.orders }} {{ entry.orders === 1 ? 'order' : 'orders' }}</small>
                <i class="rp-track"><b :style="{ width: `${Math.max(entry.share, 2)}%` }" /></i>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="rp-row rp-row--four">
        <article class="rp-card">
          <!-- No date stamp up here: the first row of the list is the period. -->
          <header><div><span class="rp-card__mark"><ReceiptText :size="17" /></span><div><h2>Report summary</h2></div></div></header>
          <dl class="rp-summary">
            <div v-for="row in summaryRows" :key="row.key">
              <dt><component :is="row.icon" :size="15" />{{ row.label }}<em v-if="row.scope">{{ row.scope }}</em></dt>
              <dd>{{ row.value }}<span v-if="row.badge" class="rp-badge" :class="{ 'is-open': row.badge === 'Open' }">{{ row.badge }}</span></dd>
            </div>
          </dl>
        </article>

        <article class="rp-card">
          <header><div><span class="rp-card__mark rp-card__mark--gold"><Sparkles :size="17" /></span><div><h2>Top selling items</h2></div></div></header>
          <label class="rp-search">
            <Search :size="15" />
            <input v-model="lineQuery" type="search" placeholder="Search an item" />
            <button v-if="lineQuery" type="button" aria-label="Clear item search" @click="lineQuery = ''"><X :size="13" /></button>
          </label>
          <p v-if="topLines.length === 0" class="rp-empty">No matching items were sold in this period.</p>
          <ol v-else class="rp-lines">
            <li v-for="line in topLines" :key="line.name">
              <span class="rp-lines__rank">{{ line.rank }}</span>
              <div>
                <p>{{ line.name }}</p>
                <i class="rp-track"><b :style="{ width: `${line.percent}%` }" /></i>
              </div>
              <span class="rp-lines__qty">{{ line.quantity }}</span>
              <strong>{{ formatCurrency(line.salesCents) }}</strong>
            </li>
          </ol>
        </article>

        <article class="rp-card">
          <header>
            <div><span class="rp-card__mark"><Hash :size="17" /></span><div><h2>Recent transactions</h2></div></div>
            <button class="rp-link" type="button" @click="reportView = 'payments'">View all <ArrowRight :size="14" /></button>
          </header>
          <p v-if="recentTransactions.length === 0" class="rp-empty">No tickets in this period.</p>
          <table v-else class="rp-table">
            <thead><tr><th>Time</th><th>Receipt</th><th>Items</th><th class="is-right">Amount</th></tr></thead>
            <tbody>
              <tr v-for="order in recentTransactions" :key="order.id">
                <td>{{ formatTime(order.createdAt) }}</td>
                <td class="is-mono">#{{ order.ticketNumber }}</td>
                <td>{{ itemCount(order) }}</td>
                <td class="is-right is-strong">{{ formatCurrency(order.totalCents) }}</td>
              </tr>
            </tbody>
          </table>
        </article>

        <article class="rp-card">
          <header>
            <div>
              <span class="rp-card__mark"><CalendarDays :size="17" /></span>
              <div><h2>Peak sales hours</h2><p>{{ busiestHour ? `Busiest at ${busiestHour.label}` : 'Total sales by hour' }}</p></div>
            </div>
          </header>
          <ColumnChart v-if="peakHours.length" :bars="peakHours" :format-value="formatCurrency" :label-every="3" />
          <p v-else class="rp-empty">No sales to place on a clock yet.</p>
        </article>
      </section>
    </template>

    <!-- ── Payments ────────────────────────────────────────────────────── -->
    <template v-else-if="reportView === 'payments'">
      <section class="rp-row rp-row--split">
        <article class="rp-card">
          <header><div><span class="rp-card__mark"><Wallet :size="17" /></span><div><h2>Payment methods</h2><p>How the period’s takings arrived</p></div></div></header>
          <ul class="rp-methods">
            <li v-for="entry in paymentTotals" :key="entry.method">
              <span class="rp-methods__icon" :style="{ color: entry.color }"><component :is="entry.icon" :size="18" /></span>
              <div>
                <p>{{ entry.label }}</p>
                <small>{{ entry.count }} {{ entry.count === 1 ? 'order' : 'orders' }} · avg {{ formatCurrency(entry.average) }}</small>
                <i class="rp-track"><b :style="{ width: `${Math.max(entry.share, entry.total > 0 ? 2 : 0)}%`, background: entry.color }" /></i>
              </div>
              <div class="rp-methods__value">
                <strong>{{ formatCurrency(entry.total) }}</strong>
                <small>{{ entry.share.toFixed(1) }} %</small>
              </div>
            </li>
          </ul>
        </article>

        <article class="rp-card">
          <header><div><span class="rp-card__mark"><Store :size="17" /></span><div><h2>Where the orders came from</h2></div></div></header>
          <div class="rp-channels">
            <div v-for="channel in channelSplit" :key="channel.key">
              <span><component :is="channel.icon" :size="17" /></span>
              <strong>{{ channel.count }}</strong>
              <p>{{ channel.label }}</p>
              <small>{{ formatCurrency(channel.total) }}</small>
            </div>
          </div>
        </article>
      </section>

      <section class="rp-row">
        <article class="rp-card">
          <header>
            <div><span class="rp-card__mark"><Hash :size="17" /></span><div><h2>Transactions</h2><p>{{ periodOrders.length }} tickets · {{ rangeCaption }}</p></div></div>
            <label class="rp-search rp-search--inline">
              <Search :size="15" />
              <input v-model="txQuery" type="search" placeholder="Search receipt, customer, or payment" />
              <button v-if="txQuery" type="button" aria-label="Clear transaction search" @click="txQuery = ''"><X :size="13" /></button>
            </label>
          </header>
          <p v-if="transactionList.length === 0" class="rp-empty">No tickets match this search.</p>
          <div v-else class="rp-table-wrap">
            <table class="rp-table rp-table--wide">
              <thead>
                <tr><th>Time</th><th>Receipt</th><th>Customer</th><th>Payment</th><th>Items</th><th class="is-right">Amount</th></tr>
              </thead>
              <tbody>
                <tr v-for="order in transactionList" :key="order.id" :class="{ 'is-voided': order.voidedAt }">
                  <td>{{ formatFullDate(order.createdAt) }}</td>
                  <td class="is-mono">#{{ order.ticketNumber }}</td>
                  <td>{{ order.customerName || 'Walk-in' }}</td>
                  <td>{{ paymentLabel(order.paymentMethod) }}<span v-if="order.voidedAt" class="rp-badge">Voided</span></td>
                  <td>{{ itemCount(order) }}</td>
                  <td class="is-right is-strong">{{ formatCurrency(order.totalCents) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>
    </template>

    <!-- ── Shift ───────────────────────────────────────────────────────── -->
    <template v-else>
      <section class="rp-row rp-row--split">
        <article class="rp-card">
          <header>
            <div>
              <span class="rp-card__mark"><Landmark :size="17" /></span>
              <div><h2>Shift snapshot</h2><p v-if="shiftReport">Opened by {{ shiftReport.openedBy }}</p></div>
            </div>
            <span v-if="shiftReport" class="rp-badge is-open">Open</span>
          </header>
          <p v-if="!shiftReport" class="rp-empty">Open a shift to see a live cash position here.</p>
          <template v-else>
            <div class="rp-stats">
              <div v-for="stat in shiftReport.stats" :key="stat.key"><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong></div>
            </div>
            <div v-if="shiftReport.movements.length" class="rp-moves">
              <p class="rp-moves__head">Cash movements</p>
              <div v-for="movement in shiftReport.movements" :key="movement.id">
                <span :class="movement.movementType === 'pay_in' ? 'is-in' : 'is-out'">{{ movement.movementType === 'pay_in' ? 'Paid in' : 'Paid out' }}</span>
                <p>{{ movement.reason || 'No reason given' }}</p>
                <strong>{{ formatCurrency(movement.amountCents) }}</strong>
              </div>
            </div>
          </template>
        </article>

        <article class="rp-card">
          <header><div><span class="rp-card__mark rp-card__mark--gold"><Sparkles :size="17" /></span><div><h2>Top selling items</h2><p>{{ rangeCaption }}</p></div></div></header>
          <p v-if="topLines.length === 0" class="rp-empty">Nothing was sold in this period.</p>
          <ol v-else class="rp-lines">
            <li v-for="line in topLines" :key="line.name">
              <span class="rp-lines__rank">{{ line.rank }}</span>
              <div><p>{{ line.name }}</p><i class="rp-track"><b :style="{ width: `${line.percent}%` }" /></i></div>
              <span class="rp-lines__qty">{{ line.quantity }}</span>
              <strong>{{ formatCurrency(line.salesCents) }}</strong>
            </li>
          </ol>
        </article>
      </section>

      <section class="rp-row">
        <article class="rp-card">
          <header><div><span class="rp-card__mark"><CalendarDays :size="17" /></span><div><h2>Shift history</h2><p>Closed shifts, with the counted-cash variance for each</p></div></div></header>
          <p v-if="store.shiftHistory.length === 0" class="rp-empty">No shifts have been closed yet.</p>
          <div v-else class="rp-table-wrap">
            <table class="rp-table rp-table--wide">
              <thead>
                <tr><th>Opened</th><th>Closed</th><th>Cashier</th><th>Orders</th><th class="is-right">Sales</th><th class="is-right">Expected</th><th class="is-right">Counted</th><th class="is-right">Variance</th></tr>
              </thead>
              <tbody>
                <tr v-for="shift in store.shiftHistory" :key="shift.id">
                  <td>{{ formatFullDate(shift.openedAt) }}</td>
                  <td>{{ shift.closedAt ? formatFullDate(shift.closedAt) : 'Open' }}</td>
                  <td>{{ userName(shift.closedByUserId ?? shift.openedByUserId) }}</td>
                  <td>{{ shift.orderCount }}</td>
                  <td class="is-right">{{ formatCurrency(shift.totalSalesCents) }}</td>
                  <td class="is-right">{{ formatCurrency(shift.expectedCashCents) }}</td>
                  <td class="is-right">{{ shift.closingCashCents == null ? '—' : formatCurrency(shift.closingCashCents) }}</td>
                  <td class="is-right is-strong"><span class="rp-variance" :class="varianceClass(shift.varianceCashCents)">{{ varianceLabel(shift.varianceCashCents) }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.reports-page{--rp-surface:rgba(255,255,255,.9);--rp-border:rgba(68,83,104,.12);--rp-card-border:rgba(255,255,255,.75);--rp-shadow:0 7px 24px rgba(63,80,103,.05);--rp-green:#08783f;--rp-green-ink:#08783f;--rp-green-soft:#e8f5ee;--rp-on-green:#fff;--rp-control:#fff;--rp-field:rgba(245,247,249,.92);--rp-track:#e5e8ea;--rp-tile:linear-gradient(145deg,rgba(247,249,251,.96),rgba(241,244,247,.84));--rp-row-hover:rgba(8,120,63,.035);--rp-thead:linear-gradient(180deg,rgba(243,245,247,.92),rgba(237,239,242,.84));--rp-thead-ink:#45506a;--rp-pay-cash:#12a05c;--rp-pay-wallet:#2a86e0;--rp-pay-card:#8b6ce0;--rp-tone-leaf:#08783f;--rp-tone-leaf-bg:#e8f5ee;--rp-tone-mint:#0b8f86;--rp-tone-mint-bg:#e3f4f2;--rp-tone-amber:#b3760a;--rp-tone-amber-bg:#fdf1de;--rp-tone-rose:#c2453c;--rp-tone-rose-bg:#fdeae8;--rp-tone-violet:#6443cb;--rp-tone-violet-bg:#f0eafd;--chart-accent:#08783f;--chart-accent-soft:rgba(8,120,63,.4);--chart-grid:rgba(68,83,104,.16);--chart-track:rgba(68,83,104,.12);--chart-surface:#fff;--chart-tip-bg:#fff;--chart-tip-border:rgba(68,83,104,.14);--chart-tip-shadow:0 10px 26px rgba(40,52,68,.16);display:grid;gap:15px;padding:2px 0 26px}
.rp-hero{display:flex;align-items:end;justify-content:space-between;gap:24px;flex-wrap:wrap}.rp-hero__title{display:flex;align-items:flex-start;gap:14px;min-width:0}.rp-hero__mark{width:46px;height:46px;display:grid;flex:none;place-items:center;border-radius:13px;background:var(--rp-green-soft);color:var(--rp-green-ink)}.rp-hero h1{margin:0;font-size:29px;line-height:36px;letter-spacing:-.025em}.rp-hero p{max-width:74ch;margin:3px 0 0;color:var(--text-secondary);font-size:14px;line-height:20px}
.rp-hero__tools{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.rp-datechip{display:inline-flex;align-items:center;gap:8px;min-height:40px;padding:0 14px;border:1px solid var(--rp-border);border-radius:10px;background:var(--rp-control);color:var(--text-secondary);font-size:12.5px;white-space:nowrap}.rp-export{display:inline-flex;align-items:center;gap:8px;min-height:40px;padding:0 16px;border:0;border-radius:10px;background:var(--rp-green);color:var(--rp-on-green);font:inherit;font-size:13px;font-weight:650;cursor:pointer}.rp-export:hover{background:color-mix(in srgb,var(--rp-green) 86%,#000)}
.rp-kpis{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:13px}.rp-kpi{min-height:104px;display:flex;align-items:center;gap:15px;padding:16px 18px;border:1px solid var(--rp-card-border);border-radius:14px;background:var(--rp-surface);box-shadow:var(--rp-shadow)}.rp-kpi__icon{width:48px;height:48px;display:grid;flex:none;place-items:center;border-radius:13px}.rp-kpi__icon--leaf{background:var(--rp-tone-leaf-bg);color:var(--rp-tone-leaf)}.rp-kpi__icon--mint{background:var(--rp-tone-mint-bg);color:var(--rp-tone-mint)}.rp-kpi__icon--amber{background:var(--rp-tone-amber-bg);color:var(--rp-tone-amber)}.rp-kpi__icon--rose{background:var(--rp-tone-rose-bg);color:var(--rp-tone-rose)}.rp-kpi__icon--violet{background:var(--rp-tone-violet-bg);color:var(--rp-tone-violet)}.rp-kpi>div{min-width:0}.rp-kpi p{display:flex;align-items:center;gap:5px;margin:0 0 3px;color:var(--text-secondary);font-size:12px}.rp-kpi__info{display:inline-flex;color:var(--text-tertiary);cursor:help}.rp-kpi strong{display:block;font-size:22px;line-height:28px;letter-spacing:-.025em;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.rp-kpi small{display:flex;flex-wrap:wrap;align-items:baseline;gap:2px 5px;margin-top:4px;color:var(--rp-green-ink);font-size:12px;font-weight:700}.rp-kpi small.is-down{color:var(--danger)}.rp-kpi small.is-muted{color:var(--text-tertiary);font-weight:500}.rp-kpi small b{color:var(--text-secondary);font-weight:500;white-space:nowrap}
.rp-tabs{display:inline-flex;gap:4px;padding:4px;border:1px solid var(--rp-border);border-radius:var(--radius-pill);background:var(--rp-control);width:max-content;max-width:100%;overflow-x:auto}.rp-tab{display:inline-flex;align-items:center;gap:7px;min-height:36px;padding:0 16px;border:0;border-radius:var(--radius-pill);background:transparent;color:var(--text-secondary);font:inherit;font-size:13px;font-weight:600;white-space:nowrap;cursor:pointer}.rp-tab:hover{color:var(--text-primary)}.rp-tab.is-on{background:var(--rp-green-soft);color:var(--rp-green-ink)}
.rp-row{display:grid;gap:14px}.rp-row--trend{grid-template-columns:minmax(0,1.5fr) minmax(320px,1fr)}.rp-row--split{grid-template-columns:minmax(0,1.2fr) minmax(0,1fr)}.rp-row--four{grid-template-columns:repeat(4,minmax(0,1fr))}.rp-stack{display:grid;gap:14px;align-content:start;min-width:0}
.rp-card{display:flex;min-width:0;flex-direction:column;gap:14px;padding:16px 18px 18px;border:1px solid var(--rp-card-border);border-radius:16px;background:var(--rp-surface);box-shadow:var(--rp-shadow)}.rp-card>header{display:flex;align-items:flex-start;justify-content:space-between;gap:14px}.rp-card>header>div:first-child{display:flex;align-items:center;gap:11px;min-width:0}.rp-card__mark{width:34px;height:34px;display:grid;flex:none;place-items:center;border-radius:10px;background:var(--rp-green-soft);color:var(--rp-green-ink)}.rp-card__mark--gold{background:var(--rp-tone-amber-bg);color:var(--rp-tone-amber)}.rp-card h2{margin:0;font-size:16px;line-height:21px;letter-spacing:-.012em}.rp-card header p{margin:2px 0 0;color:var(--text-secondary);font-size:11.5px}.rp-stamp{color:var(--text-tertiary);font-size:11px;white-space:nowrap}.rp-link{display:inline-flex;align-items:center;gap:4px;padding:0;border:0;background:none;color:var(--rp-green-ink);font:inherit;font-size:11.5px;font-weight:650;white-space:nowrap;cursor:pointer}.rp-empty{margin:auto 0;padding:26px 0;color:var(--text-tertiary);font-size:12.5px;text-align:center}
.rp-selects{display:flex;gap:8px;flex:none}.rp-select{display:inline-flex;align-items:center;min-height:34px;padding:0 6px 0 12px;border:1px solid var(--rp-border);border-radius:9px;background:var(--rp-control)}.rp-select select{border:0;outline:0;background:transparent;color:var(--text-primary);font:inherit;font-size:12.5px;font-weight:600;cursor:pointer}
.rp-donut{display:flex;align-items:center;gap:18px;flex-wrap:wrap}.rp-legend{display:grid;flex:1 1 190px;gap:9px;min-width:0;margin:0;padding:0;list-style:none}.rp-legend li{display:grid;grid-template-columns:9px minmax(0,1fr) auto auto;align-items:center;gap:9px;font-size:12px}.rp-legend i{width:9px;height:9px;border-radius:999px}.rp-legend span{min-width:0;overflow:hidden;color:var(--text-secondary);text-overflow:ellipsis;white-space:nowrap}.rp-legend em{color:var(--text-tertiary);font-size:11px;font-style:normal;font-variant-numeric:tabular-nums}.rp-legend strong{font-size:12px;font-variant-numeric:tabular-nums;white-space:nowrap}
.rp-modes{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.rp-mode{min-width:0;padding:12px 13px;border-radius:12px;background:var(--rp-tile)}.rp-mode__icon{display:grid;width:28px;height:28px;place-items:center;border-radius:9px;background:var(--rp-green-soft);color:var(--rp-green-ink)}.rp-mode p{margin:9px 0 3px;font-size:11.5px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.rp-mode strong{display:block;font-size:15px;letter-spacing:-.02em;font-variant-numeric:tabular-nums}.rp-mode small{display:block;margin-top:2px;color:var(--text-secondary);font-size:10.5px}
.rp-track{display:block;height:5px;margin-top:8px;border-radius:999px;background:var(--rp-track);overflow:hidden}.rp-track b{display:block;height:100%;border-radius:inherit;background:var(--rp-green)}
.rp-summary{display:grid;gap:2px;margin:0}.rp-summary>div{display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:34px;padding:4px 0;border-bottom:1px solid var(--rp-border)}.rp-summary>div:last-child{border-bottom:0}.rp-summary dt{display:flex;align-items:center;gap:8px;min-width:0;color:var(--text-secondary);font-size:12px}.rp-summary dt svg{color:var(--text-tertiary);flex:none}.rp-summary dt em{padding:2px 6px;border-radius:5px;background:var(--rp-field);color:var(--text-tertiary);font-size:9.5px;font-style:normal;white-space:nowrap}.rp-summary dd{display:flex;align-items:center;gap:7px;margin:0;font-size:12.5px;font-weight:650;font-variant-numeric:tabular-nums;text-align:right}
.rp-badge{padding:2px 8px;border-radius:999px;background:var(--rp-field);color:var(--text-secondary);font-size:10px;font-weight:650}.rp-badge.is-open{background:var(--rp-green-soft);color:var(--rp-green-ink)}
.rp-search{display:flex;align-items:center;gap:9px;min-height:36px;padding:0 11px;border:1px solid var(--rp-border);border-radius:9px;background:var(--rp-field);color:var(--text-secondary)}.rp-search input{width:100%;min-width:0;border:0;outline:0;background:transparent;color:var(--text-primary);font:inherit;font-size:12.5px}.rp-search input::-webkit-search-cancel-button{display:none}.rp-search button{display:grid;width:22px;height:22px;flex:none;place-items:center;border:0;border-radius:6px;background:transparent;color:var(--text-tertiary);cursor:pointer}.rp-search--inline{flex:0 1 320px;min-width:180px}
.rp-lines{display:grid;gap:11px;margin:0;padding:0;list-style:none}.rp-lines li{display:grid;grid-template-columns:22px minmax(0,1fr) auto auto;align-items:center;gap:10px}.rp-lines__rank{display:grid;width:22px;height:22px;place-items:center;border-radius:7px;background:var(--rp-field);color:var(--text-secondary);font-size:10.5px;font-weight:700}.rp-lines>li>div{min-width:0}.rp-lines p{margin:0;overflow:hidden;font-size:12px;font-weight:600;text-overflow:ellipsis;white-space:nowrap}.rp-lines .rp-track{margin-top:6px}.rp-lines__qty{color:var(--text-secondary);font-size:11.5px;font-variant-numeric:tabular-nums}.rp-lines strong{font-size:12px;font-variant-numeric:tabular-nums;white-space:nowrap}
.rp-table{width:100%;border-collapse:collapse;font-size:12px}.rp-table th{padding:7px 8px;background:var(--rp-thead);color:var(--rp-thead-ink);font-size:10.5px;font-weight:600;text-align:left;white-space:nowrap}.rp-table th:first-child{border-radius:7px 0 0 7px}.rp-table th:last-child{border-radius:0 7px 7px 0}.rp-table td{padding:8px;border-bottom:1px solid var(--rp-border);color:var(--text-secondary);white-space:nowrap}.rp-table tbody tr:last-child td{border-bottom:0}.rp-table tbody tr:hover td{background:var(--rp-row-hover)}.rp-table .is-right{text-align:right}.rp-table .is-strong{color:var(--text-primary);font-weight:650;font-variant-numeric:tabular-nums}.rp-table .is-mono{color:var(--text-primary);font-variant-numeric:tabular-nums}.rp-table tr.is-voided td{opacity:.55}.rp-table--wide td:nth-child(3){white-space:normal}.rp-table-wrap{overflow-x:auto}.rp-table--wide{min-width:620px}
.rp-methods{display:grid;gap:14px;margin:0;padding:0;list-style:none}.rp-methods li{display:grid;grid-template-columns:38px minmax(0,1fr) auto;align-items:center;gap:13px}.rp-methods__icon{display:grid;width:38px;height:38px;place-items:center;border-radius:11px;background:var(--rp-field)}.rp-methods p{margin:0;font-size:12.5px;font-weight:600}.rp-methods small{color:var(--text-secondary);font-size:11px}.rp-methods__value{text-align:right}.rp-methods__value strong{display:block;font-size:14px;font-variant-numeric:tabular-nums}.rp-methods__value small{display:block;margin-top:2px}
.rp-channels{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.rp-channels>div{min-width:0;padding:14px 13px;border-radius:12px;background:var(--rp-tile)}.rp-channels span{display:grid;width:30px;height:30px;place-items:center;border-radius:9px;background:var(--rp-green-soft);color:var(--rp-green-ink)}.rp-channels strong{display:block;margin-top:10px;font-size:22px;line-height:26px;letter-spacing:-.025em}.rp-channels p{margin:3px 0 0;font-size:11.5px;font-weight:600}.rp-channels small{color:var(--text-secondary);font-size:10.5px;font-variant-numeric:tabular-nums}
.rp-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.rp-stats>div{min-width:0;padding:11px 12px;border-radius:11px;background:var(--rp-tile)}.rp-stats span{display:block;color:var(--text-secondary);font-size:10.5px}.rp-stats strong{display:block;margin-top:4px;font-size:13.5px;letter-spacing:-.015em;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}
.rp-moves{display:grid;gap:9px}.rp-moves__head{margin:0;color:var(--text-secondary);font-size:11px;font-weight:600}.rp-moves>div{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:11px}.rp-moves span{padding:3px 8px;border-radius:999px;font-size:10px;font-weight:650}.rp-moves span.is-in{background:var(--rp-green-soft);color:var(--rp-green-ink)}.rp-moves span.is-out{background:var(--rp-tone-rose-bg);color:var(--rp-tone-rose)}.rp-moves p{margin:0;overflow:hidden;color:var(--text-secondary);font-size:11.5px;text-overflow:ellipsis;white-space:nowrap}.rp-moves strong{font-size:12.5px;font-variant-numeric:tabular-nums}
.rp-variance{padding:3px 9px;border-radius:999px;font-size:11px;font-weight:650}.rp-variance.is-even{background:var(--rp-field);color:var(--text-secondary)}.rp-variance.is-short{background:var(--rp-tone-rose-bg);color:var(--rp-tone-rose)}.rp-variance.is-over{background:var(--rp-tone-amber-bg);color:var(--rp-tone-amber)}
.rp-tab:focus-visible,.rp-export:focus-visible,.rp-link:focus-visible,.rp-kpi__info:focus-visible,.rp-select:focus-within{outline:2px solid var(--rp-green);outline-offset:2px}
/* Five tiles never divide evenly below full width; three-then-two reads
   better than four-then-one, so the step goes straight from five to three. */
@media(max-width:1559px){.rp-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(max-width:1499px){.rp-row--four{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:1180px){.rp-row--trend,.rp-row--split{grid-template-columns:minmax(0,1fr)}.rp-modes{grid-template-columns:repeat(4,minmax(0,1fr))}.rp-stats{grid-template-columns:repeat(4,minmax(0,1fr))}}
@media(max-width:999px){.rp-row--four{grid-template-columns:minmax(0,1fr)}}
@media(max-width:860px){.rp-kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.rp-modes,.rp-channels,.rp-stats{grid-template-columns:repeat(2,minmax(0,1fr))}.rp-hero{align-items:flex-start;flex-direction:column;gap:14px}.rp-hero h1{font-size:25px}.rp-hero p{font-size:13px}.rp-hero__tools{width:100%}.rp-hero__tools :deep(.range-selector){flex:1 1 220px}/* Wrap rather than stack: a short action still belongs beside the title, and
   only the full-width search is pushed onto a line of its own. */
.rp-card>header{flex-wrap:wrap}.rp-search--inline{flex:1 1 auto;width:100%}}
@media(max-width:520px){.rp-kpis{grid-template-columns:minmax(0,1fr)}.rp-kpi{min-height:88px;gap:12px;padding:13px}.rp-kpi__icon{width:40px;height:40px;border-radius:11px}.rp-kpi strong{font-size:19px;line-height:24px}.rp-modes,.rp-channels,.rp-stats{grid-template-columns:minmax(0,1fr)}.rp-card{padding:14px}.rp-donut{justify-content:center}.rp-selects{width:100%}.rp-select{flex:1 1 0}.rp-select select{width:100%}}
/*
 * The dark palette, written twice for the reason OrdersPage sets out at
 * length: dark is reached three ways — [data-theme='dark'], the system
 * preference with no stamp at all, and the four fixed-dark colour themes —
 * and a selector list cannot straddle a media query. Keep the two in sync.
 */
[data-theme='dark'] .reports-page,[data-color-theme='nocturne'] .reports-page,[data-color-theme='reserve'] .reports-page,[data-color-theme='harbor'] .reports-page,[data-color-theme='mono'] .reports-page{--rp-surface:color-mix(in srgb,var(--bg-surface) 94%,transparent);--rp-border:var(--separator);--rp-card-border:var(--separator);--rp-shadow:0 7px 24px rgba(0,0,0,.34);--rp-green:#3fbd77;--rp-green-ink:#6fdc9c;--rp-green-soft:rgba(63,189,119,.16);--rp-on-green:#062b16;--rp-control:var(--bg-elevated);--rp-field:rgba(255,255,255,.06);--rp-track:rgba(255,255,255,.12);--rp-tile:rgba(255,255,255,.05);--rp-row-hover:rgba(255,255,255,.05);--rp-thead:linear-gradient(180deg,rgba(255,255,255,.07),rgba(255,255,255,.04));--rp-thead-ink:var(--text-secondary);--rp-pay-cash:#5fd699;--rp-pay-wallet:#6bb2f0;--rp-pay-card:#b49bff;--rp-tone-leaf:#6fdc9c;--rp-tone-leaf-bg:rgba(63,189,119,.16);--rp-tone-mint:#61d8cf;--rp-tone-mint-bg:rgba(97,216,207,.16);--rp-tone-amber:#edb64a;--rp-tone-amber-bg:rgba(237,182,74,.16);--rp-tone-rose:#ff9d96;--rp-tone-rose-bg:rgba(255,157,150,.16);--rp-tone-violet:#c0aaff;--rp-tone-violet-bg:rgba(192,170,255,.18);--chart-accent:#3fbd77;--chart-accent-soft:rgba(63,189,119,.42);--chart-grid:rgba(255,255,255,.14);--chart-track:rgba(255,255,255,.12);--chart-surface:var(--bg-elevated);--chart-tip-bg:var(--bg-elevated);--chart-tip-border:var(--separator);--chart-tip-shadow:0 12px 30px rgba(0,0,0,.45)}
@media(prefers-color-scheme:dark){html:not([data-theme='light']) .reports-page{--rp-surface:color-mix(in srgb,var(--bg-surface) 94%,transparent);--rp-border:var(--separator);--rp-card-border:var(--separator);--rp-shadow:0 7px 24px rgba(0,0,0,.34);--rp-green:#3fbd77;--rp-green-ink:#6fdc9c;--rp-green-soft:rgba(63,189,119,.16);--rp-on-green:#062b16;--rp-control:var(--bg-elevated);--rp-field:rgba(255,255,255,.06);--rp-track:rgba(255,255,255,.12);--rp-tile:rgba(255,255,255,.05);--rp-row-hover:rgba(255,255,255,.05);--rp-thead:linear-gradient(180deg,rgba(255,255,255,.07),rgba(255,255,255,.04));--rp-thead-ink:var(--text-secondary);--rp-pay-cash:#5fd699;--rp-pay-wallet:#6bb2f0;--rp-pay-card:#b49bff;--rp-tone-leaf:#6fdc9c;--rp-tone-leaf-bg:rgba(63,189,119,.16);--rp-tone-mint:#61d8cf;--rp-tone-mint-bg:rgba(97,216,207,.16);--rp-tone-amber:#edb64a;--rp-tone-amber-bg:rgba(237,182,74,.16);--rp-tone-rose:#ff9d96;--rp-tone-rose-bg:rgba(255,157,150,.16);--rp-tone-violet:#c0aaff;--rp-tone-violet-bg:rgba(192,170,255,.18);--chart-accent:#3fbd77;--chart-accent-soft:rgba(63,189,119,.42);--chart-grid:rgba(255,255,255,.14);--chart-track:rgba(255,255,255,.12);--chart-surface:var(--bg-elevated);--chart-tip-bg:var(--bg-elevated);--chart-tip-border:var(--separator);--chart-tip-shadow:0 12px 30px rgba(0,0,0,.45)}}
</style>
