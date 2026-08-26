<script setup lang="ts">
import { ref } from 'vue'
import ParticleField from '@pos/web/landing/ParticleField.vue'
import { vReveal } from '@pos/web/landing/reveal'

// The merchant pitch on /signup.
//
// This was PosMarketing.vue, and it sold a point-of-sale: "One app. Four
// businesses", a register, a dashboard. Omaykan is an ecommerce platform now —
// the storefront customers order from is the product, and the register is one
// of the two counters it feeds. The narrative is rebuilt around that: what you
// get, how it works, what happens behind the counter, then the form.
//
// The registration form itself comes in through the #form slot rather than
// sitting below this component, so the closing section can put the pitch and
// the form side by side. Its markup stays in OnboardingPage.vue with the
// state that drives it.

const heroParticles = ref<InstanceType<typeof ParticleField> | null>(null)
function onHeroPointerMove(e: PointerEvent) {
  heroParticles.value?.setPointerFromEvent(e)
}
function onHeroPointerLeave() {
  heroParticles.value?.clearPointer()
}

// The demo recording in "Behind the counter" autoplays as ambient motion.
// Someone who asked their OS to stop animations gets the poster frame instead.
const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const steps = [
  {
    title: 'Create your store',
    body: 'Business name, your name, a password. Pick your business type — café, grocery, restaurant, or nail salon — and the catalog, layout, and checkout adapt to it.',
  },
  {
    title: 'Add your products',
    body: 'Start from a ready-made catalog for your business type, then change prices and add your own. What you list is exactly what customers see online.',
  },
  {
    title: 'Share your store code',
    body: "You get a short code the moment you sign up. Customers enter it in the Omaykan app to find your shop — it's in Settings whenever you need it again.",
  },
  {
    title: 'Take orders, both ways',
    body: 'Walk-ins ring up at the register. Online orders arrive in the same queue, a rider collects them, and both land in the same day’s numbers.',
  },
]
</script>

