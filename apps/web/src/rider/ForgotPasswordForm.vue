<script setup lang="ts">
import { ref } from 'vue'
import { requestRiderPasswordReset } from '@pos/web/rider/api'
import { messageFor } from '@pos/web/rider/rider'

/*
 * The way back in for a rider who cannot sign in.
 *
 * Before this there was none. A rider who forgot their password could not
 * reset it, could not be reset by an operator — there is no such screen — and
 * could not register again, because the email is already taken by the account
 * they are locked out of. The only exit was a database edit.
 *
 * The reply is the same whether or not the address belongs to a rider, and this
 * screen shows it verbatim rather than claiming a link was sent. The platform's
 * riders are a small and knowable set; a form that said "no such account" would
 * be a way to enumerate them.
 */

const emit = defineEmits<{ back: [] }>()

const email = ref('')
const submitting = ref(false)
const formError = ref('')
const sent = ref('')

async function submit() {
  if (submitting.value) return

  if (!email.value.trim()) {
    formError.value = 'Your email, please.'
    return
  }

  submitting.value = true
  formError.value = ''

  try {
    const { message } = await requestRiderPasswordReset(email.value.trim())
    sent.value = message
  } catch (error) {
    formError.value = messageFor(error, 'Could not send a reset link.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <h1 class="rdr-title">Forgotten password</h1>
    <p class="rdr-sub">
      We will email you a link. It works once, and it stops working in an hour.
    </p>

    <p v-if="formError" class="rdr-flash rdr-flash--error">{{ formError }}</p>

    <!-- Once sent, the form goes. Leaving it on screen invites a second and a
         third request, and the throttle is five an hour. -->
    <div v-if="sent" class="rdr-card">
      <p class="rdr-flash rdr-flash--ok" style="margin: 0">{{ sent }}</p>
      <p class="rdr-field__hint" style="margin: 14px 0 0">
        Check the spam folder before asking again — the mail comes from
        info@omaykan.com.
      </p>
    </div>

    <form v-else class="rdr-card" novalidate @submit.prevent="submit">
      <label class="rdr-field">
        <span class="rdr-field__label">Email</span>
        <input
          v-model="email"
          class="rdr-input"
          type="email"
          inputmode="email"
          autocomplete="email"
        >
      </label>

      <button class="rdr-btn rdr-btn--block" type="submit" :disabled="submitting">
        {{ submitting ? 'Sending…' : 'Email me a link' }}
      </button>
    </form>

    <p class="rdr-sub" style="margin: 22px 0 0; text-align: center">
      <button class="rdr-link" type="button" @click="emit('back')">Back to sign in</button>
    </p>
  </div>
</template>
