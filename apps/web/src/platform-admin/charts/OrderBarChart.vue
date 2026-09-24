<script setup lang="ts">
import { computed, ref } from 'vue'
import { axisDate } from '../format'

const props = defineProps<{ points: Array<{ date: string; value: number }>; height?: number }>()
const W = 720
const H = computed(() => props.height ?? 220)
const PAD = { top: 14, right: 10, bottom: 28, left: 42 }
const plotW = W - PAD.left - PAD.right
const plotH = computed(() => H.value - PAD.top - PAD.bottom)
const maximum = computed(() => Math.max(1, ...props.points.map((point) => point.value)))
const ceiling = computed(() => Math.max(5, Math.ceil(maximum.value / 5) * 5))
const gap = computed(() => Math.max(1.5, Math.min(5, plotW / Math.max(1, props.points.length) * .22)))
const barWidth = computed(() => Math.max(2, plotW / Math.max(1, props.points.length) - gap.value))
const ticks = computed(() => [0, .33, .66, 1].map((fraction) => ({ value: Math.round(ceiling.value * fraction), y: PAD.top + plotH.value - fraction * plotH.value })))
const dateTicks = computed(() => {
  const step = Math.max(1, Math.ceil(props.points.length / 6))
  return props.points.map((point, index) => ({ ...point, index })).filter((point) => point.index % step === 0)
})
const active = ref<number | null>(null)
const xFor = (index: number) => PAD.left + index * (plotW / Math.max(1, props.points.length)) + gap.value / 2
const yFor = (value: number) => PAD.top + plotH.value - (value / ceiling.value) * plotH.value
</script>

<template>
  <div class="bars">
    <svg :viewBox="`0 0 ${W} ${H}`" role="img" aria-label="Orders placed by day">
      <g class="bars__grid"><line v-for="tick in ticks" :key="tick.y" :x1="PAD.left" :x2="W - PAD.right" :y1="tick.y" :y2="tick.y" /></g>
      <g class="bars__ylabel"><text v-for="tick in ticks" :key="tick.y" :x="PAD.left - 8" :y="tick.y + 3">{{ tick.value }}</text></g>
      <rect
        v-for="(point, index) in points"
        :key="point.date"
        class="bars__bar"
        :class="{ 'bars__bar--active': active === index }"
        :x="xFor(index)"
        :y="yFor(point.value)"
        :width="barWidth"
        :height="Math.max(1, PAD.top + plotH - yFor(point.value))"
        rx="2"
        @pointerenter="active = index"
        @pointerleave="active = null"
      />
      <g class="bars__xlabel"><text v-for="tick in dateTicks" :key="tick.date" :x="xFor(tick.index) + barWidth / 2" :y="H - 8">{{ axisDate(tick.date) }}</text></g>
    </svg>
    <div v-if="active !== null && points[active]" class="bars__tip"><strong>{{ points[active].value }}</strong><span>orders</span><small>{{ axisDate(points[active].date) }}</small></div>
  </div>
</template>

<style scoped>
.bars { position: relative; }
.bars svg { display: block; width: 100%; height: auto; }
.bars__grid line { stroke: var(--adm-grid); stroke-width: 1; }
.bars__ylabel text, .bars__xlabel text { fill: var(--adm-axis); font-size: 10.5px; }
.bars__ylabel text { text-anchor: end; }
.bars__xlabel text { text-anchor: middle; }
.bars__bar { fill: #93b7a0; transition: fill 140ms ease; }
.bars__bar--active { fill: #236442; }
.bars__tip { position: absolute; top: 6px; right: 8px; display: grid; padding: 7px 10px; border-radius: 8px; background: #12382b; color: #fff; font-size: 10px; pointer-events: none; }
.bars__tip strong { font-size: 13px; }
.bars__tip span, .bars__tip small { color: rgba(255, 255, 255, .68); }
</style>
