<script setup lang="ts">
import { reactive, ref } from 'vue'
import { resetRiderPassword } from '@pos/web/rider/api'
import { fieldErrorsFor, messageFor } from '@pos/web/rider/rider'

/*
 * Where the emailed link lands.
 *
 * The token and the address come from the query string, put there by
 * RiderPasswordReset — this form never asks the rider to type either, because
 * a 60-character token typed by hand at a kerb is not a recovery flow.
 *
 * Signing in afterwards is deliberate rather than lazy: the API deletes every
 * token on the account when a password is reset, on the grounds that somebody
 * resetting a password may be doing it because someone else had the old one.
 * Handing back a fresh session here would undo half of that.
 */

const props = defineProps<{ token: string; email: string }>()
const emit = defineEmits<{ done: [] }>()

const form = reactive({ password: '', confirm: '' })
const submitting = ref(false)
const formError = ref('')
const fields = ref<Record<string, string>>({})
const finished = ref('')

async function submit() {
  if (submitting.value) return

  if (form.password !== form.confirm) {
    formError.value = 'The two passwords do not match.'
    return
  }

  submitting.value = true
  formError.value = ''
  fields.value = {}

  try {
    const { message } = await resetRiderPassword(props.token, props.email, form.password)
    finished.value = message
  } catch (error) {
    fields.value = fieldErrorsFor(error)
    formError.value = messageFor(error, 'Could not reset your password.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <h1 class="rdr-title">Choose a new password</h1>
    <p class="rdr-sub">For {{ email }}.</p>

    <p v-if="formError" class="rdr-flash rdr-flash--error">{{ formError }}</p>

    <div v-if="finished" class="rdr-card">
      <p class="rdr-flash rdr-flash--ok" style="margin: 0 0 14px">{{ finished }}</p>
      <button class="rdr-btn rdr-btn--block" type="button" @click="emit('done')">Sign in</button>
    </div>

    <form v-else class="rdr-card" novalidate @submit.prevent="submit">
      <label class="rdr-field">
        <span class="rdr-field__label">New password</span>
        <input
          v-model="form.password"
          class="rdr-input"
          type="password"
          autocomplete="new-password"
        >
        <span class="rdr-field__hint">At least eight characters.</span>
        <span v-if="fields.password" class="rdr-field__error">{{ fields.password }}</span>
      </label>

      <label class="rdr-field">
        <span class="rdr-field__label">New password again</span>
        <input
          v-model="form.confirm"
          class="rdr-input"
          type="password"
          autocomplete="new-password"
        >
      </label>

      <!-- An expired or spent token comes back on `token`, and it is the one
           error the rider can actually act on: ask for another link. -->
      <p v-if="fields.token" class="rdr-field__error" style="margin: 0 0 14px">
        {{ fields.token }}
      </p>

      <button class="rdr-btn rdr-btn--block" type="submit" :disabled="submitting">
        {{ submitting ? 'Saving…' : 'Set my password' }}
      </button>
    </form>

    <p class="rdr-sub" style="margin: 22px 0 0; text-align: center">
      <button class="rdr-link" type="button" @click="emit('done')">Back to sign in</button>
    </p>
  </div>
</template>
