/*
 * The signed-in customer's bearer token.
 *
 * Its own module so `api.ts` can attach the header to every request without
 * importing the account store, and the account store can set the token without
 * importing the transport — the two would otherwise import each other.
 *
 * localStorage rather than a cookie: the API is token-authenticated (Sanctum
 * personal access tokens, not the SPA cookie mode), the storefront can be
 * served from a different origin than the API, and there is no CSRF surface to
 * defend because nothing is authenticated by ambient credentials.
 */

const STORAGE_KEY = 'sf_customer_token'

function read(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

// Mirrored in memory so a read per request doesn't touch storage, and so it
// still works in a context where storage throws.
let token: string | null = read()

export function customerToken(): string | null {
  return token
}

export function setCustomerToken(next: string | null): void {
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
