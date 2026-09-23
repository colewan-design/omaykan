import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createBrowserPosRepository, TenantAccessError, type DataStore, type TenantAccessStatus } from '@pos/data/index'

/**
 * The till's half of "may this shop trade".
 *
 * The till is offline-first — a sale is rung up locally and pushed later — so
 * the server refusing the push cannot stop one by itself. These cover the
 * local half: learning the verdict, refusing a sale on it, keeping the
 * session through it, and learning when it is lifted.
 *
 * See documentation/subscription-and-suspension.md §5.
 */

const STORE_ID = 'store-1'

function memoryStore(seed: Record<string, unknown> = {}): DataStore {
  const data = new Map<string, unknown>(Object.entries(seed))
  return {
    async read<T>(key: string, fallback: T) {
      return (data.has(key) ? data.get(key) : fallback) as T
    },
    async write<T>(key: string, value: T) {
      data.set(key, value)
    },
  }
}

function signedInRepository() {
  return createBrowserPosRepository({
    store: memoryStore({
      'pos.settings': {},
      'pos.sync.session': {
        token: 'staff-token',
        userId: 'user-1',
        storeId: STORE_ID,
        storeName: 'Demo Coffee',
        organizationId: 'org-1',
        organizationSlug: 'demo-coffee',
      },
    }),
    sync: { apiBaseUrl: 'https://api.test', organizationSlug: 'demo-coffee', storeCode: 'main' },
  })
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function storesSaying(tenantAccess: string) {
  return json(200, { stores: [{ id: STORE_ID, tenantAccess }] })
}

const saleInput = {
  orderType: 'takeaway',
  items: [],
  paymentMethod: 'cash',
  tenderedCents: 0,
  changeCents: 0,
} as unknown as Parameters<ReturnType<typeof createBrowserPosRepository>['saveOrder']>[0]

describe('tenant access on the till', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('is allowed until the server says otherwise', async () => {
    const repository = signedInRepository()

    expect(await repository.loadTenantAccess()).toEqual({ access: 'allowed', message: null })
  })

  it('learns a suspension from a refusal, keeps the session, and tells listeners', async () => {
    const repository = signedInRepository()
    const heard: TenantAccessStatus[] = []
    repository.onTenantAccessChange((status) => heard.push(status))

    fetchMock.mockResolvedValueOnce(json(403, {
      message: 'This shop has been suspended. Contact support@omaykan.com to sort it out.',
      tenantAccess: 'suspended',
    }))

    const refusal = await repository.openShift({ openingCashCents: 0 }).catch((error: unknown) => error)

    expect(refusal).toBeInstanceOf(TenantAccessError)
    expect((refusal as TenantAccessError).access).toBe('suspended')
    expect((await repository.loadTenantAccess()).access).toBe('suspended')
    expect(heard.map((status) => status.access)).toEqual(['suspended'])

    // 403, not 401: the token is fine, and clearing it would sign the
    // merchant out on the day their shop was suspended.
    expect(await repository.getSyncStoreId()).toBe(STORE_ID)
  })

  it('refuses to ring up a sale while not allowed, without touching the network', async () => {
    const repository = signedInRepository()

    fetchMock.mockResolvedValueOnce(storesSaying('unpaid'))
    await repository.refreshTenantAccess()
    fetchMock.mockClear()

    await expect(repository.saveOrder(saleInput)).rejects.toBeInstanceOf(TenantAccessError)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('learns it has been let back in from an explicit check', async () => {
    const repository = signedInRepository()

    fetchMock.mockResolvedValueOnce(storesSaying('unpaid'))
    expect((await repository.refreshTenantAccess()).access).toBe('unpaid')

    fetchMock.mockResolvedValueOnce(storesSaying('allowed'))
    expect(await repository.refreshTenantAccess()).toEqual({ access: 'allowed', message: null })
  })

  it('keeps the last known answer when the server cannot be reached', async () => {
    const repository = signedInRepository()

    fetchMock.mockResolvedValueOnce(storesSaying('suspended'))
    await repository.refreshTenantAccess()

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    expect((await repository.refreshTenantAccess()).access).toBe('suspended')
  })

  it('treats a reason it does not recognise as an ordinary error', async () => {
    const repository = signedInRepository()

    fetchMock.mockResolvedValueOnce(json(403, { message: 'Nope.', tenantAccess: 'something-new' }))

    const refusal = await repository.openShift({ openingCashCents: 0 }).catch((error: unknown) => error)

    expect(refusal).not.toBeInstanceOf(TenantAccessError)
    expect((await repository.loadTenantAccess()).access).toBe('allowed')
  })
})
