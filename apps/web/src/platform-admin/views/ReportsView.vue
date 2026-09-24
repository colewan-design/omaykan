<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowDownToLine,
  ArrowUp,
  BarChart3,
  CalendarDays,
  CircleDollarSign,
  ClipboardList,
  Clock3,
  CreditCard,
  FileText,
  Info,
  Package,
  Percent,
  ReceiptText,
  ShoppingCart,
  Store,
  Tag,
} from '@lucide/vue'
import { api, type ReportRange, type ReportSnapshot } from '../api'
import { count, dateTime, percentDelta, pesos, pesosCompact, shortDate } from '../format'
import CategoryDonut from '../charts/CategoryDonut.vue'
import HourlyBarChart from '../charts/HourlyBarChart.vue'
import TrendChart from '../charts/TrendChart.vue'

type ReportTab = 'summary' | 'payments' | 'activity'

const RANGES: Array<{ value: ReportRange; label: string }> = [
  { value: 'today', label: 'Today' },
  { value: '7', label: '7 days' },
  { value: '30', label: '30 days' },
  { value: '365', label: '12 months' },
  { value: 'all', label: 'All' },
]

const TABS: Array<{ value: ReportTab; label: string; icon: typeof BarChart3 }> = [
  { value: 'summary', label: 'Summary', icon: BarChart3 },
  { value: 'payments', label: 'Payments', icon: CreditCard },
  { value: 'activity', label: 'Activity', icon: Clock3 },
]

