import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { slugFromStoreCheckoutPath } from '@pos/shared/index'
import { fetchStores, type StoreSummary } from '@pos/web/commerce/api'
import { adoptHandedOverBasket } from '@pos/web/commerce/basketHandoff'
import { setStorefrontContext } from '@pos/web/commerce/context'
import StoreCheckoutPage from './StoreCheckoutPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
import '@pos/web/landing/marketing.css'
import '@pos/web/landing/landing.css'

// One designed look, forced light, as on every other storefront entry.
document.documentElement.dataset.theme = 'light'

/** Lines the cart page left unticked for this order: `?skip=<id>.<id>`. */
function readSkipped(): string[] {
  const raw = new URLSearchParams(window.location.search).get('skip') ?? ''
  return raw.split('.').filter(Boolean)
}

/**
 * The shop this checkout is for, from `/shop/<slug>/checkout`.
 *
 * Resolved before mount for the reason shop/main.ts gives: the catalog loads
 * once, on first use, and the order has to be priced and posted against this
 * shop's shelf. `?shop=` is read too, for a server that has not been taught
 * the path yet.
 *
 * Null when the slug is missing, unknown, or the directory cannot be reached.
 * Never the build-time tenant: a checkout that quietly placed the order with a
 * different shop than the one on the page would be the worst way to fail.
 */
async function resolveShop(): Promise<{ slug: string; store: StoreSummary | null; failed: boolean }> {
  const slug =
    slugFromStoreCheckoutPath(window.location.pathname) ||
    (new URLSearchParams(window.location.search).get('shop') ?? '')

  if (slug === '') return { slug, store: null, failed: false }

  // A basket carried over from the shop's subdomain, and only if it is this
  // shop's; one for another shop is dropped rather than checked out here.
  await adoptHandedOverBasket(slug)

  try {
    const stores = await fetchStores()
    const store = stores.find((candidate) => candidate.orgSlug === slug) ?? null
    if (store) {
      setStorefrontContext({
        orgSlug: store.orgSlug,
        storeCode: store.storeCode,
        storeAddress: store.address,
        businessMode: store.businessMode as Parameters<typeof setStorefrontContext>[0]['businessMode'],
        storeLat: store.lat,
        storeLng: store.lng,
      })
    }
    return { slug, store, failed: false }
  } catch {
    return { slug, store: null, failed: true }
  }
}

resolveShop().then(({ slug, store, failed }) => {
  const app = createApp(StoreCheckoutPage, { slug, store, directoryFailed: failed, skipped: readSkipped() })
  app.use(createPinia())
  app.mount('#checkout-app')
})
