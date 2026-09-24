<script setup lang="ts">
import { computed } from 'vue'

// A ring of shares. No charting library: a slice is one circle with a dash
// pattern the length of its arc, offset by everything drawn before it.

const props = withDefaults(
  defineProps<{
    slices: { key: string; label: string; value: number; color: string }[]
    /** The figure in the hole, and what it is. */
    centerValue: string
    centerLabel: string
    size?: number
    thickness?: number
  }>(),
  { size: 164, thickness: 19 },
)

const radius = computed(() => (props.size - props.thickness) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const total = computed(() => props.slices.reduce((sum, slice) => sum + slice.value, 0))
const drawn = computed(() => props.slices.filter((slice) => slice.value > 0))

/**
 * Each slice's arc and where it starts, walking the ring from twelve o'clock.
 *
 * The gap between slices comes off the end of each arc, and only when there is
 * more than one — a single slice with a bite out of it would read as a share
 * of something rather than the whole of it.
 */
const arcs = computed(() => {
  if (total.value <= 0) return []
  const gap = drawn.value.length > 1 ? 2.5 : 0
  let offset = 0
  return drawn.value.map((slice) => {
    const length = (slice.value / total.value) * circumference.value
    const arc = { ...slice, length: Math.max(length - gap, 0.5), offset }
    offset += length
    return arc
  })
})
</script>

<template>
  <div class="donut" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :viewBox="`0 0 ${size} ${size}`" :width="size" :height="size" role="img" aria-hidden="true">
      <g :transform="`rotate(-90 ${size / 2} ${size / 2})`">
        <circle
          class="donut__track"
          :cx="size / 2"
          :cy="size / 2"
          :r="radius"
          fill="none"
          :stroke-width="thickness"
        />
        <circle
          v-for="arc in arcs"
          :key="arc.key"
          :cx="size / 2"
          :cy="size / 2"
          :r="radius"
          fill="none"
          :stroke="arc.color"
          :stroke-width="thickness"
          stroke-linecap="butt"
          :stroke-dasharray="`${arc.length} ${circumference - arc.length}`"
          :stroke-dashoffset="-arc.offset"
        />
      </g>
    </svg>
    <div class="donut__center">
      <strong>{{ centerValue }}</strong>
      <small>{{ centerLabel }}</small>
    </div>
  </div>
</template>

<style scoped>
.donut {
  position: relative;
  flex: none;
  display: grid;
  place-items: center;
}

.donut__track {
  stroke: var(--chart-track, rgba(120, 120, 128, 0.16));
}

.donut__center {
  position: absolute;
  display: grid;
  gap: 2px;
  max-width: 68%;
  text-align: center;
}

.donut__center strong {
  font-size: 17px;
  line-height: 21px;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.donut__center small {
  color: var(--text-secondary);
  font-size: 10.5px;
}
</style>
