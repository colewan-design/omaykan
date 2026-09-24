<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowRight,
  BarChart3,
  Box,
  CalendarDays,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Download,
  HeartPulse,
  MoreHorizontal,
  Package,
  RefreshCw,
  ShoppingBag,
  Store,
  UsersRound,
  Zap,
} from '@lucide/vue'
import { api, type Overview } from '../api'
import { count, dateTime, percentDelta, pesos, pesosCompact, PROGRESS_LABELS } from '../format'
import PageHero from '../PageHero.vue'
import TrendChart from '../charts/TrendChart.vue'
import ProgressSplit from '../charts/ProgressSplit.vue'

const emit = defineEmits<{ navigate: [key: string] }>()
const data = ref<Overview | null>(null)
const loading = ref(true)
const error = ref('')
const days = ref<7 | 30 | 90>(30)

async function load() {
  loading.value = true
  error.value = ''
  try { data.value = await api.overview(days.value) }
  catch (err) { error.value = err instanceof Error ? err.message : 'Could not load the dashboard.' }
  finally { loading.value = false }
}

onMounted(load)

const salesPoints = computed(() => (data.value?.series ?? []).map((point) => ({ date: point.date, value: point.salesCents })))
const hasSales = computed(() => salesPoints.value.some((point) => point.value > 0))
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
})

function deltaCopy(value: number | null | undefined): string {
  const formatted = percentDelta(value ?? null)
  return formatted ? `${formatted} vs previous ${days.value} days` : 'No earlier period to compare'
}

