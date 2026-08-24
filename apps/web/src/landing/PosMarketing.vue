<script setup lang="ts">
import { ref } from 'vue'
import ParticleField from './ParticleField.vue'
import { vReveal } from './reveal'

// The Baguio Online Market product story — what the POS is, what sets it apart, and the
// Register/Dashboard breakdown. This used to sit on the consumer landing page;
// it now fronts the merchant registration page instead, because the landing
// page is the customer-facing delivery storefront.

withDefaults(
  defineProps<{
    /** Where the "Get started" CTAs point. On /signup this is the on-page
        anchor to the registration form. */
    signupHref?: string
  }>(),
  { signupHref: '/signup' },
)

const heroParticles = ref<InstanceType<typeof ParticleField> | null>(null)
function onHeroPointerMove(e: PointerEvent) {
  heroParticles.value?.setPointerFromEvent(e)
}
function onHeroPointerLeave() {
  heroParticles.value?.clearPointer()
}
</script>

<template>
  <div class="pm-root">
      <!-- ── Hero (centered) ──────────────────────────────────────────── -->
      <div class="lp-hero-wrap" @pointermove="onHeroPointerMove" @pointerleave="onHeroPointerLeave">
        <span class="lp-blob lp-blob--1" aria-hidden="true"></span>
        <span class="lp-blob lp-blob--2" aria-hidden="true"></span>
        <div class="lp-particle-zone lp-particle-zone--hero" aria-hidden="true">
          <ParticleField ref="heroParticles" variant="dark" :density="34" :repel-radius="0.5" :repel-strength="0.45" />
        </div>
        <div class="lp-hero">
          <div class="lp-hero-copy">
            <div class="lp-eyebrow hero-in" style="animation-delay:0ms">
              <span class="lp-eyebrow-dot"></span>
              Now in early access
            </div>
            <h1 class="lp-headline">
              <span class="lp-line hero-in" style="animation-delay:140ms">One app.</span><br>
              <span class="lp-line accent hero-in" style="animation-delay:220ms">Four businesses.</span>
            </h1>
            <p class="lp-sub hero-in" style="animation-delay:380ms">
              Baguio Online Market becomes a different point-of-sale depending on whether you run a café, a grocery, a restaurant, or a nail salon.
              <strong>Checkout in under 10 seconds.</strong>
            </p>
            <div class="lp-actions hero-in" style="animation-delay:500ms">
              <a :href="signupHref" class="lp-btn-primary">
                Get started free
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
              </a>
              <a href="/app" class="lp-btn-ghost">Explore the app</a>
            </div>
            <p class="lp-trust hero-in" style="animation-delay:580ms">
              No card required <span>·</span> Free during early access <span>·</span> Cancel anytime
            </p>
          </div>

          <div class="lp-hero-visual">
            <img
              class="lp-hero-character"
              src="/hero-character.png"
              alt="Illustrated cashier ringing up an order on Baguio Online Market"
              width="1448"
              height="1086"
            />
          </div>
        </div>
      </div>

      <!-- ── Who we are / What sets us apart ─────────────────────────────── -->
      <section class="lp-fortis">
        <div id="about" class="lp-container lp-fortis-block">
          <p v-reveal class="lp-fortis-eyebrow">Who we are</p>
          <div v-reveal="120" class="lp-fortis-intro">
            <p class="lp-fortis-lead">
              Built with a focus on speed, reliability, and simplicity, Baguio Online Market becomes a different point-of-sale depending on whether you run a café, a grocery, a restaurant, or a nail salon.
            </p>
            <p class="lp-fortis-sub">
              Pick a business type once, and the layout, catalog, and checkout flow follow — built to work as hard as you do, online or off.
            </p>
            <a href="/app" class="lp-fortis-btn">
              More About Baguio Online Market
              <span class="lp-fortis-btn-arrow">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
              </span>
            </a>
          </div>
        </div>

        <div id="highlights" class="lp-container lp-fortis-block">
          <p v-reveal class="lp-fortis-eyebrow">What sets us apart</p>

          <div class="lp-fortis-grid">
            <div v-reveal class="lp-fortis-photo-card reveal--scale">
              <div class="lp-fortis-photo lp-fortis-photo--manufacturing" style="background-image:url(/checkout-counter.png)" role="img" aria-label="A café employee ringing up an order on a Baguio Online Market tablet at the counter"></div>
              <div class="lp-fortis-photo-copy">
                <h3>Fast, one-tap checkout</h3>
                <p>Ring up an order in under 10 seconds with one-tap products and instant totals — on any device, at any counter.</p>
              </div>
            </div>

            <div v-reveal="60" class="lp-fortis-card lp-fortis-card--dark reveal--scale">
              <span class="lp-fortis-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 4v5c0 4.5-3 8-7 9-4-1-7-4.5-7-9V7l7-4z"/><path d="M9 12l2 2 4-4"/></svg>
              </span>
              <h3><span class="accent">Always On</span>, Even Offline</h3>
              <p>Baguio Online Market keeps ringing up sales even when the internet drops, then syncs everything the moment you're back online.</p>
            </div>

            <div v-reveal="120" class="lp-fortis-card lp-fortis-card--partners reveal--scale">
              <span class="lp-fortis-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
              </span>
              <h3>Built for Your Whole Team</h3>
              <p>Admin, Manager, and Cashier roles keep every teammate focused on exactly what their job needs — nothing more.</p>
            </div>

            <div v-reveal="60" class="lp-fortis-card lp-fortis-card--efficiency reveal--scale">
              <div class="lp-fortis-card--efficiency-copy">
                <h3>Smart inventory tracking</h3>
                <a href="/app" class="lp-fortis-btn lp-fortis-btn--sm">
                  Explore the app
                  <span class="lp-fortis-btn-arrow">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                  </span>
                </a>
              </div>
              <div class="lp-fortis-photo lp-fortis-photo--battery" style="background-image:url(/pos-counter-wide.png)" role="img" aria-label="A grocery employee handing a bagged order to a customer at a counter with a Baguio Online Market tablet"></div>
            </div>
          </div>
        </div>

        <div id="solutions" class="lp-container lp-fortis-block">
          <div class="lp-fortis-solutions-head">
            <p v-reveal class="lp-fortis-eyebrow">Our solutions</p>
            <h2 v-reveal="60" class="lp-fortis-heading">The Register &amp; The Dashboard</h2>
            <p v-reveal="120" class="lp-fortis-solutions-sub">
              From the countertop checkout to the numbers behind it, Baguio Online Market is engineered to keep every order fast and every insight current — for cafés, groceries, restaurants, and nail salons alike.
            </p>
          </div>

          <div class="lp-solutions-grid">
            <div v-reveal class="lp-solution-card reveal--scale">
              <div class="lp-solution-photo" style="background-image:url(/solution-checkout.png); background-position:58% 62%;" role="img" aria-label="A restaurant server tapping through a one-tap menu grid on a Baguio Online Market tablet"></div>
              <div class="lp-solution-body">
                <h3>Smart Checkout Register</h3>
                <p>A fast, one-tap register that adapts its layout and catalog to your business — built to keep lines moving at any counter.</p>
                <ul class="lp-solution-list">
                  <li>One-tap checkout in under 10 seconds</li>
                  <li>Works fully offline, syncs when back online</li>
                  <li>Card, cash, and digital payments built in</li>
                  <li>Role-based access for every teammate</li>
                </ul>
                <a href="/app" class="lp-fortis-btn lp-fortis-btn--sm">
                  Explore the Register
                  <span class="lp-fortis-btn-arrow">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                  </span>
                </a>
              </div>
            </div>

            <div v-reveal="60" class="lp-solution-card reveal--scale">
              <div class="lp-solution-photo" style="background-image:url(/solution-analytics.png); background-position:38% 58%;" role="img" aria-label="A nail salon owner reviewing revenue charts on a Baguio Online Market tablet"></div>
              <div class="lp-solution-body">
                <h3>Real-Time Analytics</h3>
                <p>Revenue, orders, and top products update the moment they happen — so you're never working off a stale report.</p>
                <ul class="lp-solution-list">
                  <li>Live revenue and order tracking</li>
                  <li>Low-stock alerts before you run out</li>
                  <li>Multi-location reporting</li>
                  <li>Secure, role-based dashboards</li>
                </ul>
                <a href="/app" class="lp-fortis-btn lp-fortis-btn--sm">
                  Explore Analytics
                  <span class="lp-fortis-btn-arrow">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                  </span>
                </a>
              </div>
            </div>
          </div>
        </div>
      </section>  </div>
