import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { storeCheckoutPath } from '@pos/shared/index'
import { ORG_SLUG, setStorefrontContext } from '@pos/web/commerce/context'
import { adoptHandedOverBasket } from '@pos/web/commerce/basketHandoff'
import { readCartShop, useStorefrontCart } from '@pos/web/commerce/cart'
import CartPage from './CartPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
import '@pos/web/landing/marketing.css'
import '@pos/web/landing/landing.css'

// Same setup as the landing and account entries: one designed look, forced
// light so the page doesn't flip to the app's dark palette on a visitor's OS.
document.documentElement.dataset.theme = 'light'

/**
 * Checkout is each shop's own page now, /shop/<slug>/checkout. `/cart?step=checkout`
 * is what went out before that — in sign-in return links, in bookmarks, and
 * from shop pages still open in someone's tab — so it goes on to the shop's
 * checkout rather than stopping at the cart. Only with a basket to check out:
 * an empty one has nowhere to send them, and the cart page says so.
 */
function forwardToShopCheckout(): boolean {
  const params = new URLSearchParams(window.location.search)
  if (params.get('step') !== 'checkout' || useStorefrontCart().cartLines.value.length === 0) return false
  const slug = readCartShop()?.orgSlug || ORG_SLUG
  if (!slug) return false
  window.location.replace(storeCheckoutPath(slug))
  return true
}

adoptHandedOverBasket().finally(() => {
  if (forwardToShopCheckout()) return

  // The basket belongs to whichever shop it was filled from, which need not be
  // the build-time tenant. Set before mount for the reason landing/main.ts
  // gives: the catalog composable loads once, on first use, and the lines have
  // to be checked against the same store they were priced by.
  const shop = readCartShop()
  if (shop) setStorefrontContext(shop)

  const app = createApp(CartPage)
  app.use(createPinia())
  app.mount('#cart-app')
})
