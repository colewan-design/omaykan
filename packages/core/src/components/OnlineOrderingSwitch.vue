<script setup lang="ts">
import { PauseCircle, PlayCircle } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import type { OrderingState } from '@pos/data/index'
import { getPosRepository } from '@pos/core/services/runtime'

/**
 * The shop's own "not taking online orders right now".
 *
 * Pausing asks when to reopen, because "closed until tomorrow morning" should
 * be one tap and a shop that forgets to reopen should not stay closed for a
 * week. The server ends a pause by itself at its resume time.
 *
 * Renders nothing on a till with no server behind it: there is no storefront
 * to pause. See documentation/merchant-features.md §3.
 */

const repository = getPosRepository()
const state = ref<OrderingState | null>(null)
const choosing = ref(false)
const saving = ref(false)
const error = ref('')

type Preset = { label: string; resumesAt: () => string | null }

function atHour(hour: number, dayOffset = 0): string {
  const at = new Date()
  at.setDate(at.getDate() + dayOffset)
  at.setHours(hour, 0, 0, 0)
  return at.toISOString()
}

const presets: Preset[] = [
  { label: 'For 1 hour', resumesAt: () => new Date(Date.now() + 60 * 60 * 1000).toISOString() },
  { label: 'For 2 hours', resumesAt: () => new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString() },
  { label: 'Until tomorrow, 7 AM', resumesAt: () => atHour(7, 1) },
  { label: 'Until I reopen', resumesAt: () => null },
]

const resumeLabel = computed(() => {
  const at = state.value?.resumesAt
  if (!at) return 'until you reopen'
  const when = new Date(at)
  const sameDay = when.toDateString() === new Date().toDateString()
  const time = when.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
  return sameDay ? `until ${time}` : `until ${when.toLocaleDateString([], { weekday: 'short' })} ${time}`
})

async function apply(paused: boolean, resumesAt: string | null = null) {
  saving.value = true
  error.value = ''
  try {
    state.value = await repository.setOrderingPaused(paused, resumesAt)
    choosing.value = false
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'That did not save. Try again.'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  state.value = await repository.loadOrderingState()
})
</script>

<template>
  <div v-if="state" class="ordering" :class="{ 'ordering--paused': state.paused }">
    <template v-if="state.paused">
      <p class="ordering__status">
        <PauseCircle :size="16" aria-hidden="true" />
        <span><strong>Online orders paused</strong> {{ resumeLabel }}</span>
      </p>
      <button class="ordering__button" type="button" :disabled="saving" @click="apply(false)">
        <PlayCircle :size="16" aria-hidden="true" />
        {{ saving ? 'Reopening…' : 'Reopen now' }}
      </button>
    </template>

    <template v-else-if="!choosing">
      <p class="ordering__status"><span class="ordering__dot" aria-hidden="true" /> Taking online orders</p>
      <button class="ordering__button" type="button" @click="choosing = true">
        <PauseCircle :size="16" aria-hidden="true" />
        Pause
      </button>
    </template>

    <template v-else>
      <p class="ordering__status">Pause online orders…</p>
      <div class="ordering__presets" role="group" aria-label="How long to pause">
        <button
          v-for="preset in presets"
          :key="preset.label"
          class="ordering__preset"
          type="button"
          :disabled="saving"
          @click="apply(true, preset.resumesAt())"
        >
          {{ preset.label }}
        </button>
        <button class="ordering__preset ordering__preset--cancel" type="button" :disabled="saving" @click="choosing = false">
          Cancel
        </button>
      </div>
    </template>

    <p v-if="error" class="ordering__error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.ordering {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-top: 14px;
  padding: 10px 12px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
}

.ordering--paused {
  border-color: color-mix(in srgb, var(--warning) 45%, transparent);
  background: color-mix(in srgb, var(--warning) 10%, var(--bg-elevated));
}

.ordering__status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 13px;
  color: var(--text-primary);
}

.ordering--paused .ordering__status {
  color: var(--text-primary);
}

.ordering__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
}

.ordering__button,
.ordering__preset {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-pill);
  background: var(--bg-elevated);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
}

.ordering__presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}

.ordering__preset--cancel {
  border-color: transparent;
  background: none;
  color: var(--text-secondary);
}

.ordering__button:disabled,
.ordering__preset:disabled {
  opacity: 0.6;
  cursor: default;
}

.ordering__error {
  width: 100%;
  margin: 0;
  font-size: 12px;
  color: var(--danger);
}
</style>
