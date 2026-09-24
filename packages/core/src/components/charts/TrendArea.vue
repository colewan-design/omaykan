<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

// A line over a wash, with the value under the pointer called out.
//
// The geometry is measured in real pixels rather than drawn into a viewBox and
// scaled: a scaled viewBox stretches the stroke and the dots with it, and the
// tooltip has to land on the same coordinates the line was drawn at.

const props = withDefaults(
  defineProps<{
    points: { label: string; value: number }[]
    formatValue: (value: number) => string
    /** A floor, not a fixed size: the chart takes whatever the card gives it. */
    minHeight?: number
    label?: string
  }>(),
  { minHeight: 236, label: 'Trend' },
)

const PAD = { top: 16, right: 16, bottom: 28, left: 64 }
/** Below this much room per label the axis starts skipping them. */
const MIN_LABEL_WIDTH = 52

/**
 * The gradient needs an id of its own per instance, and one `url(#…)` can
 * resolve to. Derived from the label it would pick up a space — "Revenue
 * trend" — which makes the reference invalid and silently paints the wash
 * black, so it is counted instead.
 */
let instances = 0
const gradientId = `trend-wash-${(instances += 1)}`

const host = ref<HTMLElement | null>(null)
const width = ref(760)
const height = ref(props.minHeight)
const hovered = ref<number | null>(null)
let observer: ResizeObserver | null = null

function measure(box: { width: number; height: number }) {
  if (box.width > 0) width.value = box.width
  height.value = Math.max(box.height, props.minHeight)
}

onMounted(() => {
  if (!host.value) return
  observer = new ResizeObserver((entries) => {
    const box = entries[0]?.contentRect
    if (box) measure(box)
  })
  observer.observe(host.value)
  measure({ width: host.value.clientWidth, height: host.value.clientHeight })
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})

const plot = computed(() => ({
  width: Math.max(width.value - PAD.left - PAD.right, 40),
  height: Math.max(height.value - PAD.top - PAD.bottom, 40),
}))

/**
 * The top of the axis, rounded up to a figure worth printing. An axis that
 * reads 0 / 1,245 / 2,490 is harder to read off than one that reads 0 / 1,500
 * / 3,000, even though it fits the data more tightly.
 */
function niceCeiling(value: number): number {
  if (value <= 0) return 1
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const normalized = value / magnitude
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10
  return step * magnitude
}

const ceiling = computed(() => niceCeiling(Math.max(...props.points.map((point) => point.value), 0)))
const gridValues = computed(() => [0, 0.25, 0.5, 0.75, 1].map((fraction) => ceiling.value * fraction))

function x(index: number): number {
  if (props.points.length <= 1) return PAD.left + plot.value.width / 2
  return PAD.left + (index / (props.points.length - 1)) * plot.value.width
}

function y(value: number): number {
  return PAD.top + plot.value.height - (value / ceiling.value) * plot.value.height
}

const coords = computed(() => props.points.map((point, index) => ({ ...point, index, cx: x(index), cy: y(point.value) })))

