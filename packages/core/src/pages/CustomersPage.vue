<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronRight, Pencil, Plus, Search, Trash2, Users, X } from '@lucide/vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import ChartCard from '@pos/core/components/ChartCard.vue'
import MetricCard from '@pos/core/components/MetricCard.vue'
import CustomerPointsPanel from '@pos/core/components/CustomerPointsPanel.vue'
import LoyaltySettingsCard from '@pos/core/components/LoyaltySettingsCard.vue'
import { getPosRepository } from '@pos/core/services/runtime'
import RangeSelector, { type Range } from '@pos/core/components/RangeSelector.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { formatCompactDate, formatCurrency, guestCustomerName, type Customer, type OrderSummary } from '@pos/shared/index'

const store = usePosStore()

/** Points by customer id, from the server. Empty when it cannot be reached. */
const balances = ref<Record<string, number>>({})

onMounted(async () => {
  if (!store.isReady) {
    void store.initialize()
  }
  balances.value = await getPosRepository().loadLoyaltyBalances()
})

const tierFilterOptions: { value: 'all' | CustomerTier; label: string }[] = [
  { value: 'all',        label: 'All tiers' },
  { value: 'VIP',        label: 'VIP' },
  { value: 'Regular',    label: 'Regular' },
  { value: 'Occasional', label: 'Occasional' },
  { value: 'New',        label: 'New' },
]

const range = ref<Range>('month')
const searchQuery = ref('')
const tierFilter = ref<'all' | CustomerTier>('all')
const showForm = ref(false)
const editingCustomerId = ref<string | null>(null)
const formName = ref('')
const formPhone = ref('')
const formEmail = ref('')
const formNotes = ref('')
/**
 * Enrolled in points. Asked, not assumed: keeping a named person's number for
 * a points scheme is personal data under the Data Privacy Act, and nobody
 * earns until they have agreed.
 */
const formConsent = ref(false)
const formError = ref('')
const savingCustomer = ref(false)
const now = new Date()

type CustomerTier = 'VIP' | 'Regular' | 'Occasional' | 'New'

interface Bounds {
  start: Date
  end: Date
  prevStart: Date
  prevEnd: Date
}

