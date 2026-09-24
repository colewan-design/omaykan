<script setup lang="ts">
import {
  ArrowUpRight,
  Check,
  CircleCheckBig,
  Copy,
  CreditCard,
  ImagePlus,
  MonitorCog,
  Palette,
  Plug,
  Settings2,
  ShieldCheck,
  Store,
  Trash2,
  Upload,
  Users,
} from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { businessModeLabel, storefrontUrl as storefrontUrlFor } from '@pos/shared/index'
import MenuRow from '@pos/core/components/MenuRow.vue'
import SubscriptionPanel from '@pos/core/components/SubscriptionPanel.vue'
import ToggleSwitch from '@pos/core/components/ToggleSwitch.vue'
import { usePosStore } from '@pos/core/stores/pos'
import type { Appearance, AppSettings, BusinessMode, Theme } from '@pos/shared/index'

const store = usePosStore()

const businessModeOptions = (['coffee-shop', 'grocery', 'restaurant', 'nail-salon'] as const).map((mode) => ({
  value: mode,
  label: businessModeLabel(mode),
}))

const appearanceOptions = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

const themeOptions = [
  { value: 'default', label: 'Default' },
  { value: 'ember', label: 'Ember' },
  { value: 'matcha', label: 'Matcha' },
  { value: 'nocturne', label: 'Nocturne' },
  { value: 'casa', label: 'Casa' },
  { value: 'bloom', label: 'Bloom' },
  { value: 'reserve', label: 'Reserve' },
  { value: 'grove', label: 'Grove' },
  { value: 'harbor', label: 'Harbor' },
  { value: 'mono', label: 'Mono' },
]

const tabs = [
  { id: 'business-profile', label: 'General', icon: Settings2 },
  { id: 'online-store', label: 'Store profile', icon: Store },
  { id: 'subscription', label: 'Billing', icon: CreditCard },
  { id: 'operations', label: 'Register', icon: MonitorCog },
  { id: 'appearance', label: 'Appearance', icon: Palette },
  { id: 'access', label: 'Access', icon: ShieldCheck },
] as const

const activeSection = ref<(typeof tabs)[number]['id']>('business-profile')
const businessNameDraft = ref('')
const businessImageError = ref('')
const storefrontLinkCopied = ref(false)
let sectionObserver: IntersectionObserver | null = null

const storefrontUrl = computed(() =>
  store.settings.storefrontSlug
    ? storefrontUrlFor(store.settings.storefrontSlug, {
        rootDomain: import.meta.env.VITE_SHOP_ROOT_DOMAIN,
        origin: window.location.origin,
      })
    : '',
)

const businessNamePlaceholder = computed(() => `${businessModeLabel(store.settings.businessMode)} name`)
const storeSlug = computed(() => store.settings.storefrontSlug || 'Complete seller onboarding to create your link')

watch(
  () => store.settings.businessName,
  (value) => {
    businessNameDraft.value = value
  },
  { immediate: true },
)

onMounted(() => {
  if (!store.isReady) void store.initialize()

  sectionObserver = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
      if (visible) activeSection.value = visible.target.id as typeof activeSection.value
    },
    { rootMargin: '-18% 0px -68% 0px', threshold: [0, 0.2, 0.5] },
  )

  tabs.forEach(({ id }) => {
    const section = document.getElementById(id)
    if (section) sectionObserver?.observe(section)
  })
})

onBeforeUnmount(() => sectionObserver?.disconnect())

function updateSetting<K extends keyof AppSettings>(key: K, value: AppSettings[K]) {
  void store.updateSettings({ ...store.settings, [key]: value })
}

function scrollToSection(id: (typeof tabs)[number]['id']) {
  activeSection.value = id
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function commitBusinessName() {
  const nextName = businessNameDraft.value.trim()
  businessNameDraft.value = nextName
  if (nextName !== store.settings.businessName) updateSetting('businessName', nextName)
}

function handleBusinessNameKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    commitBusinessName()
  }
}

function setBusinessImage(imageUrl: string) {
  businessImageError.value = ''
  if (imageUrl !== store.settings.businessImageUrl) updateSetting('businessImageUrl', imageUrl)
}

function removeBusinessImage() {
  setBusinessImage('')
}

function handleBusinessImageChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    businessImageError.value = 'Please choose an image file.'
    input.value = ''
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    businessImageError.value = 'Choose an image smaller than 5 MB.'
    input.value = ''
    return
  }

  const reader = new FileReader()
  reader.onload = () => setBusinessImage(typeof reader.result === 'string' ? reader.result : '')
  reader.onerror = () => { businessImageError.value = 'Unable to read that image.' }
  reader.readAsDataURL(file)
  input.value = ''
}

