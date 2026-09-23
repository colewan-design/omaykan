<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  googleSignInAvailable,
  releaseGoogleButton,
  renderGoogleButton,
} from '@pos/core/services/google'
import { messageFor, useRiderSession } from '@pos/web/rider/rider'

/*
 * Sign-in for a rider who already applied.
 *
 * Open to every status, not just approved ones. A rejected or suspended rider
 * signing in is the *point* — their status screen is the only place the
 * reviewer's note is written down, and refusing them the door would leave them
 * with a password that appears to be broken and no way to find out why.
 */

const emit = defineEmits<{ register: []; forgot: [] }>()

const session = useRiderSession()

const form = reactive({ email: '', password: '' })
const submitting = ref(false)
const formError = ref('')

async function submit() {
  if (submitting.value) return

  if (!form.email.trim() || !form.password) {
    formError.value = 'Your email and password, please.'
    return
  }

  submitting.value = true
  formError.value = ''

  try {
    await session.signIn(form.email.trim(), form.password)
  } catch (error) {
    formError.value = messageFor(error, 'Could not sign you in.')
  } finally {
    submitting.value = false
  }
}

// -- Google ------------------------------------------------------------------

/*
 * Sign-in only, and only on this card. The endpoint behind it refuses a Google
 * account with no rider profile, because approval rests on a licence and a
 * plate that no credential carries — so there is deliberately no Google button
 * on the registration form, where it would look like a way to skip them.
 */

const googleSlot = ref<HTMLElement | null>(null)
/** Blank client id, or a blocked script: either way the gap closes up. */
const googleUsable = ref(googleSignInAvailable())

async function onGoogleCredential(credential: string) {
  if (submitting.value) return

  submitting.value = true
  formError.value = ''

  try {
    await session.signInWithGoogle(credential)
  } catch (error) {
    // The 403 for "no rider profile yet" arrives here and says so in words —
    // it is the one message on this card worth reading carefully, so it goes
    // through unchanged rather than being flattened into the fallback.
    formError.value = messageFor(error, 'Could not sign you in with Google.')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (!googleUsable.value || googleSlot.value === null) return

  try {
    await renderGoogleButton(googleSlot.value, onGoogleCredential)
  } catch {
    // Blocked, offline, or Google having a bad day. Not shown: the form above
    // does the same job, and a red line about a button nobody pressed only
    // makes the page look broken.
    googleUsable.value = false
  }
})

onBeforeUnmount(() => releaseGoogleButton(onGoogleCredential))
</script>

<template>
  <div>
    <h1 class="rdr-title">Rider sign in</h1>
    <p class="rdr-sub">Pick up where you left off — your jobs, and the board.</p>

    <p v-if="formError" class="rdr-flash rdr-flash--error">{{ formError }}</p>

    <form class="rdr-card" novalidate @submit.prevent="submit">
      <label class="rdr-field">
        <span class="rdr-field__label">Email</span>
        <input
          v-model="form.email"
          class="rdr-input"
          type="email"
          inputmode="email"
          autocomplete="email"
        >
      </label>

      <label class="rdr-field">
        <span class="rdr-field__label">Password</span>
        <input
          v-model="form.password"
          class="rdr-input"
          type="password"
          autocomplete="current-password"
        >
      </label>

      <button class="rdr-btn rdr-btn--block" type="submit" :disabled="submitting">
        {{ submitting ? 'Signing in…' : 'Sign in' }}
      </button>

      <!-- Google's own button renders into this slot; it is an iframe, which is
           why it is a bare div and not styled from here. -->
      <template v-if="googleUsable">
        <p class="rdr-or"><span>or</span></p>
        <div ref="googleSlot" class="rdr-google" :class="{ 'rdr-google--busy': submitting }" />
      </template>
    </form>

    <!-- Under the button, not beside the password field: a rider who is about
         to succeed should not be offered a detour, and one who has just failed
         is looking exactly here. -->
    <p class="rdr-sub" style="margin: 18px 0 0; text-align: center">
      <button class="rdr-link" type="button" @click="emit('forgot')">
        Forgotten your password?
      </button>
    </p>

    <p class="rdr-sub" style="margin: 22px 0 0; text-align: center">
      New here?
      <button class="rdr-link" type="button" @click="emit('register')">Apply to ride</button>
    </p>
  </div>
</template>
