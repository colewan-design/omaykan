<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { LocateFixed, MapPin, X } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { findArea, serviceAreaGroups } from '@pos/web/commerce/areas'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { useDeliveryAddress } from '@pos/web/commerce/deliveryAddress'

// The delivery slot in the header bar, and the panel behind it.
//
// This used to be a label and a dead link pointing at the product shelves.
// Setting an address here now decides what is on those shelves: the catalog is
// re-fetched around the pin, and the API answers with what a branch in
// delivery range can actually send — see commerce/catalog.ts.
//
// Three ways in, in the order they are worth offering:
//   1. the device's own location, which is the only exact one;
//   2. an address already saved to the signed-in account;
//   3. an area picked off a list, for everyone who declines the first.
// There is no geocoder to turn free text into a pin, so a typed street on its
// own places nothing — the panel says so rather than pretending.

const delivery = useDeliveryAddress()
const account = useCustomerAccount()
const catalog = useStorefrontCatalog()

const open = ref(false)
const root = ref<HTMLElement | null>(null)

const areaId = ref('')
const line1 = ref('')
const formError = ref('')

const state = computed(() => catalog.delivery)

/** The saved book is only worth showing to someone who has one. */
const savedAddresses = computed(() => account.addresses.value)

/** Set, but with nothing to filter by — see setUnplaceable. */
const unplaceable = computed(() => delivery.hasAddress.value && delivery.coords.value === null)

const servingStore = computed(() => state.value.stores[0] ?? null)

function onDocumentPointerDown(event: MouseEvent) {
  if (root.value && !root.value.contains(event.target as Node)) open.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}

watch(open, (isOpen) => {
  if (isOpen) {
    // Seed the form from whatever is already set, so re-opening it reads as
    // editing an address rather than starting again.
    areaId.value = delivery.address.value?.areaId ?? ''
    line1.value = delivery.address.value?.line1 ?? ''
    formError.value = ''
    document.addEventListener('pointerdown', onDocumentPointerDown)
    document.addEventListener('keydown', onKeydown)
  } else {
    document.removeEventListener('pointerdown', onDocumentPointerDown)
    document.removeEventListener('keydown', onKeydown)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onKeydown)
})

async function locate() {
  if (await delivery.useMyLocation()) open.value = false
}

function apply() {
  const area = areaId.value ? findArea(areaId.value) : null

  if (area) {
    delivery.setArea(area, line1.value)
    open.value = false
    return
  }

  if (line1.value.trim()) {
    delivery.setUnplaceable(line1.value)
    open.value = false
    return
  }

  formError.value = 'Pick the area you are in, or share your location.'
}

function clear() {
  delivery.clear()
  areaId.value = ''
  line1.value = ''
  open.value = false
}

// The page below the header also asks for this panel — the out-of-range
// notice on the shelves is where most people will realise they need it.
defineExpose({ openPanel: () => (open.value = true) })
</script>

