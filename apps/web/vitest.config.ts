import { defineConfig } from 'vitest/config'
import path from 'node:path'
import vue from '@vitejs/plugin-vue'

// Shares the app's aliases so a test imports a module by the same specifier
// the app does — otherwise a passing test can be exercising a different file.
export default defineConfig({
  // For the few specs that mount a component; each opts into jsdom itself.
  plugins: [vue()],
  resolve: {
    alias: {
      '@pos/core': path.resolve(__dirname, '../../packages/core/src'),
      '@pos/shared': path.resolve(__dirname, '../../packages/shared/src'),
      '@pos/data': path.resolve(__dirname, '../../packages/data/src'),
      '@pos/web': path.resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'node',
    include: ['test/**/*.spec.ts'],
  },
})
