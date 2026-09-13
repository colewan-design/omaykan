import {
  calculateTax,
  defaultRoles,
  defaultSettings,
  demoCategories,
  demoProducts,
  guestCustomerName,
  slugTicket,
  type AppEvent,
  type AppEventType,
  type AppSettings,
  type AuthSession,
  type BusinessMode,
  type CashMovementSummary,
  type CashMovementType,
  type CatalogSnapshot,
  type Category,
  type ConversationSummary,
  type ConversationThread,
  type CreateCustomerInput,
  type CreateCategoryInput,
  type CreateOrderInput,
  type CreateProductInput,
  type CreateSupplierInput,
  type CreateTableInput,
  type Customer,
  type DeliveryStage,
  type OrderStatus,
  type OrderSummary,
  type OrderType,
  type PaymentMethod,
  type Product,
  type ReorderMark,
  type RestaurantTable,
  type RoleDefinition,
  type SavedRider,
  type SavedRiderDirectory,
  type ShiftSummary,
  type Supplier,
  type UserAccount,
} from '@pos/shared/index'

export interface DataStore {
  read<T>(key: string, fallback: T): Promise<T>
  write<T>(key: string, value: T): Promise<void>
}

export interface PosRepository {
  loadCatalog(): Promise<CatalogSnapshot>
  loadOrders(): Promise<OrderSummary[]>
  loadCustomers(): Promise<Customer[]>
  loadActiveShift(): Promise<ShiftSummary | null>
  loadShiftHistory(): Promise<ShiftSummary[]>
  saveOrder(input: CreateOrderInput): Promise<OrderSummary>
  updateOrderStatus(orderId: string, status: OrderStatus): Promise<OrderSummary>
  voidOrder(orderId: string, input: { userId?: string | null; reason?: string | null }): Promise<OrderSummary>
  loadOnlineOrders(): Promise<OrderSummary[]>
  settleOrderPayment(
    orderId: string,
    input: { paymentMethod: PaymentMethod; tenderedCents: number; changeCents: number; userId?: string | null },
  ): Promise<OrderSummary>
  /**
   * Online orders only, and API-backed only — they live server-side, not in
   * the offline outbox, so unlike a register sale these need the network.
   */
  updateOnlineOrderStatus(orderId: string, status: OrderStatus): Promise<OrderSummary>
  /**
   * Name the rider carrying an online order.
   *
   * Three shapes, matching the three ways a shop dispatches — `savedRiderId`
   * picks someone off the shop's list (and reaches their app when that row is
   * a platform account), `riderName`/`riderPhone` types one in, and
   * `saveRider` remembers a typed-in one for next time. Sending neither a
   * saved id nor a name is a 422 from the API, not a silent no-op.
   */
  assignOrderRider(
    orderId: string,
    input: {
      savedRiderId?: string | null
      riderName?: string
      riderPhone?: string | null
      saveRider?: boolean
      saveNote?: string | null
    },
  ): Promise<OrderSummary>
  /** Put the order back on the platform board, with nobody assigned. */
  unassignOrderRider(orderId: string): Promise<OrderSummary>
  /** The shop's own riders, plus the ones who have delivered for it before. */
  loadSavedRiders(): Promise<SavedRiderDirectory>
  saveRider(input: {
    riderId?: string | null
    name: string
    phone?: string | null
    note?: string | null
  }): Promise<SavedRider>
  deleteSavedRider(id: string): Promise<void>
  updateOrderDeliveryStage(orderId: string, stage: DeliveryStage): Promise<OrderSummary>
  /**
   * Customer messages. Server-side like online orders, so a local-only store
   * has an empty inbox rather than an error. A shop answers threads; it has no
   * way to start one.
   */
  loadConversations(): Promise<ConversationSummary[]>
  /** Reading a thread is what marks it read for the shop. */
  loadConversation(id: string): Promise<ConversationThread>
  sendConversationMessage(id: string, body: string): Promise<ConversationThread>
  /** For the nav badge. Zero, never a throw, when the API can't be reached. */
  loadUnreadMessageCount(): Promise<number>
  saveCustomer(input: CreateCustomerInput): Promise<Customer>
  updateCustomer(customer: Customer): Promise<Customer>
  deleteCustomer(id: string): Promise<void>
  loadTables(): Promise<RestaurantTable[]>
  saveTable(input: CreateTableInput): Promise<RestaurantTable>
  updateTable(table: RestaurantTable): Promise<RestaurantTable>
  deleteTable(id: string): Promise<void>
  loadSuppliers(): Promise<Supplier[]>
  saveSupplier(input: CreateSupplierInput): Promise<Supplier>
  updateSupplier(supplier: Supplier): Promise<Supplier>
  deleteSupplier(id: string): Promise<void>
  loadReorderMarks(): Promise<ReorderMark[]>
  markReorder(input: {
    productId: string
    supplierId: string | null
    quantity: number
    userId?: string | null
  }): Promise<ReorderMark>
  clearReorderMark(id: string): Promise<void>
  openShift(input: {
    openingCashCents: number
    userId?: string | null
  }): Promise<ShiftSummary>
  addCashMovement(input: {
    movementType: CashMovementType
    amountCents: number
    reason?: string
    userId?: string | null
  }): Promise<ShiftSummary>
  closeShift(input: {
    countedCashCents: number
    userId?: string | null
  }): Promise<ShiftSummary>
  adjustInventory(input: {
    productId: string
    quantityDelta: number
    adjustmentType: 'sale' | 'restock' | 'manual_correction'
    reason?: string
    orderId?: string
  }): Promise<Product | null>
  loadSettings(): Promise<AppSettings>
  saveSettings(settings: AppSettings): Promise<void>
  loadUsers(): Promise<UserAccount[]>
  saveUsers(users: UserAccount[]): Promise<void>
  loginUser(username: string, password: string): Promise<{ user: UserAccount; session: AuthSession } | null>
  registerUser(input: {
    fullName: string
    username: string
    password: string
  }): Promise<{ user: UserAccount; session: AuthSession } | null>
  createStaffAccount(input: {
    fullName: string
    username: string
    password: string
    roleId: string
  }): Promise<UserAccount>
  updateUserRole(userId: string, roleId: string): Promise<void>
  loadRoles(): Promise<RoleDefinition[]>
  saveRoles(roles: RoleDefinition[]): Promise<void>
  loadSession(): Promise<AuthSession | null>
  saveSession(session: AuthSession | null): Promise<void>
  // The paired store's id, for subscribing to its live (Reverb) channel. Null
  // until a backend device session exists (local-only, or not yet paired).
  getSyncStoreId(): Promise<string | null>
  loadAppEvents(): Promise<AppEvent[]>
  trackAppEvent(input: {
    eventType: AppEventType
    payload: Record<string, unknown>
  }): Promise<AppEvent>
  saveProduct(input: CreateProductInput): Promise<Product>
  updateProduct(product: Product): Promise<Product>
  deleteProduct(id: string): Promise<void>
  saveCategory(input: CreateCategoryInput): Promise<Category>
  updateCategory(category: Category): Promise<Category>
  deleteCategory(id: string): Promise<void>
}

export interface BrowserPosRepositoryOptions {
  store?: DataStore
  sync?: Partial<SyncConfig>
}

interface SyncConfig {
  apiBaseUrl: string
  organizationSlug: string
  storeCode: string
  deviceName: string
  platform: string
  appVersion: string
}

/**
 * The token every backend call rides on.
 *
 * It used to be a *device* session: the till paired once with the shop's code
 * and held a token that belonged to the shop rather than to anyone in it. It is
 * a staff session now — minted by signing in and choosing a store — which is
 * why there is no `deviceId` here and why `ensureRemoteSession` can no longer
 * conjure one. Nothing syncs until somebody signs in.
 */
interface SyncSession {
  token: string
  userId: string
  storeId: string
  storeName: string
  organizationId: string
  organizationSlug: string
}

interface SyncOutboxEvent {
  id: string
  entityType: 'order' | 'category' | 'product' | 'inventory_adjustment' | 'app_event'
  entityId: string
  operation: 'upsert'
  occurredAt: string
  payload: Record<string, unknown>
}

interface BackendCategory {
  id: string
  name: string
  sort_order?: number
}

interface BackendProduct {
  id: string
  category_id: string | null
  sku: string | null
  barcode: string | null
  name: string
  price_cents: number
  tax_rate: string | number
  product_type?: string
  track_inventory?: boolean
  is_active?: boolean
  deleted_at?: string | null
}

interface BackendInventoryLevel {
  product_id: string
  qty_on_hand: string | number
  reorder_level?: string | number | null
}

interface BackendProductOverride {
  product_id: string
  price_cents?: number | null
  is_available?: boolean | null
  display_name?: string | null
}

interface BackendShiftSummary {
  id: string
  openedByUserId?: string | null
  closedByUserId?: string | null
  openingCashCents: number
  closingCashCents?: number | null
  cashSalesCents: number
  totalSalesCents: number
  orderCount: number
  payInsCents: number
  payOutsCents: number
  expectedCashCents: number
  varianceCashCents?: number | null
  openedAt: string
  closedAt?: string | null
  movements: CashMovementSummary[]
}

const storageKeys = {
  orders: 'pos.orders',
  settings: 'pos.settings',
  appEvents: 'pos.app-events',
  deviceId: 'pos.device-id',
  products: 'pos.products',
  categories: 'pos.categories',
  customers: 'pos.customers',
  tables: 'pos.tables',
  suppliers: 'pos.suppliers',
  reorderMarks: 'pos.reorder-marks',
  users: 'pos.users',
  roles: 'pos.roles',
  session: 'pos.session',
  activeShift: 'pos.shift.active',
  shiftHistory: 'pos.shift.history',
  syncSession: 'pos.sync.session',
  syncCursor: 'pos.sync.cursor',
  syncOutbox: 'pos.sync.outbox',
  // A fingerprint of the shop photo this device last got onto the server —
  // not the photo itself, which is a data URL and already stored once.
  publishedStoreImage: 'pos.sync.store-image',
} as const

/**
 * Cheap change-detector for a value too large to keep a second copy of.
 *
 * FNV-1a over the string, with its length alongside. Not a security property:
 * the only question it answers is "is this the same image the server already
 * has", and the cost of a rare false match is one photo that does not update.
 */
function fingerprint(value: string): string {
  let hash = 0x811c9dc5
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193)
  }
  return `${value.length}:${(hash >>> 0).toString(36)}`
}

const guestSessionUserId = '__guest__'

class BrowserLocalStore implements DataStore {
  async read<T>(key: string, fallback: T): Promise<T> {
    const raw = window.localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  }

  async write<T>(key: string, value: T): Promise<void> {
    window.localStorage.setItem(key, JSON.stringify(value))
  }
}

const indexedDbConfig = {
  databaseName: 'pos-offline-store',
  databaseVersion: 1,
  storeName: 'kv',
  migrationKey: 'pos.meta.local-storage-migrated',
} as const

interface KeyValueRecord<T = unknown> {
  key: string
  value: T
}

