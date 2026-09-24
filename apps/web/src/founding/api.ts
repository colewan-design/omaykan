/**
 * The two public endpoints behind the founding-seller campaign page.
 *
 * Same-origin `/api`, like the rest of the marketing surfaces — nginx puts
 * Laravel behind the same host in production and vite.config.ts proxies it in
 * dev, so there is no base URL to resolve here.
 */

/** What `GET /api/founding-sellers/status` answers. */
export interface FoundingStatus {
  /** How many businesses the campaign is open to. Thirty, today. */
  limit: number
  /** Badges actually issued. Moves only when an operator accepts somebody. */
  claimed: number
  remaining: number
  open: boolean
  /** The category list the dropdown is built from, and the only values `apply` accepts. */
  categories: string[]
}

export interface ApplicationDraft {
  businessName: string
  ownerName: string
  category: string
  mobile: string
  email: string
  socialUrl: string
  address: string
  productsDescription: string
  offersDelivery: boolean
  wantsFounding: boolean
}

export interface ApplicationResult {
  id: string
  businessName: string
  /** Whether the campaign still had room at the moment this was submitted. */
  foundingOpen: boolean
  remaining: number
}

/** A field name from the draft, as the API spells it in `errors`. */
type FieldName = keyof ApplicationDraft

/**
 * A refusal the form can act on.
 *
 * Laravel answers a 422 as `{ message, errors: { field: [msg, ...] } }`, and
 * the form marks the offending inputs rather than dropping a single sentence
 * above a page of untouched fields. Anything else — a 500, a dropped
 * connection — arrives with no `fields`, and the form shows the message alone.
 */
export class ApplicationError extends Error {
  // Declared and assigned rather than a constructor parameter property: this
  // project builds with `erasableSyntaxOnly`, which rejects those.
  readonly fields: Partial<Record<FieldName, string>>

  constructor(message: string, fields: Partial<Record<FieldName, string>> = {}) {
    super(message)
    this.name = 'ApplicationError'
    this.fields = fields
  }
}

/**
 * The campaign's state, or `null` if it cannot be read.
 *
 * Null rather than a throw on purpose: the page is a pitch that stands on its
 * own, and a backend that is down must not keep somebody from reading it or
 * from filling the form in. The counter is simply left off, and the category
 * dropdown falls back to the list in `FALLBACK_CATEGORIES`.
 */
export async function fetchFoundingStatus(signal?: AbortSignal): Promise<FoundingStatus | null> {
  try {
    const response = await fetch('/api/founding-sellers/status', {
      headers: { Accept: 'application/json' },
      signal,
    })
    if (!response.ok) return null

    const body = (await response.json()) as Partial<FoundingStatus>
    if (typeof body.limit !== 'number' || !Array.isArray(body.categories)) return null

    return {
      limit: body.limit,
      claimed: body.claimed ?? 0,
      remaining: body.remaining ?? body.limit,
      open: body.open ?? true,
      categories: body.categories,
    }
  } catch {
    return null
  }
}

/**
 * The categories used when `status` could not be read.
 *
 * A copy of the server's list, which is the one thing on this page that has to
 * be duplicated: the dropdown cannot be empty, and a submission whose category
 * is not one of these is refused. Keep it in step with
 * FoundingSellerController::CATEGORIES — a stale entry here fails validation
 * on submit, which is a visible error rather than a silent one.
 */
export const FALLBACK_CATEGORIES = [
  'Sari-sari / grocery',
  'Market stall / fresh produce',
  'Restaurant / carinderia',
  'Coffee shop / milk tea',
  'Bakery / pastries',
  'Handicrafts / souvenirs',
  'Salon / services',
  'Other',
]

export async function submitApplication(draft: ApplicationDraft): Promise<ApplicationResult> {
  let response: Response
  try {
    response = await fetch('/api/founding-sellers/apply', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(draft),
    })
  } catch {
    throw new ApplicationError('Could not reach Omaykan. Check your connection and try again.')
  }

  const body = (await response.json().catch(() => ({}))) as {
    message?: string
    errors?: Record<string, string[]>
  } & Partial<ApplicationResult>

  if (response.ok) {
    return {
      id: body.id ?? '',
      businessName: body.businessName ?? draft.businessName,
      foundingOpen: body.foundingOpen ?? false,
      remaining: body.remaining ?? 0,
    }
  }

  // 429 has a `message` of its own, but it is Laravel's English and says
  // nothing about what to do. The throttle is 10/min, so waiting is the answer.
  if (response.status === 429) {
    throw new ApplicationError('That is a lot of tries in one minute. Wait a moment and send it again.')
  }

  const fields: Partial<Record<FieldName, string>> = {}
  for (const [field, messages] of Object.entries(body.errors ?? {})) {
    const first = messages?.[0]
    if (first) fields[field as FieldName] = first
  }

  throw new ApplicationError(
    body.message && Object.keys(fields).length === 0
      ? body.message
      : 'Some details need another look.',
    fields,
  )
}
