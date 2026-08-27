<script setup lang="ts">
import { Check, Copy } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import Customers from './Customers.vue'
import RiderReview from './RiderReview.vue'
import SupportInbox from './SupportInbox.vue'

interface OrgAdmin {
  uid: string
  username: string
  fullName: string
  email: string | null
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

/*
 * sessionStorage, not localStorage: the operator token outlives a reload but
 * not the tab. This dashboard can delete a tenant, so leaving a live session
 * on a machine someone walks away from is not a convenience worth having.
 */
const TOKEN_STORAGE_KEY = 'platform_admin_token'

/*
 * Two queues behind one sign-in: the stores waiting on a subscription check,
 * and the riders waiting on a licence check. The rider side is mounted only
 * once it is switched to, so signing in does not pull a queue of identity
 * documents nobody asked to see.
 */
const view = ref<'stores' | 'riders' | 'customers' | 'inbox'>('stores')

const emailInput = ref('')
const passwordInput = ref('')
const token = ref('')
const operator = ref<{ name: string; email: string } | null>(null)
const unlocked = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const rows = ref<OrgRow[]>([])
const busyKey = ref('')

const revealedPassword = ref<{ username: string; password: string } | null>(null)
const passwordCopied = ref(false)

const emailComposer = ref<{
  row: OrgRow
  admin: OrgAdmin
  subject: string
  message: string
} | null>(null)
const emailComposerError = ref('')

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

/**
 * Drops the session and returns to the sign-in screen. Called both on an
 * explicit sign-out and whenever the API says the token is no longer good —
 * a 401 (expired or revoked) or a 403 (the account was disabled).
 */
function endSession(message = '') {
  token.value = ''
  operator.value = null
  unlocked.value = false
  rows.value = []
  passwordInput.value = ''
  window.sessionStorage.removeItem(TOKEN_STORAGE_KEY)
  errorMessage.value = message
}

async function postAction(body: Record<string, unknown>) {
  successMessage.value = ''
  const response = await fetch('/api/platform-admin', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      Authorization: `Bearer ${token.value}`,
    },
    body: JSON.stringify(body),
  })
  const data = await response.json().catch(() => ({}))
  if (response.status === 401 || response.status === 403) {
    endSession(data.message || 'Your session has ended. Sign in again.')
    throw new Error(data.message || 'Your session has ended. Sign in again.')
  }
  if (!response.ok) {
    throw new Error(data.error || data.message || 'Something went wrong.')
  }
  return data
}

async function loadOrgs() {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const data = await postAction({ action: 'listOrgs' })
    rows.value = data.organizations ?? data.orgs ?? []
    unlocked.value = true
  } catch (err) {
    // A dead session has already been cleared by postAction, and its message
    // is the one worth keeping; anything else is a transient failure that
    // should not throw the operator back to the sign-in screen.
    if (unlocked.value) {
      errorMessage.value = err instanceof Error ? err.message : 'Something went wrong.'
    }
  } finally {
    loading.value = false
  }
}

async function signIn() {
  if (!emailInput.value.trim() || !passwordInput.value) return

  loading.value = true
  errorMessage.value = ''
  try {
    const response = await fetch('/api/platform-admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({
        email: emailInput.value.trim(),
        password: passwordInput.value,
      }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      // 422 from Laravel carries the field errors; the single message under
      // `email` is the deliberately vague one, so show it as-is.
      throw new Error(data.errors?.email?.[0] || data.message || 'Unable to sign in.')
    }
    token.value = data.token
    operator.value = { name: data.admin.name, email: data.admin.email }
    // Cleared as soon as it has been exchanged for a token — there is no
    // reason for the password to stay in memory for the rest of the session.
    passwordInput.value = ''
    window.sessionStorage.setItem(TOKEN_STORAGE_KEY, data.token)
    await loadOrgs()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to sign in.'
  } finally {
    loading.value = false
  }
}

