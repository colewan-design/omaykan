<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  ArrowDown,
  ArrowRight,
  ArrowUp,
  ArrowUpDown,
  Award,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  Crown,
  Ellipsis,
  Info,
  Mail,
  Phone,
  Plus,
  Repeat,
  Search,
  ShoppingBag,
  SquarePen,
  TrendingUp,
  Trash2,
  Upload,
  UserPlus,
  Users,
  Wallet,
  X,
} from '@lucide/vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import CustomerPointsPanel from '@pos/core/components/CustomerPointsPanel.vue'
import LoyaltySettingsCard from '@pos/core/components/LoyaltySettingsCard.vue'
import { getPosRepository } from '@pos/core/services/runtime'
import RangeSelector, { type Range } from '@pos/core/components/RangeSelector.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { formatCurrency, guestCustomerName, type Customer, type OrderSummary } from '@pos/shared/index'

const store = usePosStore()

/** Points by customer id, from the server. Empty when it cannot be reached. */
const balances = ref<Record<string, number>>({})

onMounted(async () => {
  if (!store.isReady) {
    void store.initialize()
  }
  balances.value = await getPosRepository().loadLoyaltyBalances()
})

type CustomerTier = 'VIP' | 'Regular' | 'Occasional' | 'New'
type Audience = 'all' | 'points' | 'repeat' | 'inactive'
type SortKey = 'name' | 'orders' | 'last' | 'spend'

const tierFilterOptions: { value: 'all' | CustomerTier; label: string }[] = [
  { value: 'all',        label: 'All tiers' },
  { value: 'VIP',        label: 'VIP' },
  { value: 'Regular',    label: 'Regular' },
  { value: 'Occasional', label: 'Occasional' },
  { value: 'New',        label: 'New' },
]

const audienceOptions: { value: Audience; label: string }[] = [
  { value: 'all',      label: 'All customers' },
  { value: 'points',   label: 'Points members' },
  { value: 'repeat',   label: 'Repeat buyers' },
  { value: 'inactive', label: 'Inactive 30+ days' },
]

const ROWS_PER_PAGE = 8
const INACTIVE_DAYS = 30

const range = ref<Range>('month')
const searchQuery = ref('')
const tierFilter = ref<'all' | CustomerTier>('all')
const audience = ref<Audience>('all')
const sortKey = ref<SortKey>('last')
const sortAscending = ref(false)
const currentPage = ref(1)
const selectedIds = ref<Set<string>>(new Set())
const openRowMenu = ref<string | null>(null)
const directoryMenuOpen = ref(false)
const directoryEl = ref<HTMLElement | null>(null)
const importInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const importNotice = ref('')

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
  enrolled: boolean
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

function inBounds(value: string, start: Date, end: Date) {
  const at = new Date(value)
  return at >= start && at < end
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
    enrolled: Boolean(customer?.loyaltyConsentAt),
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
    value: `${Math.abs(percentage).toFixed(percentage % 1 === 0 ? 0 : 1)}%`,
    positive: percentage >= 0,
  }
}

const relativeTime = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
const dayFormat = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })

/** "2 hours ago", falling back to a date once it is older than a month. */
function timeAgo(value: string) {
  const at = new Date(value).getTime()
  // A till whose clock runs fast should not report a sale as "tomorrow".
  const seconds = Math.min(Math.round((at - now.getTime()) / 1000), 0)
  const steps: [number, Intl.RelativeTimeFormatUnit][] = [
    [60, 'second'],
    [3600, 'minute'],
    [86400, 'hour'],
    [604800, 'day'],
    [2592000, 'week'],
  ]

  let previousLimit = 1
  for (const [limit, unit] of steps) {
    if (Math.abs(seconds) < limit) {
      return relativeTime.format(Math.round(seconds / previousLimit), unit)
    }
    previousLimit = limit
  }

  return dayFormat.format(new Date(value))
}

function lastOrderLabel(profile: CustomerProfile) {
  return profile.lastVisitAt ? dayFormat.format(new Date(profile.lastVisitAt)) : 'No orders yet'
}

function contactLine(profile: CustomerProfile) {
  return profile.phone || profile.email || 'Walk-in, no contact saved'
}

/**
 * A stable colour per person so the same face keeps the same tile between
 * renders. Decoration, not data: every row states its tier in words beside it.
 */
function avatarTone(profile: CustomerProfile) {
  let hash = 0
  for (const char of profile.id) {
    hash = (hash * 31 + char.charCodeAt(0)) % 997
  }
  return `cx-avatar--${hash % 6}`
}

const bounds = computed(() => getBounds(range.value))
const activeOrders = computed(() => store.orders.filter((order) => !order.voidedAt))
const periodOrders = computed(() =>
  activeOrders.value.filter((order) => inBounds(order.createdAt, bounds.value.start, bounds.value.end)),
)
const priorOrders = computed(() =>
  range.value === 'all'
    ? []
    : activeOrders.value.filter((order) => inBounds(order.createdAt, bounds.value.prevStart, bounds.value.prevEnd)),
)

const customerProfiles = computed(() => buildProfiles(periodOrders.value, store.customers))
const previousCustomerProfiles = computed(() => buildProfiles(priorOrders.value, store.customers))
const activeCustomerProfiles = computed(() => customerProfiles.value.filter((customer) => customer.visits > 0))
const previousActiveCustomerProfiles = computed(() => previousCustomerProfiles.value.filter((customer) => customer.visits > 0))
/** Every order ever, for the questions that are about a lapse, not a period. */
const lifetimeProfiles = computed(() => buildProfiles(activeOrders.value, store.customers))

/** Lapsed: bought at some point, but nothing in the last 30 days. */
const inactiveIds = computed(() => {
  const cutoff = now.getTime() - INACTIVE_DAYS * 86400000
  const ids = new Set<string>()
  for (const customer of lifetimeProfiles.value) {
    if (customer.lastVisitAt && new Date(customer.lastVisitAt).getTime() < cutoff) {
      ids.add(customer.id)
    }
  }
  return ids
})

function matchesAudience(customer: CustomerProfile) {
  switch (audience.value) {
    case 'points':
      return customer.enrolled
    case 'repeat':
      return customer.visits > 1
    case 'inactive':
      return inactiveIds.value.has(customer.id)
    default:
      return true
  }
}

