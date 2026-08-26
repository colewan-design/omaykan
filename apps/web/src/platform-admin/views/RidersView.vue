<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { dateTime, messageFor } from '@pos/web/platform-admin/admin'
import {
  decideRider,
  fetchRiderDocument,
  fetchRiders,
  type ReviewRider,
  type RiderStatus,
} from '@pos/web/platform-admin/api'

/*
 * The operator's side of rider registration.
 *
 * Riders sign up unvetted and can do nothing until a human has looked at their
 * licence — so without this screen, registration is a dead end: applications
 * arrive and nobody can clear them without hand-rolling an API call. That is
 * the whole reason this exists.
 *
 * The document images are the awkward part. They live on the private disk and
 * have no URL: the only way to see one is an authenticated request, which an
 * `<img src>` cannot make. So each image is fetched as a blob and shown from an
 * object URL — which also means every one has to be revoked by hand, and none
 * of them are ever put anywhere a browser or a proxy would cache them.
 */

const riders = ref<ReviewRider[]>([])
const statusFilter = ref<'' | RiderStatus>('')
const loading = ref(false)
const error = ref('')
const busyId = ref('')

/** The rider whose documents are open, and the two object URLs being shown. */
const viewing = ref<ReviewRider | null>(null)
const documents = ref<{ license: string; plate: string }>({ license: '', plate: '' })
const documentsError = ref('')

/** Per-rider note, typed before a reject or a suspend. */
const notes = ref<Record<string, string>>({})

