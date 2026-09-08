/**
 * Mapbox, loaded only when something actually needs a map.
 *
 * `mapbox-gl` is a ~800 KB parse before it draws a single tile. The register,
 * the catalog, the shift screen and the whole storefront never need it, so it
 * is behind a dynamic import and pulled in the first time a delivery map
 * mounts. A shop on a Baguio DSL line should not pay for a map it never opens.
 *
 * Everything here fails soft. No token, a blocked CDN, a browser without WebGL
 * — none of those are errors the customer or the merchant can do anything
 * about, and all of them leave the screen showing the delivery stage in words,
 * which is what it showed before there was a map at all.
 */

import type { Map as MapboxMap } from 'mapbox-gl'

/**
 * A public (`pk.*`) token, compiled into the bundle. That is what public tokens
 * are for; the protection is the URL restriction set on the token in the
 * Mapbox account, not secrecy. A secret (`sk.*`) token must never go here.
 */
export const MAPBOX_TOKEN: string = (import.meta.env.VITE_MAPBOX_TOKEN ?? '').trim()

const LIGHT_STYLE: string =
  (import.meta.env.VITE_MAPBOX_STYLE ?? '').trim() || 'mapbox://styles/mapbox/streets-v12'

/** Kept in step with the app's own light/dark, so a map never glares at night. */
const DARK_STYLE = 'mapbox://styles/mapbox/dark-v11'

export function mapboxStyleFor(dark: boolean): string {
  return dark ? DARK_STYLE : LIGHT_STYLE
}

/** Whether a map is worth attempting at all. Checked before rendering a canvas. */
export function mapsAvailable(): boolean {
  return MAPBOX_TOKEN.length > 0
}

export interface LatLng {
  lat: number
  lng: number
}

/**
 * A coordinate pair is only usable if both halves are real numbers.
 *
 * Takes nullable members because that is the shape the API serves — a store
 * whose owner never set a location, an address the geocoder could not place —
 * and the whole point of this guard is to be handed those and say no.
 */
type MaybePoint = { lat?: number | null; lng?: number | null }

export function isPoint(value: MaybePoint | null | undefined): value is LatLng {
  return (
    !!value &&
    typeof value.lat === 'number' &&
    typeof value.lng === 'number' &&
    Number.isFinite(value.lat) &&
    Number.isFinite(value.lng) &&
    // 0,0 is the Gulf of Guinea and is what an unset column looks like once it
    // has been through a Number() cast. Nothing in Baguio is there.
    !(value.lat === 0 && value.lng === 0)
  )
}

type MapboxModule = typeof import('mapbox-gl')

let modulePromise: Promise<MapboxModule | null> | null = null

/**
 * Load the library once per page and hand the same promise to every caller.
 *
 * The CSS is imported alongside it: without it the markers and the attribution
 * land in the top-left corner of the page rather than on the map, which looks
 * like a broken deploy rather than a missing stylesheet.
 */
export function loadMapbox(): Promise<MapboxModule | null> {
  if (!mapsAvailable()) return Promise.resolve(null)

  modulePromise ??= (async () => {
    try {
      const [module] = await Promise.all([import('mapbox-gl'), import('mapbox-gl/dist/mapbox-gl.css')])
      const mapbox = (module as unknown as { default?: MapboxModule }).default ?? module
      // `accessToken` is a settable static on the module in every published
      // build, but is typed readonly, so this is the one cast the file needs.
      ;(mapbox as unknown as { accessToken: string }).accessToken = MAPBOX_TOKEN
      return mapbox
    } catch {
      // Offline, blocked, or a browser that cannot run it. The caller draws
      // the text fallback and nothing is logged at the user.
      return null
    }
  })()

  return modulePromise
}

// -- The road between two points ---------------------------------------------

/**
 * The driving route, as a line of coordinates.
 *
 * A straight line between shop and door is a lie in a city built on a mountain:
 * it crosses ravines, and the "5 minutes away" it implies is fifteen. So the
 * Directions API is asked for the real road, once per pair of endpoints, and
 * the answer is cached for the life of the page — a route from this shop to
 * this address does not change while somebody watches it.
 *
 * Falls back to a straight line, which is honest enough as a *hint* of
 * direction and is what the caller draws dashed rather than solid.
 */
const routeCache = new Map<string, LatLng[] | null>()

