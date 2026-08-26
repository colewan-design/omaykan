<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import RegisterForm from '@pos/web/rider/RegisterForm.vue'
import SignInForm from '@pos/web/rider/SignInForm.vue'
import StatusScreen from '@pos/web/rider/StatusScreen.vue'
import WorkScreen from '@pos/web/rider/WorkScreen.vue'
import { useRiderSession } from '@pos/web/rider/rider'

/*
 * The rider portal.
 *
 * Four states, and the account itself decides which one is showing: no session
 * at all (apply, or sign in), a session that is not cleared to work (the status
 * screen), and a session that is (the board). There is no navigation between
 * them and no route to type — a rider cannot browse to the job board by
 * knowing its URL, because the board is not a URL, it is what this component
 * renders when the API says the account is approved. The real enforcement is
 * `rider.approved` on the server; this only avoids showing a door that would
 * not open.
 *
 * A separate Vite entry rather than a route in the storefront app, like
 * /account and /platform-admin: a rider is not a shopper, shares none of the
 * storefront's chrome, cart or catalog, and has no reason to download them.
 */

const session = useRiderSession()

/** Which side of the gate a signed-out visitor is on. */
const gate = ref<'register' | 'sign-in'>('register')

const heading = computed(() => {
  if (session.rider.value === null) return gate.value === 'register' ? 'Ride with Omaykan' : 'Rider sign in'
  return session.approved.value ? 'Your jobs' : 'Your application'
})

watch(heading, (value) => (document.title = `${value} — Omaykan`), { immediate: true })

/** First name only — the bar is 20px tall and "Hi, Juan" is the whole point. */
const firstName = computed(() => session.rider.value?.name.trim().split(/\s+/)[0] ?? '')
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

    <main class="rdr-main" :class="{ 'rdr-main--narrow': !session.approved.value }">
      <!-- Held until the stored token has been checked, so a returning rider
           never sees the application form flash up before their own jobs. -->
      <p v-if="session.hydrating.value" class="rdr-sub">Loading…</p>

      <template v-else-if="session.rider.value === null">
        <RegisterForm v-if="gate === 'register'" @sign-in="gate = 'sign-in'" />
        <SignInForm v-else @register="gate = 'register'" />
      </template>

      <WorkScreen v-else-if="session.approved.value" />

      <StatusScreen v-else :rider="session.rider.value" />
    </main>
  </div>
</template>
