<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import {
  ChevronLeft,
  CircleDollarSign,
  CreditCard,
  Gift,
  History,
  MapPin,
  Settings,
  Users,
} from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import FdHeader from '@pos/web/landing/FdHeader.vue'
import FdFooter from '@pos/web/landing/FdFooter.vue'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import CustomerAuth from './CustomerAuth.vue'
import AccountPreferences from './sections/AccountPreferences.vue'
import DeliveryAddresses from './sections/DeliveryAddresses.vue'
import PaymentMethods from './sections/PaymentMethods.vue'
import OrderHistoryPanel from './sections/OrderHistoryPanel.vue'
import StoreCredit from './sections/StoreCredit.vue'
import GiftCards from './sections/GiftCards.vue'
import ReferAFriend from './sections/ReferAFriend.vue'

// The customer portal.
//
// The menu panel is the whole design: a greeting, the address we'd write to,
// and one row per thing a customer might have come here to change. On a wide
// screen that panel is the left column and the chosen section sits beside it;
// on a phone the panel *is* the page until a row is tapped, and the section
// replaces it — a 320px sidebar next to a form is unusable on a phone, and a
// sidebar that collapses into a hamburger hides the only navigation there is.
//
// This is a separate Vite entry, like /about and /signup, so the sections are
// not routes — the chosen one lives in ?section= and is pushed to history, so
// Back walks out of a section rather than off the site.

const SHOP_HREF = '/'

const account = useCustomerAccount()

interface Section {
  id: string
  label: string
  icon: Component
  component: Component
  /** Second line under the label, the way the reference shows the balance. */
  detail?: () => string
}

const sections: Section[] = [
  { id: 'preferences', label: 'Account preferences', icon: Settings, component: AccountPreferences },
  { id: 'addresses', label: 'Delivery addresses', icon: MapPin, component: DeliveryAddresses },
  { id: 'payment', label: 'Payment methods', icon: CreditCard, component: PaymentMethods },
  { id: 'orders', label: 'Order history', icon: History, component: OrderHistoryPanel },
  {
    id: 'credit',
    label: 'Store credit',
    icon: CircleDollarSign,
    component: StoreCredit,
    detail: () => `${formatCurrency(account.storeCreditCents.value)} available`,
  },
  { id: 'gift-cards', label: 'Gift cards', icon: Gift, component: GiftCards },
  { id: 'referrals', label: 'Refer a friend', icon: Users, component: ReferAFriend },
]

function readSection(): string {
  try {
    const requested = new URLSearchParams(window.location.search).get('section')?.trim() ?? ''
    return sections.some((section) => section.id === requested) ? requested : ''
  } catch {
    return ''
  }
}

const activeId = ref(readSection())
const active = computed(() => sections.find((section) => section.id === activeId.value) ?? null)

/*
 * Where to go once they're signed in.
 *
 * Checkout sends a signed-out shopper here with ?next= pointing back at the
 * page they were shopping on, because the cart lives in the site header and
 * landing them on the portal afterwards would mean walking back and reopening
 * it. Nothing depends on this — the cart is in localStorage either way — it
 * just stops the sign-in feeling like a detour.
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

function openSection(id: string) {
  activeId.value = id
  window.history.pushState({}, '', `${window.location.pathname}?section=${id}`)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function closeSection() {
  activeId.value = ''
  window.history.pushState({}, '', window.location.pathname)
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

/** First name only — "Hi, Christian." reads as a person, "Hi, Christian Colewan." does not. */
const firstName = computed(() => account.account.value?.name.trim().split(/\s+/)[0] ?? '')

// -- Signing out -----------------------------------------------------------

// Two-step rather than a browser confirm(). The stakes are lower than they
// were — addresses and cards are on the server now, so this ends a session
// rather than deleting anything — but the gift codes kept on this device do go
// with it, and a mis-tapped "Sign out" on a phone is easy.
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
  closeSection()
}

function search(term: string) {
  window.location.href = term ? `/?q=${encodeURIComponent(term)}` : '/'
}
</script>

<template>
  <div class="landing fd acct">
    <!-- No account banner here: the card below already is the invitation. -->
    <FdHeader :shop-href="SHOP_HREF" :account-banner="false" @search="search" />

    <main class="acct-main">
      <!-- A stored token is being traded for the account it belongs to. Showing
           the sign-in card here would tell a signed-in customer they are signed
           out, and they'd start typing before it resolved a moment later. -->
      <section v-if="account.hydrating.value" class="acct-gate" aria-busy="true">
        <p class="acct-gate__waiting">Getting your account…</p>
      </section>

      <!-- Signed out: sign in, register, or recover a password. -->
      <CustomerAuth v-else-if="!account.signedIn.value" />

      <!-- Signed in: the menu panel, and whatever it has opened. -->
      <div v-else class="acct-shell" :class="{ 'acct-shell--open': active }">
        <nav class="acct-menu" aria-label="Your account">
          <h1 class="acct-menu__greeting">Hi, {{ firstName }}.</h1>
          <p class="acct-menu__email">{{ account.account.value?.email }}</p>

          <ul class="acct-menu__list">
            <li v-for="section in sections" :key="section.id">
              <a
                class="acct-menu__row"
                :class="{ 'acct-menu__row--on': section.id === activeId }"
                :href="`?section=${section.id}`"
                :aria-current="section.id === activeId ? 'page' : undefined"
                @click.prevent="openSection(section.id)"
              >
                <component :is="section.icon" class="acct-menu__icon" :size="24" :stroke-width="1.4" />
                <span class="acct-menu__text">
                  <span class="acct-menu__label">{{ section.label }}</span>
                  <span v-if="section.detail" class="acct-menu__detail">{{ section.detail() }}</span>
                </span>
              </a>
            </li>
          </ul>

          <div class="acct-menu__out">
            <button
              v-if="!confirmingSignOut"
              type="button"
              class="acct-menu__signout"
              @click="confirmingSignOut = true"
            >
              Sign out
            </button>
            <template v-else>
              <p class="acct-menu__warn">
                This signs out this device only. Your addresses, payment methods and orders stay on
                your account — saved gift card codes are kept on this device and will go.
              </p>
              <div class="acct-actions" style="margin-top: 10px">
                <button type="button" class="acct-btn" :disabled="signingOut" @click="signOut">
                  {{ signingOut ? 'Signing out…' : 'Sign out' }}
                </button>
                <button
                  type="button"
                  class="acct-link"
                  :disabled="signingOut"
                  @click="confirmingSignOut = false"
                >
                  Cancel
                </button>
              </div>
            </template>
          </div>
        </nav>

        <section v-if="active" class="acct-panel">
          <button type="button" class="acct-panel__back" @click="closeSection">
            <ChevronLeft :size="17" :stroke-width="2" />
            Your account
          </button>
          <component :is="active.component" />
        </section>

        <!-- Wide screens only: the right column would otherwise be blank. -->
        <section v-else class="acct-panel acct-panel--idle">
          <p class="acct-panel__idle-title">Everything about your orders, in one place.</p>
          <p class="acct-panel__idle-note">Pick something on the left to get started.</p>
        </section>
      </div>
    </main>

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

