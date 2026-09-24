import { createApp } from 'vue'
import FoundingPage from './FoundingPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
// marketing.css carries the `.reveal` classes the v-reveal directive toggles;
// landing.css carries the `.fd` palette and `--fd-inset` this page lays out on.
import '@pos/web/landing/marketing.css'
import '@pos/web/landing/landing.css'

// Same call as the landing and about entries: this is one designed look, so
// don't let the app's dark palette flip it on a visitor's OS setting.
document.documentElement.dataset.theme = 'light'

createApp(FoundingPage).mount('#founding-app')
