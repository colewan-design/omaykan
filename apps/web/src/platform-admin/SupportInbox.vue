<script setup lang="ts">
import {
  Bike,
  CheckCheck,
  ChevronRight,
  Clock3,
  Mail,
  MessageCircle,
  RefreshCw,
  Reply,
  Search,
  Send,
  Settings2,
  SlidersHorizontal,
  Store,
  Users,
} from '@lucide/vue'
import { computed, nextTick, ref } from 'vue'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ (event: 'session-ended', message: string): void }>()

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

type InboxCategory = 'customer' | 'seller' | 'rider' | 'system'
type InboxFilter = 'all' | 'unread' | 'seller' | 'rider' | 'system'

const loading = ref(false)
const loadingMessage = ref(false)
const sending = ref(false)
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
const activeFilter = ref<InboxFilter>('all')
const sentReplies = ref(0)
const replySentAt = ref<Date | null>(null)
const lastSentBody = ref('')
const replyTextarea = ref<HTMLTextAreaElement | null>(null)

function categoryFor(message: Pick<InboxSummary, 'subject' | 'fromName' | 'fromEmail'>): InboxCategory {
  const text = `${message.subject} ${message.fromName} ${message.fromEmail}`.toLowerCase()
  if (/mailer-daemon|no-?reply|system|undeliver/.test(text)) return 'system'
  if (/rider|delivery|courier/.test(text)) return 'rider'
  if (/seller|farm|market|shop|store|merchant/.test(text)) return 'seller'
  return 'customer'
}

const filteredMessages = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()

  return messages.value.filter((message) => {
    const category = categoryFor(message)
    const matchesFilter = activeFilter.value === 'all'
      || (activeFilter.value === 'unread' && (!message.isRead || message.id === selectedId.value))
      || activeFilter.value === category

    if (!matchesFilter) return false
    if (!query) return true

    return [message.subject, message.fromName, message.fromEmail, message.snippet]
      .join(' ')
      .toLowerCase()
      .includes(query)
  })
})

const selectedSummary = computed(() => messages.value.find((message) => message.id === selectedId.value) ?? null)
const selectedReplyEmail = computed(() => selected.value?.replyToEmail || selected.value?.fromEmail || '')
const uniqueSenders = computed(() => new Set(messages.value.map((message) => message.fromEmail.toLowerCase())).size)

const filters = computed(() => ([
  { id: 'all' as const, label: 'All', count: messages.value.length },
  { id: 'unread' as const, label: 'Unread', count: unreadCount.value },
  { id: 'seller' as const, label: 'Sellers', count: messages.value.filter((message) => categoryFor(message) === 'seller').length },
  { id: 'rider' as const, label: 'Riders', count: messages.value.filter((message) => categoryFor(message) === 'rider').length },
  { id: 'system' as const, label: 'System', count: messages.value.filter((message) => categoryFor(message) === 'system').length },
]))

function authHeaders(json = true): Record<string, string> {
  return {
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    Accept: 'application/json',
    Authorization: `Bearer ${props.token}`,
  }
}

