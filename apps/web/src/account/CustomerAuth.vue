<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Eye, EyeOff } from '@lucide/vue'
import { messageFor, useCustomerAccount } from '@pos/web/commerce/customer'
import {
  googleSignInAvailable,
  releaseGoogleButton,
  renderGoogleButton,
} from '@pos/web/commerce/google'

/*
 * The storefront's sign-in card.
 *
 * Four states rather than four pages: signing in, creating an account, asking
 * for a reset link, and choosing a new password. They share one card, one
 * error line and one busy flag, because they are one decision — "who are you"
 * — and bouncing a shopper between routes to answer it loses them.
 *
 * Nothing here is a route. The portal is a single Vite entry (see
 * AccountPage.vue), so the mode is local state; the one exception is the reset
 * link from email, which arrives as ?token=&email= and has to be honoured on
 * load. Those params are stripped once used — a spent token left in the URL
 * would fail on the next refresh and read as a broken link.
 *
 * There is no emit: `useCustomerAccount` is module-level, so a successful
 * sign-in flips `signedIn` for the header and the portal at the same time.
 *
 * Google sits below the form, after an "or", on the two cards where it means
 * anything: email and password lead, and Google is the alternative offered
 * underneath. It is absent entirely when the build has no client id, and it
 * removes itself if the script will not load — a content blocker eating it must
 * leave a working sign-in card behind, not a dead button.
 */

const account = useCustomerAccount()

type Mode = 'signin' | 'register' | 'forgot' | 'reset'

const mode = ref<Mode>('signin')
const pending = ref(false)
const error = ref('')
/** The neutral "if that email has an account…" reply, kept apart from errors. */
const notice = ref('')

const form = ref({ name: '', email: '', phone: '', password: '' })
const showPassword = ref(false)

// -- Google ------------------------------------------------------------------

/**
 * Only where it is an answer to the question on screen. On 'forgot' and 'reset'
 * the shopper is midway through recovering a password, and a Google button
 * there would be a second, unrelated way in offered at the worst moment.
 */
const googleOffered = computed(() => mode.value === 'signin' || mode.value === 'register')

const googleSlot = ref<HTMLElement | null>(null)
/** Flipped off for good if the script is blocked, so the gap closes up. */
const googleUsable = ref(googleSignInAvailable())

async function onGoogleCredential(credential: string) {
  if (pending.value) return

  error.value = ''
  notice.value = ''
  pending.value = true

  try {
    await account.signInWithGoogle(credential)
  } catch (caught) {
    error.value = messageFor(caught)
  } finally {
    pending.value = false
  }
}

async function mountGoogleButton() {
  if (!googleUsable.value || !googleOffered.value || googleSlot.value === null) return

  try {
    await renderGoogleButton(googleSlot.value, onGoogleCredential)
  } catch {
    // Blocked, offline, or Google having a bad day. Not an error to show: the
    // form underneath does the same job, and a red line about a button the
    // shopper never pressed only makes the page look broken.
    googleUsable.value = false
  }
}

onBeforeUnmount(() => releaseGoogleButton(onGoogleCredential))

// Held from the URL rather than shown: the customer proves ownership of the
// address by having received the link, so re-typing either value would only be
// a chance to get it wrong.
const resetToken = ref('')

// The slot only exists on two of the four modes, so the button is drawn when
// the switch puts it in the DOM rather than once on mount. `flush: 'post'` is
// what makes the element there to draw into; the template ref filling in during
// mount is itself one of the changes this fires on, so there is no separate
// onMounted call and no double render.
watch([googleOffered, googleSlot], () => void mountGoogleButton(), { flush: 'post' })

onMounted(() => {
  try {
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    const email = params.get('email')

    if (token && email) {
      resetToken.value = token
      form.value.email = email
      mode.value = 'reset'
      return
    }

    // The second exception to "nothing here is a route": ?mode=register opens
    // the create-account card directly, so the header's account banner lands
    // on the form it advertises instead of on the sign-in card. Left in the
    // URL rather than stripped — unlike a reset token it is not spent, and a
    // refresh should stay where the shopper was put. Only this one value is
    // honoured; anything else falls through to sign-in.
    if (params.get('mode') === 'register') {
      mode.value = 'register'
    }
  } catch {
    // A URL we can't parse just means the ordinary sign-in card.
  }
})

