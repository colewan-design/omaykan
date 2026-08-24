import { getApp, getApps, initializeApp, type FirebaseApp } from 'firebase/app'
import {
  createUserWithEmailAndPassword,
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  type Auth,
  type User,
} from 'firebase/auth'
import {
  collection,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  increment,
  limit,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
  type DocumentSnapshot,
  type Firestore,
  type QueryDocumentSnapshot,
  type Transaction,
} from 'firebase/firestore'
import {
  guestCustomerName,
  type Category,
  type CashMovementSummary,
  type CashMovementType,
  type AuthSession,
  type OrderChannel,
  type OrderSummary,
  type PaymentMethod,
  type PaymentStatus,
  type Product,
  type RoleDefinition,
  type ShiftSummary,
  type UserAccount,
} from '@pos/shared/index'

// ── Config ────────────────────────────────────────────────────────────────────

export interface FirebaseWebConfig {
  apiKey: string
  authDomain: string
  projectId: string
  storageBucket?: string
  messagingSenderId?: string
  appId: string
}

export interface FirebaseSyncConfig {
  firebaseConfig: FirebaseWebConfig
  organizationSlug: string
  storeCode: string
  deviceName: string
  platform: string
  appVersion: string
}

export interface FirebaseSyncSession {
  token: string
  deviceId: string
  storeId: string
  storeName: string
  organizationId: string
  organizationSlug: string
}

// ── Firestore document shapes ───────────────────────────────────────────────────

interface FsUser {
  organizationId: string
  fullName: string
  username: string
  status: string
  roleKey: string
  createdAt: Timestamp | null
}

interface FsCategory {
  name: string
  sortOrder: number
  deletedAt?: Timestamp | null
}

interface FsProduct {
  categoryId: string | null
  sku: string
  barcode: string
  name: string
  productType: string
  taxRate: number | string
  priceCents: number
  compareAtPriceCents?: number | null
  trackInventory: boolean
  isActive: boolean
  businessModes: string[]
  stockQty: number | null
  lowStockThreshold: number | null
}

interface FsShift {
  openedByUserId: string | null
  closedByUserId: string | null
  openingCashCents: number
  closingCashCents: number | null
  cashSalesCents: number
  totalSalesCents: number
  orderCount: number
  payInsCents: number
  payOutsCents: number
  expectedCashCents: number
  varianceCashCents: number | null
  openedAt: Timestamp | null
  closedAt: Timestamp | null
}

interface FsCashMovement {
  userId: string | null
  movementType: string
  amountCents: number
  reason: string | null
  createdAt: Timestamp | null
}

interface FsRole {
  name: string
  permissions: Record<string, unknown>
}

interface SyncOutboxEvent {
  id: string
  entityType: 'order' | 'category' | 'product' | 'inventory_adjustment' | 'app_event'
  entityId: string
  operation: 'upsert'
  occurredAt: string
  payload: Record<string, unknown>
}

// ── Mappers ───────────────────────────────────────────────────────────────────

function toIso(ts: Timestamp | null | undefined): string {
  return ts ? ts.toDate().toISOString() : new Date().toISOString()
}

function mapFsCategory(docSnap: QueryDocumentSnapshot): Category {
  const data = docSnap.data() as FsCategory
  return { id: docSnap.id, name: data.name }
}

function mapFsProduct(docSnap: QueryDocumentSnapshot): Product {
  const data = docSnap.data() as FsProduct
  const stockQty = data.trackInventory && data.stockQty != null ? Number(data.stockQty) : undefined

  return {
    id: docSnap.id,
    categoryId: data.categoryId ?? 'uncategorized',
    sku: data.sku,
    barcode: data.barcode ?? '',
    name: data.name,
    priceCents: data.priceCents,
    compareAtPriceCents: data.compareAtPriceCents ?? undefined,
    taxRate: Number(data.taxRate),
    kind: data.productType === 'weighted' ? 'weighted' : 'standard',
    businessModes: (data.businessModes ?? []) as Product['businessModes'],
    outOfStock: !data.isActive || stockQty === 0,
    stockQty,
    lowStockThreshold: data.lowStockThreshold ?? undefined,
  }
}

