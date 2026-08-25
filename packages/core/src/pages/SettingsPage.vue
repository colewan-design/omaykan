<script setup lang="ts">
import {
  Check,
  CircleCheckBig,
  Copy,
  ImagePlus,
  Store,
  Trash2,
} from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { businessModeLabel } from '@pos/shared/index'
import MenuRow from '@pos/core/components/MenuRow.vue'
import SettingsGroup from '@pos/core/components/SettingsGroup.vue'
import ToggleSwitch from '@pos/core/components/ToggleSwitch.vue'
import { usePosStore } from '@pos/core/stores/pos'
import type { Appearance, AppSettings, BusinessMode, Theme } from '@pos/shared/index'

const store = usePosStore()

onMounted(() => {
  if (!store.isReady) {
    void store.initialize()
  }
})

const businessModeOptions = (['coffee-shop', 'grocery', 'restaurant', 'nail-salon'] as const).map((mode) => ({
  value: mode,
  label: businessModeLabel(mode),
}))

const syncModeOptions = [
  { value: 'local-only', label: 'Local-only' },
  { value: 'online-sync', label: 'Online sync' },
]

const appearanceOptions = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

const themeOptions = [
  { value: 'default', label: 'Default' },
  { value: 'ember', label: 'Ember (coffee shop)' },
  { value: 'matcha', label: 'Matcha (tea house)' },
  { value: 'nocturne', label: 'Nocturne (late-night bar)' },
  { value: 'casa', label: 'Casa (mediterranean kitchen)' },
  { value: 'bloom', label: 'Bloom (patisserie & flowers)' },
  { value: 'reserve', label: 'Reserve (steakhouse & bar)' },
  { value: 'grove', label: 'Grove (juice & smoothie bar)' },
  { value: 'harbor', label: 'Harbor (seafood & market)' },
  { value: 'mono', label: 'Mono (studio store)' },
]

const businessNameDraft = ref('')
const businessImageError = ref('')
const pairingCodeCopied = ref(false)

async function copyPairingCode() {
  try {
    await navigator.clipboard.writeText(store.settings.pairingCode)
    pairingCodeCopied.value = true
    setTimeout(() => { pairingCodeCopied.value = false }, 2000)
  } catch {
    // Clipboard permission denied — the code is still shown on screen to copy manually.
  }
}

const businessNamePlaceholder = computed(() => `${businessModeLabel(store.settings.businessMode)} name`)

watch(
  () => store.settings.businessName,
  (value) => {
    businessNameDraft.value = value
  },
  { immediate: true },
)

function updateSetting<K extends keyof AppSettings>(key: K, value: AppSettings[K]) {
  void store.updateSettings({ ...store.settings, [key]: value })
}

function commitBusinessName() {
  const nextName = businessNameDraft.value.trim()
  if (nextName === store.settings.businessName) {
    businessNameDraft.value = nextName
    return
  }

  businessNameDraft.value = nextName
  updateSetting('businessName', nextName)
}

function setBusinessImage(imageUrl: string) {
  businessImageError.value = ''
  if (imageUrl === store.settings.businessImageUrl) {
    return
  }

  updateSetting('businessImageUrl', imageUrl)
}

function removeBusinessImage() {
  setBusinessImage('')
}

function handleBusinessNameKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    commitBusinessName()
  }
}

function handleBusinessImageChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) {
    return
  }

  if (!file.type.startsWith('image/')) {
    businessImageError.value = 'Please choose an image file.'
    input.value = ''
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    const result = typeof reader.result === 'string' ? reader.result : ''
    if (!result) {
      businessImageError.value = 'Unable to read that image.'
      return
    }

    setBusinessImage(result)
  }
  reader.onerror = () => {
    businessImageError.value = 'Unable to read that image.'
  }
  reader.readAsDataURL(file)
  input.value = ''
}

</script>

