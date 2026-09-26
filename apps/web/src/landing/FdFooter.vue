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
import MountainMark from './MountainMark.vue'
import { DISCOVERY_TOWNS } from './towns'

// Shared by the landing, about and account pages: the redesign's dark forest
// footer. `shopHref` follows the same rule as FdHeader's: an in-page anchor on
// the landing page, a real navigation home from anywhere else.
//
// The redesign also draws a newsletter box and social icons. Neither is here:
// there is no mailing list to subscribe anyone to and no social accounts to
// link, and a Subscribe button that goes nowhere is worse than no button.
const props = withDefaults(
  defineProps<{
    shopHref?: string
    showBackToTop?: boolean
    fallbackCategories?: Array<{ id: string; name: string }>
  }>(),
  { shopHref: '#shop', showBackToTop: true, fallbackCategories: () => [] },
)

// Quoted from the constants the checkout charges from, so the footer cannot
// promise one rate while the cart bills another.
const peso = (cents: number) => `₱${cents / 100}`
const baseFee = peso(DELIVERY_BASE_FEE_CENTS)
const perKm = peso(DELIVERY_PER_KM_CENTS)

// Real aisles off the live catalog, like the header menu. Always a full
// navigation with ?category=, even on the landing page: a footer link is far
// enough from the shelves that reloading into the aisle is clearer than
// silently swapping the page above.
const stockedCategories = useStockedCategories()
const footerCategories = computed(() => {
  const categories = stockedCategories.value.length > 0
    ? stockedCategories.value
    : props.fallbackCategories
  return categories.slice(0, 5)
})

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
    <button v-if="props.showBackToTop" type="button" class="fd-totop" @click="backToTop">
      Back to top
    </button>

    <div class="fd-footer__cols">
      <div class="fd-footer__brand">
        <BrandLogo variant="dark" :size="19" />
        <p class="fd-footer__tag">Your neighbourhood market, online.</p>
      </div>

      <div>
        <p class="fd-footer__title">Shop</p>
        <a v-for="cat in footerCategories" :key="cat.id" :href="`/?category=${cat.id}`">
          {{ cat.name }}
        </a>
        <a :href="anchor('shops')">Nearby shops</a>
        <a :href="anchor('deals')">Deals</a>
        <!-- Each town's own landing page (/baguio), which Laravel renders; see
             towns.ts. -->
        <a v-for="town in DISCOVERY_TOWNS" :key="town.slug" :href="`/${town.slug}`">
          Shops in {{ town.name }}
        </a>
      </div>

      <div>
        <p class="fd-footer__title">Sell</p>
        <a href="/seller/signup">Become a merchant</a>
        <!-- The rider portal, not /seller/signup: that form asks for a business name
             and a business mode, which is a dead end for someone with a bike. -->
        <a href="/rider">Become a rider</a>
        <a href="/about#for-shops">Seller pricing</a>
        <!-- Two different sign-ins live behind these: the shopper's portal, and
             the register's login. -->
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
        <!-- The address is spelled out under the link because a footer is where
             people look for one, and a mailto: is no use on a device with no
             mail client set up. -->
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

      <div class="fd-footer__place" aria-hidden="true">
        <MountainMark :size="72" />
        <p>Baguio · La Trinidad</p>
      </div>
    </div>

    <div class="fd-footer__bottom">
      <span>© 2026 Omaykan. Built for local commerce.</span>
      <span class="fd-footer__motto">Local shops. Local hands. A brighter tomorrow.</span>
    </div>
  </footer>
</template>

<style scoped>
.fd-footer {
  padding: 48px var(--fd-inset) 30px;
  background: var(--sf-forest);
  color: var(--sf-cream);
}

/* Auto width, centred — stretched edge to edge it was a 1384px pill holding
   three words, which read as a broken container rather than a button. */
.fd-totop {
  display: block;
  margin: 0 auto 40px;
  padding: 12px 28px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 999px;
  background: transparent;
  color: var(--sf-cream);
  /* Longhands, not `font: 600 14.5px/1 inherit`: a CSS-wide keyword like
     `inherit` is only legal as a shorthand's *entire* value, so that
     declaration was dropped whole and the button rendered at the inherited
     17px/400 instead. */
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: border-color 150ms, color 150ms;
}
.fd-totop:hover { border-color: var(--sf-gold); color: var(--sf-gold); }
.fd-totop:focus-visible { outline: 2px solid var(--sf-gold); outline-offset: 2px; }

.fd-footer__cols {
  display: grid;
  grid-template-columns: 1.4fr repeat(5, 1fr) auto;
  gap: 32px;
}

.fd-footer__cols > div { display: flex; flex-direction: column; gap: 10px; }

.fd-footer__brand { gap: 0 !important; }

.fd-footer__tag {
  margin: 10px 0 0;
  max-width: 22ch;
  font-size: 13.5px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.7);
}

.fd-footer__title {
  margin: 0 0 4px;
  font-family: var(--sf-serif);
  font-size: 15px;
  font-weight: 700;
  color: var(--sf-paper);
}

.fd-footer__cols a { font-size: 13.5px; color: rgba(255, 255, 255, 0.75); }
.fd-footer__cols a:hover { color: #fff; text-decoration: underline; text-underline-offset: 3px; }

.fd-footer__note { margin: 0; font-size: 13px; line-height: 1.55; color: rgba(255, 255, 255, 0.66); }

/* Sits directly under its "Contact us" link rather than a gap away, so the two
   read as one item in the column. */
.fd-footer__email { margin-top: -5px; word-break: break-word; }

.fd-footer__place {
  align-items: center;
  justify-content: flex-start;
  color: var(--sf-cream);
}
.fd-footer__place p {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: 14px;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.fd-footer__bottom {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px 24px;
  margin: 36px 0 0;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.14);
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.55);
}

.fd-footer__motto { font-family: var(--sf-serif); font-style: italic; }

/* Seven columns, stepping down to 3 -> 2 -> 1. The brand block spans the row
   once it is no longer first in a line of seven, and the mountain mark is
   decoration that stops earning its column. */
@media (max-width: 1180px) {
  .fd-footer__place { display: none !important; }
  .fd-footer__cols { grid-template-columns: 1.4fr repeat(5, 1fr); }
}
@media (max-width: 1080px) {
  .fd-footer__cols { grid-template-columns: repeat(3, 1fr); gap: 30px 26px; }
  .fd-footer__brand { grid-column: 1 / -1; }
}
@media (max-width: 760px) {
  .fd-footer { padding: 36px var(--fd-gutter) 28px; }
}
@media (max-width: 620px) {
  .fd-footer__cols { grid-template-columns: repeat(2, 1fr); gap: 26px 20px; }
}
@media (max-width: 460px) {
  .fd-footer__cols { grid-template-columns: 1fr; }
}
</style>