const data = ref<ReportSnapshot | null>(null)
const loading = ref(true)
const error = ref('')
const range = ref<ReportRange>('30')
const tab = ref<ReportTab>('summary')

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await api.report(range.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load this report.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function setRange(next: ReportRange) {
  if (range.value === next) return
  range.value = next
  void load()
}

const windowLabel = computed(() => data.value
  ? `${shortDate(data.value.window.from)} – ${shortDate(data.value.window.to)}`
  : 'Loading reporting period...')
const salesPoints = computed(() => (data.value?.series ?? []).map((point) => ({ date: point.date, value: point.salesCents })))
const paymentTotal = computed(() => (data.value?.paymentsByMethod ?? []).reduce((sum, row) => sum + row.value, 0))
const modeTotal = computed(() => (data.value?.salesByBusinessMode ?? []).reduce((sum, row) => sum + row.value, 0))
const modeMax = computed(() => Math.max(1, ...(data.value?.salesByBusinessMode ?? []).map((row) => row.value)))
const topProductMax = computed(() => Math.max(1, ...(data.value?.topProducts ?? []).map((row) => row.revenueCents)))
const peak = computed(() => (data.value?.ordersByHour ?? []).reduce(
  (best, row) => row.orders > best.orders ? row : best,
  { hour: 0, orders: 0 },
))

function delta(value: number | null | undefined): string {
  return percentDelta(value ?? null) ?? 'No prior comparison'
}

function isDown(value: number | null | undefined): boolean {
  return (value ?? 0) < 0
}

function hourLabel(hour: number): string {
  if (hour === 0) return '12:00 AM'
  if (hour < 12) return `${hour}:00 AM`
  if (hour === 12) return '12:00 PM'
  return `${hour - 12}:00 PM`
}

function csvCell(value: string | number): string {
  const text = String(value)
  return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

function exportReport() {
  if (!data.value) return
  const report = data.value
  const rows: Array<Array<string | number>> = [
    ['Omaykan marketplace report'],
    ['Period', report.window.from, report.window.to],
    ['Source', report.sourceNote],
    [],
    ['Headline', 'Amount / count'],
    ['Gross sales', report.headline.grossSalesCents / 100],
    ['Net sales', report.headline.netSalesCents / 100],
    ['Collected total', report.headline.collectedCents / 100],
    ['Tax collected', report.headline.taxCents / 100],
    ['Discounts given', report.headline.discountCents / 100],
    ['Paid orders', report.headline.orders],
    ['Average ticket', report.headline.averageOrderValueCents / 100],
    [],
    ['Payment method', 'Collected amount'],
    ...report.paymentsByMethod.map((row) => [row.label, row.value / 100]),
    [],
    ['Business mode', 'Net sales', 'Orders'],
    ...report.salesByBusinessMode.map((row) => [row.label, row.value / 100, row.orders]),
    [],
    ['Top product', 'Quantity', 'Revenue'],
    ...report.topProducts.map((row) => [row.label, row.quantity, row.revenueCents / 100]),
    [],
    ['Recent receipt', 'Store', 'Items', 'Payment', 'Amount', 'Recorded at'],
    ...report.recentTransactions.map((row) => [row.ticketNumber, row.storeName ?? '', row.items, row.paymentMethod, row.totalCents / 100, row.createdAt ?? '']),
  ]

  const csv = rows.map((row) => row.map(csvCell).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `omaykan-report-${report.window.from}-${report.window.to}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <section class="reports-page">
    <header class="report-heading">
      <div class="report-heading__title">
        <span><ClipboardList :size="24" /></span>
        <div>
          <h1>Reports</h1>
          <p>Audit-friendly marketplace summaries, payment totals, sales activity, and top-selling products.</p>
        </div>
      </div>

      <div class="report-heading__tools">
        <span class="report-window"><CalendarDays :size="16" /> {{ windowLabel }}</span>
        <div class="report-ranges" role="group" aria-label="Report period">
          <button v-for="item in RANGES" :key="item.value" type="button" :class="{ 'report-range--active': range === item.value }" @click="setRange(item.value)">{{ item.label }}</button>
        </div>
        <button class="report-export" type="button" :disabled="!data || loading" @click="exportReport"><ArrowDownToLine :size="17" /> Export CSV</button>
      </div>
    </header>

    <p v-if="error" class="report-alert" role="alert">{{ error }}</p>

    <section class="report-metrics" aria-label="Report totals">
      <article class="report-metric report-metric--green">
        <span class="report-metric__icon"><ShoppingCart :size="22" /></span>
        <div><small>Gross sales</small><strong>{{ loading ? '—' : pesos(data?.headline.grossSalesCents ?? 0, { exact: true }) }}</strong><em :class="{ 'is-down': isDown(data?.headline.grossSalesChangePercent) }"><ArrowUp :size="12" /> {{ delta(data?.headline.grossSalesChangePercent) }}</em></div>
      </article>
      <article class="report-metric report-metric--green">
        <span class="report-metric__icon"><CircleDollarSign :size="22" /></span>
        <div><small>Net sales</small><strong>{{ loading ? '—' : pesos(data?.headline.netSalesCents ?? 0, { exact: true }) }}</strong><em :class="{ 'is-down': isDown(data?.headline.netSalesChangePercent) }"><ArrowUp :size="12" /> {{ delta(data?.headline.netSalesChangePercent) }}</em></div>
      </article>
      <article class="report-metric report-metric--amber">
        <span class="report-metric__icon"><Percent :size="22" /></span>
        <div><small>Tax collected</small><strong>{{ loading ? '—' : pesos(data?.headline.taxCents ?? 0, { exact: true }) }}</strong><em :class="{ 'is-down': isDown(data?.headline.taxChangePercent) }"><ArrowUp :size="12" /> {{ delta(data?.headline.taxChangePercent) }}</em></div>
      </article>
      <article class="report-metric report-metric--coral">
        <span class="report-metric__icon"><Tag :size="22" /></span>
        <div><small>Discounts given</small><strong>{{ loading ? '—' : pesos(data?.headline.discountCents ?? 0, { exact: true }) }}</strong><em :class="{ 'is-down': isDown(data?.headline.discountChangePercent) }"><ArrowUp :size="12" /> {{ delta(data?.headline.discountChangePercent) }}</em></div>
      </article>
      <article class="report-metric report-metric--blue">
        <span class="report-metric__icon"><ReceiptText :size="22" /></span>
        <div><small>Orders / Avg ticket</small><strong>{{ loading ? '—' : `${count(data?.headline.orders ?? 0)} / ${pesos(data?.headline.averageOrderValueCents ?? 0, { exact: true })}` }}</strong><em :class="{ 'is-down': isDown(data?.headline.ordersChangePercent) }"><ArrowUp :size="12" /> {{ delta(data?.headline.ordersChangePercent) }}</em></div>
      </article>
    </section>

    <nav class="report-tabs" aria-label="Report sections">
      <button v-for="item in TABS" :key="item.value" type="button" :class="{ 'report-tab--active': tab === item.value }" @click="tab = item.value"><component :is="item.icon" :size="16" /> {{ item.label }}</button>
    </nav>

    <section class="report-main" :class="`report-main--${tab}`">
      <article v-show="tab !== 'payments'" class="report-card report-revenue">
        <header class="report-card__header">
          <div class="report-card__title"><span><BarChart3 :size="18" /></span><div><h2>Revenue trend</h2><p>Net sales after discounts for the selected period</p></div></div>
          <span class="report-chip">Daily</span>
        </header>
        <div class="report-chart-total"><strong>{{ pesos(data?.headline.netSalesCents ?? 0, { exact: true }) }}</strong><span>{{ count(data?.headline.orders ?? 0) }} paid orders</span></div>
        <TrendChart :points="salesPoints" :format="pesosCompact" measure="in net sales" :height="180" empty-message="No paid sales in this period." />
      </article>

      <article v-show="tab !== 'activity'" class="report-card report-payments">
        <header class="report-card__header"><div class="report-card__title"><span><CreditCard :size="18" /></span><div><h2>Payment breakdown</h2><p>Collected order totals by recorded tender</p></div></div></header>
        <CategoryDonut :rows="data?.paymentsByMethod ?? []" :format="pesos" total-label="Total collected" />
        <p v-if="!loading && paymentTotal === 0" class="report-empty">No settled payments in this period.</p>
      </article>

      <article v-show="tab !== 'activity'" class="report-card report-modes">
        <header class="report-card__header"><div class="report-card__title"><span><Store :size="18" /></span><div><h2>Sales by business mode</h2><p>Net sales by fulfilment type</p></div></div></header>
        <div class="mode-grid">
          <article v-for="row in data?.salesByBusinessMode ?? []" :key="row.label">
            <div><strong>{{ row.label }}</strong><small>{{ row.orders }} orders · {{ modeTotal ? Math.round((row.value / modeTotal) * 100) : 0 }}%</small></div>
            <b>{{ pesos(row.value) }}</b>
            <i><span :style="{ width: `${(row.value / modeMax) * 100}%` }"></span></i>
          </article>
        </div>
        <p v-if="!loading && !(data?.salesByBusinessMode.length ?? 0)" class="report-empty">No fulfilment data in this period.</p>
      </article>
    </section>

    <section class="report-bottom" :class="`report-bottom--${tab}`">
      <article v-show="tab === 'summary'" class="report-card report-summary">
        <header class="report-card__header"><div class="report-card__title"><span><FileText :size="18" /></span><div><h2>Report summary</h2><p>{{ windowLabel }}</p></div></div></header>
        <dl>
          <div><dt>Gross merchandise sales</dt><dd>{{ pesos(data?.headline.grossSalesCents ?? 0, { exact: true }) }}</dd></div>
          <div><dt>Discounts</dt><dd>−{{ pesos(data?.headline.discountCents ?? 0, { exact: true }) }}</dd></div>
          <div><dt>Net sales</dt><dd>{{ pesos(data?.headline.netSalesCents ?? 0, { exact: true }) }}</dd></div>
          <div><dt>Tax collected</dt><dd>{{ pesos(data?.headline.taxCents ?? 0, { exact: true }) }}</dd></div>
          <div><dt>Collected order total</dt><dd>{{ pesos(data?.headline.collectedCents ?? 0, { exact: true }) }}</dd></div>
        </dl>
      </article>

      <article v-show="tab === 'summary'" class="report-card report-products">
        <header class="report-card__header"><div class="report-card__title"><span><Package :size="18" /></span><div><h2>Top-selling products</h2><p>Ranked by paid line revenue</p></div></div></header>
        <ol>
          <li v-for="(product, index) in data?.topProducts ?? []" :key="product.id ?? product.label"><span>{{ index + 1 }}</span><i><img v-if="product.imageUrl" :src="product.imageUrl" alt=""><Package v-else :size="14" /></i><div><strong>{{ product.label }}</strong><small>{{ count(product.quantity) }} sold</small></div><em>{{ pesos(product.revenueCents) }}</em><b><u :style="{ width: `${(product.revenueCents / topProductMax) * 100}%` }"></u></b></li>
        </ol>
        <p v-if="!loading && !(data?.topProducts.length ?? 0)" class="report-empty">No paid product sales in this period.</p>
      </article>

      <article class="report-card report-transactions">
        <header class="report-card__header"><div class="report-card__title"><span><ReceiptText :size="18" /></span><div><h2>Recent transactions</h2><p>Latest paid orders in this period</p></div></div></header>
        <div class="transaction-table" role="table" aria-label="Recent paid transactions">
          <div class="transaction-row transaction-row--head" role="row"><span>Receipt</span><span>Store</span><span>Items</span><span>Payment</span><span>Amount</span><span>Recorded</span></div>
          <div v-for="row in data?.recentTransactions ?? []" :key="row.id" class="transaction-row" role="row"><strong>{{ row.ticketNumber }}</strong><span>{{ row.storeName || 'Unknown store' }}</span><span>{{ row.items }}</span><span>{{ row.paymentMethod }}</span><b>{{ pesos(row.totalCents, { exact: true }) }}</b><time>{{ dateTime(row.createdAt) }}</time></div>
        </div>
        <p v-if="!loading && !(data?.recentTransactions.length ?? 0)" class="report-empty">No paid transactions in this period.</p>
      </article>

      <article v-show="tab !== 'payments'" class="report-card report-peak">
        <header class="report-card__header"><div class="report-card__title"><span><Clock3 :size="18" /></span><div><h2>Peak order hours</h2><p>Paid orders by local time</p></div></div></header>
        <div class="peak-callout"><strong>{{ peak.orders ? hourLabel(peak.hour) : 'No peak yet' }}</strong><span>{{ peak.orders }} orders at the busiest hour</span></div>
        <HourlyBarChart :rows="data?.ordersByHour ?? []" />
      </article>
    </section>

    <footer class="report-source"><Info :size="14" /><span>{{ data?.sourceNote ?? 'Paid, non-voided orders in the selected period.' }}</span></footer>
  </section>
</template>

<style scoped>
.reports-page { display: grid; gap: 12px; min-width: 0; padding: 16px var(--adm-gutter) 26px; color: #17221c; }
.report-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.report-heading__title { display: flex; align-items: center; gap: 12px; min-width: 0; }
.report-heading__title > span { display: grid; width: 46px; height: 46px; flex: 0 0 auto; place-items: center; border-radius: 12px; color: #16804b; background: #e6f3e9; }
.report-heading h1 { margin: 0; font-family: Georgia, serif; font-size: 29px; line-height: 1; }
.report-heading p { max-width: 620px; margin: 6px 0 0; color: #66716a; font-size: 11.5px; line-height: 1.4; }
.report-heading__tools { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; }
.report-window { display: inline-flex; height: 38px; align-items: center; gap: 7px; padding: 0 11px; border: 1px solid #dedad2; border-radius: 9px; color: #3e4943; background: #fff; font-size: 10.5px; white-space: nowrap; }
.report-ranges { display: flex; padding: 3px; border: 1px solid #dedad2; border-radius: 9px; background: #fff; }
.report-ranges button { height: 30px; padding: 0 11px; border: 0; border-radius: 7px; color: #5a645e; background: transparent; font: inherit; font-size: 10px; font-weight: 650; cursor: pointer; }
.report-ranges button.report-range--active, .report-ranges button.report-range--active:hover { color: #fff; background: #17613d; }
.report-export { display: inline-flex; height: 38px; align-items: center; gap: 7px; padding: 0 14px; border: 1px solid #155137; border-radius: 9px; color: #fff; background: #155137; font: inherit; font-size: 10.5px; font-weight: 750; cursor: pointer; }
.report-export:disabled { opacity: .55; cursor: wait; }
.report-alert { margin: 0; padding: 10px 13px; border: 1px solid #f2d2ce; border-radius: 9px; color: #902e2e; background: #fff0ef; font-size: 11px; }

.report-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.report-metric { display: grid; grid-template-columns: 43px minmax(0, 1fr); align-items: center; gap: 10px; min-height: 91px; padding: 11px 12px; border: 1px solid #e1ddd4; border-radius: 12px; background: rgba(255,255,255,.82); box-shadow: 0 3px 14px rgba(33,45,38,.04); box-sizing: border-box; }
.report-metric__icon { display: grid; width: 41px; height: 41px; place-items: center; border-radius: 10px; }
.report-metric--green .report-metric__icon { color: #177342; background: #e4f3e8; }.report-metric--amber .report-metric__icon { color: #b06a08; background: #fff0d6; }.report-metric--coral .report-metric__icon { color: #b64739; background: #fde9e5; }.report-metric--blue .report-metric__icon { color: #246bb7; background: #e7f0fc; }
.report-metric div { display: grid; min-width: 0; gap: 2px; }
.report-metric small { color: #56615a; font-size: 9.5px; }
.report-metric strong { overflow: hidden; font-family: Georgia, serif; font-size: 17px; line-height: 1.15; text-overflow: ellipsis; white-space: nowrap; }
.report-metric em { display: inline-flex; align-items: center; gap: 3px; color: #18804a; font-size: 9px; font-style: normal; font-weight: 750; }
.report-metric em.is-down { color: #c3453b; }.report-metric em.is-down svg { transform: rotate(180deg); }

.report-tabs { display: flex; gap: 7px; overflow-x: auto; scrollbar-width: none; }
.report-tabs button { display: inline-flex; height: 35px; align-items: center; justify-content: center; gap: 7px; min-width: 116px; padding: 0 15px; border: 1px solid #dedad2; border-radius: 999px; color: #56615b; background: #fff; font: inherit; font-size: 10.5px; font-weight: 700; cursor: pointer; }
.report-tabs button.report-tab--active, .report-tabs button.report-tab--active:hover { color: #fff; border-color: #17613d; background: #17613d; }

.report-card { min-width: 0; overflow: hidden; border: 1px solid #e1ddd4; border-radius: 13px; background: rgba(255,255,255,.84); box-shadow: 0 3px 14px rgba(33,45,38,.035); }
.report-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; padding: 11px 13px 8px; }
.report-card__title { display: flex; align-items: flex-start; gap: 9px; min-width: 0; }
.report-card__title > span { display: grid; width: 31px; height: 31px; flex: 0 0 auto; place-items: center; border-radius: 8px; color: #187748; background: #e6f3e9; }
.report-card__title h2 { margin: 1px 0 0; font-family: Georgia, serif; font-size: 14px; line-height: 1.2; }
.report-card__title p { margin: 2px 0 0; color: #747e78; font-size: 9.3px; }
.report-chip { display: inline-flex; height: 28px; align-items: center; padding: 0 9px; border: 1px solid #dfdbd2; border-radius: 7px; color: #56615b; font-size: 9px; }
.report-main { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(330px, .9fr); grid-template-areas: 'revenue payments' 'revenue modes'; gap: 11px; }
.report-main--payments { grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-areas: 'payments modes'; }
.report-main--activity { grid-template-columns: minmax(0, 1fr); grid-template-areas: 'revenue'; }
.report-revenue { grid-area: revenue; padding: 0 10px 8px; }.report-payments { grid-area: payments; }.report-modes { grid-area: modes; }
.report-chart-total { display: flex; align-items: baseline; gap: 9px; padding: 0 7px 2px; }
.report-chart-total strong { font-family: Georgia, serif; font-size: 22px; }.report-chart-total span { color: #7a837e; font-size: 9px; }
.report-revenue :deep(.trend__svg) { touch-action: pan-y; }
.report-payments :deep(.donut) { padding: 0 12px 12px; grid-template-columns: 125px minmax(180px, 1fr); gap: 8px; }
.report-payments :deep(.donut__chart) { width: 120px; height: 120px; }.report-payments :deep(.donut__chart strong) { font-size: 14px; }.report-payments :deep(.donut li) { min-height: 25px; font-size: 9.5px; }.report-payments :deep(.donut li strong) { font-size: 9.5px; }
.mode-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; padding: 0 12px 12px; }
.mode-grid article { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 5px 8px; padding: 9px; border: 1px solid #e7e3da; border-radius: 9px; background: #faf9f6; }
.mode-grid article div { display: grid; gap: 1px; min-width: 0; }.mode-grid strong { overflow: hidden; font-size: 9.5px; text-overflow: ellipsis; white-space: nowrap; }.mode-grid small { color: #7a837e; font-size: 8px; }.mode-grid b { font-family: Georgia, serif; font-size: 11px; }.mode-grid i { grid-column: 1 / -1; height: 6px; overflow: hidden; border-radius: 99px; background: #e5e9e6; }.mode-grid i span { display: block; height: 100%; border-radius: inherit; background: #36a765; }

.report-bottom { display: grid; grid-template-columns: .82fr 1.05fr 1.28fr .76fr; gap: 11px; align-items: stretch; }
.report-bottom--payments { grid-template-columns: minmax(0, 1fr); }.report-bottom--activity { grid-template-columns: minmax(0, 1.7fr) minmax(280px, .7fr); }
.report-bottom--summary .transaction-table { min-width: 0; }
.report-bottom--summary .transaction-row { grid-template-columns: .8fr 1.35fr .42fr .8fr; }
.report-bottom--summary .transaction-row > :nth-child(4),
.report-bottom--summary .transaction-row > :nth-child(6) { display: none; }
.report-summary dl { display: grid; margin: 0; padding: 0 13px 12px; }.report-summary dl div { display: flex; justify-content: space-between; gap: 10px; padding: 7px 0; border-bottom: 1px solid #eeeae3; }.report-summary dl div:last-child { border: 0; }.report-summary dt { color: #5e6962; font-size: 9px; }.report-summary dd { margin: 0; font-size: 9.5px; font-weight: 750; }
.report-products ol { display: grid; gap: 0; margin: 0; padding: 0 12px 11px; list-style: none; }.report-products li { display: grid; grid-template-columns: 13px 22px minmax(70px,1fr) auto; align-items: center; gap: 6px; padding: 3px 0; border-bottom: 1px solid #eeeae3; font-size: 8.5px; }.report-products li > i { display: grid; width: 21px; height: 21px; place-items: center; overflow: hidden; border-radius: 5px; background: #eef1ef; color: #68736d; }.report-products img { width: 100%; height: 100%; object-fit: cover; }.report-products li > div { display: grid; min-width: 0; gap: 1px; }.report-products strong { overflow: hidden; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }.report-products small { color: #7b847f; font-size: 8px; }.report-products em { font-size: 9px; font-style: normal; font-weight: 700; }.report-products li > b { grid-column: 3 / -1; height: 4px; overflow: hidden; border-radius: 99px; background: #e8ebe8; }.report-products li > b u { display: block; height: 100%; border-radius: inherit; background: #58a676; }
.transaction-table { min-width: 530px; padding: 0 12px 10px; }.report-transactions { overflow-x: auto; }.transaction-row { display: grid; grid-template-columns: .85fr 1.4fr .45fr .8fr .85fr 1fr; gap: 7px; align-items: center; min-height: 26px; border-bottom: 1px solid #eeeae3; color: #505b55; font-size: 8.5px; }.transaction-row--head { min-height: 23px; color: #7c8580; font-size: 7.8px; text-transform: uppercase; letter-spacing: .04em; }.transaction-row strong, .transaction-row b { color: #26312b; font-size: 8.8px; }.transaction-row time { color: #737d77; font-size: 8px; }
.report-peak { padding-bottom: 10px; }.peak-callout { display: grid; gap: 1px; padding: 0 13px 5px; }.peak-callout strong { font-family: Georgia, serif; font-size: 16px; }.peak-callout span { color: #78817c; font-size: 8.5px; }.report-peak :deep(.hours) { padding-inline: 10px; }.report-peak :deep(.hours__plot) { gap: 3px; height: 74px; }.report-peak :deep(.hours__labels) { gap: 3px; font-size: 6.5px; }
.report-empty { margin: 8px 12px 13px; color: #7b847f; font-size: 9.5px; text-align: center; }
.report-source { display: flex; align-items: center; gap: 6px; color: #78817c; font-size: 9px; }

button:focus-visible { outline: 3px solid rgba(40,128,79,.2); outline-offset: 2px; }

@media (max-width: 1250px) {
  .report-heading { align-items: flex-start; flex-direction: column; }.report-heading__tools { width: 100%; flex-wrap: wrap; }.report-metrics { grid-template-columns: repeat(3, minmax(0,1fr)); }.report-bottom { grid-template-columns: repeat(2, minmax(0,1fr)); }.report-transactions { grid-column: 1 / -1; }
}

@media (max-width: 850px) {
  .reports-page { padding-inline: 16px; }.report-main, .report-main--payments, .report-main--activity { grid-template-columns: minmax(0,1fr); grid-template-areas: 'revenue' 'payments' 'modes'; }.report-metrics { display: flex; overflow-x: auto; scroll-snap-type: x mandatory; scrollbar-width: none; }.report-metric { min-width: 220px; scroll-snap-align: start; }.report-bottom, .report-bottom--activity { grid-template-columns: minmax(0,1fr); }.report-transactions { grid-column: auto; }.report-revenue { order: -1; }
}

@media (max-width: 560px) {
  .report-heading__title { align-items: flex-start; }.report-heading__tools { display: grid; grid-template-columns: 1fr auto; }.report-window { grid-column: 1 / -1; justify-content: center; }.report-ranges { grid-column: 1 / -1; overflow-x: auto; }.report-ranges button { flex: 1 0 auto; }.report-export { grid-column: 1 / -1; justify-content: center; }.report-tabs button { min-width: 108px; }.report-payments :deep(.donut) { grid-template-columns: minmax(0,1fr); }.report-payments :deep(.donut__chart) { width: 145px; height: 145px; }.mode-grid { grid-template-columns: minmax(0,1fr); }.report-chart-total { align-items: flex-start; flex-direction: column; gap: 2px; }.report-bottom { gap: 10px; }
}
</style>
