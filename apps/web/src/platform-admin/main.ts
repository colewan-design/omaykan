import { createApp } from 'vue'
import PortalShell from '@pos/web/platform-admin/PortalShell.vue'
import { router } from '@pos/web/platform-admin/router'
import '@pos/core/styles/tokens.css'
import './platform.css'

/*
 * The operator portal entry.
 *
 * tokens.css only, not app.css: the portal needs the type scale and the font
 * stack, but app.css paints the POS window — a gradient body, a dark palette —
 * and dragging it in would mean undoing it before anything here could be read.
 * That is the same call rider/main.ts makes, for the same reason.
 *
 * Forced light, like the landing, account and rider entries: this is one
 * designed surface, and it should not flip palette because of an OS setting.
 *
 * No Pinia. The session is a module-level reactive object in admin.ts, which is
 * all a portal with one account needs.
 */
document.documentElement.dataset.theme = 'light'

createApp(PortalShell).use(router).mount('#platform-admin-app')
