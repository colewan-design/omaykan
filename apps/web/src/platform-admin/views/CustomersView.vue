<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Search } from '@lucide/vue'
import { api, type CustomerRow, type Pagination } from '../api'
import { count, pesos, shortDate } from '../format'
import PageHero from '../PageHero.vue'

// The people who shop here.
//
// Shoppers are the one population on this platform with no owner — a shop sees
// only the orders placed with it, and a customer's own portal sees only
// themselves — so this is the only place a whole account can be looked at when
// somebody writes in about one. Read-only, for the reasons
// PlatformCustomerController gives.

const props = defineProps<{ search: string }>()

const rows = ref<CustomerRow[]>([])
const pagination = ref<Pagination>({ page: 1, perPage: 25, total: 0, lastPage: 1 })
const loading = ref(true)
const error = ref('')
const term = ref('')
const page = ref(1)

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
    const data = await api.customers({ q: term.value, page: page.value })
    rows.value = data.customers
    pagination.value = data.pagination
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load customers.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function goPage(next: number) {
  page.value = Math.min(Math.max(1, next), pagination.value.lastPage)
  load()
}

/**
 * A shorthand for how much of a regular someone is. It is a reading of the
 * order count, not a status the platform assigns — nothing is gated on it, so
 * it can be a plain description rather than a tier with rules.
 */
function standing(row: CustomerRow): { label: string; pill: string } {
  if (row.ordersCount >= 10) return { label: 'Regular', pill: 'adm-pill--completed' }
  if (row.ordersCount >= 2) return { label: 'Returning', pill: 'adm-pill--shipped' }
  if (row.ordersCount === 1) return { label: 'First order', pill: 'adm-pill--processing' }
  return { label: 'Signed up', pill: 'adm-pill--neutral' }
}

const showing = computed(() =>
  pagination.value.total === 0 ? 'No customers' : `${count(pagination.value.total)} customers`,
)
</script>

<template>
  <PageHero title="Customers" subtitle="Everyone with an account on the storefront, and what they have ordered." />

  <div class="page">
    <div class="adm-card cust__panel">
      <div class="cust__toolbar">
        <form class="adm-field cust__search" role="search" @submit.prevent="page = 1; load()">
          <Search :size="15" aria-hidden="true" />
          <input v-model="term" type="search" placeholder="Search name, email or phone" aria-label="Search customers" />
        </form>
        <p class="cust__showing">{{ showing }}</p>
      </div>

      <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>
      <p v-else-if="loading" class="adm-note">Loading customers…</p>

      <template v-else-if="rows.length > 0">
        <div class="adm-tablewrap">
          <table class="adm-table">
            <thead>
              <tr>
                <th>Customer</th>
                <th>Phone</th>
                <th class="adm-right">Orders</th>
                <th class="adm-right">Spent</th>
                <th>Last order</th>
                <th>Standing</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <div class="cust__who">
                    <span class="cust__avatar" aria-hidden="true">{{ (row.name || '?').slice(0, 1) }}</span>
                    <span class="cust__name">
                      <strong>{{ row.name }}</strong>
                      <small>{{ row.email }}</small>
                    </span>
                  </div>
                </td>
                <td class="cust__muted">{{ row.phone ?? '—' }}</td>
                <td class="adm-right adm-num">{{ count(row.ordersCount) }}</td>
                <!-- Paid orders only: see PlatformCustomerController. -->
                <td class="adm-right adm-num">{{ pesos(row.totalSpentCents) }}</td>
                <td class="cust__muted">{{ shortDate(row.lastOrderAt) }}</td>
                <td>
                  <span class="adm-pill" :class="standing(row).pill">{{ standing(row).label }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="pagination.lastPage > 1" class="cust__pager">
          <button type="button" class="adm-btn adm-btn--quiet" :disabled="pagination.page <= 1" @click="goPage(pagination.page - 1)">
            <ChevronLeft :size="15" aria-hidden="true" />
            Previous
          </button>
          <span class="cust__pageno">Page {{ pagination.page }} of {{ pagination.lastPage }}</span>
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
        <p class="adm-empty__title">No customers yet</p>
        <p class="adm-empty__copy">
          <template v-if="term">Nobody matches “{{ term }}”.</template>
          <template v-else>Shoppers appear here when they create an account on the storefront.</template>
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 16px var(--adm-gutter) 0;
}

.cust__panel {
  padding: 0 0 6px;
}

.cust__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
}

.cust__search {
  flex: 1;
  min-width: 220px;
  max-width: 380px;
}

.cust__showing {
  margin: 0;
  color: var(--sf-muted);
  font-size: 13px;
}

.cust__who {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.cust__avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--sf-sand);
  color: var(--sf-forest);
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  flex: none;
}

.cust__name {
  display: grid;
  min-width: 0;
  line-height: 1.3;
}

.cust__name small {
  color: var(--sf-faint);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cust__muted {
  color: var(--sf-muted);
  white-space: nowrap;
}

.cust__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 14px;
}

.cust__pageno {
  color: var(--sf-muted);
  font-size: 13px;
}

@media (max-width: 560px) {
  .page {
    padding: 14px 16px 0;
  }
}
</style>
