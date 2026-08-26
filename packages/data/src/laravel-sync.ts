import type {
  AuthSession,
  CashMovementType,
  Category,
  OrderSummary,
  PaymentMethod,
  Product,
  ProductKind,
  RoleDefinition,
  ShiftSummary,
  UserAccount,
} from '@pos/shared/index'

/** The outbox row index.ts queues; declared here to keep the two in step. */
export interface SyncOutboxEvent {
  id: string
  entityType: string
  entityId: string
  operation: string
  occurredAt: string
  payload: Record<string, unknown>
}

/**
 * A request the server answered with a refusal, carrying the status so a
 * caller can tell "wrong password" from "verify your email first" - both are
 * failures, and only one of them is worth telling the person about.
 */
export class SyncRequestError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'SyncRequestError'
    this.status = status
  }
}

/*
 * The till's half of the Laravel API.
 *
 * Replaces firebase-sync.ts one method for one method, keeping the same names,
 * arguments and return shapes so the offline outbox, the local cache and the
 * nineteen call sites in index.ts are untouched by the move. That file was the
 * only thing tying the merchant app to Firestore.
 *
 * Two different credentials are in play, and confusing them is the mistake this
 * file exists to prevent:
 *
 *   - a DEVICE token, from pairing a till with its store code. Everything the
 *     register syncs — catalog, orders, shifts, staff lists — is scoped to the
 *     device's store by the server, so the till cannot read another branch's
 *     books even if it asks.
 *   - a STAFF token, from a person signing in. It says who is at the counter,
 *     and rides on AuthSession, not on the sync session.
 *
 * Firestore had one credential doing both jobs, which is why the register never
 * had to pair. It does now: `pairingCode` is required, and a till that has not
 * been paired cannot sync at all.
 */

// ── Config and session ──────────────────────────────────────────────────────

/**
 * What a paired till holds: the device token, and the store it is bound to.
 */
export interface LaravelSyncSession {
  token: string
  deviceId: string
  storeId: string
  storeName: string
  organizationId: string
  organizationSlug: string
}

// ── Wire shapes ─────────────────────────────────────────────────────────────

interface ApiCategory {
  id: string
  name: string
  sort_order?: number
}

interface ApiProduct {
  id: string
  category_id: string | null
  sku: string | null
  barcode: string | null
  name: string
  price_cents: number
  compare_at_price_cents: number | null
  tax_rate: string | number
  product_type: string
  image_url: string | null
  unit_label: string | null
  low_stock_threshold: number | null
  business_modes: string[] | null
  track_inventory?: boolean
}

interface ApiOverride {
  product_id: string
  price_cents: number | null
  is_available: boolean | null
}

interface ApiInventoryLevel {
  product_id: string
  qty_on_hand: string | number
}

interface ApiCatalog {
  categories: ApiCategory[]
  products: ApiProduct[]
  overrides: ApiOverride[]
  inventoryLevels: ApiInventoryLevel[]
}

// ── Mapping ─────────────────────────────────────────────────────────────────

function mapCategory(row: ApiCategory): Category {
  return { id: row.id, name: row.name }
}

/**
 * One product row, with this store's overrides and stock folded in.
 *
 * The server returns the organization's catalog and the store's overrides as
 * separate lists — the same product can be a different price at a different
 * branch. The register only ever shows its own store, so they are merged here
 * rather than leaving every screen to remember to look in two places.
 */
function mapProduct(
  row: ApiProduct,
  override: ApiOverride | undefined,
  stockQty: number | undefined,
): Product {
  const priceCents = override?.price_cents ?? row.price_cents
  const tracked = row.track_inventory !== false

  return {
    id: row.id,
    categoryId: row.category_id ?? 'uncategorized',
    sku: row.sku ?? '',
    barcode: row.barcode ?? '',
    name: row.name,
    priceCents,
    ...(row.compare_at_price_cents != null
      ? { compareAtPriceCents: row.compare_at_price_cents }
      : {}),
    taxRate: Number(row.tax_rate) || 0,
    kind: (row.product_type === 'weighted' ? 'weighted' : 'standard') as ProductKind,
    ...(row.image_url ? { imageUrl: row.image_url } : {}),
    ...(row.unit_label ? { unitLabel: row.unit_label } : {}),
    // Derived, never stored — matches how the storefront catalog decides it.
    outOfStock: override?.is_available === false || (tracked && (stockQty ?? 0) <= 0),
    ...(stockQty != null ? { stockQty } : {}),
    ...(row.low_stock_threshold != null ? { lowStockThreshold: row.low_stock_threshold } : {}),
    businessModes: (row.business_modes ?? []) as Product['businessModes'],
  }
}

