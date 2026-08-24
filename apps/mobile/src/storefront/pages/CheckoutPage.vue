<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { Bike, LocateFixed, ShieldCheck, Truck } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/storefront/cart'
import { createOnlineOrder, STORE_ADDRESS, STORE_LAT, STORE_LNG } from '@pos/web/storefront/firebase'
import {
  DELIVERY_BASE_FEE_CENTS,
  DELIVERY_MAX_KM,
  haversineKm,
  quoteDelivery,
} from '@pos/web/storefront/delivery'
import { useStorefrontOrderHistory } from '../orderHistory'

const cart = useStorefrontCart()
const orderHistory = useStorefrontOrderHistory()
const router = useRouter()

const name = ref('')
const phone = ref('')
const email = ref('')
const fulfillmentMethod = ref<'pickup' | 'delivery'>('pickup')
const deliveryAddress = ref('')
const paymentMethod = ref<'cash' | 'ewallet'>('cash')
const submitting = ref(false)
const error = ref('')

// Drop-off pin, only set when the customer taps "Use my location". Delivery
// still works without it — the store then charges the flat base fee.
const dropLat = ref<number | null>(null)
const dropLng = ref<number | null>(null)
const locating = ref(false)
const locationError = ref('')

const storeHasPin = computed(() => STORE_LAT !== null && STORE_LNG !== null)

/**
 * Client-side estimate only. api/create-online-order.ts recomputes the fee
 * from the store's own pin and that value is what gets charged.
 */
const deliveryQuote = computed(() => {
  if (fulfillmentMethod.value !== 'delivery') return null
  if (!storeHasPin.value || dropLat.value === null || dropLng.value === null) return null
  return quoteDelivery(haversineKm(STORE_LAT!, STORE_LNG!, dropLat.value, dropLng.value))
})

