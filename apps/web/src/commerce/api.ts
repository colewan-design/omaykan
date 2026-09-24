import type { Category, Product } from '@pos/shared/index'
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
    const tenantAccess = data && typeof data === 'object' && typeof (data as { tenantAccess?: unknown }).tenantAccess === 'string'
      ? (data as { tenantAccess: string }).tenantAccess
      : null

    const fields = data && typeof data === 'object' && (data as { errors?: unknown }).errors
      && typeof (data as { errors?: unknown }).errors === 'object'
      ? Object.keys((data as { errors: Record<string, unknown> }).errors)
      : []

    throw new ApiRequestError(errorMessageFrom(data, fallbackError), response.status, tenantAccess, fields)
  }

  return data as TResult
}

/** Carries the status so callers can tell "gone" from "broken". */
export class ApiRequestError extends Error {
  // Declared rather than a constructor parameter property: the build runs
  // TypeScript with erasableSyntaxOnly, which rejects that shorthand.
  readonly status: number
  /**
   * `closed` when the shop exists but may not trade — suspended, or its
   * subscription lapsed. The server does not say which: to a shopper the shop
   * is simply closed, and why is between the merchant and Omaykan.
   */
  readonly tenantAccess: string | null
  /** The fields a 422 named — `promoCode`, `fulfillment.address` — so a caller can say it beside the right one. */
  readonly fields: string[]

  constructor(message: string, status: number, tenantAccess: string | null = null, fields: string[] = []) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.tenantAccess = tenantAccess
    this.fields = fields
  }

  /** The shop is there and not taking orders — as against not found, or broken. */
  get shopClosed(): boolean {
    return this.tenantAccess === 'closed'
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
  photoUrls: string[] | null
  brand: string | null
  packagingType: string | null
  unitLabel: string | null
  description?: string | null
  businessModes: string[]
  outOfStock: boolean
  stockQty: number | null
  lowStockThreshold: number | null
}

interface ApiShop {
  name: string
  businessTypeLabel: string | null
  ownerName: string | null
  address: string | null
  imageUrl: string | null
  ordering?: { paused: boolean; resumesAt: string | null; message: string | null }
}

/**
 * The shop behind the shelf: who the shopper is buying from, and where they
 * are. One per storefront — every product on it comes from the same counter.
 */
export interface StorefrontShop {
  name: string
  /** "Sari-sari store", "Coffee shop" — what kind of counter it is. */
  businessTypeLabel?: string
  /** The person whose name is over the door. Absent if the org has no admin. */
  ownerName?: string
  /** Street line, as the owner typed it into Settings. */
  address?: string
  /**
   * The shop's own photo, if its owner uploaded one in Settings. Not a picture
   * of anything on the shelf — the listing only ever uses it dimmed, behind
   * the mark on a product that has no photo of its own.
   */
  imageUrl?: string
  /**
   * Set while the shop has paused its own online ordering — "This shop isn't
   * taking orders right now — back at 4:00 PM." The menu still shows; checkout
   * says this instead of taking an order the server will refuse.
   */
  orderingPausedMessage?: string
}

export interface StorefrontCatalog {
  /** Null for the bundled demo shelf, which stands for no real shop. */
  shop: StorefrontShop | null
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
    // Older API builds send no gallery at all; an absent one is one photo.
    photoUrls: (product.photoUrls ?? []).filter((url) => typeof url === 'string' && url.trim() !== ''),
    brand: product.brand?.trim() || undefined,
    packagingType: product.packagingType?.trim() || undefined,
    unitLabel: product.unitLabel ?? undefined,
    description: product.description?.trim() || undefined,
    businessModes: (product.businessModes ?? []) as Product['businessModes'],
    outOfStock: product.outOfStock,
    stockQty: product.stockQty ?? undefined,
    lowStockThreshold: product.lowStockThreshold ?? undefined,
  }
}