class BrowserIndexedDbStore implements DataStore {
  private readonly fallbackStore = new BrowserLocalStore()
  private readonly databasePromise: Promise<IDBDatabase> | null
  private migrationPromise: Promise<void> | null = null
  private failed = false

  constructor() {
    this.databasePromise = typeof window !== 'undefined' && 'indexedDB' in window
      ? this.openDatabase()
      : null
  }

  async read<T>(key: string, fallback: T): Promise<T> {
    try {
      await this.ensureReady()
      const value = await this.getValue<T>(key)
      return value ?? fallback
    } catch {
      this.failed = true
      return this.fallbackStore.read(key, fallback)
    }
  }

  async write<T>(key: string, value: T): Promise<void> {
    try {
      await this.ensureReady()
      await this.putValue(key, value)
    } catch {
      this.failed = true
    }

    // Always mirror into localStorage too, not just on failure. If IndexedDB is later
    // reset or evicted by the browser, migrateFromLocalStorage() re-seeds from whatever
    // localStorage holds — that must be kept current, or a reset resurrects a snapshot
    // frozen at whatever this key held before IndexedDB became the primary store (e.g.
    // a shift that has since been closed).
    await this.fallbackStore.write(key, value)
  }

  private async ensureReady() {
    if (this.failed || !this.databasePromise) {
      throw new Error('IndexedDB is unavailable.')
    }

    if (!this.migrationPromise) {
      this.migrationPromise = this.migrateFromLocalStorage()
    }

    await this.migrationPromise
  }

  private openDatabase(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const request = window.indexedDB.open(indexedDbConfig.databaseName, indexedDbConfig.databaseVersion)

      request.onupgradeneeded = () => {
        const database = request.result
        if (!database.objectStoreNames.contains(indexedDbConfig.storeName)) {
          database.createObjectStore(indexedDbConfig.storeName, { keyPath: 'key' })
        }
      }

      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error ?? new Error('Failed to open IndexedDB.'))
    })
  }

  private createTransaction(mode: IDBTransactionMode) {
    if (!this.databasePromise) {
      throw new Error('IndexedDB is unavailable.')
    }

    return this.databasePromise.then((database) => database.transaction(indexedDbConfig.storeName, mode))
  }

  private async getValue<T>(key: string): Promise<T | undefined> {
    const transaction = await this.createTransaction('readonly')
    const store = transaction.objectStore(indexedDbConfig.storeName)

    return new Promise<T | undefined>((resolve, reject) => {
      const request = store.get(key)

      request.onsuccess = () => {
        const record = request.result as KeyValueRecord<T> | undefined
        resolve(record?.value)
      }
      request.onerror = () => reject(request.error ?? new Error(`Failed to read key "${key}".`))
    })
  }

  private async putValue<T>(key: string, value: T): Promise<void> {
    const transaction = await this.createTransaction('readwrite')
    const store = transaction.objectStore(indexedDbConfig.storeName)

    await new Promise<void>((resolve, reject) => {
      const request = store.put({ key, value } satisfies KeyValueRecord<T>)

      request.onsuccess = () => resolve()
      request.onerror = () => reject(request.error ?? new Error(`Failed to write key "${key}".`))
    })

    await new Promise<void>((resolve, reject) => {
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error ?? new Error(`Transaction failed for "${key}".`))
      transaction.onabort = () => reject(transaction.error ?? new Error(`Transaction aborted for "${key}".`))
    })
  }

  private async migrateFromLocalStorage() {
    const alreadyMigrated = await this.getValue<boolean>(indexedDbConfig.migrationKey)
    if (alreadyMigrated) {
      return
    }

    for (const key of Object.values(storageKeys)) {
      const existingValue = await this.getValue<unknown>(key)
      if (existingValue !== undefined) {
        continue
      }

      const raw = window.localStorage.getItem(key)
      if (raw === null) {
        continue
      }

      try {
        await this.putValue(key, JSON.parse(raw) as unknown)
      } catch {
        // Skip malformed legacy entries rather than blocking startup.
      }
    }

    await this.putValue(indexedDbConfig.migrationKey, true)
  }
}

// Wipes every pos.* key from both localStorage and IndexedDB (the entire
// local cache — session, users, roles, settings, catalog, orders, ...).
// The local store is a single global cache keyed by fixed storageKeys, not
// partitioned per organization/store, so a browser that switches which
// tenant it's bound to (see apps/web/src/tenantBinding.ts) must call this
// first — otherwise a stale session/catalog from whatever tenant this
// browser used previously leaks into the newly bound one (e.g. a leftover
// guest session showing up as "logged in" for a brand-new store).
export async function clearLocalPosCache(): Promise<void> {
  if (typeof window === 'undefined') {
    return
  }

  for (const key of Object.values(storageKeys)) {
    window.localStorage.removeItem(key)
  }
  window.localStorage.removeItem(indexedDbConfig.migrationKey)

  if (!('indexedDB' in window)) {
    return
  }

  await new Promise<void>((resolve) => {
    const request = window.indexedDB.deleteDatabase(indexedDbConfig.databaseName)
    // Best-effort — a stuck delete (onblocked, e.g. another tab still has the
    // DB open) shouldn't prevent the new tenant from booting.
    request.onsuccess = () => resolve()
    request.onerror = () => resolve()
    request.onblocked = () => resolve()
  })
}

function normalizeSyncConfig(input?: Partial<SyncConfig>): SyncConfig | null {
  const cfg = input as Partial<SyncConfig>
  // The pairing code is not required here — staff sign-in only needs the
  // org/store, and device pairing falls back to the persisted settings code.
  if (!cfg?.apiBaseUrl || !cfg.organizationSlug || !cfg.storeCode) {
    return null
  }

  return {
    apiBaseUrl: cfg.apiBaseUrl.replace(/\/+$/, ''),
    organizationSlug: cfg.organizationSlug,
    storeCode: cfg.storeCode,
    deviceName: cfg.deviceName?.trim() || defaultDeviceName(),
    platform: cfg.platform?.trim() || 'web',
    appVersion: cfg.appVersion?.trim() || '0.1.0',
  }
}

// Salted PBKDF2, not a single unsalted SHA-256 round — a leaked users list
// (e.g. from a device backup) can no longer be reversed with a rainbow table,
// and two users with the same password no longer produce the same hash.
const PBKDF2_ITERATIONS = 150_000
const PBKDF2_PREFIX = 'pbkdf2'

function requireSubtleCrypto(): SubtleCrypto {
  if (typeof window === 'undefined' || !window.crypto?.subtle) {
    // No silent plaintext fallback: an insecure context (non-HTTPS, non-Capacitor)
    // must fail loudly rather than store/compare passwords in the clear.
    throw new Error('Password hashing requires a secure context (HTTPS or the packaged app) and is unavailable here.')
  }
  return window.crypto.subtle
}

function bytesToHex(bytes: Uint8Array<ArrayBuffer>): string {
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('')
}

function hexToBytes(hex: string): Uint8Array<ArrayBuffer> {
  const bytes = new Uint8Array(hex.length / 2)
  for (let i = 0; i < bytes.length; i += 1) {
    bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16)
  }
  return bytes
}

async function deriveHash(password: string, salt: Uint8Array<ArrayBuffer>, iterations: number): Promise<string> {
  const subtle = requireSubtleCrypto()
  const keyMaterial = await subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await subtle.deriveBits({ name: 'PBKDF2', salt, iterations, hash: 'SHA-256' }, keyMaterial, 256)
  return bytesToHex(new Uint8Array(bits))
}

async function hashPassword(value: string): Promise<string> {
  const normalized = value.trim()
  requireSubtleCrypto()
  const salt = window.crypto.getRandomValues(new Uint8Array(16))
  const hash = await deriveHash(normalized, salt, PBKDF2_ITERATIONS)
  return `${PBKDF2_PREFIX}$${PBKDF2_ITERATIONS}$${bytesToHex(salt)}$${hash}`
}

// Returns whether `value` matches `stored`, and whether `stored` should be
// rewritten (an iteration-count bump, or a one-time upgrade from the old
// unsalted-SHA-256 format that predates this fix).
async function verifyPassword(value: string, stored: string): Promise<{ valid: boolean; upgradedHash: string | null }> {
  const normalized = value.trim()

  if (stored.startsWith(`${PBKDF2_PREFIX}$`)) {
    const [, iterationsRaw, saltHex, expectedHex] = stored.split('$')
    const iterations = Number(iterationsRaw)
    const candidate = await deriveHash(normalized, hexToBytes(saltHex), iterations)
    const valid = candidate === expectedHex
    const upgradedHash = valid && iterations !== PBKDF2_ITERATIONS ? await hashPassword(normalized) : null
    return { valid, upgradedHash }
  }

  // Legacy accounts: a bare unsalted SHA-256 hex digest. Verify against that
  // scheme once, then transparently upgrade the stored hash on success.
  const subtle = requireSubtleCrypto()
  const legacyDigest = await subtle.digest('SHA-256', new TextEncoder().encode(normalized))
  const valid = bytesToHex(new Uint8Array(legacyDigest)) === stored
  return { valid, upgradedHash: valid ? await hashPassword(normalized) : null }
}

function defaultDeviceName() {
  if (typeof window === 'undefined') {
    return 'Web Register'
  }

  const host = window.location.hostname || 'localhost'
  return `Web Register (${host})`
}

class RemoteAuthError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'RemoteAuthError'
  }
}

async function responseMessage(response: Response, fallback: string): Promise<string> {
  const body = await response.clone().json().catch(() => null)

  if (body && typeof body === 'object') {
    const payload = body as { message?: unknown; error?: unknown }

    if (typeof payload.error === 'string' && payload.error) {
      return payload.error
    }

    if (typeof payload.message === 'string' && payload.message) {
      return payload.message
    }
  }

  const text = (await response.text().catch(() => '')).trim()
  return text || fallback
}

function generateSku(name: string): string {
  const prefix = name.slice(0, 3).toUpperCase().padEnd(3, 'X').replace(/[^A-Z]/g, 'X')
  return `${prefix}-${crypto.randomUUID().slice(0, 4).toUpperCase()}`
}

function toBusinessModes(categoryId: string): Product['businessModes'] {
  if (['groceries', 'produce', 'dairy', 'snacks'].includes(categoryId)) {
    return ['grocery']
  }

  if (['starters', 'mains', 'desserts', 'beverages'].includes(categoryId)) {
    return ['restaurant']
  }

  return ['coffee-shop']
}

function toProductKind(kind?: string): Product['kind'] {
  return kind === 'weighted' ? 'weighted' : 'standard'
}

function mapBackendCategory(category: BackendCategory): Category {
  return {
    id: category.id,
    name: category.name,
  }
}

