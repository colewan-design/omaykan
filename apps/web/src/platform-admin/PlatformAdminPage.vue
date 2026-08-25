<script setup lang="ts">
import { Check, Copy } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

interface OrgAdmin {
  uid: string
  username: string
  fullName: string
  disabled: boolean
}

interface OrgRow {
  organizationSlug: string
  organizationName: string
  suspended: boolean
  store: { name: string; businessMode: string; pairingCode: string } | null
  subscription: {
    status: string
    plan: string
    amountCents: number
    gcashReference: string
    submittedAt: string | null
    verifiedAt: string | null
  } | null
  admins: OrgAdmin[]
}

const SECRET_STORAGE_KEY = 'platform_admin_secret'

const secretInput = ref('')
const secret = ref('')
const unlocked = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const rows = ref<OrgRow[]>([])
const busyKey = ref('')

const revealedPassword = ref<{ username: string; password: string } | null>(null)
const passwordCopied = ref(false)

const deleteTarget = ref<OrgRow | null>(null)
const deleteConfirmInput = ref('')
const deleteConfirmMatches = computed(
  () => deleteTarget.value !== null && deleteConfirmInput.value.trim() === deleteTarget.value.organizationSlug,
)

function formatPesos(amountCents: number) {
  return `₱${(amountCents / 100).toLocaleString('en-PH', { minimumFractionDigits: 2 })}`
}

function formatDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function subscriptionLabel(status: string | undefined) {
  if (status === 'active') return 'Verified'
  if (status === 'rejected') return 'Rejected'
  if (status) return 'Pending'
  return '—'
}

async function postAction(body: Record<string, unknown>) {
  const response = await fetch('/api/platform-admin', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, secret: secret.value }),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data.error || 'Something went wrong.')
  }
  return data
}

async function loadOrgs(candidateSecret: string) {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await fetch('/api/platform-admin', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'listOrgs', secret: candidateSecret }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(data.error || 'Something went wrong.')
    }
    rows.value = data.orgs ?? []
    secret.value = candidateSecret
    unlocked.value = true
    window.sessionStorage.setItem(SECRET_STORAGE_KEY, candidateSecret)
  } catch (err) {
    unlocked.value = false
    window.sessionStorage.removeItem(SECRET_STORAGE_KEY)
    errorMessage.value = err instanceof Error ? err.message : 'Something went wrong.'
  } finally {
    loading.value = false
  }
}

async function submitSecret() {
  if (!secretInput.value.trim()) return
  await loadOrgs(secretInput.value.trim())
}

async function refresh() {
  await loadOrgs(secret.value)
}

async function markVerified(row: OrgRow) {
  busyKey.value = row.organizationSlug
  try {
    await postAction({ action: 'verify', organizationSlug: row.organizationSlug })
    if (row.subscription) {
      row.subscription.status = 'active'
      row.subscription.verifiedAt = new Date().toISOString()
    }
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to verify that store.'
  } finally {
    busyKey.value = ''
  }
}

async function rejectSignup(row: OrgRow) {
  busyKey.value = row.organizationSlug
  try {
    await postAction({ action: 'reject', organizationSlug: row.organizationSlug })
    if (row.subscription) {
      row.subscription.status = 'rejected'
    }
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to reject that store.'
  } finally {
    busyKey.value = ''
  }
}

async function toggleSuspended(row: OrgRow) {
  busyKey.value = row.organizationSlug
  const nextSuspended = !row.suspended
  try {
    await postAction({
      action: nextSuspended ? 'suspendOrg' : 'reactivateOrg',
      organizationSlug: row.organizationSlug,
    })
    row.suspended = nextSuspended
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to update that store.'
  } finally {
    busyKey.value = ''
  }
}