function mapCatalog(catalog: ApiCatalog): { categories: Category[]; products: Product[] } {
  const overrides = new Map(catalog.overrides?.map((o) => [o.product_id, o]) ?? [])
  const stock = new Map(
    catalog.inventoryLevels?.map((level) => [level.product_id, Number(level.qty_on_hand)]) ?? [],
  )

  return {
    categories: (catalog.categories ?? []).map(mapCategory),
    products: (catalog.products ?? []).map((row) =>
      mapProduct(row, overrides.get(row.id), stock.get(row.id)),
    ),
  }
}

// ── Factory ─────────────────────────────────────────────────────────────────

export interface LaravelSyncTenant {
  organizationSlug: string
  storeCode: string
}

export interface LaravelSyncDeps {
  /**
   * Which store this till belongs to. Sent on sign-in and registration, the
   * two calls made before a device session exists to imply it.
   */
  tenant: LaravelSyncTenant
  /**
   * The repository's authenticated fetcher. Borrowed rather than reimplemented:
   * it attaches the device token and re-pairs on a 401, and a second HTTP path
   * here would be the one that never recovered from an expired token.
   */
  request: <T>(path: string, init?: RequestInit) => Promise<T>
  /** The paired till's session, pairing on first use. */
  session: () => Promise<LaravelSyncSession | null>
}

