<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, RefreshCw } from '@lucide/vue'
import { api, type Overview } from '../api'
import { count, dateTime, pesos, pesosCompact, PROGRESS_LABELS } from '../format'
import PageHero from '../PageHero.vue'
import StatTile from '../StatTile.vue'
import TrendChart from '../charts/TrendChart.vue'
import ProgressSplit from '../charts/ProgressSplit.vue'

// What the marketplace did over the last thirty days, in one screen and one
// request. See PlatformOverviewController for why it is a single call.

const emit = defineEmits<{ navigate: [key: string] }>()

const data = ref<Overview | null>(null)
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await api.overview()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load the dashboard.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const salesPoints = computed(() =>
  (data.value?.series ?? []).map((point) => ({ date: point.date, value: point.salesCents })),
)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
})

defineExpose({ reload: load })
</script>

<template>
  <PageHero
    :title="`${greeting} — here is the marketplace`"
    subtitle="Together we empower local communities. The last 30 days across every shop, order and shopper on Omaykan."
  >
    <template #tools>
      <button class="adm-btn adm-btn--quiet" type="button" :disabled="loading" @click="load">
        <RefreshCw :size="15" :class="{ 'dash__spin': loading }" aria-hidden="true" />
        {{ loading ? 'Refreshing…' : 'Refresh' }}
      </button>
    </template>
  </PageHero>

  <div class="page">
    <p v-if="error" class="adm-note adm-note--error">
      {{ error }}
      <button type="button" class="adm-btn adm-btn--quiet dash__retry" @click="load">Try again</button>
    </p>

    <section class="dash__tiles" aria-label="Headline figures">
      <StatTile
        label="Total sales"
        :value="pesos(data?.headline.salesCents ?? 0)"
        :delta="data?.headline.salesChangePercent ?? null"
        delta-note="vs previous 30 days"
        :loading="loading"
      />
      <StatTile
        label="Total orders"
        :value="count(data?.headline.orders ?? 0)"
        :delta="data?.headline.ordersChangePercent ?? null"
        delta-note="vs previous 30 days"
        :loading="loading"
      />
      <StatTile label="Active products" :value="count(data?.headline.activeProducts ?? 0)" :loading="loading" />
      <StatTile label="Active sellers" :value="count(data?.headline.activeSellers ?? 0)" :loading="loading" />
    </section>

    <section class="dash__charts">
      <article class="adm-card dash__panel">
        <header class="dash__panelhead">
          <h2 class="adm-h2">Sales overview</h2>
          <p class="dash__panelnote">Paid orders only, daily</p>
        </header>

        <TrendChart :points="salesPoints" :format="pesosCompact" measure="in sales" :height="230" />
      </article>

      <article class="adm-card dash__panel">
        <header class="dash__panelhead">
          <h2 class="adm-h2">Orders by status</h2>
        </header>

        <ProgressSplit :counts="data?.byProgress ?? {}" />
      </article>
    </section>

    <section class="adm-card dash__panel">
      <header class="dash__panelhead">
        <h2 class="adm-h2">Recent orders</h2>
        <button type="button" class="dash__link" @click="emit('navigate', 'orders')">
          View all
          <ArrowRight :size="14" aria-hidden="true" />
        </button>
      </header>

      <div class="adm-tablewrap">
        <table class="adm-table">
          <thead>
            <tr>
              <th>Order</th>
              <th>Customer</th>
              <th class="adm-right">Items</th>
              <th class="adm-right">Total</th>
              <th>Status</th>
              <th>Placed</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in data?.recentOrders ?? []" :key="order.id">
              <td class="dash__ticket">{{ order.ticketNumber }}</td>
              <td>{{ order.customerName }}</td>
              <td class="adm-right adm-num">{{ order.items }}</td>
              <td class="adm-right adm-num">{{ pesos(order.totalCents, { exact: true }) }}</td>
              <td>
                <span class="adm-pill" :class="`adm-pill--${order.progress}`">
                  <span class="adm-pill__dot" aria-hidden="true"></span>
                  {{ PROGRESS_LABELS[order.progress] }}
                </span>
              </td>
              <td class="dash__when">{{ dateTime(order.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && (data?.recentOrders.length ?? 0) === 0" class="adm-empty">
        <p class="adm-empty__title">No orders yet</p>
        <p class="adm-empty__copy">
          The first order a shopper places on the storefront will appear here, and the figures above
          will start moving with it.
        </p>
      </div>
      <p v-else-if="loading" class="adm-note">Loading…</p>
    </section>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  /* Explicit, because a grid item's default `min-width: auto` lets a wide
     child — the recent-orders table — push the whole column past the viewport
     instead of scrolling inside its own wrapper. That is how this page, alone
     of the nine, scrolled sideways on a phone. */
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  padding: 16px var(--adm-gutter) 0;
}

.dash__tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.dash__charts {
  display: grid;
  grid-template-columns: minmax(0, 1.85fr) minmax(0, 1fr);
  gap: 14px;
}

.dash__panel {
  padding: 16px 18px 18px;
}

.dash__panelhead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.dash__panelnote {
  margin: 0;
  color: var(--sf-faint);
  font-size: 12px;
}

.dash__link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: none;
  color: var(--sf-clay);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.dash__link:hover {
  color: var(--sf-clay-deep);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.dash__ticket {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.dash__when {
  color: var(--sf-muted);
  white-space: nowrap;
}

.dash__retry {
  margin-left: 10px;
}

.dash__spin {
  animation: dash-spin 900ms linear infinite;
}

@keyframes dash-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .dash__spin { animation: none; }
}

@media (max-width: 1100px) {
  .dash__charts {
    grid-template-columns: minmax(0, 1fr);
  }

  .dash__tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .page {
    padding: 14px 16px 0;
  }

  .dash__tiles {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
