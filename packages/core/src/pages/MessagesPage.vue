<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, RefreshCw, Send } from '@lucide/vue'
import {
  deliveryStageLabel,
  formatCurrency,
  orderStatusLabel,
  type ConversationOrderRef,
  type ConversationSummary,
  type ConversationThread,
  type DeliveryStage,
  type OrderStatus,
} from '@pos/shared/index'
import { getPosRepository } from '@pos/core/services/runtime'
import { useMessagesStore } from '@pos/core/stores/messages'

// Customer messages.
//
// Inbox on the left, the open thread on the right; on a phone, one at a time.
// A shop answers here but never starts a conversation — see the backend's
// SellerConversationController for why.
//
// Gated on the `orders` permission rather than a page key of its own: the
// people who can see a shop's online orders are the people who should be
// answering "where's my order", and a new page key would have left every role
// already saved in every shop without it — Messages invisible, even to owners,
// until someone went and ticked a box.

const MAX_BODY = 2000
const THREAD_POLL_MS = 8_000
const LIST_POLL_MS = 15_000

const repository = getPosRepository()
const messagesStore = useMessagesStore()

const conversations = ref<ConversationSummary[]>([])
const listLoading = ref(true)
const listError = ref('')

const activeId = ref('')
const thread = ref<ConversationThread | null>(null)
const threadLoading = ref(false)
const threadError = ref('')

const draft = ref('')
const sending = ref(false)
const sendError = ref('')
const scroller = ref<HTMLElement | null>(null)

const activeSummary = computed(
  () => thread.value?.conversation ?? conversations.value.find((c) => c.id === activeId.value) ?? null,
)

function when(iso: string | null | undefined): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return date.toDateString() === new Date().toDateString()
    ? date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
    : date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase() || '·'
}

function orderLine(order: ConversationOrderRef): string {
  if (order.fulfillmentMethod === 'delivery' && order.deliveryStage) {
    return deliveryStageLabel(order.deliveryStage as DeliveryStage)
  }
  return order.status ? orderStatusLabel(order.status as OrderStatus) : ''
}

async function loadList(silent = false) {
  if (!silent) listLoading.value = true
  try {
    conversations.value = await repository.loadConversations()
    listError.value = ''
  } catch {
    if (!silent) listError.value = 'Could not load messages. Check the connection and try again.'
  } finally {
    listLoading.value = false
  }
}

function remember(summary: ConversationSummary, toTop: boolean) {
  const rest = conversations.value.filter((c) => c.id !== summary.id)
  if (toTop) {
    conversations.value = [summary, ...rest]
    return
  }
  const index = conversations.value.findIndex((c) => c.id === summary.id)
  if (index >= 0) conversations.value.splice(index, 1, summary)
}

async function scrollToEnd() {
  await nextTick()
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadThread(id: string, silent = false) {
  if (!silent) {
    threadLoading.value = true
    threadError.value = ''
  }
  try {
    const next = await repository.loadConversation(id)
    if (activeId.value !== id) return
    const grew = next.messages.length !== (thread.value?.messages.length ?? 0)
    thread.value = next
    remember(next.conversation, false)
    if (grew) void scrollToEnd()
    // Reading it cleared the count server-side; take the badge down now.
    void messagesStore.refresh()
  } catch {
    if (!silent) threadError.value = 'Could not open that conversation.'
  } finally {
    if (!silent) threadLoading.value = false
  }
}

function openConversation(id: string) {
  if (activeId.value !== id) {
    thread.value = null
    draft.value = ''
    sendError.value = ''
  }
  activeId.value = id
  void loadThread(id)
}

function closeConversation() {
  activeId.value = ''
  thread.value = null
  sendError.value = ''
}

async function send() {
  const text = draft.value.trim()
  if (!text || !activeId.value || sending.value) return

  sending.value = true
  sendError.value = ''
  try {
    const next = await repository.sendConversationMessage(activeId.value, text)
    thread.value = next
    draft.value = ''
    remember(next.conversation, true)
    void scrollToEnd()
  } catch {
    sendError.value = 'That reply did not send. Try again.'
  } finally {
    sending.value = false
  }
}

function refreshAll() {
  void loadList()
  if (activeId.value) void loadThread(activeId.value)
}

let threadTimer: ReturnType<typeof setInterval> | null = null
let listTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  void loadList()

  threadTimer = setInterval(() => {
    if (document.visibilityState === 'hidden' || !activeId.value) return
    void loadThread(activeId.value, true)
  }, THREAD_POLL_MS)

  listTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void loadList(true)
  }, LIST_POLL_MS)
})

