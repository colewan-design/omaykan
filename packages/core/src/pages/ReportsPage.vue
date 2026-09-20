<script setup lang="ts">
import { FileSpreadsheet, Landmark, ReceiptText, Scale, Search } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import ChartCard from '@pos/core/components/ChartCard.vue'
import MetricCard from '@pos/core/components/MetricCard.vue'
import RangeSelector, { type Range } from '@pos/core/components/RangeSelector.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { businessModeLabel, formatCompactDate, formatCurrency, type OrderSummary, type PaymentMethod } from '@pos/shared/index'

const store = usePosStore()

onMounted(() => {
  if (!store.isReady) {
    void store.initialize()
  }
  void store.refreshShiftHistory()
})

const range = ref<Range>('today')
const reportView = ref<'summary' | 'payments' | 'shift'>('summary')
const searchQuery = ref('')
const now = new Date()

interface Bounds {
  start: Date
  end: Date
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function getMonday(d: Date) {
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  return new Date(startOfDay(d).getTime() + diff * 86400000)
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

function inBounds(order: OrderSummary, start: Date, end: Date) {
  const createdAt = new Date(order.createdAt)
  return createdAt >= start && createdAt < end
}

const bounds = computed(() => getBounds(range.value))
const reportOrders = computed(() =>
  store.orders.filter((order) => !order.voidedAt && inBounds(order, bounds.value.start, bounds.value.end)),
)

const grossSales = computed(() => reportOrders.value.reduce((sum, order) => sum + order.totalCents, 0))
const taxCollected = computed(() => reportOrders.value.reduce((sum, order) => sum + order.taxCents, 0))
// What came off, over the period. A discount nobody sees at the end of the day
// is indistinguishable from money gone missing.
const discountsGiven = computed(() => reportOrders.value.reduce((sum, order) => sum + (order.discountCents ?? 0), 0))
const netSales = computed(() => grossSales.value - taxCollected.value)
const orderCount = computed(() => reportOrders.value.length)
const averageOrder = computed(() => orderCount.value > 0 ? Math.round(grossSales.value / orderCount.value) : 0)

const paymentTotals = computed(() => {
  const methods: PaymentMethod[] = ['cash', 'card', 'ewallet']
  return methods.map((method) => {
    const total = reportOrders.value
      .filter((order) => order.paymentMethod === method)
      .reduce((sum, order) => sum + order.totalCents, 0)
    const count = reportOrders.value.filter((order) => order.paymentMethod === method).length
    const share = grossSales.value > 0 ? Math.round((total / grossSales.value) * 100) : 0
    return { method, total, count, share }
  })
})

const businessModeTotals = computed(() => {
  const modes = ['coffee-shop', 'grocery', 'restaurant', 'nail-salon'] as const
  return modes
    .map((mode) => {
      const orders = reportOrders.value.filter((order) => order.businessMode === mode)
      return {
        mode,
        label: businessModeLabel(mode),
        orders: orders.length,
        total: orders.reduce((sum, order) => sum + order.totalCents, 0),
      }
    })
    .filter((entry) => entry.orders > 0)
    .sort((a, b) => b.total - a.total)
})

const topLines = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()
  const map = new Map<string, { name: string; quantity: number; salesCents: number }>()
  for (const order of reportOrders.value) {
    for (const item of order.items) {
      const existing = map.get(item.productId) ?? { name: item.name, quantity: 0, salesCents: 0 }
      existing.quantity += item.quantity
      existing.salesCents += item.lineTotalCents
      map.set(item.productId, existing)
    }
  }

  return Array.from(map.values())
    .filter((item) => !needle || item.name.toLowerCase().includes(needle))
    .sort((a, b) => b.salesCents - a.salesCents)
    .slice(0, 8)
})

function varianceClass(varianceCents?: number | null): string {
  if (!varianceCents) return 'reports-variance--even'
  return varianceCents < 0 ? 'reports-variance--short' : 'reports-variance--over'
}

function varianceLabel(varianceCents?: number | null): string {
  if (!varianceCents) return 'Exact'
  return varianceCents < 0 ? `${formatCurrency(Math.abs(varianceCents))} short` : `${formatCurrency(varianceCents)} over`
}

