import { createApp } from 'vue'
import SupportInboxPage from './SupportInboxPage.vue'
import '@pos/core/styles/tokens.css'
import '@pos/core/styles/app.css'

document.documentElement.dataset.theme = 'light'

createApp(SupportInboxPage).mount('#support-inbox-app')
