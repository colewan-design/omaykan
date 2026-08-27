import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

// Serves app.html and landing.html from cleaner public routes in dev and preview.
function entryRouteAliases(): Plugin {
  const rewrite = (req: { url?: string }) => {
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

    if (req.url === '/signup' || req.url?.startsWith('/signup?')) {
      req.url = req.url.replace('/signup', '/signup.html')
      return
    }

    if (req.url === '/account' || req.url?.startsWith('/account?')) {
      req.url = req.url.replace('/account', '/account.html')
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
      server.middlewares.use((req, _res, next) => {
        rewrite(req)
        next()
      })
    },
    configurePreviewServer(server) {
      server.middlewares.use((req, _res, next) => {
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

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), entryRouteAliases(), requireTenant()],
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
        rider: path.resolve(__dirname, 'rider.html'),
        platformAdmin: path.resolve(__dirname, 'platform-admin.html'),
        supportInbox: path.resolve(__dirname, 'support-inbox.html'),
      },
    },
  },
})
