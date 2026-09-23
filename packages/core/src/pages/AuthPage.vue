<script setup lang="ts">
import { Eye, EyeOff, UserRound } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import {
  googleSignInAvailable,
  releaseGoogleButton,
  renderGoogleButton,
} from '@pos/core/services/google'
import { useAuthStore } from '@pos/core/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const mode = ref<'login' | 'register'>('login')
const loginForm = ref({
  username: '',
  password: '',
})
const registerForm = ref({
  fullName: '',
  username: '',
  password: '',
})
const loginPasswordVisible = ref(false)
const registerPasswordVisible = ref(false)
const showGuestAccess = computed(() => auth.canUseGuestAccess)

const heading = computed(() => (mode.value === 'login' ? 'Welcome back' : 'Create your account'))
const subtitle = computed(() =>
  mode.value === 'login'
    ? 'Sign in to start your shift.'
    : "Set up sign-in for this register — it only takes a minute.",
)

const registerHint = computed(() =>
  auth.hasUsers
    ? 'New accounts start as Cashier and can be reassigned by an admin later.'
    : "You'll be the Admin for this register, since you're the first to sign up.",
)

function resolveDestination() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect || `/${auth.firstAccessiblePage === 'register' ? '' : auth.firstAccessiblePage ?? ''}`.replace(/\/$/, '/') 
}

async function goToApp() {
  const destination = resolveDestination()
  await router.replace(destination)
}

async function submitLogin() {
  const success = await auth.login(loginForm.value.username, loginForm.value.password)
  if (!success) {
    return
  }

  await goToApp()
}

async function submitRegistration() {
  const success = await auth.register(registerForm.value)
  if (!success) {
    return
  }

  await goToApp()
}

async function continueAsGuest() {
  const success = await auth.loginAsGuest()
  if (!success) {
    return
  }

  await goToApp()
}

// -- Google -------------------------------------------------------------------

/*
 * Google sits under the form, as the alternative to a password rather than a
 * replacement for it. Three things have to be true before it appears at all:
 * the build has a client id, this register is on online sync, and we're on the
 * Login tab.
 *
 * Login only, because the backend never creates a staff account from a Google
 * token — an account here is a claim on someone's shop, so it is made by that
 * shop, through signup or by an admin in Staff. A Google button on Register
 * would look like a way to sign up and answer every press with "that account
 * isn't set up for any shop yet".
 */

const googleSlot = ref<HTMLElement | null>(null)
/** Latched off if the script won't load, so the gap closes rather than gaping. */
const googleUsable = ref(googleSignInAvailable())
const googlePending = ref(false)

const googleOffered = computed(
  () => googleUsable.value && auth.remoteAuthReady && mode.value === 'login',
)

async function onGoogleCredential(credential: string) {
  if (googlePending.value) {
    return
  }

  googlePending.value = true

  try {
    const success = await auth.loginWithGoogle(credential)
    if (success) {
      await goToApp()
    }
  } finally {
    googlePending.value = false
  }
}

async function mountGoogleButton() {
  if (!googleOffered.value || googleSlot.value === null) {
    return
  }

  try {
    await renderGoogleButton(googleSlot.value, onGoogleCredential, { shape: 'pill' })
  } catch {
    // Blocked by an extension, offline, or Google having a bad morning. Not
    // worth an error line: the username and password form underneath does the
    // same job, and a red message about a button nobody pressed only makes the
    // sign-in look broken.
    googleUsable.value = false
  }
}

// The slot only exists on the Login tab and only once `initialize` has said
// this register is online, so the button is drawn when those put it in the DOM
// rather than once on mount. `flush: 'post'` is what makes the element there to
// draw into — the template ref filling in is itself one of the changes this
// fires on, so there is no separate onMounted call and no double render.
watch([googleOffered, googleSlot], () => void mountGoogleButton(), { flush: 'post' })

onBeforeUnmount(() => releaseGoogleButton(onGoogleCredential))

/*
 * Register is for a till that has never been online — the one case where the
 * first person at it has no account anywhere and nothing can make them one.
 *
 * A register bound to a shop is the opposite: whoever is standing at it already
 * has an account, made at signup or by an admin in Staff, and the backend has
 * no route that would make another. Offering the tab there sends the owner who
 * has just signed in at /signup to a form that cannot work.
 */
// Gated on isReady as well, or the control renders on first paint — when
// remoteAuthReady is still its initial false — and vanishes a tick later.
const registrationOffered = computed(() => auth.isReady && !auth.remoteAuthReady)

onMounted(async () => {
  await auth.initialize()
  // hasUsers is the local user list, and on a browser that has just been bound
  // to a shop it is empty — /api/staff-users needs a session this screen exists
  // to get. Left on its own it opens the *Register* tab at someone who signed
  // in thirty seconds ago, so being online decides this first.
  mode.value = auth.remoteAuthReady || auth.hasUsers ? 'login' : 'register'
})
</script>