/** A shop as the directory lists it — never its pairing code. */
export interface StoreSummary {
  orgSlug: string
  storeCode: string
  name: string
  businessMode: string
  businessTypeLabel: string | null
  address: string
  /**
   * A photo off the shop's own shelf — stores have no picture of their own in
   * the schema, so the directory borrows one of their products'. Null for a
   * shop that has listed nothing with an image.
   */
  imageUrl: string | null
  lat: number | null
  lng: number | null
  productCount: number
  /** The busiest few aisles this shop has stocked, in its own category names. */
  categories: string[]
  /** Opened on Omaykan recently; the window is the API's to decide. */
  isNew: boolean
  /** Null when either side has no pin, so "no distance" is not "0 km away". */
  distanceKm: number | null
  /** The shop has paused its own online ordering. Listed, badged, sorted last. */
  orderingPaused: boolean
  /** When the pause ends by itself; null while open, or paused until reopened. */
  orderingResumesAt: string | null
}

/**
 * The shops a customer can order from.
 *
 * Unlike fetchCatalog, this is not scoped to the storefront's own tenant — it
 * is what the visitor picks that tenant from, so it runs before any store
 * context exists.
 */
export async function fetchStores(
  options: { q?: string; lat?: number | null; lng?: number | null } = {},
): Promise<StoreSummary[]> {
  const params = new URLSearchParams()
  if (options.q) params.set('q', options.q)
  // Sent only as a pair; the API rejects half a coordinate rather than
  // quietly sorting by name.
  if (typeof options.lat === 'number' && typeof options.lng === 'number') {
    params.set('lat', String(options.lat))
    params.set('lng', String(options.lng))
  }

  const query = params.toString()
  const payload = await request<{ stores: StoreSummary[] }>(
    `/api/stores${query === '' ? '' : `?${query}`}`,
    { fallbackError: 'Could not load the shops.' },
  )

  return (payload.stores ?? []).map((store) => ({
    ...store,
    categories: store.categories ?? [],
    isNew: store.isNew ?? false,
    orderingPaused: store.orderingPaused ?? false,
    orderingResumesAt: store.orderingResumesAt ?? null,
    imageUrl: resolveImageUrl(store.imageUrl),
  }))
}

/**
 * A shop photo the API serves itself lives on the API's origin; one it merely
 * names — a product photo, a static file — lives on this page's. Only the
 * first kind needs the configured base put back in front of it, and the only
 * deployment where the difference shows is the one where the two origins are
 * not the same. Which is the mobile app.
 */
export function resolveImageUrl(url: string | null): string | null {
  if (url === null || API_BASE === '') return url
  return url.startsWith('/api/') ? `${API_BASE}${url}` : url
}

/** Blank strings are as absent as nulls here — both mean "never filled in". */
function toShop(shop: ApiShop | null | undefined): StorefrontShop | null {
  if (!shop) return null
  return {
    name: shop.name,
    businessTypeLabel: shop.businessTypeLabel?.trim() || undefined,
    ownerName: shop.ownerName?.trim() || undefined,
    address: shop.address?.trim() || undefined,
    // The API serves this one itself, so it needs the base put back in front
    // of it on the deployment where the page and the API are not same-origin.
    imageUrl: resolveImageUrl(shop.imageUrl ?? null) ?? undefined,
    orderingPausedMessage: shop.ordering?.paused ? (shop.ordering.message ?? undefined) : undefined,
  }
}

export async function fetchCatalog(): Promise<StorefrontCatalog> {
  const params = new URLSearchParams({ orgSlug: ORG_SLUG, storeCode: STORE_CODE })

  const payload = await request<{ store?: ApiShop | null; categories: Category[]; products: ApiProduct[] }>(
    `/api/storefront/catalog?${params}`,
    { fallbackError: 'Could not load the menu.' },
  )

  return {
    shop: toShop(payload.store),
    categories: payload.categories ?? [],
    products: (payload.products ?? []).map(toProduct),
  }
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
   * How the rider finds the door — "green gate beside the sari-sari store".
   * Optional: an address that needs no landmark should not be made to invent
   * one, and an order never fails for the want of it.
   */
  landmark?: string
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
  promoCode?: string | null,
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
      promoCode: promoCode || undefined,
    },
    'Could not place your order.',
  )
}

