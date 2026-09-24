<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  googleSignInAvailable,
  releaseGoogleButton,
  renderGoogleButton,
} from '@pos/core/services/google'
import {
  BarChart3,
  Bike,
  ClipboardList,
  LayoutDashboard,
  MessageSquare,
  Package,
  Settings as SettingsIcon,
  ShoppingBag,
  Store,
  Users,
} from '@lucide/vue'
import { api, ApiError, loadStoredToken, setToken, type Operator, type Settings } from './api'
import AdminShell, { type NavItem } from './AdminShell.vue'
import DashboardView from './views/DashboardView.vue'
import OrdersView from './views/OrdersView.vue'
import ProductsView from './views/ProductsView.vue'
import SellersView from './views/SellersView.vue'
import CustomersView from './views/CustomersView.vue'
import AnalyticsView from './views/AnalyticsView.vue'
import ReportsView from './views/ReportsView.vue'
import SettingsView from './views/SettingsView.vue'
import SupportInbox from './SupportInbox.vue'
import RiderReview from './RiderReview.vue'
import PageHero from './PageHero.vue'
import { currentToken } from './api'

/*
 * The operator portal: a sign-in gate, and behind it nine screens in one shell.
 *
 * ## Why there is no router
 *
 * The token lives in memory and in sessionStorage, and every screen is one
 * fetch away. A route table would buy deep links at the cost of a full reload
 * per navigation; a hash buys the deep link for nothing, so the view is kept
 * in `location.hash` and read back on load. Nothing else about the page is in
 * the URL — a filtered order list is a working state, not an address.
 *
 * ## Rider review is here and not in the design
 *
 * The screens this portal was drawn from do not include one, but riders sign
 * up unvetted and can do nothing until a human has seen their licence. Drop
 * the screen and registration becomes a dead end that nobody can clear. It is
 * the ninth item for that reason.
 */

const VIEWS = [
  { key: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { key: 'orders', label: 'Orders', icon: ShoppingBag },
  { key: 'products', label: 'Products', icon: Package },
  { key: 'sellers', label: 'Sellers', icon: Store },
  { key: 'customers', label: 'Customers', icon: Users },
  { key: 'analytics', label: 'Analytics', icon: BarChart3 },
  { key: 'reports', label: 'Reports', icon: ClipboardList },
  { key: 'messages', label: 'Messages', icon: MessageSquare },
  { key: 'riders', label: 'Rider review', icon: Bike },
  { key: 'settings', label: 'Settings', icon: SettingsIcon },
] as const

type ViewKey = (typeof VIEWS)[number]['key']

const operator = ref<Operator | null>(null)
const settings = ref<Settings | null>(null)
const view = ref<ViewKey>('dashboard')
const search = ref('')

const emailInput = ref('')
const passwordInput = ref('')
const signingIn = ref(false)
const authError = ref('')
const restoring = ref(true)

const navItems = computed<NavItem[]>(() => VIEWS.map((item) => ({ ...item })))
const brandName = computed(() => settings.value?.name ?? 'Omaykan')

// ── Session ───────────────────────────────────────────────────────────────

/**
 * A token in sessionStorage is only a claim; the portal asks the server
 * whether it is still good before painting a signed-in shell around it. A
 * revoked token would otherwise render nine screens of error messages.
 */
async function restore() {
  if (!loadStoredToken()) {
    restoring.value = false
    return
  }

  try {
    operator.value = (await api.me()).admin
    await loadSettings()
    readHash()
  } catch {
    setToken('')
    operator.value = null
  } finally {
    restoring.value = false
  }
}

onMounted(restore)

async function signIn() {
  if (!emailInput.value.trim() || !passwordInput.value) return

  signingIn.value = true
  authError.value = ''
  try {
    operator.value = (await api.signIn(emailInput.value.trim(), passwordInput.value)).admin
    passwordInput.value = ''
    await loadSettings()
    readHash()
  } catch (err) {
    authError.value = err instanceof Error ? err.message : 'Could not sign in.'
  } finally {
    signingIn.value = false
  }
}

// -- Google --------------------------------------------------------------

/*
 * A second door onto an operator account that already exists. The endpoint
 * refuses any Google identity with no row behind it — operator accounts are
 * made from the console — so this button widens nothing; it only saves the
 * password.
 */

const googleSlot = ref<HTMLElement | null>(null)
/** Blank client id, or a blocked script: either way the gap closes up. */
const googleUsable = ref(googleSignInAvailable())

async function onGoogleCredential(credential: string) {
  if (signingIn.value) return

  signingIn.value = true
  authError.value = ''
  try {
    operator.value = (await api.signInWithGoogle(credential)).admin
    passwordInput.value = ''
    await loadSettings()
    readHash()
  } catch (err) {
    authError.value = err instanceof Error ? err.message : 'Could not sign in.'
  } finally {
    signingIn.value = false
  }
}

/*
 * The slot only exists while the gate is on screen, and the gate is behind
 * `restoring` as well as `operator` — so the button is drawn when the switch
 * puts the element in the DOM rather than once on mount. `flush: 'post'` is
 * what makes the element there to draw into.
 */
watch(googleSlot, async (slot) => {
  if (!googleUsable.value || slot === null) return

  try {
    await renderGoogleButton(slot, onGoogleCredential)
  } catch {
    // Blocked, offline, or Google having a bad day. Not shown: the form above
    // does the same job, and a red line about a button nobody pressed only
    // makes the gate look broken.
    googleUsable.value = false
  }
}, { flush: 'post' })

onBeforeUnmount(() => releaseGoogleButton(onGoogleCredential))

async function signOut() {
  await api.signOut()
  operator.value = null
  settings.value = null
}

/**
 * The child screens that predate the shared api module raise this when the
 * server rejects their token, so the portal can fall back to the gate rather
 * than leave them retrying against a dead session.
 */
function onSessionEnded(message: string) {
  setToken('')
  operator.value = null
  authError.value = message
}

/** The marketplace's name, for the rail. A failure here is not worth a banner. */
async function loadSettings() {
  try {
    settings.value = (await api.settings()).settings
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) onSessionEnded('Your session has ended. Sign in again.')
  }
}

