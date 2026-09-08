<script setup lang="ts">
import { ShoppingBasket } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useStockedCategories } from '@pos/web/commerce/catalog'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { useDeliveryLocation } from '@pos/web/commerce/deliveryLocation'
import { categoryIcon } from '@pos/core/utils/categoryIcons'
import CartDrawer from './CartDrawer.vue'
import AddressDialog from './AddressDialog.vue'
import SignupBanner from './SignupBanner.vue'

// The site chrome — dark green utility bar with the search dominant, then the
// category nav. Shared by the landing page and the about page so the two can't
// drift apart; each page decides what a search submit means via @search.
//
// The nav used to list a fixed marketplace taxonomy (Food, Errands, Laundry…)
// where every item pointed at the same anchor and filtered nothing. It now
// lists the catalog's own stocked categories, because selecting one has to
// produce that category's products — a nav item naming an aisle the store
// doesn't stock would have nothing to show.
//
// `shopHref` is where the category items point: the landing page handles a
// category in place (@category, no navigation), every other page sends the
// browser home with ?category= for the landing page to pick up. It is also
// where an empty cart sends you, since there is nothing else to offer there.
//
// The cart itself used to point at it too — the icon was an anchor to #shop,
// which scrolled you to the shelves and never showed you the cart it was
// counting. It opens CartDrawer now, so the badge leads somewhere, and it does
// so from the header rather than the landing page so that the about and
// account pages get the same working cart.
//
// `accountBanner` is on everywhere the chrome appears except the account
// portal itself, where inviting someone to create an account directly above
// the form that creates one is noise.
const props = withDefaults(
  defineProps<{ shopHref?: string; activeCategory?: string; accountBanner?: boolean }>(),
  { shopHref: '#shop', activeCategory: '', accountBanner: true },
)

const emit = defineEmits<{ search: [term: string]; category: [categoryId: string] }>()

// "Enter your address" was an anchor to shopHref — it scrolled to the shelves
// and collected nothing. It opens the address panel now, and lives in the
// header rather than the landing page so the about and account pages get the
// same working control, the way the cart already does.
const delivery = useDeliveryLocation()
const addressOpen = ref(false)

const categories = useStockedCategories()

/** True on the landing page, where the shelves are on this same document. */
const handlesCategoryInPage = computed(() => props.shopHref.startsWith('#'))

function categoryHref(categoryId: string): string {
  if (handlesCategoryInPage.value) return props.shopHref
  return `${props.shopHref}?category=${encodeURIComponent(categoryId)}`
}

// A category the catalog invented rather than one we seeded still gets a nav
// item — it just wears the generic basket. An unknown aisle costs an icon,
// never a place in the nav.
function navIcon(categoryName: string) {
  return categoryIcon(categoryName) ?? ShoppingBasket
}

function selectCategory(categoryId: string, event: MouseEvent) {
  // Off the landing page the anchor is a real navigation home; leave it alone.
  if (!handlesCategoryInPage.value) return
  event.preventDefault()
  emit('category', categoryId)
}

const cart = useStorefrontCart()
const cartOpen = ref(false)

// The Account slot used to point at /app/auth — the merchant register's login,
// which is not a place a shopper has any business being. It goes to the
// customer portal instead, and greets whoever is already signed in there so
// the storefront and the portal agree about who is looking at them.
const account = useCustomerAccount()
const accountFirstName = computed(() => account.account.value?.name.trim().split(/\s+/)[0] ?? '')

const searchTerm = ref('')

function submitSearch() {
  emit('search', searchTerm.value.trim())
}

// ── The category panel folds away while you read ────────────────────────
// Both header rows stick, which costs ~160px of a phone screen for a rail you
// only need when you're changing aisle. Scrolling down folds the categories
// away and leaves the search bar; scrolling back up brings them out again,
// so the rail is there the moment you go looking for it.
const navHidden = ref(false)

/** Above this the rail is always out — a nudge off the top shouldn't fold it. */
const REVEAL_ABOVE = 96
/** Ignore the sub-pixel jitter of a trackpad settling or a rubber-band. */
const DIRECTION_DELTA = 6
/** How long a programmatic scroll (see revealNav) holds the rail open. */
const PIN_MS = 900
/** …and how long after its last scroll event that hold lets go. */
const PIN_SETTLE_MS = 200
/**
 * How long to disbelieve the scroll position after the rail moves. Folding it
 * away takes ~160px off the document, and the browser's scroll anchoring
 * scrolls the page back up by as much to hold the content still — a scroll
 * event, upward, that reads exactly like the shopper reaching for the rail.
 * Left alone the two chase each other and the rail flickers half open the
 * whole way down the page. Comfortably past the fold's own 240ms.
 */
const FOLD_SETTLE_MS = 340

let lastY = 0
let pinnedUntil = 0
let settledAt = 0

function setNavHidden(hidden: boolean) {
  if (navHidden.value === hidden) return
  navHidden.value = hidden
  settledAt = Date.now() + FOLD_SETTLE_MS
}