/** Drops the reset params so a refresh doesn't replay a token that's now spent. */
function clearResetParams() {
  try {
    window.history.replaceState({}, '', window.location.pathname)
  } catch {
    // Non-fatal: the session is already established either way.
  }
}

function switchTo(next: Mode) {
  mode.value = next
  error.value = ''
  notice.value = ''
  form.value.password = ''
  showPassword.value = false
}

const copy = computed(() => {
  switch (mode.value) {
    case 'register':
      return {
        title: 'Create your account',
        sub: 'Save your addresses and your usual payment method, and checkout becomes two taps instead of a form.',
        submit: 'Create account',
      }
    case 'forgot':
      return {
        title: 'Reset your password',
        sub: 'Tell us the email on the account and we\'ll send a link to choose a new password.',
        submit: 'Send reset link',
      }
    case 'reset':
      return {
        title: 'Choose a new password',
        sub: `Setting a new password for ${form.value.email}. Every other device signed in to this account gets signed out.`,
        submit: 'Save password and sign in',
      }
    default:
      return {
        title: 'Your account',
        sub: 'Your addresses, your usual payment method and your orders, on every device you shop from.',
        submit: 'Sign in',
      }
  }
})

/**
 * Checked here only to spare a round trip on the obvious mistakes; the API
 * validates all of it again, and its messages are the ones shown when they
 * disagree.
 */
function localProblem(): string {
  const { name, email, password } = form.value

  if (!email.trim().includes('@')) return 'That doesn\'t look like an email address.'
  if (mode.value === 'register' && !name.trim()) return 'We need a name to put on your orders.'
  if (mode.value === 'forgot') return ''
  if (!password) return 'Please enter your password.'
  // Matches PasswordRule::min(8) on the API — a shorter one would only bounce.
  if (mode.value !== 'signin' && password.length < 8) {
    return 'Passwords need at least 8 characters.'
  }

  return ''
}

