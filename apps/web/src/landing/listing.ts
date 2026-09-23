import { discountPercent, type Product } from '@pos/shared/index'

// The aisle listing's state: which aisles, what price, what else narrows it,
// how it is ordered and which page is showing. All of it lives in the URL —
// `?category=` is what puts the landing page on its listing face at all — so a
// filtered page is a link that can be shared, and Back walks the filtering
// back out.
//
// Pure on purpose: the landing page owns the address bar and CategoryListing
// owns the drawing, and this is the arithmetic both lean on, pinned down by
// test/listing.spec.ts.

export type SortKey = 'shelf' | 'price-asc' | 'price-desc' | 'name'
export type SoldBy = 'piece' | 'weight'

/**
 * No "Latest", "Popular" or "Top rated": a product carries no date, and
 * nothing here counts orders or reviews. "Shop's order" is the order the shop
 * keeps its own shelf in, which is the order the API returns.
 */
export const SORT_OPTIONS: ReadonlyArray<{ key: SortKey; label: string }> = [
  { key: 'shelf', label: "Shop's order" },
  { key: 'price-asc', label: 'Price: low to high' },
  { key: 'price-desc', label: 'Price: high to low' },
  { key: 'name', label: 'Name: A to Z' },
]

const SORT_KEYS = new Set<string>(SORT_OPTIONS.map((option) => option.key))

/** `?category=all` — the listing with no aisle picked. */
export const ALL_AISLES = 'all'

export interface ListingFilters {
  /** Empty means every aisle. */
  categories: string[]
  /** Whole pesos. Null leaves that end of the range open. */
  minPeso: number | null
  maxPeso: number | null
  saleOnly: boolean
  /** Empty means both. */
  soldBy: SoldBy[]
  sort: SortKey
  /** 1-based. Clamped where it is drawn, since only there is the page count known. */
  page: number
}

export function emptyListing(categories: string[] = []): ListingFilters {
  return { categories, minPeso: null, maxPeso: null, saleOnly: false, soldBy: [], sort: 'shelf', page: 1 }
}

function wholePeso(raw: string | undefined): number | null {
  if (raw === undefined || raw.trim() === '') return null
  const value = Number(raw)
  return Number.isFinite(value) && value >= 0 ? Math.floor(value) : null
}

/** Null when there is no `?category=`: the page is not on its listing face. */
export function readListing(params: URLSearchParams): ListingFilters | null {
  const category = params.get('category')?.trim()
  if (!category) return null

  const aisles = category === ALL_AISLES
    ? []
    : [...new Set(category.split(',').map((id) => id.trim()).filter((id) => id !== ''))]
  const filters = emptyListing(aisles)

  const price = params.get('price')
  if (price) {
    const [min, max] = price.split('-')
    filters.minPeso = wholePeso(min)
    filters.maxPeso = wholePeso(max)
    // A hand-edited ?price=500-100 means the same range, not an empty one.
    if (filters.minPeso !== null && filters.maxPeso !== null && filters.minPeso > filters.maxPeso) {
      [filters.minPeso, filters.maxPeso] = [filters.maxPeso, filters.minPeso]
    }
  }

  filters.saleOnly = params.get('sale') === '1'
  filters.soldBy = [...new Set(
    (params.get('sold') ?? '').split(',').filter((kind): kind is SoldBy => kind === 'piece' || kind === 'weight'),
  )]

  const sort = params.get('sort')
  if (sort !== null && SORT_KEYS.has(sort)) filters.sort = sort as SortKey

  const page = Number(params.get('page'))
  if (Number.isInteger(page) && page > 1) filters.page = page

  return filters
}

/** Writes only what differs from a fresh listing, so the plain case stays `?category=<id>`. */
export function writeListing(params: URLSearchParams, filters: ListingFilters): void {
  params.set('category', filters.categories.length === 0 ? ALL_AISLES : filters.categories.join(','))
  if (filters.minPeso !== null || filters.maxPeso !== null) {
    params.set('price', `${filters.minPeso ?? ''}-${filters.maxPeso ?? ''}`)
  }
  if (filters.saleOnly) params.set('sale', '1')
  if (filters.soldBy.length > 0) params.set('sold', filters.soldBy.join(','))
  if (filters.sort !== 'shelf') params.set('sort', filters.sort)
  if (filters.page > 1) params.set('page', String(filters.page))
}

