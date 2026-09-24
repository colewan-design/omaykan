<script setup lang="ts">
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import MountainMark from '@pos/web/landing/MountainMark.vue'

/**
 * The campaign page's own bar.
 *
 * Not FdHeader, which carries a cart, a category rail and a search box — none
 * of which mean anything to a shop owner who came here from a flyer. Not
 * MerchantHeader either: that one's anchors point at the sections of
 * /seller/signup.
 *
 * The "Sign Up" link is an in-page anchor rather than a route. The form is
 * already on this page, and sending somebody to /seller/signup instead would
 * put them in front of the self-serve builder — a different product decision
 * from the one this campaign is making, which is that we set the store up.
 */
</script>

<template>
  <header class="fh">
    <div class="fh__inner">
      <a class="fh__brand" href="/" aria-label="Omaykan home">
        <MountainMark :size="52" />
        <span class="fh__lockup">
          <BrandLogo variant="light" :size="24" />
          <span class="fh__tag">Local businesses. A brighter Baguio.</span>
        </span>
      </a>

      <nav class="fh__nav" aria-label="On this page">
        <a href="#how">How it works</a>
        <a href="#perks">Benefits</a>
        <a href="#faq">FAQ</a>
      </nav>

      <a class="fh__cta" href="#apply">Sign Up</a>

      <p class="fh__aside" aria-hidden="true">
        From our local businesses<br />to a stronger community.
      </p>
    </div>
  </header>
</template>

<style scoped>
.fh {
  position: sticky;
  top: 0;
  z-index: 40;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: saturate(150%) blur(14px);
  border-bottom: 1px solid rgba(22, 75, 52, 0.08);
}

.fh__inner {
  display: flex;
  align-items: center;
  gap: 32px;
  /* Two lines of brand lockup — wordmark over the strapline — need more than
     the 66px the other marketing headers use for a single row. */
  height: 74px;
  padding-inline: var(--fd-inset);
}

.fh__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.fh__brand :deep(.mtn) {
  color: var(--sf-forest);
  flex-shrink: 0;
}

.fh__lockup {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.fh__tag {
  font-size: 9.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sf-faint);
}

.fh__nav {
  display: flex;
  align-items: center;
  gap: 30px;
  margin-left: 46px;
}

.fh__nav a {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--sf-ink);
  transition: color 140ms;
}

.fh__nav a:hover { color: var(--sf-leaf); }

.fh__cta {
  flex-shrink: 0;
  padding: 9px 18px;
  border-radius: 9px;
  background: var(--sf-forest);
  color: #fff;
  font-size: 13.5px;
  font-weight: 800;
  transition: background-color 140ms;
}

.fh__cta:hover { background: var(--sf-forest-deep); }

/* The line from the poster. Decorative, and the first thing to go. */
.fh__aside {
  margin: 0 0 0 auto;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
  text-align: right;
  color: var(--sf-muted);
}

@media (max-width: 1240px) {
  .fh__aside { display: none; }
  .fh__nav { margin-left: auto; }
}

@media (max-width: 860px) {
  .fh__inner { gap: 16px; }
  .fh__nav { display: none; }
  .fh__cta { margin-left: auto; }
}

@media (max-width: 420px) {
  .fh__brand :deep(.mtn) { display: none; }
  .fh__tag { display: none; }
}
</style>
