import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createBrowserPosRepository, type DataStore } from '@pos/data/index'

/**
 * Customers leave the till now, and a sale says whose it was and what it
 * spent. See documentation/merchant-features.md §9.
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

function signedIn() {
  const memory = memoryStore({
    'pos.settings': {},
    'pos.sync.session': {
      token: 't', userId: 'user-1', storeId: 'store-1', storeName: 'Demo',
      organizationId: 'org-1', organizationSlug: 'demo-coffee',
    },
    'pos.session': { userId: 'user-1', signedInAt: '', authSource: 'remote' },
  })
  const repository = createBrowserPosRepository({
    store: memory.store,
    sync: { apiBaseUrl: 'https://api.test', organizationSlug: 'demo-coffee', storeCode: 'main' },
  })
  return { repository, data: memory.data }
}

describe('customers and points on the till', () => {
  const fetchMock = vi.fn<typeof fetch>()
  const pushed: Array<{ entityType: string; payload: Record<string, unknown> }> = []

  beforeEach(() => {
    pushed.length = 0
    fetchMock.mockReset()
    fetchMock.mockImplementation(async (url, init) => {
      const path = String(url)
      if (path.endsWith('/api/register/orders')) {
        // A sale goes straight to the server, not through the outbox.
        pushed.push({ entityType: 'order', payload: JSON.parse(String(init?.body)) as Record<string, unknown> })
        return new Response(JSON.stringify({ orderId: 'o', ticketNumber: 'T-1' }), { status: 201 })
      }
      if (path.endsWith('/api/sync/push')) {
        const body = JSON.parse(String(init?.body)) as { events: Array<{ id: string; entityType: string; payload: Record<string, unknown> }> }
        pushed.push(...body.events)
        return new Response(JSON.stringify({ results: body.events.map((event) => ({ eventId: event.id, status: 'applied' })) }), { status: 200 })
      }
      // The catalog pull that follows a push: nothing changed.
      return new Response(JSON.stringify({
        cursor: 'c',
        changes: { categories: [], products: [], overrides: [], inventoryLevels: [], customers: [] },
      }), { status: 200 })
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends a new customer, with their consent to points, to the server', async () => {
    const { repository } = signedIn()

    const customer = await repository.saveCustomer({
      name: 'Rosa', phone: '0917', email: undefined, notes: undefined,
      loyaltyConsentAt: '2026-09-19T08:00:00.000Z',
    })

    const event = pushed.find((entry) => entry.entityType === 'customer')!
    expect(event).toBeDefined()
    expect(event.payload).toMatchObject({ name: 'Rosa', phone: '0917', loyaltyConsentAt: '2026-09-19T08:00:00.000Z', deletedAt: null })
    expect(customer.loyaltyConsentAt).toBe('2026-09-19T08:00:00.000Z')
  })

  it('sends a deletion as a deletion', async () => {
    const { repository } = signedIn()
    const customer = await repository.saveCustomer({ name: 'Rosa', phone: undefined, email: undefined, notes: undefined })
    pushed.length = 0

    await repository.deleteCustomer(customer.id)

    expect(pushed[0].payload.deletedAt).toEqual(expect.any(String))
  })

  it('says whose sale it was and what points it spent', async () => {
    const { repository } = signedIn()

    await repository.saveOrder({
      businessMode: 'coffee-shop',
      customerId: 'cust-1',
      customerName: 'Rosa',
      orderType: 'takeaway',
      paymentMethod: 'cash',
      tenderedCents: 100000,
      items: [{ productId: 'p1', name: 'Espresso', quantity: 2, unitPriceCents: 12000, lineTotalCents: 24000, taxRate: 0.12 }],
      discount: { kind: 'loyalty', amountCents: 3000, points: 30, reason: '30 points' },
    })

    const order = pushed.find((entry) => entry.entityType === 'order')!
    expect((order.payload.order as Record<string, unknown>).customerId).toBe('cust-1')
    expect(order.payload.discounts).toEqual([expect.objectContaining({ kind: 'loyalty', amountCents: 3000, points: 30 })])
    // 24000 − 3000 = 21000; VAT 2520; total 23520.
    expect((order.payload.order as Record<string, unknown>).totalCents).toBe(23520)
  })
})
