<script setup lang="ts">
// Line-art peaks, with the sun behind them if asked — the redesign's mark.
// Drawn rather than shipped as a file so it takes the colour of whatever it
// sits on: cream on the forest footer, cream on the terracotta panel.
withDefaults(defineProps<{ size?: number; sun?: boolean }>(), { size: 64, sun: true })
</script>

<template>
  <svg
    class="mtn"
    :width="size"
    :height="Math.round(size / 2)"
    viewBox="0 0 64 32"
    fill="none"
    aria-hidden="true"
  >
    <circle v-if="sun" cx="38" cy="11" r="6.5" fill="#d9a35b" />
    <path
      d="M2 30 20 10l10 10 10-12 22 22"
      stroke="currentColor"
      stroke-width="2.2"
      stroke-linejoin="round"
      stroke-linecap="round"
    />
    <path
      d="m12 30 8-9 6 6m8 3 6-12 10 12"
      stroke="currentColor"
      stroke-width="1.4"
      stroke-linejoin="round"
      stroke-linecap="round"
      opacity="0.7"
    />
  </svg>
</template>

<style scoped>
.mtn {
  display: block;
  flex-shrink: 0;
}
</style>
