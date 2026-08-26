import { createPosApp } from '@pos/core/app/createPosApp'
import { clearLocalPosCache, createBrowserPosRepository } from '@pos/data/index'
import { defaultSettings } from '@pos/shared/index'
import { consumePendingInitialSettings, readStaffTenant } from '@pos/web/tenantBinding'

// The local cache (packages/data's storageKeys — session, users, roles,
// settings, catalog, ...) is one global bucket per browser, not partitioned
// per organization/store. A browser that only ever uses the build-time
// VITE_POS_ORGANIZATION_SLUG/STORE_CODE tenant never needs this — it's the
// same tenant forever. But once a browser opts into multi-tenancy (signs up
// or pairs via /signup), switching which tenant it's bound to must wipe
// that cache first, or a stale session/catalog from whatever tenant this
// browser used previously leaks into the new one.
const CACHE_OWNER_KEY = 'pos_cache_tenant_owner'

async function bootstrap() {
  const boundTenant = readStaffTenant()
  const organizationSlug = boundTenant?.organizationSlug ?? import.meta.env.VITE_POS_ORGANIZATION_SLUG
  const storeCode = boundTenant?.storeCode ?? import.meta.env.VITE_POS_STORE_CODE
  const pairingCode = boundTenant?.pairingCode ?? ''

  // A till talks to the Laravel backend as a *device*, and a device proves
  // which store it belongs to with the store's code. There is no build-time
  // fallback for it on purpose: a code baked into the bundle would pair every
  // browser that loads the page to one shop, which is exactly what the move
  // off Firestore is meant to stop.
  //
  // /signup is where a code is entered — either by creating a store or by
  // pairing an existing one — and it binds the tenant before sending the
  // browser back here.
  if (!pairingCode) {
    window.location.replace('/signup')
    return
  }

  if (boundTenant) {
    const activeTenantId = `${organizationSlug}/${storeCode}`
    const cachedTenantId = window.localStorage.getItem(CACHE_OWNER_KEY)
    if (cachedTenantId !== activeTenantId) {
      await clearLocalPosCache()
      window.localStorage.setItem(CACHE_OWNER_KEY, activeTenantId)
    }
  }

  const repository = createBrowserPosRepository({
    sync: {
      // Same origin as the page: nginx proxies /api/ to Laravel. The mobile
      // shells, which are not served from that origin, set a full base URL.
      apiBaseUrl: import.meta.env.VITE_ONLINE_ORDER_API_BASE ?? '',
      organizationSlug,
      storeCode,
      pairingCode,
      deviceName: import.meta.env.VITE_POS_DEVICE_NAME,
      platform: 'web',
      appVersion: import.meta.env.VITE_POS_APP_VERSION ?? '0.1.0',
    },
  })

  // One business per store owner, for now: seed the store's settings from
  // what they entered on the signup form, so they don't land on empty
  // defaults and have to redo it in Settings. Queued by
  // src/onboarding/OnboardingPage.vue only on signup (never on pairing an
  // existing store), and applied at most once.
  const pendingSettings = consumePendingInitialSettings()
  if (pendingSettings) {
    await repository.saveSettings({
      ...defaultSettings,
      businessName: pendingSettings.businessName,
      businessMode: pendingSettings.businessMode,
      pairingCode: pendingSettings.pairingCode,
      syncMode: 'online-sync',
    })
  }

  const app = createPosApp({ repository })
  app.mount('#app')
}

void bootstrap()
