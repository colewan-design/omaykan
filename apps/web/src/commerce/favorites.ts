import { computed, ref } from 'vue'
import type { Product } from '@pos/shared/index'
import { UUID, currentShop, type CartShop } from '@pos/web/commerce/cart'

/*
 * The heart on a product card — the web twin of the Android app's
 * SavedProductsStore, and local for the same reason: there is no wishlist
 * table behind the API. Keeping it in the browser means the feature works
 * today and owes the server nothing; if it ever becomes an account feature,
 * this is the one module to repoint.
 *
 * Keyed "orgSlug/storeCode/productId", because a product id is only unique
 * inside its own shop's catalog. Each entry keeps the product as it was when
 * hearted and the shop it came from, so the account page can show it — and
 * put it in the cart against the right shop — without loading that shop's
 * whole shelf.
 */

const STORAGE_KEY = 'sf_saved_products'
const MAX_ENTRIES = 200

export interface SavedProduct {
  key: string
  product: Product
  shop: CartShop
  savedAt: string
}

function keyFor(shop: Pick<CartShop, 'orgSlug' | 'storeCode'>, productId: string): string {
  return `${shop.orgSlug}/${shop.storeCode}/${productId}`
}

function load(): SavedProduct[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? (JSON.parse(raw) as SavedProduct[]) : []
    if (!Array.isArray(parsed)) return []
    // The same rule as the cart: a demo product's slug id can never be ordered,
    // so it is not worth keeping past the visit that showed it.
    return parsed.filter(
      (entry) => typeof entry?.key === 'string' && entry.product?.id && UUID.test(entry.product.id) && entry.shop?.orgSlug,
    )
  } catch {
    return []
  }
}

const entries = ref<SavedProduct[]>(load())

function persist() {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.value))
  } catch {
    // Private mode / quota — the hearts still hold for this session.
  }
}

export function useSavedProducts() {
  const keys = computed(() => new Set(entries.value.map((entry) => entry.key)))

  /** Newest first: the thing hearted a minute ago is the one being looked for. */
  const items = computed(() => [...entries.value].sort((a, b) => b.savedAt.localeCompare(a.savedAt)))
  const count = computed(() => entries.value.length)

  /** Whether a product on the shelf being shown is hearted. */
  function isSaved(productId: string): boolean {
    return keys.value.has(keyFor(currentShop(), productId))
  }

  /** @returns true if the product is now saved, false if this removed it. */
  function toggle(product: Product): boolean {
    const shop = currentShop()
    const key = keyFor(shop, product.id)

    if (keys.value.has(key)) {
      entries.value = entries.value.filter((entry) => entry.key !== key)
      persist()
      return false
    }

    entries.value = [{ key, product, shop, savedAt: new Date().toISOString() }, ...entries.value].slice(0, MAX_ENTRIES)
    persist()
    return true
  }

  function remove(key: string) {
    entries.value = entries.value.filter((entry) => entry.key !== key)
    persist()
  }

  return { items, count, isSaved, toggle, remove }
}
