<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Phone, RefreshCw, Search, Star } from '@lucide/vue'
import {
  deliveryStageLabel,
  formatCompactDate,
  formatCurrency,
  orderStatusLabel,
  type DeliveryStage,
  type OrderStatus,
} from '@pos/shared/index'
import LiveDeliveryMap from '@pos/core/components/LiveDeliveryMap.vue'
import { realtimeAvailable, subscribeToOrder } from '@pos/core/realtime/publicOrderChannel'
import {
  ApiRequestError,
  fetchCustomerOrders,
  fetchOrder,
  rateRider,
  resolveImageUrl,
  type RiderPosition,
  type TrackedOrder,
} from '@pos/web/commerce/api'
import { useStorefrontOrderHistory } from '@pos/web/commerce/orderHistory'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { MESSAGING_ENABLED, RIDER_RATING_ENABLED } from '@pos/web/commerce/features'

// Past orders, and where the current ones have got to.
//
// The ids come off this device (commerce/orderHistory.ts), joined by the
// account's own orders from GET /api/customer/orders — a guest order placed
// before signing in has no account to list it under. Status is never read
// from that local copy: it is fetched live per order, so an order that moved on
// while the tab was closed doesn't show yesterday's stage.
//
// The lookup box is what makes the list portable. Someone who ordered on their
// phone can paste the id here and the order joins this browser's list too.

const history = useStorefrontOrderHistory()
const account = useCustomerAccount()

/** Live status per order id. Absent while loading, null when the fetch failed. */
const orders = ref<Record<string, TrackedOrder | null>>({})
const refreshing = ref(false)

/**
 * Ratings the customer has left in this session, by order id.
 *
 * Local rather than fetched: the API deliberately has no "did I rate this"
 * endpoint, and does not need one — the server refuses a second rating for an
 * order outright, so the only thing this has to do is stop the form reappearing
 * under somebody who just used it. On a reload the form comes back and the 422
 * is what says no, which is one round trip on a rare path rather than a field
 * on every order payload.
 */
const rated = ref<Record<string, number>>({})
const ratingBusy = ref<Record<string, boolean>>({})
const ratingError = ref<Record<string, string>>({})

/** Whether this order is in the window where a rating is possible. */
function canRate(order: TrackedOrder): boolean {
  return (
    RIDER_RATING_ENABLED &&
    order.fulfillmentMethod === 'delivery' &&
    order.deliveryStage === 'delivered' &&
    // A rider the shop typed in has no account to attach a score to.
    !!order.riderName &&
    rated.value[order.orderId] === undefined
  )
}

async function submitRating(order: TrackedOrder, score: number) {
  const id = order.orderId
  if (ratingBusy.value[id]) return

  ratingBusy.value = { ...ratingBusy.value, [id]: true }
  ratingError.value = { ...ratingError.value, [id]: '' }

  try {
    await rateRider(id, score)
    rated.value = { ...rated.value, [id]: score }
  } catch (error) {
    ratingError.value = {
      ...ratingError.value,
      [id]:
        error instanceof ApiRequestError
          ? error.message
          : 'Could not save that rating.',
    }
  } finally {
    ratingBusy.value = { ...ratingBusy.value, [id]: false }
  }
}

const lookupId = ref('')
const lookupError = ref('')
const lookingUp = ref(false)

async function refresh() {
  refreshing.value = true

  // Signed in, the server also knows the orders placed on this account from
  // any other device. They join this browser's list, so an order the
  // dashboard shows is always here when "View details" opens it.
  const fromServer = new Set<string>()
  if (account.signedIn.value) {
    try {
      for (const order of (await fetchCustomerOrders()).orders) {
        orders.value[order.orderId] = order
        fromServer.add(order.orderId)
        history.remember({
          orderId: order.orderId,
          ticketNumber: order.ticketNumber,
          totalCents: order.totalCents,
          placedAt: order.placedAt,
        })
      }
    } catch {
      // Fall back to fetching each remembered order on its own, below.
    }
  }

  await Promise.all(
    history.entries.value.filter((entry) => !fromServer.has(entry.orderId)).map(async (entry) => {
      try {
        orders.value[entry.orderId] = await fetchOrder(entry.orderId)
      } catch {
        // Offline, or the order is gone. The remembered line still shows.
        orders.value[entry.orderId] = null
      }
    }),
  )
  refreshing.value = false
}

