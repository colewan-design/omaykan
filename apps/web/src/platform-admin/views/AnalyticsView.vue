<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowUp,
  BarChart3,
  CalendarDays,
  Clock3,
  MapPin,
  Package,
  RotateCcw,
  ShoppingCart,
  Tag,
  TrendingUp,
  Trophy,
  UsersRound,
} from '@lucide/vue'
import { api, type Analytics } from '../api'
import { count, percentDelta, pesos, pesosCompact, shortDate } from '../format'
import PageHero from '../PageHero.vue'
import TrendChart from '../charts/TrendChart.vue'
import OrderBarChart from '../charts/OrderBarChart.vue'
import CategoryDonut from '../charts/CategoryDonut.vue'
import HourlyBarChart from '../charts/HourlyBarChart.vue'

const RANGES = [
  { days: 7, label: '7 days' },
  { days: 30, label: '30 days' },
  { days: 90, label: '90 days' },
  { days: 365, label: '12 months' },
]

const data = ref<Analytics | null>(null)
const loading = ref(true)
const error = ref('')
const days = ref(30)

async function load() {
  loading.value = true
  error.value = ''
  try { data.value = await api.analytics(days.value) }
  catch (err) { error.value = err instanceof Error ? err.message : 'Could not load analytics.' }
  finally { loading.value = false }
}

onMounted(load)
function setRange(next: number) { days.value = next; load() }

const salesPoints = computed(() => (data.value?.series ?? []).map((point) => ({ date: point.date, value: point.salesCents })))
const orderPoints = computed(() => (data.value?.series ?? []).map((point) => ({ date: point.date, value: point.orders })))
const windowLabel = computed(() => data.value ? `${shortDate(data.value.window.from)} – ${shortDate(data.value.window.to)}` : 'Loading date range…')
const locationMax = computed(() => Math.max(1, ...(data.value?.ordersByLocation ?? []).map((row) => row.value)))
const categoryTotal = computed(() => (data.value?.salesByCategory ?? []).reduce((sum, row) => sum + row.value, 0))
const topProductMax = computed(() => Math.max(1, ...(data.value?.topProducts ?? []).map((row) => row.revenueCents)))
const peak = computed(() => (data.value?.ordersByHour ?? []).reduce((best, row) => row.orders > best.orders ? row : best, { hour: 0, orders: 0 }))

function delta(value: number | null | undefined): string { return percentDelta(value ?? null) ?? 'No prior comparison' }
function deltaPositive(value: number | null | undefined): boolean { return (value ?? 0) >= 0 }
function hourLabel(hour: number): string {
  if (hour === 0) return '12:00 AM'
  if (hour < 12) return `${hour}:00 AM`
  if (hour === 12) return '12:00 PM'
  return `${hour - 12}:00 PM`
}
</script>

