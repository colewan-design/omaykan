import { groceryCatalogCategories, groceryCatalogProducts } from './groceryCatalog.generated'

/**
 * The one address customers, merchants and riders are told to write to.
 *
 * Kept here rather than typed into each surface so the site footer, the signup
 * card, the POS sign-in and the mobile storefront can't drift apart, and so
 * changing it is one edit. Outgoing mail is sent *from* it too — see
 * MAIL_FROM_ADDRESS in backend/.env.example.
 */
export const SUPPORT_EMAIL = 'support@omaykan.com'

/**
 * A `mailto:` for that address. The subject is worth passing wherever the page
 * already knows why someone is writing — "Signing up" from the registration
 * card, an order's ticket number from order status — because it arrives in the
 * inbox pre-sorted instead of as another blank "Help".
 */
export function supportMailto(subject?: string): string {
  const address = `mailto:${SUPPORT_EMAIL}`
  return subject ? `${address}?subject=${encodeURIComponent(subject)}` : address
}

export type BusinessMode = 'coffee-shop' | 'grocery' | 'restaurant' | 'nail-salon'
export type ProductKind = 'standard' | 'weighted'
export type OrderType = 'dine_in' | 'takeaway'
export type FulfillmentMethod = 'pickup' | 'delivery'
export type OrderStatus = 'preparing' | 'ready' | 'served'
/**
 * Where a delivery order is on the road, as against `OrderStatus`, which is
 * how far the shop has got with making it. Only delivery orders have one;
 * pickup and register sales leave it null.
 *
 * An order is created 'pending' by the storefront, and the seller moves it
 * along from the dashboard: naming a rider takes it to 'assigned', and the two
 * stages after that are the rider's progress.
 */
export type DeliveryStage = 'pending' | 'assigned' | 'picked_up' | 'delivered'

/** In order, so the dashboard can render progress and pick "what's next". */
export const deliveryStages: DeliveryStage[] = ['pending', 'assigned', 'picked_up', 'delivered']
export type PaymentMethod = 'cash' | 'card' | 'ewallet'
export type OrderChannel = 'in_person' | 'online'
export type PaymentStatus = 'paid' | 'unpaid'
export type CashMovementType = 'pay_in' | 'pay_out'
export type Appearance = 'system' | 'light' | 'dark'
export type Theme =
  | 'default'
  | 'ember'
  | 'matcha'
  | 'nocturne'
  | 'casa'
  | 'bloom'
  | 'reserve'
  | 'grove'
  | 'harbor'
  | 'mono'
export const appPageKeys = [
  'dashboard',
  'sales',
  'orders',
  'products',
  'customers',
  'suppliers',
  'employees',
  'inventory',
  'tables',
  'reports',
  'integrations',
  'register',
  'settings',
  'diagnostics',
] as const
export type AppPageKey = (typeof appPageKeys)[number]
export type AppEventType =
  | 'cart_search_used'
  | 'product_added'
  | 'cart_cleared'
  | 'payment_sheet_opened'
  | 'payment_method_selected'
  | 'order_completed'
  | 'order_voided'
  | 'settings_saved'
  | 'low_stock_alert'

export interface Category {
  id: string
  name: string
}

export interface Product {
  id: string
  categoryId: string
  sku: string
  barcode: string
  name: string
  priceCents: number
  /**
   * Original "was" price, for showing a strikethrough and a discount badge.
   * Only meaningful when higher than priceCents — the customer is always
   * charged priceCents, and the API never reads this.
   */
  compareAtPriceCents?: number
  taxRate: number
  kind: ProductKind
  imageUrl?: string
  /**
   * Further photographs of the same product, after `imageUrl`. The detail
   * page's gallery is `[imageUrl, ...photoUrls]`; everywhere else — cards,
   * order lines, the shop directory — still reads `imageUrl` alone and needs
   * to know nothing about galleries.
   */
  photoUrls?: string[]
  /** The name on the pack, which is rarely the name the merchant typed. */
  brand?: string
  /** "Can", "Sachet", "Bottle" — free text, as the merchant filed it. */
  packagingType?: string
  unitLabel?: string
  /** What it is, in the shop's own words. Shown on the storefront's product page. */
  description?: string
  outOfStock?: boolean
  stockQty?: number
  lowStockThreshold?: number
  businessModes: BusinessMode[]
}

