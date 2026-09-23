<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { formatCurrency } from '@pos/shared/index'
import { fetchRiderEarnings, type EarningsSummary } from '@pos/web/rider/api'
import { applyGateRejection, messageFor } from '@pos/web/rider/rider'

/*
 * What the rider has made.
 *
 * The portal could only ever add up whatever happened to be on screen — the
 * finished list is the server's last thirty jobs — so "how much did I make this
 * week" had no answer past thirty deliveries, and "this month" had none at all.
 * `GET /api/rider/earnings` aggregates in the database; this screen only draws.
 *
 * **The whole fee is the rider's.** There is no platform cut to show, and the
 * copy says so once rather than implying it by omission — it is the product's
 * actual position, not a promotion.
 *
 * Deliberately not a report. No CSV, no date picker, no per-shop breakdown: a
 * rider checking their phone between jobs wants today, this week, and whether
 * this week is better than last. Anything more is an accounting screen, and the
 * moment it is needed it should be built as one rather than grown here.
 */

const summary = ref<EarningsSummary | null>(null)
const loading = ref(true)
const errorMessage = ref('')

async function load() {
  loading.value = true
  errorMessage.value = ''

  try {
    summary.value = await fetchRiderEarnings()
  } catch (error) {
    if (!applyGateRejection(error)) {
      errorMessage.value = messageFor(error, 'Could not load your earnings.')
    }
  } finally {
    loading.value = false
  }
}

/** The tallest bar sets the scale, so a quiet fortnight still has a shape. */
const peakCents = computed(() =>
  Math.max(1, ...(summary.value?.days ?? []).map((day) => day.feeCents)),
)

const days = computed(() => summary.value?.days ?? [])

function barHeight(cents: number): string {
  // A floor of 2% so a day with nothing on it is still a visible baseline
  // rather than a gap the eye reads as missing data.
  return `${Math.max(2, Math.round((cents / peakCents.value) * 100))}%`
}

function dayLabel(date: string): string {
  // Parsed as a local date, not UTC: `new Date('2026-09-08')` is midnight UTC,
  // which is the previous evening in Manila and would shift every label.
  const [year, month, day] = date.split('-').map(Number)
  return new Intl.DateTimeFormat('en-PH', { weekday: 'narrow' }).format(
    new Date(year, month - 1, day),
  )
}

const averageCents = computed(() => {
  const all = summary.value?.allTime
  if (!all || all.jobs === 0) return 0
  return Math.round(all.feeCents / all.jobs)
})

onMounted(() => void load())
</script>

<template>
  <div>
    <h1 class="rdr-title">Earnings</h1>
    <p class="rdr-sub">Every peso of the delivery fee is yours — the platform takes none of it.</p>

    <p v-if="errorMessage" class="rdr-flash rdr-flash--error">{{ errorMessage }}</p>
    <p v-if="loading" class="rdr-sub">Loading…</p>

    <template v-else-if="summary">
      <div class="rdr-card rdr-earn__hero">
        <p class="rdr-earn__hero-label">Today</p>
        <p class="rdr-earn__hero-value">{{ formatCurrency(summary.today.feeCents) }}</p>
        <p class="rdr-earn__hero-note">
          {{ summary.today.jobs }} delivery{{ summary.today.jobs === 1 ? '' : ' jobs' }} finished
        </p>
      </div>

      <div class="rdr-earn__grid">
        <div class="rdr-card rdr-earn__tile">
          <p class="rdr-earn__tile-label">This week</p>
          <p class="rdr-earn__tile-value">{{ formatCurrency(summary.week.feeCents) }}</p>
          <p class="rdr-earn__tile-note">{{ summary.week.jobs }} jobs</p>
        </div>
        <div class="rdr-card rdr-earn__tile">
          <p class="rdr-earn__tile-label">This month</p>
          <p class="rdr-earn__tile-value">{{ formatCurrency(summary.month.feeCents) }}</p>
          <p class="rdr-earn__tile-note">{{ summary.month.jobs }} jobs</p>
        </div>
      </div>

      <p class="rdr-section-title">Last 14 days</p>

      <div class="rdr-card">
        <div class="rdr-earn__chart">
          <div v-for="day in days" :key="day.date" class="rdr-earn__col">
            <!-- The value is on the bar's title rather than printed under it:
                 fourteen labels do not fit on a phone, and a rider who wants
                 one number can hold the bar. -->
            <div
              class="rdr-earn__bar"
              :class="{ 'rdr-earn__bar--empty': day.feeCents === 0 }"
              :style="{ height: barHeight(day.feeCents) }"
              :title="`${day.date}: ${formatCurrency(day.feeCents)} from ${day.jobs} job${day.jobs === 1 ? '' : 's'}`"
            />
            <span class="rdr-earn__day">{{ dayLabel(day.date) }}</span>
          </div>
        </div>
      </div>

      <div class="rdr-card rdr-earn__totals">
        <div class="rdr-earn__row">
          <span>All time</span>
          <strong>{{ formatCurrency(summary.allTime.feeCents) }}</strong>
        </div>
        <div class="rdr-earn__row">
          <span>Deliveries finished</span>
          <strong>{{ summary.allTime.jobs }}</strong>
        </div>
        <div v-if="summary.allTime.jobs > 0" class="rdr-earn__row">
          <span>Average per delivery</span>
          <strong>{{ formatCurrency(averageCents) }}</strong>
        </div>
      </div>

      <p class="rdr-earn__foot">
        Counted from the day you took each job, in Philippine time. Jobs you are still
        carrying are not in here — they are money you are about to make, not money you
        have made.
      </p>
    </template>

    <div class="rdr-actions">
      <button class="rdr-btn rdr-btn--ghost" type="button" :disabled="loading" @click="load()">
        {{ loading ? 'Refreshing…' : 'Refresh' }}
      </button>
    </div>
  </div>
</template>