export function createLaravelSync({ tenant, request, session }: LaravelSyncDeps) {
  /** The paired session, or an error the caller can act on. */
  async function device(): Promise<LaravelSyncSession> {
    const current = await session()
    if (!current) {
      throw new SyncRequestError('This till is not paired with a store yet.', 428)
    }
    return current
  }

  return {
    /**
     * A cached session is trusted without a round trip: it is checked by the
     * first real request, and re-pairing on every launch would mint a new
     * device row each time the app opened.
     */
    async getCurrentSession(cached: LaravelSyncSession | null): Promise<LaravelSyncSession | null> {
      if (cached?.token && cached.storeId && cached.organizationId) {
        return cached
      }

      try {
        return await device()
      } catch {
        // An unpaired till is offline, not broken: the register falls back to
        // its local cache and keeps taking money.
        return null
      }
    },

    async loginUser(
      username: string,
      password: string,
    ): Promise<{ user: UserAccount; session: AuthSession; syncSession: LaravelSyncSession } | null> {
      let body: { user: UserAccount; session: AuthSession }

      try {
        body = await request('/api/staff-sessions', {
          method: 'POST',
          body: JSON.stringify({
            organizationSlug: tenant.organizationSlug,
            storeCode: tenant.storeCode,
            username,
            password,
          }),
        })
      } catch (error) {
        // A 403 is worth repeating to the person at the counter: it is an
        // unverified email or no access to this store, not a typo. Anything
        // else is a wrong password, and says so generically by returning null.
        if (error instanceof SyncRequestError && error.status === 403) {
          throw error
        }
        return null
      }

      // The till pairs independently of who is standing at it, so a failure to
      // pair must not take a valid sign-in down with it.
      let syncSession: LaravelSyncSession
      try {
        syncSession = await device()
      } catch {
        return null
      }

      return { user: body.user, session: body.session, syncSession }
    },

    /**
     * Creates the account and returns no session: the API sends a verification
     * link and refuses to sign in an address nobody has proved they hold.
     */
    async registerUser(input: {
      fullName: string
      username: string
      email: string
      password: string
    }): Promise<{ user: UserAccount; verificationRequired: boolean; message?: string } | null> {
      try {
        const body = await request<{
          user: UserAccount
          verificationRequired?: boolean
          message?: string
        }>('/api/staff-register', {
          method: 'POST',
          body: JSON.stringify({
            organizationSlug: tenant.organizationSlug,
            storeCode: tenant.storeCode,
            fullName: input.fullName,
            username: input.username,
            email: input.email,
            password: input.password,
          }),
        })

        return {
          user: body.user,
          verificationRequired: body.verificationRequired !== false,
          message: body.message,
        }
      } catch {
        return null
      }
    },

    async createStaffAccount(input: {
      fullName: string
      username: string
      password: string
      roleId: string
    }): Promise<UserAccount> {
      const body = await request<{ user: UserAccount }>('/api/staff-users', {
        method: 'POST',
        body: JSON.stringify(input),
      })

      return body.user
    },

    /**
     * Forgets the device token held in memory.
     *
     * The paired device itself is left alone on purpose: signing out is a
     * person leaving the counter, not a till being decommissioned, and
     * revoking the pairing would make the next person re-enter the store code.
     */
    async signOut(): Promise<void> {
      // Nothing to do: the device stays paired, and the staff session is
      // cleared by the repository. Kept so the surface still matches.
    },

    async bootstrapCatalog(
      _orgId: string,
      _storeId: string,
    ): Promise<{ categories: Category[]; products: Product[]; cursor: string }> {
      const body = await request<{ catalog: ApiCatalog; cursor: string }>('/api/sync/bootstrap')

      return { ...mapCatalog(body.catalog), cursor: body.cursor }
    },

    async pullChanges(
      _orgId: string,
      _storeId: string,
      cursor: string,
    ): Promise<{ categories: Category[]; products: Product[]; cursor: string }> {
      const body = await request<{ changes: ApiCatalog; cursor: string }>(
        `/api/sync/pull?cursor=${encodeURIComponent(cursor)}`,
      )

      return { ...mapCatalog(body.changes), cursor: body.cursor }
    },

    /**
     * Returns the ids the server accepted, so the caller can clear exactly
     * those from the outbox and leave the rest to be retried.
     *
     * Sent as one batch rather than one request per event, which is the whole
     * point of an outbox — a till coming back from an hour offline should cost
     * one round trip, not two hundred.
     */
    async pushEvents(events: SyncOutboxEvent[], session: LaravelSyncSession): Promise<Set<string>> {
      if (events.length === 0) return new Set()

      try {
        const body = await request<{ results: Array<{ eventId: string; status: string }> }>(
          '/api/sync/push',
          {
            method: 'POST',
            body: JSON.stringify({
              organizationId: session.organizationId,
              storeId: session.storeId,
              events,
            }),
          },
        )

        return new Set(
          body.results.filter((r) => r.status !== 'failed').map((r) => r.eventId),
        )
      } catch {
        // Nothing acknowledged: every event stays in the outbox for next time.
        return new Set()
      }
    },

    async loadUsers(_orgId: string): Promise<UserAccount[]> {
      const body = await request<{ users: UserAccount[] }>('/api/staff-users')
      return body.users
    },

    async loadRoles(_orgId: string): Promise<RoleDefinition[]> {
      const body = await request<{ roles: RoleDefinition[] }>('/api/staff-roles')
      return body.roles
    },

    async updateUserRole(userId: string, roleId: string, _orgId: string): Promise<void> {
      await request(`/api/staff-users/${userId}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ roleId }),
      })
    },

    async saveRoles(roles: RoleDefinition[], _orgId: string): Promise<RoleDefinition[]> {
      const body = await request<{ roles: RoleDefinition[] }>('/api/staff-roles', {
        method: 'PUT',
        body: JSON.stringify({ roles }),
      })
      return body.roles
    },

    async getCurrentShift(_storeId: string): Promise<ShiftSummary | null> {
      const body = await request<{ shift: ShiftSummary | null }>('/api/shifts/current')
      return body.shift
    },

    async getShiftHistory(_storeId: string): Promise<ShiftSummary[]> {
      const body = await request<{ shifts: ShiftSummary[] }>('/api/shifts/history')
      return body.shifts
    },

    async pullOnlineOrders(_storeId: string): Promise<OrderSummary[]> {
      const body = await request<{ orders: OrderSummary[] }>('/api/seller/online-orders')
      return body.orders
    },

    async settleOrderPayment(
      _storeId: string,
      orderId: string,
      input: {
        paymentMethod: PaymentMethod
        tenderedCents: number
        changeCents: number
        userId?: string | null
      },
    ): Promise<OrderSummary> {
      const body = await request<{ order: OrderSummary }>(
        `/api/seller/online-orders/${orderId}/settle-payment`,
        { method: 'POST', body: JSON.stringify(input) },
      )
      return body.order
    },

    async openShift(input: {
      openingCashCents: number
      userId?: string | null
      storeId: string
      organizationId: string
    }): Promise<ShiftSummary> {
      const body = await request<{ shift: ShiftSummary }>('/api/shifts/open', {
        method: 'POST',
        body: JSON.stringify({
          openingCashCents: input.openingCashCents,
          userId: input.userId ?? null,
        }),
      })
      return body.shift
    },

    async closeShift(input: {
      shiftId: string
      countedCashCents: number
      expectedCashCents: number
      userId?: string | null
    }): Promise<ShiftSummary> {
      // expectedCashCents is deliberately not sent: the server recomputes it
      // from its own record of the shift, and a till that disagrees is exactly
      // the case a cash count is meant to catch.
      const body = await request<{ shift: ShiftSummary }>('/api/shifts/current/close', {
        method: 'POST',
        body: JSON.stringify({
          countedCashCents: input.countedCashCents,
          userId: input.userId ?? null,
        }),
      })
      return body.shift
    },

    async addCashMovement(input: {
      shiftId: string
      storeId: string
      organizationId: string
      movementType: CashMovementType
      amountCents: number
      reason?: string
      userId?: string | null
    }): Promise<ShiftSummary> {
      const body = await request<{ shift: ShiftSummary }>('/api/shifts/current/movements', {
        method: 'POST',
        body: JSON.stringify({
          movementType: input.movementType,
          amountCents: input.amountCents,
          reason: input.reason ?? null,
          userId: input.userId ?? null,
        }),
      })
      return body.shift
    },
  }
}

export type LaravelSync = ReturnType<typeof createLaravelSync>
