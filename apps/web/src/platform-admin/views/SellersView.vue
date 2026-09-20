<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Check, Copy, Search } from '@lucide/vue'
import { api, type SellerRow } from '../api'
import { count, pesos, shortDate } from '../format'
import PageHero from '../PageHero.vue'
import SubscriptionPaymentsQueue from '../SubscriptionPaymentsQueue.vue'

// The shops on the marketplace, and the four things an operator does to one:
// verify the subscription payment, reject it, suspend the shop, or delete it.
//
// This is the only screen in the portal that writes to somebody else's record,
// which is why every destructive step asks first and why the subscription
// state is on the row rather than behind a click — an operator approving a
// GCash payment should be able to see what they are approving.

const props = defineProps<{ search: string }>()

const rows = ref<SellerRow[]>([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const busy = ref('')
const term = ref('')
const filter = ref<'all' | 'pending' | 'approved' | 'suspended'>('all')

/** Shown once, in the clear, then forgotten — see resetOwnerPassword. */
const revealed = ref<{ username: string; password: string } | null>(null)
const copied = ref(false)

const deleting = ref<SellerRow | null>(null)
const deleteConfirm = ref('')

watch(
  () => props.search,
  (next) => {
    term.value = next
  },
)

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

onMounted(load)

/**
 * Where a shop stands, in one word.
 *
 * Suspension outranks the subscription: a suspended shop is off the
 * marketplace whatever its payment says, and showing it as "approved" because
 * the GCash reference cleared would be the wrong fact on the row.
 */
function state(row: SellerRow): 'suspended' | 'approved' | 'pending' | 'rejected' {
  if (row.suspended) return 'suspended'
  if (row.subscription?.status === 'active') return 'approved'
  if (row.subscription?.status === 'rejected') return 'rejected'
  return 'pending'
}

const STATE_PILL: Record<string, string> = {
  approved: 'adm-pill--completed',
  pending: 'adm-pill--processing',
  rejected: 'adm-pill--cancelled',
  suspended: 'adm-pill--cancelled',
}

const visible = computed(() => {
  const needle = term.value.trim().toLowerCase()

  return rows.value.filter((row) => {
    if (filter.value !== 'all' && state(row) !== filter.value) return false
    if (!needle) return true

    return [row.organizationName, row.organizationSlug, row.store?.name, row.store?.businessTypeLabel]
      .some((field) => (field ?? '').toLowerCase().includes(needle))
  })
})

const tallies = computed(() => ({
  all: rows.value.length,
  pending: rows.value.filter((row) => state(row) === 'pending').length,
  approved: rows.value.filter((row) => state(row) === 'approved').length,
  suspended: rows.value.filter((row) => state(row) === 'suspended').length,
}))

async function act(key: string, action: string, payload: Record<string, unknown>, message: string) {
  busy.value = key
  error.value = ''
  notice.value = ''
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
    // Clipboard blocked: the password is on screen to be read out instead.
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
</script>

<template>
  <PageHero title="Sellers" subtitle="The shops trading on the marketplace, and the state of each one's subscription." />

  <div class="page">
    <!-- Above the seller list on purpose: this is a queue somebody works
         through, and it is the only place a subscription's paid-to date is
         ever set. -->
    <SubscriptionPaymentsQueue />

    <div class="adm-card sell__panel">
      <nav class="adm-tabs" aria-label="Filter sellers">
        <button
          v-for="tab in (['all', 'pending', 'approved', 'suspended'] as const)"
          :key="tab"
          type="button"
          class="adm-tab"
          :class="{ 'adm-tab--on': filter === tab }"
          @click="filter = tab"
        >
          {{ tab === 'all' ? 'All sellers' : tab[0].toUpperCase() + tab.slice(1) }}
          <span class="adm-tab__count">{{ count(tallies[tab]) }}</span>
        </button>
      </nav>

      <div class="sell__toolbar">
        <label class="adm-field sell__search">
          <Search :size="15" aria-hidden="true" />
          <input v-model="term" type="search" placeholder="Search shop or slug" aria-label="Search sellers" />
        </label>
      </div>

      <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>
      <p v-if="notice" class="sell__notice">{{ notice }}</p>
      <p v-if="loading" class="adm-note">Loading sellers…</p>

      <div v-else-if="visible.length > 0" class="adm-tablewrap">
        <table class="adm-table">
          <thead>
            <tr>
              <th>Seller</th>
              <th>Trade</th>
              <th>Owner</th>
              <th class="adm-right">Subscription</th>
              <th>Status</th>
              <th class="adm-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.organizationSlug">
              <td>
                <span class="sell__name">
                  <strong>{{ row.organizationName }}</strong>
                  <small>{{ row.store?.name ?? 'No shop yet' }} · {{ row.organizationSlug }}</small>
                </span>
              </td>
              <td class="sell__muted">{{ row.store?.businessTypeLabel ?? '—' }}</td>
              <td class="sell__muted">
                <template v-if="row.admins[0]">
                  {{ row.admins[0].fullName }}
                  <small v-if="row.admins[0].disabled" class="sell__flag">disabled</small>
                </template>
                <template v-else>—</template>
              </td>
              <td class="adm-right adm-num">
                <template v-if="row.subscription">
                  {{ pesos(row.subscription.amountCents) }}
                  <small class="sell__sub">{{ row.subscription.plan }} · {{ shortDate(row.subscription.submittedAt) }}</small>
                  <small v-if="row.subscription.trialEndsAt" class="sell__sub">
                    Free until {{ shortDate(row.subscription.trialEndsAt) }}
                  </small>
                </template>
                <template v-else>—</template>
              </td>
              <td>
                <span class="adm-pill" :class="STATE_PILL[state(row)]">
                  <span class="adm-pill__dot" aria-hidden="true"></span>
                  {{ state(row)[0].toUpperCase() + state(row).slice(1) }}
                </span>
                <!-- The server's own verdict, when it is stricter than the
                     pill: only possible with billing enforced, and it is the
                     one fact here the shop is actually living with. -->
                <small v-if="row.tenantAccess === 'unpaid'" class="sell__flag">selling blocked — unpaid</small>
              </td>
              <td class="adm-right">
                <div class="sell__actions">
                  <button
                    v-if="state(row) === 'pending'"
                    type="button"
                    class="adm-btn sell__mini"
                    :disabled="busy !== ''"
                    @click="act(row.organizationSlug + ':v', 'verify', { organizationSlug: row.organizationSlug }, `${row.organizationName} is verified.`)"
                  >
                    Verify
                  </button>
                  <button
                    v-if="state(row) === 'pending'"
                    type="button"
                    class="adm-btn adm-btn--quiet sell__mini"
                    :disabled="busy !== ''"
                    @click="act(row.organizationSlug + ':r', 'reject', { organizationSlug: row.organizationSlug }, `${row.organizationName} was rejected.`)"
                  >
                    Reject
                  </button>
                  <button
                    v-if="!row.suspended"
                    type="button"
                    class="adm-btn adm-btn--quiet sell__mini"
                    :disabled="busy !== ''"
                    @click="act(row.organizationSlug + ':s', 'suspendOrg', { organizationSlug: row.organizationSlug }, `${row.organizationName} is suspended.`)"
                  >
                    Suspend
                  </button>
                  <button
                    v-else
                    type="button"
                    class="adm-btn sell__mini"
                    :disabled="busy !== ''"
                    @click="act(row.organizationSlug + ':a', 'reactivateOrg', { organizationSlug: row.organizationSlug }, `${row.organizationName} is trading again.`)"
                  >
                    Reactivate
                  </button>
                  <button
                    v-if="row.admins[0]"
                    type="button"
                    class="adm-btn adm-btn--quiet sell__mini"
                    :disabled="busy !== ''"
                    @click="resetPassword(row)"
                  >
                    Reset password
                  </button>
                  <button
                    type="button"
                    class="adm-btn adm-btn--quiet sell__mini sell__danger"
                    :disabled="busy !== ''"
                    @click="deleting = row; deleteConfirm = ''"
                  >
                    Delete
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="adm-empty">
        <p class="adm-empty__title">No sellers here</p>
        <p class="adm-empty__copy">
          <template v-if="term">No shop matches “{{ term }}”.</template>
          <template v-else>Shops appear here when someone completes the seller signup form.</template>
        </p>
      </div>
    </div>
  </div>

  <!-- The new password, shown once. Nothing stores the plaintext, so closing
       this without copying it means issuing another one. -->
  <div v-if="revealed" class="sell__overlay" @click.self="revealed = null">
    <div class="sell__dialog adm-card" role="dialog" aria-labelledby="pw-title">
      <h2 id="pw-title" class="adm-h2">New password for {{ revealed.username }}</h2>
      <p class="sell__dialogcopy">
        This is shown once and is not stored anywhere. Copy it now and pass it to the owner —
        their existing sessions have already been signed out.
      </p>
      <code class="sell__password">{{ revealed.password }}</code>
      <div class="sell__dialogrow">
        <button type="button" class="adm-btn" @click="copyPassword">
          <component :is="copied ? Check : Copy" :size="15" aria-hidden="true" />
          {{ copied ? 'Copied' : 'Copy' }}
        </button>
        <button type="button" class="adm-btn adm-btn--quiet" @click="revealed = null">Done</button>
      </div>
    </div>
  </div>

  <div v-if="deleting" class="sell__overlay" @click.self="deleting = null">
    <div class="sell__dialog adm-card" role="dialog" aria-labelledby="del-title">
      <h2 id="del-title" class="adm-h2">Delete {{ deleting.organizationName }}?</h2>
      <p class="sell__dialogcopy">
        This removes the shop, its catalog, its orders and any account that belongs to no other
        shop. It cannot be undone. Type <strong>{{ deleting.organizationSlug }}</strong> to confirm.
      </p>
      <label class="adm-field">
        <input v-model="deleteConfirm" type="text" :placeholder="deleting.organizationSlug" aria-label="Confirm the slug" />
      </label>
      <div class="sell__dialogrow">
        <button
          type="button"
          class="adm-btn sell__danger"
          :disabled="deleteConfirm.trim() !== deleting.organizationSlug || busy !== ''"
          @click="confirmDelete"
        >
          Delete permanently
        </button>
        <button type="button" class="adm-btn adm-btn--quiet" @click="deleting = null">Cancel</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 16px var(--adm-gutter) 0;
}

.sell__panel {
  padding: 0 0 6px;
}

.adm-tabs {
  padding: 4px 14px 0;
}

.sell__toolbar {
  padding: 12px 16px;
}

.sell__search {
  max-width: 340px;
}

.sell__notice {
  margin: 0 16px 10px;
  padding: 9px 12px;
  border-radius: 9px;
  background: rgba(12, 163, 12, 0.1);
  color: #0a6b0a;
  font-size: 13px;
}

.sell__name {
  display: grid;
  line-height: 1.3;
}

.sell__name small {
  color: var(--sf-faint);
  font-size: 11.5px;
}

.sell__muted {
  color: var(--sf-muted);
}

.sell__flag {
  color: #9c2626;
  font-size: 11px;
  font-weight: 700;
}

.sell__sub {
  display: block;
  color: var(--sf-faint);
  font-size: 11.5px;
  font-weight: 500;
}

.sell__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.sell__mini {
  height: 30px;
  padding: 0 11px;
  font-size: 12.5px;
}

.sell__danger {
  background: #9c2626;
  border-color: #9c2626;
  color: #fff;
}

.sell__danger:hover:not(:disabled) {
  background: #7d1e1e;
}

.sell__overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(23, 35, 28, 0.5);
}

.sell__dialog {
  display: grid;
  gap: 12px;
  width: min(440px, 100%);
  padding: 20px;
  background: var(--sf-paper);
}

.sell__dialogcopy {
  margin: 0;
  color: var(--sf-muted);
  font-size: 13.5px;
  line-height: 1.5;
}

.sell__password {
  padding: 12px;
  border-radius: 10px;
  background: var(--sf-sand);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 17px;
  letter-spacing: 0.06em;
  text-align: center;
  user-select: all;
}

.sell__dialogrow {
  display: flex;
  gap: 8px;
}

@media (max-width: 560px) {
  .page {
    padding: 14px 16px 0;
  }
}
</style>