<template>
  <PageHero title="Analytics" subtitle="Marketplace performance insights to help grow local communities.">
    <template #tools>
      <div class="an__heroTools">
        <span class="an__window"><CalendarDays :size="15" />{{ windowLabel }}</span>
        <div class="an__ranges" role="group" aria-label="Date range">
          <button v-for="range in RANGES" :key="range.days" type="button" :class="{ 'an__range--on': days === range.days }" @click="setRange(range.days)">{{ range.label }}</button>
        </div>
      </div>
    </template>
  </PageHero>

  <div class="page">
    <p v-if="error" class="adm-note adm-note--error an__error">{{ error }}</p>

    <section class="an__tiles" aria-label="Headline figures">
      <article class="an__stat">
        <span class="an__staticon an__staticon--green"><BarChart3 :size="22" /></span>
        <div><small>Revenue</small><strong>{{ loading ? '—' : pesos(data?.headline.salesCents ?? 0) }}</strong><em :class="{ 'an__down': !deltaPositive(data?.headline.salesChangePercent) }"><ArrowUp :size="12" />{{ delta(data?.headline.salesChangePercent) }}</em><i>vs. previous {{ days }} days</i></div>
      </article>
      <article class="an__stat">
        <span class="an__staticon an__staticon--amber"><ShoppingCart :size="22" /></span>
        <div><small>Orders</small><strong>{{ loading ? '—' : count(data?.headline.orders ?? 0) }}</strong><em :class="{ 'an__down': !deltaPositive(data?.headline.ordersChangePercent) }"><ArrowUp :size="12" />{{ delta(data?.headline.ordersChangePercent) }}</em><i>vs. previous {{ days }} days</i></div>
      </article>
      <article class="an__stat">
        <span class="an__staticon an__staticon--coral"><Tag :size="22" /></span>
        <div><small>Average order value</small><strong>{{ loading ? '—' : pesos(data?.headline.averageOrderValueCents ?? 0) }}</strong><em :class="{ 'an__down': !deltaPositive(data?.headline.averageOrderValueChangePercent) }"><ArrowUp :size="12" />{{ delta(data?.headline.averageOrderValueChangePercent) }}</em><i>vs. previous {{ days }} days</i></div>
      </article>
      <article class="an__stat">
        <span class="an__staticon an__staticon--blue"><UsersRound :size="22" /></span>
        <div><small>Customers served</small><strong>{{ loading ? '—' : count(data?.headline.customersServed ?? 0) }}</strong><em :class="{ 'an__down': !deltaPositive(data?.headline.customersServedChangePercent) }"><ArrowUp :size="12" />{{ delta(data?.headline.customersServedChangePercent) }}</em><i>vs. previous {{ days }} days</i></div>
      </article>
      <article class="an__stat">
        <span class="an__staticon an__staticon--purple"><RotateCcw :size="22" /></span>
        <div><small>Returning customers</small><strong>{{ loading ? '—' : `${Math.round(data?.returningCustomers.percent ?? 0)}%` }}</strong><em><ArrowUp :size="12" />{{ count(data?.returningCustomers.customers ?? 0) }} customers</em><i>of identified shoppers</i></div>
      </article>
    </section>

    <section class="an__primary">
      <article class="adm-card an__panel an__revenue">
        <header class="an__panelhead"><div class="an__paneltitle"><span class="an__titleicon an__titleicon--green"><BarChart3 :size="18" /></span><div><h2>Revenue</h2><p>Total sales from paid orders</p></div></div><span class="an__select">Daily</span></header>
        <div class="an__headline"><strong>{{ pesos(data?.headline.salesCents ?? 0) }}</strong><em :class="{ 'an__down': !deltaPositive(data?.headline.salesChangePercent) }"><ArrowUp :size="14" />{{ delta(data?.headline.salesChangePercent) }}</em><small>vs. previous {{ days }} days</small></div>
        <TrendChart :points="salesPoints" :format="pesosCompact" measure="in sales" :height="218" />
      </article>

      <article class="adm-card an__panel an__orders">
        <header class="an__panelhead"><div class="an__paneltitle"><span class="an__titleicon an__titleicon--amber"><ShoppingCart :size="18" /></span><div><h2>Orders trend</h2><p>Number of orders placed</p></div></div><span class="an__select">Daily</span></header>
        <div class="an__headline"><strong>{{ count(data?.headline.orders ?? 0) }}</strong><em :class="{ 'an__down': !deltaPositive(data?.headline.ordersChangePercent) }"><ArrowUp :size="14" />{{ delta(data?.headline.ordersChangePercent) }}</em><small>vs. previous {{ days }} days</small></div>
        <OrderBarChart :points="orderPoints" :height="218" />
      </article>
    </section>

    <section class="an__details">
      <article class="adm-card an__panel an__categories">
        <header class="an__panelhead"><div class="an__paneltitle"><span class="an__titleicon an__titleicon--green"><TrendingUp :size="18" /></span><div><h2>Sales by category</h2><p>Share of total revenue</p></div></div><span class="an__select">Revenue</span></header>
        <CategoryDonut :rows="data?.salesByCategory ?? []" :format="pesos" />
      </article>

      <article class="adm-card an__panel an__locations">
        <header class="an__panelhead"><div class="an__paneltitle"><span class="an__titleicon an__titleicon--green"><MapPin :size="18" /></span><div><h2>Orders by location</h2><p>Number of orders by town</p></div></div><span class="an__select">Orders</span></header>
        <ul class="an__locationlist">
          <li v-for="row in data?.ordersByLocation ?? []" :key="row.label"><span>{{ row.label }}</span><i><b :style="{ width: `${(row.value / locationMax) * 100}%` }"></b></i><strong>{{ count(row.value) }}</strong></li>
        </ul>
        <p v-if="!loading && !(data?.ordersByLocation.length ?? 0)" class="an__empty">No order locations in this period.</p>
      </article>

      <article class="adm-card an__panel an__summary">
        <header class="an__panelhead"><div class="an__paneltitle"><span class="an__titleicon an__titleicon--green"><BarChart3 :size="18" /></span><div><h2>Performance summary</h2><p>Key insights from the last {{ days }} days</p></div></div></header>
        <ul class="an__summarylist">
          <li><span><ArrowUp :size="17" /></span><div><strong>Revenue {{ deltaPositive(data?.headline.salesChangePercent) ? 'increased' : 'changed' }} by {{ delta(data?.headline.salesChangePercent) }}</strong><small>{{ pesos(data?.headline.salesCents ?? 0) }} in paid sales this period.</small></div></li>
          <li><span><UsersRound :size="17" /></span><div><strong>{{ count(data?.headline.customersServed ?? 0) }} customers served</strong><small>{{ count(data?.returningCustomers.customers ?? 0) }} were returning shoppers.</small></div></li>
          <li><span><ShoppingCart :size="17" /></span><div><strong>Orders are {{ delta(data?.headline.ordersChangePercent) }}</strong><small>{{ count(data?.headline.orders ?? 0) }} orders in the selected period.</small></div></li>
          <li><span class="an__summaryamber"><Trophy :size="17" /></span><div><strong>Top category: {{ data?.salesByCategory[0]?.label ?? 'No sales yet' }}</strong><small>{{ categoryTotal ? `${Math.round(((data?.salesByCategory[0]?.value ?? 0) / categoryTotal) * 100)}% of category sales` : 'Waiting for paid orders' }}.</small></div></li>
          <li><span class="an__summaryblue"><Clock3 :size="17" /></span><div><strong>Peak ordering time</strong><small>{{ peak.orders ? `${hourLabel(peak.hour)} with ${peak.orders} orders.` : 'No orders to compare yet.' }}</small></div></li>
        </ul>
      </article>

      <article class="adm-card an__panel an__products">
        <header class="an__panelhead"><div class="an__paneltitle"><span class="an__titleicon an__titleicon--amber"><Package :size="18" /></span><div><h2>Top products</h2><p>Best performing products by revenue</p></div></div></header>
        <ol class="an__productlist">
          <li v-for="(product, index) in data?.topProducts ?? []" :key="product.id ?? product.label"><span>{{ index + 1 }}</span><i><img v-if="product.imageUrl" :src="product.imageUrl" alt="" /><Package v-else :size="14" /></i><strong>{{ product.label }}</strong><em>{{ pesos(product.revenueCents) }}</em><small>{{ Math.round((product.revenueCents / topProductMax) * 100) }}%</small><b><u :style="{ width: `${(product.revenueCents / topProductMax) * 100}%` }"></u></b></li>
        </ol>
        <p v-if="!loading && !(data?.topProducts.length ?? 0)" class="an__empty">No paid product sales in this period.</p>
      </article>

      <article class="adm-card an__panel an__peak">
        <header class="an__panelhead"><div class="an__paneltitle"><span class="an__titleicon an__titleicon--amber"><Clock3 :size="18" /></span><div><h2>Peak ordering times</h2><p>Orders by time of day</p></div></div><span class="an__select">Orders</span></header>
        <HourlyBarChart :rows="data?.ordersByHour ?? []" />
      </article>
    </section>
  </div>
