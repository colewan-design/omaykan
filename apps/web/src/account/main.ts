import { createApp } from 'vue'
import { createPinia } from 'pinia'
import AccountPage from './AccountPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
import '@pos/web/landing/marketing.css'
import '@pos/web/landing/landing.css'
import './account.css'

// Same setup as the landing and about entries: one designed look, forced light
// so the page doesn't flip to the app's dark palette on a visitor's OS setting.
//
// Unlike those two this entry does not call setPosRepository — nothing on the
// portal reads the POS repository. The header and footer only need the
// storefront catalog, which comes from the API (commerce/catalog.ts), and
// getPosRepository only throws when something actually asks for it.
document.documentElement.dataset.theme = 'light'

const app = createApp(AccountPage)
app.use(createPinia())
app.mount('#account-app')