<template>
  <div class="pm-root">
    <!-- ── Hero ──────────────────────────────────────────────────────── -->
    <section class="pm-hero-wrap" @pointermove="onHeroPointerMove" @pointerleave="onHeroPointerLeave">
      <span class="lp-blob lp-blob--1" aria-hidden="true"></span>
      <span class="lp-blob lp-blob--2" aria-hidden="true"></span>
      <div class="lp-particle-zone lp-particle-zone--hero" aria-hidden="true">
        <ParticleField ref="heroParticles" variant="dark" :density="34" :repel-radius="0.5" :repel-strength="0.45" />
      </div>

      <div class="pm-hero">
        <div class="pm-hero-copy">
          <div class="lp-eyebrow hero-in" style="animation-delay:0ms">
            <span class="lp-eyebrow-dot"></span>
            Now in early access
          </div>
          <h1 class="pm-headline">
            <span class="lp-line hero-in" style="animation-delay:140ms">Sell online.</span><br>
            <span class="lp-line accent hero-in" style="animation-delay:220ms">Ring up in store.</span>
          </h1>
          <p class="pm-sub hero-in" style="animation-delay:380ms">
            Omaykan gives your shop a page on the delivery app, a point-of-sale for the counter,
            and riders to take orders to the door — one catalog behind all three.
            <strong>No commission on anything you sell.</strong>
          </p>
          <div class="pm-actions hero-in" style="animation-delay:500ms">
            <a href="#register" class="pm-btn-primary">
              Start selling free
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            </a>
            <a href="/app" class="pm-btn-ghost">See the app</a>
          </div>
          <p class="pm-trust hero-in" style="animation-delay:580ms">
            Free during early access <span>·</span> No card required <span>·</span> Cancel anytime
          </p>
        </div>
      </div>

      <!-- Out of the grid on purpose: on desktop this is pinned to the right
           edge of the viewport, not to the 1280px container. -->
      <div class="pm-hero-bleed">
        <img
          class="pm-hero-photo"
          src="/hero-merchant.webp"
          alt="A market vendor smiling behind her stall of pineapples, papayas, and dragon fruit"
          width="1600"
          height="1600"
          fetchpriority="high"
          decoding="async"
        />
      </div>
    </section>

    <!-- ── What you get ──────────────────────────────────────────────── -->
    <section id="what" class="pm-band">
      <div class="pm-container">
        <header class="pm-head">
          <p v-reveal class="pm-eyebrow">What you get</p>
          <h2 v-reveal="60" class="pm-h2">A storefront, a register, and riders.</h2>
          <p v-reveal="120" class="pm-head-sub">
            All three switch on the day you sign up. There is nothing to wire together and
            nothing to install — your shop is orderable the same afternoon.
          </p>
        </header>

        <div class="pm-trio">
          <article v-reveal class="pm-card reveal--scale">
            <div
              class="pm-card__photo"
              style="background-image:url(/card-shop-online.webp); background-position:center;"
              role="img"
              aria-label="The Omaykan app open on a phone, showing a shop's catalog and categories"
            ></div>
            <div class="pm-card__body">
              <h3>Your shop, online</h3>
              <p>
                Customers find you in the Omaykan app, browse your catalog, and order for delivery —
                at the same prices you charge at the counter. Orders land in the same queue as your walk-ins.
              </p>
            </div>
          </article>

          <article v-reveal="60" class="pm-card reveal--scale">
            <div
              class="pm-card__photo"
              style="background-image:url(/card-register.webp); background-position:center;"
              role="img"
              aria-label="A cashier tapping through the menu on a counter till"
            ></div>
            <div class="pm-card__body">
              <h3>A register for the counter</h3>
              <p>
                One-tap checkout in under 10 seconds, on any device. Card, cash, and digital payments
                built in — and it keeps ringing up sales when the internet drops.
              </p>
            </div>
          </article>

          <article v-reveal="120" class="pm-card reveal--scale">
            <div
              class="pm-card__photo"
              style="background-image:url(/card-riders.webp); background-position:center;"
              role="img"
              aria-label="A delivery rider on a motorbike checking their phone for the next order"
            ></div>
            <div class="pm-card__body">
              <h3>Riders on demand</h3>
              <p>
                Local riders collect from your counter and deliver. You pack the bag — we handle the
                trip and keep the customer posted the whole way.
              </p>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- ── How it works ──────────────────────────────────────────────── -->
    <section id="how" class="pm-band pm-band--dark">
      <div class="pm-container">
        <header class="pm-head">
          <p v-reveal class="pm-eyebrow pm-eyebrow--onDark">How it works</p>
          <h2 v-reveal="60" class="pm-h2 pm-h2--onDark">From sign-up to your first order.</h2>
          <p v-reveal="120" class="pm-head-sub pm-head-sub--onDark">
            Four steps, all of them yours to do. Nobody has to approve you first.
          </p>
        </header>

        <ol class="pm-steps">
          <li v-for="(step, i) in steps" :key="step.title" v-reveal="i * 60" class="pm-step">
            <span class="pm-step__num">{{ String(i + 1).padStart(2, '0') }}</span>
            <h3 class="pm-step__title">{{ step.title }}</h3>
            <p class="pm-step__body">{{ step.body }}</p>
          </li>
        </ol>
      </div>
    </section>

    <!-- ── Behind the counter ────────────────────────────────────────── -->
    <section id="counter" class="pm-band">
      <div class="pm-container">
        <header class="pm-head">
          <p v-reveal class="pm-eyebrow">Behind the counter</p>
          <h2 v-reveal="60" class="pm-h2">One catalog, both counters.</h2>
          <p v-reveal="120" class="pm-head-sub">
            Sell a bag of rice at the register and the online listing drops by one. There is no
            second inventory to keep in step, and no end-of-day reconciling between the two.
          </p>
        </header>
      </div>

      <!-- Out of the container on purpose. At half-card width the recording was
           a thumbnail of a UI you could not read; it is the evidence behind the
           claim above, so it runs the full width of the viewport. -->
      <div class="pm-demo">
        <video
          class="pm-demo__video"
          poster="/pos-demo-poster.webp"
          preload="metadata"
          :autoplay="!reduceMotion"
          muted
          loop
          playsinline
          disablepictureinpicture
          aria-label="A screen recording of Omaykan: signing in, ringing up an order at the register, then the dashboard, reports, customers, and settings"
        >
          <source src="/pos-demo.mp4" type="video/mp4" />
        </video>
      </div>

      <div class="pm-container">
        <div class="pm-points">
          <article v-reveal class="pm-point reveal--scale">
            <span class="pm-point__icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 8l-9-5-9 5v8l9 5 9-5V8z"/><path d="M3.3 7.5L12 12l8.7-4.5"/><path d="M12 12v9"/></svg>
            </span>
            <h3>Stock that counts itself</h3>
            <p>Every sale moves the same number, whichever counter it came from — with low-stock alerts before you run out.</p>
          </article>

          <article v-reveal="60" class="pm-point reveal--scale">
            <span class="pm-point__icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 4v5c0 4.5-3 8-7 9-4-1-7-4.5-7-9V7l7-4z"/><path d="M9 12l2 2 4-4"/></svg>
            </span>
            <h3>Always on, even offline</h3>
            <p>Omaykan keeps ringing up sales when the internet drops, then syncs everything the moment you're back.</p>
          </article>

          <article v-reveal="120" class="pm-point reveal--scale">
            <span class="pm-point__icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            </span>
            <h3>Roles for your whole team</h3>
            <p>Admin, Manager, and Cashier roles keep every teammate on exactly what their job needs — nothing more.</p>
          </article>

          <article v-reveal="180" class="pm-point reveal--scale">
            <span class="pm-point__icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20V11"/><path d="M10 20V4"/><path d="M16 20v-6"/><path d="M2 20h20"/></svg>
            </span>
            <h3>Today's numbers, live</h3>
            <p>Counter sales and online orders in one running total — top products, low stock, and every location, updated as it happens.</p>
            <a href="/app" class="pm-btn-pill pm-btn-pill--sm pm-point__cta">
              See the dashboard
              <span class="pm-btn-pill__arrow">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
              </span>
            </a>
          </article>
        </div>
      </div>
    </section>

    <!-- ── Conversion: pitch beside the form ─────────────────────────── -->
    <section id="register" class="pm-signup">
      <span class="lp-blob lp-blob--cta" aria-hidden="true"></span>

      <div class="pm-signup__grid">
        <!-- Sticky rather than the form: the form is the taller column, so
             pinning the pitch keeps the reasons in view while they fill it in,
             instead of pinning a card that can't fit on screen anyway. -->
        <aside class="pm-signup__pitch">
          <p class="pm-eyebrow pm-eyebrow--onDark">Start selling</p>
          <h2 class="pm-signup__title">Free while we're in early access.</h2>
          <p class="pm-signup__lead">
            No card, no setup fee, and no commission on your sales. Create your store now and it's
            orderable in the app today.
          </p>

          <ul class="pm-checks">
            <li>Your storefront on the Omaykan app</li>
            <li>The full point-of-sale, on any device</li>
            <li>Local riders for delivery</li>
            <li>Live sales and stock across both counters</li>
            <li>Roles for your whole team</li>
          </ul>

          <div class="pm-modes">
            <span class="pm-modes__label">Built for</span>
            <span class="pm-mode">Coffee shop</span>
            <span class="pm-mode">Grocery</span>
            <span class="pm-mode">Restaurant</span>
            <span class="pm-mode">Nail salon</span>
          </div>
        </aside>

        <div class="pm-signup__form">
          <slot name="form" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ── Hero ──────────────────────────────────────────────────────────── */
