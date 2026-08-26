<script setup lang="ts">
import { Eye, EyeOff, UserRound } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
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

onMounted(async () => {
  await auth.initialize()
  mode.value = auth.hasUsers ? 'login' : 'register'
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

      <div class="segmented-control auth-mode-switch" role="group" aria-label="Authentication mode">
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

      <div v-if="showGuestAccess" class="auth-guest">
        <div class="auth-divider" role="separator"><span>or</span></div>
        <button class="auth-guest-button" type="button" @click="continueAsGuest">
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
