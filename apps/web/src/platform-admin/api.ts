import { platformToken, setPlatformToken } from '@pos/web/platform-admin/session'

/*
 * The operator portal's entire backend.
 *
 * Separate from rider/api.ts and commerce/api.ts rather than a section inside
 * either: those attach a rider's and a shopper's token, and this one is a
 * fourth guard whose token must never travel to their routes. Sharing a
 * request path is how a token ends up somewhere it is rejected — or worse,
 * somewhere it is not.
 *
 * Empty base by default so the web deployment — served from the same origin as
 * /api — uses relative paths, matching the other two. Every request carries
 * Accept: application/json, including the document blob fetch: Laravel's
 * Authenticate middleware redirects a non-JSON unauthenticated request to a
 * `login` route this API does not have, which turns a 401 into a 500.
 */
const API_BASE = (import.meta.env.VITE_ONLINE_ORDER_API_BASE ?? '').replace(/\/$/, '')

/** Laravel's `{message, errors: {field: [msg]}}`, kept per field. */
export type FieldErrors = Record<string, string>

/**
 * Carries the status and the per-field errors.
 *
 * `adminStatus` carries the `platform.active` middleware's 403 body, so the
 * shell can tell a disabled operator what happened rather than looping them
 * back to a sign-in form that will refuse them too.
 */
export class PlatformApiError extends Error {
  // Declared rather than constructor parameter properties: the build runs
  // TypeScript with erasableSyntaxOnly, which rejects that shorthand.
  readonly status: number

  readonly fields: FieldErrors

  readonly adminStatus: string | null

  constructor(message: string, status: number, fields: FieldErrors = {}, adminStatus: string | null = null) {
    super(message)
    this.name = 'PlatformApiError'
    this.status = status
    this.fields = fields
    this.adminStatus = adminStatus
  }
}

function fieldErrorsFrom(payload: unknown): FieldErrors {
  if (!payload || typeof payload !== 'object') return {}

  const errors = (payload as { errors?: Record<string, unknown> }).errors
  if (!errors || typeof errors !== 'object') return {}

  const fields: FieldErrors = {}

  for (const [field, messages] of Object.entries(errors)) {
    const first = (Array.isArray(messages) ? messages : [messages]).find(
      (message): message is string => typeof message === 'string' && message.length > 0,
    )
    if (first) fields[field] = first
  }

  return fields
}

function messageFrom(payload: unknown, fields: FieldErrors, fallback: string): string {
  const body = (payload ?? {}) as { message?: unknown }

  // The field-level message first: it is the specific one.
  const firstField = Object.values(fields)[0]
  if (firstField) return firstField
  if (typeof body.message === 'string' && body.message) return body.message

  return fallback
}

/** "Too Many Attempts." turned into something with a number in it. */
function retryMessage(retryAfter: string | null): string {
  const seconds = Number(retryAfter)

  if (!Number.isFinite(seconds) || seconds <= 0) {
    return 'Too many tries just now. Give it a minute and try again.'
  }

  const minutes = Math.ceil(seconds / 60)

  return seconds < 60
    ? `Too many tries just now. Try again in ${Math.ceil(seconds)} seconds.`
    : `Too many tries just now. Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`
}

/**
 * Called whenever the API says the session is gone. Set by the shell, so the
 * transport can drop a dead token without importing the router.
 */
let onSessionLost: (() => void) | null = null

export function setSessionLostHandler(handler: (() => void) | null): void {
  onSessionLost = handler
}

async function raw(path: string, init: RequestInit = {}): Promise<Response> {
  const token = platformToken()

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })

  // A 401 anywhere means the token is dead — revoked from the operators screen,
  // signed out elsewhere, or simply old. Dropping it here rather than in each
  // caller means no screen can be left holding a token that cannot work.
  if (response.status === 401) {
    setPlatformToken(null)
    onSessionLost?.()
  }

  return response
}

async function request<TResult>(
  path: string,
  init: RequestInit & { fallbackError?: string } = {},
): Promise<TResult> {
  const { fallbackError = 'Something went wrong. Please try again.', ...rest } = init

  const response = await raw(path, rest)
  const data = await response.json().catch(() => null)

  if (!response.ok) {
    if (response.status === 429) {
      throw new PlatformApiError(retryMessage(response.headers.get('Retry-After')), 429)
    }

    const fields = fieldErrorsFrom(data)
    const body = (data ?? {}) as { adminStatus?: unknown }

    throw new PlatformApiError(
      messageFrom(data, fields, fallbackError),
      response.status,
      fields,
      typeof body.adminStatus === 'string' ? body.adminStatus : null,
    )
  }

  return data as TResult
}