const shiftReport = computed(() => {
  const shift = store.activeShift
  if (!shift) {
    return null
  }

  return {
    openedAt: shift.openedAt,
    openingCashCents: shift.openingCashCents,
    cashSalesCents: shift.cashSalesCents,
    totalSalesCents: shift.totalSalesCents,
    orderCount: shift.orderCount,
    payInsCents: shift.payInsCents,
    payOutsCents: shift.payOutsCents,
    expectedCashCents: shift.expectedCashCents,
    movements: shift.movements.slice(0, 6),
  }
})

const recentOrders = computed(() =>
  reportOrders.value
    .slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 10),
)

const rangeCaption = computed(() => {
  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
  if (range.value === 'today') {
    return formatter.format(now)
  }
  const endForCaption = new Date(bounds.value.end.getTime() - 86400000)
  return `${formatter.format(bounds.value.start)} - ${formatter.format(endForCaption)}`
})
</script>

<template>
  <div class="reports-page">
    <section class="reports-header">
      <div>
        <h1 class="reports-title">Reports</h1>
        <p class="reports-copy">
          Review audit-friendly daily summaries, payment totals, shift cash position, and top-selling lines in a
          format that’s closer to an X report than a live dashboard.
        </p>
      </div>

      <div class="reports-toolbar">
        <div class="reports-range">
          <span class="reports-range__text">{{ rangeCaption }}</span>
          <RangeSelector v-model="range" />
        </div>
      </div>
    </section>

    <section class="reports-kpis">
      <MetricCard label="Gross Sales" :value="formatCurrency(grossSales)" />
      <MetricCard label="Net Sales" :value="formatCurrency(netSales)" />
      <MetricCard label="Tax Collected" :value="formatCurrency(taxCollected)" />
      <MetricCard label="Discounts Given" :value="formatCurrency(discountsGiven)" />
      <MetricCard label="Orders / Avg Ticket" :value="`${orderCount} / ${formatCurrency(averageOrder)}`" />
    </section>

    <div class="reports-view-switch">
      <button class="segment-button" :class="{ active: reportView === 'summary' }" type="button" @click="reportView = 'summary'">
        Summary
      </button>
      <button class="segment-button" :class="{ active: reportView === 'payments' }" type="button" @click="reportView = 'payments'">
        Payments
      </button>
      <button class="segment-button" :class="{ active: reportView === 'shift' }" type="button" @click="reportView = 'shift'">
        Shift
      </button>
    </div>

    <section v-if="reportView === 'summary'" class="reports-grid">
      <ChartCard title="Report Summary" summary="High-level totals suitable for a daily report review.">
        <div class="reports-summary">
          <div class="reports-summary__row">
            <span><FileSpreadsheet :size="15" /> Report period</span>
            <strong>{{ rangeCaption }}</strong>
          </div>
          <div class="reports-summary__row">
            <span><ReceiptText :size="15" /> Completed orders</span>
            <strong>{{ orderCount }}</strong>
          </div>
          <div class="reports-summary__row">
            <span><Scale :size="15" /> Net vs tax</span>
            <strong>{{ formatCurrency(netSales) }} / {{ formatCurrency(taxCollected) }}</strong>
          </div>
          <div class="reports-summary__row">
            <span><Landmark :size="15" /> Cash expected</span>
            <strong>{{ shiftReport ? formatCurrency(shiftReport.expectedCashCents) : 'No open shift' }}</strong>
          </div>
        </div>
      </ChartCard>

      <ChartCard title="Business Mode Totals" summary="Revenue totals grouped by configured business modes.">
        <div v-if="businessModeTotals.length === 0" class="empty-state">
          No sales recorded in this report range.
        </div>
        <div v-else class="reports-mode-list">
          <div v-for="entry in businessModeTotals" :key="entry.mode" class="reports-mode-row">
            <div>
              <strong>{{ entry.label }}</strong>
              <p>{{ entry.orders }} orders</p>
            </div>
            <strong>{{ formatCurrency(entry.total) }}</strong>
          </div>
        </div>
      </ChartCard>
    </section>

    <section v-else-if="reportView === 'payments'" class="reports-grid">
      <ChartCard title="Payment Breakdown" summary="Revenue and order counts by payment method.">
        <div class="reports-payment-list">
          <div v-for="entry in paymentTotals" :key="entry.method" class="reports-payment-row">
            <div>
              <strong>{{ entry.method }}</strong>
              <p>{{ entry.count }} orders · {{ entry.share }}% of sales</p>
            </div>
            <strong>{{ formatCurrency(entry.total) }}</strong>
          </div>
        </div>
      </ChartCard>

      <ChartCard title="Recent Orders" summary="Latest orders included in the current report range.">
        <div v-if="recentOrders.length === 0" class="empty-state">
          No orders in this range.
        </div>
        <div v-else class="reports-order-list">
          <article v-for="order in recentOrders" :key="order.id" class="reports-order-row">
            <div>
              <strong>Ticket {{ order.ticketNumber }}</strong>
              <p>{{ formatCompactDate(order.createdAt) }} · {{ order.paymentMethod }} · {{ businessModeLabel(order.businessMode) }}</p>
            </div>
            <strong>{{ formatCurrency(order.totalCents) }}</strong>
          </article>
        </div>
      </ChartCard>
    </section>

    <section v-else class="reports-grid">
      <ChartCard title="Shift Snapshot" summary="Current shift cash position and recent cash movements.">
        <div v-if="!shiftReport" class="empty-state">
          Open a shift to generate a live shift report snapshot.
        </div>
        <div v-else class="reports-shift">
          <div class="reports-shift__stats">
            <div class="reports-shift__stat">
              <span>Opened</span>
              <strong>{{ formatCompactDate(shiftReport.openedAt) }}</strong>
            </div>
            <div class="reports-shift__stat">
              <span>Total sales</span>
              <strong>{{ formatCurrency(shiftReport.totalSalesCents) }}</strong>
            </div>
            <div class="reports-shift__stat">
              <span>Orders</span>
              <strong>{{ shiftReport.orderCount }}</strong>
            </div>
            <div class="reports-shift__stat">
              <span>Opening float</span>
              <strong>{{ formatCurrency(shiftReport.openingCashCents) }}</strong>
            </div>
            <div class="reports-shift__stat">
              <span>Cash sales</span>
              <strong>{{ formatCurrency(shiftReport.cashSalesCents) }}</strong>
            </div>
            <div class="reports-shift__stat">
              <span>Expected cash</span>
              <strong>{{ formatCurrency(shiftReport.expectedCashCents) }}</strong>
            </div>
          </div>

          <div class="reports-shift__movement-list">
            <div v-for="movement in shiftReport.movements" :key="movement.id" class="reports-shift__movement">
              <div>
                <strong>{{ movement.movementType === 'pay_in' ? 'Pay in' : 'Pay out' }}</strong>
                <p>{{ movement.reason || 'No reason provided' }}</p>
              </div>
              <strong>{{ formatCurrency(movement.amountCents) }}</strong>
            </div>
          </div>
        </div>
      </ChartCard>

      <ChartCard title="Shift History" summary="Past closed shifts, with the counted-cash variance for each.">
        <div v-if="store.shiftHistory.length === 0" class="empty-state">
          No shifts have been closed yet.
        </div>
        <div v-else class="reports-shift-history">
          <div v-for="shift in store.shiftHistory" :key="shift.id" class="reports-shift-history__row">
            <div>
              <strong>{{ formatCompactDate(shift.openedAt) }} – {{ shift.closedAt ? formatCompactDate(shift.closedAt) : 'Open' }}</strong>
              <p>{{ shift.orderCount }} orders · {{ formatCurrency(shift.totalSalesCents) }} total sales</p>
            </div>
            <div class="reports-shift-history__cash">
              <span>Expected {{ formatCurrency(shift.expectedCashCents) }} · Counted {{ formatCurrency(shift.closingCashCents ?? 0) }}</span>
              <strong :class="varianceClass(shift.varianceCashCents)">{{ varianceLabel(shift.varianceCashCents) }}</strong>
            </div>
          </div>
        </div>
      </ChartCard>

      <ChartCard title="Top Selling Lines" summary="Most valuable items in the selected report range.">
        <div class="reports-lines__toolbar">
          <label class="reports-search">
            <Search :size="16" />
            <input v-model="searchQuery" type="search" placeholder="Search line item name" />
          </label>
        </div>

        <div v-if="topLines.length === 0" class="empty-state">
          No matching line items in this report range.
        </div>
        <div v-else class="reports-lines">
          <div v-for="line in topLines" :key="line.name" class="reports-line-row">
            <div>
              <strong>{{ line.name }}</strong>
              <p>{{ line.quantity }} sold</p>
            </div>
            <strong>{{ formatCurrency(line.salesCents) }}</strong>
          </div>
        </div>
      </ChartCard>
    </section>
  </div>
