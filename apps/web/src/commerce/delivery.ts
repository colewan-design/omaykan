// A delivery fee the shopper can see before they commit to the order.
//
// Deliberately a mirror of backend/app/Services/DeliveryQuoter.php and not a
// call to it: the drawer re-quotes on every keystroke and every drag of the
// location pin, and a round trip per keystroke would make the total flicker.
// Nothing here is authoritative — OnlineOrderController re-quotes from the
// store's own pin and *that* number is what gets charged. Keep the constants
// in step with the PHP; they are the same four numbers.
//
// This file was lost with the rest of apps/web/src/storefront when the
// storefront was moved out, which is what left the landing page's cart with a
// count and nowhere to go. It comes back here, under commerce/, because the
// fee belongs to the checkout rather than to any one storefront shell.

export const DELIVERY_BASE_FEE_CENTS = 4900

/** Distance included in the base fee; only kilometres past this one add to it. */
export const DELIVERY_BASE_KM = 2

export const DELIVERY_PER_KM_CENTS = 1500

/** Past this the store does not deliver at all, at any price. */
export const DELIVERY_MAX_KM = 15

const EARTH_RADIUS_KM = 6371

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180
}

/** Great-circle distance, the same measure DistanceService uses server-side. */
export function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const dLat = toRadians(lat2 - lat1)
  const dLng = toRadians(lng2 - lng1)

  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * Math.sin(dLng / 2) ** 2

  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)))
}

/** Whole kilometres past the included distance, each at the per-km rate. */
export function feeForKm(distanceKm: number): number {
  const extraKm = Math.max(0, Math.ceil(distanceKm - DELIVERY_BASE_KM))

  return DELIVERY_BASE_FEE_CENTS + extraKm * DELIVERY_PER_KM_CENTS
}

export interface DeliveryQuote {
  /** False past DELIVERY_MAX_KM, where there is no fee because there is no delivery. */
  serviceable: boolean
  feeCents: number
  distanceKm: number
}

export function quoteDelivery(distanceKm: number): DeliveryQuote {
  if (distanceKm > DELIVERY_MAX_KM) {
    return { serviceable: false, feeCents: 0, distanceKm: Math.round(distanceKm * 100) / 100 }
  }

  return {
    serviceable: true,
    feeCents: feeForKm(distanceKm),
    distanceKm: Math.round(distanceKm * 100) / 100,
  }
}
