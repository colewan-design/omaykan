<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Box, MapPin, Route, Store } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
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
 */

const board = ref<DeliveryOffer[]>([])
const active = ref<DeliveryAssignment[]>([])
const completed = ref<DeliveryAssignment[]>([])

const tab = ref<'board' | 'mine'>('board')
const loading = ref(true)
const busyId = ref('')
const errorMessage = ref('')
const notice = ref('')

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
 * leaving the rider staring at an error over a board they can no longer use.
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
  <div>
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

      <article v-for="job in active" :key="job.id" class="rdr-card rdr-job">
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
          Deliver to <strong>{{ job.deliveryAddress || job.dropoffArea || 'address from the shop' }}</strong>
          <template v-if="job.customerName"><br>{{ job.customerName }}</template>
          <template v-if="job.customerPhone">
            <br>
            <a class="rdr-call" :href="`tel:${job.customerPhone}`">Call {{ job.customerPhone }}</a>
          </template>
        </p>

        <p v-if="job.collectCents > 0" class="rdr-job__line">
          Collect <strong>{{ formatCurrency(job.collectCents) }}</strong> in cash at the door.
        </p>

        <ul v-if="job.items.length" class="rdr-job__items">
          <li v-for="item in job.items" :key="item.name">{{ item.quantity }} × {{ item.name }}</li>
        </ul>

        <div class="rdr-actions">
          <button
            v-if="job.deliveryStage === 'assigned'"
            class="rdr-btn"
            type="button"
            :disabled="busyId === job.id"
            @click="advance(job, 'picked_up')"
          >
            Picked it up
          </button>
          <button
            v-else-if="job.deliveryStage === 'picked_up'"
            class="rdr-btn"
            type="button"
            :disabled="busyId === job.id"
            @click="advance(job, 'delivered')"
          >
            Delivered
          </button>

          <!-- Only before pickup. Once the food is in the bag, handing it back
               is a phone call to the shop, not a button. -->
          <button
            v-if="job.deliveryStage === 'assigned'"
            class="rdr-btn rdr-btn--danger"
            type="button"
            :disabled="busyId === job.id"
            @click="giveBack(job)"
          >
            Give back
          </button>
        </div>
      </article>

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
