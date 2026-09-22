import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoCategories, demoProducts } from '@pos/shared/index'
import { createBrowserPosRepository, type DataStore } from '@pos/data/index'

/**
 * A till tied to a shop shows that shop's catalog and nothing else — never
 * the bundled demo shelf, which would make a merchant believe their shop has
 * products the server (and the shop's public page) does not.
 */

function memoryStore(seed: Record<string, unknown> = {}) {
  const data = new Map<string, unknown>(Object.entries(seed))
  const store: DataStore = {
    async read<T>(key: string, fallback: T) {
      return (data.has(key) ? data.get(key) : fallback) as T
    },
    async write<T>(key: string, value: T) {
      data.set(key, value)
    },
  }
  return { store, data }
}

const session = {
  token: 't', userId: 'user-1', storeId: 'store-1', storeName: 'Shop',
  organizationId: 'org-1', organizationSlug: 'colewan-market',
}

const realProduct = {
  ...demoProducts[0],
  id: '0b9f3c1e-5d2a-4c1b-9a51-2f0f6a7d9e10',
  categoryId: '6a1d7e22-8c4f-4b3a-9d0e-1c2b3a4d5e6f',
  name: 'House Blend',
}
const realCategory = { id: '6a1d7e22-8c4f-4b3a-9d0e-1c2b3a4d5e6f', name: 'Coffee' }

function tillFor(seed: Record<string, unknown>) {
  const memory = memoryStore({ 'pos.settings': {}, 'pos.sync.session': session, ...seed })
  const repository = createBrowserPosRepository({
    store: memory.store,
    sync: { apiBaseUrl: 'https://api.test', organizationSlug: 'colewan-market', storeCode: 'main' },
  })
  return { repository, data: memory.data }
}

describe('the catalog on a shop till', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('is empty, not the demo shelf, when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => { throw new TypeError('offline') }))
    const { repository } = tillFor({})

    const catalog = await repository.loadCatalog()

    expect(catalog.products).toEqual([])
    expect(catalog.categories).toEqual([])
  })

  it('drops demo products an older build left in the cache', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => new Response(JSON.stringify({
      cursor: 'c2',
      changes: { categories: [], products: [], overrides: [], inventoryLevels: [], customers: [] },
    }), { status: 200 })))
    const { repository, data } = tillFor({
      'pos.sync.cursor': 'c1',
      'pos.products': [...demoProducts, realProduct],
      'pos.categories': [...demoCategories, realCategory],
    })

    const catalog = await repository.loadCatalog()

    expect(catalog.products.map((product) => product.id)).toEqual([realProduct.id])
    expect(catalog.categories.map((category) => category.id)).toEqual([realCategory.id])
    expect((data.get('pos.products') as unknown[]).length).toBe(1)
  })
})