</template>

<style scoped>
.page { display: grid; gap: 12px; min-width: 0; padding: 12px var(--adm-gutter) 0; }
.page > * { min-width: 0; }
.an__heroTools { display: flex; align-items: center; gap: 8px; }
.an__window { display: inline-flex; align-items: center; gap: 7px; height: 34px; padding: 0 10px; border-radius: 999px; background: #fff; color: #344054; font-size: 10.5px; font-weight: 650; white-space: nowrap; }
.an__ranges { display: flex; gap: 2px; padding: 3px; border-radius: 999px; background: #fff; }
.an__ranges button { height: 28px; padding: 0 12px; border: 0; border-radius: 999px; background: transparent; color: #52605a; font: inherit; font-size: 10.5px; font-weight: 650; cursor: pointer; }
.an__ranges .an__range--on { background: #12382b; color: #fff; }
.an__error { padding: 10px; border-radius: 9px; background: #fff; }
.an__tiles { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.an__stat { display: grid; grid-template-columns: 44px minmax(0, 1fr); align-items: center; gap: 11px; min-height: 96px; padding: 12px 13px; border: 1px solid #e3e5e2; border-radius: 12px; background: #fff; box-sizing: border-box; box-shadow: 0 3px 14px rgba(30, 43, 35, .04); }
.an__staticon { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 50%; }
.an__staticon--green { background: #e4f3e8; color: #2d7547; }.an__staticon--amber { background: #fff0dc; color: #bb6e11; }.an__staticon--coral { background: #ffebe3; color: #d95e2b; }.an__staticon--blue { background: #e5efff; color: #176bd6; }.an__staticon--purple { background: #eee5ff; color: #7543df; }
.an__stat div { display: grid; min-width: 0; grid-template-columns: auto 1fr; align-items: center; column-gap: 7px; }
.an__stat small { grid-column: 1 / -1; color: #344054; font-size: 10px; font-weight: 650; }
.an__stat strong { grid-column: 1 / -1; margin: 1px 0; color: #101828; font-family: var(--sf-serif); font-size: 20px; white-space: nowrap; }
.an__stat em { display: inline-flex; align-items: center; gap: 2px; color: #168146; font-size: 10px; font-style: normal; font-weight: 700; white-space: nowrap; }
.an__stat i { overflow: hidden; color: #7b8580; font-size: 8.5px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.an__down { color: #c14a32 !important; }.an__down svg { transform: rotate(180deg); }
.an__primary { display: grid; grid-template-columns: minmax(0, 1.75fr) minmax(320px, 1fr); gap: 12px; }
.an__panel { min-width: 0; padding: 12px 14px; border-color: #e3e5e2; background: #fff; box-shadow: 0 3px 14px rgba(30, 43, 35, .035); }
.an__panelhead { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.an__paneltitle { display: flex; align-items: flex-start; gap: 9px; min-width: 0; }
.an__paneltitle h2 { margin: 1px 0 0; color: #111c16; font-family: var(--sf-serif); font-size: 14px; }
.an__paneltitle p { margin: 2px 0 0; color: #718096; font-size: 9.5px; }
.an__titleicon { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px; flex: none; }.an__titleicon--green { background: #e4f3e8; color: #2d7547; }.an__titleicon--amber { background: #fff0dc; color: #b86c0b; }
.an__select { display: inline-flex; align-items: center; height: 29px; padding: 0 10px; border: 1px solid #dfe3df; border-radius: 7px; color: #344054; font-size: 9.5px; }
.an__headline { display: flex; align-items: baseline; gap: 10px; min-height: 31px; }
.an__headline strong { font-family: var(--sf-serif); font-size: 25px; color: #101828; }
.an__headline em { display: inline-flex; align-items: center; gap: 3px; color: #168146; font-size: 11px; font-style: normal; font-weight: 700; }
.an__headline small { color: #7b8580; font-size: 9px; }
.an__details { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1.08fr) minmax(250px, .72fr); grid-template-areas: 'categories locations summary' 'products peak summary'; gap: 12px; align-items: stretch; }
.an__categories { grid-area: categories; }.an__locations { grid-area: locations; }.an__summary { grid-area: summary; }.an__products { grid-area: products; }.an__peak { grid-area: peak; }
.an__locationlist { display: grid; gap: 9px; margin: 5px 0 0; padding: 0; list-style: none; }
.an__locationlist li { display: grid; grid-template-columns: minmax(60px, .55fr) minmax(100px, 1.5fr) auto; align-items: center; gap: 9px; color: #344054; font-size: 10px; }
.an__locationlist i, .an__productlist b { display: block; height: 9px; overflow: hidden; border-radius: 3px; background: #edf0ed; }
.an__locationlist b, .an__productlist u { display: block; height: 100%; border-radius: inherit; background: #6ea080; }
.an__locationlist li:first-child b { background: #215f3e; }.an__locationlist strong { font-size: 9.5px; }
.an__summarylist { display: grid; gap: 12px; margin: 5px 0 0; padding: 0; list-style: none; }
.an__summarylist li { display: grid; grid-template-columns: 28px minmax(0, 1fr); gap: 9px; }
.an__summarylist li > span { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: #e4f3e8; color: #2d7547; }
.an__summarylist li > .an__summaryamber { background: #fff0dc; color: #b86c0b; }.an__summarylist li > .an__summaryblue { background: #e5efff; color: #176bd6; }
.an__summarylist div { display: grid; gap: 2px; }.an__summarylist strong { color: #17221c; font-size: 10.5px; }.an__summarylist small { color: #718096; font-size: 9px; line-height: 1.35; }
.an__productlist { display: grid; gap: 6px; margin: 4px 0 0; padding: 0; list-style: none; }
.an__productlist li { display: grid; grid-template-columns: 14px 22px minmax(90px, 1.4fr) auto 26px minmax(70px, 1fr); align-items: center; gap: 7px; color: #344054; font-size: 9.5px; }
.an__productlist li > i { display: grid; place-items: center; width: 22px; height: 22px; overflow: hidden; border-radius: 5px; background: #eef1ef; color: #607069; font-style: normal; }.an__productlist img { width: 100%; height: 100%; object-fit: cover; }
.an__productlist strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.an__productlist em { font-style: normal; font-weight: 650; }.an__productlist small { color: #718096; text-align: right; }
.an__empty { margin: 14px 0; color: #718096; font-size: 10px; text-align: center; }
@media (max-width: 1250px) { .an__tiles { grid-template-columns: repeat(3, minmax(0, 1fr)); } .an__details { grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-areas: 'categories locations' 'products peak' 'summary summary'; } }
@media (max-width: 950px) { .an__primary { grid-template-columns: 1fr; }.an__tiles { grid-template-columns: repeat(2, minmax(0, 1fr)); }.an__summarylist { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 650px) { .page { padding: 14px 16px 0; } .an__heroTools { align-items: stretch; flex-direction: column; width: 100%; }.an__window { justify-content: center; }.an__ranges { overflow-x: auto; }.an__ranges button { flex: 1; white-space: nowrap; }.an__tiles, .an__details, .an__summarylist { grid-template-columns: 1fr; }.an__details { grid-template-areas: 'categories' 'locations' 'products' 'peak' 'summary'; }.an__stat { min-height: 90px; }.an__headline { flex-wrap: wrap; }.an__headline small { flex-basis: 100%; }.an__productlist li { grid-template-columns: 14px 22px minmax(80px, 1fr) auto 24px; }.an__productlist b { display: none; } }
</style>
