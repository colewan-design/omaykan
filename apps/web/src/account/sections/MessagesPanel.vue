<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChevronLeft, Send, X } from '@lucide/vue'
import {
  fetchConversation,
  fetchConversations,
  fetchOrder,
  resolveImageUrl,
  sendConversationMessage,
  startConversation,
  type ConversationSummary,
  type ConversationThread,
} from '@pos/web/commerce/api'
import { messageFor } from '@pos/web/commerce/customer'
import { useUnreadMessages } from '@pos/web/commerce/messages'

// Messages with shops.
//
// One column, list or thread, never both: this renders inside the portal's
// right-hand panel, which is already the narrow half of the page, and a list
// squeezed beside a thread in there would leave neither readable.
//
// A thread can be opened three ways, all by URL so they survive a sign-in
// detour: ?conversation= from a link, ?order= from an order card, and
// ?shop=&store=&name= from a shop's page. The last two open a *draft* when
// there is no conversation with that shop yet — nothing exists server-side
// until the first message is sent, so an abandoned "Message this shop" leaves
// no empty thread in anybody's inbox.

const MAX_BODY = 2000
const THREAD_POLL_MS = 8_000
const LIST_POLL_MS = 30_000

const unread = useUnreadMessages()

const conversations = ref<ConversationSummary[]>([])
const listLoading = ref(true)
const listError = ref('')

const activeId = ref('')
const thread = ref<ConversationThread | null>(null)
const threadLoading = ref(false)
const threadError = ref('')

/** A shop with no thread yet. The first message is what creates one. */
interface Draft {
  name: string
  orgSlug?: string
  storeCode?: string
  /** Set when the draft came from an order; also how the server finds the shop. */
  orderId?: string
}
const draft = ref<Draft | null>(null)

/** The order the next message is about. Cleared once it has been sent. */
const attached = ref<{ id: string; ticketNumber: string } | null>(null)

const body = ref('')
const sending = ref(false)
const sendError = ref('')
const scroller = ref<HTMLElement | null>(null)

const open = computed(() => activeId.value !== '' || draft.value !== null)
const shopName = computed(() => thread.value?.conversation.store.name ?? draft.value?.name ?? '')
const shopHref = computed(() => {
  const slug = thread.value?.conversation.store.orgSlug
  return slug ? `/?shop=${encodeURIComponent(slug)}` : ''
})

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

// -- The list --------------------------------------------------------------

async function loadList(silent = false) {
  if (!silent) listLoading.value = true
  try {
    conversations.value = (await fetchConversations()).conversations
    listError.value = ''
  } catch (error) {
    if (!silent) listError.value = messageFor(error, 'Could not load your messages.')
  } finally {
    listLoading.value = false
  }
}

/** Keep the list's copy of a thread current; a new message moves it to the top. */
function remember(summary: ConversationSummary, toTop: boolean) {
  const rest = conversations.value.filter((c) => c.id !== summary.id)
  if (toTop) {
    conversations.value = [summary, ...rest]
    return
  }
  const index = conversations.value.findIndex((c) => c.id === summary.id)
  if (index >= 0) conversations.value.splice(index, 1, summary)
}

// -- A thread --------------------------------------------------------------

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
    const next = await fetchConversation(id)
    // Somebody tapped back, or into another thread, while this was in flight.
    if (activeId.value !== id) return
    const grew = next.messages.length !== (thread.value?.messages.length ?? 0)
    thread.value = next
    remember(next.conversation, false)
    if (grew) void scrollToEnd()
    // The server has just marked it read; let the header catch up now rather
    // than at its next poll.
    void unread.refresh()
  } catch (error) {
    if (!silent) threadError.value = messageFor(error, 'Could not open that conversation.')
  } finally {
    if (!silent) threadLoading.value = false
  }
}

function openConversation(id: string) {
  draft.value = null
  if (activeId.value !== id) {
    thread.value = null
    attached.value = null
  }
  activeId.value = id
  sendError.value = ''
  void loadThread(id)
}

function backToList() {
  activeId.value = ''
  thread.value = null
  draft.value = null
  attached.value = null
  sendError.value = ''
  void loadList(true)
}

async function send() {
  const text = body.value.trim()
  if (!text || sending.value) return

  sending.value = true
  sendError.value = ''
  try {
    let next: ConversationThread
    if (activeId.value) {
      next = await sendConversationMessage(activeId.value, text, attached.value?.id)
    } else if (draft.value) {
      next = await startConversation({
        body: text,
        orderId: draft.value.orderId ?? attached.value?.id ?? null,
        orgSlug: draft.value.orgSlug,
        storeCode: draft.value.storeCode,
      })
    } else {
      return
    }

    activeId.value = next.conversation.id
    draft.value = null
    thread.value = next
    attached.value = null
    body.value = ''
    remember(next.conversation, true)
    void scrollToEnd()
  } catch (error) {
    sendError.value = messageFor(error, 'Could not send that message.')
  } finally {
    sending.value = false
  }
}

// -- Arriving from a link --------------------------------------------------

