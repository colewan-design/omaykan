<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

/*
 * The operator's side of rider registration.
 *
 * Riders sign up unvetted and can do nothing until a human has looked at their
 * licence — so without this screen, registration is a dead end: applications
 * arrive and nobody can clear them without hand-rolling an API call. That is
 * the whole reason this exists.
 *
 * The document images are the awkward part. They live on the private disk and
 * have no URL: the only way to see one is POST /api/rider-review/{id}/document
 * carrying the operator's bearer token, which is not something an <img src> can
 * do. So each image is fetched as a blob and shown from an object URL — which
 * also means every one has to be revoked by hand, and none of them are ever put
 * anywhere a browser or a proxy would cache them.
 */

const props = defineProps<{ token: string }>()

/*
 * Raised when the API rejects the token, so the page above can drop the
 * session rather than leaving this screen retrying against a dead one.
 */
const emit = defineEmits<{ (event: 'session-ended', message: string): void }>()

function authHeaders(json = true): Record<string, string> {
  return {
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    Accept: 'application/json',
    Authorization: `Bearer ${props.token}`,
  }
}

interface ReviewRider {
  id: string
  name: string
  email: string
  phone: string
  licenseNumber: string
  plateNumber: string
  status: 'pending' | 'approved' | 'rejected' | 'suspended'
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string | null
  deliveriesCompleted: number
  lastSeenAt: string | null
}

const riders = ref<ReviewRider[]>([])
const loading = ref(false)
const errorMessage = ref('')
const busyId = ref('')

/** The rider whose documents are open, and the two object URLs being shown. */
const viewing = ref<ReviewRider | null>(null)
const documents = ref<{ license: string; plate: string }>({ license: '', plate: '' })
const documentsError = ref('')

/** Per-rider note, typed before a reject or a suspend. */
const notes = ref<Record<string, string>>({})

function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
}

async function post(path: string, body: Record<string, unknown> = {}) {
  const response = await fetch(path, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })
  const data = await response.json().catch(() => ({}))
  if (response.status === 401 || response.status === 403) {
    const message = data.message || 'Your session has ended. Sign in again.'
    emit('session-ended', message)
    throw new Error(message)
  }
  if (!response.ok) {
    throw new Error(data.message || data.error || 'Something went wrong.')
  }
  return data
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    riders.value = (await post('/api/rider-review')).riders ?? []
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to load the rider queue.'
  } finally {
    loading.value = false
  }
}

async function decide(rider: ReviewRider, status: 'approved' | 'rejected' | 'suspended') {
  // A rejection or a suspension without a reason is a dead end for the rider —
  // the note is the only thing their status screen can show them.
  const note = (notes.value[rider.id] ?? '').trim()
  if (status !== 'approved' && !note) {
    errorMessage.value = 'Add a note first — the rider sees it, and it is the only reason they get.'
    return
  }

  busyId.value = rider.id
  errorMessage.value = ''

  try {
    const data = await post(`/api/rider-review/${rider.id}/decision`, { status, note: note || null })
    riders.value = riders.value.map((row) => (row.id === rider.id ? data.rider : row))
    notes.value[rider.id] = ''
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Unable to record that decision.'
  } finally {
    busyId.value = ''
  }
}

