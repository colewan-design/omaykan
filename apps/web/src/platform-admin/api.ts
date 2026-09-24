/*
 * Every call the operator portal makes, in one place.
 *
 * ## Why this file exists at all
 *
 * The portal used to call `/api/platform/...` from a dozen `fetch`es spread
 * across four components. The backend has always served `/api/platform-admin/...`
 * — Laravel's own routes/api.php has said so since the operator portal was
 * written — so *every one of those calls 404'd*, and the sign-in screen failed
 * with "The route api/platform/login could not be found" in production. One
 * wrong prefix in one place would have been a typo; the same wrong prefix in
 * twelve places is what happens when there is no seam to put it behind.
 *
 * This is that seam. The prefix appears once, on the line below, and nothing
 * else in the portal writes a URL.
 */

const BASE = '/api/platform-admin'

/**
 * The operator's token, kept for the tab rather than the browser.
 *
 * sessionStorage, not localStorage: this token authorises cross-tenant reads
 * of every shop and shopper on the platform, and it should not outlive the
 * window it was minted in. Closing the tab signs out.
 */
const TOKEN_KEY = 'platform_admin_token'

let token = ''

export function loadStoredToken(): string {
  try {
    token = window.sessionStorage.getItem(TOKEN_KEY) ?? ''
  } catch {
    // Private windows and blocked storage: the session lasts the page load.
    token = ''
  }
  return token
}

export function setToken(next: string) {
  token = next
  try {
    if (next) window.sessionStorage.setItem(TOKEN_KEY, next)
    else window.sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // As above — an unstorable token still works for this page.
  }
}

export function currentToken(): string {
  return token
}

