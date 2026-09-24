<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  rows: Array<{ label: string; value: number }>
  format: (value: number) => string
}>()
const colors = ['#195d39', '#75a987', '#df6533', '#efb42b', '#368bd1', '#8d68c9', '#c6a55b']
const circumference = 314.16
const total = computed(() => props.rows.reduce((sum, row) => sum + row.value, 0))
const segments = computed(() => {
  let used = 0
  return props.rows.map((row, index) => {
    const percent = total.value ? (row.value / total.value) * 100 : 0
    const length = (percent / 100) * circumference
    const segment = { ...row, percent, color: colors[index % colors.length], dasharray: `${length} ${circumference - length}`, dashoffset: -used }
    used += length
    return segment
  })
})
</script>

<template>
  <div class="donut">
    <div class="donut__chart">
      <svg viewBox="0 0 120 120" role="img" :aria-label="segments.map((segment) => `${segment.label} ${Math.round(segment.percent)}%`).join(', ')">
        <circle cx="60" cy="60" r="50" class="donut__track" />
        <circle v-for="segment in segments" :key="segment.label" cx="60" cy="60" r="50" class="donut__segment" :stroke="segment.color" :stroke-dasharray="segment.dasharray" :stroke-dashoffset="segment.dashoffset" />
      </svg>
      <p><strong>{{ format(total) }}</strong><span>Total sales</span></p>
    </div>
    <ul>
      <li v-for="segment in segments" :key="segment.label"><i :style="{ background: segment.color }"></i><span>{{ segment.label }}</span><strong>{{ format(segment.value) }}</strong><small>{{ Math.round(segment.percent) }}%</small></li>
    </ul>
  </div>
</template>

<style scoped>
.donut { display: grid; grid-template-columns: minmax(150px, .82fr) minmax(210px, 1.25fr); align-items: center; gap: 14px; }
.donut__chart { position: relative; width: 165px; height: 165px; margin: auto; }
.donut__chart svg { display: block; width: 100%; height: 100%; transform: rotate(-90deg); }
.donut__track, .donut__segment { fill: none; stroke-width: 20; }
.donut__track { stroke: #edf0ed; }
.donut__chart p { position: absolute; inset: 0; display: grid; place-content: center; justify-items: center; margin: 0; }
.donut__chart strong { font-family: var(--sf-serif); font-size: 19px; color: #101828; }
.donut__chart span { color: #667085; font-size: 10.5px; }
.donut ul { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.donut li { display: grid; grid-template-columns: auto minmax(0, 1fr) auto 30px; align-items: center; gap: 8px; min-height: 31px; border-bottom: 1px solid #eceeea; color: #344054; font-size: 10.5px; }
.donut li:last-child { border: 0; }
.donut li i { width: 11px; height: 11px; border-radius: 50%; }
.donut li strong { color: #344054; font-size: 10.5px; }
.donut li small { color: #718096; font-size: 10px; text-align: right; }
@media (max-width: 520px) { .donut { grid-template-columns: 1fr; } }
</style>