</template>

<style scoped>
/* ── Hero (centered) ───────────────────────────────────────────────── */
.lp-hero-wrap {
  position: relative; overflow: hidden; background: var(--bg-surface);
  min-height: 100vh; display: flex; align-items: center;
  margin-top: -68px;
}
.lp-hero {
  position: relative; z-index: 1;
  padding: 0 40px 80px;
  display: grid; grid-template-columns: 1fr 1.15fr; align-items: center; gap: 64px; text-align: left;
  max-width: 1360px; margin: 0 auto;
}
.lp-hero-copy { display: flex; flex-direction: column; align-items: flex-start; }
.lp-eyebrow {
  display: inline-flex; align-items: center; gap: 7px; padding: 5px 14px;
  background: var(--accent-light); border: 1px solid var(--accent-border);
  border-radius: 980px; font-size: 13px; font-weight: 600; color: var(--accent); margin-bottom: 28px;
}
.lp-eyebrow-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); animation: lp-blink 2.2s ease-in-out infinite; }
@keyframes lp-blink { 0%, 100% { opacity: 1; } 55% { opacity: 0.25; } }
.lp-headline { font-size: clamp(2.8rem, 6vw, 5rem); font-weight: 800; line-height: 1.05; letter-spacing: -0.04em; margin: 0 0 22px; color: var(--text-primary); }
.lp-headline .accent { color: var(--accent); }
.lp-sub { font-size: 1.25rem; color: var(--text-secondary); line-height: 1.65; margin: 0 0 36px; max-width: 520px; }
.lp-sub strong { color: var(--text-primary); font-weight: 600; }
.lp-actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: flex-start; margin-bottom: 22px; }
.lp-btn-primary {
  display: inline-flex; align-items: center; gap: 9px; padding: 16px 30px;
  background: var(--accent); color: var(--accent-ink); font-size: 16px; font-weight: 800;
  border-radius: 13px; letter-spacing: -0.01em; border: 1px solid transparent; transition: transform 220ms, box-shadow 220ms, background 150ms;
  box-shadow: 0 12px 28px rgba(var(--accent-rgb),0.35), 0 3px 8px rgba(var(--accent-rgb),0.2);
}
.lp-btn-primary:hover { background: var(--accent-pressed); transform: translateY(-2px); box-shadow: 0 16px 34px rgba(var(--accent-rgb),0.4), 0 4px 10px rgba(var(--accent-rgb),0.22); }
.lp-btn-ghost {
  display: inline-flex; align-items: center; gap: 9px; padding: 16px 30px;
  background: var(--fill); color: var(--text-primary); font-size: 16px; font-weight: 800;
  border-radius: 13px; letter-spacing: -0.01em; border: 1px solid var(--separator-strong); transition: background 150ms;
}
.lp-btn-ghost:hover { background: var(--bg-elevated); }
.lp-trust { font-size: 12.5px; color: var(--text-tertiary); }
.lp-trust span { margin: 0 6px; }