export class ApiError extends Error {
  /**
   * 401 is the one status the shell reacts to structurally: the token has
   * expired or been revoked, and the portal has to fall back to the sign-in
   * screen rather than show an error on a page with no data behind it.
   *
   * Declared as a field and assigned, rather than a constructor parameter
   * property: this build runs TypeScript's `erasableSyntaxOnly`, under which
   * `constructor(readonly status: number)` is not legal.
   */
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT'
  body?: unknown
  /** Query string values; null and '' are dropped rather than sent empty. */
  query?: Record<string, string | number | null | undefined>
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${BASE}${path}`, window.location.origin)

  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== null && value !== undefined && value !== '') {
      url.searchParams.set(key, String(value))
    }
  }

  const response = await fetch(url.toString(), {
    method: options.method ?? 'GET',
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  })

  const data = await response.json().catch(() => ({}))

  if (!response.ok) {
    // Laravel returns `errors` on a 422 and `message` on everything else. The
    // first field error is what the operator can act on; the generic message
    // is the fallback, and the status is the last resort so that an error
    // without a body still says something more than "failed".
    const firstFieldError = Object.values(
      (data as { errors?: Record<string, string[]> }).errors ?? {},
    )[0]?.[0]

    throw new ApiError(
      firstFieldError ?? (data as { message?: string }).message ?? `Request failed (${response.status}).`,
      response.status,
    )
  }

  return data as T
}

// ── Types ────────────────────────────────────────────────────────────────

export type Progress = 'processing' | 'shipped' | 'completed' | 'cancelled'

export interface Operator {
  id: string
  name: string
  email: string
  lastLoginAt: string | null
}

export interface Headline {
  salesCents: number
  salesChangePercent: number | null
  orders: number
  ordersChangePercent: number | null
  activeProducts: number
  activeSellers: number
  averageOrderValueCents: number
  averageOrderValueChangePercent: number | null
  customersServed: number
  customersServedChangePercent: number | null
}

export interface SeriesPoint {
  date: string
  salesCents: number
  orders: number
}

export interface RecentOrder {
  id: string
  ticketNumber: string
  customerName: string
  items: number
  totalCents: number
  progress: Progress
  createdAt: string | null
}

export interface Overview {
  window: { from: string; to: string; days: number }
  headline: Headline
  series: SeriesPoint[]
  byProgress: Record<Progress, number>
  recentOrders: RecentOrder[]
}

export interface OrderRow extends RecentOrder {
  storeName: string | null
  paymentStatus: string
  fulfillmentMethod: string | null
}

export interface Pagination {
  page: number
  perPage: number
  total: number
  lastPage: number
}

export interface ProductRow {
  id: string
  name: string
  sku: string | null
  categoryName: string | null
  sellerName: string | null
  priceCents: number
  compareAtPriceCents: number | null
  imageUrl: string | null
  unitLabel: string | null
  stockOnHand: number | null
  trackInventory: boolean
  lowStockThreshold: number | null
  isActive: boolean
}

export interface CategoryTab {
  id: string
  name: string
  products: number
}

export interface Breakdown {
  label: string
  value: number
}

export interface Analytics {
  window: { from: string; to: string; days: number }
  headline: Headline
  series: SeriesPoint[]
  salesByCategory: Breakdown[]
  ordersByLocation: Breakdown[]
  returningCustomers: { customers: number; total: number; percent: number }
  topProducts: Array<{
    id: string | null
    label: string
    imageUrl: string | null
    revenueCents: number
    quantity: number
  }>
  ordersByHour: Array<{ hour: number; orders: number }>
}

export type ReportRange = 'today' | '7' | '30' | '365' | 'all'

export interface ReportSnapshot {
  window: { from: string; to: string; range: ReportRange; days: number }
  headline: {
    grossSalesCents: number
    netSalesCents: number
    collectedCents: number
    taxCents: number
    discountCents: number
    orders: number
    averageOrderValueCents: number
    grossSalesChangePercent: number | null
    netSalesChangePercent: number | null
    taxChangePercent: number | null
    discountChangePercent: number | null
    ordersChangePercent: number | null
    averageOrderValueChangePercent: number | null
  }
  series: Array<{ date: string; salesCents: number }>
  paymentsByMethod: Breakdown[]
  salesByBusinessMode: Array<Breakdown & { orders: number }>
  topProducts: Analytics['topProducts']
  ordersByHour: Array<{ hour: number; orders: number }>
  recentTransactions: Array<{
    id: string
    ticketNumber: string
    storeName: string | null
    items: number
    totalCents: number
    paymentMethod: string
    createdAt: string | null
  }>
  sourceNote: string
}

export interface DeliverySettings {
  baseFeeCents: number
  freeDeliveryOverCents: number
  maxDistanceKm: number
}

export interface NotificationSettings {
  newOrder: boolean
  newSeller: boolean
  lowStock: boolean
  weeklySummary: boolean
}

export interface Settings {
  name: string
  tagline: string | null
  description: string | null
  website: string | null
  address: string | null
  contactEmail: string | null
  contactPhone: string | null
  delivery: DeliverySettings
  notifications: NotificationSettings
  /** What a new merchant's subscription is recorded at. Only the amount is editable. */
  plan: { id: string; amountCents: number }
  /** BILLING_ENFORCE on the server. Read-only: a config flag, not a setting. */
  billingEnforced: boolean
  updatedAt: string | null
}

export interface SellerAdmin {
  uid: string
  username: string
  fullName: string
  email: string | null
  disabled: boolean
}

export interface SellerRow {
  organizationSlug: string
  organizationName: string
  createdAt: string | null
  suspended: boolean
  /**
   * What the till and storefront will actually do — the server's own verdict,
   * computed from `suspended` and the subscription by the same method they call.
   */
  tenantAccess: 'allowed' | 'suspended' | 'unpaid'
  store: {
    name: string
    businessMode: string
    businessTypeLabel?: string
    storeCode: string
    imageUrl: string | null
  } | null
  subscription: {
    status: string
    plan: string
    amountCents: number
    gcashReference: string
    submittedAt: string | null
    verifiedAt: string | null
    /** Early access, as a date. Null for a row the backfill has not reached. */
    trialEndsAt: string | null
    currentPeriodEndsAt: string | null
  } | null
  admins: SellerAdmin[]
}

/**
 * One transfer a merchant says they made — `SubscriptionPayment` on the server.
 *
 * `periodStart` / `periodEnd` are ours, not theirs: the merchant reports a
 * transfer, and an operator decides what it buys. Accepting is what calls
 * `SubscriptionBilling::recordPayment()` with a date.
 */
export interface SubscriptionPaymentRow {
  id: string
  organizationSlug: string | null
  organizationName: string | null
  status: 'submitted' | 'accepted' | 'rejected'
  reference: string
  amountCents: number
  note: string | null
  submittedBy: string | null
  submittedAt: string | null
  periodStart: string | null
  periodEnd: string | null
  rejectionReason: string | null
}

export interface CustomerRow {
  id: string
  name: string
  email: string
  phone: string | null
  ordersCount: number
  addressesCount: number
  totalSpentCents: number
  lastOrderAt: string | null
  createdAt: string | null
}

// ── Calls ────────────────────────────────────────────────────────────────

export const api = {
  async signIn(email: string, password: string): Promise<{ token: string; admin: Operator }> {
    // The one call with no token to send: it is what mints one.
    const data = await request<{ token: string; admin: Operator }>('/login', {
      method: 'POST',
      body: { email, password },
    })
    setToken(data.token)
    return data
  },

  /**
   * Sign in with a Google ID token.
   *
   * Signs in only. The endpoint behind it creates nothing — an operator row is
   * made from the console with `platform-admin:create`, and a Google identity
   * with no row is refused — so this is a second door onto an existing account,
   * never a way to become one.
   */
  async signInWithGoogle(credential: string): Promise<{ token: string; admin: Operator }> {
    const data = await request<{ token: string; admin: Operator }>('/auth/google', {
      method: 'POST',
      body: { credential },
    })
    setToken(data.token)
    return data
  },

  me: () => request<{ admin: Operator }>('/me'),

  async signOut(): Promise<void> {
    try {
      await request('/logout', { method: 'POST' })
    } finally {
      // Whatever the server said, this browser is done with the token.
      setToken('')
    }
  },

  overview: (days?: number) => request<Overview>('/overview', { query: { days } }),

  orders: (query: { q?: string; progress?: Progress | ''; days?: number; page?: number }) =>
    request<{ orders: OrderRow[]; counts: Record<Progress, number>; pagination: Pagination }>('/orders', { query }),

  products: (query: { q?: string; categoryId?: string; page?: number }) =>
    request<{ products: ProductRow[]; categories: CategoryTab[]; pagination: Pagination }>('/products', { query }),

  analytics: (days: number) => request<Analytics>('/analytics', { query: { days } }),

  report: (range: ReportRange) => request<ReportSnapshot>('/reports', { query: { range } }),

  settings: () => request<{ settings: Settings }>('/settings'),

  saveSettings: (patch: Partial<Record<string, unknown>>) =>
    request<{ settings: Settings }>('/settings', { method: 'PUT', body: patch }),

  customers: (query: { q?: string; page?: number }) =>
    request<{ customers: CustomerRow[]; pagination: Pagination }>('/customers', { query }),

  /**
   * Sellers are the one screen behind the action-dispatch endpoint rather than
   * a REST path: `POST /api/platform-admin` with an `action`, which is how the
   * operator's write tools were built. Listing goes through the same door.
   */
  sellers: () => request<{ organizations: SellerRow[] }>('', { method: 'POST', body: { action: 'listOrgs' } }),

  sellerAction: (action: string, payload: Record<string, unknown> = {}) =>
    request<Record<string, unknown>>('', { method: 'POST', body: { action, ...payload } }),

  /**
   * The manual transfers merchants say they have made, pending first.
   *
   * This is the whole of billing: there is no gateway, so accepting one of
   * these is the only thing that ever sets a subscription's billing period.
   */
  subscriptionPayments: (organizationSlug?: string) =>
    request<{ payments: SubscriptionPaymentRow[] }>('', {
      method: 'POST',
      body: { action: 'listPayments', ...(organizationSlug ? { organizationSlug } : {}) },
    }),
}
