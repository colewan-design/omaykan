<script setup lang="ts">
import { Bike, ShoppingBag } from '@lucide/vue'
import FdHeader from '@pos/web/landing/FdHeader.vue'
import FdFooter from '@pos/web/landing/FdFooter.vue'
import { vReveal } from '@pos/web/landing/reveal'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'
import {
  DELIVERY_BASE_FEE_CENTS,
  DELIVERY_BASE_KM,
  DELIVERY_MAX_KM,
  DELIVERY_PER_KM_CENTS,
} from '@pos/web/commerce/delivery'

// The page someone opens when they want to know who they would be dealing
// with — and, more to the point, why a shop's prices here are the same as the
// prices on its shelf. Everything on it is a claim we have to be able to stand
// behind, so the copy is drawn from documentation/positioning.md rather than
// written fresh.
//
// Laid out as a centred editorial column — a tab strip, then alternating bands
// of a heading, a photograph and a short paragraph, rules between them. It is
// the shape a grocer's about page takes because it works: someone skimming for
// one answer can find it without reading the rest.
//
// Every link back to shopping is a real navigation home — this is a separate
// Vite entry, not a route, so #shop anchors would resolve against this page.
const SHOP_HREF = '/'

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

// Quoted from the same constants the checkout charges from, so the page can't
// promise one fee while the cart bills another. Whole pesos: these are round
// figures and "₱49.00" reads like a receipt, not a promise.
const peso = (cents: number) => `₱${cents / 100}`
const baseFee = peso(DELIVERY_BASE_FEE_CENTS)
const perKm = peso(DELIVERY_PER_KM_CENTS)
</script>

