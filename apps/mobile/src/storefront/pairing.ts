import { ref } from 'vue'
import {
  resolveStoreCode,
  setStorefrontContext,
  type ResolveStoreCodeResult,
} from '@pos/web/storefront/firebase'

// Local-only store pairing — this is a single shared build for every
// business owner (unlike the web deployment, which is one build per org),
// so there's no fixed org/store baked in at build time. The customer types
// in a short code once and this remembers the result, same storage pattern
// as orderHistory.ts.
const STORAGE_KEY = 'sf_store_pairing'

export type StorePairing = ResolveStoreCodeResult

function load(): StorePairing | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StorePairing) : null
  } catch {
    return null
  }
}

// Dev-only convenience: reuse apps/mobile/.env's org/store/businessMode as
// an implicit pairing so local development doesn't require typing a code
// every time. Never used in a production build.
function devDefaultPairing(): StorePairing | null {
  if (!import.meta.env.DEV) return null
  const orgSlug = import.meta.env.VITE_POS_ORGANIZATION_SLUG
  const storeCode = import.meta.env.VITE_POS_STORE_CODE
  const businessMode = import.meta.env.VITE_POS_BUSINESS_MODE
  if (!orgSlug || !storeCode || !businessMode) return null
  return {
    orgSlug,
    storeCode,
    businessMode,
    storeName: orgSlug,
    storeAddress: import.meta.env.VITE_POS_STORE_ADDRESS ?? '',
    storeLat: Number(import.meta.env.VITE_POS_STORE_LAT) || null,
    storeLng: Number(import.meta.env.VITE_POS_STORE_LNG) || null,
  }
}

const current = ref<StorePairing | null>(load() ?? devDefaultPairing())

export function useStorefrontPairing() {
  function apply(pairing: StorePairing) {
    setStorefrontContext(pairing)
  }

  async function resolve(code: string): Promise<void> {
    const result = await resolveStoreCode(code)
    current.value = result
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(result))
    apply(result)
  }

  return { current, apply, resolve }
}