async function copyStorefrontLink() {
  if (!storefrontUrl.value) return
  try {
    await navigator.clipboard.writeText(storefrontUrl.value)
    storefrontLinkCopied.value = true
    setTimeout(() => { storefrontLinkCopied.value = false }, 2000)
  } catch {
    storefrontLinkCopied.value = false
  }
}
</script>

<template>
  <div class="seller-settings">
    <header class="seller-settings__heading">
      <div>
        <h1>Settings</h1>
        <p>Manage your store profile, billing, appearance, and device preferences.</p>
      </div>
      <span class="settings-saved-badge"><CircleCheckBig :size="15" /> Changes save automatically</span>
    </header>

    <nav class="seller-settings__tabs" aria-label="Settings sections">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        :class="{ 'is-active': activeSection === tab.id }"
        @click="scrollToSection(tab.id)"
      >
        <component :is="tab.icon" :size="17" />
        <span>{{ tab.label }}</span>
      </button>
    </nav>

    <div class="seller-settings__layout">
      <div class="seller-settings__primary">
        <section id="business-profile" class="settings-panel settings-panel--profile">
          <header class="settings-panel__header">
            <span class="settings-panel__icon"><Store :size="18" /></span>
            <div><h2>Business Profile</h2><p>This information appears on your register and public store.</p></div>
            <a v-if="storefrontUrl" class="settings-panel__action" :href="storefrontUrl" target="_blank" rel="noopener">
              View store profile <ArrowUpRight :size="14" />
            </a>
          </header>

          <div class="profile-editor">
            <div class="profile-editor__media">
              <div class="profile-editor__avatar">
                <img v-if="store.settings.businessImageUrl" :src="store.settings.businessImageUrl" :alt="store.settings.businessName || 'Business profile image'">
                <Store v-else :size="30" />
              </div>
              <label class="profile-editor__upload">
                <span class="profile-editor__upload-icon"><Upload :size="19" /></span>
                <span><strong>Upload your store logo or photo</strong><small>PNG, JPG or WebP (max 5 MB)</small></span>
                <span class="settings-panel__action"><ImagePlus :size="14" /> Choose image</span>
                <input type="file" accept="image/png,image/jpeg,image/webp" @change="handleBusinessImageChange">
              </label>
              <button v-if="store.settings.businessImageUrl" class="profile-editor__remove" type="button" aria-label="Remove business image" @click="removeBusinessImage"><Trash2 :size="16" /></button>
            </div>

            <div class="profile-editor__fields">
              <label class="settings-field settings-field--wide">
                <span>Business name <em>Required</em></span>
                <input v-model="businessNameDraft" type="text" maxlength="60" :placeholder="businessNamePlaceholder" @blur="commitBusinessName" @keydown="handleBusinessNameKeydown">
              </label>
              <label class="settings-field settings-field--wide">
                <span>Store slug</span>
                <span class="settings-field__read-only">{{ storeSlug }}</span>
                <small v-if="store.settings.storefrontSlug">omaykan.com/{{ store.settings.storefrontSlug }}</small>
              </label>
            </div>
            <p v-if="businessImageError" class="settings-error" role="alert">{{ businessImageError }}</p>
          </div>
        </section>

        <section id="online-store" class="settings-panel">
          <header class="settings-panel__header">
            <span class="settings-panel__icon"><Store :size="18" /></span>
            <div><h2>Online Store</h2><p>Share the same public link anywhere customers find your shop.</p></div>
          </header>
          <div class="online-store-row" :class="{ 'is-disabled': !storefrontUrl }">
            <div><span>Public storefront</span><strong>{{ storefrontUrl || 'Your link will appear after seller onboarding' }}</strong></div>
            <button type="button" :disabled="!storefrontUrl" @click="copyStorefrontLink">
              <Check v-if="storefrontLinkCopied" :size="16" />
              <Copy v-else :size="16" />
              {{ storefrontLinkCopied ? 'Copied' : 'Copy link' }}
            </button>
          </div>
        </section>

        <section id="operations" class="settings-panel">
          <header class="settings-panel__header">
            <span class="settings-panel__icon"><MonitorCog :size="18" /></span>
            <div><h2>Operational Settings</h2><p>Configure how this register behaves for your kind of business.</p></div>
          </header>
          <div class="settings-menu-grid">
            <MenuRow label="Business type" :options="businessModeOptions" :model-value="store.settings.businessMode" ariaLabel="Business type" @update:model-value="(value) => updateSetting('businessMode', value as BusinessMode)" />
          </div>
        </section>
      </div>

      <aside class="seller-settings__side">
        <section id="subscription" class="settings-panel settings-panel--billing">
          <header class="settings-panel__header">
            <span class="settings-panel__icon"><CreditCard :size="18" /></span>
            <div><h2>Subscription & Billing</h2><p>Manage your plan, payments, and billing status.</p></div>
          </header>
          <SubscriptionPanel />
        </section>

        <section id="access" class="settings-panel">
          <header class="settings-panel__header">
            <span class="settings-panel__icon"><ShieldCheck :size="18" /></span>
            <div><h2>Security & Access</h2><p>Manage staff access and connected tools.</p></div>
          </header>
          <div class="access-grid">
            <RouterLink to="/employees"><span><Users :size="18" /></span><div><strong>Team & access</strong><small>Employee accounts and permissions</small></div><ArrowUpRight :size="15" /></RouterLink>
            <RouterLink to="/integrations"><span><Plug :size="18" /></span><div><strong>Integrations</strong><small>Printers, payments, and services</small></div><ArrowUpRight :size="15" /></RouterLink>
          </div>
        </section>
      </aside>
    </div>

    <section id="appearance" class="settings-panel settings-panel--appearance">
      <header class="settings-panel__header">
        <span class="settings-panel__icon"><Palette :size="18" /></span>
        <div><h2>Appearance & Behavior</h2><p>Customize how Omaykan looks and behaves on this device.</p></div>
      </header>
      <div class="appearance-grid">
        <MenuRow label="Appearance mode" :options="appearanceOptions" :model-value="store.settings.appearance" ariaLabel="Appearance mode" @update:model-value="(value) => updateSetting('appearance', value as Appearance)" />
        <MenuRow label="Theme style" :options="themeOptions" :model-value="store.settings.theme" ariaLabel="Theme style" @update:model-value="(value) => updateSetting('theme', value as Theme)" />
        <div class="appearance-toggle"><div><strong>Animate total updates</strong><small>Show subtle motion when totals change.</small></div><ToggleSwitch :model-value="store.settings.accentTotalAnimation" ariaLabel="Animate total updates" @update:model-value="(value) => updateSetting('accentTotalAnimation', value)" /></div>
        <div class="appearance-toggle"><div><strong>Share diagnostics</strong><small>Help improve reliability with anonymous diagnostics.</small></div><ToggleSwitch :model-value="store.settings.telemetryEnabled" ariaLabel="Share anonymous diagnostics" @update:model-value="(value) => updateSetting('telemetryEnabled', value)" /></div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.seller-settings {
  --settings-green: #20cf66;
  --settings-green-soft: color-mix(in srgb, var(--accent) 16%, var(--bg-elevated));
  display: grid;
  gap: 12px;
  width: 100%;
  padding: 8px 0 20px;
}

