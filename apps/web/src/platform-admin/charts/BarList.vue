<script setup lang="ts">
import { computed } from 'vue'

/*
 * "Compare these magnitudes" — categories by takings, towns by order count.
 *
 * Horizontal bars rather than a donut, and one hue rather than eight.
 *
 * The mockup drew both of these as pie-like rings with a colour per slice.
 * Past about seven slices those colours stop being tellable apart (the
 * eighth is a generated hue that collapses under CVD), and an angle is a
 * poorer judgement of size than a length — which is the whole job here. A
 * sorted bar with the label beside it answers "which is biggest, and by how
 * much" in one pass, and the category names get room to be read.
 *
 * The ramp is sequential: darker means more. Colour carries no identity here,
 * so nothing breaks when a filter changes which rows survive.
 */

const props = defineProps<{
  rows: { label: string; value: number }[]
  /** Formats a value for the row's right-hand figure. */
  format: (value: number) => string
  /** Announced to screen readers as the thing being measured. */
  measure: string
}>()

const max = computed(() => Math.max(1, ...props.rows.map((row) => row.value)))
const total = computed(() => props.rows.reduce((sum, row) => sum + row.value, 0))

/**
 * Darkest for the largest, lightening down the list — but never so light it
 * stops reading against cream, so the ramp is walked from the dark end and
 * stops before the two palest steps.
 */
function shade(index: number): string {
  const step = Math.max(1, Math.min(6, 6 - Math.floor((index / Math.max(1, props.rows.length - 1)) * 4)))
  return `var(--adm-ramp-${step})`
}

function share(value: number): string {
  if (total.value <= 0) return '0%'
  return `${Math.round((value / total.value) * 100)}%`
}
</script>

<template>
  <div class="bars">
    <ul v-if="rows.length > 0" class="bars__list">
      <li v-for="(row, index) in rows" :key="row.label" class="bars__row">
        <span class="bars__label" :title="row.label">{{ row.label }}</span>

        <span class="bars__track">
          <span
            class="bars__fill"
            :style="{ width: `${Math.max(2, (row.value / max) * 100)}%`, background: shade(index) }"
          ></span>
        </span>

        <span class="bars__value adm-num">
          {{ format(row.value) }}
          <small>{{ share(row.value) }}</small>
        </span>
      </li>
    </ul>

    <p v-else class="bars__empty">No {{ measure }} in this window yet.</p>
  </div>
</template>

<style scoped>
.bars__list {
  display: grid;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.bars__row {
  display: grid;
  grid-template-columns: minmax(72px, 26%) minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.bars__label {
  font-size: 13px;
  color: var(--sf-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bars__track {
  height: 9px;
  border-radius: 999px;
  background: var(--sf-sand);
  overflow: hidden;
}

.bars__fill {
  display: block;
  height: 100%;
  /* 4px rounded data-end, anchored square at the baseline. */
  border-radius: 0 4px 4px 0;
}

.bars__value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.bars__value small {
  color: var(--sf-faint);
  font-weight: 500;
  font-size: 11.5px;
}

.bars__empty {
  margin: 0;
  padding: 22px 0;
  color: var(--sf-faint);
  font-size: 13px;
  text-align: center;
}

@media (max-width: 520px) {
  .bars__row {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas: 'label value' 'track track';
    row-gap: 5px;
  }

  .bars__label { grid-area: label; }
  .bars__value { grid-area: value; }
  .bars__track { grid-area: track; }
}
</style>
