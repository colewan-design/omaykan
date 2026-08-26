import { computed, reactive, watch, type ComputedRef } from 'vue'
import { demoCategories, demoProducts, type Category } from '@pos/shared/index'
import {
  fetchCatalog,
  unfilteredDelivery,
  type StorefrontCatalog,
  type StorefrontProduct,
} from '@pos/web/commerce/api'
import { BUSINESS_MODE } from '@pos/web/commerce/context'
import { useDeliveryAddress } from '@pos/web/commerce/deliveryAddress'

export type { StorefrontCatalog }

function demoStorefrontCatalog(): StorefrontCatalog {
  const products = demoProducts.filter((product) => product.businessModes.includes(BUSINESS_MODE) && !product.outOfStock)
  const categoryIds = new Set(products.map((product) => product.categoryId))
  const categories = demoCategories.filter((category) => categoryIds.has(category.id))
  return { categories, products, delivery: unfilteredDelivery }
}

export async function loadStorefrontCatalog(
  coords?: { lat: number; lng: number } | null,
): Promise<StorefrontCatalog> {
  try {
    const timeout = new Promise<never>((_, reject) => {
      window.setTimeout(() => reject(new Error('catalog-timeout')), 6000)
    })

    // Business-mode filtering, per-store price and availability overrides, and
    // dropping out-of-stock items all happen on the server now. The browser
    // used to do that for itself against org-wide Firestore documents, which
    // meant it never saw a store's own overrides at all. With a delivery
    // address set, the server also drops whatever no branch in range stocks.
    const catalog = await Promise.race([fetchCatalog(coords), timeout])

    // An answer that was filtered by address is authoritative even when it is
    // empty — "nothing delivers here" is the result, and falling through to
    // the demo shelves would contradict it with a page full of products.
    if (catalog.delivery.requested) return catalog

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

const state = reactive<StorefrontCatalogState>({
  categories: [],
  products: [],
  delivery: unfilteredDelivery,
  loading: true,
  error: '',
})

let loadStarted = false

/**
 * Guards against an out-of-order reply: changing the delivery address twice in
 * quick succession fires two fetches, and the first one landing last would
 * leave the page showing a catalog for an address nobody picked.
 */
let latestRequest = 0

function refresh(coords: { lat: number; lng: number } | null) {
  const request = ++latestRequest
  state.loading = true

  loadStorefrontCatalog(coords)
    .then((catalog) => {
      if (request !== latestRequest) return
      state.categories = catalog.categories
      state.products = catalog.products
      state.delivery = catalog.delivery
    })
    .finally(() => {
      if (request === latestRequest) state.loading = false
    })
}

export function useStorefrontCatalog(): StorefrontCatalogState {
  if (!loadStarted) {
    loadStarted = true

    const { coords } = useDeliveryAddress()
    refresh(coords.value)

    // The shelves are a function of the address, so setting or clearing one
    // reloads them rather than filtering what is already on screen: which
    // branch can reach you decides prices and stock, not just which products
    // survive.
    watch(coords, (next) => refresh(next))
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

/** The branches serving the current address — empty until one is picked. */
export function useServingStores() {
  const catalog = useStorefrontCatalog()
  return computed(() => catalog.delivery.stores)
}

export type { StorefrontProduct }
