<script setup lang="ts">
import { onMounted, ref } from 'vue'

// Full-width video banner at the top of the landing page, modelled on the
// FreshDirect reference: a short, wide strip of footage behind a left-weighted
// scrim, the headline over it, and a play/pause control in the bottom-left.
//
// The footage is a Manila market stall, which is the literal subject of the
// pitch further down the page ("Every shop here is a real counter somewhere in
// the city") rather than generic stock.
//
// The file is encoded already cropped to the banner's 1600x380, not letterboxed
// by CSS from the 16:9 original — at this shape more than half the source frame
// is off-screen, so shipping the full frame would mean paying to download
// pixels nobody sees. It also fixes the framing here rather than leaving it to
// object-position: the crop keeps the stallholder's head and one handwritten
// price tag, which a centred crop would both miss.

const video = ref<HTMLVideoElement | null>(null)

// Read once, before the first render, so the `autoplay` attribute is present
// in the initial DOM for the browsers that honour it. Someone who asked their
// OS to stop animations gets the poster frame and a button, not motion.
const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const playing = ref(!reduceMotion)

function toggle() {
  const el = video.value
  if (!el) return
  if (el.paused) void el.play().catch(() => { playing.value = false })
  else el.pause()
}

onMounted(() => {
  const el = video.value
  if (!el) return

  // Belt and braces: an unmuted play() is rejected outright, and the `muted`
  // content attribute alone has historically not been enough in every engine.
  el.muted = true
  if (reduceMotion) return

  // Muted autoplay is allowed everywhere, but a data-saver or battery-saver
  // mode can still refuse it. Reflect what actually happened so the button
  // doesn't offer to "pause" a video that never started.
  void el.play().catch(() => { playing.value = false })
})
</script>

<template>
  <section class="fd-hero">
    <div class="fd-hero__frame">
      <video
        ref="video"
        class="fd-hero__video"
        poster="/delivery/hero-market-poster.webp"
        preload="metadata"
        :autoplay="!reduceMotion"
        muted
        loop
        playsinline
        disablepictureinpicture
        aria-hidden="true"
        tabindex="-1"
        @play="playing = true"
        @pause="playing = false"
      >
        <source src="/delivery/hero-market.mp4" type="video/mp4" />
      </video>

      <div class="fd-hero__scrim" aria-hidden="true"></div>

      <button
        type="button"
        class="fd-hero__toggle"
        :aria-label="playing ? 'Pause background video' : 'Play background video'"
        @click="toggle"
      >
        <svg v-if="playing" width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5.14v13.72a1 1 0 0 0 1.54.84l10.3-6.86a1 1 0 0 0 0-1.68L9.54 4.3A1 1 0 0 0 8 5.14Z"/></svg>
      </button>
    </div>

    <div class="fd-hero__copy">
      <h1 class="fd-hero__title">Shop the market, from home.</h1>
      <p class="fd-hero__sub">
        Order from the carinderias, sari-sari stores, and market stalls around you — at the
        same price they charge at the counter. No commission taken, nothing marked up.
      </p>
    </div>
  </section>
</template>

<style scoped>
.fd-hero {
  position: relative;
  margin-bottom: 48px;
}

.fd-hero__frame {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  background: #06240f;
  /* The encoded size of the file, so the banner is the video's own shape and
     object-fit has nothing left to crop. */
  aspect-ratio: 1600 / 380;
  /* Below roughly 1150px the ratio alone would make the strip shorter than the
     overlaid copy. This lets the frame grow instead, trading a little side
     crop for text that still fits. */
  min-height: 240px;
  /* Not redundant with the block default: with a definite min-height and an
     aspect-ratio, an auto width gets *derived from the height* (240 x 4.21 =
     1010px), which overflows the column and puts a horizontal scrollbar on the
     page. Pinning the width keeps the ratio working in one direction only. */
  width: 100%;
}

.fd-hero__video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Market footage is bright and busy, so the headline needs its own ground —
   heaviest at the left where the text sits, clearing by the midpoint so the
   stall is still legible. */
.fd-hero__scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    rgba(6, 36, 15, 0.92) 0%,
    rgba(6, 36, 15, 0.78) 34%,
    rgba(6, 36, 15, 0.3) 64%,
    rgba(6, 36, 15, 0.06) 100%
  );
}

.fd-hero__copy {
  position: absolute;
  inset: 0;
  z-index: 1;
  max-width: 620px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  padding: 0 clamp(24px, 4vw, 56px);
}

.fd-hero__title {
  margin: 0;
  font-size: clamp(1.6rem, 3vw, 2.6rem);
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: -0.03em;
  color: #fff;
  text-wrap: balance;
}

.fd-hero__sub {
  margin: 0;
  max-width: 34em;
  font-size: clamp(0.9rem, 1.1vw, 1.05rem);
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.88);
}

.fd-hero__toggle {
  position: absolute;
  left: 18px;
  bottom: 16px;
  z-index: 2;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 999px;
  background: rgba(6, 36, 15, 0.45);
  color: #fff;
  cursor: pointer;
  backdrop-filter: blur(2px);
  transition: background 150ms, transform 150ms;
}
.fd-hero__toggle:hover { background: rgba(6, 36, 15, 0.75); transform: scale(1.06); }
.fd-hero__toggle:focus-visible { outline: 2px solid #bbf451; outline-offset: 2px; }

/* Phones: a banner this wide is only ~120px tall here, which is far too short
   to hold the copy. The strip stays a strip and the words move underneath it,
   so the hero keeps the reference's height without squeezing the text. */
@media (max-width: 720px) {
  .fd-hero { margin-bottom: 32px; }
  .fd-hero__frame { aspect-ratio: 3 / 1; min-height: 0; }
  .fd-hero__scrim { display: none; }

  .fd-hero__copy {
    position: static;
    max-width: none;
    padding: 16px 0 0;
    gap: 8px;
  }
  .fd-hero__title { font-size: 1.6rem; color: #1a1a1a; }
  .fd-hero__sub { font-size: 0.95rem; color: #4a5b52; }

  .fd-hero__toggle { left: 12px; bottom: 12px; width: 30px; height: 30px; }
}
</style>
