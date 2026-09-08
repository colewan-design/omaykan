<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Phone, RefreshCw, Search } from '@lucide/vue'
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
  fetchOrder,
  type RiderPosition,
  type TrackedOrder,
} from '@pos/web/commerce/api'
import { useStorefrontOrderHistory } from '@pos/web/commerce/orderHistory'

// Past orders, and where the current ones have got to.
//
// The ids come off this device (commerce/orderHistory.ts) because the API has
// no "list my orders" — it can only return an order by id. Status is never read
// from that local copy: it is fetched live per order, so an order that moved on
// while the tab was closed doesn't show yesterday's stage.
//
// The lookup box is what makes the list portable. Someone who ordered on their
// phone can paste the id here and the order joins this browser's list too.

const history = useStorefrontOrderHistory()

/** Live status per order id. Absent while loading, null when the fetch failed. */
const orders = ref<Record<string, TrackedOrder | null>>({})
const refreshing = ref(false)

const lookupId = ref('')
const lookupError = ref('')
const lookingUp = ref(false)

async function refresh() {
  if (history.entries.value.length === 0) return
  refreshing.value = true
  await Promise.all(
    history.entries.value.map(async (entry) => {
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

onMounted(refresh)

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
        Orders placed from this browser, with where each one has got to right now.
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

      <article v-for="entry in history.entries.value" :key="entry.orderId" class="acct-card">
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
          <button type="button" class="acct-link acct-link--danger" @click="history.forget(entry.orderId)">
            Remove from this list
          </button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
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
</style>
