// Carrying a basket from a shop's subdomain to checkout on the main site.
//
// `<slug>.omaykan.com` is its own origin, so its localStorage — the basket,
// and the customer's sign-in — is not the main site's. Checkout, accounts and
// Google sign-in stay on omaykan.com (Google will not take a wildcard origin),
// so the shop page hands the basket over in the checkout link instead:
//
//   https://omaykan.com/cart?step=checkout&basket=<slug>~<storeCode>~<id>*<qty>.<id>*<qty>
//
// Only ids and quantities travel. The cart page prices them again from the
// shop's live shelf, and the server prices the order from its own rows, so
// nothing in the link can change what anything costs.

import { UUID } from '@pos/web/commerce/cart'

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
