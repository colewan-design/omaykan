import type { BusinessMode, Category, Product } from '@pos/shared/index'
import { BUSINESS_MODE, ORG_SLUG, STORE_CODE } from '@pos/web/commerce/context'
import { customerToken } from '@pos/web/commerce/session'

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

  // Attached to every call, not just the /customer ones. That is deliberate:
  // it is also what lets POST /online-orders record who placed an order, so a
  // signed-in customer's order shows up in their history without checkout
  // having to know anything about accounts.
  const token = customerToken()

  const response = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: {
      Accept: 'application/json',
      ...(rest.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
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
  storeCode?: string
  storeName?: string
  storeAddress?: string
  distanceKm?: number | null
}

/**
 * A product as the storefront sees it: the shared shape, plus which branch
 * this particular one would come off, where that branch is and how far away it
 * is. The distance is null until a delivery address is set — see fetchCatalog.
 */
export interface StorefrontProduct extends Product {
  storeCode?: string
  storeName?: string
  /** Empty when the branch never filled its address in. */
  storeAddress?: string
  distanceKm?: number | null
}

export interface CatalogDeliveryStore {
  code: string
  name: string
  address: string
  /** Null when the branch never set a pin, so no distance can be quoted. */
  distanceKm: number | null
  feeCents: number | null
}

/** How the catalog above was filtered, and what to say about it. */
export interface CatalogDelivery {
  /** False when no address was sent — nothing was filtered. */
  requested: boolean
  /** False when no branch reaches the address; the catalog is then empty. */
  serviceable: boolean
  maxKm: number
  /** Charged when no distance can be worked out — quoted by the API. */
  baseFeeCents: number
  /** The branches that can deliver here, nearest first. */
  stores: CatalogDeliveryStore[]
  /** Only when out of range: the closest branch, for "we're X km away". */
  nearest: CatalogDeliveryStore | null
}

export interface StorefrontCatalog {
  categories: Category[]
  products: StorefrontProduct[]
  delivery: CatalogDelivery
}

/** What an un-filtered catalog reports — no address was in play. */
export const unfilteredDelivery: CatalogDelivery = {
  requested: false,
  serviceable: true,
  maxKm: 15,
  baseFeeCents: 4900,
  stores: [],
  nearest: null,
}

/**
 * The API already filters to this store's business mode, applies per-store
 * price and availability overrides, and drops anything out of stock — all of
 * which the browser used to do for itself against org-wide Firestore documents.
 * So this only has to widen nulls back into the optional fields the shared
 * Product type uses.
 */
function toProduct(product: ApiProduct): StorefrontProduct {
  return {
    id: product.id,
    categoryId: product.categoryId,
    sku: product.sku,
    barcode: product.barcode,
    name: product.name,
    priceCents: product.priceCents,
    compareAtPriceCents: product.compareAtPriceCents ?? undefined,
    // The API stores a percentage (12.00); every TypeScript consumer of
    // Product.taxRate — calculateTax, the demo catalogs, the register's cart —
    // treats it as a fraction (0.12). Mapping it straight through made the
    // cart quote twelve times the real VAT, which nothing noticed while the
    // storefront had no checkout to show a total on.
    taxRate: Number(product.taxRate) / 100,
    kind: product.kind === 'weighted' ? 'weighted' : 'standard',
    imageUrl: product.imageUrl ?? undefined,
    unitLabel: product.unitLabel ?? undefined,
    businessModes: (product.businessModes ?? []) as Product['businessModes'],
    outOfStock: product.outOfStock,
    stockQty: product.stockQty ?? undefined,
    lowStockThreshold: product.lowStockThreshold ?? undefined,
    storeCode: product.storeCode,
    storeName: product.storeName,
    storeAddress: product.storeAddress,
    distanceKm: product.distanceKm ?? null,
  }
}

/**
 * With a drop pin the API answers "what can reach this address" rather than
 * "what this one store sells": the union of every branch in range, nearest
 * first, and an empty list when none of them reach it. Without one it answers
 * exactly what it always did.
 */
export async function fetchCatalog(
  coords?: { lat: number; lng: number } | null,
): Promise<StorefrontCatalog> {
  const params = new URLSearchParams({ orgSlug: ORG_SLUG, storeCode: STORE_CODE })

  if (coords) {
    params.set('lat', String(coords.lat))
    params.set('lng', String(coords.lng))
  }

  const payload = await request<{
    categories: Category[]
    products: ApiProduct[]
    delivery?: CatalogDelivery
  }>(`/api/storefront/catalog?${params}`, { fallbackError: 'Could not load the menu.' })

  return {
    categories: payload.categories ?? [],
    products: (payload.products ?? []).map(toProduct),
    // An older deployment answers without the block; treat that as unfiltered
    // rather than as "nothing delivers here".
    delivery: payload.delivery ?? unfilteredDelivery,
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

/**
 * `storeCode` overrides the storefront's home branch, and checkout passes the
 * branch the cart's items are actually coming from: with a delivery address
 * set, the catalog can be served by a nearer branch than the one this
 * storefront is bound to, and the order has to be placed against the counter
 * that holds the stock.
 */
export function createOnlineOrder(
  items: CreateOnlineOrderItem[],
  guest: CreateOnlineOrderGuest,
  fulfillment: CreateOnlineOrderFulfillment,
  paymentMethod?: 'cash' | 'ewallet',
  storeCode?: string,
): Promise<CreateOnlineOrderResult> {
  return postJson<CreateOnlineOrderResult>(
    '/api/online-orders',
    {
      orgSlug: ORG_SLUG,
      storeCode: storeCode || STORE_CODE,
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
  /** Null until somebody says the cash changed hands. */
  paymentConfirmedAt: string | null
  paymentConfirmedBy: 'seller' | 'customer' | null
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

/**
 * The customer's own word that they have paid.
 *
 * Cash on arrival with no gateway means somebody has to say so, and for a
 * delivery the customer is the only side present when it happens — the cash
 * goes to a rider at their door and the till never sees it. The seller can
 * still settle it from the dashboard; whichever comes first wins, and the
 * order records which side it was.
 */
export function confirmOrderPayment(orderId: string): Promise<TrackedOrder> {
  return postJson<TrackedOrder>(
    `/api/online-orders/${encodeURIComponent(orderId)}/confirm-payment`,
    {},
    "Couldn't record that just now. Please try again.",
  )
}

// -- Customer accounts -----------------------------------------------------
//
// The storefront's first real identity: /api/customer/* behind Laravel's
// `customer` guard, which is a different guard and a different table from the
// staff one. Every mutation replies with the whole account rather than the row
// that changed — saving an address can move the default off another one, and a
// partial reply would leave the caller patching up a list it can't see.

export type SubstitutionPreference = 'call' | 'best-match' | 'refund'

export interface CustomerPreferences {
  emailUpdates: boolean
  smsUpdates: boolean
  marketingEmails: boolean
  substitutions: SubstitutionPreference
}

export interface CustomerAddress {
  id: string
  label: string
  line1: string
  barangay: string
  city: string
  notes: string
  lat: number | null
  lng: number | null
  isDefault: boolean
}

export type CustomerPaymentKind = 'cash' | 'ewallet'

export interface CustomerPaymentMethod {
  id: string
  kind: CustomerPaymentKind
  detail: string
  isDefault: boolean
}

export interface CustomerAccount {
  id: string
  name: string
  email: string
  phone: string
  preferences: CustomerPreferences
  addresses: CustomerAddress[]
  paymentMethods: CustomerPaymentMethod[]
}

interface AccountEnvelope {
  account: CustomerAccount
}

interface SessionEnvelope extends AccountEnvelope {
  token: string
}

interface VerificationEnvelope extends AccountEnvelope {
  verificationRequired: boolean
  message: string
}

function patchJson<TResult>(path: string, body: unknown, fallbackError?: string): Promise<TResult> {
  return request<TResult>(path, { method: 'PATCH', body: JSON.stringify(body), fallbackError })
}

function deleteJson<TResult>(path: string, fallbackError?: string): Promise<TResult> {
  return request<TResult>(path, { method: 'DELETE', fallbackError })
}

export function registerCustomer(input: {
  name: string
  email: string
  phone?: string
  password: string
}): Promise<VerificationEnvelope> {
  return postJson<VerificationEnvelope>(
    '/api/customer/register',
    { ...input, password_confirmation: input.password },
    'Could not create your account.',
  )
}

export function loginCustomer(email: string, password: string): Promise<SessionEnvelope> {
  return postJson<SessionEnvelope>('/api/customer/login', { email, password }, 'Could not sign you in.')
}

export function logoutCustomer(): Promise<{ signedOut: boolean }> {
  return postJson<{ signedOut: boolean }>('/api/customer/logout', {}, 'Could not sign you out.')
}

export function fetchCustomerAccount(): Promise<AccountEnvelope> {
  return request<AccountEnvelope>('/api/customer/me', { fallbackError: 'Could not load your account.' })
}

export function updateCustomerAccount(patch: {
  name?: string
  phone?: string
  preferences?: Partial<CustomerPreferences>
}): Promise<AccountEnvelope> {
  return patchJson<AccountEnvelope>('/api/customer/account', patch, 'Could not save your details.')
}

export function updateCustomerEmail(email: string, currentPassword: string): Promise<AccountEnvelope> {
  return patchJson<AccountEnvelope>(
    '/api/customer/account/email',
    { email, currentPassword },
    'Could not change your email.',
  )
}

export function updateCustomerPassword(
  currentPassword: string,
  password: string,
): Promise<AccountEnvelope> {
  return patchJson<AccountEnvelope>(
    '/api/customer/account/password',
    { currentPassword, password, password_confirmation: password },
    'Could not change your password.',
  )
}

/** Always resolves, whether or not the address has an account — by design. */
export function requestPasswordReset(email: string): Promise<{ message: string }> {
  return postJson<{ message: string }>(
    '/api/customer/forgot-password',
    { email },
    'Could not send a reset link.',
  )
}

export function resetCustomerPassword(input: {
  token: string
  email: string
  password: string
}): Promise<SessionEnvelope> {
  return postJson<SessionEnvelope>(
    '/api/customer/reset-password',
    { ...input, password_confirmation: input.password },
    'Could not reset your password.',
  )
}

export type CustomerAddressInput = Omit<CustomerAddress, 'id' | 'lat' | 'lng' | 'isDefault'> &
  Partial<Pick<CustomerAddress, 'lat' | 'lng' | 'isDefault'>>

export function createCustomerAddress(input: CustomerAddressInput): Promise<AccountEnvelope> {
  return postJson<AccountEnvelope>('/api/customer/addresses', input, 'Could not save that address.')
}

export function updateCustomerAddress(
  id: string,
  patch: Partial<CustomerAddressInput>,
): Promise<AccountEnvelope> {
  return patchJson<AccountEnvelope>(
    `/api/customer/addresses/${encodeURIComponent(id)}`,
    patch,
    'Could not update that address.',
  )
}

export function deleteCustomerAddress(id: string): Promise<AccountEnvelope> {
  return deleteJson<AccountEnvelope>(
    `/api/customer/addresses/${encodeURIComponent(id)}`,
    'Could not remove that address.',
  )
}

export function createCustomerPaymentMethod(input: {
  kind: CustomerPaymentKind
  detail?: string
}): Promise<AccountEnvelope> {
  return postJson<AccountEnvelope>(
    '/api/customer/payment-methods',
    input,
    'Could not save that payment method.',
  )
}

export function updateCustomerPaymentMethod(
  id: string,
  patch: { detail?: string; isDefault?: boolean },
): Promise<AccountEnvelope> {
  return patchJson<AccountEnvelope>(
    `/api/customer/payment-methods/${encodeURIComponent(id)}`,
    patch,
    'Could not update that payment method.',
  )
}

export function deleteCustomerPaymentMethod(id: string): Promise<AccountEnvelope> {
  return deleteJson<AccountEnvelope>(
    `/api/customer/payment-methods/${encodeURIComponent(id)}`,
    'Could not remove that payment method.',
  )
}

/** Orders placed while signed in. Guest orders are not in here — see the API. */
export function fetchCustomerOrders(): Promise<{ orders: TrackedOrder[] }> {
  return request<{ orders: TrackedOrder[] }>('/api/customer/orders', {
    fallbackError: 'Could not load your orders.',
  })
}
