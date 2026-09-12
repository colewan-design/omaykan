<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Check } from '@lucide/vue'
import { api, type Settings } from '../api'
import { dateTime } from '../format'
import PageHero from '../PageHero.vue'
import type { Operator } from '../api'

// The marketplace's own record, and the two policies it sets centrally.
//
// Three of these tabs save; two do not, and say so. A toggle that cannot
// actually change the thing it names is worse than no toggle — see
// PlatformSettingsController for why payments and operator accounts are not
// editable from here.

const props = defineProps<{ operator: Operator | null }>()

type Tab = 'store' | 'delivery' | 'notifications' | 'payments' | 'security'

const TABS: { key: Tab; label: string }[] = [
  { key: 'store', label: 'Store info' },
  { key: 'payments', label: 'Payments' },
  { key: 'delivery', label: 'Delivery' },
  { key: 'notifications', label: 'Notifications' },
  { key: 'security', label: 'Security' },
]

const tab = ref<Tab>('store')
const settings = ref<Settings | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    settings.value = (await api.settings()).settings
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load settings.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function save(patch: Record<string, unknown>) {
  saving.value = true
  error.value = ''
  saved.value = ''
  try {
    settings.value = (await api.saveSettings(patch)).settings
    saved.value = 'Saved.'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'That did not save.'
  } finally {
    saving.value = false
  }
}

function saveStore() {
  if (!settings.value) return
  save({
    name: settings.value.name,
    tagline: settings.value.tagline,
    description: settings.value.description,
    website: settings.value.website,
    address: settings.value.address,
    contactEmail: settings.value.contactEmail,
    contactPhone: settings.value.contactPhone,
  })
}

function saveDelivery() {
  if (!settings.value) return
  save({ delivery: settings.value.delivery })
}

function saveNotifications() {
  if (!settings.value) return
  save({ notifications: settings.value.notifications })
}

/** Pesos in the field, centavos on the wire — the schema stores centavos. */
function pesoModel(key: 'baseFeeCents' | 'freeDeliveryOverCents') {
  return {
    get: () => (settings.value ? settings.value.delivery[key] / 100 : 0),
    set: (value: number) => {
      if (settings.value) settings.value.delivery[key] = Math.round(Number(value || 0) * 100)
    },
  }
}

const NOTIFICATIONS: { key: keyof Settings['notifications']; label: string; note: string }[] = [
  { key: 'newOrder', label: 'New order placed', note: 'Every order across the marketplace.' },
  { key: 'newSeller', label: 'New seller signup', note: 'A shop has submitted its subscription for review.' },
  { key: 'lowStock', label: 'Low stock warnings', note: 'A tracked product falls to its reorder level.' },
  { key: 'weeklySummary', label: 'Weekly summary', note: 'Monday morning digest of the week just gone.' },
]
</script>