export interface Customer {
  id: string
  name: string
  phone?: string
  email?: string
  notes?: string
  /**
   * When they agreed to be enrolled in points. Null or absent: they earn
   * nothing. Keeping a named person's number for a points scheme is personal
   * data under the Data Privacy Act, so the till asks first.
   */
  loyaltyConsentAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface Supplier {
  id: string
  name: string
  contact: string
  categoryIds: string[]
  leadTimeDays: number
  orderWindow: string
  createdAt: string
  updatedAt: string
}

// A lightweight "this stock is already on order" flag for the Suppliers
// reorder queue — not a full purchase-order/receiving workflow, just enough
// to stop the same shortage from being reordered twice while it's in transit.
export interface ReorderMark {
  id: string
  productId: string
  supplierId: string | null
  quantity: number
  markedByUserId?: string | null
  markedAt: string
}

export interface GuestContact {
  name: string
  phone?: string
  email?: string
}

export type TableStatus = 'available' | 'served' | 'reserved'

export interface RestaurantTable {
  id: string
  floor: string
  label: string
  capacity: number
  status: TableStatus
  guestName: string | null
  guestCount: number | null
  seatedAt: string | null
  createdAt: string
  updatedAt: string
}

export type CreateTableInput = Pick<RestaurantTable, 'floor' | 'label' | 'capacity'>

export interface OrderItemSummary {
  productId: string
  name: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
  /**
   * The product's VAT rate at the moment of sale, as a fraction (0.12). Kept
   * on the line because the order's tax was computed from it, and a later
   * change to the product must not change what this receipt says. Absent on
   * orders recorded before it was kept.
   */
  taxRate?: number
}

/**
 * A discount the cashier gives, before it is priced. Exactly one of
 * `percent` and `amountCents`.
 *
 * `manual` is the only kind the till offers today. The table it lands in
 * (`order_discounts`) already has room for `promo` and `loyalty`, and for the
 * statutory senior-citizen and PWD discounts once their VAT treatment is
 * confirmed — see documentation/merchant-features.md §7.
 */
export interface OrderDiscountInput {
  /**
   * `manual` is the cashier's own discretion, and counts against their role's
   * limit. `promo` is a code the server checked (PromoCodeController::check):
   * its amount is the server's, and it is not the cashier's discretion.
   */
  kind: 'manual' | 'promo' | 'loyalty'
  /** 0–100. */
  percent?: number
  amountCents?: number
  /** Points spent, for `loyalty`. Their worth is the server's (LoyaltyController::check). */
  points?: number
  /** Why, in the cashier's words — "regular", "damaged box". The code itself, for a promo. */
  reason?: string | null
  /** Which code, for `promo`. */
  promoCodeId?: string | null
}

/** A discount as it was actually applied: always in centavos. */
export interface AppliedDiscount {
  kind: 'manual' | 'promo' | 'loyalty'
  amountCents: number
  points?: number
  /** Set when the cashier asked for a percentage; null for a flat amount. */
  percent: number | null
  reason: string | null
  appliedByUserId?: string | null
  promoCodeId?: string | null
}

export interface PricedOrder {
  subtotalCents: number
  discountCents: number
  taxCents: number
  totalCents: number
  discount: AppliedDiscount | null
}

/**
 * A rider one shop keeps on file.
 *
 * `onPlatform` is the distinction that matters everywhere this is shown. False
 * means a name and a number: assigning them records who is carrying the order
 * and the shop rings them, which is how deliveries worked before there was a
 * rider app and is still how most of them work. True means a real account —
 * the order lands in that person's app, they can hand it back to the board,
 * and their position feeds the live map.
 */
/**
 * Customer messages, as the shop's inbox sees them. One conversation per
 * customer per shop; a message can name an order. See the backend's
 * SellerConversationController.
 */
export interface ConversationMessage {
  id: number
  from: 'customer' | 'store'
  body: string
  order: { id: string; ticketNumber: string } | null
  createdAt: string
  /** First name of the member of staff who wrote a store message. */
  authorName: string | null
}

export interface ConversationSummary {
  id: string
  /** The customer's name only — their email and phone are theirs to give. */
  customer: { name: string }
  lastMessage: ConversationMessage | null
  lastMessageAt: string | null
  unreadCount: number
}

/** One of the customer's recent orders at this shop, beside the thread. */
export interface ConversationOrderRef {
  id: string
  ticketNumber: string
  status: string | null
  deliveryStage: string | null
  fulfillmentMethod: string | null
  totalCents: number
  createdAt: string | null
}

export interface ConversationThread {
  conversation: ConversationSummary
  messages: ConversationMessage[]
  recentOrders: ConversationOrderRef[]
}

export interface SavedRider {
  id: string
  riderId: string | null
  name: string
  phone: string | null
  note: string | null
  onPlatform: boolean
  timesUsed: number
  lastUsedAt: string | null
  /** The account's status, or null for an off-platform rider. */
  status: string | null
  /** Whether that account is reporting a position right now. */
  online: boolean
}

/** A rider who has delivered for this shop but is not on its list yet. */
export interface RecentRider {
  riderId: string
  name: string
  phone: string | null
  onPlatform: true
  status: string
  online: boolean
}

/** What the assign-a-rider picker is built from. */
export interface SavedRiderDirectory {
  saved: SavedRider[]
  recent: RecentRider[]
}

/**
 * A rider's last reported position, exactly as the API serves it.
 *
 * `stale` and `ageSeconds` are computed server-side rather than from the
 * client's clock: a phone with the wrong time would otherwise declare a live
 * rider missing, or a missing one live.
 */
/** What a shop or a customer is told about the rider carrying an order. */
export interface RiderVehicleProfile {
  id: string
  name: string
  /** Server-relative, or null for a rider who has not uploaded one. */
  photoUrl: string | null
  vehicle: {
    type: string
    make: string | null
    model: string | null
    color: string | null
    /** "red Honda Click" — colour first, because it reads furthest. */
    label: string
    plateNumber: string
  }
  rating: {
    /** Null, not zero, for a rider nobody has rated yet. */
    average: number | null
    count: number
  }
}

export interface RiderPosition {
  lat: number
  lng: number
  headingDeg: number | null
  speedKph: number | null
  accuracyM: number | null
  at: string
  ageSeconds: number
  stale: boolean
}

/** Where a delivery starts and where it ends. */
export interface OrderRoute {
  pickup: { name: string | null; address: string | null; lat: number | null; lng: number | null }
  dropoff: { address: string | null; lat: number | null; lng: number | null }
}

export interface OrderSummary {
  id: string
  ticketNumber: string
  businessMode: BusinessMode
  createdByUserId?: string | null
  customerId: string | null
  customerName: string
  orderType: OrderType
  tableNumber: string | null
  status: OrderStatus
  paymentMethod: PaymentMethod
  subtotalCents: number
  /** Off the subtotal, before tax. Absent (0) on orders from before discounts. */
  discountCents?: number
  discount?: AppliedDiscount | null
  taxCents: number
  totalCents: number
  tenderedCents: number
  changeCents: number
  createdAt: string
  items: OrderItemSummary[]
  // Absent on every order created before online ordering existed — treat a
  // missing channel as 'in_person' and a missing paymentStatus as 'paid'.
  channel?: OrderChannel
  paymentStatus?: PaymentStatus
  guestContact?: GuestContact | null
  // Only meaningful for channel: 'online' — in-person orders have neither.
  fulfillmentMethod?: FulfillmentMethod
  deliveryAddress?: string | null
  // Delivery only, and only once the seller has acted: the dashboard names a
  // rider and walks the stage forward from there. Null on pickup orders and on
  // anything rung up at the register.
  deliveryStage?: DeliveryStage | null
  riderName?: string | null
  riderPhone?: string | null
  /**
   * Set only when the rider is a platform account rather than a name the shop
   * typed in. It is what separates "our nephew on a tricycle", who has no app
   * and no map, from a rider whose position the dashboard can draw.
   */
  riderId?: string | null
  /** The rider's last known fix. Null when nobody is reporting one. */
  riderPosition?: RiderPosition | null
  /**
   * A face, a bike and a score, for the person at the counter.
   *
   * Ungated for the shop, unlike the customer's copy: the shop is a party to
   * this delivery for its whole life, and counter staff handing over a bag need
   * to know which of the three people waiting is the one with this order. Null
   * for a rider typed in at the till — no account, nothing to describe.
   */
  riderProfile?: RiderVehicleProfile | null
  /** The two fixed ends of the trip, for the delivery map. */
  route?: OrderRoute | null
  /** Quoted by the API from the drop-off distance; 0 for pickup. */
  deliveryFeeCents?: number
  // Absent/null on every non-voided order. A void reverses the whole order —
  // inventory restored, excluded from revenue — there is no partial/line-item
  // refund yet.
  voidedAt?: string | null
  voidedByUserId?: string | null
  voidReason?: string | null
  // Set when staff mark an online cash/e-wallet order as paid (see
  // SettleOnlinePaymentSheet) — there is no payment gateway, so this is an
  // audit trail for a manual confirmation, not proof a charge succeeded.
  paymentConfirmedAt?: string | null
  paymentConfirmedByUserId?: string | null
}

export interface CashMovementSummary {
  id: string
  userId?: string | null
  movementType: CashMovementType
  amountCents: number
  reason?: string | null
  createdAt: string
}

export interface ShiftSummary {
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

export interface AppSettings {
  businessMode: BusinessMode
  businessName: string
  businessImageUrl: string
  // The shop's own handle in its storefront link, `/?shop=<slug>`. This
  // replaced `pairingCode`, the code a customer used to type to find the shop
  // — which was also the secret a till paired with, and is gone with pairing.
  // Blank on single-tenant deployments that never went through signup.
  storefrontSlug: string
  appearance: Appearance
  theme: Theme
  accentTotalAnimation: boolean
  telemetryEnabled: boolean
}

export interface RoleDefinition {
  id: string
  name: string
  permissions: Record<AppPageKey, boolean>
  // Separate from page access: whether this role can add employees, assign
  // roles, and edit non-locked role definitions. Absent/undefined on older
  // saved roles means false. Granting the 'admin' role, or granting this
  // flag itself, stays owner-only regardless of who holds it.
  canManageStaff?: boolean
  /**
   * The largest discount this role may give on its own, as a percentage of the
   * order's subtotal. Absent falls back to DEFAULT_DISCOUNT_LIMITS. Admin is
   * always 100. See RolePermissions::maxDiscountPercent on the server, which
   * holds the same defaults.
   */
  maxDiscountPercent?: number
}

/** What each built-in role may discount when the shop has not said. Mirrors the backend. */
export const DEFAULT_DISCOUNT_LIMITS: Record<string, number> = {
  admin: 100,
  manager: 100,
  cashier: 0,
  guest: 0,
}

export function maxDiscountPercentFor(role: RoleDefinition | null | undefined): number {
  if (!role) return 0
  if (role.id === 'admin') return 100
  return role.maxDiscountPercent ?? DEFAULT_DISCOUNT_LIMITS[role.id] ?? 0
}

/**
 * Pages only the true owner may open, whatever a role's permissions say.
 *
 * `employees` is deliberately *not* here: staff management is delegable, and a
 * Manager holds it through `canManageStaff` (see the auth store). It was listed
 * here until 2026-08-27, disagreeing with both the router and defaultRoles —
 * harmlessly, because nothing read this list. The router reads it now, so keep
 * it correct.
 */
export const ownerPageKeys = ['integrations', 'diagnostics'] as const
export type OwnerPageKey = (typeof ownerPageKeys)[number]

export function isOwnerPage(page: AppPageKey): boolean {
  return (ownerPageKeys as readonly string[]).includes(page)
}

export interface UserAccount {
  id: string
  fullName: string
  username: string
  passwordHash: string
  roleId: string
  createdAt: string
}

export interface AuthSession {
  userId: string
  signedInAt: string
  authToken?: string | null
  authSource?: 'local' | 'remote'
}

export interface AppEvent {
  id: string
  eventType: AppEventType
  payload: Record<string, unknown>
  deviceId: string
  appVersion: string
  createdAt: string
  sentAt: string | null
}

export interface CatalogSnapshot {
  categories: Category[]
  products: Product[]
}

export type CreateProductInput = Omit<Product, 'id'>
export type CreateCategoryInput = { name: string }
export type CreateCustomerInput = Pick<Customer, 'name' | 'phone' | 'email' | 'notes' | 'loyaltyConsentAt'>
export type CreateSupplierInput = Pick<Supplier, 'name' | 'contact' | 'categoryIds' | 'leadTimeDays' | 'orderWindow'>

export interface CreateOrderInput {
  /** Set when retrying a sale the server may already have recorded. */
  id?: string
  businessMode: BusinessMode
  customerId?: string | null
  customerName?: string | null
  orderType: OrderType
  tableNumber?: string | null
  paymentMethod: PaymentMethod
  tenderedCents: number
  items: OrderItemSummary[]
  /** Priced by priceOrder, exactly as the cart showed it. */
  discount?: OrderDiscountInput | null
}

export const guestCustomerName = 'Guest'

export const paymentMethodOptions: { value: PaymentMethod; label: string }[] = [
  { value: 'cash', label: 'Cash' },
  { value: 'card', label: 'Card' },
  { value: 'ewallet', label: 'E-wallet' },
]

export const defaultSettings: AppSettings = {
  businessMode: 'coffee-shop',
  businessName: '',
  businessImageUrl: '',
  storefrontSlug: '',
  appearance: 'system',
  theme: 'default',
  accentTotalAnimation: true,
  telemetryEnabled: true,
}

function createPermissions(allowedPages: AppPageKey[]): Record<AppPageKey, boolean> {
  return Object.fromEntries(
    appPageKeys.map((page) => [page, allowedPages.includes(page)]),
  ) as Record<AppPageKey, boolean>
}

export const defaultRoles: RoleDefinition[] = [
  {
    id: 'admin',
    name: 'Admin',
    permissions: createPermissions([...appPageKeys]),
    canManageStaff: true,
  },
  {
    id: 'manager',
    name: 'Manager',
    permissions: createPermissions(['dashboard', 'sales', 'orders', 'products', 'customers', 'suppliers', 'employees', 'inventory', 'reports', 'register', 'settings', 'tables']),
    canManageStaff: true,
  },
  {
    id: 'cashier',
    name: 'Cashier',
    permissions: createPermissions(['dashboard', 'sales', 'orders', 'register', 'settings', 'tables']),
    canManageStaff: false,
  },
  {
    id: 'guest',
    name: 'Guest',
    permissions: createPermissions([]),
    canManageStaff: false,
  },
]

export function businessModeLabel(mode: BusinessMode): string {
  switch (mode) {
    case 'coffee-shop':
      return 'Coffee shop'
    case 'grocery':
      return 'Grocery store'
    case 'restaurant':
      return 'Restaurant'
    case 'nail-salon':
      return 'Nail Salon'
  }
}

export function orderStatusLabel(status: OrderStatus): string {
  switch (status) {
    case 'preparing':
      return 'On Kitchen Hand'
    case 'ready':
      return 'To be Served'
    case 'served':
      return 'All Done'
  }
}

export function deliveryStageLabel(stage: DeliveryStage): string {
  switch (stage) {
    case 'pending':
      return 'Needs a rider'
    case 'assigned':
      return 'Rider assigned'
    case 'picked_up':
      return 'On the way'
    case 'delivered':
      return 'Delivered'
  }
}

/** Null at the end of the road — 'delivered' has nowhere further to go. */
export function nextDeliveryStage(stage: DeliveryStage): DeliveryStage | null {
  const index = deliveryStages.indexOf(stage)
  return index === -1 || index === deliveryStages.length - 1 ? null : deliveryStages[index + 1]
}

export function nextOrderStatus(status: OrderStatus): OrderStatus {
  switch (status) {
    case 'preparing':
      return 'ready'
    case 'ready':
      return 'served'
    case 'served':
      return 'served'
  }
}

export function tableStatusLabel(status: TableStatus): string {
  switch (status) {
    case 'available':
      return 'Available'
    case 'served':
      return 'Served'
    case 'reserved':
      return 'Reserved'
  }
}

export function appPageLabel(page: AppPageKey): string {
  switch (page) {
    case 'dashboard':
      return 'Dashboard'
    case 'sales':
      return 'Sales'
    case 'register':
      return 'Register'
    case 'orders':
      return 'Orders'
    case 'products':
      return 'Products'
    case 'customers':
      return 'Customers'
    case 'suppliers':
      return 'Suppliers'
    case 'employees':
      return 'Employees'
    case 'inventory':
      return 'Inventory'
    case 'tables':
      return 'Tables'
    case 'reports':
      return 'Reports'
    case 'integrations':
      return 'Integrations'
    case 'settings':
      return 'Settings'
    case 'diagnostics':
      return 'Diagnostics'
  }
}

export const demoCategories: Category[] = [
  { id: 'coffee', name: 'Coffee' },
  { id: 'tea', name: 'Tea' },
  { id: 'pastry', name: 'Pastry' },
  { id: 'cold-drinks', name: 'Cold Drinks' },
  { id: 'groceries', name: 'Groceries' },
  { id: 'produce', name: 'Produce' },
  { id: 'dairy', name: 'Dairy' },
  { id: 'snacks', name: 'Snacks' },
  { id: 'starters', name: 'Starters' },
  { id: 'mains', name: 'Mains' },
  { id: 'desserts', name: 'Desserts' },
  { id: 'beverages', name: 'Beverages' },
  { id: 'manicures', name: 'Manicures' },
  { id: 'pedicures', name: 'Pedicures' },
  { id: 'nail-enhancements', name: 'Enhancements' },
  { id: 'nail-addons', name: 'Add-ons' },
  { id: 'salon-retail', name: 'Retail' },
  // The grocery aisles carried over from the legacy scrape (Bakery, Frozen,
  // Meat & Seafood, …) — see groceryCatalog.generated.ts.
  ...groceryCatalogCategories,
]

function standardProduct(
  index: number,
  categoryId: string,
  skuPrefix: string,
  id: string,
  name: string,
  priceCents: number,
  businessModes: BusinessMode[],
  extra: Partial<Product> = {},
): Product {
  return {
    id,
    categoryId,
    sku: `${skuPrefix}-${String(index).padStart(3, '0')}`,
    barcode: String(480000000000 + index + skuOffsets[skuPrefix]),
    name,
    priceCents,
    taxRate: 0.12,
    kind: 'standard',
    businessModes,
    ...extra,
  }
}

const skuOffsets: Record<string, number> = {
  COF: 0,
  TEA: 100,
  PAS: 200,
  CLD: 300,
  GRO: 400,
  PRO: 500,
  DAI: 600,
  SNK: 700,
  APP: 800,
  MAN: 900,
  DES: 1000,
  BEV: 1100,
  MNC: 1200,
  PED: 1300,
  ENH: 1400,
  ADO: 1500,
  RET: 1600,
}

// Coffee-shop items use local photos from apps/web/public/products. A handful
// of the grocery fixtures had no photo at all and rendered as a grey placeholder
// tile on the storefront shelves, so they now hotlink smmarkets.ph from the same
// scrape that feeds groceryCatalog.generated.ts — demo data, on someone else's
// CDN, to be swapped for the tenant's own photos before production.
export const demoProducts: Product[] = [
  // Coffee
  standardProduct(1, 'coffee', 'COF', 'espresso', 'Espresso', 12000, ['coffee-shop'], {
    imageUrl: '/products/espresso.jpg',
    stockQty: 48, lowStockThreshold: 10,
  }),
  standardProduct(2, 'coffee', 'COF', 'latte', 'Cafe Latte', 18000, ['coffee-shop'], {
    compareAtPriceCents: 22000,
    imageUrl: '/products/latte.jpg',
    stockQty: 32, lowStockThreshold: 10,
  }),
  standardProduct(3, 'coffee', 'COF', 'americano', 'Americano', 15000, ['coffee-shop'], {
    imageUrl: '/products/americano.jpg',
    stockQty: 4, lowStockThreshold: 10,
  }),
  standardProduct(4, 'coffee', 'COF', 'cappuccino', 'Cappuccino', 17000, ['coffee-shop'], {
    imageUrl: '/products/cappuccino.jpg',
    stockQty: 2, lowStockThreshold: 10,
  }),
  standardProduct(5, 'coffee', 'COF', 'caramel-mocha', 'Caramel Mocha', 19500, ['coffee-shop'], {
    imageUrl: '/products/caramel-mocha.jpg',
    stockQty: 0, outOfStock: true,
  }),
  standardProduct(6, 'coffee', 'COF', 'flat-white', 'Flat White', 18500, ['coffee-shop'], {
    imageUrl: '/products/flat-white.jpg',
    stockQty: 15, lowStockThreshold: 8,
  }),
  standardProduct(7, 'coffee', 'COF', 'caramel-macchiato', 'Caramel Macchiato', 19000, ['coffee-shop'], {
    imageUrl: '/products/caramel-macchiato.jpg',
    stockQty: 18, lowStockThreshold: 8,
  }),
  standardProduct(8, 'coffee', 'COF', 'affogato', 'Affogato', 21500, ['coffee-shop'], {
    compareAtPriceCents: 26000,
    imageUrl: '/products/affogato.jpg',
    stockQty: 12, lowStockThreshold: 5,
  }),

  // Tea
  standardProduct(1, 'tea', 'TEA', 'matcha', 'Iced Matcha', 21000, ['coffee-shop'], {
    compareAtPriceCents: 25000,
    imageUrl: '/products/matcha.jpg',
    stockQty: 3, lowStockThreshold: 8,
  }),
  standardProduct(2, 'tea', 'TEA', 'black-tea', 'Black Tea', 11000, ['coffee-shop'], {
    imageUrl: '/products/black-tea.jpg',
    stockQty: 22, lowStockThreshold: 8,
  }),
  standardProduct(3, 'tea', 'TEA', 'green-tea', 'Green Tea', 11000, ['coffee-shop'], {
    imageUrl: '/products/green-tea.jpg',
    stockQty: 20, lowStockThreshold: 8,
  }),
  standardProduct(4, 'tea', 'TEA', 'chai-latte', 'Chai Latte', 17500, ['coffee-shop'], {
    imageUrl: '/products/chai-latte.jpg',
    stockQty: 14, lowStockThreshold: 8,
  }),
  standardProduct(5, 'tea', 'TEA', 'earl-grey', 'Earl Grey Tea', 11500, ['coffee-shop'], {
    stockQty: 25, lowStockThreshold: 10,
  }),

  // Pastry
  standardProduct(1, 'pastry', 'PAS', 'croissant', 'Butter Croissant', 9500, ['coffee-shop'], {
    imageUrl: '/products/croissant.jpg',
    stockQty: 24, lowStockThreshold: 8,
  }),
  standardProduct(2, 'pastry', 'PAS', 'blueberry-muffin', 'Blueberry Muffin', 10500, ['coffee-shop'], {
    imageUrl: '/products/blueberry-muffin.jpg',
    stockQty: 0, outOfStock: true,
  }),
  standardProduct(3, 'pastry', 'PAS', 'chocolate-muffin', 'Chocolate Muffin', 10500, ['coffee-shop'], {
    imageUrl: '/products/chocolate-muffin.jpg',
    stockQty: 18, lowStockThreshold: 6,
  }),
  standardProduct(4, 'pastry', 'PAS', 'cinnamon-roll', 'Cinnamon Roll', 12000, ['coffee-shop'], {
    imageUrl: '/products/cinnamon-roll.jpg',
    stockQty: 12, lowStockThreshold: 5,
  }),
  standardProduct(5, 'pastry', 'PAS', 'banana-bread', 'Banana Bread Slice', 9800, ['coffee-shop'], {
    imageUrl: '/products/banana-bread.jpg',
    stockQty: 15, lowStockThreshold: 5,
  }),
  standardProduct(6, 'pastry', 'PAS', 'bagel', 'Plain Bagel', 8500, ['coffee-shop'], {
    imageUrl: '/products/bagel.jpg',
    stockQty: 10, lowStockThreshold: 4,
  }),
  standardProduct(7, 'pastry', 'PAS', 'apple-danish', 'Apple Danish', 11000, ['coffee-shop'], {
    imageUrl: '/products/apple-danish.jpg',
    stockQty: 8, lowStockThreshold: 4,
  }),
  standardProduct(8, 'pastry', 'PAS', 'glazed-donut', 'Glazed Donut', 7500, ['coffee-shop'], {
    imageUrl: '/products/glazed-donut.jpg',
    stockQty: 16, lowStockThreshold: 6,
  }),
  standardProduct(9, 'pastry', 'PAS', 'brownie', 'Chocolate Brownie', 9200, ['coffee-shop'], {
    imageUrl: '/products/chocolate-muffin.jpg',
    stockQty: 14, lowStockThreshold: 5,
  }),
  standardProduct(10, 'pastry', 'PAS', 'sandwich', 'Club Sandwich', 15500, ['coffee-shop'], {
    imageUrl: '/products/bread-loaf.jpg',
    stockQty: 8, lowStockThreshold: 3,
  }),

  // Cold drinks
  standardProduct(1, 'cold-drinks', 'CLD', 'iced-americano', 'Iced Americano', 16000, ['coffee-shop'], {
    imageUrl: '/products/iced-americano.jpg',
    stockQty: 20, lowStockThreshold: 8,
  }),
  standardProduct(2, 'cold-drinks', 'CLD', 'iced-latte', 'Iced Latte', 19500, ['coffee-shop'], {
    imageUrl: '/products/iced-latte.jpg',
    stockQty: 18, lowStockThreshold: 8,
  }),
  standardProduct(3, 'cold-drinks', 'CLD', 'iced-mocha', 'Iced Mocha', 21000, ['coffee-shop'], {
    imageUrl: '/products/iced-mocha.jpg',
    stockQty: 15, lowStockThreshold: 6,
  }),
  standardProduct(4, 'cold-drinks', 'CLD', 'cold-brew', 'Cold Brew', 17500, ['coffee-shop'], {
    imageUrl: '/products/cold-brew.jpg',
    stockQty: 0, outOfStock: true,
  }),
  standardProduct(5, 'cold-drinks', 'CLD', 'lemonade', 'Fresh Lemonade', 13000, ['coffee-shop'], {
    imageUrl: '/products/lemonade.jpg',
    stockQty: 12, lowStockThreshold: 5,
  }),
  standardProduct(6, 'cold-drinks', 'CLD', 'sparkling-water', 'Sparkling Water', 8000, ['coffee-shop'], {
    stockQty: 30, lowStockThreshold: 10,
  }),
  standardProduct(7, 'cold-drinks', 'CLD', 'water-bottle', 'Water Bottle', 6500, ['coffee-shop'], {
    imageUrl: '/products/still-water.jpg',
    stockQty: 35, lowStockThreshold: 12,
  }),

  // Groceries
  standardProduct(1, 'groceries', 'GRO', 'milk', 'Fresh Milk 1L', 9800, ['grocery'], {
    imageUrl: '/products/milk.jpg',
    stockQty: 60, lowStockThreshold: 15,
  }),
  standardProduct(2, 'groceries', 'GRO', 'rice', 'Premium Rice 5kg', 28500, ['grocery'], {
    imageUrl: '/products/rice.jpg',
    stockQty: 5, lowStockThreshold: 15,
  }),
  standardProduct(3, 'groceries', 'GRO', 'cooking-oil', 'Cooking Oil 1L', 11500, ['grocery'], {
    imageUrl: '/products/cooking-oil.jpg',
    stockQty: 0, outOfStock: true,
  }),
  standardProduct(4, 'groceries', 'GRO', 'white-sugar', 'White Sugar 1kg', 7200, ['grocery'], {
    imageUrl: '/products/white-sugar.jpg',
    stockQty: 40, lowStockThreshold: 12,
  }),
  standardProduct(5, 'groceries', 'GRO', 'iodized-salt', 'Iodized Salt 500g', 2500, ['grocery'], {
    imageUrl: 'https://smmarkets.ph/media/catalog/product/s/m/sm_bonus_iodized_salt_500g.jpg?optimize=low&bg-color=255%2C255%2C255&fit=bounds&height=300&width=300',
    stockQty: 55, lowStockThreshold: 15,
  }),
  standardProduct(6, 'groceries', 'GRO', 'soy-sauce', 'Soy Sauce 1L', 6800, ['grocery'], {
    imageUrl: 'https://smmarkets.ph/media/catalog/product/2/7/27._10006753_datu_puti_soy_sauce_1lt.png?optimize=low&bg-color=255%2C255%2C255&fit=bounds&height=300&width=300',
    stockQty: 28, lowStockThreshold: 10,
  }),
  standardProduct(7, 'groceries', 'GRO', 'instant-noodles', 'Instant Noodles', 1800, ['grocery'], {
    imageUrl: '/products/instant-noodles.jpg',
    stockQty: 80, lowStockThreshold: 20,
  }),
  standardProduct(8, 'groceries', 'GRO', 'canned-sardines', 'Canned Sardines', 3200, ['grocery'], {
    imageUrl: 'https://smmarkets.ph/media/catalog/product/1/0/10007027_sm_bonus_sardines_chili_155g.png?optimize=low&bg-color=255%2C255%2C255&fit=bounds&height=300&width=300',
    stockQty: 65, lowStockThreshold: 20,
  }),
  standardProduct(9, 'groceries', 'GRO', 'bread-loaf', 'White Bread Loaf', 6500, ['grocery'], {
    imageUrl: '/products/bread-loaf.jpg',
    stockQty: 22, lowStockThreshold: 8,
  }),

  // Produce (sold by weight)
  standardProduct(1, 'produce', 'PRO', 'bananas', 'Bananas', 7600, ['grocery'], {
    kind: 'weighted',
    unitLabel: '/ kg',
    imageUrl: '/products/bananas.jpg',
    stockQty: 25, lowStockThreshold: 8,
  }),
  standardProduct(2, 'produce', 'PRO', 'tomatoes', 'Tomatoes', 14500, ['grocery'], {
    kind: 'weighted',
    unitLabel: '/ kg',
    imageUrl: '/products/tomatoes.jpg',
    stockQty: 18, lowStockThreshold: 6,
  }),
  standardProduct(3, 'produce', 'PRO', 'red-onions', 'Red Onions', 13000, ['grocery'], {
    kind: 'weighted',
    unitLabel: '/ kg',
    imageUrl: '/products/red-onions.jpg',
    stockQty: 20, lowStockThreshold: 8,
  }),
  standardProduct(4, 'produce', 'PRO', 'garlic', 'Garlic', 22000, ['grocery'], {
    kind: 'weighted',
    unitLabel: '/ kg',
    imageUrl: '/products/garlic.jpg',
    stockQty: 12, lowStockThreshold: 5,
  }),
  standardProduct(5, 'produce', 'PRO', 'potatoes', 'Potatoes', 9500, ['grocery'], {
    kind: 'weighted',
    unitLabel: '/ kg',
    imageUrl: '/products/potatoes.jpg',
    stockQty: 30, lowStockThreshold: 10,
  }),
  standardProduct(6, 'produce', 'PRO', 'carrots', 'Carrots', 11000, ['grocery'], {
    kind: 'weighted',
    unitLabel: '/ kg',
    imageUrl: '/products/carrots.jpg',
    stockQty: 22, lowStockThreshold: 8,
  }),
  standardProduct(7, 'produce', 'PRO', 'cabbage', 'Cabbage', 6500, ['grocery'], {
    kind: 'weighted',
    unitLabel: '/ kg',
    imageUrl: '/products/cabbage.jpg',
    stockQty: 15, lowStockThreshold: 5,
  }),
  standardProduct(8, 'produce', 'PRO', 'calamansi', 'Calamansi', 18000, ['grocery'], {
    imageUrl: 'https://smmarkets.ph/media/catalog/product/1/0/10-20481794-calamansi_1.png?optimize=low&bg-color=255%2C255%2C255&fit=bounds&height=300&width=300',
    kind: 'weighted',
    unitLabel: '/ kg',
    stockQty: 8, lowStockThreshold: 3,
  }),

  // Dairy
  standardProduct(1, 'dairy', 'DAI', 'cheddar-cheese', 'Cheddar Cheese 200g', 14500, ['grocery'], {
    imageUrl: '/products/cheddar-cheese.jpg',
    stockQty: 18, lowStockThreshold: 6,
  }),
  standardProduct(2, 'dairy', 'DAI', 'butter', 'Butter 200g', 13500, ['grocery'], {
    imageUrl: 'https://smmarkets.ph/media/catalog/product/1/0/10051230020_copy_.png?optimize=low&bg-color=255%2C255%2C255&fit=bounds&height=300&width=300',
    stockQty: 22, lowStockThreshold: 8,
  }),
  standardProduct(3, 'dairy', 'DAI', 'yogurt-cup', 'Yogurt Cup', 4500, ['grocery'], {
    imageUrl: '/products/yogurt-cup.jpg',
    stockQty: 30, lowStockThreshold: 10,
  }),
  standardProduct(4, 'dairy', 'DAI', 'eggs-dozen', 'Eggs (dozen)', 9000, ['grocery'], {
    imageUrl: '/products/eggs-dozen.jpg',
    stockQty: 25, lowStockThreshold: 8,
  }),
  standardProduct(5, 'dairy', 'DAI', 'condensed-milk', 'Condensed Milk 300ml', 5800, ['grocery'], {
    imageUrl: '/products/condensed-milk.jpg',
    stockQty: 35, lowStockThreshold: 12,
  }),

  // Snacks
  standardProduct(1, 'snacks', 'SNK', 'potato-chips', 'Potato Chips', 5500, ['grocery'], {
    imageUrl: '/products/potato-chips.jpg',
    stockQty: 45, lowStockThreshold: 15,
  }),
  standardProduct(2, 'snacks', 'SNK', 'chocolate-bar', 'Chocolate Bar', 4800, ['grocery'], {
    imageUrl: '/products/chocolate-bar.jpg',
    stockQty: 50, lowStockThreshold: 15,
  }),
  standardProduct(3, 'snacks', 'SNK', 'crackers', 'Crackers Pack', 3800, ['grocery'], {
    imageUrl: '/products/crackers.jpg',
    stockQty: 38, lowStockThreshold: 12,
  }),
  standardProduct(4, 'snacks', 'SNK', 'roasted-peanuts', 'Roasted Peanuts 100g', 4200, ['grocery'], {
    imageUrl: '/products/roasted-peanuts.jpg',
    stockQty: 42, lowStockThreshold: 12,
  }),
  standardProduct(5, 'snacks', 'SNK', 'assorted-biscuits', 'Assorted Biscuits', 5200, ['grocery'], {
    imageUrl: 'https://smmarkets.ph/media/catalog/product/h/t/httpsshop.smmarkets.phpubmediawysiwygro_photos11052020440415-1.png?optimize=low&bg-color=255%2C255%2C255&fit=bounds&height=300&width=300',
    stockQty: 30, lowStockThreshold: 10,
  }),

  // Restaurant — Starters
  standardProduct(1, 'starters', 'APP', 'spring-rolls', 'Spring Rolls (6 pcs)', 12000, ['restaurant'], {
    imageUrl: '/products/spring-rolls.jpg',
    stockQty: 20, lowStockThreshold: 6,
  }),
  standardProduct(2, 'starters', 'APP', 'soup-of-the-day', 'Soup of the Day', 15000, ['restaurant'], {
    imageUrl: '/products/soup-of-the-day.jpg',
    stockQty: 15, lowStockThreshold: 5,
  }),
  standardProduct(3, 'starters', 'APP', 'calamari', 'Crispy Calamari', 18000, ['restaurant'], {
    imageUrl: '/products/calamari.jpg',
    stockQty: 18, lowStockThreshold: 6,
  }),
  standardProduct(4, 'starters', 'APP', 'tokwat-baboy', "Tokwa't Baboy", 14500, ['restaurant'], {
    stockQty: 12, lowStockThreshold: 4,
  }),
  standardProduct(5, 'starters', 'APP', 'mixed-greens-salad', 'Mixed Greens Salad', 13000, ['restaurant', 'coffee-shop'], {
    imageUrl: '/products/mixed-greens-salad.jpg',
    stockQty: 14, lowStockThreshold: 5,
  }),

  // Restaurant — Mains
  standardProduct(1, 'mains', 'MAN', 'chicken-adobo', 'Chicken Adobo', 28000, ['restaurant'], {
    imageUrl: '/products/chicken-adobo.jpg',
    stockQty: 22, lowStockThreshold: 6,
  }),
  standardProduct(2, 'mains', 'MAN', 'pork-sinigang', 'Pork Sinigang', 32000, ['restaurant'], {
    stockQty: 15, lowStockThreshold: 5,
  }),
  standardProduct(3, 'mains', 'MAN', 'grilled-bangus', 'Grilled Bangus', 35000, ['restaurant'], {
    imageUrl: '/products/grilled-bangus.jpg',
    stockQty: 12, lowStockThreshold: 4,
  }),
  standardProduct(4, 'mains', 'MAN', 'beef-caldereta', 'Beef Caldereta', 38000, ['restaurant'], {
    imageUrl: '/products/beef-caldereta.jpg',
    stockQty: 10, lowStockThreshold: 4,
  }),
  standardProduct(5, 'mains', 'MAN', 'kare-kare', 'Kare-Kare', 42000, ['restaurant'], {
    imageUrl: '/products/kare-kare.jpg',
    stockQty: 8, lowStockThreshold: 3,
  }),
  standardProduct(6, 'mains', 'MAN', 'pork-bbq', 'Pork BBQ (3 sticks)', 18000, ['restaurant'], {
    imageUrl: '/products/pork-bbq.jpg',
    stockQty: 25, lowStockThreshold: 8,
  }),
  standardProduct(7, 'mains', 'MAN', 'laing', 'Laing', 20000, ['restaurant'], {
    stockQty: 18, lowStockThreshold: 6,
  }),
  standardProduct(8, 'mains', 'MAN', 'steamed-rice', 'Steamed Rice', 5000, ['restaurant'], {
    imageUrl: '/products/steamed-rice.jpg',
    stockQty: 50, lowStockThreshold: 15,
  }),

  // Restaurant — Desserts
  standardProduct(1, 'desserts', 'DES', 'leche-flan', 'Leche Flan', 12000, ['restaurant'], {
    imageUrl: '/products/leche-flan.jpg',
    stockQty: 16, lowStockThreshold: 5,
  }),
  standardProduct(2, 'desserts', 'DES', 'halo-halo', 'Halo-Halo', 18500, ['restaurant'], {
    stockQty: 12, lowStockThreshold: 4,
  }),
  standardProduct(3, 'desserts', 'DES', 'ice-cream', 'Ice Cream (2 scoops)', 15000, ['restaurant'], {
    imageUrl: '/products/ice-cream.jpg',
    stockQty: 20, lowStockThreshold: 6,
  }),
  standardProduct(4, 'desserts', 'DES', 'biko', 'Biko', 10000, ['restaurant'], {
    stockQty: 14, lowStockThreshold: 5,
  }),
  standardProduct(5, 'desserts', 'DES', 'turon', 'Turon (3 pcs)', 8000, ['restaurant'], {
    imageUrl: '/products/turon.jpg',
    stockQty: 18, lowStockThreshold: 6,
  }),

  // Restaurant — Beverages
  standardProduct(1, 'beverages', 'BEV', 'still-water', 'Still Water', 5000, ['restaurant'], {
    imageUrl: '/products/still-water.jpg',
    stockQty: 40, lowStockThreshold: 12,
  }),
  standardProduct(2, 'beverages', 'BEV', 'iced-tea', 'Iced Tea', 9000, ['restaurant'], {
    imageUrl: '/products/iced-tea.jpg',
    stockQty: 35, lowStockThreshold: 12,
  }),
  standardProduct(3, 'beverages', 'BEV', 'calamansi-juice', 'Calamansi Juice', 8500, ['restaurant'], {
    imageUrl: '/products/calamansi-juice.jpg',
    stockQty: 28, lowStockThreshold: 10,
  }),
  standardProduct(4, 'beverages', 'BEV', 'soda-can', 'Soda (can)', 7500, ['restaurant'], {
    imageUrl: '/products/soda-can.jpg',
    stockQty: 45, lowStockThreshold: 15,
  }),
  standardProduct(5, 'beverages', 'BEV', 'buko-juice', 'Fresh Buko Juice', 12000, ['restaurant'], {
    imageUrl: '/products/buko-juice.jpg',
    stockQty: 18, lowStockThreshold: 6,
  }),
  standardProduct(6, 'beverages', 'BEV', 'san-miguel', 'San Miguel Beer', 11000, ['restaurant'], {
    stockQty: 30, lowStockThreshold: 10,
  }),

  // Nail Salon — Manicures
  standardProduct(1, 'manicures', 'MNC', 'classic-manicure', 'Classic Manicure', 25000, ['nail-salon'], {
    imageUrl: '/products/classic-manicure.jpg',
  }),
  standardProduct(2, 'manicures', 'MNC', 'express-manicure', 'Express Manicure', 18000, ['nail-salon'], {
    imageUrl: '/products/express-manicure.jpg',
  }),
  standardProduct(3, 'manicures', 'MNC', 'gel-manicure', 'Gel Manicure', 45000, ['nail-salon'], {
    imageUrl: '/products/gel-manicure.jpg',
  }),
  standardProduct(4, 'manicures', 'MNC', 'spa-manicure', 'Spa Manicure', 55000, ['nail-salon'], {
    imageUrl: '/products/spa-manicure.jpg',
  }),

  // Nail Salon — Pedicures
  standardProduct(1, 'pedicures', 'PED', 'classic-pedicure', 'Classic Pedicure', 35000, ['nail-salon'], {
    imageUrl: '/products/classic-pedicure.jpg',
  }),
  standardProduct(2, 'pedicures', 'PED', 'express-pedicure', 'Express Pedicure', 25000, ['nail-salon'], {
    imageUrl: '/products/express-pedicure.jpg',
  }),
  standardProduct(3, 'pedicures', 'PED', 'gel-pedicure', 'Gel Pedicure', 55000, ['nail-salon'], {
    imageUrl: '/products/gel-pedicure.jpg',
  }),
  standardProduct(4, 'pedicures', 'PED', 'spa-pedicure', 'Spa Pedicure', 65000, ['nail-salon'], {
    imageUrl: '/products/spa-pedicure.jpg',
  }),

  // Nail Salon — Enhancements
  standardProduct(1, 'nail-enhancements', 'ENH', 'nail-refill', 'Nail Refill / Fill-in', 50000, ['nail-salon'], {
    imageUrl: '/products/nail-refill.jpg',
  }),
  standardProduct(2, 'nail-enhancements', 'ENH', 'acrylic-full-set', 'Acrylic Full Set', 80000, ['nail-salon'], {
    imageUrl: '/products/acrylic-full-set.jpg',
  }),
  standardProduct(3, 'nail-enhancements', 'ENH', 'dip-powder-full-set', 'Dip Powder Full Set', 85000, ['nail-salon'], {
    imageUrl: '/products/dip-powder-full-set.jpg',
  }),
  standardProduct(4, 'nail-enhancements', 'ENH', 'gel-extension-full-set', 'Gel Extension Full Set', 90000, ['nail-salon'], {
    imageUrl: '/products/gel-extension-full-set.jpg',
  }),

  // Nail Salon — Add-ons
  standardProduct(1, 'nail-addons', 'ADO', 'nail-art-per-nail', 'Nail Art (per nail)', 5000, ['nail-salon'], {
    imageUrl: '/products/nail-art-per-nail.jpg',
  }),
  standardProduct(2, 'nail-addons', 'ADO', 'rhinestone-accent', 'Rhinestone Accent', 8000, ['nail-salon'], {
    imageUrl: '/products/rhinestone-accent.jpg',
  }),
  standardProduct(3, 'nail-addons', 'ADO', 'french-tip', 'French Tip', 10000, ['nail-salon'], {
    imageUrl: '/products/french-tip.jpg',
  }),
  standardProduct(4, 'nail-addons', 'ADO', 'paraffin-wax-treatment', 'Paraffin Wax Treatment', 20000, ['nail-salon'], {
    imageUrl: '/products/paraffin-wax-treatment.jpg',
  }),
  standardProduct(5, 'nail-addons', 'ADO', 'chrome-cat-eye-finish', 'Chrome / Cat Eye Finish', 15000, ['nail-salon'], {
    imageUrl: '/products/chrome-cat-eye-finish.jpg',
  }),

  // Nail Salon — Retail
  standardProduct(1, 'salon-retail', 'RET', 'nail-polish-bottle', 'Nail Polish Bottle', 15000, ['nail-salon'], {
    imageUrl: '/products/nail-polish-bottle.jpg',
    stockQty: 40, lowStockThreshold: 10,
  }),
  standardProduct(2, 'salon-retail', 'RET', 'cuticle-oil', 'Cuticle Oil', 18000, ['nail-salon'], {
    imageUrl: '/products/cuticle-oil.jpg',
    stockQty: 30, lowStockThreshold: 8,
  }),
  standardProduct(3, 'salon-retail', 'RET', 'hand-cream', 'Hand Cream', 22000, ['nail-salon'], {
    imageUrl: '/products/hand-cream.jpg',
    stockQty: 25, lowStockThreshold: 8,
  }),
  standardProduct(4, 'salon-retail', 'RET', 'nail-strengthener', 'Nail Strengthener', 25000, ['nail-salon'], {
    imageUrl: '/products/nail-strengthener.jpg',
    stockQty: 20, lowStockThreshold: 6,
  }),

  // A real-sized grocery shelf on top of the hand-written one above, so
  // grocery mode demos with hundreds of priced, photographed items instead of
  // a dozen placeholders.
  ...groceryCatalogProducts,
]

export function formatCurrency(amountCents: number): string {
  return new Intl.NumberFormat('en-PH', {
    style: 'currency',
    currency: 'PHP',
    minimumFractionDigits: 2,
  }).format(amountCents / 100)
}

/**
 * Percentage off, or null when the product isn't discounted.
 * One implementation so the storefront card, the product page and the landing
 * page can't disagree about what counts as a discount or how it rounds.
 */
export function discountPercent(product: Pick<Product, 'priceCents' | 'compareAtPriceCents'>): number | null {
  const was = product.compareAtPriceCents
  if (!was || !Number.isFinite(was) || was <= product.priceCents) return null
  return Math.round(((was - product.priceCents) / was) * 100)
}

export function formatCompactDate(value: string): string {
  return new Intl.DateTimeFormat('en-PH', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value))
}

/**
 * Price an order: the one calculation the cart shows, the till records and the
 * receipt prints, so the three can never disagree.
 *
 * VAT is charged per line at that line's own rate (a zero-rated vegetable next
 * to a 12% soft drink). An order-level discount comes off before tax, so VAT
 * is on what the customer actually pays for: it is shared across the lines in
 * proportion to their value — whole centavos, floored, the remainder on the
 * largest line — and each line is taxed on what is left of it.
 *
 * The backend applies the same split when it checks a synced order
 * (SyncController); change one and change both.
 */
export function priceOrder(
  lines: Array<{ lineTotalCents: number; taxRate: number }>,
  discount?: OrderDiscountInput | null,
): PricedOrder {
  const subtotalCents = lines.reduce((sum, line) => sum + line.lineTotalCents, 0)

  let discountCents = 0
  if (discount && subtotalCents > 0) {
    const requested = discount.percent != null
      ? Math.round((subtotalCents * Math.min(100, Math.max(0, discount.percent))) / 100)
      : Math.round(discount.amountCents ?? 0)
    discountCents = Math.min(subtotalCents, Math.max(0, requested))
  }

  const shares = lines.map((line) =>
    subtotalCents > 0 ? Math.floor((discountCents * line.lineTotalCents) / subtotalCents) : 0,
  )
  const remainder = discountCents - shares.reduce((sum, share) => sum + share, 0)
  if (remainder > 0 && lines.length > 0) {
    let largest = 0
    lines.forEach((line, index) => {
      if (line.lineTotalCents > lines[largest].lineTotalCents) largest = index
    })
    shares[largest] += remainder
  }

  const taxCents = lines.reduce(
    (sum, line, index) => sum + calculateTax(line.lineTotalCents - shares[index], line.taxRate),
    0,
  )

  return {
    subtotalCents,
    discountCents,
    taxCents,
    totalCents: subtotalCents - discountCents + taxCents,
    discount: discount && discountCents > 0
      ? {
          kind: discount.kind,
          amountCents: discountCents,
          percent: discount.percent ?? null,
          reason: discount.reason?.trim() || null,
          ...(discount.promoCodeId ? { promoCodeId: discount.promoCodeId } : {}),
          ...(discount.points ? { points: discount.points } : {}),
        }
      : null,
  }
}

/** A discount as a percentage of the subtotal it came off — what role limits are checked against. */
export function discountPercentOf(discountCents: number, subtotalCents: number): number {
  return subtotalCents > 0 ? (discountCents / subtotalCents) * 100 : 0
}

export function calculateTax(amountCents: number, rate: number): number {
  return Math.round(amountCents * rate)
}

export function slugTicket(id: string): string {
  return id.slice(0, 8).toUpperCase()
}

const categoryTagVars = [
  '--tag-1-blue',
  '--tag-2-aqua',
  '--tag-3-yellow',
  '--tag-4-green',
  '--tag-5-violet',
  '--tag-6-red',
  '--tag-7-magenta',
  '--tag-8-orange',
]

// Stable per-category color identity for tag pills — same category always gets
// the same hue, whether it's a seeded demo category or one a store created.
export function categoryTagVar(categoryId: string): string {
  const seededIndex = demoCategories.findIndex((category) => category.id === categoryId)
  if (seededIndex >= 0) {
    return `var(${categoryTagVars[seededIndex % categoryTagVars.length]})`
  }

  let hash = 0
  for (let i = 0; i < categoryId.length; i++) {
    hash = (hash * 31 + categoryId.charCodeAt(i)) >>> 0
  }
  return `var(${categoryTagVars[hash % categoryTagVars.length]})`
}

// -- A shop's own page ---------------------------------------------------------
//
// The link a seller hands out. With a shop domain configured it is the shop's
// own subdomain, `https://<slug>.omaykan.com`; without one (a laptop, a preview
// build) it is `/shop/<slug>` on whatever origin is serving. Built and read
// here, in one place, because the till's Settings and Dashboard write it and
// the storefront's shop entry parses it back — and a link the two disagreed
// about would be a link that opens nothing.

export const STOREFRONT_PATH_PREFIX = '/shop/'

/**
 * Labels that are the platform's, never a shop's. Signup refuses these as
 * slugs too (SignupController::RESERVED_SLUGS) — keep the two lists together.
 */
export const RESERVED_SHOP_SUBDOMAINS: readonly string[] = [
  'www', 'api', 'app', 'admin', 'mail', 'smtp', 'imap', 'pop', 'ftp', 'cdn',
  'static', 'assets', 'shop', 'shops', 'store', 'stores', 'rider', 'riders',
  'seller', 'sellers', 'help', 'support', 'status', 'blog', 'docs', 'dev',
  'staging', 'test', 'demo', 'platform', 'dashboard', 'account', 'cart',
  'checkout', 'reverb', 'ws', 'omaykan',
]

/** A slug that can stand as one DNS label: lowercase, digits, inner hyphens, ≤ 63. */
export function isShopSubdomainLabel(label: string): boolean {
  return /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label) && !RESERVED_SHOP_SUBDOMAINS.includes(label)
}

