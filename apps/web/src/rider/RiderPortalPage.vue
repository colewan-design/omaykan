<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Bike, CircleUser, Wallet } from '@lucide/vue'
import EarningsScreen from '@pos/web/rider/EarningsScreen.vue'
import ForgotPasswordForm from '@pos/web/rider/ForgotPasswordForm.vue'
import ProfileScreen from '@pos/web/rider/ProfileScreen.vue'
import RegisterForm from '@pos/web/rider/RegisterForm.vue'
import ResetPasswordForm from '@pos/web/rider/ResetPasswordForm.vue'
import SignInForm from '@pos/web/rider/SignInForm.vue'
import StatusScreen from '@pos/web/rider/StatusScreen.vue'
import WorkScreen from '@pos/web/rider/WorkScreen.vue'
import { useRiderSession } from '@pos/web/rider/rider'

/*
 * The rider portal.
 *
 * The account decides which screen is showing: no session at all (apply, sign
 * in, or recover), a session that is not cleared to work (the status screen),
 * and a session that is (the work screens). There is still no route to type — a
 * rider cannot browse to the job board by knowing its URL, because the board is
 * not a URL, it is what this component renders when the API says the account is
 * approved. The real enforcement is `rider.approved` on the server; this only
 * avoids showing a door that would not open.
 *
 * A signed-in, approved rider now has three screens rather than one, so there
 * is a tab bar. It is at the bottom, phone-style, and only for approved riders:
 * a pending account has one screen and a tab bar over it would be three-quarters
 * disabled furniture.
 *
 * A separate Vite entry rather than a route in the storefront app, like
 * /account and /platform-admin: a rider is not a shopper, shares none of the
 * storefront's chrome, cart or catalog, and has no reason to download them.
 */

const session = useRiderSession()

/**
 * Which door a signed-out visitor is at.
 *
 * `reset` is not chosen, it is arrived at: RiderPasswordReset mails a link back
 * to this page carrying a token and the address it was issued for, and finding
 * both in the query string is the only way into that state.
 */
type Gate = 'register' | 'sign-in' | 'forgot' | 'reset'

const query = new URLSearchParams(window.location.search)
const resetToken = ref(query.get('token') ?? '')
const resetEmail = ref(query.get('email') ?? '')

const gate = ref<Gate>(resetToken.value && resetEmail.value ? 'reset' : 'register')

/**
 * The token leaves the address bar as soon as it has been read.
 *
 * A reset token in a URL is a credential in the browser history, in the
 * referrer of anything this page loads, and in whatever the rider pastes into a
 * chat when asking for help. It is already in memory by this point, so nothing
 * is lost by taking it out of the bar — and `replaceState` does it without a
 * reload, which would throw it away.
 */
if (gate.value === 'reset') {
  window.history.replaceState({}, '', window.location.pathname)
}

/** Which of the three an approved rider is looking at. */
const tab = ref<'work' | 'earnings' | 'account'>('work')

const heading = computed(() => {
  if (session.rider.value === null) {
    if (gate.value === 'register') return 'Ride with Omaykan'
    if (gate.value === 'forgot') return 'Forgotten password'
    if (gate.value === 'reset') return 'Choose a new password'
    return 'Rider sign in'
  }

  if (!session.approved.value) return 'Your application'

  if (tab.value === 'earnings') return 'Your earnings'
  if (tab.value === 'account') return 'Your account'

  return 'Your jobs'
})

watch(heading, (value) => (document.title = `${value} — Omaykan`), { immediate: true })

/** First name only — the bar is 20px tall and "Hi, Juan" is the whole point. */
const firstName = computed(() => session.rider.value?.name.trim().split(/\s+/)[0] ?? '')

function afterReset() {
  resetToken.value = ''
  resetEmail.value = ''
  gate.value = 'sign-in'
}
</script>

<template>
  <div class="rdr">
    <header class="rdr-bar">
      <div class="rdr-bar__brand">
        Omaykan
        <span class="rdr-bar__tag">Rider</span>
      </div>

      <div v-if="session.signedIn.value" class="rdr-bar__who">
        <span class="rdr-bar__name">{{ firstName }}</span>
        <button class="rdr-bar__out" type="button" @click="session.signOut()">Sign out</button>
      </div>
    </header>

    <main
      class="rdr-main"
      :class="{
        'rdr-main--narrow': !session.approved.value,
        'rdr-main--tabbed': session.approved.value,
      }"
    >
      <!-- Held until the stored token has been checked, so a returning rider
           never sees the application form flash up before their own jobs. -->
      <p v-if="session.hydrating.value" class="rdr-sub">Loading…</p>

      <template v-else-if="session.rider.value === null">
        <ResetPasswordForm
          v-if="gate === 'reset'"
          :token="resetToken"
          :email="resetEmail"
          @done="afterReset"
        />
        <ForgotPasswordForm v-else-if="gate === 'forgot'" @back="gate = 'sign-in'" />
        <RegisterForm v-else-if="gate === 'register'" @sign-in="gate = 'sign-in'" />
        <SignInForm v-else @register="gate = 'register'" @forgot="gate = 'forgot'" />
      </template>

      <template v-else-if="session.approved.value">
        <WorkScreen v-if="tab === 'work'" />
        <EarningsScreen v-else-if="tab === 'earnings'" />
        <ProfileScreen v-else />
      </template>

      <StatusScreen v-else :rider="session.rider.value" />
    </main>

    <!-- Approved only. A rider waiting on review has one screen, and a bar with
         two dead tabs on it would say the opposite. -->
    <nav v-if="session.approved.value && !session.hydrating.value" class="rdr-nav">
      <button
        class="rdr-nav__item"
        :class="{ 'rdr-nav__item--on': tab === 'work' }"
        type="button"
        @click="tab = 'work'"
      >
        <Bike :size="20" :stroke-width="1.8" />
        Jobs
      </button>
      <button
        class="rdr-nav__item"
        :class="{ 'rdr-nav__item--on': tab === 'earnings' }"
        type="button"
        @click="tab = 'earnings'"
      >
        <Wallet :size="20" :stroke-width="1.8" />
        Earnings
      </button>
      <button
        class="rdr-nav__item"
        :class="{ 'rdr-nav__item--on': tab === 'account' }"
        type="button"
        @click="tab = 'account'"
      >
        <CircleUser :size="20" :stroke-width="1.8" />
        Account
      </button>
    </nav>
  </div>
</template>
