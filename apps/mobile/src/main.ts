import { createApp } from 'vue'
import '@pos/core/styles/tokens.css'
import '@pos/web/storefront/theme.css'
import StorefrontRoot from './storefront/StorefrontRoot.vue'
import { createStorefrontRouter } from './storefront/router'

const app = createApp(StorefrontRoot)
app.use(createStorefrontRouter())
app.mount('#app')