.pm-hero-wrap {
  position: relative;
  overflow: hidden;
  background: var(--bg-surface);
  /* The sticky header is 68px and in flow, so the hero fills what's left of
     the first screen rather than being pulled up under it. */
  min-height: calc(100vh - 68px);
  display: flex;
  align-items: center;
}

.pm-hero {
  position: relative;
  z-index: 1;
  display: grid;
  /* Copy column slightly the wider of the two: "Ring up in store." breaks to a
     third line under about 560px, which costs the headline its two-beat shape. */
  grid-template-columns: 1.05fr 1fr;
  align-items: center;
  gap: 64px;
  max-width: 1280px;
  margin: 0 auto;
  padding: 72px 40px;
}

.pm-hero-copy { display: flex; flex-direction: column; align-items: flex-start; min-width: 0; }

.lp-eyebrow {
  display: inline-flex; align-items: center; gap: 7px; padding: 5px 14px;
  background: var(--accent-light); border: 1px solid var(--accent-border);
  border-radius: 980px; font-size: 13px; font-weight: 600; color: var(--accent-deep); margin-bottom: 28px;
}
.lp-eyebrow-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); animation: pm-blink 2.2s ease-in-out infinite; }
@keyframes pm-blink { 0%, 100% { opacity: 1; } 55% { opacity: 0.25; } }

