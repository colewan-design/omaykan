<script setup lang="ts">
import { computed } from 'vue'
import {
  DELIVERY_BASE_FEE_CENTS,
  DELIVERY_BASE_KM,
  DELIVERY_PER_KM_CENTS,
} from '@pos/web/commerce/delivery'
import { useDeliveryLocation } from '@pos/web/commerce/deliveryLocation'

// The line under the hero that says what this shop list actually is: the
// counters near one particular person, not a national catalogue.
//
// It is also the only delivery control on a phone — the header's is hidden
// below 1080px to keep the bar one row tall — so it has to be a real button
// and not decoration.

const delivery = useDeliveryLocation()

const heading = computed(() => {
  if (delivery.area.value !== '') return `Stores and products available near ${delivery.area.value}`
  if (delivery.hasPin.value) return 'Stores and products available near you'
  return 'See what’s available near you'
})

/**
 * Only the pin can sort by distance — a typed address cannot be turned into
 * coordinates anywhere in this project, so promising "nearest first" on the
 * strength of typed text would be a claim the directory does not honour.
 */
// Read from the constants the checkout actually charges from, never typed out:
// the footer hard-coded "₱49" and "₱15/km" as prose, which is a promise that
// silently goes wrong the day the rate changes. Whole pesos, because these are
// round figures and "₱49.00" reads like a receipt rather than a price.
const peso = (cents: number) => `₱${cents / 100}`
const fromFee = peso(DELIVERY_BASE_FEE_CENTS)
const feeDetail = `covers the first ${DELIVERY_BASE_KM} km, then ${peso(DELIVERY_PER_KM_CENTS)}/km`

const note = computed(() => {
  if (!delivery.isSet.value) return 'Set your delivery location and we’ll put the closest shops first.'
  if (delivery.hasPin.value) return 'Local shops only, sorted by how close they are to you.'
  return 'Local shops only — share your location to sort them by distance.'
})
</script>

<template>
  <section class="fd-near" aria-labelledby="fd-near-title">
    <span class="fd-near__pin" aria-hidden="true">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z"/></svg>
    </span>

    <div class="fd-near__copy">
      <h2 id="fd-near-title" class="fd-near__title">{{ heading }}</h2>
      <p class="fd-near__note">{{ note }}</p>
    </div>

    <!-- "What will delivery cost?" is asked before anything is browsed, not
         after — it was only answered in the footer, past every shelf. -->
    <p class="fd-near__fee">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 17h4V5H2v12h3"/><path d="M20 17h2v-3.34a4 4 0 0 0-1.17-2.83L19 9h-5v8h1"/><circle cx="7.5" cy="17.5" r="2.5"/><circle cx="17.5" cy="17.5" r="2.5"/></svg>
      <span><strong>Delivery from {{ fromFee }}</strong> — {{ feeDetail }}</span>
    </p>

    <button type="button" class="fd-near__btn" @click="delivery.openDialog()">
      {{ delivery.isSet.value ? 'Change' : 'Set location' }}
    </button>
  </section>
</template>

<style scoped>
.fd-near {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 40px;
  padding: 16px 20px;
  border: 1px solid #dcebe1;
  border-radius: 12px;
  background: #f2f8f4;
}

.fd-near__pin {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 999px;
  background: #1a6b3c;
  color: #bbf451;
}

.fd-near__copy { flex: 1; min-width: 0; }

.fd-near__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: #06240f;
}

.fd-near__note {
  margin: 2px 0 0;
  font-size: 13.5px;
  line-height: 1.45;
  color: #4a5b52;
}

.fd-near__fee {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 0 16px;
  border-left: 1px solid #d6e6dc;
  font-size: 13px;
  line-height: 1.4;
  color: #4a5b52;
  max-width: 320px;
}
.fd-near__fee svg { flex-shrink: 0; color: #1a6b3c; }
.fd-near__fee strong { color: #06240f; font-weight: 800; }

.fd-near__btn {
  flex-shrink: 0;
  padding: 10px 20px;
  border: 1.5px solid #1a6b3c;
  border-radius: 999px;
  background: none;
  color: #1a6b3c;
  /* Longhands: `inherit` is only legal as the shorthand's entire value, so
     `font: 800 14px/1.2 inherit` is dropped whole and the element renders at
     the inherited 17px/400 instead. Same trap as .fd-totop in FdFooter. */
  font-family: inherit;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.2;
  cursor: pointer;
  transition: background 150ms, color 150ms;
}
.fd-near__btn:hover { background: #1a6b3c; color: #fff; }
.fd-near__btn:focus-visible { outline: 2px solid #1a6b3c; outline-offset: 2px; }

/* On a phone this is the only way to set a delivery location, so it keeps its
   button rather than collapsing to text — it just stacks. */
@media (max-width: 600px) {
  .fd-near {
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 28px;
    padding: 14px 16px;
  }
  .fd-near__copy { flex-basis: calc(100% - 52px); }
  .fd-near__fee {
    flex-basis: 100%;
    max-width: none;
    padding: 12px 0 0;
    border-left: none;
    border-top: 1px solid #d6e6dc;
  }
  .fd-near__title { font-size: 0.98rem; }
  .fd-near__btn { width: 100%; }
}
</style>
