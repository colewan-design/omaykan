<script setup lang="ts">
import {
  ArrowRight,
  Bike,
  CalendarClock,
  Check,
  HeartHandshake,
  Leaf,
  MapPin,
  Store,
  Tag,
  Users,
  Wallet,
} from '@lucide/vue'
import FdHeader from '@pos/web/landing/FdHeader.vue'
import FdFooter from '@pos/web/landing/FdFooter.vue'
import { vReveal } from '@pos/web/landing/reveal'
import { supportMailto } from '@pos/shared/index'

// The page someone opens when they want to know who they would be dealing
// with. Built to the approved About mockup: a tab strip, a split hero, then
// full-bleed bands that alternate ground colour rather than being divided by
// rules. Every claim on it is one we have to be able to stand behind, so the
// copy tracks documentation/positioning.md.
//
// The hero film is shared with the landing page. The supporting photography is
// kept in public/about with descriptive filenames so each crop can be tuned
// independently without coupling the page to download-folder filenames.
//
// Every link back to shopping is a real navigation home — this is a separate
// Vite entry, not a route, so #shop anchors would resolve against this page.
const SHOP_HREF = '/'

const ABOUT_NAV_CATEGORIES = [
  { id: 'groceries', name: 'Groceries' },
  { id: 'produce', name: 'Produce' },
  { id: 'dairy', name: 'Dairy' },
  { id: 'snacks', name: 'Snacks' },
  { id: 'meat-seafood', name: 'Meat & Seafood' },
  { id: 'bakery', name: 'Bakery' },
  { id: 'frozen', name: 'Frozen' },
  { id: 'international', name: 'International' },
  { id: 'ready-to-cook', name: 'Ready to Cook' },
  { id: 'ready-to-eat', name: 'Ready to Eat' },
]

// The strip under the header. Four surfaces that actually exist: this page,
// the merchant onboarding entry (signup.html), the rider portal (rider.html),
// and a person to write to. Nothing here points at a page we have not built.
const TABS = [
  { label: 'About Us', href: '/about', current: true },
  { label: 'Sell on Omaykan', href: '/signup', current: false },
  { label: 'Ride with Omaykan', href: '/rider', current: false },
  { label: 'Contact Us', href: supportMailto('Omaykan hello'), current: false },
]

function search(term: string) {
  // The shelves live on the landing page; ?q= is picked up there on arrival.
  window.location.href = term ? `/?q=${encodeURIComponent(term)}` : '/'
}

// The three reassurances under the hero buttons.
const HERO_MARKS = [
  { icon: Store, lines: ['Support', 'local shops'] },
  { icon: Leaf, lines: ['Fresh and', 'quality products'] },
  { icon: Users, lines: ['Stronger', 'communities'] },
]

const STEPS = [
  {
    n: '1',
    title: 'Shop nearby',
    body: 'Browse shops near you and add your favorite items to your cart.',
    image: '/about/shop-nearby.webp',
    alt: 'A shopper browsing fresh market produce on a phone in Baguio',
    width: 1672,
    height: 941,
  },
  {
    n: '2',
    title: 'Store prepares your order',
    body: 'Your chosen shop carefully packs your items fresh.',
    image: '/about/prepare-order.webp',
    alt: 'A local shopkeeper packing fresh vegetables into a paper bag',
    width: 1672,
    height: 941,
  },
  {
    n: '3',
    title: 'Delivered locally',
    body: 'A rider from your area delivers it to your door, supporting local livelihoods.',
    image: '/about/deliver-locally.webp',
    alt: 'A local delivery rider traveling between Baguio and La Trinidad',
    width: 1672,
    height: 941,
  },
]

// What a shop switches on the day it signs up.
const SELLER_FEATURES = [
  'Easy online storefront',
  'Simple product management',
  'Hassle-free order management',
  'Reach more local customers',
  'No percentage-based sales commission',
]

const RIDER_BENEFITS = [
  { icon: Wallet, title: 'Keep the full delivery fee', body: '100% goes to you.' },
  { icon: CalendarClock, title: 'Flexible, local deliveries', body: 'Work around your schedule.' },
  {
    icon: HeartHandshake,
    title: 'Help your community',
    body: 'Be part of a stronger, more connected Baguio.',
  },
]
</script>