// ── Which screen ──────────────────────────────────────────────────────────

function readHash() {
  const key = window.location.hash.replace('#', '') as ViewKey
  if (VIEWS.some((item) => item.key === key)) view.value = key
}

function navigate(key: string) {
  view.value = key as ViewKey
  search.value = ''
  window.location.hash = key
}

window.addEventListener('hashchange', readHash)

// A term typed in the shell's search bar lands on whichever screen is open.
// The three screens that take one watch this prop; the rest ignore it.
watch(view, () => {
  search.value = ''
})
</script>

<template>
  <div class="adm">
    <!-- ── The gate ───────────────────────────────────────────────────── -->
    <div v-if="restoring" class="gate">
      <p class="gate__restoring">Checking your session…</p>
    </div>

    <div v-else-if="!operator" class="gate">
      <form class="gate__card" @submit.prevent="signIn">
        <span class="gate__mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor">
            <path d="M12 2C7 4 4 8 4 13a8 8 0 0 0 8 8 8 8 0 0 0 8-8c0-5-3-9-8-11Zm0 3.2c3 1.9 4.8 4.7 4.8 7.8A4.8 4.8 0 0 1 12 17.8Z" />
          </svg>
        </span>

        <h1 class="gate__title">Omaykan</h1>
        <p class="gate__sub">Platform admin</p>

        <label class="gate__label">
          Email
          <span class="adm-field">
            <input v-model="emailInput" type="email" autocomplete="username" required />
          </span>
        </label>

        <label class="gate__label">
          Password
          <span class="adm-field">
            <input v-model="passwordInput" type="password" autocomplete="current-password" required />
          </span>
        </label>

        <button type="submit" class="adm-btn gate__submit" :disabled="signingIn">
          {{ signingIn ? 'Signing in…' : 'Sign in' }}
        </button>

        <!-- Google's own button renders into this slot; it is an iframe, which
             is why it is a bare div and not styled from here. -->
        <template v-if="googleUsable">
          <p class="gate__or"><span>or</span></p>
          <div ref="googleSlot" class="gate__google" :class="{ 'gate__google--busy': signingIn }" />
        </template>

        <p v-if="authError" class="gate__error">{{ authError }}</p>
      </form>
    </div>

    <!-- ── The portal ─────────────────────────────────────────────────── -->
    <AdminShell
      v-else
      :items="navItems"
      :active="view"
      :operator="operator"
      :brand-name="brandName"
      @navigate="navigate"
      @search="search = $event"
      @sign-out="signOut"
    >
      <DashboardView v-if="view === 'dashboard'" @navigate="navigate" />
      <OrdersView v-else-if="view === 'orders'" :search="search" />
      <ProductsView v-else-if="view === 'products'" :search="search" />
      <SellersView v-else-if="view === 'sellers'" :search="search" />
      <CustomersView v-else-if="view === 'customers'" :search="search" />
      <AnalyticsView v-else-if="view === 'analytics'" />
      <ReportsView v-else-if="view === 'reports'" />
      <SettingsView v-else-if="view === 'settings'" :operator="operator" />

      <!-- The two screens that predate the shared api module. They own their
           own markup; the hero above them is what puts them in the shell. -->
      <template v-else-if="view === 'messages'">
        <PageHero title="Messages" subtitle="Support emails from customers, sellers and partners, all handled within the Omaykan portal." />
        <div class="adm-legacy">
          <SupportInbox :token="currentToken()" @session-ended="onSessionEnded" />
        </div>
      </template>

      <template v-else-if="view === 'riders'">
        <PageHero title="Rider review" subtitle="Applications waiting on a look at a licence and a plate." />
        <div class="adm-legacy">
          <RiderReview :token="currentToken()" @session-ended="onSessionEnded" />
        </div>
      </template>
    </AdminShell>
  </div>
