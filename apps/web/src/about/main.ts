import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { setPosRepository } from '@pos/core/services/runtime'
import AboutPage from './AboutPage.vue'
import { createDemoPosRepository } from '@pos/web/landing/demoRepository'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
import '@pos/web/landing/marketing.css'
import '@pos/web/landing/landing.css'

// Same setup as the landing entry: one designed look, forced light so the
// page doesn't flip to the app's dark palette on a visitor's OS setting.
document.documentElement.dataset.theme = 'light'

setPosRepository(createDemoPosRepository())

const app = createApp(AboutPage)
app.use(createPinia())
app.mount('#about-app')
