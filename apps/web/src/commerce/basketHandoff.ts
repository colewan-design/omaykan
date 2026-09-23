// Carrying a basket from a shop's subdomain to checkout on the main site.
//
// `<slug>.omaykan.com` is its own origin, so its localStorage — the basket,
// and the customer's sign-in — is not the main site's. Checkout, accounts and
// Google sign-in stay on omaykan.com (Google will not take a wildcard origin),
// so the shop page hands the basket over in the checkout link instead:
//
//   https://omaykan.com/shop/<slug>/checkout?basket=<slug>~<storeCode>~<id>*<qty>.<id>*<qty>
//
// (or /cart?basket=… for the cart). Only ids and quantities travel. The page prices them again from the
// shop's live shelf, and the server prices the order from its own rows, so
// nothing in the link can change what anything costs.

import { fetchStores } from '@pos/web/commerce/api'
import { UUID, useStorefrontCart, type CartShop } from '@pos/web/commerce/cart'
import { loadStorefrontCatalog } from '@pos/web/commerce/catalog'
import { setStorefrontContext } from '@pos/web/commerce/context'

export const BASKET_PARAM = 'basket'

/** Enough for any real basket, small enough to keep the URL well inside limits. */
const MAX_LINES = 80
const MAX_QUANTITY = 999

export interface BasketHandoff {
  orgSlug: string
  storeCode: string
  lines: Array<{ productId: string; quantity: number }>
}

const TOKEN = /^[A-Za-z0-9_-]{1,100}$/

export function encodeBasketHandoff(handoff: BasketHandoff): string {
  const lines = handoff.lines
    .filter((line) => UUID.test(line.productId) && line.quantity > 0)
    .slice(0, MAX_LINES)
    .map((line) => `${line.productId}*${Math.min(MAX_QUANTITY, Math.floor(line.quantity))}`)
  return [handoff.orgSlug, handoff.storeCode, lines.join('.')].join('~')
}

/** Null for anything malformed. A bad line drops the whole handoff, not just itself. */
export function decodeBasketHandoff(raw: string | null): BasketHandoff | null {
  if (!raw) return null
  const parts = raw.split('~')
  if (parts.length !== 3) return null
  const [orgSlug, storeCode, body] = parts as [string, string, string]
  if (!TOKEN.test(orgSlug) || !TOKEN.test(storeCode) || body === '') return null

  const lines: BasketHandoff['lines'] = []
  const seen = new Set<string>()
  for (const entry of body.split('.')) {
    const [productId, quantityText, ...rest] = entry.split('*')
    if (rest.length > 0 || !productId || !UUID.test(productId) || !/^\d{1,3}$/.test(quantityText ?? '')) return null
    const quantity = Number(quantityText)
    if (quantity < 1 || seen.has(productId)) return null
    seen.add(productId)
    lines.push({ productId, quantity })
  }
  if (lines.length === 0 || lines.length > MAX_LINES) return null

  return { orgSlug, storeCode, lines }
}

/**
 * Take a basket handed over in this page's address, if there is one.
 *
 * It replaces whatever basket this origin had: the customer pressed checkout
 * on that shop a moment ago, and an order can only come from one shop anyway.
 * Lines are priced from the shop's live shelf; one that is no longer on it is
 * left out rather than carried as a line checkout would refuse.
 *
 * The parameter is taken off the address either way, so a refresh or a shared
 * link does not hand the same basket over again on top of later changes.
 *
 * `onlyFor` is the shop a page is for: a handoff naming any other is dropped.
 * Leaves the storefront context pointed at the handed-over shop when it takes
 * one. Never throws — with the directory or shelf out of reach the page opens
 * on the basket it already had, and the shop still has the lines one tap back.
 */
export async function adoptHandedOverBasket(onlyFor?: string): Promise<void> {
  const params = new URLSearchParams(window.location.search)
  const raw = params.get(BASKET_PARAM)
  if (raw === null) return

  params.delete(BASKET_PARAM)
  const query = params.toString()
  window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`)

  const handoff = decodeBasketHandoff(raw)
  if (!handoff || (onlyFor !== undefined && handoff.orgSlug !== onlyFor)) return

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
    // Directory or shelf unreachable: open the page on the basket it had.
  }
}
