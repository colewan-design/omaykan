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

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), entryRouteAliases()],
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
