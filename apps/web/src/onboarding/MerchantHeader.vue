<script setup lang="ts">
import BrandLogo from '@pos/core/components/BrandLogo.vue'

// /seller/signup had no chrome at all: a merchant who landed here from a shared link
// had no way back to the marketplace and no way to reach the form except
// scrolling past the whole pitch. This is the merchant-side header — separate
// from FdHeader, which carries a cart and a category rail that mean nothing on
// a registration page.

withDefaults(
  defineProps<{
    /** After signup succeeds the pitch is unmounted, so its section anchors
        would scroll to nothing. Drop them and keep only the brand. */
    compact?: boolean
  }>(),
  { compact: false },
)
</script>

<template>
  <header class="mh">
    <div class="mh__inner">
      <a class="mh__brand" href="/">
        <BrandLogo variant="light" :size="20" />
        <span class="mh__tag">for merchants</span>
      </a>

      <template v-if="!compact">
        <nav class="mh__nav" aria-label="Sections">
          <a href="#what">What you get</a>
          <a href="#how">How it works</a>
          <a href="#counter">Behind the counter</a>
        </nav>

        <div class="mh__actions">
          <a class="mh__link" href="/">Back to shopping</a>
          <a class="mh__cta" href="#register">Start selling free</a>
        </div>
      </template>

      <div v-else class="mh__actions">
        <a class="mh__link" href="/">Back to shopping</a>
      </div>
    </div>
  </header>
</template>

<style scoped>
.mh {
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: saturate(160%) blur(14px);
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
}

.mh__inner {
  display: flex;
  align-items: center;
  gap: 28px;
  max-width: 1280px;
  height: 68px;
  margin: 0 auto;
  padding: 0 40px;
}

.mh__brand {
  display: inline-flex;
  align-items: baseline;
  gap: 9px;
  text-decoration: none;
  flex-shrink: 0;
}

/* Lowercase and quiet — it qualifies the brand, it isn't a second brand. */
.mh__tag {
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: #8a938e;
}

.mh__nav {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-left: auto;
}

.mh__nav a,
.mh__link {
  font-size: 13.5px;
  font-weight: 600;
  color: #5b6b62;
  text-decoration: none;
  transition: color 150ms;
}

.mh__nav a:hover,
.mh__link:hover { color: #1a1a1a; }

.mh__actions {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-left: auto;
  flex-shrink: 0;
}

.mh__nav + .mh__actions { margin-left: 0; }

.mh__cta {
  display: inline-flex;
  align-items: center;
  padding: 10px 20px;
  border-radius: 980px;
  /* Dark ink on the bright green: white would be ~2.2:1. */
  background: #22c55e;
  color: #06240f;
  font-size: 13.5px;
  font-weight: 800;
  letter-spacing: -0.01em;
  text-decoration: none;
  transition: filter 150ms, transform 150ms;
}

.mh__cta:hover { filter: brightness(1.06); transform: translateY(-1px); }

@media (max-width: 1080px) {
  .mh__nav { display: none; }
}

@media (max-width: 720px) {
  .mh__inner { padding: 0 20px; gap: 16px; }
  .mh__tag,
  .mh__link { display: none; }
}
</style>