async function applyLink() {
  const params = new URLSearchParams(window.location.search)

  const conversationId = params.get('conversation')
  if (conversationId) {
    openConversation(conversationId)
    return
  }

  const orderId = params.get('order')
  if (orderId) {
    try {
      const order = await fetchOrder(orderId)
      const about = { id: order.orderId, ticketNumber: order.ticketNumber }
      const existing = conversations.value.find((c) => c.store.id === order.storeId)
      if (existing) {
        openConversation(existing.id)
      } else {
        draft.value = { name: order.route?.pickup?.name || 'the shop', orderId: order.orderId }
      }
      // After opening: opening a different thread clears what was attached.
      attached.value = about
    } catch {
      listError.value = "We couldn't find that order."
    }
    return
  }

  const slug = params.get('shop')
  const code = params.get('store')
  if (slug && code) {
    const existing = conversations.value.find(
      (c) => c.store.orgSlug === slug && c.store.storeCode === code,
    )
    if (existing) {
      openConversation(existing.id)
    } else {
      draft.value = { name: params.get('name') || 'this shop', orgSlug: slug, storeCode: code }
    }
  }
}

// -- Keeping up ------------------------------------------------------------

let threadTimer: ReturnType<typeof setInterval> | null = null
let listTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await loadList()
  await applyLink()

  threadTimer = setInterval(() => {
    if (document.visibilityState === 'hidden' || !activeId.value) return
    void loadThread(activeId.value, true)
  }, THREAD_POLL_MS)

  listTimer = setInterval(() => {
    if (document.visibilityState === 'hidden' || open.value) return
    void loadList(true)
  }, LIST_POLL_MS)
})

onBeforeUnmount(() => {
  if (threadTimer) clearInterval(threadTimer)
  if (listTimer) clearInterval(listTimer)
})
</script>

<template>
  <div>
    <template v-if="!open">
      <div class="acct-head">
        <h1 class="acct-head__title">Messages</h1>
        <p class="acct-head__sub">
          Questions for the shops you buy from, and their answers. Start one with “Message this
          shop” on any product, or “Message the shop” on an order.
        </p>
      </div>

      <p v-if="listLoading" class="acct-card__note">Loading your messages…</p>
      <p v-else-if="listError" class="acct-flash acct-flash--error">{{ listError }}</p>

      <div v-else-if="conversations.length === 0" class="acct-empty">
        <p class="acct-empty__title">No messages yet</p>
        <p class="acct-empty__note">
          Ask a shop whether they have something, or about an order you've placed. Their reply
          shows up here.
        </p>
      </div>

      <ul v-else class="msg-list">
        <li v-for="c in conversations" :key="c.id">
          <button
            type="button"
            class="msg-row"
            :class="{ 'msg-row--unread': c.unreadCount > 0 }"
            @click="openConversation(c.id)"
          >
            <img
              v-if="c.store.imageUrl"
              class="msg-row__avatar"
              :src="resolveImageUrl(c.store.imageUrl)!"
              alt=""
              loading="lazy"
            />
            <span v-else class="msg-row__avatar msg-row__avatar--letter">{{ initial(c.store.name) }}</span>

            <span class="msg-row__text">
              <span class="msg-row__top">
                <span class="msg-row__name">{{ c.store.name }}</span>
                <span class="msg-row__when">{{ when(c.lastMessageAt) }}</span>
              </span>
              <span class="msg-row__preview">
                {{ c.lastMessage?.from === 'customer' ? 'You: ' : '' }}{{ c.lastMessage?.body }}
              </span>
            </span>

            <span v-if="c.unreadCount > 0" class="msg-row__badge" :aria-label="`${c.unreadCount} unread`">
              {{ c.unreadCount }}
            </span>
          </button>
        </li>
      </ul>
    </template>

    <template v-else>
      <button type="button" class="msg-back" @click="backToList">
        <ChevronLeft :size="17" :stroke-width="2" />
        All messages
      </button>

      <section class="acct-card msg-thread">
        <header class="msg-thread__head">
          <p class="acct-card__title">{{ shopName }}</p>
          <a v-if="shopHref" :href="shopHref" class="acct-link">Visit shop</a>
        </header>

        <div ref="scroller" class="msg-thread__body" aria-live="polite">
          <p v-if="threadLoading && !thread" class="acct-card__note">Loading…</p>
          <p v-else-if="threadError" class="acct-flash acct-flash--error">{{ threadError }}</p>
          <p v-else-if="!thread" class="msg-thread__hint">
            Ask {{ shopName }} anything — whether they have something in stock, or when your order
            will be ready. They'll reply here.
          </p>

          <div
            v-for="m in thread?.messages ?? []"
            :key="m.id"
            class="msg-bubble"
            :class="m.from === 'customer' ? 'msg-bubble--mine' : 'msg-bubble--theirs'"
          >
            <p v-if="m.order" class="msg-bubble__about">About order #{{ m.order.ticketNumber }}</p>
            <p class="msg-bubble__body">{{ m.body }}</p>
            <p class="msg-bubble__when">{{ when(m.createdAt) }}</p>
          </div>
        </div>

        <form class="msg-compose" @submit.prevent="send">
          <p v-if="attached" class="msg-compose__about">
            About order #{{ attached.ticketNumber }}
            <!-- Not removable on a draft that came from an order: that order is
                 how the server knows which shop this is going to. -->
            <button
              v-if="!draft?.orderId"
              type="button"
              class="msg-compose__drop"
              aria-label="Don't attach this order"
              @click="attached = null"
            >
              <X :size="13" :stroke-width="2.2" />
            </button>
          </p>

          <div class="msg-compose__row">
            <textarea
              v-model="body"
              class="acct-textarea msg-compose__input"
              rows="2"
              :maxlength="MAX_BODY"
              :placeholder="`Message ${shopName}`"
              aria-label="Your message"
              @keydown.enter.exact.prevent="send"
            />
            <button type="submit" class="acct-btn" :disabled="sending || !body.trim()">
              <Send :size="16" :stroke-width="2" />
              {{ sending ? 'Sending…' : 'Send' }}
            </button>
          </div>
          <p class="msg-compose__hint">Enter to send · Shift+Enter for a new line</p>
          <p v-if="sendError" class="acct-flash acct-flash--error">{{ sendError }}</p>
        </form>
      </section>
    </template>
  </div>
