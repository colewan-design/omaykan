<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Banknote, Bike, MapPin, ShieldCheck, ShoppingBasket, Store } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { createOnlineOrder } from '@pos/web/commerce/api'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { STORE_ADDRESS } from '@pos/web/commerce/context'
import { messageFor, useCustomerAccount } from '@pos/web/commerce/customer'
import { useDeliveryAddress } from '@pos/web/commerce/deliveryAddress'
import { useStorefrontOrderHistory } from '@pos/web/commerce/orderHistory'
import FdCheckoutAuth from './FdCheckoutAuth.vue'

// Checkout — the last thing the storefront was missing.
//
// The cart has been able to collect items since the rebuild, but there was
// nowhere to turn one into an order: the web storefront had no checkout at
// all, and POST /api/online-orders was reachable only from the mobile app.
//
// Ordering needs an account; filling a cart does not. Anyone can browse and
// add to the cart as a guest, and the account gate stands here — at the point
// where an order is about to exist and someone has to be reachable about it.
// The API enforces the same rule (auth:customer on POST /api/online-orders),
// so this card is the courteous version of a boundary that holds either way.
//
// Three things this deliberately does not do:
//
//   - take a payment, or ask how you'd like to pay. It is cash on arrival,
//     full stop: there is no gateway to integrate, and a choice of method is
//     worth offering only once there is a second one that actually works
//     differently. The order is then marked paid by whoever is standing there
//     when the money changes hands — the customer, from their order page, or
//     the shop, from the dashboard.
//   - collect an address twice. The delivery address set in the header is the
//     one the shelves were filtered by, so it is the one being delivered to —
//     this shows it and hands editing back to that panel.
//   - trust its own arithmetic. Every peso shown is recomputed server-side
//     from the merchant's records before the order is written.

const emit = defineEmits<{ placed: [orderId: string]; back: []; changeAddress: [] }>()

const cart = useStorefrontCart()
const catalog = useStorefrontCatalog()
const account = useCustomerAccount()
const delivery = useDeliveryAddress()
const orderHistory = useStorefrontOrderHistory()

const name = ref('')
const phone = ref('')
const email = ref('')
const method = ref<'pickup' | 'delivery'>('delivery')
const address = ref('')

const submitting = ref(false)
const error = ref('')

// -- Prefilling -------------------------------------------------------------

/**
 * The account arrives asynchronously (the portal token is traded for it on
 * load), so this fills the form when it lands rather than on mount — and only
 * where the customer has not already typed something of their own.
 */
watch(
  () => account.account.value,
  (customer) => {
    if (!customer) return
    if (!name.value) name.value = customer.name
    if (!phone.value) phone.value = customer.phone
    if (!email.value) email.value = customer.email
  },
  { immediate: true },
)

/** The header's address is the delivery address; this is its editable line. */
watch(
  () => delivery.label.value,
  (label) => {
    if (label && !address.value) address.value = label
  },
  { immediate: true },
)

// -- What is being bought, and from whom -------------------------------------

const lines = computed(() => cart.cartLines.value)
const empty = computed(() => lines.value.length === 0)

/**
 * The gate. `hydrating` matters as much as `signedIn`: the stored token is
 * traded for the account asynchronously on load, so a returning shopper is
 * momentarily indistinguishable from a stranger. Showing the sign-in card in
 * that window would tell someone who *is* signed in that they are not.
 */
const needsAccount = computed(() => !account.hydrating.value && !account.signedIn.value)

/**
 * Which branch the order goes to. With a delivery address set the catalog can
 * be served by a nearer branch than the one this storefront is bound to, and
 * the order has to name the counter that actually holds the stock.
 */
const storeCodes = computed(() => [
  ...new Set(lines.value.map((line) => line.product.storeCode).filter(Boolean)),
])

const orderStoreCode = computed(() => storeCodes.value[0] ?? '')

/**
 * Two branches in one cart. Only reachable for an organization running more
 * than one, and an order belongs to exactly one counter — so this asks for a
 * split rather than quietly sending everything to whichever branch came first.
 */