function formatDate(value: string | null): string {
  if (!value) return 'Unknown time'
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function formatListDate(value: string | null): string {
  if (!value) return '--'
  const date = new Date(value)
  const now = new Date()
  return new Intl.DateTimeFormat('en-PH', date.toDateString() === now.toDateString()
    ? { hour: 'numeric', minute: '2-digit' }
    : { month: 'short', day: 'numeric' },
  ).format(date)
}

function formatSentTime(value: Date): string {
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' }).format(value)
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return (parts.length > 1 ? `${parts[0][0]}${parts.at(-1)?.[0]}` : parts[0]?.slice(0, 2) || 'OM').toUpperCase()
}

function categoryLabel(message: Pick<InboxSummary, 'subject' | 'fromName' | 'fromEmail'>): string {
  const category = categoryFor(message)
  return category.charAt(0).toUpperCase() + category.slice(1)
}

function categoryIcon(message: Pick<InboxSummary, 'subject' | 'fromName' | 'fromEmail'>) {
  const category = categoryFor(message)
  if (category === 'seller') return Store
  if (category === 'rider') return Bike
  if (category === 'system') return Settings2
  return Users
}

function defaultReplySubject(subject: string): string {
  return /^re:/i.test(subject.trim()) ? subject.trim() : `Re: ${subject.trim() || 'your message to Omaykan support'}`
}

async function parseJson(response: Response) {
  return response.json().catch(() => ({}))
}

function handleSessionLoss(message: string) {
  emit('session-ended', message)
  throw new Error(message)
}

async function loadList(selectMessageId?: string) {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await fetch('/api/platform-admin/inbox', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ limit: 30 }),
    })
    const data = await parseJson(response)

    if (response.status === 401 || response.status === 403) {
      handleSessionLoss(data.message || 'Your session has ended. Sign in again.')
    }
    if (!response.ok) throw new Error(data.message || data.error || 'Unable to load the support inbox.')

    messages.value = data.messages ?? []
    unreadCount.value = data.unreadCount ?? 0
    const nextId = selectMessageId || selectedId.value || messages.value[0]?.id

    if (nextId && messages.value.some((message) => message.id === nextId)) {
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
  const isNewSelection = selectedId.value !== messageId
  const wasUnread = messages.value.some((message) => message.id === messageId && !message.isRead)
  selectedId.value = messageId
  loadingMessage.value = true
  errorMessage.value = ''
  replyError.value = ''
  successMessage.value = ''
  if (isNewSelection) {
    lastSentBody.value = ''
    replySentAt.value = null
  }

  try {
    const response = await fetch(`/api/platform-admin/inbox/${messageId}`, { headers: authHeaders(false) })
    const data = await parseJson(response)

    if (response.status === 401 || response.status === 403) {
      handleSessionLoss(data.message || 'Your session has ended. Sign in again.')
    }
    if (!response.ok) throw new Error(data.message || data.error || 'Unable to load that message.')

    selected.value = data.message
    replySubject.value = defaultReplySubject(data.message?.subject ?? '')
    messages.value = messages.value.map((message) => message.id === messageId
      ? { ...message, isRead: true, snippet: data.message?.snippet ?? message.snippet }
      : message)
    if (wasUnread) unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to load that message.'
  } finally {
    loadingMessage.value = false
  }
}

async function focusComposer() {
  await nextTick()
  replyTextarea.value?.focus()
}

function insertGreeting() {
  const name = selected.value?.replyToName || selected.value?.fromName || 'there'
  if (!replyBody.value.trim()) replyBody.value = `Hi ${name},\n\nThank you for reaching out to Omaykan Support. `
  void focusComposer()
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
    const response = await fetch(`/api/platform-admin/inbox/${selected.value.id}/reply`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ subject, message }),
    })
    const data = await parseJson(response)

    if (response.status === 401 || response.status === 403) {
      handleSessionLoss(data.message || 'Your session has ended. Sign in again.')
    }
    if (!response.ok) throw new Error(data.message || data.error || 'Unable to send that reply.')

    lastSentBody.value = message
    replySentAt.value = new Date()
    sentReplies.value += 1
    successMessage.value = `Reply queued for ${data.to}.`
    replyBody.value = ''
  } catch (err) {
    replyError.value = err instanceof Error ? err.message : 'Unable to send that reply.'
  } finally {
    sending.value = false
  }
}

void loadList()
</script>