async function submit() {
  if (pending.value) return

  const problem = localProblem()
  if (problem) {
    error.value = problem
    return
  }

  error.value = ''
  notice.value = ''
  pending.value = true

  const { name, email, phone, password } = form.value

  try {
    switch (mode.value) {
      case 'register': {
        const result = await account.register({
          name: name.trim(),
          email: email.trim(),
          phone: phone.trim() || undefined,
          password,
        })
        notice.value = result.message
        mode.value = 'signin'
        form.value.name = ''
        form.value.phone = ''
        form.value.password = ''
        break
      }

      case 'forgot': {
        const result = await account.sendPasswordReset(email.trim())
        notice.value = result.message
        form.value.password = ''
        break
      }

      case 'reset':
        await account.resetPassword({ token: resetToken.value, email: email.trim(), password })
        clearResetParams()
        break

      default:
        await account.signIn(email.trim(), password)
    }
  } catch (caught) {
    error.value = messageFor(caught)
    // Never left in the field after a failure: on a shared screen the next
    // person shouldn't be able to reveal it, and a wrong one is retyped anyway.
    form.value.password = ''
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="acct-gate">
    <h1 class="acct-gate__title">{{ copy.title }}</h1>
    <p class="acct-gate__sub">{{ copy.sub }}</p>

    <form class="acct-card acct-gate__form" novalidate @submit.prevent="submit">
      <label v-if="mode === 'register'" class="acct-field">
        <span class="acct-field__label">Your name</span>
        <input
          v-model="form.name"
          class="acct-input"
          type="text"
          autocomplete="name"
          :disabled="pending"
        />
      </label>

      <!-- Fixed on the reset card: the token was issued for this address, and
           editing it here would only produce a mismatch the API rejects. -->
      <label class="acct-field">
        <span class="acct-field__label">Email</span>
        <input
          v-model="form.email"
          class="acct-input"
          type="email"
          autocomplete="email"
          inputmode="email"
          :readonly="mode === 'reset'"
          :disabled="pending"
        />
      </label>

      <label v-if="mode === 'register'" class="acct-field">
        <span class="acct-field__label">Phone <span class="acct-field__opt">optional</span></span>
        <input
          v-model="form.phone"
          class="acct-input"
          type="tel"
          autocomplete="tel"
          inputmode="tel"
          :disabled="pending"
        />
        <span class="acct-field__hint">How a rider reaches you if they can't find the door.</span>
      </label>

      <label v-if="mode !== 'forgot'" class="acct-field">
        <span class="acct-field__label">
          {{ mode === 'reset' ? 'New password' : 'Password' }}
        </span>
        <span class="auth-secret">
          <input
            v-model="form.password"
            class="acct-input auth-secret__input"
            :type="showPassword ? 'text' : 'password'"
            :autocomplete="mode === 'signin' ? 'current-password' : 'new-password'"
            :disabled="pending"
          />
          <button
            type="button"
            class="auth-secret__peek"
            :aria-label="showPassword ? 'Hide password' : 'Show password'"
            :aria-pressed="showPassword"
            @click="showPassword = !showPassword"
          >
            <component :is="showPassword ? EyeOff : Eye" :size="18" :stroke-width="1.6" />
          </button>
        </span>
        <span v-if="mode !== 'signin'" class="acct-field__hint">At least 8 characters.</span>
      </label>

      <button type="submit" class="acct-btn acct-gate__go" :disabled="pending">
        {{ pending ? 'One moment…' : copy.submit }}
      </button>

      <p v-if="error" class="acct-flash acct-flash--error" role="alert">{{ error }}</p>
      <p v-else-if="notice" class="acct-flash" role="status">{{ notice }}</p>

      <!-- Sign-in is the only card that offers the reset route: from the others
           it is either where you came from, or where you already are. -->
      <p v-if="mode === 'signin'" class="auth-aside">
        <button type="button" class="acct-link" :disabled="pending" @click="switchTo('forgot')">
          Forgot your password?
        </button>
      </p>

      <!-- Google's own button renders into this slot; it is an iframe, which is
           why it is a bare div and not styled from here. -->
      <template v-if="googleUsable && googleOffered">
        <p class="auth-or"><span>or</span></p>
        <div ref="googleSlot" class="auth-google" :class="{ 'auth-google--busy': pending }" />
      </template>
    </form>

    <p class="auth-switch">
      <template v-if="mode === 'signin'">
        New here?
        <button type="button" class="acct-link" @click="switchTo('register')">Create an account</button>
      </template>
      <template v-else-if="mode === 'register'">
        Already have an account?
        <button type="button" class="acct-link" @click="switchTo('signin')">Sign in</button>
      </template>
      <template v-else>
        <button type="button" class="acct-link" @click="switchTo('signin')">Back to sign in</button>
      </template>
    </p>

    <p v-if="googleUsable && googleOffered" class="acct-gate__note">
      Continuing with Google signs you in with the email on your Google account. We never see your
      Google password, and we only ask for your name, email address and picture.
    </p>

    <p class="acct-gate__note">
      You don't need an account to order — checkout works without one. Signing in is what keeps
      your addresses and your order history together across your phone and your laptop.
    </p>
  </section>
</template>

<style scoped>
/* Google's button is an iframe and brings its own everything. All this does is
   centre it and stop a double-press while a sign-in is already in flight. */
.auth-google {
  display: flex;
  justify-content: center;
  min-height: 44px;
}

.auth-google--busy {
  pointer-events: none;
  opacity: 0.55;
}

/* A rule with the word sitting in a gap in it, rather than three elements
   pretending to be one. */
.auth-or {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0 14px;
  font-size: 13px;
  color: var(--acct-faint);
}

.auth-or::before,
.auth-or::after {
  flex: 1;
  height: 1px;
  background: var(--acct-rule);
  content: '';
}

/* The peek button sits inside the field, so the input needs room for it and
   the wrapper has to be the positioning context rather than the label. */
.auth-secret {
  position: relative;
  display: block;
}

.auth-secret__input {
  width: 100%;
  padding-right: 44px;
}

.auth-secret__peek {
  position: absolute;
  top: 50%;
  right: 6px;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  transform: translateY(-50%);
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--acct-muted);
  cursor: pointer;
}

.auth-secret__peek:hover {
  color: var(--acct-ink);
}

.acct-field__opt {
  font-weight: 400;
  color: var(--acct-faint);
  text-transform: none;
  letter-spacing: 0;
}

.auth-aside {
  margin: 16px 0 0;
  font-size: 13.5px;
}

.auth-switch {
  margin: 22px 0 0;
  font-size: 14.5px;
  color: var(--acct-muted);
  text-align: center;
}
</style>
