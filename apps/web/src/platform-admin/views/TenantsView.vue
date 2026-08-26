<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { messageFor, pesos } from '@pos/web/platform-admin/admin'
import {
  fetchTenants,
  setSubscriptionStatus,
  type SubscriptionStatus,
  type TenantFilters,
  type TenantRow,
} from '@pos/web/platform-admin/api'

/*
 * The tenant list.
 *
 * The old version rendered every organization into one wide table with every
 * action inline — which was workable at a dozen tenants and is not the shape
 * to grow into. This is a list: searchable, filterable, paginated, and each
 * row links to the tenant's own page where the destructive things live.
 *
 * Verify and reject stay inline, though. Working the queue is the job this
 * screen exists for, and making an operator open a page per shop to click one
 * button would be a worse tool, not a tidier one.
 */

const rows = ref<TenantRow[]>([])
const page = ref(1)
const lastPage = ref(1)
const total = ref(0)

const search = ref('')
const state = ref<'' | 'active' | 'suspended'>('')
const subscription = ref<'' | SubscriptionStatus | 'none'>('')

const loading = ref(false)
const error = ref('')
const busySlug = ref('')

async function load() {
  loading.value = true
  error.value = ''

  const filters: TenantFilters = { page: page.value }
  if (search.value.trim()) filters.q = search.value.trim()
  if (state.value) filters.state = state.value
  if (subscription.value) filters.subscription = subscription.value

  try {
    const result = await fetchTenants(filters)
    rows.value = result.organizations
    page.value = result.page
    lastPage.value = result.lastPage
    total.value = result.total
  } catch (err) {
    error.value = messageFor(err, 'Could not load the tenants.')
  } finally {
    loading.value = false
  }
}

onMounted(load)

// Any filter change starts from page one — staying on page 3 of a narrower
// result set shows an empty table and reads as a bug.
let debounce: ReturnType<typeof setTimeout> | undefined
watch([search, state, subscription], () => {
  page.value = 1
  clearTimeout(debounce)
  debounce = setTimeout(load, 250)
})

function subscriptionLabel(status: SubscriptionStatus | undefined) {
  if (status === 'active') return 'Verified'
  if (status === 'rejected') return 'Rejected'
  if (status === 'pending_verification') return 'Pending'
  return '—'
}

function subscriptionClass(status: SubscriptionStatus | undefined) {
  if (status === 'active') return 'pa-badge--good'
  if (status === 'rejected') return 'pa-badge--bad'
  if (status === 'pending_verification') return 'pa-badge--warn'
  return ''
}

async function decide(row: TenantRow, status: 'active' | 'rejected') {
  busySlug.value = row.organizationSlug
  error.value = ''

  try {
    const result = await setSubscriptionStatus(row.organizationSlug, status)
    if (row.subscription) {
      row.subscription.status = result.status
      row.subscription.verifiedAt = status === 'active' ? new Date().toISOString() : null
    }
  } catch (err) {
    error.value = messageFor(err, 'Could not update that subscription.')
  } finally {
    busySlug.value = ''
  }
}

async function turnPage(delta: number) {
  page.value = Math.min(lastPage.value, Math.max(1, page.value + delta))
  await load()
}
</script>

<template>
  <div class="pa-head">
    <div>
      <h1>Tenants</h1>
      <p class="pa-head__copy">
        {{ total }} {{ total === 1 ? 'store' : 'stores' }} on the platform.
      </p>
    </div>
    <button class="pa-button pa-button--quiet" type="button" :disabled="loading" @click="load">
      {{ loading ? 'Refreshing…' : 'Refresh' }}
    </button>
  </div>

  <div class="pa-filters">
    <label class="pa-field">
      <span class="pa-field__label">Search</span>
      <input v-model="search" class="pa-input" type="search" placeholder="Name or slug">
    </label>
    <label class="pa-field">
      <span class="pa-field__label">Account</span>
      <select v-model="state" class="pa-select">
        <option value="">Any</option>
        <option value="active">Active</option>
        <option value="suspended">Suspended</option>
      </select>
    </label>
    <label class="pa-field">
      <span class="pa-field__label">Subscription</span>
      <select v-model="subscription" class="pa-select">
        <option value="">Any</option>
        <option value="pending_verification">Pending</option>
        <option value="active">Verified</option>
        <option value="rejected">Rejected</option>
        <option value="none">None on record</option>
      </select>
    </label>
  </div>

  <p v-if="error" class="pa-alert">{{ error }}</p>

  <section class="pa-panel">
    <p v-if="!rows.length && !loading" class="pa-empty">No tenants match that.</p>

    <div v-else class="pa-table-wrap">
      <table class="pa-table">
        <thead>
          <tr>
            <th>Store</th>
            <th>Owner</th>
            <th>Type</th>
            <th>Subscription</th>
            <th>Account</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.organizationSlug">
            <td>
              <RouterLink class="pa-link" :to="`/tenants/${row.organizationSlug}`">
                <strong>{{ row.organizationName }}</strong>
              </RouterLink>
              <div class="pa-slug">{{ row.organizationSlug }}</div>
            </td>
            <td>
              <div v-if="!row.admins.length" class="pa-slug">—</div>
              <div v-for="admin in row.admins" :key="admin.uid">
                {{ admin.username }}
                <span v-if="admin.disabled" class="pa-badge pa-badge--bad">Login off</span>
              </div>
            </td>
            <td>{{ row.store?.businessMode ?? '—' }}</td>
            <td>
              <span class="pa-badge" :class="subscriptionClass(row.subscription?.status)">
                {{ subscriptionLabel(row.subscription?.status) }}
              </span>
              <div v-if="row.subscription" class="pa-tile__note">
                {{ pesos(row.subscription.amountCents) }} · {{ row.subscription.gcashReference }}
              </div>
              <div v-if="row.subscription?.status === 'pending_verification'" class="pa-actions">
                <button
                  class="pa-link"
                  type="button"
                  :disabled="busySlug === row.organizationSlug"
                  @click="decide(row, 'active')"
                >
                  Verify
                </button>
                <button
                  class="pa-link pa-link--danger"
                  type="button"
                  :disabled="busySlug === row.organizationSlug"
                  @click="decide(row, 'rejected')"
                >
                  Reject
                </button>
              </div>
            </td>
            <td>
              <span class="pa-badge" :class="row.suspended ? 'pa-badge--bad' : 'pa-badge--good'">
                {{ row.suspended ? 'Suspended' : 'Active' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <div v-if="lastPage > 1" class="pa-pager">
    <span>Page {{ page }} of {{ lastPage }}</span>
    <button class="pa-button pa-button--quiet" type="button" :disabled="page <= 1" @click="turnPage(-1)">
      Previous
    </button>
    <button
      class="pa-button pa-button--quiet"
      type="button"
      :disabled="page >= lastPage"
      @click="turnPage(1)"
    >
      Next
    </button>
  </div>
</template>
