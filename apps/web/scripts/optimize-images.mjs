/**
 * Generate responsive AVIF and WebP variants for everything in `public/`.
 *
 * The images in `public/` are the originals — full-resolution photographs and
 * screenshots, several of them megabytes, all copied verbatim into `dist/` by
 * Vite and served at the same width to every device. A phone rendering a card
 * at 390px was downloading a 1505px file to do it.
 *
 * This writes smaller, better-compressed copies next to them under
 * `public/_opt/`, mirroring the source layout:
 *
 *   public/storefront/hero.webp
 *     → public/_opt/storefront/hero-480.avif   hero-480.webp
 *       public/_opt/storefront/hero-960.avif   hero-960.webp
 *       public/_opt/storefront/hero-1440.avif  hero-1440.webp
 *
 * `_opt` is generated and gitignored. It is written into `public/` rather than
 * `dist/` so the dev server serves the variants too — `<picture>` does not fall
 * back to `<img src>` when a `<source>` candidate 404s, so a dev-only gap would
 * mean broken images every time you run `npm run dev`.
 *
 * `src/ui/ResponsiveImg.vue` builds those paths by convention, which is why
 * there is no manifest and no size threshold here: every raster in `public/`
 * gets variants, so a path the component derives always resolves. Sources are
 * never upscaled (`withoutEnlargement`), so an image narrower than a target
 * width is emitted at its own width under that name. The `w` descriptor is then
 * generous by a little and the browser may pick it for a slightly wider slot —
 * softness at worst, never a broken URL.
 *
 * Reruns are cheap: a variant newer than its source is left alone. Delete
 * `public/_opt/` to force a full rebuild.
 *
 * Usage:
 *   node scripts/optimize-images.mjs
 *   IMAGE_WIDTHS=480,960 node scripts/optimize-images.mjs
 *
 * Env:
 *   IMAGE_WIDTHS   comma-separated target widths (default 480,960,1440)
 *   IMAGE_FORCE    set to rebuild variants even when they look current
 */

import path from 'node:path'
import { readdir, stat, mkdir, rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const PUBLIC = path.join(ROOT, 'public')
const OUT_DIR = path.join(PUBLIC, '_opt')

const WIDTHS = (process.env.IMAGE_WIDTHS?.trim() || '480,960,1440')
  .split(',')
  .map((w) => Number(w.trim()))
  .filter((w) => Number.isFinite(w) && w > 0)
  .sort((a, b) => a - b)

const FORCE = process.env.IMAGE_FORCE != null && process.env.IMAGE_FORCE !== ''

// SVG is already resolution-independent; ICO and GIF are icons and animations
// that these codecs would either mangle or make no smaller.
const RASTER = new Set(['.jpg', '.jpeg', '.png', '.webp'])

// AVIF at quality 50 sits roughly where WebP does at 78 for photographs, and
// effort 4 keeps a full build in the tens of seconds rather than minutes.
const ENCODERS = [
  { ext: '.avif', apply: (img) => img.avif({ quality: 50, effort: 4 }) },
  { ext: '.webp', apply: (img) => img.webp({ quality: 78 }) },
]

/** Every raster file under `public/`, excluding the generated tree itself. */
async function sources(dir = PUBLIC) {
  const found = []
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (full === OUT_DIR) continue
      found.push(...(await sources(full)))
    } else if (RASTER.has(path.extname(entry.name).toLowerCase())) {
      found.push(full)
    }
  }
  return found
}

async function mtime(file) {
  try {
    return (await stat(file)).mtimeMs
  } catch {
    return null
  }
}

/** Write one variant, or report it as skipped when it is already current. */
async function variant(source, sourceMtime, width, encoder) {
  const rel = path.relative(PUBLIC, source)
  const base = path.join(path.dirname(rel), path.basename(rel, path.extname(rel)))
  const out = path.join(OUT_DIR, `${base}-${width}${encoder.ext}`)

  const existing = await mtime(out)
  if (!FORCE && existing != null && existing >= sourceMtime) {
    return { skipped: true, bytes: (await stat(out)).size }
  }

  await mkdir(path.dirname(out), { recursive: true })
  const info = await encoder
    .apply(sharp(source).resize({ width, withoutEnlargement: true }))
    .toFile(out)
  return { skipped: false, bytes: info.size }
}

/** Run `task` over `items`, `limit` at a time. sharp is native and parallel. */
async function pool(items, limit, task) {
  const results = []
  let next = 0
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const index = next++
      results[index] = await task(items[index])
    }
  })
  await Promise.all(workers)
  return results
}

async function main() {
  if (WIDTHS.length === 0) {
    console.error('optimize-images: IMAGE_WIDTHS produced no usable widths')
    process.exitCode = 1
    return
  }

  // A source that has since been deleted would otherwise leave its variants
  // behind forever, and they would keep shipping.
  if (FORCE) await rm(OUT_DIR, { recursive: true, force: true })

  const files = await sources()
  if (files.length === 0) {
    console.log('optimize-images: no raster images under public/')
    return
  }

  console.log(`optimize-images: ${files.length} sources → widths ${WIDTHS.join(', ')}`)

  let originalBytes = 0
  let writtenBytes = 0
  let written = 0
  let skipped = 0
  const failures = []

  const results = await pool(files, 4, async (source) => {
    const sourceStat = await stat(source)
    const rel = path.relative(PUBLIC, source).replace(/\\/g, '/')
    try {
      const made = []
      for (const width of WIDTHS) {
        for (const encoder of ENCODERS) {
          made.push(await variant(source, sourceStat.mtimeMs, width, encoder))
        }
      }
      return { rel, source: sourceStat.size, made }
    } catch (error) {
      failures.push(`${rel}: ${error.message}`)
      return null
    }
  })

  for (const result of results) {
    if (result == null) continue
    originalBytes += result.source
    // The browser downloads one variant, not all of them. Comparing the
    // original against the widest WebP is the honest desktop-case number.
    const widest = result.made[result.made.length - 1]
    writtenBytes += widest.bytes
    for (const made of result.made) (made.skipped ? (skipped += 1) : (written += 1))
  }

  const mb = (bytes) => `${(bytes / 1048576).toFixed(2)} MB`
  const saved = originalBytes - writtenBytes
  console.log(`  wrote ${written} variant(s), ${skipped} already current`)
  console.log(`  originals ${mb(originalBytes)} → widest variant ${mb(writtenBytes)}`)
  console.log(`  saves ${mb(saved)} (${((saved / originalBytes) * 100).toFixed(1)}%) on a desktop load`)

  if (failures.length > 0) {
    console.error(`\noptimize-images failed on ${failures.length} file(s):`)
    for (const f of failures) console.error(`  - ${f}`)
    console.error('\nThose keep serving their original; <picture> sources for them will 404.')
    process.exitCode = 1
  }
}

await main()
