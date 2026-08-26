<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshCw, Search } from '@lucide/vue'
import {
  deliveryStageLabel,
  formatCompactDate,
  formatCurrency,
  orderStatusLabel,
  type DeliveryStage,
  type OrderStatus,
} from '@pos/shared/index'
import {
  ApiRequestError,
  confirmOrderPayment,
  fetchOrder,
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

/** The order id currently being marked paid, so only its button spins. */
const confirming = ref('')
const confirmError = ref('')

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

/**
 * The customer's own word that they paid, from the place they are most likely
 * to be when they remember: the order page they landed on at checkout is one
 * tab they may well have closed, and cash is handed over long after it.
 *
 * The shop can settle it from their till instead — whichever comes first wins,
 * and the order records which side it was.
 */
async function confirmPaid(orderId: string) {
  if (confirming.value) return

  confirming.value = orderId
  confirmError.value = ''

  try {
    orders.value[orderId] = await confirmOrderPayment(orderId)
  } catch {
    confirmError.value = "Couldn't record that just now. Try again in a moment."
  } finally {
    confirming.value = ''
  }
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

        <ul v-if="orders[entry.orderId]?.items.length" class="acct-order__items">
          <li v-for="item in orders[entry.orderId]!.items" :key="item.productId">
            <span>{{ item.quantity }} × {{ item.name }}</span>
            <span>{{ formatCurrency(item.lineTotalCents) }}</span>
          </li>
        </ul>

        <p v-if="orders[entry.orderId]?.deliveryAddress" class="acct-card__note acct-order__addr">
          To {{ orders[entry.orderId]!.deliveryAddress }}
        </p>

        <!-- Cash on arrival: somebody has to say it changed hands, and for a
             delivery the customer is the only side that was there. -->
        <p v-if="orders[entry.orderId]" class="acct-order__pay">
          <span v-if="orders[entry.orderId]!.paymentStatus === 'paid'" class="acct-tag">Paid</span>
          <template v-else>Cash on arrival — not yet marked paid.</template>
          <span
            v-if="orders[entry.orderId]!.paymentConfirmedBy === 'seller'"
            class="acct-order__payby"
          >
            Confirmed by the shop
          </span>
        </p>

        <p v-if="confirmError" class="acct-flash acct-flash--error">{{ confirmError }}</p>

        <div class="acct-actions" style="margin-top: 14px">
          <button
            v-if="orders[entry.orderId] && orders[entry.orderId]!.paymentStatus !== 'paid'"
            type="button"
            class="acct-link"
            :disabled="confirming === entry.orderId"
            @click="confirmPaid(entry.orderId)"
          >
            {{ confirming === entry.orderId ? 'Saving…' : "I've paid" }}
          </button>
          <a class="acct-link" :href="`/?order=${encodeURIComponent(entry.orderId)}`">View order</a>
          <button type="button" class="acct-link acct-link--danger" @click="history.forget(entry.orderId)">
            Remove from this list
          </button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
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

.acct-order__pay {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 10px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.acct-order__payby { color: #14532d; font-weight: 600; }

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