<template>
  <section class="si-page">
    <div class="si-stats" aria-label="Inbox overview">
      <article class="si-stat si-stat--blue">
        <span class="si-stat__icon"><Mail :size="24" /></span>
        <div><span>Unread messages</span><strong>{{ unreadCount }}</strong><small>Require your attention</small></div>
        <ChevronRight :size="18" class="si-stat__arrow" />
      </article>
      <article class="si-stat si-stat--green">
        <span class="si-stat__icon"><MessageCircle :size="24" /></span>
        <div><span>Open tickets</span><strong>{{ messages.length }}</strong><small>Active conversations</small></div>
        <ChevronRight :size="18" class="si-stat__arrow" />
      </article>
      <article class="si-stat si-stat--amber">
        <span class="si-stat__icon"><Users :size="24" /></span>
        <div><span>Active senders</span><strong>{{ uniqueSenders }}</strong><small>People in this inbox</small></div>
        <ChevronRight :size="18" class="si-stat__arrow" />
      </article>
      <article class="si-stat si-stat--mint">
        <span class="si-stat__icon"><CheckCheck :size="24" /></span>
        <div><span>Replies sent</span><strong>{{ sentReplies }}</strong><small>This support session</small></div>
        <ChevronRight :size="18" class="si-stat__arrow" />
      </article>
    </div>

    <div class="si-shell">
      <aside class="si-inbox" aria-label="Support messages">
        <div class="si-tabs" role="tablist" aria-label="Message filters">
          <button
            v-for="filter in filters"
            :key="filter.id"
            class="si-tab"
            :class="{ 'si-tab--active': activeFilter === filter.id }"
            type="button"
            role="tab"
            :aria-selected="activeFilter === filter.id"
            @click="activeFilter = filter.id"
          >
            {{ filter.label }} <span>{{ filter.count }}</span>
          </button>
        </div>

        <div class="si-toolbar">
          <label class="si-search" aria-label="Search inbox">
            <Search :size="18" />
            <input v-model="searchQuery" type="search" placeholder="Search messages, senders or subjects..." autocomplete="off">
          </label>
          <button class="si-filter-button" type="button" title="Show unread messages" aria-label="Show unread messages" @click="activeFilter = activeFilter === 'unread' ? 'all' : 'unread'">
            <SlidersHorizontal :size="18" />
          </button>
        </div>

        <div class="si-list" :aria-busy="loading">
          <div v-if="loading && !messages.length" class="si-empty">Loading support messages...</div>
          <div v-else-if="!filteredMessages.length" class="si-empty">
            {{ searchQuery.trim() ? 'No messages match that search.' : 'No messages in this view.' }}
          </div>

          <button
            v-for="message in filteredMessages"
            :key="message.id"
            class="si-row"
            :class="{ 'si-row--active': message.id === selectedId, 'si-row--unread': !message.isRead }"
            type="button"
            @click="openMessage(message.id)"
          >
            <span class="si-avatar" :class="`si-avatar--${categoryFor(message)}`">
              <component :is="categoryIcon(message)" v-if="categoryFor(message) !== 'customer'" :size="20" />
              <template v-else>{{ initials(message.fromName) }}</template>
            </span>
            <span class="si-row__content">
              <span class="si-row__top"><strong>{{ message.fromName }}</strong><time>{{ formatListDate(message.receivedAt) }}</time></span>
              <span class="si-row__subject">{{ message.subject }}</span>
              <span class="si-row__snippet">{{ message.snippet || message.fromEmail }}</span>
            </span>
            <span v-if="!message.isRead" class="si-unread-dot" aria-label="Unread"></span>
          </button>
        </div>
      </aside>

      <section class="si-thread" :aria-busy="loadingMessage">
        <template v-if="selected">
          <header class="si-thread__header">
            <div class="si-sender">
              <span class="si-avatar si-avatar--large">{{ initials(selected.fromName) }}</span>
              <span><strong>{{ selected.fromName }}</strong><small>{{ selected.fromEmail }}</small></span>
            </div>
            <div class="si-thread__actions">
              <button class="si-action si-action--primary" type="button" @click="focusComposer"><Reply :size="16" /> Reply</button>
              <button class="si-action" type="button" :disabled="loading" @click="loadList(selected.id)"><RefreshCw :size="16" :class="{ 'si-spin': loading }" /> Refresh</button>
            </div>
          </header>

          <div class="si-subject-row">
            <div>
              <h2>{{ selected.subject }}</h2>
              <span class="si-badge"><component :is="categoryIcon(selected)" :size="13" /> {{ categoryLabel(selected) }}</span>
            </div>
            <time>{{ formatDate(selected.receivedAt) }}</time>
          </div>

          <div v-if="errorMessage" class="si-banner si-banner--error">{{ errorMessage }}</div>
          <div v-if="successMessage" class="si-banner si-banner--success">{{ successMessage }}</div>

          <div class="si-conversation">
            <article class="si-message si-message--incoming">
              <span class="si-avatar">{{ initials(selected.fromName) }}</span>
              <div class="si-bubble">
                <strong>{{ selected.fromName }}</strong>
                <p>{{ selected.body || selectedSummary?.snippet || 'No message body available.' }}</p>
              </div>
            </article>

            <article v-if="lastSentBody && replySentAt" class="si-message si-message--outgoing">
              <span class="si-avatar si-avatar--admin">A</span>
              <div class="si-bubble">
                <div class="si-message__meta"><strong>Admin (Omaykan Support)</strong><time>{{ formatSentTime(replySentAt) }}</time></div>
                <p>{{ lastSentBody }}</p>
              </div>
            </article>
          </div>

          <form class="si-composer" @submit.prevent="sendReply">
            <div class="si-composer__top">
              <strong>Reply</strong>
              <span>To {{ selectedReplyEmail }}</span>
            </div>
            <label class="si-subject-input">
              <span>Subject</span>
              <input v-model="replySubject" type="text" maxlength="190">
            </label>
            <textarea ref="replyTextarea" v-model="replyBody" rows="4" maxlength="5000" placeholder="Write your reply..."></textarea>
            <p v-if="replyError" class="si-reply-error">{{ replyError }}</p>
            <div class="si-composer__footer">
              <button class="si-canned" type="button" @click="insertGreeting"><Clock3 :size="16" /> Insert greeting</button>
              <button class="si-send" type="submit" :disabled="sending"><Send :size="16" /> {{ sending ? 'Sending...' : 'Send reply' }}</button>
            </div>
          </form>
        </template>

        <div v-else class="si-empty si-empty--thread">
          <MessageCircle :size="32" />
          <strong>{{ loadingMessage ? 'Loading message...' : 'Select a message' }}</strong>
          <span>Choose a conversation to read and reply.</span>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.si-page {
  display: grid;
  gap: 16px;
  color: #19231f;
}

