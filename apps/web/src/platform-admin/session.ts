/*
 * The signed-in operator's bearer token.
 *
 * Its own module for the same reason the rider portal's is: `api.ts` attaches
 * the header to every request without importing the store, and the store sets
 * the token without importing the transport.
 *
 * A separate key from every other portal's, not a shared one. An operator, a
 * shopper and a rider are three different accounts on three different guards,
 * and one browser can hold all three — the person running the platform also
 * buys coffee. Sharing a key would sign one out whenever another signed in,
 * and would send an operator token to routes that reject it.
 */

const STORAGE_KEY = 'omk_platform_token'

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

export function platformToken(): string | null {
  return token
}

export function setPlatformToken(next: string | null): void {
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