.seller-settings__heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding: 2px 2px 0; }
.seller-settings__heading h1 { margin: 0; color: var(--text-primary); font-size: clamp(27px, 2.2vw, 34px); line-height: 1.05; letter-spacing: -0.035em; }
.seller-settings__heading p { margin: 5px 0 0; color: var(--text-secondary); font-size: 14px; }
.settings-saved-badge { display: inline-flex; align-items: center; gap: 7px; min-height: 32px; padding: 0 11px; border-radius: 999px; background: color-mix(in srgb, var(--success) 13%, transparent); color: var(--success); font-size: 12px; font-weight: 650; white-space: nowrap; }

.seller-settings__tabs { position: sticky; top: 0; z-index: 8; display: flex; gap: 4px; min-width: 0; overflow-x: auto; padding: 3px; border: 1px solid var(--separator); border-radius: 12px; background: color-mix(in srgb, var(--bg-surface) 93%, transparent); backdrop-filter: blur(16px); scrollbar-width: none; }
.seller-settings__tabs::-webkit-scrollbar { display: none; }
.seller-settings__tabs button { display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; gap: 8px; min-width: 112px; min-height: 38px; padding: 0 14px; border: 0; border-radius: 9px; background: transparent; color: var(--text-secondary); font-size: 12px; font-weight: 650; cursor: pointer; }
.seller-settings__tabs button:hover { background: var(--fill); color: var(--text-primary); }
.seller-settings__tabs button.is-active { background: var(--accent); color: var(--accent-text-on); box-shadow: inset 0 -2px 0 color-mix(in srgb, white 45%, transparent); }

