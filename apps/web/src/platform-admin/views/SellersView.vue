<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Ban,
  Check,
  CheckCircle2,
  ChevronDown,
  Clock3,
  Copy,
  Download,
  ExternalLink,
  Eye,
  KeyRound,
  MoreHorizontal,
  Pencil,
  Plus,
  RotateCcw,
  Search,
  Store,
  Trash2,
} from '@lucide/vue'
import { api, type SellerRow } from '../api'
import { count, pesos, shortDate } from '../format'
import PageHero from '../PageHero.vue'
import SubscriptionPaymentsQueue from '../SubscriptionPaymentsQueue.vue'

const props = defineProps<{ search: string }>()

type SellerState = 'suspended' | 'approved' | 'pending' | 'rejected'
type SellerFilter = 'all' | 'pending' | 'approved' | 'suspended'

const rows = ref<SellerRow[]>([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const busy = ref('')
const term = ref('')
const filter = ref<SellerFilter>('all')
const tradeFilter = ref('all')
const planFilter = ref('all')
const sort = ref<'newest' | 'oldest' | 'name'>('newest')
const menuFor = ref('')
const selected = ref(new Set<string>())

const revealed = ref<{ username: string; password: string } | null>(null)
const copied = ref(false)
const deleting = ref<SellerRow | null>(null)
const deleteConfirm = ref('')

watch(() => props.search, (next) => { term.value = next })

function state(row: SellerRow): SellerState {
  if (row.suspended) return 'suspended'
  if (row.subscription?.status === 'active') return 'approved'
  if (row.subscription?.status === 'rejected') return 'rejected'
  return 'pending'
}

const STATE_PILL: Record<SellerState, string> = {
  approved: 'adm-pill--completed',
  pending: 'adm-pill--processing',
  rejected: 'adm-pill--cancelled',
  suspended: 'adm-pill--cancelled',
}

const tallies = computed(() => ({
  all: rows.value.length,
  pending: rows.value.filter((row) => state(row) === 'pending').length,
  approved: rows.value.filter((row) => state(row) === 'approved').length,
  suspended: rows.value.filter((row) => state(row) === 'suspended').length,
}))

const tradeTypes = computed(() => [...new Set(rows.value
  .map((row) => row.store?.businessTypeLabel)
  .filter((value): value is string => Boolean(value)))].sort())

const plans = computed(() => [...new Set(rows.value
  .map((row) => row.subscription?.plan)
  .filter((value): value is string => Boolean(value)))].sort())

const visible = computed(() => {
  const needle = term.value.trim().toLowerCase()
  const result = rows.value.filter((row) => {
    if (filter.value !== 'all' && state(row) !== filter.value) return false
    if (tradeFilter.value !== 'all' && row.store?.businessTypeLabel !== tradeFilter.value) return false
    if (planFilter.value !== 'all' && row.subscription?.plan !== planFilter.value) return false
    if (!needle) return true
    return [
      row.organizationName,
      row.organizationSlug,
      row.store?.name,
      row.store?.businessTypeLabel,
      row.admins[0]?.fullName,
      row.admins[0]?.email,
    ].some((field) => (field ?? '').toLowerCase().includes(needle))
  })

  return result.sort((a, b) => {
    if (sort.value === 'name') return a.organizationName.localeCompare(b.organizationName)
    const left = a.createdAt ? new Date(a.createdAt).getTime() : 0
    const right = b.createdAt ? new Date(b.createdAt).getTime() : 0
    return sort.value === 'newest' ? right - left : left - right
  })
})

const allVisibleSelected = computed(() => visible.value.length > 0
  && visible.value.every((row) => selected.value.has(row.organizationSlug)))

function relativeDate(value: string | null): string {
  if (!value) return ''
  const days = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 86_400_000))
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  if (days < 14) return `${days} days ago`
  if (days < 60) return `${Math.round(days / 7)} weeks ago`
  if (days < 730) return `${Math.round(days / 30)} months ago`
  return `${Math.round(days / 365)} years ago`
}

