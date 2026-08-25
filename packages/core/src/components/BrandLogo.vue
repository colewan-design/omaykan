<script setup lang="ts">
// Brand lockup: the pine-basket mark beside a two-tone lowercase wordmark.
//
// Structured the way the FreshDirect wordmark is — one run-together word,
// heavy rounded lowercase, tight tracking, split across two colours at the
// syllable break. "omay" carries the accent colour, "kan" the neutral.
withDefaults(
  defineProps<{
    /** `dark` = sits on the deep-green bar; `light` = on white. */
    variant?: 'dark' | 'light'
    /** Wordmark cap height in px; the mark scales with it. */
    size?: number
    /** Hide the wordmark and show only the basket (favicon-style lockup). */
    markOnly?: boolean
  }>(),
  { variant: 'dark', size: 19, markOnly: false },
)
</script>

<template>
  <span class="brand" :class="`brand--${variant}`" :style="{ '--brand-size': `${size}px` }">
    <img
      class="brand__mark"
      :src="variant === 'dark' ? '/logo-mark-light.png' : '/logo-mark.png'"
      alt=""
      width="512"
      height="512"
    />
    <span v-if="!markOnly" class="brand__word">
      <span class="brand__word-a">omay</span><span class="brand__word-b">kan</span>
    </span>
    <span v-else class="brand__sr">Omaykan</span>
  </span>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: calc(var(--brand-size) * 0.42);
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
}

.brand__mark {
  width: calc(var(--brand-size) * 1.72);
  height: calc(var(--brand-size) * 1.72);
  object-fit: contain;
  flex-shrink: 0;
}

.brand__word {
  font-family: 'Nunito', 'Outfit', system-ui, sans-serif;
  font-size: var(--brand-size);
  font-weight: 900;
  /* Tight, like the reference — the two colours carry the word break, so the
     letters can close right up. */
  letter-spacing: -0.045em;
  font-feature-settings: 'kern' 1;
}

/* On the deep-green bar: lime + white. */
.brand--dark .brand__word-a { color: #bbf451; }
.brand--dark .brand__word-b { color: #ffffff; }

/* On white: brand green + near-black. */
.brand--light .brand__word-a { color: #16a34a; }
.brand--light .brand__word-b { color: #1a1a1a; }

.brand__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
