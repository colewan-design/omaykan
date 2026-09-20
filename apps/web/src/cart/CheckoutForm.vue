<script setup lang="ts">
import { computed } from 'vue'
import { Crosshair } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { addressLineFor, type Checkout } from '@pos/web/commerce/checkout'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { STORE_ADDRESS } from '@pos/web/commerce/context'
import { DELIVERY_BASE_FEE_CENTS, DELIVERY_MAX_KM } from '@pos/web/commerce/delivery'

// The checkout step's left column: who it is for, how it gets to them, and how
// they will pay. The state lives in useCheckout, owned by the cart page, so the
// summary beside this form can price and place the order from the same answers.

const props = defineProps<{ checkout: Checkout }>()

// Top-level refs so the template unwraps them — refs reached through a prop
// object are not.
const {
  name,
  phone,
  email,
  fulfillmentMethod,
  deliveryAddress,
  paymentMethod,
  addressChoice,
  locating,
  locationError,
  savedAddresses,
  isDelivery,
  hasDropPin,
  deliveryQuote,
  outOfRange,
} = props.checkout

const catalog = useStorefrontCatalog()
const pickupAddress = computed(() => catalog.shop?.address || STORE_ADDRESS)
</script>

<template>
  <div class="ckf">
    <section class="ckf-card">
      <h2 class="ckf-card__title">Who's it for?</h2>

      <label class="ckf-field">
        <span>Name</span>
        <input v-model="name" type="text" autocomplete="name" placeholder="Your name" />
      </label>

      <div class="ckf-pair">
        <label class="ckf-field">
          <span>Phone</span>
          <input v-model="phone" type="tel" autocomplete="tel" placeholder="09xx xxx xxxx" />
        </label>
        <label class="ckf-field">
          <span>Email</span>
          <input v-model="email" type="email" autocomplete="email" placeholder="you@example.com" />
        </label>
      </div>

      <p class="ckf-hint">A phone number or an email — the shop needs one way to reach you.</p>
    </section>

    <section class="ckf-card">
      <h2 class="ckf-card__title">How do you want it?</h2>

      <div class="ckf-toggle" role="group" aria-label="Pickup or delivery">
        <button
          type="button"
          :class="{ 'ckf-toggle--on': fulfillmentMethod === 'delivery' }"
          :aria-pressed="fulfillmentMethod === 'delivery'"
          @click="fulfillmentMethod = 'delivery'"
        >Delivery</button>
        <button
          type="button"
          :class="{ 'ckf-toggle--on': fulfillmentMethod === 'pickup' }"
          :aria-pressed="fulfillmentMethod === 'pickup'"
          @click="fulfillmentMethod = 'pickup'"
        >Pickup</button>
      </div>

      <template v-if="isDelivery">
        <div v-if="savedAddresses.length > 0" class="ckf-saved">
          <button
            v-for="address in savedAddresses"
            :key="address.id"
            type="button"
            class="ckf-saved__item"
            :class="{ 'ckf-saved__item--on': addressChoice === address.id }"
            @click="checkout.selectSavedAddress(address.id)"
          >
            <strong>{{ address.label || 'Saved address' }}</strong>
            <span>{{ addressLineFor(address) }}</span>
          </button>

          <button
            type="button"
            class="ckf-saved__item"
            :class="{ 'ckf-saved__item--on': addressChoice === '' }"
            @click="checkout.useNewAddress()"
          >
            <strong>Somewhere else</strong>
            <span>Type a one-off address</span>
          </button>
        </div>

        <label class="ckf-field">
          <span>Delivery address</span>
          <textarea
            v-model="deliveryAddress"
            rows="2"
            autocomplete="street-address"
            placeholder="House or unit number, street, barangay, city"
            @input="addressChoice = ''"
          />
        </label>

        <button type="button" class="ckf-locate" :disabled="locating" @click="checkout.useMyLocation()">
          <Crosshair :size="15" :stroke-width="2" />
          {{ locating ? 'Finding you…' : hasDropPin ? 'Location shared ✓' : 'Use my location' }}
        </button>

        <p v-if="locationError" class="ckf-warn">{{ locationError }}</p>
        <p v-else-if="outOfRange" class="ckf-warn">
          That's {{ deliveryQuote!.distanceKm }} km out — past the {{ DELIVERY_MAX_KM }} km the shop
          delivers to. Try pickup instead.
        </p>
        <p v-else-if="deliveryQuote" class="ckf-hint">
          About {{ deliveryQuote.distanceKm }} km away. The shop confirms the final fee.
        </p>
        <p v-else class="ckf-hint">
          Share your location for an exact fee — otherwise the flat
          {{ formatCurrency(DELIVERY_BASE_FEE_CENTS) }} rate applies.
        </p>
      </template>

      <p v-else class="ckf-hint">
        Collect it yourself<template v-if="pickupAddress"> at {{ pickupAddress }}</template>. No
        delivery fee.
      </p>
    </section>

    <section class="ckf-card">
      <h2 class="ckf-card__title">Payment</h2>

      <div class="ckf-toggle" role="group" aria-label="Payment method">
        <button
          type="button"
          :class="{ 'ckf-toggle--on': paymentMethod === 'cash' }"
          :aria-pressed="paymentMethod === 'cash'"
          @click="paymentMethod = 'cash'"
        >Cash</button>
        <button
          type="button"
          :class="{ 'ckf-toggle--on': paymentMethod === 'ewallet' }"
          :aria-pressed="paymentMethod === 'ewallet'"
          @click="paymentMethod = 'ewallet'"
        >GCash</button>
      </div>

      <p class="ckf-hint">You pay on {{ isDelivery ? 'delivery' : 'pickup' }} — nothing is charged now.</p>
    </section>
  </div>
