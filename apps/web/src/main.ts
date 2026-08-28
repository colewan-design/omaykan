import { createPosApp } from '@pos/core/app/createPosApp'
import { clearLocalPosCache, createBrowserPosRepository } from '@pos/data/index'
import { defaultSettings } from '@pos/shared/index'
import { consumePendingInitialSettings, consumePendingPairingCode, readStaffTenant } from '@pos/web/tenantBinding'

// The local cache (packages/data's storageKeys — session, users, roles,
// settings, catalog, ...) is one global bucket per browser, not partitioned
// per organization/store. A browser that only ever uses the build-time
// VITE_POS_ORGANIZATION_SLUG/STORE_CODE tenant never needs this — it's the
// same tenant forever. But once a browser opts into multi-tenancy (signs up
// or pairs via /signup), switching which tenant it's bound to must wipe
// that cache first, or a stale session/catalog from whatever tenant this
// browser used previously leaks into the new one.
const CACHE_OWNER_KEY = 'pos_cache_tenant_owner'
// Set once we've defaulted a freshly bound browser to online sync, so a later
// deliberate switch to local-only in Settings survives reboots.
const SYNC_SEEDED_KEY = 'pos_sync_seeded'

// The app is served by the same origin as the Laravel API in production, so the
// origin is the right default; an env override covers split-host dev setups.
function resolveApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE ?? import.meta.env.VITE_ONLINE_ORDER_API_BASE
  const trimmed = typeof configured === 'string' ? configured.trim() : ''
  return (trimmed || window.location.origin).replace(/\/+$/, '')
}

function resolveConfiguredPairingCode(): string {
  const configured = import.meta.env.VITE_POS_PAIRING_CODE
  return typeof configured === 'string' ? configured.trim() : ''
}

async function bootstrap() {
  const boundTenant = readStaffTenant()
  const organizationSlug = boundTenant?.organizationSlug ?? import.meta.env.VITE_POS_ORGANIZATION_SLUG
  const storeCode = boundTenant?.storeCode ?? import.meta.env.VITE_POS_STORE_CODE
  const configuredPairingCode = resolveConfiguredPairingCode()

  if (boundTenant) {
    const activeTenantId = `${organizationSlug}/${storeCode}`
    const cachedTenantId = window.localStorage.getItem(CACHE_OWNER_KEY)
    if (cachedTenantId !== activeTenantId) {
      await clearLocalPosCache()
      window.localStorage.setItem(CACHE_OWNER_KEY, activeTenantId)
      window.localStorage.removeItem(SYNC_SEEDED_KEY)
    }
  }

  const repository = createBrowserPosRepository({
    sync: {
      apiBaseUrl: resolveApiBaseUrl(),
      organizationSlug,
      storeCode,
      // The production seller app is hard-wired to one tenant; without this
      // public code a fresh browser can sign staff in, but it cannot open the
      // device session needed for sync-backed features like publishing the
      // shop photo to the public directory.
      pairingCode: configuredPairingCode,
      deviceName: import.meta.env.VITE_POS_DEVICE_NAME,
      platform: 'web',
      appVersion: import.meta.env.VITE_POS_APP_VERSION ?? '0.1.0',
    },
  })

  const pendingSettings = consumePendingInitialSettings()
  const pendingPairingCode = consumePendingPairingCode()
  if (pendingSettings) {
    // One business per store owner, for now: seed the store's settings from
    // what they entered on the signup form, so they don't land on empty
    // defaults and have to redo it in Settings. Queued by
    // src/onboarding/OnboardingPage.vue only on signup (never on pairing an
    // existing store), and applied at most once.
    await repository.saveSettings({
      ...defaultSettings,
      businessName: pendingSettings.businessName,
      businessMode: pendingSettings.businessMode,
      pairingCode: pendingSettings.pairingCode,
      syncMode: 'online-sync',
    })
    window.localStorage.setItem(SYNC_SEEDED_KEY, '1')
  } else if (organizationSlug && storeCode && !window.localStorage.getItem(SYNC_SEEDED_KEY)) {
    // The deployed seller app is backend-first: default a freshly bound browser
    // to online sync so staff sign-in reaches the Laravel API. Pairing an
    // existing store also drops its code in here without touching the store's
    // business identity, which lives on the server already.
    const current = await repository.loadSettings()
    const pairingCode = (pendingPairingCode ?? '').trim() || current.pairingCode.trim() || configuredPairingCode
    await repository.saveSettings({
      ...current,
      pairingCode,
      syncMode: 'online-sync',
    })
    window.localStorage.setItem(SYNC_SEEDED_KEY, '1')
  }

  const app = createPosApp({ repository })
  app.mount('#app')
}

void bootstrap()