.lp-hero-visual { position: relative; width: 100%; aspect-ratio: 1448 / 1086; }
.lp-hero-character { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }

/* ── Section chrome ────────────────────────────────────────────────── */
.lp-section { scroll-margin-top: 60px; padding: 96px 40px; }
.lp-section--surface { background: var(--bg-surface); border-top: 1px solid var(--separator); border-bottom: 1px solid var(--separator); }
.lp-section--dark { background: var(--marketing-dark); }
#highlights.lp-container {
  max-width: 1280px;
  min-height: 100vh;
  display: flex; flex-direction: column; justify-content: center;
}

.lp-eyebrow-label {
  display: inline-flex; align-items: center; padding: 5px 13px;
  background: var(--accent-light); border: 1px solid var(--accent-border);
  border-radius: 980px; font-size: 11px; font-weight: 700; letter-spacing: 0.08em;
  text-transform: uppercase; color: var(--accent); margin-bottom: 18px;
}
.lp-eyebrow-label--cta { background: rgba(255,255,255,0.08); border-color: rgba(255,255,255,0.16); color: rgba(255,255,255,0.55); }

/* ── Who we are / What sets us apart (Fortis Energy Systems) ─────────── */
.lp-fortis {
  --fortis-bg: #f5f9f6;
  --fortis-surface: #ffffff;
  --fortis-dark: #1a1a1a;
  /* Was #ea5b1c. Kept under the same name so every rule below still reads
     as "the section's one accent" — the value is now the Baguio green. */
  --fortis-orange: #22c55e;
  --fortis-ink: #1a1a1a;
  --fortis-text: #5b5f66;
  --fortis-border: rgba(0, 0, 0, 0.08);
  background: var(--fortis-bg);
  padding: 96px 40px;
}
.lp-fortis-block + .lp-fortis-block { margin-top: 96px; }
.lp-fortis-eyebrow {
  display: block; text-align: center; margin: 0 0 40px;
  font-size: 11px; font-weight: 700; letter-spacing: 0.14em; text-transform: uppercase; color: #8a8f98;
}

