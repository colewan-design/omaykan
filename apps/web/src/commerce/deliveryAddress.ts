import { computed, ref } from 'vue'
import type { CustomerAddress } from '@pos/web/commerce/api'
import { findArea, matchArea, type ServiceArea } from '@pos/web/commerce/areas'

/*
 * Where the shopper wants their order delivered — the one answer the whole
 * storefront hangs off.
 *
 * The header asks for it, and the catalog is fetched around it: the API only
 * returns what a branch within delivery range can actually put in a box for
 * this address (see StorefrontCatalogController). So this is not a checkout
 * detail collected at the end; it is a filter set at the top, which is why it
 * lives beside the cart rather than inside a checkout form.
 *
 * Module-level and mirrored to localStorage, like the cart: the landing page,
 * the about page and the account portal are separate Vite entries, so moving
 * between them is a full page load and anything held only in a component
 * would be asked for again every time.
 */

export type DeliveryAddressSource = 'device' | 'area' | 'saved'

export interface DeliveryAddress {
  /** Street line, as typed. Optional — an area alone is enough to filter. */
  line1: string
  /** The picked service area, empty when the pin came from the device. */
  areaId: string
  areaName: string
  /**
   * Null when the address is written but unplaceable. The catalog is then
   * fetched unfiltered rather than guessing at a distance — see the panel,
   * which says as much.
   */
  lat: number | null
  lng: number | null
  source: DeliveryAddressSource
  /** Set when this came from the signed-in customer's address book. */
  savedAddressId?: string
}

const STORAGE_KEY = 'sf_delivery_address'

function loadPersisted(): DeliveryAddress | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return null

    const parsed = JSON.parse(raw) as DeliveryAddress
    if (!parsed || typeof parsed !== 'object') return null

    const lat = Number.isFinite(parsed.lat) ? Number(parsed.lat) : null
    const lng = Number.isFinite(parsed.lng) ? Number(parsed.lng) : null

    return {
      line1: typeof parsed.line1 === 'string' ? parsed.line1 : '',
      areaId: typeof parsed.areaId === 'string' ? parsed.areaId : '',
      areaName: typeof parsed.areaName === 'string' ? parsed.areaName : '',
      lat,
      lng,
      source: parsed.source === 'device' || parsed.source === 'saved' ? parsed.source : 'area',
      savedAddressId: parsed.savedAddressId,
    }
  } catch {
    return null
  }
}

const current = ref<DeliveryAddress | null>(loadPersisted())
const locating = ref(false)
const locationError = ref('')

function persist() {
  try {
    if (current.value === null) window.localStorage.removeItem(STORAGE_KEY)
    else window.localStorage.setItem(STORAGE_KEY, JSON.stringify(current.value))
  } catch {
    // Private mode / quota — the choice still holds for this page.
  }
}

function commit(address: DeliveryAddress) {
  current.value = address
  locationError.value = ''
  persist()
}

/** The one line the header shows once an address is set. */
function oneLine(address: DeliveryAddress): string {
  return [address.line1, address.areaName].filter(Boolean).join(', ') || 'Your location'
}

export function useDeliveryAddress() {
  const address = computed(() => current.value)
  const hasAddress = computed(() => current.value !== null)
  const label = computed(() => (current.value ? oneLine(current.value) : ''))

  /**
   * What gets sent to the catalog endpoint. Null covers both "no address yet"
   * and "an address we could not place", and both mean the same thing to the
   * API: don't filter.
   */
  const coords = computed(() => {
    const value = current.value
    if (!value || value.lat === null || value.lng === null) return null
    return { lat: value.lat, lng: value.lng }
  })

  function setArea(area: ServiceArea, line1 = '') {
    commit({
      line1: line1.trim(),
      areaId: area.id,
      areaName: area.name,
      lat: area.lat,
      lng: area.lng,
      source: 'area',
    })
  }

  /**
   * An address with no area picked and no pin. Kept rather than refused: the
   * shopper told us where they are and we simply can't place it, which is a
   * better state than an empty header that looks like nothing happened.
   */
  function setUnplaceable(line1: string) {
    commit({ line1: line1.trim(), areaId: '', areaName: '', lat: null, lng: null, source: 'area' })
  }

  /** The accurate path: the device's own pin, named by the nearest area. */
  function useMyLocation(): Promise<boolean> {
    if (locating.value) return Promise.resolve(false)

    if (!navigator.geolocation) {
      locationError.value = 'This browser cannot share a location. Pick your area instead.'
      return Promise.resolve(false)
    }

    locating.value = true
    locationError.value = ''

    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const { latitude, longitude } = position.coords
          commit({
            line1: '',
            areaId: '',
            areaName: 'Your current location',
            lat: latitude,
            lng: longitude,
            source: 'device',
          })
          locating.value = false
          resolve(true)
        },
        () => {
          locationError.value = "Couldn't get your location. Pick your area instead."
          locating.value = false
          resolve(false)
        },
        { enableHighAccuracy: true, timeout: 10000 },
      )
    })
  }

  /**
   * One of the signed-in customer's saved addresses.
   *
   * Those carry a pin only when something captured one — the account form
   * never asks — so a saved address is placed by reading its barangay and
   * street line against the area list, and left unplaced if neither says
   * anything recognisable.
   */
  function setFromSaved(saved: CustomerAddress) {
    const matched = saved.lat !== null && saved.lng !== null
      ? null
      : matchArea(saved.barangay, saved.line1, saved.label)

    commit({
      line1: [saved.line1, saved.barangay].filter(Boolean).join(', '),
      areaId: matched?.id ?? '',
      areaName: matched?.name ?? saved.city ?? '',
      lat: saved.lat ?? matched?.lat ?? null,
      lng: saved.lng ?? matched?.lng ?? null,
      source: 'saved',
      savedAddressId: saved.id,
    })
  }

  function clear() {
    current.value = null
    locationError.value = ''
    persist()
  }

  return {
    address,
    hasAddress,
    label,
    coords,
    locating: computed(() => locating.value),
    locationError: computed(() => locationError.value),
    area: computed(() => (current.value?.areaId ? findArea(current.value.areaId) : null)),
    setArea,
    setUnplaceable,
    useMyLocation,
    setFromSaved,
    clear,
  }
}
