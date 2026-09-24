<script setup lang="ts">
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'

/**
 * The campaign page's footer: the ridge line, the wordmark, and the three
 * words the poster ends on.
 *
 * Deliberately not FdFooter. That one is six columns of shopper navigation —
 * aisles, delivery rates, order tracking — which is the wrong map entirely for
 * somebody who came here to sell. This keeps the few links a seller might
 * actually want and gets out of the way.
 */
</script>

<template>
  <footer class="ff">
    <!-- In the flow rather than absolutely placed over the footer: the footer's
         own background would sit behind the sky half of this and paint it the
         same forest green as the ridges, which is exactly as visible as no
         ridge at all. Here the page above shows through instead. -->
    <svg class="ff__ridge" viewBox="0 0 1440 120" preserveAspectRatio="none" aria-hidden="true" focusable="false">
      <path d="M0 120V64l120-30 110 26 128-38 122 34 140-42 128 40 126-32 134 36 118-28 114 30v100Z" fill="#245c43" />
      <path d="M0 120V86l134-24 126 20 140-28 128 26 136-32 130 30 134-22 128 26 122-18 162 24v112Z" fill="#0d3424" />
    </svg>

    <div class="ff__inner">
    <div class="ff__body">
      <p class="ff__script" aria-hidden="true">Same Mountains<br />More Opportunities</p>

      <div class="ff__brand">
        <BrandLogo variant="dark" :size="24" />
        <p class="ff__tag">Local businesses. A brighter Baguio.</p>
      </div>

      <nav class="ff__links" aria-label="Omaykan">
        <a href="/">Marketplace</a>
        <a href="/about">About Omaykan</a>
        <a href="/seller/signup">Set up a shop yourself</a>
        <a href="/rider">Ride with Omaykan</a>
        <a :href="supportMailto('Founding seller question')">{{ SUPPORT_EMAIL }}</a>
      </nav>
    </div>

    <p class="ff__motto">
      <span>Local Businesses</span>
      <span aria-hidden="true">·</span>
      <span>Local People</span>
      <span aria-hidden="true">·</span>
      <span>A Stronger Community</span>
    </p>
    </div>
  </footer>
</template>

<style scoped>
.ff {
  margin-top: 60px;
  color: #dfeadf;
}

.ff__ridge {
  display: block;
  width: 100%;
  height: 120px;
  /* Closes the hairline of page background that subpixel rounding leaves
     between the bottom of the svg and the panel under it. */
  margin-bottom: -1px;
}

.ff__inner { background: var(--sf-forest-deep); }

.ff__body {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 40px;
  flex-wrap: wrap;
  padding: 8px var(--fd-inset) 26px;
}

.ff__script {
  order: 3;
  margin: 0;
  font-family: var(--sf-script);
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: #9fc7ab;
}

.ff__brand {
  order: 1;
  display: grid;
  gap: 6px;
}

.ff__tag {
  margin: 0;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #7ea98c;
}

.ff__links {
  order: 2;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 26px;
  max-width: 560px;
}

.ff__links a {
  font-size: 13px;
  font-weight: 600;
  color: #cfe0d2;
  transition: color 140ms;
}

.ff__links a:hover {
  color: #fff;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.ff__motto {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin: 0;
  padding: 16px var(--fd-inset) 26px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #8fb79c;
}

@media (max-width: 900px) {
  .ff__ridge { height: 90px; }
  .ff__body { gap: 26px; }
  .ff__script { order: 2; font-size: 23px; }
  .ff__links { order: 3; }
}
</style>
