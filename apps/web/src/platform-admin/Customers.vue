<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

/*
 * The operator's view of the people who shop on the storefront.
 *
 * A shopper is the one account nobody owns: a store sees only the orders placed
 * with it, and the customer portal shows a customer only themselves. When
 * someone writes in about a missing order there is otherwise no screen that can
 * answer "who is this and what did they buy" — that is what this is for.
 *
 * Read-only, matching the endpoint. Everything an operator might want to change
 * about a shopper either needs a column that does not exist yet or sends mail
 * to a real person, so neither belongs behind a button added in passing.
 */

const props = defineProps<{ token: string }>()

/*
 * Raised when the API rejects the token, so the page above drops the session
 * rather than leaving this screen retrying against a dead one.
 */
const emit = defineEmits<{ (event: 'session-ended', message: string): void }>()

interface CustomerRow {
  id: string
  name: string
  email: string
  phone: string | null
  emailVerified: boolean
  ordersCount: number
  addressesCount: number
  totalSpentCents: number
  lastOrderAt: string | null
  createdAt: string | null
}

interface CustomerAddress {
  id: string
  label: string
  line1: string
  barangay: string | null
  city: string
  notes: string | null
  isDefault: boolean
}

interface CustomerOrder {
  id: string
  ticketNumber: string | null
  storeName: string | null
  status: string
  paymentStatus: string
  fulfillmentMethod: string | null
  totalCents: number
  placedAt: string | null
}

interface CustomerPaymentMethod {
  id: string
  kind: string
  detail: string | null
  isDefault: boolean
}

type CustomerDetail = CustomerRow & {
  addresses: CustomerAddress[]
  orders: CustomerOrder[]
  paymentMethods: CustomerPaymentMethod[]
}

const rows = ref<CustomerRow[]>([])
const pagination = ref({ page: 1, perPage: 25, total: 0, lastPage: 1 })
const search = ref('')
const loading = ref(false)
const errorMessage = ref('')

const selected = ref<CustomerDetail | null>(null)
const detailLoading = ref(false)

function formatPesos(amountCents: number) {
  return `₱${(amountCents / 100).toLocaleString('en-PH', { minimumFractionDigits: 2 })}`
}

function formatDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium' }).format(new Date(value))
}

async function get(path: string) {
  const response = await fetch(path, {
    headers: { Accept: 'application/json', Authorization: `Bearer ${props.token}` },
  })
  const data = await response.json().catch(() => ({}))
  if (response.status === 401 || response.status === 403) {
    const message = data.message || 'Your session has ended. Sign in again.'
    emit('session-ended', message)
    throw new Error(message)
  }
  if (!response.ok) {
    throw new Error(data.message || data.error || 'Something went wrong.')
  }
  return data
}

async function load(page = 1) {
  loading.value = true
  errorMessage.value = ''
  try {
    const query = new URLSearchParams({ page: String(page) })
    if (search.value.trim()) query.set('q', search.value.trim())

    const data = await get(`/api/platform-admin/customers?${query.toString()}`)
    rows.value = data.customers ?? []
    pagination.value = data.pagination ?? pagination.value
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to load customers.'
  } finally {
    loading.value = false
  }
}

async function openCustomer(row: CustomerRow) {
  detailLoading.value = true
  errorMessage.value = ''
  try {
    const data = await get(`/api/platform-admin/customers/${encodeURIComponent(row.id)}`)
    selected.value = data.customer
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to open that customer.'
  } finally {
    detailLoading.value = false
  }
}

function closeCustomer() {
  selected.value = null
}

/*
 * Debounced: the search box hits the API on every keystroke otherwise, and this
 * list is behind an operator credential rather than a cache.
 */
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(search, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void load(1), 300)
})

onMounted(() => void load(1))
</script>

