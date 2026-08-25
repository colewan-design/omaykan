<script setup lang="ts">
import FdHeader from '@pos/web/landing/FdHeader.vue'
import FdFooter from '@pos/web/landing/FdFooter.vue'
import { vReveal } from '@pos/web/landing/reveal'

// The lead promo used to open the landing page, above the shelves. It reads as
// a statement of what Omaykan is rather than a shopping surface, so it lives
// here instead and the landing page opens straight on the catalog.
//
// Every link back to shopping is a real navigation home — this is a separate
// Vite entry, not a route, so #shop anchors would resolve against this page.
const SHOP_HREF = '/'

function search(term: string) {
  // The shelves live on the landing page; ?q= is picked up there on arrival.
  window.location.href = term ? `/?q=${encodeURIComponent(term)}` : '/'
}
</script>

<template>
  <div class="landing fd">
    <FdHeader :shop-href="SHOP_HREF" @search="search" />

    <main class="fd-main">
      <section class="fd-promo">
        <h1 v-reveal class="fd-promo__title">Skip the trip to the market</h1>
        <p v-reveal="60" class="fd-promo__sub">
          Order from the shops you already know in Baguio. Riders in your neighbourhood bring it
          over the same day — cash or GCash on arrival, and vendors keep every peso.
        </p>
        <a v-reveal="60" :href="SHOP_HREF" class="fd-promo__link">Start shopping — no commissions, ever</a>
        <div v-reveal="120" class="fd-promo__art">
          <img src="/delivery/hero-rider.webp" alt="Rider carrying two bags of fresh groceries" width="900" height="1125" />
          <div class="fd-promo__art-copy">
            <span class="fd-promo__kicker">Same-day delivery</span>
            <span class="fd-promo__fee">Flat ₱49 · first 2&nbsp;km</span>
          </div>
        </div>
      </section>
    </main>

    <FdFooter :shop-href="SHOP_HREF" />
  </div>
</template>

<style scoped>
.fd-main { background: #fff; }

/* ── Lead promo ──────────────────────────────────────────────────── */
.fd-promo {
  padding: 44px var(--fd-gutter) 72px;
}
.fd-promo__title {
  margin: 0 0 10px;
  font-size: clamp(2rem, 4.6vw, 3.25rem);
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1.04;
  color: #1a1a1a;
}
.fd-promo__sub {
  margin: 0 0 8px;
  max-width: 640px;
  font-size: 16px;
  line-height: 1.6;
  color: #6b7280;
}
.fd-promo__link {
  display: inline-block;
  margin-bottom: 24px;
  font-size: 15px;
  font-weight: 600;
  color: #16a34a;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.fd-promo__link:hover { color: #1a6b3c; }

/* Wide banner image, as in the reference's full-bleed promo. */
.fd-promo__art {
  position: relative;
  height: 320px;
  border-radius: 12px;
  overflow: hidden;
  background: radial-gradient(120% 140% at 78% 10%, #5bbf8a 0, #1a6b3c 55%, #103f23);
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
}
/* Bottom-anchored and capped at the banner height: the source is a cutout
   figure, so anything over 100% crops the rider's head. */
.fd-promo__art img {
  height: 100%;
  max-height: 100%;
  width: auto;
  object-fit: contain;
  object-position: bottom;
  margin-right: 7%;
  align-self: flex-end;
}
/* Scrim so the script line stays legible over the lighter part of the
   gradient, and the left half doesn't read as dead space. */
.fd-promo__art::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(6,36,15,0.45) 0%, rgba(6,36,15,0.12) 42%, transparent 65%);
  z-index: 1;
}
.fd-promo__art-copy {
  position: absolute;
  left: 40px;
  bottom: 40px;
  z-index: 2;
  display: grid;
  gap: 8px;
}
.fd-promo__kicker {
  font-family: 'Caveat', cursive;
  font-size: 34px;
  font-weight: 700;
  color: #bbf451;
  line-height: 1;
}
.fd-promo__fee {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
}

@media (max-width: 760px) {
  .fd-promo { padding: 28px var(--fd-gutter) 56px; }
  .fd-promo__art { height: 240px; }
  .fd-promo__art-copy { left: 20px; bottom: 20px; }
  .fd-promo__kicker { font-size: 26px; }
}
</style>