</template>

<style scoped>
.gate {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--sf-cream);
}

.gate__restoring {
  color: var(--sf-muted);
  font-size: 14px;
}

.gate__card {
  display: grid;
  gap: 12px;
  width: min(380px, 100%);
  padding: 28px;
  border: 1px solid var(--sf-rule);
  border-radius: 18px;
  background: var(--sf-paper);
  box-shadow: 0 18px 50px rgba(35, 29, 24, 0.1);
  text-align: left;
}

.gate__mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: var(--sf-forest);
  color: #b9d9a8;
}

.gate__title {
  margin: 4px 0 0;
  font-family: var(--sf-serif);
  font-size: 1.5rem;
  font-weight: 700;
}

.gate__sub {
  margin: 0 0 6px;
  color: var(--sf-faint);
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.gate__label {
  display: grid;
  gap: 5px;
  color: var(--sf-muted);
  font-size: 12.5px;
  font-weight: 600;
}

.gate__submit {
  margin-top: 4px;
  height: 42px;
}

.gate__error {
  margin: 0;
  color: var(--sf-clay-deep);
  font-size: 13px;
}

/* A rule with the word sitting in a gap in it, rather than three elements
   pretending to be one. */
.gate__or {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 2px 0;
  color: var(--sf-muted);
  font-size: 12.5px;
}

.gate__or::before,
.gate__or::after {
  flex: 1;
  height: 1px;
  background: var(--sf-rule);
  content: '';
}

/* Google's button is an iframe and brings its own everything. All this does is
   centre it and stop a double-press while a sign-in is already in flight. */
.gate__google {
  display: flex;
  justify-content: center;
  min-height: 44px;
}

.gate__google--busy {
  pointer-events: none;
  opacity: 0.55;
}

.adm-legacy {
  padding: 16px var(--adm-gutter) 0;
  /*
   * These two screens predate the portal and were laid out for a desktop
   * window; rider review in particular has a fixed-width document viewer that
   * does not fold. Containing the overflow here keeps the promise the rest of
   * the portal makes — the page itself never scrolls sideways, only a wrapper
   * does — without rewriting a working screen to get there.
   */
  overflow-x: auto;
}

@media (max-width: 560px) {
  .adm-legacy {
    padding: 14px 16px 0;
  }
}
</style>