// The dashboard opens this section with ?order= naming the one it was asked
// about; bring that card into view once the list is in.
onMounted(async () => {
  await refresh()
  const wanted = new URLSearchParams(window.location.search).get('order')
  if (wanted) {
    await nextTick()
    document.getElementById(`acct-order-${wanted}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
})

async function lookup() {
  const id = lookupId.value.trim()
  if (!id) return
  lookingUp.value = true
  lookupError.value = ''
  try {
    const order = await fetchOrder(id)
    orders.value[order.orderId] = order
    history.remember({
      orderId: order.orderId,
      ticketNumber: order.ticketNumber,
      totalCents: order.totalCents,
      placedAt: order.placedAt,
    })
    lookupId.value = ''
  } catch (error) {
    lookupError.value =
      error instanceof ApiRequestError && error.status === 404
        ? "We couldn't find an order with that number."
        : 'Could not check that order right now. Try again in a moment.'
  } finally {
    lookingUp.value = false
  }
}

/**
 * A delivery order's stage is the honest answer to "where is it" — the kitchen
 * status stops being interesting once a rider has it. Pickup orders have no
 * stage, so they fall back to the kitchen status.
 */
function statusLine(order: TrackedOrder): string {
  if (order.fulfillmentMethod === 'delivery' && order.deliveryStage) {
    return deliveryStageLabel(order.deliveryStage as DeliveryStage)
  }
  return orderStatusLabel(order.status as OrderStatus)
}

function isDone(order: TrackedOrder): boolean {
  return order.deliveryStage === 'delivered' || order.status === 'served'
}

// ── Watching an order move ──────────────────────────────────────────────────
//
// Until now this page fetched each order once on mount and then sat still,
// which is fine for a receipt and useless for a delivery: the interesting part
// of a delivery all happens in the twenty minutes after you open the page.
//
// So orders still on the road get watched. Reverb where the build has it — the
// backend has always broadcast to the public `order.{uuid}` channel and nothing
// on the web ever listened — and a poll where it does not, because a storefront
// served without a websocket should still show a moving rider, just less often.

/** Delivery orders that have not yet been handed over. */
const liveOrders = computed(() =>
  Object.values(orders.value).filter(
    (order): order is TrackedOrder =>
      !!order && order.fulfillmentMethod === 'delivery' && order.deliveryStage !== 'delivered',
  ),
)

/** How near the rider is to the door, for a map worth drawing. */
function mapWorthShowing(order: TrackedOrder): boolean {
  if (order.fulfillmentMethod !== 'delivery') return false
  // A delivered order's map is a picture of somewhere the rider no longer is.
  if (order.deliveryStage === 'delivered') return false
  return !!order.riderPosition || !!order.route?.dropoff?.lat || !!order.route?.pickup?.lat
}

const subscriptions = new Map<string, () => void>()
let pollTimer: ReturnType<typeof setInterval> | null = null

/**
 * A position ping patches the one field it carries.
 *
 * Refetching the whole order ten times a minute would be a request per rider
 * per customer for two floats, and would make the card flicker as items and
 * totals were replaced with identical copies of themselves.
 */
function applyPosition(orderId: string, payload: unknown) {
  const order = orders.value[orderId]
  if (!order) return

  const fix = payload as Partial<RiderPosition> & { deliveryStage?: string }
  if (typeof fix?.lat !== 'number' || typeof fix?.lng !== 'number') return

  orders.value[orderId] = {
    ...order,
    deliveryStage: fix.deliveryStage ?? order.deliveryStage,
    riderPosition: {
      lat: fix.lat,
      lng: fix.lng,
      headingDeg: fix.headingDeg ?? null,
      speedKph: fix.speedKph ?? null,
      accuracyM: fix.accuracyM ?? null,
      at: fix.at ?? new Date().toISOString(),
      ageSeconds: fix.ageSeconds ?? 0,
      stale: fix.stale ?? false,
    },
  }
}

async function refetch(orderId: string) {
  try {
    orders.value[orderId] = await fetchOrder(orderId)
  } catch {
    // Leave the last good copy on screen rather than blanking the card.
  }
}

function syncSubscriptions() {
  const wanted = new Set(liveOrders.value.map((order) => order.orderId))

  for (const [orderId, stop] of subscriptions) {
    // Delivered, or removed from this browser's list. Stop paying for it.
    if (!wanted.has(orderId)) {
      stop()
      subscriptions.delete(orderId)
    }
  }

  if (!realtimeAvailable()) return

  for (const orderId of wanted) {
    if (subscriptions.has(orderId)) continue
    subscriptions.set(
      orderId,
      subscribeToOrder({
        orderId,
        onEvent: (event, payload) => {
          if (event === 'rider.position') {
            applyPosition(orderId, payload)
            return
          }
          // A stage change moves more than one field — the rider's name and
          // number appear and disappear with it — so this one is worth a fetch.
          void refetch(orderId)
        },
      }),
    )
  }
}

watch(liveOrders, syncSubscriptions, { deep: false })

onMounted(() => {
  // The fallback for a build with no websocket. Twelve seconds is slower than
  // the rider's ping and fast enough that the marker still reads as moving.
  if (!realtimeAvailable()) {
    pollTimer = setInterval(() => {
      for (const order of liveOrders.value) void refetch(order.orderId)
    }, 12_000)
  }
})

onBeforeUnmount(() => {
  for (const stop of subscriptions.values()) stop()
  subscriptions.clear()
  if (pollTimer) clearInterval(pollTimer)
})

function telHref(phone: string): string {
  return `tel:${phone.replace(/[^\d+]/g, '')}`
}
</script>

<template>
  <div>
    <div class="acct-head">
      <h1 class="acct-head__title">Order history</h1>
      <p class="acct-head__sub">
        Your account's orders, and any placed from this browser, with where each one has got to
        right now.
      </p>
    </div>

    <form class="acct-card acct-lookup" @submit.prevent="lookup">
      <label class="acct-field" style="margin: 0; flex: 1; min-width: 220px">
        <span class="acct-field__label">Track an order by number</span>
        <input
          v-model="lookupId"
          class="acct-input"
          type="text"
          placeholder="Paste the order number from your confirmation"
        />
      </label>
      <button type="submit" class="acct-btn" :disabled="lookingUp || !lookupId.trim()">
        <Search :size="16" :stroke-width="2" />
        {{ lookingUp ? 'Checking…' : 'Track' }}
      </button>
    </form>
    <p v-if="lookupError" class="acct-flash acct-flash--error">{{ lookupError }}</p>

    <div v-if="history.entries.value.length === 0" class="acct-empty" style="margin-top: 16px">
      <p class="acct-empty__title">No orders yet</p>
      <p class="acct-empty__note">
        Orders you place show up here automatically. If you ordered on another device, paste its
        number above and it'll join this list.
      </p>
    </div>

    <div v-else class="acct-orders">
      <div class="acct-orders__bar">
        <span>{{ history.entries.value.length }} order{{ history.entries.value.length === 1 ? '' : 's' }}</span>
        <button type="button" class="acct-link" :disabled="refreshing" @click="refresh">
          <RefreshCw :size="13" :stroke-width="2" :class="{ 'acct-spin': refreshing }" />
          {{ refreshing ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>

      <article
        v-for="entry in history.entries.value"
        :id="`acct-order-${entry.orderId}`"
        :key="entry.orderId"
        class="acct-card acct-order"
      >
        <div class="acct-order__top">
          <div>
            <p class="acct-card__title">Order #{{ entry.ticketNumber }}</p>
            <p class="acct-card__note">{{ formatCompactDate(entry.placedAt) }}</p>
          </div>
          <p class="acct-order__total">
            {{ formatCurrency(orders[entry.orderId]?.totalCents ?? entry.totalCents) }}
          </p>
        </div>

        <p
          v-if="orders[entry.orderId]"
          class="acct-order__status"
          :class="{ 'acct-order__status--done': isDone(orders[entry.orderId]!) }"
        >
          {{ statusLine(orders[entry.orderId]!) }}
          <span v-if="orders[entry.orderId]!.riderName" class="acct-order__rider">
            · {{ orders[entry.orderId]!.riderName }}
          </span>
        </p>
        <p v-else-if="orders[entry.orderId] === null" class="acct-order__status acct-order__status--stale">
          Status unavailable right now
        </p>

        <!--
          Who is bringing it.

          Above the map, because "a man in a red Honda Click" is what a customer
          at a window actually matches against, and the map only says where he
          is. The whole block disappears the moment the order is delivered —
          that is the server withholding it, not a v-if here, and it is why a
          forwarded tracking link does not carry somebody's face forever.
        -->
        <div
          v-if="orders[entry.orderId]?.riderProfile"
          class="acct-rider"
        >
          <img
            v-if="orders[entry.orderId]!.riderProfile!.photoUrl"
            class="acct-rider__photo"
            :src="resolveImageUrl(orders[entry.orderId]!.riderProfile!.photoUrl)!"
            :alt="`Photo of ${orders[entry.orderId]!.riderProfile!.name}`"
            loading="lazy"
          />
          <span v-else class="acct-rider__photo acct-rider__photo--letter">
            {{ orders[entry.orderId]!.riderProfile!.name.trim().charAt(0).toUpperCase() || 'R' }}
          </span>

          <div class="acct-rider__who">
            <strong>{{ orders[entry.orderId]!.riderProfile!.name }}</strong>
            <span class="acct-rider__bike">
              {{ orders[entry.orderId]!.riderProfile!.vehicle.label }}
              · {{ orders[entry.orderId]!.riderProfile!.vehicle.plateNumber }}
            </span>
          </div>

          <!--
            Only once there are enough of them to mean something. A rider on
            their third delivery has an average that moves a full point on one
            bad night, and putting it beside their face invites a judgement the
            number cannot support.
          -->
          <span
            v-if="
              orders[entry.orderId]!.riderProfile!.rating.average !== null &&
              orders[entry.orderId]!.riderProfile!.rating.count >= 5
            "
            class="acct-rider__rating"
          >
            <Star :size="13" :stroke-width="2" />
            {{ orders[entry.orderId]!.riderProfile!.rating.average!.toFixed(1) }}
          </span>
        </div>

        <!--
          The map, for a delivery still on the road. It is under the status
          line rather than above it because the words are the answer and the
          map is the detail — and because a customer on a slow connection sees
          the answer before the tiles arrive.
        -->
        <template v-if="orders[entry.orderId] && mapWorthShowing(orders[entry.orderId]!)">
          <LiveDeliveryMap
            class="acct-order__map"
            :pickup="orders[entry.orderId]!.route?.pickup ?? null"
            :dropoff="orders[entry.orderId]!.route?.dropoff ?? null"
            :rider="orders[entry.orderId]!.riderPosition"
            :rider-name="orders[entry.orderId]!.riderName"
            :stage="orders[entry.orderId]!.deliveryStage"
            height="240px"
          />
          <a
            v-if="orders[entry.orderId]!.riderPhone"
            class="acct-btn acct-order__call"
            :href="telHref(orders[entry.orderId]!.riderPhone!)"
          >
            <Phone :size="15" :stroke-width="2" />
            Call {{ orders[entry.orderId]!.riderName || 'the rider' }}
          </a>
        </template>

        <!--
          Rate the ride.

          Only on a delivered delivery that a platform rider carried, and only
          until it is used — the server refuses a second one outright, so this
          is about not showing a form that cannot work rather than about
          enforcing anything.

          Five buttons and no comment box. Most people will tap a number and
          nothing else, the number is what the average is made of, and a
          free-text field pointed at a named individual with no moderation
          behind it is a thing to add deliberately or not at all.
        -->
        <div v-if="orders[entry.orderId] && canRate(orders[entry.orderId]!)" class="acct-rate">
          <span class="acct-rate__ask">How was the rider?</span>
          <div class="acct-rate__stars">
            <button
              v-for="score in 5"
              :key="score"
              type="button"
              class="acct-rate__star"
              :disabled="ratingBusy[entry.orderId]"
              :aria-label="`${score} out of 5`"
              @click="submitRating(orders[entry.orderId]!, score)"
            >
              <Star :size="20" :stroke-width="2" />
            </button>
          </div>
          <p v-if="ratingError[entry.orderId]" class="acct-rate__error">
            {{ ratingError[entry.orderId] }}
          </p>
        </div>
        <p v-else-if="rated[entry.orderId] !== undefined" class="acct-rate__thanks">
          Thanks — you rated this {{ rated[entry.orderId] }} out of 5.
        </p>

        <ul v-if="orders[entry.orderId]?.items.length" class="acct-order__items">
          <li v-for="item in orders[entry.orderId]!.items" :key="item.productId">
            <span>{{ item.quantity }} × {{ item.name }}</span>
            <span>{{ formatCurrency(item.lineTotalCents) }}</span>
          </li>
        </ul>

        <p v-if="orders[entry.orderId]?.deliveryAddress" class="acct-card__note acct-order__addr">
          To {{ orders[entry.orderId]!.deliveryAddress }}
        </p>

        <div class="acct-actions" style="margin-top: 14px">
          <!-- Any order on this list, guest ones included: the id is the same
               capability the tracking link runs on. -->
          <a
            v-if="MESSAGING_ENABLED"
            class="acct-link"
            :href="`/account?section=messages&order=${encodeURIComponent(entry.orderId)}`"
          >
            Message the shop
          </a>
          <button type="button" class="acct-link acct-link--danger" @click="history.forget(entry.orderId)">
            Remove from this list
          </button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.acct-order {
  /* Clear of the sticky header when ?order= scrolls a card into view. */
  scroll-margin-top: 110px;
}

.acct-order__map {
  margin-top: 14px;
}

.acct-order__call {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  text-decoration: none;
}

.acct-lookup {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 14px;
}

.acct-orders {
  margin-top: 16px;
}

.acct-orders__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
}

.acct-orders__bar .acct-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
}

.acct-spin {
  animation: acct-spin 0.9s linear infinite;
}

@keyframes acct-spin {
  to {
    transform: rotate(360deg);
  }
}

.acct-order__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.acct-order__total {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  white-space: nowrap;
  color: #1a1a1a;
}

.acct-order__status {
  margin: 14px 0 0;
  font-size: 13.5px;
  font-weight: 700;
  color: #1a6b3c;
}

.acct-order__status--done {
  color: #6b7280;
}

.acct-order__status--stale {
  color: #9ca3af;
  font-weight: 600;
}

.acct-order__rider {
  font-weight: 600;
  color: #6b7280;
}

.acct-order__items {
  margin: 12px 0 0;
  padding: 12px 0 0;
  border-top: 1px solid #e8eae9;
  list-style: none;
}

.acct-order__items li {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 3px 0;
  font-size: 13.5px;
  color: #6b7280;
}

.acct-order__addr {
  margin-top: 10px;
}
/* -- Who is bringing it -------------------------------------------------- */

.acct-rider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--separator);
  border-radius: 12px;
}

.acct-rider__photo {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

/* The fallback the app draws too: most riders never upload a photo, and an
   empty disc reads as a broken image rather than as a choice. */
.acct-rider__photo--letter {
  display: grid;
  place-items: center;
  background: var(--accent-soft, #e3efe7);
  color: var(--accent);
  font-weight: 600;
}

.acct-rider__who {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.acct-rider__bike {
  font-size: 0.82rem;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.acct-rider__rating {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.85rem;
  color: var(--text-secondary);
  flex-shrink: 0;
}

/* -- Rate the ride ------------------------------------------------------- */

.acct-rate {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.acct-rate__ask {
  font-size: 0.88rem;
  color: var(--text-secondary);
}

.acct-rate__stars {
  display: inline-flex;
  gap: 2px;
}

.acct-rate__star {
  background: none;
  border: 0;
  padding: 4px;
  cursor: pointer;
  color: var(--text-tertiary);
  line-height: 0;
}

.acct-rate__star:hover:not(:disabled),
/* Every star up to the hovered one lights, which is the convention people
   already read — hovering the fourth means "four", not "the fourth". */
.acct-rate__stars:hover .acct-rate__star:hover ~ .acct-rate__star {
  color: var(--warning);
}

.acct-rate__star:disabled {
  cursor: default;
  opacity: 0.5;
}

.acct-rate__error {
  width: 100%;
  margin: 4px 0 0;
  font-size: 0.82rem;
  color: var(--danger);
}

.acct-rate__thanks {
  margin-top: 12px;
  font-size: 0.88rem;
  color: var(--text-secondary);
}
</style>
