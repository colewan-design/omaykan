/**
 * Write the rendered DOM back into the built HTML entries.
 *
 * Every entry in this app ships as an empty shell — a mount div and a module
 * script — so a crawler that does not execute JavaScript sees a blank page
 * with no outgoing links, and reports the site as having none. The pages are
 * link-rich once Vue runs: the homepage alone renders 126 anchors. This step
 * runs the built bundle in headless Chromium and saves what it produced, so
 * the HTML on disk carries that content and those links.
 *
 * It serves `dist` with `vite preview`, which applies the same clean-URL
 * rewrites as dev and production nginx (`/landing` → landing.html, and so on),
 * so a route is prerendered under the URL it is actually served at.
 *
 * Only public pages are listed. Anything behind a sign-in (/app,
 * /platform-admin, /support-inbox) or belonging to one shopper (/account,
 * /cart, /checkout) must not be baked into a file every visitor receives.
 *
 * The catalog routes bake in a product snapshot taken at build time. That is
 * the point — it is what lets a crawler see the shelves — but it means the
 * prerendered listing is only as fresh as the last deploy. The live page
 * corrects itself the moment Vue mounts.
 *
 * Usage:
 *   node scripts/prerender.mjs
 *   PRERENDER_API_BASE=http://127.0.0.1:8001 node scripts/prerender.mjs
 *
 * Env:
 *   PRERENDER_API_BASE  where /api is proxied during prerender
 *                       (default http://127.0.0.1:8000)
 *   PRERENDER_SITE_URL  origin written into sitemap.xml
 *                       (default https://omaykan.com)
 *   PRERENDER_PORT      port for the temporary preview server (default 4178)
 */

import path from 'node:path'
import { writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { loadEnv, preview } from 'vite'
import { chromium } from 'playwright'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const DIST = path.join(ROOT, 'dist')

const API_BASE = process.env.PRERENDER_API_BASE?.trim() || 'http://127.0.0.1:8000'
const SITE_URL = (process.env.PRERENDER_SITE_URL?.trim() || 'https://omaykan.com').replace(/\/+$/, '')
const PORT = Number(process.env.PRERENDER_PORT ?? 4178)

/**
 * Where shops live, read from the same `.env.production` the bundle was built
 * from so the sitemap cannot name an address the build does not link to.
 * Blank for a build with no shop domain, where a shop's page is a path on the
 * main site instead.
 */
const SHOP_ROOT_DOMAIN = (loadEnv('production', ROOT, 'VITE_').VITE_SHOP_ROOT_DOMAIN ?? '').trim().toLowerCase()

/**
 * `minLinks` and `minText` are the floor a page must clear to be written.
 *
 * Without them a failed API call or a mid-render crash would be saved as a
 * perfectly valid empty page and deployed, which is the same silent failure
 * that put a storefront live with zero products on 2026-08-27. A prerender
 * that cannot prove it rendered something should stop the build, not ship.
 *
 * `/` and `/landing` run the same entry and both show the catalog, so their
 * floors assume a shelf: the homepage renders about 126 anchors against a
 * populated API and roughly none against a broken one.
 */
const ROUTES = [
  { url: '/', file: 'index.html', minLinks: 20, minText: 2000, minProducts: 10 },
  { url: '/landing', file: 'landing.html', minLinks: 20, minText: 2000, minProducts: 10 },
  { url: '/about', file: 'about.html', minLinks: 3, minText: 800 },
  { url: '/seller/signup', file: 'signup.html', minLinks: 1, minText: 400 },
  // The campaign page is mostly copy, and only its header and footer carry
  // links, so text is what proves it rendered. The floor is well under what it
  // actually measures (~3000): innerText does not count a closed <details>, so
  // the six FAQ answers are invisible to this check, and a floor set just
  // below today's number would fail the build over an edit to one paragraph.
  { url: '/seller/founding', file: 'founding.html', minLinks: 5, minText: 1800 },
  // The rider portal is a sign-in card and genuinely has no anchors, so text
  // is the only evidence it rendered.
  { url: '/rider', file: 'rider.html', minLinks: 0, minText: 400 },
]

/**
 * The static paths in sitemap.xml, besides `/` and the shops.
 *
 * Kept separate from ROUTES rather than derived from it: `/` and `/landing`
 * run the same entry and show the same catalog, so listing both would offer a
 * crawler two addresses for one page. Everything else here happens to be
 * prerendered as well, but being in the sitemap and being prerendered are
 * different decisions — a page can want one without the other.
 */
const EXTRA_SITEMAP_PATHS = ['/about', '/seller/signup', '/seller/founding', '/rider']

function isoDate() {
  return new Date().toISOString().slice(0, 10)
}

function sitemapXml(urls) {
  const today = isoDate()
  const entries = urls
    .map((u) => `  <url>\n    <loc>${u}</loc>\n    <lastmod>${today}</lastmod>\n  </url>`)
    .join('\n')
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries}\n</urlset>\n`
}

/**
 * A shop's canonical address, the same one `storefrontUrl` builds for the
 * bundle: its subdomain where a shop domain is configured, and `/shop/<slug>`
 * on the main site where it is not.
 *
 * The rule is duplicated rather than imported because that helper is
 * TypeScript in `packages/shared` and this script runs in plain node. Only the
 * hostname-shape half of it is repeated: the API answers with real shops, and
 * signup never issues a slug that cannot be a subdomain.
 */
function shopUrl(slug) {
  const canBeHost = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(slug)
  return SHOP_ROOT_DOMAIN !== '' && canBeHost ? `https://${slug}.${SHOP_ROOT_DOMAIN}` : `${SITE_URL}/shop/${slug}`
}

