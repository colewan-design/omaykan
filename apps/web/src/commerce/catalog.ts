import { computed, reactive, type ComputedRef } from 'vue'
import { demoCategories, demoProducts, type Category } from '@pos/shared/index'
import { fetchCatalog, type StorefrontCatalog } from '@pos/web/commerce/api'
import { BUSINESS_MODE } from '@pos/web/commerce/context'

export type { StorefrontCatalog }

function demoStorefrontCatalog(): StorefrontCatalog {
  const products = demoProducts.filter((product) => product.businessModes.includes(BUSINESS_MODE) && !product.outOfStock)
  const categoryIds = new Set(products.map((product) => product.categoryId))
  const categories = demoCategories.filter((category) => categoryIds.has(category.id))
  return { categories, products }
}

export async function loadStorefrontCatalog(): Promise<StorefrontCatalog> {
  try {
    const timeout = new Promise<never>((_, reject) => {
      window.setTimeout(() => reject(new Error('catalog-timeout')), 6000)
    })

    // Business-mode filtering, per-store price and availability overrides, and
    // dropping out-of-stock items all happen on the server now. The browser
    // used to do that for itself against org-wide Firestore documents, which
    // meant it never saw a store's own overrides at all.
    const catalog = await Promise.race([fetchCatalog(), timeout])

    if (catalog.products.length > 0) return catalog
    return demoStorefrontCatalog()
  } catch {
    return demoStorefrontCatalog()
  }
}

interface StorefrontCatalogState extends StorefrontCatalog {
  loading: boolean
  error: string
}

const state = reactive<StorefrontCatalogState>({ categories: [], products: [], loading: true, error: '' })
let loadStarted = false

export function useStorefrontCatalog(): StorefrontCatalogState {
  if (!loadStarted) {
    loadStarted = true
    loadStorefrontCatalog()
      .then((catalog) => {
        state.categories = catalog.categories
        state.products = catalog.products
      })
      .finally(() => {
        state.loading = false
      })
  }
  return state
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
