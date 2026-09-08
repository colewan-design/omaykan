<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { updateRiderPassword, updateRiderProfile } from '@pos/web/rider/api'
import { fieldErrorsFor, messageFor, useRiderSession } from '@pos/web/rider/rider'
import type { RiderProfile } from '@pos/web/rider/api'

/*
 * The rider's own account.
 *
 * The gap this fills is blunt: until now nothing anywhere could change a single
 * field on a rider after registration. A new phone number, a new bike, a
 * password someone else had learned — all of them meant asking an operator to
 * edit the database, and there is no operator screen for it either.
 *
 * Two forms rather than one, because they are two different acts. Saving a
 * phone number should not ask for a password, and changing a password should
 * not quietly save a half-edited name alongside it.
 *
 * What is *not* here is as deliberate as what is — see RiderAccountController:
 * the email is the login handle and the address a reset link goes to, and the
 * licence number is the thing an operator actually approved. Both are shown,
 * greyed, with the reason written next to them: a field that is missing reads
 * as an oversight, and a field that is refused on submit is worse.
 */

const session = useRiderSession()

const details = reactive({ name: '', phone: '', plateNumber: '' })
const detailsSaving = ref(false)
const detailsError = ref('')
const detailsNotice = ref('')
const detailsFields = ref<Record<string, string>>({})

const password = reactive({ current: '', next: '', confirm: '' })
const passwordSaving = ref(false)
const passwordError = ref('')
const passwordNotice = ref('')
const passwordFields = ref<Record<string, string>>({})

/** Seeded from the session, and re-seeded whenever the account itself changes. */
watch(
  session.rider,
  (rider: RiderProfile | null) => {
    if (rider === null) return
    details.name = rider.name
    details.phone = rider.phone
    details.plateNumber = rider.plateNumber
  },
  { immediate: true },
)

async function saveDetails() {
  if (detailsSaving.value) return

  detailsSaving.value = true
  detailsError.value = ''
  detailsNotice.value = ''
  detailsFields.value = {}

  try {
    await updateRiderProfile({
      name: details.name.trim(),
      phone: details.phone.trim(),
      plateNumber: details.plateNumber.trim(),
    })

    // Re-read rather than trusting the local copy: the session is what the
    // header, the status screen and the gate all render from, and two copies
    // of the rider is how they start disagreeing.
    await session.refresh()
    detailsNotice.value = 'Saved.'
  } catch (error) {
    detailsFields.value = fieldErrorsFor(error)
    detailsError.value = messageFor(error, 'Could not save your details.')
  } finally {
    detailsSaving.value = false
  }
}

async function savePassword() {
  if (passwordSaving.value) return

  if (password.next !== password.confirm) {
    passwordFields.value = { password: 'The two new passwords do not match.' }
    passwordError.value = 'The two new passwords do not match.'
    return
  }

  passwordSaving.value = true
  passwordError.value = ''
  passwordNotice.value = ''
  passwordFields.value = {}

  try {
    await updateRiderPassword(password.current, password.next)
    password.current = ''
    password.next = ''
    password.confirm = ''
    passwordNotice.value = 'Password changed. Your other phones have been signed out.'
  } catch (error) {
    passwordFields.value = fieldErrorsFor(error)
    passwordError.value = messageFor(error, 'Could not change your password.')
  } finally {
    passwordSaving.value = false
  }
}
</script>

<template>
  <div v-if="session.rider.value">
    <h1 class="rdr-title">Your account</h1>
    <p class="rdr-sub">Keep these right — the shop reads them off the screen when you arrive.</p>

    <!-- ── Details ──────────────────────────────────────────────────── -->
    <p v-if="detailsError" class="rdr-flash rdr-flash--error">{{ detailsError }}</p>
    <p v-if="detailsNotice" class="rdr-flash rdr-flash--ok">{{ detailsNotice }}</p>

    <form class="rdr-card" novalidate @submit.prevent="saveDetails">
      <label class="rdr-field">
        <span class="rdr-field__label">Name</span>
        <input v-model="details.name" class="rdr-input" type="text" autocomplete="name">
        <span v-if="detailsFields.name" class="rdr-field__error">{{ detailsFields.name }}</span>
      </label>

      <label class="rdr-field">
        <span class="rdr-field__label">Phone</span>
        <input v-model="details.phone" class="rdr-input" type="tel" inputmode="tel" autocomplete="tel">
        <span v-if="detailsFields.phone" class="rdr-field__error">{{ detailsFields.phone }}</span>
      </label>

      <label class="rdr-field">
        <span class="rdr-field__label">Plate number</span>
        <input v-model="details.plateNumber" class="rdr-input" type="text">
        <span class="rdr-field__hint">
          Change this when you change bikes. The plate we have a photo of stays on file, so
          the operator can see they no longer match.
        </span>
        <span v-if="detailsFields.plateNumber" class="rdr-field__error">
          {{ detailsFields.plateNumber }}
        </span>
      </label>

      <button class="rdr-btn rdr-btn--block" type="submit" :disabled="detailsSaving">
        {{ detailsSaving ? 'Saving…' : 'Save details' }}
      </button>
    </form>

    <!-- ── The two that are not yours to change ─────────────────────── -->
    <div class="rdr-card">
      <label class="rdr-field">
        <span class="rdr-field__label">Email</span>
        <input class="rdr-input" type="email" :value="session.rider.value.email" disabled>
        <span class="rdr-field__hint">
          This is how you sign in and where a reset link goes, so it is changed by asking us
          rather than from here.
        </span>
      </label>

      <label class="rdr-field">
        <span class="rdr-field__label">Licence number</span>
        <input class="rdr-input" type="text" :value="session.rider.value.licenseNumber" disabled>
        <span class="rdr-field__hint">
          Someone checked this against the photo you sent. Changing it would mean checking it
          again — message us and we will.
        </span>
      </label>
    </div>

    <!-- ── Password ─────────────────────────────────────────────────── -->
    <p class="rdr-section-title">Password</p>

    <p v-if="passwordError" class="rdr-flash rdr-flash--error">{{ passwordError }}</p>
    <p v-if="passwordNotice" class="rdr-flash rdr-flash--ok">{{ passwordNotice }}</p>

    <form class="rdr-card" novalidate @submit.prevent="savePassword">
      <label class="rdr-field">
        <span class="rdr-field__label">Current password</span>
        <input
          v-model="password.current"
          class="rdr-input"
          type="password"
          autocomplete="current-password"
        >
        <span v-if="passwordFields.currentPassword" class="rdr-field__error">
          {{ passwordFields.currentPassword }}
        </span>
      </label>

      <label class="rdr-field">
        <span class="rdr-field__label">New password</span>
        <input
          v-model="password.next"
          class="rdr-input"
          type="password"
          autocomplete="new-password"
        >
        <span v-if="passwordFields.password" class="rdr-field__error">
          {{ passwordFields.password }}
        </span>
      </label>

      <label class="rdr-field">
        <span class="rdr-field__label">New password again</span>
        <input
          v-model="password.confirm"
          class="rdr-input"
          type="password"
          autocomplete="new-password"
        >
      </label>

      <p class="rdr-field__hint" style="margin: 0 0 14px">
        Changing it signs out every other phone you are signed in on. This one stays — you are
        in the middle of a shift, not locked out of it.
      </p>

      <button class="rdr-btn rdr-btn--block" type="submit" :disabled="passwordSaving">
        {{ passwordSaving ? 'Changing…' : 'Change password' }}
      </button>
    </form>
  </div>
</template>
