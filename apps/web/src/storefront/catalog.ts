import { collection, getDocs, query, where } from 'firebase/firestore'
import { reactive } from 'vue'
import { demoCategories, demoProducts, type Category, type Product } from '@pos/shared/index'
import { BUSINESS_MODE, ORG_SLUG, db } from '@pos/web/storefront/firebase'

interface FsCategory {
  name: string
}

interface FsProduct {
  categoryId: string | null
  sku: string
  barcode: string
  name: string
  priceCents: number
  compareAtPriceCents?: number | null
  taxRate: number | string
  productType: string
  imageUrl?: string
  unitLabel?: string
  isActive: boolean
  trackInventory: boolean
  businessModes: string[]
  stockQty: number | null
  lowStockThreshold: number | null
}

function mapProduct(id: string, data: FsProduct): Product {
  const stockQty = data.trackInventory && data.stockQty != null ? Number(data.stockQty) : undefined
  return {
    id,
    categoryId: data.categoryId ?? 'uncategorized',
    sku: data.sku,
    barcode: data.barcode ?? '',
    name: data.name,
    priceCents: data.priceCents,
    compareAtPriceCents: data.compareAtPriceCents ?? undefined,
    taxRate: Number(data.taxRate),
    kind: data.productType === 'weighted' ? 'weighted' : 'standard',
    imageUrl: data.imageUrl,
    unitLabel: data.unitLabel,
    businessModes: (data.businessModes ?? []) as Product['businessModes'],
    outOfStock: data.isActive === false || (data.trackInventory && stockQty === 0),
    stockQty,
    lowStockThreshold: data.lowStockThreshold ?? undefined,
  }
}

export interface StorefrontCatalog {
  categories: Category[]
  products: Product[]
}

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

    const remoteCatalog = Promise.all([
      getDocs(collection(db, 'organizations', ORG_SLUG, 'categories')),
      getDocs(
        query(
          collection(db, 'organizations', ORG_SLUG, 'products'),
          where('businessModes', 'array-contains', BUSINESS_MODE),
        ),
      ),
    ]).then(([categoriesSnap, productsSnap]) => {
      const categories = categoriesSnap.docs.map((docSnap) => ({
        id: docSnap.id,
        name: (docSnap.data() as FsCategory).name,
      }))

      const products = productsSnap.docs
        .map((docSnap) => mapProduct(docSnap.id, docSnap.data() as FsProduct))
        .filter((product) => !product.outOfStock)

      return { categories, products }
    })

    const catalog = await Promise.race([remoteCatalog, timeout])
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
