<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Banknote,
  Bell,
  Check,
  Clock3,
  CreditCard,
  Globe2,
  Mail,
  MapPin,
  Phone,
  ShieldCheck,
  Store,
  Truck,
} from '@lucide/vue'
import { api, type Operator, type Settings } from '../api'
import { dateTime, pesos } from '../format'
import PageHero from '../PageHero.vue'

// The marketplace's own record, and the policies it sets centrally.
//
// Four of these tabs save; two do not, and say so. A toggle that cannot
// actually change the thing it names is worse than no toggle — see
// PlatformSettingsController for why payments and operator accounts are not
// editable from here, and config/billing.php for why billing enforcement is
// not either. Those two wear a badge rather than only a paragraph, so the rule
// is visible before you go hunting for the switch that isn't there.
//
// The kit is the portal's own: PageHero, .adm-card, .adm-tabs, .adm-field,
// .adm-btn and the --sf-* palette out of admin.css; the section chips are
// AnalyticsView's, so the two read as one product. Nothing here comes from the
// seller workspace's settings screen — that is a different surface with a
// different palette, and borrowing from it is what put this page wrong before.

const props = defineProps<{ operator: Operator | null }>()

type Tab = 'store' | 'payments' | 'delivery' | 'subscription' | 'notifications' | 'security'

const TABS: { key: Tab; label: string }[] = [
  { key: 'store', label: 'Store info' },
  { key: 'payments', label: 'Payments' },
  { key: 'delivery', label: 'Delivery' },
  { key: 'subscription', label: 'Subscription' },
  { key: 'notifications', label: 'Notifications' },
  { key: 'security', label: 'Security' },
]

const NOTIFICATIONS: { key: keyof Settings['notifications']; label: string; note: string }[] = [
  { key: 'newOrder', label: 'New order placed', note: 'Every order across the marketplace.' },
  { key: 'newSeller', label: 'New seller signup', note: 'A shop has submitted its subscription for review.' },
  { key: 'lowStock', label: 'Low stock warnings', note: 'A tracked product falls to its reorder level.' },
  { key: 'weeklySummary', label: 'Weekly summary', note: 'Monday morning digest of the week just gone.' },
]

const tab = ref<Tab>('store')
const settings = ref<Settings | null>(null)
const loading = ref(true)
const saving = ref('')
const error = ref('')
const saved = ref('')

const descriptionCount = computed(() => settings.value?.description?.length ?? 0)

/** The delivery policy in one line, so the effect is legible before the edit. */
const deliveryReadout = computed(() => {
  const policy = settings.value?.delivery
  if (!policy) return ''

  const free = policy.freeDeliveryOverCents > 0
    ? `free over ${pesos(policy.freeDeliveryOverCents)}`
    : 'no free-delivery threshold'

  return `${pesos(policy.baseFeeCents)} base · ${free} · within ${policy.maxDistanceKm} km`
})

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

async function save(section: string, patch: Record<string, unknown>) {
  saving.value = section
  error.value = ''
  saved.value = ''
  try {
    settings.value = (await api.saveSettings(patch)).settings
    saved.value = `${section} saved.`
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'That did not save.'
  } finally {
    saving.value = ''
  }
}