/**
 * The shop directory, read from the same API the storefront uses.
 *
 * Fetched before anything is rendered, and fatal if it fails. A rendered page
 * cannot be trusted to report a dead API: `landing/main.ts` installs a demo
 * repository and `loadStorefrontCatalog` swallows fetch errors, so the
 * homepage renders a full, plausible shelf with the API switched off — the
 * same silent fallback that put a storefront live with zero real products on
 * 2026-08-27. Counting links on the result would have called that a success.
 * Asking the API directly is the only honest check.
 */
async function fetchShops(origin) {
  const res = await fetch(`${origin}/api/stores`)
  if (!res.ok) throw new Error(`/api/stores answered ${res.status}`)

  const body = await res.json()
  // The endpoint answers `{ stores: [...] }`. The other shapes are tolerated
  // so a future envelope change surfaces as the error below rather than as a
  // sitemap that has quietly lost every shop.
  const stores = Array.isArray(body) ? body : (body.stores ?? body.data ?? [])
  const slugs = stores
    .map((store) => store?.orgSlug)
    .filter((slug) => typeof slug === 'string' && slug !== '')

  if (slugs.length === 0) {
    throw new Error(`/api/stores returned no usable shops (keys: ${Object.keys(body).join(', ') || 'array'})`)
  }
  return slugs
}

