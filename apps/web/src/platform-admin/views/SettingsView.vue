<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Banknote,
  Bell,
  Check,
  CircleDollarSign,
  CreditCard,
  Globe2,
  Info,
  LockKeyhole,
  Mail,
  MapPin,
  PackageCheck,
  Phone,
  ReceiptText,
  Settings2,
  ShieldCheck,
  Store,
  Truck,
  UserRoundCog,
} from '@lucide/vue'
import { api, type Operator, type Settings } from '../api'
import { dateTime } from '../format'

const props = defineProps<{ operator: Operator | null }>()

type Tab = 'general' | 'profile' | 'billing' | 'delivery' | 'notifications' | 'security'

const TABS = [
  { key: 'general' as const, label: 'General', icon: Settings2 },
  { key: 'profile' as const, label: 'Marketplace profile', icon: Store },
  { key: 'billing' as const, label: 'Billing', icon: CreditCard },
  { key: 'delivery' as const, label: 'Delivery', icon: Truck },
  { key: 'notifications' as const, label: 'Notifications', icon: Bell },
  { key: 'security' as const, label: 'Security', icon: ShieldCheck },
]

const NOTIFICATIONS: { key: keyof Settings['notifications']; label: string; note: string }[] = [
  { key: 'newOrder', label: 'New order placed', note: 'Every order across the marketplace.' },
  { key: 'newSeller', label: 'New seller signup', note: 'A shop has submitted its subscription for review.' },
  { key: 'lowStock', label: 'Low stock warnings', note: 'A tracked product falls to its reorder level.' },
  { key: 'weeklySummary', label: 'Weekly summary', note: 'A Monday morning digest of the week just gone.' },
]

const tab = ref<Tab>('general')
const settings = ref<Settings | null>(null)
const loading = ref(true)
const saving = ref('')
const error = ref('')
const saved = ref('')

const descriptionCount = computed(() => settings.value?.description?.length ?? 0)
const isGeneral = computed(() => tab.value === 'general')

function visible(section: Exclude<Tab, 'general'>): boolean {
  return tab.value === 'general' || tab.value === section
}

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