onBeforeUnmount(() => {
  if (threadTimer) clearInterval(threadTimer)
  if (listTimer) clearInterval(listTimer)
})
</script>

<template>
  <div class="msg-page">
    <div class="msg-header">
      <div>
        <p class="eyebrow">Storefront</p>
        <h1 class="panel-title">Messages</h1>
      </div>
      <button type="button" class="secondary-button" @click="refreshAll">
        <RefreshCw :size="16" />
        Refresh
      </button>
    </div>

    <div class="msg-shell" :class="{ 'msg-shell--open': activeId }">
      <section class="surface-panel msg-inbox" aria-label="Conversations">
        <p v-if="listLoading && conversations.length === 0" class="msg-muted">Loading…</p>
        <p v-else-if="listError" class="msg-error">{{ listError }}</p>
        <div v-else-if="conversations.length === 0" class="empty-state">
          No messages yet. When a customer asks this shop something from the storefront, it
          lands here.
        </div>

        <ul v-else class="msg-inbox__list">
          <li v-for="c in conversations" :key="c.id">
            <button
              type="button"
              class="msg-row"
              :class="{ 'msg-row--on': c.id === activeId, 'msg-row--unread': c.unreadCount > 0 }"
              @click="openConversation(c.id)"
            >
              <span class="msg-row__avatar">{{ initial(c.customer.name) }}</span>
              <span class="msg-row__text">
                <span class="msg-row__top">
                  <span class="msg-row__name">{{ c.customer.name }}</span>
                  <span class="msg-row__when">{{ when(c.lastMessageAt) }}</span>
                </span>
                <span class="msg-row__preview">
                  {{ c.lastMessage?.from === 'store' ? 'You: ' : '' }}{{ c.lastMessage?.body }}
                </span>
              </span>
              <span v-if="c.unreadCount > 0" class="msg-row__badge">{{ c.unreadCount }}</span>
            </button>
          </li>
        </ul>
      </section>

      <section class="surface-panel msg-thread" aria-label="Conversation">
        <div v-if="!activeId" class="empty-state msg-thread__idle">
          Pick a conversation to read it and reply.
        </div>

        <template v-else>
          <header class="msg-thread__head">
            <button type="button" class="msg-thread__back" aria-label="Back to all messages" @click="closeConversation">
              <ArrowLeft :size="18" />
            </button>
            <h2 class="subpanel-title">{{ activeSummary?.customer.name ?? 'Customer' }}</h2>
          </header>

          <!-- "Where's my order" is most of what a shop gets asked; the answer
               shouldn't be a trip to the dashboard away. -->
          <div v-if="thread?.recentOrders.length" class="msg-orders">
            <span class="msg-orders__label">Their orders here</span>
            <span v-for="o in thread.recentOrders" :key="o.id" class="msg-orders__chip">
              #{{ o.ticketNumber }} · {{ orderLine(o) }} · {{ formatCurrency(o.totalCents) }}
            </span>
          </div>

          <div ref="scroller" class="msg-thread__body" aria-live="polite">
            <p v-if="threadLoading && !thread" class="msg-muted">Loading…</p>
            <p v-else-if="threadError" class="msg-error">{{ threadError }}</p>

            <div
              v-for="m in thread?.messages ?? []"
              :key="m.id"
              class="msg-bubble"
              :class="m.from === 'store' ? 'msg-bubble--mine' : 'msg-bubble--theirs'"
            >
              <p v-if="m.order" class="msg-bubble__about">About order #{{ m.order.ticketNumber }}</p>
              <p class="msg-bubble__body">{{ m.body }}</p>
              <p class="msg-bubble__when">
                <template v-if="m.from === 'store' && m.authorName">{{ m.authorName }} · </template>{{ when(m.createdAt) }}
              </p>
            </div>
          </div>

          <form class="msg-compose" @submit.prevent="send">
            <textarea
              v-model="draft"
              class="msg-compose__input"
              rows="2"
              :maxlength="MAX_BODY"
              placeholder="Write a reply"
              aria-label="Your reply"
              @keydown.enter.exact.prevent="send"
            />
            <button type="submit" class="primary-button msg-compose__send" :disabled="sending || !draft.trim()">
              <Send :size="16" />
              {{ sending ? 'Sending…' : 'Send' }}
            </button>
          </form>
          <p v-if="sendError" class="msg-error msg-compose__error">{{ sendError }}</p>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.msg-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-5);
  padding-bottom: var(--space-10);
}

