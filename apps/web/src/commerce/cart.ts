import { computed, reactive } from 'vue'
import { calculateTax } from '@pos/shared/index'
import type { StorefrontProduct } from '@pos/web/commerce/api'

interface CartLine {
  /**
   * The catalog's own shape, which carries the branch the item came off. That
   * is what checkout places the order against — see createOnlineOrder.
   */
  product: StorefrontProduct
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

function loadPersisted(): [string, CartLine][] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as CartLine[]
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((line) => line?.product?.id && Number.isFinite(line.quantity) && line.quantity > 0)
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

export function useStorefrontCart() {
  /** `quantity` is what the product detail page's stepper adds in one go. */
  function add(product: StorefrontProduct, quantity = 1) {
    const step = Math.max(1, Math.floor(quantity))
    const existing = lines.get(product.id)
    // Re-store the product on every add: a persisted line can be carrying a
    // stale snapshot from a previous visit, and this refreshes it from the
    // catalog the customer is actually looking at.
    lines.set(product.id, { product, quantity: (existing?.quantity ?? 0) + step })
    persist()
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

  function clear() {
    lines.clear()
    persist()
  }

  const cartLines = computed(() => Array.from(lines.values()))
  const itemCount = computed(() => cartLines.value.reduce((sum, line) => sum + line.quantity, 0))
  const subtotalCents = computed(() =>
    cartLines.value.reduce((sum, line) => sum + Math.round(line.product.priceCents * line.quantity), 0),
  )
  const taxCents = computed(() =>
    cartLines.value.reduce(
      (sum, line) => sum + calculateTax(Math.round(line.product.priceCents * line.quantity), line.product.taxRate),
      0,
    ),
  )
  const totalCents = computed(() => subtotalCents.value + taxCents.value)

  return { cartLines, itemCount, subtotalCents, taxCents, totalCents, add, decrement, remove, clear }
}