</template>

<style scoped>
.ckf { display: grid; gap: 16px; }

.ckf-card {
  padding: 22px 24px;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: #ffffff;
}

.ckf-card__title {
  margin: 0 0 14px;
  font-family: var(--sf-serif);
  font-size: 18px;
  font-weight: 700;
  color: var(--sf-ink);
}

.ckf-field { display: block; margin-bottom: 12px; }
.ckf-field > span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--sf-ink);
}
.ckf-field input,
.ckf-field textarea {
  width: 100%;
  padding: 11px 13px;
  border: 1px solid var(--sf-rule);
  border-radius: 6px;
  background: #fff;
  font-family: inherit;
  font-size: 14.5px;
  line-height: 1.4;
  color: var(--sf-ink);
  resize: vertical;
}
.ckf-field input:focus,
.ckf-field textarea:focus {
  outline: none;
  border-color: var(--sf-clay);
  box-shadow: 0 0 0 3px rgba(180, 83, 42, 0.14);
}

.ckf-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

.ckf-hint { margin: 6px 0 0; font-size: 13px; line-height: 1.5; color: var(--sf-muted); }
.ckf-warn { margin: 6px 0 0; font-size: 13px; font-weight: 600; color: #a05a14; }

.ckf-toggle { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 14px; }
.ckf-toggle button {
  padding: 12px 10px;
  border: 1px solid var(--sf-rule);
  border-radius: 6px;
  background: #fff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  color: var(--sf-muted);
  cursor: pointer;
}
.ckf-toggle button:hover { border-color: var(--sf-clay); }
.ckf-toggle .ckf-toggle--on {
  border-color: var(--sf-clay);
  background: #fbefe8;
  color: var(--sf-clay-deep);
}

.ckf-saved {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}
.ckf-saved__item {
  display: grid;
  gap: 3px;
  padding: 11px 13px;
  border: 1px solid var(--sf-rule);
  border-radius: 6px;
  background: #fff;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}
.ckf-saved__item strong { font-size: 13.5px; font-weight: 700; color: var(--sf-ink); }
.ckf-saved__item span { font-size: 12.5px; color: var(--sf-muted); }
.ckf-saved__item:hover { border-color: var(--sf-clay); }
.ckf-saved__item--on { border-color: var(--sf-clay); background: #fbefe8; }

.ckf-locate {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 9px 15px;
  border: 1px solid var(--sf-rule);
  border-radius: 999px;
  background: #fff;
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  color: var(--sf-forest);
  cursor: pointer;
}
.ckf-locate:hover:not(:disabled) { border-color: var(--sf-forest); }
.ckf-locate:disabled { opacity: 0.6; cursor: default; }

@media (max-width: 560px) {
  .ckf-card { padding: 18px 16px; }
  .ckf-pair { grid-template-columns: 1fr; }
}
</style>