<template>
  <div class="landing fd">
    <FdHeader :shop-href="SHOP_HREF" @search="search" />

    <main class="ab">
      <div class="ab__col">
        <h1 class="ab__pagetitle">About Us</h1>

        <!-- ── Section tabs ───────────────────────────────────────────── -->
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

        <!-- ── Lede film ──────────────────────────────────────────────── -->
        <section class="ab-sec ab-sec--tight">
          <video
            v-reveal
            class="ab-film"
            poster="/delivery/hero-market-poster.webp"
            preload="metadata"
            controls
            playsinline
          >
            <source src="/delivery/hero-market.mp4" type="video/mp4" />
          </video>
          <p v-reveal="60" class="ab-copy ab-copy--lede">
            Omaykan is a commission-free ordering channel for the shops of Baguio. You order from a
            neighbourhood store, someone at that store packs it, a rider from your own area brings
            it over, and you pay the person who hands it to you. We take nothing out of that
            order — not from the shop, not from the rider, and not from you.
          </p>
        </section>

        <hr class="ab-rule" />

        <!-- ── Three steps ────────────────────────────────────────────── -->
        <section class="ab-sec">
          <h2 v-reveal class="ab-h2">Grocery Delivery In 3 Simple Steps</h2>
          <div class="ab-trio">
            <figure v-reveal="40" class="ab-trio__item">
              <img
                src="/card-shop-online.webp"
                alt="The Omaykan storefront open on a phone, showing a shop's aisles"
                loading="lazy"
              />
              <figcaption>
                Shop a real neighbourhood store from the web or your phone — its own shelf, at the
                price it charges over the counter.
              </figcaption>
            </figure>
            <figure v-reveal="90" class="ab-trio__item">
              <img
                src="/card-register.webp"
                alt="A shopkeeper ringing up an order on the counter till"
                loading="lazy"
              />
              <figcaption>
                Your order lands on that shop's own till, in the same queue as its walk-in
                customers. Nobody relays it through a call centre.
              </figcaption>
            </figure>
            <figure v-reveal="140" class="ab-trio__item">
              <img
                src="/card-riders.webp"
                alt="A delivery rider checking their phone for the next drop"
                loading="lazy"
              />
              <figcaption>
                A rider working your area collects it and brings it to your door. Pay cash or GCash
                when it reaches you — or collect it yourself for free.
              </figcaption>
            </figure>
          </div>
        </section>

        <hr class="ab-rule" />

        <!-- ── Our promise ────────────────────────────────────────────── -->
        <section class="ab-sec">
          <h2 v-reveal class="ab-h2">Our Promise</h2>
          <p v-reveal="40" class="ab-copy">
            Delivery apps take 20–30% from the shop, revise the rider's rate downward, and hand you
            a menu marked up to survive both. Everyone in that transaction ends up worse off than
            they were before the app arrived. Our promise is the three lines that undo it — and
            every one of them is something you can check for yourself.
          </p>
          <div class="ab-promise">
            <article v-reveal="60" class="ab-promise__card">
              <span class="ab-promise__figure">0%</span>
              <h3>Commission from the shop</h3>
              <p>
                Not on the first order, not on the thousandth. The shop keeps the whole of what you
                pay it. We charge a flat monthly subscription, and that is the entirety of what we
                ever take.
              </p>
            </article>
            <article v-reveal="110" class="ab-promise__card">
              <span class="ab-promise__figure">100%</span>
              <h3>Of the delivery fee to the rider</h3>
              <p>
                The fee for delivery is the rider's, in full. We take no cut of it and we do not
                quietly revise their rate. They see exactly what they earned on every drop.
              </p>
            </article>
            <article v-reveal="160" class="ab-promise__card">
              <span class="ab-promise__figure ab-promise__figure--word">In-store</span>
              <h3>Prices for you</h3>
              <p>
                What the shop charges at its counter is what you see here. No inflated menu, no
                service fee, no small-order fee — the shop's price, and a delivery fee stated
                plainly.
              </p>
            </article>
          </div>
        </section>

        <hr class="ab-rule" />

        <!-- ── The covenant ───────────────────────────────────────────── -->
        <section class="ab-sec">
          <h2 v-reveal class="ab-h2">The Price You See Is The Price In The Shop</h2>
          <img
            v-reveal="40"
            class="ab-banner"
            src="/pos-counter-wide.png"
            alt="A shop counter with the Omaykan till open on it"
            loading="lazy"
          />
          <p v-reveal="60" class="ab-copy">
            This is the whole of what makes us different, so we did not leave it to good intentions.
            A shop's till and its storefront read the same product record — there is no second price
            to set, and no screen anywhere that a markup could be typed into. If a shop cannot sell
            it to you at the counter for what it says here, it is not sold here either. Find an item
            priced above its shelf and tell us; that is not a support ticket, it is the one rule.
          </p>
        </section>

        <hr class="ab-rule" />

        <!-- ── Two promises with icons ────────────────────────────────── -->
        <section class="ab-sec">
          <div class="ab-duo">
            <div v-reveal class="ab-duo__item">
              <span class="ab-duo__icon"><ShoppingBag :size="30" :stroke-width="1.75" /></span>
              <h3 class="ab-duo__title">Pickup Is Always Free</h3>
              <p>
                Every shop on Omaykan takes pickup orders. Reserve what you want, walk over when it
                is packed, pay nothing for delivery — and still at the counter price. On a small
                basket it is very often the right answer, so we say so.
              </p>
              <a :href="SHOP_HREF" class="ab-duo__link">Shop Now &gt;</a>
            </div>
            <div v-reveal="80" class="ab-duo__item">
              <span class="ab-duo__icon"><Bike :size="30" :stroke-width="1.75" /></span>
              <h3 class="ab-duo__title">A Delivery Fee That Goes To The Rider</h3>
              <p>
                Flat {{ baseFee }} for the first {{ DELIVERY_BASE_KM }} km, then {{ perKm }} for
                each kilometre after that, out to {{ DELIVERY_MAX_KM }} km. Stated before you order,
                unchanged at the door, and paid in full to the person who carried it.
              </p>
              <a href="/rider" class="ab-duo__link">Ride With Omaykan &gt;</a>
            </div>
          </div>
        </section>

        <hr class="ab-rule" />

        <!-- ── For shops ──────────────────────────────────────────────── -->
        <section class="ab-sec">
          <h2 v-reveal class="ab-h2">Omaykan For Shops</h2>
          <img
            v-reveal="40"
            class="ab-banner"
            src="/hero-merchant.webp"
            alt="A shop owner behind the counter of their store"
            loading="lazy"
          />
          <p v-reveal="60" class="ab-copy">
            A storefront, a register for the counter, and riders — all three switch on the day a
            shop signs up, for one flat monthly fee and no commission on anything sold. Orders from
            this site land in the same queue as the walk-ins, priced from the same catalog, so there
            is no second system to keep in step. The customers stay the shop's own.
            <a href="/signup">Put your shop on Omaykan &gt;</a>
          </p>
        </section>

        <!-- ── Ready to shop ──────────────────────────────────────────── -->
        <a v-reveal href="/" class="ab-cta">
          <img src="/delivery/hero-market-poster.webp" alt="" loading="lazy" />
          <span class="ab-cta__copy">
            <span class="ab-cta__title">Ready to shop?</span>
            <span class="ab-cta__sub">Start with the shops near you. &gt;</span>
          </span>
        </a>

        <hr class="ab-rule" />

        <!-- ── Riding with us ─────────────────────────────────────────── -->
        <section class="ab-sec">
          <h2 v-reveal class="ab-h2">Riding With Omaykan</h2>
          <div class="ab-duo-img">
            <img
              v-reveal="40"
              src="/delivery/hero-rider.webp"
              alt="A rider carrying two bags of groceries"
              loading="lazy"
            />
            <img
              v-reveal="90"
              src="/card-riders.webp"
              alt="A rider on a motorbike checking the next drop"
              loading="lazy"
            />
          </div>
          <p v-reveal="60" class="ab-copy">
            A rider keeps every peso of the fee on every drop they make, sees what a job pays before
            they accept it, and works across every shop on the platform rather than for one. We take
            no cut and we do not set your rate for you. Apply with a licence and a plate, and you
            can be on the board once you are cleared.
            <a href="/rider">Apply to ride &gt;</a>
          </p>
        </section>

        <hr class="ab-rule" />

        <!-- ── Where we are ───────────────────────────────────────────── -->
        <section class="ab-sec ab-sec--last">
          <h2 v-reveal class="ab-h2">We Are Starting In Baguio</h2>
          <p v-reveal="40" class="ab-copy">
            One city, and one neighbourhood at a time. Ten shops on the same few streets is worth
            more than a hundred scattered across Luzon: it is what lets a rider stack drops instead
            of idling, what keeps the fee low enough to be worth paying, and what carries word of
            mouth down a street. We would rather be genuinely useful where you live than thinly
            available everywhere. A problem with an order, a shop you would like to see on here, or
            a question about any of the above — write to
            <a :href="supportMailto('Omaykan hello')">{{ SUPPORT_EMAIL }}</a> and a person will
            answer you.
          </p>
        </section>
      </div>
    </main>

    <FdFooter :shop-href="SHOP_HREF" />
  </div>
