import { computed, reactive } from 'vue'
import {
  fetchRider,
  loginRider,
  logoutRider,
  registerRider,
  RiderApiError,
  type RiderProfile,
  type RiderRegistration,
  type RiderSession,
} from '@pos/web/rider/api'
import { riderToken, setRiderToken } from '@pos/web/rider/session'

/*
 * The signed-in rider.
 *
 * Module-level state, like the cart and the customer account: the portal's
 * header, its status screen and the work screens all have to be looking at one
 * session, and a per-component copy would let them disagree about whether the
 * rider is still approved.
 *
 * Registration and sign-in both land here, and both return a token even when
 * the account is only `pending` — deliberately, on the API's side. A rider who
 * closes the tab after signing up would otherwise have no way back to their
 * own status, and "wait for the email" is not a status screen.
 */

interface RiderState {
  rider: RiderProfile | null
  /** True until the stored token has been checked against the API. */
  hydrating: boolean
}

const state = reactive<RiderState>({ rider: null, hydrating: riderToken() !== null })

let hydrateStarted = false

/**
 * Trades the stored token for the rider it belongs to.
 *
 * A token the server no longer honours is dropped here rather than left to
 * fail the next call — and for a rider that is a routine event, not an exotic
 * one: suspending an account deletes its tokens, so the next request from a
 * suspended rider's phone is a 401 by design. Anything else (offline, API
 * down) leaves the token alone; a flaky signal on the road is no reason to
 * sign someone out.
 */
async function hydrate(): Promise<void> {
  if (hydrateStarted) return
  hydrateStarted = true

  if (riderToken() === null) {
    state.hydrating = false
    return
  }

  try {
    state.rider = (await fetchRider()).rider
  } catch (error) {
    if (error instanceof RiderApiError && error.status === 401) {
      setRiderToken(null)
      state.rider = null
    }
  } finally {
    state.hydrating = false
  }
}

function adopt(session: RiderSession): RiderProfile {
  setRiderToken(session.token)
  state.rider = session.rider
  state.hydrating = false
  return session.rider
}

export function useRiderSession() {
  void hydrate()

  const rider = computed(() => state.rider)
  const signedIn = computed(() => state.rider !== null)
  const hydrating = computed(() => state.hydrating)
  const approved = computed(() => state.rider?.status === 'approved')

  async function register(input: RiderRegistration): Promise<RiderProfile> {
    return adopt(await registerRider(input))
  }

  async function signIn(email: string, password: string): Promise<RiderProfile> {
    return adopt(await loginRider(email, password))
  }

  /**
   * Re-reads the account from the API.
   *
   * The one thing a pending rider can usefully do is find out whether they
   * have been approved yet, and this is it — there is no push channel to the
   * portal, so the status screen refreshes on demand.
   */
  async function refresh(): Promise<void> {
    try {
      state.rider = (await fetchRider()).rider
    } catch (error) {
      if (error instanceof RiderApiError && error.status === 401) {
        setRiderToken(null)
        state.rider = null
      } else {
        throw error
      }
    }
  }

  /**
   * Signs out this device only; other phones stay signed in. The API call is
   * best-effort: if it fails the local token still goes, because the person
   * holding the phone asked to be signed out.
   */
  async function signOut(): Promise<void> {
    try {
      await logoutRider()
    } catch {
      // Already-invalid token, or offline. Either way, drop it below.
    }
    setRiderToken(null)
    state.rider = null
  }

  return { rider, signedIn, hydrating, approved, register, signIn, refresh, signOut }
}

/**
 * Called when a work request comes back 403 from the approval gate: an account
 * approved when the screen loaded can be suspended while it is open, and the
 * portal should move to the status screen rather than show an error over a job
 * board the rider can no longer act on.
 */
export function applyGateRejection(error: unknown): boolean {
  if (!(error instanceof RiderApiError) || error.status !== 403 || error.riderStatus === null) {
    return false
  }

  if (state.rider !== null) {
    state.rider = {
      ...state.rider,
      status: error.riderStatus,
      reviewNote: error.reviewNote ?? state.rider.reviewNote,
    }
  }

  return true
}

/** The message to show a rider when a call fails, never the raw error. */
export function messageFor(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/** Per-field messages from a 422, for forms that show them under the input. */
export function fieldErrorsFor(error: unknown): Record<string, string> {
  return error instanceof RiderApiError ? error.fields : {}
}
