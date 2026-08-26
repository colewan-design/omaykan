<script setup lang="ts">
/*
 * One figure, with what it means underneath.
 *
 * `attention` is not decoration: it marks the tiles that represent work
 * waiting on a human, which is the only thing on the dashboard an operator has
 * to act on. Everything else is context.
 */

defineProps<{
  label: string
  value: string | number
  note?: string
  attention?: boolean
}>()
</script>

<template>
  <div class="pa-tile" :class="{ 'pa-tile--attention': attention }">
    <p class="pa-tile__label">{{ label }}</p>
    <p class="pa-tile__value">{{ value }}</p>
    <p v-if="note" class="pa-tile__note">{{ note }}</p>
  </div>
</template>