</template>

<style scoped>
/* A centred editorial column on white — the whole page is one measure, and
   every band inside it lines up on the same left and right edge. */
.ab { background: #fff; padding: 0 var(--fd-gutter) 64px; }
.ab__col { max-width: 1040px; margin: 0 auto; }

.ab__pagetitle {
  margin: 0;
  padding: 22px 0 14px;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

/* ── Tab strip ─────────────────────────────────────────────────────── */
.ab-tabs {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
}
.ab-tabs__item {
  padding: 13px 16px;
  border: 1px solid var(--separator-strong);
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.ab-tabs__item:hover { border-color: var(--accent-pressed); color: var(--accent-pressed); }
/* The current page reads as a filled tab with an accent edge, so the strip
   says where you are as well as where you can go. */
.ab-tabs__item--current {
  background: var(--accent-light);
  border-color: var(--accent-border);
  border-left: 4px solid var(--accent-pressed);
  color: var(--accent-deep);
  font-weight: 700;
}

/* ── Section rhythm ────────────────────────────────────────────────── */
.ab-sec { padding: 44px 0; text-align: center; }
.ab-sec--tight { padding-top: 32px; }
.ab-sec--last { padding-bottom: 8px; }

.ab-rule {
  height: 0;
  margin: 0;
  border: 0;
  border-top: 1px solid var(--separator);
}

.ab-h2 {
  margin: 0 0 26px;
  font-size: clamp(1.35rem, 2.2vw, 1.75rem);
  font-weight: 800;
  letter-spacing: -0.025em;
  line-height: 1.2;
  color: var(--text-primary);
}

/* Centred, and held to a readable measure rather than the full column. */
.ab-copy {
  max-width: 760px;
  margin: 24px auto 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-secondary);
}
.ab-copy--lede { font-size: 15px; }
.ab-copy a {
  color: var(--accent-deep);
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
  white-space: nowrap;
}
.ab-copy a:hover { color: var(--accent-pressed); }

/* ── Lede film ─────────────────────────────────────────────────────── */
.ab-film {
  display: block;
  width: 100%;
  max-width: 840px;
  margin: 0 auto;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  background: #0b3a1f;
  border-radius: 6px;
}

/* ── Three steps ───────────────────────────────────────────────────── */
.ab-trio {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 22px;
}
.ab-trio__item { margin: 0; }
.ab-trio__item img {
  width: 100%;
  height: 210px;
  object-fit: cover;
  border-radius: 6px;
  background: var(--bg-elevated);
}
.ab-trio__item figcaption {
  margin-top: 14px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-secondary);
}

/* ── Promise cards ─────────────────────────────────────────────────── */
.ab-promise {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 18px;
  margin-top: 30px;
}
.ab-promise__card {
  padding: 26px 22px;
  background: var(--bg-base);
  border: 1px solid var(--separator);
  border-radius: 8px;
}
.ab-promise__figure {
  display: block;
  font-size: 40px;
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1;
  color: var(--accent-pressed);
}
/* "In-store" is a word where the others are a number; drop it a size so the
   three sit on the same optical line instead of one wrapping. */
.ab-promise__figure--word { font-size: 29px; }
.ab-promise__card h3 {
  margin: 14px 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}
.ab-promise__card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-secondary);
}