interface CustomerProfile {
  id: string
  customerId: string | null
  name: string
  initials: string
  visits: number
  totalSpendCents: number
  averageSpendCents: number
  lastVisitAt: string
  favoriteItem: string
  preferredPaymentMethod: string
  tier: CustomerTier
  phone?: string
  email?: string
  notes?: string
  hasRecord: boolean
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function getMonday(d: Date) {
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  return new Date(startOfDay(d).getTime() + diff * 86400000)
}

function getBounds(value: Range): Bounds {
  const today = startOfDay(now)
  switch (value) {
    case 'today': {
      const start = today
      const end = new Date(today.getTime() + 86400000)
      return { start, end, prevStart: new Date(start.getTime() - 86400000), prevEnd: start }
    }
    case 'week': {
      const start = getMonday(now)
      const end = new Date(start.getTime() + 7 * 86400000)
      return { start, end, prevStart: new Date(start.getTime() - 7 * 86400000), prevEnd: start }
    }
    case 'month': {
      const start = new Date(today.getFullYear(), today.getMonth(), 1)
      const end = new Date(today.getFullYear(), today.getMonth() + 1, 1)
      const prevStart = new Date(today.getFullYear(), today.getMonth() - 1, 1)
      return { start, end, prevStart, prevEnd: start }
    }
    case 'all': {
      const epoch = new Date(0)
      const far = new Date(8640000000000000)
      return { start: epoch, end: far, prevStart: epoch, prevEnd: epoch }
    }
  }
}

function inBounds(order: OrderSummary, start: Date, end: Date) {
  const createdAt = new Date(order.createdAt)
  return createdAt >= start && createdAt < end
}

function initialsFor(name: string) {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}

function tierFor(visits: number, totalSpendCents: number): CustomerTier {
  if (visits >= 8 || totalSpendCents >= 150000) return 'VIP'
  if (visits >= 4 || totalSpendCents >= 70000) return 'Regular'
  if (visits >= 2) return 'Occasional'
  return 'New'
}

function isGuestOrder(order: OrderSummary) {
  return order.customerName === guestCustomerName
}

function profileKeyForOrder(order: OrderSummary) {
  return order.customerId ?? `name:${order.customerName.trim().toLowerCase()}`
}

function baseProfile(name: string, customer?: Customer): CustomerProfile {
  return {
    id: customer?.id ?? `name:${name.trim().toLowerCase()}`,
    customerId: customer?.id ?? null,
    name,
    initials: initialsFor(name),
    visits: 0,
    totalSpendCents: 0,
    averageSpendCents: 0,
    lastVisitAt: '',
    favoriteItem: 'No orders yet',
    preferredPaymentMethod: 'Not set',
    tier: 'New',
    phone: customer?.phone,
    email: customer?.email,
    notes: customer?.notes,
    hasRecord: Boolean(customer),
  }
}

function buildProfiles(orders: OrderSummary[], customers: Customer[]) {
  const profileMap = new Map<string, CustomerProfile>()
  const favoriteCounts = new Map<string, Map<string, number>>()
  const paymentCounts = new Map<string, Map<string, number>>()

  for (const customer of customers) {
    profileMap.set(customer.id, baseProfile(customer.name, customer))
  }

  for (const order of orders) {
    if (isGuestOrder(order)) {
      continue
    }

    const key = profileKeyForOrder(order)
    const existing = profileMap.get(key) ?? baseProfile(order.customerName)
    existing.id = key
    existing.customerId = order.customerId ?? existing.customerId
    existing.name = order.customerName
    existing.initials = initialsFor(order.customerName)
    existing.visits += 1
    existing.totalSpendCents += order.totalCents
    if (!existing.lastVisitAt || order.createdAt > existing.lastVisitAt) {
      existing.lastVisitAt = order.createdAt
    }
    if (existing.preferredPaymentMethod === 'Not set') {
      existing.preferredPaymentMethod = order.paymentMethod
    }
    if (existing.favoriteItem === 'No orders yet') {
      existing.favoriteItem = order.items[0]?.name ?? 'Mixed order'
    }
    profileMap.set(key, existing)

    const itemCounts = favoriteCounts.get(key) ?? new Map<string, number>()
    for (const item of order.items) {
      itemCounts.set(item.name, (itemCounts.get(item.name) ?? 0) + item.quantity)
    }
    favoriteCounts.set(key, itemCounts)

    const paymentMap = paymentCounts.get(key) ?? new Map<string, number>()
    paymentMap.set(order.paymentMethod, (paymentMap.get(order.paymentMethod) ?? 0) + 1)
    paymentCounts.set(key, paymentMap)
  }

  return Array.from(profileMap.values())
    .map((profile) => {
      const favoriteItem = Array.from(favoriteCounts.get(profile.id)?.entries() ?? [])
        .sort((a, b) => b[1] - a[1])[0]?.[0] ?? profile.favoriteItem
      const preferredPaymentMethod = Array.from(paymentCounts.get(profile.id)?.entries() ?? [])
        .sort((a, b) => b[1] - a[1])[0]?.[0] ?? profile.preferredPaymentMethod
      const averageSpendCents = profile.visits > 0 ? Math.round(profile.totalSpendCents / profile.visits) : 0
      return {
        ...profile,
        favoriteItem,
        preferredPaymentMethod,
        averageSpendCents,
        tier: tierFor(profile.visits, profile.totalSpendCents),
      }
    })
    .sort((a, b) => {
      if (b.totalSpendCents !== a.totalSpendCents) {
        return b.totalSpendCents - a.totalSpendCents
      }
      if (b.visits !== a.visits) {
        return b.visits - a.visits
      }
      return a.name.localeCompare(b.name)
    })
}

function delta(current: number, previous: number) {
  if (range.value === 'all' || previous === 0) {
    return null
  }

  const percentage = ((current - previous) / previous) * 100
  return {
    value: `${Math.abs(percentage).toFixed(1)}%`,
    positive: percentage >= 0,
  }
}

function resetForm() {
  editingCustomerId.value = null
  formName.value = ''
  formPhone.value = ''
  formEmail.value = ''
  formNotes.value = ''
  formConsent.value = false
  formError.value = ''
}

function closeForm() {
  showForm.value = false
  resetForm()
}

function openCreateForm() {
  showForm.value = true
  resetForm()
}

function openEditForm(profile: CustomerProfile) {
  if (!profile.hasRecord || !profile.customerId) {
    return
  }

  const customer = store.customers.find((entry) => entry.id === profile.customerId)
  if (!customer) {
    return
  }

  showForm.value = true
  editingCustomerId.value = customer.id
  formName.value = customer.name
  formPhone.value = customer.phone ?? ''
  formEmail.value = customer.email ?? ''
  formNotes.value = customer.notes ?? ''
  formConsent.value = Boolean(customer.loyaltyConsentAt)
  formError.value = ''
}

async function saveCustomer() {
  const name = formName.value.trim()
  if (!name) {
    formError.value = 'Customer name is required.'
    return
  }

  savingCustomer.value = true
  formError.value = ''

  try {
    const existing = editingCustomerId.value
      ? store.customers.find((entry) => entry.id === editingCustomerId.value)
      : null
    // Keep the original date of consent while it stands; a fresh one only
    // when it is newly given.
    const loyaltyConsentAt = formConsent.value ? existing?.loyaltyConsentAt ?? new Date().toISOString() : null

    if (editingCustomerId.value) {
      await store.editCustomer({
        id: editingCustomerId.value,
        name,
        phone: formPhone.value,
        email: formEmail.value,
        notes: formNotes.value,
        loyaltyConsentAt,
        createdAt: store.customers.find((entry) => entry.id === editingCustomerId.value)?.createdAt ?? new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      })
    } else {
      await store.createCustomer({
        name,
        phone: formPhone.value,
        email: formEmail.value,
        notes: formNotes.value,
        loyaltyConsentAt,
      })
    }
    closeForm()
  } finally {
    savingCustomer.value = false
  }
}

async function removeCustomer(profile: CustomerProfile) {
  if (!profile.hasRecord || !profile.customerId) {
    return
  }

  if (!window.confirm(`Remove ${profile.name} from saved customers? Existing orders will keep their history.`)) {
    return
  }

  await store.removeCustomer(profile.customerId)
  if (editingCustomerId.value === profile.customerId) {
    closeForm()
  }
}

function contactLine(profile: CustomerProfile) {
  return profile.phone || profile.email || 'Walk-in details not saved'
}

function visitLabel(profile: CustomerProfile) {
  return profile.lastVisitAt ? formatCompactDate(profile.lastVisitAt) : 'No visits yet'
}

const bounds = computed(() => getBounds(range.value))
const activeOrders = computed(() => store.orders.filter((order) => !order.voidedAt))
const periodOrders = computed(() => activeOrders.value.filter((order) => inBounds(order, bounds.value.start, bounds.value.end)))
const priorOrders = computed(() =>
  range.value === 'all'
    ? []
    : activeOrders.value.filter((order) => inBounds(order, bounds.value.prevStart, bounds.value.prevEnd)),
)

const customerProfiles = computed(() => buildProfiles(periodOrders.value, store.customers))
const previousCustomerProfiles = computed(() => buildProfiles(priorOrders.value, store.customers))
const activeCustomerProfiles = computed(() => customerProfiles.value.filter((customer) => customer.visits > 0))
const previousActiveCustomerProfiles = computed(() => previousCustomerProfiles.value.filter((customer) => customer.visits > 0))

const filteredCustomers = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()

