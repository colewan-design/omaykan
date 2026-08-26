import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

/*
 * The portal's routes.
 *
 * A router rather than a `view` ref — which is what the secret-gated version
 * used for its two tabs — because a tenant now has a page of its own. An
 * operator working the queue needs to be able to send a colleague the URL of
 * the shop they are arguing about, and to come back to it after a refresh.
 *
 * Base is /platform-admin, matching the Vite entry. Nothing here guards a
 * route: the shell renders sign-in in place of the whole layout when there is
 * no session, and the server enforces the rest. A client-side guard on a
 * cross-tenant tool would be theatre.
 */

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'overview', component: () => import('@pos/web/platform-admin/views/OverviewView.vue') },
  { path: '/tenants', name: 'tenants', component: () => import('@pos/web/platform-admin/views/TenantsView.vue') },
  {
    path: '/tenants/:slug',
    name: 'tenant',
    component: () => import('@pos/web/platform-admin/views/TenantDetailView.vue'),
  },
  { path: '/riders', name: 'riders', component: () => import('@pos/web/platform-admin/views/RidersView.vue') },
  { path: '/audit', name: 'audit', component: () => import('@pos/web/platform-admin/views/AuditView.vue') },
  { path: '/operators', name: 'operators', component: () => import('@pos/web/platform-admin/views/OperatorsView.vue') },
  // Anything else is somebody's stale bookmark; the dashboard is the honest
  // place to land.
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({
  history: createWebHistory('/platform-admin'),
  routes,
})
