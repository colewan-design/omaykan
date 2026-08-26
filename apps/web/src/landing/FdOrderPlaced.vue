<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Banknote, Bike, CheckCircle2, Copy, MapPin, Phone, RefreshCw, Store } from '@lucide/vue'
import {
  deliveryStageLabel,
  deliveryStages,
  formatCompactDate,
  formatCurrency,
  orderStatusLabel,
  SUPPORT_EMAIL,
  supportMailto,
  type DeliveryStage,
  type OrderStatus,
} from '@pos/shared/index'
import {
  ApiRequestError,
  confirmOrderPayment,
  fetchOrder,
  type TrackedOrder,
} from '@pos/web/commerce/api'

// What the customer sees the moment an order exists, and for as long as they
// leave the tab open.
//
// The order id in the URL is the whole capability — there is no account
// required to see this, matching GET /api/online-orders/{order}, which is
// public by unguessable id for exactly that reason. So this page is also the
// link someone can send to whoever is waiting at home.
//
// Status is polled rather than pushed. Reverb broadcasts OrderStatusChanged
// already, but nothing in this app has an Echo client wired up yet; a 20s poll
// that stops once the order is done is honest and costs one request a minute.

const props = defineProps<{ orderId: string }>()

const emit = defineEmits<{ back: [] }>()

const POLL_MS = 20000

const order = ref<TrackedOrder | null>(null)
const loading = ref(true)
const notFound = ref(false)
const error = ref('')
const copied = ref(false)
const confirming = ref(false)
const confirmError = ref('')

let timer = 0

const isDelivery = computed(() => order.value?.fulfillmentMethod === 'delivery')

const stage = computed<DeliveryStage | null>(
  () => (order.value?.deliveryStage as DeliveryStage | null) ?? null,
)

/**
 * The stage is the honest answer for a delivery — once a rider has it, how far
 * the kitchen has got stops being the interesting part. Pickup orders have no
 * stage and fall back to the kitchen status.
 */
const statusLine = computed(() => {
  if (!order.value) return ''
  if (isDelivery.value && stage.value) return deliveryStageLabel(stage.value)
  return orderStatusLabel(order.value.status as OrderStatus)
})

const done = computed(
  () => order.value?.deliveryStage === 'delivered' || order.value?.status === 'served',
)

const stageIndex = computed(() => (stage.value ? deliveryStages.indexOf(stage.value) : -1))

async function load(silent = false) {
  if (!silent) loading.value = true
  error.value = ''

  try {
    order.value = await fetchOrder(props.orderId)
    notFound.value = false
  } catch (err) {
    if (err instanceof ApiRequestError && err.status === 404) {
      notFound.value = true
    } else if (!silent) {
      error.value = 'Could not check this order right now. Try again in a moment.'
    }
  } finally {
    loading.value = false
  }
}

function startPolling() {
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    // Nothing more is going to change, and a backgrounded tab does not need
    // to keep asking.
    if (done.value || document.hidden) return
    void load(true)
  }, POLL_MS)
}

onMounted(() => {
  void load()
  startPolling()
})

// The order id lives in the URL, so this face can be re-pointed at a different
// order without ever being unmounted.
watch(() => props.orderId, () => void load())

onBeforeUnmount(() => window.clearInterval(timer))

const paid = computed(() => order.value?.paymentStatus === 'paid')

/**
 * Marks the order paid from this side of the handover.
 *
 * Cash goes to a rider at the door, or over the counter at pickup — either
 * way the till may be nowhere near it, so the customer's word is worth having.
 * The shop can still settle it themselves; whichever comes first wins, and the
 * order records which side it was.
 */
async function confirmPaid() {
  if (confirming.value || paid.value) return

  confirming.value = true
  confirmError.value = ''

  try {
    order.value = await confirmOrderPayment(props.orderId)
  } catch {
    confirmError.value = "Couldn't record that just now. Try again in a moment."
  } finally {
    confirming.value = false
  }
}

async function copyId() {
  try {
    await navigator.clipboard.writeText(props.orderId)
    copied.value = true
    window.setTimeout(() => (copied.value = false), 2000)
  } catch {
    // Insecure origin or denied permission — the id is on screen either way.
  }
}
</script>

