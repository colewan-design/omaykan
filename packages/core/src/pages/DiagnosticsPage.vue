<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { formatCompactDate } from '@pos/shared/index'
import { usePosStore } from '@pos/core/stores/pos'

const store = usePosStore()
onMounted(() => { if (!store.isReady) void store.initialize() })

const telemetryBreakdown = computed(() => {
  const totals = new Map<string, number>()
  for (const event of store.appEvents) {
    totals.set(event.eventType, (totals.get(event.eventType) ?? 0) + 1)
  }
  return [...totals.entries()]
    .map(([eventType, count]) => ({ eventType, count }))
    .sort((a, b) => b.count - a.count)
})

const latestEvent = computed(() => store.appEvents[0] ?? null)
</script>

<template>
  <div class="diag-page">

    <div class="diag-header">
      <div>
        <p class="eyebrow">Developer</p>
        <h1 class="panel-title">Diagnostics</h1>
      </div>
      <span class="order-badge">{{ store.pendingAppEvents.length }} pending</span>
    </div>

    <div class="diag-stats">
      <article class="diag-stat">
        <p class="diag-stat__label">Telemetry</p>
        <strong>{{ store.settings.telemetryEnabled ? 'Enabled' : 'Disabled' }}</strong>
      </article>
      <article class="diag-stat">
        <p class="diag-stat__label">Latest event</p>
        <strong>{{ latestEvent ? formatCompactDate(latestEvent.createdAt) : 'None yet' }}</strong>
      </article>
      <article class="diag-stat">
        <p class="diag-stat__label">Captured events</p>
        <strong>{{ store.appEvents.length }}</strong>
      </article>
    </div>

    <section class="surface-panel">
      <div class="subpanel-header">
        <h2 class="subpanel-title">Event mix</h2>
      </div>
      <div v-if="telemetryBreakdown.length === 0" class="empty-state">
        Interact with the register to generate app events.
      </div>
      <div v-else class="analytics-list">
        <div
          v-for="entry in telemetryBreakdown"
          :key="entry.eventType"
          class="analytics-row"
        >
          <code class="diag-event-type">{{ entry.eventType }}</code>
          <strong>{{ entry.count }}</strong>
        </div>
      </div>
    </section>

    <section class="surface-panel">
      <div class="subpanel-header">
        <h2 class="subpanel-title">Latest event payload</h2>
      </div>
      <div v-if="latestEvent" class="analytics-event">
        <div class="analytics-row">
          <span>Type</span>
          <strong>{{ latestEvent.eventType }}</strong>
        </div>
        <div class="analytics-row">
          <span>Device</span>
          <strong>{{ latestEvent.deviceId.slice(0, 8) }}</strong>
        </div>
        <div class="analytics-row">
          <span>App version</span>
          <strong>{{ latestEvent.appVersion }}</strong>
        </div>
        <pre class="analytics-json">{{ JSON.stringify(latestEvent.payload, null, 2) }}</pre>
      </div>
      <div v-else class="empty-state">
        No telemetry captured yet.
      </div>
    </section>

  </div>
</template>

<style scoped>
.diag-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-5);
  padding-bottom: var(--space-10);
}

.diag-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.diag-stats {
  display: grid;
  gap: var(--space-3);
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
}

.diag-stat {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-elevated) 94%, transparent);
}

.diag-stat__label {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.diag-event-type {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}
</style>
