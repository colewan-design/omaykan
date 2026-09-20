import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createBrowserPosRepository, type DataStore } from '@pos/data/index'
import type { CreateOrderInput, OrderSummary } from '@pos/shared/index'

/**
 * The till is online-only: a sale is recorded on the server before it is
 * recorded here, and a refusal leaves nothing behind.
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
    'pos.sync.outbox': [],
  })
  const repository = createBrowserPosRepository({
    store: memory.store,
    sync: { apiBaseUrl: 'https://api.test', organizationSlug: 'demo-coffee', storeCode: 'main' },
  })
  return { repository, data: memory.data }
}

const sale: CreateOrderInput = {
  businessMode: 'coffee-shop',
  orderType: 'takeaway',
  paymentMethod: 'cash',
  tenderedCents: 30000,
  items: [{ productId: 'p1', name: 'Espresso', quantity: 2, unitPriceCents: 12000, lineTotalCents: 24000, taxRate: 0.12 }],
}

describe('ringing up a sale', () => {
  const fetchMock = vi.fn<typeof fetch>()
  let registerResponse: () => Response | Promise<Response>
  const sent: Array<Record<string, unknown>> = []

  beforeEach(() => {
    sent.length = 0
    fetchMock.mockReset()
    registerResponse = () => new Response(JSON.stringify({ orderId: 'o', ticketNumber: 'SRV-1' }), { status: 201 })
    fetchMock.mockImplementation(async (url, init) => {
      const path = String(url)
      if (path.endsWith('/api/register/orders')) {
        sent.push(JSON.parse(String(init?.body)) as Record<string, unknown>)
        return registerResponse()
      }
      if (path.endsWith('/api/sync/push')) {
        return new Response(JSON.stringify({ results: [] }), { status: 200 })
      }
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

  it('records it on the server first, and not in the outbox', async () => {
    const { repository, data } = signedIn()

    const order = await repository.saveOrder(sale)

    expect(sent).toHaveLength(1)
    expect((sent[0].order as Record<string, unknown>).id).toBe(order.id)
    expect(order.ticketNumber).toBe('SRV-1')
    expect(data.get('pos.orders')).toEqual([expect.objectContaining({ id: order.id })])
    expect(data.get('pos.sync.outbox')).toEqual([])
  })

  it('keeps nothing when the server refuses it, and says why', async () => {
    const { repository, data } = signedIn()
    registerResponse = () => new Response(JSON.stringify({
      message: 'Your role cannot give discounts.',
      errors: { order: ['Your role cannot give discounts. Ask a manager to sign in and apply it.'] },
    }), { status: 422 })

    await expect(repository.saveOrder(sale)).rejects.toThrow(
      'Your role cannot give discounts. Ask a manager to sign in and apply it.',
    )
    expect((data.get('pos.orders') as OrderSummary[] | undefined) ?? []).toEqual([])
  })

  it('says so plainly when the server cannot be reached', async () => {
    const { repository, data } = signedIn()
    registerResponse = () => {
      throw new TypeError('Failed to fetch')
    }

    await expect(repository.saveOrder(sale)).rejects.toThrow("Can't reach Omaykan, so nothing was recorded.")
    expect((data.get('pos.orders') as OrderSummary[] | undefined) ?? []).toEqual([])
  })

  it('sends a retried sale under the id it was first sent with', async () => {
    const { repository, data } = signedIn()

    await repository.saveOrder({ ...sale, id: '11111111-1111-4111-8111-111111111111' })
    await repository.saveOrder({ ...sale, id: '11111111-1111-4111-8111-111111111111' })

    expect(sent.map((body) => (body.order as Record<string, unknown>).id)).toEqual([
      '11111111-1111-4111-8111-111111111111',
      '11111111-1111-4111-8111-111111111111',
    ])
    expect(data.get('pos.orders')).toHaveLength(1)
  })
})