const filteredCustomers = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()

  return customerProfiles.value.filter((customer) => {
    if (tierFilter.value !== 'all' && customer.tier !== tierFilter.value) {
      return false
    }

    if (!matchesAudience(customer)) {
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

const sortedCustomers = computed(() => {
  const direction = sortAscending.value ? 1 : -1
  return filteredCustomers.value.slice().sort((a, b) => {
    switch (sortKey.value) {
      case 'name':
        return a.name.localeCompare(b.name) * direction
      case 'orders':
        return (a.visits - b.visits) * direction || a.name.localeCompare(b.name)
      case 'spend':
        return (a.totalSpendCents - b.totalSpendCents) * direction || a.name.localeCompare(b.name)
      case 'last':
        return a.lastVisitAt.localeCompare(b.lastVisitAt) * direction || a.name.localeCompare(b.name)
    }
  })
})

const pageCount = computed(() => Math.max(1, Math.ceil(sortedCustomers.value.length / ROWS_PER_PAGE)))
const pageCustomers = computed(() =>
  sortedCustomers.value.slice((currentPage.value - 1) * ROWS_PER_PAGE, currentPage.value * ROWS_PER_PAGE),
)
const rangeStart = computed(() => (sortedCustomers.value.length === 0 ? 0 : (currentPage.value - 1) * ROWS_PER_PAGE + 1))
const rangeEnd = computed(() => Math.min(currentPage.value * ROWS_PER_PAGE, sortedCustomers.value.length))

/** Page numbers with gaps, so 50 pages still fit on one line. */
const pageNumbers = computed<(number | null)[]>(() => {
  const total = pageCount.value
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1)
  }

  const wanted = new Set([1, total, currentPage.value, currentPage.value - 1, currentPage.value + 1])
  const pages = Array.from(wanted).filter((page) => page >= 1 && page <= total).sort((a, b) => a - b)
  const withGaps: (number | null)[] = []
  let previous = 0
  for (const page of pages) {
    if (previous && page - previous > 1) {
      withGaps.push(null)
    }
    withGaps.push(page)
    previous = page
  }
  return withGaps
})

const allPageSelected = computed(
  () => pageCustomers.value.length > 0 && pageCustomers.value.every((customer) => selectedIds.value.has(customer.id)),
)
const selectedProfiles = computed(() => sortedCustomers.value.filter((customer) => selectedIds.value.has(customer.id)))

watch([searchQuery, tierFilter, audience, range], () => {
  currentPage.value = 1
})

watch(pageCount, (total) => {
  if (currentPage.value > total) {
    currentPage.value = total
  }
})

function toggleSort(key: SortKey) {
  if (sortKey.value === key) {
    sortAscending.value = !sortAscending.value
    return
  }
  sortKey.value = key
  // Names read best A-Z; everything else is a "most first" question.
  sortAscending.value = key === 'name'
}

function sortState(key: SortKey) {
  if (sortKey.value !== key) return 'none'
  return sortAscending.value ? 'ascending' : 'descending'
}

function toggleSelect(id: string) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function togglePageSelection() {
  const next = new Set(selectedIds.value)
  if (allPageSelected.value) {
    for (const customer of pageCustomers.value) next.delete(customer.id)
  } else {
    for (const customer of pageCustomers.value) next.add(customer.id)
  }
  selectedIds.value = next
}

function clearSelection() {
  selectedIds.value = new Set()
}

function selectAllFiltered() {
  selectedIds.value = new Set(sortedCustomers.value.map((customer) => customer.id))
}

function clearFilters() {
  searchQuery.value = ''
  tierFilter.value = 'all'
  audience.value = 'all'
  currentPage.value = 1
}

function showDirectory() {
  clearFilters()
  directoryEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
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
        createdAt: existing?.createdAt ?? new Date().toISOString(),
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
  const next = new Set(selectedIds.value)
  next.delete(profile.id)
  selectedIds.value = next
}

async function removeSelected() {
  const removable = selectedProfiles.value.filter((customer) => customer.hasRecord && customer.customerId)
  if (removable.length === 0) {
    return
  }

  const what = removable.length === 1 ? removable[0].name : `${removable.length} customers`
  if (!window.confirm(`Remove ${what} from saved customers? Existing orders will keep their history.`)) {
    return
  }

  for (const customer of removable) {
    await store.removeCustomer(customer.customerId as string)
  }
  clearSelection()
}

/* ── Import and export ──────────────────────────────────────────────────── */

/** Minimal RFC 4180: quoted fields, doubled quotes inside them, CRLF or LF. */
function parseCsv(text: string) {
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false

  for (let index = 0; index < text.length; index++) {
    const char = text[index]

    if (quoted) {
      if (char === '"' && text[index + 1] === '"') {
        field += '"'
        index++
      } else if (char === '"') {
        quoted = false
      } else {
        field += char
      }
      continue
    }

    if (char === '"') {
      quoted = true
    } else if (char === ',') {
      row.push(field)
      field = ''
    } else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[index + 1] === '\n') index++
      row.push(field)
      rows.push(row)
      row = []
      field = ''
    } else {
      field += char
    }
  }

  if (field || row.length) {
    row.push(field)
    rows.push(row)
  }

  return rows.filter((entry) => entry.some((value) => value.trim()))
}

function columnIndexes(header: string[]) {
  const find = (...names: string[]) =>
    header.findIndex((cell) => names.includes(cell.trim().toLowerCase()))

  const name = find('name', 'customer', 'customer name', 'full name')
  if (name < 0) {
    // No header the file admits to: read the columns in the order the export
    // writes them.
    return { headerRow: false, name: 0, phone: 1, email: 2, notes: 3 }
  }

  return {
    headerRow: true,
    name,
    phone: find('phone', 'mobile', 'contact', 'phone number'),
    email: find('email', 'e-mail', 'email address'),
    notes: find('notes', 'note', 'remarks'),
  }
}

async function importCustomers(file: File) {
  importing.value = true
  importNotice.value = ''

  try {
    const rows = parseCsv(await file.text())
    if (rows.length === 0) {
      importNotice.value = 'That file had no rows.'
      return
    }

    const columns = columnIndexes(rows[0])
    const body = columns.headerRow ? rows.slice(1) : rows
    const taken = new Set(store.customers.map((customer) => customer.name.trim().toLowerCase()))
    let added = 0
    let skipped = 0

    for (const entry of body) {
      const name = (entry[columns.name] ?? '').trim()
      if (!name || taken.has(name.toLowerCase())) {
        skipped++
        continue
      }

      taken.add(name.toLowerCase())
      await store.createCustomer({
        name,
        phone: columns.phone >= 0 ? (entry[columns.phone] ?? '').trim() : '',
        email: columns.email >= 0 ? (entry[columns.email] ?? '').trim() : '',
        notes: columns.notes >= 0 ? (entry[columns.notes] ?? '').trim() : '',
        // Consent is given by the person, never by a spreadsheet: imported
        // contacts start outside the points scheme.
        loyaltyConsentAt: null,
      })
      added++
    }

    importNotice.value = added
      ? `Imported ${added} ${added === 1 ? 'customer' : 'customers'}${skipped ? `, skipped ${skipped} already saved or unnamed` : ''}.`
      : 'Nothing to import — every row was blank or already saved.'
  } catch (error) {
    importNotice.value = error instanceof Error ? error.message : 'That file could not be read.'
  } finally {
    importing.value = false
  }
}

function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    void importCustomers(file)
  }
  input.value = ''
}