<template>
  <section class="fdop">
    <!-- ── No such order ───────────────────────────────────────────── -->
    <div v-if="notFound" class="fdop__missing">
      <p class="fdop__missing-title">We couldn't find that order</p>
      <p class="fdop__missing-note">
        The number may be mistyped. If you have the confirmation email, the number is in it — or
        write to <a :href="supportMailto('Order not found')">{{ SUPPORT_EMAIL }}</a
        >.
      </p>
      <button type="button" class="fd-btn" @click="emit('back')">Back to the shop</button>
    </div>

    <p v-else-if="loading && !order" class="fdop__loading">Loading your order…</p>

    <template v-else-if="order">
      <!-- ── The receipt head ──────────────────────────────────────── -->
      <div class="fdop__head">
        <CheckCircle2 class="fdop__tick" :size="42" :stroke-width="1.6" />
        <h1 class="fdop__title">{{ done ? 'All done' : 'Order placed' }}</h1>
        <p class="fdop__sub">
          The shop has it. Your number is
          <strong>{{ order.ticketNumber }}</strong> — say it at the counter, or give it to the
          rider.
        </p>

        <p class="fdop__status">
          <span class="fdop__dot" :class="{ 'fdop__dot--done': done }" />
          {{ statusLine }}
          <button type="button" class="fdop__refresh" :disabled="loading" @click="load()">
            <RefreshCw :size="14" :stroke-width="2" />
            {{ loading ? 'Checking…' : 'Refresh' }}
          </button>
        </p>

        <p v-if="error" class="fdop__error">{{ error }}</p>
      </div>

      <!-- ── Where it is ───────────────────────────────────────────── -->
      <div v-if="isDelivery" class="fdop__card">
        <ol class="fdop__track">
          <li
            v-for="(step, index) in deliveryStages"
            :key="step"
            :class="{
              'fdop__step--on': index <= stageIndex,
              'fdop__step--now': index === stageIndex,
            }"
          >
            <span class="fdop__step-dot" />
            <span class="fdop__step-label">{{ deliveryStageLabel(step) }}</span>
          </li>
        </ol>

        <p class="fdop__where">
          <MapPin :size="15" :stroke-width="1.8" />
          {{ order.deliveryAddress }}
        </p>

        <p v-if="order.riderName" class="fdop__rider">
          <Bike :size="15" :stroke-width="1.8" />
          {{ order.riderName }} is bringing it
          <a v-if="order.riderPhone" class="fdop__phone" :href="`tel:${order.riderPhone}`">
            <Phone :size="13" :stroke-width="2" />
            {{ order.riderPhone }}
          </a>
        </p>
      </div>

      <div v-else class="fdop__card">
        <p class="fdop__where">
          <Store :size="15" :stroke-width="1.8" />
          Ready to collect at the shop. They'll have it under {{ order.ticketNumber }}.
        </p>
      </div>

      <!-- ── What is in it ─────────────────────────────────────────── -->
      <div class="fdop__card">
        <ul class="fdop__lines">
          <li v-for="item in order.items" :key="item.productId + item.name">
            <span class="fdop__qty">{{ item.quantity }}×</span>
            <span class="fdop__lname">{{ item.name }}</span>
            <span>{{ formatCurrency(item.lineTotalCents) }}</span>
          </li>
        </ul>

        <dl class="fdop__totals">
          <div>
            <dt>Subtotal</dt>
            <dd>{{ formatCurrency(order.subtotalCents) }}</dd>
          </div>
          <div>
            <dt>VAT</dt>
            <dd>{{ formatCurrency(order.taxCents) }}</dd>
          </div>
          <div v-if="order.deliveryFeeCents > 0">
            <dt>Delivery</dt>
            <dd>{{ formatCurrency(order.deliveryFeeCents) }}</dd>
          </div>
          <div class="fdop__grand">
            <dt>Total</dt>
            <dd>{{ formatCurrency(order.totalCents) }}</dd>
          </div>
        </dl>

        <!-- Cash on arrival, and nothing but the two of you to record it. -->
        <div class="fdop__pay" :class="{ 'fdop__pay--paid': paid }">
          <Banknote :size="17" :stroke-width="1.8" />

          <div class="fdop__pay-body">
            <template v-if="paid">
              <p class="fdop__pay-title">Paid</p>
              <p class="fdop__pay-note">
                <template v-if="order.paymentConfirmedBy === 'seller'">Confirmed by the shop</template>
                <template v-else-if="order.paymentConfirmedBy === 'customer'">
                  Marked paid from this order page
                </template>
                <template v-else>Confirmed</template>
                <template v-if="order.paymentConfirmedAt">
                  · {{ formatCompactDate(order.paymentConfirmedAt) }}
                </template>
              </p>
            </template>

            <template v-else>
              <p class="fdop__pay-title">
                {{ formatCurrency(order.totalCents) }} in cash on arrival
              </p>
              <p class="fdop__pay-note">
                Tell us once you've handed it over — the shop can mark it too, whoever gets there
                first.
              </p>
            </template>
          </div>

          <button
            v-if="!paid"
            type="button"
            class="fdop__pay-btn"
            :disabled="confirming"
            @click="confirmPaid"
          >
            {{ confirming ? 'Saving…' : "I've paid" }}
          </button>
        </div>

        <p v-if="confirmError" class="fdop__error">{{ confirmError }}</p>
      </div>

      <!-- ── Keeping hold of it ────────────────────────────────────── -->
      <div class="fdop__card fdop__keep">
        <p class="fdop__keep-title">Keep this number</p>
        <p class="fdop__keep-note">
          It is the only way back to this page. It is saved in this browser, and in
          <a href="/account?section=orders">your account</a> if you were signed in.
        </p>
        <p class="fdop__id">
          <code>{{ props.orderId }}</code>
          <button type="button" class="fdop__copy" @click="copyId">
            <Copy :size="14" :stroke-width="2" />
            {{ copied ? 'Copied' : 'Copy' }}
          </button>
        </p>
      </div>

      <div class="fdop__actions">
        <button type="button" class="fd-btn" @click="emit('back')">Keep shopping</button>
        <a class="fd-linkbtn" href="/account?section=orders">All your orders</a>
      </div>
    </template>
  </section>