<template>
  <PageHero title="Settings" subtitle="Configure the marketplace and the policies that apply across every shop." />

  <div class="page">
    <div class="adm-card set__panel">
      <nav class="adm-tabs" aria-label="Settings sections">
        <button
          v-for="item in TABS"
          :key="item.key"
          type="button"
          class="adm-tab"
          :class="{ 'adm-tab--on': tab === item.key }"
          @click="tab = item.key"
        >
          {{ item.label }}
        </button>
      </nav>

      <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>
      <p v-else-if="loading" class="adm-note">Loading settings…</p>

      <div v-else-if="settings" class="set__body">
        <p v-if="saved" class="set__saved"><Check :size="14" aria-hidden="true" /> {{ saved }}</p>

        <!-- ── Store info ─────────────────────────────────────────────── -->
        <form v-if="tab === 'store'" class="set__form" @submit.prevent="saveStore">
          <h2 class="adm-h2">Marketplace information</h2>

          <div class="set__grid">
            <label class="set__label">
              Name
              <span class="adm-field"><input v-model="settings.name" type="text" required /></span>
            </label>

            <label class="set__label">
              Tagline
              <span class="adm-field"><input v-model="settings.tagline" type="text" /></span>
            </label>
          </div>

          <label class="set__label">
            Description
            <textarea v-model="settings.description" class="set__textarea" rows="4"></textarea>
          </label>

          <div class="set__grid">
            <label class="set__label">
              Website
              <span class="adm-field"><input v-model="settings.website" type="url" placeholder="https://" /></span>
            </label>

            <label class="set__label">
              Address
              <span class="adm-field"><input v-model="settings.address" type="text" /></span>
            </label>

            <label class="set__label">
              Contact email
              <span class="adm-field"><input v-model="settings.contactEmail" type="email" /></span>
            </label>

            <label class="set__label">
              Contact number
              <span class="adm-field"><input v-model="settings.contactPhone" type="tel" /></span>
            </label>
          </div>

          <div class="set__actions">
            <button type="submit" class="adm-btn" :disabled="saving">{{ saving ? 'Saving…' : 'Save changes' }}</button>
            <small v-if="settings.updatedAt" class="set__stamp">Last changed {{ dateTime(settings.updatedAt) }}</small>
          </div>
        </form>

        <!-- ── Delivery ───────────────────────────────────────────────── -->
        <form v-else-if="tab === 'delivery'" class="set__form" @submit.prevent="saveDelivery">
          <h2 class="adm-h2">Delivery policy</h2>
          <p class="set__hint">
            Applies to every shop on the marketplace. The fee actually charged is recomputed from the
            shop's own pin at checkout, so this is the floor rather than the final figure.
          </p>

          <div class="set__grid">
            <label class="set__label">
              Base fee (₱)
              <span class="adm-field">
                <input
                  type="number"
                  min="0"
                  step="1"
                  :value="pesoModel('baseFeeCents').get()"
                  @input="pesoModel('baseFeeCents').set(Number(($event.target as HTMLInputElement).value))"
                />
              </span>
            </label>

            <label class="set__label">
              Free delivery over (₱)
              <span class="adm-field">
                <input
                  type="number"
                  min="0"
                  step="1"
                  :value="pesoModel('freeDeliveryOverCents').get()"
                  @input="pesoModel('freeDeliveryOverCents').set(Number(($event.target as HTMLInputElement).value))"
                />
              </span>
              <small>Zero means never.</small>
            </label>

            <label class="set__label">
              Maximum distance (km)
              <span class="adm-field">
                <input v-model.number="settings.delivery.maxDistanceKm" type="number" min="0" max="100" step="0.5" />
              </span>
            </label>
          </div>

          <div class="set__actions">
            <button type="submit" class="adm-btn" :disabled="saving">{{ saving ? 'Saving…' : 'Save changes' }}</button>
          </div>
        </form>

        <!-- ── Notifications ──────────────────────────────────────────── -->
        <form v-else-if="tab === 'notifications'" class="set__form" @submit.prevent="saveNotifications">
          <h2 class="adm-h2">What the operator hears about</h2>

          <ul class="set__toggles">
            <li v-for="item in NOTIFICATIONS" :key="item.key">
              <label>
                <input v-model="settings.notifications[item.key]" type="checkbox" />
                <span>
                  <strong>{{ item.label }}</strong>
                  <small>{{ item.note }}</small>
                </span>
              </label>
            </li>
          </ul>

          <div class="set__actions">
            <button type="submit" class="adm-btn" :disabled="saving">{{ saving ? 'Saving…' : 'Save changes' }}</button>
          </div>
        </form>

        <!-- ── Payments (read-only, and says so) ──────────────────────── -->
        <section v-else-if="tab === 'payments'" class="set__form">
          <h2 class="adm-h2">Payments</h2>
          <p class="set__hint">
            What the marketplace accepts is decided by the payment integration's own credentials on
            the server, not by this screen. A switch here could not actually turn a tender off, so
            there isn't one — change these on the server and they change here.
          </p>

          <dl class="set__facts">
            <div><dt>Cash on delivery</dt><dd>Accepted — settled by the rider at the door.</dd></div>
            <div><dt>GCash</dt><dd>Accepted — reference recorded against the order.</dd></div>
            <div><dt>Card</dt><dd>Counter only, through the shop's own terminal.</dd></div>
            <div><dt>Currency</dt><dd>Philippine peso (₱), stored as centavos throughout.</dd></div>
          </dl>
        </section>

        <!-- ── Security (read-only, and says so) ──────────────────────── -->
        <section v-else class="set__form">
          <h2 class="adm-h2">Security</h2>

          <dl class="set__facts">
            <div><dt>Signed in as</dt><dd>{{ props.operator?.name }} · {{ props.operator?.email }}</dd></div>
            <div><dt>Last sign-in</dt><dd>{{ dateTime(props.operator?.lastLoginAt ?? null) }}</dd></div>
            <div>
              <dt>Sessions</dt>
              <dd>One at a time — signing in revokes every other token on this account.</dd>
            </div>
            <div>
              <dt>Operator accounts</dt>
              <dd>
                Created from the server console with <code>php artisan platform-admin:create</code>.
                There is deliberately no way to mint one from this portal: a portal that can create
                its own operators is a portal where one stolen session is permanent.
              </dd>
            </div>
          </dl>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 16px var(--adm-gutter) 0;
}

