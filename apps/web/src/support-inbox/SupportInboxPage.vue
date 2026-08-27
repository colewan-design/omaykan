<script setup lang="ts">
import { ArrowLeft, Mail, MailOpen, RefreshCw, Search } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

const TOKEN_STORAGE_KEY = 'omk_platform_token'

interface InboxSummary {
  id: string
  subject: string
  fromName: string
  fromEmail: string
  receivedAt: string | null
  snippet: string
  isRead: boolean
}

interface InboxMessage extends InboxSummary {
  body: string
  replyToName: string
  replyToEmail: string
  messageIdHeader: string
  references: string
  inReplyTo: string
}

interface PlatformAdmin {
  id?: string
  name: string
  email: string
  role?: string
}

function currentToken(): string {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY) ?? ''
}

function authHeaders(json = false): Record<string, string> {
  return {
    Accept: 'application/json',
    Authorization: `Bearer ${currentToken()}`,
    ...(json ? { 'Content-Type': 'application/json' } : {}),
  }
}

const loading = ref(false)
const loadingMessage = ref(false)
const sending = ref(false)
const checkingSession = ref(true)
const errorMessage = ref('')
const successMessage = ref('')
const unreadCount = ref(0)
const messages = ref<InboxSummary[]>([])
const selectedId = ref('')
const selected = ref<InboxMessage | null>(null)
const replySubject = ref('')
const replyBody = ref('')
const replyError = ref('')
const searchQuery = ref('')
const unreadOnly = ref(false)
const signedIn = ref(false)
const admin = ref<PlatformAdmin | null>(null)

const filteredMessages = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()

  return messages.value.filter((message) => {
    const matchesUnread = !unreadOnly.value || !message.isRead || message.id === selectedId.value
    if (!matchesUnread) return false
    if (!query) return true

    return [message.subject, message.fromName, message.fromEmail, message.snippet]
      .join(' ')
      .toLowerCase()
      .includes(query)
  })
})

const selectedSummary = computed(() => messages.value.find((message) => message.id === selectedId.value) ?? null)
const selectedReplyEmail = computed(() => selected.value?.replyToEmail || selected.value?.fromEmail || '')

function clearSession(message = 'Your operator session has ended. Sign in again.') {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY)
  signedIn.value = false
  admin.value = null
  messages.value = []
  unreadCount.value = 0
  selectedId.value = ''
  selected.value = null
  replySubject.value = ''
  replyBody.value = ''
  errorMessage.value = message
}

function formatDate(value: string | null): string {
  if (!value) return 'Unknown time'
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function formatListDate(value: string | null): string {
  if (!value) return '--'

  const date = new Date(value)
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()

  return new Intl.DateTimeFormat(
    'en-PH',
    sameDay ? { hour: '2-digit', minute: '2-digit' } : { month: 'short', day: 'numeric' },
  ).format(date)
}

function defaultReplySubject(subject: string): string {
  return /^re:/i.test(subject.trim()) ? subject.trim() : `Re: ${subject.trim() || 'your message to Omaykan support'}`
}

async function parseJson(response: Response) {
  return response.json().catch(() => ({}))
}

async function ensureSession() {
  checkingSession.value = true
  errorMessage.value = ''

  const token = currentToken()
  if (!token) {
    signedIn.value = false
    checkingSession.value = false
    return
  }

  try {
    const response = await fetch('/api/platform/me', {
      headers: authHeaders(),
    })
    const data = await parseJson(response)

    if (response.status === 401 || response.status === 403) {
      clearSession(data.message || 'Your operator session has ended. Sign in again.')
      return
    }
    if (!response.ok) {
      throw new Error(data.message || 'Unable to verify your operator session.')
    }

    admin.value = data.admin
    signedIn.value = true
    await loadList()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to verify your operator session.'
  } finally {
    checkingSession.value = false
  }
}

async function loadList(selectMessageId?: string) {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const response = await fetch('/api/platform/inbox?limit=30', {
      headers: authHeaders(),
    })
    const data = await parseJson(response)

    if (response.status === 401 || response.status === 403) {
      clearSession(data.message || 'Your operator session has ended. Sign in again.')
      return
    }
    if (!response.ok) {
      throw new Error(data.message || data.error || 'Unable to load the support inbox.')
    }

    messages.value = data.messages ?? []
    unreadCount.value = data.unreadCount ?? 0

    const nextId = selectMessageId ?? selectedId.value ?? messages.value[0]?.id
    if (nextId) {
      await openMessage(nextId)
    } else {
      selectedId.value = ''
      selected.value = null
      replySubject.value = ''
      replyBody.value = ''
    }
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to load the support inbox.'
  } finally {
    loading.value = false
  }
}