function initials(name: string): string {
  return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function quote(value: unknown): string { return `"${String(value ?? '').replaceAll('"', '""')}"` }

function exportReport() {
  if (!data.value) return
  const overviewRows = [
    ['Metric', 'Value'],
    ['Window', `${data.value.window.from} to ${data.value.window.to}`],
    ['Total sales', pesos(data.value.headline.salesCents)],
    ['Total orders', data.value.headline.orders],
    ['Active products', data.value.headline.activeProducts],
    ['Active sellers', data.value.headline.activeSellers],
    [],
    ['Recent orders'],
    ['Order ID', 'Customer', 'Items', 'Total', 'Status', 'Placed'],
    ...data.value.recentOrders.map((order) => [order.ticketNumber, order.customerName, order.items, pesos(order.totalCents, { exact: true }), PROGRESS_LABELS[order.progress], order.createdAt]),
  ]
  const blob = new Blob([overviewRows.map((row) => row.map(quote).join(',')).join('\n')], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `omaykan-overview-${days.value}-days.csv`
  link.click()
  URL.revokeObjectURL(url)
}

defineExpose({ reload: load })
</script>

<template>
  <PageHero
    eyebrow="Omaykan Admin Portal"
    :title="`${greeting} — here is the marketplace overview`"
    :subtitle="`Together we empower local communities. The last ${days} days across every shop, order and shopper on Omaykan.`"
  >
    <template #tools>
      <label class="dash__range">
        <CalendarDays :size="16" aria-hidden="true" />
        <select v-model="days" aria-label="Dashboard date range" @change="load">
          <option :value="7">Last 7 days</option>
          <option :value="30">Last 30 days</option>
          <option :value="90">Last 90 days</option>
        </select>
        <ChevronDown :size="14" aria-hidden="true" />
      </label>
      <button type="button" class="dash__export" :disabled="!data" @click="exportReport">
        <Download :size="16" aria-hidden="true" /> Export report
      </button>
    </template>
  </PageHero>

  <div class="page">
    <p v-if="error" class="adm-note adm-note--error dash__error">
      {{ error }}
      <button type="button" class="adm-btn adm-btn--quiet dash__retry" @click="load">Try again</button>
    </p>

    <section class="dash__tiles" aria-label="Headline figures">
      <button type="button" class="dash__stat" @click="emit('navigate', 'analytics')">
        <span class="dash__staticon dash__staticon--green"><BarChart3 :size="24" /></span>
        <span class="dash__statcopy"><small>Total sales</small><strong>{{ loading ? '—' : pesos(data?.headline.salesCents ?? 0) }}</strong><em>{{ deltaCopy(data?.headline.salesChangePercent) }}</em></span>
        <ChevronRight :size="16" class="dash__statgo" />
      </button>
      <button type="button" class="dash__stat" @click="emit('navigate', 'orders')">
        <span class="dash__staticon dash__staticon--amber"><Box :size="24" /></span>
        <span class="dash__statcopy"><small>Total orders</small><strong>{{ loading ? '—' : count(data?.headline.orders ?? 0) }}</strong><em>{{ deltaCopy(data?.headline.ordersChangePercent) }}</em></span>
        <ChevronRight :size="16" class="dash__statgo" />
      </button>
      <button type="button" class="dash__stat" @click="emit('navigate', 'products')">
        <span class="dash__staticon dash__staticon--green"><Package :size="24" /></span>
        <span class="dash__statcopy"><small>Active products</small><strong>{{ loading ? '—' : count(data?.headline.activeProducts ?? 0) }}</strong><em>Available across the marketplace</em></span>
        <ChevronRight :size="16" class="dash__statgo" />
      </button>
      <button type="button" class="dash__stat" @click="emit('navigate', 'sellers')">
        <span class="dash__staticon dash__staticon--amber"><UsersRound :size="24" /></span>
        <span class="dash__statcopy"><small>Active sellers</small><strong>{{ loading ? '—' : count(data?.headline.activeSellers ?? 0) }}</strong><em>Currently trading</em></span>
        <ChevronRight :size="16" class="dash__statgo" />
      </button>
    </section>

    <section class="dash__workspace">
      <article class="adm-card dash__panel dash__sales">
        <header class="dash__panelhead">
          <div class="dash__paneltitle"><span class="dash__titleicon dash__titleicon--green"><BarChart3 :size="18" /></span><h2>Sales overview</h2></div>
          <span class="dash__metricselect">Paid orders only, daily <ChevronDown :size="14" /></span>
        </header>
        <div class="dash__chart">
          <TrendChart :points="salesPoints" :format="pesosCompact" measure="in sales" :height="235" empty-message="" />
          <div v-if="!loading && !hasSales" class="dash__chartempty">
            <span><BarChart3 :size="24" /></span>
            <strong>No sales data yet</strong>
            <p>Sales data will appear here once orders are completed<br />and payment is received.</p>
          </div>
          <div v-if="loading" class="dash__chartempty"><RefreshCw :size="22" class="dash__spin" /><strong>Loading sales…</strong></div>
        </div>
      </article>

      <article class="adm-card dash__panel dash__ordersplit">
        <header class="dash__panelhead">
          <div class="dash__paneltitle"><span class="dash__titleicon dash__titleicon--amber"><CalendarDays :size="18" /></span><h2>Orders by status</h2></div>
        </header>
        <ProgressSplit :counts="data?.byProgress ?? {}" />
      </article>

      <div class="dash__side">
        <article class="adm-card dash__panel dash__health">
          <header class="dash__panelhead"><div class="dash__paneltitle"><span class="dash__titleicon"><HeartPulse :size="18" /></span><h2>Platform health</h2></div><ChevronRight :size="15" /></header>
          <ul>
            <li><span>Marketplace data</span><strong :class="error ? 'dash__healthbad' : ''"><i></i>{{ error ? 'Issue' : 'Operational' }}</strong></li>
            <li><span>Order reporting</span><strong><i></i>Operational</strong></li>
            <li><span>Seller access</span><strong><i></i>Operational</strong></li>
            <li><span>Admin session</span><strong><i></i>Secure</strong></li>
          </ul>
        </article>

        <article class="adm-card dash__panel dash__quick">
          <header class="dash__panelhead"><div class="dash__paneltitle"><span class="dash__titleicon dash__titleicon--amber"><Zap :size="18" /></span><h2>Quick actions</h2></div></header>
          <button type="button" @click="emit('navigate', 'orders')"><ShoppingBag :size="18" /><span>View all orders</span><ChevronRight :size="15" /></button>
          <button type="button" @click="emit('navigate', 'sellers')"><Store :size="18" /><span>Manage sellers</span><ChevronRight :size="15" /></button>
        </article>
      </div>
    </section>

    <section class="adm-card dash__panel dash__recent">
      <header class="dash__panelhead">
        <div class="dash__paneltitle"><span class="dash__titleicon dash__titleicon--green"><ClipboardList :size="18" /></span><h2>Recent orders</h2></div>
        <button type="button" class="dash__link" @click="emit('navigate', 'orders')">View all orders <ArrowRight :size="14" /></button>
      </header>
      <div class="adm-tablewrap">
        <table class="adm-table">
          <thead><tr><th>Order ID</th><th>Customer</th><th>Items</th><th>Total</th><th>Status</th><th>Placed</th><th>Actions</th></tr></thead>
          <tbody>
            <tr v-for="order in data?.recentOrders ?? []" :key="order.id">
              <td class="dash__ticket">{{ order.ticketNumber }}</td>
              <td><span class="dash__customer"><i>{{ initials(order.customerName) }}</i>{{ order.customerName }}</span></td>
              <td class="adm-num">{{ order.items }}</td>
              <td class="adm-num dash__total">{{ pesos(order.totalCents, { exact: true }) }}</td>
              <td><span class="adm-pill" :class="`adm-pill--${order.progress}`"><span class="adm-pill__dot"></span>{{ PROGRESS_LABELS[order.progress] }}</span></td>
              <td class="dash__when">{{ dateTime(order.createdAt) }}</td>
              <td><button type="button" class="dash__more" aria-label="Open order actions" @click="emit('navigate', 'orders')"><MoreHorizontal :size="16" /></button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!loading && !(data?.recentOrders.length ?? 0)" class="adm-empty"><p class="adm-empty__title">No orders yet</p><p class="adm-empty__copy">The first storefront order will appear here and the figures above will begin moving.</p></div>
      <p v-else-if="loading" class="adm-note">Loading…</p>
    </section>
  </div>
</template>

<style scoped>
.page { display: grid; grid-template-columns: minmax(0, 1fr); gap: 14px; min-width: 0; padding: 14px var(--adm-gutter) 0; }
.page > * { min-width: 0; }
.dash__range, .dash__export { display: inline-flex; align-items: center; gap: 8px; height: 38px; padding: 0 13px; border: 1px solid rgba(255, 255, 255, .2); border-radius: 9px; background: #fff; color: #1e2d26; font: inherit; font-size: 12px; font-weight: 650; }
.dash__range select { min-width: 95px; border: 0; outline: 0; appearance: none; background: transparent; color: inherit; font: inherit; font-weight: inherit; }
.dash__range svg:last-child { pointer-events: none; }
.dash__export { border-color: rgba(255, 255, 255, .34); background: #0a8d4a; color: #fff; cursor: pointer; }
.dash__export:hover { background: #087840; }
.dash__export:disabled { opacity: .6; cursor: default; }
.dash__tiles { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.dash__stat { display: grid; grid-template-columns: 52px minmax(0, 1fr) auto; align-items: center; gap: 13px; min-height: 102px; padding: 13px 16px; border: 1px solid #e4e6e3; border-radius: 13px; background: #fff; color: #15221b; font: inherit; text-align: left; box-shadow: 0 4px 18px rgba(32, 44, 37, .045); cursor: pointer; }
.dash__stat:hover { border-color: #cbd8d0; box-shadow: 0 7px 22px rgba(32, 44, 37, .075); }
.dash__staticon { display: grid; place-items: center; width: 50px; height: 50px; border-radius: 11px; }
.dash__staticon--green { background: #e3f6ea; color: #078a47; }
.dash__staticon--amber { background: #fff0d8; color: #b86c0b; }
.dash__statcopy { display: grid; min-width: 0; gap: 2px; }
.dash__statcopy small { color: #52605a; font-size: 11.5px; font-weight: 650; }
.dash__statcopy strong { overflow: hidden; color: #101828; font-family: var(--sf-serif); font-size: 23px; line-height: 1.1; text-overflow: ellipsis; white-space: nowrap; }
.dash__statcopy em { overflow: hidden; color: #718096; font-size: 10.5px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.dash__statgo { padding: 7px; border-radius: 50%; background: #f5f5f2; color: #52605a; box-sizing: content-box; }
.dash__workspace { display: grid; grid-template-columns: minmax(0, 2.55fr) minmax(225px, .9fr) minmax(245px, 1fr); gap: 12px; align-items: stretch; }
.dash__panel { padding: 13px 15px; border-color: #e4e6e3; background: #fff; box-shadow: 0 4px 18px rgba(32, 44, 37, .04); }
.dash__panelhead { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 29px; margin-bottom: 9px; }
.dash__paneltitle { display: flex; align-items: center; gap: 9px; min-width: 0; }
.dash__paneltitle h2 { margin: 0; color: #111c16; font-family: var(--sf-serif); font-size: 15px; }
.dash__titleicon { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px; background: #e9f7ee; color: #0b8e4b; flex: none; }
.dash__titleicon--amber { background: #fff2df; color: #b86c0b; }
.dash__metricselect { display: inline-flex; align-items: center; gap: 18px; height: 32px; padding: 0 11px; border: 1px solid #e0e4e1; border-radius: 8px; color: #344054; font-size: 10.5px; white-space: nowrap; }
.dash__chart { position: relative; min-height: 235px; }
.dash__chartempty { position: absolute; inset: 0; display: grid; place-content: center; justify-items: center; color: #667085; text-align: center; pointer-events: none; }
.dash__chartempty span { display: grid; place-items: center; width: 48px; height: 48px; margin-bottom: 8px; border-radius: 50%; background: #eef1ef; color: #52605a; }
.dash__chartempty strong { color: #101828; font-size: 13px; }
.dash__chartempty p { margin: 8px 0 0; font-size: 10.5px; line-height: 1.45; }
.dash__ordersplit { min-width: 0; }
.dash__side { display: grid; gap: 12px; min-width: 0; }
.dash__health ul { display: grid; gap: 9px; margin: 0; padding: 11px 12px; border-radius: 9px; background: #eef8f1; list-style: none; }
.dash__health li { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #344054; font-size: 10.5px; }
.dash__health strong { display: flex; align-items: center; gap: 6px; color: #197646; font-size: 10px; font-weight: 500; white-space: nowrap; }
.dash__health i { width: 7px; height: 7px; border-radius: 50%; background: #17c964; }
.dash__health .dash__healthbad { color: #a32828; }
.dash__health .dash__healthbad i { background: #e43c35; }
.dash__quick button { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 11px; width: 100%; min-height: 39px; margin-top: 7px; padding: 0 11px; border: 1px solid #e0e4e1; border-radius: 8px; background: #fff; color: #183227; font: inherit; font-size: 11px; text-align: left; cursor: pointer; }
.dash__quick button:hover { background: #f3f7f4; }
.dash__recent { padding-bottom: 6px; }
.dash__link { display: inline-flex; align-items: center; gap: 7px; padding: 0; border: 0; background: none; color: #ad620c; font: inherit; font-size: 11px; font-weight: 700; cursor: pointer; }
.dash__recent .adm-table { min-width: 900px; font-size: 11px; }
.dash__recent .adm-table th { padding: 7px 13px; font-size: 9px; }
.dash__recent .adm-table td { height: 35px; padding: 5px 13px; box-sizing: border-box; }
.dash__ticket, .dash__total { color: #101828; font-weight: 700; }
.dash__customer { display: inline-flex; align-items: center; gap: 9px; white-space: nowrap; }
.dash__customer i { display: grid; place-items: center; width: 25px; height: 25px; border-radius: 50%; background: #e5e7e6; color: #3c4741; font-size: 9px; font-style: normal; font-weight: 700; }
.dash__when { color: #667085; white-space: nowrap; }
.dash__more { display: grid; place-items: center; width: 30px; height: 28px; padding: 0; border: 1px solid #e0e4e1; border-radius: 7px; background: #fff; color: #344054; cursor: pointer; }
.dash__error { padding: 12px; border-radius: 10px; background: #fff; }
.dash__retry { margin-left: 10px; }
.dash__spin { animation: dash-spin 900ms linear infinite; }
@keyframes dash-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .dash__spin { animation: none; } }
@media (max-width: 1250px) { .dash__workspace { grid-template-columns: minmax(0, 2fr) minmax(230px, 1fr); } .dash__side { grid-column: 1 / -1; grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 1050px) { .dash__tiles { grid-template-columns: repeat(2, minmax(0, 1fr)); } .dash__workspace { grid-template-columns: minmax(0, 1fr); } .dash__side { grid-column: auto; } }
@media (max-width: 620px) { .page { padding: 14px 16px 0; } .dash__tiles, .dash__side { grid-template-columns: minmax(0, 1fr); } .dash__range, .dash__export { flex: 1; justify-content: center; } .dash__panel { padding: 12px; } .dash__panelhead { align-items: flex-start; } .dash__metricselect { gap: 6px; } }
</style>