export function storefrontPath(slug: string): string {
  return `${STOREFRONT_PATH_PREFIX}${encodeURIComponent(slug)}`
}

/**
 * The full link to a shop's page.
 *
 * `rootDomain` is the bare domain shops hang off (`omaykan.com`), blank for
 * none. A slug that cannot be a hostname — an old one with an underscore, say —
 * falls back to the path form on `origin` rather than producing a dead link.
 */
export function storefrontUrl(slug: string, options: { rootDomain?: string; origin: string }): string {
  const root = (options.rootDomain ?? '').trim().toLowerCase()
  if (root !== '' && isShopSubdomainLabel(slug)) return `https://${slug}.${root}`
  return `${options.origin.replace(/\/+$/, '')}${storefrontPath(slug)}`
}

/** The slug a shop subdomain names, or '' for the root, `www`, or any other host. */
export function slugFromShopHost(hostname: string, rootDomain: string): string {
  const host = hostname.trim().toLowerCase().replace(/\.$/, '')
  const root = rootDomain.trim().toLowerCase()
  if (root === '' || !host.endsWith(`.${root}`)) return ''
  const label = host.slice(0, -(root.length + 1))
  return isShopSubdomainLabel(label) ? label : ''
}

/**
 * The slug in a `/shop/<slug>` path, or '' for any other path.
 *
 * A trailing slash is tolerated — it is what a link pasted into a chat app
 * often grows — but anything deeper is not a shop page.
 */
export function slugFromStorefrontPath(pathname: string): string {
  if (!pathname.startsWith(STOREFRONT_PATH_PREFIX)) return ''
  const rest = pathname.slice(STOREFRONT_PATH_PREFIX.length).replace(/\/$/, '')
  if (rest === '' || rest.includes('/')) return ''
  try {
    return decodeURIComponent(rest)
  } catch {
    return ''
  }
}