<template>
  <div class="landing fd ab-root">
    <FdHeader
      :shop-href="SHOP_HREF"
      :fallback-categories="ABOUT_NAV_CATEGORIES"
      @search="search"
    />

    <main class="ab">
      <!-- ── Tab strip ────────────────────────────────────────────────── -->
      <div class="ab-band ab-band--tabs">
        <div class="ab-wrap">
          <nav class="ab-tabs" aria-label="About Omaykan">
            <a
              v-for="tab in TABS"
              :key="tab.label"
              :href="tab.href"
              class="ab-tabs__item"
              :class="{ 'ab-tabs__item--current': tab.current }"
              :aria-current="tab.current ? 'page' : undefined"
            >
              {{ tab.label }}
            </a>
          </nav>
        </div>
      </div>

      <!-- ── Hero ─────────────────────────────────────────────────────── -->
      <section class="ab-band ab-band--hero">
        <div class="ab-wrap ab-hero">
          <div v-reveal class="ab-hero__copy">
            <span class="ab-eyebrow">Our story</span>
            <h1 class="ab-hero__title">
              Your neighborhood market, <span class="ab-hero__accent">online.</span>
            </h1>
            <p class="ab-hero__lede">
              Omaykan connects you with nearby groceries, sari-sari stores, wet-market vendors, and
              local shops in Baguio and La Trinidad. Real shops. Real people. Delivered to your
              door.
            </p>
            <div class="ab-hero__actions">
              <a :href="SHOP_HREF" class="ab-btn ab-btn--solid">
                Start shopping<ArrowRight :size="17" :stroke-width="2.5" />
              </a>
              <a href="/signup" class="ab-btn ab-btn--outline">
                Sell on Omaykan<ArrowRight :size="17" :stroke-width="2.5" />
              </a>
            </div>
            <ul class="ab-marks">
              <li v-for="mark in HERO_MARKS" :key="mark.lines[1]">
                <component :is="mark.icon" :size="26" :stroke-width="1.75" />
                <span>{{ mark.lines[0] }}<br />{{ mark.lines[1] }}</span>
              </li>
            </ul>
          </div>

          <video
            v-reveal="60"
            class="ab-film"
            poster="/delivery/hero-market-poster.webp"
            preload="metadata"
            controls
            playsinline
          >
            <source src="/delivery/hero-market.mp4" type="video/mp4" />
          </video>
        </div>
      </section>

      <!-- ── How it works ─────────────────────────────────────────────── -->
      <section id="delivery" class="ab-band ab-band--mint">
        <div class="ab-wrap">
          <header class="ab-head">
            <span v-reveal class="ab-eyebrow">How it works</span>
            <h2 v-reveal class="ab-h2">Grocery Delivery in 3 Simple Steps</h2>
            <p v-reveal="40" class="ab-sub">
              From your favorite local shops to your door — it's that easy.
            </p>
          </header>

          <div class="ab-steps">
            <figure
              v-for="(step, i) in STEPS"
              :key="step.n"
              v-reveal="40 + i * 50"
              class="ab-step"
            >
              <img
                :src="step.image"
                :alt="step.alt"
                :width="step.width"
                :height="step.height"
                loading="lazy"
              />
              <figcaption>
                <h3><span class="ab-step__num">{{ step.n }}</span>{{ step.title }}</h3>
                <p>{{ step.body }}</p>
              </figcaption>
            </figure>
          </div>
        </div>
      </section>

      <!-- ── Our promise ──────────────────────────────────────────────── -->
      <section class="ab-band ab-band--white">
        <div class="ab-wrap">
          <header class="ab-head">
            <span v-reveal class="ab-eyebrow">Our promise</span>
            <h2 v-reveal class="ab-h2">A marketplace designed differently</h2>
            <p v-reveal="40" class="ab-sub">
              We're built to give more value to local shops, riders, and communities.
            </p>
          </header>

          <div class="ab-promise">
            <article v-reveal="60" class="ab-promise__card">
              <div class="ab-promise__top">
                <Store :size="38" :stroke-width="1.6" />
                <div class="ab-promise__fig">
                  <span class="ab-promise__num">0%</span>
                  <span class="ab-promise__label">Seller commission</span>
                </div>
              </div>
              <p>Keep more of what you earn. We don't take a percentage of your sales.</p>
            </article>

            <article v-reveal="110" class="ab-promise__card ab-promise__card--cream">
              <div class="ab-promise__top">
                <Bike :size="38" :stroke-width="1.6" />
                <div class="ab-promise__fig">
                  <span class="ab-promise__num">100%</span>
                  <span class="ab-promise__label">Delivery fee goes to the rider</span>
                </div>
              </div>
              <p>The full delivery fee is paid to the rider — no cuts and no deductions.</p>
            </article>

            <article v-reveal="160" class="ab-promise__card">
              <div class="ab-promise__top">
                <Tag :size="38" :stroke-width="1.6" />
                <div class="ab-promise__fig">
                  <span class="ab-promise__label">Prices set by the shop</span>
                </div>
              </div>
              <p>Shops control their own prices, just like their physical stores.</p>
            </article>
          </div>
        </div>
      </section>

      <!-- ── Fair for everyone ────────────────────────────────────────── -->
      <section class="ab-band ab-band--parity">
        <img
          v-reveal
          class="ab-parity__img"
          src="/about/market-community.webp"
          alt="A busy Baguio market filled with locally grown produce"
          width="1672"
          height="941"
          loading="lazy"
        />
        <div v-reveal="60" class="ab-parity__copy">
          <span class="ab-eyebrow">Fair for everyone</span>
          <h2 class="ab-h2">Local shops stay in control of their prices.</h2>
          <p>
            Unlike other platforms, we do not set or change shop prices. What you see on Omaykan is
            the price set by each shop — the same fair prices you'd find in their physical stores.
          </p>
          <a href="#for-shops" class="ab-btn ab-btn--outline">
            Learn more about our approach<ArrowRight :size="17" :stroke-width="2.5" />
          </a>
        </div>
      </section>

      <!-- ── For shops ────────────────────────────────────────────────── -->
      <section id="for-shops" class="ab-band ab-band--mint-pale">
        <div class="ab-wrap ab-tri">
          <div v-reveal class="ab-tri__copy">
            <span class="ab-eyebrow">Omaykan for shops</span>
            <h2 class="ab-h2">Built for local businesses</h2>
            <p>
              Whether you run a sari-sari store, a market stall, or a specialty shop, Omaykan gives
              you a simple way to sell online and reach more customers in your area.
            </p>
            <a href="/signup" class="ab-btn ab-btn--solid">
              Become a seller<ArrowRight :size="17" :stroke-width="2.5" />
            </a>
          </div>

          <img
            v-reveal="60"
            class="ab-tri__img ab-tri__img--seller"
            src="/about/shop-owner.webp"
            alt="A local sari-sari store owner standing proudly in his shop"
            width="1536"
            height="1024"
            loading="lazy"
          />

          <ul v-reveal="110" class="ab-tri__panel ab-checks">
            <li v-for="feature in SELLER_FEATURES" :key="feature">
              <span class="ab-checks__tick"><Check :size="12" :stroke-width="3.5" /></span>
              <span>{{ feature }}</span>
            </li>
          </ul>
        </div>
      </section>

      <!-- ── Ready to shop ────────────────────────────────────────────── -->
      <section class="ab-band ab-cta">
        <div class="ab-wrap ab-cta__inner">
          <div class="ab-cta__copy">
            <h2 v-reveal class="ab-cta__title">Ready to shop local?</h2>
            <p v-reveal="40" class="ab-cta__sub">
              Discover great prices, fresh finds, and support the shops in your neighborhood.
            </p>
            <a v-reveal="80" href="/" class="ab-btn ab-btn--lime">
              Browse nearby shops<ArrowRight :size="17" :stroke-width="2.5" />
            </a>
          </div>

          <div class="ab-cta__art" aria-hidden="true">
            <img src="/about/market-community.webp" alt="" width="1672" height="941" loading="lazy" />
          </div>
        </div>
      </section>

      <!-- ── Ride with Omaykan ────────────────────────────────────────── -->
      <section class="ab-band ab-band--white">
        <div class="ab-wrap ab-tri">
          <div v-reveal class="ab-tri__copy">
            <span class="ab-eyebrow">Ride with Omaykan</span>
            <h2 class="ab-h2">Deliver with purpose</h2>
            <p>
              Be your own boss and earn on your own terms. Help your community get the essentials
              they need, while keeping 100% of the delivery fee.
            </p>
            <a href="/rider" class="ab-btn ab-btn--solid">
              Become a rider<ArrowRight :size="17" :stroke-width="2.5" />
            </a>
          </div>

          <img
            v-reveal="60"
            class="ab-tri__img ab-tri__img--rider"
            src="/about/rider-city.webp"
            alt="A local delivery rider overlooking Baguio City"
            width="1536"
            height="1024"
            loading="lazy"
          />

          <ul v-reveal="110" class="ab-tri__panel ab-perks">
            <li v-for="perk in RIDER_BENEFITS" :key="perk.title">
              <span class="ab-perks__icon">
                <component :is="perk.icon" :size="20" :stroke-width="1.9" />
              </span>
              <span>
                <strong>{{ perk.title }}</strong>
                <em>{{ perk.body }}</em>
              </span>
            </li>
          </ul>
        </div>
      </section>

      <!-- ── Our home ─────────────────────────────────────────────────── -->
      <section class="ab-band ab-band--mint">
        <div class="ab-wrap">
          <div v-reveal class="ab-home">
            <div class="ab-home__copy">
              <span class="ab-eyebrow">Our home</span>
              <h2 class="ab-h2">Starting in Baguio and La Trinidad</h2>
              <p>
                Omaykan is beginning in Baguio and La Trinidad, home to vibrant markets and
                resilient local businesses. We're growing community by community, with the goal of
                bringing the convenience of local shopping to more cities across the Philippines.
              </p>
            </div>

            <!-- Holds the illustration's space and its one piece of real
                 content until the artwork itself is wired in. -->
            <div class="ab-home__art" role="img" aria-label="Baguio and La Trinidad, where Omaykan is starting">
              <img src="/about/deliver-locally.webp" alt="" width="1672" height="941" loading="lazy" />
              <span class="ab-home__pin"><MapPin :size="28" :stroke-width="2" /></span>
              <span class="ab-home__place">Baguio<br />&amp; La Trinidad</span>
            </div>
          </div>
        </div>
      </section>
    </main>

    <FdFooter
      :shop-href="SHOP_HREF"
      :show-back-to-top="false"
      :fallback-categories="ABOUT_NAV_CATEGORIES"
    />
  </div>