</template>

<style scoped>
/* -- The list ------------------------------------------------------------ */

.msg-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--acct-rule);
  border-radius: var(--acct-radius);
  background: var(--acct-surface);
  overflow: hidden;
}

.msg-list li + li {
  border-top: 1px solid var(--acct-rule);
}

.msg-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 16px 20px;
  border: none;
  background: none;
  font: inherit;
  text-align: left;
  color: var(--acct-ink);
  cursor: pointer;
}

.msg-row:hover {
  background: #fafbfa;
}

.msg-row__avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 50%;
  object-fit: cover;
}

.msg-row__avatar--letter {
  display: grid;
  place-items: center;
  background: rgba(26, 107, 60, 0.1);
  color: var(--acct-green);
  font-weight: 800;
}

.msg-row__text {
  flex: 1;
  min-width: 0;
}

.msg-row__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.msg-row__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
}

.msg-row__when {
  flex-shrink: 0;
  font-size: 12.5px;
  color: var(--acct-faint);
}

.msg-row__preview {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  color: var(--acct-muted);
}

.msg-row--unread .msg-row__name,
.msg-row--unread .msg-row__preview {
  font-weight: 700;
  color: var(--acct-ink);
}

.msg-row__badge {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--acct-green);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

/* -- A thread ------------------------------------------------------------ */

.msg-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 14px;
  padding: 0;
  border: none;
  background: none;
  color: var(--acct-muted);
  font: 600 14px/1 inherit;
  cursor: pointer;
}

.msg-back:hover {
  color: var(--acct-ink);
}

.msg-thread {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.msg-thread__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--acct-rule);
}

.msg-thread__head .acct-card__title {
  margin: 0;
}

/* Tall enough to read a conversation, short enough that the box to answer in
   is still on screen on a laptop. */
.msg-thread__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: min(52vh, 460px);
  padding: 18px 20px;
  overflow-y: auto;
  background: var(--acct-ground);
}

.msg-thread__hint {
  margin: auto 0;
  text-align: center;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--acct-muted);
}

.msg-bubble {
  max-width: min(78%, 460px);
  padding: 10px 14px;
  border-radius: 16px;
}

.msg-bubble--mine {
  align-self: flex-end;
  border-bottom-right-radius: 4px;
  background: var(--acct-green);
  color: #fff;
}

.msg-bubble--theirs {
  align-self: flex-start;
  border: 1px solid var(--acct-rule);
  border-bottom-left-radius: 4px;
  background: var(--acct-surface);
  color: var(--acct-ink);
}

.msg-bubble__about {
  margin: 0 0 4px;
  font-size: 11.5px;
  font-weight: 800;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  opacity: 0.75;
}

/* Kept exactly as typed — a list of items is the likeliest thing someone
   sends, and collapsing its line breaks would run it into one line. */
.msg-bubble__body {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.msg-bubble__when {
  margin: 4px 0 0;
  font-size: 11.5px;
  opacity: 0.65;
  text-align: right;
}

/* -- Composer ------------------------------------------------------------ */

.msg-compose {
  padding: 14px 20px 16px;
  border-top: 1px solid var(--acct-rule);
}

.msg-compose__about {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  padding: 4px 6px 4px 10px;
  border-radius: 999px;
  background: rgba(26, 107, 60, 0.1);
  color: var(--acct-green);
  font-size: 12.5px;
  font-weight: 700;
}

.msg-compose__drop {
  display: grid;
  place-items: center;
  padding: 2px;
  border: none;
  border-radius: 50%;
  background: none;
  color: inherit;
  cursor: pointer;
}

.msg-compose__drop:hover {
  background: rgba(26, 107, 60, 0.15);
}

.msg-compose__row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.msg-compose__input {
  flex: 1;
  min-height: 46px;
  max-height: 160px;
}

.msg-compose__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--acct-faint);
}

@media (max-width: 560px) {
  .msg-compose__row {
    flex-direction: column;
    align-items: stretch;
  }

  .msg-compose__hint {
    display: none;
  }
}
</style>
