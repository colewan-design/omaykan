import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import type { ServerResponse } from 'node:http'

// Serves app.html and landing.html from cleaner public routes in dev and preview.
function entryRouteAliases(shopRootDomain: string): Plugin {
  // The signup form is the *seller* one — it asks for a business name and a
  // business type — so it lives at /seller/signup. /signup redirects there
  // rather than 404ing: it is the URL the seller Android app opens, and
  // every link that went out before the move. Production does the same in
  // nginx; this keeps dev and preview honest about it.
  const redirect = (req: { url?: string }, res: ServerResponse) => {
    if (req.url !== '/signup' && !req.url?.startsWith('/signup?')) return false
    res.writeHead(301, { Location: req.url.replace('/signup', '/seller/signup') })
    res.end()
    return true
  }

  const rewrite = (req: { url?: string; headers: { host?: string } }) => {
    // A shop subdomain (`nenas.localhost` with VITE_SHOP_ROOT_DOMAIN=localhost)
    // opens that shop at its root, as nginx does in production.
    const host = (req.headers.host ?? '').split(':')[0]!.toLowerCase()
    if (shopRootDomain !== '' && host.endsWith(`.${shopRootDomain}`) && (req.url === '/' || req.url?.startsWith('/?'))) {
      req.url = req.url.replace('/', '/shop.html')
      return
    }

    if (req.url === '/landing' || req.url?.startsWith('/landing?')) {
      req.url = req.url.replace('/landing', '/landing.html')
      return
    }

    if (req.url === '/app' || req.url?.startsWith('/app?')) {
      req.url = req.url.replace('/app', '/app.html')
      return
    }

    if (req.url?.startsWith('/app/')) {
      req.url = '/app.html'
      return
    }

    if (req.url === '/about' || req.url?.startsWith('/about?')) {
      req.url = req.url.replace('/about', '/about.html')
      return
    }

    if (req.url === '/seller/signup' || req.url?.startsWith('/seller/signup?')) {
      req.url = req.url.replace('/seller/signup', '/signup.html')
      return
    }

    if (req.url === '/account' || req.url?.startsWith('/account?')) {
      req.url = req.url.replace('/account', '/account.html')
      return
    }

    if (req.url === '/cart' || req.url?.startsWith('/cart?')) {
      req.url = req.url.replace('/cart', '/cart.html')
      return
    }

    // A shop's own page, /shop/<slug>. The slug is read back off the path by
    // shop/main.ts, so only the file served changes here, not the URL.
    if (req.url?.startsWith('/shop/')) {
      req.url = '/shop.html'
      return
    }

    if (req.url === '/rider' || req.url?.startsWith('/rider?')) {
      req.url = req.url.replace('/rider', '/rider.html')
      return
    }

    if (req.url === '/platform-admin' || req.url?.startsWith('/platform-admin?')) {
      req.url = req.url.replace('/platform-admin', '/platform-admin.html')
      return
    }

    if (req.url === '/support-inbox' || req.url?.startsWith('/support-inbox?')) {
      req.url = req.url.replace('/support-inbox', '/support-inbox.html')
    }
  }
  return {
    name: 'entry-route-aliases',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (redirect(req, res)) return
        rewrite(req)
        next()
      })
    },
    configurePreviewServer(server) {
      server.middlewares.use((req, res, next) => {
        if (redirect(req, res)) return
        rewrite(req)
        next()
      })
    },
  }
}

/**
 * Refuse to ship a production bundle with no tenant baked into it.
 *
 * VITE_* values are compile-time constants, so a blank slug cannot be fixed on
 * the server — it needs a rebuild and a redeploy. And it fails silently:
 * fetchCatalog sends `?orgSlug=&storeCode=`, the API answers 422,
 * loadStorefrontCatalog swallows it and falls back to a demo catalog that is
 * itself empty unless VITE_POS_DEMO_ORG_SLUG is set. The storefront then
 * renders perfectly, with nothing on the shelves and no error anywhere.
 *
 * That shipped once, on 2026-08-27, against a catalog of 464 products. This is
 * the check that would have caught it.
 */
function requireTenant(): Plugin {
  return {
    name: 'require-tenant',
    apply: 'build',
    configResolved(config) {
      if (config.mode !== 'production') return

      const missing = ['VITE_POS_ORGANIZATION_SLUG', 'VITE_POS_STORE_CODE']
        .filter((key) => !String(config.env[key] ?? '').trim())

      if (missing.length > 0) {
        throw new Error(
          `Refusing to build: ${missing.join(' and ')} ${missing.length > 1 ? 'are' : 'is'} blank.
` +
          'The storefront resolves its tenant at build time, so a blank value ships a shop with no ' +
          'products and no visible error. Set it in apps/web/.env.production — see .env.production.example.',
        )
      }
    },
  }
}

/**
 * Same-origin `/api` in dev, the way production serves it.
 *
 * Production puts nginx in front of both halves, so a page can call
 * `fetch('/api/...')` and reach Laravel — and twelve of them do, across
 * onboarding, platform-admin and support-inbox. A dev server has no such
 * front: `/api/staff/sign-in` on :5173 is a route Vite knows nothing about, so
 * it answers 404, and the seller signup page's sign-in tab reports "That
 * sign-in did not work" for what is really a missing proxy. The staff app at
 * /app was unaffected and hid the problem — it builds absolute URLs from
 * VITE_API_BASE instead.
 *
 * Proxying makes the two topologies agree rather than asking every caller to
 * remember which style it is using. Dev-server only: nothing here is in the
 * bundle, and a built deployment still relies on its own nginx.
 */
function apiProxy(apiBase: string) {
  return {
    '/api': { target: apiBase, changeOrigin: true },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Third argument '' loads every key, not just the VITE_ ones — this is the
  // config file, not the bundle, so there is nothing to leak into.
  const env = loadEnv(mode, __dirname, '')
  const apiBase = env.VITE_API_BASE?.trim() || 'http://127.0.0.1:8000'

  return {
    plugins: [vue(), entryRouteAliases((env.VITE_SHOP_ROOT_DOMAIN ?? '').trim().toLowerCase()), requireTenant()],
    server: { proxy: apiProxy(apiBase) },
    // `vite preview` serves the built bundle with no nginx either.
    preview: { proxy: apiProxy(apiBase) },
    resolve: {
      alias: {
        '@pos/core': path.resolve(__dirname, '../../packages/core/src'),
        '@pos/shared': path.resolve(__dirname, '../../packages/shared/src'),
        '@pos/data': path.resolve(__dirname, '../../packages/data/src'),
        '@pos/web': path.resolve(__dirname, 'src'),
      },
    },
    build: {
      rollupOptions: {
        input: {
          main: path.resolve(__dirname, 'index.html'),
          app: path.resolve(__dirname, 'app.html'),
          landing: path.resolve(__dirname, 'landing.html'),
          about: path.resolve(__dirname, 'about.html'),
          signup: path.resolve(__dirname, 'signup.html'),
          account: path.resolve(__dirname, 'account.html'),
          cart: path.resolve(__dirname, 'cart.html'),
          shop: path.resolve(__dirname, 'shop.html'),
          rider: path.resolve(__dirname, 'rider.html'),
          platformAdmin: path.resolve(__dirname, 'platform-admin.html'),
          supportInbox: path.resolve(__dirname, 'support-inbox.html'),
        },
      },
    },
  }
})