.pm-headline {
  margin: 0 0 22px;
  font-size: clamp(2.5rem, 4.4vw, 3.9rem);
  font-weight: 800;
  line-height: 1.04;
  letter-spacing: -0.04em;
  color: var(--text-primary);
}
.pm-headline .accent { color: var(--accent); }

.pm-sub {
  margin: 0 0 36px;
  max-width: 540px;
  font-size: 1.2rem;
  line-height: 1.6;
  color: var(--text-secondary);
}
.pm-sub strong { color: var(--text-primary); font-weight: 700; }

.pm-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 22px; }

.pm-btn-primary {
  display: inline-flex; align-items: center; gap: 9px; padding: 16px 30px;
  border: 1px solid transparent; border-radius: 13px;
  background: var(--accent); color: var(--accent-ink);
  font-size: 16px; font-weight: 800; letter-spacing: -0.01em;
  box-shadow: 0 12px 28px rgba(var(--accent-rgb),0.35), 0 3px 8px rgba(var(--accent-rgb),0.2);
  transition: transform 220ms, box-shadow 220ms, background 150ms;
}
.pm-btn-primary:hover {
  background: var(--accent-pressed); transform: translateY(-2px);
  box-shadow: 0 16px 34px rgba(var(--accent-rgb),0.4), 0 4px 10px rgba(var(--accent-rgb),0.22);
}

.pm-btn-ghost {
  display: inline-flex; align-items: center; padding: 16px 30px;
  border: 1px solid var(--separator-strong); border-radius: 13px;
  background: var(--fill); color: var(--text-primary);
  font-size: 16px; font-weight: 800; letter-spacing: -0.01em;
  transition: background 150ms;
}
.pm-btn-ghost:hover { background: var(--bg-elevated); }

.pm-trust { margin: 0; font-size: 12.5px; color: var(--text-tertiary); }
.pm-trust span { margin: 0 6px; }

/* Full bleed: the photo runs to the right edge of the viewport and the full
   height of the hero. The left edge is masked to transparent rather than cut,
   so it dissolves into the surface instead of ending in a seam beside the copy. */
