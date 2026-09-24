<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft, Box, MapPin, Navigation, Phone, Store } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import TripMap from '@pos/web/rider/TripMap.vue'
import type { DeliveryAssignment } from '@pos/web/rider/api'

/*
 * One job, on its own screen.
 *
 * The portal used to put everything on the list: shop, address, customer,
 * basket and three buttons, stacked inside a card, repeated for every job the
 * rider was carrying. That is readable at one job and unusable at three — and
 * it is the wrong shape for the moment it is actually used in, which is a
 * person stopped at a kerb with one hand on the bike looking for one address.
 *
 * So the list stays a list — enough to choose between jobs — and this is the
 * screen for the job that has been chosen. It is the counterpart of
 * JobDetailScreen.kt in the Android app, and carries the two things the portal
 * had no home for: the picture of where the trip goes, and the handoff into
 * whatever map and dialler the rider already uses.
 */

const props = defineProps<{
  job: DeliveryAssignment
  busy: boolean
}>()

const emit = defineEmits<{
  back: []
  advance: [stage: 'picked_up' | 'delivered']
  release: []
}>()

const STAGE_LABEL: Record<string, string> = {
  assigned: 'Head to the shop',
  picked_up: 'On the way to the customer',
  delivered: 'Delivered',
}

/**
 * Where the rider is going *now* — the shop before pickup, the door after.
 *
 * One Navigate button that follows the job rather than two the rider has to
 * choose between at a kerb. Getting this backwards sends someone the wrong way
 * down a mountain, so it keys off `deliveryStage` and nothing else.
 */
const heading = computed(() => {
  if (props.job.deliveryStage === 'assigned') {
    return {
      label: `Navigate to ${props.job.pickup.storeName}`,
      lat: props.job.pickup.lat,
      lng: props.job.pickup.lng,
      address: props.job.pickup.address,
    }
  }

  return {
    label: 'Navigate to the customer',
    lat: props.job.deliveryLat,
    lng: props.job.deliveryLng,
    address: props.job.deliveryAddress,
  }
})

/**
 * A Google Maps URL rather than a `geo:` link.
 *
 * `geo:` is the right answer in a native app and the wrong one here: a desktop
 * browser does nothing at all with it, so the button would silently fail for
 * anybody dispatching from a laptop. This link opens the Maps app on Android,
 * Apple's handler on iOS, and a web map everywhere else — and Maps is a
 * destination the rider can share out to Waze from, which a dead link is not.
 *
 * Coordinates when there are any, the typed address when there are not: a shop
 * that never set a pin still has a street name, and searching for it beats
 * offering nothing.
 */
const navigateUrl = computed(() => {
  const { lat, lng, address } = heading.value
  const destination =
    typeof lat === 'number' && typeof lng === 'number' ? `${lat},${lng}` : (address ?? '')

  if (!destination) return null

  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(destination)}`
})

const itemCount = computed(() =>
  props.job.items.reduce((total, item) => total + item.quantity, 0),
)
</script>

<template>
  <div>
    <button class="rdr-back" type="button" @click="emit('back')">
      <ArrowLeft :size="16" :stroke-width="2" />
      All jobs
    </button>

    <div class="rdr-card rdr-job">
      <!-- Inside the card, because `.rdr-map` bleeds to its edges with negative
           margins that only cancel out against the card's own padding.
           Both ends once the job is claimed: the drop-off coordinates are
           withheld on the board and present here, which is the whole difference
           between an offer and an assignment. -->
      <TripMap
        :pickup-lat="job.pickup.lat"
        :pickup-lng="job.pickup.lng"
        :dropoff-lat="job.deliveryLat"
        :dropoff-lng="job.deliveryLng"
        :height="190"
      />

      <div class="rdr-job__top">
        <div>
          <p class="rdr-job__shop">{{ job.pickup.storeName }}</p>
          <p class="rdr-job__where">
            <Store :size="13" :stroke-width="1.8" />
            {{ job.pickup.address || 'Ask at the counter' }}
          </p>
        </div>
        <div class="rdr-job__fee">
          <span class="rdr-job__fee-value">{{ formatCurrency(job.deliveryFeeCents) }}</span>
          <span class="rdr-job__fee-label">Yours</span>
        </div>
      </div>

      <div class="rdr-steps">
        <span class="rdr-step rdr-step--on" />
        <span class="rdr-step" :class="{ 'rdr-step--on': job.deliveryStage !== 'assigned' }" />
        <span class="rdr-step" :class="{ 'rdr-step--on': job.deliveryStage === 'delivered' }" />
      </div>
      <p class="rdr-stage">{{ STAGE_LABEL[job.deliveryStage] ?? job.deliveryStage }}</p>

      <!-- The two handoffs, side by side and thumb-sized. Navigate first: it is
           the one that is needed while moving. -->
      <div class="rdr-handoff">
        <a
          v-if="navigateUrl"
          class="rdr-btn rdr-btn--ghost rdr-handoff__item"
          :href="navigateUrl"
          target="_blank"
          rel="noopener"
        >
          <Navigation :size="16" :stroke-width="1.8" />
          {{ heading.label }}
        </a>

        <a
          v-if="job.customerPhone"
          class="rdr-btn rdr-btn--ghost rdr-handoff__item"
          :href="`tel:${job.customerPhone}`"
        >
          <Phone :size="16" :stroke-width="1.8" />
          Call {{ job.customerName || 'the customer' }}
        </a>
      </div>

      <p class="rdr-job__line">
        <MapPin :size="13" :stroke-width="1.8" />
        Deliver to
        <strong>{{ job.deliveryAddress || job.dropoffArea || 'address from the shop' }}</strong>
        <!-- Under the address, not merged into it: the rider reads the address
             to get to the street and this to find the door. -->
        <template v-if="job.deliveryLandmark"><br>{{ job.deliveryLandmark }}</template>
        <template v-if="job.customerName"><br>{{ job.customerName }}</template>
      </p>

      <p v-if="job.collectCents > 0" class="rdr-job__line rdr-job__line--collect">
        Collect <strong>{{ formatCurrency(job.collectCents) }}</strong> in cash at the door.
      </p>
      <p v-else class="rdr-job__line">Already paid — nothing to collect.</p>

      <!-- The bag, to check against. Counted in the heading because a rider
           standing at a counter is verifying a number before a list. -->
      <template v-if="job.items.length">
        <p class="rdr-section-title">
          <Box :size="14" :stroke-width="1.8" />
          {{ itemCount }} item{{ itemCount === 1 ? '' : 's' }} to collect
        </p>
        <ul class="rdr-job__items">
          <li v-for="item in job.items" :key="item.name">{{ item.quantity }} × {{ item.name }}</li>
        </ul>
      </template>

      <div class="rdr-actions">
        <button
          v-if="job.deliveryStage === 'assigned'"
          class="rdr-btn rdr-btn--block"
          type="button"
          :disabled="busy"
          @click="emit('advance', 'picked_up')"
        >
          Picked it up
        </button>
        <button
          v-else-if="job.deliveryStage === 'picked_up'"
          class="rdr-btn rdr-btn--block"
          type="button"
          :disabled="busy"
          @click="emit('advance', 'delivered')"
        >
          Delivered
        </button>

        <!-- Only before pickup. Once the food is in the bag, handing it back is
             a phone call to the shop, not a button. -->
        <button
          v-if="job.deliveryStage === 'assigned'"
          class="rdr-btn rdr-btn--danger rdr-btn--block"
          type="button"
          :disabled="busy"
          @click="emit('release')"
        >
          Give back
        </button>
      </div>
    </div>
  </div>
</template>