const mixedBranches = computed(() => storeCodes.value.length > 1)

const servingStore = computed(
  () =>
    catalog.delivery.stores.find((store) => store.code === orderStoreCode.value) ??
    catalog.delivery.stores[0] ??
    null,
)

const pickupAddress = computed(() => servingStore.value?.address || STORE_ADDRESS)

// -- Delivery ---------------------------------------------------------------

/** No branch reaches the chosen address — delivery is off the table for it. */
const outOfRange = computed(() => catalog.delivery.requested && !catalog.delivery.serviceable)

const deliveryFeeCents = computed(() => {
  if (method.value === 'pickup') return 0
  return servingStore.value?.feeCents ?? catalog.delivery.baseFeeCents
})

/** A fee worked out from a real distance, rather than the flat starting one. */
const feeIsExact = computed(
  () => method.value === 'delivery' && servingStore.value?.feeCents != null,
)

const totalCents = computed(() => cart.totalCents.value + deliveryFeeCents.value)

// An address nobody can place still buys a delivery — the shop just charges
// the flat fee, exactly as the API does. Worth saying, not worth blocking on.
const addressUnplaceable = computed(
  () => method.value === 'delivery' && delivery.coords.value === null,
)

watch(outOfRange, (isOut) => {
  if (isOut) method.value = 'pickup'
}, { immediate: true })

// -- Placing it -------------------------------------------------------------

const contactGiven = computed(() => phone.value.trim() !== '' || email.value.trim() !== '')

const canSubmit = computed(
  () =>
    !empty.value &&
    !mixedBranches.value &&
    name.value.trim() !== '' &&
    contactGiven.value &&
    (method.value === 'pickup' || address.value.trim() !== '') &&
    !submitting.value,
)

