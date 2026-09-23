import Echo from 'laravel-echo'
import Pusher from 'pusher-js'

// Laravel Echo's reverb/pusher connector reaches for a global Pusher.
;(globalThis as unknown as { Pusher?: typeof Pusher }).Pusher = Pusher

/**
 * The customer's side of one order, live.
 *
 * The counterpart to orderChannel.ts, and a different thing in one important
 * way: this channel is **public**. A storefront customer has no account and no
 * token — the tracking link is a UUID and holding it is the whole
 * authorization — so there is no `/broadcasting/auth` hop and no bearer header
 * here. The backend has always broadcast to `order.{uuid}` alongside the
 * store's private channel (see OrderDeliveryUpdated); nothing on the web had
 * ever subscribed to it, which is why the tracking view only ever knew what it
 * knew at page load.
 *
 * What rides it:
 *   - `order.status-changed` — the kitchen's progress
 *   - `order.delivery-updated` — a rider took it, picked it up, delivered it
 *   - `rider.position` — every ten seconds while it is on the road
 *
 * Because it is a UUID-keyed public channel, the payloads are already written
 * to be safe for anyone holding the link: the rider's number and position are
 * withheld after handover on the *server*, not here.
 */

export interface PublicOrderChannelOptions {
  orderId: string
  /** Broadcast name without the leading dot, plus the payload. */
  onEvent: (event: string, payload: unknown) => void
}

const PUBLIC_ORDER_EVENTS = ['.order.status-changed', '.order.delivery-updated', '.rider.position']

function reverbEnv() {
  const key = import.meta.env.VITE_REVERB_APP_KEY
  if (!key || !String(key).trim()) return null

  const scheme = (import.meta.env.VITE_REVERB_SCHEME ?? 'https') as string
  const port = Number(import.meta.env.VITE_REVERB_PORT ?? (scheme === 'https' ? 443 : 80))
  const path = (import.meta.env.VITE_REVERB_PATH ?? '') as string

  return {
    key: String(key),
    host: String(import.meta.env.VITE_REVERB_HOST ?? window.location.hostname),
    port,
    forceTLS: scheme === 'https',
    path: path.trim(),
  }
}

/** Whether realtime is configured for this build at all. */
export function realtimeAvailable(): boolean {
  return reverbEnv() !== null
}

/**
 * Watch one order. Returns a teardown; call it on unmount.
 *
 * A no-op when Reverb is not configured for this build, which is a supported
 * state — the caller polls instead. Every deployment without a websocket still
 * gets a moving map, just at the polling interval rather than the ping rate.
 */
export function subscribeToOrder(options: PublicOrderChannelOptions): () => void {
  const env = reverbEnv()
  if (!env || !options.orderId) return () => {}

  let echo: Echo<'reverb'> | null = null

  try {
    echo = new Echo({
      broadcaster: 'reverb',
      key: env.key,
      wsHost: env.host,
      wsPort: env.port,
      wssPort: env.port,
      wsPath: env.path,
      forceTLS: env.forceTLS,
      enabledTransports: ['ws', 'wss'],
    } as ConstructorParameters<typeof Echo>[0])

    const channel = echo.channel(`order.${options.orderId}`)
    for (const event of PUBLIC_ORDER_EVENTS) {
      channel.listen(event, (payload: unknown) => options.onEvent(event.slice(1), payload))
    }
  } catch {
    // A socket that will not open is not worth telling the customer about;
    // the page's own refresh still moves the order along.
    return () => {}
  }

  return () => {
    try {
      echo?.leave(`order.${options.orderId}`)
      echo?.disconnect()
    } catch {
      // Best-effort teardown — a socket that never connected is already gone.
    }
  }
}
