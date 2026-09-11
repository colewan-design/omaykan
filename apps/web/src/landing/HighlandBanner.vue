<script setup lang="ts">
// The strip of highland across the top of the aisle listing, where the listing
// redesign puts a photograph of terraced mountains.
//
// Drawn rather than photographed. None of the site's own photographs is a
// clear landscape — the about page's view over Baguio has a rider filling its
// left half — and a stock shot of somebody else's mountains would picture a
// place this isn't. Layered ridges in the storefront's own greens, with mist
// between them and pines along the near crests, read as the Cordillera at a
// strip's height and cost no request.
//
// Every ridge is a sum of sines sampled into a path, so each pine stands
// exactly on the crest it grows from. The random numbers come from a fixed
// seed, so the banner is the same drawing on every load.

const W = 1600
const H = 340
const STEP = 8

type Wave = readonly [amplitude: number, wavelength: number, phase: number]
type Height = (x: number) => number

function ridge(base: number, waves: readonly Wave[]): Height {
  return (x) =>
    waves.reduce((y, [amplitude, wavelength, phase]) => y + amplitude * Math.sin((2 * Math.PI * x) / wavelength + phase), base)
}

function silhouette(height: Height): string {
  let d = `M0 ${height(0).toFixed(1)}`
  for (let x = STEP; x <= W; x += STEP) d += `L${x} ${height(x).toFixed(1)}`
  return `${d}L${W} ${H}L0 ${H}Z`
}

/** A line following the crest some way below it: the terrace walls on the front hill. */
function contour(height: Height, drop: number): string {
  let d = `M0 ${(height(0) + drop).toFixed(1)}`
  for (let x = STEP; x <= W; x += STEP) d += `L${x} ${(height(x) + drop).toFixed(1)}`
  return d
}

/** mulberry32 — small, seeded, and enough for placing trees. */
function seeded(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

interface Stand {
  seed: number
  gap: readonly [number, number]
  size: readonly [number, number]
  density: number
  sink: number
}

/** Two-tiered pines along a crest, drawn as one path. */
function pines(height: Height, stand: Stand): string {
  const rand = seeded(stand.seed)
  const [minGap, maxGap] = stand.gap
  const [minSize, maxSize] = stand.size
  let d = ''
  for (let x = rand() * maxGap; x < W + maxGap; x += minGap + rand() * (maxGap - minGap)) {
    if (rand() > stand.density) continue
    const h = minSize + rand() * (maxSize - minSize)
    const w = h * 0.32
    const y = height(x) + stand.sink + rand() * 3
    const at = (dx: number, dy: number) => `${(x + dx).toFixed(1)} ${(y + dy).toFixed(1)}`
    d += `M${at(0, -h)}L${at(w * 0.6, -h * 0.42)}L${at(w * 0.3, -h * 0.42)}L${at(w, 0)}`
      + `L${at(-w, 0)}L${at(-w * 0.3, -h * 0.42)}L${at(-w * 0.6, -h * 0.42)}Z`
  }
  return d
}

const far = ridge(150, [[24, 560, 0.5], [11, 240, 1.9], [4, 95, 0.4]])
const mid = ridge(194, [[20, 640, 2.2], [9, 270, 0.3], [4, 80, 1.2]])
const near = ridge(236, [[15, 720, 4.1], [8, 310, 2.7], [3, 64, 0.8]])
const front = ridge(280, [[13, 860, 1.3], [7, 350, 3.4], [3, 84, 2.1]])
const ground = ridge(318, [[7, 940, 0.8], [4, 300, 2.0]])

const farPath = silhouette(far)
const midPath = silhouette(mid)
const nearPath = silhouette(near)
const nearPines = pines(near, { seed: 7, gap: [10, 26], size: [9, 16], density: 0.7, sink: 3 })
const frontPath = silhouette(front)
const terraces = [9, 17, 25].map((drop) => contour(front, drop)).join('')
const frontPines = pines(front, { seed: 21, gap: [14, 40], size: [14, 26], density: 0.55, sink: 4 })
const groundPath = silhouette(ground)
const groundPines = pines(ground, { seed: 42, gap: [30, 90], size: [26, 50], density: 0.6, sink: 6 })
</script>

<template>
  <div class="hlb" aria-hidden="true">
    <!-- Anchored at the bottom: a wide window crops sky, a narrow one crops
         the sides, and the ground always meets the page. -->
    <svg class="hlb__art" :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="xMidYMax slice" focusable="false">
      <defs>
        <linearGradient id="hlb-sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#e3cfa8" />
          <stop offset="0.5" stop-color="#efe3cc" />
          <stop offset="1" stop-color="#f5efe4" />
        </linearGradient>
        <radialGradient id="hlb-glow">
          <stop offset="0" stop-color="#fbe3b4" stop-opacity="0.95" />
          <stop offset="0.35" stop-color="#f7dcae" stop-opacity="0.5" />
          <stop offset="1" stop-color="#f5efe4" stop-opacity="0" />
        </radialGradient>
        <filter id="hlb-mist" x="-10%" y="-100%" width="120%" height="300%">
          <feGaussianBlur stdDeviation="9" />
        </filter>
      </defs>

      <rect :width="W" :height="H" fill="url(#hlb-sky)" />
      <circle cx="1240" cy="110" r="260" fill="url(#hlb-glow)" />
      <circle cx="1240" cy="110" r="28" fill="#f9e2b0" />

      <path :d="farPath" fill="#cdd2c2" />
      <g fill="#f7f2ea" opacity="0.8" filter="url(#hlb-mist)">
        <ellipse cx="360" cy="214" rx="300" ry="13" />
        <ellipse cx="1080" cy="206" rx="340" ry="15" />
      </g>

      <path :d="midPath" fill="#a3b19d" />
      <g fill="#f7f2ea" opacity="0.7" filter="url(#hlb-mist)">
        <ellipse cx="720" cy="252" rx="260" ry="10" />
        <ellipse cx="1420" cy="250" rx="220" ry="9" />
      </g>

      <path :d="nearPath" fill="#667e68" />
      <path :d="nearPines" fill="#667e68" />

      <path :d="frontPath" fill="#34493b" />
      <path :d="terraces" fill="none" stroke="#4a6552" stroke-width="1.4" opacity="0.55" />
      <path :d="frontPines" fill="#34493b" />

      <path :d="groundPath" fill="#1f2e25" />
      <path :d="groundPines" fill="#1f2e25" />
    </svg>
  </div>
</template>

<style scoped>
.hlb {
  height: clamp(140px, 19vw, 290px);
  overflow: hidden;
  background: #efe3cc;
}

.hlb__art { display: block; width: 100%; height: 100%; }
</style>
