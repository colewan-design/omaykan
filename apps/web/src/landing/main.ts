import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { setPosRepository } from '@pos/core/services/runtime'
import LandingPage from './LandingPage.vue'
import { createDemoPosRepository } from './demoRepository'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
import './marketing.css'
import './landing.css'

// Force the light theme tokens — this page has one designed look and
// shouldn't flip to the app's dark palette based on visitor OS settings.
document.documentElement.dataset.theme = 'light'

setPosRepository(createDemoPosRepository())

const app = createApp(LandingPage)
app.use(createPinia())
app.mount('#landing-app')
