<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  messageFor,
  substitutionOptions,
  useCustomerAccount,
  type SubstitutionPreference,
} from '@pos/web/commerce/customer'

// Name, phone and the two decisions that change what happens to an order: how
// we reach the customer, and what a rider does when a line is out of stock.
// Both are asked here rather than at checkout, where every extra question
// costs an order.
//
// Email and password live at the bottom, each behind the current password.
// They are the credentials — an unattended laptop is otherwise one click from
// becoming somebody else's account — and the API enforces the same rule.

const account = useCustomerAccount()

// Edited on a copy so an abandoned edit doesn't rewrite the stored profile:
// these fields feed a real order, so a half-typed phone number must not stick.
const form = reactive({
  name: '',
  phone: '',
  substitutions: 'call' as SubstitutionPreference,
})

watch(
  account.account,
  (profile) => {
    if (!profile) return
    form.name = profile.name
    form.phone = profile.phone
    form.substitutions = profile.preferences.substitutions
  },
  { immediate: true },
)

const saving = ref(false)
const saved = ref(false)
const error = ref('')

async function save() {
  if (!form.name.trim()) {
    error.value = 'A name is needed — it goes on the order.'
    return
  }

  saving.value = true
  error.value = ''

  try {
    await account.updateProfile({
      name: form.name.trim(),
      phone: form.phone.trim(),
      preferences: { substitutions: form.substitutions },
    })
    saved.value = true
    setTimeout(() => (saved.value = false), 2400)
  } catch (cause) {
    error.value = messageFor(cause, 'Could not save your details.')
  } finally {
    saving.value = false
  }
}

const toggleError = ref('')

/** Toggles write straight through: a checkbox with a Save button lies. */
async function toggle(key: 'emailUpdates' | 'smsUpdates' | 'marketingEmails', value: boolean) {
  toggleError.value = ''
  try {
    await account.updateProfile({ preferences: { [key]: value } })
  } catch (cause) {
    toggleError.value = messageFor(cause, 'Could not save that.')
  }
}

// -- Email ------------------------------------------------------------------

const emailForm = reactive({ email: '', currentPassword: '' })
const emailOpen = ref(false)
const emailBusy = ref(false)
const emailError = ref('')
const emailSaved = ref(false)

function openEmail() {
  emailForm.email = account.account.value?.email ?? ''
  emailForm.currentPassword = ''
  emailError.value = ''
  emailOpen.value = true
}

async function saveEmail() {
  emailBusy.value = true
  emailError.value = ''

  try {
    await account.changeEmail(emailForm.email.trim(), emailForm.currentPassword)
    emailOpen.value = false
    emailSaved.value = true
    setTimeout(() => (emailSaved.value = false), 2400)
  } catch (cause) {
    emailError.value = messageFor(cause, 'Could not change your email.')
  } finally {
    emailBusy.value = false
  }
}

// -- Password ---------------------------------------------------------------

const passwordForm = reactive({ currentPassword: '', password: '', confirm: '' })
const passwordOpen = ref(false)
const passwordBusy = ref(false)
const passwordError = ref('')
const passwordSaved = ref(false)

function openPassword() {
  passwordForm.currentPassword = ''
  passwordForm.password = ''
  passwordForm.confirm = ''
  passwordError.value = ''
  passwordOpen.value = true
}

async function savePassword() {
  if (passwordForm.password !== passwordForm.confirm) {
    passwordError.value = 'The two new passwords don\'t match.'
    return
  }

  passwordBusy.value = true
  passwordError.value = ''

  try {
    await account.changePassword(passwordForm.currentPassword, passwordForm.password)
    passwordOpen.value = false
    passwordSaved.value = true
    setTimeout(() => (passwordSaved.value = false), 4000)
  } catch (cause) {
    passwordError.value = messageFor(cause, 'Could not change your password.')
  } finally {
    passwordBusy.value = false
  }
}
</script>

