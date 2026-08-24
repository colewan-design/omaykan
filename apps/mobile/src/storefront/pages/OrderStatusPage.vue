<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { collection, doc, getDocs, onSnapshot } from 'firebase/firestore'
import { Bike, CheckCircle2, Clock3, MapPin, ReceiptText } from '@lucide/vue'
import { formatCurrency, orderStatusLabel, type OrderItemSummary, type OrderStatus } from '@pos/shared/index'
import { ORG_SLUG, STORE_CODE, STORE_ADDRESS, db } from '@pos/web/storefront/firebase'
import {
  DELIVERY_STAGES,
  deliveryStageFor,
  stageIndex,
  type DeliveryStage,
} from '@pos/web/storefront/delivery'

const props = defineProps<{ orderId: string }>()

interface TrackedOrder {
  ticketNumber: string
  status: OrderStatus
  totalCents: number
  paymentStatus?: string
  paymentMethod?: string
  fulfillmentMethod?: 'pickup' | 'delivery'
  deliveryAddress?: string | null
  deliveryFeeCents?: number
  deliveryDistanceKm?: number | null
  deliveryStage?: DeliveryStage | null
  riderName?: string | null
  riderPhone?: string | null
}

const order = ref<TrackedOrder | null>(null)
const items = ref<OrderItemSummary[]>([])
const notFound = ref(false)
let unsubscribe: (() => void) | null = null

onMounted(() => {
  const orderRef = doc(db, 'organizations', ORG_SLUG, 'stores', STORE_CODE, 'orders', props.orderId)

  // Fetched independently of the status listener below: a failure here (flaky
  // network, a since-deleted order) must not stop the live status listener
  // from attaching — the customer still needs to see status updates even if
  // the item list can't be shown.
  getDocs(collection(orderRef, 'items'))
    .then((itemsSnap) => {
      items.value = itemsSnap.docs.map((itemDoc) => {
        const data = itemDoc.data() as {
          productId: string | null
          productName: string
          quantity: number
          unitPriceCents: number
          lineTotalCents: number
        }
        return {
          productId: data.productId ?? '',
          name: data.productName,
          quantity: data.quantity,
          unitPriceCents: data.unitPriceCents,
          lineTotalCents: data.lineTotalCents,
        }
      })
    })
    .catch((err) => console.error('Failed to load order items:', err))

  unsubscribe = onSnapshot(orderRef, (snap) => {
    if (!snap.exists()) {
      notFound.value = true
      return
    }

    const data = snap.data() as TrackedOrder
    order.value = {
      ticketNumber: data.ticketNumber,
      status: data.status,
      totalCents: data.totalCents,
      paymentStatus: data.paymentStatus,
      paymentMethod: data.paymentMethod,
      fulfillmentMethod: data.fulfillmentMethod,
      deliveryAddress: data.deliveryAddress,
      deliveryFeeCents: data.deliveryFeeCents,
      deliveryDistanceKm: data.deliveryDistanceKm,
      deliveryStage: data.deliveryStage,
      riderName: data.riderName,
      riderPhone: data.riderPhone,
    }
  })
})

onUnmounted(() => unsubscribe?.())

const fulfillmentLabel = computed(() => (order.value?.fulfillmentMethod === 'delivery' ? 'Delivery' : 'Pickup'))
const fulfillmentDetail = computed(() =>
  order.value?.fulfillmentMethod === 'delivery'
    ? `Delivering to ${order.value.deliveryAddress}`
    : `Ready for pickup at ${STORE_ADDRESS || 'the store'}`,
)
const paymentLabel = computed(() => (order.value?.paymentMethod === 'ewallet' ? 'GCash' : 'Cash'))

// Delivery timeline, merged in from Baguio Delivery. Only delivery orders get
// the rider stages; pickup orders keep the plain status copy above.
const isDelivery = computed(() => order.value?.fulfillmentMethod === 'delivery')

const currentStage = computed<DeliveryStage | null>(() =>
  order.value && isDelivery.value ? deliveryStageFor(order.value.status, order.value.deliveryStage) : null,
)

