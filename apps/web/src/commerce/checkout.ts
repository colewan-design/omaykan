import { computed, ref, watch, type ComputedRef } from 'vue'
import { priceOrder } from '@pos/shared/index'
import { useStorefrontCart, type CartLine } from '@pos/web/commerce/cart'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { useStorefrontOrderHistory } from '@pos/web/commerce/orderHistory'
import {
  ApiRequestError,
  createOnlineOrder,
  quoteOnlineOrder,
  type CreateOnlineOrderResult,
  type CustomerAddress,
} from '@pos/web/commerce/api'
import { BUSINESS_MODE, STORE_LAT, STORE_LNG } from '@pos/web/commerce/context'
import { DELIVERY_BASE_FEE_CENTS, haversineKm, quoteDelivery } from '@pos/web/commerce/delivery'

/*
 * Everything checkout knows, for the cart page to lay out.
 *
 * This was the body of CartDrawer, the header's slide-over, until the cart
 * became a page of its own. It takes the lines being ordered rather than
 * reading the whole basket, because the cart page lets a shopper tick which
 * lines go into this order — the rest stay in the basket for next time.
 */

// Matches OnlineOrderController::ONLINE_MODES. A salon has nothing to put in a
// cart, and the API would reject the order on businessMode anyway — better to
// say so before the shopper fills the form in.
const ONLINE_MODES = ['coffee-shop', 'grocery', 'restaurant']

export interface PlacedOrder extends CreateOnlineOrderResult {
  lines: CartLine[]
  method: 'pickup' | 'delivery'
  /** How we said we would reach them: the number if they gave one, else the email. */
  reachBy: 'number' | 'email'
}

export function addressLineFor(address: CustomerAddress): string {
  return [address.line1, address.barangay, address.city]
    .map((part) => part.trim())
    .filter(Boolean)
    .join(', ')
}

