import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { setStorefrontContext } from '@pos/web/commerce/context'
import { fetchStores } from '@pos/web/commerce/api'
import { BASKET_PARAM, decodeBasketHandoff } from '@pos/web/commerce/basketHandoff'
import { readCartShop, useStorefrontCart, type CartShop } from '@pos/web/commerce/cart'
import { loadStorefrontCatalog } from '@pos/web/commerce/catalog'
import CartPage from './CartPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
import '@pos/web/landing/marketing.css'
import '@pos/web/landing/landing.css'

// Same setup as the landing and account entries: one designed look, forced
// light so the page doesn't flip to the app's dark palette on a visitor's OS.
document.documentElement.dataset.theme = 'light'

/**
 * A basket handed over from a shop's subdomain (see commerce/basketHandoff.ts).
 *
 * It replaces whatever basket this origin had: the customer pressed checkout
 * on that shop a moment ago, and an order can only come from one shop anyway.
 * Lines are priced from the shop's live shelf; one that is no longer on it is
 * left out rather than carried as a line checkout would refuse.
 *
 * The parameter is taken off the address either way, so a refresh or a shared
 * cart link does not hand the same basket over again on top of later changes.
 */
async function importHandedOverBasket(): Promise<void> {
  const params = new URLSearchParams(window.location.search)
  const raw = params.get(BASKET_PARAM)
  if (raw === null) return

  params.delete(BASKET_PARAM)
  const query = params.toString()
  window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`)

  const handoff = decodeBasketHandoff(raw)
  if (!handoff) return

  try {
    const stores = await fetchStores()
    const store = stores.find((s) => s.orgSlug === handoff.orgSlug && s.storeCode === handoff.storeCode)
    if (!store) return

    const shop: CartShop = {
      orgSlug: store.orgSlug,
      storeCode: store.storeCode,
      storeAddress: store.address,
      businessMode: store.businessMode as CartShop['businessMode'],
      storeLat: store.lat,
      storeLng: store.lng,
      name: store.name,
    }
    setStorefrontContext(shop)

    const catalog = await loadStorefrontCatalog()
    const onShelf = new Map(catalog.products.map((product) => [product.id, product]))
    const cart = useStorefrontCart()
    cart.clear()
    for (const line of handoff.lines) {
      const product = onShelf.get(line.productId)
      if (product && !product.outOfStock) cart.add(product, line.quantity, shop)
    }
  } catch {
    // Directory or shelf unreachable: open the cart as it was. The shop page
    // still has the basket, one tap back.
  }
}

importHandedOverBasket().finally(() => {
  // The basket belongs to whichever shop it was filled from, which need not be
  // the build-time tenant. Set before mount for the reason landing/main.ts
  // gives: the catalog composable loads once, on first use, and the order has
  // to be posted to the same store the lines were priced by.
  const shop = readCartShop()
  if (shop) setStorefrontContext(shop)

  const app = createApp(CartPage)
  app.use(createPinia())
  app.mount('#cart-app')
})