</template>

<style scoped>
/* Restated rather than inherited: LandingPage's .fd-btn / .fd-linkbtn live in
   its scoped block, which does not reach into a child component. */
.fd-btn {
  display: inline-block;
  padding: 12px 24px;
  border: none;
  border-radius: 999px;
  background: #1a1a1a;
  color: #fff;
  font: 700 14.5px/1.2 inherit;
  cursor: pointer;
}
.fd-btn:hover { background: #1a6b3c; }

.fd-linkbtn {
  border: none;
  background: none;
  padding: 0;
  color: #1a6b3c;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.fdop { max-width: 620px; margin: 0 auto; }

.fdop__loading { color: #4a5b52; font-size: 15px; }

/* -- Head ----------------------------------------------------------------- */

.fdop__head { text-align: center; margin-bottom: 22px; }
.fdop__tick { display: inline-block; color: #22c55e; }

.fdop__title {
  margin: 10px 0 6px;
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 800;
  letter-spacing: -0.03em;
}

.fdop__sub { margin: 0 auto; max-width: 440px; color: #4a5b52; font-size: 14.5px; line-height: 1.55; }

.fdop__status {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin: 16px 0 0;
  padding: 8px 14px;
  border-radius: 999px;
  background: #eefaf1;
  color: #14532d;
  font-size: 13.5px;
  font-weight: 700;
}

.fdop__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #f5a623;
}

.fdop__dot--done { background: #22c55e; }

.fdop__refresh {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 4px;
  border: none;
  background: none;
  padding: 0;
  color: #1a6b3c;
  font: 600 12.5px/1 inherit;
  text-decoration: underline;
  cursor: pointer;
}

.fdop__refresh:disabled { opacity: 0.6; cursor: progress; }

.fdop__error { margin: 10px 0 0; color: #b3261e; font-size: 13px; }

/* -- Cards ---------------------------------------------------------------- */

.fdop__card {
  padding: 18px 20px;
  border: 1px solid #e4e9e6;
  border-radius: 14px;
  background: #fff;
}

.fdop__card + .fdop__card { margin-top: 14px; }

/* -- Delivery progress ---------------------------------------------------- */

.fdop__track {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin: 0 0 16px;
  padding: 0;
  list-style: none;
  counter-reset: step;
}

.fdop__track li { display: flex; flex-direction: column; align-items: center; gap: 7px; position: relative; }

/* The connecting rail, drawn behind each dot but the first. */
.fdop__track li::before {
  content: '';
  position: absolute;
  top: 5px;
  right: 50%;
  left: -50%;
  height: 2px;
  background: #e4e9e6;
}

.fdop__track li:first-child::before { display: none; }
.fdop__step--on::before { background: #22c55e !important; }

.fdop__step-dot {
  position: relative;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: #e4e9e6;
}

.fdop__step--on .fdop__step-dot { background: #22c55e; }
.fdop__step--now .fdop__step-dot { box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.22); }

.fdop__step-label { color: #8b968f; font-size: 11.5px; font-weight: 700; text-align: center; line-height: 1.3; }
.fdop__step--on .fdop__step-label { color: #14532d; }

.fdop__where,
.fdop__rider {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 0;
  color: #3d4a43;
  font-size: 13.5px;
  line-height: 1.5;
}

.fdop__rider { margin-top: 10px; font-weight: 700; color: #14532d; }

.fdop__phone {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #1a6b3c;
  text-decoration: underline;
}

/* -- Items ---------------------------------------------------------------- */

.fdop__lines { margin: 0 0 14px; padding: 0; list-style: none; }
.fdop__lines li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 9px;
  padding: 7px 0;
  font-size: 13.5px;
  border-bottom: 1px solid #f1f4f2;
}

.fdop__qty { color: #1a6b3c; font-weight: 800; }
.fdop__lname { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.fdop__totals { margin: 0; }
.fdop__totals > div { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.fdop__totals > div + div { margin-top: 5px; }
.fdop__totals dt { color: #4a5b52; font-size: 13.5px; }
.fdop__totals dd { margin: 0; font-size: 13.5px; font-weight: 600; }

.fdop__grand { margin-top: 10px !important; padding-top: 10px; border-top: 1px solid #ecefed; }
.fdop__grand dt { font-size: 15px !important; font-weight: 800; color: #1a1a1a !important; }
.fdop__grand dd { font-size: 17px !important; font-weight: 800; }

.fdop__pay {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-top: 14px;
  padding: 12px 13px;
  border: 1px solid #e4e9e6;
  border-radius: 11px;
  background: #f7fbf8;
}

.fdop__pay svg { flex-shrink: 0; color: #1a6b3c; }
.fdop__pay-body { flex: 1; min-width: 0; }
.fdop__pay-title { margin: 0; font-size: 13.5px; font-weight: 800; }
.fdop__pay-note { margin: 2px 0 0; color: #4a5b52; font-size: 12px; line-height: 1.45; }

.fdop__pay--paid { border-color: #bbe7c9; background: #eefaf1; }
.fdop__pay--paid .fdop__pay-title { color: #14532d; }

.fdop__pay-btn {
  flex-shrink: 0;
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 999px;
  background: #22c55e;
  color: #06240f;
  font: 800 13px/1 inherit;
  cursor: pointer;
}

.fdop__pay-btn:hover:not(:disabled) { background: #16a34a; color: #fff; }
.fdop__pay-btn:disabled { opacity: 0.6; cursor: progress; }

/* -- Order number --------------------------------------------------------- */

.fdop__keep { background: #f7fbf8; }
.fdop__keep-title { margin: 0 0 4px; font-size: 14px; font-weight: 800; }
.fdop__keep-note { margin: 0 0 12px; color: #4a5b52; font-size: 12.5px; line-height: 1.5; }
.fdop__keep-note a { color: #1a6b3c; text-decoration: underline; }

.fdop__id { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 0; }

.fdop__id code {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 8px 10px;
  border: 1px solid #e4e9e6;
  border-radius: 8px;
  background: #fff;
  font-size: 12px;
}

.fdop__copy {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid #d7ded9;
  border-radius: 999px;
  background: #fff;
  font: 700 12.5px/1 inherit;
  color: #1a6b3c;
  cursor: pointer;
}

.fdop__copy:hover { border-color: #1a6b3c; background: #f2f9f4; }

.fdop__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  flex-wrap: wrap;
  margin-top: 22px;
}

/* -- Missing -------------------------------------------------------------- */

.fdop__missing { text-align: center; padding: 50px 20px; }
.fdop__missing-title { margin: 0 0 6px; font-size: 17px; font-weight: 800; }
.fdop__missing-note { margin: 0 auto 20px; max-width: 420px; color: #4a5b52; font-size: 14px; line-height: 1.55; }
.fdop__missing-note a { color: #1a6b3c; text-decoration: underline; }
</style>
