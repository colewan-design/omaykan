import { createApp } from 'vue'
import RiderPortalPage from './RiderPortalPage.vue'
import '@pos/core/styles/tokens.css'
import './rider.css'

/*
 * The rider portal entry.
 *
 * tokens.css only, not app.css: the portal needs the type scale and the font
 * stack, but app.css paints the POS window — a gradient body, a dark palette —
 * and dragging it in would mean undoing it before anything here could be read.
 *
 * Forced light for the same reason the landing and account entries are: this
 * is one designed surface, and it should not flip to a dark palette because of
 * a phone's OS setting.
 *
 * No Pinia. The session is a module-level reactive object in rider.ts, which
 * is all a portal with one account and no cross-store coordination needs.
 */
document.documentElement.dataset.theme = 'light'

createApp(RiderPortalPage).mount('#rider-app')