function postJson<TResult>(path: string, body: unknown = {}, fallbackError?: string): Promise<TResult> {
  return request<TResult>(path, { method: 'POST', body: JSON.stringify(body), fallbackError })
}

function query(params: Record<string, string | number | undefined> | object): string {
  const search = new URLSearchParams()

  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }

  const qs = search.toString()

  return qs ? `?${qs}` : ''
}

// -- The operator ----------------------------------------------------------

export type AdminRole = 'owner' | 'operator'

export interface PlatformAdminProfile {
  id: string
  name: string
  email: string
  role: AdminRole
  status: 'active' | 'disabled'
  lastLoginAt: string | null
  createdAt: string | null
}

export function signIn(email: string, password: string): Promise<{ admin: PlatformAdminProfile; token: string }> {
  return postJson('/api/platform/login', { email, password }, 'Could not sign you in.')
}

export function fetchMe(): Promise<{ admin: PlatformAdminProfile }> {
  return request('/api/platform/me', { fallbackError: 'Could not load your account.' })
}

export function signOut(): Promise<{ signedOut: boolean }> {
  return postJson('/api/platform/logout')
}

// -- Overview --------------------------------------------------------------

export interface OverviewQueueRow {
  organizationSlug: string
  organizationName: string
  amountCents: number
  gcashReference: string
  submittedAt: string | null
  waitingDays: number | null
}

export interface OverviewVolumeDay {
  date: string
  orders: number
  revenueCents: number
  pos: number
  online: number
}

export interface Overview {
  queues: {
    pendingSignups: number
    pendingRiders: number
    oldestSignups: OverviewQueueRow[]
  }
  tenants: { total: number; suspended: number; newLast7Days: number; newLast30Days: number }
  revenue: { activeSubscriptions: number; monthlyCents: number; rejected: number }
  volume: {
    days: number
    orders: number
    revenueCents: number
    posOrders: number
    onlineOrders: number
    byDay: OverviewVolumeDay[]
  }
  topTenants: { organizationSlug: string; organizationName: string; orders: number; revenueCents: number }[]
}

export function fetchOverview(): Promise<Overview> {
  return request('/api/platform/overview', { fallbackError: 'Could not load the dashboard.' })
}

// -- Tenants ---------------------------------------------------------------

export type SubscriptionStatus = 'pending_verification' | 'active' | 'rejected'

export interface TenantOwner {
  uid: string
  username: string
  fullName: string
  disabled: boolean
}

export interface TenantRow {
  organizationSlug: string
  organizationName: string
  suspended: boolean
  store: { name: string; businessMode: string; pairingCode: string } | null
  subscription: {
    status: SubscriptionStatus
    plan: string
    amountCents: number
    gcashReference: string
    submittedAt: string | null
    verifiedAt: string | null
  } | null
  admins: TenantOwner[]
}

export interface TenantDetail extends TenantRow {
  createdAt: string | null
  rejectionReason: string | null
  staffCount: number
  stores: {
    id: string
    name: string
    code: string
    businessMode: string | null
    pairingCode: string | null
    address: string | null
  }[]
  last30Days: {
    orders: number
    revenueCents: number
    byChannel: Record<string, { orders: number; revenueCents: number }>
  }
}

export interface TenantFilters {
  q?: string
  state?: 'active' | 'suspended'
  subscription?: SubscriptionStatus | 'none'
  page?: number
}

export function fetchTenants(filters: TenantFilters = {}): Promise<{
  organizations: TenantRow[]
  page: number
  lastPage: number
  total: number
}> {
  return request(`/api/platform/organizations${query(filters)}`, {
    fallbackError: 'Could not load the tenants.',
  })
}

export function fetchTenant(slug: string): Promise<{ organization: TenantDetail; auditTrail: AuditLogRow[] }> {
  return request(`/api/platform/organizations/${encodeURIComponent(slug)}`, {
    fallbackError: 'Could not load that tenant.',
  })
}

export function setSubscriptionStatus(
  slug: string,
  status: 'active' | 'rejected',
  reason?: string,
): Promise<{ status: SubscriptionStatus }> {
  return postJson(
    `/api/platform/organizations/${encodeURIComponent(slug)}/subscription`,
    { status, reason },
    'Could not update that subscription.',
  )
}

