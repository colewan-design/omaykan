<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { dateTime, messageFor } from '@pos/web/platform-admin/admin'
import { fetchAuditLogs, type AuditFilters, type AuditLogRow } from '@pos/web/platform-admin/api'

/*
 * Reading the record.
 *
 * This screen is the reason named operator accounts replaced the shared
 * secret. Before it, the only trace an action left was an anonymous line in the
 * application log on the VPS — so "who suspended this shop" had no answer that
 * anyone could look up.
 *
 * Read-only, and there is no control anywhere to change or remove a row. Every
 * operator can see every other operator's actions, including an owner's:
 * mutual visibility is the point.
 */

const logs = ref<AuditLogRow[]>([])
const actors = ref<string[]>([])
const actions = ref<string[]>([])

const page = ref(1)
const lastPage = ref(1)
const total = ref(0)

const actor = ref('')
const action = ref('')
const from = ref('')
const to = ref('')

const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''

  const filters: AuditFilters = { page: page.value }
  if (actor.value) filters.actor = actor.value
  if (action.value) filters.action = action.value
  if (from.value) filters.from = from.value
  if (to.value) filters.to = to.value

  try {
    const result = await fetchAuditLogs(filters)
    logs.value = result.logs
    page.value = result.page
    lastPage.value = result.lastPage
    total.value = result.total
    actors.value = result.actors
    actions.value = result.actions
  } catch (err) {
    error.value = messageFor(err, 'Could not load the audit log.')
  } finally {
    loading.value = false
  }
}

onMounted(load)

watch([actor, action, from, to], () => {
  page.value = 1
  void load()
})

async function turnPage(delta: number) {
  page.value = Math.min(lastPage.value, Math.max(1, page.value + delta))
  await load()
}

/**
 * The context, as one readable line.
 *
 * Deliberately not a JSON dump: the operator wants "reason: Chargeback", not
 * braces. Keys are already camelCase from the API, which reads well enough.
 */
function contextLine(row: AuditLogRow): string {
  return Object.entries(row.context)
    .filter(([, value]) => value !== null && value !== '')
    .map(([key, value]) => `${key}: ${value}`)
    .join(' · ')
}

/** Only an Organization subject has a page to link to. */
function subjectLink(row: AuditLogRow): string | null {
  return row.subjectType === 'Organization' && row.subjectId ? `/tenants/${row.subjectId}` : null
}
</script>

<template>
  <div class="pa-head">
    <div>
      <h1>Audit log</h1>
      <p class="pa-head__copy">
        Every operator action, as it happened. {{ total }} recorded.
      </p>
    </div>
    <button class="pa-button pa-button--quiet" type="button" :disabled="loading" @click="load">
      {{ loading ? 'Refreshing…' : 'Refresh' }}
    </button>
  </div>

  <div class="pa-filters">
    <label class="pa-field">
      <span class="pa-field__label">Operator</span>
      <select v-model="actor" class="pa-select">
        <option value="">Anyone</option>
        <option v-for="email in actors" :key="email" :value="email">{{ email }}</option>
      </select>
    </label>
    <label class="pa-field">
      <span class="pa-field__label">Action</span>
      <select v-model="action" class="pa-select">
        <option value="">Anything</option>
        <option v-for="name in actions" :key="name" :value="name">{{ name }}</option>
      </select>
    </label>
    <label class="pa-field">
      <span class="pa-field__label">From</span>
      <input v-model="from" class="pa-input" type="date">
    </label>
    <label class="pa-field">
      <span class="pa-field__label">To</span>
      <input v-model="to" class="pa-input" type="date">
    </label>
  </div>

  <p v-if="error" class="pa-alert">{{ error }}</p>

  <section class="pa-panel">
    <p v-if="!logs.length && !loading" class="pa-empty">Nothing matches those filters.</p>

    <div v-else class="pa-table-wrap">
      <table class="pa-table">
        <thead>
          <tr>
            <th>When</th>
            <th>Operator</th>
            <th>Action</th>
            <th>Subject</th>
            <th>Detail</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in logs" :key="row.id">
            <td>{{ dateTime(row.createdAt) }}</td>
            <td>
              {{ row.actorName ?? row.actorEmail }}
              <div class="pa-slug">{{ row.actorEmail }}</div>
            </td>
            <td><strong>{{ row.action }}</strong></td>
            <td>
              <RouterLink v-if="subjectLink(row)" class="pa-link" :to="subjectLink(row)!">
                {{ row.subjectId }}
              </RouterLink>
              <span v-else class="pa-slug">{{ row.subjectId ?? '—' }}</span>
            </td>
            <td class="pa-tile__note">{{ contextLine(row) || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <div v-if="lastPage > 1" class="pa-pager">
    <span>Page {{ page }} of {{ lastPage }}</span>
    <button class="pa-button pa-button--quiet" type="button" :disabled="page <= 1" @click="turnPage(-1)">
      Previous
    </button>
    <button
      class="pa-button pa-button--quiet"
      type="button"
      :disabled="page >= lastPage"
      @click="turnPage(1)"
    >
      Next
    </button>
  </div>
</template>