async function resetPassword(row: OrgRow, admin: OrgAdmin) {
  busyKey.value = admin.uid
  try {
    const data = await postAction({
      action: 'resetOwnerPassword',
      organizationSlug: row.organizationSlug,
      uid: admin.uid,
    })
    revealedPassword.value = { username: admin.username, password: data.password }
    passwordCopied.value = false
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to reset that password.'
  } finally {
    busyKey.value = ''
  }
}

async function toggleDisabled(row: OrgRow, admin: OrgAdmin) {
  busyKey.value = admin.uid
  const nextDisabled = !admin.disabled
  try {
    await postAction({
      action: 'setOwnerDisabled',
      organizationSlug: row.organizationSlug,
      uid: admin.uid,
      disabled: nextDisabled,
    })
    admin.disabled = nextDisabled
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to update that login.'
  } finally {
    busyKey.value = ''
  }
}

async function copyRevealedPassword() {
  if (!revealedPassword.value) return
  try {
    await navigator.clipboard.writeText(revealedPassword.value.password)
    passwordCopied.value = true
    setTimeout(() => { passwordCopied.value = false }, 2000)
  } catch {
    // Clipboard permission denied — the password is still shown on screen to copy manually.
  }
}

function closeRevealedPassword() {
  revealedPassword.value = null
  passwordCopied.value = false
}

function startDelete(row: OrgRow) {
  deleteTarget.value = row
  deleteConfirmInput.value = ''
}

function cancelDelete() {
  deleteTarget.value = null
  deleteConfirmInput.value = ''
}

async function confirmDelete() {
  if (!deleteTarget.value || !deleteConfirmMatches.value) return
  const target = deleteTarget.value
  busyKey.value = target.organizationSlug
  try {
    await postAction({
      action: 'deleteOrg',
      organizationSlug: target.organizationSlug,
      confirmSlug: deleteConfirmInput.value.trim(),
    })
    rows.value = rows.value.filter((r) => r.organizationSlug !== target.organizationSlug)
    deleteTarget.value = null
    deleteConfirmInput.value = ''
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to delete that store.'
  } finally {
    busyKey.value = ''
  }
}

onMounted(() => {
  const stored = window.sessionStorage.getItem(SECRET_STORAGE_KEY)
  if (stored) {
    void loadOrgs(stored)
  }
})
</script>