  return customerProfiles.value.filter((customer) => {
    if (tierFilter.value !== 'all' && customer.tier !== tierFilter.value) {
      return false
    }

    if (!needle) {
      return true
    }

    return (
      customer.name.toLowerCase().includes(needle)
      || customer.favoriteItem.toLowerCase().includes(needle)
      || customer.preferredPaymentMethod.toLowerCase().includes(needle)
      || (customer.phone ?? '').toLowerCase().includes(needle)
      || (customer.email ?? '').toLowerCase().includes(needle)
    )
  })
})

const totalCustomers = computed(() => activeCustomerProfiles.value.length)
const previousTotalCustomers = computed(() => previousActiveCustomerProfiles.value.length)
const repeatCustomers = computed(() => activeCustomerProfiles.value.filter((customer) => customer.visits > 1).length)
const previousRepeatCustomers = computed(() => previousActiveCustomerProfiles.value.filter((customer) => customer.visits > 1).length)
const guestOrders = computed(() => periodOrders.value.filter((order) => isGuestOrder(order)).length)
const previousGuestOrders = computed(() => priorOrders.value.filter((order) => isGuestOrder(order)).length)
const totalCustomerRevenue = computed(() => activeCustomerProfiles.value.reduce((sum, customer) => sum + customer.totalSpendCents, 0))
const averageLifetimeValue = computed(() =>
  totalCustomers.value > 0 ? Math.round(totalCustomerRevenue.value / totalCustomers.value) : 0,
)
const previousAverageLifetimeValue = computed(() => {
  const total = previousActiveCustomerProfiles.value.reduce((sum, customer) => sum + customer.totalSpendCents, 0)
  return previousTotalCustomers.value > 0 ? Math.round(total / previousTotalCustomers.value) : 0
})

