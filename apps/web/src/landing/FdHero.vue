<script setup lang="ts">
import { ArrowRight, BadgeCheck, HandCoins, Store } from '@lucide/vue'
import MountainMark from './MountainMark.vue'

// The front page's opening: a full-bleed photograph with the pitch set over
// its left side, in the highland redesign's serif.
//
// The photograph is a named slot rather than a chosen asset. Whatever file is
// at /storefront/hero.webp is the hero; today that is a copy of the market
// photo from the about page, and replacing it with the real shoot is a file
// drop with no code change. It is framed with object-fit: cover and a left
// scrim, so any landscape photo with its subject right of centre will sit.
//
// It used to be a looping video of a market stall. A still loads in one
// request, needs no pause control, and is what the redesign draws.

const HERO_IMAGE = '/storefront/hero.webp'

/** Three things that are true of every order, not taglines. */
const promises = [
  { icon: Store, label: 'Real shops in your city' },
  { icon: BadgeCheck, label: 'The same price as the counter' },
  { icon: HandCoins, label: 'Cash or GCash on arrival' },
]
</script>

<template>
  <section class="sfhero">
    <img class="sfhero__img" :src="HERO_IMAGE" alt="" fetchpriority="high" />
    <div class="sfhero__scrim" aria-hidden="true"></div>

    <div class="sfhero__inner">
      <div class="sfhero__copy">
        <!-- Where, before anything else. The banner has to answer "is this
             even my city?" in the first glance. -->
        <p class="sf-eyebrow sfhero__eyebrow">Local shops of Baguio &amp; La Trinidad</p>

        <h1 class="sfhero__title">Shop the market, from&nbsp;home.</h1>

        <p class="sfhero__sub">
          Groceries, sari-sari stores, wet market sellers and local shops near you — delivered at
          the price they charge at the counter.
        </p>

        <!-- Two audiences land here, and each gets a next step: the shopper the
             louder one, the merchant the quieter. -->
        <div class="sfhero__cta">
          <a href="#shop" class="sfhero__btn">
            Start shopping
            <ArrowRight :size="18" :stroke-width="2" />
          </a>
          <a href="/signup" class="sfhero__ghost">Sell on Omaykan</a>
        </div>

        <ul class="sfhero__promises">
          <li v-for="promise in promises" :key="promise.label">
            <component :is="promise.icon" :size="26" :stroke-width="1.4" />
            <span>{{ promise.label }}</span>
          </li>
        </ul>
      </div>

      <p class="sfhero__script" aria-hidden="true">
        More than a market.<br />
        A neighbour,<br />
        a counter,<br />
        a brighter tomorrow.
      </p>

      <p class="sfhero__sign" aria-hidden="true">
        <MountainMark :size="46" :sun="false" />
        <span>Baguio · La Trinidad</span>
      </p>
    </div>
  </section>
</template>

<style scoped>
.sfhero {
  position: relative;
  display: flex;
  min-height: clamp(420px, 34vw, 520px);
  overflow: hidden;
  background: var(--sf-forest-deep);
  color: var(--sf-paper);
}

.sfhero__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 60% 40%;
}

/* Heaviest at the left where the words sit, clearing across the middle so
   the photograph is still the thing you see, and settling a little at the
   right edge for the script. */
.sfhero__scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(23, 35, 28, 0.94) 0%, rgba(23, 35, 28, 0.8) 30%, rgba(23, 35, 28, 0.2) 62%, rgba(23, 35, 28, 0.5) 100%),
    linear-gradient(0deg, rgba(23, 35, 28, 0.5) 0%, transparent 38%);
}

.sfhero__inner {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 620px) 1fr;
  align-items: center;
  gap: 32px;
  width: 100%;
  padding: clamp(40px, 5vw, 64px) var(--fd-gutter) clamp(36px, 4vw, 52px);
}

.sfhero__eyebrow { color: rgba(251, 248, 243, 0.85); }

.sfhero__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: clamp(2.3rem, 4.6vw, 3.6rem);
  font-weight: 700;
  line-height: 1.06;
  text-wrap: balance;
}

.sfhero__sub {
  margin: 16px 0 0;
  max-width: 34em;
  font-size: clamp(1rem, 1.3vw, 1.15rem);
  line-height: 1.55;
  color: rgba(251, 248, 243, 0.9);
}

.sfhero__cta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 22px;
  margin-top: 26px;
}

.sfhero__btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 13px 26px;
  border-radius: 6px;
  background: var(--sf-clay);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.25);
  transition: background 150ms;
}
.sfhero__btn:hover { background: var(--sf-clay-deep); }
.sfhero__btn:focus-visible { outline: 2px solid var(--sf-gold); outline-offset: 3px; }

.sfhero__ghost {
  color: var(--sf-paper);
  font-size: 15px;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 4px;
}
.sfhero__ghost:hover { color: var(--sf-gold); }

.sfhero__promises {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 30px;
  margin: 32px 0 0;
  padding: 0;
  list-style: none;
}

.sfhero__promises li {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 160px;
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.3;
}
.sfhero__promises svg { flex-shrink: 0; }

.sfhero__script {
  justify-self: end;
  align-self: start;
  margin: 12px 4% 0 0;
  font-family: var(--sf-script);
  font-size: clamp(1.5rem, 2.2vw, 2.1rem);
  line-height: 1.12;
  color: var(--sf-paper);
  transform: rotate(-8deg);
  text-shadow: 0 2px 14px rgba(0, 0, 0, 0.5);
}

.sfhero__sign {
  position: absolute;
  right: var(--fd-gutter);
  bottom: 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.26em;
  text-transform: uppercase;
  color: var(--sf-paper);
}

/* Tablets: one column. The script and the sign are decoration, and on a
   narrower photo they would sit on top of the words. */
@media (max-width: 900px) {
  .sfhero__inner { grid-template-columns: 1fr; }
  .sfhero__script,
  .sfhero__sign { display: none; }
}

/* Phones: the scrim runs top to bottom instead, since the copy now fills the
   width and there is no clear side of the photo to leave open. */
@media (max-width: 600px) {
  .sfhero__scrim {
    background: linear-gradient(180deg, rgba(23, 35, 28, 0.6) 0%, rgba(23, 35, 28, 0.9) 100%);
  }
  .sfhero__promises { gap: 12px; }
  .sfhero__promises li { max-width: none; flex-basis: 100%; }
}
</style>
