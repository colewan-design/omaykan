<script setup lang="ts">
import { ref } from 'vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { serviceCategories } from './categories'

// The site chrome — dark green utility bar with the search dominant, then the
// category nav. Shared by the landing page and the about page so the two can't
// drift apart; each page decides what a search submit means via @search.
//
// `shopHref` is where the category items and the cart point: the landing page
// scrolls to its own shelves, every other page navigates home to them.
const props = withDefaults(defineProps<{ shopHref?: string }>(), { shopHref: '#shop' })

const emit = defineEmits<{ search: [term: string] }>()

const cart = useStorefrontCart()
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
        <span class="fd-bar__label">Account</span>
        <a href="/app/auth">Sign in</a>
      </div>

      <a :href="props.shopHref" class="fd-cart" aria-label="Cart">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
        <span v-if="cart.itemCount.value > 0" class="fd-cart__count">{{ cart.itemCount.value }}</span>
      </a>
    </header>

    <nav class="fd-nav" aria-label="Shop by category">
      <div class="fd-nav__row">
        <a v-for="cat in serviceCategories" :key="cat.slug" :href="props.shopHref" class="fd-navcat">
          <component :is="cat.icon" :size="30" :stroke-width="1.5" />
          <span>{{ cat.label }}</span>
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

/* Even slots so eight categories span the bar; min-width tips the row into
   sideways scrolling on narrow screens rather than wrapping the header. */
.fd-nav__row {
  display: flex;
  align-items: stretch;
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
  min-width: 112px;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  padding: 15px 10px 13px;
  border-bottom: 3px solid transparent;
  color: rgba(255, 255, 255, 0.92);
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.fd-navcat:hover {
  color: #fff;
  border-bottom-color: #f5a623;
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
  .fd-brand { margin-right: auto; }
}
</style>
