import { fetchStores, type StoreSummary } from '@pos/web/commerce/api'
import { reloadStorefrontCatalog } from '@pos/web/commerce/catalog'
import { resetStorefrontContext, setStorefrontContext } from '@pos/web/commerce/context'

/**
 * Point the storefront at another shop.
 *
 * The landing page used to do this with a full navigation, because the catalog
 * is a module-level singleton that loaded once on first use — setting the
 * context afterwards fetched nothing and left the old shelf up. It now has a
 * deliberate reload, so the swap can happen in place and a shopper browsing
 * the market does not lose the page on every shop they look at.
 *
 * Everything downstream reads the context through ESM live bindings rather
 * than copying it at import time — `currentShop()` in cart.ts, the catalog
 * fetch in api.ts — so re-pointing it here is enough; nothing needs telling.
 */
export function applyShop(store: StoreSummary): void {
  setStorefrontContext({
    orgSlug: store.orgSlug,
    storeCode: store.storeCode,
    storeAddress: store.address,
    businessMode: store.businessMode as Parameters<typeof setStorefrontContext>[0]['businessMode'],
    storeLat: store.lat,
    storeLng: store.lng,
  })
  reloadStorefrontCatalog()
}

/**
 * The same swap, from a slug alone — what `?shop=` in the URL carries.
 *
 * The slug is checked against the directory rather than trusted: it arrives
 * from the URL, and pointing the storefront at an unlisted or misspelt tenant
 * would render an empty shop with no explanation. Returns whether it took, so
 * a caller arriving on a bad slug can leave the env tenant standing.
 */
export async function applyShopBySlug(slug: string): Promise<boolean> {
  if (slug === '') return false
  try {
    const stores = await fetchStores()
    const match = stores.find((store) => store.orgSlug === slug)
    if (!match) return false
    applyShop(match)
    return true
  } catch {
    // Directory unreachable: fall through to whichever shop is already set
    // rather than showing nothing at all.
    return false
  }
}

/**
 * Back to the shop the bundle was built for — what a URL with no `?shop=`
 * means. Pressing Back off a chosen shop lands here.
 */
export function resetShop(): void {
  resetStorefrontContext()
  reloadStorefrontCatalog()
}