function avatarFallback(row: SellerRow): string {
  return row.organizationName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function toggleSelected(slug: string) {
  const next = new Set(selected.value)
  next.has(slug) ? next.delete(slug) : next.add(slug)
  selected.value = next
}

function toggleAll() {
  const next = new Set(selected.value)
  if (allVisibleSelected.value) visible.value.forEach((row) => next.delete(row.organizationSlug))
  else visible.value.forEach((row) => next.add(row.organizationSlug))
  selected.value = next
}

function openShop(row: SellerRow) {
  window.open(`/shop/${encodeURIComponent(row.organizationSlug)}`, '_blank', 'noopener,noreferrer')
}

function addSeller() {
  window.open('/seller/signup', '_blank', 'noopener,noreferrer')
}

function exportCsv() {
  const quote = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`
  const header = ['Seller', 'Slug', 'Trade type', 'Owner', 'Email', 'Plan', 'Status', 'Date added']
  const body = visible.value.map((row) => [
    row.organizationName,
    row.organizationSlug,
    row.store?.businessTypeLabel,
    row.admins[0]?.fullName,
    row.admins[0]?.email,
    row.subscription?.plan,
    state(row),
    row.createdAt,
  ])
  const blob = new Blob([[header, ...body].map((line) => line.map(quote).join(',')).join('\n')], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'omaykan-sellers.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = (await api.sellers()).organizations
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load sellers.'
  } finally {
    loading.value = false
  }
}

async function act(key: string, action: string, payload: Record<string, unknown>, message: string) {
  busy.value = key
  error.value = ''
  notice.value = ''
  menuFor.value = ''
  try {
    await api.sellerAction(action, payload)
    notice.value = message
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'That did not work.'
  } finally {
    busy.value = ''
  }
}

async function resetPassword(row: SellerRow) {
  const owner = row.admins[0]
  if (!owner) return
  busy.value = `${row.organizationSlug}:pw`
  error.value = ''
  menuFor.value = ''
  try {
    const data = await api.sellerAction('resetOwnerPassword', {
      organizationSlug: row.organizationSlug,
      uid: owner.uid,
    })
    revealed.value = { username: owner.username, password: String(data.password) }
    copied.value = false
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not reset that password.'
  } finally {
    busy.value = ''
  }
}

async function copyPassword() {
  if (!revealed.value) return
  try {
    await navigator.clipboard.writeText(revealed.value.password)
    copied.value = true
  } catch {
    copied.value = false
  }
}

async function confirmDelete() {
  const target = deleting.value
  if (!target || deleteConfirm.value.trim() !== target.organizationSlug) return
  await act(
    `${target.organizationSlug}:del`,
    'deleteOrg',
    { organizationSlug: target.organizationSlug, confirmSlug: deleteConfirm.value.trim() },
    `${target.organizationName} was deleted.`,
  )
  deleting.value = null
  deleteConfirm.value = ''
}

function closeMenu(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest('.sell__actionmenu') && !target.closest('.sell__more')) menuFor.value = ''
}

onMounted(() => {
  load()
  document.addEventListener('click', closeMenu)
})
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>

<template>
  <PageHero
    title="Sellers"
    subtitle="Manage all stores on the marketplace. Approve new applications, monitor subscriptions, and support sellers."
  >
    <template #tools>
      <button type="button" class="sell__add" @click="addSeller">
        <Plus :size="18" :stroke-width="2.2" aria-hidden="true" />
        Add Seller
      </button>
    </template>
  </PageHero>

  <div class="page">
    <section class="sell__stats" aria-label="Seller overview">
      <article class="sell__stat">
        <span class="sell__staticon sell__staticon--green"><Store :size="22" /></span>
        <div><p>Total Sellers</p><strong>{{ count(tallies.all) }}</strong><small>Marketplace total</small></div>
      </article>
      <article class="sell__stat">
        <span class="sell__staticon sell__staticon--amber"><Clock3 :size="22" /></span>
        <div><p>Pending Approval</p><strong>{{ count(tallies.pending) }}</strong><small>{{ tallies.pending ? 'Requires review' : 'No pending applications' }}</small></div>
      </article>
      <article class="sell__stat">
        <span class="sell__staticon sell__staticon--green"><CheckCircle2 :size="22" /></span>
        <div><p>Active Sellers</p><strong>{{ count(tallies.approved) }}</strong><small>{{ tallies.all ? Math.round((tallies.approved / tallies.all) * 100) : 0 }}% of total</small></div>
      </article>
      <article class="sell__stat">
        <span class="sell__staticon sell__staticon--amber"><Ban :size="22" /></span>
        <div><p>Suspended</p><strong>{{ count(tallies.suspended) }}</strong><small>{{ tallies.suspended ? 'Stores need attention' : 'No suspended stores' }}</small></div>
      </article>
    </section>

    <SubscriptionPaymentsQueue />

    <section class="adm-card sell__panel">
      <header class="sell__panelhead">
        <nav class="sell__tabs" aria-label="Filter sellers">
          <button
            v-for="tab in (['all', 'pending', 'approved', 'suspended'] as const)"
            :key="tab"
            type="button"
            class="sell__tab"
            :class="{ 'sell__tab--on': filter === tab }"
            @click="filter = tab"
          >
            {{ tab === 'all' ? 'All Sellers' : tab[0].toUpperCase() + tab.slice(1) }}
            <span>{{ count(tallies[tab]) }}</span>
          </button>
        </nav>
        <button type="button" class="sell__export" @click="exportCsv">
          <Download :size="15" aria-hidden="true" /> Export <ChevronDown :size="14" />
        </button>
      </header>

      <div class="sell__toolbar">
        <label class="sell__search">
          <Search :size="17" aria-hidden="true" />
          <input v-model="term" type="search" placeholder="Search seller, owner, or store slug..." aria-label="Search sellers" />
        </label>
        <label class="sell__select"><span class="adm-sr">Status</span><select v-model="filter"><option value="all">All status</option><option value="pending">Pending</option><option value="approved">Approved</option><option value="suspended">Suspended</option></select><ChevronDown :size="14" /></label>
        <label class="sell__select"><span class="adm-sr">Trade type</span><select v-model="tradeFilter"><option value="all">All trade types</option><option v-for="trade in tradeTypes" :key="trade" :value="trade">{{ trade }}</option></select><ChevronDown :size="14" /></label>
        <label class="sell__select"><span class="adm-sr">Subscription plan</span><select v-model="planFilter"><option value="all">All subscription plans</option><option v-for="plan in plans" :key="plan" :value="plan">{{ plan }}</option></select><ChevronDown :size="14" /></label>
        <label class="sell__select sell__sort"><span class="adm-sr">Sort sellers</span><select v-model="sort"><option value="newest">Sort by: Newest</option><option value="oldest">Sort by: Oldest</option><option value="name">Sort by: Name</option></select><ChevronDown :size="14" /></label>
      </div>

      <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>
      <p v-if="notice" class="sell__notice">{{ notice }}</p>
      <p v-if="loading" class="adm-note">Loading sellers…</p>

      <div v-else-if="visible.length" class="adm-tablewrap">
        <table class="adm-table sell__table">
          <thead>
            <tr>
              <th class="sell__check"><input type="checkbox" :checked="allVisibleSelected" aria-label="Select all visible sellers" @change="toggleAll" /></th>
              <th>Seller / Store</th><th>Trade Type</th><th>Owner</th><th>Subscription</th><th>Status</th><th>Date Added</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.organizationSlug">
              <td class="sell__check"><input type="checkbox" :checked="selected.has(row.organizationSlug)" :aria-label="`Select ${row.organizationName}`" @change="toggleSelected(row.organizationSlug)" /></td>
              <td>
                <div class="sell__identity">
                  <span class="sell__photo">
                    <img v-if="row.store?.imageUrl" :src="row.store.imageUrl" alt="" />
                    <span v-else>{{ avatarFallback(row) }}</span>
                  </span>
                  <span class="sell__name"><strong>{{ row.store?.name ?? row.organizationName }}</strong><small>{{ row.organizationSlug }}</small></span>
                  <button type="button" class="sell__external" aria-label="Open storefront" @click="openShop(row)"><ExternalLink :size="14" /></button>
                </div>
              </td>
              <td><span class="sell__trade" :data-mode="row.store?.businessMode ?? 'other'">{{ row.store?.businessTypeLabel ?? 'Unspecified' }}</span></td>
              <td><span class="sell__owner"><strong>{{ row.admins[0]?.fullName ?? 'No owner' }}</strong><small>{{ row.admins[0]?.email ?? row.admins[0]?.username ?? '—' }}</small></span></td>
              <td>
                <span v-if="row.subscription" class="sell__subscription"><strong>{{ pesos(row.subscription.amountCents) }} <em>/ month</em></strong><small v-if="row.subscription.trialEndsAt">Free until {{ shortDate(row.subscription.trialEndsAt) }}</small><small v-else-if="row.subscription.currentPeriodEndsAt">Paid until {{ shortDate(row.subscription.currentPeriodEndsAt) }}</small><small v-else>{{ row.subscription.plan }}</small></span>
                <span v-else class="sell__muted">—</span>
              </td>
              <td><span class="adm-pill" :class="STATE_PILL[state(row)]"><span class="adm-pill__dot"></span>{{ state(row)[0].toUpperCase() + state(row).slice(1) }}</span><small v-if="row.tenantAccess === 'unpaid'" class="sell__flag">selling blocked — unpaid</small></td>
              <td><span class="sell__date"><strong>{{ row.createdAt ? shortDate(row.createdAt) : '—' }}</strong><small>{{ relativeDate(row.createdAt) }}</small></span></td>
              <td>
                <div class="sell__actions">
                  <button type="button" class="sell__action" @click="menuFor = menuFor === row.organizationSlug ? '' : row.organizationSlug"><Pencil :size="14" /> Edit</button>
                  <button type="button" class="sell__action" @click="openShop(row)"><Eye :size="14" /> View</button>
                  <button type="button" class="sell__more" aria-label="More seller actions" :aria-expanded="menuFor === row.organizationSlug" @click.stop="menuFor = menuFor === row.organizationSlug ? '' : row.organizationSlug"><MoreHorizontal :size="17" /></button>
                  <div v-if="menuFor === row.organizationSlug" class="sell__actionmenu">
                    <button v-if="state(row) === 'pending'" type="button" @click="act(row.organizationSlug + ':v', 'verify', { organizationSlug: row.organizationSlug }, `${row.organizationName} is verified.`)"><CheckCircle2 :size="15" /> Approve seller</button>
                    <button v-if="state(row) === 'pending'" type="button" @click="act(row.organizationSlug + ':r', 'reject', { organizationSlug: row.organizationSlug }, `${row.organizationName} was rejected.`)"><Ban :size="15" /> Reject application</button>
                    <button v-if="!row.suspended" type="button" @click="act(row.organizationSlug + ':s', 'suspendOrg', { organizationSlug: row.organizationSlug }, `${row.organizationName} is suspended.`)"><Ban :size="15" /> Suspend store</button>
                    <button v-else type="button" @click="act(row.organizationSlug + ':a', 'reactivateOrg', { organizationSlug: row.organizationSlug }, `${row.organizationName} is trading again.`)"><RotateCcw :size="15" /> Reactivate store</button>
                    <button v-if="row.admins[0]" type="button" @click="resetPassword(row)"><KeyRound :size="15" /> Reset password</button>
                    <button type="button" class="sell__menudanger" @click="deleting = row; deleteConfirm = ''; menuFor = ''"><Trash2 :size="15" /> Delete seller</button>
                  </div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="adm-empty"><p class="adm-empty__title">No sellers here</p><p class="adm-empty__copy">{{ term ? `No seller matches “${term}”.` : 'Sellers will appear here when they complete the signup form.' }}</p></div>
      <footer v-if="!loading && visible.length" class="sell__foot">Showing 1–{{ visible.length }} of {{ visible.length }} sellers <span><button type="button" disabled>‹</button><button type="button" class="sell__page">1</button><button type="button" disabled>›</button></span></footer>
    </section>
  </div>

  <div v-if="revealed" class="sell__overlay" @click.self="revealed = null">
    <div class="sell__dialog adm-card" role="dialog" aria-labelledby="pw-title">
      <h2 id="pw-title" class="adm-h2">New password for {{ revealed.username }}</h2>
      <p>This password is shown once and is not stored. Copy it now and pass it to the owner.</p>
      <code>{{ revealed.password }}</code>
      <div><button type="button" class="adm-btn" @click="copyPassword"><component :is="copied ? Check : Copy" :size="15" />{{ copied ? 'Copied' : 'Copy' }}</button><button type="button" class="adm-btn adm-btn--quiet" @click="revealed = null">Done</button></div>
    </div>
  </div>

  <div v-if="deleting" class="sell__overlay" @click.self="deleting = null">
    <div class="sell__dialog adm-card" role="dialog" aria-labelledby="del-title">
      <h2 id="del-title" class="adm-h2">Delete {{ deleting.organizationName }}?</h2>
      <p>This removes the shop, catalog, orders, and accounts unique to it. Type <strong>{{ deleting.organizationSlug }}</strong> to confirm.</p>
      <label class="adm-field"><input v-model="deleteConfirm" type="text" :placeholder="deleting.organizationSlug" aria-label="Confirm the slug" /></label>
      <div><button type="button" class="adm-btn sell__danger" :disabled="deleteConfirm.trim() !== deleting.organizationSlug || busy !== ''" @click="confirmDelete">Delete permanently</button><button type="button" class="adm-btn adm-btn--quiet" @click="deleting = null">Cancel</button></div>
    </div>
  </div>
</template>

<style scoped>
.page { display: grid; gap: 16px; min-width: 0; padding: 16px var(--adm-gutter) 0; }
.page > * { min-width: 0; }
.sell__add { display: inline-flex; align-items: center; gap: 8px; height: 42px; padding: 0 20px; border: 0; border-radius: 9px; background: #0c9a50; color: #fff; font: inherit; font-size: 13.5px; font-weight: 700; box-shadow: 0 8px 20px rgba(12, 154, 80, .22); cursor: pointer; }
.sell__add:hover { background: #078442; }
.sell__stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.sell__stat { display: flex; align-items: center; gap: 15px; min-height: 94px; padding: 16px 18px; border: 1px solid #e6e5e1; border-radius: 13px; background: rgba(255, 255, 255, .92); box-shadow: 0 5px 18px rgba(38, 45, 41, .04); box-sizing: border-box; }
.sell__staticon { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 11px; flex: none; }
.sell__staticon--green { background: #e7f7ed; color: #08974b; }
.sell__staticon--amber { background: #fff2df; color: #b46613; }
.sell__stat div { display: grid; min-width: 0; }
.sell__stat p, .sell__stat small { margin: 0; color: #667085; font-size: 11.5px; }
.sell__stat strong { margin: 1px 0 2px; color: #101828; font-size: 21px; line-height: 1.1; font-variant-numeric: tabular-nums; }
.sell__panel { position: relative; padding: 0; overflow: visible; background: #fff; border-color: #e4e5e3; box-shadow: 0 6px 24px rgba(35, 46, 39, .05); }
.sell__panelhead { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 50px; padding: 0 12px; border-bottom: 1px solid #eaebe9; }
.sell__tabs { display: flex; align-self: stretch; gap: 5px; }
.sell__tab { display: flex; align-items: center; gap: 8px; margin: 7px 0; padding: 0 14px; border: 0; border-radius: 999px; background: transparent; color: #344054; font: inherit; font-size: 12.5px; font-weight: 650; white-space: nowrap; cursor: pointer; }
.sell__tab span { display: grid; place-items: center; min-width: 20px; height: 20px; padding: 0 5px; border: 1px solid #dfe3df; border-radius: 999px; background: #fff; color: #52605a; font-size: 11px; }
.sell__tab--on { background: #0b9650; color: #fff; }
.sell__tab--on span { border-color: transparent; color: #267044; }
.sell__export, .sell__action, .sell__more { display: inline-flex; align-items: center; justify-content: center; gap: 7px; height: 34px; padding: 0 12px; border: 1px solid #e0e3e0; border-radius: 8px; background: #fff; color: #18231e; font: inherit; font-size: 12px; font-weight: 650; cursor: pointer; }
.sell__toolbar { display: grid; grid-template-columns: minmax(240px, 1.5fr) repeat(3, minmax(130px, .6fr)) minmax(145px, .7fr); gap: 10px; padding: 10px 16px; border-bottom: 1px solid #eaebe9; background: #fdfdfc; }
.sell__search, .sell__select { display: flex; align-items: center; gap: 8px; height: 38px; padding: 0 12px; border: 1px solid #dfe3e0; border-radius: 8px; background: #fff; color: #667085; }
.sell__search:focus-within, .sell__select:focus-within { border-color: #0b9650; box-shadow: 0 0 0 3px rgba(11, 150, 80, .1); }
.sell__search input, .sell__select select { min-width: 0; width: 100%; border: 0; outline: 0; appearance: none; background: transparent; color: #344054; font: inherit; font-size: 12px; }
.sell__select svg { flex: none; pointer-events: none; }
.sell__notice { margin: 10px 16px 0; padding: 9px 12px; border-radius: 8px; background: #e9f8ef; color: #08783c; font-size: 12.5px; }
.sell__table { min-width: 1160px; font-size: 12px; }
.sell__table th { padding: 10px 12px; background: #fcfcfb; color: #5f6763; font-size: 9.5px; letter-spacing: .06em; }
.sell__table td { height: 57px; padding: 8px 12px; color: #142019; box-sizing: border-box; }
.sell__check { width: 34px; padding-right: 0 !important; text-align: center; }
.sell__check input { width: 14px; height: 14px; accent-color: #0b9650; }
.sell__identity { display: flex; align-items: center; gap: 9px; min-width: 210px; }
.sell__photo { display: grid; place-items: center; width: 40px; height: 40px; overflow: hidden; border-radius: 10px; background: linear-gradient(145deg, #174b32, #8bbf68); color: #fff; font-size: 11px; font-weight: 800; flex: none; }
.sell__photo img { width: 100%; height: 100%; object-fit: cover; }
.sell__name, .sell__owner, .sell__subscription, .sell__date { display: grid; gap: 2px; line-height: 1.2; }
.sell__name strong, .sell__owner strong, .sell__subscription strong, .sell__date strong { font-size: 12px; font-style: normal; font-weight: 650; white-space: nowrap; }
.sell__name small, .sell__owner small, .sell__subscription small, .sell__date small { color: #718096; font-size: 10.5px; white-space: nowrap; }
.sell__subscription em { font-style: normal; font-weight: 500; }
.sell__external { padding: 3px; border: 0; background: transparent; color: #08a257; cursor: pointer; }
.sell__trade { display: inline-flex; max-width: 125px; padding: 5px 11px; border-radius: 999px; background: #e8f5e7; color: #317536; font-size: 10.5px; font-weight: 650; white-space: nowrap; }
.sell__trade[data-mode="grocery"] { background: #e3eefc; color: #2868b0; }
.sell__trade[data-mode="restaurant"], .sell__trade[data-mode="coffee-shop"] { background: #fff0dc; color: #ac6415; }
.sell__muted { color: #98a2b3; }
.sell__flag { display: block; margin-top: 3px; color: #a32828; font-size: 9.5px; font-weight: 700; }
.sell__actions { position: relative; display: flex; gap: 6px; white-space: nowrap; }
.sell__action, .sell__more { height: 32px; padding: 0 10px; }
.sell__more { width: 34px; padding: 0; }
.sell__action:hover, .sell__more:hover, .sell__export:hover { background: #f4f7f5; }
.sell__actionmenu { position: absolute; z-index: 12; top: calc(100% + 5px); right: 0; display: grid; min-width: 190px; padding: 5px; border: 1px solid #dfe3e0; border-radius: 10px; background: #fff; box-shadow: 0 14px 38px rgba(22, 35, 27, .16); }
.sell__actionmenu button { display: flex; align-items: center; gap: 9px; padding: 9px 10px; border: 0; border-radius: 7px; background: transparent; color: #24312a; font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.sell__actionmenu button:hover { background: #f2f6f3; }
.sell__actionmenu .sell__menudanger { color: #a32828; }
.sell__foot { display: flex; align-items: center; justify-content: space-between; min-height: 42px; padding: 0 16px; color: #667085; font-size: 11.5px; }
.sell__foot span { display: flex; gap: 5px; }
.sell__foot button { width: 30px; height: 30px; border: 1px solid #e0e3e0; border-radius: 7px; background: #fff; }
.sell__foot .sell__page { border-color: #0b9650; background: #0b9650; color: #fff; }
.sell__overlay { position: fixed; inset: 0; z-index: 40; display: grid; place-items: center; padding: 20px; background: rgba(14, 32, 23, .5); }
.sell__dialog { display: grid; gap: 13px; width: min(440px, 100%); padding: 20px; background: #fff; }
.sell__dialog p { margin: 0; color: #667085; font-size: 13px; line-height: 1.5; }
.sell__dialog code { padding: 12px; border-radius: 9px; background: #eff4f0; font-size: 17px; letter-spacing: .06em; text-align: center; user-select: all; }
.sell__dialog > div { display: flex; gap: 8px; }
.sell__danger { background: #a32828; border-color: #a32828; }
@media (max-width: 1180px) { .sell__stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } .sell__toolbar { grid-template-columns: repeat(2, minmax(0, 1fr)); } .sell__search { grid-column: 1 / -1; } }
@media (max-width: 700px) { .page { padding: 14px 16px 0; } .sell__stats { grid-template-columns: 1fr; } .sell__panelhead { display: grid; grid-template-columns: minmax(0, 1fr) auto; padding: 10px; } .sell__tabs { min-width: 0; overflow-x: auto; flex-wrap: nowrap; scrollbar-width: none; } .sell__tabs::-webkit-scrollbar { display: none; } .sell__tab { flex: none; height: 34px; margin: 0; padding: 0 10px; } .sell__export { flex: none; } .sell__toolbar { grid-template-columns: 1fr; } .sell__search { grid-column: auto; } }
</style>