<template>
  <div class="settings-page">
    <div class="settings-main">
      <div class="settings-main__header">
        <h1 class="settings-page__title">Settings</h1>
        <span class="settings-saved-badge">
          <CircleCheckBig :size="14" />
          <span>Changes are saved automatically</span>
        </span>
      </div>

      <div class="settings-columns">
        <SettingsGroup id="business-profile" class="settings-columns__main" label="Business Profile">
          <div class="settings-row settings-row--stack settings-profile">
            <div class="settings-profile__header">
              <div class="settings-profile__avatar">
                <img
                  v-if="store.settings.businessImageUrl"
                  :src="store.settings.businessImageUrl"
                  :alt="store.settings.businessName || 'Business profile image'"
                >
                <Store v-else :size="22" />
              </div>

              <div class="settings-profile__copy">
                <p class="settings-row__label">Business image</p>
                <p class="settings-row__description">Upload a logo or storefront photo for your register profile.</p>
              </div>
            </div>

            <div class="settings-profile__actions">
              <label class="settings-upload-button">
                <ImagePlus :size="16" />
                <span>{{ store.settings.businessImageUrl ? 'Change image' : 'Upload image' }}</span>
                <input
                  accept="image/*"
                  class="settings-upload-button__input"
                  type="file"
                  @change="handleBusinessImageChange"
                >
              </label>

              <button
                v-if="store.settings.businessImageUrl"
                class="settings-remove-button"
                type="button"
                @click="removeBusinessImage"
              >
                <Trash2 :size="16" />
                <span>Remove</span>
              </button>
            </div>

            <label class="settings-field settings-field--stack">
              <span class="settings-row__label">Business name</span>
              <input
                v-model="businessNameDraft"
                class="sheet-input"
                type="text"
                maxlength="60"
                :placeholder="businessNamePlaceholder"
                @blur="commitBusinessName"
                @keydown="handleBusinessNameKeydown"
              >
            </label>

            <p v-if="businessImageError" class="settings-profile__error">{{ businessImageError }}</p>
          </div>

          <template #footnote>
            Business profile details are saved on this device and shown in the register header.
          </template>
        </SettingsGroup>

        <div class="settings-columns__side">
          <SettingsGroup
            v-if="store.settings.pairingCode"
            id="online-store"
            label="Online Store"
            description="Customers enter this code in the Omaykan app to find and order from your store."
          >
            <div class="settings-row settings-row--stack">
              <div class="settings-profile__actions">
                <span class="settings-code-value">{{ store.settings.pairingCode }}</span>
                <button class="settings-upload-button" type="button" @click="copyPairingCode">
                  <Check v-if="pairingCodeCopied" :size="16" />
                  <Copy v-else :size="16" />
                  <span>{{ pairingCodeCopied ? 'Copied' : 'Copy code' }}</span>
                </button>
              </div>
            </div>
          </SettingsGroup>

          <SettingsGroup id="general" label="General" description="Choose the type of business this register runs.">
            <MenuRow
              label="Business mode"
              :options="businessModeOptions"
              :model-value="store.settings.businessMode"
              ariaLabel="Business mode"
              @update:model-value="(value) => updateSetting('businessMode', value as BusinessMode)"
            />
          </SettingsGroup>

          <SettingsGroup id="data-sync" label="Data & Sync">
            <MenuRow
              label="Sync mode"
              :options="syncModeOptions"
              :model-value="store.settings.syncMode"
              ariaLabel="Sync mode"
              @update:model-value="(value) => updateSetting('syncMode', value as AppSettings['syncMode'])"
            />

            <template #footnote>
              Local-only keeps all data on this device. Switch to Online sync to back up sales and share your
              catalog across registers.
            </template>
          </SettingsGroup>
        </div>
      </div>

      <SettingsGroup id="appearance" class="settings-card--wide" label="Appearance & Behavior">
        <div class="settings-appearance-grid">
          <MenuRow
            label="Appearance"
            :options="appearanceOptions"
            :model-value="store.settings.appearance"
            ariaLabel="Appearance"
            @update:model-value="(value) => updateSetting('appearance', value as Appearance)"
          />

          <MenuRow
            label="Theme"
            :options="themeOptions"
            :model-value="store.settings.theme"
            ariaLabel="Theme"
            @update:model-value="(value) => updateSetting('theme', value as Theme)"
          />

          <div class="settings-row settings-row--toggle">
            <div>
              <p class="settings-row__label">Animate total updates</p>
              <p class="settings-row__description">Show a subtle motion when the total changes.</p>
            </div>
            <ToggleSwitch
              :model-value="store.settings.accentTotalAnimation"
              ariaLabel="Animate total updates"
              @update:model-value="(value) => updateSetting('accentTotalAnimation', value)"
            />
          </div>
        </div>

        <template #footnote>
          Each theme swaps the accent and surface colors for a boutique look matched to a type of business. Ember,
          Matcha, Casa, Bloom, and Grove follow the Appearance setting above with their own Light and Dark
          variants; Nocturne, Reserve, Harbor, and Mono are each a single fixed mood regardless of Appearance.
        </template>
      </SettingsGroup>
    </div>
  </div>
</template>

<style scoped>
.settings-code-value {
  font: var(--type-title2);
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--text-primary);
}
</style>