interface FsOrder {
  ticketNumber: string
  businessMode?: string
  orderType: string
  tableNumber?: string | null
  status?: string
  channel?: string
  paymentStatus?: string
  paymentMethod: string | null
  subtotalCents: number
  taxCents: number
  totalCents: number
  tenderedCents: number
  changeCents: number
  customerId?: string | null
  customerName?: string
  guestContact?: { name: string; phone?: string; email?: string } | null
  fulfillmentMethod?: string
  deliveryAddress?: string | null
  createdAt: Timestamp | null
  voidedAt?: Timestamp | null
  voidedByUserId?: string | null
  voidReason?: string | null
  paymentConfirmedAt?: Timestamp | null
  paymentConfirmedByUserId?: string | null
}

interface FsOrderItem {
  productId: string | null
  productName: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
}

// Reads back an order Firestore doc written either by pushOrderEvent (staff,
// in-person) or the createOnlineOrder Cloud Function (guest, online) — the two
// writers share the same collection/shape, differing only in the additive
// channel/paymentStatus/guestContact fields.
function mapFsOrder(
  orderSnap: QueryDocumentSnapshot | DocumentSnapshot,
  itemDocs: QueryDocumentSnapshot[],
): OrderSummary {
  const data = orderSnap.data() as FsOrder

  return {
    id: orderSnap.id,
    ticketNumber: data.ticketNumber,
    businessMode: (data.businessMode ?? 'coffee-shop') as OrderSummary['businessMode'],
    customerId: data.customerId ?? null,
    customerName: data.customerName?.trim() || data.guestContact?.name || guestCustomerName,
    orderType: data.orderType as OrderSummary['orderType'],
    tableNumber: data.tableNumber ?? null,
    status: (data.status ?? 'preparing') as OrderSummary['status'],
    paymentMethod: (data.paymentMethod ?? 'cash') as PaymentMethod,
    subtotalCents: data.subtotalCents,
    taxCents: data.taxCents,
    totalCents: data.totalCents,
    tenderedCents: data.tenderedCents,
    changeCents: data.changeCents,
    createdAt: toIso(data.createdAt),
    items: itemDocs.map((itemDoc) => {
      const item = itemDoc.data() as FsOrderItem
      return {
        productId: item.productId ?? '',
        name: item.productName,
        quantity: item.quantity,
        unitPriceCents: item.unitPriceCents,
        lineTotalCents: item.lineTotalCents,
      }
    }),
    channel: (data.channel ?? 'in_person') as OrderChannel,
    paymentStatus: (data.paymentStatus ?? 'paid') as PaymentStatus,
    guestContact: data.guestContact ?? null,
    fulfillmentMethod: data.fulfillmentMethod as OrderSummary['fulfillmentMethod'],
    deliveryAddress: data.deliveryAddress ?? null,
    voidedAt: data.voidedAt ? toIso(data.voidedAt) : null,
    voidedByUserId: data.voidedByUserId ?? null,
    voidReason: data.voidReason ?? null,
    paymentConfirmedAt: data.paymentConfirmedAt ? toIso(data.paymentConfirmedAt) : null,
    paymentConfirmedByUserId: data.paymentConfirmedByUserId ?? null,
  }
}

function mapFsCashMovement(docSnap: QueryDocumentSnapshot): CashMovementSummary {
  const data = docSnap.data() as FsCashMovement
  return {
    id: docSnap.id,
    userId: data.userId ?? null,
    movementType: data.movementType as CashMovementType,
    amountCents: data.amountCents,
    reason: data.reason ?? null,
    createdAt: toIso(data.createdAt),
  }
}

function mapFsShift(
  shiftSnap: DocumentSnapshot,
  movementDocs: QueryDocumentSnapshot[],
): ShiftSummary {
  const data = shiftSnap.data() as FsShift
  return {
    id: shiftSnap.id,
    openedByUserId: data.openedByUserId ?? null,
    closedByUserId: data.closedByUserId ?? null,
    openingCashCents: data.openingCashCents,
    closingCashCents: data.closingCashCents ?? null,
    cashSalesCents: data.cashSalesCents,
    // Shifts opened before totalSalesCents/orderCount existed predate these Firestore fields.
    totalSalesCents: data.totalSalesCents ?? data.cashSalesCents ?? 0,
    orderCount: data.orderCount ?? 0,
    payInsCents: data.payInsCents,
    payOutsCents: data.payOutsCents,
    expectedCashCents: data.expectedCashCents,
    varianceCashCents: data.varianceCashCents ?? null,
    openedAt: toIso(data.openedAt),
    closedAt: data.closedAt ? toIso(data.closedAt) : null,
    movements: movementDocs.map(mapFsCashMovement),
  }
}

