<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
  eyebrow?: string
}>()
</script>

<template>
  <header class="hero">
    <div class="hero__inner">
      <div class="hero__copy">
        <p v-if="eyebrow" class="hero__eyebrow">{{ eyebrow }}</p>
        <h1 class="hero__title">{{ title }}</h1>
        <p v-if="subtitle" class="hero__sub">{{ subtitle }}</p>
      </div>

      <div class="hero__tools">
        <slot name="tools" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.hero {
  position: relative;
  margin: 12px var(--adm-gutter) 0;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--adm-radius);
  background: #12382b;
}

.hero__inner {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  min-height: 124px;
  padding: 20px 22px;
  box-sizing: border-box;
}

.hero__copy {
  min-width: 0;
}

.hero__eyebrow {
  margin: 0 0 5px;
  color: rgba(237, 247, 240, 0.72);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.hero__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: clamp(1.35rem, 2.4vw, 1.75rem);
  font-weight: 700;
  line-height: 1.15;
  color: #fff;
}

.hero__sub {
  margin: 6px 0 0;
  max-width: 66ch;
  color: rgba(246, 250, 247, 0.82);
  font-size: 13.5px;
  line-height: 1.45;
}

.hero__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

@media (max-width: 560px) {
  .hero { margin: 16px 16px 0; }
  .hero__inner { padding: 16px; }
}
</style>
