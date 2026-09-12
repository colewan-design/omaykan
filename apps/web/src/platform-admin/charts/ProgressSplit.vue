<script setup lang="ts">
import { computed } from 'vue'
import { count } from '../format'
import { PROGRESS_LABELS } from '../format'

/*
 * How the orders on the platform divide across the four stages.
 *
 * A hero figure with a stacked bar under it, rather than the mockup's donut.
 * The number in the middle of a donut is the thing people actually read off
 * it, so it is promoted to a real hero figure; the split below it is a single
 * stacked bar, which compares four parts of one whole better than four arcs
 * and needs no legend line to connect a colour to a word — the word is on the
 * row beside the swatch.
 *
 * The four colours were chosen with the palette validator rather than by eye
 * (see admin.css) and every one of them ships with its label, never alone.
 */

const props = defineProps<{
  counts: Record<string, number>
}>()

/** Fixed order: the sequence an order moves through, then the void. */
const ORDER = ['processing', 'shipped', 'completed', 'cancelled'] as const

const total = computed(() => ORDER.reduce((sum, key) => sum + (props.counts[key] ?? 0), 0))

const segments = computed(() =>
  ORDER.map((key) => ({
    key,
    label: PROGRESS_LABELS[key],
    value: props.counts[key] ?? 0,
    percent: total.value > 0 ? ((props.counts[key] ?? 0) / total.value) * 100 : 0,
  })),
)

const drawn = computed(() => segments.value.filter((segment) => segment.value > 0))
</script>

<template>
  <div class="split">
    <p class="split__hero">
      <strong class="adm-num">{{ count(total) }}</strong>
      <span>{{ total === 1 ? 'order' : 'orders' }} all time</span>
    </p>

    <div v-if="total > 0" class="split__bar" role="img" :aria-label="segments.map((s) => `${s.label} ${s.value}`).join(', ')">
      <span
        v-for="segment in drawn"
        :key="segment.key"
        class="split__seg"
        :class="`split__seg--${segment.key}`"
        :style="{ width: `${segment.percent}%` }"
      ></span>
    </div>
    <div v-else class="split__bar split__bar--empty" aria-hidden="true"></div>

    <ul class="split__legend">
      <li v-for="segment in segments" :key="segment.key">
        <span class="split__dot" :class="`split__dot--${segment.key}`" aria-hidden="true"></span>
        <span class="split__name">{{ segment.label }}</span>
        <span class="split__count adm-num">{{ count(segment.value) }}</span>
        <span class="split__pct adm-num">{{ total > 0 ? Math.round(segment.percent) : 0 }}%</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.split {
  display: grid;
  gap: 12px;
}

.split__hero {
  display: grid;
  gap: 1px;
  margin: 0;
}

.split__hero strong {
  font-family: var(--sf-serif);
  font-size: 2rem;
  font-weight: 700;
  line-height: 1.05;
}

.split__hero span {
  color: var(--sf-muted);
  font-size: 12.5px;
}

.split__bar {
  display: flex;
  /* The 2px surface gap between segments, so two adjacent fills never read as
     one longer one. */
  gap: 2px;
  height: 11px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--sf-sand);
}

.split__seg {
  display: block;
  height: 100%;
}

.split__seg--processing { background: var(--adm-processing); }
.split__seg--shipped { background: var(--adm-shipped); }
.split__seg--completed { background: var(--adm-completed); }
.split__seg--cancelled { background: var(--adm-cancelled); }

.split__legend {
  display: grid;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.split__legend li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 9px;
  font-size: 13px;
}

.split__dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
}

.split__dot--processing { background: var(--adm-processing); }
.split__dot--shipped { background: var(--adm-shipped); }
.split__dot--completed { background: var(--adm-completed); }
.split__dot--cancelled { background: var(--adm-cancelled); }

.split__name {
  color: var(--sf-ink);
}

.split__count {
  font-weight: 700;
}

.split__pct {
  min-width: 34px;
  color: var(--sf-faint);
  font-size: 12px;
  text-align: right;
}
</style>
