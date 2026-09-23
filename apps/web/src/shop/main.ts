import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { slugFromStorefrontPath, storefrontUrl } from '@pos/shared/index'
import { fetchStores, type StoreSummary } from '@pos/web/commerce/api'
import { setStorefrontContext } from '@pos/web/commerce/context'
import { SHOP_ROOT_DOMAIN, mainSiteOrigin, shopSlugFromHost } from '@pos/web/commerce/shopDomain'
import ShopPage from './ShopPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
import '@pos/web/landing/marketing.css'
import '@pos/web/landing/landing.css'

// One designed look, forced light, as on every other storefront entry.
document.documentElement.dataset.theme = 'light'

/**
 * The shop this page is for, checked against the directory.
 *
 * The host names it on a shop subdomain (`nenas-market-stall.omaykan.com`);
 * otherwise the path does (`/shop/nenas-market-stall`).
 *
 * Resolved before mount for the reason landing/main.ts gives: the catalog
 * composable loads once, on first use, so the storefront context has to be
 * pointed at this shop before anything asks for a shelf. `?shop=` is read too,
 * so the page also works where the path rewrite is not in place yet.
 *
 * Null when the slug is missing, unknown, or the directory cannot be reached —
 * the page says so rather than falling back to the build-time tenant, because
 * a shared link that quietly opened a different shop would be worse than one
 * that opened nothing.
 */
async function resolveShop(): Promise<{ slug: string; store: StoreSummary | null; failed: boolean }> {
  const named = shopSlugFromHost() || slugFromStorefrontPath(window.location.pathname)
  const fromQuery = new URLSearchParams(window.location.search).get('shop') ?? ''

  // `?shop=<other>` on this shop's own page — what every link made before
  // shops had their own address looks like. It would otherwise reopen this
  // same shop; send it to the one it names. Never resolves: the page is
  // being left.
  if (named !== '' && fromQuery !== '' && fromQuery !== named) {
    const origin = mainSiteOrigin() || window.location.origin
    window.location.replace(storefrontUrl(fromQuery, { rootDomain: SHOP_ROOT_DOMAIN, origin }))
    return new Promise(() => {})
  }

  const slug = named || fromQuery

  if (slug === '') return { slug, store: null, failed: false }

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
  const app = createApp(ShopPage, { slug, store, directoryFailed: failed })
  app.use(createPinia())
  app.mount('#shop-app')
})
