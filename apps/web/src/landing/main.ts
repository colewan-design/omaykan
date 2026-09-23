import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { setPosRepository } from '@pos/core/services/runtime'
import { applyShopBySlug } from '@pos/web/commerce/shopSwitch'
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

/**
 * Which shop this page is showing.
 *
 * `?shop=<orgSlug>` comes from the directory; without it the build-time env
 * tenant stands, so an install that only ever has one shop behaves exactly as
 * before. Resolved before mount because the catalog composable loads once, on
 * first use — setting the context afterwards would fetch the wrong shop's
 * shelf and never correct it.
 *
 * The slug is checked against the directory rather than trusted: it arrives
 * from the URL, and pointing the storefront at an unlisted or misspelt tenant
 * would render an empty shop with no explanation.
 */
async function resolveShop(): Promise<void> {
  await applyShopBySlug(new URLSearchParams(window.location.search).get('shop') ?? '')
}

resolveShop().finally(() => {
  const app = createApp(LandingPage)
  app.use(createPinia())
  app.mount('#landing-app')
})