function syntheticEmail(username: string, orgSlug: string): string {
  return `${username.trim().toLowerCase()}@${orgSlug}.pos`
}

// ── Factory ───────────────────────────────────────────────────────────────────

export function createFirebaseSync(config: FirebaseSyncConfig) {
  const app: FirebaseApp = getApps().length ? getApp() : initializeApp(config.firebaseConfig)
  const auth: Auth = getAuth(app)
  const db: Firestore = getFirestore(app)

  async function resolveOrgStore() {
    const orgSnap = await getDoc(doc(db, 'organizations', config.organizationSlug))
    if (!orgSnap.exists()) {
      throw new Error(`Organization '${config.organizationSlug}' not found in Firebase.`)
    }

    const storeSnap = await getDoc(doc(db, 'organizations', config.organizationSlug, 'stores', config.storeCode))
    if (!storeSnap.exists()) {
      throw new Error(`Store '${config.storeCode}' not found under organization '${config.organizationSlug}'.`)
    }

    return {
      organizationId: config.organizationSlug,
      storeId: config.storeCode,
      storeName: (storeSnap.data() as { name: string }).name,
    }
  }

  async function waitForAuthReady(): Promise<User | null> {
    if (auth.currentUser) {
      return auth.currentUser
    }

    return new Promise((resolve) => {
      const unsubscribe = onAuthStateChanged(auth, (user) => {
        unsubscribe()
        resolve(user)
      })
    })
  }

  async function loadShift(orgId: string, storeId: string, shiftId: string): Promise<ShiftSummary | null> {
    const shiftRef = doc(db, 'organizations', orgId, 'stores', storeId, 'shifts', shiftId)
    const shiftSnap = await getDoc(shiftRef)
    if (!shiftSnap.exists()) return null

    const movementsSnap = await getDocs(query(collection(shiftRef, 'cashMovements'), orderBy('createdAt', 'desc')))
    return mapFsShift(shiftSnap, movementsSnap.docs)
  }

  // Firestore's client SDK only supports transaction.get() by DocumentReference, not by Query —
  // so the open shift must be located with a plain (non-transactional) query beforehand. The
  // transaction re-reads it by reference (tx.get(ref) is allowed) and re-checks closedAt before
  // applying the increment, so a shift that closes in the meantime is simply skipped — the same
  // best-effort race tolerance the original Postgres trigger had (a plain UPDATE ... WHERE
  // closed_at IS NULL, no serializable lock either).
  async function findOpenShiftRef(orgId: string, storeId: string) {
    const shiftsRef = collection(db, 'organizations', orgId, 'stores', storeId, 'shifts')
    const openShiftQuery = query(shiftsRef, where('closedAt', '==', null), orderBy('openedAt', 'desc'), limit(1))
    const snap = await getDocs(openShiftQuery)
    return snap.empty ? null : snap.docs[0].ref
  }

  async function pushOrderEvent(
    tx: Transaction,
    event: SyncOutboxEvent,
    session: FirebaseSyncSession,
    openShiftRef: ReturnType<typeof doc> | null,
  ) {
    const { organizationId: orgId, storeId } = session
    const p = event.payload as {
      order: Record<string, unknown>
      items: Array<Record<string, unknown>>
      payments: Array<Record<string, unknown>>
    }

    const order = p.order as {
      ticketNumber: string
      orderType: string
      tableNumber?: string | null
      status?: string
      subtotalCents: number
      taxCents: number
      totalCents: number
      completedAt: string
      businessMode?: string
    }

    const paymentMethod = (p.payments[0] as Record<string, string>)?.paymentMethod ?? 'cash'

    const items = p.items as Array<{
      id: string
      productId: string
      productName: string
      quantity: number
      unitPriceCents: number
      lineTotalCents: number
    }>

    // Reads first — Firestore transactions require all reads before any writes.
    const productRefs = items.map((item) => doc(db, 'organizations', orgId, 'products', item.productId))
    const productSnaps = await Promise.all(productRefs.map((ref) => tx.get(ref)))

    let openShiftDocRef = null as ReturnType<typeof doc> | null
    if (openShiftRef) {
      const shiftSnap = await tx.get(openShiftRef)
      if (shiftSnap.exists() && !(shiftSnap.data() as FsShift).closedAt) {
        openShiftDocRef = openShiftRef
      }
    }

    // Writes.
    const orderRef = doc(db, 'organizations', orgId, 'stores', storeId, 'orders', event.entityId)
    tx.set(orderRef, {
      ticketNumber: order.ticketNumber,
      businessMode: order.businessMode ?? 'coffee-shop',
      orderType: order.orderType,
      tableNumber: order.tableNumber ?? null,
      status: order.status ?? 'preparing',
      paymentMethod,
      subtotalCents: order.subtotalCents,
      taxCents: order.taxCents,
      totalCents: order.totalCents,
      tenderedCents: Number((p.payments[0] as Record<string, number>)?.tenderedCents ?? order.totalCents),
      changeCents: Number((p.payments[0] as Record<string, number>)?.changeCents ?? 0),
      completedAt: order.completedAt,
      createdAt: serverTimestamp(),
    })

    items.forEach((item, index) => {
      const itemRef = doc(collection(orderRef, 'items'), `item-${index}`)
      tx.set(itemRef, {
        productId: item.productId || null,
        productName: item.productName,
        quantity: item.quantity,
        unitPriceCents: item.unitPriceCents,
        lineTotalCents: item.lineTotalCents,
      })

      const productSnap = productSnaps[index]
      if (productSnap.exists() && (productSnap.data() as FsProduct).trackInventory) {
        const invRef = doc(db, 'organizations', orgId, 'stores', storeId, 'inventoryLevels', item.productId)
        tx.set(invRef, { qtyOnHand: increment(-item.quantity), updatedAt: serverTimestamp() }, { merge: true })
        tx.update(productRefs[index], { stockQty: increment(-item.quantity) })

        const adjRef = doc(collection(db, 'organizations', orgId, 'stores', storeId, 'inventoryAdjustments'))
        tx.set(adjRef, {
          productId: item.productId,
          orderId: event.entityId,
          adjustmentType: 'sale',
          quantityDelta: -item.quantity,
          reason: null,
          createdAt: serverTimestamp(),
        })
      }
    })

    if (openShiftDocRef) {
      tx.update(openShiftDocRef, {
        ...(paymentMethod === 'cash'
          ? { cashSalesCents: increment(order.totalCents), expectedCashCents: increment(order.totalCents) }
          : {}),
        totalSalesCents: increment(order.totalCents),
        orderCount: increment(1),
      })
    }
  }

  async function pushProductEvent(tx: Transaction, event: SyncOutboxEvent, session: FirebaseSyncSession) {
    const p = event.payload as {
      categoryId: string
      sku: string
      barcode: string
      name: string
      productType: string
      taxRate: number
      priceCents: number
      compareAtPriceCents?: number | null
      trackInventory: boolean
      stockQty: number | null
      lowStockThreshold: number | null
      isActive: boolean
      deletedAt?: string
    }

    const productRef = doc(db, 'organizations', session.organizationId, 'products', event.entityId)
    tx.set(productRef, {
      categoryId: p.categoryId === 'uncategorized' ? null : p.categoryId,
      sku: p.sku,
      barcode: p.barcode ?? '',
      name: p.name,
      productType: p.productType ?? 'standard',
      taxRate: p.taxRate,
      priceCents: p.priceCents,
      compareAtPriceCents: p.compareAtPriceCents ?? null,
      trackInventory: p.trackInventory ?? false,
      lowStockThreshold: p.lowStockThreshold ?? null,
      isActive: p.isActive !== false && !p.deletedAt,
      businessModes: [],
      stockQty: p.stockQty ?? null,
      updatedAt: serverTimestamp(),
      deletedAt: p.deletedAt ?? null,
    }, { merge: true })
  }

  async function pushCategoryEvent(tx: Transaction, event: SyncOutboxEvent, session: FirebaseSyncSession) {
    const p = event.payload as { name: string; sortOrder?: number; deletedAt?: string }
    const categoryRef = doc(db, 'organizations', session.organizationId, 'categories', event.entityId)
    tx.set(categoryRef, {
      name: p.name,
      sortOrder: p.sortOrder ?? 0,
      updatedAt: serverTimestamp(),
      deletedAt: p.deletedAt ?? null,
    }, { merge: true })
  }

  async function pushInventoryAdjustmentEvent(tx: Transaction, event: SyncOutboxEvent, session: FirebaseSyncSession) {
    const p = event.payload as {
      productId: string
      quantityDelta: number
      adjustmentType: string
      reason: string | null
      orderId: string | null
    }

    const invRef = doc(db, 'organizations', session.organizationId, 'stores', session.storeId, 'inventoryLevels', p.productId)
    const productRef = doc(db, 'organizations', session.organizationId, 'products', p.productId)

    tx.set(invRef, { qtyOnHand: increment(p.quantityDelta), updatedAt: serverTimestamp() }, { merge: true })
    tx.update(productRef, { stockQty: increment(p.quantityDelta) })

    const adjRef = doc(collection(db, 'organizations', session.organizationId, 'stores', session.storeId, 'inventoryAdjustments'))
    tx.set(adjRef, {
      productId: p.productId,
      orderId: p.orderId ?? null,
      adjustmentType: p.adjustmentType,
      quantityDelta: p.quantityDelta,
      reason: p.reason ?? null,
      createdAt: serverTimestamp(),
    })
  }

  return {
    async getCurrentSession(cached: FirebaseSyncSession | null): Promise<FirebaseSyncSession | null> {
      const user = await waitForAuthReady()
      if (!user) {
        return null
      }

      if (cached?.organizationId && cached?.storeId) {
        return { ...cached, token: await user.getIdToken() }
      }

      try {
        const { organizationId, storeId, storeName } = await resolveOrgStore()
        return {
          token: await user.getIdToken(),
          deviceId: '',
          storeId,
          storeName,
          organizationId,
          organizationSlug: config.organizationSlug,
        }
      } catch {
        return null
      }
    },

    async loginUser(
      username: string,
      password: string,
    ): Promise<{ user: UserAccount; session: AuthSession; syncSession: FirebaseSyncSession } | null> {
      const email = syntheticEmail(username, config.organizationSlug)

      let cred
      try {
        cred = await signInWithEmailAndPassword(auth, email, password)
      } catch {
        return null
      }

      const userSnap = await getDoc(doc(db, 'users', cred.user.uid))
      if (!userSnap.exists()) {
        return null
      }

      // Platform-operator kill switch (api/platform-admin.ts) — an org can be
      // locked out entirely without touching any individual user account.
      const orgSnap = await getDoc(doc(db, 'organizations', config.organizationSlug))
      if (orgSnap.data()?.suspended === true) {
        return null
      }

      const userData = userSnap.data() as FsUser
      const { organizationId, storeId, storeName } = await resolveOrgStore()

      const user: UserAccount = {
        id: cred.user.uid,
        fullName: userData.fullName,
        username: userData.username,
        passwordHash: '',
        roleId: userData.roleKey ?? 'cashier',
        createdAt: toIso(userData.createdAt),
      }

      const authSession: AuthSession = {
        userId: cred.user.uid,
        signedInAt: new Date().toISOString(),
        authSource: 'remote',
      }

      const syncSession: FirebaseSyncSession = {
        token: await cred.user.getIdToken(),
        deviceId: '',
        storeId,
        storeName,
        organizationId,
        organizationSlug: config.organizationSlug,
      }

      return { user, session: authSession, syncSession }
    },

    async registerUser(input: {
      fullName: string
      username: string
      password: string
    }): Promise<{ user: UserAccount; session: AuthSession; syncSession: FirebaseSyncSession } | null> {
      const email = syntheticEmail(input.username, config.organizationSlug)

      let cred
      try {
        cred = await createUserWithEmailAndPassword(auth, email, input.password)
      } catch {
        return null
      }

      let organizationId: string
      let storeId: string
      let storeName: string
      try {
        ;({ organizationId, storeId, storeName } = await resolveOrgStore())
      } catch {
        await firebaseSignOut(auth)
        return null
      }

      const userRef = doc(db, 'users', cred.user.uid)
      const orgRef = doc(db, 'organizations', organizationId)
      const fullName = input.fullName.trim()
      const username = input.username.trim().toLowerCase()

      let roleKey: string
      try {
        roleKey = await runTransaction(db, async (tx) => {
          const orgSnap = await tx.get(orgRef)
          const claimed = orgSnap.data()?.firstAdminClaimed === true
          const resolvedRoleKey = claimed ? 'cashier' : 'admin'

          tx.set(userRef, {
            organizationId,
            fullName,
            username,
            status: 'active',
            roleKey: resolvedRoleKey,
            createdAt: serverTimestamp(),
          })

          if (!claimed) {
            tx.update(orgRef, { firstAdminClaimed: true })
          }

          return resolvedRoleKey
        })
      } catch {
        await firebaseSignOut(auth)
        return null
      }

      const user: UserAccount = {
        id: cred.user.uid,
        fullName,
        username,
        passwordHash: '',
        roleId: roleKey,
        createdAt: new Date().toISOString(),
      }

      const authSession: AuthSession = {
        userId: cred.user.uid,
        signedInAt: new Date().toISOString(),
        authSource: 'remote',
      }

      const syncSession: FirebaseSyncSession = {
        token: await cred.user.getIdToken(),
        deviceId: '',
        storeId,
        storeName,
        organizationId,
        organizationSlug: config.organizationSlug,
      }

      return { user, session: authSession, syncSession }
    },

    async createStaffAccount(input: {
      fullName: string
      username: string
      password: string
      roleId: string
    }): Promise<UserAccount> {
      const currentUser = await waitForAuthReady()
      const idToken = await currentUser?.getIdToken()
      if (!idToken) {
        throw new Error('Your admin session has expired — please sign in again.')
      }

      const response = await fetch('/api/staff-create', {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${idToken}`,
        },
        body: JSON.stringify(input),
      })

      const body = await response.json().catch(() => ({})) as {
        user?: { id: string; fullName: string; username: string; roleId: string; createdAt: string }
        error?: string
      }

      if (!response.ok || !body.user) {
        throw new Error(body.error || 'Unable to create that account.')
      }

      return {
        id: body.user.id,
        fullName: body.user.fullName,
        username: body.user.username,
        passwordHash: '',
        roleId: body.user.roleId,
        createdAt: body.user.createdAt,
      }
    },

    async signOut(): Promise<void> {
      await firebaseSignOut(auth)
    },

    async bootstrapCatalog(
      orgId: string,
      _storeId: string,
    ): Promise<{ categories: Category[]; products: Product[]; cursor: string }> {
      const [catSnap, prodSnap] = await Promise.all([
        getDocs(query(collection(db, 'organizations', orgId, 'categories'), orderBy('sortOrder', 'asc'))),
        getDocs(query(collection(db, 'organizations', orgId, 'products'), where('isActive', '==', true))),
      ])

      return {
        categories: catSnap.docs.filter((d) => !(d.data() as FsCategory).deletedAt).map(mapFsCategory),
        products: prodSnap.docs.map(mapFsProduct),
        cursor: new Date().toISOString(),
      }
    },

    async pullChanges(
      orgId: string,
      _storeId: string,
      cursor: string,
    ): Promise<{ categories: Category[]; products: Product[]; cursor: string }> {
      const cursorTs = Timestamp.fromDate(new Date(cursor))
      const [catSnap, prodSnap] = await Promise.all([
        getDocs(query(collection(db, 'organizations', orgId, 'categories'), where('updatedAt', '>', cursorTs))),
        getDocs(query(collection(db, 'organizations', orgId, 'products'), where('updatedAt', '>', cursorTs))),
      ])

      return {
        categories: catSnap.docs.filter((d) => !(d.data() as FsCategory).deletedAt).map(mapFsCategory),
        products: prodSnap.docs.map(mapFsProduct),
        cursor: new Date().toISOString(),
      }
    },

    async pushEvents(events: SyncOutboxEvent[], session: FirebaseSyncSession): Promise<Set<string>> {
      const applied = new Set<string>()

      for (const event of events) {
        try {
          const eventRef = doc(
            db, 'organizations', session.organizationId, 'stores', session.storeId, 'syncEvents', event.id,
          )

          let openShiftRef = null as ReturnType<typeof doc> | null
          if (event.entityType === 'order') {
            const payments = (event.payload as { payments?: Array<Record<string, unknown>> }).payments
            const paymentMethod = (payments?.[0] as Record<string, string>)?.paymentMethod ?? 'cash'
            if (paymentMethod === 'cash') {
              openShiftRef = await findOpenShiftRef(session.organizationId, session.storeId)
            }
          }

          await runTransaction(db, async (tx) => {
            const eventSnap = await tx.get(eventRef)
            if (eventSnap.exists()) {
              return
            }

            if (event.entityType === 'order') {
              await pushOrderEvent(tx, event, session, openShiftRef)
            } else if (event.entityType === 'product') {
              await pushProductEvent(tx, event, session)
            } else if (event.entityType === 'category') {
              await pushCategoryEvent(tx, event, session)
            } else if (event.entityType === 'inventory_adjustment') {
              await pushInventoryAdjustmentEvent(tx, event, session)
            }

            tx.set(eventRef, {
              entityType: event.entityType,
              entityId: event.entityId,
              operation: event.operation,
              occurredAt: event.occurredAt,
              payload: event.payload,
              deviceId: session.deviceId,
              receivedAt: serverTimestamp(),
              appliedAt: serverTimestamp(),
            })
          })

          applied.add(event.id)
        } catch {
          // Leave event in outbox for next sync attempt.
        }
      }

      return applied
    },

    async loadUsers(orgId: string): Promise<UserAccount[]> {
      const snap = await getDocs(query(collection(db, 'users'), where('organizationId', '==', orgId)))

      return snap.docs.map((docSnap) => {
        const data = docSnap.data() as FsUser
        return {
          id: docSnap.id,
          fullName: data.fullName,
          username: data.username,
          passwordHash: '',
          roleId: data.roleKey ?? 'cashier',
          createdAt: toIso(data.createdAt),
        }
      })
    },

    async loadRoles(orgId: string): Promise<RoleDefinition[]> {
      const snap = await getDocs(
        query(collection(db, 'organizations', orgId, 'roles'), where('deletedAt', '==', null)),
      )

      return snap.docs.map((docSnap) => {
        const data = docSnap.data() as FsRole
        return {
          id: docSnap.id,
          name: data.name,
          permissions: data.permissions as RoleDefinition['permissions'],
        }
      })
    },

    async updateUserRole(userId: string, roleId: string, orgId: string): Promise<void> {
      const userRef = doc(db, 'users', userId)
      const snap = await getDoc(userRef)
      if (!snap.exists() || (snap.data() as FsUser).organizationId !== orgId) {
        return
      }
      await updateDoc(userRef, { roleKey: roleId })
    },

    async saveRoles(roles: RoleDefinition[], orgId: string): Promise<RoleDefinition[]> {
      for (const role of roles) {
        await setDoc(doc(db, 'organizations', orgId, 'roles', role.id), {
          name: role.name,
          permissions: role.permissions ?? {},
          deletedAt: null,
        }, { merge: true })
      }
      return roles
    },

    async getCurrentShift(storeId: string): Promise<ShiftSummary | null> {
      const shiftsRef = collection(db, 'organizations', config.organizationSlug, 'stores', storeId, 'shifts')
      const snap = await getDocs(
        query(shiftsRef, where('closedAt', '==', null), orderBy('openedAt', 'desc'), limit(1)),
      )

      if (snap.empty) return null

      const shiftDoc = snap.docs[0]
      const movementsSnap = await getDocs(query(collection(shiftDoc.ref, 'cashMovements'), orderBy('createdAt', 'desc')))
      return mapFsShift(shiftDoc, movementsSnap.docs)
    },

    async getShiftHistory(storeId: string): Promise<ShiftSummary[]> {
      const shiftsRef = collection(db, 'organizations', config.organizationSlug, 'stores', storeId, 'shifts')
      const snap = await getDocs(
        query(shiftsRef, where('closedAt', '!=', null), orderBy('closedAt', 'desc'), limit(50)),
      )

      return Promise.all(
        snap.docs.map(async (shiftDoc) => {
          const movementsSnap = await getDocs(
            query(collection(shiftDoc.ref, 'cashMovements'), orderBy('createdAt', 'desc')),
          )
          return mapFsShift(shiftDoc, movementsSnap.docs)
        }),
      )
    },

    async pullOnlineOrders(storeId: string): Promise<OrderSummary[]> {
      const ordersRef = collection(db, 'organizations', config.organizationSlug, 'stores', storeId, 'orders')
      const snap = await getDocs(
        query(ordersRef, where('channel', '==', 'online'), orderBy('createdAt', 'desc'), limit(50)),
      )

      return Promise.all(
        snap.docs.map(async (orderDoc) => {
          const itemsSnap = await getDocs(collection(orderDoc.ref, 'items'))
          return mapFsOrder(orderDoc, itemsSnap.docs)
        }),
      )
    },

    async settleOrderPayment(
      storeId: string,
      orderId: string,
      input: { paymentMethod: PaymentMethod; tenderedCents: number; changeCents: number; userId?: string | null },
    ): Promise<OrderSummary> {
      const orderRef = doc(db, 'organizations', config.organizationSlug, 'stores', storeId, 'orders', orderId)
      await updateDoc(orderRef, {
        paymentStatus: 'paid',
        paymentMethod: input.paymentMethod,
        tenderedCents: input.tenderedCents,
        changeCents: input.changeCents,
        // No payment gateway exists — this is a manual confirmation by staff,
        // so keep an audit trail of who marked it paid and when.
        paymentConfirmedAt: serverTimestamp(),
        paymentConfirmedByUserId: input.userId ?? null,
      })

      const [orderSnap, itemsSnap] = await Promise.all([getDoc(orderRef), getDocs(collection(orderRef, 'items'))])
      if (!orderSnap.exists()) {
        throw new Error(`Order ${orderId} not found.`)
      }
      return mapFsOrder(orderSnap, itemsSnap.docs)
    },

    async openShift(input: {
      openingCashCents: number
      userId?: string | null
      storeId: string
      organizationId: string
    }): Promise<ShiftSummary> {
      const shiftsRef = collection(db, 'organizations', input.organizationId, 'stores', input.storeId, 'shifts')
      const newRef = doc(shiftsRef)

      await setDoc(newRef, {
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
        openedAt: serverTimestamp(),
        closedAt: null,
      })

      return {
        id: newRef.id,
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
    },

    async closeShift(input: {
      shiftId: string
      countedCashCents: number
      expectedCashCents: number
      userId?: string | null
    }): Promise<ShiftSummary> {
      const shiftRef = doc(
        db, 'organizations', config.organizationSlug, 'stores', config.storeCode, 'shifts', input.shiftId,
      )
      const variance = input.countedCashCents - input.expectedCashCents

      await updateDoc(shiftRef, {
        closedByUserId: input.userId ?? null,
        closingCashCents: input.countedCashCents,
        varianceCashCents: variance,
        closedAt: serverTimestamp(),
      })

      const shift = await loadShift(config.organizationSlug, config.storeCode, input.shiftId)
      if (!shift) {
        throw new Error('Failed to close shift.')
      }
      return shift
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
      const shiftRef = doc(db, 'organizations', input.organizationId, 'stores', input.storeId, 'shifts', input.shiftId)
      const movementRef = doc(collection(shiftRef, 'cashMovements'))
      const isPayIn = input.movementType === 'pay_in'

      await runTransaction(db, async (tx) => {
        tx.set(movementRef, {
          userId: input.userId ?? null,
          movementType: input.movementType,
          amountCents: input.amountCents,
          reason: input.reason ?? null,
          createdAt: serverTimestamp(),
        })

        tx.update(shiftRef, {
          payInsCents: increment(isPayIn ? input.amountCents : 0),
          payOutsCents: increment(isPayIn ? 0 : input.amountCents),
          expectedCashCents: increment(isPayIn ? input.amountCents : -input.amountCents),
        })
      })

      const shift = await loadShift(input.organizationId, input.storeId, input.shiftId)
      if (!shift) {
        throw new Error('Failed to reload shift after cash movement.')
      }
      return shift
    },
  }
}

export type FirebaseSync = ReturnType<typeof createFirebaseSync>
