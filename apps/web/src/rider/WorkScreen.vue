<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Box, ChevronRight, MapPin, Route, Store } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import JobDetailScreen from '@pos/web/rider/JobDetailScreen.vue'
import LocationShare from '@pos/web/rider/LocationShare.vue'
import TripMap from '@pos/web/rider/TripMap.vue'
import {
  acceptDelivery,
  advanceDelivery,
  fetchBoard,
  fetchMyDeliveries,
  releaseDelivery,
  RiderApiError,
  type DeliveryAssignment,
  type DeliveryOffer,
} from '@pos/web/rider/api'
import { applyGateRejection, messageFor } from '@pos/web/rider/rider'

/*
 * An approved rider's actual job: what is going, what they are carrying, and
 * what they have finished.
 *
 * The board and the rider's own list live in one component rather than two,
 * because every action moves a job between them — accepting takes one off the
 * board and puts it in `active`, releasing does the reverse. Two components
 * would each hold half the truth and would have to tell each other every time,
 * which is how one of them ends up showing a job that is already gone.
 *
 * A claimed job opens into JobDetailScreen rather than unfolding in place. The
 * list is for choosing between jobs; the detail screen is for doing one, and it
 * is where the map and the navigate-and-call handoffs live. Same split the
 * Android app makes, and for the same reason: the moment this is read is
 * somebody stopped at a kerb looking for one address.
 */

const board = ref<DeliveryOffer[]>([])
const active = ref<DeliveryAssignment[]>([])
const completed = ref<DeliveryAssignment[]>([])

const tab = ref<'board' | 'mine'>('board')
const loading = ref(true)
const busyId = ref('')
const errorMessage = ref('')
const notice = ref('')

/**
 * The job being looked at, by id rather than by object.
 *
 * Holding the object would freeze it: every action replaces the row in
 * `active` with the server's answer, and a detail screen bound to the old copy
 * would still be offering "Picked it up" on a job that is already picked up.
 */
const openJobId = ref('')

const openJob = computed(() => active.value.find((job) => job.id === openJobId.value) ?? null)

const STAGE_LABEL: Record<string, string> = {
  assigned: 'Head to the shop',
  picked_up: 'On the way to the customer',
  delivered: 'Delivered',
}

function formatWhen(value: string | null): string {
  if (!value) return ''
  return new Intl.DateTimeFormat('en-PH', { timeStyle: 'short' }).format(new Date(value))
}

function distanceLabel(km: number | null): string | null {
  return km === null ? null : `${km.toFixed(1)} km`
}

/**
 * Every call goes through here so a suspension mid-shift is handled in one
 * place: the approval gate answers 403 with the new status, the session picks
 * it up, and the portal shell swaps to the status screen by itself rather than
 * leaving the rider staring at an error over a board they can no longer act on.
 */
function handle(error: unknown, fallback: string) {
  if (applyGateRejection(error)) return
  errorMessage.value = messageFor(error, fallback)
}

async function load(options: { quiet?: boolean } = {}) {
  if (!options.quiet) loading.value = true
  errorMessage.value = ''

  try {
    const [boardResult, mine] = await Promise.all([fetchBoard(), fetchMyDeliveries()])
    board.value = boardResult.orders ?? []
    active.value = mine.active ?? []
    completed.value = mine.completed ?? []
  } catch (error) {
    handle(error, 'Could not load your jobs.')
  } finally {
    loading.value = false
  }
}

async function take(offer: DeliveryOffer) {
  busyId.value = offer.id
  errorMessage.value = ''
  notice.value = ''

  try {
    const { order } = await acceptDelivery(offer.id)
    board.value = board.value.filter((row) => row.id !== offer.id)
    active.value = [...active.value, order]
    tab.value = 'mine'
    // Straight into the job. A rider who has just taken one is about to want
    // the address and the Navigate button, not a list with one row on it.
    openJobId.value = order.id
    notice.value = `${order.pickup.storeName} is yours. Head over and pick it up.`
  } catch (error) {
    // 409 is the normal race, not a fault: two riders tapped the same job.
    // The board is reloaded rather than just apologising, so the next tap is
    // against what is actually still there.
    if (error instanceof RiderApiError && error.status === 409) {
      notice.value = error.message
      void load({ quiet: true })
    } else {
      handle(error, 'Could not take that job.')
    }
  } finally {
    busyId.value = ''
  }
}