async function signOut() {
  const current = token.value
  endSession()
  // Best-effort: the local session is already gone, and a failed request here
  // must not leave the operator looking at a dashboard they just left.
  await fetch('/api/platform-admin/logout', {
    method: 'POST',
    headers: { Accept: 'application/json', Authorization: `Bearer ${current}` },
  }).catch(() => undefined)
}

async function refresh() {
  await loadOrgs()
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

function startEmailComposer(row: OrgRow, admin: OrgAdmin) {
  if (!admin.email) return
  emailComposer.value = {
    row,
    admin,
    subject: `Re: ${row.organizationName}`,
    message: '',
  }
  emailComposerError.value = ''
}

function closeEmailComposer() {
  emailComposer.value = null
  emailComposerError.value = ''
}

async function sendOwnerEmail() {
  if (!emailComposer.value) return

  const subject = emailComposer.value.subject.trim()
  const message = emailComposer.value.message.trim()

  if (!subject || !message) {
    emailComposerError.value = 'Add both a subject and a message.'
    return
  }

  busyKey.value = `email:${emailComposer.value.admin.uid}`
  emailComposerError.value = ''

  try {
    await postAction({
      action: 'sendOwnerEmail',
      organizationSlug: emailComposer.value.row.organizationSlug,
      uid: emailComposer.value.admin.uid,
      subject,
      message,
    })
    successMessage.value = `Email queued for ${emailComposer.value.admin.email}.`
    closeEmailComposer()
  } catch (err) {
    emailComposerError.value = err instanceof Error ? err.message : 'Unable to send that email.'
  } finally {
    busyKey.value = ''
  }
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

/*
 * Restores the session across a reload. `/me` rather than going straight to
 * listOrgs: it confirms the token is still good and gets the operator's name
 * back for the header, and a stale token is cleared here rather than surfacing
 * as an error on the first thing the operator clicks.
 */
onMounted(async () => {
  const stored = window.sessionStorage.getItem(TOKEN_STORAGE_KEY)
  if (!stored) return

  token.value = stored
  loading.value = true
  try {
    const response = await fetch('/api/platform-admin/me', {
      headers: { Accept: 'application/json', Authorization: `Bearer ${stored}` },
    })
    if (!response.ok) {
      endSession()
      return
    }
    const data = await response.json()
    operator.value = { name: data.admin.name, email: data.admin.email }
    unlocked.value = true
    await loadOrgs()
  } catch {
    endSession()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="pa-page">
    <section v-if="!unlocked" class="auth-page">
      <section class="auth-card">
        <div class="auth-brand auth-brand--stacked">
          <BrandLogo variant="light" :size="21" />
          <strong>Platform admin</strong>
        </div>
        <form class="auth-form" @submit.prevent="signIn">
          <label class="settings-field">
            <span class="settings-row__label">Email</span>
            <input
              v-model="emailInput"
              class="sheet-input"
              type="email"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
            >
          </label>
          <label class="settings-field">
            <span class="settings-row__label">Password</span>
            <input
              v-model="passwordInput"
              class="sheet-input"
              type="password"
              autocomplete="current-password"
            >
          </label>
          <button class="primary-button auth-submit" type="submit" :disabled="loading">
            {{ loading ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
        <p v-if="errorMessage" class="auth-error">{{ errorMessage }}</p>
      </section>
    </section>

    <section v-else class="pa-content">
      <nav class="pa-views">
        <button
          class="pa-view"
          :class="{ 'pa-view--on': view === 'stores' }"
          type="button"
          @click="view = 'stores'"
        >
          Stores
        </button>
        <button
          class="pa-view"
          :class="{ 'pa-view--on': view === 'riders' }"
          type="button"
          @click="view = 'riders'"
        >
          Riders
        </button>
        <button
          class="pa-view"
          :class="{ 'pa-view--on': view === 'customers' }"
          type="button"
          @click="view = 'customers'"
        >
          Customers
        </button>
        <button
          class="pa-view"
          :class="{ 'pa-view--on': view === 'inbox' }"
          type="button"
          @click="view = 'inbox'"
        >
          Inbox
        </button>
        <span v-if="operator" class="pa-operator">
          {{ operator.name }}
          <button class="pa-signout" type="button" @click="signOut">Sign out</button>
        </span>
      </nav>

      <template v-if="view === 'stores'">
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
      <p v-else-if="successMessage" class="pa-success">{{ successMessage }}</p>

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
                    <div class="pa-admin__identity">
                      <strong>{{ admin.fullName || admin.username }}</strong>
                      <span class="pa-slug">@{{ admin.username }}</span>
                      <span class="pa-slug">{{ admin.email || 'No email on file' }}</span>
                    </div>
                    <span v-if="admin.disabled" class="pa-badge pa-badge--danger">Login disabled</span>
                  </div>
                  <div class="pa-admin__actions">
                    <button
                      class="pa-link-button"
                      type="button"
                      :disabled="!admin.email || busyKey === `email:${admin.uid}`"
                      @click="startEmailComposer(row, admin)"
                    >
                      Email
                    </button>
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
      </template>

      <RiderReview v-else-if="view === 'riders'" :token="token" @session-ended="endSession" />
      <Customers v-else-if="view === 'customers'" :token="token" @session-ended="endSession" />
      <SupportInbox v-else :token="token" @session-ended="endSession" />
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

    <div v-if="emailComposer" class="pa-modal-backdrop" @click.self="closeEmailComposer">
      <div class="pa-modal pa-modal--wide">
        <h2 class="pa-modal__title">Email {{ emailComposer.admin.fullName || emailComposer.admin.username }}</h2>
        <p class="pa-modal__copy">
          This will send from the Omaykan mailbox to {{ emailComposer.admin.email }}.
        </p>
        <label class="settings-field">
          <span class="settings-row__label">Subject</span>
          <input v-model="emailComposer.subject" class="sheet-input" type="text" maxlength="190">
        </label>
        <label class="settings-field">
          <span class="settings-row__label">Message</span>
          <textarea v-model="emailComposer.message" class="sheet-input pa-textarea" rows="8" maxlength="5000"></textarea>
        </label>
        <p v-if="emailComposerError" class="auth-error">{{ emailComposerError }}</p>
        <div class="pa-modal-actions">
          <button
            class="segment-button"
            type="button"
            :disabled="busyKey === `email:${emailComposer.admin.uid}`"
            @click="closeEmailComposer"
          >
            Cancel
          </button>
          <button
            class="primary-button"
            type="button"
            :disabled="busyKey === `email:${emailComposer.admin.uid}`"
            @click="sendOwnerEmail"
          >
            {{ busyKey === `email:${emailComposer.admin.uid}` ? 'Sending…' : 'Send email' }}
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

.pa-views {
  display: flex;
  gap: 8px;
}

.pa-view {
  height: 36px;
  padding: 0 18px;
  border: 1px solid var(--border-subtle, rgba(0, 0, 0, 0.12));
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font: 600 14px/1 inherit;
  cursor: pointer;
}

.pa-view--on {
  border-color: transparent;
  background: var(--accent, #1a6b3c);
  color: #fff;
}

/* Pushed to the far end of the nav — who you are signed in as, and the way out. */
.pa-operator {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  color: var(--text-secondary);
  font: 600 13px/1 inherit;
}

.pa-signout {
  border: 0;
  padding: 0;
  background: none;
  color: var(--text-secondary);
  font: 600 13px/1 inherit;
  text-decoration: underline;
  cursor: pointer;
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

.pa-admin__identity {
  display: grid;
  gap: 2px;
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

.pa-modal--wide {
  max-width: 560px;
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

.pa-textarea {
  min-height: 180px;
  resize: vertical;
}

.pa-success {
  margin: 0;
  padding: 12px 14px;
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--success) 10%, var(--bg-surface));
  border: 0.5px solid color-mix(in srgb, var(--success) 40%, transparent);
  color: var(--success);
  font: var(--type-caption);
  font-weight: 600;
}
</style>
