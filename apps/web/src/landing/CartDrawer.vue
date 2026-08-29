<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { formatCurrency } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { useStorefrontOrderHistory } from '@pos/web/commerce/orderHistory'
import {
  ApiRequestError,
  createOnlineOrder,
  type CreateOnlineOrderResult,
  type CustomerAddress,
} from '@pos/web/commerce/api'
import { BUSINESS_MODE, STORE_ADDRESS, STORE_LAT, STORE_LNG } from '@pos/web/commerce/context'
import {
  DELIVERY_BASE_FEE_CENTS,
  DELIVERY_MAX_KM,
  haversineKm,
  quoteDelivery,
} from '@pos/web/commerce/delivery'

// Where the cart badge finally leads.
//
// The header has counted items since the landing page was rebuilt, but the
// icon was an <a href="#shop"> — it scrolled you to the shelves you were
// already looking at, and there was no surface anywhere that could show what
// you had picked, let alone order it. This is that surface: the cart, the
// checkout and the receipt as three steps of one panel.
//
// One panel rather than a /cart page on purpose. The landing page is a single
// document that does its category and product browsing in place, so sending
// the shopper to a separate entry to review a cart would be the one hard
// navigation in an otherwise in-page flow — and they'd come back to the top of
// the page having lost their aisle.
//
// It lives in the header (see FdHeader.vue) rather than on the landing page,
// so the about and account pages get a working cart from the same icon.

const props = defineProps<{ open: boolean; shopHref: string }>()

const emit = defineEmits<{ close: [] }>()

const cart = useStorefrontCart()
const account = useCustomerAccount()
const orderHistory = useStorefrontOrderHistory()

// Matches OnlineOrderController::ONLINE_MODES. A salon has nothing to put in a
// cart, and the API would reject the order on businessMode anyway — better to
// say so before the shopper fills the form in.
const ONLINE_MODES = ['coffee-shop', 'grocery', 'restaurant']
const canOrderOnline = computed(() => ONLINE_MODES.includes(BUSINESS_MODE))

type Step = 'cart' | 'checkout' | 'placed'
const step = ref<Step>('cart')

const name = ref('')
const phone = ref('')
const email = ref('')
const fulfillmentMethod = ref<'pickup' | 'delivery'>('delivery')
const deliveryAddress = ref('')
const paymentMethod = ref<'cash' | 'ewallet'>('cash')

/** Id of the saved address in use, or '' when typing a one-off one. */
const addressChoice = ref('')

// Drop-off pin, set from a saved address that has one or from "Use my
// location". Delivery still works without it — the store then charges the flat
// base fee, exactly as DeliveryQuoter does when it gets no coordinates.
const dropLat = ref<number | null>(null)
const dropLng = ref<number | null>(null)
const locating = ref(false)
const locationError = ref('')

const submitting = ref(false)
const error = ref('')
const placed = ref<CreateOnlineOrderResult | null>(null)

// Whether POST /online-orders wants an account, which the storefront cannot
// know up front: this repo's OnlineOrderController is deliberately
// unauthenticated ("a storefront customer has no account"), while the deployed
// backend puts `auth:customer` on the route and turns a guest order into a
// bare 401. Rather than guess which one is answering — and either block a
// guest checkout that would have worked, or show "Unauthenticated." to a
// shopper — the order is attempted and a 401 is turned into the one thing
// they can act on. The cart is untouched, so signing in and coming back
// finishes the order.
const needsSignIn = ref(false)

/*
 * Checkout is for account holders.
 *
 * A product decision, not a technical one: POST /api/online-orders still takes
 * a guest order and must keep doing so — the Android app offers checkout with
 * no account at all, and the backend reads the token as optional. This gate is
 * the web storefront's own policy, applied at the one door the web storefront
 * owns.
 *
 * `hydrating` is held apart from "signed out". A stored token is traded for the
 * account after first paint, so treating that gap as signed out would show a
 * returning shopper a sign-in wall for a moment and then snatch it away.
 */
const checkoutGated = computed(() => !account.hydrating.value && !account.signedIn.value)