async function advance(job: DeliveryAssignment, stage: 'picked_up' | 'delivered') {
  busyId.value = job.id
  errorMessage.value = ''
  notice.value = ''

  try {
    const { order } = await advanceDelivery(job.id, stage)

    if (stage === 'delivered') {
      active.value = active.value.filter((row) => row.id !== job.id)
      completed.value = [order, ...completed.value]
      // The job is finished, so the screen for doing it closes itself rather
      // than sitting there with no button left on it.
      if (openJobId.value === job.id) openJobId.value = ''
      notice.value = 'Delivered. Nice one.'
    } else {
      active.value = active.value.map((row) => (row.id === job.id ? order : row))
    }
  } catch (error) {
    handle(error, 'Could not update that delivery.')
  } finally {
    busyId.value = ''
  }
}

async function giveBack(job: DeliveryAssignment) {
  busyId.value = job.id
  errorMessage.value = ''

  try {
    await releaseDelivery(job.id)
    active.value = active.value.filter((row) => row.id !== job.id)
    if (openJobId.value === job.id) openJobId.value = ''
    notice.value = 'Given back — it is on the board again.'
    void load({ quiet: true })
  } catch (error) {
    handle(error, 'Could not give that job back.')
  } finally {
    busyId.value = ''
  }
}

/** Earnings the rider keeps in full — the platform takes nothing from a fee. */
const earnedTodayCents = computed(() => {
  const today = new Date().toDateString()
  return completed.value
    .filter((job) => job.acceptedAt !== null && new Date(job.acceptedAt).toDateString() === today)
    .reduce((total, job) => total + job.deliveryFeeCents, 0)
})

onMounted(() => void load())
</script>