function csvCell(value: string | number) {
  const text = String(value)
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function exportCustomers(rows: CustomerProfile[]) {
  const header = ['Name', 'Phone', 'Email', 'Notes', 'Tier', 'Orders', 'Total spend', 'Last order']
  const body = rows.map((customer) => [
    customer.name,
    customer.phone ?? '',
    customer.email ?? '',
    customer.notes ?? '',
    customer.tier,
    customer.visits,
    (customer.totalSpendCents / 100).toFixed(2),
    customer.lastVisitAt ? new Date(customer.lastVisitAt).toISOString().slice(0, 10) : '',
  ])

  const csv = [header, ...body].map((entry) => entry.map(csvCell).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `customers-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

/* ── Menus ──────────────────────────────────────────────────────────────── */

function closeMenus() {
  openRowMenu.value = null
  directoryMenuOpen.value = false
}

onMounted(() => document.addEventListener('click', closeMenus))
onUnmounted(() => document.removeEventListener('click', closeMenus))

function toggleRowMenu(id: string) {
  directoryMenuOpen.value = false
  openRowMenu.value = openRowMenu.value === id ? null : id
}

function toggleDirectoryMenu() {
  openRowMenu.value = null
  directoryMenuOpen.value = !directoryMenuOpen.value
}

/* ── Headline numbers ───────────────────────────────────────────────────── */

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

const kpis = computed(() => [
  {
    key: 'named',
    icon: Users,
    label: 'Active Named Customers',
    value: totalCustomers.value.toLocaleString('en-PH'),
    delta: delta(totalCustomers.value, previousTotalCustomers.value),
    help: 'People with a name on at least one sale in this period. Guest checkouts are counted separately.',
  },
  {
    key: 'repeat',
    icon: Repeat,
    label: 'Repeat Customers',
    value: repeatCustomers.value.toLocaleString('en-PH'),
    delta: delta(repeatCustomers.value, previousRepeatCustomers.value),
    help: 'Named customers who bought more than once inside this period.',
  },
  {
    key: 'guest',
    icon: ShoppingBag,
    label: 'Guest Orders',
    value: guestOrders.value.toLocaleString('en-PH'),
    delta: delta(guestOrders.value, previousGuestOrders.value),
    help: 'Walk-in sales rung up as Guest. A high share is normal — attaching a name is optional.',
  },
  {
    key: 'value',
    icon: Wallet,
    label: 'Average Lifetime Value',
    value: formatCurrency(averageLifetimeValue.value),
    delta: delta(averageLifetimeValue.value, previousAverageLifetimeValue.value),
    help: 'Total spend by named customers in this period, divided by how many of them there were.',
  },
])

const tierBreakdown = computed(() => {
  const tiers: CustomerTier[] = ['VIP', 'Regular', 'Occasional', 'New']
  return tiers.map((tier) => {
    const count = activeCustomerProfiles.value.filter((customer) => customer.tier === tier).length
    const percentage = totalCustomers.value > 0 ? Math.round((count / totalCustomers.value) * 100) : 0
    return { tier, count, percentage }
  })
})

/**
 * The mix as a ring. Arcs are drawn from the top clockwise with a 2px gap
 * between them, and every slice is also named, counted and given its share in
 * the list beside it — colour is never the only thing carrying identity.
 */
const RING = { radius: 44, width: 17, gap: 2 }
const circumference = 2 * Math.PI * RING.radius

const donutSegments = computed(() => {
  const shown = tierBreakdown.value.filter((segment) => segment.count > 0)
  let used = 0
  return shown.map((segment) => {
    const share = totalCustomers.value > 0 ? segment.count / totalCustomers.value : 0
    const length = share * circumference
    const drawn = shown.length > 1 ? Math.max(length - RING.gap, 0.6) : length
    const dash = `${drawn} ${circumference - drawn}`
    const offset = -used
    used += length
    return { ...segment, dash, offset }
  })
})

const topCustomers = computed(() => activeCustomerProfiles.value.slice(0, 5))

interface ActivityEntry {
  id: string
  kind: 'purchase' | 'joined' | 'updated'
  name: string
  detail: string
  at: string
}

const recentActivity = computed<ActivityEntry[]>(() => {
  const entries: ActivityEntry[] = []

  for (const order of periodOrders.value) {
    if (isGuestOrder(order)) continue
    entries.push({
      id: `order-${order.id}`,
      kind: 'purchase',
      name: order.customerName,
      detail: `Completed a purchase of ${formatCurrency(order.totalCents)}`,
      at: order.createdAt,
    })
  }

  for (const customer of store.customers) {
    if (inBounds(customer.createdAt, bounds.value.start, bounds.value.end)) {
      entries.push({
        id: `new-${customer.id}`,
        kind: 'joined',
        name: customer.name,
        detail: customer.loyaltyConsentAt ? 'Joined and enrolled in points' : 'New customer registered',
        at: customer.createdAt,
      })
    } else if (
      customer.updatedAt !== customer.createdAt
      && inBounds(customer.updatedAt, bounds.value.start, bounds.value.end)
    ) {
      entries.push({
        id: `edit-${customer.id}`,
        kind: 'updated',
        name: customer.name,
        detail: 'Updated contact information',
        at: customer.updatedAt,
      })
    }
  }

  return entries.sort((a, b) => b.at.localeCompare(a.at)).slice(0, 6)
})

const activityIcon = { purchase: ShoppingBag, joined: UserPlus, updated: SquarePen } as const

const pointsMembers = computed(() => store.customers.filter((customer) => customer.loyaltyConsentAt).length)
const pointsShare = computed(() =>
  store.customers.length > 0 ? Math.round((pointsMembers.value / store.customers.length) * 100) : 0,
)
const newCustomers = computed(
  () => store.customers.filter((customer) => inBounds(customer.createdAt, bounds.value.start, bounds.value.end)).length,
)
const potentialRegulars = computed(() => {
  const buyers = lifetimeProfiles.value.filter((customer) => customer.visits > 0)
  if (buyers.length === 0) return 0
  const averageSpend = buyers.reduce((sum, customer) => sum + customer.averageSpendCents, 0) / buyers.length
  // Two or more visits and a basket above the house average, but not yet high
  // enough to have earned a tier of their own.
  return buyers.filter(
    (customer) => customer.visits >= 2 && customer.tier !== 'VIP' && customer.averageSpendCents >= averageSpend,
  ).length
})

const insights = computed(() => [
  {
    key: 'points',
    icon: Award,
    label: 'Points members',
    value: pointsMembers.value.toLocaleString('en-PH'),
    hint: store.customers.length ? `${pointsShare.value}% of saved customers` : 'No saved customers yet',
  },
  {
    key: 'new',
    icon: UserPlus,
    label: 'New this period',
    value: newCustomers.value.toLocaleString('en-PH'),
    hint: 'Saved while this range was open',
  },
  {
    key: 'inactive',
    icon: Clock,
    label: `Inactive ${INACTIVE_DAYS}+ days`,
    value: inactiveIds.value.size.toLocaleString('en-PH'),
    hint: 'May need a nudge',
  },
  {
    key: 'regulars',
    icon: TrendingUp,
    label: 'Potential regulars',
    value: potentialRegulars.value.toLocaleString('en-PH'),
    hint: '2+ orders, above-average basket',
  },
])

const rangeCaption = computed(() => {
  if (range.value === 'all') {
    return 'All time'
  }
  if (range.value === 'today') {
    return dayFormat.format(now)
  }

  const endForCaption = new Date(bounds.value.end.getTime() - 86400000)
  return `${dayFormat.format(bounds.value.start)} - ${dayFormat.format(endForCaption)}`
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

const directorySummary = computed(
  () =>
    `${store.customers.length} saved ${store.customers.length === 1 ? 'contact' : 'contacts'}`
    + ` · ${totalCustomers.value} active in this range · ${guestOrders.value} guest orders`,
)
</script>

<template>
  <div class="cx-page">
    <header class="cx-header">
      <div class="cx-header__copy">
        <h1 class="cx-title">Customers</h1>
        <p class="cx-subtitle">
          Build lasting relationships with your customers. Track loyalty, find your top buyers, and get insights to
          grow repeat business.
        </p>
      </div>

      <div class="cx-header__actions">
        <button class="primary-button cx-new" type="button" @click="openCreateForm">
          <Plus :size="16" aria-hidden="true" />
          <span>New customer</span>
        </button>
        <div class="cx-range">
          <span class="cx-range__text">{{ rangeCaption }}</span>
          <RangeSelector v-model="range" />
        </div>
      </div>
    </header>

    <LoyaltySettingsCard />

    <section v-if="showForm" class="cx-card cx-form">
      <header class="cx-form__head">
        <div>
          <p class="cx-form__eyebrow">{{ editingCustomerId ? 'Edit customer' : 'Add customer' }}</p>
          <h2>{{ editingCustomerId ? 'Update saved customer details' : 'Save a named customer for future checkouts' }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="Close customer form" @click="closeForm">
          <X :size="16" />
        </button>
      </header>

      <div class="cx-form__grid">
        <input v-model="formName" class="sheet-input" type="text" placeholder="Customer name" />
        <input v-model="formPhone" class="sheet-input" type="tel" placeholder="Phone number (optional)" />
        <input v-model="formEmail" class="sheet-input" type="email" placeholder="Email (optional)" />
        <textarea v-model="formNotes" class="sheet-input cx-form__notes" rows="3" placeholder="Notes (optional)" />
        <label class="cx-form__consent">
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
          class="cx-form__points"
        />
      </div>

      <p v-if="formError" class="cx-form__error" role="alert">{{ formError }}</p>

      <div class="cx-form__actions">
        <button class="secondary-button" type="button" @click="closeForm">Cancel</button>
        <button class="primary-button" type="button" :disabled="savingCustomer" @click="saveCustomer">
          {{ savingCustomer ? 'Saving…' : editingCustomerId ? 'Save changes' : 'Create customer' }}
        </button>
      </div>
    </section>

    <section class="cx-kpis" aria-label="Customer summary">
      <article v-for="kpi in kpis" :key="kpi.key" class="cx-card cx-kpi">
        <span class="cx-kpi__icon"><component :is="kpi.icon" :size="23" aria-hidden="true" /></span>
        <div class="cx-kpi__body">
          <span class="cx-kpi__label">{{ kpi.label }}</span>
          <strong class="cx-kpi__value">{{ kpi.value }}</strong>
          <span v-if="kpi.delta" class="cx-kpi__delta" :class="kpi.delta.positive ? 'is-up' : 'is-down'">
            <component :is="kpi.delta.positive ? ArrowUp : ArrowDown" :size="13" aria-hidden="true" />
            <b>{{ kpi.delta.positive ? '+' : '−' }}{{ kpi.delta.value }}</b>
            <span>{{ previousRangeCaption }}</span>
          </span>
          <span v-else class="cx-kpi__delta cx-kpi__delta--none">No earlier period to compare</span>
        </div>
        <span class="cx-kpi__info" tabindex="0" role="img" :aria-label="kpi.help" :data-tip="kpi.help">
          <Info :size="15" aria-hidden="true" />
        </span>
      </article>
    </section>

    <div class="cx-columns">
      <div class="cx-column">
        <section ref="directoryEl" class="cx-card cx-panel cx-directory">
          <header class="cx-panel__head">
            <div>
              <h2>Customer Directory</h2>
              <p>Search and manage your customers, view their purchase history, and more.</p>
            </div>
            <div class="cx-directory__tools">
              <button class="table-action" type="button" :disabled="importing" @click="importInput?.click()">
                <Upload :size="14" aria-hidden="true" />
                {{ importing ? 'Importing…' : 'Import customers' }}
              </button>
              <input
                ref="importInput"
                class="cx-file"
                type="file"
                accept=".csv,text/csv"
                aria-label="Choose a CSV file of customers"
                @change="onImportFile"
              />
              <span class="cx-menu-wrap" @click.stop>
                <button
                  class="table-action table-action--icon"
                  type="button"
                  aria-label="More directory actions"
                  :aria-expanded="directoryMenuOpen"
                  @click="toggleDirectoryMenu"
                >
                  <Ellipsis :size="17" aria-hidden="true" />
                </button>
                <span v-if="directoryMenuOpen" class="cx-menu">
                  <button type="button" @click="exportCustomers(sortedCustomers); closeMenus()">
                    Export this list (CSV)
                  </button>
                  <button type="button" @click="selectAllFiltered(); closeMenus()">Select everything shown</button>
                  <button type="button" @click="clearFilters(); closeMenus()">Clear filters</button>
                </span>
              </span>
            </div>
          </header>

          <p v-if="importNotice" class="cx-notice" role="status">{{ importNotice }}</p>

          <div class="cx-filters">
            <label class="cx-search">
              <Search :size="16" aria-hidden="true" />
              <input
                v-model="searchQuery"
                type="search"
                placeholder="Search customer, item, payment, or contact…"
                aria-label="Search customers"
              />
            </label>
            <AutocompleteSelect
              v-model="tierFilter"
              class="cx-select"
              label="Filter customers by tier"
              :options="tierFilterOptions"
            />
            <AutocompleteSelect
              v-model="audience"
              class="cx-select"
              label="Filter customers by audience"
              :options="audienceOptions"
            />
          </div>

          <p class="cx-directory__summary">{{ directorySummary }}</p>

          <Transition name="bulk-bar">
            <div v-if="selectedIds.size > 0" class="bulk-bar" role="toolbar" aria-label="Bulk customer actions">
              <div class="bulk-bar__left">
                <span class="bulk-bar__count">{{ selectedIds.size }} selected</span>
                <button class="bulk-bar__link" type="button" @click="selectAllFiltered">
                  Select all {{ sortedCustomers.length }}
                </button>
                <button class="bulk-bar__link" type="button" @click="clearSelection">Clear</button>
              </div>
              <div class="bulk-bar__actions">
                <button class="table-action" type="button" @click="exportCustomers(selectedProfiles)">
                  Export selected
                </button>
                <button class="danger-button bulk-bar__delete" type="button" @click="removeSelected">
                  <Trash2 :size="14" aria-hidden="true" /> Remove
                </button>
              </div>
            </div>
          </Transition>

          <div v-if="sortedCustomers.length === 0" class="cx-empty">
            <Users :size="26" aria-hidden="true" />
            <strong>No customers match these filters</strong>
            <span>Guest stays the default for walk-ins — add a named customer when a shop wants loyalty or contact details.</span>
          </div>

          <template v-else>
            <div class="cx-table" role="table" aria-label="Customer directory">
              <div class="cx-table__row cx-table__row--head" role="row">
                <button
                  class="pcheck"
                  :class="{ 'pcheck--checked': allPageSelected }"
                  type="button"
                  role="checkbox"
                  :aria-checked="allPageSelected"
                  aria-label="Select the customers on this page"
                  @click="togglePageSelection"
                >
                  <Check v-if="allPageSelected" :size="12" />
                </button>
                <button class="cx-sort" type="button" role="columnheader" :aria-sort="sortState('name')" @click="toggleSort('name')">
                  Customer
                  <component :is="sortKey === 'name' ? (sortAscending ? ArrowUp : ArrowDown) : ArrowUpDown" :size="13" aria-hidden="true" />
                </button>
                <span role="columnheader">Tier</span>
                <button class="cx-sort" type="button" role="columnheader" :aria-sort="sortState('orders')" @click="toggleSort('orders')">
                  Orders
                  <component :is="sortKey === 'orders' ? (sortAscending ? ArrowUp : ArrowDown) : ArrowUpDown" :size="13" aria-hidden="true" />
                </button>
                <button class="cx-sort" type="button" role="columnheader" :aria-sort="sortState('last')" @click="toggleSort('last')">
                  Last Order
                  <component :is="sortKey === 'last' ? (sortAscending ? ArrowUp : ArrowDown) : ArrowUpDown" :size="13" aria-hidden="true" />
                </button>
                <button class="cx-sort cx-sort--end" type="button" role="columnheader" :aria-sort="sortState('spend')" @click="toggleSort('spend')">
                  Total Spend
                  <component :is="sortKey === 'spend' ? (sortAscending ? ArrowUp : ArrowDown) : ArrowUpDown" :size="13" aria-hidden="true" />
                </button>
                <span role="columnheader">Contact</span>
                <span role="columnheader" class="cx-table__actions-head">Actions</span>
              </div>

              <div
                v-for="customer in pageCustomers"
                :key="customer.id"
                class="cx-table__row"
                :class="{ 'cx-table__row--selected': selectedIds.has(customer.id) }"
                role="row"
              >
                <button
                  class="pcheck"
                  :class="{ 'pcheck--checked': selectedIds.has(customer.id) }"
                  type="button"
                  role="checkbox"
                  :aria-checked="selectedIds.has(customer.id)"
                  :aria-label="`${selectedIds.has(customer.id) ? 'Deselect' : 'Select'} ${customer.name}`"
                  @click="toggleSelect(customer.id)"
                >
                  <Check v-if="selectedIds.has(customer.id)" :size="12" />
                </button>

                <span class="cx-identity" role="cell">
                  <span class="cx-avatar" :class="avatarTone(customer)" aria-hidden="true">{{ customer.initials || 'C' }}</span>
                  <span class="cx-identity__copy">
                    <strong>{{ customer.name }}</strong>
                    <small>
                      {{ contactLine(customer) }}
                      <template v-if="customer.customerId && balances[customer.customerId] != null">
                        · {{ balances[customer.customerId] }} pts
                      </template>
                    </small>
                  </span>
                </span>

                <span role="cell">
                  <span class="cx-tier" :class="`cx-tier--${customer.tier.toLowerCase()}`">
                    <Crown v-if="customer.tier === 'VIP'" :size="12" aria-hidden="true" />
                    {{ customer.tier }}
                  </span>
                </span>

                <span class="cx-num" role="cell">{{ customer.visits }}</span>
                <span class="cx-date" role="cell">{{ lastOrderLabel(customer) }}</span>
                <strong class="cx-num cx-num--end" role="cell">{{ formatCurrency(customer.totalSpendCents) }}</strong>

                <span class="cx-contact" role="cell">
                  <a
                    v-if="customer.phone"
                    class="cx-contact__link"
                    :href="`tel:${customer.phone}`"
                    :aria-label="`Call ${customer.name}`"
                    :title="customer.phone"
                  >
                    <Phone :size="15" aria-hidden="true" />
                  </a>
                  <a
                    v-if="customer.email"
                    class="cx-contact__link"
                    :href="`mailto:${customer.email}`"
                    :aria-label="`Email ${customer.name}`"
                    :title="customer.email"
                  >
                    <Mail :size="15" aria-hidden="true" />
                  </a>
                  <span v-if="!customer.phone && !customer.email" class="cx-contact__none">—</span>
                </span>

                <span class="cx-table__actions" role="cell" @click.stop>
                  <span class="cx-menu-wrap">
                    <button
                      class="table-action table-action--icon"
                      type="button"
                      :aria-label="`Actions for ${customer.name}`"
                      :aria-expanded="openRowMenu === customer.id"
                      @click="toggleRowMenu(customer.id)"
                    >
                      <Ellipsis :size="17" aria-hidden="true" />
                    </button>
                    <span v-if="openRowMenu === customer.id" class="cx-menu cx-menu--end">
                      <button type="button" :disabled="!customer.hasRecord" @click="openEditForm(customer); closeMenus()">
                        Edit details
                      </button>
                      <button type="button" @click="exportCustomers([customer]); closeMenus()">Export row (CSV)</button>
                      <button
                        class="cx-menu__danger"
                        type="button"
                        :disabled="!customer.hasRecord"
                        @click="removeCustomer(customer); closeMenus()"
                      >
                        Remove customer
                      </button>
                    </span>
                  </span>
                </span>
              </div>
            </div>

            <footer class="cx-pager">
              <span>Showing {{ rangeStart }}–{{ rangeEnd }} of {{ sortedCustomers.length }} customers</span>
              <div class="cx-pager__pages">
                <button type="button" aria-label="Previous page" :disabled="currentPage === 1" @click="currentPage--">
                  <ChevronLeft :size="15" aria-hidden="true" />
                </button>
                <template v-for="(page, index) in pageNumbers" :key="`${page}-${index}`">
                  <span v-if="page === null" class="cx-pager__gap" aria-hidden="true">…</span>
                  <button
                    v-else
                    type="button"
                    class="cx-pager__page"
                    :class="{ 'cx-pager__page--current': page === currentPage }"
                    :aria-current="page === currentPage ? 'page' : undefined"
                    :aria-label="`Page ${page}`"
                    @click="currentPage = page"
                  >
                    {{ page }}
                  </button>
                </template>
                <button type="button" aria-label="Next page" :disabled="currentPage === pageCount" @click="currentPage++">
                  <ChevronRight :size="15" aria-hidden="true" />
                </button>
              </div>
            </footer>
          </template>
        </section>

        <section class="cx-card cx-panel cx-insights">
          <header class="cx-panel__head">
            <div>
              <h2>Customer Insights</h2>
              <p>Small pockets of business worth a message this week.</p>
            </div>
          </header>

          <div class="cx-insights__grid">
            <article v-for="insight in insights" :key="insight.key" class="cx-insight">
              <span class="cx-insight__icon"><component :is="insight.icon" :size="19" aria-hidden="true" /></span>
              <div>
                <span class="cx-insight__label">{{ insight.label }}</span>
                <strong class="cx-insight__value">{{ insight.value }}</strong>
                <span class="cx-insight__hint">{{ insight.hint }}</span>
              </div>
            </article>
          </div>
        </section>
      </div>

      <div class="cx-column cx-column--side">
        <section class="cx-card cx-panel">
          <header class="cx-panel__head">
            <div>
              <h2>Top Customers</h2>
              <p>Highest spend in this period.</p>
            </div>
            <button class="cx-viewall" type="button" @click="showDirectory">
              View all <ArrowRight :size="15" aria-hidden="true" />
            </button>
          </header>

          <ol v-if="topCustomers.length" class="cx-top">
            <li v-for="(customer, index) in topCustomers" :key="customer.id" class="cx-top__row">
              <span class="cx-top__rank" :class="index < 3 ? `cx-top__rank--${index + 1}` : ''" aria-hidden="true">
                <Award v-if="index < 3" :size="15" />
                <template v-else>{{ index + 1 }}</template>
              </span>
              <span class="cx-avatar cx-avatar--sm" :class="avatarTone(customer)" aria-hidden="true">
                {{ customer.initials || 'C' }}
              </span>
              <span class="cx-top__body">
                <strong>{{ customer.name }}</strong>
                <small>{{ customer.visits }} {{ customer.visits === 1 ? 'order' : 'orders' }}</small>
              </span>
              <strong class="cx-top__amount">{{ formatCurrency(customer.totalSpendCents) }}</strong>
            </li>
          </ol>
          <p v-else class="cx-panel__empty">
            Named customers appear here once a sale is attached to someone other than Guest.
          </p>
        </section>

        <section class="cx-card cx-panel">
          <header class="cx-panel__head">
            <div>
              <h2>Customer Mix</h2>
              <p>Named customers by tier.</p>
            </div>
            <span
              class="cx-kpi__info cx-panel__info"
              tabindex="0"
              role="img"
              aria-label="Tiers are earned by visits and spend in this period: VIP from 8 visits or ₱1,500, Regular from 4 or ₱700, Occasional from 2."
              data-tip="Tiers are earned by visits and spend in this period: VIP from 8 visits or ₱1,500, Regular from 4 or ₱700, Occasional from 2."
            >
              <Info :size="15" aria-hidden="true" />
            </span>
          </header>

          <div class="cx-mix">
            <div class="cx-donut">
              <svg
                viewBox="0 0 120 120"
                role="img"
                :aria-label="`Customer mix: ${tierBreakdown.map((segment) => `${segment.tier} ${segment.count} (${segment.percentage}%)`).join(', ')}`"
              >
                <circle class="cx-donut__track" cx="60" cy="60" :r="RING.radius" :stroke-width="RING.width" />
                <circle
                  v-for="segment in donutSegments"
                  :key="segment.tier"
                  class="cx-donut__arc"
                  :class="`cx-donut__arc--${segment.tier.toLowerCase()}`"
                  cx="60"
                  cy="60"
                  :r="RING.radius"
                  :stroke-width="RING.width"
                  :stroke-dasharray="segment.dash"
                  :stroke-dashoffset="segment.offset"
                />
              </svg>
              <p class="cx-donut__center">
                <strong>{{ totalCustomers.toLocaleString('en-PH') }}</strong>
                <span>customers</span>
              </p>
            </div>

            <ul class="cx-legend">
              <li v-for="segment in tierBreakdown" :key="segment.tier" class="cx-legend__row">
                <i :class="`cx-swatch cx-swatch--${segment.tier.toLowerCase()}`" aria-hidden="true" />
                <span class="cx-legend__label">{{ segment.tier }}</span>
                <strong class="cx-legend__count">{{ segment.count }}</strong>
                <span class="cx-legend__track" aria-hidden="true">
                  <span
                    class="cx-legend__fill"
                    :class="`cx-legend__fill--${segment.tier.toLowerCase()}`"
                    :style="{ width: `${segment.percentage}%` }"
                  />
                </span>
                <span class="cx-legend__pct">{{ segment.percentage }}%</span>
              </li>
            </ul>
          </div>
        </section>

        <section class="cx-card cx-panel">
          <header class="cx-panel__head">
            <div>
              <h2>Recent Customer Activity</h2>
              <p>The latest from named customers.</p>
            </div>
            <RouterLink class="cx-viewall" to="/orders">View all <ArrowRight :size="15" aria-hidden="true" /></RouterLink>
          </header>

          <ul v-if="recentActivity.length" class="cx-activity">
            <li v-for="entry in recentActivity" :key="entry.id" class="cx-activity__row">
              <span class="cx-activity__icon" :class="`cx-activity__icon--${entry.kind}`">
                <component :is="activityIcon[entry.kind]" :size="15" aria-hidden="true" />
              </span>
              <span class="cx-activity__body">
                <strong>{{ entry.name }}</strong>
                <small>{{ entry.detail }}</small>
              </span>
              <span class="cx-activity__time">{{ timeAgo(entry.at) }}</span>
            </li>
          </ul>
          <p v-else class="cx-panel__empty">
            Activity shows up here once checkout starts using saved customers.
          </p>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
/*
 * Tier colours are fixed hues, not the shop's accent: a mix chart that
 * repaints itself when the owner picks a new theme stops meaning anything.
 * Both sets clear the six colour checks (lightness band, chroma floor, CVD
 * separation, normal-vision floor, contrast) against their own surface.
 */
.cx-page {
  --tier-vip: #b07c00;
  --tier-regular: #1a6b3c;
  --tier-occasional: #2a78d6;
  --tier-new: #a1559b;

  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  padding: 4px 0 32px;
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) .cx-page {
    --tier-vip: #a18c24;
    --tier-regular: #2b7a52;
    --tier-occasional: #408dea;
    --tier-new: #c861ad;
  }
}

:root[data-theme='dark'] .cx-page {
  --tier-vip: #a18c24;
  --tier-regular: #2b7a52;
  --tier-occasional: #408dea;
  --tier-new: #c861ad;
}

/* ── Header ───────────────────────────────────────────────────────────── */

.cx-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.cx-title {
  margin: 0;
  color: var(--text-primary);
  font: var(--type-title1);
  letter-spacing: -0.025em;
}

.cx-subtitle {
  max-width: 62ch;
  margin: 3px 0 0;
  color: var(--text-secondary);
  font: var(--type-subhead);
}

.cx-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.cx-new {
  min-height: 42px;
  gap: 8px;
  padding-inline: 18px;
  border-radius: 12px;
  font: 600 0.875rem/1rem var(--font-sans);
  box-shadow: 0 5px 14px color-mix(in srgb, var(--accent) 18%, transparent);
}

.cx-range {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.cx-range__text {
  color: var(--text-secondary);
  font: 400 0.8125rem/1rem var(--font-sans);
  white-space: nowrap;
}

/* ── Cards ────────────────────────────────────────────────────────────── */

.cx-card {
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--separator) 72%, transparent);
  border-radius: 15px;
  background: var(--bg-elevated);
  box-shadow: 0 2px 8px rgba(20, 43, 32, 0.035);
}

.cx-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 18px 16px;
}

.cx-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.cx-panel__head h2 {
  margin: 0;
  color: var(--text-primary);
  font: 700 1.0625rem/1.4rem var(--font-sans);
  letter-spacing: -0.01em;
}

.cx-panel__head p {
  margin: 2px 0 0;
  color: var(--text-secondary);
  font: 400 0.8125rem/1.1rem var(--font-sans);
}

.cx-panel__empty {
  margin: 0;
  padding: 18px 0;
  color: var(--text-secondary);
  font: 400 0.8125rem/1.2rem var(--font-sans);
}

.cx-viewall {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--accent);
  font: 600 0.8125rem/1.2rem var(--font-sans);
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
}

.cx-viewall:hover {
  text-decoration: underline;
}

/* ── Customer form ────────────────────────────────────────────────────── */

.cx-form {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.cx-form__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.cx-form__eyebrow {
  margin: 0;
  color: var(--text-secondary);
  font: 500 0.75rem/1rem var(--font-sans);
}

.cx-form__head h2 {
  margin: 3px 0 0;
  color: var(--text-primary);
  font: 700 1.0625rem/1.4rem var(--font-sans);
}

.cx-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.cx-form__notes {
  grid-column: 1 / -1;
  resize: vertical;
}

/* Enrolment in points: a checkbox with the reason it is asked. */
.cx-form__consent {
  grid-column: 1 / -1;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: var(--text-primary);
  font-size: 13px;
}

.cx-form__consent small {
  display: block;
  margin-top: 2px;
  color: var(--text-secondary);
}

.cx-form__points {
  grid-column: 1 / -1;
}

.cx-form__error {
  margin: 0;
  color: var(--danger-600);
  font: var(--type-caption);
}

.cx-form__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-3);
}

/* ── KPIs ─────────────────────────────────────────────────────────────── */

.cx-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.cx-kpi {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 17px 16px;
}

.cx-kpi__icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 11%, transparent);
  color: var(--accent);
}

.cx-kpi__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.cx-kpi__label {
  padding-right: 18px;
  color: var(--text-secondary);
  font: 500 0.8125rem/1.1rem var(--font-sans);
}

.cx-kpi__value {
  color: var(--text-primary);
  font: 700 1.5rem/1.9rem var(--font-sans);
  letter-spacing: -0.015em;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cx-kpi__delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 1px;
  color: var(--text-secondary);
  font: 400 0.8125rem/1rem var(--font-sans);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cx-kpi__delta b {
  margin-right: 4px;
  font-weight: 600;
}

.cx-kpi__delta.is-up b,
.cx-kpi__delta.is-up svg { color: var(--accent); }

.cx-kpi__delta.is-down b,
.cx-kpi__delta.is-down svg { color: var(--danger); }

.cx-kpi__delta--none { color: var(--text-tertiary); }

.cx-kpi__info {
  position: absolute;
  top: 13px;
  right: 13px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--text-tertiary);
  cursor: help;
  outline: none;
}

.cx-kpi__info:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 22%, transparent);
}

.cx-kpi__info::after {
  content: attr(data-tip);
  position: absolute;
  top: calc(100% + 8px);
  right: -6px;
  z-index: 20;
  width: 220px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--text-primary);
  color: var(--bg-elevated);
  font: 400 0.75rem/1.05rem var(--font-sans);
  text-align: left;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-4px);
  transition: opacity var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.cx-kpi__info:hover::after,
.cx-kpi__info:focus-visible::after {
  opacity: 1;
  transform: none;
}

/* In a panel header the icon sits in the flow, but it still has to be the
   thing its tooltip is measured against. */
.cx-panel__info {
  position: relative;
  top: 0;
  right: 0;
  flex: none;
}

/* ── Two-column body ──────────────────────────────────────────────────── */

.cx-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.85fr) minmax(320px, 1fr);
  align-items: start;
  gap: 16px;
}

.cx-column {
  min-width: 0;
  display: grid;
  gap: 16px;
  align-content: start;
}

/* ── Directory ────────────────────────────────────────────────────────── */

.cx-directory__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cx-file {
  display: none;
}

.cx-menu-wrap {
  position: relative;
  display: inline-flex;
}

.cx-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 14;
  min-width: 185px;
  display: grid;
  gap: 2px;
  padding: 6px;
  border: 1px solid var(--separator);
  border-radius: 11px;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-md);
}

.cx-menu--end {
  left: auto;
  right: 0;
}

.cx-menu button {
  min-height: 34px;
  padding: 0 9px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-primary);
  font: 500 0.75rem/1rem var(--font-sans);
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}

.cx-menu button:hover:not(:disabled) {
  background: var(--fill);
}

.cx-menu button:disabled {
  color: var(--text-tertiary);
  cursor: not-allowed;
}

.cx-menu__danger:not(:disabled) {
  color: var(--danger-600);
}

.cx-notice {
  margin: 0;
  padding: 9px 12px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--accent) 9%, transparent);
  color: var(--text-primary);
  font: 500 0.8125rem/1.15rem var(--font-sans);
}

.cx-filters {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
}

.cx-search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 40px;
  padding: 0 var(--space-3);
  border: 1px solid var(--separator);
  border-radius: 10px;
  background: var(--bg-elevated);
  color: var(--text-secondary);
}

.cx-search:focus-within {
  border-color: var(--accent);
}

.cx-search input {
  width: 100%;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font: 400 0.8125rem/1rem var(--font-sans);
}

.cx-select {
  width: 100%;
  min-width: 0;
}

.cx-select :deep(.acselect__trigger) {
  height: 40px;
  min-height: 40px;
  border-radius: 10px;
  border-width: 1px;
  font-size: 0.8125rem;
}

.cx-directory__summary {
  margin: -4px 0 0;
  color: var(--text-secondary);
  font: 400 0.75rem/1rem var(--font-sans);
}

.cx-empty {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 6px;
  min-height: 220px;
  padding: 20px;
  border: 1px dashed var(--separator);
  border-radius: 13px;
  color: var(--text-tertiary);
  text-align: center;
}

.cx-empty strong {
  color: var(--text-primary);
  font: var(--type-headline);
}

.cx-empty span {
  max-width: 46ch;
  font: var(--type-caption);
  color: var(--text-secondary);
}

/* ── Directory table ──────────────────────────────────────────────────── */

.cx-table {
  display: grid;
}

.cx-table__row {
  display: grid;
  grid-template-columns: 22px minmax(170px, 2.1fr) 108px 66px minmax(96px, 1fr) minmax(96px, 1fr) 74px 46px;
  align-items: center;
  gap: 10px;
  min-height: 58px;
  padding: 0 2px;
  border-bottom: 1px solid color-mix(in srgb, var(--separator) 70%, transparent);
  color: var(--text-primary);
  font: 400 0.8125rem/1.1rem var(--font-sans);
  transition: background var(--dur-fast) var(--ease-out);
}

.cx-table__row:hover {
  background: color-mix(in srgb, var(--fill) 38%, transparent);
}

.cx-table__row--selected {
  background: color-mix(in srgb, var(--accent) 7%, transparent);
}

.cx-table__row--head {
  min-height: 38px;
  border-bottom-color: var(--separator);
  color: var(--text-secondary);
  font: 500 0.75rem/1rem var(--font-sans);
}

.cx-table__row--head:hover {
  background: none;
}

.cx-table__row > * {
  min-width: 0;
}

.cx-sort {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.cx-sort:hover {
  color: var(--accent);
}

.cx-sort svg {
  color: var(--text-tertiary);
}

.cx-sort--end {
  justify-content: flex-end;
}

.cx-table__actions-head {
  text-align: center;
}

.cx-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.cx-identity__copy {
  display: grid;
  min-width: 0;
}

.cx-identity__copy strong {
  overflow: hidden;
  color: var(--text-primary);
  font: 600 0.875rem/1.2rem var(--font-sans);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cx-identity__copy small {
  overflow: hidden;
  color: var(--text-secondary);
  font: 400 0.75rem/1.05rem var(--font-sans);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cx-avatar {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--avatar) 16%, var(--bg-elevated));
  color: var(--avatar);
  font: 700 0.8125rem/1rem var(--font-sans);
}

.cx-avatar--sm {
  width: 30px;
  height: 30px;
  font-size: 0.75rem;
}

/* On a dark card the same six hues are the initials, not the tile behind
   them, so they have to come up to meet the text. */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) .cx-page {
    --avatar-0: #6cc48f;
    --avatar-1: #7db4f7;
    --avatar-2: #d68ac9;
    --avatar-3: #dcae4a;
    --avatar-4: #58bcc4;
    --avatar-5: #e08b7c;
  }
}

:root[data-theme='dark'] .cx-page {
  --avatar-0: #6cc48f;
  --avatar-1: #7db4f7;
  --avatar-2: #d68ac9;
  --avatar-3: #dcae4a;
  --avatar-4: #58bcc4;
  --avatar-5: #e08b7c;
}

.cx-avatar--0 { --avatar: var(--avatar-0, #1a6b3c); }
.cx-avatar--1 { --avatar: var(--avatar-1, #2a78d6); }
.cx-avatar--2 { --avatar: var(--avatar-2, #a1559b); }
.cx-avatar--3 { --avatar: var(--avatar-3, #b07c00); }
.cx-avatar--4 { --avatar: var(--avatar-4, #0f7c86); }
.cx-avatar--5 { --avatar: var(--avatar-5, #b04a3a); }

.cx-tier {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--tier) 15%, transparent);
  color: var(--tier);
  font: 600 0.6875rem/0.9rem var(--font-sans);
  white-space: nowrap;
}

.cx-tier--vip        { --tier: var(--tier-vip); }
.cx-tier--regular    { --tier: var(--tier-regular); }
.cx-tier--occasional { --tier: var(--tier-occasional); }
.cx-tier--new        { --tier: var(--tier-new); }

.cx-num {
  font-variant-numeric: tabular-nums;
}

.cx-num--end {
  font-weight: 600;
  text-align: right;
}

.cx-date {
  overflow: hidden;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cx-contact {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.cx-contact__link {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: var(--text-secondary);
}

.cx-contact__link:hover,
.cx-contact__link:focus-visible {
  background: var(--fill);
  color: var(--accent);
  outline: none;
}

.cx-contact__none {
  color: var(--text-tertiary);
}

.cx-table__actions {
  display: flex;
  justify-content: center;
}

/* ── Pagination ───────────────────────────────────────────────────────── */

.cx-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-top: 12px;
  color: var(--text-secondary);
  font: 400 0.75rem/1rem var(--font-sans);
}

.cx-pager__pages {
  display: flex;
  align-items: center;
  gap: 4px;
}

.cx-pager__pages button {
  min-width: 32px;
  min-height: 32px;
  display: inline-grid;
  place-items: center;
  padding: 0 7px;
  border: 1px solid var(--separator);
  border-radius: 9px;
  background: var(--bg-elevated);
  color: var(--text-primary);
  font: 500 0.75rem/1rem var(--font-sans);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.cx-pager__pages button:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--accent) 34%, var(--separator));
  color: var(--accent);
}

.cx-pager__pages button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.cx-pager__page--current,
.cx-pager__page--current:hover {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}

.cx-pager__gap {
  padding: 0 2px;
  color: var(--text-tertiary);
}

/* ── Insights ─────────────────────────────────────────────────────────── */

.cx-insights__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.cx-insight {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 13px;
  border: 1px solid color-mix(in srgb, var(--separator) 60%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--fill) 26%, transparent);
}

.cx-insight > div {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.cx-insight__icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-elevated));
  color: var(--accent);
}

.cx-insight__label {
  color: var(--text-secondary);
  font: 500 0.75rem/1rem var(--font-sans);
}

.cx-insight__value {
  color: var(--text-primary);
  font: 700 1.25rem/1.6rem var(--font-sans);
  font-variant-numeric: tabular-nums;
}

.cx-insight__hint {
  overflow: hidden;
  color: var(--text-tertiary);
  font: 400 0.6875rem/0.95rem var(--font-sans);
  text-overflow: ellipsis;
}

/* ── Top customers ────────────────────────────────────────────────────── */

.cx-top {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cx-top__row {
  display: grid;
  grid-template-columns: 26px auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  border-bottom: 1px solid color-mix(in srgb, var(--separator) 60%, transparent);
}

.cx-top__row:last-child {
  border-bottom: 0;
}

.cx-top__rank {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--fill);
  color: var(--text-secondary);
  font: 600 0.75rem/1rem var(--font-sans);
}

.cx-top__rank--1 { background: color-mix(in srgb, var(--tier-vip) 20%, transparent); color: var(--tier-vip); }
.cx-top__rank--2 { background: color-mix(in srgb, var(--text-tertiary) 22%, transparent); color: var(--text-secondary); }
.cx-top__rank--3 { background: color-mix(in srgb, #b0672c 20%, transparent); color: #a05f27; }

.cx-top__body {
  display: grid;
  min-width: 0;
}

.cx-top__body strong {
  overflow: hidden;
  color: var(--text-primary);
  font: 600 0.8125rem/1.15rem var(--font-sans);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cx-top__body small {
  color: var(--text-secondary);
  font: 400 0.75rem/1rem var(--font-sans);
}

.cx-top__amount {
  color: var(--text-primary);
  font: 600 0.8125rem/1.15rem var(--font-sans);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ── Mix (donut + legend) ─────────────────────────────────────────────── */

.cx-mix {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
}

.cx-donut {
  position: relative;
  width: 148px;
  height: 148px;
}

.cx-donut svg {
  display: block;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.cx-donut__track {
  fill: none;
  stroke: color-mix(in srgb, var(--fill) 70%, transparent);
}

.cx-donut__arc {
  fill: none;
  stroke-linecap: butt;
  transition: stroke-dasharray var(--dur-base) var(--ease-out);
}

.cx-donut__arc--vip        { stroke: var(--tier-vip); }
.cx-donut__arc--regular    { stroke: var(--tier-regular); }
.cx-donut__arc--occasional { stroke: var(--tier-occasional); }
.cx-donut__arc--new        { stroke: var(--tier-new); }

.cx-donut__center {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  margin: 0;
  text-align: center;
}

.cx-donut__center strong {
  color: var(--text-primary);
  font: 700 1.3rem/1.55rem var(--font-sans);
  font-variant-numeric: tabular-nums;
}

.cx-donut__center span {
  color: var(--text-secondary);
  font: 400 0.6875rem/0.95rem var(--font-sans);
}

.cx-legend {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cx-legend__row {
  display: grid;
  grid-template-columns: 11px minmax(56px, auto) 26px minmax(40px, 1fr) 34px;
  align-items: center;
  gap: 8px;
  min-height: 34px;
}

.cx-swatch {
  width: 11px;
  height: 11px;
  border-radius: 50%;
}

.cx-swatch--vip        { background: var(--tier-vip); }
.cx-swatch--regular    { background: var(--tier-regular); }
.cx-swatch--occasional { background: var(--tier-occasional); }
.cx-swatch--new        { background: var(--tier-new); }

.cx-legend__label {
  color: var(--text-primary);
  font: 400 0.8125rem/1.1rem var(--font-sans);
}

.cx-legend__count {
  color: var(--text-primary);
  font: 600 0.8125rem/1.1rem var(--font-sans);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.cx-legend__track {
  height: 7px;
  border-radius: 999px;
  background: var(--fill);
  overflow: hidden;
}

.cx-legend__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width var(--dur-base) var(--ease-out);
}

.cx-legend__fill--vip        { background: var(--tier-vip); }
.cx-legend__fill--regular    { background: var(--tier-regular); }
.cx-legend__fill--occasional { background: var(--tier-occasional); }
.cx-legend__fill--new        { background: var(--tier-new); }

.cx-legend__pct {
  color: var(--text-secondary);
  font: 400 0.75rem/1rem var(--font-sans);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

/* ── Activity ─────────────────────────────────────────────────────────── */

.cx-activity {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cx-activity__row {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 50px;
  border-bottom: 1px solid color-mix(in srgb, var(--separator) 60%, transparent);
}

.cx-activity__row:last-child {
  border-bottom: 0;
}

.cx-activity__icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--fill);
  color: var(--text-secondary);
}

.cx-activity__icon--purchase {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}

.cx-activity__icon--joined {
  background: color-mix(in srgb, var(--tier-occasional) 14%, transparent);
  color: var(--tier-occasional);
}

.cx-activity__icon--updated {
  background: color-mix(in srgb, var(--tier-new) 14%, transparent);
  color: var(--tier-new);
}

.cx-activity__body {
  display: grid;
  min-width: 0;
}

.cx-activity__body strong {
  overflow: hidden;
  color: var(--text-primary);
  font: 600 0.8125rem/1.15rem var(--font-sans);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cx-activity__body small {
  overflow: hidden;
  color: var(--text-secondary);
  font: 400 0.75rem/1rem var(--font-sans);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cx-activity__time {
  color: var(--text-tertiary);
  font: 400 0.75rem/1rem var(--font-sans);
  white-space: nowrap;
}

/* ── Responsive ───────────────────────────────────────────────────────── */

@media (max-width: 1340px) {
  .cx-columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .cx-column--side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .cx-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cx-insights__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cx-table__row {
    grid-template-columns: 22px minmax(150px, 2fr) 104px 60px minmax(90px, 1fr) 68px 44px;
  }

  .cx-table__row > [role='cell']:nth-child(5),
  .cx-table__row--head > *:nth-child(5) {
    display: none;
  }
}

@media (max-width: 900px) {
  .cx-column--side {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 780px) {
  .cx-header,
  .cx-header__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .cx-subtitle {
    display: none;
  }

  .cx-filters {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .cx-search {
    grid-column: 1 / -1;
  }

  .cx-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .cx-panel__head {
    flex-wrap: wrap;
  }

  .cx-directory__tools {
    width: 100%;
  }

  .cx-table__row--head {
    display: none;
  }

  /* Each customer becomes a two-line card: who they are and what they spent,
     then their tier, a way to reach them, and the actions. */
  .cx-table__row {
    grid-template-columns: 22px minmax(0, 1fr) auto auto;
    grid-template-areas:
      'check identity identity spend'
      'check tier contact actions';
    row-gap: 8px;
    padding: 12px 2px;
  }

  .cx-table__row > .pcheck { grid-area: check; align-self: start; margin-top: 4px; }
  .cx-identity { grid-area: identity; }
  .cx-num--end { grid-area: spend; align-self: start; }
  .cx-table__row > [role='cell']:nth-child(3) { grid-area: tier; }
  .cx-contact { grid-area: contact; justify-content: flex-end; }
  .cx-table__row > .cx-num:not(.cx-num--end),
  .cx-table__row > .cx-date { display: none; }
  .cx-table__actions { grid-area: actions; justify-content: flex-end; }

  .cx-mix {
    grid-template-columns: minmax(0, 1fr);
    justify-items: center;
  }

  .cx-legend {
    width: 100%;
  }

  .cx-pager {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
}

@media (max-width: 520px) {
  .cx-kpis,
  .cx-insights__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
