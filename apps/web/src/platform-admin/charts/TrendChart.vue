<script setup lang="ts">
import { computed, ref } from 'vue'
import { axisDate } from '../format'

/*
 * One measure over time, drawn as an area under a line, with a crosshair and
 * a tooltip that follows the pointer.
 *
 * ## One measure, not two
 *
 * The mockup this portal follows put revenue and order count on one plot with
 * a y-axis down each side. That chart cannot be read: the crossing point of
 * the two lines is an artefact of two arbitrary scales, and every reader takes
 * it for a fact about the business. So this component draws exactly one
 * series, and the analytics screen stacks two of them sharing an x-axis
 * instead — same comparison, no invented crossings.
 *
 * ## Why the marks are where they are
 *
 * The plot is drawn in a fixed user-space viewBox and scaled by CSS, so the
 * geometry is computed once rather than on every resize. `vector-effect` keeps
 * the stroke a true 2px after that scaling — without it a wide chart draws a
 * hairline and a narrow one draws a rope.
 */

const props = defineProps<{
  points: { date: string; value: number }[]
  /** Formats a value for the axis and the tooltip. */
  format: (value: number) => string
  /** Named in the tooltip beside the number, e.g. "in sales". */
  measure: string
  height?: number
}>()

const W = 720
const PAD = { top: 14, right: 10, bottom: 26, left: 52 }

/*
 * The drawing's own height, in the same user space as its width.
 *
 * The SVG is laid out as `width: 100%; height: auto`, so the viewBox aspect
 * ratio — not a CSS height — is what decides how tall the chart renders. A
 * fixed CSS height with a fixed viewBox letterboxes the plot inside the panel
 * instead, which is what a first cut of this did: the chart stopped short of
 * the right-hand edge at every width above the aspect ratio.
 */
const H = computed(() => props.height ?? 220)

const plotW = W - PAD.left - PAD.right
const plotH = computed(() => H.value - PAD.top - PAD.bottom)

/**
 * A DOM id has to be one token — `url(#trend-fill-in sales)` silently fails
 * and the area paints black, which is exactly what shipped from the first
 * draft. Slugged rather than hoped over.
 */
const gradientId = computed(() => `trend-fill-${props.measure.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}`)

/**
 * The top of the scale, rounded up to something a person would choose.
 *
 * Always anchored at zero: a trend chart whose axis starts at the minimum
 * turns a 2% wobble into a cliff, which is the most common way a dashboard
 * misleads without anyone intending it.
 */
const ceiling = computed(() => {
  const peak = Math.max(0, ...props.points.map((point) => point.value))
  if (peak <= 0) return 1

  const magnitude = 10 ** Math.floor(Math.log10(peak))
  return Math.ceil(peak / magnitude) * magnitude
})

const xFor = (index: number) =>
  PAD.left + (props.points.length <= 1 ? plotW / 2 : (index / (props.points.length - 1)) * plotW)

const yFor = (value: number) => PAD.top + plotH.value - (value / ceiling.value) * plotH.value

const linePath = computed(() =>
  props.points.map((point, index) => `${index === 0 ? 'M' : 'L'}${xFor(index).toFixed(1)} ${yFor(point.value).toFixed(1)}`).join(''),
)

const areaPath = computed(() => {
  if (props.points.length === 0) return ''
  const base = PAD.top + plotH.value
  return `${linePath.value}L${xFor(props.points.length - 1).toFixed(1)} ${base}L${xFor(0).toFixed(1)} ${base}Z`
})

/** Four gridlines and their labels — enough to read against, few enough to recede. */
const ticks = computed(() =>
  [0, 0.25, 0.5, 0.75, 1].map((fraction) => ({
    value: ceiling.value * fraction,
    y: PAD.top + plotH.value - fraction * plotH.value,
  })),
)

/**
 * Date labels along the bottom. Every point would collide at 30 days and
 * illegibly at 365, so this thins them to about six whatever the window is.
 */
const dateTicks = computed(() => {
  const step = Math.max(1, Math.ceil(props.points.length / 6))
  return props.points
    .map((point, index) => ({ ...point, index }))
    .filter((point) => point.index % step === 0)
})

// ── Hover ─────────────────────────────────────────────────────────────────

const hoverIndex = ref<number | null>(null)
const svg = ref<SVGSVGElement | null>(null)

/**
 * Nearest point to the pointer, in user space.
 *
 * The pointer arrives in CSS pixels and the plot is in viewBox units, so the
 * x has to be converted through the rendered width — reading `offsetX`
 * directly would select the wrong day on every screen but one.
 */
