<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { dateTime, messageFor, pesos, usePlatformSession } from '@pos/web/platform-admin/admin'
import {
  deleteTenant,
  fetchTenant,
  resetOwnerPassword,
  setOwnerDisabled,
  setSubscriptionStatus,
  setTenantSuspended,
  type AuditLogRow,
  type TenantDetail,
  type TenantOwner,
} from '@pos/web/platform-admin/api'
import ConfirmDangerDialog from '@pos/web/platform-admin/components/ConfirmDangerDialog.vue'
import RevealedSecret from '@pos/web/platform-admin/components/RevealedSecret.vue'
import { router } from '@pos/web/platform-admin/router'

/*
 * One tenant, and everything that can be done to it.
 *
 * A page of its own rather than an expanding row: the destructive actions live
 * here, and they deserve a screen where the operator can read the name of what
 * they are about to suspend or delete alongside the numbers that justify it —
 * how long the shop has been trading, how many orders last month, who else
 * they have talked to about it.
 *
 * The tenant's own audit trail is on this page for that last reason. "Suspended
 * by someone, twice, in the last week" is context a decision needs, and it used
 * to exist nowhere.
 */

const route = useRoute()
const session = usePlatformSession()

const slug = computed(() => String(route.params.slug ?? ''))

const tenant = ref<TenantDetail | null>(null)
const trail = ref<AuditLogRow[]>([])
const loading = ref(true)
const error = ref('')
const busy = ref('')

const rejectReason = ref('')
const suspendReason = ref('')
const revealed = ref<{ title: string; value: string } | null>(null)
const confirmingDelete = ref(false)

