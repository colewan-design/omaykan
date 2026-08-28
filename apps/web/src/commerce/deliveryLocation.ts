import { computed, ref, type ComputedRef, type Ref } from 'vue'

/**
 * Where the visitor wants things delivered, set from the header before they
 * have picked a shop or signed in.
 *
 * Distinct from the addresses under /api/customer/addresses: those belong to
 * an account and are chosen at checkout. This is the anonymous, pre-account
 * answer to "where are you?", and its job is to order the shop directory by
 * distance. It stays in localStorage because there is no one to store it
 * against yet.
 *
 * The pin is optional and comes from the browser, not from the typed text.
 * There is no geocoder wired into this project, so an address someone types
 * cannot be turned into coordinates — the text is what a rider reads, and the
 * pin is what distance is measured from. Either can exist without the other.
 */

const STORAGE_KEY = 'sf_delivery_location'

export interface DeliveryLocation {
  address: string
  lat: number | null
  lng: number | null
}

const EMPTY: DeliveryLocation = { address: '', lat: null, lng: null }

function read(): DeliveryLocation {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw === null) return { ...EMPTY }

    const parsed = JSON.parse(raw) as Partial<DeliveryLocation>
    return {
      address: typeof parsed.address === 'string' ? parsed.address : '',
      lat: typeof parsed.lat === 'number' ? parsed.lat : null,
      lng: typeof parsed.lng === 'number' ? parsed.lng : null,
    }
  } catch {
    // Unreadable or unparseable — a corrupt entry must not take the page
    // down over something this peripheral.
    return { ...EMPTY }
  }
}

const location: Ref<DeliveryLocation> = ref(read())

function persist(): void {
  try {
    if (location.value.address === '' && location.value.lat === null) {
      window.localStorage.removeItem(STORAGE_KEY)
      return
    }
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(location.value))
  } catch {
    /* the value still works for this session */
  }
}

export function useDeliveryLocation(): {
  location: Ref<DeliveryLocation>
  isSet: ComputedRef<boolean>
  summary: ComputedRef<string>
  set: (next: Partial<DeliveryLocation>) => void
  clear: () => void
} {
  return {
    location,
    isSet: computed(() => location.value.address !== '' || location.value.lat !== null),
    summary: computed(() => {
      if (location.value.address !== '') return location.value.address
      // A pin with no text is still an answer, and saying so beats showing
      // the raw coordinates to a shopper.
      if (location.value.lat !== null) return 'Using your location'
      return ''
    }),
    set(next: Partial<DeliveryLocation>) {
      location.value = { ...location.value, ...next }
      persist()
    },
    clear() {
      location.value = { ...EMPTY }
      persist()
    },
  }
}