/* Intro (heading + copy, centered) */
.lp-fortis-intro { max-width: 700px; margin: 0 auto; text-align: center; }
.lp-fortis-lead { font-size: 1.375rem; font-weight: 700; letter-spacing: -0.015em; line-height: 1.45; color: var(--fortis-ink); margin: 0 0 24px; }
.lp-fortis-sub { font-size: 14.5px; line-height: 1.7; color: var(--fortis-text); margin: 0 0 32px; }

.lp-fortis-btn {
  display: inline-flex; align-items: center; align-self: flex-start; gap: 14px; padding: 6px 6px 6px 22px;
  /* Dark ink, not white: white on the green accent is only ~2.2:1. */
  background: var(--fortis-orange); color: #06240f; font-size: 14px; font-weight: 700;
  border-radius: 980px; transition: filter 150ms;
}
.lp-fortis-btn:hover { filter: brightness(1.06); }
.lp-fortis-btn-arrow {
  display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px;
  border-radius: 50%; background: var(--fortis-dark); color: #fff; flex-shrink: 0;
}
.lp-fortis-btn--sm { margin-top: 18px; padding: 5px 5px 5px 18px; font-size: 13.5px; }
.lp-fortis-btn--sm .lp-fortis-btn-arrow { width: 30px; height: 30px; }

/* What sets us apart (asymmetric grid) */
.lp-fortis-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; margin-top: 8px; }
.lp-fortis-photo-card { grid-column: 1; grid-row: 1 / span 2; background: var(--fortis-surface); border: 1px solid var(--fortis-border); border-radius: 20px; overflow: hidden; display: flex; flex-direction: column; padding: 16px 16px 0; }
.lp-fortis-card--dark { grid-column: 2; grid-row: 1; }
.lp-fortis-card--partners { grid-column: 3; grid-row: 1; }
.lp-fortis-card--efficiency { grid-column: 2 / span 2; grid-row: 2; }

