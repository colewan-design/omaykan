<script setup lang="ts">
import { ArrowRight } from '@lucide/vue'
import MountainMark from './MountainMark.vue'

// The redesign's full-bleed band under the shelves: the people behind the
// counters on the left, why buying from them matters on the right, and a
// strip of woven border along the top.
//
// The photograph is a named slot like the hero's: /storefront/people.webp,
// today a copy of the shop-owner portrait from the about page. Swap the file
// to change it.
//
// Every sentence here is one the rest of the site already stands behind — no
// commission, the rider keeps the fee — because a band about trust is the
// worst place on the page to overstate anything.

const PEOPLE_IMAGE = '/storefront/people.webp'
</script>

<template>
  <section class="sfstory" aria-labelledby="sfstory-title">
    <div class="sf-weave" aria-hidden="true"></div>

    <div class="sfstory__grid">
      <div class="sfstory__people">
        <img class="sfstory__img" :src="PEOPLE_IMAGE" alt="" loading="lazy" />
        <div class="sfstory__shade" aria-hidden="true"></div>

        <div class="sfstory__copy">
          <p class="sf-eyebrow">Our shops</p>
          <h2 id="sfstory-title" class="sfstory__title">People. Community. Progress.</h2>
          <p class="sfstory__text">
            Every counter on Omaykan is a real shop somewhere in Baguio and La Trinidad, run by
            someone you could walk up to and thank.
          </p>
          <a href="/about" class="sfstory__btn">
            Read our story
            <ArrowRight :size="15" :stroke-width="2" />
          </a>
        </div>
      </div>

      <div class="sfstory__support">
        <MountainMark :size="60" :sun="false" class="sfstory__mark" />
        <div>
          <h2 class="sfstory__title sfstory__title--sm">
            Support local.<br />
            Keep the market close to home.
          </h2>
          <p class="sfstory__text">
            Omaykan takes no percentage of a sale, so what you pay goes to the shop that packed
            your order and the rider who brought it.
          </p>
          <a href="/#shops" class="sfstory__btn">
            Shop with purpose
            <ArrowRight :size="15" :stroke-width="2" />
          </a>
        </div>
      </div>

      <div class="sfstory__horizon" aria-hidden="true">
        <span class="sf-weave-v sfstory__stripe"></span>
        <p class="sfstory__caption">Local hands<br />Brighter horizons</p>
        <svg class="sfstory__peaks" viewBox="0 0 240 120" preserveAspectRatio="xMidYMax slice">
          <path d="M0 120V76l40-26 30 20 50-52 45 44 30-18 45 28v48Z" fill="#b8ad9b" />
          <path d="m120 18-12 12 8-2 6 6 6-7 6 3Z" fill="#f4eee4" />
          <path d="M0 120V94l50-22 40 18 50-30 50 28 50-8v40Z" fill="#8e8474" />
          <g fill="#1f2e25" opacity="0.75">
            <path d="m20 120 7-18 7 18Z" />
            <path d="m38 120 6-14 6 14Z" />
            <path d="m150 120 7-20 7 20Z" />
            <path d="m168 120 6-15 6 15Z" />
            <path d="m200 120 7-17 7 17Z" />
          </g>
        </svg>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* Full-bleed out of the page's padded column: the band runs edge to edge
   the way the hero does. */
.sfstory {
  margin: 8px calc(-1 * var(--fd-gutter)) 56px;
}

.sfstory__grid {
  display: grid;
  grid-template-columns: 1.05fr 1fr 0.5fr;
  min-height: 300px;
}

/* ── The people ──────────────────────────────────────────────────────── */

.sfstory__people {
  position: relative;
  display: flex;
  align-items: center;
  overflow: hidden;
  background: var(--sf-forest-deep);
  color: var(--sf-paper);
}

.sfstory__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 75% 30%;
}

.sfstory__shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(23, 35, 28, 0.95) 0%, rgba(23, 35, 28, 0.82) 42%, rgba(23, 35, 28, 0.1) 88%);
}

.sfstory__copy {
  position: relative;
  z-index: 1;
  max-width: 420px;
  padding: 40px 24px 40px calc(var(--fd-gutter) + 24px);
}

.sfstory__title {
  margin: 0 0 12px;
  font-family: var(--sf-serif);
  font-size: clamp(1.5rem, 2.3vw, 2rem);
  font-weight: 700;
  line-height: 1.15;
}

.sfstory__title--sm { font-size: clamp(1.3rem, 1.9vw, 1.65rem); }

.sfstory__text {
  margin: 0 0 20px;
  max-width: 40ch;
  font-size: 14.5px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.9);
}

.sfstory__btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 4px;
  background: var(--sf-paper);
  color: var(--sf-ink);
  font-size: 13.5px;
  font-weight: 700;
  text-decoration: none;
  transition: background 150ms;
}
.sfstory__btn:hover { background: var(--sf-sand); }
.sfstory__btn:focus-visible { outline: 2px solid var(--sf-gold); outline-offset: 3px; }

/* ── Support local ───────────────────────────────────────────────────── */

.sfstory__support {
  display: flex;
  align-items: flex-start;
  gap: 22px;
  padding: 44px 36px;
  background: var(--sf-clay);
  color: var(--sf-paper);
}

.sfstory__mark { margin-top: 6px; color: var(--sf-paper); }

/* ── The horizon ─────────────────────────────────────────────────────── */

.sfstory__horizon {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: 40px 18px 0 38px;
  background: linear-gradient(180deg, #efe6d6 0%, #e2d5bf 100%);
  color: var(--sf-forest);
}

.sfstory__stripe {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
}

.sfstory__caption {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.7;
  letter-spacing: 0.26em;
  text-align: center;
  text-transform: uppercase;
}

.sfstory__peaks {
  display: block;
  width: calc(100% + 56px);
  height: 150px;
  margin: 0 -18px 0 -38px;
}

/* ── Narrower screens ────────────────────────────────────────────────── */

@media (max-width: 1080px) {
  .sfstory__grid { grid-template-columns: 1fr 1fr; }
  .sfstory__horizon { display: none; }
}

@media (max-width: 720px) {
  .sfstory__grid { grid-template-columns: 1fr; }
  .sfstory__people { min-height: 340px; }
  .sfstory__shade {
    background: linear-gradient(180deg, rgba(23, 35, 28, 0.35) 0%, rgba(23, 35, 28, 0.92) 70%);
  }
  .sfstory__people { align-items: flex-end; }
  .sfstory__copy { padding: 28px var(--fd-gutter); }
  .sfstory__support { padding: 32px var(--fd-gutter); flex-direction: column; gap: 14px; }
}
</style>
