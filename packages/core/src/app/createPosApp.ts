import { createApp } from 'vue'
import { createPinia } from 'pinia'
import AppShell from '@pos/core/app/AppShell.vue'
import { createPosRouter } from '@pos/core/app/router'
import { setPosRepository } from '@pos/core/services/runtime'
import { useAuthStore } from '@pos/core/stores/auth'
import { isOwnerPage } from '@pos/shared/index'
import type { PosRepository } from '@pos/data/index'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'

export function createPosApp(options: { repository: PosRepository }) {
  setPosRepository(options.repository)

  const app = createApp(AppShell)
  const pinia = createPinia()
  const router = createPosRouter()

  app.use(pinia)

  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    await auth.initialize()

    if (to.meta.publicOnly) {
      if (auth.currentUser) {
        if (!auth.firstAccessiblePage) {
          await auth.logout()
          return true
        }

        return auth.firstAccessiblePage === 'register'
          ? { name: 'register' }
          : auth.firstAccessiblePage
            ? { name: auth.firstAccessiblePage }
            : false
      }

      return true
    }

    if (!auth.currentUser) {
      return { name: 'auth', query: { redirect: to.fullPath } }
    }

    if (to.meta.pageKey && isOwnerPage(to.meta.pageKey) && !auth.isOwner) {
      if (auth.firstAccessiblePage) {
        return auth.firstAccessiblePage === 'register'
          ? { name: 'register' }
          : { name: auth.firstAccessiblePage }
      }

      await auth.logout()
      return { name: 'auth' }
    }

    if (to.meta.pageKey && !auth.canAccess(to.meta.pageKey)) {
      if (auth.firstAccessiblePage) {
        return auth.firstAccessiblePage === 'register'
          ? { name: 'register' }
          : { name: auth.firstAccessiblePage }
      }

      await auth.logout()
      return { name: 'auth' }
    }

    return true
  })

  app.use(router)

  return app
}