async function openMessage(messageId: string) {
  selectedId.value = messageId
  loadingMessage.value = true
  errorMessage.value = ''
  replyError.value = ''

  try {
    const response = await fetch(`/api/platform/inbox/${messageId}`, {
      headers: authHeaders(),
    })
    const data = await parseJson(response)

    if (response.status === 401 || response.status === 403) {
      clearSession(data.message || 'Your operator session has ended. Sign in again.')
      return
    }
    if (!response.ok) {
      throw new Error(data.message || data.error || 'Unable to load that message.')
    }

    selected.value = data.message
    replySubject.value = defaultReplySubject(data.message?.subject ?? '')
    messages.value = messages.value.map((message) =>
      message.id === messageId
        ? {
            ...message,
            isRead: true,
            snippet: data.message?.snippet ?? message.snippet,
          }
        : message,
    )
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to load that message.'
  } finally {
    loadingMessage.value = false
  }
}

async function sendReply() {
  if (!selected.value) return

  const subject = replySubject.value.trim()
  const message = replyBody.value.trim()

  if (!subject || !message) {
    replyError.value = 'Add both a subject and a reply.'
    return
  }

  sending.value = true
  successMessage.value = ''
  replyError.value = ''

  try {
    const response = await fetch(`/api/platform/inbox/${selected.value.id}/reply`, {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ subject, message }),
    })
    const data = await parseJson(response)

    if (response.status === 401 || response.status === 403) {
      clearSession(data.message || 'Your operator session has ended. Sign in again.')
      return
    }
    if (!response.ok) {
      throw new Error(data.message || data.error || 'Unable to send that reply.')
    }

    successMessage.value = `Reply queued for ${data.to}.`
    replyBody.value = ''
    await loadList(selected.value.id)
  } catch (err) {
    replyError.value = err instanceof Error ? err.message : 'Unable to send that reply.'
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  void ensureSession()
})
</script>

