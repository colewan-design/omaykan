<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import {
  CircleDollarSign,
  CreditCard,
  Gift,
  Heart,
  LayoutDashboard,
  LogOut,
  MapPin,
  MessageCircle,
  Package,
  User,
  Users,
} from '@lucide/vue'
import FdHeader from '@pos/web/landing/FdHeader.vue'
import FdFooter from '@pos/web/landing/FdFooter.vue'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { useUnreadMessages } from '@pos/web/commerce/messages'
import { MESSAGING_ENABLED } from '@pos/web/commerce/features'
import CustomerAuth from './CustomerAuth.vue'
import AccountDashboard from './sections/AccountDashboard.vue'
import AccountPreferences from './sections/AccountPreferences.vue'
import DeliveryAddresses from './sections/DeliveryAddresses.vue'
import PaymentMethods from './sections/PaymentMethods.vue'
import OrderHistoryPanel from './sections/OrderHistoryPanel.vue'
import MessagesPanel from './sections/MessagesPanel.vue'
import WishlistPanel from './sections/WishlistPanel.vue'
import StoreCredit from './sections/StoreCredit.vue'
import GiftCards from './sections/GiftCards.vue'
import ReferAFriend from './sections/ReferAFriend.vue'

// The customer portal, in the storefront's highland redesign: a sidebar of
// sections on the left, and on the right either the dashboard — who you are,
// your latest orders, your addresses and saved items at a glance — or the
// section picked.
//
// On a phone the sidebar becomes a row of chips above the content rather than
// a hamburger: a menu hidden behind an icon hides the only navigation there is.
//
// This is a separate Vite entry, like /about and /cart, so the sections are
// not routes — the chosen one lives in ?section= and is pushed to history, so
// Back walks out of a section rather than off the site. No ?section= is the
// dashboard.

const SHOP_HREF = '/'

const account = useCustomerAccount()
const unreadMessages = useUnreadMessages().unread

interface Section {
  id: string
  label: string
  icon: Component
  component: Component
  /** A count worth showing beside the label. */
  badge?: () => number
}

// The dashboard is not in this list: it is what no section means.
const sections: Section[] = [
  { id: 'orders', label: 'Orders', icon: Package, component: OrderHistoryPanel },
  { id: 'wishlist', label: 'Wishlist', icon: Heart, component: WishlistPanel },
  ...(MESSAGING_ENABLED
    ? [{ id: 'messages', label: 'Messages', icon: MessageCircle, component: MessagesPanel, badge: () => unreadMessages.value }]
    : []),
  { id: 'addresses', label: 'Addresses', icon: MapPin, component: DeliveryAddresses },
  // The id predates the redesign's "Profile" label; links in the wild use it.
  { id: 'preferences', label: 'Profile', icon: User, component: AccountPreferences },
  { id: 'payment', label: 'Payment methods', icon: CreditCard, component: PaymentMethods },
]

// Below a rule: real sections, but not what most visits are for.
const extras: Section[] = [
  { id: 'credit', label: 'Store credit', icon: CircleDollarSign, component: StoreCredit },
  { id: 'gift-cards', label: 'Gift cards', icon: Gift, component: GiftCards },
  { id: 'referrals', label: 'Refer a friend', icon: Users, component: ReferAFriend },
]

const everySection = [...sections, ...extras]

function readSection(): string {
  try {
    const requested = new URLSearchParams(window.location.search).get('section')?.trim() ?? ''
    return everySection.some((section) => section.id === requested) ? requested : ''
  } catch {
    return ''
  }
}

const activeId = ref(readSection())
const active = computed(() => everySection.find((section) => section.id === activeId.value) ?? null)

/*
 * Where to go once they're signed in.
 *
 * Checkout sends a signed-out shopper here with ?next= pointing back at the
 * cart, so signing in returns them to it rather than to the portal. Nothing
 * depends on this — the cart is in localStorage either way — it just stops the
 * sign-in feeling like a detour.
 *
 * Only a same-origin path is honoured. Anything else is an open redirect, and
 * this URL is one a shopper could be handed by somebody else.
 */
