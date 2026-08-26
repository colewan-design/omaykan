<script setup lang="ts">
import { computed } from 'vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'
import { useStockedCategories } from '@pos/web/commerce/catalog'

// Shared by the landing page and the about page. `shopHref` follows the same
// rule as FdHeader's: an in-page anchor on the landing page, a real navigation
// home from anywhere else.
const props = withDefaults(defineProps<{ shopHref?: string }>(), { shopHref: '#shop' })

// The column used to name a fixed taxonomy (Food, Pharmacy, Errands…) that no
// store stocks, every entry pointing at the same anchor. It now names real
// aisles, like the header nav. Always a full navigation with ?category=, even
// on the landing page: a footer link is far enough from the shelves that
// reloading into the aisle is clearer than silently swapping the page above.
const stockedCategories = useStockedCategories()
const footerCategories = computed(() => stockedCategories.value.slice(0, 5))

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
        <!-- The rider portal, not /signup: that form asks for a business name
             and a business mode, which is a dead end for someone with a bike. -->
        <a href="/rider">Become a rider</a>
        <!-- Two different sign-ins live behind these: the shopper's portal, and
             the register's login. They used to share one "Sign in" link, which
             sent customers to the merchant app. -->
        <a href="/account">Your account</a>
        <a href="/app/auth">Merchant sign in</a>
      </div>
      <div>
        <p class="fd-footer__title">Help</p>
        <a :href="props.shopHref">Delivery information</a>
        <a :href="props.shopHref">Track an order</a>
        <!-- "Contact us" used to point at the signup form, which is not a way
             to contact anyone. The address is spelled out under it because a
             footer is where people look for one, and because a mailto: is no
             use to someone reading on a device with no mail client set up. -->
        <a :href="supportMailto('Omaykan help')">Contact us</a>
        <p class="fd-footer__note fd-footer__email">{{ SUPPORT_EMAIL }}</p>
      </div>
      <div>
        <p class="fd-footer__title">Categories</p>
        <a v-for="cat in footerCategories" :key="cat.id" :href="`/?category=${cat.id}`">
          {{ cat.name }}
        </a>
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

/* Auto width, centred — stretched edge to edge this was a 1384px pill holding
   two words, which read as a broken container rather than a button. */
.fd-totop {
  display: block;
  margin: 0 auto 40px;
  padding: 14px 30px;
  border: 1px solid #dfe3e0;
  border-radius: 999px;
  background: #fff;
  color: #1a1a1a;
  /* Longhands, not `font: 600 14.5px/1 inherit`: a CSS-wide keyword like
     `inherit` is only legal as a shorthand's *entire* value, so that
     declaration was dropped whole and the button rendered at the inherited
     17px/400 instead of the 14.5px/600 it asks for. */
  font-family: inherit;
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: border-color 150ms, color 150ms;
}
.fd-totop:hover { border-color: #1a6b3c; color: #1a6b3c; }
.fd-totop:focus-visible { outline: 2px solid #1a6b3c; outline-offset: 2px; }

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

/* Sits directly under its "Contact us" link rather than a gap away, so the two
   read as one item in the column. */
.fd-footer__email { margin-top: -6px; word-break: break-word; }

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