<template>
  <div ref="root" class="fd-deliv">
    <button
      type="button"
      class="fd-deliv__trigger"
      :aria-expanded="open"
      aria-haspopup="dialog"
      @click="open = !open"
    >
      <span class="fd-deliv__label">Delivery</span>
      <span class="fd-deliv__value">
        <MapPin :size="13" :stroke-width="2.2" />
        <span class="fd-deliv__text">{{ delivery.hasAddress.value ? delivery.label.value : 'Enter your address' }}</span>
      </span>
    </button>

    <div v-if="open" class="fd-deliv__panel" role="dialog" aria-label="Delivery address">
      <div class="fd-deliv__head">
        <p class="fd-deliv__title">Where are we delivering?</p>
        <button type="button" class="fd-deliv__close" aria-label="Close" @click="open = false">
          <X :size="17" :stroke-width="2" />
        </button>
      </div>

      <p class="fd-deliv__blurb">
        The shelves only show what a shop near you can actually send — set this and anything out of
        range drops off the page.
      </p>

      <button type="button" class="fd-deliv__locate" :disabled="delivery.locating.value" @click="locate">
        <LocateFixed :size="17" :stroke-width="1.9" />
        {{ delivery.locating.value ? 'Finding you…' : 'Use my current location' }}
      </button>

      <p v-if="delivery.locationError.value" class="fd-deliv__error">{{ delivery.locationError.value }}</p>

      <template v-if="savedAddresses.length > 0">
        <p class="fd-deliv__legend">Saved addresses</p>
        <button
          v-for="saved in savedAddresses"
          :key="saved.id"
          type="button"
          class="fd-deliv__saved"
          @click="delivery.setFromSaved(saved); open = false"
        >
          <MapPin :size="15" :stroke-width="1.8" />
          <span>
            <strong>{{ saved.label }}</strong>
            {{ [saved.line1, saved.barangay, saved.city].filter(Boolean).join(', ') }}
          </span>
        </button>
      </template>

      <p class="fd-deliv__legend">Or pick your area</p>

      <select v-model="areaId" class="fd-deliv__select" aria-label="Your area">
        <option value="">Select an area…</option>
        <optgroup v-for="group in serviceAreaGroups" :key="group.name" :label="group.name">
          <option v-for="area in group.areas" :key="area.id" :value="area.id">{{ area.name }}</option>
        </optgroup>
      </select>

      <input
        v-model="line1"
        type="text"
        class="fd-deliv__input"
        placeholder="Street and house number (optional)"
        aria-label="Street address"
        @keyup.enter="apply"
      />

      <p v-if="formError" class="fd-deliv__error">{{ formError }}</p>

      <div class="fd-deliv__actions">
        <button type="button" class="fd-deliv__save" @click="apply">Save address</button>
        <button v-if="delivery.hasAddress.value" type="button" class="fd-deliv__clear" @click="clear">
          Clear
        </button>
      </div>

      <!-- What the address actually did to the catalog. -->
      <p v-if="unplaceable" class="fd-deliv__note">
        We couldn't place that address on a map, so nothing has been filtered. Pick an area, or share
        your location, to see only what reaches you.
      </p>

      <p v-else-if="state.requested && !state.serviceable" class="fd-deliv__note fd-deliv__note--bad">
        No shop delivers here yet.
        <template v-if="state.nearest?.distanceKm">
          The nearest is {{ state.nearest.name }}, {{ state.nearest.distanceKm.toFixed(1) }} km away —
          past the {{ state.maxKm }} km limit.
        </template>
      </p>

      <p v-else-if="state.requested && servingStore" class="fd-deliv__note fd-deliv__note--good">
        <template v-if="servingStore.distanceKm !== null">
          {{ servingStore.name }} delivers here — {{ servingStore.distanceKm.toFixed(1) }} km away,
          from {{ formatCurrency(servingStore.feeCents ?? 0) }}.
        </template>
        <template v-else>{{ servingStore.name }} delivers here.</template>
        <template v-if="state.stores.length > 1">
          {{ state.stores.length - 1 }} more branch{{ state.stores.length - 1 === 1 ? '' : 'es' }} in range.
        </template>
      </p>

      <p class="fd-deliv__fine">
        Areas are approximate — the exact drop-off is confirmed at checkout.
      </p>
    </div>
  </div>
</template>

<style scoped>
.fd-deliv { position: relative; flex-shrink: 0; }

.fd-deliv__trigger {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  max-width: 210px;
  padding: 0 0 0 22px;
  border: none;
  border-left: 1px solid rgba(255, 255, 255, 0.22);
  background: none;
  color: #fff;
  font: inherit;
  line-height: 1.3;
  text-align: left;
  cursor: pointer;
}

.fd-deliv__label { font-size: 13.5px; font-weight: 700; }

