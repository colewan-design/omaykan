import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

function appHistoryFallback(): Plugin {
  return {
    name: 'products-qa-history-fallback',
    configureServer(server) {
      server.middlewares.use((request, _response, next) => {
        if (request.url === '/app' || request.url?.startsWith('/app/')) request.url = '/app.html'
        next()
      })
    },
  }
}

export default defineConfig({
  plugins: [vue(), appHistoryFallback()],
  resolve: {
    alias: {
      '@pos/core': path.resolve(__dirname, '../../packages/core/src'),
      '@pos/shared': path.resolve(__dirname, '../../packages/shared/src'),
      '@pos/data': path.resolve(__dirname, '../../packages/data/src'),
      '@pos/web': path.resolve(__dirname, 'src'),
    },
  },
})