const tierBreakdown = computed(() => {
  const tiers: CustomerTier[] = ['VIP', 'Regular', 'Occasional', 'New']
  return tiers.map((tier) => {
    const count = activeCustomerProfiles.value.filter((customer) => customer.tier === tier).length
    const percentage = totalCustomers.value > 0 ? Math.round((count / totalCustomers.value) * 100) : 0
    return { tier, count, percentage }
  })
})

const topCustomers = computed(() => activeCustomerProfiles.value.slice(0, 5))
const recentCustomerOrders = computed(() =>
  periodOrders.value
    .filter((order) => !isGuestOrder(order))
    .slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 8)
    .map((order) => ({
      id: order.id,
      ticketNumber: order.ticketNumber,
      customerName: order.customerName,
      amount: order.totalCents,
      createdAt: order.createdAt,
    })),
)

const rangeCaption = computed(() => {
  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
  if (range.value === 'today') {
    return formatter.format(now)
  }

  const endForCaption = new Date(bounds.value.end.getTime() - 86400000)
  return `${formatter.format(bounds.value.start)} - ${formatter.format(endForCaption)}`
})

const previousRangeCaption = computed(() => {
  if (range.value === 'all') {
    return ''
  }

  const formatter = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric' })
  if (range.value === 'today') {
    return `vs ${formatter.format(bounds.value.prevStart)}`
  }

  const endForCaption = new Date(bounds.value.prevEnd.getTime() - 86400000)
  return `vs ${formatter.format(bounds.value.prevStart)} - ${formatter.format(endForCaption)}`
})
</script>