function onMove(event: PointerEvent) {
  const rect = svg.value?.getBoundingClientRect()
  if (!rect || props.points.length === 0) return

  const x = ((event.clientX - rect.left) / rect.width) * W
  const ratio = (x - PAD.left) / plotW
  const index = Math.round(ratio * (props.points.length - 1))

  hoverIndex.value = Math.min(props.points.length - 1, Math.max(0, index))
}

const hovered = computed(() => (hoverIndex.value === null ? null : props.points[hoverIndex.value]))

/** Flip the tooltip to the left of the crosshair once it nears the right edge. */
const tooltipAnchor = computed(() => {
  if (hoverIndex.value === null) return { x: 0, flip: false }
  const x = xFor(hoverIndex.value)
  return { x, flip: x > PAD.left + plotW * 0.62 }
})

const empty = computed(() => props.points.every((point) => point.value === 0))
</script>

<template>
  <div class="trend">
    <svg
      ref="svg"
      class="trend__svg"
      :viewBox="`0 0 ${W} ${H}`"
      role="img"
      :aria-label="`${measure} for each day from ${points[0]?.date ?? ''} to ${points[points.length - 1]?.date ?? ''}`"
      @pointermove="onMove"
      @pointerleave="hoverIndex = null"
    >
      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="var(--adm-ramp-5)" stop-opacity="0.32" />
          <stop offset="1" stop-color="var(--adm-ramp-5)" stop-opacity="0.02" />
        </linearGradient>
      </defs>

      <!-- Grid first, so every mark sits on top of it. -->
      <g class="trend__grid">
        <line v-for="tick in ticks" :key="tick.y" :x1="PAD.left" :x2="W - PAD.right" :y1="tick.y" :y2="tick.y" />
      </g>
      <g class="trend__ylabels">
        <text v-for="tick in ticks" :key="`l-${tick.y}`" :x="PAD.left - 8" :y="tick.y + 3.5">
          {{ format(tick.value) }}
        </text>
      </g>

      <path v-if="!empty" :d="areaPath" :fill="`url(#${gradientId})`" />
      <path
        v-if="!empty"
        class="trend__line"
        :d="linePath"
        fill="none"
        stroke="var(--adm-ramp-6)"
        stroke-width="2"
        stroke-linejoin="round"
        stroke-linecap="round"
        vector-effect="non-scaling-stroke"
      />

      <g class="trend__xlabels">
        <text v-for="tick in dateTicks" :key="tick.date" :x="xFor(tick.index)" :y="H - 8">
          {{ axisDate(tick.date) }}
        </text>
      </g>

      <!-- Crosshair. Drawn last so it is never behind the area fill. -->
      <g v-if="hoverIndex !== null && hovered">
        <line
          class="trend__cross"
          :x1="xFor(hoverIndex)"
          :x2="xFor(hoverIndex)"
          :y1="PAD.top"
          :y2="PAD.top + plotH"
        />
        <!-- The 2px surface ring is what keeps the marker readable where it
             lands on top of the line it belongs to. -->
        <circle :cx="xFor(hoverIndex)" :cy="yFor(hovered.value)" r="5" fill="var(--adm-ramp-6)" stroke="var(--sf-paper)" stroke-width="2" />
      </g>
    </svg>

    <div
      v-if="hovered"
      class="trend__tip"
      :style="{
        left: `${(tooltipAnchor.x / W) * 100}%`,
        transform: tooltipAnchor.flip ? 'translate(-100%, 0)' : 'none',
      }"
    >
      <strong>{{ format(hovered.value) }}</strong>
      <span>{{ measure }}</span>
      <small>{{ axisDate(hovered.date) }}</small>
    </div>

    <p v-if="empty" class="trend__empty">Nothing recorded in this window yet.</p>
  </div>
</template>

<style scoped>
.trend {
  position: relative;
}

.trend__svg {
  display: block;
  width: 100%;
  height: auto;
  touch-action: none;
}

.trend__grid line {
  stroke: var(--adm-grid);
  stroke-width: 1;
}

.trend__ylabels text {
  fill: var(--adm-axis);
  font-size: 10.5px;
  text-anchor: end;
}

.trend__xlabels text {
  fill: var(--adm-axis);
  font-size: 10.5px;
  text-anchor: middle;
}

.trend__cross {
  stroke: var(--sf-faint);
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

.trend__tip {
  position: absolute;
  top: 4px;
  display: grid;
  gap: 1px;
  padding: 7px 10px;
  border-radius: 9px;
  background: var(--sf-forest);
  color: #fff;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
  box-shadow: 0 6px 18px rgba(23, 35, 28, 0.25);
}

.trend__tip strong {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.trend__tip span {
  color: rgba(255, 255, 255, 0.72);
}

.trend__tip small {
  color: rgba(255, 255, 255, 0.55);
  font-size: 11px;
}

.trend__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  margin: 0;
  color: var(--sf-faint);
  font-size: 13px;
  pointer-events: none;
}
</style>