function safeNext(): string {
  try {
    const raw = new URLSearchParams(window.location.search).get('next') ?? ''
    // A single leading slash and no scheme: "/cart" yes, "//evil.com" and
    // "https://evil.com" no.
    return /^\/(?!\/)[^\s]*$/.test(raw) ? raw : ''
  } catch {
    return ''
  }
}

watch(
  () => account.signedIn.value,
  (signedIn) => {
    const next = safeNext()
    if (signedIn && next) window.location.replace(next)
  },
  { immediate: true },
)

/** `extra` carries what a section is being opened about — ?order= for Orders. */
function openSection(id: string, extra: Record<string, string> = {}) {
  activeId.value = id
  const params = new URLSearchParams(extra)
  if (id) params.set('section', id)
  const query = params.toString()
  window.history.pushState({}, '', query ? `${window.location.pathname}?${query}` : window.location.pathname)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function applyUrl() {
  activeId.value = readSection()
}

onMounted(() => window.addEventListener('popstate', applyUrl))
onBeforeUnmount(() => window.removeEventListener('popstate', applyUrl))

// The tab title is how someone finds this page again among a dozen open tabs.
watch(
  active,
  (section) => {
    document.title = section ? `${section.label} — Omaykan` : 'Your account — Omaykan'
  },
  { immediate: true },
)

// -- Signing out -----------------------------------------------------------

// Two-step rather than a browser confirm(). The stakes are lower than they
// were — addresses and cards are on the server now, so this ends a session
// rather than deleting anything — but the gift codes kept on this device do go
// with it, and a mis-tapped "Log out" on a phone is easy.
const confirmingSignOut = ref(false)
const signingOut = ref(false)

async function signOut() {
  signingOut.value = true
  try {
    await account.signOut()
  } finally {
    signingOut.value = false
  }
  confirmingSignOut.value = false
  openSection('')
}

function search(term: string) {
  window.location.href = term ? `/?q=${encodeURIComponent(term)}` : '/'
}
</script>

<template>
  <div class="landing fd acct">
    <!-- No account banner here: the card below already is the invitation. No
         category menu either: this page isn't a shelf. -->
    <FdHeader
      :shop-href="SHOP_HREF"
      :account-banner="false"
      :category-nav="false"
      @search="search"
    />

    <main class="acct-main">
      <!-- A stored token is being traded for the account it belongs to. Showing
           the sign-in card here would tell a signed-in customer they are signed
           out, and they'd start typing before it resolved a moment later. -->
      <section v-if="account.hydrating.value" class="acct-gate" aria-busy="true">
        <p class="acct-gate__waiting">Getting your account…</p>
      </section>

      <!-- Signed out: sign in, register, or recover a password. -->
      <CustomerAuth v-else-if="!account.signedIn.value" />

      <!-- Signed in: the sidebar, and the dashboard or the section it opened. -->
      <div v-else class="acct-shell">
        <aside class="acct-side">
          <p class="acct-side__title">My Account</p>

          <nav aria-label="Your account">
            <ul class="acct-nav">
              <li>
                <a
                  class="acct-nav__row"
                  :class="{ 'acct-nav__row--on': !active }"
                  href="/account"
                  :aria-current="!active ? 'page' : undefined"
                  @click.prevent="openSection('')"
                >
                  <LayoutDashboard class="acct-nav__icon" :size="19" :stroke-width="1.7" />
                  <span class="acct-nav__label">Dashboard</span>
                </a>
              </li>

              <li v-for="section in sections" :key="section.id">
                <a
                  class="acct-nav__row"
                  :class="{ 'acct-nav__row--on': section.id === activeId }"
                  :href="`?section=${section.id}`"
                  :aria-current="section.id === activeId ? 'page' : undefined"
                  @click.prevent="openSection(section.id)"
                >
                  <component :is="section.icon" class="acct-nav__icon" :size="19" :stroke-width="1.7" />
                  <span class="acct-nav__label">{{ section.label }}</span>
                  <span v-if="(section.badge?.() ?? 0) > 0" class="acct-nav__badge">
                    {{ section.badge!() > 9 ? '9+' : section.badge!() }}
                  </span>
                </a>
              </li>

              <li class="acct-nav__rule" role="presentation"></li>

              <li v-for="section in extras" :key="section.id">
                <a
                  class="acct-nav__row acct-nav__row--quiet"
                  :class="{ 'acct-nav__row--on': section.id === activeId }"
                  :href="`?section=${section.id}`"
                  :aria-current="section.id === activeId ? 'page' : undefined"
                  @click.prevent="openSection(section.id)"
                >
                  <component :is="section.icon" class="acct-nav__icon" :size="19" :stroke-width="1.7" />
                  <span class="acct-nav__label">{{ section.label }}</span>
                </a>
              </li>

              <li class="acct-nav__rule" role="presentation"></li>

              <li>
                <button
                  type="button"
                  class="acct-nav__row acct-nav__row--out"
                  :aria-expanded="confirmingSignOut"
                  @click="confirmingSignOut = !confirmingSignOut"
                >
                  <LogOut class="acct-nav__icon" :size="19" :stroke-width="1.7" />
                  <span class="acct-nav__label">Log out</span>
                </button>
              </li>
            </ul>
          </nav>

          <div v-if="confirmingSignOut" class="acct-side__confirm">
            <p>
              This signs out this device only. Your addresses, payment methods and orders stay on
              your account — saved gift card codes are kept on this device and will go.
            </p>
            <div class="acct-actions" style="margin-top: 12px">
              <button type="button" class="acct-btn" :disabled="signingOut" @click="signOut">
                {{ signingOut ? 'Signing out…' : 'Log out' }}
              </button>
              <button type="button" class="acct-link" :disabled="signingOut" @click="confirmingSignOut = false">
                Cancel
              </button>
            </div>
          </div>

          <!-- The reference's sidebar foot: misty ridges, pines, a line of type. -->
          <div class="acct-side__art" aria-hidden="true">
            <svg viewBox="0 0 240 150" preserveAspectRatio="xMidYMax meet">
              <path d="M0 150V70l34-26 22 16 40-44 36 34 26-16 34 22 48-30v124Z" fill="#ddd4c6" />
              <path d="m96 16-9 10 6-2 4 5 5-6 4 3Z" fill="#ffffff" />
              <path d="M0 150V96l42-26 30 18 46-34 40 30 34-14 48 26v54Z" fill="#c7bdad" />
              <path d="M0 150v-30l52-16 38 14 44-20 50 22 56-12v42Z" fill="#aca290" />
              <g fill="#5f6a5b">
                <path d="m18 150 9-30 9 30Z" />
                <path d="m34 150 7-22 7 22Z" />
                <path d="m50 150 10-36 10 36Z" />
                <path d="m66 150 6-18 6 18Z" />
                <path d="m176 150 9-32 9 32Z" />
                <path d="m192 150 7-22 7 22Z" />
                <path d="m206 150 10-38 10 38Z" />
              </g>
            </svg>
            <p>Local hands.<br />Brighter horizons.</p>
          </div>
        </aside>

        <section class="acct-content">
          <nav class="acct-crumbs" aria-label="Breadcrumb">
            <a href="/">Home</a>
            <span aria-hidden="true">›</span>
            <a v-if="active" href="/account" @click.prevent="openSection('')">Account</a>
            <span v-else class="acct-crumbs__here">Account</span>
            <template v-if="active">
              <span aria-hidden="true">›</span>
              <span class="acct-crumbs__here">{{ active.label }}</span>
            </template>
          </nav>

          <AccountDashboard v-if="!active" @open="openSection" />
          <component :is="active.component" v-else />
        </section>
      </div>
    </main>

    <div class="sf-weave" aria-hidden="true"></div>
    <FdFooter :shop-href="SHOP_HREF" />
  </div>
</template>

<style scoped>
.acct-main {
  min-height: 60vh;
  background: var(--acct-ground);
}

/* The signed-out gate is styled in account.css, not here: CustomerAuth.vue
   renders it, and a scoped rule would reach that component's root element and
   nothing inside it. */

/* ── The shell ───────────────────────────────────────────────────────── */

/* Columns stretch rather than sitting at `start`: the sidebar has to run the
   full height of whatever is beside it, or it ends in mid-air. */
.acct-shell {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  max-width: 1320px;
  margin: 0 auto;
}

/* ── Sidebar ─────────────────────────────────────────────────────────── */

.acct-side {
  display: flex;
  flex-direction: column;
  padding: 36px 20px 0;
  background: #f1eadf;
  border-right: 1px solid var(--acct-rule);
}

.acct-side__title {
  margin: 0 0 20px 10px;
  font-family: var(--sf-serif);
  font-size: 21px;
  font-weight: 700;
  color: var(--acct-ink);
}

.acct-nav {
  margin: 0;
  padding: 0;
  list-style: none;
}

.acct-nav__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 11px 12px;
  border: none;
  border-radius: 6px;
  background: none;
  color: var(--acct-ink);
  font-family: inherit;
  font-size: 14.5px;
  font-weight: 500;
  line-height: 1.2;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background 150ms, color 150ms;
}