</template>

<style scoped>
/* Palette taken off the approved mockup rather than the marketing tokens: this
   page runs a deeper forest green and a near-black ink than the landing page,
   and a lime that appears nowhere else. Scoped so none of it leaks. */
.ab-root {
  --ab-ink: #0b1220;
  --ab-body: #4b5563;
  --ab-green: #04663a;
  --ab-green-hover: #035430;
  --ab-lime: #d9ff58;
  --ab-line: #e4ebe6;
  font-family: 'Nunito', var(--font-sans);
}

.ab { background: #fff; }

.ab-band { padding: 72px 0; }
.ab-band--tabs { padding: 26px 0 0; background: #fff; }
.ab-band--hero { padding: 44px 0 64px; background: #f9fcfa; }
.ab-band--white { background: #fff; }
.ab-band--mint { background: #f1f7f3; }
.ab-band--mint-pale { background: #f7fbf8; }

.ab-wrap {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 32px;
}

/* ── Tab strip ─────────────────────────────────────────────────────── */
/* A centred group of four rather than a full-width row: the strip is
   navigation, not a section, and it should read as one object. */
.ab-tabs {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}
.ab-tabs__item {
  min-width: 200px;
  padding: 13px 20px;
  border: 1px solid var(--ab-line);
  border-radius: 8px;
  background: #fff;
  font-size: 14px;
  font-weight: 700;
  text-align: center;
  color: var(--ab-ink);
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.ab-tabs__item:hover { border-color: var(--ab-green); color: var(--ab-green); }
.ab-tabs__item--current {
  background: #d1e6d5;
  border-color: #bcdcc4;
  color: var(--ab-green);
}

/* ── Shared type ───────────────────────────────────────────────────── */
.ab-eyebrow {
  display: block;
  margin-bottom: 12px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: var(--ab-green);
}
.ab-h2 {
  margin: 0 0 16px;
  font-size: clamp(1.75rem, 2.6vw, 2.125rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--ab-ink);
}
.ab-sub {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: var(--ab-body);
}
.ab-head { max-width: 720px; margin: 0 auto 40px; text-align: center; }
.ab-head .ab-eyebrow { margin-bottom: 8px; }

/* ── Buttons ───────────────────────────────────────────────────────── */
.ab-btn {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 13px 24px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  white-space: nowrap;
  transition: background 0.15s, border-color 0.15s, color 0.15s, transform 0.15s;
}
.ab-btn:active { transform: translateY(1px); }
.ab-btn--solid { background: var(--ab-green); color: #fff; }
.ab-btn--solid:hover { background: var(--ab-green-hover); }
.ab-btn--outline {
  border: 1.5px solid var(--ab-green);
  background: #fff;
  color: var(--ab-green);
}
.ab-btn--outline:hover { background: #eff7f1; }
/* Lime on the dark band: near-black ink, because white on #d9ff58 is ~1.2:1. */
.ab-btn--lime { background: var(--ab-lime); color: #0f2b16; }
.ab-btn--lime:hover { background: #c9f43f; }

/* ── Hero ──────────────────────────────────────────────────────────── */
.ab-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.86fr) minmax(0, 1fr);
  align-items: center;
  gap: 56px;
}
.ab-hero__title {
  margin: 0 0 18px;
  font-size: clamp(2.125rem, 3.4vw, 2.875rem);
  font-weight: 800;
  letter-spacing: -0.035em;
  line-height: 1.1;
  color: var(--ab-ink);
}
.ab-hero__accent { color: var(--ab-green); }
.ab-hero__lede {
  margin: 0;
  max-width: 44ch;
  font-size: 16px;
  line-height: 1.6;
  color: var(--ab-body);
}
.ab-hero__actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 26px; }

.ab-marks {
  display: flex;
  flex-wrap: wrap;
  gap: 30px;
  margin: 34px 0 0;
  padding: 0;
  list-style: none;
}
.ab-marks li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--ab-ink);
}
.ab-marks svg { flex: none; color: var(--ab-green); }

.ab-film {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  background: #0b3a1f;
  border-radius: 14px;
  box-shadow: 0 10px 30px rgba(4, 48, 27, 0.12);
}

/* ── Steps ─────────────────────────────────────────────────────────── */
.ab-steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 26px;
}
.ab-step {
  margin: 0;
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(11, 18, 32, 0.05);
}
.ab-step img {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}
.ab-step:nth-child(1) img { object-position: center 54%; }
.ab-step:nth-child(2) img { object-position: center 44%; }
.ab-step:nth-child(3) img { object-position: center 56%; }
.ab-step figcaption { padding: 18px 22px 24px; }
.ab-step h3 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 8px;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--ab-ink);
}
.ab-step__num {
  display: grid;
  place-items: center;
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--ab-green);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}
/* Indented to the title's text, not the badge, so the card reads as one block. */
.ab-step p {
  margin: 0;
  padding-left: 38px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--ab-body);
}

/* ── Promise ───────────────────────────────────────────────────────── */
.ab-promise {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
}
.ab-promise__card {
  padding: 30px 26px 28px;
  border-radius: 14px;
  background: #eff7f1;
  text-align: center;
}
.ab-promise__card--cream { background: #fdf8e7; }
.ab-promise__top {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  min-height: 62px;
  margin-bottom: 16px;
  color: var(--ab-green);
}
.ab-promise__num {
  display: block;
  font-size: 44px;
  font-weight: 800;
  letter-spacing: -0.045em;
  line-height: 1;
  color: var(--ab-green);
}
.ab-promise__label {
  display: block;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--ab-green);
}
.ab-promise__num + .ab-promise__label { margin-top: 6px; }
.ab-promise__card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--ab-body);
}