/* ── Wide banners ──────────────────────────────────────────────────── */
.ab-banner {
  width: 100%;
  height: 260px;
  object-fit: cover;
  border-radius: 6px;
  background: var(--bg-elevated);
}

/* ── Icon duo ──────────────────────────────────────────────────────── */
.ab-duo {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 44px;
}
.ab-duo__item { max-width: 420px; margin: 0 auto; }
.ab-duo__icon {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: var(--accent-pressed);
  color: #fff;
}
.ab-duo__title {
  margin: 0 0 12px;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
.ab-duo__item p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}
.ab-duo__link {
  display: inline-block;
  margin-top: 12px;
  font-size: 13px;
  font-weight: 700;
  color: var(--accent-deep);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.ab-duo__link:hover { color: var(--accent-pressed); }

/* ── Ready-to-shop banner ──────────────────────────────────────────── */
.ab-cta {
  position: relative;
  display: block;
  margin: 12px 0 44px;
  border-radius: 6px;
  overflow: hidden;
}
.ab-cta img {
  width: 100%;
  height: 230px;
  object-fit: cover;
}
/* Scrim: the market photo is bright across the middle, which is exactly where
   the words sit. */
.ab-cta::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(6,36,15,0.62), rgba(6,36,15,0.34));
}
.ab-cta__copy {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 10px;
  text-align: center;
  padding: 0 20px;
}
.ab-cta__title {
  font-size: clamp(1.75rem, 4vw, 2.75rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #fff;
  text-shadow: 0 2px 18px rgba(0,0,0,0.35);
}
.ab-cta__sub {
  font-size: 15px;
  font-weight: 600;
  color: rgba(255,255,255,0.94);
  text-decoration: underline;
  text-underline-offset: 4px;
}

/* ── Rider pair ────────────────────────────────────────────────────── */
.ab-duo-img {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 22px;
}
.ab-duo-img img {
  width: 100%;
  height: 240px;
  object-fit: cover;
  object-position: center 30%;
  border-radius: 6px;
  background: var(--bg-elevated);
}

@media (max-width: 760px) {
  .ab-sec { padding: 34px 0; }
  .ab-trio__item img { height: 180px; }
  .ab-banner { height: 200px; }
  .ab-cta img { height: 190px; }
  .ab-duo-img img { height: 200px; }
  .ab-duo { gap: 34px; }
}
</style>