const currentStageIndex = computed(() => (currentStage.value ? stageIndex(currentStage.value) : -1))

const stages = computed(() =>
  DELIVERY_STAGES.map((stage, index) => ({
    ...stage,
    done: index < currentStageIndex.value,
    active: index === currentStageIndex.value,
  })),
)
</script>

<template>
  <div class="order-status">
    <p v-if="notFound" class="order-status__state">We couldn't find that order.</p>
    <p v-else-if="!order" class="order-status__state">Loading your order...</p>

    <template v-else>
      <section class="order-status__hero">
        <div class="order-status__icon">
          <CheckCircle2 :size="28" />
        </div>
        <p class="order-status__eyebrow">Order {{ order.ticketNumber }}</p>
        <h1>{{ orderStatusLabel(order.status) }}</h1>
        <p class="order-status__copy">
          Your order is saved in the store queue. Please prepare
          <strong>{{ formatCurrency(order.totalCents) }}</strong>
          in {{ paymentLabel }}.
        </p>
      </section>

      <!-- Rider timeline, from Baguio Delivery's order lifecycle. -->
      <section v-if="isDelivery" class="timeline">
        <div class="timeline__head">
          <Bike :size="18" />
          <div>
            <strong>{{ order.riderName ? `${order.riderName} is your rider` : 'Delivery progress' }}</strong>
            <span v-if="order.deliveryDistanceKm">{{ order.deliveryDistanceKm.toFixed(1) }} km from the store</span>
            <span v-else>We'll update this as your order moves.</span>
          </div>
          <a v-if="order.riderPhone" :href="`tel:${order.riderPhone}`" class="timeline__call">Call</a>
        </div>

        <ol class="timeline__list">
          <li
            v-for="stage in stages"
            :key="stage.id"
            class="timeline__step"
            :class="{ 'timeline__step--done': stage.done, 'timeline__step--active': stage.active }"
          >
            <span class="timeline__marker">
              <component :is="stage.icon" :size="14" />
            </span>
            <div class="timeline__text">
              <strong>{{ stage.label }}</strong>
              <span v-if="stage.active">{{ stage.detail }}</span>
            </div>
          </li>
        </ol>
      </section>

      <section class="order-status__details">
        <div class="order-status__detail">
          <Clock3 :size="18" />
          <div>
            <strong>Status update</strong>
            <span>Open this page anytime to watch the live kitchen/store progress.</span>
          </div>
        </div>
        <div class="order-status__detail">
          <MapPin :size="18" />
          <div>
            <strong>{{ fulfillmentLabel }}</strong>
            <span>{{ fulfillmentDetail }}</span>
          </div>
        </div>
        <div class="order-status__detail">
          <ReceiptText :size="18" />
          <div>
            <strong v-if="order.paymentStatus === 'paid'">Payment confirmed</strong>
            <strong v-else>Payment</strong>
            <span v-if="order.paymentStatus === 'paid'">
              The store has confirmed your {{ paymentLabel }} payment for this order.
            </span>
            <span v-else>
              No online charge was collected —
              {{ order.paymentMethod === 'ewallet' ? 'the store will confirm your GCash payment' : 'pay in cash' }}
              when your order is claimed{{ order.fulfillmentMethod === 'delivery' ? ' or delivered' : '' }}.
            </span>
          </div>
        </div>
      </section>

      <section class="order-status__items">
        <article v-for="item in items" :key="`${item.productId}-${item.name}`" class="order-status__item">
          <div>
            <strong>{{ item.name }}</strong>
            <span>{{ item.quantity }} x {{ formatCurrency(item.unitPriceCents) }}</span>
          </div>
          <strong>{{ formatCurrency(item.lineTotalCents) }}</strong>
        </article>

        <article v-if="order.deliveryFeeCents" class="order-status__item order-status__item--fee">
          <div>
            <strong>Delivery fee</strong>
            <span v-if="order.deliveryDistanceKm">{{ order.deliveryDistanceKm.toFixed(1) }} km</span>
          </div>
          <strong>{{ formatCurrency(order.deliveryFeeCents) }}</strong>
        </article>
      </section>

      <RouterLink to="/" class="order-status__back">Continue shopping</RouterLink>
    </template>
  </div>
