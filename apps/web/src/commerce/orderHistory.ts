import { computed, ref } from 'vue'

/*
 * Which orders this browser has placed or tracked.
 *
 * The web twin of apps/mobile/src/storefront/orderHistory.ts, and it exists for
 * the same reason: the API can return an order by id but has no way to list
 * orders "belonging to" someone, because the storefront never authenticates a
 * customer. The id is the only handle, so the browser has to keep the ids.
 *
 * It holds ids and a placed-at snapshot, never a status — status goes stale the
 * moment it's written down, so the account page fetches it live per order.
 */

const STORAGE_KEY = 'sf_order_history'
const MAX_ENTRIES = 40

export interface OrderHistoryEntry {
  orderId: string
  ticketNumber: string
  totalCents: number
  placedAt: string
}

function load(): OrderHistoryEntry[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as OrderHistoryEntry[]
    if (!Array.isArray(parsed)) return []
    return parsed.filter((entry) => typeof entry?.orderId === 'string' && entry.orderId.length > 0)
  } catch {
    return []
  }
}

const entries = ref<OrderHistoryEntry[]>(load())

function persist() {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.value))
  } catch {
    // Private mode / quota — the list still works for this session.
  }
}

export function useStorefrontOrderHistory() {
  /**
   * Called both by checkout after placing an order and by the account page
   * when someone tracks an order id by hand — an order tracked on a new phone
   * should join the list there, not be forgotten on reload.
   */
  function remember(order: {
    orderId: string
    ticketNumber: string
    totalCents: number
    placedAt?: string | null
  }) {
    const entry: OrderHistoryEntry = {
      orderId: order.orderId,
      ticketNumber: order.ticketNumber,
      totalCents: order.totalCents,
      placedAt: order.placedAt ?? new Date().toISOString(),
    }
    entries.value = [entry, ...entries.value.filter((e) => e.orderId !== entry.orderId)].slice(0, MAX_ENTRIES)
    persist()
  }

  function forget(orderId: string) {
    entries.value = entries.value.filter((entry) => entry.orderId !== orderId)
    persist()
  }

  const sorted = computed(() =>
    [...entries.value].sort((a, b) => b.placedAt.localeCompare(a.placedAt)),
  )

  return { entries: sorted, remember, forget }
}
