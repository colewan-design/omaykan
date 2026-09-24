<script setup lang="ts">
import { computed } from 'vue'

// Plain columns against a ruled ground. Laid out with grid rather than drawn,
// so it reflows with the card and needs nothing measured.

const props = withDefaults(
  defineProps<{
    bars: { key: string; label: string; value: number }[]
    formatValue: (value: number) => string
    /** Print every nth label; the rest are there for screen readers only. */
    labelEvery?: number
    /** A floor: the plot takes whatever height the card has spare. */
    minHeight?: number
  }>(),
  { labelEvery: 3, minHeight: 142 },
)

function niceCeiling(value: number): number {
  if (value <= 0) return 1
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const normalized = value / magnitude
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10
  return step * magnitude
}

const ceiling = computed(() => niceCeiling(Math.max(...props.bars.map((bar) => bar.value), 0)))
const ticks = computed(() => [1, 0.666, 0.333, 0].map((fraction) => ceiling.value * fraction))
const peak = computed(() => Math.max(...props.bars.map((bar) => bar.value), 0))

const columns = computed(() =>
  props.bars.map((bar, index) => ({
    ...bar,
    index,
    /** A column with takings in it never reads as an empty slot. */
    percent: bar.value > 0 ? Math.max((bar.value / ceiling.value) * 100, 2) : 0,
    isPeak: peak.value > 0 && bar.value === peak.value,
  })),
)
</script>

<template>
  <div class="columns">
    <div class="columns__scale" aria-hidden="true">
      <span v-for="(tick, index) in ticks" :key="index">{{ formatValue(tick) }}</span>
    </div>

    <div class="columns__plot" :style="{ minHeight: `${minHeight}px` }">
      <div class="columns__rules" aria-hidden="true"><i v-for="index in 4" :key="index" /></div>
      <div class="columns__bars">
        <div v-for="column in columns" :key="column.key" class="columns__bar">
          <i :class="{ 'is-peak': column.isPeak }" :style="{ height: `${column.percent}%` }" />
          <span class="sr-only">{{ column.label }}: {{ formatValue(column.value) }}</span>
        </div>
      </div>
    </div>

    <!-- Labels sit over the middle of their column rather than inside it: a
         column can be narrower than the word under it, and a slot that clips
         turns "11 AM" into "11". -->
    <div class="columns__axis" aria-hidden="true">
      <template v-for="column in columns" :key="column.key">
        <span v-if="column.index % labelEvery === 0" :style="{ left: `${((column.index + 0.5) / columns.length) * 100}%` }">
          {{ column.label }}
        </span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.columns {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr) auto;
  align-items: stretch;
  gap: 4px 10px;
  min-width: 0;
  /* Takes the card's leftover room rather than leaving a gap under itself. */
  flex: 1 1 auto;
}

.columns__scale {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-bottom: 1px;
  color: var(--text-tertiary);
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

.columns__plot {
  position: relative;
  min-width: 0;
}

.columns__rules {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.columns__rules i {
  display: block;
  height: 1px;
  background: var(--chart-grid, rgba(120, 120, 128, 0.22));
}

.columns__bars {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 100%;
}

.columns__bar {
  display: flex;
  min-width: 0;
  flex: 1 1 0;
  align-items: flex-end;
  height: 100%;
}

.columns__bar i {
  width: 100%;
  min-height: 2px;
  border-radius: 3px 3px 1px 1px;
  background: var(--chart-accent-soft, rgba(26, 107, 60, 0.45));
}

.columns__bar i.is-peak {
  background: var(--chart-accent, #1a6b3c);
}

.columns__axis {
  position: relative;
  grid-column: 2;
  height: 14px;
  color: var(--text-tertiary);
  font-size: 10.5px;
}

.columns__axis span {
  position: absolute;
  top: 0;
  white-space: nowrap;
  transform: translateX(-50%);
}
</style>