<template>
  <section>
    <div class="pa-header">
      <div>
        <h1 class="pa-title">Customers</h1>
        <p class="pa-copy">
          Everyone with a storefront account, across every store. Search by name, email or phone.
        </p>
      </div>
      <button class="segment-button" type="button" :disabled="loading" @click="load(pagination.page)">
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </div>

    <label class="settings-field cu-search">
      <span class="settings-row__label">Search</span>
      <input
        v-model="search"
        class="sheet-input"
        type="search"
        placeholder="Name, email or phone"
        autocomplete="off"
      >
    </label>

    <p v-if="errorMessage" class="auth-error">{{ errorMessage }}</p>

    <div class="pa-table-wrap surface-panel">
      <table class="pa-table">
        <thead>
          <tr>
            <th>Customer</th>
            <th>Orders</th>
            <th>Spent</th>
            <th>Last order</th>
            <th>Joined</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!rows.length && !loading">
            <td colspan="6" class="cu-empty">
              {{ search.trim() ? 'No customer matches that search.' : 'No customers yet.' }}
            </td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td>
              <strong>{{ row.name }}</strong>
              <div class="pa-slug">{{ row.email }}</div>
              <div class="pa-slug">{{ row.phone || 'No phone' }}</div>
              <!-- The one flag support actually needs: it explains why someone
                   cannot sign in, which is most of what gets asked. -->
              <span v-if="!row.emailVerified" class="cu-flag">Email unverified</span>
            </td>
            <td>{{ row.ordersCount }}</td>
            <td>{{ formatPesos(row.totalSpentCents) }}</td>
            <td>{{ formatDate(row.lastOrderAt) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>
              <button class="pa-link-button" type="button" @click="openCustomer(row)">View</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="pagination.lastPage > 1" class="cu-pager">
      <button
        class="segment-button"
        type="button"
        :disabled="loading || pagination.page <= 1"
        @click="load(pagination.page - 1)"
      >
        Previous
      </button>
      <span class="pa-copy">
        Page {{ pagination.page }} of {{ pagination.lastPage }} · {{ pagination.total }} total
      </span>
      <button
        class="segment-button"
        type="button"
        :disabled="loading || pagination.page >= pagination.lastPage"
        @click="load(pagination.page + 1)"
      >
        Next
      </button>
    </div>

    <div v-if="selected || detailLoading" class="cu-sheet-backdrop" @click.self="closeCustomer">
      <section class="cu-sheet surface-panel">
        <p v-if="detailLoading" class="pa-copy">Loading…</p>

        <template v-else-if="selected">
          <div class="pa-header">
            <div>
              <h2 class="pa-title">{{ selected.name }}</h2>
              <p class="pa-copy">{{ selected.email }} · {{ selected.phone || 'No phone' }}</p>
            </div>
            <button class="segment-button" type="button" @click="closeCustomer">Close</button>
          </div>

          <dl class="cu-stats">
            <div><dt>Orders</dt><dd>{{ selected.ordersCount }}</dd></div>
            <div><dt>Spent</dt><dd>{{ formatPesos(selected.totalSpentCents) }}</dd></div>
            <div><dt>Joined</dt><dd>{{ formatDate(selected.createdAt) }}</dd></div>
            <div>
              <dt>Email</dt>
              <dd>{{ selected.emailVerified ? 'Verified' : 'Unverified' }}</dd>
            </div>
          </dl>

          <h3 class="cu-subhead">Addresses</h3>
          <p v-if="!selected.addresses.length" class="pa-copy">No saved addresses.</p>
          <ul v-else class="cu-list">
            <li v-for="address in selected.addresses" :key="address.id">
              <strong>{{ address.label }}</strong>
              <span v-if="address.isDefault" class="cu-flag">Default</span>
              <div class="pa-slug">
                {{ address.line1 }}<template v-if="address.barangay">, {{ address.barangay }}</template>, {{ address.city }}
              </div>
              <div v-if="address.notes" class="pa-slug">{{ address.notes }}</div>
            </li>
          </ul>

          <h3 class="cu-subhead">Payment methods</h3>
          <p v-if="!selected.paymentMethods.length" class="pa-copy">None saved.</p>
          <ul v-else class="cu-list">
            <li v-for="method in selected.paymentMethods" :key="method.id">
              <strong>{{ method.kind }}</strong>
              <span v-if="method.isDefault" class="cu-flag">Default</span>
              <div v-if="method.detail" class="pa-slug">{{ method.detail }}</div>
            </li>
          </ul>

          <h3 class="cu-subhead">Recent orders</h3>
          <p v-if="!selected.orders.length" class="pa-copy">No orders yet.</p>
          <table v-else class="pa-table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Store</th>
                <th>Status</th>
                <th>Payment</th>
                <th>Total</th>
                <th>Placed</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in selected.orders" :key="order.id">
                <td>{{ order.ticketNumber || order.id.slice(0, 8) }}</td>
                <td>{{ order.storeName || '—' }}</td>
                <td>{{ order.status }}</td>
                <td>{{ order.paymentStatus }}</td>
                <td>{{ formatPesos(order.totalCents) }}</td>
                <td>{{ formatDate(order.placedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </template>
      </section>
    </div>
  </section>
</template>

<style scoped>
.cu-search {
  max-width: 380px;
  margin-bottom: var(--space-4, 16px);
}

.cu-empty {
  padding: 24px 0;
  color: var(--text-secondary);
  text-align: center;
}

.cu-flag {
  display: inline-block;
  margin-top: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--surface-sunken, rgba(0, 0, 0, 0.06));
  color: var(--text-secondary);
  font: 600 11px/1.6 inherit;
}

.cu-pager {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: var(--space-4, 16px);
}

/* Fixed rather than absolute: the list behind it scrolls, and a detail panel
   that scrolls away from the row you opened is worse than no panel. */
.cu-sheet-backdrop {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 5vh 16px;
  background: rgba(0, 0, 0, 0.35);
  overflow-y: auto;
  z-index: 40;
}

.cu-sheet {
  width: min(760px, 100%);
  padding: var(--space-5, 20px);
  border-radius: 16px;
}

.cu-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin: var(--space-4, 16px) 0;
}

.cu-stats dt {
  color: var(--text-secondary);
  font: 600 12px/1.6 inherit;
}

.cu-stats dd {
  margin: 0;
  font: 600 16px/1.4 inherit;
}

.cu-subhead {
  margin: var(--space-5, 20px) 0 8px;
  font: 600 14px/1.4 inherit;
}

.cu-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.cu-list li + li {
  margin-top: 12px;
}
</style>