export function useCheckout(lines: ComputedRef<CartLine[]>) {
  const cart = useStorefrontCart()
  const account = useCustomerAccount()
  const orderHistory = useStorefrontOrderHistory()

  const canOrderOnline = computed(() => ONLINE_MODES.includes(BUSINESS_MODE))

  const name = ref('')
  const phone = ref('')
  const email = ref('')
  const fulfillmentMethod = ref<'pickup' | 'delivery'>('delivery')
  const deliveryAddress = ref('')
  /**
   * How the rider finds the door, kept apart from the address that names the
   * street. A Baguio address is often a district — "Loakan, Baguio City" — and
   * what closes the last fifty metres is the thing beside it.
   */
  const deliveryLandmark = ref('')
  const paymentMethod = ref<'cash' | 'ewallet'>('cash')

  /** Id of the saved address in use, or '' when typing a one-off one. */
  const addressChoice = ref('')

  // Drop-off pin, set from a saved address that has one or from "Use my
  // location". Delivery still works without it — the store then charges the
  // flat base fee, exactly as DeliveryQuoter does when it gets no coordinates.
  const dropLat = ref<number | null>(null)
  const dropLng = ref<number | null>(null)
  const locating = ref(false)
  const locationError = ref('')

  const submitting = ref(false)
  const error = ref('')
  const placed = ref<PlacedOrder | null>(null)

  // Whether POST /online-orders wants an account, which the storefront cannot
  // know up front: this repo's OnlineOrderController is deliberately
  // unauthenticated, while the deployed backend puts `auth:customer` on the
  // route and turns a guest order into a bare 401. Rather than guess which one
  // is answering, the order is attempted and a 401 is turned into the one thing
  // the shopper can act on. The cart is untouched, so signing in and coming
  // back finishes the order.
  const needsSignIn = ref(false)

  /*
   * Checkout is for account holders.
   *
   * A product decision, not a technical one: POST /api/online-orders still
   * takes a guest order and must keep doing so — the Android app offers
   * checkout with no account at all. This gate is the web storefront's own
   * policy, applied at the one door the web storefront owns.
   *
   * `hydrating` is held apart from "signed out". A stored token is traded for
   * the account after first paint, so treating that gap as signed out would
   * show a returning shopper a sign-in wall for a moment and then snatch it away.
   */
  const checkoutGated = computed(() => !account.hydrating.value && !account.signedIn.value)

  /**
   * Where to send someone to sign in, and how to get them back here — to the
   * same step, since the URL carries it. The basket itself survives regardless;
   * it is in localStorage.
   */
  const signInHref = computed(() => {
    try {
      const here = window.location.pathname + window.location.search
      return `/account?next=${encodeURIComponent(here)}`
    } catch {
      return '/account?next=%2Fcart'
    }
  })

  const savedAddresses = computed<CustomerAddress[]>(() => account.account.value?.addresses ?? [])

  function selectSavedAddress(id: string) {
    const address = savedAddresses.value.find((candidate) => candidate.id === id)
    if (!address) return

    addressChoice.value = id
    deliveryAddress.value = addressLineFor(address)
    // The saved address's "notes for the rider" is a landmark under another
    // name — its own form asks for "green gate beside the sari-sari store". It
    // used to be glued onto the end of the address line with an em dash, which
    // made one field out of two different things and left the rider reading
    // directions and an address mixed together. It fills the landmark now.
    deliveryLandmark.value = address.notes.trim()
    dropLat.value = address.lat
    dropLng.value = address.lng
    locationError.value = ''
  }

  function useNewAddress() {
    addressChoice.value = ''
    deliveryAddress.value = ''
    // Cleared with the address it belonged to. A landmark left behind from a
    // saved address would point the rider at the wrong street entirely.
    deliveryLandmark.value = ''
    dropLat.value = null
    dropLng.value = null
    locationError.value = ''
  }

  /**
   * Fill what the account already knows. Only ever fills blanks: this runs
   * again when the account hydrates (the request outlives the first paint) and
   * must not overwrite something typed in the meantime.
   */
  function applyAccountDefaults() {
    const acct = account.account.value
    if (!acct) return

    if (!name.value.trim()) name.value = acct.name
    if (!phone.value.trim()) phone.value = acct.phone
    if (!email.value.trim()) email.value = acct.email

    if (!addressChoice.value && !deliveryAddress.value.trim()) {
      const preferred = acct.addresses.find((address) => address.isDefault) ?? acct.addresses[0]
      if (preferred) selectSavedAddress(preferred.id)
    }

    const method = acct.paymentMethods.find((candidate) => candidate.isDefault) ?? acct.paymentMethods[0]
    if (method) paymentMethod.value = method.kind
  }

  watch(() => account.account.value, applyAccountDefaults, { immediate: true })

  const storeHasPin = computed(() => STORE_LAT !== null && STORE_LNG !== null)
  const isDelivery = computed(() => fulfillmentMethod.value === 'delivery')
  const hasDropPin = computed(() => dropLat.value !== null && dropLng.value !== null)

  /**
   * An estimate, and labelled as one in the UI. OnlineOrderController
   * re-quotes from the store's own pin and charges that.
   */
  const deliveryQuote = computed(() => {
    if (!isDelivery.value) return null
    if (!storeHasPin.value || !hasDropPin.value) return null

    return quoteDelivery(haversineKm(STORE_LAT!, STORE_LNG!, dropLat.value!, dropLng.value!))
  })

  const deliveryFeeCents = computed(() => {
    if (!isDelivery.value) return 0
    return deliveryQuote.value?.feeCents ?? DELIVERY_BASE_FEE_CENTS
  })

  const outOfRange = computed(() => deliveryQuote.value?.serviceable === false)

  // ── Promo code ──────────────────────────────────────────────────────
  //
  // The server decides whether a code applies and for how much (the quote
  // endpoint, the same rules checkout itself runs); the page only asks. What
  // the code takes off is then priced here with priceOrder — the arithmetic
  // the server charges with — so the VAT shown is the VAT charged.
  const promoInput = ref('')
  const appliedPromo = ref<{ code: string; description: string; discountCents: number } | null>(null)
  const promoMessage = ref('')
  const promoChecking = ref(false)

  async function checkPromo(code: string): Promise<void> {
    promoChecking.value = true
    promoMessage.value = ''
    try {
      const quote = await quoteOnlineOrder(
        lines.value.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
        { promoCode: code, phone: phone.value.trim() || null },
      )
      if (quote.promo?.ok) {
        appliedPromo.value = { code: quote.promo.code, description: quote.promo.description, discountCents: quote.discountCents }
      } else {
        appliedPromo.value = null
        promoMessage.value = quote.promo && !quote.promo.ok ? quote.promo.message : "That code doesn't apply."
      }
    } catch (err) {
      appliedPromo.value = null
      promoMessage.value = err instanceof Error && err.message ? err.message : "Couldn't check that code. Try again."
    } finally {
      promoChecking.value = false
    }
  }

  function applyPromo() {
    const code = promoInput.value.trim()
    if (code === '' || promoChecking.value) return
    void checkPromo(code)
  }

  function removePromo() {
    appliedPromo.value = null
    promoMessage.value = ''
    promoInput.value = ''
  }

  // What a percentage takes off depends on the basket; a changed basket asks
  // again rather than keeping a figure for lines that are no longer there.
  watch(
    () => lines.value.map((line) => `${line.product.id}:${line.quantity}`).join(','),
    () => {
      if (appliedPromo.value) void checkPromo(appliedPromo.value.code)
    },
  )

  const totals = computed(() => {
    const priced = priceOrder(
      lines.value.map((line) => ({
        lineTotalCents: Math.round(line.product.priceCents * line.quantity),
        taxRate: line.product.taxRate,
      })),
      appliedPromo.value ? { kind: 'manual', amountCents: appliedPromo.value.discountCents } : null,
    )
    return {
      itemCount: lines.value.reduce((sum, line) => sum + line.quantity, 0),
      subtotalCents: priced.subtotalCents,
      discountCents: priced.discountCents,
      taxCents: priced.taxCents,
      totalCents: priced.totalCents,
    }
  })
  const grandTotalCents = computed(() => totals.value.totalCents + deliveryFeeCents.value)

  function useMyLocation() {
    if (locating.value) return

    if (!navigator.geolocation) {
      locationError.value = 'This device cannot share a location.'
      return
    }

    locating.value = true
    locationError.value = ''
    navigator.geolocation.getCurrentPosition(
      (position) => {
        dropLat.value = position.coords.latitude
        dropLng.value = position.coords.longitude
        locating.value = false
      },
      () => {
        locationError.value = "Couldn't get your location. You can still type your address."
        locating.value = false
      },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  const canSubmit = computed(
    () =>
      canOrderOnline.value &&
      // Belt and braces: the cart step already refuses to open checkout for a
      // signed-out shopper, but a session can end in another tab meanwhile.
      !checkoutGated.value &&
      lines.value.length > 0 &&
      name.value.trim().length > 0 &&
      (phone.value.trim().length > 0 || email.value.trim().length > 0) &&
      (!isDelivery.value || deliveryAddress.value.trim().length > 0) &&
      !outOfRange.value,
  )

  async function placeOrder(): Promise<boolean> {
    if (!canSubmit.value || submitting.value) return false

    submitting.value = true
    error.value = ''
    needsSignIn.value = false

    const ordering = lines.value.map((line) => ({ ...line }))

    try {
      const result = await createOnlineOrder(
        ordering.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
        {
          name: name.value.trim(),
          phone: phone.value.trim() || undefined,
          email: email.value.trim() || undefined,
        },
        {
          method: fulfillmentMethod.value,
          address: isDelivery.value ? deliveryAddress.value.trim() : undefined,
          landmark:
            isDelivery.value && deliveryLandmark.value.trim()
              ? deliveryLandmark.value.trim()
              : undefined,
          ...(isDelivery.value && hasDropPin.value ? { lat: dropLat.value!, lng: dropLng.value! } : {}),
        },
        paymentMethod.value,
        appliedPromo.value?.code ?? null,
      )

      // Remembered before the lines leave the basket, so a storage failure
      // can't leave the shopper with neither their cart nor a way back to it.
      orderHistory.remember({
        orderId: result.orderId,
        ticketNumber: result.ticketNumber,
        totalCents: result.totalCents,
      })
      // Only what was ordered. Lines left unticked stay for next time.
      cart.removeMany(ordering.map((line) => line.product.id))
      removePromo()

      placed.value = {
        ...result,
        lines: ordering,
        method: fulfillmentMethod.value,
        reachBy: phone.value.trim() ? 'number' : 'email',
      }
      return true
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 401) {
        needsSignIn.value = true
        error.value = 'This store takes orders from signed-in shoppers only.'
      } else if (err instanceof ApiRequestError && err.fields.includes('promoCode')) {
        // The code stopped applying between the quote and the order — its
        // last use went to someone else. Say so beside the code, take it off,
        // and let the shopper decide: the order was not placed at full price.
        appliedPromo.value = null
        promoMessage.value = err.message
        error.value = `${err.message} Your order hasn't been placed — check the new total and try again.`
      } else {
        error.value =
          err instanceof Error && err.message
            ? err.message
            : "Couldn't place your order right now. Please check your details and try again."
      }
      return false
    } finally {
      submitting.value = false
    }
  }

  return {
    canOrderOnline,
    name,
    phone,
    email,
    fulfillmentMethod,
    deliveryAddress,
    deliveryLandmark,
    paymentMethod,
    addressChoice,
    locating,
    locationError,
    submitting,
    error,
    placed,
    needsSignIn,
    checkoutGated,
    signInHref,
    savedAddresses,
    selectSavedAddress,
    useNewAddress,
    isDelivery,
    hasDropPin,
    deliveryQuote,
    deliveryFeeCents,
    outOfRange,
    totals,
    grandTotalCents,
    promoInput,
    appliedPromo,
    promoMessage,
    promoChecking,
    applyPromo,
    removePromo,
    useMyLocation,
    canSubmit,
    placeOrder,
  }
}

export type Checkout = ReturnType<typeof useCheckout>