</template>

<style scoped>
.reports-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-5);
}

.reports-header,
.reports-kpis,
.reports-grid {
  display: grid;
  gap: var(--space-4);
}

.reports-header {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
}

.reports-title {
  margin: 0;
  font: var(--type-title1);
}

.reports-copy {
  max-width: 72ch;
  margin: var(--space-2) 0 0;
  color: var(--text-secondary);
}

.reports-toolbar,
.reports-range,
.reports-summary__row,
.reports-lines__toolbar {
  display: flex;
  align-items: center;
}

.reports-range {
  gap: var(--space-3);
  justify-content: end;
  flex-wrap: wrap;
}

.reports-range__text {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.reports-kpis {
  /* Five figures: gross, net, tax, discounts, orders. Wraps before it squeezes. */
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
}

.reports-view-switch {
  display: inline-flex;
  gap: var(--space-2);
  padding: 4px;
  border-radius: var(--radius-pill);
  background: var(--fill);
  width: fit-content;
}

.reports-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.reports-summary,
.reports-mode-list,
.reports-payment-list,
.reports-order-list,
.reports-shift,
.reports-lines {
  display: grid;
  gap: var(--space-4);
}

.reports-summary__row,
.reports-mode-row,
.reports-payment-row,
.reports-order-row,
.reports-line-row,
.reports-shift__movement {
  justify-content: space-between;
  gap: var(--space-3);
}

.reports-summary__row span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
}