export function setTenantSuspended(
  slug: string,
  suspended: boolean,
  reason?: string,
): Promise<{ suspended: boolean }> {
  return postJson(
    `/api/platform/organizations/${encodeURIComponent(slug)}/suspension`,
    { suspended, reason },
    'Could not update that tenant.',
  )
}

export function resetOwnerPassword(slug: string, uid: string): Promise<{ password: string }> {
  return postJson(
    `/api/platform/organizations/${encodeURIComponent(slug)}/owners/${encodeURIComponent(uid)}/password-reset`,
    {},
    'Could not reset that password.',
  )
}

export function setOwnerDisabled(slug: string, uid: string, disabled: boolean): Promise<{ disabled: boolean }> {
  return postJson(
    `/api/platform/organizations/${encodeURIComponent(slug)}/owners/${encodeURIComponent(uid)}/status`,
    { disabled },
    'Could not update that login.',
  )
}

export function deleteTenant(slug: string, confirmSlug: string): Promise<{ deleted: boolean }> {
  return request(`/api/platform/organizations/${encodeURIComponent(slug)}`, {
    method: 'DELETE',
    body: JSON.stringify({ confirmSlug }),
    fallbackError: 'Could not delete that tenant.',
  })
}

// -- Riders ----------------------------------------------------------------

export const RIDER_STATUSES = ['pending', 'approved', 'rejected', 'suspended'] as const

export type RiderStatus = (typeof RIDER_STATUSES)[number]

export interface ReviewRider {
  id: string
  name: string
  email: string
  phone: string
  licenseNumber: string
  plateNumber: string
  status: RiderStatus
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string | null
  deliveriesCompleted: number
  lastSeenAt: string | null
}

export function fetchRiders(status?: RiderStatus): Promise<{ riders: ReviewRider[] }> {
  return request(`/api/platform/riders${query({ status })}`, {
    fallbackError: 'Could not load the rider queue.',
  })
}

export function decideRider(
  id: string,
  status: 'approved' | 'rejected' | 'suspended',
  note?: string,
): Promise<{ rider: ReviewRider }> {
  return postJson(
    `/api/platform/riders/${encodeURIComponent(id)}/decision`,
    { status, note },
    'Could not record that decision.',
  )
}

/**
 * A document image, as an object URL.
 *
 * The images live on the private disk and have no URL of their own — the only
 * way to see one is an authenticated request, which an `<img src>` cannot
 * make. So each is fetched as a blob and shown from an object URL, which the
 * caller has to revoke by hand. Nothing here ever lands in a browser or proxy
 * cache: the endpoint answers `no-store`.
 */
export async function fetchRiderDocument(id: string, document: 'license' | 'plate'): Promise<string> {
  const response = await raw(
    `/api/platform/riders/${encodeURIComponent(id)}/document/${document}`,
  )

  if (!response.ok) {
    throw new PlatformApiError('Could not load that document.', response.status)
  }

  return URL.createObjectURL(await response.blob())
}

// -- Audit log -------------------------------------------------------------

export interface AuditLogRow {
  id: string
  actorEmail: string
  actorName: string | null
  action: string
  subjectType: string | null
  subjectId: string | null
  context: Record<string, unknown>
  ipAddress: string | null
  createdAt: string | null
}

export interface AuditFilters {
  actor?: string
  action?: string
  subject?: string
  from?: string
  to?: string
  page?: number
}

export function fetchAuditLogs(filters: AuditFilters = {}): Promise<{
  logs: AuditLogRow[]
  page: number
  lastPage: number
  total: number
  actors: string[]
  actions: string[]
}> {
  return request(`/api/platform/audit-logs${query(filters)}`, {
    fallbackError: 'Could not load the audit log.',
  })
}

// -- Operators -------------------------------------------------------------

export function fetchAdmins(): Promise<{ admins: PlatformAdminProfile[] }> {
  return request('/api/platform/admins', { fallbackError: 'Could not load the operators.' })
}

export function createAdmin(input: {
  name: string
  email: string
  role: AdminRole
}): Promise<{ admin: PlatformAdminProfile; password: string }> {
  return postJson('/api/platform/admins', input, 'Could not create that operator.')
}

export function setAdminStatus(
  id: string,
  status: 'active' | 'disabled',
): Promise<{ admin: PlatformAdminProfile }> {
  return postJson(
    `/api/platform/admins/${encodeURIComponent(id)}/status`,
    { status },
    'Could not update that operator.',
  )
}
