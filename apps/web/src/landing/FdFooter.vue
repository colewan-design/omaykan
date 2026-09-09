<script setup lang="ts">
import { computed } from 'vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'
import {
  DELIVERY_BASE_FEE_CENTS,
  DELIVERY_BASE_KM,
  DELIVERY_PER_KM_CENTS,
} from '@pos/web/commerce/delivery'
import { useStockedCategories } from '@pos/web/commerce/catalog'

// Shared by the landing page and the about page. `shopHref` follows the same
// rule as FdHeader's: an in-page anchor on the landing page, a real navigation
// home from anywhere else.
const props = withDefaults(defineProps<{ shopHref?: string }>(), { shopHref: '#shop' })

// Was typed out as "₱49" and "₱15/km". Quoted from the constants the checkout
// charges from instead, so the footer cannot promise one rate while the cart
// bills another — the same rule AboutPage already follows.
const peso = (cents: number) => `₱${cents / 100}`
const baseFee = peso(DELIVERY_BASE_FEE_CENTS)
const perKm = peso(DELIVERY_PER_KM_CENTS)

// The column used to name a fixed taxonomy (Food, Pharmacy, Errands…) that no
// store stocks, every entry pointing at the same anchor. It now names real
// aisles, like the header nav. Always a full navigation with ?category=, even
// on the landing page: a footer link is far enough from the shelves that
// reloading into the aisle is clearer than silently swapping the page above.
const stockedCategories = useStockedCategories()
const footerCategories = computed(() => stockedCategories.value.slice(0, 3))

/**
 * The in-page anchors only exist on the landing page. From anywhere else they
 * have to be a real navigation home first, or the link does nothing at all.
 */
function anchor(id: string): string {
  return props.shopHref.startsWith('#') ? `#${id}` : `/#${id}`
}

function backToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <footer class="fd-footer">
    <button type="button" class="fd-totop" @click="backToTop">Back to Top</button>

    <div class="fd-footer__cols">
      <div class="fd-footer__brand">
        <BrandLogo variant="light" :size="17" class="fd-footer__logo" />
        <p class="fd-footer__tag">Your neighbourhood market, online.</p>
      </div>

      <div>
        <p class="fd-footer__title">Shop</p>
        <!-- Real aisles off the live catalog, not a fixed taxonomy: a link to
             an empty category is a dead click. -->
        <a v-for="cat in footerCategories" :key="cat.id" :href="`/?category=${cat.id}`">
          {{ cat.name }}
        </a>
        <a :href="anchor('shops')">Nearby shops</a>
        <a :href="anchor('deals')">Deals</a>
      </div>

      <div>
        <p class="fd-footer__title">Sell</p>
        <a href="/signup">Become a merchant</a>
        <!-- The rider portal, not /signup: that form asks for a business name
             and a business mode, which is a dead end for someone with a bike. -->
        <a href="/rider">Become a rider</a>
        <a href="/about#for-shops">Seller pricing</a>
        <!-- Two different sign-ins live behind these: the shopper's portal, and
             the register's login. They used to share one "Sign in" link, which
             sent customers to the merchant app. -->
        <a href="/app/auth">Merchant sign in</a>
      </div>

      <div>
        <p class="fd-footer__title">Help</p>
        <a href="/about#delivery">How delivery works</a>
        <a href="/account">Track an order</a>
        <a href="/account">Your account</a>
      </div>

      <div>
        <p class="fd-footer__title">Company</p>
        <a href="/about">About Omaykan</a>
        <!-- "Contact us" used to point at the signup form, which is not a way
             to contact anyone. The address is spelled out under it because a
             footer is where people look for one, and because a mailto: is no
             use to someone reading on a device with no mail client set up. -->
        <a :href="supportMailto('Omaykan help')">Contact us</a>
        <p class="fd-footer__note fd-footer__email">{{ SUPPORT_EMAIL }}</p>
      </div>

      <div>
        <p class="fd-footer__title">How you pay</p>
        <p class="fd-footer__note">Cash or GCash, handed to the rider on arrival.</p>
        <p class="fd-footer__note">Nothing is charged online.</p>
        <p class="fd-footer__note">
          Flat {{ baseFee }} delivery for the first {{ DELIVERY_BASE_KM }}&nbsp;km, then {{ perKm }}/km.
        </p>
      </div>
    </div>

    <div class="fd-footer__bottom">
      <span>© 2026 Omaykan. Built for local commerce.</span>
    </div>
  </footer>
</template>

<style scoped>
/* Top padding rather than a margin on .fd-totop: the button is the footer's
   first child, so its top margin would collapse out through the parent and
   push the grey block down instead of seating the button inside it. */
.fd-footer { background: #f7f8f7; padding: 48px var(--fd-gutter) 40px; }

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
  /* Brand column is wider than the link columns it sits beside. */
  grid-template-columns: 1.3fr repeat(5, 1fr);
  gap: 32px;
}

/* The brand block is a heading, not a list, so it does not want the column
   gap the link stacks use. */
.fd-footer__brand { gap: 0 !important; }

.fd-footer__cols > div { display: flex; flex-direction: column; gap: 11px; }
/* The footer sits on a light ground — BrandLogo's "light" variant is the one
   drawn *for* a light background. The tagline was set in white against it and
   was effectively invisible. */
.fd-footer__tag {
  margin: 8px 0 0;
  max-width: 22ch;
  font-size: 13.5px;
  line-height: 1.45;
  color: #6b7280;
}

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

/* Six columns now, so this steps 6 -> 3 -> 2 -> 1. The brand block spans the
   row once it is no longer first in a line of six, otherwise it takes a whole
   column to hold two short lines. */
@media (max-width: 1080px) {
  .fd-footer__cols { grid-template-columns: repeat(3, 1fr); gap: 30px 26px; }
  .fd-footer__brand { grid-column: 1 / -1; }
}
@media (max-width: 760px) {
  .fd-footer { padding: 36px var(--fd-gutter) 32px; }
}
@media (max-width: 620px) {
  .fd-footer__cols { grid-template-columns: repeat(2, 1fr); gap: 26px 20px; }
}
@media (max-width: 460px) {
  .fd-footer__cols { grid-template-columns: 1fr; }
}
</style>
