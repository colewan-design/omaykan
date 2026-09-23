import { clearLocalPosCache } from '@pos/data/index'
import type { BusinessMode } from '@pos/shared/index'

// Client-side override for which organization/store the staff app (app.html)
// talks to. Absent by default — src/main.ts falls back to the build-time
// VITE_POS_ORGANIZATION_SLUG/VITE_POS_STORE_CODE env vars, which is the
// production deployment's single hardcoded tenant. This key only gets set
// after a browser explicitly signs up or signs in via src/onboarding, so
// existing browsers/deployments are completely unaffected.
export const STAFF_TENANT_STORAGE_KEY = 'pos_staff_tenant'

export interface StaffTenant {
  organizationSlug: string
  storeCode: string
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
      ? { organizationSlug: parsed.organizationSlug, storeCode: parsed.storeCode }
      : null
  } catch {
    return null
  }
}

// The local cache (packages/data's storageKeys: session, users, roles,
// settings, catalog, ...) is one global bucket per browser, not partitioned
// per organization/store. A browser that only ever uses the build-time
// VITE_POS_ORGANIZATION_SLUG/STORE_CODE tenant never needs this, because it's
// the same tenant forever. But once a browser opts into multi-tenancy (signs up
// or signs in via /seller/signup), switching which tenant it's bound to must
// wipe that cache first, or a stale session/catalog from whatever tenant this
// browser used previously leaks into the new one.
const CACHE_OWNER_KEY = 'pos_cache_tenant_owner'
// Set once a freshly bound browser has been given its shop's storefront
// address, so it is only done the first time.
export const SYNC_SEEDED_KEY = 'pos_sync_seeded'

/**
 * Make the local cache belong to `tenant`, wiping it if it belonged to another.
 *
 * Run by /app at boot, and by /seller/signup before it writes a session into
 * the cache. The second is why this is shared: a session written first would
 * be wiped by /app's boot a moment later, because the cache would still name
 * the previous tenant as its owner.
 */
export async function claimLocalCacheFor(tenant: StaffTenant) {
  const tenantId = `${tenant.organizationSlug}/${tenant.storeCode}`
  if (window.localStorage.getItem(CACHE_OWNER_KEY) === tenantId) return

  await clearLocalPosCache()
  window.localStorage.setItem(CACHE_OWNER_KEY, tenantId)
  window.localStorage.removeItem(SYNC_SEEDED_KEY)
}

// The app is served by the same origin as the Laravel API in production, so the
// origin is the right default; an env override covers split-host dev setups.
export function resolveApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE ?? import.meta.env.VITE_ONLINE_ORDER_API_BASE
  const trimmed = typeof configured === 'string' ? configured.trim() : ''
  return (trimmed || window.location.origin).replace(/\/+$/, '')
}

// One business per store owner, for now: whatever they entered on the
// signup form (business name/type) becomes that store's actual settings on
// first boot, instead of landing on empty defaults they'd have to redo in
// Settings. Only written on signup, never when an existing account signs in —
// a store that already exists already has its own settings, and a second
// browser opening it must not reset them.
const PENDING_INITIAL_SETTINGS_KEY = 'pos_staff_pending_settings'

export interface PendingInitialSettings {
  businessName: string
  businessMode: BusinessMode
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
      ? { businessName: parsed.businessName, businessMode: parsed.businessMode }
      : null
  } catch {
    return null
  }
}

