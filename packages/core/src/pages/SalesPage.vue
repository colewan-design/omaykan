<script setup lang="ts">
import {
  ArrowDown,
  ArrowRight,
  ArrowUp,
  Banknote,
  ChartColumn,
  Coins,
  CreditCard,
  Ellipsis,
  Info,
  Search,
  ShoppingBag,
  ShoppingCart,
  Smartphone,
  Truck,
  Utensils,
} from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import RangeSelector, { type Range } from '@pos/core/components/RangeSelector.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { formatCurrency, type OrderSummary, type PaymentMethod } from '@pos/shared/index'

const store = usePosStore()

onMounted(() => {
  if (!store.isReady) {
    void store.initialize()
  }
})

const range = ref<Range>('week')
const now = new Date()
const DAY = 86400000

// ── Period ────────────────────────────────────────────────────────────────

interface Bounds {
  start: Date
  end: Date
  prevStart: Date
  prevEnd: Date
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function getMonday(d: Date) {
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  return new Date(startOfDay(d).getTime() + diff * DAY)
}

function getBounds(value: Range): Bounds {
  const today = startOfDay(now)
  switch (value) {
    case 'today':
      return { start: today, end: new Date(today.getTime() + DAY), prevStart: new Date(today.getTime() - DAY), prevEnd: today }
    case 'week': {
      const start = getMonday(now)
      return { start, end: new Date(start.getTime() + 7 * DAY), prevStart: new Date(start.getTime() - 7 * DAY), prevEnd: start }
    }
    case 'month': {
      const start = new Date(today.getFullYear(), today.getMonth(), 1)
      const end = new Date(today.getFullYear(), today.getMonth() + 1, 1)
      return { start, end, prevStart: new Date(today.getFullYear(), today.getMonth() - 1, 1), prevEnd: start }
    }
    case 'all': {
      const epoch = new Date(0)
      return { start: epoch, end: new Date(8640000000000000), prevStart: epoch, prevEnd: epoch }
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
const previousOrders = computed(() =>
  range.value === 'all'
    ? []
    : activeOrders.value.filter((order) => inBounds(order, bounds.value.prevStart, bounds.value.prevEnd)),
)

const rangeCaption = computed(() => {
  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
  if (range.value === 'today') return formatter.format(now)
  if (range.value === 'all') return 'All time'
  return `${formatter.format(bounds.value.start)} - ${formatter.format(new Date(bounds.value.end.getTime() - DAY))}`
})

const comparisonLabel = computed(() => ({
  today: 'vs. yesterday',
  week: 'vs. last week',
  month: 'vs. last month',
  all: '',
})[range.value])

// ── KPIs ──────────────────────────────────────────────────────────────────

const sum = (orders: OrderSummary[], pick: (order: OrderSummary) => number) =>
  orders.reduce((total, order) => total + pick(order), 0)

/** What the shop earned from its goods: item totals after discounts, before tax and delivery fees. */
const netOf = (order: OrderSummary) => order.subtotalCents - (order.discountCents ?? 0)

function delta(current: number, previous: number) {
  if (range.value === 'all' || previous === 0) return null
  const percentage = ((current - previous) / previous) * 100
  return { value: `${Math.abs(percentage).toFixed(Math.abs(percentage) >= 10 ? 0 : 1)}%`, positive: percentage >= 0 }
}

const kpis = computed(() => {
  const current = periodOrders.value
  const previous = previousOrders.value
  const gross = sum(current, (order) => order.totalCents)
  const previousGross = sum(previous, (order) => order.totalCents)
  const net = sum(current, netOf)
  const previousNet = sum(previous, netOf)
  const average = current.length ? Math.round(gross / current.length) : 0
  const previousAverage = previous.length ? Math.round(previousGross / previous.length) : 0

  return [
    {
      key: 'gross',
      label: 'Gross Sales',
      value: formatCurrency(gross),
      delta: delta(gross, previousGross),
      icon: ChartColumn,
      help: 'Everything customers paid, including tax and delivery fees. Voided orders are left out.',
    },
    {
      key: 'orders',
      label: 'Orders',
      value: current.length.toLocaleString('en-PH'),
      delta: delta(current.length, previous.length),
      icon: ShoppingCart,
      help: 'Orders completed in this period, not counting voided ones.',
    },
    {
      key: 'average',
      label: 'Avg. Order Value',
      value: formatCurrency(average),
      delta: delta(average, previousAverage),
      icon: ShoppingBag,
      help: 'Gross sales divided by the number of orders.',
    },
    {
      key: 'net',
      label: 'Net Sales',
      value: formatCurrency(net),
      delta: delta(net, previousNet),
      icon: Coins,
      help: 'Item sales after discounts, before tax and delivery fees.',
    },
  ]
})

// ── Sales trend ───────────────────────────────────────────────────────────

interface TrendBucket {
  key: string
  /** Starts after now: nothing can have been sold yet, so the line stops short of it. */
  future: boolean
  /** The bucket named in full, for the hover tip. */
  tip: string
  label: string
  sublabel: string
  showLabel: boolean
  revenue: number
  orders: number
}

function hourLabel(hour: number) {
  if (hour === 0) return '12a'
  if (hour === 12) return '12p'
  return hour < 12 ? `${hour}a` : `${hour - 12}p`
}

const trendBuckets = computed<TrendBucket[]>(() => {
  const orders = periodOrders.value
  const weekday = new Intl.DateTimeFormat('en-PH', { weekday: 'short' })
  const monthDay = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric' })
  const monthName = new Intl.DateTimeFormat('en-PH', { month: 'short' })

  let buckets: TrendBucket[]
  let indexOf: (date: Date) => number

  if (range.value === 'today') {
    buckets = Array.from({ length: 24 }, (_, hour) => ({
      key: String(hour), future: hour > now.getHours(), tip: new Intl.DateTimeFormat('en-PH', { hour: 'numeric', minute: '2-digit' }).format(new Date(2000, 0, 1, hour)), label: hourLabel(hour), sublabel: '', showLabel: hour % 3 === 0, revenue: 0, orders: 0,
    }))
    indexOf = (date) => date.getHours()
  } else if (range.value === 'week' || range.value === 'month') {
    const start = bounds.value.start
    const days = Math.round((bounds.value.end.getTime() - start.getTime()) / DAY)
    buckets = Array.from({ length: days }, (_, index) => {
      const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
      const future = date > now
      const tip = new Intl.DateTimeFormat('en-PH', { weekday: 'short', month: 'short', day: 'numeric' }).format(date)
      return range.value === 'week'
        ? { key: date.toISOString(), future, tip, label: weekday.format(date), sublabel: monthDay.format(date), showLabel: true, revenue: 0, orders: 0 }
        : {
            key: date.toISOString(),
            future,
            tip,
            label: String(date.getDate()),
            sublabel: '',
            showLabel: index === 0 || (index + 1) % 5 === 0,
            revenue: 0,
            orders: 0,
          }
    })
    indexOf = (date) => Math.round((startOfDay(date).getTime() - startOfDay(start).getTime()) / DAY)
  } else {
    // All time: one bar per month, the last twelve at most.
    const earliest = orders.reduce((min, order) => Math.min(min, new Date(order.createdAt).getTime()), now.getTime())
    const first = new Date(earliest)
    const months = Math.min(12, (now.getFullYear() - first.getFullYear()) * 12 + now.getMonth() - first.getMonth() + 1)
    const firstMonth = new Date(now.getFullYear(), now.getMonth() - months + 1, 1)
    buckets = Array.from({ length: months }, (_, index) => {
      const date = new Date(firstMonth.getFullYear(), firstMonth.getMonth() + index, 1)
      return { key: date.toISOString(), future: false, tip: `${monthName.format(date)} ${date.getFullYear()}`, label: monthName.format(date), sublabel: String(date.getFullYear()), showLabel: true, revenue: 0, orders: 0 }
    })
    indexOf = (date) => (date.getFullYear() - firstMonth.getFullYear()) * 12 + date.getMonth() - firstMonth.getMonth()
  }

  for (const order of orders) {
    const bucket = buckets[indexOf(new Date(order.createdAt))]
    if (!bucket) continue
    bucket.revenue += order.totalCents
    bucket.orders += 1
  }

  return buckets
})

const trendCaption = computed(() => ({
  today: 'Hourly gross sales and number of orders for today.',
  week: 'Daily gross sales and number of orders for this week.',
  month: 'Daily gross sales and number of orders for this month.',
  all: 'Monthly gross sales and number of orders.',
})[range.value])

/** A round ceiling for an axis, split into four equal steps. Counts step in whole numbers. */
function niceMax(value: number, whole = false) {
  if (value <= 0) return 4
  const step = value / 4
  if (whole && step <= 1) return 4
  const magnitude = 10 ** Math.floor(Math.log10(step))
  const factors = whole ? [1, 2, 2.5, 3, 5, 10].filter((factor) => Number.isInteger(factor * magnitude)) : [1, 1.5, 2, 2.5, 3, 5, 10]
  const nice = factors.find((factor) => factor * magnitude >= step) ?? 10
  return nice * magnitude * 4
}

const CHART = { width: 720, height: 240, left: 64, right: 44, top: 12, bottom: 44 }
const plotWidth = CHART.width - CHART.left - CHART.right
const plotHeight = CHART.height - CHART.top - CHART.bottom

const trendChart = computed(() => {
  const buckets = trendBuckets.value
  const revenueMax = niceMax(Math.max(0, ...buckets.map((bucket) => bucket.revenue)) / 100) * 100
  const ordersMax = niceMax(Math.max(0, ...buckets.map((bucket) => bucket.orders)), true)
  const slot = plotWidth / Math.max(buckets.length, 1)
  const barWidth = Math.min(56, slot * 0.58)
  const baseline = CHART.top + plotHeight

  const points = buckets.map((bucket, index) => {
    const x = CHART.left + slot * index + slot / 2
    const barHeight = (bucket.revenue / revenueMax) * plotHeight
    return {
      ...bucket,
      x,
      bar: { x: x - barWidth / 2, y: baseline - barHeight, width: barWidth, height: barHeight },
      y: baseline - (bucket.orders / ordersMax) * plotHeight,
    }
  })

  const past = points.filter((point) => !point.future)

  const ticks = [0, 1, 2, 3, 4].map((step) => ({
    y: baseline - (plotHeight * step) / 4,
    revenue: compactPeso((revenueMax * step) / 4),
    orders: Math.round((ordersMax * step) / 4).toLocaleString('en-PH'),
  }))

  return {
    points,
    ticks,
    baseline,
    past,
    line: past.map((point, index) => `${index ? 'L' : 'M'}${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(' '),
    showDots: past.length <= 31,
  }
})

function compactPeso(cents: number) {
  const pesos = cents / 100
  if (pesos >= 1_000_000) return `₱${(pesos / 1_000_000).toLocaleString('en-PH', { maximumFractionDigits: 1 })}M`
  if (pesos >= 100_000) return `₱${(pesos / 1000).toLocaleString('en-PH', { maximumFractionDigits: 0 })}K`
  return `₱${pesos.toLocaleString('en-PH', { maximumFractionDigits: 0 })}`
}

const hoveredTrend = ref<number | null>(null)
const hasSales = computed(() => periodOrders.value.length > 0)

// ── Breakdowns ────────────────────────────────────────────────────────────

const paymentMeta: Record<PaymentMethod, { label: string; icon: unknown }> = {
  cash: { label: 'Cash', icon: Banknote },
  ewallet: { label: 'E-wallet', icon: Smartphone },
  card: { label: 'Card', icon: CreditCard },
}

type OrderKind = 'delivery' | 'pickup' | 'in-store'

/** How an order reached the customer: delivered, collected, or rung up at the counter. */
function orderKind(order: OrderSummary): OrderKind {
  if (order.channel === 'online') return order.fulfillmentMethod === 'delivery' ? 'delivery' : 'pickup'
  return 'in-store'
}

const kindMeta: Record<OrderKind, { label: string; icon: unknown }> = {
  delivery: { label: 'Delivery', icon: Truck },
  pickup: { label: 'Pickup', icon: ShoppingBag },
  'in-store': { label: 'Dine-in / POS', icon: Utensils },
}

function shareRows<K extends string>(keys: K[], keyOf: (order: OrderSummary) => K, meta: Record<K, { label: string; icon: unknown }>) {
  const total = sum(periodOrders.value, (order) => order.totalCents)
  return keys
    .map((key) => {
      const value = sum(periodOrders.value.filter((order) => keyOf(order) === key), (order) => order.totalCents)
      return { key, ...meta[key], value, share: total ? Math.round((value / total) * 100) : 0 }
    })
    .sort((a, b) => b.value - a.value)
}

const paymentRows = computed(() => shareRows<PaymentMethod>(['cash', 'ewallet', 'card'], (order) => order.paymentMethod, paymentMeta))
const kindRows = computed(() => shareRows<OrderKind>(['delivery', 'pickup', 'in-store'], orderKind, kindMeta))

// ── Peak hours ────────────────────────────────────────────────────────────

const peakMetric = ref<'revenue' | 'orders'>('revenue')
const peakMetricOptions: { value: 'revenue' | 'orders'; label: string }[] = [
  { value: 'revenue', label: 'Gross Sales (₱)' },
  { value: 'orders', label: 'Orders' },
]

const peakHours = computed(() => {
  const buckets = Array.from({ length: 24 }, (_, hour) => ({ hour, label: hourLabel(hour), revenue: 0, orders: 0 }))
  for (const order of periodOrders.value) {
    const bucket = buckets[new Date(order.createdAt).getHours()]
    bucket.revenue += order.totalCents
    bucket.orders += 1
  }

  // Trading hours by default, widened to take in any sale made outside them.
  const busy = buckets.filter((bucket) => bucket.orders > 0).map((bucket) => bucket.hour)
  const first = Math.min(6, ...busy)
  const last = Math.max(22, ...busy)
  const shown = buckets.slice(first, last + 1)

  const valueOf = (bucket: (typeof shown)[number]) => (peakMetric.value === 'revenue' ? bucket.revenue : bucket.orders)
  const max = Math.max(0, ...shown.map(valueOf))
  const peak = max > 0 ? shown.reduce((best, bucket) => (valueOf(bucket) > valueOf(best) ? bucket : best)) : null

  return {
    peakHour: peak?.hour ?? null,
    bars: shown.map((bucket) => ({
      ...bucket,
      value: valueOf(bucket),
      height: max > 0 ? Math.max((valueOf(bucket) / max) * 100, valueOf(bucket) > 0 ? 3 : 1.5) : 1.5,
      display: peakMetric.value === 'revenue' ? formatCurrency(bucket.revenue) : `${bucket.orders} ${bucket.orders === 1 ? 'order' : 'orders'}`,
      time: new Intl.DateTimeFormat('en-PH', { hour: 'numeric', minute: '2-digit' }).format(new Date(2000, 0, 1, bucket.hour)),
    })),
  }
})

const hoveredHour = ref<number | null>(null)
const calloutHour = computed(() => hoveredHour.value ?? peakHours.value.peakHour)

// ── Recent sales ──────────────────────────────────────────────────────────

const searchQuery = ref('')
const paymentFilter = ref<'all' | PaymentMethod>('all')
const kindFilter = ref<'all' | OrderKind>('all')

const paymentFilterOptions: { value: 'all' | PaymentMethod; label: string }[] = [
  { value: 'all', label: 'All payments' },
  { value: 'cash', label: 'Cash' },
  { value: 'ewallet', label: 'E-wallet' },
  { value: 'card', label: 'Card' },
]

const kindFilterOptions: { value: 'all' | OrderKind; label: string }[] = [
  { value: 'all', label: 'All modes' },
  { value: 'in-store', label: 'Dine-in / POS' },
  { value: 'pickup', label: 'Pickup' },
  { value: 'delivery', label: 'Delivery' },
]

const recentSales = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase().replace(/[₱,]/g, '')

  return periodOrders.value
    .filter((order) => {
      if (paymentFilter.value !== 'all' && order.paymentMethod !== paymentFilter.value) return false
      if (kindFilter.value !== 'all' && orderKind(order) !== kindFilter.value) return false
      if (!needle) return true
      return (
        order.ticketNumber.toLowerCase().includes(needle)
        || (order.customerName ?? '').toLowerCase().includes(needle)
        || (order.totalCents / 100).toFixed(2).includes(needle)
      )
    })
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 8)
})

const recentCaption = computed(() => ({
  today: 'Latest transactions from today.',
  week: 'Latest transactions from this week.',
  month: 'Latest transactions from this month.',
  all: 'Latest transactions.',
})[range.value])

const dateFormat = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
const timeFormat = new Intl.DateTimeFormat('en-PH', { hour: 'numeric', minute: '2-digit' })

function statusOf(order: OrderSummary) {
  if (order.paymentStatus === 'unpaid') return { label: 'Unpaid', tone: 'warning' }
  if (order.status === 'served') return { label: 'Completed', tone: 'success' }
  if (order.status === 'ready') return { label: 'Ready', tone: 'info' }
  return { label: 'Preparing', tone: 'info' }
}
</script>

<template>
  <div class="sales-page">
    <header class="sales-header">
      <div>
        <h1 class="sales-title">Sales</h1>
        <p class="sales-copy">Monitor your revenue, orders, payment mix, and store performance at a glance.</p>
      </div>

      <div class="sales-range">
        <span class="sales-range__text">{{ rangeCaption }}</span>
        <RangeSelector v-model="range" />
      </div>
    </header>

    <section class="sales-kpis" aria-label="Sales summary">
      <article v-for="kpi in kpis" :key="kpi.key" class="sales-card sales-kpi">
        <span class="sales-kpi__icon"><component :is="kpi.icon" :size="24" aria-hidden="true" /></span>
        <div class="sales-kpi__body">
          <span class="sales-kpi__label">{{ kpi.label }}</span>
          <strong class="sales-kpi__value">{{ kpi.value }}</strong>
          <span v-if="kpi.delta" class="sales-kpi__delta" :class="kpi.delta.positive ? 'is-up' : 'is-down'">
            <component :is="kpi.delta.positive ? ArrowUp : ArrowDown" :size="14" aria-hidden="true" />
            <b>{{ kpi.delta.positive ? '+' : '−' }}{{ kpi.delta.value }}</b>
            <span>{{ comparisonLabel }}</span>
          </span>
          <span v-else-if="comparisonLabel" class="sales-kpi__delta sales-kpi__delta--none">No earlier sales to compare</span>
        </div>
        <span class="sales-kpi__info" tabindex="0" role="img" :aria-label="kpi.help" :data-tip="kpi.help">
          <Info :size="16" aria-hidden="true" />
        </span>
      </article>
    </section>

    <section class="sales-row sales-row--trend">
      <article class="sales-card sales-panel">
        <header class="sales-panel__head">
          <div>
            <h2>Sales Trend</h2>
            <p>{{ trendCaption }}</p>
          </div>
          <div class="sales-legend" aria-hidden="true">
            <span><i class="sales-legend__bar" />Gross Sales (₱)</span>
            <span><i class="sales-legend__line" />Orders</span>
          </div>
        </header>

        <div class="sales-trend">
          <svg
            class="sales-trend__svg"
            :viewBox="`0 0 ${CHART.width} ${CHART.height}`"
            role="img"
            :aria-label="`Sales trend: ${trendBuckets.map((b) => `${b.label} ${b.sublabel} ${formatCurrency(b.revenue)}, ${b.orders} orders`).join('; ')}`"
            @mouseleave="hoveredTrend = null"
          >
            <g class="sales-trend__grid">
              <g v-for="tick in trendChart.ticks" :key="tick.y">
                <line :x1="CHART.left" :x2="CHART.width - CHART.right" :y1="tick.y" :y2="tick.y" />
                <text :x="CHART.left - 10" :y="tick.y + 4" text-anchor="end">{{ tick.revenue }}</text>
                <text :x="CHART.width - CHART.right + 10" :y="tick.y + 4" text-anchor="start">{{ tick.orders }}</text>
              </g>
            </g>

            <g v-for="(point, index) in trendChart.points" :key="point.key" @mouseenter="hoveredTrend = index">
              <rect
                class="sales-trend__hit"
                :x="point.x - (CHART.width - CHART.left - CHART.right) / trendChart.points.length / 2"
                :y="CHART.top"
                :width="(CHART.width - CHART.left - CHART.right) / trendChart.points.length"
                :height="CHART.height - CHART.top - CHART.bottom"
              />
              <rect
                class="sales-trend__bar"
                :class="{ 'is-hovered': hoveredTrend === index }"
                :x="point.bar.x"
                :y="point.bar.y"
                :width="point.bar.width"
                :height="point.bar.height"
                rx="3"
              />
              <text v-if="point.showLabel" class="sales-trend__x" :x="point.x" :y="trendChart.baseline + 18" text-anchor="middle">{{ point.label }}</text>
              <text v-if="point.showLabel && point.sublabel" class="sales-trend__x sales-trend__x--sub" :x="point.x" :y="trendChart.baseline + 34" text-anchor="middle">{{ point.sublabel }}</text>
            </g>

            <path class="sales-trend__line" :d="trendChart.line" />
            <template v-if="trendChart.showDots">
              <circle
                v-for="(point, index) in trendChart.past"
                :key="`dot-${point.key}`"
                class="sales-trend__dot"
                :class="{ 'is-hovered': hoveredTrend === index }"
                :cx="point.x"
                :cy="point.y"
                :r="hoveredTrend === index ? 6 : 4.5"
              />
            </template>
          </svg>

          <div
            v-if="hoveredTrend !== null && trendChart.points[hoveredTrend]"
            class="sales-tip"
            :style="{
              left: `${(trendChart.points[hoveredTrend].x / CHART.width) * 100}%`,
              top: `${(Math.min(trendChart.points[hoveredTrend].bar.y, trendChart.points[hoveredTrend].y) / CHART.height) * 100}%`,
            }"
          >
            <strong>{{ formatCurrency(trendChart.points[hoveredTrend].revenue) }}</strong>
            <span>
              {{ trendChart.points[hoveredTrend].orders }} {{ trendChart.points[hoveredTrend].orders === 1 ? 'order' : 'orders' }}
              · {{ trendChart.points[hoveredTrend].tip }}
            </span>
          </div>

          <p v-if="!hasSales" class="sales-empty-overlay">No sales yet in this period.</p>
        </div>
      </article>

      <article class="sales-card sales-panel">
        <header class="sales-panel__head">
          <div>
            <h2>Payment Breakdown</h2>
            <p>Total sales by payment method.</p>
          </div>
        </header>

        <ul class="sales-shares">
          <li v-for="(row, index) in paymentRows" :key="row.key" class="sales-share">
            <span class="sales-share__icon"><component :is="row.icon" :size="19" aria-hidden="true" /></span>
            <span class="sales-share__label">{{ row.label }}</span>
            <strong class="sales-share__value">{{ formatCurrency(row.value) }}</strong>
            <span class="sales-share__pct">{{ row.share }}%</span>
            <span class="sales-share__track" aria-hidden="true">
              <span class="sales-share__fill" :class="`sales-share__fill--${index}`" :style="{ width: `${row.share}%` }" />
            </span>
          </li>
        </ul>
      </article>
    </section>

    <section class="sales-row sales-row--split">
      <article class="sales-card sales-panel">
        <header class="sales-panel__head">
          <div>
            <h2>Sales by Order Type</h2>
            <p>Breakdown of sales by fulfillment type.</p>
          </div>
        </header>

        <ul class="sales-shares sales-shares--plain">
          <li v-for="(row, index) in kindRows" :key="row.key" class="sales-share">
            <span class="sales-share__icon sales-share__icon--plain"><component :is="row.icon" :size="20" aria-hidden="true" /></span>
            <span class="sales-share__label">{{ row.label }}</span>
            <strong class="sales-share__value">{{ formatCurrency(row.value) }}</strong>
            <span class="sales-share__pct">{{ row.share }}%</span>
            <span class="sales-share__track" aria-hidden="true">
              <span class="sales-share__fill" :class="`sales-share__fill--${index}`" :style="{ width: `${row.share}%` }" />
            </span>
          </li>
        </ul>
      </article>

      <article class="sales-card sales-panel">
        <header class="sales-panel__head">
          <div>
            <h2>Peak Selling Hours</h2>
            <p>Sales volume by hour of day.</p>
          </div>
          <AutocompleteSelect v-model="peakMetric" class="sales-peak__metric" label="Peak hours measure" :options="peakMetricOptions" />
        </header>

        <div class="sales-peak" @mouseleave="hoveredHour = null">
          <div class="sales-peak__bars">
            <div
              v-for="bar in peakHours.bars"
              :key="bar.hour"
              class="sales-peak__col"
              @mouseenter="hoveredHour = bar.hour"
            >
              <div
                v-if="calloutHour === bar.hour && bar.value > 0"
                class="sales-peak__callout"
                :style="{ bottom: `calc(${bar.height}% + 6px)` }"
              >
                <strong>{{ bar.display }}</strong>
                <span>{{ bar.time }}</span>
              </div>
              <span
                class="sales-peak__bar"
                :class="{ 'is-peak': calloutHour === bar.hour && bar.value > 0 }"
                :style="{ height: `${bar.height}%` }"
                :aria-label="`${bar.time}: ${bar.display}`"
                role="img"
              />
            </div>
          </div>
          <div class="sales-peak__axis" aria-hidden="true">
            <span v-for="bar in peakHours.bars" :key="bar.hour">{{ bar.label }}</span>
          </div>
          <p v-if="!hasSales" class="sales-empty-overlay">Busy hours appear after your first sales.</p>
        </div>
      </article>
    </section>

    <section class="sales-card sales-panel sales-recent">
      <header class="sales-panel__head sales-recent__head">
        <div>
          <h2>Recent Sales</h2>
          <p>{{ recentCaption }}</p>
        </div>
        <RouterLink class="sales-viewall" to="/orders">View all <ArrowRight :size="16" aria-hidden="true" /></RouterLink>
        <div class="sales-recent__filters">
          <label class="sales-search">
            <Search :size="16" aria-hidden="true" />
            <input v-model="searchQuery" type="search" placeholder="Search ticket, customer, or amount…" aria-label="Search recent sales" />
          </label>
          <AutocompleteSelect v-model="paymentFilter" class="sales-select" label="Filter by payment method" :options="paymentFilterOptions" />
          <AutocompleteSelect v-model="kindFilter" class="sales-select" label="Filter by order type" :options="kindFilterOptions" />
        </div>
      </header>

      <div v-if="recentSales.length === 0" class="sales-recent__empty">
        {{ hasSales ? 'No sales match these filters.' : 'No sales yet in this period.' }}
      </div>
      <div v-else class="sales-table" role="table" aria-label="Recent sales">
        <div class="sales-table__row sales-table__row--head" role="row">
          <span role="columnheader">#</span>
          <span role="columnheader">Customer</span>
          <span role="columnheader">Payment</span>
          <span role="columnheader">Mode</span>
          <span role="columnheader">Total</span>
          <span role="columnheader">Date &amp; Time</span>
          <span role="columnheader">Status</span>
          <span role="columnheader" class="sales-table__actions">Actions</span>
        </div>
        <div v-for="order in recentSales" :key="order.id" class="sales-table__row" role="row">
          <span role="cell" class="sales-table__ticket">#{{ order.ticketNumber }}</span>
          <span role="cell" class="sales-table__customer">{{ order.customerName || 'Walk-in' }}</span>
          <span role="cell" class="sales-table__iconcell">
            <component :is="paymentMeta[order.paymentMethod].icon" :size="16" aria-hidden="true" />
            {{ paymentMeta[order.paymentMethod].label }}
          </span>
          <span role="cell" class="sales-table__iconcell">
            <component :is="kindMeta[orderKind(order)].icon" :size="16" aria-hidden="true" />
            {{ kindMeta[orderKind(order)].label }}
          </span>
          <strong role="cell" class="sales-table__total">{{ formatCurrency(order.totalCents) }}</strong>
          <span role="cell" class="sales-table__date">
            {{ dateFormat.format(new Date(order.createdAt)) }}
            <small>{{ timeFormat.format(new Date(order.createdAt)) }}</small>
          </span>
          <span role="cell">
            <span class="sales-status" :class="`sales-status--${statusOf(order).tone}`">{{ statusOf(order).label }}</span>
          </span>
          <span role="cell" class="sales-table__actions">
            <RouterLink
              class="sales-table__more"
              to="/orders"
              :aria-label="`Find ticket ${order.ticketNumber} in Orders`"
              title="View in Orders"
            >
              <Ellipsis :size="18" aria-hidden="true" />
            </RouterLink>
          </span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.sales-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  padding: 4px 0 32px;
}

/* ── Header ───────────────────────────────────────────────────────────── */

.sales-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.sales-title {
  margin: 0;
  font: var(--type-title1);
  letter-spacing: -0.025em;
  color: var(--text-primary);
}

.sales-copy {
  max-width: 80ch;
  margin: 3px 0 0;
  color: var(--text-secondary);
  font: var(--type-subhead);
}

.sales-range {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.sales-range__text {
  color: var(--text-secondary);
  font: var(--type-caption);
  font-size: 0.8125rem;
  white-space: nowrap;
}

/* ── Cards ────────────────────────────────────────────────────────────── */

.sales-card {
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--separator) 72%, transparent);
  border-radius: 15px;
  background: var(--bg-elevated);
  box-shadow: 0 2px 8px rgba(20, 43, 32, 0.035);
}

.sales-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.sales-kpi {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 16px;
}

.sales-kpi__icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--accent) 11%, transparent);
  color: var(--accent);
}

.sales-kpi__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.sales-kpi__label {
  color: var(--text-secondary);
  font: 500 0.8125rem/1.1rem var(--font-sans);
}

.sales-kpi__value {
  color: var(--text-primary);
  font: 700 1.5rem/1.9rem var(--font-sans);
  letter-spacing: -0.015em;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sales-kpi__delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  color: var(--text-secondary);
  font: 400 0.8125rem/1rem var(--font-sans);
  white-space: nowrap;
}

.sales-kpi__delta b {
  margin-right: 6px;
  font-weight: 600;
}

.sales-kpi__delta.is-up b,
.sales-kpi__delta.is-up svg {
  color: var(--accent);
}

.sales-kpi__delta.is-down b,
.sales-kpi__delta.is-down svg {
  color: var(--danger);
}

.sales-kpi__delta--none {
  color: var(--text-tertiary);
}

.sales-kpi__info {
  position: absolute;
  top: 14px;
  right: 14px;
  display: grid;
  place-items: center;
  color: var(--text-tertiary);
  border-radius: 50%;
  cursor: help;
  outline: none;
}

.sales-kpi__info:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 22%, transparent);
}

.sales-kpi__info::after {
  content: attr(data-tip);
  position: absolute;
  top: calc(100% + 8px);
  right: -6px;
  z-index: 20;
  width: 220px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--text-primary);
  color: var(--bg-elevated);
  font: 400 0.75rem/1.05rem var(--font-sans);
  text-align: left;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-4px);
  transition: opacity var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.sales-kpi__info:hover::after,
.sales-kpi__info:focus-visible::after {
  opacity: 1;
  transform: none;
}

/* ── Panels ───────────────────────────────────────────────────────────── */

.sales-row {
  display: grid;
  gap: 16px;
}

.sales-row--trend {
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
}

.sales-row--split {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.sales-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 18px 16px;
}

.sales-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.sales-panel__head h2 {
  margin: 0;
  color: var(--text-primary);
  font: 700 1.0625rem/1.4rem var(--font-sans);
  letter-spacing: -0.01em;
}

.sales-panel__head p {
  margin: 2px 0 0;
  color: var(--text-secondary);
  font: 400 0.8125rem/1.1rem var(--font-sans);
}

.sales-legend {
  display: flex;
  gap: 16px;
  color: var(--text-secondary);
  font: 500 0.8125rem/1rem var(--font-sans);
  white-space: nowrap;
}

.sales-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.sales-legend__bar {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: var(--accent);
}

.sales-legend__line {
  position: relative;
  width: 18px;
  height: 2px;
  background: color-mix(in srgb, var(--accent) 60%, #6fcf8e);
}

.sales-legend__line::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: inherit;
  transform: translate(-50%, -50%);
}

/* ── Trend chart ──────────────────────────────────────────────────────── */

.sales-trend {
  position: relative;
}

.sales-trend__svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

.sales-trend__grid line {
  stroke: var(--separator);
  stroke-width: 1;
}

.sales-trend__grid text,
.sales-trend__x {
  fill: var(--text-secondary);
  font: 500 12px var(--font-sans);
  font-variant-numeric: tabular-nums;
}

.sales-trend__x--sub {
  fill: var(--text-tertiary);
  font-weight: 400;
}

.sales-trend__hit {
  fill: transparent;
}

.sales-trend__bar {
  fill: color-mix(in srgb, var(--accent) 30%, var(--bg-elevated));
  transition: fill var(--dur-fast) var(--ease-out);
}

.sales-trend__bar.is-hovered {
  fill: color-mix(in srgb, var(--accent) 48%, var(--bg-elevated));
}

.sales-trend__line {
  fill: none;
  stroke: var(--accent);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
  pointer-events: none;
}

.sales-trend__dot {
  fill: var(--accent);
  stroke: var(--bg-elevated);
  stroke-width: 2;
  pointer-events: none;
}

.sales-tip {
  position: absolute;
  z-index: 5;
  display: grid;
  gap: 1px;
  padding: 6px 10px;
  border: 1px solid var(--separator);
  border-radius: 9px;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-md);
  transform: translate(-50%, calc(-100% - 10px));
  pointer-events: none;
  white-space: nowrap;
}

.sales-tip strong {
  color: var(--text-primary);
  font: 700 0.8125rem/1.1rem var(--font-sans);
}

.sales-tip span {
  color: var(--text-secondary);
  font: 400 0.75rem/1rem var(--font-sans);
}

.sales-empty-overlay {
  position: absolute;
  inset: 0 0 20px;
  display: grid;
  place-items: center;
  margin: 0;
  color: var(--text-secondary);
  font: 500 0.875rem/1.2rem var(--font-sans);
  pointer-events: none;
}

/* ── Share rows (payment, order type) ─────────────────────────────────── */

.sales-shares {
  flex: 1;
  display: grid;
  grid-auto-rows: minmax(52px, 1fr);
  margin: 0;
  padding: 0;
  list-style: none;
}

.sales-share {
  display: grid;
  grid-template-columns: 36px minmax(70px, 1fr) auto 48px minmax(80px, 1.7fr);
  align-items: center;
  gap: 12px;
  min-height: 52px;
  border-bottom: 1px solid color-mix(in srgb, var(--separator) 70%, transparent);
}

.sales-share:last-child {
  border-bottom: none;
}

.sales-share__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--accent);
}

.sales-share__icon--plain {
  background: transparent;
  color: var(--text-secondary);
}

.sales-share__label {
  color: var(--text-primary);
  font: 500 0.875rem/1.2rem var(--font-sans);
}

.sales-share__value {
  color: var(--text-primary);
  font: 600 0.875rem/1.2rem var(--font-sans);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.sales-share__pct {
  color: var(--text-secondary);
  font: 500 0.8125rem/1rem var(--font-sans);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.sales-share__track {
  height: 12px;
  border-radius: 999px;
  background: var(--fill);
  overflow: hidden;
}

.sales-share__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width var(--dur-medium, 240ms) var(--ease-out);
}

.sales-share__fill--0 { background: var(--accent); }
.sales-share__fill--1 { background: color-mix(in srgb, var(--accent) 72%, #7fd49a); }
.sales-share__fill--2 { background: color-mix(in srgb, var(--accent) 50%, #9fe0b3); }

/* ── Peak hours ───────────────────────────────────────────────────────── */

.sales-peak__metric {
  flex: none;
  width: 170px;
}

.sales-peak__metric :deep(.acselect__trigger) {
  height: 36px;
  min-height: 36px;
  font-size: 0.8125rem;
}

.sales-peak {
  position: relative;
  display: grid;
  gap: 6px;
  padding-top: 38px;
}

.sales-peak__bars,
.sales-peak__axis {
  display: grid;
  grid-auto-columns: minmax(0, 1fr);
  grid-auto-flow: column;
  gap: 6px;
}

.sales-peak__bars {
  height: 110px;
  align-items: end;
  border-bottom: 1px solid var(--separator);
}

.sales-peak__col {
  position: relative;
  display: flex;
  align-items: flex-end;
  height: 100%;
}

.sales-peak__bar {
  width: 100%;
  border-radius: 3px 3px 0 0;
  background: color-mix(in srgb, var(--accent) 30%, var(--bg-elevated));
  transition: background var(--dur-fast) var(--ease-out), height var(--dur-medium, 240ms) var(--ease-out);
}

.sales-peak__bar.is-peak {
  background: var(--accent);
}

.sales-peak__callout {
  position: absolute;
  left: 50%;
  z-index: 2;
  display: grid;
  justify-items: center;
  padding: 4px 8px;
  border: 1px solid var(--separator);
  border-radius: 7px;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-sm);
  transform: translateX(-50%);
  white-space: nowrap;
  pointer-events: none;
}

.sales-peak__callout strong {
  color: var(--text-primary);
  font: 700 0.75rem/1rem var(--font-sans);
}

.sales-peak__callout span {
  color: var(--text-secondary);
  font: 400 0.6875rem/0.9rem var(--font-sans);
}

.sales-peak__axis span {
  overflow: hidden;
  color: var(--text-secondary);
  font: 500 0.75rem/1rem var(--font-sans);
  text-align: center;
  white-space: nowrap;
}

/* ── Recent sales ─────────────────────────────────────────────────────── */

.sales-recent__head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'title viewall'
    'title filters';
  align-items: center;
  row-gap: 6px;
}

.sales-recent__head > div:first-child {
  grid-area: title;
  align-self: start;
}

.sales-viewall {
  grid-area: viewall;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-self: end;
  align-self: start;
  color: var(--accent);
  font: 600 0.875rem/1.2rem var(--font-sans);
  text-decoration: none;
}

.sales-viewall:hover {
  text-decoration: underline;
}

.sales-recent__filters {
  grid-area: filters;
  display: grid;
  grid-template-columns: minmax(180px, 290px) 190px 190px;
  justify-content: end;
  gap: 10px;
}

.sales-search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 40px;
  padding: 0 var(--space-3);
  border: 1px solid var(--separator);
  border-radius: 10px;
  background: var(--bg-elevated);
  color: var(--text-secondary);
}

.sales-search:focus-within {
  border-color: var(--accent);
}

.sales-search input {
  width: 100%;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font: 400 0.8125rem/1rem var(--font-sans);
}

.sales-select {
  width: 100%;
  min-width: 0;
}

.sales-select :deep(.acselect__trigger) {
  height: 40px;
  min-height: 40px;
  border-radius: 10px;
  border-width: 1px;
  font-size: 0.8125rem;
}

.sales-recent__empty {
  padding: 28px 0 12px;
  color: var(--text-secondary);
  text-align: center;
}

.sales-table {
  display: grid;
}

.sales-table__row {
  display: grid;
  grid-template-columns: 0.8fr 1.3fr 1fr 1.1fr 0.9fr 1.35fr 0.95fr 64px;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 0 4px;
  border-bottom: 1px solid color-mix(in srgb, var(--separator) 70%, transparent);
  color: var(--text-primary);
  font: 400 0.8125rem/1.1rem var(--font-sans);
}

.sales-table__row:last-child {
  border-bottom: none;
}

.sales-table__row--head {
  min-height: 34px;
  color: var(--text-secondary);
  font-weight: 500;
}

.sales-table__row > * {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sales-table__ticket {
  font-variant-numeric: tabular-nums;
}

.sales-table__iconcell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.sales-table__iconcell svg {
  flex: none;
  color: var(--text-secondary);
}

.sales-table__total {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.sales-table__date small {
  margin-left: 6px;
  color: var(--text-secondary);
  font-size: inherit;
}

.sales-table__actions {
  text-align: center;
}

.sales-table__more {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 28px;
  border-radius: 8px;
  color: var(--text-secondary);
}

.sales-table__more:hover,
.sales-table__more:focus-visible {
  background: var(--fill);
  color: var(--text-primary);
  outline: none;
}

.sales-status {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font: 600 0.75rem/1rem var(--font-sans);
}

.sales-status--success {
  background: color-mix(in srgb, var(--success) 14%, transparent);
  color: color-mix(in srgb, var(--success) 72%, #086837);
}

.sales-status--warning {
  background: color-mix(in srgb, var(--warning) 16%, transparent);
  color: color-mix(in srgb, var(--warning) 80%, #8a4b00);
}

.sales-status--info {
  background: var(--fill);
  color: var(--text-secondary);
}

/* ── Responsive ───────────────────────────────────────────────────────── */

@media (max-width: 1280px) {
  .sales-kpi__value {
    font-size: 1.3rem;
  }

  .sales-kpi__icon {
    width: 46px;
    height: 46px;
  }
}

@media (max-width: 1100px) {
  .sales-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sales-row--trend,
  .sales-row--split {
    grid-template-columns: minmax(0, 1fr);
  }

  .sales-table__row {
    grid-template-columns: 0.8fr 1.3fr 1fr 1.1fr 0.9fr 0.95fr 48px;
  }

  .sales-table__date {
    display: none;
  }
}

@media (max-width: 780px) {
  .sales-header {
    flex-direction: column;
    align-items: stretch;
  }

  .sales-range {
    justify-content: flex-start;
  }

  .sales-recent__head {
    grid-template-areas:
      'title viewall'
      'filters filters';
  }

  .sales-recent__filters {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .sales-search {
    grid-column: 1 / -1;
  }

  .sales-legend {
    display: none;
  }

  .sales-peak__axis span {
    overflow: visible;
    font-size: 0.6875rem;
  }

  .sales-peak__axis span:nth-child(even) {
    visibility: hidden;
  }

  .sales-peak__bars,
  .sales-peak__axis {
    gap: 3px;
  }

  .sales-panel__head:has(.sales-peak__metric) {
    flex-direction: column;
  }

  /* Below this the chart's text would shrink past reading; it scrolls sideways in its card instead. */
  .sales-trend {
    overflow-x: auto;
    margin: 0 -18px;
    padding: 0 18px;
  }

  .sales-trend__svg {
    min-width: 600px;
  }

  .sales-table__row--head {
    display: none;
  }

  /* Each sale becomes a two-line card: who and how much, then how and when. */
  .sales-table__row {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas:
      'customer total'
      'meta status';
    gap: 4px 12px;
    padding: 10px 0;
  }

  .sales-table__row > * { display: none; }
  .sales-table__customer { display: block; grid-area: customer; font-weight: 600; }
  .sales-table__total { display: block; grid-area: total; text-align: right; }
  .sales-table__row > [role='cell']:nth-child(3) { display: inline-flex; grid-area: meta; color: var(--text-secondary); }
  .sales-table__row > [role='cell']:nth-child(7) { display: block; grid-area: status; text-align: right; }
}

@media (max-width: 520px) {
  .sales-kpis {
    grid-template-columns: minmax(0, 1fr);
  }

  .sales-share {
    grid-template-columns: 34px minmax(0, 1fr) auto 40px;
  }

  .sales-share__track {
    grid-column: 2 / -1;
    margin-bottom: 10px;
  }
}
</style>