.lp-fortis-photo { background-size: cover; background-position: center; }
.lp-fortis-photo--manufacturing { flex: 1 1 auto; min-height: 320px; background: linear-gradient(135deg, #2b2f36, #14161a 60%, #05060a); background-position: 62% 68%; border-radius: 14px; }
.lp-fortis-photo--battery { flex: 1 1 50%; min-height: auto; background-position: 45% 55%; }
.lp-fortis-photo-copy { padding: 22px 6px 26px; }
.lp-fortis-photo-copy h3 { font-size: 1.05rem; font-weight: 800; letter-spacing: -0.02em; line-height: 1.3; color: var(--fortis-ink); margin: 0 0 10px; }
.lp-fortis-photo-copy p { font-size: 13.5px; line-height: 1.65; color: var(--fortis-text); margin: 0; }

.lp-fortis-card { padding: 30px 28px; border-radius: 20px; display: flex; flex-direction: column; justify-content: center; }
.lp-fortis-card:not(.lp-fortis-card--dark) { background: var(--fortis-surface); border: 1px solid var(--fortis-border); }
.lp-fortis-card--dark { background: var(--fortis-dark); }
.lp-fortis-card--dark h3 { color: #fff; }
.lp-fortis-card--dark h3 .accent { color: var(--fortis-orange); }
.lp-fortis-card--dark p { color: rgba(255, 255, 255, 0.62); }
.lp-fortis-card h3 { font-size: 1.05rem; font-weight: 800; letter-spacing: -0.02em; line-height: 1.3; color: var(--fortis-ink); margin: 0 0 10px; }
.lp-fortis-card p { font-size: 13.5px; line-height: 1.65; color: var(--fortis-text); margin: 0; }
.lp-fortis-icon {
  display: inline-flex; align-items: center; justify-content: center; width: 38px; height: 38px;
  border-radius: 10px; background: var(--fortis-orange); color: var(--fortis-dark); margin-bottom: 16px; flex-shrink: 0;
}

.lp-fortis-card--efficiency { flex-direction: row; align-items: stretch; padding: 0; overflow: hidden; }
.lp-fortis-card--efficiency-copy { flex: 1 1 50%; padding: 30px 28px; display: flex; flex-direction: column; justify-content: center; }

/* Our solutions */
.lp-fortis-solutions-head { max-width: 640px; margin: 0 auto 48px; text-align: center; }
.lp-fortis-heading { font-size: clamp(1.9rem, 3.2vw, 2.5rem); font-weight: 800; letter-spacing: -0.02em; line-height: 1.15; color: var(--fortis-ink); margin: 0 0 14px; }
.lp-fortis-solutions-sub { font-size: 14.5px; line-height: 1.7; color: var(--fortis-text); margin: 0; }

.lp-solutions-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.lp-solution-card { background: var(--fortis-surface); border: 1px solid var(--fortis-border); border-radius: 20px; overflow: hidden; display: flex; flex-direction: column; }
.lp-solution-photo { aspect-ratio: 16 / 11; background-size: cover; background-position: center; }
.lp-solution-body { padding: 28px 28px 32px; display: flex; flex-direction: column; flex: 1; }
.lp-solution-body h3 { font-size: 1.2rem; font-weight: 800; letter-spacing: -0.02em; color: var(--fortis-ink); margin: 0 0 10px; }
.lp-solution-body > p { font-size: 13.5px; line-height: 1.65; color: var(--fortis-text); margin: 0 0 18px; }
.lp-solution-list { list-style: none; margin: 0 0 26px; padding: 0; display: flex; flex-direction: column; gap: 9px; }
.lp-solution-list li { font-size: 13px; color: var(--fortis-text); display: flex; align-items: center; gap: 9px; }
.lp-solution-list li::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: var(--fortis-orange); flex-shrink: 0; }
.lp-solution-body .lp-fortis-btn { margin-top: auto; align-self: flex-start; }

/* ── Responsive ───────────────────────────────────── */
@media (max-width: 1020px) {
  .lp-hero { grid-template-columns: 1fr; padding: 0 40px 64px; gap: 40px; text-align: center; }
  .lp-hero-copy { align-items: center; }
  .lp-actions { justify-content: center; }
  .lp-hero-visual { max-width: 720px; margin: 0 auto; }
}
@media (max-width: 880px) {
  .lp-fortis-grid { grid-template-columns: 1fr; }
  .lp-fortis-photo-card,
  .lp-fortis-card--dark,
  .lp-fortis-card--partners,
  .lp-fortis-card--efficiency {
    grid-column: auto; grid-row: auto;
  }
  .lp-fortis-card--efficiency { flex-direction: column; }
  .lp-fortis-photo--battery { min-height: 200px; }
  .lp-solutions-grid { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .lp-section { padding: 72px 20px; }
  .lp-fortis { padding: 72px 20px; }
  .lp-fortis-block + .lp-fortis-block { margin-top: 64px; }
  .lp-hero { padding: 0 20px 48px; }
}
</style>
