<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Award,
  CalendarDays,
  ChartColumn,
  ClipboardList,
  Coins,
  Crown,
  Gem,
  Link2,
  Megaphone,
  Package,
  QrCode,
  Settings,
  ShoppingCart,
  Store,
  Tag,
  Users,
} from '@lucide/vue'
import { vReveal } from '@pos/web/landing/reveal'
import ApplicationForm from './ApplicationForm.vue'
import ApplicationSent from './ApplicationSent.vue'
import BadgeCard from './BadgeCard.vue'
import FoundingFaq from './FoundingFaq.vue'
import FoundingFooter from './FoundingFooter.vue'
import FoundingHeader from './FoundingHeader.vue'
import HighlandScene from './HighlandScene.vue'
import { fetchFoundingStatus, type FoundingStatus } from './api'

/**
 * The founding-seller campaign: the first 30 Baguio / La Trinidad businesses.
 *
 * The page is a pitch with the form beside it rather than under it — a shop
 * owner reading the perks should never have to hunt for where to apply. On a
 * narrow screen the rail falls below the pitch, which is the one order that
 * works when they cannot sit side by side.
 *
 * Everything the campaign promises in money is stated once, in `OFFER` below,
 * and repeated nowhere else on this page. Two places to change ₱199 is one
 * place too many for a number that is going in print.
 */

const status = ref<FoundingStatus | null>(null)
const sentFor = ref<string | null>(null)
let inflight: AbortController | null = null

/**
 * The campaign's real state, read once on mount.
 *
 * Failure is silent on purpose — `fetchFoundingStatus` answers null rather
 * than throwing. The page stands on its own without a counter, and a backend
 * blip must not stop somebody applying; the form falls back to its own copy of
 * the category list and the API is still the authority on submit.
 */
onMounted(async () => {
  inflight = new AbortController()
  status.value = await fetchFoundingStatus(inflight.signal)
})

onBeforeUnmount(() => inflight?.abort())

function onApplied(businessName: string) {
  sentFor.value = businessName
  // The rail is sticky and the confirmation is shorter than the form, so on a
  // narrow screen the swap can happen entirely below the fold.
  requestAnimationFrame(() => {
    document.getElementById('apply')?.scrollIntoView({ block: 'start', behavior: 'smooth' })
  })
}

/** The four numbers the campaign is built on. Stated here, and only here. */
const OFFER = [
  { icon: Settings, value: '₱0', label: 'setup' },
  { icon: Coins, value: '₱0', label: 'commission' },
  { icon: CalendarDays, value: 'Free', label: '2–3 months', highlight: true },
  { icon: Tag, value: '₱199/month', label: 'afterward' },
]

/** What every seller gets, founding or not — the strip under the hero. */
const INCLUDED = [
  { icon: Store, label: 'Free online store setup' },
  { icon: Link2, label: 'Own Omaykan subdomain' },
  { icon: QrCode, label: 'QR code for your shop' },
  { icon: ShoppingCart, label: 'Online ordering' },
  { icon: ChartColumn, label: 'Seller dashboard' },
  { icon: Users, label: 'Listed on Omaykan marketplace' },
]

/**
 * What only the first thirty get.
 *
 * Every one of these is something we can actually do — there is no countdown
 * clock and no "3 spots left!!" here, because the scarcity is real and does
 * not need dressing up. `remaining` on the form says the true number when
 * badges have actually been issued.
 */
const PERKS = [
  {
    icon: Gem,
    title: 'Early access to new features',
    body: 'Be the first to try upcoming tools and updates.',
  },
  {
    icon: Crown,
    title: 'Priority marketplace placement during launch',
    body: 'Get more visibility when we go live.',
  },
  {
    icon: Award,
    title: 'Founding-seller badge',
    body: 'A number that is permanently yours, shown on your store.',
  },
  {
    icon: Megaphone,
    title: 'Promotional features',
    body: 'Be featured in our launch campaigns and community promotions.',
  },
  {
    icon: QrCode,
    title: 'Free QR materials',
    body: 'A printed QR code for your shop, for in-store and online use.',
  },
  {
    icon: Tag,
    title: 'Locked-in introductory pricing',
    body: 'Keep the founding rate of ₱199/month even when the regular price rises.',
  },
]

const STEPS = [
  {
    icon: ClipboardList,
    title: 'Submit application',
    body: 'Fill out the form with your business details. It takes about two minutes.',
  },
  {
    icon: Store,
    title: 'We set up your store',
    body: 'Our team builds your Omaykan online store and calls you to go through it.',
  },
  {
    icon: Package,
    title: 'Start receiving orders',
    body: 'Go live and start reaching more customers in Baguio and La Trinidad.',
  },
]
</script>