.set__panel {
  padding: 0 0 8px;
}

.adm-tabs {
  padding: 4px 14px 0;
}

.set__body {
  padding: 18px;
}

.set__form {
  display: grid;
  gap: 14px;
  max-width: 760px;
}

.set__hint {
  margin: 0;
  max-width: 66ch;
  color: var(--sf-muted);
  font-size: 13px;
  line-height: 1.55;
}

.set__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.set__label {
  display: grid;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--sf-muted);
}

.set__label small {
  color: var(--sf-faint);
  font-weight: 500;
}

.set__textarea {
  padding: 10px 12px;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: #fff;
  color: var(--sf-ink);
  font: inherit;
  font-size: 14px;
  resize: vertical;
}

.set__textarea:focus {
  outline: none;
  border-color: var(--sf-forest-soft);
  box-shadow: 0 0 0 3px rgba(43, 63, 51, 0.12);
}

.set__toggles {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.set__toggles label {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 11px 12px;
  border-radius: 10px;
  cursor: pointer;
}

.set__toggles label:hover {
  background: var(--sf-sand);
}

.set__toggles input {
  margin-top: 2px;
  width: 16px;
  height: 16px;
  accent-color: var(--sf-forest);
}

.set__toggles span {
  display: grid;
  gap: 1px;
}

.set__toggles strong {
  font-size: 13.5px;
  font-weight: 600;
}

.set__toggles small {
  color: var(--sf-muted);
  font-size: 12px;
}

.set__facts {
  display: grid;
  gap: 0;
  margin: 0;
}

.set__facts > div {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 14px;
  padding: 11px 0;
  border-bottom: 1px solid var(--sf-rule);
}

.set__facts > div:last-child {
  border-bottom: none;
}

.set__facts dt {
  color: var(--sf-muted);
  font-size: 12.5px;
  font-weight: 700;
}

.set__facts dd {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.5;
}

.set__facts code {
  padding: 1px 5px;
  border-radius: 5px;
  background: var(--sf-sand);
  font-size: 12.5px;
}

.set__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.set__stamp {
  color: var(--sf-faint);
  font-size: 12px;
}

.set__saved {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 14px;
  padding: 7px 11px;
  border-radius: 9px;
  background: rgba(12, 163, 12, 0.1);
  color: #0a6b0a;
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 680px) {
  .set__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .set__facts > div {
    grid-template-columns: minmax(0, 1fr);
    gap: 3px;
  }
}

@media (max-width: 560px) {
  .page {
    padding: 14px 16px 0;
  }
}
</style>
