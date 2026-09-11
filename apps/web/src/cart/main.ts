import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { setStorefrontContext } from '@pos/web/commerce/context'
import { readCartShop } from '@pos/web/commerce/cart'
import CartPage from './CartPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
import '@pos/web/landing/marketing.css'
import '@pos/web/landing/landing.css'

// Same setup as the landing and account entries: one designed look, forced
// light so the page doesn't flip to the app's dark palette on a visitor's OS.
document.documentElement.dataset.theme = 'light'

// The basket belongs to whichever shop it was filled from, which need not be
// the build-time tenant. Set before mount for the reason landing/main.ts gives:
// the catalog composable loads once, on first use, and the order has to be
// posted to the same store the lines were priced by.
const shop = readCartShop()
if (shop) setStorefrontContext(shop)

const app = createApp(CartPage)
app.use(createPinia())
app.mount('#cart-app')