/* ── Fair for everyone ─────────────────────────────────────────────── */
/* The photograph runs to the page edge; the copy still stops on the same
   right-hand line as every other band, hence the computed padding. */
.ab-band--parity {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  padding: 0;
  background: #fff;
}
.ab-parity__img {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 340px;
  object-fit: cover;
  object-position: center 58%;
}
.ab-parity__copy {
  padding: 64px max(24px, calc((100vw - 1280px) / 2)) 64px 56px;
}
.ab-parity__copy p {
  margin: 0 0 26px;
  max-width: 52ch;
  font-size: 15px;
  line-height: 1.7;
  color: var(--ab-body);
}

/* ── Three-column bands (shops, riders) ────────────────────────────── */
.ab-tri {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.95fr) minmax(0, 0.92fr);
  align-items: center;
  gap: 26px;
}
.ab-tri__copy p {
  margin: 0;
  max-width: 44ch;
  font-size: 15px;
  line-height: 1.7;
  color: var(--ab-body);
}
.ab-tri__copy .ab-btn { margin-top: 26px; }
/* The ratio is the mockup's, and it is what sets the row's height — left to
   its intrinsic size a portrait photograph drags the whole band tall. */
.ab-tri__img {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 3 / 2;
  object-fit: cover;
  border-radius: 14px;
}
.ab-tri__img--seller { object-position: center 42%; }
.ab-tri__img--rider { object-position: center 48%; }
.ab-tri__panel {
  align-self: stretch;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 18px;
  margin: 0;
  padding: 28px 26px;
  border: 1px solid var(--ab-line);
  border-radius: 14px;
  background: #fff;
  list-style: none;
}