async function main() {
  console.log(`prerender: api ${API_BASE}, site ${SITE_URL}`)

  const server = await preview({
    root: ROOT,
    preview: {
      port: PORT,
      strictPort: true,
      // Overrides the config's own proxy so the build can be pointed at
      // whichever API has the catalog, without editing .env.production.
      proxy: { '/api': { target: API_BASE, changeOrigin: true } },
    },
  })

  const origin = server.resolvedUrls?.local?.[0]?.replace(/\/+$/, '') ?? `http://localhost:${PORT}`
  // Before a single page is rendered: prove the API is really answering.
  let shops
  try {
    shops = await fetchShops(origin)
    console.log(`  api ok: ${shops.length} shops`)
  } catch (error) {
    await server.close()
    console.error(`\nprerender aborted: ${error.message}`)
    console.error(
      `The storefront would still have rendered — it falls back to a bundled demo\n` +
        `catalog when the API is unreachable — so these pages would have been baked\n` +
        `and shipped looking fine with none of the real shelves in them.\n\n` +
        `Point PRERENDER_API_BASE at an API that has the catalog (currently ${API_BASE}),\n` +
        `or run "npm run build:no-prerender" to ship the unprerendered shells.`,
    )
    process.exitCode = 1
    return
  }

  const browser = await chromium.launch()
  const failures = []

  try {
    for (const route of ROUTES) {
      const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
      const page = await context.newPage()

      try {
        const response = await page.goto(origin + route.url, { waitUntil: 'networkidle', timeout: 60000 })
        if (!response?.ok()) throw new Error(`HTTP ${response?.status() ?? 'no response'}`)

        // networkidle lands before Vue has painted the data those requests
        // carried; this is the gap between the last response and the DOM.
        await page.waitForTimeout(1500)

        const { links, text, products } = await page.evaluate(() => ({
          links: document.querySelectorAll('a[href]').length,
          text: document.body?.innerText?.trim().length ?? 0,
          // The storefront links a product as `?product=<id>`. Counting these
          // is the one measure the demo fallback cannot fake: it carries no
          // product ids, so a shelf served from it scores zero.
          products: document.querySelectorAll('a[href*="product="]').length,
        }))

        if (links < route.minLinks || text < route.minText) {
          throw new Error(
            `rendered too thin: ${links} links (need ${route.minLinks}), ` +
              `${text} chars of text (need ${route.minText}). ` +
              'Usually the API was unreachable or returned nothing.',
          )
        }

        // A page that renders a full layout with an empty shelf is the failure
        // worth catching: the catalog request 404s on a tenant the API does
        // not have, the page falls back to the bundled demo, and everything
        // still looks right. Check the tenant in .env matches a real shop.
        if (route.minProducts && products < route.minProducts) {
          throw new Error(
            `rendered ${products} products (need ${route.minProducts}) from an otherwise ` +
              'complete page. The catalog request failed and the demo fallback took over — ' +
              'check VITE_POS_ORGANIZATION_SLUG/VITE_POS_STORE_CODE against the API being used.',
          )
        }

        const html = await page.evaluate(() => `<!doctype html>\n${document.documentElement.outerHTML}`)
        await writeFile(path.join(DIST, route.file), html, 'utf8')
        const shelf = route.minProducts ? `, ${products} products` : ''
        console.log(`  ${route.url.padEnd(16)} → ${route.file.padEnd(14)} ${links} links, ${text} chars${shelf}`)
      } catch (error) {
        failures.push(`${route.url}: ${error.message}`)
        console.error(`  ${route.url.padEnd(16)} FAILED  ${error.message}`)
      } finally {
        await context.close()
      }
    }

    // Each shop as `<slug>.omaykan.com` — the address the directory links and
    // the only one production serves the shop page at. A sitemap normally
    // speaks only for its own host; listing another is allowed when that host
    // points back at this sitemap, and every subdomain serves the main site's
    // `robots.txt`, which names it.
    const urls = [...['/', ...EXTRA_SITEMAP_PATHS].map((p) => `${SITE_URL}${p}`), ...shops.map(shopUrl)]
    await writeFile(path.join(DIST, 'sitemap.xml'), sitemapXml(urls), 'utf8')
    console.log(`  sitemap.xml      → ${urls.length} urls`)
  } finally {
    await browser.close()
    await server.close()
  }

  if (failures.length > 0) {
    console.error(`\nprerender failed on ${failures.length} route(s):`)
    for (const f of failures) console.error(`  - ${f}`)
    console.error('\nThe built pages are unprerendered but otherwise intact; the shells still boot.')
    process.exitCode = 1
  } else {
    console.log('\nprerender: all routes written')
  }
}

await main()