async function placeOrder() {
  if (!canSubmit.value) {
    error.value = 'Please fill in your name, a way to reach you, and where this is going.'
    return
  }

  submitting.value = true
  error.value = ''

  const isDelivery = method.value === 'delivery'
  const coords = delivery.coords.value

  try {
    const result = await createOnlineOrder(
      lines.value.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
      {
        name: name.value.trim(),
        phone: phone.value.trim() || undefined,
        email: email.value.trim() || undefined,
      },
      {
        method: method.value,
        address: isDelivery ? address.value.trim() : undefined,
        // Sent only for delivery, and only when the address was actually
        // placed: the API recomputes the fee from these and refuses a pin
        // outside the radius, so a stale one would be worse than none.
        ...(isDelivery && coords ? { lat: coords.lat, lng: coords.lng } : {}),
      },
      // Cash, always: see the note at the top of this file.
      'cash',
      orderStoreCode.value || undefined,
    )

    orderHistory.remember({
      orderId: result.orderId,
      ticketNumber: result.ticketNumber,
      totalCents: result.totalCents,
    })
    cart.clear()
    emit('placed', result.orderId)
  } catch (err) {
    // Stock ran out, the address fell outside the radius, the network died —
    // all of them leave the cart alone so the customer can fix it and retry.
    error.value = messageFor(err, "Couldn't place your order. Please try again.")
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="fdco">
    <div class="fdco__crumbs">
      <button type="button" class="fd-linkbtn" @click="emit('back')">← Keep shopping</button>
    </div>

    <h1 class="fdco__title">Checkout</h1>

    <!-- ── Nothing to buy ──────────────────────────────────────────── -->
    <div v-if="empty" class="fdco__empty">
      <ShoppingBasket :size="34" :stroke-width="1.4" />
      <p class="fdco__empty-title">Your cart is empty</p>
      <p class="fdco__empty-note">Add something from the shelves and come back here.</p>
      <button type="button" class="fd-btn" @click="emit('back')">Browse the shelves</button>
    </div>

    <div v-else class="fdco__grid">
      <!-- ── Not signed in: the gate stands in for the form ────────── -->
      <div v-if="needsAccount" class="fdco__form">
        <FdCheckoutAuth />
      </div>

      <!-- ── The form ──────────────────────────────────────────────── -->
      <div v-else class="fdco__form">
        <p v-if="mixedBranches" class="fdco__block fdco__alert">
          Your cart has items from more than one branch, and an order goes to one counter. Remove
          the items from one branch and order them separately.
        </p>

        <!-- Contact -->
        <section class="fdco__block">
          <h2 class="fdco__h2">Who is this for?</h2>
          <p v-if="account.signedIn.value" class="fdco__hint">
            Filled in from your account — change anything you like, it won't be saved back.
          </p>

          <label class="fdco__field">
            <span>Name</span>
            <input v-model="name" type="text" autocomplete="name" placeholder="Who we ask for" />
          </label>

          <div class="fdco__row">
            <label class="fdco__field">
              <span>Mobile number</span>
              <input v-model="phone" type="tel" autocomplete="tel" placeholder="09xx xxx xxxx" />
            </label>
            <label class="fdco__field">
              <span>Email</span>
              <input v-model="email" type="email" autocomplete="email" placeholder="For your receipt" />
            </label>
          </div>

          <p class="fdco__hint">One of the two is enough — it is how the shop reaches you.</p>
        </section>

        <!-- Fulfillment -->
        <section class="fdco__block">
          <h2 class="fdco__h2">How do you want it?</h2>

          <div class="fdco__segments">
            <button
              type="button"
              class="fdco__segment"
              :class="{ 'fdco__segment--on': method === 'delivery' }"
              :disabled="outOfRange"
              @click="method = 'delivery'"
            >
              <Bike :size="18" :stroke-width="1.8" />
              Delivery
            </button>
            <button
              type="button"
              class="fdco__segment"
              :class="{ 'fdco__segment--on': method === 'pickup' }"
              @click="method = 'pickup'"
            >
              <Store :size="18" :stroke-width="1.8" />
              Pick it up
            </button>
          </div>

          <p v-if="outOfRange" class="fdco__alert" style="margin-top: 14px">
            No branch delivers to your address yet, so this one is pickup only.
            <button type="button" class="fd-linkbtn" @click="emit('changeAddress')">
              Change address
            </button>
          </p>

          <template v-else-if="method === 'delivery'">
            <label class="fdco__field" style="margin-top: 14px">
              <span>Where the rider is going</span>
              <textarea
                v-model="address"
                rows="3"
                placeholder="House or unit number, street, barangay, and a landmark if it helps"
              />
            </label>

            <p class="fdco__hint">
              <MapPin :size="14" :stroke-width="2" />
              <template v-if="delivery.hasAddress.value">
                From the address in the header.
                <button type="button" class="fd-linkbtn" @click="emit('changeAddress')">Change it</button>
              </template>
              <template v-else>
                <button type="button" class="fd-linkbtn" @click="emit('changeAddress')">
                  Set your delivery address
                </button>
                so the fee can be worked out exactly.
              </template>
            </p>

            <p v-if="addressUnplaceable" class="fdco__note">
              We could not put this address on a map, so the flat starting fee is shown. The shop
              confirms the real one when they accept the order.
            </p>
          </template>

          <p v-else class="fdco__note" style="margin-top: 14px">
            <Store :size="15" :stroke-width="1.8" />
            Collect from {{ servingStore?.name || 'the shop' }}<template v-if="pickupAddress">,
            {{ pickupAddress }}</template>. Nothing to pay for delivery.
          </p>
        </section>

        <!-- Payment -->
        <section class="fdco__block">
          <h2 class="fdco__h2">Paying</h2>

          <p class="fdco__note" style="margin-top: 0">
            <Banknote :size="16" :stroke-width="1.8" />
            <span>
              <strong>Cash on arrival.</strong> Nothing is charged online, and there is nothing to
              enter here.
            </span>
          </p>

          <p class="fdco__hint">
            <ShieldCheck :size="14" :stroke-width="2" />
            Once you've handed the money over, either you or the shop marks the order paid — you
            from the page you land on next, they from their till.
          </p>
        </section>
      </div>

      <!-- ── The summary ───────────────────────────────────────────── -->
      <aside class="fdco__summary">
        <h2 class="fdco__h2">Your order</h2>

        <ul class="fdco__lines">
          <li v-for="line in lines" :key="line.product.id">
            <span class="fdco__qty">{{ line.quantity }}×</span>
            <span class="fdco__lname">{{ line.product.name }}</span>
            <span>{{ formatCurrency(line.product.priceCents * line.quantity) }}</span>
          </li>
        </ul>

        <dl class="fdco__totals">
          <div>
            <dt>Subtotal</dt>
            <dd>{{ formatCurrency(cart.subtotalCents.value) }}</dd>
          </div>
          <div>
            <dt>VAT</dt>
            <dd>{{ formatCurrency(cart.taxCents.value) }}</dd>
          </div>
          <div>
            <dt>
              Delivery
              <template v-if="method === 'delivery' && servingStore?.distanceKm != null">
                · {{ servingStore.distanceKm.toFixed(1) }} km
              </template>
            </dt>
            <dd>
              <template v-if="method === 'pickup'">Free</template>
              <template v-else>{{ feeIsExact ? '' : 'from ' }}{{ formatCurrency(deliveryFeeCents) }}</template>
            </dd>
          </div>
          <div class="fdco__grand">
            <dt>Total</dt>
            <dd>{{ formatCurrency(totalCents) }}</dd>
          </div>
        </dl>

        <p v-if="error" class="fdco__alert">{{ error }}</p>

        <!-- While the gate is up there is nothing to place yet, and a live
             button that only produces "sign in first" would be a worse way of
             saying what the card next to it already says. -->
        <p v-if="needsAccount" class="fdco__note" style="margin-top: 0">
          <ShieldCheck :size="15" :stroke-width="1.8" />
          <span>Sign in to place this order. Your cart is saved either way.</span>
        </p>

        <button
          v-else
          type="button"
          class="fdco__place"
          :disabled="!canSubmit"
          @click="placeOrder"
        >
          {{ submitting ? 'Placing your order…' : `Place order · ${formatCurrency(totalCents)}` }}
        </button>

        <p class="fdco__fine">
          The shop is charged no commission on this order, and the price you see is the price at
          their counter.
        </p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
/* .fd-btn and .fd-linkbtn are defined in LandingPage's scoped block, which
   does not reach inside a child component — so they are restated here rather
   than left unstyled. */
.fd-btn {
  display: inline-block;
  padding: 12px 24px;
  border: none;
  border-radius: 999px;
  background: #1a1a1a;
  color: #fff;
  font: 700 14.5px/1.2 inherit;
  cursor: pointer;
}
.fd-btn:hover { background: #1a6b3c; }

.fd-linkbtn {
  border: none;
  background: none;
  padding: 0;
  color: #1a6b3c;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.fdco { max-width: 1160px; margin: 0 auto; }

.fdco__crumbs { margin-bottom: 10px; }

.fdco__title {
  margin: 0 0 24px;
  font-size: clamp(1.6rem, 3vw, 2.2rem);
  font-weight: 800;
  letter-spacing: -0.03em;
}

.fdco__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 348px;
  gap: 28px;
  align-items: start;
}

/* -- Blocks --------------------------------------------------------------- */

.fdco__block {
  padding: 20px;
  border: 1px solid #e4e9e6;
  border-radius: 14px;
  background: #fff;
}

.fdco__block + .fdco__block { margin-top: 16px; }

.fdco__h2 { margin: 0 0 14px; font-size: 16px; font-weight: 800; }

.fdco__field { display: block; margin-top: 12px; }
.fdco__field > span {
  display: block;
  margin-bottom: 5px;
  color: #4a5b52;
  font-size: 12.5px;
  font-weight: 700;
}

.fdco__field input,
.fdco__field textarea {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid #d7ded9;
  border-radius: 10px;
  background: #fff;
  /* Spelled out rather than `font: ... inherit`: a textarea's own default
     family is monospace, and the shorthand does not reliably override it. */
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: #1a1a1a;
  resize: vertical;
}

.fdco__field input:focus,
.fdco__field textarea:focus { outline: 2px solid #1a6b3c; outline-offset: -1px; }

.fdco__row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

.fdco__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin: 10px 0 0;
  color: #6b7280;
  font-size: 12.5px;
  line-height: 1.45;
}

.fdco__hint .fd-linkbtn { margin-left: 0; }

.fdco__note {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  margin: 12px 0 0;
  padding: 11px 12px;
  border-radius: 10px;
  background: #f4f6f5;
  color: #3d4a43;
  font-size: 12.5px;
  line-height: 1.45;
}

.fdco__alert {
  margin: 0 0 14px;
  padding: 11px 12px;
  border-radius: 10px;
  background: #fdf0ef;
  color: #8a2c22;
  font-size: 13px;
  line-height: 1.45;
}

.fdco__alert .fd-linkbtn { color: #8a2c22; }

/* -- Segments ------------------------------------------------------------- */

.fdco__segments { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }

.fdco__segment {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 46px;
  border: 1px solid #d7ded9;
  border-radius: 10px;
  background: #fff;
  color: #3d4a43;
  font: 700 14px/1 inherit;
  cursor: pointer;
}

.fdco__segment:hover:not(:disabled) { border-color: #1a6b3c; }
.fdco__segment--on { border-color: #1a6b3c; background: #eefaf1; color: #14532d; }
.fdco__segment:disabled { opacity: 0.45; cursor: not-allowed; }

/* -- Summary -------------------------------------------------------------- */

.fdco__summary {
  position: sticky;
  top: 190px;
  padding: 20px;
  border: 1px solid #e4e9e6;
  border-radius: 14px;
  background: #fff;
}

.fdco__lines { margin: 0 0 14px; padding: 0; list-style: none; }
.fdco__lines li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 9px;
  padding: 7px 0;
  font-size: 13.5px;
  border-bottom: 1px solid #f1f4f2;
}

.fdco__qty { color: #1a6b3c; font-weight: 800; }
.fdco__lname { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.fdco__totals { margin: 0 0 14px; }
.fdco__totals > div { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.fdco__totals > div + div { margin-top: 5px; }
.fdco__totals dt { color: #4a5b52; font-size: 13.5px; }
.fdco__totals dd { margin: 0; font-size: 13.5px; font-weight: 600; }

.fdco__grand { margin-top: 10px !important; padding-top: 10px; border-top: 1px solid #ecefed; }
.fdco__grand dt { font-size: 15px !important; font-weight: 800; color: #1a1a1a !important; }
.fdco__grand dd { font-size: 18px !important; font-weight: 800; }

.fdco__place {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 999px;
  background: #22c55e;
  color: #06240f;
  font: 800 15px/1 inherit;
  cursor: pointer;
}

.fdco__place:hover:not(:disabled) { background: #16a34a; color: #fff; }
.fdco__place:disabled { opacity: 0.5; cursor: not-allowed; }

.fdco__fine { margin: 12px 0 0; color: #8b968f; font-size: 11.5px; line-height: 1.45; }

/* -- Empty ---------------------------------------------------------------- */

.fdco__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 60px 20px;
  text-align: center;
  color: #6b7280;
}

.fdco__empty-title { margin: 10px 0 0; color: #1a1a1a; font-size: 17px; font-weight: 800; }
.fdco__empty-note { margin: 0 0 18px; font-size: 14px; }

@media (max-width: 900px) {
  .fdco__grid { grid-template-columns: minmax(0, 1fr); }
  .fdco__summary { position: static; }
}

@media (max-width: 560px) {
  .fdco__row { grid-template-columns: 1fr; }
}
</style>
