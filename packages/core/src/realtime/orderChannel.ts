import Echo from 'laravel-echo'
import Pusher from 'pusher-js'

// Laravel Echo's reverb/pusher connector reaches for a global Pusher.
;(globalThis as unknown as { Pusher?: typeof Pusher }).Pusher = Pusher

export interface OrderChannelOptions {
  /** Backend origin for the /broadcasting/auth call. Empty → the page origin. */
  apiBaseUrl?: string
  storeId: string
  /** Staff bearer token — authorizes the private store.{id} channel. */
  token: string
  /** Called on any live order event (placed, status change, delivery update). */
  onOrderEvent: (payload: unknown) => void
}

// The Reverb websocket the browser connects to. Blank key means realtime is
// switched off for this build (dev, or a deployment without Reverb running),
// in which case subscribing is a no-op and the app keeps its polling refresh.
function reverbEnv() {
  const key = import.meta.env.VITE_REVERB_APP_KEY
  if (!key || !String(key).trim()) {
    return null
  }
  const scheme = (import.meta.env.VITE_REVERB_SCHEME ?? 'https') as string
  const port = Number(import.meta.env.VITE_REVERB_PORT ?? (scheme === 'https' ? 443 : 80))
  // Reverb sits behind nginx at a sub-path (e.g. /reverb) rather than the
  // pusher default, so the socket path must be given explicitly.
  const path = (import.meta.env.VITE_REVERB_PATH ?? '') as string
  return {
    key: String(key),
    host: String(import.meta.env.VITE_REVERB_HOST ?? window.location.hostname),
    port,
    forceTLS: scheme === 'https',
    path: path.trim(),
  }
}

const ORDER_EVENTS = ['.order.placed', '.order.status-changed', '.order.delivery-updated']

/**
 * Subscribe to a store's live order feed over Reverb. Returns a teardown
 * function; call it on unmount. Safe to call when realtime is disabled or the
 * store/token is missing — it simply returns a no-op teardown.
 */
export function subscribeToStoreOrders(options: OrderChannelOptions): () => void {
  const env = reverbEnv()
  if (!env || !options.storeId || !options.token) {
    return () => {}
  }

  const echo = new Echo({
    broadcaster: 'reverb',
    key: env.key,
    wsHost: env.host,
    wsPort: env.port,
    wssPort: env.port,
    wsPath: env.path,
    forceTLS: env.forceTLS,
    enabledTransports: ['ws', 'wss'],
    // Sanctum-guarded route (see backend routes/api.php) — the staff app uses a
    // bearer token, not the web session the default /broadcasting/auth expects.
    authEndpoint: `${(options.apiBaseUrl || window.location.origin).replace(/\/+$/, '')}/api/broadcasting/auth`,
    auth: {
      headers: {
        Authorization: `Bearer ${options.token}`,
        Accept: 'application/json',
      },
    },
  } as ConstructorParameters<typeof Echo>[0])

  const channel = echo.private(`store.${options.storeId}`)
  for (const event of ORDER_EVENTS) {
    channel.listen(event, options.onOrderEvent)
  }

  return () => {
    try {
      echo.leave(`store.${options.storeId}`)
      echo.disconnect()
    } catch {
      // Best-effort teardown — a socket that never connected is already gone.
    }
  }
}