<template>
  <section class="si-page">
    <div class="si-frame">
      <header class="si-topbar">
        <a class="si-back" href="/platform-admin">
          <ArrowLeft :size="15" />
          <span>Back to portal</span>
        </a>

        <div v-if="admin" class="si-operator">
          <span>{{ admin.name }}</span>
          <span>{{ admin.email }}</span>
        </div>
      </header>

      <section v-if="checkingSession" class="si-gate surface-panel">
        <h1>Checking session</h1>
        <p>Loading your support inbox access.</p>
      </section>

      <section v-else-if="!signedIn" class="si-gate surface-panel">
        <h1>Operator sign-in required</h1>
        <p>Open the platform portal first, then come back here to work the support inbox.</p>
        <a class="primary-button si-gate__action" href="/platform-admin">Go to platform sign-in</a>
      </section>

      <section v-else class="si-shell surface-panel">
        <header class="si-header">
          <div class="si-header__copy">
            <h1 class="si-title">Support Inbox</h1>
            <p class="si-copy">
              Read messages sent to support@omaykan.com and reply without leaving the inbox.
            </p>
          </div>

          <div class="si-header__actions">
            <span class="si-counter">{{ unreadCount }} unread</span>
            <button class="si-refresh" type="button" :disabled="loading" @click="loadList()">
              <RefreshCw :size="14" :class="{ 'si-spin': loading }" />
              <span>{{ loading ? 'Refreshing...' : 'Refresh' }}</span>
            </button>
          </div>
        </header>

        <p v-if="errorMessage" class="auth-error si-banner si-banner--error">{{ errorMessage }}</p>
        <p v-else-if="successMessage" class="si-banner si-banner--success">{{ successMessage }}</p>

        <div class="si-toolbar">
          <label class="si-search" aria-label="Search inbox">
            <Search :size="15" />
            <input
              v-model="searchQuery"
              type="search"
              placeholder="Search sender, subject or snippet"
              autocomplete="off"
            >
          </label>

          <button
            class="si-chip"
            :class="{ 'si-chip--active': unreadOnly }"
            type="button"
            @click="unreadOnly = !unreadOnly"
          >
            {{ unreadOnly ? 'Unread only on' : 'Unread only' }}
          </button>

          <div class="si-toolbar__meta">
            <span>{{ filteredMessages.length }} shown</span>
            <span>{{ messages.length }} total</span>
          </div>
        </div>

        <div class="si-layout">
          <aside class="si-list" aria-label="Support messages">
            <div class="si-list__head">
              <span>Messages</span>
              <span>Received</span>
            </div>

            <div v-if="!filteredMessages.length && !loading" class="si-empty si-empty--list">
              {{ searchQuery.trim() ? 'No messages match that search.' : 'No support messages yet.' }}
            </div>

            <button
              v-for="message in filteredMessages"
              :key="message.id"
              class="si-row"
              :class="{ 'si-row--active': message.id === selectedId, 'si-row--unread': !message.isRead }"
              type="button"
              @click="openMessage(message.id)"
            >
              <div class="si-row__status">
                <span class="si-dot" :class="{ 'si-dot--muted': message.isRead }"></span>
                <component :is="message.isRead ? MailOpen : Mail" :size="15" />
              </div>

              <div class="si-row__content">
                <div class="si-row__top">
                  <strong>{{ message.fromName }}</strong>
                  <span>{{ message.subject }}</span>
                </div>
                <div class="si-row__meta">
                  <span>{{ message.fromEmail }}</span>
                  <span>{{ message.snippet }}</span>
                </div>
              </div>

              <time class="si-row__time">{{ formatListDate(message.receivedAt) }}</time>
            </button>
          </aside>

          <section class="si-thread">
            <template v-if="selected">
              <div class="si-thread__header">
                <div>
                  <h2 class="si-thread__subject">{{ selected.subject }}</h2>
                  <div class="si-thread__meta">
                    <span>From {{ selected.fromName }} &lt;{{ selected.fromEmail }}&gt;</span>
                    <span>Reply to {{ selected.replyToName }} &lt;{{ selectedReplyEmail }}&gt;</span>
                    <span>{{ formatDate(selected.receivedAt) }}</span>
                  </div>
                </div>
                <span class="si-thread__badge">{{ selectedSummary?.isRead ? 'Opened' : 'New' }}</span>
              </div>

              <div class="si-thread__body">
                {{ selected.body || selectedSummary?.snippet || 'No message body available.' }}
              </div>

              <form class="si-reply" @submit.prevent="sendReply">
                <div class="si-reply__header">
                  <h3 class="si-reply__title">Reply</h3>
                  <span class="si-reply__hint">Sent from support@omaykan.com</span>
                </div>

                <label class="si-field">
                  <span>Subject</span>
                  <input v-model="replySubject" class="sheet-input si-input" type="text" maxlength="190">
                </label>

                <label class="si-field">
                  <span>Message</span>
                  <textarea
                    v-model="replyBody"
                    class="sheet-input si-input si-input--body"
                    rows="7"
                    maxlength="5000"
                  ></textarea>
                </label>

                <p v-if="replyError" class="auth-error si-reply__error">{{ replyError }}</p>

                <div class="si-reply__actions">
                  <span class="si-reply__caption">
                    Replying to {{ selected.replyToName || selected.fromName }}
                  </span>
                  <button class="primary-button si-send" type="submit" :disabled="sending">
                    {{ sending ? 'Sending...' : 'Send reply' }}
                  </button>
                </div>
              </form>
            </template>

            <div v-else class="si-empty si-empty--thread">
              {{ loadingMessage ? 'Loading message...' : 'Select a message to read and reply.' }}
            </div>
          </section>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.si-page {
  --si-bg: #18181b;
  --si-border: rgba(255, 255, 255, 0.08);
  --si-border-strong: rgba(255, 255, 255, 0.12);
  --si-text: #f5f7fb;
  --si-muted: #a0a6b2;
  --si-subtle: #6f7685;
  --si-success: #71d49f;
  min-height: 100vh;
  padding: 28px 18px 40px;
  background:
    radial-gradient(circle at top left, rgba(94, 151, 255, 0.16), transparent 28%),
    radial-gradient(circle at top right, rgba(74, 143, 117, 0.14), transparent 24%),
    #eef1f5;
}

.si-frame {
  width: min(1380px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 14px;
}

.si-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.si-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #445161;
  font: 700 0.82rem/1 var(--font-sans, inherit);
  text-decoration: none;
}

.si-operator {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #64707d;
  font: 600 0.8rem/1.3 var(--font-sans, inherit);
}

.si-gate {
  display: grid;
  gap: 12px;
  max-width: 540px;
  padding: 28px;
  margin: 32px auto 0;
  border-radius: 24px;
}

.si-gate h1,
.si-gate p {
  margin: 0;
}

.si-gate h1 {
  color: #1f2933;
  font: 700 1.45rem/1.1 var(--font-sans, inherit);
}

