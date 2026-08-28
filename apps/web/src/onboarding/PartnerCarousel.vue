<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchStores, type StoreSummary } from '@pos/web/commerce/api'
import { vReveal } from '@pos/web/landing/reveal'

/**
 * The shops already selling on Omaykan, on the merchant signup page.
 *
 * The rest of the pitch is Omaykan describing itself. This is the one band on
 * the page that is not a claim: it is read live from the public directory
 * (/api/stores), so every shop on it is a real tenant with a real shelf the
 * visitor can open and order from. A partner strip edited by hand would go
 * stale the week after launch; this one cannot.
 *
 * A carousel rather than a grid because the list grows: three partners must
 * not look like a broken row of nine, and ninety must not push the signup
 * form a screen and a half further down the page.
 */

const shops = ref<StoreSummary[]>([])
const loading = ref(true)

const track = ref<HTMLElement | null>(null)
const atStart = ref(true)
const atEnd = ref(true)
/** False while every card already fits — then the arrows and autoplay are noise. */
const scrollable = ref(false)

/** Photos that 404 fall back to the monogram, same as a shop with none. */
const brokenImages = ref(new Set<string>())

const AUTOPLAY_MS = 4200

// Someone who asked their OS to stop animations gets a strip they drive
// themselves — it still scrolls and swipes, it just never moves on its own.
const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

let timer = 0
const paused = ref(false)

onMounted(async () => {
  try {
    shops.value = await fetchStores()
  } catch {
    // The pitch reads fine without this band, and a merchant halfway to
    // signing up should not be shown an error about somebody else's shops.
    shops.value = []
  } finally {
    loading.value = false
  }

  await nextTick()
  measure()
  startAutoplay()
  window.addEventListener('resize', measure)
})

onBeforeUnmount(() => {
  stopAutoplay()
  window.removeEventListener('resize', measure)
})

function measure() {
  const el = track.value
  if (!el) return
  // The 1px slack absorbs sub-pixel scroll positions, which otherwise leave
  // "next" enabled at the end of the strip with nowhere left to go.
  scrollable.value = el.scrollWidth > el.clientWidth + 1
  atStart.value = el.scrollLeft <= 1
  atEnd.value = el.scrollLeft + el.clientWidth >= el.scrollWidth - 1
}

/**
 * A page at a time, letting scroll-snap decide where that lands: snapping to
 * the nearest card start is what keeps a click from parking a card half off
 * the edge, at any viewport width and without measuring one.
 */
function page(direction: -1 | 1) {
  const el = track.value
  if (!el) return
  el.scrollBy({ left: direction * el.clientWidth, behavior: 'smooth' })
}

function startAutoplay() {
  if (reduceMotion || timer !== 0) return
  timer = window.setInterval(() => {
    const el = track.value
    // A hidden tab still fires intervals, and scrolling one is wasted work the
    // visitor comes back to find already parked at the far end.
    if (!el || paused.value || !scrollable.value || document.hidden) return
    if (atEnd.value) el.scrollTo({ left: 0, behavior: 'smooth' })
    else page(1)
  }, AUTOPLAY_MS)
}

function stopAutoplay() {
  if (timer === 0) return
  window.clearInterval(timer)
  timer = 0
}

/** The landing page, pointed at this shop — the same ?shop= its own directory uses. */
function shopUrl(shop: StoreSummary): string {
  return `/?shop=${encodeURIComponent(shop.orgSlug)}`
}

/** First letter of the shop name, for a shop with no usable photo. */
function monogram(shop: StoreSummary): string {
  return shop.name.trim().charAt(0).toUpperCase() || '?'
}

/** Shops are unique by slug; the code alone repeats ("main" for nearly all). */
function keyOf(shop: StoreSummary): string {
  return shop.orgSlug
}

function hasPhoto(shop: StoreSummary): boolean {
  return Boolean(shop.imageUrl) && !brokenImages.value.has(keyOf(shop))
}

function onImageError(shop: StoreSummary) {
  // Replacing the Set rather than mutating it: a plain Set is not reactive, so
  // an in-place add would never reach the template.
  brokenImages.value = new Set(brokenImages.value).add(keyOf(shop))
}
</script>

