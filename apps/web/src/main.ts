import { createPosApp } from '@pos/core/app/createPosApp'
import { createBrowserPosRepository } from '@pos/data/index'
import { defaultSettings } from '@pos/shared/index'
import {
  claimLocalCacheFor,
  consumePendingInitialSettings,
  readStaffTenant,
  resolveApiBaseUrl,
  SYNC_SEEDED_KEY,
} from '@pos/web/tenantBinding'

async function bootstrap() {
  const boundTenant = readStaffTenant()
  const organizationSlug = boundTenant?.organizationSlug ?? import.meta.env.VITE_POS_ORGANIZATION_SLUG
  const storeCode = boundTenant?.storeCode ?? import.meta.env.VITE_POS_STORE_CODE

  // Wipes the local cache if this browser was last bound to another tenant.
  if (boundTenant) {
    await claimLocalCacheFor(boundTenant)
  }

  const repository = createBrowserPosRepository({
    sync: {
      apiBaseUrl: resolveApiBaseUrl(),
      organizationSlug,
      storeCode,
      deviceName: import.meta.env.VITE_POS_DEVICE_NAME,
      platform: 'web',
      appVersion: import.meta.env.VITE_POS_APP_VERSION ?? '0.1.0',
    },
  })

  const pendingSettings = consumePendingInitialSettings()
  if (pendingSettings) {
    // One business per store owner, for now: seed the store's settings from
    // what they entered on the signup form, so they don't land on empty
    // defaults and have to redo it in Settings. Queued by
    // src/onboarding/OnboardingPage.vue only on signup (never when an existing
    // account signs in), and applied at most once.
    await repository.saveSettings({
      ...defaultSettings,
      businessName: pendingSettings.businessName,
      businessMode: pendingSettings.businessMode,
      storefrontSlug: organizationSlug ?? '',
    })
    window.localStorage.setItem(SYNC_SEEDED_KEY, '1')
  } else if (organizationSlug && storeCode && !window.localStorage.getItem(SYNC_SEEDED_KEY)) {
    // A freshly bound browser learns its shop's storefront address. The
    // shop's own identity lives on the server and is left alone here.
    const current = await repository.loadSettings()
    await repository.saveSettings({
      ...current,
      storefrontSlug: current.storefrontSlug || organizationSlug,
    })
    window.localStorage.setItem(SYNC_SEEDED_KEY, '1')
  }

  const app = createPosApp({ repository })
  app.mount('#app')
}

void bootstrap()