.seller-settings__layout { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(330px, .95fr); gap: 12px; align-items: start; }
.seller-settings__primary, .seller-settings__side { display: grid; gap: 12px; min-width: 0; }
.settings-panel { scroll-margin-top: 56px; min-width: 0; overflow: hidden; border: 1px solid var(--separator); border-radius: 14px; background: var(--bg-surface); box-shadow: var(--shadow-sm); }
.settings-panel__header { display: flex; align-items: center; gap: 10px; min-height: 55px; padding: 10px 12px; border-bottom: 1px solid var(--separator); }
.settings-panel__icon { display: grid; flex: none; place-items: center; width: 32px; height: 32px; border-radius: 10px; background: var(--settings-green-soft); color: var(--accent); }
.settings-panel__header > div { min-width: 0; }
.settings-panel__header h2 { margin: 0; color: var(--text-primary); font-size: 14px; line-height: 18px; }
.settings-panel__header p { margin: 1px 0 0; color: var(--text-secondary); font-size: 11px; line-height: 15px; }
.settings-panel__action { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 32px; margin-left: auto; padding: 0 11px; border: 1px solid var(--separator); border-radius: 9px; background: var(--bg-elevated); color: var(--text-primary); font-size: 11px; font-weight: 650; text-decoration: none; white-space: nowrap; }
.settings-panel__action:hover { background: var(--fill); }