<template>
  <div class="customers-page">
    <section class="customers-header">
      <div>
        <h1 class="customers-title">Customers</h1>
        <p class="customers-copy">
          Guest stays the default for walk-ins. Add named customers only when a store wants loyalty, contact details,
          or a cleaner view of repeat business.
        </p>
      </div>

      <div class="customers-toolbar">
        <button class="primary-button customers-create" type="button" @click="openCreateForm">
          <Plus :size="15" />
          <span>New customer</span>
        </button>
        <div class="customers-range">
          <span class="customers-range__text">{{ rangeCaption }}</span>
          <RangeSelector v-model="range" />
        </div>
      </div>
    </section>

    <LoyaltySettingsCard />

    <section v-if="showForm" class="customers-form-card">
      <div class="customers-form-card__head">
        <div>
          <p class="customers-form-card__eyebrow">{{ editingCustomerId ? 'Edit customer' : 'Add customer' }}</p>
          <h2>{{ editingCustomerId ? 'Update saved customer details' : 'Save a named customer for future checkouts' }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="Close customer form" @click="closeForm">
          <X :size="16" />
        </button>
      </div>

      <div class="customers-form-grid">
        <input v-model="formName" class="sheet-input" type="text" placeholder="Customer name" />
        <input v-model="formPhone" class="sheet-input" type="tel" placeholder="Phone number (optional)" />
        <input v-model="formEmail" class="sheet-input" type="email" placeholder="Email (optional)" />
        <textarea v-model="formNotes" class="sheet-input customers-notes" rows="3" placeholder="Notes (optional)" />
        <label class="customers-consent">
          <input v-model="formConsent" type="checkbox" />
          <span>
            <strong>Enrol in points</strong>
            <small>
              Only with the customer's agreement: the shop keeps their name and number to track their points.
            </small>
          </span>
        </label>
        <CustomerPointsPanel
          v-if="editingCustomerId && formConsent"
          :key="editingCustomerId"
          :customer-id="editingCustomerId"
          class="customers-points"
        />
      </div>

      <p v-if="formError" class="customers-form-error">{{ formError }}</p>

      <div class="customers-form-actions">
        <button class="secondary-button" type="button" @click="closeForm">Cancel</button>
        <button class="primary-button" type="button" :disabled="savingCustomer" @click="saveCustomer">
          {{ savingCustomer ? 'Saving...' : editingCustomerId ? 'Save changes' : 'Create customer' }}
        </button>
      </div>
    </section>

    <section class="customers-kpis">
      <MetricCard
        label="Active Named Customers"
        :value="totalCustomers.toLocaleString('en-PH')"
        :delta="delta(totalCustomers, previousTotalCustomers)"
        :compare-label="previousRangeCaption"
      />
      <MetricCard
        label="Repeat Customers"
        :value="repeatCustomers.toLocaleString('en-PH')"
        :delta="delta(repeatCustomers, previousRepeatCustomers)"
        :compare-label="previousRangeCaption"
      />
      <MetricCard
        label="Guest Orders"
        :value="guestOrders.toLocaleString('en-PH')"
        :delta="delta(guestOrders, previousGuestOrders)"
        :compare-label="previousRangeCaption"
      />
      <MetricCard
        label="Average Lifetime Value"
        :value="formatCurrency(averageLifetimeValue)"
        :delta="delta(averageLifetimeValue, previousAverageLifetimeValue)"
        :compare-label="previousRangeCaption"
      />
    </section>

    <section class="customers-grid">
      <ChartCard title="Customer Mix" summary="Named customers only. Guest checkouts stay outside loyalty segmentation.">
        <div class="customers-tier-list">
          <div v-for="segment in tierBreakdown" :key="segment.tier" class="customers-tier-row">
            <div class="customers-tier-row__head">
              <span class="customers-tier-row__label">{{ segment.tier }}</span>
              <span class="customers-tier-row__stats">{{ segment.count }} / {{ segment.percentage }}%</span>
            </div>
            <div class="customers-tier-row__track">
              <div class="customers-tier-row__fill" :style="{ width: `${segment.percentage}%` }" />
            </div>
          </div>
        </div>
      </ChartCard>

      <ChartCard title="Top Customers" summary="Highest-spending named customers in the selected period.">
        <div v-if="topCustomers.length" class="customers-leaderboard">
          <div v-for="customer in topCustomers" :key="customer.id" class="customers-leaderboard__row">
            <div class="customers-avatar">{{ customer.initials }}</div>
            <div class="customers-leaderboard__body">
              <strong>{{ customer.name }}</strong>
              <p>{{ customer.visits }} visits / Favorite: {{ customer.favoriteItem }}</p>
            </div>
            <div class="customers-leaderboard__amount">{{ formatCurrency(customer.totalSpendCents) }}</div>
          </div>
        </div>
        <div v-else class="customers-empty">
          Named customers will appear here once a sale is attached to someone other than Guest.
        </div>
      </ChartCard>
    </section>

    <section class="customers-grid customers-grid--tables">
      <ChartCard title="Customer Directory" summary="Create, search, and reuse named customers while keeping walk-ins as Guest.">
        <div class="customers-table__head">
          <div class="customers-table__filters">
            <label class="customers-search">
              <Search :size="16" />
              <input v-model="searchQuery" type="search" placeholder="Search customer, item, payment, or contact" />
            </label>

            <AutocompleteSelect
              v-model="tierFilter"
              class="customers-select"
              label="Filter customers by tier"
              :options="tierFilterOptions"
            />
          </div>

          <span class="customers-table__note">
            {{ store.customers.length }} saved contacts, {{ totalCustomers }} active named customers, and {{ guestOrders }} guest orders in this range.
          </span>
        </div>

        <table v-if="filteredCustomers.length" class="customers-table">
          <thead>
            <tr>
              <th>Customer</th>
              <th>Contact</th>
              <th>Visits</th>
              <th>Avg Spend</th>
              <th>Favorite Item</th>
              <th>Last Visit</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="customer in filteredCustomers" :key="customer.id">
              <td>
                <div class="customers-table__identity">
                  <span class="customers-avatar customers-avatar--sm">{{ customer.initials || 'C' }}</span>
                  <div>
                    <strong>{{ customer.name }}</strong>
                    <p>
                      {{ customer.tier }} / {{ customer.preferredPaymentMethod }}
                      <template v-if="customer.customerId && balances[customer.customerId] != null">
                        / {{ balances[customer.customerId] }} pts
                      </template>
                    </p>
                  </div>
                </div>
              </td>
              <td>{{ contactLine(customer) }}</td>
              <td>{{ customer.visits }}</td>
              <td>{{ formatCurrency(customer.averageSpendCents) }}</td>
              <td>{{ customer.favoriteItem }}</td>
              <td>{{ visitLabel(customer) }}</td>
              <td>
                <div class="customers-actions">
                  <button
                    class="icon-button icon-button--sm"
                    type="button"
                    :disabled="!customer.hasRecord"
                    :aria-label="`Edit ${customer.name}`"
                    @click="openEditForm(customer)"
                  >
                    <Pencil :size="14" />
                  </button>
                  <button
                    class="icon-button icon-button--sm"
                    type="button"
                    :disabled="!customer.hasRecord"
                    :aria-label="`Delete ${customer.name}`"
                    @click="removeCustomer(customer)"
                  >
                    <Trash2 :size="14" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-else class="customers-empty">
          No customers match the current filters.
        </div>
      </ChartCard>

      <ChartCard title="Recent Customer Activity" summary="Latest named-customer activity from completed orders.">
        <div v-if="recentCustomerOrders.length" class="customers-activity">
          <div v-for="order in recentCustomerOrders" :key="order.id" class="customers-activity__row">
            <div class="customers-activity__icon">
              <Users :size="16" />
            </div>
            <div class="customers-activity__body">
              <strong>{{ order.customerName }}</strong>
              <p>Ticket #{{ order.ticketNumber }} / {{ formatCompactDate(order.createdAt) }}</p>
            </div>
            <div class="customers-activity__meta">
              <span>{{ formatCurrency(order.amount) }}</span>
            </div>
          </div>

          <RouterLink class="customers-link" to="/orders">
            <span>View orders</span>
            <ChevronRight :size="14" />
          </RouterLink>
        </div>
        <div v-else class="customers-empty">
          Named-customer activity will show up here after checkout starts using saved customers.
        </div>
      </ChartCard>
    </section>
  </div>
</template>

<style scoped>
.customers-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-5);
}