<template>
  <div class="fd fs">
    <FoundingHeader />

    <!-- The ridge behind the top of the page. Fixed height rather than sized
         from the hero: the hero's height moves with the copy, and a background
         that chased it would put the treeline in a different place on every
         breakpoint. -->
    <div class="fs__scenery" aria-hidden="true">
      <HighlandScene />
      <div class="fs__veil"></div>
      <div class="fs__fade"></div>
    </div>

    <main class="fs__shell">
      <div class="fs__flow">
        <section class="fs-hero" aria-labelledby="fs-title">
          <div class="fs-hero__copy">
            <p class="fs-hero__pill">
              <Users :size="16" :stroke-width="2.2" aria-hidden="true" />
              The First 30 Sellers Campaign
            </p>

            <h1 id="fs-title" class="fs-hero__title">
              Become an Omaykan<br /><span>Founding Seller</span>
            </h1>

            <p class="fs-hero__lead">
              Join the <strong>first 30 Baguio / La Trinidad businesses</strong>
              and launch your online store with Omaykan.
            </p>

            <p class="fs-hero__body">
              Be part of the pioneers. Founding sellers get permanent recognition on
              our platform and early advantages as we grow together with the local
              community.
            </p>
          </div>

          <BadgeCard class="fs-hero__badge" />

          <!-- Outside the copy column, spanning the hero, because the copy
               column is only ~440px once the badge and the form rail have
               taken their share — four chips in that gave "Free / 2–3 /
               months" stacked three deep. -->
          <ul class="fs-offer">
            <li v-for="item in OFFER" :key="item.label" class="fs-offer__item" :class="{ 'fs-offer__item--hl': item.highlight }">
              <component :is="item.icon" :size="19" :stroke-width="2" aria-hidden="true" />
              <span>
                <strong>{{ item.value }}</strong>
                <small>{{ item.label }}</small>
              </span>
            </li>
          </ul>
        </section>

        <ul class="fs-included" v-reveal>
          <li v-for="item in INCLUDED" :key="item.label">
            <component :is="item.icon" :size="17" :stroke-width="1.9" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </li>
        </ul>

        <section id="perks" class="fs-section" aria-labelledby="perks-title">
          <h2 id="perks-title" class="fs-section__title">Founding Seller Perks</h2>
          <p class="fs-section__sub">Exclusive advantages for the first businesses on Omaykan.</p>

          <ul class="fs-perks">
            <li v-for="(perk, index) in PERKS" :key="perk.title" v-reveal="index * 45" class="fs-perk">
              <span class="fs-perk__icon" aria-hidden="true">
                <component :is="perk.icon" :size="22" :stroke-width="1.9" />
              </span>
              <h3 class="fs-perk__title">{{ perk.title }}</h3>
              <p class="fs-perk__body">{{ perk.body }}</p>
            </li>
          </ul>
        </section>

        <section id="how" class="fs-section" aria-labelledby="how-title">
          <h2 id="how-title" class="fs-section__title">How It Works</h2>
          <p class="fs-section__sub">A simple process to get your business online.</p>

          <ol class="fs-steps">
            <li v-for="(step, index) in STEPS" :key="step.title" v-reveal="index * 60" class="fs-step">
              <span class="fs-step__num">{{ index + 1 }}</span>
              <span class="fs-step__icon" aria-hidden="true">
                <component :is="step.icon" :size="20" :stroke-width="1.9" />
              </span>
              <div>
                <h3 class="fs-step__title">{{ step.title }}</h3>
                <p class="fs-step__body">{{ step.body }}</p>
              </div>
            </li>
          </ol>

          <p class="fs-steps__note">
            You do not need a website, a computer, or any online selling experience.
            If you have a stall and a phone, we can get you online.
          </p>
        </section>
      </div>

      <aside id="apply" class="fs__rail" aria-label="Apply to sell on Omaykan">
        <ApplicationSent v-if="sentFor" :business-name="sentFor" />
        <ApplicationForm v-else :status="status" @applied="onApplied" />
        <FoundingFaq id="faq" />
      </aside>
    </main>

    <FoundingFooter />
  </div>
</template>

<style scoped>
.fs {
  position: relative;
  background: var(--sf-sand);
  color: var(--sf-ink);
  overflow-x: clip;
}

.fs__scenery {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 660px;
  pointer-events: none;
}

