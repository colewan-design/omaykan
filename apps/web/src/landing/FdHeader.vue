<script setup lang="ts">
import { computed, ref } from 'vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useStockedCategories } from '@pos/web/commerce/catalog'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { categoryIcon } from './categories'

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
// `shopHref` is where the cart and the category items point: the landing page
// handles a category in place (@category, no navigation), every other page
// sends the browser home with ?category= for the landing page to pick up.
const props = withDefaults(
  defineProps<{ shopHref?: string; activeCategory?: string }>(),
  { shopHref: '#shop', activeCategory: '' },
)

const emit = defineEmits<{ search: [term: string]; category: [categoryId: string] }>()

const categories = useStockedCategories()

/** True on the landing page, where the shelves are on this same document. */
const handlesCategoryInPage = computed(() => props.shopHref.startsWith('#'))

function categoryHref(categoryId: string): string {
  if (handlesCategoryInPage.value) return props.shopHref
  return `${props.shopHref}?category=${encodeURIComponent(categoryId)}`
}

function selectCategory(categoryId: string, event: MouseEvent) {
  // Off the landing page the anchor is a real navigation home; leave it alone.
  if (!handlesCategoryInPage.value) return
  event.preventDefault()
  emit('category', categoryId)
}

const cart = useStorefrontCart()

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

defineExpose({ clear: () => (searchTerm.value = '') })
</script>

<template>
  <div class="fd-head">
    <header class="fd-bar">
      <a href="/" class="fd-brand" aria-label="Omaykan — home">
        <BrandLogo variant="dark" :size="20" />
      </a>

      <div class="fd-bar__delivery">
        <span>Delivery</span>
        <a :href="props.shopHref">Enter your address</a>
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

      <a :href="props.shopHref" class="fd-cart" aria-label="Cart">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
        <span v-if="cart.itemCount.value > 0" class="fd-cart__count">{{ cart.itemCount.value }}</span>
      </a>
    </header>

    <nav class="fd-nav" aria-label="Shop by category">
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
          <component :is="categoryIcon(cat.id)" :size="28" :stroke-width="1.5" />
          <span>{{ cat.name }}</span>
        </a>
      </div>
    </nav>
  </div>
</template>

<style scoped>
/* Both header rows stick as one block, so the category nav stays reachable
   the whole way down the page. */
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

.fd-nav {
  background: #1a6b3c;
  border-top: 1px solid rgba(255, 255, 255, 0.14);
}

/* Even slots so the categories span the bar; min-width tips the row into
   sideways scrolling on narrow screens rather than wrapping the header.
   min-height holds the bar open while the catalog is still loading, so the
   page doesn't jolt down when the categories arrive. */
.fd-nav__row {
  display: flex;
  align-items: stretch;
  min-height: 66px;
  padding: 0 var(--fd-gutter);
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
.fd-bar__delivery a {
  font-size: 13px;
  color: rgba(255,255,255,0.85);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.fd-bar__delivery a:hover { color: #fff; }

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

.fd-cart { position: relative; display: grid; place-items: center; color: #fff; }
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
