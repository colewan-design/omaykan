import { riderToken } from '@pos/web/rider/session'

/*
 * The rider portal's entire backend.
 *
 * Separate from commerce/api.ts rather than a section inside it: that module
 * attaches the *customer* token to every call and is built around one store's
 * catalog. A rider is a different guard and works across every shop, so the
 * two share no request path — mixing them is how a rider token ends up on a
 * shopper route.
 *
 * Empty base by default so the web deployment — served from the same origin as
 * /api — uses relative paths, matching commerce/api.ts. A rider app served
 * from elsewhere sets VITE_ONLINE_ORDER_API_BASE to the full one.
 */
const API_BASE = (import.meta.env.VITE_ONLINE_ORDER_API_BASE ?? '').replace(/\/$/, '')

/** Laravel's `{message, errors: {field: [msg]}}`, kept whole. */
export type FieldErrors = Record<string, string>

/**
 * Carries the status and the per-field errors.
 *
 * The storefront's ApiRequestError flattens a 422 to its first message, which
 * is right for a two-field checkout and wrong here: registration submits eight
 * fields including two files, and "The plate image must be an image" has to
 * land under the plate image, not at the top of a form the rider then has to
 * re-read. `riderStatus` carries the approval gate's 403 body so the portal
 * can show a pending rider a different screen from a suspended one.
 */
export class RiderApiError extends Error {
  // Declared rather than constructor parameter properties: the build runs
  // TypeScript with erasableSyntaxOnly, which rejects that shorthand.
  readonly status: number

  readonly fields: FieldErrors

  readonly riderStatus: RiderStatus | null

  readonly reviewNote: string | null

