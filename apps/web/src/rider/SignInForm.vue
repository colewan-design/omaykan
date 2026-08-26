<script setup lang="ts">
import { reactive, ref } from 'vue'
import { messageFor, useRiderSession } from '@pos/web/rider/rider'

/*
 * Sign-in for a rider who already applied.
 *
 * Open to every status, not just approved ones. A rejected or suspended rider
 * signing in is the *point* — their status screen is the only place the
 * reviewer's note is written down, and refusing them the door would leave them
 * with a password that appears to be broken and no way to find out why.
 */

const emit = defineEmits<{ register: [] }>()

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
    </form>

    <p class="rdr-sub" style="margin: 22px 0 0; text-align: center">
      New here?
      <button class="rdr-link" type="button" @click="emit('register')">Apply to ride</button>
    </p>
  </div>
</template>
