import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { getPosRepository } from '@pos/core/services/runtime'
import {
  defaultSettings,
  discountPercentOf,
  formatCurrency,
  priceOrder,
  guestCustomerName,
  type AppEvent,
  type AppSettings,
  type Category,
  type CashMovementType,
  type CreateCategoryInput,
  type CreateCustomerInput,
  type CreateProductInput,
  type CreateSupplierInput,
  type CreateTableInput,
  type Customer,
  type DeliveryStage,
  type OrderDiscountInput,
  type OrderStatus,
  type OrderSummary,
  type OrderType,
  type PaymentMethod,
  type Product,
  type ReorderMark,
  type RestaurantTable,
  type RiderPosition,
  type ShiftSummary,
  type Supplier,
} from '@pos/shared/index'

export const usePosStore = defineStore('pos', () => {
  const repository = getPosRepository()

  const products = ref<Product[]>([])
  const categories = ref<{ id: string; name: string }[]>([])
  const customers = ref<Customer[]>([])
  const tables = ref<RestaurantTable[]>([])
  const suppliers = ref<Supplier[]>([])
  const reorderMarks = ref<ReorderMark[]>([])
  const orders = ref<OrderSummary[]>([])
  // Orders placed through the public storefront (POST /api/online-orders on the
  // Laravel backend) — kept separate from `orders` (this device's own
  // locally-owned sales) so an unpaid online order can never be double-counted
  // in local shift/sales totals.
  const onlineOrders = ref<OrderSummary[]>([])
  const appEvents = ref<AppEvent[]>([])
  const settings = ref<AppSettings>(defaultSettings)
  const activeShift = ref<ShiftSummary | null>(null)
  const shiftHistory = ref<ShiftSummary[]>([])
  const search = ref('')
  const selectedCategoryId = ref('all')
  const paymentMethod = ref<PaymentMethod>('cash')
  const orderType = ref<OrderType>('takeaway')
  const tableNumber = ref('')
  const selectedCustomerId = ref<string | null>(null)
  const tenderedCents = ref(0)
  const tenderedInput = ref('')
  const cart = ref<Record<string, number>>({})
  const lastCompletedOrder = ref<OrderSummary | null>(null)
  const lowStockAlert = ref<Product[]>([])
  const shiftError = ref('')
  // Why the last sale was not completed — the server's reason, or no
  // connection. Shown on the payment sheet; cleared when the sheet opens.
  const checkoutError = ref('')
  // The id a refused or unanswered sale was sent under, kept while the cart
  // is unchanged so trying again cannot record it twice.
  let pendingSale: { id: string; fingerprint: string } | null = null
  const isReady = ref(false)

  function syncTenderedFromInput() {
    if (!tenderedInput.value) {
      tenderedCents.value = 0
      return
    }

    const [wholePartRaw, fractionPartRaw = ''] = tenderedInput.value.split('.')
    const wholePart = wholePartRaw.replace(/\D/g, '') || '0'
    const fractionPart = fractionPartRaw.replace(/\D/g, '').slice(0, 2)
    const paddedFraction = fractionPart.padEnd(2, '0')

    tenderedCents.value = Number(wholePart) * 100 + Number(paddedFraction)
  }

  function formatTenderedInput(cents: number) {
    const wholePart = Math.floor(cents / 100)
    const fractionPart = cents % 100

    if (fractionPart === 0) {
      return String(wholePart)
    }

    return `${wholePart}.${String(fractionPart).padStart(2, '0')}`
  }

  const filteredProducts = computed(() => {
    const needle = search.value.trim().toLowerCase()
    return products.value.filter((product) => {
      const inMode = product.businessModes.includes(settings.value.businessMode)
      const inCategory = selectedCategoryId.value === 'all' || product.categoryId === selectedCategoryId.value
      const matches =
        !needle ||
        product.name.toLowerCase().includes(needle) ||
        product.sku.toLowerCase().includes(needle) ||
        product.barcode.includes(needle)

      return inMode && inCategory && matches
    })
  })

  const cartLines = computed(() =>
    Object.entries(cart.value)
      .map(([productId, quantity]) => {
        const product = products.value.find((entry) => entry.id === productId)
        if (!product || quantity <= 0) {
          return null
        }

        const subtotalCents = Math.round(product.priceCents * quantity)
        return { product, quantity, subtotalCents }
      })
      .filter((line): line is { product: Product; quantity: number; subtotalCents: number } => Boolean(line)),
  )

  /**
   * The discount on this sale, as the cashier asked for it. Priced — and
   * capped at the subtotal — by priceOrder, the same function saveOrder
   * records with, so the till charges exactly what the screen shows.
   */
  const discount = ref<OrderDiscountInput | null>(null)

  const priced = computed(() =>
    priceOrder(
      cartLines.value.map((line) => ({ lineTotalCents: line.subtotalCents, taxRate: line.product.taxRate })),
      discount.value,
    ),
  )
  const subtotalCents = computed(() => priced.value.subtotalCents)
  const discountCents = computed(() => priced.value.discountCents)
  const appliedDiscount = computed(() => priced.value.discount)
  const taxCents = computed(() => priced.value.taxCents)
  const totalCents = computed(() => priced.value.totalCents)
  const changeCents = computed(() => Math.max(tenderedCents.value - totalCents.value, 0))
  const itemCount = computed(() => cartLines.value.reduce((sum, line) => sum + line.quantity, 0))
  const canCheckout = computed(
    () => Boolean(
      activeShift.value
      && cartLines.value.length > 0
      && (paymentMethod.value !== 'cash' || tenderedCents.value >= totalCents.value),
    ),
  )
  const pendingAppEvents = computed(() => appEvents.value.filter((event) => !event.sentAt))
  const selectedCustomer = computed(() =>
    customers.value.find((customer) => customer.id === selectedCustomerId.value) ?? null,
  )
  const selectedCustomerName = computed(() => selectedCustomer.value?.name ?? guestCustomerName)
  const customerOptions = computed(() => [
    { value: '', label: guestCustomerName },
    ...customers.value.map((customer) => ({ value: customer.id, label: customer.name })),
  ])

  const lowStockProducts = computed(() =>
    products.value.filter((p) => {
      if (p.stockQty === undefined) return false
      const threshold = p.lowStockThreshold ?? 5
      return p.stockQty <= threshold
    }),
  )

  const outOfStockProducts = computed(() =>
    products.value.filter((p) => p.stockQty !== undefined && p.stockQty === 0),
  )

  function resetTransientState() {
    search.value = ''
    selectedCategoryId.value = 'all'
    paymentMethod.value = 'cash'
    orderType.value = 'takeaway'
    tableNumber.value = ''
    selectedCustomerId.value = null
    tenderedCents.value = 0
    tenderedInput.value = ''
    cart.value = {}
    discount.value = null
    lastCompletedOrder.value = null
    lowStockAlert.value = []
    shiftError.value = ''
  }

  async function initialize(force = false) {
    if (isReady.value && !force) {
      return
    }

    if (force) {
      resetTransientState()
    }

    const [
      catalog, savedCustomers, savedTables, savedOrders, savedSettings, savedEvents, savedShift, savedOnlineOrders,
      savedSuppliers, savedReorderMarks,
    ] = await Promise.all([
      repository.loadCatalog(),
      repository.loadCustomers(),
      repository.loadTables(),
      repository.loadOrders(),
      repository.loadSettings(),
      repository.loadAppEvents(),
      repository.loadActiveShift(),
      repository.loadOnlineOrders(),
      repository.loadSuppliers(),
      repository.loadReorderMarks(),
    ])

    products.value = catalog.products
    categories.value = catalog.categories
    customers.value = savedCustomers
    tables.value = savedTables
    orders.value = savedOrders
    settings.value = savedSettings
    appEvents.value = savedEvents
    activeShift.value = savedShift
    onlineOrders.value = savedOnlineOrders
    suppliers.value = savedSuppliers
    reorderMarks.value = savedReorderMarks
    isReady.value = true
  }

  async function refreshOnlineOrders() {
    onlineOrders.value = await repository.loadOnlineOrders()
  }

  async function refreshOrders() {
    orders.value = await repository.loadOrders()
  }

  function clearShiftError() {
    shiftError.value = ''
  }

  async function trackEvent(
    eventType: AppEvent['eventType'],
    payload: Record<string, unknown>,
    force = false,
  ) {
    if (!force && !settings.value.telemetryEnabled) {
      return
    }

    const event = await repository.trackAppEvent({ eventType, payload })
    appEvents.value = [event, ...appEvents.value]
  }

  function addProduct(productId: string) {
    cart.value[productId] = (cart.value[productId] ?? 0) + 1
    const product = products.value.find((entry) => entry.id === productId)
    void trackEvent('product_added', {
      productId,
      businessMode: settings.value.businessMode,
      categoryId: product?.categoryId ?? null,
      cartSize: Object.values(cart.value).reduce((sum, quantity) => sum + quantity, 0),
    })
  }

  function increment(productId: string) {
    addProduct(productId)
  }

  function decrement(productId: string) {
    const next = (cart.value[productId] ?? 0) - 1
    if (next <= 0) {
      delete cart.value[productId]
      return
    }

    cart.value[productId] = next
  }

  function removeLine(productId: string) {
    delete cart.value[productId]
  }

  /**
   * Take something off this sale. Returns why not, or null when applied.
   *
   * `maxPercent` is the signed-in person's role limit (maxDiscountPercentFor).
   * Over it, the discount is refused here rather than sent for the server to
   * flag after the money has changed hands: a manager signs in to give it.
   */
  function applyDiscount(input: OrderDiscountInput, maxPercent: number): string | null {
    if (cartLines.value.length === 0) return 'Add something to the order first.'

    const percent = input.percent
    const amount = input.amountCents
    if (percent == null && amount == null) return 'Enter an amount or a percentage.'
    if (percent != null && (!Number.isFinite(percent) || percent <= 0 || percent > 100)) {
      return 'A percentage between 1 and 100.'
    }
    if (amount != null && (!Number.isFinite(amount) || amount <= 0)) return 'Enter an amount above zero.'

    const trial = priceOrder(
      cartLines.value.map((line) => ({ lineTotalCents: line.subtotalCents, taxRate: line.product.taxRate })),
      input,
    )
    if (amount != null && amount > trial.subtotalCents) {
      return `That is more than the order — at most ${formatCurrency(trial.subtotalCents)}.`
    }
    if (discountPercentOf(trial.discountCents, trial.subtotalCents) > maxPercent + 1e-9) {
      return maxPercent <= 0
        ? 'Your role cannot give discounts. Ask a manager to apply this one.'
        : `Your role can take up to ${maxPercent}% off. Ask a manager for more.`
    }

    discount.value = { ...input, reason: input.reason?.trim() || null }
    return null
  }

  /**
   * A promo code typed at the counter, checked by the server. Its amount is
   * the server's and it is not the cashier's discretion, so no role limit
   * applies. Returns why not, or null when applied.
   */
  async function applyPromoCode(code: string): Promise<string | null> {
    const trimmed = code.trim()
    if (!trimmed) return 'Enter a code.'
    if (cartLines.value.length === 0) return 'Add something to the order first.'

    try {
      const check = await repository.checkPromoCode(trimmed, subtotalCents.value)
      discount.value = {
        kind: 'promo',
        amountCents: check.discountCents,
        percent: check.percent ?? undefined,
        reason: check.code,
        promoCodeId: check.promoCodeId,
      }
      return null
    } catch (error) {
      return error instanceof Error && error.message ? error.message : "That code doesn't apply."
    }
  }

  /**
   * Spend the selected customer's points on this sale. Checked by the server
   * — their balance lives there, and a balance checked offline could be spent
   * twice at two tills — and, like a promo, not the cashier's discretion.
   * Returns why not, or null when applied.
   */
  async function applyLoyaltyPoints(points: number): Promise<string | null> {
    const customer = selectedCustomer.value
    if (!customer) return 'Choose the customer first.'
    if (!Number.isInteger(points) || points <= 0) return 'Enter a number of points.'
    if (cartLines.value.length === 0) return 'Add something to the order first.'

    try {
      const check = await repository.checkLoyaltyRedemption(customer.id, points, subtotalCents.value)
      discount.value = {
        kind: 'loyalty',
        amountCents: check.discountCents,
        points: check.points,
        reason: `${check.points} points`,
      }
      return null
    } catch (error) {
      return error instanceof Error && error.message ? error.message : "Those points can't be used."
    }
  }

  // A promo's amount, or what points are worth, was checked for the basket as
  // it was. Changed, it could be wrong — a percentage of the old subtotal, or
  // more than the new one — so it comes off and the cashier applies it again.
  watch(
    () => cartLines.value.map((line) => `${line.product.id}:${line.quantity}`).join(','),
    () => {
      if (discount.value?.kind === 'promo' || discount.value?.kind === 'loyalty') discount.value = null
    },
  )

  // Points belong to a customer; a different customer cannot spend them.
  watch(selectedCustomerId, () => {
    if (discount.value?.kind === 'loyalty') discount.value = null
  })

  function removeDiscount() {
    discount.value = null
  }

  function clearCart() {
    cart.value = {}
    discount.value = null
    paymentMethod.value = 'cash'
    selectedCustomerId.value = null
    tenderedInput.value = ''
    tenderedCents.value = 0
    void trackEvent('cart_cleared', {
      businessMode: settings.value.businessMode,
    })
  }

  function appendTenderDigit(digit: number) {
    if (paymentMethod.value !== 'cash') {
      return
    }

    if (digit < 0 || digit > 9) {
      return
    }

    if (tenderedInput.value.includes('.')) {
      const [, fractionPart = ''] = tenderedInput.value.split('.')
      if (fractionPart.length >= 2) {
        return
      }

      tenderedInput.value = `${tenderedInput.value}${digit}`
      syncTenderedFromInput()
      return
    }

    tenderedInput.value = tenderedInput.value === '' || tenderedInput.value === '0'
      ? String(digit)
      : `${tenderedInput.value}${digit}`
    syncTenderedFromInput()
  }

  function appendTenderDecimal() {
    if (paymentMethod.value !== 'cash' || tenderedInput.value.includes('.')) {
      return
    }

    tenderedInput.value = tenderedInput.value === '' ? '0.' : `${tenderedInput.value}.`
    syncTenderedFromInput()
  }

  function setTendered(cents: number) {
    if (paymentMethod.value !== 'cash') return
    tenderedInput.value = formatTenderedInput(cents)
    tenderedCents.value = cents
  }

  function clearTendered() {
    tenderedInput.value = ''
    tenderedCents.value = 0
  }

  function backspaceTendered() {
    if (paymentMethod.value !== 'cash' || !tenderedInput.value) {
      clearTendered()
      return
    }

    tenderedInput.value = tenderedInput.value.slice(0, -1)
    syncTenderedFromInput()
  }

  function setSearch(value: string) {
    search.value = value
    if (value.trim().length >= 2) {
      void trackEvent('cart_search_used', {
        queryLength: value.trim().length,
        businessMode: settings.value.businessMode,
      })
    }
  }

  function setCategory(value: string) {
    selectedCategoryId.value = value
  }

  function setPaymentMethod(value: PaymentMethod) {
    paymentMethod.value = value
    tenderedInput.value = value === 'cash' ? '' : formatTenderedInput(totalCents.value)
    tenderedCents.value = value === 'cash' ? 0 : totalCents.value
    void trackEvent('payment_method_selected', {
      paymentMethod: value,
      totalCents: totalCents.value,
      businessMode: settings.value.businessMode,
    })
  }

  function setOrderType(value: OrderType) {
    orderType.value = value
  }

  function setTableNumber(value: string) {
    tableNumber.value = value
  }

  function setSelectedCustomer(value: string | null) {
    selectedCustomerId.value = value
  }

  async function notePaymentSheetOpened() {
    checkoutError.value = ''
    await trackEvent('payment_sheet_opened', {
      totalCents: totalCents.value,
      itemCount: itemCount.value,
      businessMode: settings.value.businessMode,
      customerName: selectedCustomerName.value,
    })
  }

  async function updateSettings(next: AppSettings) {
    settings.value = next
    selectedCategoryId.value = 'all'
    await repository.saveSettings(next)
    appEvents.value = await repository.loadAppEvents()
    const catalog = await repository.loadCatalog()
    products.value = catalog.products
    categories.value = catalog.categories
    await trackEvent(
      'settings_saved',
      {
        businessMode: next.businessMode,
        telemetryEnabled: next.telemetryEnabled,
      },
      true,
    )
  }

  async function createProduct(input: CreateProductInput) {
    const product = await repository.saveProduct(input)
    products.value = [product, ...products.value]
    return product
  }

  async function createCustomer(input: CreateCustomerInput) {
    const customer = await repository.saveCustomer(input)
    customers.value = [customer, ...customers.value]
    selectedCustomerId.value = customer.id
    return customer
  }

  async function editCustomer(customer: Customer) {
    const nextCustomer = await repository.updateCustomer(customer)
    const index = customers.value.findIndex((entry) => entry.id === nextCustomer.id)
    if (index !== -1) {
      customers.value[index] = nextCustomer
    }
    orders.value = orders.value.map((order) =>
      order.customerId === nextCustomer.id
        ? { ...order, customerName: nextCustomer.name }
        : order,
    )
    return nextCustomer
  }

  async function removeCustomer(id: string) {
    await repository.deleteCustomer(id)
    customers.value = customers.value.filter((customer) => customer.id !== id)
    if (selectedCustomerId.value === id) {
      selectedCustomerId.value = null
    }
  }

  async function createTable(input: CreateTableInput) {
    const table = await repository.saveTable(input)
    tables.value = [...tables.value, table]
    return table
  }

  async function editTable(table: RestaurantTable) {
    const nextTable = await repository.updateTable(table)
    const index = tables.value.findIndex((entry) => entry.id === nextTable.id)
    if (index !== -1) {
      tables.value[index] = nextTable
    }
    return nextTable
  }

  async function removeTable(id: string) {
    await repository.deleteTable(id)
    tables.value = tables.value.filter((table) => table.id !== id)
  }

  async function createSupplier(input: CreateSupplierInput) {
    const supplier = await repository.saveSupplier(input)
    suppliers.value = [...suppliers.value, supplier]
    return supplier
  }

  async function editSupplier(supplier: Supplier) {
    const nextSupplier = await repository.updateSupplier(supplier)
    const index = suppliers.value.findIndex((entry) => entry.id === nextSupplier.id)
    if (index !== -1) {
      suppliers.value[index] = nextSupplier
    }
    return nextSupplier
  }

  async function removeSupplier(id: string) {
    await repository.deleteSupplier(id)
    suppliers.value = suppliers.value.filter((supplier) => supplier.id !== id)
  }

  async function markReorder(input: { productId: string; supplierId: string | null; quantity: number; userId?: string | null }) {
    const mark = await repository.markReorder(input)
    reorderMarks.value = [mark, ...reorderMarks.value.filter((entry) => entry.productId !== input.productId)]
    return mark
  }

  async function cancelReorderMark(markId: string) {
    await repository.clearReorderMark(markId)
    reorderMarks.value = reorderMarks.value.filter((entry) => entry.id !== markId)
  }

  // Restocks the product for real and clears its "on order" mark in one step
  // — the mark only exists to stop a shortage being reordered twice while
  // it's in transit, so it's stale the moment stock actually arrives.
  async function receiveReorder(markId: string, quantity?: number) {
    const mark = reorderMarks.value.find((entry) => entry.id === markId)
    if (!mark) return

    await restockProduct(mark.productId, quantity ?? mark.quantity)
    await repository.clearReorderMark(markId)
    reorderMarks.value = reorderMarks.value.filter((entry) => entry.id !== markId)
  }

  async function editProduct(product: Product) {
    await repository.updateProduct(product)
    const index = products.value.findIndex((p) => p.id === product.id)
    if (index !== -1) {
      products.value[index] = product
    }
  }

  async function removeProduct(id: string) {
    await repository.deleteProduct(id)
    products.value = products.value.filter((p) => p.id !== id)
    delete cart.value[id]
  }

  async function createCategory(name: string) {
    const category = await repository.saveCategory({ name } as CreateCategoryInput)
    categories.value = [...categories.value, category]
    return category
  }

  async function editCategory(category: Category) {
    await repository.updateCategory(category)
    const index = categories.value.findIndex((c) => c.id === category.id)
    if (index !== -1) {
      categories.value[index] = category
    }
  }

  async function removeCategory(id: string) {
    await repository.deleteCategory(id)
    if (selectedCategoryId.value === id) {
      selectedCategoryId.value = 'all'
    }
    categories.value = categories.value.filter((c) => c.id !== id)
    const fallbackId = categories.value[0]?.id ?? 'groceries'
    products.value = products.value.map((p) =>
      p.categoryId === id ? { ...p, categoryId: fallbackId } : p,
    )
  }

  /**
   * Charge the cart. The sale is recorded on the server first (the till is
   * online-only), so this can be refused — a discount beyond the cashier's
   * role, a promo code used up, no connection. Returns whether the sale went
   * through; when it did not, `checkoutError` says why and the cart is left
   * as it was.
   */
  async function completeOrder(): Promise<boolean> {
    if (!canCheckout.value) {
      if (!activeShift.value) {
        shiftError.value = 'Open a shift before charging orders on this register.'
        checkoutError.value = shiftError.value
      }
      return false
    }

    const input = {
      businessMode: settings.value.businessMode,
      customerId: selectedCustomer.value?.id ?? null,
      customerName: selectedCustomerName.value,
      orderType: orderType.value,
      tableNumber: settings.value.businessMode === 'restaurant' ? tableNumber.value.trim() || null : null,
      paymentMethod: paymentMethod.value,
      tenderedCents: paymentMethod.value === 'cash' ? tenderedCents.value : totalCents.value,
      items: cartLines.value.map((line) => ({
        productId: line.product.id,
        name: line.product.name,
        quantity: line.quantity,
        unitPriceCents: line.product.priceCents,
        lineTotalCents: line.subtotalCents,
        taxRate: line.product.taxRate,
      })),
      discount: discount.value,
    }

    // The same sale as last time, if nothing but the cash handed over has
    // changed. A request that reached the server but whose answer was lost
    // is then answered with the sale the server already has.
    const fingerprint = JSON.stringify({ ...input, tenderedCents: undefined })
    if (pendingSale?.fingerprint !== fingerprint) {
      pendingSale = { id: crypto.randomUUID(), fingerprint }
    }

    checkoutError.value = ''
    let order: OrderSummary
    try {
      order = await repository.saveOrder({ ...input, id: pendingSale.id })
    } catch (error) {
      checkoutError.value = error instanceof Error && error.message
        ? error.message
        : 'This sale could not be recorded. Try again.'
      return false
    }
    pendingSale = null

    orders.value = [order, ...orders.value]
    lastCompletedOrder.value = order

    // saveOrder() updates the shift's cash/total-sales tallies in the repository layer,
    // but doesn't return the shift itself — re-pull it so the reactive activeShift here
    // (and anything bound to it, like the shift panel) reflects the new totals immediately.
    if (activeShift.value) {
      await refreshActiveShift()
    }

    await trackEvent('order_completed', {
      orderId: order.id,
      paymentMethod: order.paymentMethod,
      totalCents: order.totalCents,
      itemCount: order.items.reduce((sum, item) => sum + item.quantity, 0),
      businessMode: order.businessMode,
    })

    const newlyLowStock: Product[] = []
    for (const line of cartLines.value) {
      const p = line.product
      if (p.stockQty === undefined) continue

      const threshold = p.lowStockThreshold ?? 5
      const wasAboveThreshold = p.stockQty > threshold
      // The server took this off its own count with the sale.
      const updated = await repository.adjustInventory({
        productId: p.id,
        quantityDelta: -line.quantity,
        adjustmentType: 'sale',
        orderId: order.id,
        reason: `order:${order.ticketNumber}`,
        localOnly: true,
      })
      if (!updated) continue

      const idx = products.value.findIndex((x) => x.id === p.id)
      if (idx !== -1) products.value[idx] = updated

      if (wasAboveThreshold && (updated.stockQty ?? 0) <= threshold) {
        newlyLowStock.push(updated)
      }
    }

    if (newlyLowStock.length > 0) {
      lowStockAlert.value = newlyLowStock
      await trackEvent('low_stock_alert', {
        products: newlyLowStock.map((p) => ({ id: p.id, name: p.name, stockQty: p.stockQty })),
      })
    }

    cart.value = {}
    discount.value = null
    paymentMethod.value = 'cash'
    selectedCustomerId.value = null
    tenderedInput.value = ''
    tenderedCents.value = 0
    tableNumber.value = ''
    shiftError.value = ''
    return true
  }

  async function updateOrderStatus(orderId: string, status: OrderStatus) {
    const updated = await repository.updateOrderStatus(orderId, status)
    const index = orders.value.findIndex((order) => order.id === orderId)
    if (index !== -1) {
      orders.value[index] = updated
    }
    return updated
  }

  // Full-order void only — reverses the whole sale (inventory restored,
  // excluded from revenue). No partial/line-item refund. Callers are
  // responsible for permission-gating this (owner/admin only in the UI).
  async function voidOrder(orderId: string, options: { userId?: string | null; reason?: string | null } = {}) {
    const order = orders.value.find((entry) => entry.id === orderId)
    if (!order || order.voidedAt) {
      return order ?? null
    }

    const voided = await repository.voidOrder(orderId, options)
    const index = orders.value.findIndex((entry) => entry.id === orderId)
    if (index !== -1) {
      orders.value[index] = voided
    }

    if (activeShift.value) {
      await refreshActiveShift()
    }

    for (const item of order.items) {
      const product = products.value.find((p) => p.id === item.productId)
      if (!product || product.stockQty === undefined) continue

      const updated = await repository.adjustInventory({
        productId: item.productId,
        quantityDelta: item.quantity,
        adjustmentType: 'manual_correction',
        orderId: order.id,
        reason: `void:${order.ticketNumber}`,
        // The server put its own count back with the void.
        localOnly: true,
      })
      if (!updated) continue

      const productIndex = products.value.findIndex((p) => p.id === item.productId)
      if (productIndex !== -1) {
        products.value[productIndex] = updated
      }
    }

    await trackEvent('order_voided', {
      orderId: voided.id,
      totalCents: voided.totalCents,
      reason: voided.voidReason ?? null,
    })

    return voided
  }

  // Settles payment on an online order a customer already placed through the
  // storefront — distinct from completeOrder(), which creates a brand-new
  // (already-paid) order from the live cart.
  async function settleOnlineOrderPayment(
    orderId: string,
    payment: { paymentMethod: PaymentMethod; tenderedCents: number; changeCents: number; userId?: string | null },
  ) {
    return replaceOnlineOrder(await repository.settleOrderPayment(orderId, payment))
  }

  function replaceOnlineOrder(updated: OrderSummary) {
    const index = onlineOrders.value.findIndex((order) => order.id === updated.id)
    if (index !== -1) {
      onlineOrders.value[index] = updated
    }
    return updated
  }

  /**
   * Storefront orders live server-side, so these three go over the network and
   * have no offline path — unlike updateOrderStatus above, which walks a
   * register sale through the local outbox. The dashboard surfaces the failure
   * rather than pretending the rider was told.
   */
  async function updateOnlineOrderStatus(orderId: string, status: OrderStatus) {
    return replaceOnlineOrder(await repository.updateOnlineOrderStatus(orderId, status))
  }

  /**
   * Records who is carrying the order; the API moves it to 'assigned'.
   *
   * Takes the whole assignment shape rather than a name and a number, because
   * a shop has three ways to answer the question and only one of them is
   * typing: `savedRiderId` picks from the shop's list, `saveRider` remembers a
   * typed-in one. See SellerOrderController::assignRider.
   */
  async function notifyRider(
    orderId: string,
    rider: {
      savedRiderId?: string | null
      riderName?: string
      riderPhone?: string | null
      saveRider?: boolean
      saveNote?: string | null
    },
  ) {
    return replaceOnlineOrder(await repository.assignOrderRider(orderId, rider))
  }

  /** Takes the rider off and puts the order back on the platform board. */
  async function returnToBoard(orderId: string) {
    return replaceOnlineOrder(await repository.unassignOrderRider(orderId))
  }

  /**
   * The shop's own riders, and the ones who have delivered for it.
   *
   * Read fresh every time rather than cached in the store: a saved rider's
   * `online` flag is only true for the ten seconds it describes, and offering a
   * stale one as available sends an order to a phone that is switched off.
   */
  async function loadSavedRiders() {
    return repository.loadSavedRiders()
  }

  async function saveRider(input: {
    riderId?: string | null
    name: string
    phone?: string | null
    note?: string | null
  }) {
    return repository.saveRider(input)
  }

  async function deleteSavedRider(id: string) {
    return repository.deleteSavedRider(id)
  }

  /**
   * A position ping, applied to the order it belongs to.
   *
   * Kept out of `replaceOnlineOrder` deliberately: that one swaps the whole
   * order, and a ping every ten seconds per delivery would rebuild the list —
   * and every card's identity with it — six times a minute. This touches one
   * field on one order and leaves the rest of the object alone.
   */
  function applyRiderPosition(orderId: string, position: RiderPosition | null) {
    const index = onlineOrders.value.findIndex((order) => order.id === orderId)
    if (index === -1) return
    const order = onlineOrders.value[index]!
    onlineOrders.value[index] = { ...order, riderPosition: position }
  }

  async function advanceDelivery(orderId: string, stage: DeliveryStage) {
    return replaceOnlineOrder(await repository.updateOrderDeliveryStage(orderId, stage))
  }

  function clearLowStockAlert() {
    lowStockAlert.value = []
  }

  async function restockProduct(productId: string, addQty: number) {
    const updated = await repository.adjustInventory({
      productId,
      quantityDelta: addQty,
      adjustmentType: 'restock',
      reason: 'restock',
    })
    if (!updated) return

    const idx = products.value.findIndex((p) => p.id === productId)
    if (idx !== -1) {
      products.value[idx] = updated
    }
  }

  async function refreshActiveShift() {
    activeShift.value = await repository.loadActiveShift()
    return activeShift.value
  }

  async function openShift(openingCashCents: number, userId?: string | null) {
    shiftError.value = ''
    activeShift.value = await repository.openShift({ openingCashCents, userId })
    return activeShift.value
  }

  async function addCashMovement(
    movementType: CashMovementType,
    amountCents: number,
    reason?: string,
    userId?: string | null,
  ) {
    shiftError.value = ''
    activeShift.value = await repository.addCashMovement({ movementType, amountCents, reason, userId })
    return activeShift.value
  }

  async function closeShift(countedCashCents: number, userId?: string | null) {
    shiftError.value = ''
    const closedShift = await repository.closeShift({ countedCashCents, userId })
    activeShift.value = null
    shiftHistory.value = [closedShift, ...shiftHistory.value]
    return closedShift
  }

  async function refreshShiftHistory() {
    shiftHistory.value = await repository.loadShiftHistory()
    return shiftHistory.value
  }

  return {
    isReady,
    products,
    categories,
    customers,
    tables,
    suppliers,
    reorderMarks,
    orders,
    onlineOrders,
    appEvents,
    pendingAppEvents,
    settings,
    activeShift,
    shiftHistory,
    search,
    selectedCategoryId,
    paymentMethod,
    orderType,
    tableNumber,
    selectedCustomerId,
    selectedCustomer,
    selectedCustomerName,
    customerOptions,
    tenderedCents,
    tenderedInput,
    lastCompletedOrder,
    lowStockAlert,
    shiftError,
    checkoutError,
    filteredProducts,
    cartLines,
    subtotalCents,
    discount,
    discountCents,
    appliedDiscount,
    taxCents,
    totalCents,
    changeCents,
    itemCount,
    canCheckout,
    lowStockProducts,
    outOfStockProducts,
    initialize,
    clearShiftError,
    trackEvent,
    addProduct,
    increment,
    decrement,
    removeLine,
    clearCart,
    applyDiscount,
    applyPromoCode,
    applyLoyaltyPoints,
    removeDiscount,
    appendTenderDigit,
    appendTenderDecimal,
    setTendered,
    clearTendered,
    backspaceTendered,
    setSearch,
    setCategory,
    setPaymentMethod,
    setOrderType,
    setTableNumber,
    setSelectedCustomer,
    notePaymentSheetOpened,
    updateSettings,
    completeOrder,
    refreshOrders,
    updateOrderStatus,
    voidOrder,
    refreshOnlineOrders,
    settleOnlineOrderPayment,
    updateOnlineOrderStatus,
    notifyRider,
    returnToBoard,
    applyRiderPosition,
    loadSavedRiders,
    saveRider,
    deleteSavedRider,
    advanceDelivery,
    clearLowStockAlert,
    restockProduct,
    refreshActiveShift,
    refreshShiftHistory,
    openShift,
    addCashMovement,
    closeShift,
    createProduct,
    editProduct,
    removeProduct,
    createCustomer,
    editCustomer,
    removeCustomer,
    createTable,
    editTable,
    removeTable,
    createSupplier,
    editSupplier,
    removeSupplier,
    markReorder,
    cancelReorderMark,
    receiveReorder,
    createCategory,
    editCategory,
    removeCategory,
  }
})