.si-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.si-stat {
  position: relative;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) 24px;
  align-items: center;
  min-height: 86px;
  padding: 14px 16px;
  border: 1px solid #e5e0d6;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 4px 16px rgba(41, 49, 44, 0.04);
}

.si-stat__icon {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 50%;
}

.si-stat--blue .si-stat__icon { color: #246dc8; background: #e9f2ff; }
.si-stat--green .si-stat__icon { color: #19733f; background: #ebf5ec; }
.si-stat--amber .si-stat__icon { color: #c56c08; background: #fff1df; }
.si-stat--mint .si-stat__icon { color: #13723e; background: #edf5eb; }

.si-stat div { display: grid; gap: 2px; }
.si-stat div > span { color: #505a55; font-size: 12px; }
.si-stat strong { color: #161d19; font-family: Georgia, serif; font-size: 24px; line-height: 1; }
.si-stat small { color: #7b827e; font-size: 11px; }
.si-stat__arrow { justify-self: end; padding: 5px; border: 1px solid #e6e1d8; border-radius: 50%; color: #6f7672; box-sizing: content-box; }

.si-shell {
  display: grid;
  grid-template-columns: minmax(390px, 40%) minmax(0, 1fr);
  min-height: 620px;
  overflow: hidden;
  border: 1px solid #e3ded3;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 6px 22px rgba(41, 49, 44, 0.05);
}

.si-inbox {
  min-width: 0;
  border-right: 1px solid #e7e2d8;
}

.si-tabs {
  display: flex;
  gap: 8px;
  padding: 12px 14px 8px;
  overflow-x: auto;
  scrollbar-width: none;
}

.si-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  padding: 8px 12px;
  border: 1px solid transparent;
  border-radius: 9px;
  color: #47504b;
  background: #f7f5f1;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.si-tab span { display: grid; min-width: 17px; height: 17px; padding: 0 4px; place-items: center; border: 1px solid #ddd8cf; border-radius: 999px; font-size: 10px; }
.si-tab--active { color: #fff; background: #0d4a32; border-color: #0d4a32; }
.si-tab--active span { border-color: rgba(255,255,255,.35); background: rgba(255,255,255,.14); }

.si-toolbar {
  display: flex;
  gap: 8px;
  padding: 0 14px 12px;
  border-bottom: 1px solid #e7e2d8;
}

.si-search {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: 1;
  min-width: 0;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #ded9cf;
  border-radius: 9px;
  color: #66706a;
  background: #fff;
}

.si-search:focus-within { border-color: #568b70; box-shadow: 0 0 0 3px rgba(25, 111, 65, 0.09); }
.si-search input { width: 100%; border: 0; outline: 0; color: #1c2822; background: transparent; font: inherit; font-size: 12px; }
.si-search input::placeholder { color: #929892; }
.si-filter-button { display: grid; width: 38px; height: 38px; place-items: center; border: 1px solid #ded9cf; border-radius: 9px; color: #38433d; background: #fff; cursor: pointer; }

.si-list { max-height: 540px; overflow-y: auto; }
.si-row {
  position: relative;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  padding: 11px 18px;
  border: 0;
  border-bottom: 1px solid #eeeae3;
  color: #1e2923;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.si-row::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: transparent; content: ''; }
.si-row:hover { background: #faf9f6; }
.si-row--active { background: linear-gradient(90deg, #edf5ee, #f8faf6); }
.si-row--active::before { background: #16814b; }
.si-row--unread .si-row__top strong, .si-row--unread .si-row__subject { font-weight: 800; }

.si-avatar {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  align-self: start;
  border-radius: 50%;
  color: #225d3d;
  background: #dfece0;
  font-size: 13px;
  font-weight: 800;
}

.si-avatar--seller { color: #8e4b05; background: #ffead3; }
.si-avatar--rider { color: #29634a; background: #e5f2e6; }
.si-avatar--system { color: #444b48; background: #eeece8; }
.si-avatar--large { width: 46px; height: 46px; font-size: 14px; }
.si-avatar--admin { color: #fff; background: #103f2e; }
.si-row__content, .si-row__top { min-width: 0; }
.si-row__content { display: grid; gap: 3px; }
.si-row__top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.si-row__top strong, .si-row__subject, .si-row__snippet { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.si-row__top strong { font-size: 12px; font-weight: 650; }
.si-row__top time { flex: 0 0 auto; color: #757e79; font-size: 10px; }
.si-row__subject { color: #26322c; font-size: 12px; font-weight: 600; }
.si-row__snippet { color: #78807b; font-size: 10.5px; }
.si-unread-dot { position: absolute; right: 18px; bottom: 16px; width: 7px; height: 7px; border-radius: 50%; background: #24824d; }

.si-thread { display: flex; min-width: 0; flex-direction: column; background: rgba(255,255,255,.32); }
.si-thread__header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 18px; border-bottom: 1px solid #e8e3db; }
.si-sender { display: flex; align-items: center; gap: 12px; min-width: 0; }
.si-sender > span:last-child { display: grid; min-width: 0; gap: 2px; }
.si-sender strong, .si-sender small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.si-sender strong { font-size: 14px; }
.si-sender small { color: #6c756f; font-size: 11px; }
.si-thread__actions { display: flex; gap: 8px; flex: 0 0 auto; }
.si-action { display: inline-flex; height: 34px; align-items: center; gap: 6px; padding: 0 13px; border: 1px solid #ddd8cf; border-radius: 8px; color: #2a352f; background: #fff; font: inherit; font-size: 11px; font-weight: 700; cursor: pointer; }
.si-action--primary { color: #fff; border-color: #123e2e; background: #123e2e; }
.si-action:disabled { opacity: .55; cursor: wait; }

.si-subject-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 14px 20px 10px; }
.si-subject-row h2 { margin: 0 0 7px; color: #18221d; font-family: Georgia, serif; font-size: 18px; line-height: 1.25; }
.si-subject-row time { flex: 0 0 auto; padding-top: 4px; color: #6f7873; font-size: 10px; }
.si-badge { display: inline-flex; align-items: center; gap: 5px; padding: 4px 9px; border-radius: 999px; color: #1d6840; background: #e8f3e9; font-size: 10px; font-weight: 700; }

.si-banner { margin: 0 20px 8px; padding: 9px 12px; border-radius: 8px; font-size: 11px; }
.si-banner--error { color: #8c2929; background: #fff0ef; }
.si-banner--success { color: #17663c; background: #eaf5ec; }
.si-conversation { display: grid; gap: 12px; flex: 1; align-content: start; max-height: 315px; padding: 6px 20px 14px; overflow-y: auto; }
.si-message { display: flex; align-items: flex-start; gap: 10px; max-width: 88%; }
.si-message--outgoing { justify-self: end; flex-direction: row-reverse; }
.si-bubble { min-width: 0; padding: 12px 15px; border-radius: 14px; color: #36413b; background: #f3f1ed; font-size: 12px; line-height: 1.55; }
.si-message--incoming .si-bubble { border-top-left-radius: 4px; }
.si-message--outgoing .si-bubble { border-top-right-radius: 4px; background: #edf4ee; }
.si-bubble > strong { display: block; margin-bottom: 5px; color: #1e2923; font-size: 11px; }
.si-bubble p { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.si-message__meta { display: flex; justify-content: space-between; gap: 18px; margin-bottom: 5px; }
.si-message__meta time { color: #737b76; font-size: 9px; }

.si-composer { margin: 0 14px 14px; overflow: hidden; border: 1px solid #ded9cf; border-radius: 12px; background: #fff; box-shadow: 0 3px 12px rgba(36,45,40,.04); }
.si-composer__top { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 13px; border-bottom: 1px solid #ece8e0; }
.si-composer__top strong { color: #155336; font-size: 11px; }
.si-composer__top span { overflow: hidden; color: #818783; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.si-subject-input { display: grid; grid-template-columns: 52px 1fr; align-items: center; padding: 7px 13px; border-bottom: 1px solid #eeeae3; color: #77807a; font-size: 10px; }
.si-subject-input input { min-width: 0; border: 0; outline: 0; color: #29342e; background: transparent; font: inherit; font-size: 11px; }
.si-composer textarea { display: block; width: 100%; min-height: 82px; padding: 10px 13px; resize: vertical; border: 0; outline: 0; color: #26322c; background: #fff; font: inherit; font-size: 12px; line-height: 1.5; box-sizing: border-box; }
.si-composer textarea::placeholder { color: #a0a49f; }
.si-reply-error { margin: 0 13px 8px; color: #9d2d2d; font-size: 10px; }
.si-composer__footer { display: flex; align-items: center; justify-content: space-between; padding: 8px 10px; border-top: 1px solid #eeeae3; }
.si-canned, .si-send { display: inline-flex; align-items: center; gap: 7px; height: 34px; border-radius: 8px; font: inherit; font-size: 10px; font-weight: 700; cursor: pointer; }
.si-canned { padding: 0 10px; border: 0; color: #59625d; background: transparent; }
.si-send { padding: 0 15px; border: 1px solid #123e2e; color: #fff; background: #123e2e; box-shadow: 0 4px 10px rgba(18,62,46,.16); }
.si-send:disabled { opacity: .55; cursor: wait; }
.si-empty { display: grid; min-height: 180px; place-content: center; gap: 7px; padding: 24px; color: #78817b; text-align: center; font-size: 12px; }
.si-empty--thread { flex: 1; min-height: 520px; }
.si-empty--thread svg { justify-self: center; color: #2d8053; }
.si-empty--thread strong { color: #28352e; font-size: 15px; }

@keyframes si-spin { to { transform: rotate(360deg); } }
.si-spin { animation: si-spin .8s linear infinite; }

button:focus-visible, input:focus-visible, textarea:focus-visible { outline: 3px solid rgba(35, 121, 74, .2); outline-offset: 2px; }

@media (max-width: 1100px) {
  .si-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .si-shell { grid-template-columns: minmax(330px, 39%) minmax(0, 1fr); }
}

@media (max-width: 760px) {
  .si-stats { grid-template-columns: 1fr 1fr; gap: 10px; }
  .si-stat { min-height: 78px; grid-template-columns: 48px minmax(0, 1fr); padding: 12px; }
  .si-stat__icon { width: 40px; height: 40px; }
  .si-stat__arrow { display: none; }
  .si-stat strong { font-size: 21px; }
  .si-shell { display: block; }
  .si-inbox { border-right: 0; border-bottom: 1px solid #e7e2d8; }
  .si-list { max-height: 360px; }
  .si-thread { min-height: 620px; }
}

@media (max-width: 480px) {
  .si-stats { grid-template-columns: 1fr; }
  .si-stat { min-height: 70px; }
  .si-tabs { padding-inline: 10px; }
  .si-toolbar { padding-inline: 10px; }
  .si-row { padding-inline: 14px; }
  .si-thread__header { align-items: flex-start; padding: 12px; }
  .si-thread__actions { flex-direction: column; }
  .si-action { justify-content: center; }
  .si-subject-row { flex-direction: column; padding-inline: 14px; }
  .si-subject-row time { padding-top: 0; }
  .si-conversation { padding-inline: 14px; }
  .si-message { max-width: 100%; }
  .si-composer { margin-inline: 10px; }
}
</style>
