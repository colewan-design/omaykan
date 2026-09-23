<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { MapPinOff, MapPin } from '@lucide/vue'
import { shareRiderPosition, stopSharingRiderPosition } from '@pos/web/rider/api'
import { messageFor } from '@pos/web/rider/rider'

/*
 * Putting the rider on the shop's dashboard and the customer's tracking page.
 *
 * The portal was the last place this could not be done: the API has taken a
 * coordinate since 2026-09 and the Android app has had the switch since it
 * shipped, but nothing in `apps/web/src/rider` ever called either endpoint, so
 * a rider working from a phone browser was invisible to the two people waiting
 * on them.
 *
 * **It is a switch, not a consequence of taking a job**, and the argument is
 * the app's, restated because it is the sort of thing that gets "simplified"
 * later: the tempting design starts sharing when a rider accepts something —
 * fewer taps, the map always works. It is also the design where a person finds
 * out their employer's app has been reporting their position all afternoon
 * because of a button they pressed for a different reason. So it starts off,
 * and the line under it makes the platform's case rather than assuming it.
 *
 * Three rules keep it cheap, taken from PositionReporter on Android:
 *
 *   1. **The server sets the cadence.** Every reply carries `nextPingSeconds` —
 *      10 while carrying, 60 while idle — and this timer obeys it rather than
 *      picking its own interval. The browser does not know how many deliveries
 *      the rider is holding; the server does.
 *   2. **A fix that says nothing new is not sent.** Under ~15m of movement is
 *      the same street corner, and a phone standing still emits those forever.
 *   3. **A fix worse than 200m is not sent at all.** That is a cell-tower
 *      guess, and drawing it would move a rider across town.
 *
 * Off means erased: `DELETE /api/rider/position` nulls the columns, so the
 * last fix does not sit in the row going stale. Off is also what happens when
 * this component unmounts — signing out, or closing the tab, stops the feed.
 *
 * Unlike the Android app there is no foreground service and no persistent
 * notification, because a browser tab has neither. That is a real difference
 * and it cuts the right way: a tab that is closed stops reporting, and the only
 * thing that can keep this running is a page the rider can see.
 */

const ACCURACY_FLOOR_M = 200
const MOVED_ENOUGH_M = 15

const sharing = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const lastFix = ref<{ lat: number; lng: number } | null>(null)

let timer: number | null = null

/** Metres between two coordinates. Equirectangular is plenty at city scale. */
function metresBetween(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const R = 6_371_000
  const toRad = (deg: number) => (deg * Math.PI) / 180
  const x = (toRad(b.lng) - toRad(a.lng)) * Math.cos((toRad(a.lat) + toRad(b.lat)) / 2)
  const y = toRad(b.lat) - toRad(a.lat)

  return Math.sqrt(x * x + y * y) * R
}

function currentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      // Below the 10s carrying cadence, so a cached fix is never older than
      // the ping it is answering.
      maximumAge: 8_000,
      timeout: 15_000,
    })
  })
}

function scheduleNext(seconds: number) {
  if (!sharing.value) return
  timer = window.setTimeout(() => void report(), Math.max(5, seconds) * 1000)
}

async function report() {
  if (!sharing.value) return

  try {
    const fix = await currentPosition()
    const { latitude, longitude, accuracy, heading, speed } = fix.coords

    // Rule 3, before rule 2: a wildly inaccurate fix should not become the
    // baseline that the next good one is compared against.
    if (typeof accuracy === 'number' && accuracy > ACCURACY_FLOOR_M) {
      scheduleNext(15)
      return
    }

    const here = { lat: latitude, lng: longitude }

    if (lastFix.value && metresBetween(lastFix.value, here) < MOVED_ENOUGH_M) {
      scheduleNext(15)
      return
    }

    const receipt = await shareRiderPosition({
      lat: latitude,
      lng: longitude,
      headingDeg: typeof heading === 'number' && !Number.isNaN(heading) ? heading : null,
      // The browser reports m/s; the API takes km/h.
      speedKph: typeof speed === 'number' && !Number.isNaN(speed) ? speed * 3.6 : null,
      accuracyM: typeof accuracy === 'number' ? accuracy : null,
    })

    lastFix.value = here
    errorMessage.value = ''
    scheduleNext(receipt.nextPingSeconds)
  } catch (error) {
    /*
     * A refused permission is the end of it — retrying would ask again every
     * few seconds, which is how a browser decides to block the prompt for good.
     * Anything else (a tunnel, a dropped request) is temporary, so the loop
     * keeps its place and tries again on the next tick.
     */
    if (error instanceof GeolocationPositionError && error.code === error.PERMISSION_DENIED) {
      sharing.value = false
      errorMessage.value = 'Your browser is blocking location for this site. Allow it in the address bar to share.'
      return
    }

    errorMessage.value = messageFor(error, 'Could not read your position just now.')
    scheduleNext(20)
  }
}

function clearTimer() {
  if (timer !== null) {
    window.clearTimeout(timer)
    timer = null
  }
}

async function toggle() {
  if (busy.value) return

  busy.value = true
  errorMessage.value = ''

  try {
    if (sharing.value) {
      sharing.value = false
      clearTimer()
      lastFix.value = null
      await stopSharingRiderPosition()
    } else {
      if (!('geolocation' in navigator)) {
        errorMessage.value = 'This browser cannot share a location.'
        return
      }

      sharing.value = true
      // Reported immediately rather than after one interval: a rider who turns
      // this on wants to be on the map now, not in a minute.
      await report()
    }
  } catch (error) {
    errorMessage.value = messageFor(error, 'Could not change location sharing.')
  } finally {
    busy.value = false
  }
}

/*
 * Closing the tab, or signing out, stops the feed — and tells the server so,
 * rather than leaving a fix behind to go stale on its own. Best-effort: if the
 * request does not make it out, the position goes stale in two minutes and the
 * customer's page says when it was last seen, which is the designed fallback.
 */
onBeforeUnmount(() => {
  clearTimer()
  if (sharing.value) void stopSharingRiderPosition().catch(() => undefined)
})
</script>

<template>
  <div class="rdr-card rdr-share">
    <div class="rdr-share__icon" :class="{ 'rdr-share__icon--on': sharing }">
      <MapPin v-if="sharing" :size="18" :stroke-width="1.8" />
      <MapPinOff v-else :size="18" :stroke-width="1.8" />
    </div>

    <div class="rdr-share__body">
      <p class="rdr-share__title">Share your location</p>
      <p class="rdr-share__note">
        The shop and the customer can see where you are, so they stop ringing to ask.
        Turning it off erases the last one.
      </p>
      <p v-if="errorMessage" class="rdr-share__error">{{ errorMessage }}</p>
    </div>

    <button
      class="rdr-switch"
      :class="{ 'rdr-switch--on': sharing }"
      type="button"
      role="switch"
      :aria-checked="sharing"
      aria-label="Share your location"
      :disabled="busy"
      @click="toggle"
    >
      <span class="rdr-switch__dot" />
    </button>
  </div>
</template>