.customers-header,
.customers-kpis,
.customers-grid,
.customers-form-card {
  display: grid;
  gap: var(--space-4);
}

.customers-header {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
}

.customers-title {
  margin: 0;
  font: var(--type-title1);
}

.customers-copy {
  max-width: 68ch;
  margin: var(--space-2) 0 0;
  color: var(--text-secondary);
}

.customers-toolbar,
.customers-range,
.customers-table__head,
.customers-link,
.customers-form-card__head,
.customers-form-actions,
.customers-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.customers-toolbar,
.customers-range {
  flex-wrap: wrap;
}

.customers-range {
  justify-content: flex-end;
}

.customers-create {
  min-height: 40px;
  padding: 0 var(--space-4);
}

.customers-range__text,
.customers-table__note,
.customers-form-card__eyebrow {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.customers-form-card {
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-raised);
}

.customers-form-card__head h2 {
  margin: var(--space-1) 0 0;
  font: var(--type-title3);
}

.customers-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.customers-notes {
  grid-column: 1 / -1;
  resize: vertical;
}

.customers-form-error {
  margin: 0;
  color: var(--danger-600, #b42318);
  font: var(--type-caption);
}

.customers-kpis {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.customers-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.customers-grid--tables {
  align-items: start;
}

.customers-tier-list,
.customers-leaderboard,
.customers-activity {
  display: grid;
  gap: var(--space-3);
}

.customers-tier-row {
  display: grid;
  gap: var(--space-2);
}

.customers-tier-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.customers-tier-row__label {
  font-weight: 600;
}

.customers-tier-row__stats {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.customers-tier-row__track {
  height: 8px;
  border-radius: 999px;
  background: var(--surface-muted);
  overflow: hidden;
}

.customers-tier-row__fill {
  height: 100%;
  border-radius: inherit;
  background: var(--accent-primary);
}

.customers-leaderboard__row,
.customers-activity__row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
}

.customers-avatar,
.customers-activity__icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--accent-primary) 14%, var(--surface-muted));
  color: var(--accent-primary);
  font-weight: 700;
}

