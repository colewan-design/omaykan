/*
 * The Google Identity Services button, wrapped so nothing else has to know
 * about the global it installs.
 *
 * GIS is a script tag and a `window.google` — no npm package, because the
 * bundled version would go stale against a login endpoint Google keeps moving.
 * It is loaded lazily and only when a client id is configured, so a build with
 * `VITE_GOOGLE_CLIENT_ID` blank ships no third-party script at all and every
 * sign-in card simply has no Google button on it.
 *
 * Three cards use this, against three endpoints, all verifying the same way:
 * the storefront's shopper sign-in (POST /api/customer/auth/google), signup
 * (POST /api/signup), and the staff sign-in the register and admin app share
 * (POST /api/staff/auth/google). It lives in core rather than next to any one
 * of them because it is shared by all three.
 *
 * What the button gives back is an ID token — a JWT Google has signed. It is
 * not a session and it is not trusted here: it goes straight to the backend,
 * which checks the signature, the audience and the expiry before it will say
 * who anyone is. See GoogleIdentityVerifier.
 */

/** Blank switches the whole feature off, on this build, everywhere. */
export const GOOGLE_CLIENT_ID: string = (import.meta.env.VITE_GOOGLE_CLIENT_ID ?? '').trim()

export function googleSignInAvailable(): boolean {
  return GOOGLE_CLIENT_ID !== ''
}

const SCRIPT_SRC = 'https://accounts.google.com/gsi/client'

interface CredentialResponse {
  credential?: string
}

interface GoogleIdApi {
  initialize(config: {
    client_id: string
    callback: (response: CredentialResponse) => void
    cancel_on_tap_outside?: boolean
    use_fedcm_for_prompt?: boolean
    ux_mode?: 'popup' | 'redirect'
    auto_select?: boolean
  }): void
  renderButton(parent: HTMLElement, options: Record<string, unknown>): void
  disableAutoSelect(): void
}

declare global {
  interface Window {
    google?: { accounts?: { id?: GoogleIdApi } }
  }
}

/**
 * One in-flight load, shared. Two cards mounting at once — the header's and the
 * portal's — must not race two script tags into the document.
 */
let loading: Promise<GoogleIdApi> | null = null

function loadGis(): Promise<GoogleIdApi> {
  if (loading) return loading

  loading = new Promise<GoogleIdApi>((resolve, reject) => {
    const ready = window.google?.accounts?.id
    if (ready) {
      resolve(ready)
      return
    }

    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`)
    const script = existing ?? document.createElement('script')

    script.addEventListener('load', () => {
      const api = window.google?.accounts?.id
      // Loaded but not what we expected: treat it as unavailable rather than
      // letting a TypeError surface as "something went wrong" on a login form.
      api ? resolve(api) : reject(new Error('Google sign-in did not load.'))
    })

    script.addEventListener('error', () => {
      // A blocked or offline script is the ordinary case, not an exception —
      // a content blocker stops this one routinely. The caller hides the
      // button; email and password sign-in is unaffected.
      reject(new Error('Google sign-in could not be reached.'))
    })

    if (!existing) {
      script.src = SCRIPT_SRC
      script.async = true
      script.defer = true
      document.head.appendChild(script)
    }
  })

  // A failed load is not cached: the shopper's connection may come back, and
  // the next mount should get to try again.
  loading.catch(() => {
    loading = null
  })

  return loading
}

/**
 * Whoever is currently waiting on a credential.
 *
 * GIS takes one callback per `initialize` and fires it whenever a sign-in
 * completes, which is not necessarily the render that asked. Held in one place
 * so a card that has since unmounted cannot resolve over a live one.
 */
let pending: ((credential: string) => void) | null = null

/**
 * Draws Google's own button into `parent` and calls `onCredential` when the
 * shopper completes a sign-in with it.
 *
 * The button has to be Google's — it is an iframe, and the branding rules that
 * come with the API are the reason it cannot simply be our own `<button>`. The
 * only thing worth matching is the width, so it does not sit narrower than the
 * form it belongs to.
 */
export async function renderGoogleButton(
  parent: HTMLElement,
  onCredential: (credential: string) => void,
  appearance: { shape?: 'rectangular' | 'pill' } = {},
): Promise<void> {
  if (!googleSignInAvailable()) {
    throw new Error('Google sign-in is not configured for this build.')
  }

  const api = await loadGis()

  pending = onCredential

  api.initialize({
    client_id: GOOGLE_CLIENT_ID,
    callback: (response) => {
      if (response.credential) pending?.(response.credential)
    },
    // No One Tap prompt on this page. The sign-in card is already a request to
    // sign in; a floating second one over the top of it is noise, and it is the
    // part of GIS most likely to be suppressed anyway.
    auto_select: false,
    cancel_on_tap_outside: true,
    use_fedcm_for_prompt: true,
    // Popup, not redirect: a redirect flow would need a server-rendered landing
    // route, and the storefront is a static Vite entry with no such thing.
    ux_mode: 'popup',
  })

  parent.replaceChildren()

  api.renderButton(parent, {
    type: 'standard',
    theme: 'outline',
    size: 'large',
    text: 'continue_with',
    // Google's own shapes are the only ones on offer — the button is an iframe,
    // so a border-radius from our stylesheet clips the frame without rounding
    // what it draws. 'pill' is here for the staff sign-in card, whose own
    // buttons are pills; the storefront's are rectangles and take the default.
    shape: appearance.shape ?? 'rectangular',
    logo_alignment: 'left',
    width: Math.round(Math.min(400, Math.max(200, parent.clientWidth || 320))),
  })
}

/** Called on sign-out so the next visit is asked rather than assumed. */
export function forgetGoogleSession(): void {
  pending = null

  try {
    window.google?.accounts?.id?.disableAutoSelect()
  } catch {
    // GIS never loaded, or is gone. There is nothing to forget.
  }
}

/** Stops a card that is going away from resolving a later sign-in. */
export function releaseGoogleButton(onCredential: (credential: string) => void): void {
  if (pending === onCredential) pending = null
}
