<script setup lang="ts">
import BrandLogo from '@pos/core/components/BrandLogo.vue'

// Shared by the landing page and the about page. `shopHref` follows the same
// rule as FdHeader's: an in-page anchor on the landing page, a real navigation
// home from anywhere else.
const props = withDefaults(defineProps<{ shopHref?: string }>(), { shopHref: '#shop' })

function backToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <footer class="fd-footer">
    <button type="button" class="fd-totop" @click="backToTop">Back to Top</button>

    <div class="fd-footer__cols">
      <div>
        <BrandLogo variant="light" :size="17" class="fd-footer__logo" />
        <a :href="props.shopHref">Shop</a>
        <a href="/about">About us</a>
        <a href="/signup">Become a vendor</a>
        <a href="/signup">Become a rider</a>
        <a href="/app/auth">Sign in</a>
      </div>
      <div>
        <p class="fd-footer__title">Help</p>
        <a :href="props.shopHref">Delivery information</a>
        <a :href="props.shopHref">Track an order</a>
        <a href="/signup">Contact us</a>
      </div>
      <div>
        <p class="fd-footer__title">Categories</p>
        <a :href="props.shopHref">Food</a>
        <a :href="props.shopHref">Groceries</a>
        <a :href="props.shopHref">Pharmacy</a>
        <a :href="props.shopHref">Errands</a>
      </div>
      <div>
        <p class="fd-footer__title">How you pay</p>
        <p class="fd-footer__note">Cash or GCash on arrival. Nothing is charged online.</p>
        <p class="fd-footer__note">Flat ₱49 delivery for the first 2&nbsp;km, then ₱15/km.</p>
      </div>
    </div>

    <div class="fd-footer__bottom">
      <span>© 2026 Omaykan. Local vendors, local riders, no commissions.</span>
    </div>
  </footer>
</template>

<style scoped>
.fd-footer { background: #f7f8f7; padding: 0 var(--fd-gutter) 40px; }

.fd-totop {
  display: block;
  width: 100%;
  margin: 0 0 40px;
  padding: 15px;
  border: 1px solid #dfe3e0;
  border-radius: 999px;
  background: #fff;
  color: #1a1a1a;
  font: 600 14.5px/1 inherit;
  cursor: pointer;
}
.fd-totop:hover { border-color: #1a6b3c; color: #1a6b3c; }

.fd-footer__logo { margin-bottom: 6px; }

.fd-footer__cols {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
}
.fd-footer__cols > div { display: flex; flex-direction: column; gap: 11px; }
.fd-footer__title { margin: 0 0 3px; font-size: 14.5px; font-weight: 800; color: #1a1a1a; }
.fd-footer__cols a { font-size: 13.5px; color: #4b5563; }
.fd-footer__cols a:hover { color: #1a6b3c; text-decoration: underline; }
.fd-footer__note { margin: 0; font-size: 13px; line-height: 1.55; color: #6b7280; }

.fd-footer__bottom {
  margin: 34px 0 0;
  padding-top: 22px;
  border-top: 1px solid #e5e7eb;
  font-size: 13px;
  color: #9ca3af;
}

@media (max-width: 1080px) {
  .fd-footer__cols { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 760px) {
  .fd-footer { padding: 0 var(--fd-gutter) 32px; }
}
@media (max-width: 460px) {
  .fd-footer__cols { grid-template-columns: 1fr; }
}
</style>
