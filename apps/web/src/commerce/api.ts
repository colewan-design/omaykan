import type { BusinessMode, Category, Product } from '@pos/shared/index'
import { BUSINESS_MODE, ORG_SLUG, STORE_CODE } from '@pos/web/commerce/context'

// The storefront's entire backend. Everything here used to be split between
// Firestore reads straight from the browser (the catalog, order status) and the
// serverless handlers in /api (placing an order, resolving a store code); it is
// now one Laravel API, which is what lets the Firebase SDK leave the bundle.
//
// Empty base by default so the web deployment — served from the same origin as
// /api — uses relative paths. The mobile app is not served from that origin and
// must set VITE_ONLINE_ORDER_API_BASE to the full one.
const API_BASE = (import.meta.env.VITE_ONLINE_ORDER_API_BASE ?? '').replace(/\/$/, '')

/**
 * Laravel reports validation failures as `{message, errors: {field: [msg]}}`
 * and aborts as `{message}`. The old handlers used `{error}`. Both are read
 * here so a half-migrated deployment still surfaces something useful, and the
 * first field-level message wins because it is the specific one — "'Espresso'
 * doesn't have enough stock" rather than "The given data was invalid."
 */
function errorMessageFrom(payload: unknown, fallback: string): string {
  if (!payload || typeof payload !== 'object') return fallback

  const body = payload as { message?: unknown; error?: unknown; errors?: Record<string, unknown> }

  const firstFieldError = Object.values(body.errors ?? {})
    .flatMap((messages) => (Array.isArray(messages) ? messages : [messages]))
    .find((message): message is string => typeof message === 'string' && message.length > 0)

  if (firstFieldError) return firstFieldError
  if (typeof body.error === 'string' && body.error) return body.error
  if (typeof body.message === 'string' && body.message) return body.message

  return fallback
}

async function request<TResult>(
  path: string,
  init: RequestInit & { fallbackError?: string } = {},
): Promise<TResult> {
  const { fallbackError = 'Something went wrong. Please try again.', ...rest } = init

  const response = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: {
      Accept: 'application/json',
      ...(rest.body ? { 'Content-Type': 'application/json' } : {}),
      ...rest.headers,
    },
  })

  const data = await response.json().catch(() => null)

  if (!response.ok) {
    throw new ApiRequestError(errorMessageFrom(data, fallbackError), response.status)
  }

  return data as TResult
}

/** Carries the status so callers can tell "gone" from "broken". */
export class ApiRequestError extends Error {
  // Declared rather than a constructor parameter property: the build runs
  // TypeScript with erasableSyntaxOnly, which rejects that shorthand.
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

function postJson<TResult>(path: string, body: unknown, fallbackError?: string): Promise<TResult> {
  return request<TResult>(path, { method: 'POST', body: JSON.stringify(body), fallbackError })
}

// -- Catalog ---------------------------------------------------------------

interface ApiProduct {
  id: string
  categoryId: string
  sku: string
  barcode: string
  name: string
  priceCents: number
  compareAtPriceCents: number | null
  taxRate: number
  kind: 'standard' | 'weighted'
  imageUrl: string | null
  unitLabel: string | null
  businessModes: string[]
  outOfStock: boolean
  stockQty: number | null
  lowStockThreshold: number | null
}

export interface StorefrontCatalog {
  categories: Category[]
  products: Product[]
}

/**
 * The API already filters to this store's business mode, applies per-store
 * price and availability overrides, and drops anything out of stock — all of
 * which the browser used to do for itself against org-wide Firestore documents.
 * So this only has to widen nulls back into the optional fields the shared
 * Product type uses.
 */
function toProduct(product: ApiProduct): Product {
  return {
    id: product.id,
    categoryId: product.categoryId,
    sku: product.sku,
    barcode: product.barcode,
    name: product.name,
    priceCents: product.priceCents,
    compareAtPriceCents: product.compareAtPriceCents ?? undefined,
    taxRate: Number(product.taxRate),
    kind: product.kind === 'weighted' ? 'weighted' : 'standard',
    imageUrl: product.imageUrl ?? undefined,
    unitLabel: product.unitLabel ?? undefined,
    businessModes: (product.businessModes ?? []) as Product['businessModes'],
    outOfStock: product.outOfStock,
    stockQty: product.stockQty ?? undefined,
    lowStockThreshold: product.lowStockThreshold ?? undefined,
  }
}

export async function fetchCatalog(): Promise<StorefrontCatalog> {
  const params = new URLSearchParams({ orgSlug: ORG_SLUG, storeCode: STORE_CODE })

  const payload = await request<{ categories: Category[]; products: ApiProduct[] }>(
    `/api/storefront/catalog?${params}`,
    { fallbackError: 'Could not load the menu.' },
  )

  return {
    categories: payload.categories ?? [],
    products: (payload.products ?? []).map(toProduct),
  }
}

// -- Store discovery -------------------------------------------------------

export interface ResolveStoreCodeResult {
  orgSlug: string
  storeCode: string
  businessMode: BusinessMode
  storeName: string
  storeAddress: string
  storeLat?: number | null
  storeLng?: number | null
}

export function resolveStoreCode(code: string): Promise<ResolveStoreCodeResult> {
  return postJson<ResolveStoreCodeResult>(
    '/api/store-codes/resolve',
    { code },
    "We couldn't find a store with that code.",
  )
}

// -- Placing an order ------------------------------------------------------

export interface CreateOnlineOrderItem {
  productId: string
  quantity: number
}

export interface CreateOnlineOrderGuest {
  name: string
  phone?: string
  email?: string
}

export interface CreateOnlineOrderFulfillment {
  method: 'pickup' | 'delivery'
  address?: string
  /**
   * Optional drop-off coordinates, sent when the customer shares their
   * location. The API recomputes the fee from these rather than trusting the
   * client's quote.
   */
  lat?: number
  lng?: number
}

export interface CreateOnlineOrderResult {
  orderId: string
  ticketNumber: string
  totalCents: number
  /** Server-computed; 0 for pickup orders. */
  deliveryFeeCents?: number
}

export function createOnlineOrder(
  items: CreateOnlineOrderItem[],
  guest: CreateOnlineOrderGuest,
  fulfillment: CreateOnlineOrderFulfillment,
  paymentMethod?: 'cash' | 'ewallet',
): Promise<CreateOnlineOrderResult> {
  return postJson<CreateOnlineOrderResult>(
    '/api/online-orders',
    {
      orgSlug: ORG_SLUG,
      storeCode: STORE_CODE,
      businessMode: BUSINESS_MODE,
      items,
      guest,
      fulfillment,
      paymentMethod,
    },
    'Could not place your order.',
  )
}

// -- Tracking an order -----------------------------------------------------

export interface TrackedOrderItem {
  productId: string
  name: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
}

export interface TrackedOrder {
  orderId: string
  ticketNumber: string
  status: string
  paymentStatus: string
  paymentMethod: string | null
  subtotalCents: number
  taxCents: number
  deliveryFeeCents: number
  totalCents: number
  fulfillmentMethod: 'pickup' | 'delivery' | null
  deliveryAddress: string | null
  deliveryStage: string | null
  riderName: string | null
  riderPhone: string | null
  placedAt: string | null
  items: TrackedOrderItem[]
}

export function fetchOrder(orderId: string): Promise<TrackedOrder> {
  return request<TrackedOrder>(`/api/online-orders/${encodeURIComponent(orderId)}`, {
    fallbackError: 'Could not load that order.',
  })
}
