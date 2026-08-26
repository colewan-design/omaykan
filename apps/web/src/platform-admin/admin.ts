import { computed, reactive } from 'vue'
import {
  fetchMe,
  PlatformApiError,
  setSessionLostHandler,
  signIn as signInRequest,
  signOut as signOutRequest,
  type PlatformAdminProfile,
} from '@pos/web/platform-admin/api'
import { platformToken, setPlatformToken } from '@pos/web/platform-admin/session'

/*
 * The signed-in operator.
 *
 * Module-level state, like the rider portal's session: the shell, the sidebar
 * and every view have to be looking at one operator, and a per-component copy
 * would let them disagree about whether this account is still an owner.
 *
 * `disabledMessage` is the one piece of state that is not the account itself.
 * An operator can be revoked while their tab is open — that is the point of
 * being able to revoke one — and the portal owes them an explanation rather
 * than a sign-in form that will refuse them for reasons it does not state.
 */

interface AdminState {
  admin: PlatformAdminProfile | null
  /** True until the stored token has been checked against the API. */
  hydrating: boolean
  disabledMessage: string | null
}

const state = reactive<AdminState>({
  admin: null,
  hydrating: platformToken() !== null,
  disabledMessage: null,
})

let hydrateStarted = false

// Any 401 from any call drops the session here, so no screen is ever left
// holding a token the server has stopped honouring.
setSessionLostHandler(() => {
  state.admin = null
  state.hydrating = false
})

async function hydrate(): Promise<void> {
  if (hydrateStarted) return
  hydrateStarted = true

  if (platformToken() === null) {
    state.hydrating = false
    return
  }

  try {
    state.admin = (await fetchMe()).admin
  } catch (error) {
    // 401 is handled by the transport. A 403 means the account was disabled
    // while this browser held a live token — keep the message, drop the token.
    if (error instanceof PlatformApiError && error.status === 403) {
      state.disabledMessage = error.message
      setPlatformToken(null)
      state.admin = null
    }
  } finally {
    state.hydrating = false
  }
}

export function usePlatformSession() {
  void hydrate()

  const admin = computed(() => state.admin)
  const signedIn = computed(() => state.admin !== null)
  const hydrating = computed(() => state.hydrating)
  const isOwner = computed(() => state.admin?.role === 'owner')
  const disabledMessage = computed(() => state.disabledMessage)

  async function signIn(email: string, password: string): Promise<PlatformAdminProfile> {
    const session = await signInRequest(email, password)
    setPlatformToken(session.token)
    state.admin = session.admin
    state.disabledMessage = null
    state.hydrating = false
    return session.admin
  }

  /**
   * Signs out this browser only; other sessions stay signed in. The API call
   * is best-effort: if it fails the local token still goes, because the person
   * at the keyboard asked to be signed out.
   */
  async function signOut(): Promise<void> {
    try {
      await signOutRequest()
    } catch {
      // Already-invalid token, or offline. Either way, drop it below.
    }
    setPlatformToken(null)
    state.admin = null
    state.disabledMessage = null
  }

  /** Called when a request comes back 403 from the active-account gate. */
  function applyDisabled(error: unknown): boolean {
    if (!(error instanceof PlatformApiError) || error.status !== 403 || error.adminStatus === null) {
      return false
    }

    state.disabledMessage = error.message
    setPlatformToken(null)
    state.admin = null

    return true
  }

  return { admin, signedIn, hydrating, isOwner, disabledMessage, signIn, signOut, applyDisabled }
}

/** The message to show an operator when a call fails, never the raw error. */
export function messageFor(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/** Per-field messages from a 422, for forms that show them under the input. */
export function fieldErrorsFor(error: unknown): Record<string, string> {
  return error instanceof PlatformApiError ? error.fields : {}
}

// -- Shared formatting -----------------------------------------------------

const PESOS = new Intl.NumberFormat('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function pesos(cents: number): string {
  return `₱${PESOS.format(cents / 100)}`
}

/** Whole pesos, for figures big enough that the centavos are noise. */
export function pesosRounded(cents: number): string {
  return `₱${Math.round(cents / 100).toLocaleString('en-PH')}`
}

const DATE_TIME = new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' })
const DATE_ONLY = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric' })

export function dateTime(value: string | null): string {
  return value ? DATE_TIME.format(new Date(value)) : '—'
}

export function shortDate(value: string | null): string {
  return value ? DATE_ONLY.format(new Date(value)) : '—'
}

/** "11 days waiting" — the number is what makes a queue urgent. */
export function waitingLabel(days: number | null): string {
  if (days === null) return 'Just now'
  if (days === 0) return 'Today'
  return `${days} day${days === 1 ? '' : 's'} waiting`
}
