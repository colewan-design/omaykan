import type { BusinessMode } from '@pos/shared/index'

// Client-side override for which organization/store the staff app (app.html)
// talks to. Absent by default — src/main.ts falls back to the build-time
// VITE_POS_ORGANIZATION_SLUG/VITE_POS_STORE_CODE env vars, which is the
// production deployment's single hardcoded tenant. This key only gets set
// after a browser explicitly signs up or pairs via src/onboarding, so
// existing browsers/deployments are completely unaffected.
export const STAFF_TENANT_STORAGE_KEY = 'pos_staff_tenant'

export interface StaffTenant {
  organizationSlug: string
  storeCode: string
  /**
   * The store's public code, kept because the till pairs with it.
   *
   * The Laravel backend authenticates a register as a *device*, and a device
   * proves which store it belongs to by presenting this. Firestore
   * authenticated as a user instead, which is why the register never used to
   * need one; a binding without it cannot sync at all.
   */
  pairingCode: string
}

export function writeStaffTenant(tenant: StaffTenant) {
  window.localStorage.setItem(STAFF_TENANT_STORAGE_KEY, JSON.stringify(tenant))
}

export function readStaffTenant(): StaffTenant | null {
  try {
    const raw = window.localStorage.getItem(STAFF_TENANT_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<StaffTenant>
    return parsed.organizationSlug && parsed.storeCode
      ? {
          organizationSlug: parsed.organizationSlug,
          storeCode: parsed.storeCode,
          // Empty for a browser bound before pairing was required. main.ts
          // sends those back through /signup rather than failing silently.
          pairingCode: parsed.pairingCode ?? '',
        }
      : null
  } catch {
    return null
  }
}

// One business per store owner, for now: whatever they entered on the
// signup form (business name/type) becomes that store's actual settings on
// first boot, instead of landing on empty defaults they'd have to redo in
// Settings. Only written on signup, not on pairing an existing store — a
// store that already exists already has its own settings, and re-pairing a
// second device for it must not reset them.
const PENDING_INITIAL_SETTINGS_KEY = 'pos_staff_pending_settings'

export interface PendingInitialSettings {
  businessName: string
  businessMode: BusinessMode
  pairingCode: string
}

export function writePendingInitialSettings(settings: PendingInitialSettings) {
  window.localStorage.setItem(PENDING_INITIAL_SETTINGS_KEY, JSON.stringify(settings))
}

// Read-and-clear — applied at most once, right after the signup that queued it.
export function consumePendingInitialSettings(): PendingInitialSettings | null {
  try {
    const raw = window.localStorage.getItem(PENDING_INITIAL_SETTINGS_KEY)
    if (!raw) return null
    window.localStorage.removeItem(PENDING_INITIAL_SETTINGS_KEY)
    const parsed = JSON.parse(raw) as Partial<PendingInitialSettings>
    return parsed.businessName && parsed.businessMode
      ? { businessName: parsed.businessName, businessMode: parsed.businessMode, pairingCode: parsed.pairingCode ?? '' }
      : null
  } catch {
    return null
  }
}
