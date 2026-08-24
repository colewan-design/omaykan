import { createRouter, createWebHistory } from 'vue-router'
import CatalogPage from '@pos/web/storefront/pages/CatalogPage.vue'
import CheckoutPage from '@pos/web/storefront/pages/CheckoutPage.vue'
import OrderStatusPage from '@pos/web/storefront/pages/OrderStatusPage.vue'
import ProductDetailPage from '@pos/web/storefront/pages/ProductDetailPage.vue'

export function createStorefrontRouter() {
  return createRouter({
    history: createWebHistory('/store/'),
    routes: [
      { path: '/', name: 'catalog', component: CatalogPage },
      { path: '/product/:productId', name: 'product', component: ProductDetailPage, props: true },
      { path: '/checkout', name: 'checkout', component: CheckoutPage },
      { path: '/order/:orderId', name: 'order', component: OrderStatusPage, props: true },
    ],
    // Landing straight on a product deep-link shouldn't inherit the previous
    // page's scroll position.
    scrollBehavior: () => ({ top: 0 }),
  })
}