<template>
  <div class="pa-page">
    <section v-if="!unlocked" class="auth-page">
      <section class="auth-card">
        <div class="auth-brand">
          <div class="auth-brand-mark">B</div>
          <strong>Platform admin</strong>
        </div>
        <form class="auth-form" @submit.prevent="submitSecret">
          <label class="settings-field">
            <span class="settings-row__label">Secret</span>
            <input v-model="secretInput" class="sheet-input" type="password" autocomplete="off">
          </label>
          <button class="primary-button auth-submit" type="submit" :disabled="loading">
            {{ loading ? 'Checking…' : 'Unlock' }}
          </button>
        </form>
        <p v-if="errorMessage" class="auth-error">{{ errorMessage }}</p>
      </section>
    </section>

    <section v-else class="pa-content">
      <div class="pa-header">
        <div>
          <h1 class="pa-title">Stores</h1>
          <p class="pa-copy">Review signups, and manage every store's account and access.</p>
        </div>
        <button class="segment-button" type="button" :disabled="loading" @click="refresh">
          {{ loading ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>

      <p v-if="errorMessage" class="auth-error">{{ errorMessage }}</p>

      <div v-if="revealedPassword" class="pa-reveal">
        <div>
          <p class="pa-reveal__title">New password for {{ revealedPassword.username }}</p>
          <p class="pa-reveal__copy">Share this with the owner now — it won't be shown again.</p>
        </div>
        <div class="pa-reveal__value">{{ revealedPassword.password }}</div>
        <button class="settings-upload-button" type="button" @click="copyRevealedPassword">
          <Check v-if="passwordCopied" :size="16" />
          <Copy v-else :size="16" />
          <span>{{ passwordCopied ? 'Copied' : 'Copy' }}</span>
        </button>
        <button class="segment-button" type="button" @click="closeRevealedPassword">Done</button>
      </div>

      <div class="pa-table-wrap surface-panel">
        <table class="pa-table">
          <thead>
            <tr>
              <th>Store</th>
              <th>Owner</th>
              <th>Business type</th>
              <th>Subscription</th>
              <th>Account</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.organizationSlug">
              <td>
                <strong>{{ row.organizationName }}</strong>
                <div class="pa-slug">{{ row.organizationSlug }}</div>
              </td>
              <td>
                <div v-if="!row.admins.length" class="pa-empty-cell">—</div>
                <div v-for="admin in row.admins" :key="admin.uid" class="pa-admin">
                  <div class="pa-admin__info">
                    <span>{{ admin.username }}</span>
                    <span v-if="admin.disabled" class="pa-badge pa-badge--danger">Login disabled</span>
                  </div>
                  <div class="pa-admin__actions">
                    <button
                      class="pa-link-button"
                      type="button"
                      :disabled="busyKey === admin.uid"
                      @click="resetPassword(row, admin)"
                    >
                      Reset password
                    </button>
                    <button
                      class="pa-link-button"
                      type="button"
                      :disabled="busyKey === admin.uid"
                      @click="toggleDisabled(row, admin)"
                    >
                      {{ admin.disabled ? 'Enable login' : 'Disable login' }}
                    </button>
                  </div>
                </div>
              </td>
              <td>{{ row.store?.businessMode ?? '—' }}</td>
              <td>
                <span
                  class="pa-badge"
                  :class="{
                    'pa-badge--active': row.subscription?.status === 'active',
                    'pa-badge--danger': row.subscription?.status === 'rejected',
                  }"
                >
                  {{ subscriptionLabel(row.subscription?.status) }}
                </span>
                <div v-if="row.subscription" class="pa-sub-detail">
                  {{ formatPesos(row.subscription.amountCents) }} · {{ row.subscription.gcashReference }}
                  <div class="pa-slug">Submitted {{ formatDate(row.subscription.submittedAt) }}</div>
                </div>
                <div v-if="row.subscription?.status === 'pending_verification'" class="pa-row-actions">
                  <button
                    class="segment-button"
                    type="button"
                    :disabled="busyKey === row.organizationSlug"
                    @click="markVerified(row)"
                  >
                    Verify
                  </button>
                  <button
                    class="pa-link-button"
                    type="button"
                    :disabled="busyKey === row.organizationSlug"
                    @click="rejectSignup(row)"
                  >
                    Reject
                  </button>
                </div>
              </td>
              <td>
                <span class="pa-badge" :class="row.suspended ? 'pa-badge--danger' : 'pa-badge--active'">
                  {{ row.suspended ? 'Suspended' : 'Active' }}
                </span>
                <div class="pa-row-actions">
                  <button
                    class="pa-link-button"
                    type="button"
                    :disabled="busyKey === row.organizationSlug"
                    @click="toggleSuspended(row)"
                  >
                    {{ row.suspended ? 'Reactivate' : 'Suspend' }}
                  </button>
                </div>
              </td>
              <td>
                <button
                  class="outline-danger-button"
                  type="button"
                  :disabled="busyKey === row.organizationSlug"
                  @click="startDelete(row)"
                >
                  Delete
                </button>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="pa-empty">No stores yet.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="deleteTarget" class="pa-modal-backdrop" @click.self="cancelDelete">
      <div class="pa-modal">
        <h2 class="pa-modal__title">Delete "{{ deleteTarget.organizationName }}"?</h2>
        <p class="pa-modal__copy">
          This permanently deletes the store's products, orders, staff accounts, and every other record. This
          cannot be undone.
        </p>
        <label class="settings-field">
          <span class="settings-row__label">Type <strong>{{ deleteTarget.organizationSlug }}</strong> to confirm</span>
          <input v-model="deleteConfirmInput" class="sheet-input" type="text" autocomplete="off">
        </label>
        <div class="pa-modal-actions">
          <button class="segment-button" type="button" @click="cancelDelete">Cancel</button>
          <button
            class="danger-button"
            type="button"
            :disabled="!deleteConfirmMatches || busyKey === deleteTarget.organizationSlug"
            @click="confirmDelete"
          >
            {{ busyKey === deleteTarget.organizationSlug ? 'Deleting…' : 'Delete permanently' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pa-page {
  min-height: 100vh;
  background: var(--bg-canvas);
}

.pa-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-6) var(--space-4);
  display: grid;
  gap: var(--space-5);
}

.pa-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.pa-title {
  margin: 0;
  font: var(--type-title1);
  color: var(--text-primary);
}

.pa-copy {
  margin: 0;
  color: var(--text-secondary);
}

.pa-reveal {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--success) 10%, var(--bg-surface));
  border: 0.5px solid color-mix(in srgb, var(--success) 40%, transparent);
}