const deliveryFeeCents = computed(() => {
  if (fulfillmentMethod.value !== 'delivery') return 0
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
    cart.cartLines.value.length > 0 &&
    name.value.trim().length > 0 &&
    (phone.value.trim() || email.value.trim()) &&
    (fulfillmentMethod.value === 'pickup' || deliveryAddress.value.trim().length > 0) &&
    !outOfRange.value,
)

async function handleSubmit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  error.value = ''

  try {
    const isDelivery = fulfillmentMethod.value === 'delivery'
    const hasPin = dropLat.value !== null && dropLng.value !== null

    const result = await createOnlineOrder(
      cart.cartLines.value.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
      { name: name.value.trim(), phone: phone.value.trim() || undefined, email: email.value.trim() || undefined },
      {
        method: fulfillmentMethod.value,
        address: isDelivery ? deliveryAddress.value.trim() : undefined,
        ...(isDelivery && hasPin ? { lat: dropLat.value!, lng: dropLng.value! } : {}),
      },
      paymentMethod.value,
    )
    cart.clear()
    orderHistory.remember({ orderId: result.orderId, ticketNumber: result.ticketNumber, totalCents: result.totalCents })
    await router.push({ name: 'order', params: { orderId: result.orderId } })
  } catch (err) {
    error.value =
      err instanceof Error && err.message
        ? err.message
        : "Couldn't place your order right now. Please check your details and try again."
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="checkout">
    <header class="checkout__header">
      <p class="checkout__eyebrow">Cart</p>
      <h1>Your order summary</h1>
      <p>Review items, choose pickup or delivery, and confirm your order.</p>
    </header>

    <p v-if="cart.cartLines.value.length === 0" class="checkout__empty">
      Your cart is empty. <RouterLink to="/">Browse the store</RouterLink> to add products.
    </p>

    <template v-else>
      <section class="checkout__lines">
        <article v-for="line in cart.cartLines.value" :key="line.product.id" class="checkout__line">
          <div>
            <strong>{{ line.product.name }}</strong>
            <p>{{ line.quantity }} x {{ formatCurrency(line.product.priceCents) }}</p>
          </div>
          <div class="checkout__line-side">
            <span>{{ formatCurrency(line.product.priceCents * line.quantity) }}</span>
            <button type="button" @click="cart.remove(line.product.id)">Remove</button>
          </div>
        </article>
      </section>

      <section class="checkout__benefits">
        <div class="checkout__benefit">
          <ShieldCheck :size="18" />
          <span>Order goes directly to the store — no commissions, ever.</span>
        </div>
        <div class="checkout__benefit">
          <Truck :size="18" />
          <span>You pay when the order is claimed or delivered — nothing is charged now.</span>
        </div>
      </section>

      <section class="checkout__fulfillment">
        <p class="checkout__section-label">How do you want it?</p>
        <div class="checkout__segmented">
          <button
            type="button"
            class="checkout__segment"
            :class="{ 'checkout__segment--active': fulfillmentMethod === 'pickup' }"
            @click="fulfillmentMethod = 'pickup'"
          >
            Pickup
          </button>
          <button
            type="button"
            class="checkout__segment"
            :class="{ 'checkout__segment--active': fulfillmentMethod === 'delivery' }"
            @click="fulfillmentMethod = 'delivery'"
          >
            Delivery
          </button>
        </div>

        <p v-if="fulfillmentMethod === 'pickup'" class="checkout__pickup-note">
          Pickup at: <strong>{{ STORE_ADDRESS || 'the store' }}</strong>
        </p>

        <template v-else>
          <label class="checkout__field">
            <span>Delivery address</span>
            <textarea v-model="deliveryAddress" rows="2" required placeholder="House/unit no., street, barangay, city" />
          </label>

          <!-- Only worth asking for a pin when the store has one to measure
               against — without it the fee is flat however precise we get. -->
          <button
            v-if="storeHasPin"
            type="button"
            class="checkout__locate"
            :disabled="locating"
            @click="useMyLocation"
          >
            <LocateFixed :size="16" />
            {{ locating ? 'Locating…' : dropLat === null ? 'Use my location for an exact fee' : 'Location pinned' }}
          </button>

          <p v-if="locationError" class="checkout__note checkout__note--warn">{{ locationError }}</p>

          <p v-else-if="outOfRange" class="checkout__note checkout__note--warn">
            You're about {{ deliveryQuote?.distanceKm.toFixed(1) }} km away — this store delivers within
            {{ DELIVERY_MAX_KM }} km. Try pickup instead.
          </p>

          <p v-else-if="deliveryQuote" class="checkout__note">
            <Bike :size="14" />
            {{ deliveryQuote.distanceKm.toFixed(1) }} km away — delivery
            {{ formatCurrency(deliveryQuote.feeCents) }}
          </p>

          <!-- The flat-fee fallback has two causes and they read differently
               to the customer: the store never set a pin (nothing they can do)
               vs. they simply haven't shared theirs yet. -->
          <p v-else-if="!storeHasPin" class="checkout__note">
            Flat delivery fee of {{ formatCurrency(DELIVERY_BASE_FEE_CENTS) }} for this store.
          </p>

          <p v-else class="checkout__note">
            Standard delivery {{ formatCurrency(DELIVERY_BASE_FEE_CENTS) }}. Share your location for a distance-based
            fee.
          </p>
        </template>
      </section>

      <section class="checkout__fulfillment">
        <p class="checkout__section-label">How will you pay?</p>
        <div class="checkout__segmented">
          <button
            type="button"
            class="checkout__segment"
            :class="{ 'checkout__segment--active': paymentMethod === 'cash' }"
            @click="paymentMethod = 'cash'"
          >
            Cash
          </button>
          <button
            type="button"
            class="checkout__segment"
            :class="{ 'checkout__segment--active': paymentMethod === 'ewallet' }"
            @click="paymentMethod = 'ewallet'"
          >
            GCash
          </button>
        </div>
        <p v-if="paymentMethod === 'ewallet'" class="checkout__pickup-note">
          The store will confirm your GCash payment when your order is claimed or delivered.
        </p>
      </section>

      <section class="checkout__summary">
        <div class="checkout__summary-row">
          <span>Subtotal</span>
          <strong>{{ formatCurrency(cart.subtotalCents.value) }}</strong>
        </div>
        <div class="checkout__summary-row">
          <span>Tax</span>
          <strong>{{ formatCurrency(cart.taxCents.value) }}</strong>
        </div>
        <div v-if="fulfillmentMethod === 'delivery'" class="checkout__summary-row">
          <span>Delivery fee</span>
          <strong>{{ formatCurrency(deliveryFeeCents) }}</strong>
        </div>
        <div class="checkout__summary-row checkout__summary-row--total">
          <span>Total</span>
          <strong>{{ formatCurrency(grandTotalCents) }}</strong>
        </div>
      </section>

      <form class="checkout__form" @submit.prevent="handleSubmit">
        <label class="checkout__field">
          <span>Full name</span>
          <input v-model="name" type="text" required placeholder="Juan Dela Cruz" />
        </label>
        <label class="checkout__field">
          <span>Phone</span>
          <input v-model="phone" type="tel" placeholder="09xx xxx xxxx" />
        </label>
        <label class="checkout__field">
          <span>Email</span>
          <input v-model="email" type="email" placeholder="you@example.com" />
        </label>

        <p class="checkout__hint">Enter a phone or email so the store can contact you about your order.</p>
        <p v-if="error" class="checkout__error">{{ error }}</p>

        <button class="checkout__submit" type="submit" :disabled="!canSubmit || submitting">
          {{ submitting ? 'Placing order...' : `Place order - ${formatCurrency(grandTotalCents)}` }}
        </button>
      </form>
    </template>
  </div>
</template>

<style scoped>
.checkout {
  display: grid;
  gap: 14px;
}

.checkout__header {
  padding: 10px 4px 2px;
}

.checkout__eyebrow {
  margin: 0 0 6px;
  color: var(--sf-primary-dark);
  font-size: 0.82rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.checkout__header h1 {
  margin: 0;
  font-family: var(--sf-font-display);
  font-size: 1.55rem;
  line-height: 1.05;
  letter-spacing: -0.03em;
}

.checkout__header p:last-child {
  margin: 10px 0 0;
  color: var(--sf-text-gray);
  line-height: 1.5;
}

.checkout__empty {
  padding: 18px;
  border-radius: 20px;
  background: var(--sf-surface);
  color: var(--sf-text-gray);
  box-shadow: var(--sf-shadow-md);
}

.checkout__empty a {
  color: var(--sf-primary-dark);
  font-weight: 700;
  text-decoration: none;
}

.checkout__lines,
.checkout__benefits,
.checkout__summary,
.checkout__form {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: var(--sf-radius-lg);
  background: var(--sf-surface);
  box-shadow: var(--sf-shadow-md);
}

.checkout__line {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.checkout__line:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.checkout__line strong,
.checkout__line p,
.checkout__line-side span,
.checkout__line-side button {
  display: block;
}

.checkout__line p {
  margin: 6px 0 0;
  color: var(--sf-text-gray);
  font-size: 0.9rem;
}

.checkout__line-side {
  text-align: right;
}

.checkout__line-side span {
  color: var(--sf-text-dark);
  font-weight: 800;
}

.checkout__line-side button {
  margin-top: 8px;
  border: none;
  background: transparent;
  color: var(--sf-primary-dark);
  font: 700 0.84rem/1 inherit;
}

.checkout__benefit {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  color: #4b5563;
  font-size: 0.9rem;
}

.checkout__fulfillment {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: var(--sf-radius-lg);
  background: var(--sf-surface);
  box-shadow: var(--sf-shadow-md);
}

.checkout__section-label {
  margin: 0;
  color: var(--sf-text-dark);
  font-size: 0.9rem;
  font-weight: 800;
}

.checkout__segmented {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  padding: 4px;
  border-radius: 14px;
  background: var(--sf-chip);
}

.checkout__segment {
  min-height: 40px;
  border: none;
  border-radius: 11px;
  background: transparent;
  color: var(--sf-text-gray);
  font: 700 0.9rem/1 inherit;
}

.checkout__segment--active {
  background: var(--sf-surface);
  color: var(--sf-primary-dark);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.1);
}

.checkout__locate {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  border: 1px dashed var(--sf-primary-light);
  border-radius: 14px;
  background: var(--sf-banner-green);
  color: var(--sf-primary-deep);
  font: 700 0.86rem/1 inherit;
}

.checkout__locate:disabled {
  opacity: 0.6;
}

.checkout__note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--sf-primary-deep);
  font-size: 0.84rem;
  font-weight: 600;
  line-height: 1.45;
}

.checkout__note--warn {
  color: #b45309;
}

.checkout__pickup-note {
  margin: 0;
  color: #4b5563;
  font-size: 0.86rem;
  line-height: 1.5;
}

.checkout__pickup-note strong {
  color: var(--sf-text-dark);
}

.checkout__field textarea {
  padding: 12px 14px;
  border: 1px solid rgba(17, 24, 39, 0.12);
  border-radius: 14px;
  background: var(--sf-surface);
  color: var(--sf-text-dark);
  font: 600 0.94rem/1.4 inherit;
  resize: vertical;
}

.checkout__summary-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #4b5563;
  font-size: 0.94rem;
}