async function load() {
  loading.value = true
  error.value = ''

  try {
    riders.value = (await fetchRiders(statusFilter.value || undefined)).riders
  } catch (err) {
    error.value = messageFor(err, 'Could not load the rider queue.')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function decide(rider: ReviewRider, status: 'approved' | 'rejected' | 'suspended') {
  // A rejection or a suspension without a reason is a dead end for the rider —
  // the note is the only thing their status screen can show them.
  const note = (notes.value[rider.id] ?? '').trim()
  if (status !== 'approved' && !note) {
    error.value = 'Add a note first — the rider sees it, and it is the only reason they get.'
    return
  }

  busyId.value = rider.id
  error.value = ''

  try {
    const result = await decideRider(rider.id, status, note || undefined)
    riders.value = riders.value.map((row) => (row.id === rider.id ? result.rider : row))
    notes.value[rider.id] = ''
  } catch (err) {
    error.value = messageFor(err, 'Could not record that decision.')
  } finally {
    busyId.value = ''
  }
}

function releaseDocuments() {
  if (documents.value.license) URL.revokeObjectURL(documents.value.license)
  if (documents.value.plate) URL.revokeObjectURL(documents.value.plate)
  documents.value = { license: '', plate: '' }
}

async function openDocuments(rider: ReviewRider) {
  releaseDocuments()
  viewing.value = rider
  documentsError.value = ''
  busyId.value = rider.id

  try {
    const [license, plate] = await Promise.all([
      fetchRiderDocument(rider.id, 'license'),
      fetchRiderDocument(rider.id, 'plate'),
    ])
    documents.value = { license, plate }
  } catch (err) {
    documentsError.value = messageFor(err, 'Could not load the documents.')
  } finally {
    busyId.value = ''
  }
}

function closeDocuments() {
  releaseDocuments()
  viewing.value = null
  documentsError.value = ''
}

// An identity document should not outlive the tab it was opened in.
onBeforeUnmount(releaseDocuments)

function statusClass(status: RiderStatus) {
  if (status === 'approved') return 'pa-badge--good'
  if (status === 'pending') return 'pa-badge--warn'
  return 'pa-badge--bad'
}
</script>

<template>
  <div class="pa-head">
    <div>
      <h1>Riders</h1>
      <p class="pa-head__copy">
        Check each licence and plate against what was typed, then approve. Nobody can take a
        delivery until you do.
      </p>
    </div>
    <div class="pa-actions">
      <select v-model="statusFilter" class="pa-select" style="width: auto" @change="load">
        <option value="">All riders</option>
        <option value="pending">Pending</option>
        <option value="approved">Approved</option>
        <option value="rejected">Rejected</option>
        <option value="suspended">Suspended</option>
      </select>
      <button class="pa-button pa-button--quiet" type="button" :disabled="loading" @click="load">
        {{ loading ? 'Refreshing…' : 'Refresh' }}
      </button>
    </div>
  </div>

  <p v-if="error" class="pa-alert">{{ error }}</p>

  <section class="pa-panel">
    <p v-if="!riders.length && !loading" class="pa-empty">
      No rider applications {{ statusFilter ? 'with that status' : 'yet' }}.
    </p>

    <div v-else class="pa-table-wrap">
      <table class="pa-table">
        <thead>
          <tr>
            <th>Rider</th>
            <th>Licence &amp; plate</th>
            <th>Status</th>
            <th>Applied</th>
            <th>Decision</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="rider in riders" :key="rider.id">
            <td>
              <strong>{{ rider.name }}</strong>
              <div class="pa-slug">{{ rider.email }}</div>
              <div class="pa-slug">{{ rider.phone }}</div>
            </td>

            <td>
              <div class="pa-slug">{{ rider.licenseNumber }}</div>
              <div class="pa-slug">{{ rider.plateNumber }}</div>
              <button
                class="pa-link"
                type="button"
                :disabled="busyId === rider.id"
                @click="openDocuments(rider)"
              >
                View photos
              </button>
            </td>

            <td>
              <span class="pa-badge" :class="statusClass(rider.status)">{{ rider.status }}</span>
              <div v-if="rider.reviewNote" class="pa-tile__note">“{{ rider.reviewNote }}”</div>
              <div v-if="rider.deliveriesCompleted" class="pa-tile__note">
                {{ rider.deliveriesCompleted }} delivered
              </div>
            </td>

            <td>
              {{ dateTime(rider.createdAt) }}
              <div v-if="rider.reviewedAt" class="pa-tile__note">
                Reviewed {{ dateTime(rider.reviewedAt) }}
              </div>
            </td>

            <td>
              <!-- The note sits with the buttons because it is required by two
                   of the three: a rider reads it on their status screen, and
                   it is the only reason they ever get. -->
              <textarea
                v-model="notes[rider.id]"
                class="pa-note"
                placeholder="Note to the rider (required to reject or suspend)"
              />
              <div class="pa-actions">
                <button
                  v-if="rider.status !== 'approved'"
                  class="pa-button"
                  type="button"
                  :disabled="busyId === rider.id"
                  @click="decide(rider, 'approved')"
                >
                  Approve
                </button>
                <button
                  v-if="rider.status === 'pending'"
                  class="pa-link pa-link--danger"
                  type="button"
                  :disabled="busyId === rider.id"
                  @click="decide(rider, 'rejected')"
                >
                  Reject
                </button>
                <button
                  v-if="rider.status === 'approved'"
                  class="pa-link pa-link--danger"
                  type="button"
                  :disabled="busyId === rider.id"
                  @click="decide(rider, 'suspended')"
                >
                  Suspend
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <div v-if="viewing" class="pa-scrim" @click.self="closeDocuments">
    <div class="pa-dialog" style="max-width: 720px" role="dialog" aria-modal="true">
      <h2>{{ viewing.name }}</h2>
      <p class="pa-dialog__copy">
        {{ viewing.licenseNumber }} · {{ viewing.plateNumber }}
      </p>

      <p v-if="documentsError" class="pa-alert">{{ documentsError }}</p>

      <div v-else class="pa-docs">
        <div class="pa-doc">
          <p class="pa-doc__label">Driver's licence</p>
          <img v-if="documents.license" :src="documents.license" alt="Driver's licence">
          <p v-else class="pa-tile__note">Loading…</p>
        </div>
        <div class="pa-doc">
          <p class="pa-doc__label">Plate</p>
          <img v-if="documents.plate" :src="documents.plate" alt="Motorcycle plate">
          <p v-else class="pa-tile__note">Loading…</p>
        </div>
      </div>

      <div class="pa-dialog__actions">
        <button class="pa-button pa-button--quiet" type="button" @click="closeDocuments">Close</button>
      </div>
    </div>
  </div>
</template>