.customers-avatar--sm {
  width: 34px;
  height: 34px;
  font-size: 0.8rem;
}

.customers-leaderboard__body,
.customers-activity__body {
  min-width: 0;
}

.customers-leaderboard__body strong,
.customers-activity__body strong,
.customers-table__identity strong {
  display: block;
}

.customers-leaderboard__body p,
.customers-activity__body p,
.customers-table__identity p {
  margin: var(--space-1) 0 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.customers-leaderboard__amount,
.customers-activity__meta span {
  font-weight: 600;
}

.customers-table__head {
  flex-wrap: wrap;
}

.customers-table__filters {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.customers-search {
  min-width: 280px;
  flex: 1 1 320px;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  min-height: 44px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-raised);
}

.customers-search input {
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
}

.customers-search input:focus {
  outline: none;
}

.customers-select {
  width: 100%;
  min-width: 160px;
}

.customers-table {
  width: 100%;
  border-collapse: collapse;
}

.customers-table th,
.customers-table td {
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
  vertical-align: middle;
}

.customers-table th {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.customers-table__identity {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.customers-link {
  justify-content: flex-start;
  color: var(--accent-primary);
  text-decoration: none;
  font-weight: 600;
}

.customers-empty {
  color: var(--text-secondary);
  font: var(--type-body);
}

@media (max-width: 1080px) {
  .customers-kpis,
  .customers-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 780px) {
  .customers-header,
  .customers-grid,
  .customers-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .customers-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .customers-copy {
    display: none;
  }

  .customers-toolbar,
  .customers-range,
  .customers-table__head,
  .customers-table__filters {
    align-items: stretch;
  }

  .customers-range {
    justify-content: flex-start;
  }

  .customers-search,
  .customers-select {
    min-width: 0;
    width: 100%;
  }

  .customers-table {
    display: block;
    overflow-x: auto;
  }
}
/* Enrolment in points: a checkbox with the reason it is asked. */
.customers-consent {
  grid-column: 1 / -1;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 13px;
  color: var(--text-primary);
}

.customers-consent small {
  display: block;
  margin-top: 2px;
  color: var(--text-secondary);
}

.customers-points {
  grid-column: 1 / -1;
}
</style>
