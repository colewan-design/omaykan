import type { BusinessMode } from '@pos/shared/index'

// Which store this storefront is showing.
//
// Mutable (not const) on purpose: the web storefront sets these once from
// build-time env vars and never touches them again, but the mobile storefront
// resolves them at runtime — the customer pairs to a store with a short code
// at runtime from a store code. ESM live-bindings mean every
// importer just sees the current value, so nothing downstream has to handle an
// "unresolved yet" state, as long as nothing reads them before pairing.
//
// Lives here rather than in the transport module so it survives that file's
// replacement —
// this is store identity, and has nothing to do with the transport.
export let ORG_SLUG: string = import.meta.env.VITE_POS_ORGANIZATION_SLUG
export let STORE_CODE: string = import.meta.env.VITE_POS_STORE_CODE
export let STORE_ADDRESS: string = import.meta.env.VITE_POS_STORE_ADDRESS ?? ''
export let BUSINESS_MODE = import.meta.env.VITE_POS_BUSINESS_MODE as BusinessMode

// Store pin used to quote a distance-based delivery fee (merged in from Baguio
// Delivery). Null when the store never set one — the checkout then falls back
// to the flat base fee.
export let STORE_LAT: number | null = Number(import.meta.env.VITE_POS_STORE_LAT) || null
export let STORE_LNG: number | null = Number(import.meta.env.VITE_POS_STORE_LNG) || null

export function setStorefrontContext(ctx: {
  orgSlug: string
  storeCode: string
  storeAddress: string
  businessMode: BusinessMode
  storeLat?: number | null
  storeLng?: number | null
}) {
  ORG_SLUG = ctx.orgSlug
  STORE_CODE = ctx.storeCode
  STORE_ADDRESS = ctx.storeAddress
  BUSINESS_MODE = ctx.businessMode
  STORE_LAT = ctx.storeLat ?? null
  STORE_LNG = ctx.storeLng ?? null
}