<template>
  <!-- Nothing to show is not an empty state here: a marketing band with no
       content in it says less than no band at all. -->
  <section v-if="!loading && shops.length > 0" id="partners" class="pm-band pc">
    <div class="pm-container">
      <header class="pm-head">
        <p v-reveal class="pm-eyebrow">Shop partners</p>
        <h2 v-reveal="60" class="pm-h2">Already selling on Omaykan.</h2>
        <p v-reveal="120" class="pm-head-sub">
          Every shop here is live in the app right now — open one and you are looking at the same
          storefront your own customers would get.
        </p>
      </header>
    </div>

    <div
      class="pc-strip"
      @pointerenter="paused = true"
      @pointerleave="paused = false"
      @focusin="paused = true"
      @focusout="paused = false"
    >
      <button
        v-if="scrollable"
        type="button"
        class="pc-arrow"
        aria-label="Previous shops"
        :disabled="atStart"
        @click="page(-1)"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>
      </button>

      <ul ref="track" class="pc-track" :class="{ 'pc-track--fits': !scrollable }" @scroll.passive="measure">
        <li v-for="shop in shops" :key="shop.orgSlug" class="pc-item">
          <a class="pc-card" :href="shopUrl(shop)">
            <span class="pc-card__photo">
              <!-- Empty alt: the name is right underneath in text, so the photo
                   is decoration and announcing it twice only slows a reader. -->
              <img
                v-if="hasPhoto(shop)"
                :src="shop.imageUrl ?? ''"
                alt=""
                loading="lazy"
                decoding="async"
                @error="onImageError(shop)"
              >
              <span v-else class="pc-card__monogram" aria-hidden="true">{{ monogram(shop) }}</span>
            </span>
            <span class="pc-card__body">
              <span class="pc-card__name">{{ shop.name }}</span>
              <span v-if="shop.businessTypeLabel" class="pc-card__kind">{{ shop.businessTypeLabel }}</span>
            </span>
          </a>
        </li>
      </ul>

      <button
        v-if="scrollable"
        type="button"
        class="pc-arrow"
        aria-label="More shops"
        :disabled="atEnd"
        @click="page(1)"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18l6-6-6-6"/></svg>
      </button>
    </div>
  </section>
</template>

<style scoped>
.pc { padding-bottom: 92px; }

/* The arrows sit in the padding beside the track, not on top of the cards:
   overlapping them would cover the first and last shop's photo, which is the
   one thing this band exists to show. */
.pc-strip {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 40px;
}

.pc-track {
  /* Fills whatever the arrows leave, and min-width:0 lets it actually shrink
     to that — without it the track sizes to its content and the page, not the
     strip, is what ends up scrolling sideways. */
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 20px;
  margin: 0;
  /* Room for the cards' lift on hover and their shadow, which a clipping
     scroll container would otherwise shave off. */
  padding: 8px 2px 18px;
  list-style: none;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
}
.pc-track::-webkit-scrollbar { display: none; }

/* Centred only while everything fits. Centring a track that does overflow
   puts its first card off the left edge with no way to scroll back to it, so
   this is a class rather than a plain justify-content on the track. */
.pc-track--fits { justify-content: center; }

.pc-item {
  flex: 0 0 clamp(200px, 22vw, 248px);
  scroll-snap-align: start;
}

.pc-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 20px;
  overflow: hidden;
  background: var(--bg-surface);
  text-decoration: none;
  transition: transform 220ms, box-shadow 220ms, border-color 150ms;
}
.pc-card:hover {
  transform: translateY(-4px);
  border-color: var(--accent-border);
  box-shadow: 0 14px 30px rgba(0, 0, 0, 0.1);
}

.pc-card__photo {
  display: grid;
  place-items: center;
  aspect-ratio: 4 / 3;
  background: var(--fill);
  overflow: hidden;
}
/* Contain, not cover: these are pack shots on white, and cropping one to fill
   the tile cuts the label off the product it is meant to advertise. */
.pc-card__photo img { width: 100%; height: 100%; object-fit: contain; padding: 14px; }

.pc-card__monogram {
  font-size: 2.4rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--accent);
}

.pc-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}
.pc-card__name {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-primary);
  /* One line: shop names run long, and a card that grows a second line makes
     the whole row uneven. */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pc-card__kind { font-size: 12.5px; color: var(--text-tertiary); }

.pc-arrow {
  flex: none;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--separator-strong);
  border-radius: 50%;
  background: var(--bg-surface);
  color: var(--text-primary);
  cursor: pointer;
  transition: background 150ms, color 150ms, opacity 150ms;
}
.pc-arrow:hover:not(:disabled) { background: var(--accent); color: var(--accent-ink); border-color: transparent; }
.pc-arrow:disabled { opacity: 0.32; cursor: default; }

@media (max-width: 900px) {
  /* The arrows are the desktop affordance; on a touch screen the strip is
     swiped, and two buttons would only eat width the cards need. */
  .pc-arrow { display: none; }
  .pc-strip { padding: 0 20px; }
  .pc-item { flex-basis: 62vw; }
}
</style>
