<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Activity,
  CheckCircle2,
  Cloud,
  Database,
  Plug,
  Printer,
  RefreshCw,
  ShieldCheck,
  Store,
} from '@lucide/vue'
import ChartCard from '@pos/core/components/ChartCard.vue'
import ToggleSwitch from '@pos/core/components/ToggleSwitch.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { detectReceiptPrinterCapabilities, type ReceiptPrinterCapabilities } from '@pos/core/utils/printer'
import { printTestReceipt } from '@pos/core/utils/receipt'
import { businessModeLabel, formatCompactDate, type AppSettings } from '@pos/shared/index'

const store = usePosStore()
const printerCapabilities = ref<ReceiptPrinterCapabilities | null>(null)
const isDetectingPrinter = ref(false)

function printPrinterTestPage() {
  printTestReceipt({ name: store.settings.businessName })
}

onMounted(() => {
  if (!store.isReady) {
    void store.initialize()
  }
  void refreshPrinterCapabilities()
})

function updateSetting<K extends keyof AppSettings>(key: K, value: AppSettings[K]) {
  void store.updateSettings({ ...store.settings, [key]: value })
}

const hasBusinessProfile = computed(() => Boolean(store.settings.businessName.trim()))
const latestEvent = computed(() => store.appEvents[0] ?? null)
const pendingEvents = computed(() => store.pendingAppEvents.length)
const trackedProducts = computed(() => store.products.filter((product) => product.stockQty !== undefined).length)
const availableProducts = computed(() =>
  store.products.filter((product) => product.businessModes.includes(store.settings.businessMode)).length,
)
const printerReady = computed(() => Boolean(printerCapabilities.value?.browserPrintAvailable))
const printerStatus = computed(() => {
  const capabilities = printerCapabilities.value
  if (!capabilities) {
    return 'Checking'
  }
  if (!capabilities.browserPrintAvailable) {
    return 'Unavailable'
  }
  if (capabilities.grantedUsbDevices.length > 0) {
    return 'USB granted'
  }
  return 'Browser print'
})
const printerDescription = computed(() => {
  const capabilities = printerCapabilities.value
  if (!capabilities) {
    return 'Checking browser print and direct-device support.'
  }
  if (!capabilities.browserPrintAvailable) {
    return 'This browser does not expose window.print().'
  }
  if (capabilities.grantedUsbDevices.length > 0) {
    return `${capabilities.grantedUsbDevices.length} USB device access grant detected.`
  }
  if (!capabilities.secureContext) {
    return 'Browser printing is available. Direct USB/Bluetooth checks require HTTPS or localhost.'
  }
  if (capabilities.usbSupported || capabilities.bluetoothSupported) {
    return 'Browser printing is available. No direct USB printer grant has been saved for this browser.'
  }
  return 'Browser printing is available through the operating system print dialog.'
})

async function refreshPrinterCapabilities() {
  isDetectingPrinter.value = true
  try {
    printerCapabilities.value = await detectReceiptPrinterCapabilities()
  } finally {
    isDetectingPrinter.value = false
  }
}

const integrationCards = computed(() => [
  {
    key: 'sync',
    title: 'Online Sync',
    description: 'Every sale is recorded on Omaykan before it is completed.',
    status: 'Connected',
    ready: true,
    icon: Cloud,
  },
  {
    key: 'catalog',
    title: 'Catalog Feed',
    description: `${availableProducts.value} products available for ${businessModeLabel(store.settings.businessMode).toLowerCase()}.`,
    status: availableProducts.value > 0 ? 'Ready' : 'Needs products',
    ready: availableProducts.value > 0,
    icon: Database,
  },
  {
    key: 'printer',
    title: 'Receipt Printer',
    description: printerDescription.value,
    status: printerStatus.value,
    ready: printerReady.value,
    icon: Printer,
  },
  {
    key: 'telemetry',
    title: 'Telemetry',
    description: store.settings.telemetryEnabled
      ? 'Register events are being captured for diagnostics.'
      : 'Event capture is disabled.',
    status: store.settings.telemetryEnabled ? 'Enabled' : 'Disabled',
    ready: store.settings.telemetryEnabled,
    icon: Activity,
  },
])