<template>
  <div v-if="account.account.value">
    <div class="acct-head">
      <h1 class="acct-head__title">Account preferences</h1>
      <p class="acct-head__sub">
        The details that go on every order, and how you'd like to hear about them.
      </p>
    </div>

    <form class="acct-card" @submit.prevent="save">
      <p class="acct-card__title">Your details</p>
      <p class="acct-card__note" style="margin-bottom: 20px">
        The rider uses these to find you and to let you know they're on the way.
      </p>

      <div class="acct-row">
        <label class="acct-field">
          <span class="acct-field__label">Name</span>
          <input v-model="form.name" class="acct-input" type="text" autocomplete="name" />
        </label>
        <label class="acct-field">
          <span class="acct-field__label">Mobile number</span>
          <input v-model="form.phone" class="acct-input" type="tel" autocomplete="tel" placeholder="09XX XXX XXXX" />
        </label>
      </div>

      <label class="acct-field">
        <span class="acct-field__label">If something's out of stock</span>
        <select v-model="form.substitutions" class="acct-select">
          <option v-for="option in substitutionOptions" :key="option.value" :value="option.value">
            {{ option.label }} — {{ option.hint }}
          </option>
        </select>
      </label>

      <div class="acct-actions">
        <button type="submit" class="acct-btn" :disabled="saving">
          {{ saving ? 'Saving…' : 'Save changes' }}
        </button>
      </div>

      <p v-if="saved" class="acct-flash">Saved.</p>
      <p v-if="error" class="acct-flash acct-flash--error">{{ error }}</p>
    </form>

    <div class="acct-card">
      <p class="acct-card__title">Notifications</p>
      <p class="acct-card__note" style="margin-bottom: 8px">Saved as you change them.</p>

      <label class="acct-toggle">
        <input
          type="checkbox"
          :checked="account.account.value.preferences.emailUpdates"
          @change="toggle('emailUpdates', ($event.target as HTMLInputElement).checked)"
        />
        <span class="acct-toggle__body">
          <span class="acct-toggle__label">Order updates by email</span>
          <span class="acct-toggle__hint">Confirmation when you order, and again when a rider picks it up.</span>
        </span>
      </label>

      <label class="acct-toggle">
        <input
          type="checkbox"
          :checked="account.account.value.preferences.smsUpdates"
          @change="toggle('smsUpdates', ($event.target as HTMLInputElement).checked)"
        />
        <span class="acct-toggle__body">
          <span class="acct-toggle__label">Order updates by SMS</span>
          <span class="acct-toggle__hint">
            Sent to the mobile number above. Useful when the rider is close.
          </span>
        </span>
      </label>

      <label class="acct-toggle">
        <input
          type="checkbox"
          :checked="account.account.value.preferences.marketingEmails"
          @change="toggle('marketingEmails', ($event.target as HTMLInputElement).checked)"
        />
        <span class="acct-toggle__body">
          <span class="acct-toggle__label">New shops and offers</span>
          <span class="acct-toggle__hint">Occasional, and never more than once a week. Off by default.</span>
        </span>
      </label>

      <p v-if="toggleError" class="acct-flash acct-flash--error">{{ toggleError }}</p>
    </div>

    <!-- ── Credentials ──────────────────────────────────────────────── -->

    <div class="acct-card">
      <p class="acct-card__title">Email</p>
      <p class="acct-card__note">{{ account.account.value.email }}</p>

      <form v-if="emailOpen" style="margin-top: 18px" @submit.prevent="saveEmail">
        <label class="acct-field">
          <span class="acct-field__label">New email</span>
          <input v-model="emailForm.email" class="acct-input" type="email" autocomplete="email" />
        </label>
        <label class="acct-field">
          <span class="acct-field__label">Your password</span>
          <input
            v-model="emailForm.currentPassword"
            class="acct-input"
            type="password"
            autocomplete="current-password"
          />
          <span class="acct-field__hint">
            Asked for because this is the address a reset link would be sent to.
          </span>
        </label>
        <div class="acct-actions">
          <button type="submit" class="acct-btn" :disabled="emailBusy">
            {{ emailBusy ? 'Saving…' : 'Change email' }}
          </button>
          <button type="button" class="acct-link" @click="emailOpen = false">Cancel</button>
        </div>
        <p v-if="emailError" class="acct-flash acct-flash--error">{{ emailError }}</p>
      </form>

      <div v-else class="acct-actions">
        <button type="button" class="acct-btn acct-btn--ghost" @click="openEmail">Change email</button>
      </div>

      <p v-if="emailSaved" class="acct-flash">Email updated.</p>
    </div>

    <div class="acct-card">
      <p class="acct-card__title">Password</p>
      <p class="acct-card__note">
        Changing it signs out every other device — which is the point, if one of them isn't yours.
      </p>

      <form v-if="passwordOpen" style="margin-top: 18px" @submit.prevent="savePassword">
        <label class="acct-field">
          <span class="acct-field__label">Current password</span>
          <input
            v-model="passwordForm.currentPassword"
            class="acct-input"
            type="password"
            autocomplete="current-password"
          />
        </label>
        <div class="acct-row">
          <label class="acct-field">
            <span class="acct-field__label">New password</span>
            <input
              v-model="passwordForm.password"
              class="acct-input"
              type="password"
              autocomplete="new-password"
            />
          </label>
          <label class="acct-field">
            <span class="acct-field__label">Again</span>
            <input
              v-model="passwordForm.confirm"
              class="acct-input"
              type="password"
              autocomplete="new-password"
            />
          </label>
        </div>
        <div class="acct-actions">
          <button type="submit" class="acct-btn" :disabled="passwordBusy">
            {{ passwordBusy ? 'Saving…' : 'Change password' }}
          </button>
          <button type="button" class="acct-link" @click="passwordOpen = false">Cancel</button>
        </div>
        <p v-if="passwordError" class="acct-flash acct-flash--error">{{ passwordError }}</p>
      </form>

      <div v-else class="acct-actions">
        <button type="button" class="acct-btn acct-btn--ghost" @click="openPassword">Change password</button>
      </div>

      <p v-if="passwordSaved" class="acct-flash">Password changed. Other devices have been signed out.</p>
    </div>
  </div>
</template>