.si-gate p {
  color: #52606d;
  font: 500 0.96rem/1.55 var(--font-sans, inherit);
}

.si-gate__action {
  width: fit-content;
  text-decoration: none;
}

.si-shell {
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--si-border);
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 16%),
    var(--si-bg);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.18);
}

.si-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px 14px;
  border-bottom: 1px solid var(--si-border);
}

.si-header__copy {
  min-width: 0;
}

.si-title {
  margin: 0;
  color: var(--si-text);
  font: 700 1.3rem/1.1 var(--font-sans, inherit);
}

.si-copy {
  margin: 6px 0 0;
  max-width: 720px;
  color: var(--si-muted);
  font: 500 0.92rem/1.5 var(--font-sans, inherit);
}

.si-header__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.si-counter,
.si-thread__badge,
.si-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--si-border-strong);
  border-radius: 999px;
  font: 600 0.72rem/1 var(--font-sans, inherit);
  letter-spacing: 0.03em;
}

.si-counter {
  min-height: 32px;
  padding: 0 12px;
  color: var(--si-muted);
  background: rgba(255, 255, 255, 0.04);
}

.si-refresh {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--si-border-strong);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--si-text);
  font: 600 0.78rem/1 var(--font-sans, inherit);
  cursor: pointer;
}

.si-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.si-spin {
  animation: si-spin 0.9s linear infinite;
}

.si-banner {
  margin: 0 24px;
  padding: 10px 12px;
  border-radius: 12px;
  font: 600 0.82rem/1.4 var(--font-sans, inherit);
}

.si-banner--success {
  margin-top: 14px;
  border: 1px solid rgba(113, 212, 159, 0.22);
  background: rgba(113, 212, 159, 0.09);
  color: var(--si-success);
}

.si-banner--error {
  margin-top: 14px;
}

.si-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  border-bottom: 1px solid var(--si-border);
}

.si-search {
  flex: 1 1 320px;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--si-border-strong);
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.2);
  color: var(--si-subtle);
}

.si-search input {
  flex: 1 1 auto;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--si-text);
  font: 500 0.84rem/1.2 var(--font-sans, inherit);
}

.si-search input::placeholder {
  color: var(--si-subtle);
}

.si-chip {
  min-height: 34px;
  padding: 0 12px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--si-muted);
  cursor: pointer;
}

.si-chip--active {
  border-color: color-mix(in srgb, var(--accent) 44%, white 18%);
  background: color-mix(in srgb, var(--accent) 18%, transparent);
  color: var(--si-text);
}

.si-toolbar__meta {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--si-subtle);
  font: 500 0.78rem/1 var(--font-sans, inherit);
  white-space: nowrap;
}

.si-layout {
  display: grid;
  grid-template-columns: minmax(360px, 440px) minmax(0, 1fr);
  min-height: 720px;
}

.si-list {
  display: grid;
  align-content: start;
  border-right: 1px solid var(--si-border);
  background: rgba(255, 255, 255, 0.015);
}

.si-list__head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 84px;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--si-border);
  color: var(--si-subtle);
  font: 700 0.67rem/1 var(--font-sans, inherit);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.si-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
  width: 100%;
  padding: 14px 18px;
  border: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: transparent;
  color: var(--si-text);
  text-align: left;
  cursor: pointer;
  transition: background 140ms ease;
}

.si-row:hover {
  background: rgba(255, 255, 255, 0.035);
}

.si-row--active {
  background: linear-gradient(90deg, color-mix(in srgb, var(--accent) 14%, transparent), rgba(255, 255, 255, 0.025));
  box-shadow: inset 2px 0 0 color-mix(in srgb, var(--accent) 72%, white 8%);
}

.si-row--unread .si-row__top strong,
.si-row--unread .si-row__top span {
  color: #ffffff;
}

.si-row__status {
  display: grid;
  justify-items: center;
  gap: 7px;
  padding-top: 2px;
  color: var(--si-subtle);
}

.si-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 76%, white 10%);
}

.si-dot--muted {
  background: rgba(255, 255, 255, 0.18);
}

.si-row__content {
  min-width: 0;
}

