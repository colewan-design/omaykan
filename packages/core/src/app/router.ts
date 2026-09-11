import { Capacitor } from '@capacitor/core'
import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import AuthPage from '@pos/core/pages/AuthPage.vue'
import DashboardPage from '@pos/core/pages/DashboardPage.vue'
import RegisterPage from '@pos/core/pages/RegisterPage.vue'
import OrdersPage from '@pos/core/pages/OrdersPage.vue'
import MessagesPage from '@pos/core/pages/MessagesPage.vue'
import ProductsPage from '@pos/core/pages/ProductsPage.vue'
import SalesPage from '@pos/core/pages/SalesPage.vue'
import SettingsPage from '@pos/core/pages/SettingsPage.vue'
import InventoryPage from '@pos/core/pages/InventoryPage.vue'
import DiagnosticsPage from '@pos/core/pages/DiagnosticsPage.vue'
import EmployeesPage from '@pos/core/pages/EmployeesPage.vue'
import CustomersPage from '@pos/core/pages/CustomersPage.vue'
import SuppliersPage from '@pos/core/pages/SuppliersPage.vue'
import TablesPage from '@pos/core/pages/TablesPage.vue'
import ReportsPage from '@pos/core/pages/ReportsPage.vue'
import IntegrationsPage from '@pos/core/pages/IntegrationsPage.vue'
import type { AppPageKey } from '@pos/shared/index'

declare module 'vue-router' {
  interface RouteMeta {
    publicOnly?: boolean
    pageKey?: AppPageKey
  }
}

export function createPosRouter() {
  const history = Capacitor.isNativePlatform()
    ? createWebHashHistory()
    : createWebHistory('/app/')

  return createRouter({
    history,
    routes: [
      { path: '/auth',        name: 'auth',        component: AuthPage, meta: { publicOnly: true } },
      { path: '/',            redirect: { name: 'dashboard' } },
      { path: '/dashboard',   name: 'dashboard',   component: DashboardPage, meta: { pageKey: 'dashboard' } },
      { path: '/sales',       name: 'sales',       component: SalesPage, meta: { pageKey: 'sales' } },
      { path: '/orders',      name: 'orders',      component: OrdersPage, meta: { pageKey: 'orders' } },
      // Behind `orders`, not a key of its own — see MessagesPage for why.
      { path: '/messages',    name: 'messages',    component: MessagesPage, meta: { pageKey: 'orders' } },
      { path: '/products',    name: 'products',    component: ProductsPage, meta: { pageKey: 'products' } },
      { path: '/customers',   name: 'customers',   component: CustomersPage, meta: { pageKey: 'customers' } },
      { path: '/suppliers',   name: 'suppliers',   component: SuppliersPage, meta: { pageKey: 'suppliers' } },
      { path: '/employees',   name: 'employees',   component: EmployeesPage, meta: { pageKey: 'employees' } },
      { path: '/inventory',   name: 'inventory',   component: InventoryPage, meta: { pageKey: 'inventory' }, alias: '/inventories' },
      { path: '/tables',      name: 'tables',      component: TablesPage, meta: { pageKey: 'tables' } },
      { path: '/reports',     name: 'reports',     component: ReportsPage, meta: { pageKey: 'reports' } },
      { path: '/integrations',name: 'integrations',component: IntegrationsPage, meta: { pageKey: 'integrations' } },
      { path: '/register',    name: 'register',    component: RegisterPage, meta: { pageKey: 'register' } },
      { path: '/settings',    name: 'settings',    component: SettingsPage, meta: { pageKey: 'settings' } },
      { path: '/diagnostics', name: 'diagnostics', component: DiagnosticsPage, meta: { pageKey: 'diagnostics' } },
    ],
  })
}