/*
 * A scrim over the half of the scene the copy sits on.
 *
 * Not decoration. The hero paragraph lands squarely on the mid-green ridge,
 * and grey body text on #78a289 is about 1.8:1 — unreadable, and it varies
 * across its own line as the ridge does. This flattens the left side to the
 * page's own colour so every line of copy has one known background behind it,
 * and lets the mountains stay mountains on the right, where the badge card is
 * an image and does not care.
 */
.fs__veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to right,
    var(--sf-sand) 0%,
    rgba(243, 245, 241, 0.94) 30%,
    rgba(243, 245, 241, 0.5) 46%,
    rgba(243, 245, 241, 0) 62%
  );
}

/* The scene has to stop being a picture and start being the page. Without
   this the treeline ends on a hard horizontal edge across the middle of the
   perks grid. */
.fs__fade {
  position: absolute;
  inset: auto 0 0;
  height: 190px;
  background: linear-gradient(to bottom, rgba(243, 245, 241, 0), var(--sf-sand));
}

.fs__shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 440px;
  align-items: start;
  gap: 44px;
  padding: 34px var(--fd-inset) 20px;
}

.fs__flow {
  display: grid;
  gap: 36px;
  min-width: 0;
}

/*
 * The rail is a plain column, deliberately not sticky.
 *
 * Pinning it was the obvious thing — keep the form beside the pitch — but the
 * form and the FAQ together are taller than a laptop viewport, and a sticky
 * element taller than the viewport can never have its bottom scrolled to. The
 * fix for that is a scroll box inside the rail, and then the FAQ is six
 * answers hidden behind a scrollbar most people will not find.
 *
 * Left to flow, the two columns come out close in height anyway, and every
 * answer is on the page where the header's FAQ link can reach it.
 */
.fs__rail {
  display: grid;
  gap: 16px;
  align-content: start;
  min-width: 0;
}

/* ── Hero ────────────────────────────────────────────────────────────── */

.fs-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 290px);
  align-items: center;
  gap: 26px 28px;
  padding-top: 12px;
}

.fs-hero__copy { grid-column: 1; }
.fs-hero__badge { grid-column: 2; }
.fs-offer { grid-column: 1 / -1; }

.fs-hero__pill {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 18px;
  padding: 9px 17px;
  border-radius: 999px;
  background: #fdf3dd;
  border: 1px solid #f0dcae;
  color: #7a5518;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.fs-hero__title {
  margin: 0 0 16px;
  font-size: clamp(2.3rem, 3.7vw, 3.25rem);
  font-weight: 900;
  letter-spacing: -0.035em;
  line-height: 1.02;
  color: var(--sf-forest-deep);
  white-space: nowrap;
}

.fs-hero__title span { color: var(--sf-leaf); }

.fs-hero__lead {
  margin: 0 0 12px;
  font-size: clamp(1rem, 1.3vw, 1.22rem);
  line-height: 1.45;
  color: var(--sf-ink);
}

.fs-hero__lead strong {
  font-weight: 800;
  color: var(--sf-forest);
}

.fs-hero__body {
  max-width: 54ch;
  margin: 0 0 24px;
  font-size: 15px;
  line-height: 1.6;
  /* Darker than --sf-muted, which is tuned for body copy on white. This line
     sits on the scrimmed scene, where the ground is a shade deeper. */
  color: #4f5a53;
}

/*
 * A four-up grid rather than a wrapping flex row. Wrapping put ₱199/month
 * alone on a second line at every width below ~1500px — the one number a
 * seller most needs to weigh, orphaned under the three free ones, which reads
 * as an afterthought or a catch. Equal columns break to a clean 2x2 instead.
 */
.fs-offer {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.fs-offer__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 13px;
  border: 1px solid rgba(26, 122, 69, 0.16);
  background: rgba(255, 255, 255, 0.86);
  color: var(--sf-forest);
  backdrop-filter: blur(6px);
}

/* The free months are the offer's centre of gravity — the one warm chip. */
.fs-offer__item--hl {
  border-color: #eccf8f;
  background: rgba(253, 243, 221, 0.92);
  color: #7a5518;
}

.fs-offer__item span {
  display: grid;
  line-height: 1.15;
}

.fs-offer__item strong {
  font-size: 16px;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.fs-offer__item small {
  font-size: 11.5px;
  font-weight: 600;
  opacity: 0.78;
}

.fs-hero__badge { justify-self: end; }

/* ── What's included ─────────────────────────────────────────────────── */

/* The poster treats this as one compact feature ribbon. Six explicit columns
   preserve that rhythm on desktop, then step down cleanly on narrow screens. */
.fs-included {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 13px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 40px -30px rgba(13, 52, 36, 0.6);
  list-style: none;
}

.fs-included li {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 3px 0;
  font-size: 10.5px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--sf-ink);
}

