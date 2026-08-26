<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { messageFor, pesos, pesosRounded, waitingLabel } from '@pos/web/platform-admin/admin'
import { fetchOverview, type Overview } from '@pos/web/platform-admin/api'
import StatTile from '@pos/web/platform-admin/components/StatTile.vue'

/*
 * The dashboard.
 *
 * Ordered by what an operator can act on. The attention tiles come first
 * because they are work; the tenant and revenue figures are context; the trend
 * and the busiest tenants are last because nothing on them needs a decision
 * today.
 *
 * The waiting queue is named, not just counted. "3 pending" and "3 pending,
 * one of them for eleven days" are different situations and only one of them
 * is urgent — so the oldest few are listed with their age, linking straight to
 * the tenant page where the decision gets made.
 */

const overview = ref<Overview | null>(null)
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''

  try {
    overview.value = await fetchOverview()
  } catch (err) {
    error.value = messageFor(err, 'Could not load the dashboard.')
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** Scaled against the busiest day so a quiet month still reads as a shape. */
const peak = computed(() =>
  Math.max(1, ...(overview.value?.volume.byDay ?? []).map((day) => day.orders)),
)

const trendRange = computed(() => {
  const days = overview.value?.volume.byDay ?? []
  if (days.length === 0) return null
  return { from: days[0].date, to: days[days.length - 1].date }
})
</script>

<template>
  <div class="pa-head">
    <div>
      <h1>Overview</h1>
      <p class="pa-head__copy">What needs a decision, and how the platform is doing.</p>
    </div>
    <button class="pa-button pa-button--quiet" type="button" :disabled="loading" @click="load">
      {{ loading ? 'Refreshing…' : 'Refresh' }}
    </button>
  </div>

  <p v-if="error" class="pa-alert">{{ error }}</p>

  <template v-if="overview">
    <div class="pa-tiles">
      <StatTile
        label="Signups waiting"
        :value="overview.queues.pendingSignups"
        :attention="overview.queues.pendingSignups > 0"
        note="GCash payments to verify"
      />
      <StatTile
        label="Riders waiting"
        :value="overview.queues.pendingRiders"
        :attention="overview.queues.pendingRiders > 0"
        note="Licences to review"
      />
      <StatTile
        label="Tenants"
        :value="overview.tenants.total"
        :note="`${overview.tenants.newLast7Days} new this week`"
      />
      <StatTile
        label="Suspended"
        :value="overview.tenants.suspended"
        note="Locked out, data kept"
      />
      <StatTile
        label="Monthly subscriptions"
        :value="pesosRounded(overview.revenue.monthlyCents)"
        :note="`${overview.revenue.activeSubscriptions} verified`"
      />
      <StatTile
        label="Orders, 30 days"
        :value="overview.volume.orders.toLocaleString('en-PH')"
        :note="`${overview.volume.onlineOrders.toLocaleString('en-PH')} online`"
      />
    </div>

    <div class="pa-columns">
      <div class="pa-rows">
        <section class="pa-panel">
          <div class="pa-panel__head">
            <h2>Waiting the longest</h2>
            <RouterLink v-if="overview.queues.pendingSignups" class="pa-link" to="/tenants">
              All tenants
            </RouterLink>
          </div>

          <p v-if="!overview.queues.oldestSignups.length" class="pa-empty">
            Nothing waiting. The signup queue is clear.
          </p>

          <div v-else class="pa-table-wrap">
            <table class="pa-table">
              <thead>
                <tr>
                  <th>Store</th>
                  <th>Reference</th>
                  <th class="pa-table__num">Amount</th>
                  <th>Waiting</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in overview.queues.oldestSignups" :key="row.organizationSlug">
                  <td>
                    <RouterLink class="pa-link" :to="`/tenants/${row.organizationSlug}`">
                      {{ row.organizationName }}
                    </RouterLink>
                    <div class="pa-slug">{{ row.organizationSlug }}</div>
                  </td>
                  <td class="pa-slug">{{ row.gcashReference }}</td>
                  <td class="pa-table__num">{{ pesos(row.amountCents) }}</td>
                  <td>
                    <span
                      class="pa-badge"
                      :class="(row.waitingDays ?? 0) >= 3 ? 'pa-badge--warn' : ''"
                    >
                      {{ waitingLabel(row.waitingDays) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="pa-panel">
          <div class="pa-panel__head">
            <h2>Orders, last {{ overview.volume.days }} days</h2>
            <span class="pa-tile__note">{{ pesos(overview.volume.revenueCents) }} across every tenant</span>
          </div>
          <div class="pa-panel__body">
            <p v-if="!overview.volume.byDay.length" class="pa-empty">No orders yet.</p>
            <template v-else>
              <!-- Bars rather than a chart library: this is one series over a
                   month, and the shape is the entire message. -->
              <div class="pa-trend">
                <div
                  v-for="day in overview.volume.byDay"
                  :key="day.date"
                  class="pa-trend__bar"
                  :style="{ height: `${Math.max(4, (day.orders / peak) * 100)}%` }"
                  :title="`${day.date} · ${day.orders} orders · ${day.online} online`"
                />
              </div>
              <div v-if="trendRange" class="pa-trend__axis">
                <span>{{ trendRange.from }}</span>
                <span>{{ trendRange.to }}</span>
              </div>
            </template>
          </div>
        </section>
      </div>

      <section class="pa-panel">
        <div class="pa-panel__head">
          <h2>Busiest tenants</h2>
          <span class="pa-tile__note">Last 30 days</span>
        </div>

        <p v-if="!overview.topTenants.length" class="pa-empty">No orders yet.</p>

        <div v-else class="pa-table-wrap">
          <table class="pa-table">
            <thead>
              <tr>
                <th>Store</th>
                <th class="pa-table__num">Orders</th>
                <th class="pa-table__num">Sales</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in overview.topTenants" :key="row.organizationSlug">
                <td>
                  <RouterLink class="pa-link" :to="`/tenants/${row.organizationSlug}`">
                    {{ row.organizationName }}
                  </RouterLink>
                </td>
                <td class="pa-table__num">{{ row.orders.toLocaleString('en-PH') }}</td>
                <td class="pa-table__num">{{ pesosRounded(row.revenueCents) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </template>

  <p v-else-if="loading" class="pa-empty">Loading…</p>
</template>