.fd-deliv__value {
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.fd-deliv__trigger:hover .fd-deliv__value { color: #fff; }

.fd-deliv__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fd-deliv__panel {
  position: absolute;
  top: calc(100% + 12px);
  left: 0;
  z-index: 200;
  width: 336px;
  max-width: calc(100vw - 32px);
  padding: 18px;
  border-radius: 14px;
  background: #fff;
  color: #1a1a1a;
  box-shadow: 0 18px 44px rgba(6, 36, 15, 0.24);
}

.fd-deliv__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }

.fd-deliv__title { margin: 0; font-size: 15.5px; font-weight: 800; }

.fd-deliv__close {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: none;
  background: none;
  padding: 2px;
  color: #6b7280;
  cursor: pointer;
}

.fd-deliv__blurb { margin: 6px 0 14px; color: #4a5b52; font-size: 12.5px; line-height: 1.45; }

.fd-deliv__locate {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 40px;
  border: 1px solid #1a6b3c;
  border-radius: 10px;
  background: #f2f9f4;
  color: #1a6b3c;
  font: 700 13.5px/1 inherit;
  cursor: pointer;
}

.fd-deliv__locate:hover:not(:disabled) { background: #e3f3e8; }
.fd-deliv__locate:disabled { opacity: 0.6; cursor: progress; }

.fd-deliv__legend {
  margin: 16px 0 8px;
  color: #6b7280;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.fd-deliv__saved {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  width: 100%;
  margin-bottom: 6px;
  padding: 9px 10px;
  border: 1px solid #e4e9e6;
  border-radius: 10px;
  background: #fff;
  color: #1a1a1a;
  font: 400 12.5px/1.45 inherit;
  text-align: left;
  cursor: pointer;
}

.fd-deliv__saved:hover { border-color: #1a6b3c; background: #f7fbf8; }
.fd-deliv__saved strong { display: block; font-size: 13px; }
.fd-deliv__saved svg { flex-shrink: 0; margin-top: 2px; color: #1a6b3c; }

.fd-deliv__select,
.fd-deliv__input {
  width: 100%;
  height: 40px;
  padding: 0 11px;
  border: 1px solid #d7ded9;
  border-radius: 10px;
  background: #fff;
  font: 500 13.5px/1 inherit;
  color: #1a1a1a;
}

.fd-deliv__input { margin-top: 8px; }
.fd-deliv__select:focus,
.fd-deliv__input:focus { outline: 2px solid #1a6b3c; outline-offset: -1px; }

.fd-deliv__actions { display: flex; gap: 8px; margin-top: 12px; }

.fd-deliv__save {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 999px;
  background: #22c55e;
  color: #06240f;
  font: 800 13.5px/1 inherit;
  cursor: pointer;
}

.fd-deliv__save:hover { background: #16a34a; color: #fff; }

.fd-deliv__clear {
  height: 40px;
  padding: 0 16px;
  border: 1px solid #d7ded9;
  border-radius: 999px;
  background: #fff;
  font: 700 13.5px/1 inherit;
  color: #4a5b52;
  cursor: pointer;
}

.fd-deliv__clear:hover { border-color: #b9c4bd; }

.fd-deliv__note {
  margin: 13px 0 0;
  padding: 10px 11px;
  border-radius: 10px;
  background: #f4f6f5;
  color: #3d4a43;
  font-size: 12.5px;
  line-height: 1.45;
}

.fd-deliv__note--good { background: #eefaf1; color: #14532d; }
.fd-deliv__note--bad { background: #fdf0ef; color: #8a2c22; }

.fd-deliv__error { margin: 9px 0 0; color: #b3261e; font-size: 12.5px; }

.fd-deliv__fine { margin: 11px 0 0; color: #8b968f; font-size: 11.5px; line-height: 1.4; }

/* The delivery slot used to be hidden below 1080px, back when it was two lines
   of decoration. It sets what the whole page shows now, so it stays on every
   width and gives up room instead. */
@media (max-width: 1080px) {
  .fd-deliv__trigger { max-width: 148px; }
}

@media (max-width: 760px) {
  .fd-deliv__trigger { max-width: 128px; padding-left: 0; border-left: none; }
  .fd-deliv__panel { left: auto; right: 0; }
}
</style>
