<script setup lang="ts">
import { onMounted } from 'vue'
import { Browser } from '@capacitor/browser'
import { ArrowUpCircle, ChevronRight, Store } from '@lucide/vue'
import { useStorefrontPairing } from '../pairing'
import { useStorefrontUpdateCheck } from '../updateCheck'

const pairing = useStorefrontPairing()
const { status, check } = useStorefrontUpdateCheck()

onMounted(() => {
  void check()
})

async function openUpdate() {
  if (!status.value.apkUrl) return
  await Browser.open({ url: status.value.apkUrl })
}
</script>

<template>
  <div class="settings">
    <h1>Settings</h1>

    <section class="settings__group">
      <div class="settings__row settings__row--static">
        <span class="settings__row-icon"><Store :size="18" /></span>
        <div>
          <strong>{{ pairing.current.value?.storeName ?? 'Not paired' }}</strong>
          <span>{{ pairing.current.value?.storeAddress || 'No store address on file' }}</span>
        </div>
      </div>

      <button type="button" class="settings__row" :disabled="!status.updateAvailable" @click="openUpdate">
        <span class="settings__row-icon settings__row-icon--accent"><ArrowUpCircle :size="18" /></span>
        <div>
          <strong>App update</strong>
          <span v-if="status.checking">Checking…</span>
          <span v-else-if="status.updateAvailable">Version {{ status.latestVersionName }} is available</span>
          <span v-else-if="status.currentVersionName">You're on the latest version ({{ status.currentVersionName }})</span>
          <span v-else>Up to date</span>
        </div>
        <ChevronRight v-if="status.updateAvailable" :size="18" class="settings__chevron" />
      </button>
    </section>
  </div>
</template>

<style scoped>
.settings {
  display: grid;
  gap: 16px;
}

.settings h1 {
  margin: 4px 0 0;
  font-size: 1.4rem;
}

.settings__group {
  display: grid;
  gap: 1px;
  border-radius: 20px;
  background: rgba(31, 36, 48, 0.06);
  overflow: hidden;
}

.settings__row {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: none;
  background: #ffffff;
  text-align: left;
  font-family: inherit;
}

.settings__row:disabled {
  cursor: default;
}

.settings__row > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.settings__row strong {
  font-size: 0.94rem;
  color: #1f2430;
}

.settings__row span:not(.settings__row-icon) {
  font-size: 0.82rem;
  color: #6f7480;
}

.settings__row-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: rgba(31, 36, 48, 0.06);
  color: #6f7480;
}

.settings__row-icon--accent {
  background: #fdece0;
  color: var(--sf-primary);
}

.settings__chevron {
  color: var(--sf-primary);
}
</style>
