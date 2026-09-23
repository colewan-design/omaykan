import { describe, expect, it } from 'vitest'
import type { Product } from '@pos/shared/index'
import {
  ALL_AISLES,
  applyListing,
  emptyListing,
  narrowingCount,
  pageWindow,
  priceCeiling,
  priceStep,
  readListing,
  sameNarrowing,
  townOf,
  writeListing,
} from '@pos/web/landing/listing'

/*
 * The aisle listing keeps every filter in the URL, so these are the rules a
 * shared link is read back by. A drift between writeListing and readListing
 * would open someone else's link on a different shelf than the one they sent.
 */

function product(overrides: Partial<Product> & Pick<Product, 'id'>): Product {
  return {
    categoryId: 'produce',
    sku: '',
    barcode: '',
    name: overrides.id,
    priceCents: 10000,
    taxRate: 0,
    kind: 'standard',
    businessModes: [],
    ...overrides,
  }
}

const params = (query: string) => new URLSearchParams(query)

describe('readListing', () => {
  it('is null without ?category=, which is the front page', () => {
    expect(readListing(params(''))).toBeNull()
    expect(readListing(params('q=rice'))).toBeNull()
    expect(readListing(params('category='))).toBeNull()
  })

  it('reads ?category=all as every aisle', () => {
    expect(readListing(params(`category=${ALL_AISLES}`))).toEqual(emptyListing())
  })

  it('reads several aisles, once each', () => {
    expect(readListing(params('category=a,b,a'))?.categories).toEqual(['a', 'b'])
  })

  it('reads either end of a price range on its own', () => {
    expect(readListing(params('category=all&price=100-500'))).toMatchObject({ minPeso: 100, maxPeso: 500 })
    expect(readListing(params('category=all&price=-500'))).toMatchObject({ minPeso: null, maxPeso: 500 })
    expect(readListing(params('category=all&price=100-'))).toMatchObject({ minPeso: 100, maxPeso: null })
  })

  it('puts a backwards range the right way round', () => {
    expect(readListing(params('category=all&price=500-100'))).toMatchObject({ minPeso: 100, maxPeso: 500 })
  })

  it('ignores values it does not know', () => {
    const filters = readListing(params('category=all&sort=popular&sold=weight,bulk&page=0&price=abc-'))
    expect(filters).toMatchObject({ sort: 'shelf', soldBy: ['weight'], page: 1, minPeso: null, maxPeso: null })
  })
})

describe('writeListing', () => {
  it('keeps the plain case to the aisle alone', () => {
    const out = params('')
    writeListing(out, emptyListing(['produce']))
    expect(out.toString()).toBe('category=produce')
  })

  it('round-trips through readListing', () => {
    const filters = {
      ...emptyListing(['produce', 'dairy']),
      minPeso: 50,
      maxPeso: 400,
      saleOnly: true,
      soldBy: ['weight' as const],
      sort: 'price-desc' as const,
      page: 3,
    }
    const out = params('')
    writeListing(out, filters)
    expect(readListing(out)).toEqual(filters)
  })
})