/**
 * Where to send someone to sign in, and how to get them back.
 *
 * The cart lives in the header on every page, so signing in has to return to
 * the page they were shopping on rather than dumping them on the storefront
 * root having lost their aisle. The cart itself survives regardless — it is in
 * localStorage — so nothing is riding on this beyond not being annoying.
 */
const signInHref = computed(() => {
  try {
    const here = window.location.pathname + window.location.search
    return `/account?next=${encodeURIComponent(here)}`
  } catch {
    return '/account'
  }
})

const savedAddresses = computed<CustomerAddress[]>(() => account.account.value?.addresses ?? [])

function addressLineFor(address: CustomerAddress): string {
  return [address.line1, address.barangay, address.city]
    .map((part) => part.trim())
    .filter(Boolean)
    .join(', ')
}

function selectSavedAddress(id: string) {
  const address = savedAddresses.value.find((candidate) => candidate.id === id)
  if (!address) return

  addressChoice.value = id
  deliveryAddress.value = [addressLineFor(address), address.notes.trim()]
    .filter(Boolean)
    .join(' — ')
  dropLat.value = address.lat
  dropLng.value = address.lng
  locationError.value = ''
}

function useNewAddress() {
  addressChoice.value = ''
  deliveryAddress.value = ''
  dropLat.value = null
  dropLng.value = null
  locationError.value = ''
}