/** Fetches one document as a blob, because it is a POST behind the token. */
async function fetchDocument(riderId: string, document: 'license' | 'plate'): Promise<string> {
  const response = await fetch(`/api/rider-review/${riderId}/document/${document}`, {
    method: 'POST',
    headers: authHeaders(false),
  })

  if (!response.ok) throw new Error('That document could not be loaded.')

  return URL.createObjectURL(await response.blob())
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
      fetchDocument(rider.id, 'license'),
      fetchDocument(rider.id, 'plate'),
    ])
    documents.value = { license, plate }
  } catch (err) {
    documentsError.value = err instanceof Error ? err.message : 'Could not load the documents.'
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

void load()
</script>

<template>
  <div>
    <div class="pa-header">
      <div>
        <h1 class="pa-title">Riders</h1>
        <p class="pa-copy">
          Check each licence and plate against what was typed, then approve. Nobody can take a
          delivery until you do.
        </p>
      </div>
      <button class="segment-button" type="button" :disabled="loading" @click="load">
        {{ loading ? 'Refreshing…' : 'Refresh' }}
      </button>
    </div>

    <p v-if="errorMessage" class="auth-error">{{ errorMessage }}</p>

    <div class="pa-table-wrap surface-panel">
      <table class="pa-table">
        <thead>
          <tr>
            <th>Rider</th>
            <th>Licence & plate</th>
            <th>Status</th>
            <th>Applied</th>
            <th></th>
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
              <div class="rv-code">{{ rider.licenseNumber }}</div>
              <div class="rv-code">{{ rider.plateNumber }}</div>
              <button
                class="pa-link-button"
                type="button"
                :disabled="busyId === rider.id"
                @click="openDocuments(rider)"
              >
                View photos
              </button>
            </td>

            <td>
              <span
                class="pa-badge"
                :class="{ 'pa-badge--danger': rider.status === 'rejected' || rider.status === 'suspended' }"
              >
                {{ rider.status }}
              </span>
              <div v-if="rider.reviewNote" class="pa-slug rv-note">"{{ rider.reviewNote }}"</div>
              <div class="pa-slug">{{ rider.deliveriesCompleted }} delivered</div>
            </td>

            <td>
              <div class="pa-slug">{{ formatDate(rider.createdAt) }}</div>
              <div v-if="rider.reviewedAt" class="pa-slug">Reviewed {{ formatDate(rider.reviewedAt) }}</div>
            </td>

            <td>
              <label class="rv-note-field">
                <input
                  v-model="notes[rider.id]"
                  class="sheet-input"
                  type="text"
                  placeholder="Note to the rider"
                >
              </label>
              <div class="rv-actions">
                <button
                  v-if="rider.status !== 'approved'"
                  class="pa-link-button"
                  type="button"
                  :disabled="busyId === rider.id"
                  @click="decide(rider, 'approved')"
                >
                  Approve
                </button>
                <button
                  v-if="rider.status === 'pending'"
                  class="pa-link-button"
                  type="button"
                  :disabled="busyId === rider.id"
                  @click="decide(rider, 'rejected')"
                >
                  Reject
                </button>
                <button
                  v-if="rider.status === 'approved'"
                  class="pa-link-button"
                  type="button"
                  :disabled="busyId === rider.id"
                  @click="decide(rider, 'suspended')"
                >
                  Suspend
                </button>
              </div>
            </td>
          </tr>

          <tr v-if="!riders.length">
            <td colspan="5" class="pa-empty">No rider applications yet.</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Documents, shown from object URLs and dropped as soon as this closes. -->
    <div v-if="viewing" class="pa-modal-backdrop" @click.self="closeDocuments">
      <div class="pa-modal rv-modal">
        <h2 class="pa-modal__title">{{ viewing.name }}</h2>
        <p class="pa-modal__copy">
          Typed licence <strong>{{ viewing.licenseNumber }}</strong>, plate
          <strong>{{ viewing.plateNumber }}</strong>. Check both against the photos.
        </p>

        <p v-if="documentsError" class="auth-error">{{ documentsError }}</p>

        <div class="rv-docs">
          <figure class="rv-doc">
            <figcaption>Driver's licence</figcaption>
            <img v-if="documents.license" :src="documents.license" alt="Driver's licence">
            <div v-else class="rv-doc__loading">Loading…</div>
          </figure>
          <figure class="rv-doc">
            <figcaption>Plate</figcaption>
            <img v-if="documents.plate" :src="documents.plate" alt="Plate">
            <div v-else class="rv-doc__loading">Loading…</div>
          </figure>
        </div>

        <div class="pa-modal-actions">
          <button class="segment-button" type="button" @click="closeDocuments">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rv-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  letter-spacing: 0.04em;
}

.rv-note {
  max-width: 26ch;
  font-style: italic;
}

.rv-note-field {
  display: block;
  margin-bottom: 8px;
  min-width: 180px;
}

.rv-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.rv-modal {
  max-width: 860px;
}

.rv-docs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin: 18px 0;
}

@media (max-width: 700px) {
  .rv-docs {
    grid-template-columns: 1fr;
  }
}

.rv-doc figcaption {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-secondary);
}

/* Contained rather than cropped: a licence photographed at an angle must stay
   readable, and `cover` would cut the number off the edge. */
.rv-doc img {
  width: 100%;
  max-height: 420px;
  object-fit: contain;
  border-radius: 10px;
  background: #000;
}

.rv-doc__loading {
  display: grid;
  place-items: center;
  height: 220px;
  border-radius: 10px;
  background: var(--bg-canvas);
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