</template>

<style scoped>
.order-status {
  display: grid;
  gap: 14px;
}

.order-status__state {
  padding: 48px 16px;
  text-align: center;
  color: #6b7280;
}

.order-status__hero,
.order-status__details,
.order-status__items,
.timeline {
  padding: 18px;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.06);
}

.order-status__hero {
  display: grid;
  gap: 10px;
  background: linear-gradient(135deg, var(--sf-banner-green) 0%, #ffffff 72%);
}

/* ── Delivery timeline ─────────────────────────────────────────────── */
.timeline {
  display: grid;
  gap: 16px;
}

.timeline__head {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  color: var(--sf-primary-deep);
}

.timeline__head strong,
.timeline__head span {
  display: block;
}

.timeline__head strong {
  color: var(--sf-text-dark);
  font-size: 0.95rem;
}

.timeline__head span {
  margin-top: 2px;
  color: var(--sf-text-gray);
  font-size: 0.82rem;
}

.timeline__call {
  padding: 7px 14px;
  border-radius: var(--sf-radius-pill);
  background: var(--sf-primary);
  color: #fff;
  font-size: 0.8rem;
  font-weight: 800;
  text-decoration: none;
}

.timeline__list {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline__step {
  position: relative;
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 16px;
}

.timeline__step:last-child {
  padding-bottom: 0;
}

/* Connector line between markers. */
.timeline__step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 26px;
  bottom: 0;
  width: 2px;
  background: var(--sf-border);
}

.timeline__step--done:not(:last-child)::before {
  background: var(--sf-primary-light);
}

.timeline__marker {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: var(--sf-chip);
  color: var(--sf-text-muted);
}

.timeline__step--done .timeline__marker {
  background: var(--sf-primary-light);
  color: #fff;
}

.timeline__step--active .timeline__marker {
  background: var(--sf-primary);
  color: #fff;
  box-shadow: 0 0 0 4px var(--sf-banner-green);
}

.timeline__text strong {
  display: block;
  color: var(--sf-text-muted);
  font-size: 0.88rem;
  font-weight: 700;
}

.timeline__step--done .timeline__text strong,
.timeline__step--active .timeline__text strong {
  color: var(--sf-text-dark);
}

.timeline__text span {
  display: block;
  margin-top: 3px;
  color: var(--sf-text-gray);
  font-size: 0.8rem;
  line-height: 1.4;
}

.order-status__item--fee {
  border-top: 1px dashed var(--sf-border);
  padding-top: 12px;
}

.order-status__icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: var(--sf-primary);
  color: #fff;
}

.order-status__eyebrow {
  margin: 0;
  color: var(--sf-primary);
  font-size: 0.84rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.order-status__hero h1 {
  margin: 0;
  font-size: 1.7rem;
  line-height: 1.04;
  letter-spacing: -0.03em;
}

.order-status__copy {
  margin: 0;
  color: #4b5563;
  line-height: 1.55;
}

.order-status__details {
  display: grid;
  gap: 12px;
}

.order-status__detail {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  color: #4b5563;
}

.order-status__detail strong,
.order-status__detail span {
  display: block;
}

.order-status__detail strong {
  color: #111827;
  margin-bottom: 4px;
}

.order-status__items {
  display: grid;
  gap: 10px;
}

.order-status__item {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.order-status__item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.order-status__item span {
  display: block;
  margin-top: 5px;
  color: #6b7280;
  font-size: 0.88rem;
}

.order-status__back {
  justify-self: start;
  padding: 12px 16px;
  border-radius: 999px;
  background: #FDECD9;
  color: var(--sf-primary);
  text-decoration: none;
  font-weight: 800;
}
</style>
