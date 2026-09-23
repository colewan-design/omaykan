import { computed, reactive, ref } from 'vue'
import { calculateTax, type BusinessMode, type Product } from '@pos/shared/index'
import {
  BUSINESS_MODE,
  ORG_SLUG,
  STORE_ADDRESS,
  STORE_CODE,
  STORE_LAT,
  STORE_LNG,
} from '@pos/web/commerce/context'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'

export interface CartLine {
  product: Product
  quantity: number
}

// Module-level (not per-component) so the cart persists as the customer
// navigates between the catalog and checkout pages. Totals shown here are
// for the customer's convenience only — createOnlineOrder in firebase.ts
// recomputes the authoritative subtotal/tax/total server-side from the real
// product prices, exactly as packages/core/src/stores/pos.ts's cart does for
// the in-person register.
//
// Also mirrored to localStorage, so a refresh — or arriving from the landing
// page, which is a separate Vite entry and therefore a full page load —
// doesn't silently empty the cart. Same storage pattern as orderHistory.ts.
const STORAGE_KEY = 'sf_cart'

// A real product's id is a uuid, because that is what the column holds. The
// bundled demo catalog numbers its products with slugs instead, so a line the
// demo fallback put in the cart is one the server can never price — and the
// cart outlives the visit that created it, so without this it comes back and
// fails checkout on every visit until the shopper clears their own storage.
export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function loadPersisted(): [string, CartLine][] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as CartLine[]
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((line) => line?.product?.id && Number.isFinite(line.quantity) && line.quantity > 0)
      .filter((line) => UUID.test(line.product.id))
      .map((line) => [line.product.id, line])
  } catch {
    return []
  }
}

const lines = reactive(new Map<string, CartLine>(loadPersisted()))

function persist() {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(Array.from(lines.values())))
  } catch {
    // Private mode / quota — the in-memory cart still works for this session.
  }
}

// -- Which shop the basket is from ---------------------------------------------
//
// /cart is its own Vite entry, so it starts with the build-time tenant like any
// other page — but the basket may have been filled from `?shop=` on the landing
// page, and an order posted to the wrong store fails on every line. The shop is
// written down beside the basket when something is added, and the cart page
// points the storefront at it before it mounts (see cart/main.ts).

export interface CartShop {
  orgSlug: string
  storeCode: string
  storeAddress: string
  businessMode: BusinessMode
  storeLat: number | null
  storeLng: number | null
  /** The counter's name, for "From <shop>". Blank on the demo shelf, which has none. */
  name: string
}

const SHOP_KEY = 'sf_cart_shop'

/** The shop the storefront is showing right now. */
export function currentShop(): CartShop {
  return {
    orgSlug: ORG_SLUG,
    storeCode: STORE_CODE,
    storeAddress: STORE_ADDRESS,
    businessMode: BUSINESS_MODE,
    storeLat: STORE_LAT,
    storeLng: STORE_LNG,
    name: useStorefrontCatalog().shop?.name ?? '',
  }
}

function loadShop(): CartShop | null {
  try {
    const raw = window.localStorage.getItem(SHOP_KEY)
    const parsed = raw ? (JSON.parse(raw) as CartShop) : null
    if (!parsed || typeof parsed.orgSlug !== 'string' || !parsed.orgSlug) return null
    if (typeof parsed.storeCode !== 'string' || !parsed.storeCode) return null
    return parsed
  } catch {
    return null
  }
}

const shop = ref<CartShop | null>(loadShop())

function rememberShop(next: CartShop) {
  shop.value = next
  try {
    window.localStorage.setItem(SHOP_KEY, JSON.stringify(next))
  } catch {
    // As above: the basket still knows for this session.
  }
}

/** Read before mount by the cart page; nothing else needs it outside a component. */
export function readCartShop(): CartShop | null {
  return shop.value
}

/**
 * Subtotal, VAT and total for any set of lines — the whole basket, or only the
 * lines ticked for this order on the cart page.
 */
export function totalsFor(of: CartLine[]) {
  const itemCount = of.reduce((sum, line) => sum + line.quantity, 0)
  const subtotalCents = of.reduce((sum, line) => sum + Math.round(line.product.priceCents * line.quantity), 0)
  const taxCents = of.reduce(
    (sum, line) => sum + calculateTax(Math.round(line.product.priceCents * line.quantity), line.product.taxRate),
    0,
  )
  return { itemCount, subtotalCents, taxCents, totalCents: subtotalCents + taxCents }
}

export function useStorefrontCart() {
  /**
   * `quantity` is what the product detail page's stepper adds in one go.
   * `from` is the shop the product is on, when that is not the shop the page is
   * showing — a saved item added from the account page, say.
   */
  function add(product: Product, quantity = 1, from?: CartShop) {
    const step = Math.max(1, Math.floor(quantity))
    const existing = lines.get(product.id)
    // Re-store the product on every add: a persisted line can be carrying a
    // stale snapshot from a previous visit, and this refreshes it from the
    // catalog the customer is actually looking at.
    lines.set(product.id, { product, quantity: (existing?.quantity ?? 0) + step })
    persist()
    rememberShop(from ?? currentShop())
  }

  function decrement(productId: string) {
    const existing = lines.get(productId)
    if (!existing) return
    if (existing.quantity <= 1) {
      lines.delete(productId)
    } else {
      lines.set(productId, { ...existing, quantity: existing.quantity - 1 })
    }
    persist()
  }

  function remove(productId: string) {
    lines.delete(productId)
    persist()
  }

  /** What checkout takes out once an order for some of the lines is in. */
  function removeMany(productIds: string[]) {
    for (const id of productIds) lines.delete(id)
    persist()
  }

  function clear() {
    lines.clear()
    persist()
  }

  /**
   * Swap each line's product snapshot for the shelf's current copy, so the cart
   * shows today's price rather than the one from whenever it was added. The
   * server prices the order from its own rows either way; this only stops the
   * cart disagreeing with the shelf beside it.
   */
  function refresh(products: Product[]) {
    let changed = false
    for (const product of products) {
      const existing = lines.get(product.id)
      if (existing && existing.product !== product) {
        lines.set(product.id, { ...existing, product })
        changed = true
      }
    }
    if (changed) persist()
  }

  /**
   * How many of one product are in the basket, 0 when none.
   *
   * Reads the Map directly rather than scanning cartLines: every product card
   * on a shelf calls this, and a linear scan per card turns rendering a grid
   * of a few hundred into quadratic work.
   */
  function quantityOf(productId: string): number {
    return lines.get(productId)?.quantity ?? 0
  }

  const cartLines = computed(() => Array.from(lines.values()))
  const totals = computed(() => totalsFor(cartLines.value))
  const itemCount = computed(() => totals.value.itemCount)
  const subtotalCents = computed(() => totals.value.subtotalCents)
  const taxCents = computed(() => totals.value.taxCents)
  const totalCents = computed(() => totals.value.totalCents)
  const cartShop = computed(() => shop.value)

  return {
    cartLines,
    cartShop,
    itemCount,
    subtotalCents,
    taxCents,
    totalCents,
    quantityOf,
    add,
    decrement,
    remove,
    removeMany,
    clear,
    refresh,
  }
}
