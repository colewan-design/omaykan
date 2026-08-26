<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dateTime, fieldErrorsFor, messageFor, usePlatformSession } from '@pos/web/platform-admin/admin'
import {
  createAdmin,
  fetchAdmins,
  setAdminStatus,
  type AdminRole,
  type PlatformAdminProfile,
} from '@pos/web/platform-admin/api'
import RevealedSecret from '@pos/web/platform-admin/components/RevealedSecret.vue'

/*
 * The operators themselves. Owner-only, and the server says so too — this
 * screen is simply not offered to an operator, which saves showing a door that
 * will not open.
 *
 * There is no signup form anywhere for these accounts, deliberately: an open
 * registration page on a cross-tenant tool is the shared secret's problem again
 * with better UX. Accounts come from here, or from
 * `php artisan platform:admin-create` on the box.
 */

const session = usePlatformSession()

const admins = ref<PlatformAdminProfile[]>([])
const loading = ref(false)
const error = ref('')
const busyId = ref('')

const name = ref('')
const email = ref('')
const role = ref<AdminRole>('operator')
const creating = ref(false)
const fields = ref<Record<string, string>>({})
const revealed = ref<{ title: string; value: string } | null>(null)

async function load() {
  loading.value = true
  error.value = ''

  try {
    admins.value = (await fetchAdmins()).admins
  } catch (err) {
    error.value = messageFor(err, 'Could not load the operators.')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function create() {
  if (creating.value) return

  creating.value = true
  error.value = ''
  fields.value = {}

  try {
    const result = await createAdmin({
      name: name.value.trim(),
      email: email.value.trim(),
      role: role.value,
    })

    revealed.value = { title: `Password for ${result.admin.email}`, value: result.password }
    name.value = ''
    email.value = ''
    role.value = 'operator'
    await load()
  } catch (err) {
    fields.value = fieldErrorsFor(err)
    error.value = messageFor(err, 'Could not create that operator.')
  } finally {
    creating.value = false
  }
}

async function toggle(admin: PlatformAdminProfile) {
  busyId.value = admin.id
  error.value = ''

  try {
    const next = admin.status === 'active' ? 'disabled' : 'active'
    const result = await setAdminStatus(admin.id, next)
    admins.value = admins.value.map((row) => (row.id === admin.id ? result.admin : row))
  } catch (err) {
    error.value = messageFor(err, 'Could not update that operator.')
  } finally {
    busyId.value = ''
  }
}
</script>

<template>
  <div class="pa-head">
    <div>
      <h1>Operators</h1>
      <p class="pa-head__copy">
        Who can work the platform, and what they are allowed to do. Owners can delete tenants and
        manage this list; operators cannot.
      </p>
    </div>
    <button class="pa-button pa-button--quiet" type="button" :disabled="loading" @click="load">
      {{ loading ? 'Refreshing…' : 'Refresh' }}
    </button>
  </div>

  <p v-if="error" class="pa-alert">{{ error }}</p>

  <RevealedSecret
    v-if="revealed"
    :title="revealed.title"
    :value="revealed.value"
    @done="revealed = null"
  />

  <div class="pa-columns">
    <section class="pa-panel">
      <div class="pa-panel__head"><h2>Everyone with access</h2></div>

      <div class="pa-table-wrap">
        <table class="pa-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Role</th>
              <th>Status</th>
              <th>Last signed in</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="admin in admins" :key="admin.id">
              <td>
                <strong>{{ admin.name }}</strong>
                <div class="pa-slug">{{ admin.email }}</div>
              </td>
              <td style="text-transform: capitalize">{{ admin.role }}</td>
              <td>
                <span class="pa-badge" :class="admin.status === 'active' ? 'pa-badge--good' : 'pa-badge--bad'">
                  {{ admin.status }}
                </span>
              </td>
              <td>{{ dateTime(admin.lastLoginAt) }}</td>
              <td>
                <!-- No self-disable: locking yourself out of the tool you are
                     holding needs SSH to undo, so the server refuses it too. -->
                <button
                  v-if="admin.id !== session.admin.value?.id"
                  class="pa-link"
                  :class="{ 'pa-link--danger': admin.status === 'active' }"
                  type="button"
                  :disabled="busyId === admin.id"
                  @click="toggle(admin)"
                >
                  {{ admin.status === 'active' ? 'Disable' : 'Enable' }}
                </button>
                <span v-else class="pa-slug">You</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="pa-panel">
      <div class="pa-panel__head"><h2>Add an operator</h2></div>
      <form class="pa-panel__body" @submit.prevent="create">
        <label class="pa-field">
          <span class="pa-field__label">Full name</span>
          <input v-model="name" class="pa-input" type="text" required>
          <span v-if="fields.name" class="pa-field__error">{{ fields.name }}</span>
        </label>

        <label class="pa-field">
          <span class="pa-field__label">Email</span>
          <input v-model="email" class="pa-input" type="email" required>
          <span v-if="fields.email" class="pa-field__error">{{ fields.email }}</span>
        </label>

        <label class="pa-field">
          <span class="pa-field__label">Role</span>
          <select v-model="role" class="pa-select">
            <option value="operator">Operator — works the queues</option>
            <option value="owner">Owner — can also delete tenants</option>
          </select>
        </label>

        <p class="pa-tile__note" style="margin-bottom: 14px">
          A password is generated and shown once. Nothing stores it — relay it to them yourself.
        </p>

        <button class="pa-button pa-button--wide" type="submit" :disabled="creating">
          {{ creating ? 'Creating…' : 'Create operator' }}
        </button>
      </form>
    </section>
  </div>
</template>