.fs-included svg {
  flex-shrink: 0;
  color: var(--sf-leaf);
}

/* ── Sections ────────────────────────────────────────────────────────── */

/*
 * The header is sticky at 66px and landing.css turns on smooth scrolling
 * globally, so an anchor without this lands with its heading parked underneath
 * the bar. Every in-page target the header links to needs it.
 */
.fs-section,
.fs__rail {
  scroll-margin-top: 86px;
}

.fs-section__title {
  margin: 0 0 4px;
  font-size: clamp(1.5rem, 2.4vw, 2rem);
  font-weight: 900;
  letter-spacing: -0.03em;
  color: var(--sf-forest-deep);
}

.fs-section__sub {
  margin: 0 0 16px;
  font-size: 14.5px;
  line-height: 1.5;
  color: var(--sf-muted);
}

.fs-perks {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.fs-perk {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  grid-template-rows: auto 1fr;
  column-gap: 14px;
  padding: 15px 16px;
  border-radius: 16px;
  background: var(--sf-paper);
  box-shadow: 0 18px 44px -34px rgba(13, 52, 36, 0.55), 0 1px 3px rgba(13, 52, 36, 0.05);
  transition: transform 180ms ease, box-shadow 180ms ease;
}

.fs-perk:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 50px -30px rgba(13, 52, 36, 0.6), 0 1px 3px rgba(13, 52, 36, 0.06);
}

.fs-perk__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  grid-row: 1 / 3;
  border-radius: 13px;
  background: var(--sf-leaf-wash);
  color: var(--sf-forest);
}

.fs-perk__title {
  grid-column: 2;
  margin: 0 0 3px;
  font-size: 14.5px;
  font-weight: 800;
  letter-spacing: -0.015em;
  line-height: 1.3;
  color: var(--sf-ink);
}

.fs-perk__body {
  grid-column: 2;
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--sf-muted);
}

/* ── How it works ────────────────────────────────────────────────────── */

.fs-steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: none;
}

.fs-step {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 16px 15px;
  border-radius: 16px;
  background: var(--sf-paper);
  box-shadow: 0 18px 44px -34px rgba(13, 52, 36, 0.55), 0 1px 3px rgba(13, 52, 36, 0.05);
}

.fs-step__num {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--sf-forest);
  color: #fff;
  font-size: 12.5px;
  font-weight: 900;
}

.fs-step__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: var(--sf-leaf-wash);
  color: var(--sf-forest);
}

.fs-step__title {
  margin: 0 0 5px;
  font-size: 14.5px;
  font-weight: 800;
  letter-spacing: -0.015em;
  color: var(--sf-ink);
}

.fs-step__body {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--sf-muted);
}

.fs-steps__note {
  margin: 12px 0 0;
  padding: 12px 16px;
  border-radius: 13px;
  background: var(--sf-leaf-wash);
  font-size: 13.5px;
  line-height: 1.55;
  font-weight: 600;
  color: #14603a;
}

/* ── Narrow ──────────────────────────────────────────────────────────── */

@media (max-width: 1180px) {
  .fs__shell {
    grid-template-columns: minmax(0, 1fr);
    gap: 40px;
  }

  .fs__rail {
    max-width: 560px;
    margin-inline: auto;
  }

  .fs__scenery { height: 560px; }
}

@media (max-width: 1080px) {
  .fs-hero {
    grid-template-columns: minmax(0, 1fr);
    gap: 28px;
  }

  .fs-hero__copy,
  .fs-hero__badge { grid-column: 1; }

  .fs-hero__badge { justify-self: stretch; }

  /*
   * Once the badge drops below the copy, the copy is the full width of the
   * page and there is no right-hand half to leave the mountains in. A scrim
   * across the whole scene replaces the side one — lighter, because at this
   * width the ridges are the only thing keeping the hero from being a white
   * box.
   */
  .fs__veil {
    background: linear-gradient(to bottom, rgba(243, 245, 241, 0.88), rgba(243, 245, 241, 0.6));
  }
}

@media (max-width: 900px) {
  .fs-offer { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .fs-included { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 460px) {
  .fs-included { grid-template-columns: minmax(0, 1fr); }
  .fs-hero__title { white-space: normal; }
}

@media (max-width: 720px) {
  .fs__shell { padding-top: 24px; }
  .fs__flow { gap: 36px; }
  .fs__scenery { height: 480px; }
  .fs-included { padding: 14px 16px; }
}

@media (prefers-reduced-motion: reduce) {
  .fs-perk,
  .fs-perk:hover {
    transform: none;
    transition: none;
  }
}
</style>
