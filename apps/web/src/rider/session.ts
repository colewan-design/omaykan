/*
 * The signed-in rider's bearer token.
 *
 * Its own module for the same reason the storefront's is (commerce/session.ts):
 * `api.ts` attaches the header to every request without importing the store,
 * and the store sets the token without importing the transport.
 *
 * A separate key from `sf_customer_token`, not a shared one. A rider and a
 * shopper are two different accounts on two different guards, and one phone
 * can hold both — a rider who also buys coffee. Sharing the key would sign one
 * out every time the other signed in, and would send a rider token to
 * `auth:customer` routes, which reject it.
 */

const STORAGE_KEY = 'omk_rider_token'

function read(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

// Mirrored in memory so a read per request doesn't touch storage, and so the
// session still holds in a context where storage throws (private mode).
let token: string | null = read()

export function riderToken(): string | null {
  return token
}

export function setRiderToken(next: string | null): void {
  token = next

  try {
    if (next === null) {
      window.localStorage.removeItem(STORAGE_KEY)
    } else {
      window.localStorage.setItem(STORAGE_KEY, next)
    }
  } catch {
    // Private mode / quota — the session still holds for this tab.
  }
}
