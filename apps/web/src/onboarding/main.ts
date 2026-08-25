import { createApp } from 'vue'
import OnboardingPage from './OnboardingPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'
// Shared marketing chrome — this page now leads with the Omaykan product
// story (PosMarketing.vue) above the signup form.
import '../landing/marketing.css'

// The marketing half of this page is designed against the light token set,
// same as the landing page — don't let the app's dark palette flip it.
document.documentElement.dataset.theme = 'light'

createApp(OnboardingPage).mount('#onboarding-app')