.reports-summary__row strong,
.reports-mode-row strong,
.reports-payment-row strong,
.reports-order-row strong,
.reports-line-row strong,
.reports-shift__movement strong,
.reports-shift__stat strong {
  font: var(--type-subhead);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.reports-mode-row,
.reports-payment-row,
.reports-order-row,
.reports-line-row,
.reports-shift__movement {
  display: flex;
  align-items: center;
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--separator);
}

.reports-mode-row:last-child,
.reports-payment-row:last-child,
.reports-order-row:last-child,
.reports-line-row:last-child,
.reports-shift__movement:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.reports-mode-row p,
.reports-payment-row p,
.reports-order-row p,
.reports-line-row p,
.reports-shift__movement p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.reports-shift__stats {
  display: grid;
  gap: var(--space-3);
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.reports-shift__stat {
  display: grid;
  gap: 4px;
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-elevated) 94%, transparent);
}

.reports-shift-history {
  display: grid;
  gap: var(--space-3);
}

.reports-shift-history__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--separator);
}

.reports-shift-history__row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.reports-shift-history__row p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.reports-shift-history__cash {
  display: grid;
  justify-items: end;
  gap: 4px;
  text-align: right;
}

.reports-shift-history__cash span {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.reports-variance--short {
  color: var(--danger);
}

.reports-variance--over {
  color: var(--warning);
}

.reports-variance--even {
  color: var(--success);
}

.reports-shift__stat span {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.reports-search {
  min-width: min(100%, 320px);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: 0 var(--space-3);
  border: 1px solid var(--separator);
  border-radius: 14px;
  background: var(--bg-elevated);
  color: var(--text-secondary);
}

.reports-search input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
}

@media (max-width: 1100px) {
  .reports-header,
  .reports-kpis,
  .reports-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .reports-range {
    justify-content: start;
  }
}

@media (max-width: 720px) {
  .reports-summary__row,
  .reports-mode-row,
  .reports-payment-row,
  .reports-order-row,
  .reports-line-row,
  .reports-shift__movement,
  .reports-shift-history__row {
    flex-direction: column;
    align-items: stretch;
  }

  .reports-shift__stats {
    grid-template-columns: minmax(0, 1fr);
  }

  .reports-shift-history__cash {
    justify-items: start;
    text-align: left;
  }
}
</style>
