<script setup lang="ts">
defineProps<{
  title: string
  summary: string
}>()
</script>

<template>
  <article class="chart-card">
    <!-- The header is a row only when something was put in it. A card with no
         action keeps the bare heading it has always rendered. -->
    <header v-if="$slots.action" class="chart-card__header">
      <h2 class="chart-card__title">{{ title }}</h2>
      <slot name="action" />
    </header>
    <h2 v-else class="chart-card__title">{{ title }}</h2>

    <div class="chart-card__body">
      <slot />
    </div>
    <p class="sr-only">{{ summary }}</p>
  </article>
</template>

<style scoped>
.chart-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: auto 1fr;
  gap: var(--space-4);
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-elevated) 94%, transparent);
}

.chart-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.chart-card__title {
  margin: 0;
  font: var(--type-headline);
  color: var(--text-primary);
}

.chart-card__body {
  min-height: 120px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.chart-card--wide {
  grid-column: 1 / -1;
}

@media (max-width: 780px) {
  .chart-card {
    padding: var(--space-4);
    gap: var(--space-3);
  }

  .chart-card__body {
    min-height: 80px;
  }
}
</style>