.si-row__top,
.si-row__meta,
.si-thread__meta {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.si-row__top {
  align-items: baseline;
  margin-bottom: 6px;
}

.si-row__top strong,
.si-row__top span,
.si-row__meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.si-row__top strong {
  flex: 0 1 auto;
  max-width: 42%;
  color: var(--si-text);
  font: 700 0.86rem/1.2 var(--font-sans, inherit);
}

.si-row__top span {
  flex: 1 1 auto;
  color: #e7e9ee;
  font: 600 0.84rem/1.2 var(--font-sans, inherit);
}

.si-row__meta {
  color: var(--si-muted);
  font: 500 0.76rem/1.2 var(--font-sans, inherit);
}

.si-row__meta span:first-child {
  flex: 0 0 42%;
}

.si-row__meta span:last-child {
  flex: 1 1 auto;
  color: var(--si-subtle);
}

.si-row__time {
  padding-top: 2px;
  color: var(--si-subtle);
  font: 600 0.72rem/1.2 var(--font-sans, inherit);
  white-space: nowrap;
}

.si-thread {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.015), transparent 20%);
}

.si-thread__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px 18px;
  border-bottom: 1px solid var(--si-border);
}

.si-thread__subject {
  margin: 0;
  color: var(--si-text);
  font: 700 1.05rem/1.25 var(--font-sans, inherit);
}

.si-thread__meta {
  flex-wrap: wrap;
  margin-top: 10px;
}

.si-thread__meta span {
  color: var(--si-muted);
  font: 500 0.78rem/1.4 var(--font-sans, inherit);
}

.si-thread__meta span:not(:last-child)::after {
  content: '•';
  margin-left: 10px;
  color: var(--si-subtle);
}

.si-thread__badge {
  min-height: 28px;
  padding: 0 10px;
  color: var(--si-muted);
  background: rgba(255, 255, 255, 0.04);
  white-space: nowrap;
}

.si-thread__body {
  min-width: 0;
  min-height: 0;
  padding: 24px;
  overflow: auto;
  color: #eff2f8;
  font: 500 0.89rem/1.68 var(--font-sans, inherit);
  white-space: pre-wrap;
}

.si-reply {
  display: grid;
  gap: 14px;
  padding: 18px 24px 24px;
  border-top: 1px solid var(--si-border);
  background: rgba(0, 0, 0, 0.18);
}

.si-reply__header,
.si-reply__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.si-reply__title {
  margin: 0;
  color: var(--si-text);
  font: 700 0.95rem/1.2 var(--font-sans, inherit);
}

.si-reply__hint,
.si-reply__caption {
  color: var(--si-subtle);
  font: 500 0.76rem/1.3 var(--font-sans, inherit);
}

.si-field {
  display: grid;
  gap: 8px;
}

.si-field span {
  color: var(--si-muted);
  font: 700 0.7rem/1 var(--font-sans, inherit);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.si-input {
  min-height: 40px;
  border-color: var(--si-border-strong);
  background: rgba(255, 255, 255, 0.03);
  color: var(--si-text);
}

.si-input::placeholder {
  color: var(--si-subtle);
}

.si-input:focus {
  border-color: color-mix(in srgb, var(--accent) 45%, white 20%);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--accent) 18%, transparent);
}

.si-input--body {
  min-height: 160px;
  resize: vertical;
}

.si-reply__error {
  margin: 0;
}

.si-send {
  min-width: 120px;
}

.si-empty {
  display: grid;
  place-items: center;
  min-height: 200px;
  padding: 24px;
  color: var(--si-muted);
  text-align: center;
  font: 500 0.88rem/1.5 var(--font-sans, inherit);
}

.si-empty--list {
  min-height: 120px;
}

.si-empty--thread {
  min-height: 100%;
}

@keyframes si-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .si-layout {
    grid-template-columns: 1fr;
  }

  .si-list {
    border-right: 0;
    border-bottom: 1px solid var(--si-border);
  }
}

@media (max-width: 720px) {
  .si-page {
    padding: 18px 10px 28px;
  }

  .si-topbar,
  .si-header,
  .si-toolbar,
  .si-thread__header,
  .si-thread__body,
  .si-reply,
  .si-reply__header,
  .si-reply__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .si-header,
  .si-toolbar,
  .si-thread__header,
  .si-thread__body,
  .si-reply {
    padding-left: 16px;
    padding-right: 16px;
  }

  .si-operator {
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
  }

  .si-toolbar__meta {
    margin-left: 0;
    justify-content: space-between;
  }

  .si-search {
    flex: 0 0 auto;
    width: 100%;
  }

  .si-list__head {
    grid-template-columns: minmax(0, 1fr) 64px;
    padding-left: 16px;
    padding-right: 16px;
  }

  .si-row {
    grid-template-columns: auto minmax(0, 1fr);
    padding-left: 16px;
    padding-right: 16px;
  }

  .si-row__time {
    grid-column: 2;
    padding-top: 0;
  }
}
</style>