<template>
  <!-- --split marks the two-panel layout, which the signup and platform-admin
       screens don't use: it lets the narrow breakpoint drop this page's gutters
       for a full-bleed card without touching theirs. -->
  <div class="auth-page auth-page--split">
    <div class="auth-shell">
      <!-- The green half carries the brand the storefront and landing page
           already wear, so signing in doesn't look like a different product.
           It folds away under 900px: on a till in portrait, or in the admin
           app, the form is the whole point and the brand shrinks to the
           lockup at the top of the card. -->
      <aside class="auth-pitch">
        <BrandLogo variant="dark" :size="24" />

        <div>
          <h2 class="auth-pitch__title">Run your counter on Omaykan.</h2>
          <p class="auth-pitch__copy">
            A point of sale for the shop floor and a storefront your customers order from — one
            catalog behind both, free while we're in early access.
          </p>
        </div>

        <ul class="auth-pitch__list">
          <li>You keep 100% of every sale</li>
          <li>The same prices at the counter and online</li>
          <li>Cash or GCash, pickup or delivered</li>
        </ul>
      </aside>

      <section class="auth-card">
      <div class="auth-brand">
        <BrandLogo variant="light" :size="21" />
      </div>

      <!-- The whole control goes with Register, rather than leaving a lone
           "Login" tab switching between one thing. -->
      <div
        v-if="registrationOffered"
        class="segmented-control auth-mode-switch"
        role="group"
        aria-label="Authentication mode"
      >
        <button
          class="segment-button"
          :class="{ active: mode === 'login' }"
          type="button"
          @click="mode = 'login'; auth.clearAuthError()"
        >
          <span>Login</span>
        </button>
        <button
          class="segment-button"
          :class="{ active: mode === 'register' }"
          type="button"
          @click="mode = 'register'; auth.clearAuthError()"
        >
          <span>Register</span>
        </button>
      </div>

      <div class="auth-card__hero">
        <h1 class="auth-card__title">{{ heading }}</h1>
        <p class="auth-card__copy">{{ subtitle }}</p>
      </div>

      <Transition name="auth-form-fade" mode="out-in">
        <form v-if="mode === 'login'" key="login" class="auth-form" @submit.prevent="submitLogin">
          <label class="settings-field">
            <span class="settings-row__label">Username</span>
            <input v-model="loginForm.username" class="sheet-input" type="text" autocomplete="username">
          </label>
          <label class="settings-field">
            <span class="settings-row__label">Password</span>
            <div class="auth-password-field">
              <input
                v-model="loginForm.password"
                class="sheet-input"
                :type="loginPasswordVisible ? 'text' : 'password'"
                autocomplete="current-password"
              >
              <button
                class="auth-password-toggle"
                type="button"
                :aria-label="loginPasswordVisible ? 'Hide password' : 'Show password'"
                @click="loginPasswordVisible = !loginPasswordVisible"
              >
                <EyeOff v-if="loginPasswordVisible" :size="16" />
                <Eye v-else :size="16" />
              </button>
            </div>
          </label>
          <button class="primary-button auth-submit" type="submit">Sign in</button>
        </form>

        <form v-else key="register" class="auth-form" @submit.prevent="submitRegistration">
          <label class="settings-field">
            <span class="settings-row__label">Full name</span>
            <input v-model="registerForm.fullName" class="sheet-input" type="text" autocomplete="name">
          </label>
          <label class="settings-field">
            <span class="settings-row__label">Username</span>
            <input v-model="registerForm.username" class="sheet-input" type="text" autocomplete="username">
          </label>
          <label class="settings-field">
            <span class="settings-row__label">Password</span>
            <div class="auth-password-field">
              <input
                v-model="registerForm.password"
                class="sheet-input"
                :type="registerPasswordVisible ? 'text' : 'password'"
                autocomplete="new-password"
              >
              <button
                class="auth-password-toggle"
                type="button"
                :aria-label="registerPasswordVisible ? 'Hide password' : 'Show password'"
                @click="registerPasswordVisible = !registerPasswordVisible"
              >
                <EyeOff v-if="registerPasswordVisible" :size="16" />
                <Eye v-else :size="16" />
              </button>
            </div>
          </label>
          <button class="primary-button auth-submit" type="submit">Create account</button>
          <p class="auth-helper">{{ registerHint }}</p>
        </form>
      </Transition>

      <p v-if="auth.authError" class="auth-error">{{ auth.authError }}</p>

      <!-- One "or" over both ways in, not one each: they are alternatives to the
           form above, and two rules stacked would read as two separate offers. -->
      <div v-if="googleOffered || showGuestAccess" class="auth-alternatives">
        <div class="auth-divider" role="separator"><span>or</span></div>

        <!-- Google renders its own button in here. It is an iframe, and the
             branding terms that come with the API are why it can't be one of
             ours; the slot is a bare div so it has nothing to fight with. -->
        <div
          v-if="googleOffered"
          ref="googleSlot"
          class="auth-google"
          :class="{ 'auth-google--busy': googlePending }"
        />

        <button v-if="showGuestAccess" class="auth-guest-button" type="button" @click="continueAsGuest">
          <UserRound :size="16" />
          <span>Continue as guest</span>
        </button>
      </div>

      <p class="auth-support">
        Locked out or need an account set up?
        Email <a :href="supportMailto('Omaykan account help')">{{ SUPPORT_EMAIL }}</a>.
      </p>
      </section>
    </div>
  </div>
</template>
