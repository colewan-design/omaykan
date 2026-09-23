/**
 * Build `srcset` values for the variants `scripts/optimize-images.mjs` writes.
 *
 * That script mirrors every raster under `public/` into `public/_opt/` at each
 * width in `WIDTHS`, so the path for a variant is derivable and there is no
 * manifest to load:
 *
 *   /storefront/hero.webp  →  /_opt/storefront/hero-960.webp
 *
 * Only WebP is named here, even though the script also emits AVIF. `srcset`
 * does no format negotiation — every candidate in it must be decodable by the
 * browser that reads it — so the AVIF is served by nginx instead, swapped in
 * for the same `.webp` URL when the request carries `Accept: image/avif` (see
 * documentation/deployment.md). Markup stays single-format; capable browsers
 * still get the smaller bytes.
 *
 * This deliberately produces a plain `srcset` for the existing `<img>` rather
 * than a `<picture>` wrapper. Every component here styles its image through
 * `<style scoped>`, and scoped rules do not reach into a child component's
 * markup — wrapping would have meant rewriting those selectors as `:deep()`.
 *
 * Usage:
 *   <img :src="HERO" :srcset="srcSet(HERO)" :sizes="SIZES.full" alt="" />
 */

/** The widths the build emits. Keep in step with `IMAGE_WIDTHS`. */
export const WIDTHS = [480, 960, 1440] as const

/** Sources the optimizer processes; anything else has no variants. */
const OPTIMIZED = /\.(png|jpe?g|webp)$/i

/**
 * Uploaded photos — a shop's picture, a product shot, a rider's licence —
 * are served by the backend under `/api/`, so they are local-looking paths
 * with no build-time variant behind them. Naming one in a `srcset` would
 * point every candidate at a file that does not exist, and unlike a bad
 * `<source>` the browser has no original to fall back to. See
 * `resolveImageUrl` in commerce/api.ts, which is what produces them.
 */
const SERVED_BY_API = '/api/'

/**
 * Common `sizes` values, so call sites describe the slot rather than guessing.
 * Each says how wide the image lands at the viewport widths the layout uses.
 */
export const SIZES = {
  /** Edge to edge — heroes and banners. */
  full: '100vw',
  /** A half-width feature panel that stacks below the tablet breakpoint. */
  half: '(max-width: 820px) 100vw, 50vw',
  /** A card in the product and shop grids. */
  card: '(max-width: 560px) 50vw, (max-width: 1100px) 33vw, 260px',
  /** A small square — avatars, shop marks, list-row art. */
  thumb: '96px',
} as const

/**
 * The `srcset` for a local image, or `undefined` when there is nothing to
 * offer — a remote or API-served photo, an SVG, a data URI, or a blank src.
 * Vue drops an attribute bound to `undefined`, so binding this unconditionally
 * is safe on an `<img>` whose src is sometimes local and sometimes not.
 */
export function srcSet(src: string | null | undefined): string | undefined {
  if (!src || !src.startsWith('/') || src.startsWith('//')) return undefined
  if (src.startsWith('/_opt/') || src.startsWith(SERVED_BY_API)) return undefined
  if (!OPTIMIZED.test(src)) return undefined

  // What is left is a path under `public/`, and the optimizer covers every
  // raster there without a size threshold — so the variants are known to
  // exist. Any other local path is already a 404 with or without this.

  const cut = src.lastIndexOf('.')
  const stem = src.slice(0, cut)
  return WIDTHS.map((w) => `/_opt${stem}-${w}.webp ${w}w`).join(', ')
}

/** One variant's url — for `<link rel="preload">`, which takes a single href. */
export function variant(src: string, width: (typeof WIDTHS)[number]): string {
  const cut = src.lastIndexOf('.')
  return `/_opt${src.slice(0, cut)}-${width}.webp`
}