function routeKey(from: LatLng, to: LatLng): string {
  const round = (n: number) => n.toFixed(4)
  return `${round(from.lat)},${round(from.lng)}>${round(to.lat)},${round(to.lng)}`
}

export async function fetchDrivingRoute(from: LatLng, to: LatLng): Promise<LatLng[] | null> {
  if (!mapsAvailable() || !isPoint(from) || !isPoint(to)) return null

  const key = routeKey(from, to)
  if (routeCache.has(key)) return routeCache.get(key) ?? null

  try {
    const coords = `${from.lng},${from.lat};${to.lng},${to.lat}`
    const url =
      `https://api.mapbox.com/directions/v5/mapbox/driving/${coords}` +
      `?geometries=geojson&overview=full&access_token=${encodeURIComponent(MAPBOX_TOKEN)}`

    const response = await fetch(url)
    if (!response.ok) throw new Error(String(response.status))

    const body = (await response.json()) as {
      routes?: { geometry?: { coordinates?: [number, number][] } }[]
    }

    const line = body.routes?.[0]?.geometry?.coordinates
    const path = line?.map(([lng, lat]) => ({ lat, lng })) ?? null

    routeCache.set(key, path)
    return path
  } catch {
    // A cached null means "asked, got nothing" — so a dead network or a token
    // without the Directions scope costs one request, not one per repaint.
    routeCache.set(key, null)
    return null
  }
}

// -- Moving a marker like something with a motor ------------------------------

/**
 * Slide a marker from where it is to where it now is, over `durationMs`.
 *
 * Positions arrive every ten seconds. Snapping the marker makes a rider look
 * like they teleport once per breath; easing it across makes the same data read
 * as a vehicle moving down a road. This is the single change that makes the map
 * feel like the one people already know.
 *
 * Returns a cancel function — the caller must call it when a newer ping lands
 * or the component unmounts, or two animations will fight over one marker.
 */
export function animateMarker(
  setPosition: (point: LatLng) => void,
  from: LatLng,
  to: LatLng,
  durationMs = 900,
): () => void {
  if (typeof requestAnimationFrame === 'undefined') {
    setPosition(to)
    return () => {}
  }

  const startedAt = performance.now()
  let frame = 0
  let cancelled = false

  // Ease-out: a fix is a position that was already true when it was sent, so
  // arriving early and settling reads better than a constant-speed glide.
  const ease = (t: number) => 1 - (1 - t) * (1 - t)

  const step = (now: number) => {
    if (cancelled) return
    const t = Math.min(1, (now - startedAt) / durationMs)
    const k = ease(t)
    setPosition({
      lat: from.lat + (to.lat - from.lat) * k,
      lng: from.lng + (to.lng - from.lng) * k,
    })
    if (t < 1) frame = requestAnimationFrame(step)
  }

  frame = requestAnimationFrame(step)

  return () => {
    cancelled = true
    if (frame) cancelAnimationFrame(frame)
  }
}

/**
 * Keep every point on screen without yanking the map about.
 *
 * Called once when the map has its first full set of points. It is deliberately
 * *not* called on every ping: a map that refits itself every ten seconds cannot
 * be panned or zoomed by the person watching it, which is the fastest way to
 * make a live map annoying.
 */
export function fitToPoints(map: MapboxMap, points: LatLng[], padding = 64): void {
  const usable = points.filter(isPoint)
  if (usable.length === 0) return

  if (usable.length === 1) {
    map.setCenter([usable[0]!.lng, usable[0]!.lat])
    map.setZoom(15)
    return
  }

  const lngs = usable.map((p) => p.lng)
  const lats = usable.map((p) => p.lat)

  map.fitBounds(
    [
      [Math.min(...lngs), Math.min(...lats)],
      [Math.max(...lngs), Math.max(...lats)],
    ],
    { padding, maxZoom: 16, duration: 0 },
  )
}

/** Straight-line kilometres. Good enough for "how far off is he", not for pricing. */
export function haversineKm(a: LatLng, b: LatLng): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180
  const R = 6371
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const lat1 = toRad(a.lat)
  const lat2 = toRad(b.lat)

  const h =
    Math.sin(dLat / 2) ** 2 + Math.sin(dLng / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2)

  return 2 * R * Math.asin(Math.sqrt(h))
}