function mapBackendProduct(
  product: BackendProduct,
  inventoryLevels: Map<string, BackendInventoryLevel>,
  overrides: Map<string, BackendProductOverride>,
): Product {
  const inventory = inventoryLevels.get(product.id)
  const override = overrides.get(product.id)
  const categoryId = product.category_id ?? 'uncategorized'
  const stockQty = inventory ? Number(inventory.qty_on_hand) : undefined
  const lowStockThreshold = inventory?.reorder_level != null ? Number(inventory.reorder_level) : undefined
  const isAvailable = override?.is_available ?? product.is_active ?? true

  return {
    id: product.id,
    categoryId,
    sku: product.sku ?? generateSku(product.name),
    barcode: product.barcode ?? '',
    name: override?.display_name || product.name,
    priceCents: override?.price_cents ?? product.price_cents,
    taxRate: Number(product.tax_rate),
    kind: toProductKind(product.product_type),
    businessModes: toBusinessModes(categoryId),
    outOfStock: isAvailable ? stockQty === 0 : true,
    stockQty,
    lowStockThreshold,
  }
}

function mapBackendShift(shift: BackendShiftSummary): ShiftSummary {
  return {
    id: shift.id,
    openedByUserId: shift.openedByUserId ?? null,
    closedByUserId: shift.closedByUserId ?? null,
    openingCashCents: shift.openingCashCents,
    closingCashCents: shift.closingCashCents ?? null,
    cashSalesCents: shift.cashSalesCents,
    totalSalesCents: shift.totalSalesCents,
    orderCount: shift.orderCount,
    payInsCents: shift.payInsCents,
    payOutsCents: shift.payOutsCents,
    expectedCashCents: shift.expectedCashCents,
    varianceCashCents: shift.varianceCashCents ?? null,
    openedAt: shift.openedAt,
    closedAt: shift.closedAt ?? null,
    movements: shift.movements ?? [],
  }
}

function mergeDemoCatalog(storedProducts: Product[], storedCategories: Category[]) {
  const demoProductMap = new Map(demoProducts.map((p) => [p.id, p]))

  let stockPatched = false
  let imagePatched = false
  let attributionRemoved = false
  const patchedProducts = storedProducts.map((p) => {
    const demo = demoProductMap.get(p.id)
    if (!demo) {
      if (!('imageAttributionUrl' in p)) {
        return p
      }

      attributionRemoved = true
      const { imageAttributionUrl: _imageAttributionUrl, ...rest } = p as Product & { imageAttributionUrl?: string }
      return rest
    }

    let patched = p as Product & { imageAttributionUrl?: string }
    if (p.stockQty === undefined && demo.stockQty !== undefined) {
      stockPatched = true
      patched = { ...patched, stockQty: demo.stockQty, lowStockThreshold: demo.lowStockThreshold }
    }
    if (!patched.imageUrl && demo.imageUrl) {
      imagePatched = true
      patched = { ...patched, imageUrl: demo.imageUrl }
    }
    if ('imageAttributionUrl' in patched) {
      attributionRemoved = true
      const { imageAttributionUrl: _imageAttributionUrl, ...rest } = patched
      patched = rest
    }
    return patched
  })

  const storedProductIds = new Set(storedProducts.map((product) => product.id))
  const newDemoProducts = demoProducts.filter((product) => !storedProductIds.has(product.id))
  const productsChanged = newDemoProducts.length > 0 || stockPatched || imagePatched || attributionRemoved
  const mergedProducts = productsChanged
    ? [...patchedProducts, ...newDemoProducts]
    : storedProducts

  const storedCategoryIds = new Set(storedCategories.map((category) => category.id))
  const newDemoCategories = demoCategories.filter((category) => !storedCategoryIds.has(category.id))
  const mergedCategories = newDemoCategories.length > 0
    ? [...storedCategories, ...newDemoCategories]
    : storedCategories

  return {
    products: mergedProducts,
    categories: mergedCategories,
    addedDemoProducts: productsChanged,
    addedDemoCategories: newDemoCategories.length > 0,
  }
}

interface DemoSeedOrderPlan {
  id: string
  dayOffset: number
  hour: number
  minute: number
  businessMode: BusinessMode
  customerName: string
  paymentMethod: PaymentMethod
  orderType: OrderType
  lineItems: Array<{ productId: string; quantity: number }>
  status?: OrderStatus
  tableNumber?: string | null
  voidAfterMinutes?: number
  voidReason?: string | null
}

const demoProductMap = new Map(demoProducts.map((product) => [product.id, product]))

function demoTimestamp(daysAgo: number, hour: number, minute: number, extraMinutes = 0) {
  const date = new Date()
  date.setDate(date.getDate() - daysAgo)
  date.setHours(hour, minute, 0, 0)
  if (extraMinutes !== 0) {
    date.setMinutes(date.getMinutes() + extraMinutes)
  }
  return date.toISOString()
}

function cashTenderedAmount(totalCents: number) {
  return Math.ceil(totalCents / 5000) * 5000
}

function demoLineItem(productId: string, quantity: number) {
  const product = demoProductMap.get(productId)
  if (!product) {
    throw new Error(`Demo product ${productId} not found.`)
  }

  return {
    productId: product.id,
    name: product.name,
    quantity,
    unitPriceCents: product.priceCents,
    lineTotalCents: Math.round(product.priceCents * quantity),
  }
}

function createDemoOrders(createdByUserId: string | null): OrderSummary[] {
  const plans: DemoSeedOrderPlan[] = [
    {
      id: 'demo0001',
      dayOffset: 0,
      hour: 9,
      minute: 15,
      businessMode: 'coffee-shop',
      customerName: 'Mia Santos',
      paymentMethod: 'cash',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'latte', quantity: 1 },
        { productId: 'glazed-donut', quantity: 2 },
      ],
      status: 'preparing',
    },
    {
      id: 'demo0002',
      dayOffset: 0,
      hour: 10,
      minute: 40,
      businessMode: 'coffee-shop',
      customerName: guestCustomerName,
      paymentMethod: 'card',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'iced-mocha', quantity: 1 },
        { productId: 'sandwich', quantity: 1 },
        { productId: 'lemonade', quantity: 1 },
      ],
      status: 'ready',
    },
    {
      id: 'demo0003',
      dayOffset: 0,
      hour: 13,
      minute: 5,
      businessMode: 'grocery',
      customerName: 'Paolo Reyes',
      paymentMethod: 'ewallet',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'milk', quantity: 2 },
        { productId: 'bread-loaf', quantity: 1 },
        { productId: 'canned-sardines', quantity: 4 },
      ],
      status: 'preparing',
    },
    {
      id: 'demo0004',
      dayOffset: 1,
      hour: 11,
      minute: 20,
      businessMode: 'restaurant',
      customerName: 'Cruz Family',
      paymentMethod: 'card',
      orderType: 'dine_in',
      tableNumber: '4',
      lineItems: [
        { productId: 'chicken-adobo', quantity: 1 },
        { productId: 'steamed-rice', quantity: 2 },
        { productId: 'iced-tea', quantity: 2 },
      ],
      status: 'served',
    },
    {
      id: 'demo0005',
      dayOffset: 1,
      hour: 15,
      minute: 10,
      businessMode: 'coffee-shop',
      customerName: guestCustomerName,
      paymentMethod: 'cash',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'cappuccino', quantity: 1 },
        { productId: 'croissant', quantity: 2 },
      ],
      status: 'served',
    },
    {
      id: 'demo0006',
      dayOffset: 1,
      hour: 17,
      minute: 45,
      businessMode: 'nail-salon',
      customerName: 'Ava Lim',
      paymentMethod: 'ewallet',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'gel-manicure', quantity: 1 },
        { productId: 'french-tip', quantity: 1 },
      ],
      status: 'served',
    },
    {
      id: 'demo0007',
      dayOffset: 2,
      hour: 8,
      minute: 30,
      businessMode: 'grocery',
      customerName: 'Lorenzo Tan',
      paymentMethod: 'cash',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'bananas', quantity: 2 },
        { productId: 'potatoes', quantity: 1 },
        { productId: 'yogurt-cup', quantity: 2 },
      ],
      status: 'served',
    },
    {
      id: 'demo0008',
      dayOffset: 2,
      hour: 12,
      minute: 25,
      businessMode: 'restaurant',
      customerName: guestCustomerName,
      paymentMethod: 'card',
      orderType: 'dine_in',
      tableNumber: '2',
      lineItems: [
        { productId: 'calamari', quantity: 1 },
        { productId: 'grilled-bangus', quantity: 1 },
        { productId: 'calamansi-juice', quantity: 2 },
        { productId: 'leche-flan', quantity: 1 },
      ],
      status: 'served',
    },
    {
      id: 'demo0009',
      dayOffset: 3,
      hour: 9,
      minute: 5,
      businessMode: 'coffee-shop',
      customerName: 'Nina Garcia',
      paymentMethod: 'cash',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'espresso', quantity: 2 },
        { productId: 'banana-bread', quantity: 1 },
      ],
      status: 'served',
    },
    {
      id: 'demo0010',
      dayOffset: 5,
      hour: 14,
      minute: 50,
      businessMode: 'nail-salon',
      customerName: guestCustomerName,
      paymentMethod: 'cash',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'classic-manicure', quantity: 1 },
        { productId: 'cuticle-oil', quantity: 1 },
      ],
      status: 'served',
    },
    {
      id: 'demo0011',
      dayOffset: 8,
      hour: 18,
      minute: 10,
      businessMode: 'restaurant',
      customerName: 'Marco Villanueva',
      paymentMethod: 'ewallet',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'pork-bbq', quantity: 2 },
        { productId: 'steamed-rice', quantity: 2 },
        { productId: 'buko-juice', quantity: 1 },
      ],
      status: 'served',
    },
    {
      id: 'demo0012',
      dayOffset: 10,
      hour: 16,
      minute: 35,
      businessMode: 'grocery',
      customerName: 'Rafael Sy',
      paymentMethod: 'card',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'rice', quantity: 1 },
        { productId: 'soy-sauce', quantity: 1 },
        { productId: 'instant-noodles', quantity: 6 },
      ],
      status: 'served',
      voidAfterMinutes: 22,
      voidReason: 'Duplicate basket',
    },
    {
      id: 'demo0013',
      dayOffset: 12,
      hour: 11,
      minute: 0,
      businessMode: 'coffee-shop',
      customerName: guestCustomerName,
      paymentMethod: 'cash',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'iced-latte', quantity: 1 },
        { productId: 'apple-danish', quantity: 1 },
      ],
      status: 'served',
    },
    {
      id: 'demo0014',
      dayOffset: 15,
      hour: 13,
      minute: 40,
      businessMode: 'restaurant',
      customerName: 'Table 8',
      paymentMethod: 'cash',
      orderType: 'dine_in',
      tableNumber: '8',
      lineItems: [
        { productId: 'beef-caldereta', quantity: 1 },
        { productId: 'steamed-rice', quantity: 3 },
        { productId: 'soda-can', quantity: 3 },
        { productId: 'turon', quantity: 1 },
      ],
      status: 'served',
    },
    {
      id: 'demo0015',
      dayOffset: 20,
      hour: 10,
      minute: 15,
      businessMode: 'coffee-shop',
      customerName: 'Julia Fernandez',
      paymentMethod: 'cash',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'caramel-macchiato', quantity: 1 },
        { productId: 'blueberry-muffin', quantity: 2 },
      ],
      status: 'served',
      voidAfterMinutes: 15,
      voidReason: 'Accidental duplicate charge',
    },
    {
      id: 'demo0016',
      dayOffset: 27,
      hour: 9,
      minute: 45,
      businessMode: 'nail-salon',
      customerName: 'Camille Dela Cruz',
      paymentMethod: 'card',
      orderType: 'takeaway',
      lineItems: [
        { productId: 'spa-pedicure', quantity: 1 },
        { productId: 'paraffin-wax-treatment', quantity: 1 },
      ],
      status: 'served',
    },
  ]

  return plans.map((plan) => {
    const items = plan.lineItems.map((line) => demoLineItem(line.productId, line.quantity))
    const subtotalCents = items.reduce((sum, item) => sum + item.lineTotalCents, 0)
    const taxCents = calculateTax(subtotalCents, 0.12)
    const totalCents = subtotalCents + taxCents
    const tenderedCents = plan.paymentMethod === 'cash' ? cashTenderedAmount(totalCents) : totalCents
    const createdAt = demoTimestamp(plan.dayOffset, plan.hour, plan.minute)

    return {
      id: plan.id,
      ticketNumber: slugTicket(plan.id),
      businessMode: plan.businessMode,
      createdByUserId,
      customerId: null,
      customerName: plan.customerName,
      orderType: plan.orderType,
      tableNumber: plan.tableNumber ?? null,
      status: plan.status ?? 'served',
      paymentMethod: plan.paymentMethod,
      subtotalCents,
      taxCents,
      totalCents,
      tenderedCents,
      changeCents: Math.max(tenderedCents - totalCents, 0),
      createdAt,
      items,
      voidedAt: plan.voidAfterMinutes != null
        ? demoTimestamp(plan.dayOffset, plan.hour, plan.minute, plan.voidAfterMinutes)
        : null,
      voidedByUserId: null,
      voidReason: plan.voidReason ?? null,
    }
  })
}