/**
 * Fill what the account already knows. Only ever fills blanks: this runs again
 * when the account hydrates (the request outlives the first paint) and must not
 * overwrite something typed in the meantime.
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

  const card = acct.paymentMethods.find((method) => method.isDefault) ?? acct.paymentMethods[0]
  if (card) paymentMethod.value = card.kind
}

const storeHasPin = computed(() => STORE_LAT !== null && STORE_LNG !== null)
const isDelivery = computed(() => fulfillmentMethod.value === 'delivery')
const hasDropPin = computed(() => dropLat.value !== null && dropLng.value !== null)

/**
 * An estimate, and labelled as one in the UI. OnlineOrderController re-quotes
 * from the store's own pin and charges that.
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
const grandTotalCents = computed(() => cart.totalCents.value + deliveryFeeCents.value)

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
    // signed-out shopper, but a session can end in another tab while this
    // panel is open.
    !checkoutGated.value &&
    cart.cartLines.value.length > 0 &&
    name.value.trim().length > 0 &&
    (phone.value.trim().length > 0 || email.value.trim().length > 0) &&
    (!isDelivery.value || deliveryAddress.value.trim().length > 0) &&
    !outOfRange.value,
)

async function placeOrder() {
  if (!canSubmit.value || submitting.value) return

  submitting.value = true
  error.value = ''
  needsSignIn.value = false

  try {
    const result = await createOnlineOrder(
      cart.cartLines.value.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
      {
        name: name.value.trim(),
        phone: phone.value.trim() || undefined,
        email: email.value.trim() || undefined,
      },
      {
        method: fulfillmentMethod.value,
        address: isDelivery.value ? deliveryAddress.value.trim() : undefined,
        ...(isDelivery.value && hasDropPin.value ? { lat: dropLat.value!, lng: dropLng.value! } : {}),
      },
      paymentMethod.value,
    )

    // Remembered before the cart is cleared, so a storage failure can't leave
    // the shopper with neither their cart nor a way back to the order.
    orderHistory.remember({
      orderId: result.orderId,
      ticketNumber: result.ticketNumber,
      totalCents: result.totalCents,
    })
    cart.clear()

    placed.value = result
    step.value = 'placed'
  } catch (err) {
    if (err instanceof ApiRequestError && err.status === 401) {
      needsSignIn.value = true
      error.value = 'This store takes orders from signed-in shoppers only.'
    } else {
      error.value =
        err instanceof Error && err.message
          ? err.message
          : "Couldn't place your order right now. Please check your details and try again."
    }
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('close')
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') close()
}

watch(
  () => props.open,
  (isOpen) => {
    // The panel sits over a page that must not scroll away underneath it.
    document.body.style.overflow = isOpen ? 'hidden' : ''

    if (isOpen) {
      window.addEventListener('keydown', onKeydown)
      error.value = ''
      needsSignIn.value = false
      // A finished order is last visit's news; the next open starts at the cart.
      if (step.value === 'placed') step.value = 'cart'
      applyAccountDefaults()
    } else {
      window.removeEventListener('keydown', onKeydown)
    }
  },
)

// The account arrives from /api/customer/me well after first paint, and on the
// landing page usually after the shopper has already opened the cart once.
watch(() => account.account.value, applyAccountDefaults)

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})

const heading = computed(() => {
  if (step.value === 'checkout') return 'Checkout'
  if (step.value === 'placed') return 'Order placed'
  return 'Your cart'
})
</script>

<template>
  <Teleport to="body">
    <div v-if="props.open" class="cartd" role="dialog" aria-modal="true" :aria-label="heading">
      <div class="cartd__scrim" @click="close" />

      <aside class="cartd__panel">
        <header class="cartd__head">
          <button
            v-if="step === 'checkout'"
            type="button"
            class="cartd__back"
            aria-label="Back to cart"
            @click="step = 'cart'"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
          </button>

          <h2>{{ heading }}</h2>

          <button type="button" class="cartd__x" aria-label="Close cart" @click="close">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
          </button>
        </header>

        <!-- ── The cart ─────────────────────────────────────────────────── -->
        <template v-if="step === 'cart'">
          <div class="cartd__body">
            <div v-if="cart.cartLines.value.length === 0" class="cartd__empty">
              <div class="cartd__empty-mark" aria-hidden="true">🛒</div>
              <p class="cartd__empty-title">Your cart is empty</p>
              <p class="cartd__empty-note">Add something from the shelves and it will show up here.</p>
              <a :href="props.shopHref" class="cartd__ghost" @click="close">Start shopping</a>
            </div>

            <ul v-else class="cartd__lines">
              <li v-for="line in cart.cartLines.value" :key="line.product.id" class="cartd__line">
                <div class="cartd__thumb">
                  <img v-if="line.product.imageUrl" :src="line.product.imageUrl" :alt="line.product.name" loading="lazy" />
                  <span v-else aria-hidden="true">🛒</span>
                </div>

                <div class="cartd__line-main">
                  <p class="cartd__line-name">{{ line.product.name }}</p>
                  <p class="cartd__line-unit">
                    {{ formatCurrency(line.product.priceCents) }}
                    <template v-if="line.product.unitLabel"> / {{ line.product.unitLabel }}</template>
                  </p>

                  <div class="cartd__stepper">
                    <button
                      type="button"
                      :aria-label="`Remove one ${line.product.name}`"
                      @click="cart.decrement(line.product.id)"
                    >−</button>
                    <span>{{ line.quantity }}</span>
                    <button
                      type="button"
                      :aria-label="`Add one ${line.product.name}`"
                      @click="cart.add(line.product)"
                    >+</button>
                  </div>
                </div>

                <div class="cartd__line-end">
                  <p class="cartd__line-total">
                    {{ formatCurrency(line.product.priceCents * line.quantity) }}
                  </p>
                  <button
                    type="button"
                    class="cartd__drop"
                    :aria-label="`Remove ${line.product.name} from cart`"
                    @click="cart.remove(line.product.id)"
                  >Remove</button>
                </div>
              </li>
            </ul>
          </div>

          <footer v-if="cart.cartLines.value.length > 0" class="cartd__foot">
            <dl class="cartd__totals">
              <div><dt>Subtotal</dt><dd>{{ formatCurrency(cart.subtotalCents.value) }}</dd></div>
              <div><dt>VAT</dt><dd>{{ formatCurrency(cart.taxCents.value) }}</dd></div>
              <div class="cartd__totals-sum"><dt>Total</dt><dd>{{ formatCurrency(cart.totalCents.value) }}</dd></div>
            </dl>

            <p v-if="!canOrderOnline" class="cartd__note">
              This store doesn't take online orders yet.
            </p>

            <!-- Still trading the stored token for an account. Neither door is
                 the right one to show yet, so the button waits rather than
                 flashing a sign-in wall at someone who is signed in. -->
            <button
              v-if="account.hydrating.value"
              type="button"
              class="cartd__cta"
              disabled
            >
              One moment…
            </button>

            <a
              v-else-if="checkoutGated"
              :href="signInHref"
              class="cartd__cta cartd__cta--link"
            >
              Sign in to check out
            </a>

            <button
              v-else
              type="button"
              class="cartd__cta"
              :disabled="!canOrderOnline"
              @click="step = 'checkout'"
            >
              Checkout — {{ formatCurrency(cart.totalCents.value) }}
            </button>

            <p v-if="checkoutGated" class="cartd__fineprint">
              Orders are placed from an account, so your addresses and your order history stay with
              you. Your cart is kept while you sign in — it takes a minute, and you only do it once.
            </p>
            <p v-else class="cartd__fineprint">Delivery is added at checkout.</p>
          </footer>
        </template>

        <!-- ── Checkout ─────────────────────────────────────────────────── -->
        <template v-else-if="step === 'checkout'">
          <div class="cartd__body">
            <section class="cartd__section">
              <h3>Who's it for?</h3>

              <label class="cartd__field">
                <span>Name</span>
                <input v-model="name" type="text" autocomplete="name" placeholder="Your name" />
              </label>

              <div class="cartd__pair">
                <label class="cartd__field">
                  <span>Phone</span>
                  <input v-model="phone" type="tel" autocomplete="tel" placeholder="09xx xxx xxxx" />
                </label>
                <label class="cartd__field">
                  <span>Email</span>
                  <input v-model="email" type="email" autocomplete="email" placeholder="you@example.com" />
                </label>
              </div>

              <p class="cartd__hint">A phone number or an email — we need one way to reach you.</p>
            </section>

            <section class="cartd__section">
              <h3>How do you want it?</h3>

              <div class="cartd__toggle" role="group" aria-label="Pickup or delivery">
                <button
                  type="button"
                  :class="{ 'cartd__toggle--on': fulfillmentMethod === 'delivery' }"
                  @click="fulfillmentMethod = 'delivery'"
                >Delivery</button>
                <button
                  type="button"
                  :class="{ 'cartd__toggle--on': fulfillmentMethod === 'pickup' }"
                  @click="fulfillmentMethod = 'pickup'"
                >Pickup</button>
              </div>

              <template v-if="isDelivery">
                <div v-if="savedAddresses.length > 0" class="cartd__saved">
                  <button
                    v-for="address in savedAddresses"
                    :key="address.id"
                    type="button"
                    class="cartd__saved-item"
                    :class="{ 'cartd__saved-item--on': addressChoice === address.id }"
                    @click="selectSavedAddress(address.id)"
                  >
                    <strong>{{ address.label || 'Saved address' }}</strong>
                    <span>{{ addressLineFor(address) }}</span>
                  </button>

                  <button
                    type="button"
                    class="cartd__saved-item"
                    :class="{ 'cartd__saved-item--on': addressChoice === '' }"
                    @click="useNewAddress"
                  >
                    <strong>Somewhere else</strong>
                    <span>Type a one-off address</span>
                  </button>
                </div>

                <label class="cartd__field">
                  <span>Delivery address</span>
                  <textarea
                    v-model="deliveryAddress"
                    rows="2"
                    autocomplete="street-address"
                    placeholder="House or unit number, street, barangay, city"
                    @input="addressChoice = ''"
                  />
                </label>

                <button type="button" class="cartd__locate" :disabled="locating" @click="useMyLocation">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/></svg>
                  {{ locating ? 'Finding you…' : hasDropPin ? 'Location shared ✓' : 'Use my location' }}
                </button>

                <p v-if="locationError" class="cartd__warn">{{ locationError }}</p>

                <p v-else-if="outOfRange" class="cartd__warn">
                  That's {{ deliveryQuote!.distanceKm }} km out — past the {{ DELIVERY_MAX_KM }} km
                  the store delivers to. Try pickup instead.
                </p>

                <p v-else-if="deliveryQuote" class="cartd__hint">
                  About {{ deliveryQuote.distanceKm }} km away. The store confirms the final fee.
                </p>

                <p v-else class="cartd__hint">
                  Share your location for an exact fee — otherwise the flat
                  {{ formatCurrency(DELIVERY_BASE_FEE_CENTS) }} rate applies.
                </p>
              </template>

              <p v-else class="cartd__hint">
                Collect it yourself<template v-if="STORE_ADDRESS"> at {{ STORE_ADDRESS }}</template>.
                No delivery fee.
              </p>
            </section>

            <section class="cartd__section">
              <h3>Payment</h3>

              <div class="cartd__toggle" role="group" aria-label="Payment method">
                <button
                  type="button"
                  :class="{ 'cartd__toggle--on': paymentMethod === 'cash' }"
                  @click="paymentMethod = 'cash'"
                >Cash</button>
                <button
                  type="button"
                  :class="{ 'cartd__toggle--on': paymentMethod === 'ewallet' }"
                  @click="paymentMethod = 'ewallet'"
                >E-wallet</button>
              </div>

              <p class="cartd__hint">You pay on {{ isDelivery ? 'delivery' : 'pickup' }}.</p>
            </section>
          </div>

          <footer class="cartd__foot">
            <dl class="cartd__totals">
              <div><dt>Subtotal</dt><dd>{{ formatCurrency(cart.subtotalCents.value) }}</dd></div>
              <div><dt>VAT</dt><dd>{{ formatCurrency(cart.taxCents.value) }}</dd></div>
              <div v-if="isDelivery">
                <dt>Delivery{{ deliveryQuote ? '' : ' (estimate)' }}</dt>
                <dd>{{ formatCurrency(deliveryFeeCents) }}</dd>
              </div>
              <div class="cartd__totals-sum"><dt>Total</dt><dd>{{ formatCurrency(grandTotalCents) }}</dd></div>
            </dl>

            <p v-if="error" class="cartd__error">{{ error }}</p>

            <a
              v-if="needsSignIn || checkoutGated"
              :href="signInHref"
              class="cartd__cta cartd__cta--link"
            >
              Sign in to finish
            </a>

            <button
              v-else
              type="button"
              class="cartd__cta"
              :disabled="!canSubmit || submitting"
              @click="placeOrder"
            >
              {{ submitting ? 'Placing your order…' : `Place order — ${formatCurrency(grandTotalCents)}` }}
            </button>

            <p v-if="needsSignIn || checkoutGated" class="cartd__fineprint">
              Your cart is kept — come back here once you're signed in.
            </p>
            <p v-else class="cartd__fineprint">
              The store confirms prices and the delivery fee when it accepts the order.
            </p>
          </footer>
        </template>

        <!-- ── Placed ───────────────────────────────────────────────────── -->
        <template v-else>
          <div class="cartd__body">
            <div class="cartd__done">
              <div class="cartd__done-mark" aria-hidden="true">
                <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
              </div>

              <p class="cartd__done-title">Order {{ placed?.ticketNumber }} is in</p>
              <p class="cartd__done-note">
                The store is looking at it now. We'll reach you on the
                {{ phone.trim() ? 'number' : 'email' }} you gave us.
              </p>

              <dl class="cartd__totals cartd__totals--flat">
                <div><dt>Ticket</dt><dd>{{ placed?.ticketNumber }}</dd></div>
                <div v-if="placed?.deliveryFeeCents">
                  <dt>Delivery</dt><dd>{{ formatCurrency(placed.deliveryFeeCents) }}</dd>
                </div>
                <div class="cartd__totals-sum">
                  <dt>Paid on {{ isDelivery ? 'delivery' : 'pickup' }}</dt>
                  <dd>{{ formatCurrency(placed?.totalCents ?? 0) }}</dd>
                </div>
              </dl>
            </div>
          </div>

          <footer class="cartd__foot">
            <a href="/account" class="cartd__cta cartd__cta--link">Track your order</a>
            <button type="button" class="cartd__ghost" @click="close">Keep shopping</button>
          </footer>
        </template>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.cartd {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  justify-content: flex-end;
}

.cartd__scrim {
  position: absolute;
  inset: 0;
  background: rgba(6, 36, 15, 0.42);
  animation: cartd-fade 0.18s ease-out;
}

.cartd__panel {
  position: relative;
  display: flex;
  flex-direction: column;
  width: min(430px, 100%);
  background: #fff;
  box-shadow: -18px 0 48px rgba(6, 36, 15, 0.22);
  animation: cartd-slide 0.22s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes cartd-fade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cartd-slide { from { transform: translateX(100%) } to { transform: none } }

@media (prefers-reduced-motion: reduce) {
  .cartd__scrim, .cartd__panel { animation: none; }
}

.cartd__head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  padding: 0 14px 0 20px;
  min-height: 62px;
  background: #1a6b3c;
  color: #fff;
}
.cartd__head h2 { flex: 1; margin: 0; font-size: 17px; font-weight: 700; }

.cartd__back, .cartd__x {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  cursor: pointer;
}
.cartd__back { margin-left: -8px; }
.cartd__back:hover, .cartd__x:hover { background: rgba(255, 255, 255, 0.26); }

.cartd__body { flex: 1; min-height: 0; overflow-y: auto; padding: 18px 20px; }

/* -- Empty ---------------------------------------------------------------- */