.pm-hero-bleed {
  position: absolute; top: 0; right: 0; bottom: 0;
  /* Wide enough that the panel stays roughly square against a full-height hero,
     which is the ratio the asset is cropped for. */
  width: clamp(460px, 58vw, 1040px);
  z-index: 0;
  -webkit-mask-image: linear-gradient(to right, transparent 0%, #000 30%);
  mask-image: linear-gradient(to right, transparent 0%, #000 30%);
  animation: pm-photo-in 0.9s cubic-bezier(0.16, 0.84, 0.44, 1) 120ms both;
}
.pm-hero-photo { width: 100%; height: 100%; object-fit: cover; object-position: 58% 50%; display: block; }

/* Scales *down* into place, so the frame stays covered for the whole entrance. */
@keyframes pm-photo-in {
  from { opacity: 0; transform: scale(1.05); }
  to   { opacity: 1; transform: none; }
}
@media (prefers-reduced-motion: reduce) {
  .pm-hero-bleed { animation: none; }
}

/* ── Bands ─────────────────────────────────────────────────────────── */
.pm-band { padding: 104px 40px; background: var(--bg-base); scroll-margin-top: 68px; }
.pm-band--dark { background: var(--marketing-dark); }
.pm-container { max-width: 1280px; margin: 0 auto; }

.pm-head { max-width: 660px; margin: 0 auto 56px; text-align: center; }
.pm-eyebrow {
  margin: 0 0 18px;
  font-size: 11px; font-weight: 700; letter-spacing: 0.14em; text-transform: uppercase;
  color: #8a8f98;
}
.pm-eyebrow--onDark { color: var(--accent); }

.pm-h2 {
  margin: 0 0 16px;
  font-size: clamp(1.9rem, 3.4vw, 2.6rem);
  font-weight: 800; letter-spacing: -0.03em; line-height: 1.12;
  color: var(--text-primary);
}
.pm-h2--onDark { color: #fff; }

.pm-head-sub { margin: 0; font-size: 15px; line-height: 1.7; color: var(--text-secondary); }
.pm-head-sub--onDark { color: rgba(255,255,255,0.6); }

/* ── What you get (three equal cards) ──────────────────────────────── */
.pm-trio { display: grid; grid-template-columns: repeat(3, 1fr); gap: 22px; }

.pm-card {
  display: flex; flex-direction: column;
  border: 1px solid rgba(0,0,0,0.08); border-radius: 22px; overflow: hidden;
  background: var(--bg-surface);
}
.pm-card__photo { aspect-ratio: 16 / 11; background-size: cover; }
.pm-card__body { padding: 26px 26px 30px; }
.pm-card__body h3 {
  margin: 0 0 10px;
  font-size: 1.15rem; font-weight: 800; letter-spacing: -0.02em; color: var(--text-primary);
}
.pm-card__body p { margin: 0; font-size: 13.5px; line-height: 1.68; color: var(--text-secondary); }

/* ── How it works (four steps) ─────────────────────────────────────── */
.pm-steps {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px;
  margin: 0; padding: 0; list-style: none; counter-reset: none;
}
.pm-step {
  padding: 30px 26px 32px;
  border: 1px solid rgba(255,255,255,0.1); border-radius: 20px;
  background: rgba(255,255,255,0.03);
}
.pm-step__num {
  display: block; margin-bottom: 18px;
  font-size: 12px; font-weight: 800; letter-spacing: 0.1em;
  color: var(--accent);
}
.pm-step__title {
  margin: 0 0 10px;
  font-size: 1.05rem; font-weight: 800; letter-spacing: -0.02em; color: #fff;
}
.pm-step__body { margin: 0; font-size: 13.5px; line-height: 1.68; color: rgba(255,255,255,0.6); }

/* ── Behind the counter ────────────────────────────────────────────── */
/* The demo breaks out of the 1280px container and the band's padding, centred
   on the page by the margin/transform pair rather than by the layout it just
   escaped. */
.pm-demo {
  position: relative;
  /* Wider than the 1280px container so it still reads as a breakout, but no
     longer flush to the glass — and since it now has edges, it takes the same
     22px corner the cards in this section use. */
  width: min(92vw, 1680px);
  border-radius: 22px;
  margin: 0 0 48px 50%;
  transform: translateX(-50%);
  aspect-ratio: 16 / 9;
  /* Without a cap a 16:9 band is 1080px tall on a 1080p screen and swallows the
     section. The cap also crops the capture's taskbar off the bottom, which a
     fixed crop could not do — the recording zooms partway through, so the
     chrome does not stay in one place. Going much below this starts eating
     into the app itself on the zoomed passages. */
  max-height: 64vh;
  overflow: hidden;
  background: #0b0d10;
}
.pm-demo__video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; display: block; }

.pm-points { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
.pm-point {
  display: flex; flex-direction: column;
  padding: 28px 26px 30px; border-radius: 22px;
  border: 1px solid rgba(0,0,0,0.08); background: var(--bg-surface);
}
.pm-point h3 {
  margin: 0 0 10px;
  font-size: 1.05rem; font-weight: 800; letter-spacing: -0.02em; color: var(--text-primary);
}
.pm-point p { margin: 0; font-size: 13.5px; line-height: 1.65; color: var(--text-secondary); }
.pm-point__icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; margin-bottom: 16px; flex-shrink: 0;
  border-radius: 11px; background: var(--accent); color: var(--marketing-dark);
}
/* Bottom-aligned so the pill sits on one line across the row whatever the
   copy above it does. */
.pm-point__cta { margin-top: auto; padding-top: 18px; align-self: flex-start; }

.pm-btn-pill {
  display: inline-flex; align-items: center; align-self: flex-start; gap: 12px;
  padding: 6px 6px 6px 20px; border-radius: 980px;
  background: var(--accent); color: var(--accent-ink);
  font-size: 14px; font-weight: 700;
  transition: filter 150ms;
}
.pm-btn-pill:hover { filter: brightness(1.06); }
.pm-btn-pill--sm { margin-top: 18px; padding: 5px 5px 5px 18px; font-size: 13.5px; }
.pm-btn-pill__arrow {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; flex-shrink: 0;
  border-radius: 50%; background: var(--marketing-dark); color: #fff;
}

/* ── Conversion ────────────────────────────────────────────────────── */
.pm-signup {
  position: relative; overflow: hidden;
  padding: 104px 40px 112px;
  scroll-margin-top: 68px;
  background:
    radial-gradient(120% 90% at 100% 0%, rgba(187, 244, 81, 0.16), transparent 62%),
    linear-gradient(165deg, #23884f 0%, #12522f 100%);
}

.pm-signup__grid {
  position: relative; z-index: 1;
  display: grid; grid-template-columns: 1fr 520px; gap: 64px; align-items: start;
  max-width: 1180px; margin: 0 auto;
}

.pm-signup__pitch { position: sticky; top: 100px; color: #fff; }

.pm-signup__title {
  margin: 0 0 16px;
  font-size: clamp(2rem, 3.4vw, 2.7rem);
  font-weight: 800; letter-spacing: -0.03em; line-height: 1.1;
  color: #fff;
}

.pm-signup__lead {
  margin: 0 0 30px; max-width: 460px;
  font-size: 15px; line-height: 1.7; color: rgba(255,255,255,0.78);
}

.pm-checks { display: grid; gap: 12px; margin: 0 0 34px; padding: 0; list-style: none; }
.pm-checks li {
  position: relative; padding-left: 30px;
  font-size: 14.5px; line-height: 1.5; color: rgba(255,255,255,0.9);
}
.pm-checks li::before {
  content: ''; position: absolute; left: 0; top: 3px;
  width: 18px; height: 18px; border-radius: 50%;
  background: var(--accent-warm);
}
.pm-checks li::after {
  content: ''; position: absolute; left: 5px; top: 7px;
  width: 8px; height: 4px;
  border-left: 2px solid #12522f; border-bottom: 2px solid #12522f;
  transform: rotate(-45deg);
}

.pm-modes { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pm-modes__label {
  margin-right: 4px;
  font-size: 11px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase;
  color: rgba(255,255,255,0.5);
}
.pm-mode {
  padding: 6px 14px; border-radius: 980px;
  border: 1px solid rgba(255,255,255,0.22); background: rgba(255,255,255,0.08);
  font-size: 12.5px; font-weight: 600; color: rgba(255,255,255,0.92);
}

/* The card comes in through the slot, so it carries the *parent's* scope, not
   this one — its own width rule has to be overridden from here. */
.pm-signup__form :deep(.auth-card) { width: 100%; max-width: none; }

/* ── Responsive ────────────────────────────────────────────────────── */
@media (max-width: 1080px) {
  .pm-signup__grid { grid-template-columns: 1fr; gap: 44px; max-width: 620px; }
  .pm-signup__pitch { position: static; }
}

@media (max-width: 1020px) {
  .pm-hero { grid-template-columns: 1fr; gap: 40px; text-align: center; padding: 56px 40px; }
  .pm-hero-copy { align-items: center; }
  .pm-actions { justify-content: center; }
  .pm-sub { margin-left: auto; margin-right: auto; }
  /* One column, so the photo stops being an overlay and becomes an edge-to-edge
     band under the copy — still full bleed, just horizontally. */
  .pm-hero-wrap { flex-direction: column; align-items: stretch; justify-content: center; }
  .pm-hero-bleed {
    position: static; flex: 0 0 auto; width: 100%; aspect-ratio: 5 / 4;
    -webkit-mask-image: linear-gradient(to bottom, transparent 0%, #000 16%);
    mask-image: linear-gradient(to bottom, transparent 0%, #000 16%);
  }
  .pm-trio { grid-template-columns: 1fr; }
  .pm-steps { grid-template-columns: repeat(2, 1fr); }
  .pm-points { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 720px) {
  .pm-hero { padding: 40px 20px 56px; }
  .pm-band { padding: 72px 20px; }
  .pm-signup { padding: 72px 20px 80px; }
  .pm-steps { grid-template-columns: 1fr; }
  .pm-points { grid-template-columns: 1fr; }
  .pm-demo { margin-bottom: 40px; }
  .pm-head { margin-bottom: 40px; }
}
</style>