function onScroll() {
  // Clamped: iOS rubber-banding reports negative offsets at the top, which
  // would otherwise read as a scroll up and then a scroll down.
  const y = Math.max(window.scrollY, 0)
  const now = Date.now()

  if (now < pinnedUntil) {
    setNavHidden(false)
    lastY = y
    pinnedUntil = now + PIN_SETTLE_MS
    return
  }

  // Checked before the settle window, not after: a flick to the top has to
  // land with the rail out even when it arrives mid-fold.
  if (y <= REVEAL_ABOVE) {
    setNavHidden(false)
    lastY = y
    return
  }

  // Follow the page while it settles, but don't read anything into it.
  if (now < settledAt) {
    lastY = y
    return
  }

  const delta = y - lastY
  if (Math.abs(delta) < DIRECTION_DELTA) return
  setNavHidden(delta > 0)
  lastY = y
}

onMounted(() => {
  lastY = Math.max(window.scrollY, 0)
  window.addEventListener('scroll', onScroll, { passive: true })
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

/**
 * Holds the rail open through a scroll the page made itself. Picking a
 * category scrolls you down to the shelves, and folding the rail away on that
 * travel would take the lit-up aisle off screen at the exact moment it
 * started meaning something.
 */
function revealNav() {
  navHidden.value = false
  lastY = Math.max(window.scrollY, 0)
  pinnedUntil = Date.now() + PIN_MS
}

defineExpose({ clear: () => (searchTerm.value = ''), revealNav })
</script>

<template>
  <!-- Two roots: the banner scrolls away with the page while `.fd-head` stays
       sticky on its own. Nesting it inside would pin the strip to the top for
       the whole scroll of a long shelf. -->
  <SignupBanner v-if="props.accountBanner" />

  <div class="fd-head">
    <header class="fd-bar">
      <a href="/" class="fd-brand" aria-label="Omaykan — home">
        <BrandLogo variant="dark" :size="20" />
      </a>

      <div class="fd-bar__delivery">
        <span>Delivery</span>
        <button type="button" class="fd-bar__addr" @click="addressOpen = true">
          {{ delivery.isSet.value ? delivery.summary.value : 'Enter your address' }}
        </button>
      </div>

      <form class="fd-search" @submit.prevent="submitSearch">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        <input
          v-model="searchTerm"
          type="search"
          placeholder="What's on your shopping list?"
          aria-label="Search products"
        />
        <button type="submit" class="fd-search__go">Search</button>
      </form>

      <div class="fd-bar__account">
        <span class="fd-bar__label">About</span>
        <a href="/about">Our story</a>
      </div>

      <div class="fd-bar__account">
        <span class="fd-bar__label">Merchants</span>
        <a href="/signup">Sell with us</a>
      </div>

      <div class="fd-bar__account">
        <span class="fd-bar__label">{{ account.signedIn.value ? `Hi, ${accountFirstName}` : 'Account' }}</span>
        <a href="/account">{{ account.signedIn.value ? 'Your account' : 'Sign in' }}</a>
      </div>

      <!-- The three text slots above are hidden on a phone to keep the bar one
           row tall, which would leave the portal reachable only from the
           footer. This icon takes their place there. -->
      <a
        href="/account"
        class="fd-account-icon"
        :aria-label="account.signedIn.value ? 'Your account' : 'Sign in'"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
      </a>

      <button
        type="button"
        class="fd-cart"
        :aria-label="cart.itemCount.value > 0 ? `Cart, ${cart.itemCount.value} items` : 'Cart'"
        :aria-expanded="cartOpen"
        @click="cartOpen = true"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
        <span v-if="cart.itemCount.value > 0" class="fd-cart__count">{{ cart.itemCount.value }}</span>
      </button>
    </header>

    <!-- inert while folded away: a rail you can't see shouldn't be a stop on
         the way to the shelves for anyone tabbing through. -->
    <nav
      class="fd-nav"
      :class="{ 'fd-nav--away': navHidden }"
      :inert="navHidden"
      aria-label="Shop by category"
    >
      <div class="fd-nav__clip">
        <div class="fd-nav__row">
          <a
            v-for="cat in categories"
            :key="cat.id"
            :href="categoryHref(cat.id)"
            class="fd-navcat"
            :class="{ 'fd-navcat--on': cat.id === props.activeCategory }"
            :aria-current="cat.id === props.activeCategory ? 'true' : undefined"
            @click="selectCategory(cat.id, $event)"
          >
            <component :is="navIcon(cat.name)" :size="28" :stroke-width="1.5" />
            <span>{{ cat.name }}</span>
          </a>
        </div>
      </div>
    </nav>

    <CartDrawer :open="cartOpen" :shop-href="props.shopHref" @close="cartOpen = false" />
    <AddressDialog :open="addressOpen" @close="addressOpen = false" />
  </div>
</template>

<style scoped>
/* Both header rows stick as one block, so the category nav stays reachable
   the whole way down the page — folded away while you read down it, back out
   the moment you scroll up. */
.fd-head {
  position: sticky;
  top: 0;
  z-index: 100;
}

.fd-bar {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 0 var(--fd-gutter);
  min-height: 76px;
  background: #1a6b3c;
  color: #fff;
}

/* Folded away by collapsing its own row rather than by sliding: the page below
   has to close the gap, or the bar would leave a green void behind it. 0fr
   measures the row out of existence while `.fd-nav__clip` crops what no longer
   fits, which animates without anyone having to know the rail's height —
   it changes with the breakpoint and with how the category labels wrap. */
.fd-nav {
  display: grid;
  grid-template-rows: 1fr;
  background: #1a6b3c;
  transition: grid-template-rows 240ms ease;
}

.fd-nav--away {
  grid-template-rows: 0fr;
}

.fd-nav__clip {
  min-height: 0;
  overflow: hidden;
  transition: opacity 160ms ease;
}

.fd-nav--away .fd-nav__clip {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .fd-nav,
  .fd-nav__clip {
    transition: none;
  }
}

/* Even slots so the categories span the bar; min-width tips the row into
   sideways scrolling on narrow screens rather than wrapping the header.
   min-height holds the bar open while the catalog is still loading, so the
   page doesn't jolt down when the categories arrive. The rule off the bar
   rides on the row rather than on .fd-nav, so that it folds away with the
   rail instead of leaving a hairline under a collapsed header. */
.fd-nav__row {
  display: flex;
  align-items: stretch;
  min-height: 66px;
  padding: 0 var(--fd-gutter);
  border-top: 1px solid rgba(255, 255, 255, 0.14);
  overflow-x: auto;
  scrollbar-width: none;
}

.fd-nav__row::-webkit-scrollbar {
  display: none;
}

.fd-navcat {
  display: flex;
  flex: 1 1 0;
  min-width: 104px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 13px 10px 11px;
  border-bottom: 3px solid transparent;
  color: rgba(255, 255, 255, 0.92);
  font-size: 14.5px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.fd-navcat:hover {
  color: #fff;
  border-bottom-color: #f5a623;
}

/* The selected aisle stays lit while its listing is on screen — the nav is the
   only thing on the page that says which aisle you are standing in. */
.fd-navcat--on {
  background: rgba(255, 255, 255, 0.13);
  border-bottom-color: #bbf451;
  color: #fff;
}

.fd-brand { display: flex; align-items: center; flex-shrink: 0; }

.fd-bar__delivery {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  padding-left: 22px;
  border-left: 1px solid rgba(255,255,255,0.22);
  white-space: nowrap;
}
.fd-bar__delivery span { font-size: 13.5px; font-weight: 700; }
.fd-bar__delivery a,
.fd-bar__addr {
  font-size: 13px;
  color: rgba(255,255,255,0.85);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.fd-bar__delivery a:hover,
.fd-bar__addr:hover { color: #fff; }

/* Reset only what the button adds over the anchor it replaced, and cap the
   width so a long saved address cannot push the search bar off the row. */
.fd-bar__addr {
  padding: 0;
  border: 0;
  background: none;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fd-search {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 6px 0 16px;
  border-radius: 999px;
  background: #fff;
  color: #6b7280;
}
.fd-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font: 500 15px/1 inherit;
  color: #1a1a1a;
}
.fd-search input::placeholder { color: #9ca3af; }
.fd-search__go {
  flex-shrink: 0;
  height: 34px;
  padding: 0 18px;
  border: none;
  border-radius: 999px;
  background: #22c55e;
  color: #06240f;
  font: 700 13.5px/1 inherit;
  cursor: pointer;
}
.fd-search__go:hover { background: #16a34a; color: #fff; }

.fd-bar__account {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  white-space: nowrap;
}
.fd-bar__label { font-size: 13.5px; font-weight: 700; }
.fd-bar__account a {
  font-size: 13px;
  color: rgba(255,255,255,0.85);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.fd-bar__account a:hover { color: #fff; }

.fd-account-icon { display: none; }

/* A button since it opens the drawer, so it carries the reset an <a> didn't need. */
.fd-cart {
  position: relative;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  padding: 0;
  border: none;
  background: none;
  color: #fff;
  cursor: pointer;
}
.fd-cart__count {
  position: absolute;
  top: -6px;
  right: -8px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: #bbf451;
  color: #06240f;
  font-size: 11px;
  font-weight: 800;
}

@media (max-width: 1080px) {
  .fd-bar__delivery { display: none; }
}

@media (max-width: 760px) {
  .fd-bar {
    flex-wrap: wrap;
    gap: 12px;
    padding: 12px var(--fd-gutter);
    min-height: 0;
  }
  .fd-navcat { min-width: 94px; font-size: 13.5px; }
  .fd-search { order: 3; flex-basis: 100%; height: 42px; }
  .fd-bar__account { display: none; }
  .fd-account-icon { display: grid; place-items: center; color: #fff; }
  .fd-brand { margin-right: auto; }
}
</style>