.msg-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.msg-shell {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: var(--space-4);
  align-items: stretch;
}

.msg-muted {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-subhead);
}

.msg-error {
  margin: 0;
  color: var(--danger);
  font: var(--type-subhead);
}

/* -- Inbox --------------------------------------------------------------- */

.msg-inbox {
  min-width: 0;
  padding: var(--space-2);
}

.msg-inbox > .msg-muted,
.msg-inbox > .msg-error {
  padding: var(--space-3);
}

.msg-inbox__list {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.msg-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3);
  border: none;
  border-radius: var(--radius-md);
  background: none;
  color: var(--text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.msg-row:hover {
  background: var(--fill);
}

.msg-row--on {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.msg-row__avatar {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
  font-weight: 700;
}

.msg-row__text {
  flex: 1;
  min-width: 0;
}

.msg-row__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.msg-row__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font: var(--type-subhead);
  font-weight: 600;
}

.msg-row__when {
  flex-shrink: 0;
  color: var(--text-tertiary);
  font: var(--type-caption);
}

.msg-row__preview {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
  font: var(--type-caption);
  font-size: 0.8125rem;
}

.msg-row--unread .msg-row__name,
.msg-row--unread .msg-row__preview {
  font-weight: 700;
  color: var(--text-primary);
}

.msg-row__badge {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: var(--radius-pill);
  background: var(--accent);
  color: var(--accent-text-on);
  font: var(--type-caption);
  font-weight: 700;
}

/* -- Thread -------------------------------------------------------------- */

.msg-thread {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 520px;
}

.msg-thread__idle {
  margin: auto 0;
}

.msg-thread__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  border-bottom: 0.5px solid var(--separator);
}

.msg-thread__back {
  display: none;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: none;
  color: var(--text-primary);
  cursor: pointer;
}

.msg-thread__back:hover {
  background: var(--fill);
}

.msg-orders {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 0;
  border-bottom: 0.5px solid var(--separator);
}

.msg-orders__label {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.msg-orders__chip {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--fill);
  color: var(--text-primary);
  font: var(--type-caption);
  font-weight: 600;
}

.msg-thread__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-2);
  min-height: 280px;
  max-height: 58vh;
  padding: var(--space-4) 0;
  overflow-y: auto;
}

.msg-bubble {
  max-width: min(78%, 520px);
  padding: 10px 14px;
  border-radius: 16px;
}

.msg-bubble--mine {
  align-self: flex-end;
  border-bottom-right-radius: 4px;
  background: var(--accent);
  color: var(--accent-text-on);
}

.msg-bubble--theirs {
  align-self: flex-start;
  border-bottom-left-radius: 4px;
  background: var(--fill);
  color: var(--text-primary);
}

.msg-bubble__about {
  margin: 0 0 4px;
  font: var(--type-caption);
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  opacity: 0.8;
}

.msg-bubble__body {
  margin: 0;
  font: var(--type-subhead);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.msg-bubble__when {
  margin: 4px 0 0;
  font: var(--type-caption);
  opacity: 0.7;
  text-align: right;
}

.msg-compose {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 0.5px solid var(--separator);
}

.msg-compose__input {
  flex: 1;
  min-height: 46px;
  max-height: 160px;
  padding: 10px 12px;
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
  color: var(--text-primary);
  font: var(--type-subhead);
  resize: vertical;
}

.msg-compose__input:focus {
  outline: 2px solid color-mix(in srgb, var(--accent) 45%, transparent);
  outline-offset: 1px;
}

.msg-compose__send {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 46px;
}

.msg-compose__error {
  margin-top: var(--space-2);
}

/* -- Phones: one pane at a time ----------------------------------------- */

@media (max-width: 900px) {
  .msg-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .msg-thread {
    display: none;
    min-height: 0;
  }

  .msg-shell--open .msg-inbox {
    display: none;
  }

  .msg-shell--open .msg-thread {
    display: flex;
  }

  .msg-thread__back {
    display: grid;
  }
}
</style>
