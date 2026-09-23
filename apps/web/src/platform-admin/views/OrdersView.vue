<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Search } from '@lucide/vue'
import { api, type OrderRow, type Pagination, type Progress } from '../api'
import { count, dateTime, pesos, PROGRESS_LABELS } from '../format'
import PageHero from '../PageHero.vue'

// Every order on the platform, with the four tabs that cut it.
//
// Read-only by design — see PlatformOrderController. Advancing an order is the
// shop's job and cancelling one is a refund conversation; both have owners.

const props = defineProps<{ search: string }>()

const RANGES = [
  { days: 7, label: 'Last 7 days' },
  { days: 30, label: 'Last 30 days' },
  { days: 90, label: 'Last 90 days' },
  { days: 0, label: 'All time' },
]

const rows = ref<OrderRow[]>([])
const counts = ref<Record<Progress, number>>({ processing: 0, shipped: 0, completed: 0, cancelled: 0 })
const pagination = ref<Pagination>({ page: 1, perPage: 25, total: 0, lastPage: 1 })
const loading = ref(true)
const error = ref('')

const progress = ref<Progress | ''>('')
const days = ref(30)
const term = ref('')
const page = ref(1)

// The top bar's search is the portal's one search box, so a term typed there
// lands on whichever screen is open. Typing in this page's own box overrides
// it until the next one arrives.
watch(
  () => props.search,
  (next) => {
    term.value = next
    page.value = 1
    load()
  },
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await api.orders({
      q: term.value,
      progress: progress.value,
      days: days.value,
      page: page.value,
    })
    rows.value = data.orders
    counts.value = data.counts
    pagination.value = data.pagination
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load orders.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function setTab(next: Progress | '') {
  progress.value = next
  page.value = 1
  load()
}

function setRange(event: Event) {
  days.value = Number((event.target as HTMLSelectElement).value)
  page.value = 1
  load()
}

function goPage(next: number) {
  page.value = Math.min(Math.max(1, next), pagination.value.lastPage)
  load()
}

const allCount = computed(() => Object.values(counts.value).reduce((sum, value) => sum + value, 0))

const showing = computed(() => {
  if (pagination.value.total === 0) return 'No orders'
  const first = (pagination.value.page - 1) * pagination.value.perPage + 1
  const last = first + rows.value.length - 1
  return `Showing ${first}–${last} of ${count(pagination.value.total)} orders`
})
</script>

<template>
  <PageHero title="Orders" subtitle="Every order placed across the marketplace, and where each one has got to.">
    <template #tools>
      <label class="adm-field orders__range">
        <select :value="days" aria-label="Date range" @change="setRange">
          <option v-for="range in RANGES" :key="range.days" :value="range.days">{{ range.label }}</option>
        </select>
      </label>
    </template>
  </PageHero>

  <div class="page">
    <div class="adm-card orders__panel">
      <nav class="adm-tabs" aria-label="Filter orders by status">
        <button type="button" class="adm-tab" :class="{ 'adm-tab--on': progress === '' }" @click="setTab('')">
          All orders <span class="adm-tab__count">{{ count(allCount) }}</span>
        </button>
        <button
          v-for="key in (['processing', 'shipped', 'completed', 'cancelled'] as Progress[])"
          :key="key"
          type="button"
          class="adm-tab"
          :class="{ 'adm-tab--on': progress === key }"
          @click="setTab(key)"
        >
          {{ PROGRESS_LABELS[key] }} <span class="adm-tab__count">{{ count(counts[key] ?? 0) }}</span>
        </button>
      </nav>

      <div class="orders__toolbar">
        <form class="adm-field orders__search" role="search" @submit.prevent="page = 1; load()">
          <Search :size="15" aria-hidden="true" />
          <input v-model="term" type="search" placeholder="Search ticket, customer or shop" aria-label="Search orders" />
        </form>
        <p class="orders__showing">{{ showing }}</p>
      </div>

      <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>
      <p v-else-if="loading" class="adm-note">Loading orders…</p>

      <template v-else-if="rows.length > 0">
        <div class="adm-tablewrap">
          <table class="adm-table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Customer</th>
                <th>Shop</th>
                <th class="adm-right">Items</th>
                <th class="adm-right">Total</th>
                <th>Payment</th>
                <th>Status</th>
                <th>Placed</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in rows" :key="order.id">
                <td class="orders__ticket">{{ order.ticketNumber }}</td>
                <td>{{ order.customerName }}</td>
                <td class="orders__shop">{{ order.storeName ?? '—' }}</td>
                <td class="adm-right adm-num">{{ order.items }}</td>
                <td class="adm-right adm-num">{{ pesos(order.totalCents, { exact: true }) }}</td>
                <td>
                  <span class="adm-pill" :class="order.paymentStatus === 'paid' ? 'adm-pill--completed' : 'adm-pill--neutral'">
                    {{ order.paymentStatus === 'paid' ? 'Paid' : 'Unpaid' }}
                  </span>
                </td>
                <td>
                  <span class="adm-pill" :class="`adm-pill--${order.progress}`">
                    <span class="adm-pill__dot" aria-hidden="true"></span>
                    {{ PROGRESS_LABELS[order.progress] }}
                  </span>
                </td>
                <td class="orders__when">{{ dateTime(order.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="pagination.lastPage > 1" class="orders__pager">
          <button type="button" class="adm-btn adm-btn--quiet" :disabled="pagination.page <= 1" @click="goPage(pagination.page - 1)">
            <ChevronLeft :size="15" aria-hidden="true" />
            Previous
          </button>
          <span class="orders__pageno">Page {{ pagination.page }} of {{ pagination.lastPage }}</span>
          <button
            type="button"
            class="adm-btn adm-btn--quiet"
            :disabled="pagination.page >= pagination.lastPage"
            @click="goPage(pagination.page + 1)"
          >
            Next
            <ChevronRight :size="15" aria-hidden="true" />
          </button>
        </div>
      </template>

      <div v-else class="adm-empty">
        <p class="adm-empty__title">Nothing matches</p>
        <p class="adm-empty__copy">
          <template v-if="term">No order matches “{{ term }}” in this window.</template>
          <template v-else-if="progress">No order is {{ PROGRESS_LABELS[progress].toLowerCase() }} in this window.</template>
          <template v-else>No orders were placed in this window. Widen the date range to look further back.</template>
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 16px var(--adm-gutter) 0;
}

.orders__panel {
  padding: 0 0 6px;
}

.orders__range {
  background: rgba(255, 255, 255, 0.94);
  border-color: transparent;
}

.adm-tabs {
  padding: 4px 14px 0;
}

.orders__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px;
}

.orders__search {
  flex: 1;
  min-width: 220px;
  max-width: 380px;
}

.orders__showing {
  margin: 0;
  color: var(--sf-muted);
  font-size: 13px;
}

.orders__ticket {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.orders__shop,
.orders__when {
  color: var(--sf-muted);
  white-space: nowrap;
}

.orders__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 14px;
}

.orders__pageno {
  color: var(--sf-muted);
  font-size: 13px;
}

@media (max-width: 560px) {
  .page {
    padding: 14px 16px 0;
  }
}
</style>