/** `promo` is null when no code was sent. */
export interface OnlineOrderQuote {
  subtotalCents: number
  discountCents: number
  taxCents: number
  deliveryFeeCents: number
  totalCents: number
  promo:
    | { ok: true; code: string; description: string }
    | { ok: false; code: string; message: string }
    | null
}

/**
 * What this basket would cost, before ordering — the server's own arithmetic,
 * promo code included. A code that does not apply comes back as
 * `promo.ok === false` with the reason, not as an error.
 */
export function quoteOnlineOrder(
  items: CreateOnlineOrderItem[],
  options: { promoCode?: string | null; phone?: string | null; fulfillment?: CreateOnlineOrderFulfillment } = {},
): Promise<OnlineOrderQuote> {
  return postJson<OnlineOrderQuote>(
    '/api/online-orders/quote',
    {
      orgSlug: ORG_SLUG,
      storeCode: STORE_CODE,
      businessMode: BUSINESS_MODE,
      items,
      promoCode: options.promoCode || undefined,
      guest: options.phone ? { phone: options.phone } : undefined,
      fulfillment: options.fulfillment,
    },
    'Could not check that code.',
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

/** The last fix from the rider's phone, as the API gates it. */
export interface RiderPosition {
  lat: number
  lng: number
  headingDeg: number | null
  speedKph: number | null
  accuracyM: number | null
  /** ISO timestamp of the fix itself, not of the response. */
  at: string
  ageSeconds: number
  /** True once the fix is too old to draw as a live position. */
  stale: boolean
}

/** The two fixed ends of a delivery: the shop, and the door. */
export interface OrderRoute {
  pickup: { name: string | null; address: string | null; lat: number | null; lng: number | null }
  dropoff: { address: string | null; lat: number | null; lng: number | null }
}

/** What the two parties to a delivery are told about the rider carrying it. */
export interface RiderProfile {
  id: string
  name: string
  /**
   * Server-relative — `/api/riders/{id}/avatar?v=…` — or null for a rider who
   * has not uploaded one. The `v` changes when the photo does, so a replaced
   * photo is fetched rather than served from cache.
   */
  photoUrl: string | null
  vehicle: RiderVehicle
  rating: RiderRatingSummary
}

export interface RiderVehicle {
  type: string
  make: string | null
  model: string | null
  color: string | null
  /** The server's sentence — "red Honda Click". Colour first: it reads furthest. */
  label: string
  plateNumber: string
}

export interface RiderRatingSummary {
  /**
   * Null, *not* zero, for a rider nobody has rated. Rendering an unrated rider
   * as 0.0 out of 5 would put the worst possible number on the screen of the
   * person least able to have earned it.
   */
  average: number | null
  count: number
}

/** Leave a score for the rider who brought this order. One per delivery. */
export function rateRider(
  orderId: string,
  score: number,
  comment?: string,
): Promise<{ rating: { score: number; comment: string | null } }> {
  return postJson(
    `/api/customer/orders/${encodeURIComponent(orderId)}/rider-rating`,
    { score, comment: comment?.trim() || null },
    'Could not save that rating.',
  )
}

export interface TrackedOrder {
  orderId: string
  /** The shop it was ordered from — what "Message the shop" opens a thread with. */
  storeId: string
  ticketNumber: string
  status: string
  paymentStatus: string
  paymentMethod: string | null
  subtotalCents: number
  /** What a promo code took off. Absent from servers older than discounts. */
  discountCents?: number
  /** "Promo WELCOME10" — the line a receipt prints. Null when nothing came off. */
  discountLabel?: string | null
  taxCents: number
  deliveryFeeCents: number
  totalCents: number
  fulfillmentMethod: 'pickup' | 'delivery' | null
  deliveryAddress: string | null
  deliveryStage: string | null
  riderName: string | null
  riderPhone: string | null
  /**
   * Null unless a *platform* rider is carrying this right now. A rider the shop
   * typed in has no account to report from, and any rider's position is
   * withheld once the order is handed over — see Order::riderPositionForCustomer.
   */
  riderPosition: RiderPosition | null
  /**
   * Who is bringing it: a face, a bike and a score.
   *
   * Behind the same stage gate as the phone number and the position, and for
   * the phone number's reason rather than the position's — this tracking view
   * is public by UUID and the link is meant to be forwarded, so a photograph
   * left in the payload after the handover is readable by everyone who was ever
   * sent it. See Order::riderProfileForCustomer.
   *
   * Null for an order a shop handed to somebody it knows: no account, nothing
   * to describe.
   */
  riderProfile: RiderProfile | null
  route: OrderRoute | null
  placedAt: string | null
  items: TrackedOrderItem[]
}

export function fetchOrder(orderId: string): Promise<TrackedOrder> {
  return request<TrackedOrder>(`/api/online-orders/${encodeURIComponent(orderId)}`, {
    fallbackError: 'Could not load that order.',
  })
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
  /** Google's portrait, when they signed in with it. Empty otherwise. */
  avatarUrl: string
  googleLinked: boolean
  /**
   * False for someone who has only ever pressed the Google button. The portal
   * asks such an account to *set* a password rather than to change one, and
   * the API agrees — see CustomerAccountController.
   */
  hasPassword: boolean
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

/**
 * Trades an ID token from the Google button for a session of ours.
 *
 * The credential is not a session and is not trusted by anything on this side
 * of the wire: the API checks Google's signature on it, that it was issued for
 * our client id, and that it has not expired, before it will say who anyone is.
 * The reply is the same envelope `loginCustomer` returns, so nothing after this
 * point can tell the two roads apart.
 */
export function signInWithGoogle(credential: string): Promise<SessionEnvelope> {
  return postJson<SessionEnvelope>(
    '/api/customer/auth/google',
    { credential },
    'Could not sign you in with Google.',
  )
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
// -- Messages --------------------------------------------------------------
//
// A signed-in shopper talking to a shop. One conversation per shop, not per
// order — a message can still name an order, which is how "about #0042" is
// carried. See CustomerConversationController.

export interface ConversationMessage {
  id: number
  from: 'customer' | 'store'
  body: string
  order: { id: string; ticketNumber: string } | null
  createdAt: string
}

export interface ConversationShop {
  id: string | null
  name: string
  orgSlug: string | null
  storeCode: string | null
  imageUrl: string | null
}

export interface ConversationSummary {
  id: string
  store: ConversationShop
  lastMessage: ConversationMessage | null
  lastMessageAt: string | null
  unreadCount: number
}

export interface ConversationThread {
  conversation: ConversationSummary
  messages: ConversationMessage[]
}

export function fetchConversations(): Promise<{ conversations: ConversationSummary[] }> {
  return request('/api/customer/conversations', { fallbackError: 'Could not load your messages.' })
}

/** Reading a thread is what marks it read, server-side. */
export function fetchConversation(id: string): Promise<ConversationThread> {
  return request(`/api/customer/conversations/${encodeURIComponent(id)}`, {
    fallbackError: 'Could not open that conversation.',
  })
}

export function fetchUnreadMessageCount(): Promise<{ unread: number }> {
  return request('/api/customer/conversations/unread')
}

/**
 * The first message to a shop — or the next one, if a thread already exists;
 * the server lands it in the same conversation either way. The shop is named
 * by the order (`orderId`) or by the storefront's own slug and code.
 */
export function startConversation(input: {
  body: string
  orderId?: string | null
  orgSlug?: string
  storeCode?: string
}): Promise<ConversationThread> {
  return postJson('/api/customer/conversations', input, 'Could not send that message.')
}

export function sendConversationMessage(
  id: string,
  body: string,
  orderId?: string | null,
): Promise<ConversationThread> {
  return postJson(
    `/api/customer/conversations/${encodeURIComponent(id)}/messages`,
    { body, orderId: orderId ?? null },
    'Could not send that message.',
  )
}

export function fetchCustomerOrders(): Promise<{ orders: TrackedOrder[] }> {
  return request<{ orders: TrackedOrder[] }>('/api/customer/orders', {
    fallbackError: 'Could not load your orders.',
  })
}
