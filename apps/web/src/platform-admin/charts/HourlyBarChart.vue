<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ rows: Array<{ hour: number; orders: number }> }>()
const maximum = computed(() => Math.max(1, ...props.rows.map((row) => row.orders)))
function label(hour: number): string {
  if (hour === 0) return '12AM'
  if (hour < 12) return `${hour}AM`
  if (hour === 12) return '12PM'
  return `${hour - 12}PM`
}
</script>

<template>
  <div class="hours" role="img" aria-label="Orders by hour of day">
    <div class="hours__plot">
      <span v-for="row in rows" :key="row.hour" class="hours__slot" :title="`${label(row.hour)}: ${row.orders} orders`"><i :style="{ height: `${Math.max(2, (row.orders / maximum) * 100)}%` }"></i></span>
    </div>
    <div class="hours__labels"><span v-for="row in rows" :key="row.hour">{{ row.hour % 2 === 0 ? label(row.hour) : '' }}</span></div>
  </div>
</template>

<style scoped>
.hours { display: grid; gap: 5px; }
.hours__plot { display: grid; grid-template-columns: repeat(24, minmax(3px, 1fr)); align-items: end; gap: 5px; height: 86px; padding: 8px 4px 0; border-bottom: 1px solid #dfe3df; }
.hours__slot { display: flex; align-items: end; height: 100%; }
.hours__slot i { display: block; width: 100%; border-radius: 3px 3px 0 0; background: #5d9772; }
.hours__labels { display: grid; grid-template-columns: repeat(24, minmax(3px, 1fr)); gap: 5px; color: #718096; font-size: 8px; }
.hours__labels span { overflow: visible; white-space: nowrap; }
</style>