async function load() {
  loading.value = true
  error.value = ''

  try {
    const result = await fetchTenant(slug.value)
    tenant.value = result.organization
    trail.value = result.auditTrail
  } catch (err) {
    error.value = messageFor(err, 'Could not load that tenant.')
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(slug, load)

async function act<T>(key: string, run: () => Promise<T>, fallback: string): Promise<T | null> {
  busy.value = key
  error.value = ''

  try {
    return await run()
  } catch (err) {
    if (!session.applyDisabled(err)) {
      error.value = messageFor(err, fallback)
    }
    return null
  } finally {
    busy.value = ''
  }
}

async function decideSubscription(status: 'active' | 'rejected') {
  const done = await act(
    'subscription',
    () => setSubscriptionStatus(slug.value, status, rejectReason.value.trim() || undefined),
    'Could not update that subscription.',
  )

  if (done) {
    rejectReason.value = ''
    await load()
  }
}

async function toggleSuspended() {
  if (!tenant.value) return

  const next = !tenant.value.suspended
  const done = await act(
    'suspension',
    () => setTenantSuspended(slug.value, next, suspendReason.value.trim() || undefined),
    'Could not update that tenant.',
  )

  if (done) {
    suspendReason.value = ''
    await load()
  }
}

async function resetPassword(owner: TenantOwner) {
  const result = await act(
    owner.uid,
    () => resetOwnerPassword(slug.value, owner.uid),
    'Could not reset that password.',
  )

  if (result) {
    revealed.value = { title: `New password for ${owner.username}`, value: result.password }
    await load()
  }
}

async function toggleOwner(owner: TenantOwner) {
  const done = await act(
    owner.uid,
    () => setOwnerDisabled(slug.value, owner.uid, !owner.disabled),
    'Could not update that login.',
  )

  if (done) await load()
}

async function confirmDelete() {
  const done = await act('delete', () => deleteTenant(slug.value, slug.value), 'Could not delete that tenant.')

  if (done) {
    confirmingDelete.value = false
    await router.push('/tenants')
  }
}

const subscriptionBadge = computed(() => {
  const status = tenant.value?.subscription?.status
  if (status === 'active') return { label: 'Verified', cls: 'pa-badge--good' }
  if (status === 'rejected') return { label: 'Rejected', cls: 'pa-badge--bad' }
  if (status === 'pending_verification') return { label: 'Pending', cls: 'pa-badge--warn' }
  return { label: 'None on record', cls: '' }
})
</script>

<template>
  <p v-if="loading" class="pa-empty">Loading…</p>

  <template v-else-if="tenant">
    <div class="pa-head">
      <div>
        <h1>{{ tenant.organizationName }}</h1>
        <p class="pa-head__copy">
          <span class="pa-slug">{{ tenant.organizationSlug }}</span>
          · joined {{ dateTime(tenant.createdAt) }}
        </p>
      </div>
      <div class="pa-actions">
        <span class="pa-badge" :class="tenant.suspended ? 'pa-badge--bad' : 'pa-badge--good'">
          {{ tenant.suspended ? 'Suspended' : 'Active' }}
        </span>
        <button class="pa-button pa-button--quiet" type="button" @click="load">Refresh</button>
      </div>
    </div>

    <p v-if="error" class="pa-alert">{{ error }}</p>

    <RevealedSecret
      v-if="revealed"
      :title="revealed.title"
      :value="revealed.value"
      @done="revealed = null"
    />

    <div class="pa-tiles">
      <div class="pa-tile">
        <p class="pa-tile__label">Orders, 30 days</p>
        <p class="pa-tile__value">{{ tenant.last30Days.orders.toLocaleString('en-PH') }}</p>
        <p class="pa-tile__note">{{ pesos(tenant.last30Days.revenueCents) }} in sales</p>
      </div>
      <div class="pa-tile">
        <p class="pa-tile__label">Online share</p>
        <p class="pa-tile__value">
          {{ (tenant.last30Days.byChannel.online?.orders ?? 0).toLocaleString('en-PH') }}
        </p>
        <p class="pa-tile__note">
          of {{ tenant.last30Days.orders.toLocaleString('en-PH') }} orders
        </p>
      </div>
      <div class="pa-tile">
        <p class="pa-tile__label">Staff accounts</p>
        <p class="pa-tile__value">{{ tenant.staffCount }}</p>
        <p class="pa-tile__note">{{ tenant.stores.length }} store{{ tenant.stores.length === 1 ? '' : 's' }}</p>
      </div>
    </div>

    <div class="pa-columns">
      <div class="pa-rows">
        <section class="pa-panel">
          <div class="pa-panel__head">
            <h2>Subscription</h2>
            <span class="pa-badge" :class="subscriptionBadge.cls">{{ subscriptionBadge.label }}</span>
          </div>
          <div class="pa-panel__body">
            <dl v-if="tenant.subscription" class="pa-kv">
              <dt>Plan</dt>
              <dd>{{ tenant.subscription.plan }} · {{ pesos(tenant.subscription.amountCents) }}</dd>
              <dt>GCash reference</dt>
              <dd class="pa-slug">{{ tenant.subscription.gcashReference }}</dd>
              <dt>Submitted</dt>
              <dd>{{ dateTime(tenant.subscription.submittedAt) }}</dd>
              <dt>Verified</dt>
              <dd>{{ dateTime(tenant.subscription.verifiedAt) }}</dd>
              <template v-if="tenant.rejectionReason">
                <dt>Rejected because</dt>
                <dd>{{ tenant.rejectionReason }}</dd>
              </template>
            </dl>
            <p v-else class="pa-tile__note">No subscription on record for this tenant.</p>

            <template v-if="tenant.subscription">
              <label class="pa-field" style="margin-top: 16px">
                <span class="pa-field__label">Reason (kept with the decision, shown to the owner)</span>
                <input v-model="rejectReason" class="pa-input" type="text" placeholder="Optional">
              </label>
              <div class="pa-actions">
                <button
                  class="pa-button"
                  type="button"
                  :disabled="busy === 'subscription' || tenant.subscription.status === 'active'"
                  @click="decideSubscription('active')"
                >
                  Mark verified
                </button>
                <button
                  class="pa-button pa-button--danger"
                  type="button"
                  :disabled="busy === 'subscription' || tenant.subscription.status === 'rejected'"
                  @click="decideSubscription('rejected')"
                >
                  Reject
                </button>
              </div>
            </template>
          </div>
        </section>

        <section class="pa-panel">
          <div class="pa-panel__head"><h2>Owner logins</h2></div>

          <p v-if="!tenant.admins.length" class="pa-empty">No owner account on this tenant.</p>

          <div v-else class="pa-table-wrap">
            <table class="pa-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Username</th>
                  <th>Login</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="owner in tenant.admins" :key="owner.uid">
                  <td>{{ owner.fullName }}</td>
                  <td class="pa-slug">{{ owner.username }}</td>
                  <td>
                    <span class="pa-badge" :class="owner.disabled ? 'pa-badge--bad' : 'pa-badge--good'">
                      {{ owner.disabled ? 'Disabled' : 'Enabled' }}
                    </span>
                  </td>
                  <td>
                    <div class="pa-actions--stacked pa-actions">
                      <button
                        class="pa-link"
                        type="button"
                        :disabled="busy === owner.uid"
                        @click="resetPassword(owner)"
                      >
                        Reset password
                      </button>
                      <button
                        class="pa-link"
                        type="button"
                        :disabled="busy === owner.uid"
                        @click="toggleOwner(owner)"
                      >
                        {{ owner.disabled ? 'Enable login' : 'Disable login' }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="pa-panel">
          <div class="pa-panel__head"><h2>Stores</h2></div>
          <div class="pa-table-wrap">
            <table class="pa-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Code</th>
                  <th>Type</th>
                  <th>Store code</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="store in tenant.stores" :key="store.id">
                  <td>
                    {{ store.name }}
                    <div v-if="store.address" class="pa-tile__note">{{ store.address }}</div>
                  </td>
                  <td class="pa-slug">{{ store.code }}</td>
                  <td>{{ store.businessMode ?? '—' }}</td>
                  <td class="pa-slug">{{ store.pairingCode ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="pa-panel">
          <div class="pa-panel__head"><h2>Account access</h2></div>
          <div class="pa-panel__body">
            <p class="pa-tile__note">
              Suspending locks everyone in this tenant out and keeps all their data. It is
              reversible. Deleting is not.
            </p>

            <label class="pa-field" style="margin-top: 14px">
              <span class="pa-field__label">Reason (kept in the audit log)</span>
              <input v-model="suspendReason" class="pa-input" type="text" placeholder="Optional">
            </label>

            <div class="pa-actions">
              <button
                class="pa-button pa-button--quiet"
                type="button"
                :disabled="busy === 'suspension'"
                @click="toggleSuspended"
              >
                {{ tenant.suspended ? 'Reactivate tenant' : 'Suspend tenant' }}
              </button>

              <!-- Owner-only on the server too; hiding it just avoids showing
                   a door that will not open. -->
              <button
                v-if="session.isOwner.value"
                class="pa-button pa-button--danger"
                type="button"
                @click="confirmingDelete = true"
              >
                Delete tenant…
              </button>
            </div>
          </div>
        </section>
      </div>

      <section class="pa-panel">
        <div class="pa-panel__head">
          <h2>History</h2>
          <span class="pa-tile__note">Last 50 actions</span>
        </div>

        <p v-if="!trail.length" class="pa-empty">Nothing has been done to this tenant yet.</p>

        <div v-else class="pa-panel__body pa-trail">
          <div v-for="row in trail" :key="row.id" class="pa-trail__row">
            <span class="pa-trail__action">{{ row.action }}</span>
            <span class="pa-trail__meta">
              {{ row.actorName ?? row.actorEmail }} · {{ dateTime(row.createdAt) }}
            </span>
            <span v-if="row.context.reason" class="pa-trail__meta">“{{ row.context.reason }}”</span>
          </div>
        </div>
      </section>
    </div>

    <ConfirmDangerDialog
      v-if="confirmingDelete"
      title="Delete this tenant?"
      :copy="`Every order, product, shift and staff account belonging to ${tenant.organizationName} is removed for good. There is no undo and no backup restore from here.`"
      :confirm-phrase="tenant.organizationSlug"
      :busy="busy === 'delete'"
      @cancel="confirmingDelete = false"
      @confirm="confirmDelete"
    />
  </template>

  <p v-else class="pa-alert">{{ error || 'That tenant could not be found.' }}</p>
</template>