.ab-checks li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--ab-ink);
}
.ab-checks__tick {
  display: grid;
  place-items: center;
  flex: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--ab-green);
  color: #fff;
}

.ab-perks li { display: flex; align-items: flex-start; gap: 13px; }
.ab-perks__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #eff7f1;
  color: var(--ab-green);
}
.ab-perks strong {
  display: block;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--ab-ink);
}
.ab-perks em {
  display: block;
  margin-top: 3px;
  font-size: 13px;
  font-style: normal;
  line-height: 1.5;
  color: var(--ab-body);
}

/* ── Ready to shop ─────────────────────────────────────────────────── */
/* Dark on the left where the words are, opening into the market photograph on
   the right so the call to action keeps its contrast at every width. */
.ab-cta {
  padding: 0;
  background: #124c2b;
}
.ab-cta__inner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 0.78fr);
  align-items: stretch;
  min-height: 230px;
}
.ab-cta__copy {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  flex-direction: column;
  padding: 46px 52px 46px 0;
}
.ab-cta__art {
  position: relative;
  align-self: stretch;
  margin-right: -32px;
  overflow: hidden;
}
.ab-cta__art::after {
  position: absolute;
  inset: 0;
  content: '';
  background: linear-gradient(90deg, #124c2b 0%, rgba(18, 76, 43, 0.62) 24%, transparent 72%);
}
.ab-cta__art img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 52%;
}
.ab-cta__title {
  margin: 0 0 10px;
  font-size: clamp(1.75rem, 2.8vw, 2.25rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #fff;
}
.ab-cta__sub {
  margin: 0 0 24px;
  max-width: 44ch;
  font-size: 15px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.9);
}