.acct-nav__row:hover { background: rgba(180, 83, 42, 0.07); }
.acct-nav__row:focus-visible { outline: 2px solid var(--acct-accent); outline-offset: -2px; }

/* The reference's selected row: a terracotta wash with a bar on its edge. */
.acct-nav__row--on {
  background: #f3d9cb;
  color: var(--acct-accent-deep);
  font-weight: 700;
}
.acct-nav__row--on::before {
  content: '';
  position: absolute;
  top: 6px;
  bottom: 6px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--acct-accent);
}
.acct-nav__row--on:hover { background: #f0d2c1; }

.acct-nav__icon { flex-shrink: 0; color: currentColor; }
.acct-nav__row--quiet { font-size: 13.5px; color: var(--acct-muted); }
.acct-nav__row--quiet.acct-nav__row--on { color: var(--acct-accent-deep); }

.acct-nav__label { flex: 1; min-width: 0; }

.acct-nav__badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--acct-accent);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
}

.acct-nav__rule {
  height: 1px;
  margin: 12px 10px;
  background: var(--acct-rule);
}

.acct-side__confirm {
  margin: 8px 10px 0;
  padding: 14px;
  border: 1px solid var(--acct-rule);
  border-radius: 8px;
  background: var(--acct-surface);
}
.acct-side__confirm p {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--acct-muted);
}

