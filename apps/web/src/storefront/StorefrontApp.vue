<script setup lang="ts">
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'
import { Heart, Search, ShoppingCart, Store } from '@lucide/vue'
import { useStorefrontCart } from '@pos/web/storefront/cart'
import { useStorefrontSearch } from '@pos/web/storefront/search'
import { useStorefrontWishlist } from '@pos/web/storefront/wishlist'

const route = useRoute()
const router = useRouter()
const { itemCount } = useStorefrontCart()
const wishlist = useStorefrontWishlist()
const { query } = useStorefrontSearch()

function goToSection(id: string) {
  if (route.name !== 'catalog') {
    void router.push({ name: 'catalog', hash: `#${id}` })
    return
  }
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function toggleLoved() {
  wishlist.toggleLovedOnly()
  goToSection('categories')
}
</script>

<template>
  <div class="storefront">
    <header class="sf-header">
      <div class="sf-header__row">
        <RouterLink to="/" class="sf-brand">
          <span class="sf-brand__icon"><Store :size="20" /></span>
          <span class="sf-brand__text">
            <span class="sf-brand__mark">Baguio Online Market</span>
            <span class="sf-brand__sub">Order online for pickup</span>
          </span>
        </RouterLink>

        <nav class="sf-nav" aria-label="Store sections">
          <RouterLink to="/" class="sf-nav__link" :class="{ 'sf-nav__link--active': route.name === 'catalog' }">
            Shop
          </RouterLink>
          <button type="button" class="sf-nav__link" @click="goToSection('categories')">Categories</button>
          <button type="button" class="sf-nav__link" @click="goToSection('deals')">Deals</button>
        </nav>

        <label class="sf-search">
          <Search :size="17" />
          <input v-model="query" type="search" placeholder="Search products" />
        </label>

        <div class="sf-header__actions">
          <button
            type="button"
            class="sf-icon-btn"
            :class="{ 'sf-icon-btn--active': wishlist.showLovedOnly.value }"
            aria-label="Show wishlist"
            @click="toggleLoved"
          >
            <Heart :size="19" :fill="wishlist.count.value > 0 ? 'currentColor' : 'none'" />
            <span v-if="wishlist.count.value > 0" class="sf-badge">{{ wishlist.count.value }}</span>
          </button>

          <RouterLink to="/checkout" class="sf-icon-btn sf-icon-btn--cart" aria-label="Open cart">
            <ShoppingCart :size="19" />
            <span>Cart</span>
            <span v-if="itemCount > 0" class="sf-badge">{{ itemCount > 99 ? '99+' : itemCount }}</span>
          </RouterLink>
        </div>
      </div>
    </header>

    <main id="top" class="sf-main">
      <RouterView />
    </main>

    <footer class="sf-footer">
      <div class="sf-footer__row">
        <span class="sf-brand__mark">Baguio Online Market</span>
        <p>Orders are prepared in store — pay when you pick up. No online payment is collected.</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.storefront {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-base);
  color: var(--text-primary);
  font-family: var(--font-sans);
  /* Baguio green, matching the landing page. Re-pointed here rather than in
     tokens.css so the staff-facing POS app keeps its own blue accent — the
     storefront's buttons, steppers and prices all read --accent. Dark ink on
     the bright green: white would be ~2.2:1, this is ~7.5:1. */
  --accent: #22c55e;
  --accent-pressed: #16a34a;
  --accent-text-on: #06240f;
}

.sf-header {
  position: sticky;
  top: 0;
  z-index: 40;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--separator);
}

.sf-header__row {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  align-items: center;
  gap: var(--space-6);
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-3) var(--space-6);
}

.sf-brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  text-decoration: none;
  color: var(--text-primary);
}

.sf-brand__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: var(--accent-text-on);
  flex-shrink: 0;
}

.sf-brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.sf-brand__mark {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.sf-brand__sub {
  font-size: 11.5px;
  color: var(--text-tertiary);
  font-weight: 500;
}

.sf-nav {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.sf-nav__link {
  border: none;
  background: none;
  padding: 0;
  color: var(--text-secondary);
  font: 600 14px/1 inherit;
  text-decoration: none;
  cursor: pointer;
}

.sf-nav__link--active {
  color: var(--text-primary);
}

.sf-search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  justify-self: stretch;
  max-width: 380px;
  margin-left: auto;
  padding: 0 var(--space-3);
  height: 38px;
  border-radius: var(--radius-pill);
  background: var(--fill);
  color: var(--text-tertiary);
}

.sf-search input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font: 500 13.5px/1 inherit;
}

.sf-search input::placeholder {
  color: var(--text-tertiary);
}

.sf-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.sf-icon-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 var(--space-3);
  border: none;
  border-radius: var(--radius-pill);
  background: var(--fill);
  color: var(--text-secondary);
  font: 700 13px/1 inherit;
  text-decoration: none;
  cursor: pointer;
}

.sf-icon-btn--active {
  color: var(--accent);
}

.sf-icon-btn--cart {
  background: var(--accent);
  color: var(--accent-text-on);
}

.sf-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--danger);
  color: #fff;
  font-size: 10.5px;
  font-weight: 800;
}

.sf-main {
  flex: 1;
  max-width: 1160px;
  width: 100%;
  margin: 0 auto;
  padding: var(--space-6);
}

.sf-footer {
  border-top: 1px solid var(--separator);
  background: var(--bg-elevated);
}

.sf-footer__row {
  max-width: 1160px;
  margin: 0 auto;
  padding: var(--space-8) var(--space-6);
  display: grid;
  gap: var(--space-2);
}

.sf-footer p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 860px) {
  .sf-header__row {
    grid-template-columns: auto 1fr auto;
  }

  .sf-nav {
    display: none;
  }

  .sf-search {
    max-width: none;
  }

  .sf-icon-btn--cart span:not(.sf-badge) {
    display: none;
  }
}
</style>