/* ── Our home ──────────────────────────────────────────────────────── */
.ab-home {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  align-items: center;
  gap: 40px;
  padding: 44px 48px;
  border-radius: 18px;
  background: #fff;
}
.ab-home__copy p {
  margin: 0;
  max-width: 54ch;
  font-size: 15px;
  line-height: 1.7;
  color: var(--ab-body);
}
.ab-home__art {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  min-height: 190px;
  padding: 24px 28px;
  overflow: hidden;
  border-radius: 14px;
}
.ab-home__art::after {
  position: absolute;
  inset: 0;
  content: '';
  background: linear-gradient(90deg, transparent 20%, rgba(8, 45, 25, 0.72) 100%);
}
.ab-home__art img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 52%;
}
.ab-home__pin,
.ab-home__place { position: relative; z-index: 1; }
.ab-home__pin { color: #ffb11b; }
.ab-home__place {
  font-size: 17px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.02em;
  color: #fff;
}

@media (max-width: 900px) {
  .ab-tri { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
  /* The panel drops to a full-width row under the copy and the photograph. */
  .ab-tri__panel { grid-column: 1 / -1; }
  .ab-checks, .ab-perks { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
}

@media (max-width: 900px) {
  .ab-band { padding: 56px 0; }
  .ab-hero { grid-template-columns: 1fr; gap: 34px; }
  .ab-hero__lede { max-width: none; }
  .ab-band--parity { grid-template-columns: 1fr; }
  .ab-parity__img { min-height: 260px; }
  .ab-parity__copy { padding: 40px 24px 56px; }
  .ab-home { grid-template-columns: 1fr; padding: 32px 28px; }
  .ab-cta__inner { grid-template-columns: 1fr; }
  .ab-cta__copy { padding: 46px 0; }
  .ab-cta__art { min-height: 190px; margin: 0 -24px; border-width: 1px 0 0; }
}

@media (max-width: 760px) {
  .ab-wrap { padding: 0 16px; }
  .ab-band { padding: 46px 0; }
  .ab-tabs__item { min-width: 0; flex: 1 1 44%; }
  .ab-tri { grid-template-columns: 1fr; }
  .ab-tri__img { min-height: 220px; }
  .ab-marks { gap: 20px; }
  .ab-cta { padding: 0; }
  .ab-cta__copy { padding: 42px 0; }
  .ab-cta__art { min-height: 180px; margin: 0 -16px; }
}
</style>
