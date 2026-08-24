import {
  Apple,
  Bike,
  CircleDollarSign,
  Croissant,
  type LucideIcon,
  Package,
  Pill,
  ShoppingCart,
  Store,
  UtensilsCrossed,
} from '@lucide/vue'

// Delivery domain merged in from Baguio Delivery. Its Laravel backend kept
// this logic in app/Services/DistanceService.php and OrderLifecycleService.php;
// the storefront is a static client against Firestore + /api, so the parts the
// customer needs (service categories, distance, fee, the stage timeline) live
// here instead and the authoritative fee is recomputed server-side in
// api/create-online-order.ts.

/** The four service categories Baguio Delivery shipped with. */
export type ServiceCategory = 'food' | 'groceries' | 'pharmacy' | 'errands'

export interface ServiceCategoryMeta {
  id: ServiceCategory
  label: string
  icon: LucideIcon
  /** Keywords matched against a store's own catalog category names. */
  match: RegExp
}

export const SERVICE_CATEGORIES: ServiceCategoryMeta[] = [
  { id: 'food', label: 'Food', icon: UtensilsCrossed, match: /food|meal|dish|menu|kitchen|bakery|bread|pastr|snack/i },
  { id: 'groceries', label: 'Groceries', icon: ShoppingCart, match: /grocer|produce|veg|fruit|dairy|milk|meat|pantry|staple/i },
  { id: 'pharmacy', label: 'Pharmacy', icon: Pill, match: /pharma|medicine|drug|health|wellness|vitamin/i },
  { id: 'errands', label: 'Errands', icon: Package, match: /errand|laundry|delivery|pickup|service/i },
]

/** Extra tiles the Baguio landing page showed, folded into the four real ones. */
export const CATEGORY_TILES: { label: string; icon: LucideIcon; category: ServiceCategory }[] = [
  { label: 'Food', icon: UtensilsCrossed, category: 'food' },
  { label: 'Groceries', icon: ShoppingCart, category: 'groceries' },
  { label: 'Pharmacy', icon: Pill, category: 'pharmacy' },
  { label: 'Errands', icon: Package, category: 'errands' },
  { label: 'Bakery', icon: Croissant, category: 'food' },
  { label: 'Produce', icon: Apple, category: 'groceries' },
  { label: 'Medicine', icon: Pill, category: 'pharmacy' },
  { label: 'Pasalubong', icon: Store, category: 'errands' },
]

/**
 * Best-effort service category for one of the store's own catalog categories.
 * Returns null when nothing matches so the caller can leave it unfiltered.
 */
export function serviceCategoryFor(categoryName: string): ServiceCategory | null {
  return SERVICE_CATEGORIES.find((entry) => entry.match.test(categoryName))?.id ?? null
}

/**
 * Great-circle distance in kilometres.
 * Port of DistanceService::haversineKm from the Baguio Delivery backend.
 */
export function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const earthRadiusKm = 6371
  const toRad = (deg: number) => (deg * Math.PI) / 180

  const latDelta = toRad(lat2 - lat1)
  const lngDelta = toRad(lng2 - lng1)

  const a =
    Math.sin(latDelta / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(lngDelta / 2) ** 2

  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/** Baguio Delivery's flag-down covers the first 2km; every km after adds ₱15. */
export const DELIVERY_BASE_FEE_CENTS = 4900
export const DELIVERY_BASE_KM = 2
export const DELIVERY_PER_KM_CENTS = 1500
/** Beyond this the storefront stops offering delivery. */
export const DELIVERY_MAX_KM = 15

export interface DeliveryQuote {
  distanceKm: number
  feeCents: number
  /** False when the address is outside the serviceable radius. */
  serviceable: boolean
}

export function quoteDelivery(distanceKm: number): DeliveryQuote {
  const serviceable = distanceKm <= DELIVERY_MAX_KM
  const extraKm = Math.max(0, Math.ceil(distanceKm - DELIVERY_BASE_KM))
  return {
    distanceKm,
    feeCents: DELIVERY_BASE_FEE_CENTS + extraKm * DELIVERY_PER_KM_CENTS,
    serviceable,
  }
}

/**
 * Customer-facing delivery timeline.
 * Mirrors OrderLifecycleService::TRANSITIONS from the Baguio backend, minus the
 * terminal rejected/cancelled branches which the storefront renders separately.
 */
export type DeliveryStage =
  | 'pending'
  | 'accepted'
  | 'rider_assigned'
  | 'ready_for_pickup'
  | 'out_for_delivery'
  | 'completed'

export interface DeliveryStageMeta {
  id: DeliveryStage
  label: string
  detail: string
  icon: LucideIcon
}

export const DELIVERY_STAGES: DeliveryStageMeta[] = [
  { id: 'pending', label: 'Order placed', detail: 'Waiting for the store to accept.', icon: CircleDollarSign },
  { id: 'accepted', label: 'Accepted', detail: 'The store is preparing your order.', icon: Store },
  { id: 'rider_assigned', label: 'Rider assigned', detail: 'A rider nearby is on the way to the store.', icon: Bike },
  { id: 'ready_for_pickup', label: 'Ready', detail: 'Packed and waiting for the rider.', icon: Package },
  { id: 'out_for_delivery', label: 'Out for delivery', detail: 'Your rider is heading to you.', icon: Bike },
  { id: 'completed', label: 'Delivered', detail: 'Enjoy! Pay your rider on arrival.', icon: CircleDollarSign },
]

const STAGE_ORDER: DeliveryStage[] = DELIVERY_STAGES.map((stage) => stage.id)

export function stageIndex(stage: DeliveryStage): number {
  return STAGE_ORDER.indexOf(stage)
}

/**
 * Map the POS's own three-state order status onto the delivery timeline.
 * The staff app only ever moves an order preparing -> ready -> served, so a
 * delivery order that carries an explicit `deliveryStage` uses that, and one
 * that doesn't falls back to the closest equivalent.
 */
export function deliveryStageFor(
  status: 'preparing' | 'ready' | 'served',
  explicitStage?: DeliveryStage | null,
): DeliveryStage {
  if (explicitStage && STAGE_ORDER.includes(explicitStage)) return explicitStage
  switch (status) {
    case 'preparing':
      return 'accepted'
    case 'ready':
      return 'ready_for_pickup'
    case 'served':
      return 'completed'
  }
}