function saveProfile() {
  if (!settings.value) return
  void save('Marketplace profile', {
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
  void save('Delivery policy', { delivery: settings.value.delivery })
}

function saveNotifications() {
  if (!settings.value) return
  void save('Notification preferences', { notifications: settings.value.notifications })
}

function savePlan() {
  if (!settings.value) return
  void save('Merchant plan', { plan: { amountCents: settings.value.plan.amountCents } })
}

const planPesos = {
  get: () => (settings.value ? settings.value.plan.amountCents / 100 : 0),
  set: (value: number) => {
    if (settings.value) settings.value.plan.amountCents = Math.round(Number(value || 0) * 100)
  },
}

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
  <section class="settings-page">
    <header class="settings-heading">
      <h1>Settings</h1>
      <p>Manage the marketplace profile, billing, delivery, notifications, and operator access.</p>
    </header>

    <nav class="settings-tabs" aria-label="Settings sections">
      <button
        v-for="item in TABS"
        :key="item.key"
        type="button"
        :class="{ 'settings-tab--active': tab === item.key }"
        :aria-current="tab === item.key ? 'page' : undefined"
        @click="tab = item.key"
      >
        <component :is="item.icon" :size="17" />
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <div v-if="error" class="settings-alert settings-alert--error" role="alert">{{ error }}</div>
    <div v-else-if="saved" class="settings-alert settings-alert--success" role="status"><Check :size="15" /> {{ saved }}</div>

    <div v-if="loading" class="settings-loading">
      <span></span><span></span><span></span>
      <p>Loading marketplace settings...</p>
    </div>

    <div v-else-if="settings" class="settings-grid" :class="{ 'settings-grid--focused': !isGeneral }">
      <form v-show="visible('profile')" class="set-card set-card--profile" @submit.prevent="saveProfile">
        <header class="set-card__header">
          <div class="set-card__title">
            <span class="set-card__icon"><Store :size="20" /></span>
            <div><h2>Marketplace profile</h2><p>Shown across storefronts and marketplace communications.</p></div>
          </div>
          <span class="set-status"><span></span> Public</span>
        </header>

        <div class="profile-intro">
          <span class="profile-mark"><Store :size="28" /></span>
          <div><strong>{{ settings.name || 'Omaykan' }}</strong><small>{{ settings.tagline || 'Local commerce, connected.' }}</small></div>
        </div>

        <div class="field-grid">
          <label class="set-field"><span>Marketplace name <b>*</b></span><input v-model="settings.name" type="text" maxlength="120" required></label>
          <label class="set-field"><span>Tagline</span><input v-model="settings.tagline" type="text" maxlength="160"></label>
        </div>

        <label class="set-field">
          <span>Description <small>{{ descriptionCount }}/2000</small></span>
          <textarea v-model="settings.description" rows="3" maxlength="2000"></textarea>
        </label>

        <div class="field-grid field-grid--contact">
          <label class="set-field"><span>Website</span><span class="set-input"><Globe2 :size="15" /><input v-model="settings.website" type="url" placeholder="https://"></span></label>
          <label class="set-field"><span>Contact email</span><span class="set-input"><Mail :size="15" /><input v-model="settings.contactEmail" type="email" maxlength="160"></span></label>
          <label class="set-field"><span>Contact number</span><span class="set-input"><Phone :size="15" /><input v-model="settings.contactPhone" type="tel" maxlength="40"></span></label>
          <label class="set-field"><span>Marketplace address</span><span class="set-input"><MapPin :size="15" /><input v-model="settings.address" type="text" maxlength="250"></span></label>
        </div>

        <footer class="set-card__footer">
          <small v-if="settings.updatedAt">Last changed {{ dateTime(settings.updatedAt) }}</small>
          <button class="set-button" type="submit" :disabled="Boolean(saving)">{{ saving === 'Marketplace profile' ? 'Saving...' : 'Save profile' }}</button>
        </footer>
      </form>

      <form v-show="visible('billing')" class="set-card set-card--billing" @submit.prevent="savePlan">
        <header class="set-card__header">
          <div class="set-card__title">
            <span class="set-card__icon set-card__icon--amber"><CreditCard :size="20" /></span>
            <div><h2>Subscription & billing</h2><p>Set the price recorded for new merchant subscriptions.</p></div>
          </div>
          <span class="set-status"><span></span>{{ settings.billingEnforced ? 'Enforced' : 'Early access' }}</span>
        </header>

        <div class="plan-summary">
          <span class="plan-summary__icon"><CircleDollarSign :size="23" /></span>
          <div><small>{{ settings.plan.id }}</small><strong>₱{{ planPesos.get().toLocaleString('en-PH') }}<em>/ month</em></strong></div>
        </div>

        <label class="set-field"><span>Monthly price for new sellers</span><span class="set-input set-input--money"><b>₱</b><input :value="planPesos.get()" type="number" min="0" max="100000" step="1" @input="planPesos.set(Number(($event.target as HTMLInputElement).value))"></span></label>

        <div class="billing-note"><Info :size="16" /><span>Existing sellers keep the amount they joined at. Billing enforcement is configured on the server.</span></div>

        <button class="set-button set-button--wide" type="submit" :disabled="Boolean(saving)">{{ saving === 'Merchant plan' ? 'Saving...' : 'Save merchant plan' }}</button>

        <div class="accepted-payments">
          <h3>Payment infrastructure</h3>
          <div><Banknote :size="16" /><span><strong>Cash on delivery</strong><small>Settled by the rider at the door</small></span><Check :size="15" /></div>
          <div><ReceiptText :size="16" /><span><strong>GCash</strong><small>Reference recorded against the order</small></span><Check :size="15" /></div>
          <div><CreditCard :size="16" /><span><strong>Card</strong><small>Handled through each shop's terminal</small></span><Check :size="15" /></div>
        </div>
      </form>

      <form v-show="visible('delivery')" class="set-card set-card--delivery" @submit.prevent="saveDelivery">
        <header class="set-card__header">
          <div class="set-card__title">
            <span class="set-card__icon"><Truck :size="20" /></span>
            <div><h2>Delivery policy</h2><p>Marketplace-wide delivery defaults applied at checkout.</p></div>
          </div>
        </header>

        <div class="operational-fields">
          <label class="set-field"><span>Base fee</span><span class="set-input set-input--money"><b>₱</b><input :value="pesoModel('baseFeeCents').get()" type="number" min="0" step="1" @input="pesoModel('baseFeeCents').set(Number(($event.target as HTMLInputElement).value))"></span></label>
          <label class="set-field"><span>Free delivery over</span><span class="set-input set-input--money"><b>₱</b><input :value="pesoModel('freeDeliveryOverCents').get()" type="number" min="0" step="1" @input="pesoModel('freeDeliveryOverCents').set(Number(($event.target as HTMLInputElement).value))"></span></label>
          <label class="set-field"><span>Maximum distance</span><span class="set-input"><MapPin :size="15" /><input v-model.number="settings.delivery.maxDistanceKm" type="number" min="0" max="100" step="0.5"><small>km</small></span></label>
        </div>

        <div class="set-card__footer set-card__footer--plain">
          <small>Final fees are recomputed from each shop's location.</small>
          <button class="set-button" type="submit" :disabled="Boolean(saving)">{{ saving === 'Delivery policy' ? 'Saving...' : 'Save delivery policy' }}</button>
        </div>
      </form>

      <section v-show="visible('security')" class="set-card set-card--security">
        <header class="set-card__header">
          <div class="set-card__title">
            <span class="set-card__icon"><ShieldCheck :size="20" /></span>
            <div><h2>Security & access</h2><p>Your operator account and platform access policy.</p></div>
          </div>
          <span class="set-owner"><LockKeyhole :size="13" /> Operator account</span>
        </header>

        <div class="security-grid">
          <article><span><UserRoundCog :size="19" /></span><div><strong>Signed in as</strong><small>{{ props.operator?.name || 'Admin' }} · {{ props.operator?.email || '—' }}</small></div></article>
          <article><span><LockKeyhole :size="19" /></span><div><strong>Last sign-in</strong><small>{{ dateTime(props.operator?.lastLoginAt ?? null) }}</small></div></article>
          <article><span><ShieldCheck :size="19" /></span><div><strong>Session protection</strong><small>A new sign-in revokes every other token.</small></div></article>
          <article><span><PackageCheck :size="19" /></span><div><strong>Operator provisioning</strong><small>Accounts are created securely from the server console.</small></div></article>
        </div>
      </section>

      <form v-show="visible('notifications')" class="set-card set-card--notifications" @submit.prevent="saveNotifications">
        <header class="set-card__header">
          <div class="set-card__title">
            <span class="set-card__icon set-card__icon--blue"><Bell :size="20" /></span>
            <div><h2>Notification preferences</h2><p>Choose which marketplace events need operator attention.</p></div>
          </div>
        </header>

        <div class="notification-grid">
          <label v-for="item in NOTIFICATIONS" :key="item.key" class="notification-row">
            <span class="notification-row__copy"><strong>{{ item.label }}</strong><small>{{ item.note }}</small></span>
            <span class="switch"><input v-model="settings.notifications[item.key]" type="checkbox"><span></span></span>
          </label>
        </div>

        <div class="set-card__footer set-card__footer--plain">
          <small>Preferences apply to the operator portal.</small>
          <button class="set-button" type="submit" :disabled="Boolean(saving)">{{ saving === 'Notification preferences' ? 'Saving...' : 'Save notifications' }}</button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.settings-page {
  display: grid;
  gap: 14px;
  padding: 18px var(--adm-gutter) 26px;
  color: #17221c;
}

.settings-heading h1 {
  margin: 0;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: clamp(28px, 2.2vw, 36px);
  line-height: 1.05;
  letter-spacing: -.02em;
}

.settings-heading p { margin: 6px 0 0; color: #65706a; font-size: 13px; }

.settings-tabs {
  display: flex;
  gap: 4px;
  padding: 5px;
  overflow-x: auto;
  border: 1px solid #e2ddd3;
  border-radius: 12px;
  background: rgba(255, 255, 255, .66);
  scrollbar-width: none;
}

.settings-tabs button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 0 0 auto;
  min-height: 38px;
  padding: 0 16px;
  border: 0;
  border-radius: 8px;
  color: #4e5852;
  background: transparent;
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.settings-tabs button:hover { background: #f4f1eb; }
.settings-tabs button.settings-tab--active,
.settings-tabs button.settings-tab--active:hover { color: #fff; background: #17613d; box-shadow: 0 3px 9px rgba(18, 80, 49, .16); }

.settings-alert { display: flex; align-items: center; gap: 7px; padding: 10px 13px; border-radius: 9px; font-size: 12px; }
.settings-alert--error { color: #912d2d; background: #fff0ef; border: 1px solid #f4d4d1; }
.settings-alert--success { color: #17643b; background: #eaf5ec; border: 1px solid #d3e9d7; }

.settings-loading { display: grid; min-height: 420px; place-content: center; grid-template-columns: repeat(3, 8px); gap: 6px; color: #7a827d; text-align: center; }
.settings-loading span { width: 8px; height: 8px; border-radius: 50%; background: #26784d; animation: pulse 1s ease-in-out infinite; }
.settings-loading span:nth-child(2) { animation-delay: .12s; }
.settings-loading span:nth-child(3) { animation-delay: .24s; }
.settings-loading p { grid-column: 1 / -1; margin: 8px -80px 0; font-size: 12px; }

.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.32fr) minmax(340px, .88fr);
  grid-template-areas:
    'profile billing'
    'delivery security'
    'notifications notifications';
  gap: 12px;
  align-items: start;
}

.settings-grid--focused { grid-template-columns: minmax(0, 1fr); grid-template-areas: none; }
.settings-grid--focused .set-card { grid-area: auto; }
.set-card--profile { grid-area: profile; }
.set-card--billing { grid-area: billing; }
.set-card--delivery { grid-area: delivery; }
.set-card--security { grid-area: security; }
.set-card--notifications { grid-area: notifications; }

.set-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #e1ddd4;
  border-radius: 14px;
  background: rgba(255, 255, 255, .78);
  box-shadow: 0 5px 18px rgba(39, 49, 43, .045);
}

.set-card__header {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 11px 14px;
  border-bottom: 1px solid #ebe7df;
  box-sizing: border-box;
}

.set-card__title { display: flex; align-items: center; gap: 10px; min-width: 0; }
.set-card__icon { display: grid; width: 34px; height: 34px; flex: 0 0 auto; place-items: center; border-radius: 10px; color: #1c7145; background: #e8f3e9; }
.set-card__icon--amber { color: #ad6700; background: #fff2d9; }
.set-card__icon--blue { color: #276db3; background: #e9f2fc; }
.set-card__title div { min-width: 0; }
.set-card__title h2 { margin: 0; font-family: Georgia, 'Times New Roman', serif; font-size: 16px; line-height: 1.2; }
.set-card__title p { margin: 2px 0 0; color: #747d77; font-size: 10.5px; line-height: 1.35; }
.set-status, .set-owner { display: inline-flex; align-items: center; gap: 6px; flex: 0 0 auto; padding: 5px 9px; border-radius: 999px; font-size: 9.5px; font-weight: 700; }
.set-status { color: #1e7547; background: #e8f4e9; }
.set-status > span { width: 7px; height: 7px; border-radius: 50%; background: #23a85d; }
.set-owner { color: #805d0d; background: #fff4d8; }

.profile-intro { display: flex; align-items: center; gap: 11px; margin: 14px 14px 0; padding: 11px; border: 1px dashed #d9d4ca; border-radius: 11px; background: #faf9f6; }
.profile-mark { display: grid; width: 50px; height: 50px; flex: 0 0 auto; place-items: center; border-radius: 12px; color: #fff; background: #164f35; }
.profile-intro div { display: grid; gap: 2px; }
.profile-intro strong { font-family: Georgia, serif; font-size: 15px; }
.profile-intro small { color: #757e79; font-size: 10px; }

.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; padding: 12px 14px 0; }
.field-grid--contact { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.set-card > .set-field { margin: 10px 14px 0; }
.set-field { display: grid; min-width: 0; gap: 5px; color: #364139; font-size: 10.5px; font-weight: 650; }
.set-field > span:first-child { display: flex; justify-content: space-between; gap: 8px; }
.set-field > span b { color: #ca3636; }
.set-field > span small { color: #8c938f; font-weight: 500; }
.set-field input, .set-field textarea { width: 100%; min-width: 0; border: 1px solid #dcd8cf; outline: 0; color: #26322b; background: #fff; font: inherit; font-size: 11.5px; box-sizing: border-box; }
.set-field > input { height: 38px; padding: 0 11px; border-radius: 8px; }
.set-field textarea { min-height: 68px; padding: 9px 11px; resize: vertical; border-radius: 8px; line-height: 1.5; }
.set-field input:focus, .set-field textarea:focus, .set-input:focus-within { border-color: #4f9370; box-shadow: 0 0 0 3px rgba(40, 128, 79, .1); }
.set-input { display: flex; height: 38px; align-items: center; gap: 8px; min-width: 0; padding: 0 10px; border: 1px solid #dcd8cf; border-radius: 8px; color: #6f7872; background: #fff; }
.set-input input { height: 100%; padding: 0; border: 0; background: transparent; box-shadow: none; }
.set-input > b { color: #46514b; font-size: 12px; }
.set-input > small { color: #7b837e; font-size: 10px; }

.set-card__footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 13px; padding: 10px 14px; border-top: 1px solid #ebe7df; }
.set-card__footer small { color: #7d8580; font-size: 9.5px; }
.set-card__footer--plain { margin-top: 0; }
.set-button { display: inline-flex; min-height: 35px; align-items: center; justify-content: center; padding: 0 14px; border: 1px solid #155137; border-radius: 8px; color: #fff; background: #155137; box-shadow: 0 3px 9px rgba(16, 70, 45, .14); font: inherit; font-size: 10.5px; font-weight: 750; cursor: pointer; }
.set-button:hover { background: #0e432d; }
.set-button:disabled { opacity: .55; cursor: wait; }
.set-button--wide { width: calc(100% - 28px); margin: 11px 14px 0; }

.plan-summary { display: flex; align-items: center; gap: 10px; margin: 13px 14px 0; padding: 11px; border: 1px solid #e8e1d2; border-radius: 10px; background: #faf8f3; }
.plan-summary__icon { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 10px; color: #a56504; background: #fff0c9; }
.plan-summary div { display: grid; gap: 2px; }
.plan-summary small { color: #69736d; font-size: 9.5px; text-transform: capitalize; }
.plan-summary strong { font-family: Georgia, serif; font-size: 21px; }
.plan-summary em { margin-left: 3px; color: #6c756f; font-family: inherit; font-size: 10px; font-style: normal; font-weight: 500; }
.billing-note { display: flex; align-items: flex-start; gap: 8px; margin: 10px 14px 0; padding: 9px 10px; border-radius: 8px; color: #6a5a33; background: #fff9ea; font-size: 9.5px; line-height: 1.45; }
.billing-note svg { flex: 0 0 auto; }
.accepted-payments { margin-top: 12px; padding: 11px 14px 13px; border-top: 1px solid #ebe7df; }
.accepted-payments h3 { margin: 0 0 5px; font-size: 10px; text-transform: uppercase; letter-spacing: .06em; }
.accepted-payments > div { display: grid; grid-template-columns: 22px minmax(0, 1fr) 16px; align-items: center; gap: 7px; padding: 7px 0; color: #52605a; border-bottom: 1px solid #f0ede7; }
.accepted-payments > div:last-child { border-bottom: 0; }
.accepted-payments span { display: grid; gap: 1px; }
.accepted-payments strong { color: #2a362f; font-size: 10.5px; }
.accepted-payments small { color: #7e8681; font-size: 9px; }
.accepted-payments > div > svg:last-child { color: #249356; }

.operational-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; padding: 13px 14px; }
.security-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; padding: 12px 14px 14px; }
.security-grid article { display: flex; align-items: center; gap: 9px; min-width: 0; padding: 10px; border: 1px solid #e5e1d8; border-radius: 10px; background: #faf9f6; }
.security-grid article > span { display: grid; width: 34px; height: 34px; flex: 0 0 auto; place-items: center; border-radius: 9px; color: #25764b; background: #e8f3e9; }
.security-grid article > div { display: grid; min-width: 0; gap: 2px; }
.security-grid strong { font-size: 10.5px; }
.security-grid small { overflow: hidden; color: #737d77; font-size: 9.2px; line-height: 1.4; text-overflow: ellipsis; }

.notification-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; padding: 12px 14px; }
.notification-row { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 11px; border: 1px solid #e5e1d8; border-radius: 10px; background: #faf9f6; cursor: pointer; }
.notification-row__copy { display: grid; min-width: 0; gap: 2px; }
.notification-row strong { font-size: 10.5px; }
.notification-row small { color: #78817c; font-size: 9px; line-height: 1.35; }
.switch { position: relative; display: inline-flex; flex: 0 0 auto; }
.switch input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.switch span { position: relative; width: 33px; height: 19px; border-radius: 999px; background: #cbd0cc; transition: background .18s ease; }
.switch span::after { position: absolute; top: 3px; left: 3px; width: 13px; height: 13px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.18); transition: transform .18s ease; content: ''; }
.switch input:checked + span { background: #20a55d; }
.switch input:checked + span::after { transform: translateX(14px); }
.switch input:focus-visible + span { outline: 3px solid rgba(40, 128, 79, .2); outline-offset: 2px; }

button:focus-visible, input:focus-visible, textarea:focus-visible { outline: 3px solid rgba(40, 128, 79, .2); outline-offset: 2px; }

@keyframes pulse { 0%, 100% { opacity: .35; transform: translateY(0); } 50% { opacity: 1; transform: translateY(-3px); } }

@media (max-width: 1150px) {
  .settings-grid { grid-template-columns: minmax(0, 1fr); grid-template-areas: 'profile' 'billing' 'delivery' 'security' 'notifications'; }
  .notification-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 700px) {
  .settings-page { padding: 16px 16px 24px; }
  .settings-tabs { margin-inline: 0; }
  .field-grid, .field-grid--contact, .operational-fields, .security-grid, .notification-grid { grid-template-columns: minmax(0, 1fr); }
  .set-card__header { align-items: flex-start; }
  .set-card__title p { max-width: 34ch; }
}

@media (max-width: 440px) {
  .settings-page { gap: 12px; }
  .settings-heading h1 { font-size: 28px; }
  .settings-tabs button { padding-inline: 12px; }
  .set-card__header { flex-direction: column; }
  .set-card__footer { align-items: stretch; flex-direction: column; }
  .set-button { width: 100%; }
}
</style>
