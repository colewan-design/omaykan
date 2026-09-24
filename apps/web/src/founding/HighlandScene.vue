<script setup lang="ts">
import { PINES } from './treeline'

/**
 * The Cordillera behind the hero: a warm sky, three ridges and a pine
 * treeline.
 *
 * Drawn rather than photographed, for the reason MountainMark gives — and for
 * one more that matters here. This sits behind the headline and beside the
 * application form, which are the only things on the page that have to be
 * readable, and a photograph's contrast varies across its own frame: text that
 * clears AA over one part of a hillside fails over the next. Flat bands of a
 * known colour do not move, so the copy over them can be checked once.
 *
 * It also costs no request and no layout shift, which matters on the page a
 * seller opens from a flyer, on a phone, on mobile data.
 */
</script>

<template>
  <svg
    class="hs"
    viewBox="0 0 1440 520"
    preserveAspectRatio="xMidYMax slice"
    aria-hidden="true"
    focusable="false"
  >
    <defs>
      <linearGradient id="hs-sky" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#eef6ee" />
        <stop offset="55%" stop-color="#f6f4e9" />
        <stop offset="100%" stop-color="#fdf6e8" />
      </linearGradient>

      <!-- Morning haze off the ridges, the one soft edge in the picture. -->
      <radialGradient id="hs-sun" cx="0.72" cy="0.3" r="0.45">
        <stop offset="0%" stop-color="#f7d9a0" stop-opacity="0.85" />
        <stop offset="100%" stop-color="#f7d9a0" stop-opacity="0" />
      </radialGradient>

      <linearGradient id="hs-far" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#9dbfae" />
        <stop offset="100%" stop-color="#b9d2c4" />
      </linearGradient>

      <linearGradient id="hs-mid" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#5d8d72" />
        <stop offset="100%" stop-color="#78a289" />
      </linearGradient>

      <linearGradient id="hs-near" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#245c43" />
        <stop offset="100%" stop-color="#164b34" />
      </linearGradient>

      <!-- One pine, reused along the treeline at sixty-four scales. -->
      <g id="hs-pine">
        <path d="M0 0 -7 17 -3.5 17 -9 32 9 32 3.5 17 7 17Z" />
        <rect x="-1.4" y="30" width="2.8" height="6" />
      </g>
    </defs>

    <rect width="1440" height="520" fill="url(#hs-sky)" />
    <rect width="1440" height="520" fill="url(#hs-sun)" />

    <path
      d="M0 296 148 214 246 262 372 176 498 258 636 190 742 246 880 168 1010 244 1138 192 1266 254 1370 212 1440 248v272H0Z"
      fill="url(#hs-far)"
    />

    <path
      d="M0 352 126 294 232 336 346 268 470 330 590 282 700 334 828 270 948 326 1074 288 1200 338 1320 300 1440 340v180H0Z"
      fill="url(#hs-mid)"
    />

    <!-- Terraces: the one thing that says Cordillera rather than "mountains". -->
    <g stroke="#e8f0e6" stroke-width="1.6" stroke-linecap="round" opacity="0.34" fill="none">
      <path d="M126 316q108 34 216 6" />
      <path d="M118 334q124 38 244 8" />
      <path d="M700 352q104 30 206 4" />
      <path d="M694 370q118 34 226 6" />
      <path d="M1200 356q92 26 180 2" />
    </g>

    <path
      d="M0 420 110 382 214 414 330 372 452 412 566 380 686 418 812 376 930 414 1048 384 1168 416 1290 380 1440 412v108H0Z"
      fill="url(#hs-near)"
    />

    <g fill="#12402c">
      <use v-for="pine in PINES" :key="pine.key" href="#hs-pine" :transform="pine.transform" />
    </g>

    <rect y="500" width="1440" height="20" fill="#0d3424" />
  </svg>
</template>

<style scoped>
.hs {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
}
</style>
