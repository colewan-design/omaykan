<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type Analytics } from '../api'
import { count, pesos, pesosCompact, shortDate } from '../format'
import PageHero from '../PageHero.vue'
import StatTile from '../StatTile.vue'
import TrendChart from '../charts/TrendChart.vue'
import BarList from '../charts/BarList.vue'

// The same marketplace as the dashboard, over a window you choose and broken
// down by what sold and where it went.
//
// ## Why revenue and orders are two charts and not one
//
// The design this follows put both on one plot with a y-axis down each side.
// That chart cannot be read honestly: where the two lines cross is decided by
// two arbitrary scales, not by anything that happened, and every reader takes
// it for a fact. Stacked, sharing an x-axis and a window, the same comparison
// is available — the eye travels down a date instead of across a crossing —
// and nothing is implied that is not in the data.

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
  try {
    data.value = await api.analytics(days.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load analytics.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function setRange(next: number) {
  days.value = next
  load()
}

const salesPoints = computed(() =>
  (data.value?.series ?? []).map((point) => ({ date: point.date, value: point.salesCents })),
)

const orderPoints = computed(() =>
  (data.value?.series ?? []).map((point) => ({ date: point.date, value: point.orders })),
)

const windowLabel = computed(() =>
  data.value ? `${shortDate(data.value.window.from)} – ${shortDate(data.value.window.to)}` : '',
)
</script>

<template>
  <PageHero title="Analytics" subtitle="Insights to help the marketplace grow — what sold, and where it went.">
    <template #tools>
      <div class="an__ranges" role="group" aria-label="Date range">
        <button
          v-for="range in RANGES"
          :key="range.days"
          type="button"
          class="an__range"
          :class="{ 'an__range--on': days === range.days }"
          @click="setRange(range.days)"
        >
          {{ range.label }}
        </button>
      </div>
    </template>
  </PageHero>

  <div class="page">
    <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>

    <p class="an__window">{{ windowLabel }}</p>

    <section class="an__tiles" aria-label="Headline figures">
      <StatTile
        label="Revenue"
        :value="pesos(data?.headline.salesCents ?? 0)"
        :delta="data?.headline.salesChangePercent ?? null"
        delta-note="vs previous window"
        :loading="loading"
      />
      <StatTile
        label="Orders"
        :value="count(data?.headline.orders ?? 0)"
        :delta="data?.headline.ordersChangePercent ?? null"
        delta-note="vs previous window"
        :loading="loading"
      />
      <StatTile label="Average order value" :value="pesos(data?.headline.averageOrderValueCents ?? 0)" :loading="loading" />
      <StatTile label="Customers served" :value="count(data?.headline.customersServed ?? 0)" :loading="loading" />
    </section>

    <!-- Two charts, one x-axis, never two y-axes. See the note at the top. -->
    <section class="adm-card an__panel">
      <header class="an__panelhead">
        <h2 class="adm-h2">Revenue</h2>
        <p class="an__note">Paid orders only, daily</p>
      </header>
      <TrendChart :points="salesPoints" :format="pesosCompact" measure="in sales" :height="190" />

      <header class="an__panelhead an__panelhead--second">
        <h2 class="adm-h2">Orders</h2>
        <p class="an__note">Every order placed, paid or not</p>
      </header>
      <TrendChart :points="orderPoints" :format="(value) => String(Math.round(value))" measure="orders" :height="150" />
    </section>

    <section class="an__split">
      <article class="adm-card an__panel">
        <header class="an__panelhead">
          <h2 class="adm-h2">Sales by category</h2>
          <p class="an__note">Split across the lines of each order</p>
        </header>
        <BarList :rows="data?.salesByCategory ?? []" :format="pesos" measure="sales" />
      </article>

      <article class="adm-card an__panel">
        <header class="an__panelhead">
          <h2 class="adm-h2">Orders by location</h2>
          <p class="an__note">By the town of the shop that filled them</p>
        </header>
        <BarList :rows="data?.ordersByLocation ?? []" :format="count" measure="orders" />
      </article>
    </section>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  /* See DashboardView: a grid item's default `min-width: auto` lets a wide
     child push the column past the viewport. */
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  padding: 16px var(--adm-gutter) 0;
}

.an__ranges {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
}

.an__range {
  padding: 6px 13px;
  border: none;
  border-radius: 999px;
  background: none;
  color: var(--sf-muted);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.an__range--on {
  background: var(--sf-forest);
  color: #fff;
}

.an__window {
  margin: 0;
  color: var(--sf-muted);
  font-size: 13px;
}

.an__tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.an__panel {
  padding: 16px 18px 18px;
}

.an__panelhead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.an__panelhead--second {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--sf-rule);
}

.an__note {
  margin: 0;
  color: var(--sf-faint);
  font-size: 12px;
}

.an__split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 1000px) {
  .an__tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .an__split {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .page {
    padding: 14px 16px 0;
  }

  .an__tiles {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