const setupTasks = computed(() => [
  {
    label: 'Business profile',
    detail: hasBusinessProfile.value ? store.settings.businessName : 'Add a store name in settings',
    done: hasBusinessProfile.value,
  },
  {
    label: 'Catalog loaded',
    detail: `${store.products.length} products across ${store.categories.length} categories`,
    done: store.products.length > 0,
  },
  {
    label: 'Inventory tracking',
    detail: `${trackedProducts.value} products tracking stock levels`,
    done: trackedProducts.value > 0,
  },
  {
    label: 'Telemetry queue',
    detail: pendingEvents.value === 0 ? 'All captured events are uploaded' : `${pendingEvents.value} events pending upload`,
    done: pendingEvents.value === 0,
  },
  {
    label: 'Receipt printing',
    detail: printerDescription.value,
    done: printerReady.value,
  },
])
</script>

<template>
  <div class="integrations-page">
    <section class="integrations-header">
      <div>
        <h1 class="integrations-title">Integrations</h1>
        <p class="integrations-copy">Manage connected services, local device readiness, and data handoff points.</p>
      </div>
      <div class="integrations-status integrations-status--online">
        <Cloud :size="16" />
        <span>Online</span>
      </div>
    </section>

    <section class="integrations-kpis">
      <article class="integrations-kpi">
        <span class="integrations-kpi__icon"><Store :size="18" /></span>
        <div>
          <p>Business mode</p>
          <strong>{{ businessModeLabel(store.settings.businessMode) }}</strong>
        </div>
      </article>
      <article class="integrations-kpi">
        <span class="integrations-kpi__icon"><Database :size="18" /></span>
        <div>
          <p>Catalog items</p>
          <strong>{{ store.products.length }}</strong>
        </div>
      </article>
      <article class="integrations-kpi">
        <span class="integrations-kpi__icon"><Activity :size="18" /></span>
        <div>
          <p>Captured events</p>
          <strong>{{ store.appEvents.length }}</strong>
        </div>
      </article>
      <article class="integrations-kpi">
        <span class="integrations-kpi__icon"><RefreshCw :size="18" /></span>
        <div>
          <p>Pending upload</p>
          <strong>{{ pendingEvents }}</strong>
        </div>
      </article>
    </section>

    <section class="integrations-grid">
      <article
        v-for="card in integrationCards"
        :key="card.key"
        class="integration-card"
        :class="{ 'integration-card--ready': card.ready }"
      >
        <div class="integration-card__icon">
          <component :is="card.icon" :size="20" />
        </div>
        <div class="integration-card__body">
          <div class="integration-card__head">
            <h2>{{ card.title }}</h2>
            <span>{{ card.status }}</span>
          </div>
          <p>{{ card.description }}</p>
          <div v-if="card.key === 'printer'" class="integration-card__actions">
            <button
              class="integration-card__action"
              type="button"
              :disabled="isDetectingPrinter"
              @click="refreshPrinterCapabilities"
            >
              <RefreshCw :size="14" />
              <span>{{ isDetectingPrinter ? 'Checking' : 'Check printer' }}</span>
            </button>
            <button
              class="integration-card__action"
              type="button"
              @click="printPrinterTestPage"
            >
              <Printer :size="14" />
              <span>Print test receipt</span>
            </button>
          </div>
        </div>
      </article>
    </section>

    <section class="integrations-workspace">
      <ChartCard title="Connection Settings" summary="Primary integration settings for this register.">
        <div class="integrations-settings">
          <div class="integrations-setting-row">
            <div>
              <strong>Telemetry</strong>
              <p>Capture register events for reports and diagnostics.</p>
            </div>
            <ToggleSwitch
              :model-value="store.settings.telemetryEnabled"
              ariaLabel="Enable telemetry"
              @update:model-value="(value) => updateSetting('telemetryEnabled', value)"
            />
          </div>
        </div>
      </ChartCard>

      <ChartCard title="Readiness Checklist" summary="Setup checks before connecting this register to external services.">
        <div class="integrations-checklist">
          <div v-for="task in setupTasks" :key="task.label" class="integrations-check-row">
            <span class="integrations-check-row__icon" :class="{ 'integrations-check-row__icon--done': task.done }">
              <CheckCircle2 v-if="task.done" :size="17" />
              <ShieldCheck v-else :size="17" />
            </span>
            <div>
              <strong>{{ task.label }}</strong>
              <p>{{ task.detail }}</p>
            </div>
          </div>
        </div>
      </ChartCard>
    </section>

    <section class="integrations-feed">
      <div class="integrations-feed__head">
        <div>
          <h2>Latest Integration Event</h2>
          <p>Most recent event captured by the register runtime.</p>
        </div>
        <Plug :size="18" />
      </div>

      <div v-if="latestEvent" class="integrations-event">
        <div>
          <span>Type</span>
          <strong>{{ latestEvent.eventType }}</strong>
        </div>
        <div>
          <span>Captured</span>
          <strong>{{ formatCompactDate(latestEvent.createdAt) }}</strong>
        </div>
        <div>
          <span>Device</span>
          <strong>{{ latestEvent.deviceId.slice(0, 8) }}</strong>
        </div>
        <div>
          <span>Status</span>
          <strong>{{ latestEvent.sentAt ? 'Sent' : 'Pending' }}</strong>
        </div>
      </div>

      <div v-else class="integrations-empty">
        No integration events have been captured yet.
      </div>
    </section>
  </div>