.checkout__summary-row strong {
  color: var(--sf-text-dark);
}

.checkout__summary-row--total {
  padding-top: 10px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  color: var(--sf-text-dark);
  font-size: 1rem;
  font-weight: 800;
}

.checkout__summary-row--total strong {
  color: var(--sf-primary-dark);
  font-size: 1.16rem;
}

.checkout__field {
  display: grid;
  gap: 7px;
  color: #4b5563;
  font-size: 0.86rem;
  font-weight: 700;
}

.checkout__field input {
  min-height: 48px;
  padding: 0 14px;
  border: 1px solid rgba(17, 24, 39, 0.12);
  border-radius: 14px;
  background: var(--sf-surface);
  color: var(--sf-text-dark);
  font: 600 0.94rem/1 inherit;
}

.checkout__hint {
  margin: 2px 0 0;
  color: var(--sf-text-gray);
  font-size: 0.84rem;
  line-height: 1.45;
}

.checkout__error {
  margin: 0;
  color: var(--sf-danger);
  font-size: 0.9rem;
  font-weight: 700;
}

.checkout__submit {
  min-height: 50px;
  border: none;
  border-radius: 16px;
  background: var(--sf-primary);
  color: #fff;
  font: 800 1rem/1 inherit;
}

.checkout__submit:disabled {
  opacity: 0.5;
}
</style>
