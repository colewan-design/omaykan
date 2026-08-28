<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useDeliveryLocation } from '@pos/web/commerce/deliveryLocation'

/**
 * The panel behind "Enter your address" in the header.
 *
 * Two separate things, deliberately shown as two: the address a rider reads,
 * and the pin distances are measured from. Nothing here geocodes the typed
 * text — no geocoder is configured — so the pin can only come from the browser.
 * Saying that plainly is better than a field that looks like it places you on
 * a map and does not.
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const delivery = useDeliveryLocation()

const address = ref('')
const lat = ref<number | null>(null)
const lng = ref<number | null>(null)
const locating = ref(false)
const locationError = ref('')

// Re-read on every open rather than once at setup: the dialog outlives any
// single opening, and a visitor who cancels should not see their abandoned
// edit again next time.
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      address.value = delivery.location.value.address
      lat.value = delivery.location.value.lat
      lng.value = delivery.location.value.lng
      locationError.value = ''
      document.body.style.overflow = 'hidden'
      window.addEventListener('keydown', onKeydown)
    } else {
      document.body.style.overflow = ''
      window.removeEventListener('keydown', onKeydown)
    }
  },
)

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

function useMyLocation(): void {
  if (locating.value) return

  if (!navigator.geolocation) {
    locationError.value = 'This device cannot share a location.'
    return
  }

  locating.value = true
  locationError.value = ''
  navigator.geolocation.getCurrentPosition(
    (position) => {
      lat.value = position.coords.latitude
      lng.value = position.coords.longitude
      locating.value = false
    },
    () => {
      locationError.value = "Couldn't get your location. You can still type your address."
      locating.value = false
    },
    { enableHighAccuracy: true, timeout: 10000 },
  )
}

function save(): void {
  delivery.set({ address: address.value.trim(), lat: lat.value, lng: lng.value })
  emit('saved')
  emit('close')
}

function clear(): void {
  delivery.clear()
  address.value = ''
  lat.value = null
  lng.value = null
  emit('saved')
  emit('close')
}

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.open"
      class="addr"
      role="dialog"
      aria-modal="true"
      aria-labelledby="addr-title"
    >
      <div class="addr__scrim" @click="emit('close')" />

      <div class="addr__panel">
        <button type="button" class="addr__x" aria-label="Close" @click="emit('close')">
          &times;
        </button>

        <h2 id="addr-title" class="addr__title">Where are we delivering?</h2>
        <p class="addr__sub">
          We&rsquo;ll show the shops nearest you first, and keep this for checkout.
        </p>

        <label class="addr__label" for="addr-line">Delivery address</label>
        <input
          id="addr-line"
          v-model="address"
          class="addr__input"
          type="text"
          autocomplete="street-address"
          placeholder="House or unit number, street, barangay"
          @keyup.enter="save"
        />

        <div class="addr__pin">
          <button type="button" class="addr__locate" :disabled="locating" @click="useMyLocation">
            {{ locating ? 'Locating…' : 'Use my location' }}
          </button>
          <span v-if="lat !== null && lng !== null" class="addr__pinned">
            Pinned — shops will be sorted by distance
          </span>
          <span v-else class="addr__hint">
            Optional, and the only way to sort shops by distance
          </span>
        </div>

        <p v-if="locationError" class="addr__error">{{ locationError }}</p>

        <div class="addr__actions">
          <button
            type="button"
            class="addr__save"
            :disabled="address.trim() === '' && lat === null"
            @click="save"
          >
            Save address
          </button>
          <button v-if="delivery.isSet.value" type="button" class="addr__clear" @click="clear">
            Clear
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.addr {
  position: fixed;
  inset: 0;
  z-index: 220;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.addr__scrim { position: absolute; inset: 0; background: rgba(6, 36, 15, 0.42); }

.addr__panel {
  position: relative;
  width: min(440px, 100%);
  padding: 30px 30px 26px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(6, 36, 15, 0.28);
}

.addr__x {
  position: absolute;
  top: 12px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #5b6b60;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}
.addr__x:hover { background: #f5f9f6; color: #06240f; }

.addr__title {
  margin: 0 0 6px;
  font-size: 1.35rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #06240f;
}
.addr__sub { margin: 0 0 20px; font-size: 14px; line-height: 1.55; color: #46564b; }

.addr__label {
  display: block;
  margin-bottom: 6px;
  font-size: 12.5px;
  font-weight: 700;
  color: #06240f;
}

.addr__input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #d6e2d9;
  border-radius: 10px;
  font-size: 14.5px;
  color: #06240f;
}
.addr__input:focus { outline: 2px solid #1a6b3c; outline-offset: 1px; border-color: transparent; }

.addr__pin {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.addr__locate {
  padding: 9px 16px;
  border: 1px solid #1a6b3c;
  border-radius: 999px;
  background: #fff;
  color: #1a6b3c;
  font-size: 13.5px;
  font-weight: 700;
  cursor: pointer;
}
.addr__locate:hover:not(:disabled) { background: #1a6b3c; color: #fff; }
.addr__locate:disabled { opacity: 0.6; cursor: default; }

.addr__pinned { font-size: 12.5px; font-weight: 700; color: #1a6b3c; }
.addr__hint { font-size: 12.5px; color: #6b7a70; }
.addr__error { margin: 10px 0 0; font-size: 12.5px; color: #b3261e; }

.addr__actions { display: flex; align-items: center; gap: 14px; margin-top: 22px; }

.addr__save {
  padding: 12px 26px;
  border: 0;
  border-radius: 999px;
  background: #bbf451;
  color: #06240f;
  font-size: 14.5px;
  font-weight: 800;
  cursor: pointer;
}
.addr__save:hover:not(:disabled) { background: #1a6b3c; color: #fff; }
.addr__save:disabled { opacity: 0.5; cursor: default; }

.addr__clear {
  border: 0;
  background: transparent;
  color: #5b6b60;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
}
.addr__clear:hover { color: #06240f; text-decoration: underline; }

@media (max-width: 520px) {
  .addr__panel { padding: 26px 20px 22px; }
}
</style>
