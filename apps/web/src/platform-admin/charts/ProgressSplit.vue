<script setup lang="ts">
import { computed } from 'vue'
import { count, PROGRESS_LABELS } from '../format'

const props = defineProps<{ counts: Record<string, number> }>()
const ORDER = ['processing', 'shipped', 'completed', 'cancelled'] as const
const circumference = 314.16

const total = computed(() => ORDER.reduce((sum, key) => sum + (props.counts[key] ?? 0), 0))
const segments = computed(() => {
  let used = 0
  return ORDER.map((key) => {
    const value = props.counts[key] ?? 0
    const percent = total.value > 0 ? (value / total.value) * 100 : 0
    const length = (percent / 100) * circumference
    const segment = {
      key,
      label: PROGRESS_LABELS[key],
      value,
      percent,
      dasharray: `${length} ${circumference - length}`,
      dashoffset: -used,
    }
    used += length
    return segment
  })
})
</script>

<template>
  <div class="split">
    <div class="split__donut" role="img" :aria-label="segments.map((segment) => `${segment.label} ${segment.value}`).join(', ')">
      <svg viewBox="0 0 120 120" aria-hidden="true">
        <circle class="split__track" cx="60" cy="60" r="50" />
        <circle
          v-for="segment in segments"
          :key="segment.key"
          class="split__segment"
          :class="`split__segment--${segment.key}`"
          cx="60"
          cy="60"
          r="50"
          :stroke-dasharray="segment.dasharray"
          :stroke-dashoffset="segment.dashoffset"
        />
      </svg>
      <p><strong class="adm-num">{{ count(total) }}</strong><span>orders</span><small>all time</small></p>
    </div>

    <ul class="split__legend">
      <li v-for="segment in segments" :key="segment.key">
        <span class="split__dot" :class="`split__dot--${segment.key}`"></span>
        <span>{{ segment.label }}</span>
        <strong class="adm-num">{{ count(segment.value) }}</strong>
        <small class="adm-num">{{ total ? Math.round(segment.percent) : 0 }}%</small>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.split { display: grid; gap: 8px; }
.split__donut { position: relative; width: 154px; height: 154px; margin: 0 auto; }
.split__donut svg { display: block; width: 100%; height: 100%; transform: rotate(-90deg); }
.split__track, .split__segment { fill: none; stroke-width: 18; }
.split__track { stroke: #eef1ee; }
.split__segment { transform-origin: center; }
.split__segment--processing { stroke: #e6a500; }
.split__segment--shipped { stroke: #1b77cc; }
.split__segment--completed { stroke: #0b9650; }
.split__segment--cancelled { stroke: #e43c35; }
.split__donut p { position: absolute; inset: 0; display: grid; place-content: center; justify-items: center; margin: 0; line-height: 1.05; }
.split__donut strong { color: #101828; font-family: var(--sf-serif); font-size: 25px; }
.split__donut span { color: #101828; font-size: 11.5px; font-weight: 700; }
.split__donut small { margin-top: 3px; color: #718096; font-size: 10.5px; }
.split__legend { display: grid; gap: 7px; margin: 0; padding: 0; list-style: none; }
.split__legend li { display: grid; grid-template-columns: auto minmax(0, 1fr) auto 34px; align-items: center; gap: 8px; color: #344054; font-size: 11.5px; }
.split__legend strong { color: #101828; font-size: 11.5px; }
.split__legend small { color: #718096; font-size: 10.5px; text-align: right; }
.split__dot { width: 9px; height: 9px; border-radius: 50%; }
.split__dot--processing { background: #e6a500; }
.split__dot--shipped { background: #1b77cc; }
.split__dot--completed { background: #0b9650; }
.split__dot--cancelled { background: #e43c35; }
</style>
