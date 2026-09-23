<script setup lang="ts">
import {
  ArrowRight,
  BadgePercent,
  Bike,
  ChartNoAxesColumnIncreasing,
  Check,
  CirclePlay,
  Layers3,
  Link2,
  MapPin,
  MonitorSmartphone,
  PackagePlus,
  ShieldCheck,
  ShoppingCart,
  Store,
  UserRound,
  UsersRound,
} from '@lucide/vue'
import { storefrontUrl } from '@pos/shared/index'
import { SIZES, srcSet } from '@pos/web/ui/responsiveImg'
import { SHOP_ROOT_DOMAIN, mainSiteOrigin } from '@pos/web/commerce/shopDomain'

/** Each named shop's own page; the last card is the signup form below. */
function shopHref(slug: string): string {
  return storefrontUrl(slug, {
    rootDomain: SHOP_ROOT_DOMAIN,
    origin: mainSiteOrigin() || window.location.origin,
  })
}

const stories = [
  { name: 'Balili Highland Farm', kind: 'Farm produce', location: 'La Trinidad, Benguet', image: '/about/market-community.webp', href: shopHref('balili-highland-farm') },
  { name: 'Aling Nena Market Stall', kind: 'Public market stall', location: 'Baguio City', image: '/hero-merchant-redesign.webp', href: shopHref('nenas-market-stall') },
  { name: 'Lourdes Mini Grocery', kind: 'Mini grocery', location: 'Baguio City', image: '/about/shop-owner.webp', href: shopHref('lourdes-mini-grocery') },
  { name: 'Your shop could be next', kind: 'Built for local business', location: 'Across the Philippines', image: '/solution-analytics.png', href: '#register' },
]
</script>