<template>
  <!-- One job, opened out of the list. -->
  <JobDetailScreen
    v-if="openJob"
    :job="openJob"
    :busy="busyId === openJob.id"
    @back="openJobId = ''"
    @advance="(stage) => advance(openJob!, stage)"
    @release="giveBack(openJob!)"
  />

  <div v-else>
    <!-- Above the tabs, because it is true of the whole shift rather than of
         either list, and because it is the control a rider is most likely to
         be looking for when they open the portal at the start of one. -->
    <LocationShare />

    <div class="rdr-tabs">
      <button
        class="rdr-tab"
        :class="{ 'rdr-tab--on': tab === 'board' }"
        type="button"
        @click="tab = 'board'"
      >
        Job board<span v-if="board.length" class="rdr-tab__count">{{ board.length }}</span>
      </button>
      <button
        class="rdr-tab"
        :class="{ 'rdr-tab--on': tab === 'mine' }"
        type="button"
        @click="tab = 'mine'"
      >
        My jobs<span v-if="active.length" class="rdr-tab__count">{{ active.length }}</span>
      </button>
    </div>

    <p v-if="errorMessage" class="rdr-flash rdr-flash--error">{{ errorMessage }}</p>
    <p v-if="notice" class="rdr-flash rdr-flash--ok">{{ notice }}</p>

    <p v-if="loading" class="rdr-sub">Loading…</p>

    <!-- ── The board ────────────────────────────────────────────────── -->
    <template v-else-if="tab === 'board'">
      <div v-if="board.length === 0" class="rdr-empty">
        <p class="rdr-empty__title">Nothing waiting right now</p>
        <p class="rdr-empty__note">
          New deliveries land here the moment a shop takes an order. Pull up again in a bit.
        </p>
      </div>

      <article v-for="offer in board" :key="offer.id" class="rdr-card rdr-job">
        <!-- The shop only. `asOffer` withholds the drop-off coordinates until a
             job is claimed, and drawing a guessed door would put a
             precise-looking answer exactly where the server chose to be vague. -->
        <TripMap :pickup-lat="offer.pickup.lat" :pickup-lng="offer.pickup.lng" />

        <div class="rdr-job__top">
          <div>
            <p class="rdr-job__shop">{{ offer.pickup.storeName }}</p>
            <p class="rdr-job__where">
              <span v-if="offer.pickup.address">{{ offer.pickup.address }}</span>
              <span v-else>Pickup address from the shop</span>
              <template v-if="offer.dropoffArea"> → {{ offer.dropoffArea }}</template>
            </p>
          </div>
          <div class="rdr-job__fee">
            <span class="rdr-job__fee-value">{{ formatCurrency(offer.deliveryFeeCents) }}</span>
            <span class="rdr-job__fee-label">Yours</span>
          </div>
        </div>

        <div class="rdr-job__meta">
          <span v-if="distanceLabel(offer.distanceKm)" class="rdr-chip">
            <Route :size="14" :stroke-width="1.8" />{{ distanceLabel(offer.distanceKm) }}
          </span>
          <span class="rdr-chip">
            <Box :size="14" :stroke-width="1.8" />{{ offer.itemCount }} item{{ offer.itemCount === 1 ? '' : 's' }}
          </span>
          <span v-if="offer.placedAt" class="rdr-chip">Ordered {{ formatWhen(offer.placedAt) }}</span>
          <!-- Cash to collect changes whether a rider wants the job at all, so
               it sits on the card rather than appearing after they take it. -->
          <span v-if="offer.collectCents > 0" class="rdr-chip rdr-chip--collect">
            Collect {{ formatCurrency(offer.collectCents) }} cash
          </span>
          <span v-else class="rdr-chip">Already paid</span>
        </div>

        <div class="rdr-actions">
          <button
            class="rdr-btn"
            type="button"
            :disabled="busyId === offer.id"
            @click="take(offer)"
          >
            {{ busyId === offer.id ? 'Taking…' : 'Take this job' }}
          </button>
        </div>
      </article>
    </template>

    <!-- ── The rider's own jobs ─────────────────────────────────────── -->
    <template v-else>
      <div v-if="active.length === 0" class="rdr-empty">
        <p class="rdr-empty__title">You are not carrying anything</p>
        <p class="rdr-empty__note">Take a job from the board and it will show up here.</p>
      </div>

      <!-- A row, not a card full of controls: everything that acts on a job
           now lives on the job's own screen, one tap away. -->
      <button
        v-for="job in active"
        :key="job.id"
        class="rdr-card rdr-job rdr-jobrow"
        type="button"
        @click="openJobId = job.id"
      >
        <div class="rdr-jobrow__body">
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

          <p class="rdr-job__line">
            <MapPin :size="13" :stroke-width="1.8" />
            {{ job.deliveryAddress || job.dropoffArea || 'address from the shop' }}
          </p>
        </div>

        <ChevronRight class="rdr-jobrow__chevron" :size="20" :stroke-width="1.8" />
      </button>

      <template v-if="completed.length">
        <p class="rdr-section-title">
          Finished
          <template v-if="earnedTodayCents > 0"> · {{ formatCurrency(earnedTodayCents) }} today</template>
        </p>

        <article v-for="job in completed" :key="job.id" class="rdr-card rdr-job">
          <div class="rdr-job__top">
            <div>
              <p class="rdr-job__shop">{{ job.pickup.storeName }}</p>
              <p class="rdr-job__where">{{ job.dropoffArea || job.deliveryAddress }}</p>
            </div>
            <div class="rdr-job__fee">
              <span class="rdr-job__fee-value">{{ formatCurrency(job.deliveryFeeCents) }}</span>
              <span class="rdr-job__fee-label">Earned</span>
            </div>
          </div>
        </article>
      </template>
    </template>

    <div class="rdr-actions">
      <button class="rdr-btn rdr-btn--ghost" type="button" :disabled="loading" @click="load()">
        {{ loading ? 'Refreshing…' : 'Refresh' }}
      </button>
    </div>
  </div>
</template>