.acct-side__art {
  margin: auto -20px 0;
  padding-top: 48px;
  text-align: center;
}
.acct-side__art svg { display: block; width: 100%; height: auto; }
.acct-side__art p {
  margin: 0;
  padding: 14px 0 28px;
  background: #aca290;
  font-family: var(--sf-serif);
  font-size: 15px;
  line-height: 1.5;
  color: #ffffff;
}

/* ── Content ─────────────────────────────────────────────────────────── */

.acct-content {
  min-width: 0;
  padding: 22px 40px 64px;
}

.acct-crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  font-size: 13px;
  color: var(--acct-faint);
}
.acct-crumbs a { color: var(--acct-muted); }
.acct-crumbs a:hover { color: var(--acct-accent); text-decoration: underline; text-underline-offset: 3px; }
.acct-crumbs__here { color: var(--acct-ink); font-weight: 600; }

/* ── Narrower screens: the sidebar becomes a row of chips ────────────── */

@media (max-width: 900px) {
  .acct-shell { display: block; }

  .acct-side {
    padding: 14px 0 0;
    border-right: none;
    border-bottom: 1px solid var(--acct-rule);
  }

  .acct-side__title,
  .acct-side__art,
  .acct-nav__rule { display: none; }

  .acct-nav {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding: 0 var(--fd-gutter) 14px;
    scrollbar-width: none;
  }
  .acct-nav::-webkit-scrollbar { display: none; }

  .acct-nav__row {
    width: auto;
    gap: 8px;
    padding: 9px 14px;
    border: 1px solid var(--acct-rule);
    border-radius: 999px;
    background: var(--acct-surface);
    font-size: 13.5px;
    white-space: nowrap;
  }
  .acct-nav__row--on { border-color: var(--acct-accent); }
  .acct-nav__row--on::before { display: none; }

  .acct-side__confirm { margin: 0 var(--fd-gutter) 14px; }

  .acct-content { padding: 18px var(--fd-gutter) 56px; }
}
</style>