/* Columns stretch rather than sitting at `start`: the white menu panel has to
   run the full height of whatever the section beside it is, or it ends in
   mid-air with the grey ground showing under it. */
.acct-shell {
  display: grid;
  grid-template-columns: 336px minmax(0, 1fr);
  gap: 0;
  max-width: 1180px;
  margin: 0 auto;
}

/* ── Menu panel — the reference ──────────────────────────────────────── */

/* Both edges ruled: on a wide screen the panel floats inside the centred
   container, and one border would leave it looking like it had come loose. */
.acct-menu {
  padding: 56px 40px 64px;
  background: var(--acct-surface);
  border-left: 1px solid var(--acct-rule);
  border-right: 1px solid var(--acct-rule);
}

.acct-menu__greeting {
  margin: 0 0 12px;
  font-size: 40px;
  font-weight: 800;
  letter-spacing: -0.035em;
  line-height: 1.05;
  color: var(--acct-ink);
  /* A long first name shouldn't push the panel wide or clip. */
  overflow-wrap: anywhere;
}

.acct-menu__email {
  margin: 0 0 44px;
  font-size: 14px;
  color: var(--acct-muted);
  overflow-wrap: anywhere;
}

.acct-menu__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.acct-menu__row {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  align-items: center;
  gap: 22px;
  padding: 15px 0;
  color: var(--acct-ink);
  text-decoration: none;
}

.acct-menu__icon {
  justify-self: start;
  color: #4b5563;
  transition: color 0.15s ease;
}

.acct-menu__row:hover .acct-menu__icon,
.acct-menu__row--on .acct-menu__icon {
  color: var(--acct-green);
}

.acct-menu__text {
  min-width: 0;
}

/* Regular, not medium: at 500 the system UI font on Windows snaps to semibold
   and the whole list reads as seven headings. The greeting is the only heavy
   thing on the panel. */
.acct-menu__label {
  display: block;
  font-size: 15.5px;
  font-weight: 400;
  line-height: 1.35;
}

.acct-menu__row:hover .acct-menu__label {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.acct-menu__row--on .acct-menu__label {
  font-weight: 700;
}

.acct-menu__detail {
  display: block;
  margin-top: 3px;
  font-size: 13.5px;
  color: var(--acct-muted);
}

.acct-menu__out {
  margin-top: 46px;
}

.acct-menu__signout {
  padding: 0;
  border: none;
  background: none;
  color: var(--acct-ink);
  font: 600 15px/1 inherit;
  text-decoration: underline;
  text-underline-offset: 5px;
  cursor: pointer;
}

.acct-menu__warn {
  margin: 0;
  max-width: 34ch;
  font-size: 13px;
  line-height: 1.6;
  color: var(--acct-muted);
}

/* ── Right column ────────────────────────────────────────────────────── */

.acct-panel {
  padding: 56px 48px 72px;
  min-width: 0;
}

.acct-panel__back {
  display: none;
  align-items: center;
  gap: 4px;
  margin-bottom: 18px;
  padding: 0;
  border: none;
  background: none;
  color: var(--acct-muted);
  font: 600 14px/1 inherit;
  cursor: pointer;
}

.acct-panel--idle {
  padding-top: 96px;
}

.acct-panel__idle-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--acct-ink);
}

.acct-panel__idle-note {
  margin: 0;
  font-size: 14.5px;
  color: var(--acct-muted);
}

/* ── Phones: one column at a time ────────────────────────────────────── */

@media (max-width: 900px) {
  .acct-shell {
    display: block;
  }

  .acct-menu {
    padding: 44px var(--fd-gutter) 56px;
    border-left: none;
    border-right: none;
  }

  .acct-menu__greeting {
    font-size: 34px;
  }

  .acct-panel {
    padding: 30px var(--fd-gutter) 64px;
  }

  /* The idle right column has nothing to say when it isn't beside anything. */
  .acct-panel--idle {
    display: none;
  }

  /* A section is open: it takes the page, and Back returns to the menu. */
  .acct-shell--open .acct-menu {
    display: none;
  }

  .acct-shell--open .acct-panel__back {
    display: inline-flex;
  }
}
</style>
