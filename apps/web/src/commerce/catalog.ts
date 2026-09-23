import { computed, reactive, type ComputedRef } from 'vue'
import { demoCategories, demoProducts, type Category } from '@pos/shared/index'
import { ApiRequestError, fetchCatalog, type StorefrontCatalog, type StorefrontShop } from '@pos/web/commerce/api'
import { BUSINESS_MODE, DEMO_ORG_SLUG, ORG_SLUG } from '@pos/web/commerce/context'

export type { StorefrontCatalog, StorefrontShop }

const EMPTY: StorefrontCatalog = { shop: null, categories: [], products: [] }

/**
 * Demo products are only ever shown to the demo tenant.
 *
 * They are numbered with slugs rather than the uuids the products table holds,
 * so an order carrying one cannot be priced and checkout fails on it — which
 * is exactly what happened on 2026-08-27, when this fallback ran for the live
 * store and a shopper reached "Place order" with an item nothing could sell.
 * Pinning it to one tenant keeps the demo shelf for showing the product off
 * and takes it out from in front of everyone buying from a real one.
 */
function demoStorefrontCatalog(): StorefrontCatalog {
  if (DEMO_ORG_SLUG === '' || ORG_SLUG !== DEMO_ORG_SLUG) return EMPTY

  const products = demoProducts.filter((product) => product.businessModes.includes(BUSINESS_MODE) && !product.outOfStock)
  const categoryIds = new Set(products.map((product) => product.categoryId))
  const categories = demoCategories.filter((category) => categoryIds.has(category.id))
  // No shop: these products are a sample shelf, not a counter anyone can be
  // named as standing behind.
  return { shop: null, categories, products }
}

/**
 * A full grocery shelf is a few hundred KB of JSON, and this runs on phones on
 * mobile data in Baguio. Six seconds was tight enough that an ordinary slow
 * connection tripped it and fell through to the demo shelf.
 */
const CATALOG_TIMEOUT_MS = 15000

export interface LoadedCatalog extends StorefrontCatalog {
  /** True when the real catalog could not be read, whatever is being shown. */
  failed: boolean
  /**
   * The shop exists and is not trading — suspended, or unpaid. Not a failure:
   * retrying will not help, and the page says "closed" rather than "try again".
   */
  closed: boolean
}

export async function loadStorefrontCatalog(): Promise<LoadedCatalog> {
  try {
    const timeout = new Promise<never>((_, reject) => {
      window.setTimeout(() => reject(new Error('catalog-timeout')), CATALOG_TIMEOUT_MS)
    })

    // Business-mode filtering, per-store price and availability overrides, and
    // dropping out-of-stock items all happen on the server now. The browser
    // used to do that for itself against org-wide Firestore documents, which
    // meant it never saw a store's own overrides at all.
    const catalog = await Promise.race([fetchCatalog(), timeout])

    // An empty answer is a real answer — a shop that has stocked nothing yet
    // is not a failure, and must not be papered over with someone else's
    // products. Only the demo tenant gets those, and only to fill this gap.
    if (catalog.products.length > 0) return { ...catalog, failed: false, closed: false }
    return { ...demoStorefrontCatalog(), failed: false, closed: false }
  } catch (error) {
    // A closed shop gets an empty shelf and never the demo one, even on the
    // demo tenant: a sample shelf on a shop that is not trading would be an
    // invitation to fill a cart nobody can check out.
    if (error instanceof ApiRequestError && error.shopClosed) {
      return { ...EMPTY, failed: false, closed: true }
    }

    return { ...demoStorefrontCatalog(), failed: true, closed: false }
  }
}

interface StorefrontCatalogState extends StorefrontCatalog {
  loading: boolean
  error: string
  closed: boolean
}

const state = reactive<StorefrontCatalogState>({
  shop: null,
  categories: [],
  products: [],
  loading: true,
  error: '',
  closed: false,
})
let loadStarted = false

function runCatalogLoad(): void {
  state.loading = true
  state.error = ''
  loadStorefrontCatalog()
    .then((catalog) => {
      state.shop = catalog.shop
      state.categories = catalog.categories
      state.products = catalog.products
      state.closed = catalog.closed
      // Only worth saying when there is nothing to show for it: the demo
      // tenant still has its shelf, and an empty shop is its own message.
      state.error = catalog.failed && catalog.products.length === 0
        ? 'We could not load the shop just now.'
        : ''
      // A timeout that latched would leave the shelf empty for the rest of the
      // visit, because the load only ever runs once. Releasing the latch on a
      // failure is what lets "Try again" — and the next surface to ask for the
      // catalog — actually re-fetch it.
      if (state.error !== '') loadStarted = false
    })
    .finally(() => {
      state.loading = false
    })
}

export function useStorefrontCatalog(): StorefrontCatalogState {
  if (!loadStarted) {
    loadStarted = true
    runCatalogLoad()
  }
  return state
}

/**
 * Re-fetch after a failed load.
 *
 * The catalog is a module-level singleton loaded once on first use, so without
 * this a single slow response — 15s is easy to exceed when the API is busy —
 * left every surface showing an empty shelf until a full page reload.
 */
export function retryStorefrontCatalog(): void {
  if (state.loading) return
  loadStarted = true
  runCatalogLoad()
}

// Categories with nothing in them are noise in the header bar and in the
// category grid alike — an org can keep a category around after its last
// product is deactivated. Shared so both surfaces list exactly the same set.
export function useStockedCategories(): ComputedRef<Category[]> {
  const catalog = useStorefrontCatalog()
  return computed(() =>
    catalog.categories.filter((category) => catalog.products.some((product) => product.categoryId === category.id)),
  )
}