<template>
  <main class="seller-page">
    <section class="hero" aria-labelledby="seller-hero-title">
      <div class="hero__inner">
        <div class="hero__copy">
          <div class="hero__badge">
            <span><span class="hero__badge-dot"></span>Early access</span>
            <small>Join thousands of Filipino sellers</small>
          </div>
          <h1 id="seller-hero-title">Sell online.<br><span>Ring up in store.</span></h1>
          <p class="hero__lead">Omaykan gives you everything you need to sell in your store, online, and on the go — no commission, no hassle. Just a simpler way to grow your business.</p>
          <div class="hero__benefits" aria-label="What is included">
            <div class="mini-benefit"><BadgePercent :size="23" /><strong>0%<br>commission</strong></div>
            <div class="mini-benefit"><Store :size="23" /><strong>Your own<br>storefront</strong></div>
            <div class="mini-benefit"><MonitorSmartphone :size="23" /><strong>POS<br>included</strong></div>
            <div class="mini-benefit"><Bike :size="23" /><strong>Rider-ready<br>delivery</strong></div>
          </div>
          <div class="hero__actions">
            <a class="button button--primary" href="#register">Start selling free <ArrowRight :size="16" /></a>
            <a class="button button--secondary" href="#how"><CirclePlay :size="17" /> See how it works</a>
          </div>
          <div class="hero__trust" aria-label="Signup benefits">
            <span><Check :size="13" /> Free to get started</span><span><Check :size="13" /> No credit card required</span><span><Check :size="13" /> Set up in minutes</span>
          </div>
        </div>
        <div class="hero__media">
          <img src="/hero-merchant-redesign.webp" :srcset="srcSet('/hero-merchant-redesign.webp')" :sizes="SIZES.half" alt="A smiling Filipina market seller at her fresh produce stall" fetchpriority="high">
          <div class="hero__scribble" aria-hidden="true">Small sellers.<br>Bigger tomorrows.</div>
          <blockquote><span class="quote-mark">“</span><p>Omaykan helps local shops reach more customers — without losing what makes them local.</p><footer>Built for Filipino businesses</footer></blockquote>
        </div>
      </div>
    </section>

    <section id="what" class="section section--white">
      <div class="container">
        <header class="section-heading"><p class="eyebrow">Built for every kind of seller</p><h2>A storefront, a register, and riders.</h2><p>Everything you need to run and grow your business — online, in store, and for delivery.</p></header>
        <div class="solution-grid">
          <article class="solution-card">
            <div class="solution-card__media"><img src="/card-shop-online.webp" :srcset="srcSet('/card-shop-online.webp')" :sizes="SIZES.card" alt="The Omaykan storefront on a phone" loading="lazy"><span class="media-label"><Store :size="15" /> Your store is live!</span></div>
            <div class="solution-card__body"><span class="icon-box"><ShoppingCart :size="22" /></span><h3>Your shop, online</h3><p>Get your own beautiful storefront where customers can browse, order, and pay — on any device.</p><a href="#register">Learn more <ArrowRight :size="14" /></a></div>
          </article>
          <article class="solution-card">
            <div class="solution-card__media"><img src="/card-register.webp" :srcset="srcSet('/card-register.webp')" :sizes="SIZES.card" alt="A seller using a counter register" loading="lazy"></div>
            <div class="solution-card__body"><span class="icon-box"><MonitorSmartphone :size="22" /></span><h3>A register for the counter</h3><p>Ring up sales in store with a simple, powerful POS. Your online and offline sales stay in sync.</p><a href="#register">Learn more <ArrowRight :size="14" /></a></div>
          </article>
          <article class="solution-card">
            <div class="solution-card__media solution-card__media--rider"><img src="/card-riders.webp" :srcset="srcSet('/card-riders.webp')" :sizes="SIZES.card" alt="A rider ready for a delivery" loading="lazy"></div>
            <div class="solution-card__body"><span class="icon-box"><Bike :size="22" /></span><h3>Riders on demand</h3><p>Let customers order for delivery. We connect you with trusted local riders in your area.</p><a href="#register">Learn more <ArrowRight :size="14" /></a></div>
          </article>
        </div>
      </div>
    </section>

    <section id="how" class="section section--mint">
      <div class="container">
        <header class="section-heading"><p class="eyebrow">Get started in minutes</p><h2>From sign-up to your first order.</h2><p>Four simple steps. A lot more customers.</p></header>
        <ol class="steps">
          <li class="step-card"><span class="step-card__number">1</span><span class="step-card__icon"><UserRound :size="28" /></span><h3>Create your account</h3><p>Sign up in minutes with your basic information.</p></li>
          <li class="step-card"><span class="step-card__number">2</span><span class="step-card__icon"><PackagePlus :size="28" /></span><h3>Add your products</h3><p>Upload photos, set prices, and organize your menu or catalog.</p></li>
          <li class="step-card"><span class="step-card__number">3</span><span class="step-card__icon"><Link2 :size="28" /></span><h3>Share your store code</h3><p>Get your unique store link or QR code to start sharing anywhere.</p></li>
          <li class="step-card"><span class="step-card__number">4</span><span class="step-card__icon"><ShoppingCart :size="28" /></span><h3>Take orders, both ways</h3><p>Receive orders online and in store. We’ll keep everything in sync.</p></li>
        </ol>
      </div>
    </section>

    <section id="counter" class="section section--sync">
      <div class="container sync-container">
        <header class="section-heading"><p class="eyebrow">Synced across all channels</p><h2>One catalog, both counters.</h2><p>Update your products once. It’s live everywhere — your online store, in-store POS, and delivery.</p></header>
        <div class="dashboard-wrap">
          <span class="dashboard-note dashboard-note--left">Same products.<br>Everywhere.</span>
          <div class="dashboard" role="img" aria-label="Omaykan seller dashboard showing synced sales and orders">
            <aside class="dashboard__side"><strong>omay<span>kan</span></strong><nav><b>⌂&nbsp; Home</b><span>Orders</span><span>Products</span><span>Customers</span><span>Reports</span><span>Store settings</span></nav></aside>
            <div class="dashboard__main">
              <div class="dashboard__top"><div><h3>Good morning, Maria!</h3><p>Your business is doing great today.</p></div><span>Today⌄</span></div>
              <div class="dashboard__stats"><div><small>Sales today</small><strong>₱ 12,430</strong><em>+12% vs yesterday</em></div><div><small>Total orders</small><strong>48</strong><em>+10% vs yesterday</em></div><div><small>Online orders</small><strong>23</strong><em>Active orders</em></div><div><small>In-store orders</small><strong>25</strong><em>At the counter</em></div></div>
              <div class="dashboard__orders"><div class="dashboard__orders-head"><strong>Recent orders</strong><span>Search orders...</span></div><div class="order-row order-row--head"><span>Order</span><span>Customer</span><span>Channel</span><span>Amount</span><span>Status</span></div><div class="order-row"><span>#1001</span><span>Juan Dela Cruz</span><span><i>Online</i></span><span>₱350</span><span><b>Preparing</b></span></div><div class="order-row"><span>#1000</span><span>Maria Santos</span><span><i class="yellow">In-store</i></span><span>₱120</span><span><b>Completed</b></span></div><div class="order-row"><span>#0999</span><span>Carlo Reyes</span><span><i>Online</i></span><span>₱280</span><span><b class="yellow">On the way</b></span></div></div>
            </div>
          </div>
          <span class="dashboard-note dashboard-note--right">Online.<br>In store.<br>On delivery.<br>All in sync.</span>
        </div>
        <div class="sync-features">
          <article><span><Layers3 :size="23" /></span><h3>Real-time sync</h3><p>Product, stock, and orders stay updated across all channels.</p></article>
          <article><span><ChartNoAxesColumnIncreasing :size="23" /></span><h3>Built-in insights</h3><p>See what’s selling and grow with simple reports.</p></article>
          <article><span><UsersRound :size="23" /></span><h3>Work with your team</h3><p>Add staff and set access for your store.</p></article>
          <article><span><ShieldCheck :size="23" /></span><h3>Reliable and secure</h3><p>Your data and your business are always protected.</p></article>
        </div>
      </div>
    </section>

    <section id="stories" class="section section--stories">
      <div class="container">
        <div class="stories-heading"><div><p class="eyebrow">Real businesses. Real growth.</p><h2>Already selling on Omaykan.</h2><p>Meet local entrepreneurs who are reaching more customers and growing their business.</p></div><a href="/">See more stores <ArrowRight :size="14" /></a></div>
        <div class="stories-grid"><a v-for="story in stories" :key="story.name" class="story-card" :href="story.href"><img :src="story.image" :srcset="srcSet(story.image)" :sizes="SIZES.card" alt="" loading="lazy"><div><h3>{{ story.name }}</h3><p>{{ story.kind }}</p><small><MapPin :size="12" /> {{ story.location }}</small><span>View storefront <ArrowRight :size="13" /></span></div></a></div>
      </div>
    </section>

    <section id="register" class="signup-band">
      <div class="signup-band__inner">
        <aside class="signup-pitch">
          <p class="eyebrow eyebrow--light">Join thousands of sellers</p><h2>Open your Omaykan<br>store for free.</h2><p>It only takes a few minutes. Set up your store, add your products, and start accepting orders from customers near you — online and in store.</p>
          <ul><li><Check :size="14" /> ₱0 commission, ever</li><li><Check :size="14" /> Your own online storefront</li><li><Check :size="14" /> POS included</li><li><Check :size="14" /> Rider-ready delivery</li><li><Check :size="14" /> Built for local businesses</li></ul>
          <div class="signup-pitch__types"><small>Perfect for</small><div><span>Coffee shop</span><span>Grocery</span><span>Restaurant</span><span>Meat shop</span></div></div>
        </aside>
        <div class="signup-slot"><slot name="form" /></div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.seller-page { --green: #099447; --green-dark: #05743a; --green-deep: #045f30; --mint: #f1faf5; --ink: #111b17; --muted: #66716b; --line: #dfe9e3; overflow: hidden; background: #fff; color: var(--ink); }
.container { width: min(1180px, calc(100% - 48px)); margin: 0 auto; }
.hero { background: #fff; }.hero__inner { display: grid; grid-template-columns: 51% 49%; min-height: 650px; max-width: 1440px; margin: 0 auto; }.hero__copy { z-index: 2; display: flex; flex-direction: column; justify-content: center; padding: 58px 22px 48px max(32px, calc((100vw - 1180px) / 2)); }.hero__badge { display: flex; align-items: center; gap: 17px; margin-bottom: 22px; }.hero__badge > span { display: inline-flex; align-items: center; gap: 7px; padding: 8px 13px; border-radius: 999px; background: #e9f8ef; color: var(--green-dark); font-size: 12px; font-weight: 800; }.hero__badge-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--green); }.hero__badge small { color: #7a857f; font-size: 12px; }.hero h1 { margin: 0 0 18px; font-size: clamp(48px, 5.2vw, 74px); line-height: .94; letter-spacing: -.055em; font-weight: 850; }.hero h1 span { color: var(--green); }.hero__lead { max-width: 590px; margin: 0 0 23px; color: var(--muted); font-size: 16px; line-height: 1.55; }
.hero__benefits { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; max-width: 625px; margin-bottom: 23px; }.mini-benefit { display: grid; align-content: center; min-height: 102px; padding: 14px 15px; border: 1px solid #e7efea; border-radius: 13px; background: linear-gradient(145deg, #f1faf5, #fff); color: var(--green); }.mini-benefit strong { margin-top: 8px; color: var(--ink); font-size: 12px; line-height: 1.28; }.hero__actions { display: flex; gap: 12px; }.button { display: inline-flex; align-items: center; justify-content: center; gap: 10px; min-height: 50px; padding: 0 24px; border-radius: 10px; font-size: 14px; font-weight: 800; text-decoration: none; transition: transform .18s ease, box-shadow .18s ease; }.button:hover { transform: translateY(-2px); }.button--primary { background: var(--green); color: #fff; box-shadow: 0 12px 26px rgba(9, 148, 71, .22); }.button--secondary { border: 1px solid #cddbd3; color: var(--ink); background: #fff; }.button--secondary svg { color: var(--green); fill: #e2f6e9; }.hero__trust { display: flex; flex-wrap: wrap; gap: 15px; margin-top: 15px; color: #77827c; font-size: 10.5px; }.hero__trust span { display: inline-flex; align-items: center; gap: 4px; }.hero__trust svg { color: var(--green); }
.hero__media { position: relative; min-width: 0; overflow: hidden; }.hero__media::after { content: ''; position: absolute; inset: 0 auto 0 0; width: 21%; background: linear-gradient(90deg, #fff, transparent); pointer-events: none; }.hero__media > img { width: 100%; height: 100%; object-fit: cover; object-position: 56% center; display: block; }.hero__scribble { position: absolute; z-index: 2; top: 92px; left: 10%; color: #fff; font: 700 24px/1.18 "Segoe Print", cursive; transform: rotate(-5deg); text-shadow: 0 2px 10px rgba(0,0,0,.3); }.hero blockquote { position: absolute; z-index: 3; right: 6%; bottom: 45px; width: 230px; margin: 0; padding: 22px; border-radius: 12px; background: rgba(255,255,255,.96); box-shadow: 0 18px 48px rgba(14,40,24,.18); }.quote-mark { color: var(--green); font: 800 32px/1 Georgia, serif; }.hero blockquote p { margin: 2px 0 13px; font-size: 12px; line-height: 1.5; }.hero blockquote footer { color: #6d7872; font-size: 10px; font-weight: 700; }
.section { padding: 88px 0; }.section--white { background: #fff; }.section--mint { background: var(--mint); }.section-heading { max-width: 680px; margin: 0 auto 38px; text-align: center; }.eyebrow { margin: 0 0 10px; color: var(--green); font-size: 10px; font-weight: 850; letter-spacing: .18em; text-transform: uppercase; }.section-heading h2, .stories-heading h2 { margin: 0; font-size: clamp(30px, 3.2vw, 44px); line-height: 1.05; letter-spacing: -.04em; }.section-heading > p:last-child, .stories-heading > div > p:last-child { margin: 11px 0 0; color: var(--muted); font-size: 14px; }
.solution-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; }.solution-card { overflow: hidden; border: 1px solid var(--line); border-radius: 13px; background: #fff; box-shadow: 0 8px 28px rgba(24,60,39,.05); }.solution-card__media { position: relative; height: 230px; overflow: hidden; background: #e9f7ee; }.solution-card__media img { width: 100%; height: 100%; object-fit: cover; display: block; }.solution-card__media--rider img { filter: sepia(.35) hue-rotate(75deg) saturate(1.8); }.media-label { position: absolute; top: 16px; left: 16px; display: flex; align-items: center; gap: 7px; padding: 9px 12px; border-radius: 9px; background: rgba(255,255,255,.94); color: var(--green-dark); font-size: 11px; font-weight: 800; box-shadow: 0 7px 20px rgba(0,0,0,.1); }.solution-card__body { position: relative; padding: 24px 24px 25px; }.icon-box { position: absolute; top: -28px; left: 20px; display: grid; place-items: center; width: 50px; height: 50px; border-radius: 11px; background: #e7f8ee; color: var(--green); box-shadow: 0 5px 15px rgba(9,148,71,.12); }.solution-card h3 { margin: 8px 0; font-size: 18px; letter-spacing: -.02em; }.solution-card p { min-height: 63px; margin: 0 0 16px; color: var(--muted); font-size: 13px; line-height: 1.55; }.solution-card a, .stories-heading > a { display: inline-flex; align-items: center; gap: 6px; color: var(--green-dark); font-size: 12px; font-weight: 800; text-decoration: none; }
.steps { display: grid; grid-template-columns: repeat(4, 1fr); gap: 28px; margin: 0; padding: 0; list-style: none; }.step-card { position: relative; min-height: 195px; padding: 28px 22px 24px; border: 1px solid #e1ece5; border-radius: 12px; background: rgba(255,255,255,.92); }.step-card:not(:last-child)::after { content: '›'; position: absolute; top: 50%; right: -21px; color: #aebbb4; font-size: 30px; font-weight: 300; transform: translateY(-50%); }.step-card__number { position: absolute; top: 16px; left: 16px; display: grid; place-items: center; width: 27px; height: 27px; border-radius: 50%; background: #99dfb6; color: #fff; font-size: 12px; font-weight: 850; }.step-card__icon { display: grid; place-items: center; width: 64px; height: 64px; margin: 0 auto 18px; border-radius: 20px; background: #e6f8ed; color: var(--green); }.step-card h3 { margin: 0 0 7px; font-size: 15px; }.step-card p { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.5; }
.section--sync { background: linear-gradient(180deg, #fff 0%, #f3faf6 100%); padding-bottom: 94px; }.dashboard-wrap { position: relative; max-width: 890px; margin: 58px auto 0; }.dashboard { display: grid; grid-template-columns: 180px 1fr; min-height: 470px; overflow: hidden; border: 8px solid #27312d; border-bottom-width: 16px; border-radius: 16px; background: #f5f8f6; box-shadow: 0 24px 60px rgba(21,62,39,.2); transform: perspective(1200px) rotateX(.5deg); }.dashboard__side { padding: 22px 16px; background: #fff; border-right: 1px solid #e0e8e3; }.dashboard__side > strong { color: #126b3a; font-size: 18px; letter-spacing: -.04em; }.dashboard__side > strong span { color: #f2a11a; }.dashboard__side nav { display: grid; gap: 8px; margin-top: 32px; }.dashboard__side nav span,.dashboard__side nav b { padding: 10px 12px; border-radius: 7px; color: #6c7771; font-size: 11px; font-weight: 600; }.dashboard__side nav b { background: #e8f7ee; color: var(--green-dark); }.dashboard__main { padding: 30px; }.dashboard__top { display: flex; justify-content: space-between; align-items: flex-start; }.dashboard__top h3 { margin: 0; font-size: 18px; }.dashboard__top p { margin: 4px 0 0; color: #748079; font-size: 10px; }.dashboard__top > span { padding: 8px 12px; border: 1px solid #dfe7e2; border-radius: 7px; background: #fff; color: #68736d; font-size: 10px; }
.dashboard__stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 25px 0; }.dashboard__stats div { display: grid; gap: 6px; padding: 15px; border: 1px solid #e3ebe6; border-radius: 9px; background: #fff; }.dashboard__stats small { color: #78827d; font-size: 9px; }.dashboard__stats strong { font-size: 16px; }.dashboard__stats em { color: var(--green); font-size: 8px; font-style: normal; }.dashboard__orders { padding: 18px; border: 1px solid #e1e9e4; border-radius: 10px; background: #fff; }.dashboard__orders-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-size: 11px; }.dashboard__orders-head span { min-width: 140px; padding: 7px 10px; border: 1px solid #e2e9e4; border-radius: 6px; color: #99a19d; font-size: 8px; }.order-row { display: grid; grid-template-columns: .7fr 1.4fr 1fr .8fr 1fr; align-items: center; min-height: 38px; border-top: 1px solid #edf1ee; color: #4f5a54; font-size: 9px; }.order-row--head { min-height: 30px; border: 0; color: #8b948f; }.order-row i,.order-row b { display: inline-flex; width: fit-content; padding: 4px 7px; border-radius: 99px; background: #e4f7eb; color: var(--green-dark); font-size: 7px; font-style: normal; }.order-row .yellow { background: #fff0c9; color: #96700a; }.dashboard-note { position: absolute; color: var(--green); font: 700 20px/1.2 "Segoe Print", cursive; }.dashboard-note--left { left: -140px; top: 150px; transform: rotate(-8deg); }.dashboard-note--right { right: -145px; top: 80px; transform: rotate(5deg); }
.sync-features { position: relative; z-index: 2; display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-top: -14px; }.sync-features article { min-height: 150px; padding: 22px; border: 1px solid #e1ebe5; border-radius: 12px; background: #fff; box-shadow: 0 10px 25px rgba(23,71,43,.06); }.sync-features article > span { display: grid; place-items: center; width: 43px; height: 43px; border-radius: 10px; background: #e7f8ee; color: var(--green); }.sync-features h3 { margin: 15px 0 7px; font-size: 14px; }.sync-features p { margin: 0; color: var(--muted); font-size: 11px; line-height: 1.5; }
.section--stories { padding-top: 64px; }.stories-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 30px; margin-bottom: 28px; }.stories-heading h2 { font-size: 30px; }.stories-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; }.story-card { overflow: hidden; border: 1px solid var(--line); border-radius: 11px; background: #fff; color: inherit; text-decoration: none; transition: transform .2s ease, box-shadow .2s ease; }.story-card:hover { transform: translateY(-4px); box-shadow: 0 14px 34px rgba(19,62,38,.1); }.story-card img { width: 100%; height: 142px; object-fit: cover; display: block; }.story-card > div { display: grid; padding: 16px; }.story-card h3 { margin: 0 0 3px; font-size: 13px; }.story-card p { margin: 0 0 7px; color: var(--muted); font-size: 10px; }.story-card small { display: flex; align-items: center; gap: 4px; color: #7e8983; font-size: 9px; }.story-card > div > span { display: inline-flex; align-items: center; gap: 5px; margin-top: 14px; color: var(--green-dark); font-size: 10px; font-weight: 800; }
.signup-band { position: relative; padding: 78px 0; background: linear-gradient(135deg, #076c37 0%, #0b944b 56%, #057239 100%); }.signup-band::before { content: ''; position: absolute; inset: 0; opacity: .12; background: radial-gradient(circle at 20% 25%, transparent 0 30%, #74d89e 31% 32%, transparent 33%), linear-gradient(125deg, transparent 45%, #5cd08b 46% 47%, transparent 48%); background-size: 360px 300px, 520px 520px; }.signup-band__inner { position: relative; display: grid; grid-template-columns: minmax(0, .95fr) minmax(490px, 1.05fr); align-items: start; gap: 75px; width: min(1180px, calc(100% - 48px)); margin: 0 auto; }.signup-pitch { padding-top: 18px; color: #fff; }.eyebrow--light { color: #a5edc1; }.signup-pitch h2 { margin: 10px 0 18px; font-size: clamp(38px, 4.3vw, 58px); line-height: .98; letter-spacing: -.05em; }.signup-pitch > p:not(.eyebrow) { max-width: 520px; margin: 0 0 24px; color: rgba(255,255,255,.78); font-size: 14px; line-height: 1.65; }.signup-pitch ul { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; font-size: 13px; }.signup-pitch li { display: flex; align-items: center; gap: 9px; }.signup-pitch li svg { padding: 2px; border-radius: 50%; background: #fff; color: var(--green-dark); }.signup-pitch__types { margin-top: 30px; }.signup-pitch__types small { display: block; margin-bottom: 10px; color: #a8e6bf; font-size: 9px; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; }.signup-pitch__types > div { display: flex; flex-wrap: wrap; gap: 8px; }.signup-pitch__types span { padding: 8px 14px; border: 1px solid rgba(255,255,255,.25); border-radius: 99px; background: rgba(255,255,255,.08); font-size: 10px; }.signup-slot { min-width: 0; }
@media (max-width: 1180px) { .dashboard-note { display: none; } }
@media (max-width: 900px) { .hero__inner { grid-template-columns: 1fr; }.hero__copy { padding: 64px 28px; }.hero__media { min-height: 540px; }.hero__media::after { inset: 0 0 auto; width: auto; height: 15%; background: linear-gradient(180deg,#fff,transparent); }.solution-grid { grid-template-columns: 1fr; max-width: 620px; margin: 0 auto; }.solution-card__media { height: 300px; }.solution-card p { min-height: 0; }.steps { grid-template-columns: repeat(2,1fr); }.step-card:nth-child(2)::after { display: none; }.dashboard { grid-template-columns: 140px 1fr; }.dashboard__main { padding: 20px; }.dashboard__stats { grid-template-columns: repeat(2,1fr); }.stories-grid { grid-template-columns: repeat(2,1fr); }.signup-band__inner { grid-template-columns: 1fr; gap: 42px; max-width: 680px; }.signup-pitch { text-align: center; }.signup-pitch > p:not(.eyebrow) { margin-left: auto; margin-right: auto; }.signup-pitch ul { width: fit-content; margin: 0 auto; text-align: left; }.signup-pitch__types > div { justify-content: center; } }
@media (max-width: 640px) { .container,.signup-band__inner { width: min(100% - 32px, 1180px); }.section { padding: 68px 0; }.hero__copy { padding: 48px 20px; }.hero h1 { font-size: 45px; }.hero__badge small { display: none; }.hero__benefits { grid-template-columns: repeat(2,1fr); }.hero__actions { flex-direction: column; }.hero__trust { gap: 9px 13px; }.hero__media { min-height: 470px; }.hero blockquote { right: 16px; bottom: 18px; width: 210px; }.hero__scribble { top: 40px; left: 24px; font-size: 20px; }.solution-card__media { height: 220px; }.steps { grid-template-columns: 1fr; gap: 14px; }.step-card::after { display: none !important; }.step-card { min-height: 0; }.dashboard-wrap { margin-top: 34px; overflow: hidden; border-radius: 13px; height: 282px; }.dashboard { width: 760px; min-height: 420px; transform: scale(.67); transform-origin: top left; }.sync-features { grid-template-columns: 1fr 1fr; margin-top: 18px; }.sync-features article { min-height: 160px; padding: 18px; }.stories-heading { align-items: flex-start; flex-direction: column; }.stories-grid { grid-template-columns: 1fr; }.story-card { display: grid; grid-template-columns: 42% 58%; }.story-card img { height: 100%; min-height: 165px; }.signup-band { padding: 64px 0; }.signup-band__inner { min-width: 0; }.signup-pitch h2 { font-size: 42px; } }
</style>
