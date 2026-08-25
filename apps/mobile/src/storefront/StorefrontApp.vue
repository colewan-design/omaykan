<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import { Heart, History, House, Settings, ShoppingBag } from '@lucide/vue'
import { useStorefrontCart } from '@pos/web/storefront/cart'
import { useStorefrontWishlist } from '@pos/web/storefront/wishlist'

const route = useRoute()
const router = useRouter()
const { itemCount } = useStorefrontCart()
const wishlist = useStorefrontWishlist()

const showNav = computed(() => route.name !== 'product')

const activeTab = computed(() => {
  if (route.name === 'checkout') return 'cart'
  if (route.name === 'history' || route.name === 'order') return 'history'
  if (route.name === 'settings') return 'settings'
  if (route.name === 'catalog' && wishlist.showLovedOnly.value) return 'wishlist'
  return 'home'
})

function activateTab(tab: 'home' | 'wishlist' | 'cart' | 'history' | 'settings') {
  switch (tab) {
    case 'home':
      if (wishlist.showLovedOnly.value) wishlist.toggleLovedOnly()
      if (route.name !== 'catalog') void router.push({ name: 'catalog' })
      break
    case 'wishlist':
      wishlist.toggleLovedOnly()
      if (route.name !== 'catalog') void router.push({ name: 'catalog' })
      break
    case 'cart':
      void router.push({ name: 'checkout' })
      break
    case 'history':
      void router.push({ name: 'history' })
      break
    case 'settings':
      void router.push({ name: 'settings' })
      break
  }
}
</script>

<template>
  <div class="storefront">
    <main class="sf-main" :class="{ 'sf-main--with-nav': showNav }">
      <RouterView />
    </main>

    <nav v-if="showNav" class="sf-nav" aria-label="Store navigation">
      <button
        type="button"
        class="sf-nav__item"
        :class="{ 'sf-nav__item--active': activeTab === 'home' }"
        aria-label="Home"
        @click="activateTab('home')"
      >
        <House :size="22" :fill="activeTab === 'home' ? 'currentColor' : 'none'" />
      </button>
      <button
        type="button"
        class="sf-nav__item"
        :class="{ 'sf-nav__item--active': activeTab === 'cart' }"
        aria-label="Cart"
        @click="activateTab('cart')"
      >
        <span class="sf-nav__icon-wrap">
          <ShoppingBag :size="22" />
          <span v-if="itemCount > 0" class="sf-badge">{{ itemCount > 99 ? '99+' : itemCount }}</span>
        </span>
      </button>
      <button
        type="button"
        class="sf-nav__item"
        :class="{ 'sf-nav__item--active': activeTab === 'wishlist' }"
        aria-label="Liked products"
        @click="activateTab('wishlist')"
      >
        <Heart :size="22" :fill="activeTab === 'wishlist' ? 'currentColor' : 'none'" />
      </button>
      <button
        type="button"
        class="sf-nav__item"
        :class="{ 'sf-nav__item--active': activeTab === 'history' }"
        aria-label="Order history"
        @click="activateTab('history')"
      >
        <History :size="22" />
      </button>
      <button
        type="button"
        class="sf-nav__item"
        :class="{ 'sf-nav__item--active': activeTab === 'settings' }"
        aria-label="Settings"
        @click="activateTab('settings')"
      >
        <Settings :size="22" />
      </button>
    </nav>
  </div>
</template>

<style scoped>
.storefront {
  min-height: 100vh;
  background: #f8f6f2;
  color: #1f2430;
  font-family: "Poppins", "Segoe UI", sans-serif;
}

.sf-main {
  padding: 16px 16px 28px;
}

.sf-main--with-nav {
  padding-bottom: 104px;
}

.sf-nav {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: calc(14px + env(safe-area-inset-bottom));
  z-index: 45;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  padding: 12px 10px;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 16px 34px rgba(31, 36, 48, 0.14);
}

.sf-nav__item {
  position: relative;
  display: grid;
  place-items: center;
  border: none;
  background: transparent;
  color: #b7b4ad;
  padding: 6px;
}

.sf-nav__item--active {
  color: #1f2430;
}

.sf-nav__icon-wrap {
  position: relative;
  display: inline-flex;
}

.sf-badge {
  position: absolute;
  top: -8px;
  right: -10px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--sf-primary);
  color: #fff;
  font-size: 0.64rem;
  font-weight: 800;
}
</style>