function saveStore() {
  if (!settings.value) return
  save('Marketplace information', {
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
  save('Delivery policy', { delivery: settings.value.delivery })
}

function saveNotifications() {
  if (!settings.value) return
  save('Notification preferences', { notifications: settings.value.notifications })
}

function savePlan() {
  if (!settings.value) return
  save('Merchant subscription', { plan: { amountCents: settings.value.plan.amountCents } })
}

/** Same pesos-in, centavos-out rule as the delivery fees. */
const planPesos = {
  get: () => (settings.value ? settings.value.plan.amountCents / 100 : 0),
  set: (value: number) => {
    if (settings.value) settings.value.plan.amountCents = Math.round(Number(value || 0) * 100)
  },
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
</script>

<template>
  <PageHero title="Settings" subtitle="Configure the marketplace and the policies that apply across every shop.">
    <template #tools>
      <span v-if="settings?.updatedAt" class="set__heroStamp">
        <Clock3 :size="14" aria-hidden="true" />Last changed {{ dateTime(settings.updatedAt) }}
      </span>
    </template>
  </PageHero>

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
        <!-- ── Store info ─────────────────────────────────────────────── -->
        <form v-if="tab === 'store'" class="set__form" @submit.prevent="saveStore">
          <header class="set__head">
            <span class="set__icon set__icon--green"><Store :size="18" aria-hidden="true" /></span>
            <div>
              <h2 class="adm-h2">Marketplace information</h2>
              <p>How Omaykan names itself to shoppers, and where they reach it.</p>
            </div>
          </header>

          <div class="set__grid">
            <label class="set__label">
              <span>Name <b aria-hidden="true">*</b></span>
              <span class="adm-field"><input v-model="settings.name" type="text" maxlength="120" required /></span>
            </label>

            <label class="set__label">
              Tagline
              <span class="adm-field"><input v-model="settings.tagline" type="text" maxlength="160" /></span>
            </label>
          </div>

          <label class="set__label">
            <span class="set__labelRow">Description <small>{{ descriptionCount }}/2000</small></span>
            <textarea v-model="settings.description" class="set__textarea" rows="4" maxlength="2000"></textarea>
          </label>

          <div class="set__grid">
            <label class="set__label">
              Website
              <span class="adm-field"><Globe2 :size="15" aria-hidden="true" /><input v-model="settings.website" type="url" placeholder="https://" /></span>
            </label>

            <label class="set__label">
              Address
              <span class="adm-field"><MapPin :size="15" aria-hidden="true" /><input v-model="settings.address" type="text" maxlength="250" /></span>
            </label>

            <label class="set__label">
              Contact email
              <span class="adm-field"><Mail :size="15" aria-hidden="true" /><input v-model="settings.contactEmail" type="email" maxlength="160" /></span>
            </label>

            <label class="set__label">
              Contact number
              <span class="adm-field"><Phone :size="15" aria-hidden="true" /><input v-model="settings.contactPhone" type="tel" maxlength="40" /></span>
            </label>
          </div>

          <div class="set__actions">
            <button type="submit" class="adm-btn" :disabled="Boolean(saving)">{{ saving === 'Marketplace information' ? 'Saving…' : 'Save changes' }}</button>
            <p v-if="saved" class="set__saved"><Check :size="14" aria-hidden="true" /> {{ saved }}</p>
          </div>
        </form>

        <!-- ── Payments (read-only, and says so) ──────────────────────── -->
        <section v-else-if="tab === 'payments'" class="set__form">
          <header class="set__head">
            <span class="set__icon set__icon--amber"><Banknote :size="18" aria-hidden="true" /></span>
            <div>
              <h2 class="adm-h2">Payments</h2>
              <p>What the marketplace accepts, and in what currency.</p>
            </div>
            <span class="set__badge">Set on the server</span>
          </header>

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

        <!-- ── Delivery ───────────────────────────────────────────────── -->
        <form v-else-if="tab === 'delivery'" class="set__form" @submit.prevent="saveDelivery">
          <header class="set__head">
            <span class="set__icon set__icon--green"><Truck :size="18" aria-hidden="true" /></span>
            <div>
              <h2 class="adm-h2">Delivery policy</h2>
              <p>The marketplace-wide floor every shop's checkout starts from.</p>
            </div>
          </header>

          <p class="set__readout">{{ deliveryReadout }}</p>

          <p class="set__hint">
            Applies to every shop on the marketplace. The fee actually charged is recomputed from the
            shop's own pin at checkout, so this is the floor rather than the final figure.
          </p>

          <div class="set__grid set__grid--three">
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
            <button type="submit" class="adm-btn" :disabled="Boolean(saving)">{{ saving === 'Delivery policy' ? 'Saving…' : 'Save changes' }}</button>
            <p v-if="saved" class="set__saved"><Check :size="14" aria-hidden="true" /> {{ saved }}</p>
          </div>
        </form>

        <!-- ── Subscription ───────────────────────────────────────────── -->
        <form v-else-if="tab === 'subscription'" class="set__form" @submit.prevent="savePlan">
          <header class="set__head">
            <span class="set__icon set__icon--amber"><CreditCard :size="18" aria-hidden="true" /></span>
            <div>
              <h2 class="adm-h2">Merchant subscription</h2>
              <p>What a new shop's subscription is recorded at when it signs up.</p>
            </div>
            <span class="set__badge" :class="{ 'set__badge--on': settings.billingEnforced }">
              {{ settings.billingEnforced ? 'Enforced' : 'Early access' }}
            </span>
          </header>

          <p class="set__readout">{{ pesos(settings.plan.amountCents) }} / month · plan {{ settings.plan.id }}</p>

          <p class="set__hint">
            Changing it re-prices nobody who has already signed up — each shop keeps the amount it
            joined at. Nothing charges this yet: Omaykan is free during early access.
          </p>

          <div class="set__grid set__grid--three">
            <label class="set__label">
              Monthly price (₱)
              <span class="adm-field">
                <input
                  type="number"
                  min="0"
                  max="100000"
                  step="1"
                  :value="planPesos.get()"
                  @input="planPesos.set(Number(($event.target as HTMLInputElement).value))"
                />
              </span>
            </label>
          </div>

          <dl class="set__facts">
            <div>
              <dt>Enforcement</dt>
              <dd v-if="settings.billingEnforced">
                On — a shop with a lapsed subscription can read its records but not sell, and its
                storefront is closed.
              </dd>
              <dd v-else>
                Off — subscriptions are recorded and never block anyone. Suspension still applies.
                Set <code>BILLING_ENFORCE</code> on the server to change this.
              </dd>
            </div>
          </dl>

          <div class="set__actions">
            <button type="submit" class="adm-btn" :disabled="Boolean(saving)">{{ saving === 'Merchant subscription' ? 'Saving…' : 'Save changes' }}</button>
            <p v-if="saved" class="set__saved"><Check :size="14" aria-hidden="true" /> {{ saved }}</p>
          </div>
        </form>

        <!-- ── Notifications ──────────────────────────────────────────── -->
        <form v-else-if="tab === 'notifications'" class="set__form" @submit.prevent="saveNotifications">
          <header class="set__head">
            <span class="set__icon set__icon--blue"><Bell :size="18" aria-hidden="true" /></span>
            <div>
              <h2 class="adm-h2">What the operator hears about</h2>
              <p>Which marketplace events are worth an interruption.</p>
            </div>
          </header>

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
            <button type="submit" class="adm-btn" :disabled="Boolean(saving)">{{ saving === 'Notification preferences' ? 'Saving…' : 'Save changes' }}</button>
            <p v-if="saved" class="set__saved"><Check :size="14" aria-hidden="true" /> {{ saved }}</p>
          </div>
        </form>

        <!-- ── Security (read-only, and says so) ──────────────────────── -->
        <section v-else class="set__form">
          <header class="set__head">
            <span class="set__icon set__icon--green"><ShieldCheck :size="18" aria-hidden="true" /></span>
            <div>
              <h2 class="adm-h2">Security</h2>
              <p>This operator account, and how portal access is granted.</p>
            </div>
            <span class="set__badge">Read-only</span>
          </header>

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

.set__heroStamp {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--sf-ink);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
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

/* ── Section header ──────────────────────────────────────────────────── */

/*
 * The chip palette is AnalyticsView's, restated rather than imported for the
 * same reason admin.css restates the storefront's: these are two scoped blocks
 * in two files. Keep them in step.
 */
.set__head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--sf-rule);
}

.set__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
}