export function soldByOf(product: Product): SoldBy {
  return product.kind === 'weighted' ? 'weight' : 'piece'
}

/** Filtered and ordered; never reorders or mutates the array it is given. */
export function applyListing(products: readonly Product[], filters: ListingFilters): Product[] {
  const aisles = new Set(filters.categories)
  const kinds = new Set(filters.soldBy)
  const min = filters.minPeso === null ? null : filters.minPeso * 100
  const max = filters.maxPeso === null ? null : filters.maxPeso * 100

  const kept = products.filter((product) =>
    (aisles.size === 0 || aisles.has(product.categoryId))
    && (min === null || product.priceCents >= min)
    && (max === null || product.priceCents <= max)
    && (!filters.saleOnly || discountPercent(product) !== null)
    && (kinds.size === 0 || kinds.has(soldByOf(product))),
  )

  // Array#sort is stable, so equal prices keep the shop's own order.
  switch (filters.sort) {
    case 'price-asc':
      return kept.sort((a, b) => a.priceCents - b.priceCents)
    case 'price-desc':
      return kept.sort((a, b) => b.priceCents - a.priceCents)
    case 'name':
      return kept.sort((a, b) => a.name.localeCompare(b.name, 'en', { sensitivity: 'base' }))
    default:
      return kept
  }
}

/** How far one notch of the price slider moves, for a slider that tops out at `ceiling` pesos. */
export function priceStep(ceiling: number): number {
  if (ceiling <= 500) return 5
  if (ceiling <= 2000) return 10
  if (ceiling <= 10000) return 50
  return 100
}

/**
 * The top of the price slider: the dearest item, rounded up to a notch the
 * slider can land on. Band edges are multiples of every step below them, so
 * rounding up never carries a ceiling into the next band.
 */
export function priceCeiling(products: readonly Product[]): number {
  const topCents = products.reduce((top, product) => Math.max(top, product.priceCents), 0)
  const top = Math.ceil(topCents / 100)
  const step = priceStep(top)
  return Math.max(step, Math.ceil(top / step) * step)
}

/** Sidebar filters in force — the count on the phone's Filters button. */
export function narrowingCount(filters: ListingFilters): number {
  return filters.categories.length
    + (filters.minPeso !== null || filters.maxPeso !== null ? 1 : 0)
    + (filters.saleOnly ? 1 : 0)
    + filters.soldBy.length
}

function sameSet(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false
  const seen = new Set(a)
  return b.every((item) => seen.has(item))
}

/** Same aisles, price and toggles; sort and page are not the sidebar's. */
export function sameNarrowing(a: ListingFilters, b: ListingFilters): boolean {
  return sameSet(a.categories, b.categories)
    && a.minPeso === b.minPeso
    && a.maxPeso === b.maxPeso
    && a.saleOnly === b.saleOnly
    && sameSet(a.soldBy, b.soldBy)
}

/**
 * The page buttons to draw: always the first and last, the current page and
 * its neighbours, and a fuller run at either end so the row keeps its width.
 * A gap of one page is drawn as that page — "1 … 3" would hide a single
 * button behind an ellipsis the same width as it.
 */
export function pageWindow(current: number, total: number): Array<number | 'gap'> {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)

  const keep = new Set([1, total, current - 1, current, current + 1])
  if (current <= 3) [2, 3, 4].forEach((page) => keep.add(page))
  if (current >= total - 2) [total - 3, total - 2, total - 1].forEach((page) => keep.add(page))

  const pages = [...keep].filter((page) => page >= 1 && page <= total).sort((a, b) => a - b)
  const out: Array<number | 'gap'> = []
  pages.forEach((page, i) => {
    if (i > 0) {
      const skipped = page - pages[i - 1]
      if (skipped === 2) out.push(page - 1)
      else if (skipped > 2) out.push('gap')
    }
    out.push(page)
  })
  return out
}

/**
 * The town, not the doorstep: the last segment of a street line such as
 * "Magsaysay Avenue, Baguio City". The same rule ShopDirectory's cards use.
 */
export function townOf(address: string | null | undefined): string {
  const parts = (address ?? '').split(',').map((part) => part.trim()).filter((part) => part !== '')
  return parts.length === 0 ? '' : parts[parts.length - 1]
}