.pa-reveal__title {
  margin: 0;
  font-weight: 600;
  color: var(--text-primary);
}

.pa-reveal__copy {
  margin: 0;
  font: var(--type-caption);
  color: var(--text-secondary);
}

.pa-reveal__value {
  margin-left: auto;
  font: var(--type-title2);
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--text-primary);
}

.pa-table-wrap {
  overflow-x: auto;
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-xl);
}

.pa-table {
  width: 100%;
  border-collapse: collapse;
  font: var(--type-subhead);
}

.pa-table th,
.pa-table td {
  padding: var(--space-3) var(--space-4);
  text-align: left;
  vertical-align: top;
  border-bottom: 0.5px solid var(--separator);
  white-space: nowrap;
  color: var(--text-primary);
}

.pa-table th {
  color: var(--text-secondary);
  font: var(--type-caption);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.pa-table tr:last-child td {
  border-bottom: none;
}

.pa-slug {
  font: var(--type-caption);
  color: var(--text-tertiary);
}

.pa-sub-detail {
  margin-top: var(--space-1);
  font: var(--type-caption);
  color: var(--text-secondary);
  white-space: normal;
}

.pa-badge {
  display: inline-flex;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--warning) 14%, transparent);
  color: var(--warning);
  font: var(--type-caption);
  font-weight: 600;
}

.pa-badge--active {
  background: color-mix(in srgb, var(--success) 12%, transparent);
  color: var(--success);
}

.pa-badge--danger {
  background: color-mix(in srgb, var(--danger) 12%, transparent);
  color: var(--danger);
}

.pa-empty {
  text-align: center;
  color: var(--text-secondary);
}

.pa-empty-cell {
  color: var(--text-tertiary);
}

.pa-admin {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  white-space: normal;
}

.pa-admin:not(:last-child) {
  margin-bottom: var(--space-2);
}

.pa-admin__info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.pa-admin__actions {
  display: flex;
  gap: var(--space-3);
  flex-shrink: 0;
}

.pa-link-button {
  border: none;
  background: none;
  padding: 0;
  color: var(--accent);
  font: var(--type-caption);
  font-weight: 600;
  white-space: nowrap;
}

.pa-link-button:hover {
  text-decoration: underline;
}

.pa-link-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pa-row-actions {
  margin-top: var(--space-2);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.pa-modal-backdrop {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  padding: var(--space-4);
  background: rgb(0 0 0 / 45%);
  z-index: 100;
}

.pa-modal {
  width: 100%;
  max-width: 420px;
  padding: var(--space-5);
  border-radius: var(--radius-xl);
  background: var(--bg-surface);
  display: grid;
  gap: var(--space-3);
}

.pa-modal__title {
  margin: 0;
  font: var(--type-title2);
  color: var(--text-primary);
}

.pa-modal__copy {
  margin: 0;
  color: var(--text-secondary);
}

.pa-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