.set__icon--green { background: #e4f3e8; color: #2d7547; }
.set__icon--amber { background: #fff0dc; color: #b86c0b; }
.set__icon--blue { background: #e5efff; color: #176bd6; }

.set__head > div {
  flex: 1;
  min-width: 0;
}

.set__head p {
  margin: 3px 0 0;
  max-width: 60ch;
  color: var(--sf-muted);
  font-size: 12.5px;
  line-height: 1.45;
}

/* Says "there is no switch here" before the paragraph has to. */
.set__badge {
  display: inline-flex;
  flex: none;
  align-items: center;
  margin-top: 2px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--sf-sand);
  color: var(--sf-muted);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.set__badge--on {
  background: rgba(12, 163, 12, 0.13);
  color: #0a6b0a;
}

/* ── Body ────────────────────────────────────────────────────────────── */

/* The setting as it currently reads, before you edit the fields under it. */
.set__readout {
  margin: 0;
  padding: 11px 13px;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: var(--sf-sand);
  color: var(--sf-ink);
  font-family: var(--sf-serif);
  font-size: 15px;
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

.set__grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

/*
 * align-content, because a label carrying a hint under its field is one grid
 * row taller than its neighbours. Stretched, that extra row pushes its input
 * out of line with the rest of the row; packed to the start, the fields align.
 */
.set__label {
  display: grid;
  align-content: start;
  gap: 5px;
  min-width: 0;
  color: var(--sf-muted);
  font-size: 12.5px;
  font-weight: 600;
}

.set__labelRow {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.set__label b {
  color: var(--sf-clay);
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
  font-weight: 600;
}

.set__facts dd {
  margin: 0;
  color: var(--sf-ink);
  font-size: 13.5px;
  line-height: 1.5;
}

.set__facts code {
  padding: 1px 5px;
  border-radius: 5px;
  background: var(--sf-sand);
  font-size: 12.5px;
}

/* ── Actions ─────────────────────────────────────────────────────────── */

.set__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding-top: 4px;
}

.set__saved {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #0a6b0a;
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 760px) {
  .page {
    padding: 14px 16px 0;
  }

  .set__body {
    padding: 14px;
  }

  .set__grid,
  .set__grid--three {
    grid-template-columns: minmax(0, 1fr);
  }

  .set__head {
    flex-wrap: wrap;
  }

  .set__facts > div {
    grid-template-columns: minmax(0, 1fr);
    gap: 3px;
  }
}
</style>