.cartd__empty { display: grid; justify-items: center; gap: 6px; padding: 54px 10px; text-align: center; }
.cartd__empty-mark { font-size: 42px; }
.cartd__empty-title { margin: 6px 0 0; font-size: 17px; font-weight: 700; color: #16321f; }
.cartd__empty-note { margin: 0 0 12px; font-size: 14px; color: #6b7280; }

/* -- Lines ---------------------------------------------------------------- */

.cartd__lines { list-style: none; margin: 0; padding: 0; }

.cartd__line {
  display: flex;
  gap: 13px;
  padding: 15px 0;
  border-bottom: 1px solid #eef1ee;
}
.cartd__line:first-child { padding-top: 0; }

.cartd__thumb {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 12px;
  background: #f4f7f4;
  overflow: hidden;
  font-size: 24px;
}
.cartd__thumb img { width: 100%; height: 100%; object-fit: cover; }

.cartd__line-main { flex: 1; min-width: 0; }
.cartd__line-name { margin: 0; font-size: 14.5px; font-weight: 650; color: #16321f; }
.cartd__line-unit { margin: 2px 0 8px; font-size: 12.5px; color: #6b7280; }

.cartd__stepper {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: 1px solid #dbe3dc;
  border-radius: 999px;
  padding: 2px;
}
.cartd__stepper button {
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #1a6b3c;
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
}
.cartd__stepper button:hover { background: #eaf5ed; }
.cartd__stepper span { min-width: 22px; text-align: center; font-size: 13.5px; font-weight: 700; }

.cartd__line-end { display: flex; flex-direction: column; align-items: flex-end; justify-content: space-between; }
.cartd__line-total { margin: 0; font-size: 14.5px; font-weight: 750; color: #16321f; }
.cartd__drop {
  border: none;
  background: none;
  padding: 0;
  color: #9ca3af;
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}
.cartd__drop:hover { color: #b91c1c; }

/* -- Form ----------------------------------------------------------------- */

.cartd__section { padding-bottom: 20px; margin-bottom: 20px; border-bottom: 1px solid #eef1ee; }
.cartd__section:last-child { padding-bottom: 0; margin-bottom: 0; border-bottom: none; }
.cartd__section h3 { margin: 0 0 12px; font-size: 14px; font-weight: 750; color: #16321f; }

.cartd__field { display: block; margin-bottom: 10px; }
.cartd__field > span { display: block; margin-bottom: 5px; font-size: 12.5px; font-weight: 650; color: #4b5563; }
.cartd__field input, .cartd__field textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #dbe3dc;
  border-radius: 10px;
  background: #fff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: #16321f;
  resize: vertical;
}
.cartd__field input:focus, .cartd__field textarea:focus {
  outline: none;
  border-color: #1a6b3c;
  box-shadow: 0 0 0 3px rgba(26, 107, 60, 0.13);
}

.cartd__pair { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }

.cartd__hint { margin: 6px 0 0; font-size: 12.5px; color: #6b7280; }
.cartd__warn { margin: 6px 0 0; font-size: 12.5px; font-weight: 600; color: #b45309; }
.cartd__note { margin: 0 0 10px; font-size: 13px; font-weight: 600; color: #b45309; text-align: center; }
.cartd__error {
  margin: 0 0 10px;
  padding: 9px 12px;
  border-radius: 10px;
  background: #fef2f2;
  font-size: 13px;
  font-weight: 600;
  color: #b91c1c;
}

.cartd__toggle { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 12px; }
.cartd__toggle button {
  padding: 11px 10px;
  border: 1px solid #dbe3dc;
  border-radius: 10px;
  background: #fff;
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 650;
  line-height: 1;
  color: #4b5563;
  cursor: pointer;
}
.cartd__toggle button:hover { border-color: #1a6b3c; }
.cartd__toggle--on {
  border-color: #1a6b3c !important;
  background: #eaf5ed !important;
  color: #14532d !important;
}

.cartd__saved { display: grid; gap: 8px; margin-bottom: 12px; }
.cartd__saved-item {
  display: grid;
  font-family: inherit;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid #dbe3dc;
  border-radius: 10px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.cartd__saved-item strong { font-size: 13px; font-weight: 700; color: #16321f; }
.cartd__saved-item span { font-size: 12.5px; color: #6b7280; }
.cartd__saved-item--on { border-color: #1a6b3c; background: #eaf5ed; }

.cartd__locate {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px;
  border: 1px solid #dbe3dc;
  border-radius: 999px;
  background: #fff;
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 650;
  line-height: 1;
  color: #1a6b3c;
  cursor: pointer;
}
.cartd__locate:hover:not(:disabled) { background: #eaf5ed; }
.cartd__locate:disabled { opacity: 0.6; cursor: default; }

/* -- Footer --------------------------------------------------------------- */

.cartd__foot {
  flex-shrink: 0;
  padding: 16px 20px 20px;
  border-top: 1px solid #eef1ee;
  background: #fbfcfb;
}

.cartd__totals { margin: 0 0 14px; }
/* The gap matters: "Paid on delivery" is long enough to fill the row, and
   space-between leaves nothing between it and the amount when it does. */
.cartd__totals div { display: flex; justify-content: space-between; gap: 14px; padding: 3px 0; }
.cartd__totals dd { white-space: nowrap; }
.cartd__totals dt { font-size: 13.5px; color: #6b7280; }
.cartd__totals dd { margin: 0; font-size: 13.5px; font-weight: 650; color: #16321f; }
.cartd__totals-sum { margin-top: 6px; padding-top: 9px !important; border-top: 1px solid #e5e9e5; }
.cartd__totals-sum dt { font-size: 15px !important; font-weight: 750; color: #16321f !important; }
.cartd__totals-sum dd { font-size: 17px !important; font-weight: 800; }
.cartd__totals--flat { margin-top: 20px; text-align: left; }

.cartd__cta {
  display: block;
  width: 100%;
  padding: 14px 16px;
  border: none;
  border-radius: 12px;
  background: #1a6b3c;
  color: #fff;
  font-family: inherit;
  font-size: 14.5px;
  font-weight: 750;
  line-height: 1;
  text-align: center;
  text-decoration: none;
  cursor: pointer;
}
.cartd__cta:hover:not(:disabled) { background: #14532d; }
.cartd__cta:disabled { background: #c9d4cb; color: #6b7280; cursor: default; }
.cartd__cta--link { margin-bottom: 8px; }

.cartd__ghost {
  display: block;
  width: 100%;
  padding: 11px 16px;
  border: 1px solid #dbe3dc;
  border-radius: 12px;
  background: #fff;
  color: #1a6b3c;
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 650;
  line-height: 1;
  text-align: center;
  text-decoration: none;
  cursor: pointer;
}
.cartd__ghost:hover { background: #eaf5ed; }

.cartd__fineprint { margin: 9px 0 0; font-size: 11.5px; color: #9ca3af; text-align: center; }

/* -- Done ----------------------------------------------------------------- */

.cartd__done { display: grid; justify-items: center; padding: 34px 4px; text-align: center; }
.cartd__done-mark {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 999px;
  background: #eaf5ed;
  color: #1a6b3c;
}
.cartd__done-title { margin: 16px 0 0; font-size: 18px; font-weight: 750; color: #16321f; }
.cartd__done-note { margin: 6px 0 0; font-size: 13.5px; color: #6b7280; max-width: 32ch; }

@media (max-width: 480px) {
  .cartd__panel { width: 100%; }
  .cartd__pair { grid-template-columns: 1fr; }
}
</style>