const linePath = computed(() => coords.value.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.cx} ${point.cy}`).join(' '))

const areaPath = computed(() => {
  if (coords.value.length === 0) return ''
  const floor = PAD.top + plot.value.height
  const first = coords.value[0]!
  const last = coords.value[coords.value.length - 1]!
  return `M${first.cx} ${floor} ${linePath.value.slice(1)} L${last.cx} ${floor} Z`
})

/** Print every nth label, so they never run into one another. */
const labelStep = computed(() => {
  if (props.points.length <= 1) return 1
  const perLabel = plot.value.width / (props.points.length - 1)
  return Math.max(1, Math.ceil(MIN_LABEL_WIDTH / Math.max(perLabel, 1)))
})

const active = computed(() => (hovered.value === null ? null : (coords.value[hovered.value] ?? null)))

function onMove(event: MouseEvent) {
  if (coords.value.length === 0) return
  const bounds = (event.currentTarget as SVGElement).getBoundingClientRect()
  const pointer = event.clientX - bounds.left
  let nearest = 0
  for (let index = 1; index < coords.value.length; index += 1) {
    if (Math.abs(coords.value[index]!.cx - pointer) < Math.abs(coords.value[nearest]!.cx - pointer)) nearest = index
  }
  hovered.value = nearest
}

/** Keeps the callout inside the card when the point is near either edge. */
const tooltipStyle = computed(() => {
  const point = active.value
  if (!point) return {}
  const clamped = Math.min(Math.max(point.cx, PAD.left + 46), width.value - 46)
  return { left: `${clamped}px`, top: `${Math.max(point.cy - 14, 6)}px` }
})
</script>

<template>
  <div ref="host" class="trend" :style="{ minHeight: `${minHeight}px` }">
    <svg
      class="trend__svg"
      :width="width"
      :height="height"
      :aria-label="label"
      role="img"
      @mousemove="onMove"
      @mouseleave="hovered = null"
    >
      <g class="trend__grid">
        <template v-for="(value, index) in gridValues" :key="index">
          <line :x1="PAD.left" :x2="width - PAD.right" :y1="y(value)" :y2="y(value)" />
          <text :x="PAD.left - 12" :y="y(value) + 4" text-anchor="end">{{ formatValue(value) }}</text>
        </template>
      </g>

      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" class="trend__wash-top" />
          <stop offset="100%" class="trend__wash-bottom" />
        </linearGradient>
      </defs>

      <path v-if="areaPath" class="trend__area" :d="areaPath" :fill="`url(#${gradientId})`" />
      <path v-if="linePath" class="trend__line" :d="linePath" fill="none" />

      <circle v-for="point in coords" :key="`dot-${point.index}`" class="trend__dot" :cx="point.cx" :cy="point.cy" r="3.5" />

      <g v-if="active" class="trend__cursor">
        <line :x1="active.cx" :x2="active.cx" :y1="PAD.top" :y2="PAD.top + plot.height" />
        <circle :cx="active.cx" :cy="active.cy" r="6" />
      </g>

      <g class="trend__axis">
        <text
          v-for="point in coords"
          :key="`x-${point.index}`"
          :x="point.cx"
          :y="height - 8"
          text-anchor="middle"
          :class="{ 'is-hidden': point.index % labelStep !== 0 && point.index !== coords.length - 1 }"
        >
          {{ point.label }}
        </text>
      </g>
    </svg>

    <div v-if="active" class="trend__tip" :style="tooltipStyle">
      <span>{{ active.label }}</span>
      <strong><i /> {{ formatValue(active.value) }}</strong>
    </div>
  </div>
</template>

<style scoped>
.trend {
  position: relative;
  min-width: 0;
  /* Takes the card's leftover room; the SVG is sized from what it measures. */
  flex: 1 1 auto;
}

.trend__svg {
  position: absolute;
  inset: 0;
  display: block;
  overflow: visible;
}

.trend__grid line {
  stroke: var(--chart-grid, rgba(120, 120, 128, 0.22));
  stroke-dasharray: 3 5;
  stroke-width: 1;
}

.trend__grid text,
.trend__axis text {
  fill: var(--text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.trend__axis text.is-hidden {
  display: none;
}

.trend__wash-top { stop-color: var(--chart-accent, #1a6b3c); stop-opacity: 0.32; }
.trend__wash-bottom { stop-color: var(--chart-accent, #1a6b3c); stop-opacity: 0; }

.trend__line {
  stroke: var(--chart-accent, #1a6b3c);
  stroke-width: 2.25;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.trend__dot {
  fill: var(--chart-accent, #1a6b3c);
}

.trend__cursor line {
  stroke: var(--chart-accent, #1a6b3c);
  stroke-dasharray: 4 4;
  stroke-width: 1;
  opacity: 0.55;
}

.trend__cursor circle {
  fill: var(--chart-accent, #1a6b3c);
  stroke: var(--chart-surface, #fff);
  stroke-width: 3;
}

.trend__tip {
  position: absolute;
  z-index: 2;
  display: grid;
  gap: 2px;
  padding: 7px 11px;
  border: 1px solid var(--chart-tip-border, rgba(60, 60, 67, 0.16));
  border-radius: 10px;
  background: var(--chart-tip-bg, #fff);
  box-shadow: var(--chart-tip-shadow, 0 8px 22px rgba(40, 52, 68, 0.16));
  pointer-events: none;
  white-space: nowrap;
  transform: translate(-50%, -100%);
}

.trend__tip span {
  color: var(--text-secondary);
  font-size: 11px;
}

.trend__tip strong {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.trend__tip i {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--chart-accent, #1a6b3c);
}
</style>