.profile-editor { display: grid; gap: 14px; padding: 12px; }
.profile-editor__media { position: relative; display: grid; grid-template-columns: 78px minmax(0, 1fr); gap: 12px; }
.profile-editor__avatar { display: grid; place-items: center; width: 78px; height: 78px; overflow: hidden; border: 1px solid var(--separator); border-radius: 14px; background: var(--settings-green-soft); color: var(--accent); }
.profile-editor__avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-editor__upload { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 78px; padding: 10px; border: 1px dashed color-mix(in srgb, var(--text-secondary) 45%, transparent); border-radius: 12px; cursor: pointer; }
.profile-editor__upload:hover { background: var(--fill); }
.profile-editor__upload input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
.profile-editor__upload-icon { display: grid; place-items: center; width: 35px; height: 35px; border-radius: 9px; background: var(--bg-elevated); color: var(--text-primary); }
.profile-editor__upload strong, .profile-editor__upload small { display: block; }
.profile-editor__upload strong { color: var(--text-primary); font-size: 12px; }
.profile-editor__upload small { margin-top: 2px; color: var(--text-secondary); font-size: 10px; }
.profile-editor__upload .settings-panel__action { pointer-events: none; }
.profile-editor__remove { position: absolute; left: 52px; bottom: 4px; display: grid; place-items: center; width: 26px; height: 26px; border: 1px solid var(--separator); border-radius: 8px; background: var(--bg-elevated); color: var(--danger); cursor: pointer; }
.profile-editor__fields { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.settings-field { display: grid; gap: 5px; min-width: 0; }
.settings-field > span:first-child { color: var(--text-primary); font-size: 11px; font-weight: 650; }
.settings-field em { margin-left: 4px; color: var(--danger); font-size: 9px; font-style: normal; text-transform: uppercase; }
.settings-field input, .settings-field__read-only { display: flex; align-items: center; width: 100%; min-height: 36px; padding: 0 10px; border: 1px solid var(--separator); border-radius: 9px; outline: none; background: var(--bg-elevated); color: var(--text-primary); font: inherit; font-size: 12px; }
.settings-field input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 14%, transparent); }
.settings-field small { overflow: hidden; color: var(--text-tertiary); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.settings-error { margin: 0; color: var(--danger); font-size: 11px; }

.online-store-row { display: flex; align-items: center; gap: 16px; padding: 12px; }
.online-store-row > div { display: grid; min-width: 0; gap: 3px; }
.online-store-row span { color: var(--text-secondary); font-size: 10px; }
.online-store-row strong { overflow: hidden; color: var(--text-primary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.online-store-row button { display: inline-flex; flex: none; align-items: center; gap: 7px; min-height: 34px; margin-left: auto; padding: 0 12px; border: 1px solid var(--separator); border-radius: 9px; background: var(--bg-elevated); color: var(--text-primary); font-size: 11px; font-weight: 650; cursor: pointer; }
.online-store-row button:disabled, .online-store-row.is-disabled { opacity: .58; }

.settings-menu-grid { display: grid; grid-template-columns: minmax(0, 1fr); padding: 10px; }
.settings-menu-grid :deep(.settings-row), .appearance-grid :deep(.settings-row) { overflow: hidden; border: 1px solid var(--separator); border-radius: 10px; background: var(--bg-elevated); }
.settings-menu-grid :deep(.menu-row__trigger), .appearance-grid :deep(.menu-row__trigger) { min-height: 50px; padding: 0 12px; }
.settings-menu-grid :deep(.menu-row__label), .appearance-grid :deep(.menu-row__label) { font-size: 11px; font-weight: 650; }
.settings-menu-grid :deep(.menu-row__value-text), .appearance-grid :deep(.menu-row__value-text) { font-size: 11px; }

.settings-panel--billing :deep(.subs) { margin: 0; padding: 12px; border: 0; border-radius: 0; background: transparent; }
.settings-panel--billing :deep(.subs__price) { font-size: 21px; }
.settings-panel--billing :deep(.subs__paybtn), .settings-panel--billing :deep(.subs__submit) { border-radius: 9px; background: var(--accent); color: var(--accent-text-on); }
.settings-panel--billing :deep(.subs__input) { min-height: 36px; border-radius: 8px; background: var(--bg-elevated); }
.settings-panel--billing :deep(.subs__how), .settings-panel--billing :deep(.subs__payhint), .settings-panel--billing :deep(.subs__note) { font-size: 11px; line-height: 16px; }

.access-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 10px; }
.access-grid a { display: grid; grid-template-columns: auto minmax(0,1fr) auto; align-items: center; gap: 9px; min-height: 64px; padding: 9px; border: 1px solid var(--separator); border-radius: 10px; background: var(--bg-elevated); color: var(--text-primary); text-decoration: none; }
.access-grid a:hover { background: var(--fill); }
.access-grid a > span { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 9px; background: var(--settings-green-soft); color: var(--accent); }
.access-grid strong, .access-grid small { display: block; }
.access-grid strong { font-size: 11px; }
.access-grid small { margin-top: 2px; color: var(--text-secondary); font-size: 9px; line-height: 13px; }

.settings-panel--appearance { scroll-margin-top: 56px; }
.appearance-grid { display: grid; grid-template-columns: 1fr 1.35fr 1fr 1fr; gap: 8px; padding: 10px; }
.appearance-toggle { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 52px; padding: 8px 10px; border: 1px solid var(--separator); border-radius: 10px; background: var(--bg-elevated); }
.appearance-toggle strong, .appearance-toggle small { display: block; }
.appearance-toggle strong { color: var(--text-primary); font-size: 11px; }
.appearance-toggle small { margin-top: 2px; color: var(--text-secondary); font-size: 9px; line-height: 12px; }

@media (max-width: 1180px) {
  .seller-settings__layout { grid-template-columns: minmax(0, 1fr); }
  .seller-settings__side { grid-template-columns: 1.15fr .85fr; }
  .appearance-grid { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .seller-settings { gap: 10px; padding-bottom: 12px; }
  .seller-settings__heading { align-items: flex-start; }
  .seller-settings__heading p { max-width: 34ch; line-height: 19px; }
  .settings-saved-badge { display: none; }
  .seller-settings__tabs { margin-inline: -2px; }
  .seller-settings__tabs button { min-width: auto; padding-inline: 12px; }
  .seller-settings__tabs button span { display: none; }
  .seller-settings__side, .profile-editor__fields, .appearance-grid { grid-template-columns: minmax(0, 1fr); }
  .profile-editor__media { grid-template-columns: 64px minmax(0, 1fr); }
  .profile-editor__avatar { width: 64px; height: 64px; }
  .profile-editor__upload { grid-template-columns: auto minmax(0,1fr); min-height: 64px; }
  .profile-editor__upload .settings-panel__action { display: none; }
  .profile-editor__remove { left: 40px; }
  .settings-panel__header { align-items: flex-start; }
  .settings-panel__header .settings-panel__action { width: 32px; padding: 0; font-size: 0; }
  .access-grid { grid-template-columns: minmax(0,1fr); }
}

@media (max-width: 430px) {
  .seller-settings__heading h1 { font-size: 28px; }
  .seller-settings__heading p { font-size: 12px; }
  .profile-editor { padding: 10px; }
  .profile-editor__media { grid-template-columns: minmax(0,1fr); }
  .profile-editor__avatar { width: 72px; height: 72px; }
  .profile-editor__remove { left: 48px; top: 48px; bottom: auto; }
  .online-store-row { align-items: flex-end; }
  .online-store-row button { width: 34px; padding: 0; font-size: 0; }
}
</style>
