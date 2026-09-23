<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDownRight, ArrowUpRight } from '@lucide/vue'
import { percentDelta } from './format'

// One headline number with the change beside it.
//
// A single current value is a stat tile, not a one-bar chart — see the
// dataviz form heuristic. The delta is the only decoration, and it earns its
// place because "₱482,350" and "₱482,350, down a third" are different facts.

const props = defineProps<{
  label: string
  value: string
  /**
   * Percent change against the previous window of the same length.
   *
   * Three states, and they mean different things. A number is a change; `null`
   * is "this figure is windowed, but there is no earlier window to compare it
   * against", which is worth saying; and omitting the prop entirely is "this
   * figure is not windowed at all" — how many products are listed is a fact
   * about now, and a versus-last-month line under it would be noise.
   */
  delta?: number | null
  /** What the delta is measured against, e.g. "vs last 30 days". */
  deltaNote?: string
  loading?: boolean
}>()

const windowed = computed(() => props.delta !== undefined)
const deltaText = computed(() => percentDelta(props.delta ?? null))

/**
 * Up is good and down is not, for every figure this tile is used for (sales,
 * orders, customers). Nothing here counts something you want less of; if
 * something ever does, this needs a prop rather than a quiet inversion.
 */
const rising = computed(() => (props.delta ?? 0) >= 0)
</script>

<template>
  <article class="tile adm-card">
    <p class="tile__label">{{ label }}</p>

    <p v-if="loading" class="adm-skeleton tile__ghost" aria-hidden="true"></p>
    <p v-else class="tile__value adm-num">{{ value }}</p>

    <p v-if="!loading && windowed" class="tile__delta" :class="rising ? 'tile__delta--up' : 'tile__delta--down'">
      <template v-if="deltaText">
        <component :is="rising ? ArrowUpRight : ArrowDownRight" :size="14" aria-hidden="true" />
        <span>{{ deltaText }}</span>
        <small v-if="deltaNote">{{ deltaNote }}</small>
      </template>
      <!-- No previous period to compare against. An invented "+100%" is the
           kind of number that ends up in a pitch deck. -->
      <small v-else class="tile__nodelta">No earlier period to compare</small>
    </p>
  </article>
</template>

<style scoped>
.tile {
  display: grid;
  align-content: start;
  gap: 5px;
  padding: 15px 16px;
}

.tile__label {
  margin: 0;
  color: var(--sf-muted);
  font-size: 12.5px;
  font-weight: 600;
}

.tile__value {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.15;
  color: var(--sf-ink);
}

.tile__ghost {
  height: 27px;
  width: 70%;
  margin: 2px 0;
}

.tile__delta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}

.tile__delta--up { color: #0a6b0a; }
.tile__delta--down { color: #9c2626; }

.tile__delta small {
  color: var(--sf-faint);
  font-weight: 500;
}

.tile__nodelta {
  font-size: 11.5px;
}
</style>
