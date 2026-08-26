<script setup lang="ts">
import { ref } from 'vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { messageFor, usePlatformSession } from '@pos/web/platform-admin/admin'

/*
 * Operator sign-in.
 *
 * What used to be here was a single "Secret" field — one credential shared by
 * everybody, held in sessionStorage, and resent as a body field on every
 * request. This is a named account instead, which is what makes the audit log
 * mean anything.
 *
 * No "forgot password" link, deliberately: there is no self-service reset for
 * an operator. An owner issues a new one from the operators screen, or
 * `php artisan platform:admin-create` does it on the box.
 */

const session = usePlatformSession()

const email = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (busy.value) return

  busy.value = true
  error.value = ''

  try {
    await session.signIn(email.value.trim(), password.value)
  } catch (err) {
    // The 422 message is already the specific one ("That email and password
    // don't match an operator account"), and it is deliberately the same for a
    // wrong password and an unknown address — so it goes at the top rather
    // than under a field, which would imply the field is the wrong one.
    error.value = messageFor(err, 'Could not sign you in.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="pa-signin">
    <div class="pa-signin__card">
      <div class="pa-signin__brand">
        <BrandLogo variant="light" :size="20" />
        <strong>Omaykan</strong>
        <span class="pa-signin__tag">Platform</span>
      </div>

      <p v-if="session.disabledMessage.value" class="pa-alert">
        {{ session.disabledMessage.value }}
      </p>

      <form @submit.prevent="submit">
        <label class="pa-field">
          <span class="pa-field__label">Email</span>
          <input
            v-model="email"
            class="pa-input"
            type="email"
            autocomplete="username"
            required
          >
        </label>

        <label class="pa-field">
          <span class="pa-field__label">Password</span>
          <input
            v-model="password"
            class="pa-input"
            type="password"
            autocomplete="current-password"
            required
          >
        </label>

        <p v-if="error" class="pa-alert">{{ error }}</p>

        <button class="pa-button pa-button--wide" type="submit" :disabled="busy">
          {{ busy ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </div>
  </section>
</template>