</template>

<style scoped>
.integrations-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-5);
  padding: var(--space-4) 0 var(--space-8);
}

.integrations-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.integrations-title {
  margin: 0;
  font: var(--type-title1);
}

.integrations-copy {
  max-width: 64ch;
  margin: var(--space-2) 0 0;
  color: var(--text-secondary);
}

.integrations-status {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 36px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--warning) 14%, transparent);
  color: var(--warning);
  font: var(--type-caption);
  font-weight: 700;
}

.integrations-status--online {
  background: color-mix(in srgb, var(--success) 14%, transparent);
  color: var(--success);
}

.integrations-kpis,
.integrations-grid,
.integrations-workspace {
  display: grid;
  gap: var(--space-4);
}

.integrations-kpis {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.integrations-kpi,
.integration-card,
.integrations-feed {
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-xl);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.integrations-kpi {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
}

.integrations-kpi__icon,
.integration-card__icon,
.integrations-check-row__icon {
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}

.integrations-kpi__icon {
  width: 40px;
  height: 40px;
}

.integrations-kpi p,
.integration-card p,
.integrations-setting-row p,
.integrations-check-row p,
.integrations-feed__head p,
.integrations-event span {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.integrations-kpi strong {
  color: var(--text-primary);
  font: var(--type-title2);
  font-variant-numeric: tabular-nums;
}

.integrations-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.integration-card {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
}

.integration-card--ready .integration-card__icon {
  background: color-mix(in srgb, var(--success) 14%, transparent);
  color: var(--success);
}

.integration-card__icon {
  width: 44px;
  height: 44px;
}

.integration-card__body {
  display: grid;
  gap: var(--space-2);
}

.integration-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.integration-card__head h2,
.integrations-feed__head h2 {
  margin: 0;
  color: var(--text-primary);
  font: var(--type-headline);
}

.integration-card__head span {
  flex: none;
  padding: 3px var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--fill);
  color: var(--text-secondary);
  font: var(--type-caption);
  font-weight: 700;
}

.integration-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.integration-card__action {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 32px;
  padding: 0 var(--space-3);
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
  color: var(--text-primary);
  font: var(--type-caption);
  font-weight: 700;
}

.integration-card__action:disabled {
  cursor: progress;
  opacity: 0.7;
}

.integrations-workspace {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.integrations-settings,
.integrations-checklist {
  display: grid;
  gap: var(--space-3);
}

.integrations-setting-row,
.integrations-check-row,
.integrations-feed__head,
.integrations-event {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.integrations-setting-row,
.integrations-check-row {
  padding: var(--space-3) 0;
  border-bottom: 0.5px solid var(--separator);
}

.integrations-setting-row:last-child,
.integrations-check-row:last-child {
  border-bottom: none;
}

.integrations-setting-row strong,
.integrations-check-row strong,
.integrations-event strong {
  color: var(--text-primary);
  font: var(--type-subhead);
  font-weight: 700;
}

.integrations-check-row {
  justify-content: flex-start;
}

.integrations-check-row__icon {
  width: 38px;
  height: 38px;
  color: var(--warning);
  background: color-mix(in srgb, var(--warning) 14%, transparent);
}

.integrations-check-row__icon--done {
  color: var(--success);
  background: color-mix(in srgb, var(--success) 14%, transparent);
}

.integrations-feed {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
}

.integrations-event {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--fill);
}

.integrations-event div {
  display: grid;
  gap: var(--space-1);
}

.integrations-empty {
  padding: var(--space-6);
  border-radius: var(--radius-lg);
  background: var(--fill);
  color: var(--text-secondary);
  text-align: center;
}

@media (max-width: 1180px) {
  .integrations-kpis,
  .integrations-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .integrations-workspace {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .integrations-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .integrations-kpis,
  .integrations-grid,
  .integrations-event {
    grid-template-columns: minmax(0, 1fr);
  }

  .integrations-setting-row {
    align-items: flex-start;
  }
}
</style>
