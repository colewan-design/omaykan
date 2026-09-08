<script setup lang="ts">
import { computed } from 'vue'

/*
 * The picture at the top of a job.
 *
 * A Mapbox *static image*, not a map — the same decision `:rider` on Android
 * wrote down in core/map/StaticMap.kt, and it holds harder in a browser. A
 * rider deciding whether to take a job is not panning, zooming or rotating;
 * they are asking "where is this, and is it far". One `<img>` answers that in
 * one request, costs nothing to load on a phone, and cannot be the reason the
 * board fails to render.
 *
 * **Pins only, never a line between them.** A straight line from shop to door
 * is not the road, and drawing one puts a precise-looking answer on screen in
 * exactly the place the platform has chosen to be vague — the board withholds
 * the drop-off coordinates until a job is claimed, so half the cards would be
 * drawing a line to a point nobody has been given. The Android app draws a real
 * route because it asks the Directions API for one; until the portal does the
 * same, no line is the honest picture.
 *
 * A blank token is a supported state, not a broken one: the component renders
 * nothing at all, and every card is the card that shipped before there was a
 * map. Same rule as the app's.
 */

const props = defineProps<{
  pickupLat: number | null
  pickupLng: number | null
  dropoffLat?: number | null
  dropoffLng?: number | null
  /** Taller for a job's own screen than for a row on the board. */
  height?: number
}>()

const TOKEN = (import.meta.env.VITE_MAPBOX_TOKEN ?? '') as string

/** Retina, because the alternative on a phone is a blurred street name. */
const DENSITY = '@2x'

const STYLE = 'mapbox/streets-v12'

/** The brand green for the shop, the danger red for the door. */
const PICKUP_PIN = 'pin-s-shop+1a6b3c'
const DROPOFF_PIN = 'pin-s-embassy+b42318'

function hasPoint(lat: number | null | undefined, lng: number | null | undefined): boolean {
  return typeof lat === 'number' && typeof lng === 'number' && Number.isFinite(lat) && Number.isFinite(lng)
}

const height = computed(() => props.height ?? 132)

const src = computed(() => {
  if (!TOKEN) return null
  if (!hasPoint(props.pickupLat, props.pickupLng)) return null

  const overlays = [`${PICKUP_PIN}(${props.pickupLng},${props.pickupLat})`]

  if (hasPoint(props.dropoffLat, props.dropoffLng)) {
    overlays.push(`${DROPOFF_PIN}(${props.dropoffLng},${props.dropoffLat})`)
  }

  /*
   * `auto` frames whatever is on the image, so one pin gets a sensible
   * neighbourhood zoom and two get both ends with padding — without this
   * component having to work out a bounding box or a zoom level, which is
   * arithmetic Mapbox has already done and would be one more thing to get
   * wrong at the equator.
   */
  const viewport = overlays.length > 1 ? 'auto' : `${props.pickupLng},${props.pickupLat},14`

  // 640 is the widest single-scale tile Mapbox will render; @2x doubles it.
  return (
    `https://api.mapbox.com/styles/v1/${STYLE}/static/` +
    `${overlays.join(',')}/${viewport}/640x${Math.round(height.value * 2)}${DENSITY}` +
    `?access_token=${encodeURIComponent(TOKEN)}&attribution=false&logo=false`
  )
})

const alt = computed(() =>
  hasPoint(props.dropoffLat, props.dropoffLng)
    ? 'Map showing the shop and the delivery address'
    : 'Map showing where the shop is',
)
</script>

<template>
  <!-- No wrapper when there is no image: an empty grey box is worse than
       nothing, because it reads as a picture that failed to load. -->
  <img
    v-if="src"
    class="rdr-map"
    :src="src"
    :alt="alt"
    :style="{ height: `${height}px` }"
    loading="lazy"
    decoding="async"
  >
</template>