  constructor(
    message: string,
    status: number,
    fields: FieldErrors = {},
    riderStatus: RiderStatus | null = null,
    reviewNote: string | null = null,
  ) {
    super(message)
    this.name = 'RiderApiError'
    this.status = status
    this.fields = fields
    this.riderStatus = riderStatus
    this.reviewNote = reviewNote
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

  // The field-level message first: it is the specific one. "The email has
  // already been taken" beats "The given data was invalid."
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

async function request<TResult>(
  path: string,
  init: RequestInit & { fallbackError?: string } = {},
): Promise<TResult> {
  const { fallbackError = 'Something went wrong. Please try again.', ...rest } = init
  const token = riderToken()

  // Content-Type is set for a JSON body only. FormData must be left alone:
  // the browser has to add the multipart boundary itself, and naming the type
  // by hand produces a body PHP parses as empty — no files, no fields.
  const isFormData = rest.body instanceof FormData

  const response = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: {
      Accept: 'application/json',
      ...(rest.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...rest.headers,
    },
  })

  const data = await response.json().catch(() => null)

  if (!response.ok) {
    // Registration is throttled to a handful an hour, so a rider who mistypes
    // and retries can genuinely hit it. Laravel answers "Too Many Attempts.",
    // which reads like a fault rather than a wait — and leaves someone
    // refreshing at exactly the wrong moment.
    if (response.status === 429) {
      throw new RiderApiError(retryMessage(response.headers.get('Retry-After')), 429)
    }

    const fields = fieldErrorsFrom(data)
    const body = (data ?? {}) as { riderStatus?: unknown; reviewNote?: unknown }

    throw new RiderApiError(
      messageFrom(data, fields, fallbackError),
      response.status,
      fields,
      isRiderStatus(body.riderStatus) ? body.riderStatus : null,
      typeof body.reviewNote === 'string' ? body.reviewNote : null,
    )
  }

  return data as TResult
}

function postJson<TResult>(path: string, body: unknown, fallbackError?: string): Promise<TResult> {
  return request<TResult>(path, { method: 'POST', body: JSON.stringify(body), fallbackError })
}

// -- The rider -------------------------------------------------------------

export const RIDER_STATUSES = ['pending', 'approved', 'rejected', 'suspended'] as const

export type RiderStatus = (typeof RIDER_STATUSES)[number]

function isRiderStatus(value: unknown): value is RiderStatus {
  return typeof value === 'string' && (RIDER_STATUSES as readonly string[]).includes(value)
}

export interface RiderProfile {
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
}

export interface RiderSession {
  rider: RiderProfile
  token: string
}

export interface RiderRegistration {
  name: string
  email: string
  phone: string
  password: string
  passwordConfirmation: string
  licenseNumber: string
  plateNumber: string
  licenseImage: File
  plateImage: File
}

/**
 * Registration, as multipart — it carries the two document photos.
 *
 * The keys are the API's, not the form's: `password_confirmation` is what
 * Laravel's `confirmed` rule looks for, and the rest are camelCase because
 * that is what RiderAuthController validates.
 */
export function registerRider(input: RiderRegistration): Promise<RiderSession> {
  const body = new FormData()

  body.set('name', input.name)
  body.set('email', input.email)
  body.set('phone', input.phone)
  body.set('password', input.password)
  body.set('password_confirmation', input.passwordConfirmation)
  body.set('licenseNumber', input.licenseNumber)
  body.set('plateNumber', input.plateNumber)
  body.set('licenseImage', input.licenseImage)
  body.set('plateImage', input.plateImage)

  return request<RiderSession>('/api/rider/register', {
    method: 'POST',
    body,
    fallbackError: 'Could not create your rider account.',
  })
}

export function loginRider(email: string, password: string): Promise<RiderSession> {
  return postJson<RiderSession>(
    '/api/rider/login',
    { email, password },
    'Could not sign you in.',
  )
}

export function fetchRider(): Promise<{ rider: RiderProfile }> {
  return request<{ rider: RiderProfile }>('/api/rider/me', {
    fallbackError: 'Could not load your account.',
  })
}

export function logoutRider(): Promise<{ signedOut: boolean }> {
  return request<{ signedOut: boolean }>('/api/rider/logout', { method: 'POST' })
}

// -- Work ------------------------------------------------------------------

export interface DeliveryPickup {
  storeId: string
  storeName: string
  address: string | null
  lat: number | null
  lng: number | null
}

/** An unclaimed job. Deliberately carries no customer name or phone. */
export interface DeliveryOffer {
  id: string
  ticketNumber: number | string | null
  placedAt: string | null
  orderStatus: string
  pickup: DeliveryPickup
  dropoffArea: string | null
  distanceKm: number | null
  deliveryFeeCents: number
  itemCount: number
  paymentStatus: string
  collectCents: number
}

export type DeliveryStage = 'pending' | 'assigned' | 'picked_up' | 'delivered'

/** A claimed job: the offer, plus what it takes to actually finish it. */
export interface DeliveryAssignment extends DeliveryOffer {
  deliveryStage: DeliveryStage
  acceptedAt: string | null
  deliveryAddress: string | null
  deliveryLat: number | null
  deliveryLng: number | null
  customerName: string | null
  customerPhone: string | null
  items: { name: string; quantity: number }[]
}

export function fetchBoard(): Promise<{ orders: DeliveryOffer[] }> {
  return request<{ orders: DeliveryOffer[] }>('/api/rider/board', {
    fallbackError: 'Could not load the job board.',
  })
}

export function fetchMyDeliveries(): Promise<{
  active: DeliveryAssignment[]
  completed: DeliveryAssignment[]
}> {
  return request('/api/rider/deliveries', { fallbackError: 'Could not load your deliveries.' })
}

export function acceptDelivery(orderId: string): Promise<{ order: DeliveryAssignment }> {
  return request<{ order: DeliveryAssignment }>(`/api/rider/deliveries/${orderId}/accept`, {
    method: 'POST',
    fallbackError: 'Could not take that job.',
  })
}

export function advanceDelivery(
  orderId: string,
  stage: 'picked_up' | 'delivered',
): Promise<{ order: DeliveryAssignment }> {
  return postJson<{ order: DeliveryAssignment }>(
    `/api/rider/deliveries/${orderId}/stage`,
    { stage },
    'Could not update that delivery.',
  )
}

export function releaseDelivery(orderId: string): Promise<{ released: boolean }> {
  return request<{ released: boolean }>(`/api/rider/deliveries/${orderId}/release`, {
    method: 'POST',
    fallbackError: 'Could not give that job back.',
  })
}