export function createBrowserPosRepository(options: BrowserPosRepositoryOptions = {}): PosRepository {
  const store = options.store ?? new BrowserIndexedDbStore()
  const syncConfig = normalizeSyncConfig(options.sync)
  const appVersion = syncConfig?.appVersion ?? '0.1.0'

  async function isOnlineSyncEnabled() {
    if (!syncConfig) {
      return false
    }

    const settings = await store.read<Partial<AppSettings>>(storageKeys.settings, defaultSettings)
    return settings.syncMode === 'online-sync'
  }

  async function getDeviceId() {
    const existing = await store.read<string | null>(storageKeys.deviceId, null)
    if (existing) {
      return existing
    }

    const created = crypto.randomUUID()
    await store.write(storageKeys.deviceId, created)
    return created
  }

  async function readSyncSession() {
    return store.read<SyncSession | null>(storageKeys.syncSession, null)
  }

  async function writeSyncSession(session: SyncSession | null) {
    await store.write(storageKeys.syncSession, session)
  }

  async function readOutbox() {
    return store.read<SyncOutboxEvent[]>(storageKeys.syncOutbox, [])
  }

  async function writeOutbox(events: SyncOutboxEvent[]) {
    await store.write(storageKeys.syncOutbox, events)
  }

  async function appendOutboxEvent(event: SyncOutboxEvent) {
    const events = await readOutbox()
    if (events.some((entry) => entry.id === event.id)) {
      return
    }
    await writeOutbox([...events, event])
  }

  async function markAppEventsSent(eventIds: Set<string>) {
    if (eventIds.size === 0) {
      return
    }

    const sentAt = new Date().toISOString()
    const events = await store.read<AppEvent[]>(storageKeys.appEvents, [])
    await store.write(
      storageKeys.appEvents,
      events.map((event) => (
        eventIds.has(event.id) && !event.sentAt
          ? { ...event, sentAt }
          : event
      )),
    )
  }

  async function readSyncCursor() {
    return store.read<string | null>(storageKeys.syncCursor, null)
  }

  async function writeSyncCursor(cursor: string | null) {
    await store.write(storageKeys.syncCursor, cursor)
  }

  async function readActiveShift() {
    const shift = await store.read<ShiftSummary | null>(storageKeys.activeShift, null)
    // A shift with closedAt set has already been closed — never surface it as the active
    // shift, even if a stale copy lingers under the activeShift cache key (e.g. re-seeded
    // from a mirrored snapshot that predates the close).
    if (!shift || shift.closedAt) {
      return null
    }
    // Shifts opened before totalSalesCents/orderCount existed are missing these fields in
    // storage — back-fill totalSalesCents from the cash figure we do have, rather than showing NaN.
    return {
      ...shift,
      totalSalesCents: shift.totalSalesCents ?? shift.cashSalesCents ?? 0,
      orderCount: shift.orderCount ?? 0,
    }
  }

  async function writeActiveShift(shift: ShiftSummary | null) {
    await store.write(storageKeys.activeShift, shift)
  }

  async function refreshRemoteShift() {
    const response = await backendFetch<{ shift: BackendShiftSummary | null }>('/api/shifts/current')
    const shift = response.shift ? mapBackendShift(response.shift) : null
    await writeActiveShift(shift)
    return shift
  }

  async function updateCachedShift(
    updater: (current: ShiftSummary | null) => ShiftSummary | null,
  ) {
    const current = await readActiveShift()
    const next = updater(current)
    await writeActiveShift(next)
    return next
  }

  async function backendFetch<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    if (!syncConfig) {
      throw new Error('Sync configuration is missing.')
    }

    const onlineSyncEnabled = await isOnlineSyncEnabled()
    if (!onlineSyncEnabled) {
      throw new Error('Online sync is disabled.')
    }

    const session = await ensureRemoteSession()
    const response = await fetch(`${syncConfig.apiBaseUrl}${path}`, {
      ...init,
      headers: {
        'Accept': 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
        ...(init.headers ?? {}),
      },
    })

    if (response.status === 401 && retry) {
      await writeSyncSession(null)
      return backendFetch<T>(path, init, false)
    }

    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || `Request failed: ${response.status}`)
    }

    return response.json() as Promise<T>
  }

  let publishingStoreImage = false

  /**
   * Put the shop's own photo — Settings > Business image — where customers can
   * see it.
   *
   * The image lives in AppSettings as a data URL and never had anywhere to go:
   * until now the only things that read it were the receipt header and the
   * settings avatar, both on this device. The storefront directory and the
   * partner carousel on /signup read it off the store record instead, so it
   * has to be pushed.
   *
   * Guarded by a stored fingerprint rather than by "did this save change it",
   * which is what makes an image uploaded long before this existed reach the
   * server the first time the app opens after the update.
   */
  async function publishStoreImage(settings: AppSettings): Promise<void> {
    if (settings.syncMode !== 'online-sync' || publishingStoreImage) {
      return
    }

    const image = settings.businessImageUrl || ''
    const stamp = fingerprint(image)
    if ((await store.read<string>(storageKeys.publishedStoreImage, '')) === stamp) {
      return
    }

    publishingStoreImage = true
    try {
      await backendFetch('/api/seller/store-image', {
        method: 'PUT',
        // An empty image is the owner having pressed Remove, and has to travel
        // as null — the server treats it as "take the picture down".
        body: JSON.stringify({ image: image === '' ? null : image }),
      })
      await store.write(storageKeys.publishedStoreImage, stamp)
    } catch {
      // Offline, unpaired, or refused. The marker is left alone, so the next
      // settings save — or the next time the app opens — tries again.
    } finally {
      publishingStoreImage = false
    }
  }

  /**
   * The session this till is running on, or null.
   *
   * This used to *create* one on demand by pairing with the shop's code, which
   * is why sync could run before anyone had signed in. It cannot now: the token
   * belongs to a person, and only `loginUser` can mint one. A null here is not
   * an error — it is "nobody has signed in yet", and every caller already
   * treats it as "stay offline for the moment".
   */
  async function ensureRemoteSession(): Promise<SyncSession | null> {
    if (!syncConfig) {
      return null
    }

    return readSyncSession()
  }

  /**
   * Sign in, choose this till's store, and keep the token for both jobs.
   *
   * Two calls, because proving who you are does not say which shop you are
   * standing in. The store is picked by the code this build is configured for,
   * so a till stays pinned to its own branch and a manager who covers three
   * does not have to choose on every shift; if that store is not among the ones
   * the account reaches, the sign-in fails rather than quietly opening another.
   *
   * The token it ends with is the same one `ensureRemoteSession` hands to every
   * backend call. There is no longer a second, device-shaped session alongside
   * the person's — the till acts as whoever is signed in, and that is what puts
   * a name on a settled payment and a closed drawer.
   */
  async function signInRemotely(
    credentials: { identifier: string; password: string } | { googleCredential: string },
  ): Promise<{ user: UserAccount; session: AuthSession } | null> {
    const base = syncConfig?.apiBaseUrl
    if (!base) {
      return null
    }

    const endpoint = 'googleCredential' in credentials
      ? '/api/staff/auth/google'
      : '/api/staff/sign-in'

    const response = await fetch(`${base}${endpoint}`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(
        'googleCredential' in credentials
          ? { credential: credentials.googleCredential }
          : credentials,
      ),
    })

    // 403 is the door saying why it is shut — an unverified address, a disabled
    // account, no membership anywhere — and each of those is worth repeating to
    // the person rather than falling back to the local user list, which would
    // let a sacked cashier in on a stale cached account.
    if (response.status === 403) {
      throw new RemoteAuthError(await responseMessage(response, 'This account cannot sign in here.'))
    }

    if (!response.ok) {
      return null
    }

    const signIn = await response.json() as {
      token: string
      user: { id: string; fullName: string; username: string | null; email: string | null }
      stores: Array<{ id: string; code: string; name: string; organizationSlug: string; role: string }>
    }

    const wanted = syncConfig?.storeCode
    const store_ = signIn.stores.find((entry) => entry.code === wanted) ?? null

    if (!store_) {
      throw new RemoteAuthError(
        "This account doesn't have access to this store. Ask an admin to add you in Staff.",
      )
    }

    const chosen = await fetch(`${base}/api/staff/session-store`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${signIn.token}`,
      },
      body: JSON.stringify({ storeId: store_.id }),
    })

    if (!chosen.ok) {
      throw new RemoteAuthError(await responseMessage(chosen, 'Could not open that store.'))
    }

    const body = await chosen.json() as {
      token: string
      user: UserAccount
      store: { id: string; name: string; organizationId: string; organizationSlug: string }
    }

    const session: AuthSession = {
      userId: body.user.id,
      signedInAt: new Date().toISOString(),
      authToken: body.token,
      authSource: 'remote',
    }

    const existingUsers = await store.read<UserAccount[]>(storageKeys.users, [])
    await store.write(storageKeys.users, [
      body.user,
      ...existingUsers.filter((entry) => entry.id !== body.user.id),
    ])
    await store.write(storageKeys.session, session)

    await writeSyncSession({
      token: body.token,
      userId: body.user.id,
      storeId: body.store.id,
      storeName: body.store.name,
      organizationId: body.store.organizationId,
      organizationSlug: body.store.organizationSlug,
    })

    return { user: body.user, session }
  }

  async function syncCatalogFromBootstrap() {
    const response = await backendFetch<{
      catalog: {
        categories: BackendCategory[]
        products: BackendProduct[]
        overrides: BackendProductOverride[]
        inventoryLevels: BackendInventoryLevel[]
      }
      cursor: string
    }>('/api/sync/bootstrap')

    const overrides = new Map(response.catalog.overrides.map((entry) => [entry.product_id, entry]))
    const inventoryLevels = new Map(response.catalog.inventoryLevels.map((entry) => [entry.product_id, entry]))
    const categories = response.catalog.categories.map(mapBackendCategory)
    const products = response.catalog.products.map((entry) => mapBackendProduct(entry, inventoryLevels, overrides))

    await store.write(storageKeys.categories, categories)
    await store.write(storageKeys.products, products)
    await writeSyncCursor(response.cursor)

    return { categories, products }
  }

  async function pullCatalogChanges() {
    const cursor = await readSyncCursor()
    const response = await backendFetch<{
      cursor: string
      changes: {
        categories: BackendCategory[]
        products: BackendProduct[]
        overrides: BackendProductOverride[]
        inventoryLevels: BackendInventoryLevel[]
      }
    }>(`/api/sync/pull${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`)

    const currentCategories = await store.read<Category[]>(storageKeys.categories, [])
    const currentProducts = await store.read<Product[]>(storageKeys.products, [])
    const nextCategories = new Map(currentCategories.map((entry) => [entry.id, entry]))
    const nextProducts = new Map(currentProducts.map((entry) => [entry.id, entry]))

    for (const category of response.changes.categories) {
      nextCategories.set(category.id, mapBackendCategory(category))
    }

    const currentInventory = new Map<string, BackendInventoryLevel>()
    for (const product of currentProducts) {
      if (typeof product.stockQty === 'number') {
        currentInventory.set(product.id, {
          product_id: product.id,
          qty_on_hand: product.stockQty,
          reorder_level: product.lowStockThreshold ?? null,
        })
      }
    }

    for (const inventory of response.changes.inventoryLevels) {
      currentInventory.set(inventory.product_id, inventory)
    }

    const currentOverrides = new Map<string, BackendProductOverride>()
    for (const override of response.changes.overrides) {
      currentOverrides.set(override.product_id, override)
    }

    for (const product of response.changes.products) {
      nextProducts.set(product.id, mapBackendProduct(product, currentInventory, currentOverrides))
    }

    await store.write(storageKeys.categories, Array.from(nextCategories.values()))
    await store.write(storageKeys.products, Array.from(nextProducts.values()))
    await writeSyncCursor(response.cursor)
  }

  async function flushOutbox(): Promise<Set<string>> {
    if (!await isOnlineSyncEnabled()) {
      return new Set()
    }

    const events = await readOutbox()
    if (events.length === 0) {
      return new Set()
    }

    const session = await ensureRemoteSession()
    if (!session) {
      return new Set()
    }

    const response = await backendFetch<{ results: Array<{ eventId: string; status: string }> }>('/api/sync/push', {
      method: 'POST',
      body: JSON.stringify({
        organizationId: session.organizationId,
        storeId: session.storeId,
        events: events.map((event) => ({
          id: event.id,
          entityType: event.entityType,
          entityId: event.entityId,
          operation: event.operation,
          occurredAt: event.occurredAt,
          payload: event.payload,
        })),
      }),
    })

    const successfulIds = new Set(
      response.results
        .filter((result) => result.status === 'applied' || result.status === 'duplicate')
        .map((result) => result.eventId),
    )

    if (successfulIds.size > 0) {
      await writeOutbox(events.filter((event) => !successfulIds.has(event.id)))
      await markAppEventsSent(successfulIds)
    }

    await pullCatalogChanges()
    return successfulIds
  }

  async function maybeSyncCatalog() {
    if (!await isOnlineSyncEnabled()) {
      return null
    }

    const cursor = await readSyncCursor()
    if (!cursor) {
      return syncCatalogFromBootstrap()
    }

    await pullCatalogChanges()
    return {
      categories: await store.read<Category[]>(storageKeys.categories, []),
      products: await store.read<Product[]>(storageKeys.products, []),
    }
  }

  async function loadCachedCatalog() {
    const storedProducts = await store.read<Product[] | null>(storageKeys.products, null)
    const storedCategories = await store.read<Category[] | null>(storageKeys.categories, null)

    if (storedProducts === null || storedCategories === null) {
      await store.write(storageKeys.products, demoProducts)
      await store.write(storageKeys.categories, demoCategories)
      return { products: demoProducts, categories: demoCategories }
    }

    const merged = mergeDemoCatalog(storedProducts, storedCategories)
    if (merged.addedDemoProducts) {
      await store.write(storageKeys.products, merged.products)
    }
    if (merged.addedDemoCategories) {
      await store.write(storageKeys.categories, merged.categories)
    }

    return {
      products: merged.products,
      categories: merged.categories,
    }
  }

  async function enqueueProductEvent(product: Product) {
    await appendOutboxEvent({
      id: crypto.randomUUID(),
      entityType: 'product',
      entityId: product.id,
      operation: 'upsert',
      occurredAt: new Date().toISOString(),
      payload: {
        categoryId: product.categoryId,
        sku: product.sku,
        barcode: product.barcode,
        name: product.name,
        productType: product.kind,
        taxRate: product.taxRate,
        priceCents: product.priceCents,
        trackInventory: product.stockQty !== undefined,
        stockQty: product.stockQty ?? null,
        lowStockThreshold: product.lowStockThreshold ?? null,
        isActive: !product.outOfStock,
        // How the product reads on a storefront. These were absent from the
        // payload entirely, so a photo, a unit label or a markdown set at the
        // counter never left the till — the row saved, synced and listed in
        // the POS exactly as expected, and the storefront showed none of it.
        imageUrl: product.imageUrl ?? null,
        photoUrls: product.photoUrls ?? [],
        brand: product.brand ?? null,
        packagingType: product.packagingType ?? null,
        unitLabel: product.unitLabel ?? null,
        compareAtPriceCents: product.compareAtPriceCents ?? null,
      },
    })
  }

  async function enqueueInventoryAdjustmentEvent(input: {
    productId: string
    quantityDelta: number
    adjustmentType: 'sale' | 'restock' | 'manual_correction'
    reason?: string
    orderId?: string
  }) {
    await appendOutboxEvent({
      id: crypto.randomUUID(),
      entityType: 'inventory_adjustment',
      entityId: crypto.randomUUID(),
      operation: 'upsert',
      occurredAt: new Date().toISOString(),
      payload: {
        productId: input.productId,
        quantityDelta: input.quantityDelta,
        adjustmentType: input.adjustmentType,
        reason: input.reason ?? null,
        orderId: input.orderId ?? null,
      },
    })
  }

  async function enqueueCategoryEvent(category: Category, deletedAt?: string) {
    await appendOutboxEvent({
      id: crypto.randomUUID(),
      entityType: 'category',
      entityId: category.id,
      operation: 'upsert',
      occurredAt: new Date().toISOString(),
      payload: {
        name: category.name,
        sortOrder: 0,
        ...(deletedAt ? { deletedAt } : {}),
      },
    })
  }

  async function enqueueAppTelemetryEvent(event: AppEvent) {
    await appendOutboxEvent({
      id: event.id,
      entityType: 'app_event',
      entityId: event.id,
      operation: 'upsert',
      occurredAt: event.createdAt,
      payload: {
        eventType: event.eventType,
        payload: event.payload,
        deviceId: event.deviceId,
        appVersion: event.appVersion,
        createdAt: event.createdAt,
      },
    })
  }

  async function enqueuePendingAppTelemetryEvents() {
    const events = await store.read<AppEvent[]>(storageKeys.appEvents, [])
    for (const event of events) {
      if (!event.sentAt) {
        await enqueueAppTelemetryEvent(event)
      }
    }
  }

  async function tryLoadRemoteUsers() {
    if (!await isOnlineSyncEnabled()) {
      return null
    }

    const response = await backendFetch<{ users: UserAccount[] }>('/api/staff-users')
    await store.write(storageKeys.users, response.users)
    return response.users
  }

  async function tryLoadRemoteRoles() {
    if (!await isOnlineSyncEnabled()) {
      return null
    }

    const response = await backendFetch<{ roles: RoleDefinition[] }>('/api/staff-roles')
    await store.write(storageKeys.roles, response.roles)
    return response.roles
  }

  function normalizeCustomer(input: Partial<Customer> & Pick<Customer, 'id' | 'name'>): Customer {
    const timestamp = new Date().toISOString()
    return {
      id: input.id,
      name: input.name.trim(),
      phone: input.phone?.trim() || undefined,
      email: input.email?.trim() || undefined,
      notes: input.notes?.trim() || undefined,
      createdAt: input.createdAt ?? timestamp,
      updatedAt: input.updatedAt ?? timestamp,
    }
  }

  function normalizeTable(input: Partial<RestaurantTable> & Pick<RestaurantTable, 'id' | 'floor' | 'label' | 'capacity'>): RestaurantTable {
    const timestamp = new Date().toISOString()
    return {
      id: input.id,
      floor: input.floor.trim(),
      label: input.label.trim(),
      capacity: input.capacity,
      status: input.status ?? 'available',
      guestName: input.guestName?.trim() || null,
      guestCount: input.guestCount ?? null,
      seatedAt: input.seatedAt ?? null,
      createdAt: input.createdAt ?? timestamp,
      updatedAt: input.updatedAt ?? timestamp,
    }
  }

  function normalizeOrder(order: OrderSummary): OrderSummary {
    return {
      ...order,
      businessMode: order.businessMode || 'coffee-shop',
      createdByUserId: 'createdByUserId' in order ? order.createdByUserId ?? null : null,
      customerId: 'customerId' in order ? order.customerId ?? null : null,
      customerName:
        'customerName' in order && typeof order.customerName === 'string' && order.customerName.trim()
          ? order.customerName.trim()
          : guestCustomerName,
      tableNumber: 'tableNumber' in order ? order.tableNumber ?? null : null,
      // Orders saved before status tracking existed have already been fulfilled — treat them as done
      // rather than surfacing them as freshly "preparing" in the Track Order panel.
      status: order.status ?? 'served',
    }
  }

  return {
    async loadCatalog() {
      try {
        const synced = await maybeSyncCatalog()
        if (synced) {
          return synced
        }
      } catch {
        // Fall back to local cache when the backend is unavailable.
      }

      return loadCachedCatalog()
    },

    async loadOrders() {
      try {
        await flushOutbox()
      } catch {
        // Keep the POS usable even when sync fails.
      }

      const currentSession = await store.read<AuthSession | null>(storageKeys.session, null)
      let orders = await store.read<OrderSummary[]>(storageKeys.orders, [])
      if (orders.length === 0 && !(await isOnlineSyncEnabled())) {
        orders = createDemoOrders(currentSession?.userId ?? null)
        await store.write(storageKeys.orders, orders)
      } else if (!(await isOnlineSyncEnabled())) {
        const demoOrdersById = new Map(
          createDemoOrders(currentSession?.userId ?? null).map((order) => [order.id, order] as const),
        )
        const patchedOrders = orders.map((order) => {
          if (!order.id.startsWith('demo')) {
            return order
          }

          const seededOrder = demoOrdersById.get(order.id)
          if (!seededOrder) {
            return currentSession?.userId && order.createdByUserId !== currentSession.userId
              ? { ...order, createdByUserId: currentSession.userId }
              : order
          }

          const nextCreatedByUserId = currentSession?.userId ?? order.createdByUserId ?? null
          if (order.status === seededOrder.status && order.createdByUserId === nextCreatedByUserId) {
            return order
          }

          return {
            ...order,
            status: seededOrder.status,
            createdByUserId: nextCreatedByUserId,
          }
        })
        if (JSON.stringify(patchedOrders) !== JSON.stringify(orders)) {
          orders = patchedOrders
          await store.write(storageKeys.orders, orders)
        }
      }
      return orders
        .map((order) => normalizeOrder(order))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    },

    async loadCustomers() {
      const customers = await store.read<Customer[]>(storageKeys.customers, [])
      return customers
        .map((customer) => normalizeCustomer(customer))
        .filter((customer) => customer.name.length > 0)
        .sort((a, b) => a.name.localeCompare(b.name, 'en', { sensitivity: 'base' }))
    },

    async loadTables() {
      const tables = await store.read<RestaurantTable[]>(storageKeys.tables, [])
      return tables.map((table) => normalizeTable(table))
    },

    async loadActiveShift() {
      if (await isOnlineSyncEnabled()) {
        try {
          return await refreshRemoteShift()
        } catch {
          // Fall back to cached active shift if the backend is unavailable.
        }
      }

      return readActiveShift()
    },

    async loadShiftHistory() {
      if (await isOnlineSyncEnabled()) {
        try {
          const response = await backendFetch<{ shifts: BackendShiftSummary[] }>('/api/shifts/history')
          const shifts = response.shifts.map(mapBackendShift)
          await store.write(storageKeys.shiftHistory, shifts)
          return shifts
        } catch {
          // Fall back to the cached history if the backend is unavailable.
        }
      }

      return store.read<ShiftSummary[]>(storageKeys.shiftHistory, [])
    },

    async saveOrder(input) {
      const subtotalCents = input.items.reduce((sum, item) => sum + item.lineTotalCents, 0)
      const taxCents = Math.round(subtotalCents * 0.12)
      const totalCents = subtotalCents + taxCents
      const orderId = crypto.randomUUID()
      const ticketSeed = crypto.randomUUID()
      const order: OrderSummary = {
        id: orderId,
        ticketNumber: slugTicket(ticketSeed),
        businessMode: input.businessMode,
        createdByUserId: (await store.read<AuthSession | null>(storageKeys.session, null))?.userId ?? null,
        customerId: input.customerId ?? null,
        customerName: input.customerName?.trim() || guestCustomerName,
        orderType: input.orderType,
        tableNumber: input.tableNumber?.trim() || null,
        status: 'preparing',
        paymentMethod: input.paymentMethod,
        subtotalCents,
        taxCents,
        totalCents,
        tenderedCents: input.tenderedCents,
        changeCents: Math.max(input.tenderedCents - totalCents, 0),
        createdAt: new Date().toISOString(),
        items: input.items,
      }

      const orders = await store.read<OrderSummary[]>(storageKeys.orders, [])
      await store.write(storageKeys.orders, [order, ...orders])

      await appendOutboxEvent({
        id: crypto.randomUUID(),
        entityType: 'order',
        entityId: order.id,
        operation: 'upsert',
        occurredAt: order.createdAt,
        payload: {
          order: {
            id: order.id,
            ticketNumber: order.ticketNumber,
            orderType: order.orderType,
            tableNumber: order.tableNumber,
            status: order.status,
            paymentStatus: 'paid',
            subtotalCents: order.subtotalCents,
            taxCents: order.taxCents,
            totalCents: order.totalCents,
            businessDate: order.createdAt.slice(0, 10),
            completedAt: order.createdAt,
          },
          items: order.items.map((item) => ({
            id: crypto.randomUUID(),
            productId: item.productId,
            productName: item.name,
            quantity: item.quantity,
            unitPriceCents: item.unitPriceCents,
            lineTotalCents: item.lineTotalCents,
          })),
          payments: [{
            id: crypto.randomUUID(),
            paymentMethod: order.paymentMethod,
            amountCents: order.totalCents,
            tenderedCents: order.tenderedCents,
            changeCents: order.changeCents,
          }],
        },
      })

      try {
        await flushOutbox()
      } catch {
        // Order stays queued until the next sync attempt.
      }

      await updateCachedShift((current) => {
        if (!current || current.closedAt) {
          return current
        }

        const isCash = order.paymentMethod === 'cash'
        return {
          ...current,
          cashSalesCents: current.cashSalesCents + (isCash ? order.totalCents : 0),
          expectedCashCents: current.expectedCashCents + (isCash ? order.totalCents : 0),
          totalSalesCents: current.totalSalesCents + order.totalCents,
          orderCount: current.orderCount + 1,
        }
      })

      return order
    },

    async updateOrderStatus(orderId, status) {
      const orders = await store.read<OrderSummary[]>(storageKeys.orders, [])
      const index = orders.findIndex((entry) => entry.id === orderId)
      if (index === -1) {
        throw new Error(`Order ${orderId} not found.`)
      }

      const updated = normalizeOrder({ ...orders[index], status })
      const next = orders.slice()
      next[index] = updated
      await store.write(storageKeys.orders, next)
      return updated
    },

    async voidOrder(orderId, input) {
      const orders = await store.read<OrderSummary[]>(storageKeys.orders, [])
      const index = orders.findIndex((entry) => entry.id === orderId)
      if (index === -1) {
        throw new Error(`Order ${orderId} not found.`)
      }
      if (orders[index].voidedAt) {
        return orders[index]
      }

      const updated = normalizeOrder({
        ...orders[index],
        voidedAt: new Date().toISOString(),
        voidedByUserId: input.userId ?? null,
        voidReason: input.reason?.trim() || null,
      })
      const next = orders.slice()
      next[index] = updated
      await store.write(storageKeys.orders, next)

      // Mirrors saveOrder()'s shift-tally update above, with the same
      // limitation: orders aren't tagged with the shift they were rung on, so
      // this only reverses against whichever shift is open right now — a
      // no-op if none is open, or if the original shift has since closed.
      await updateCachedShift((current) => {
        if (!current || current.closedAt) {
          return current
        }

        const isCash = updated.paymentMethod === 'cash'
        return {
          ...current,
          cashSalesCents: current.cashSalesCents - (isCash ? updated.totalCents : 0),
          expectedCashCents: current.expectedCashCents - (isCash ? updated.totalCents : 0),
          totalSalesCents: current.totalSalesCents - updated.totalCents,
          orderCount: Math.max(0, current.orderCount - 1),
        }
      })

      return updated
    },

    // Storefront orders are written server-side, not by this device — unlike
    // in-person sales they are not in the local cache/outbox, so they need
    // their own pull, and a local-only store has no storefront to receive them
    // from at all. The storefront POSTs to Laravel, so the backend is where
    // today's orders are.
    async loadOnlineOrders() {
      if (!(await isOnlineSyncEnabled())) {
        return []
      }

      try {
        const payload = await backendFetch<{ orders: OrderSummary[] }>('/api/seller/online-orders')
        return (payload.orders ?? []).map(normalizeOrder)
      } catch {
        // Don't blank the dashboard on a transient sync hiccup.
        return []
      }
    },

    async settleOrderPayment(orderId, input) {
      const payload = await backendFetch<{ order: OrderSummary }>(
        `/api/seller/online-orders/${encodeURIComponent(orderId)}/settle-payment`,
        { method: 'POST', body: JSON.stringify(input) },
      )
      return normalizeOrder(payload.order)
    },

    // backendFetch throws without a sync config, and the dashboard shows that
    // message on the card rather than looking like the rider was told.
    async updateOnlineOrderStatus(orderId, status) {
      const payload = await backendFetch<{ order: OrderSummary }>(
        `/api/seller/online-orders/${encodeURIComponent(orderId)}/status`,
        { method: 'POST', body: JSON.stringify({ status }) },
      )
      return normalizeOrder(payload.order)
    },

    async assignOrderRider(orderId, input) {
      const payload = await backendFetch<{ order: OrderSummary }>(
        `/api/seller/online-orders/${encodeURIComponent(orderId)}/rider`,
        { method: 'POST', body: JSON.stringify(input) },
      )
      return normalizeOrder(payload.order)
    },

    async unassignOrderRider(orderId) {
      const payload = await backendFetch<{ order: OrderSummary }>(
        `/api/seller/online-orders/${encodeURIComponent(orderId)}/rider`,
        { method: 'DELETE' },
      )
      return normalizeOrder(payload.order)
    },

    // Not cached and not mirrored into IndexedDB, unlike the catalog. A saved
    // rider's `status` and `online` are only true at the moment they are read —
    // a stale copy would offer a suspended rider as available, which is worse
    // than a spinner.
    async loadSavedRiders() {
      return backendFetch<SavedRiderDirectory>('/api/seller/riders')
    },

    async saveRider(input) {
      const payload = await backendFetch<{ savedRider: SavedRider }>('/api/seller/riders', {
        method: 'POST',
        body: JSON.stringify(input),
      })
      return payload.savedRider
    },

    async deleteSavedRider(id) {
      await backendFetch<{ deleted: boolean }>(`/api/seller/riders/${encodeURIComponent(id)}`, {
        method: 'DELETE',
      })
    },

    async updateOrderDeliveryStage(orderId, stage) {
      const payload = await backendFetch<{ order: OrderSummary }>(
        `/api/seller/online-orders/${encodeURIComponent(orderId)}/delivery-stage`,
        { method: 'POST', body: JSON.stringify({ stage }) },
      )
      return normalizeOrder(payload.order)
    },

    // Not cached: a conversation is only worth reading as it is now, and a
    // stale copy would show a customer's question as unanswered after a
    // colleague already answered it.
    async loadConversations() {
      if (!(await isOnlineSyncEnabled())) {
        return []
      }
      const payload = await backendFetch<{ conversations: ConversationSummary[] }>('/api/seller/conversations')
      return payload.conversations ?? []
    },

    async loadConversation(id) {
      return backendFetch<ConversationThread>(`/api/seller/conversations/${encodeURIComponent(id)}`)
    },

    async sendConversationMessage(id, body) {
      return backendFetch<ConversationThread>(
        `/api/seller/conversations/${encodeURIComponent(id)}/messages`,
        { method: 'POST', body: JSON.stringify({ body }) },
      )
    },

    async loadUnreadMessageCount() {
      if (!(await isOnlineSyncEnabled())) {
        return 0
      }
      try {
        const payload = await backendFetch<{ unread: number }>('/api/seller/conversations/unread')
        return payload.unread ?? 0
      } catch {
        return 0
      }
    },

    async saveCustomer(input) {
      const timestamp = new Date().toISOString()
      const customer = normalizeCustomer({
        id: crypto.randomUUID(),
        name: input.name,
        phone: input.phone,
        email: input.email,
        notes: input.notes,
        createdAt: timestamp,
        updatedAt: timestamp,
      })

      const customers = await store.read<Customer[]>(storageKeys.customers, [])
      await store.write(storageKeys.customers, [customer, ...customers])
      return customer
    },

    async updateCustomer(customer) {
      const customers = await store.read<Customer[]>(storageKeys.customers, [])
      const existing = customers.find((entry) => entry.id === customer.id)
      const nextCustomer = normalizeCustomer({
        ...customer,
        createdAt: existing?.createdAt ?? customer.createdAt,
        updatedAt: new Date().toISOString(),
      })

      await store.write(
        storageKeys.customers,
        customers.map((entry) => (entry.id === nextCustomer.id ? nextCustomer : entry)),
      )

      const orders = await store.read<OrderSummary[]>(storageKeys.orders, [])
      await store.write(
        storageKeys.orders,
        orders.map((order) =>
          order.customerId === nextCustomer.id
            ? { ...normalizeOrder(order), customerName: nextCustomer.name }
            : normalizeOrder(order),
        ),
      )

      return nextCustomer
    },

    async deleteCustomer(id) {
      const customers = await store.read<Customer[]>(storageKeys.customers, [])
      await store.write(
        storageKeys.customers,
        customers.filter((customer) => customer.id !== id),
      )
    },

    async saveTable(input) {
      const timestamp = new Date().toISOString()
      const table = normalizeTable({
        id: crypto.randomUUID(),
        floor: input.floor,
        label: input.label,
        capacity: input.capacity,
        status: 'available',
        createdAt: timestamp,
        updatedAt: timestamp,
      })

      const tables = await store.read<RestaurantTable[]>(storageKeys.tables, [])
      await store.write(storageKeys.tables, [...tables, table])
      return table
    },

    async updateTable(table) {
      const tables = await store.read<RestaurantTable[]>(storageKeys.tables, [])
      const existing = tables.find((entry) => entry.id === table.id)
      const nextTable = normalizeTable({
        ...table,
        createdAt: existing?.createdAt ?? table.createdAt,
        updatedAt: new Date().toISOString(),
      })

      await store.write(
        storageKeys.tables,
        tables.map((entry) => (entry.id === nextTable.id ? nextTable : entry)),
      )

      return nextTable
    },

    async deleteTable(id) {
      const tables = await store.read<RestaurantTable[]>(storageKeys.tables, [])
      await store.write(
        storageKeys.tables,
        tables.filter((table) => table.id !== id),
      )
    },

    async loadSuppliers() {
      return store.read<Supplier[]>(storageKeys.suppliers, [])
    },

    async saveSupplier(input) {
      const timestamp = new Date().toISOString()
      const supplier: Supplier = {
        id: crypto.randomUUID(),
        name: input.name.trim(),
        contact: input.contact.trim(),
        categoryIds: input.categoryIds,
        leadTimeDays: input.leadTimeDays,
        orderWindow: input.orderWindow.trim(),
        createdAt: timestamp,
        updatedAt: timestamp,
      }

      const suppliers = await store.read<Supplier[]>(storageKeys.suppliers, [])
      await store.write(storageKeys.suppliers, [...suppliers, supplier])
      return supplier
    },

    async updateSupplier(supplier) {
      const suppliers = await store.read<Supplier[]>(storageKeys.suppliers, [])
      const existing = suppliers.find((entry) => entry.id === supplier.id)
      const nextSupplier: Supplier = {
        ...supplier,
        name: supplier.name.trim(),
        contact: supplier.contact.trim(),
        orderWindow: supplier.orderWindow.trim(),
        createdAt: existing?.createdAt ?? supplier.createdAt,
        updatedAt: new Date().toISOString(),
      }

      await store.write(
        storageKeys.suppliers,
        suppliers.map((entry) => (entry.id === nextSupplier.id ? nextSupplier : entry)),
      )
      return nextSupplier
    },

    async deleteSupplier(id) {
      const suppliers = await store.read<Supplier[]>(storageKeys.suppliers, [])
      await store.write(
        storageKeys.suppliers,
        suppliers.filter((supplier) => supplier.id !== id),
      )
    },

    async loadReorderMarks() {
      return store.read<ReorderMark[]>(storageKeys.reorderMarks, [])
    },

    async markReorder(input) {
      const mark: ReorderMark = {
        id: crypto.randomUUID(),
        productId: input.productId,
        supplierId: input.supplierId,
        quantity: input.quantity,
        markedByUserId: input.userId ?? null,
        markedAt: new Date().toISOString(),
      }

      const marks = await store.read<ReorderMark[]>(storageKeys.reorderMarks, [])
      // One active mark per product — a re-mark replaces rather than stacks.
      await store.write(
        storageKeys.reorderMarks,
        [mark, ...marks.filter((entry) => entry.productId !== input.productId)],
      )
      return mark
    },

    async clearReorderMark(id) {
      const marks = await store.read<ReorderMark[]>(storageKeys.reorderMarks, [])
      await store.write(
        storageKeys.reorderMarks,
        marks.filter((entry) => entry.id !== id),
      )
    },

    async openShift(input) {
      if (await isOnlineSyncEnabled()) {
        const response = await backendFetch<{ shift: BackendShiftSummary }>('/api/shifts/open', {
          method: 'POST',
          body: JSON.stringify({
            openingCashCents: input.openingCashCents,
            userId: input.userId ?? null,
          }),
        })
        const shift = mapBackendShift(response.shift)
        await writeActiveShift(shift)
        return shift
      }

      const shift: ShiftSummary = {
        id: crypto.randomUUID(),
        openedByUserId: input.userId ?? null,
        closedByUserId: null,
        openingCashCents: input.openingCashCents,
        closingCashCents: null,
        cashSalesCents: 0,
        totalSalesCents: 0,
        orderCount: 0,
        payInsCents: 0,
        payOutsCents: 0,
        expectedCashCents: input.openingCashCents,
        varianceCashCents: null,
        openedAt: new Date().toISOString(),
        closedAt: null,
        movements: [],
      }

      await writeActiveShift(shift)
      return shift
    },

    async addCashMovement(input) {
      if (await isOnlineSyncEnabled()) {
        const response = await backendFetch<{ shift: BackendShiftSummary }>('/api/shifts/current/movements', {
          method: 'POST',
          body: JSON.stringify({
            movementType: input.movementType,
            amountCents: input.amountCents,
            reason: input.reason ?? null,
            userId: input.userId ?? null,
          }),
        })
        const shift = mapBackendShift(response.shift)
        await writeActiveShift(shift)
        return shift
      }

      const shift = await updateCachedShift((current) => {
        if (!current || current.closedAt) {
          throw new Error('No active shift is open for this register.')
        }

        const movement: CashMovementSummary = {
          id: crypto.randomUUID(),
          userId: input.userId ?? null,
          movementType: input.movementType,
          amountCents: input.amountCents,
          reason: input.reason ?? null,
          createdAt: new Date().toISOString(),
        }

        const payInsCents = current.payInsCents + (input.movementType === 'pay_in' ? input.amountCents : 0)
        const payOutsCents = current.payOutsCents + (input.movementType === 'pay_out' ? input.amountCents : 0)

        return {
          ...current,
          payInsCents,
          payOutsCents,
          expectedCashCents: current.openingCashCents + current.cashSalesCents + payInsCents - payOutsCents,
          movements: [movement, ...current.movements],
        }
      })

      if (!shift) {
        throw new Error('No active shift is open for this register.')
      }

      return shift
    },

    async closeShift(input) {
      if (await isOnlineSyncEnabled()) {
        const response = await backendFetch<{ shift: BackendShiftSummary }>('/api/shifts/current/close', {
          method: 'POST',
          body: JSON.stringify({
            countedCashCents: input.countedCashCents,
            userId: input.userId ?? null,
          }),
        })
        const shift = mapBackendShift(response.shift)
        await writeActiveShift(null)
        return shift
      }

      const current = await readActiveShift()
      if (!current || current.closedAt) {
        throw new Error('No active shift is open for this register.')
      }

      const closedShift: ShiftSummary = {
        ...current,
        closedByUserId: input.userId ?? null,
        closingCashCents: input.countedCashCents,
        varianceCashCents: input.countedCashCents - current.expectedCashCents,
        closedAt: new Date().toISOString(),
      }

      const history = await store.read<ShiftSummary[]>(storageKeys.shiftHistory, [])
      await store.write(storageKeys.shiftHistory, [closedShift, ...history].slice(0, 50))

      await writeActiveShift(null)
      return closedShift
    },

    async adjustInventory(input) {
      const products = await store.read<Product[]>(storageKeys.products, [])
      const product = products.find((entry) => entry.id === input.productId)
      if (!product || product.stockQty === undefined) {
        return null
      }

      const updated: Product = {
        ...product,
        stockQty: Math.max(0, product.stockQty + input.quantityDelta),
        outOfStock: Math.max(0, product.stockQty + input.quantityDelta) === 0,
      }

      await store.write(
        storageKeys.products,
        products.map((entry) => (entry.id === updated.id ? updated : entry)),
      )

      await enqueueInventoryAdjustmentEvent(input)

      try {
        await flushOutbox()
      } catch {
        // Inventory adjustments stay queued until sync succeeds.
      }

      return updated
    },

    async loadSettings() {
      const saved = await store.read<Partial<AppSettings>>(storageKeys.settings, defaultSettings)
      const settings = { ...defaultSettings, ...saved }

      // Deliberately not awaited: opening the app must not wait on an upload.
      // This is what carries a photo uploaded before the backend knew about
      // business images, and it is a local string compare on every boot after
      // the first successful publish.
      void publishStoreImage(settings)

      return settings
    },

    async saveSettings(settings) {
      await store.write(storageKeys.settings, settings)

      // The shop's public face, published separately from the catalog sync
      // below: it is not a catalog record, it has no outbox, and failing to
      // publish it must not stop the settings save or hold up the sync.
      await publishStoreImage(settings)

      if (settings.syncMode === 'online-sync') {
        try {
          await ensureRemoteSession()
          await enqueuePendingAppTelemetryEvents()
          await syncCatalogFromBootstrap()
          await flushOutbox()
        } catch {
          // Leave the local state intact if pairing or sync is unavailable.
        }
      }
    },

    async loadUsers() {
      try {
        const remoteUsers = await tryLoadRemoteUsers()
        if (remoteUsers) {
          return remoteUsers
        }
      } catch {
        // Fall back to cached local users.
      }

      return store.read<UserAccount[]>(storageKeys.users, [])
    },

    async saveUsers(users) {
      await store.write(storageKeys.users, users)
    },

    async loginUser(username, password) {
      if (await isOnlineSyncEnabled()) {
        try {
          const remote = await signInRemotely({ identifier: username, password })
          if (remote) {
            return remote
          }
        } catch (error) {
          if (error instanceof RemoteAuthError) {
            throw error
          }

          // Fall back to local auth below.
        }
      }

      const users = await store.read<UserAccount[]>(storageKeys.users, [])
      const normalizedUsername = username.trim().toLowerCase()
      const user = users.find((entry) => entry.username === normalizedUsername)

      if (!user) {
        return null
      }

      const { valid, upgradedHash } = await verifyPassword(password, user.passwordHash)
      if (!valid) {
        return null
      }

      if (upgradedHash) {
        user.passwordHash = upgradedHash
        await store.write(storageKeys.users, users.map((entry) => (entry.id === user.id ? user : entry)))
      }

      const session: AuthSession = {
        userId: user.id,
        signedInAt: new Date().toISOString(),
        authSource: 'local',
      }
      await store.write(storageKeys.session, session)

      return { user, session }
    },

    async registerUser(input) {
      if (await isOnlineSyncEnabled()) {
        try {
          const response = await fetch(`${syncConfig?.apiBaseUrl}/api/staff-register`, {
            method: 'POST',
            headers: {
              'Accept': 'application/json',
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              organizationSlug: syncConfig?.organizationSlug,
              storeCode: syncConfig?.storeCode,
              fullName: input.fullName,
              username: input.username,
              password: input.password,
            }),
          })

          if (response.ok) {
            const body = await response.json() as {
              user: UserAccount
              session: AuthSession
            }
            const existingUsers = await store.read<UserAccount[]>(storageKeys.users, [])
            await store.write(storageKeys.users, [
              body.user,
              ...existingUsers.filter((entry) => entry.id !== body.user.id),
            ])
            await store.write(storageKeys.session, body.session)
            return body
          }
        } catch {
          // Fall back to local registration below.
        }
      }

      const users = await store.read<UserAccount[]>(storageKeys.users, [])
      const fullName = input.fullName.trim()
      const username = input.username.trim().toLowerCase()
      const password = input.password.trim()

      if (!fullName || !username || !password) {
        return null
      }

      if (users.some((user) => user.username === username)) {
        return null
      }

      const defaultRoleId = users.length > 0 ? 'cashier' : 'admin'
      const user: UserAccount = {
        id: crypto.randomUUID(),
        fullName,
        username,
        passwordHash: await hashPassword(password),
        roleId: defaultRoleId,
        createdAt: new Date().toISOString(),
      }

      const nextUsers = [...users, user]
      await store.write(storageKeys.users, nextUsers)

      const session: AuthSession = {
        userId: user.id,
        signedInAt: new Date().toISOString(),
        authSource: 'local',
      }
      await store.write(storageKeys.session, session)

      return { user, session }
    },

    async createStaffAccount(input) {
      const fullName = input.fullName.trim()
      const username = input.username.trim().toLowerCase()
      const password = input.password.trim()
      const roleId = input.roleId.trim()

      if (!fullName || !username || !password || !roleId) {
        throw new Error('Full name, username, password, and role are required.')
      }

      // Backend note: there is no POST /staff-users endpoint yet, so an admin
      // creating a colleague from the POS writes a local-only account. Add a
      // backend create endpoint if these need to sign in from another device.
      const users = await store.read<UserAccount[]>(storageKeys.users, [])
      if (users.some((user) => user.username === username)) {
        throw new Error('That username is already in use.')
      }

      const user: UserAccount = {
        id: crypto.randomUUID(),
        fullName,
        username,
        passwordHash: await hashPassword(password),
        roleId,
        createdAt: new Date().toISOString(),
      }

      await store.write(storageKeys.users, [...users, user])
      // Deliberately does not touch storageKeys.session — the admin stays signed in as themselves.
      return user
    },

    async updateUserRole(userId, roleId) {
      const users = await store.read<UserAccount[]>(storageKeys.users, [])
      const nextUsers = users.map((user) => (user.id === userId ? { ...user, roleId } : user))
      await store.write(storageKeys.users, nextUsers)

      if (await isOnlineSyncEnabled()) {
        try {
          await backendFetch<{ user: UserAccount }>(`/api/staff-users/${userId}/role`, {
            method: 'PATCH',
            body: JSON.stringify({ roleId }),
          })
          const refreshedUsers = await tryLoadRemoteUsers()
          if (refreshedUsers) return
        } catch {
          // Keep the local role update even if the backend call fails.
        }
      }
    },

    async loadRoles() {
      try {
        const remoteRoles = await tryLoadRemoteRoles()
        if (remoteRoles) {
          return remoteRoles
        }
      } catch {
        // Fall back to cached roles.
      }

      const roles = await store.read<RoleDefinition[]>(storageKeys.roles, [])
      return roles.length > 0 ? roles : defaultRoles
    },

    async saveRoles(roles) {
      await store.write(storageKeys.roles, roles)

      if (await isOnlineSyncEnabled()) {
        try {
          const response = await backendFetch<{ roles: RoleDefinition[] }>('/api/staff-roles', {
            method: 'PUT',
            body: JSON.stringify({ roles }),
          })
          await store.write(storageKeys.roles, response.roles)
        } catch {
          // Keep local roles cached if remote sync fails.
        }
      }
    },

    async loadSession() {
      return store.read<AuthSession | null>(storageKeys.session, null)
    },

    async saveSession(session) {
      await store.write(storageKeys.session, session)

      if (!session || session.userId === guestSessionUserId) {
        await writeSyncSession(null)
      }
    },

    async getSyncStoreId() {
      const session = await readSyncSession()
      return session?.storeId ?? null
    },

    async loadAppEvents() {
      const events = await store.read<AppEvent[]>(storageKeys.appEvents, [])
      return events.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    },

    async trackAppEvent(input) {
      const event: AppEvent = {
        id: crypto.randomUUID(),
        eventType: input.eventType,
        payload: input.payload,
        deviceId: await getDeviceId(),
        appVersion,
        createdAt: new Date().toISOString(),
        sentAt: null,
      }

      const events = await store.read<AppEvent[]>(storageKeys.appEvents, [])
      await store.write(storageKeys.appEvents, [event, ...events])

      if (await isOnlineSyncEnabled()) {
        try {
          await enqueueAppTelemetryEvent(event)
          const appliedIds = await flushOutbox()
          if (appliedIds.has(event.id)) {
            return { ...event, sentAt: new Date().toISOString() }
          }
        } catch {
          // Telemetry remains local and queued for the next sync attempt.
        }
      }

      return event
    },

    async saveProduct(input) {
      const product: Product = {
        ...input,
        id: crypto.randomUUID(),
        sku: generateSku(input.name),
        barcode: String(Date.now()).slice(-12),
      }

      const products = await store.read<Product[]>(storageKeys.products, [])
      await store.write(storageKeys.products, [product, ...products])
      await enqueueProductEvent(product)

      try {
        await flushOutbox()
      } catch {
        // Product changes remain local until sync succeeds.
      }

      return product
    },

    async updateProduct(product) {
      const products = await store.read<Product[]>(storageKeys.products, [])
      const previous = products.find((p) => p.id === product.id) ?? null
      await store.write(
        storageKeys.products,
        products.map((p) => (p.id === product.id ? product : p)),
      )

      const metadataChanged = !previous
        || previous.categoryId !== product.categoryId
        || previous.sku !== product.sku
        || previous.barcode !== product.barcode
        || previous.name !== product.name
        || previous.priceCents !== product.priceCents
        || previous.taxRate !== product.taxRate
        || previous.kind !== product.kind
        || previous.lowStockThreshold !== product.lowStockThreshold
        || previous.outOfStock !== product.outOfStock
        || String(previous.stockQty ?? '') !== String(product.stockQty ?? '')

      if (metadataChanged) {
        await enqueueProductEvent(product)
      }

      const previousQty = previous?.stockQty
      const nextQty = product.stockQty
      if (
        previous
        && typeof previousQty === 'number'
        && typeof nextQty === 'number'
        && previousQty !== nextQty
      ) {
        await enqueueInventoryAdjustmentEvent({
          productId: product.id,
          quantityDelta: nextQty - previousQty,
          adjustmentType: 'manual_correction',
          reason: 'product_edit',
        })
      }

      try {
        await flushOutbox()
      } catch {
        // Product changes remain local until sync succeeds.
      }

      return product
    },

    async deleteProduct(id) {
      const products = await store.read<Product[]>(storageKeys.products, [])
      const target = products.find((product) => product.id === id)
      await store.write(
        storageKeys.products,
        products.filter((p) => p.id !== id),
      )

      if (target) {
        await appendOutboxEvent({
          id: crypto.randomUUID(),
          entityType: 'product',
          entityId: target.id,
          operation: 'upsert',
          occurredAt: new Date().toISOString(),
          payload: {
            categoryId: target.categoryId,
            sku: target.sku,
            barcode: target.barcode,
            name: target.name,
            productType: target.kind,
            taxRate: target.taxRate,
            priceCents: target.priceCents,
            trackInventory: target.stockQty !== undefined,
            isActive: false,
            deletedAt: new Date().toISOString(),
          },
        })
      }

      try {
        await flushOutbox()
      } catch {
        // Product deletion remains queued until sync succeeds.
      }
    },

    async saveCategory(input) {
      const category: Category = {
        id: crypto.randomUUID(),
        name: input.name,
      }

      const categories = await store.read<Category[]>(storageKeys.categories, [])
      await store.write(storageKeys.categories, [...categories, category])
      await enqueueCategoryEvent(category)

      try {
        await flushOutbox()
      } catch {
        // Category changes remain local until sync succeeds.
      }

      return category
    },

    async updateCategory(category) {
      const categories = await store.read<Category[]>(storageKeys.categories, [])
      await store.write(
        storageKeys.categories,
        categories.map((c) => (c.id === category.id ? category : c)),
      )

      await enqueueCategoryEvent(category)

      try {
        await flushOutbox()
      } catch {
        // Category changes remain local until sync succeeds.
      }

      return category
    },

    async deleteCategory(id) {
      const categories = await store.read<Category[]>(storageKeys.categories, [])
      const removed = categories.find((category) => category.id === id)
      const remaining = categories.filter((c) => c.id !== id)
      await store.write(storageKeys.categories, remaining)

      const products = await store.read<Product[]>(storageKeys.products, [])
      const fallbackId = remaining[0]?.id ?? products[0]?.categoryId ?? 'groceries'
      await store.write(
        storageKeys.products,
        products.map((p) => (p.categoryId === id ? { ...p, categoryId: fallbackId } : p)),
      )

      if (removed) {
        await enqueueCategoryEvent(removed, new Date().toISOString())
      }

      try {
        await flushOutbox()
      } catch {
        // Category deletion remains queued until sync succeeds.
      }
    },
  }
}