describe('applyListing', () => {
  const shelf = [
    product({ id: 'kale', categoryId: 'produce', priceCents: 9000 }),
    product({ id: 'Apple', categoryId: 'produce', priceCents: 15000, compareAtPriceCents: 20000 }),
    product({ id: 'milk', categoryId: 'dairy', priceCents: 9000 }),
    product({ id: 'beef', categoryId: 'meat', priceCents: 42050, kind: 'weighted' }),
  ]
  const ids = (list: Product[]) => list.map((p) => p.id)

  it('keeps the shop order and leaves its input alone', () => {
    const before = ids(shelf)
    expect(ids(applyListing(shelf, emptyListing()))).toEqual(before)
    applyListing(shelf, { ...emptyListing(), sort: 'price-desc' })
    expect(ids(shelf)).toEqual(before)
  })

  it('narrows to the picked aisles', () => {
    expect(ids(applyListing(shelf, emptyListing(['produce', 'meat'])))).toEqual(['kale', 'Apple', 'beef'])
  })

  it('treats both ends of the price range as whole pesos, inclusive', () => {
    expect(ids(applyListing(shelf, { ...emptyListing(), minPeso: 90, maxPeso: 150 }))).toEqual(['kale', 'Apple', 'milk'])
    expect(ids(applyListing(shelf, { ...emptyListing(), minPeso: 420 }))).toEqual(['beef'])
    expect(ids(applyListing(shelf, { ...emptyListing(), maxPeso: 420 }))).toEqual(['kale', 'Apple', 'milk'])
  })

  it('keeps only markdowns when asked', () => {
    expect(ids(applyListing(shelf, { ...emptyListing(), saleOnly: true }))).toEqual(['Apple'])
  })

  it('tells weighed goods from pieces', () => {
    expect(ids(applyListing(shelf, { ...emptyListing(), soldBy: ['weight'] }))).toEqual(['beef'])
    expect(ids(applyListing(shelf, { ...emptyListing(), soldBy: ['piece'] }))).toEqual(['kale', 'Apple', 'milk'])
  })

  it('sorts by price with ties left in shop order', () => {
    expect(ids(applyListing(shelf, { ...emptyListing(), sort: 'price-asc' }))).toEqual(['kale', 'milk', 'Apple', 'beef'])
    expect(ids(applyListing(shelf, { ...emptyListing(), sort: 'price-desc' }))).toEqual(['beef', 'Apple', 'kale', 'milk'])
  })

  it('sorts by name whatever the case', () => {
    expect(ids(applyListing(shelf, { ...emptyListing(), sort: 'name' }))).toEqual(['Apple', 'beef', 'kale', 'milk'])
  })
})

describe('priceCeiling', () => {
  it('rounds the dearest item up to a notch the slider can reach', () => {
    expect(priceCeiling([product({ id: 'a', priceCents: 18000 }), product({ id: 'b', priceCents: 64520 })])).toBe(650)
    expect(priceCeiling([product({ id: 'a', priceCents: 49950 })])).toBe(500)
    expect(priceCeiling([product({ id: 'a', priceCents: 1234500 })])).toBe(12400)
  })

  it('never tops out at zero, so the slider has somewhere to go', () => {
    expect(priceCeiling([])).toBe(priceStep(0))
  })
})

describe('pageWindow', () => {
  it('lists every page when there are few', () => {
    expect(pageWindow(1, 1)).toEqual([1])
    expect(pageWindow(4, 7)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('keeps a full run at either end', () => {
    expect(pageWindow(1, 10)).toEqual([1, 2, 3, 4, 'gap', 10])
    expect(pageWindow(10, 10)).toEqual([1, 'gap', 7, 8, 9, 10])
  })

  it('shows the neighbours of a page in the middle', () => {
    expect(pageWindow(5, 10)).toEqual([1, 'gap', 4, 5, 6, 'gap', 10])
  })

  it('draws a single skipped page rather than an ellipsis', () => {
    expect(pageWindow(4, 10)).toEqual([1, 2, 3, 4, 5, 'gap', 10])
  })
})

describe('narrowing', () => {
  it('counts each thing the sidebar narrows by', () => {
    expect(narrowingCount(emptyListing())).toBe(0)
    expect(narrowingCount({ ...emptyListing(['a', 'b']), maxPeso: 100, saleOnly: true, soldBy: ['weight'] })).toBe(5)
  })

  it('compares aisles as a set and ignores sort and page', () => {
    expect(sameNarrowing(emptyListing(['a', 'b']), { ...emptyListing(['b', 'a']), sort: 'name', page: 4 })).toBe(true)
    expect(sameNarrowing(emptyListing(['a']), { ...emptyListing(['a']), minPeso: 10 })).toBe(false)
  })
})

describe('townOf', () => {
  it('takes the last segment of the street line', () => {
    expect(townOf('Magsaysay Avenue, Baguio City')).toBe('Baguio City')
    expect(townOf('La Trinidad')).toBe('La Trinidad')
  })

  it('is blank when there is no address', () => {
    expect(townOf(undefined)).toBe('')
    expect(townOf(' , ')).toBe('')
  })
})
