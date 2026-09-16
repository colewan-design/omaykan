<script setup lang="ts">
/**
 * Who is carrying what, and how far along they are.
 *
 * Lifted out of the seller dashboard when that page was rebuilt to its design
 * mockup, which has no delivery card. This was the only place in the app a
 * shop could put a rider on an order, so it moved here rather than being
 * deleted — the Orders page is where a merchant already goes to chase one.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { MapPin, MessageSquare, Phone, Undo2 } from '@lucide/vue'
import {
  deliveryStageLabel,
  nextDeliveryStage,
  type OrderSummary,
  type RecentRider,
  type SavedRider,
} from '@pos/shared/index'
import { usePosStore } from '@pos/core/stores/pos'
import ChartCard from '@pos/core/components/ChartCard.vue'
import LiveDeliveryMap from '@pos/core/components/LiveDeliveryMap.vue'

const props = withDefaults(defineProps<{ dark?: boolean }>(), { dark: false })

const store = usePosStore()

const opsError = ref('')
const busyOrderId = ref('')

/** Per-order rider drafts, keyed by order id so two cards can't share a name. */
const riderDrafts = reactive<Record<string, { name: string; phone: string; save: boolean }>>({})

function riderDraft(orderId: string) {
  if (!riderDrafts[orderId]) {
    // `save` defaults on. A shop typing a name into this box almost always has
    // a rider they will use again, and the cost of a wrong guess is one row
    // they can delete — against retyping the same number four times a day.
    riderDrafts[orderId] = { name: '', phone: '', save: true }
  }
  return riderDrafts[orderId]
}

// ── The shop's own riders ───────────────────────────────────────────────────
//
// Loaded once when this mounts, and again after anything changes the list. Not
// part of the pos store's cached state: an `online` flag is true for the ten
// seconds it describes, and a cached one would offer a rider whose phone is
// off.

const savedRiders = ref<SavedRider[]>([])
const recentRiders = ref<RecentRider[]>([])
const ridersLoaded = ref(false)

onMounted(() => void loadRiders())

async function loadRiders() {
  try {
    const directory = await store.loadSavedRiders()
    savedRiders.value = directory.saved
    recentRiders.value = directory.recent
  } catch {
    // A local-only till has no backend to ask. The typed-in form below is the
    // whole feature for them and still works.
    savedRiders.value = []
    recentRiders.value = []
  } finally {
    ridersLoaded.value = true
  }
}

/** Deliveries still on the shop's hands — anything not yet dropped off. */
const activeDeliveries = computed(() =>
  store.onlineOrders
    .filter(
      (order) =>
        !order.voidedAt &&
        order.fulfillmentMethod === 'delivery' &&
        (order.deliveryStage ?? 'pending') !== 'delivered',
    )
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
)

/** Whether a saved rider can actually be handed an order right now. */
function riderAssignable(rider: SavedRider): boolean {
  return !rider.onPlatform || rider.status === 'approved'
}

async function runOrderAction(orderId: string, action: () => Promise<unknown>) {
  if (busyOrderId.value) return
  busyOrderId.value = orderId
  opsError.value = ''
  try {
    await action()
  } catch (error) {
    // These all cross the network — a storefront order isn't in the offline
    // outbox — so say so plainly instead of leaving a button that did nothing.
    opsError.value = error instanceof Error ? error.message : 'That did not go through. Try again.'
  } finally {
    busyOrderId.value = ''
  }
}

function assignSavedRider(order: OrderSummary, rider: SavedRider) {
  return runOrderAction(order.id, async () => {
    await store.notifyRider(order.id, { savedRiderId: rider.id })
    // The picker is ordered by use, and this was one.
    void loadRiders()
  })
}

function keepRecentRider(rider: RecentRider) {
  return runOrderAction(rider.riderId, async () => {
    await store.saveRider({ riderId: rider.riderId, name: rider.name, phone: rider.phone })
    await loadRiders()
  })
}

function forgetRider(rider: SavedRider) {
  return runOrderAction(rider.id, async () => {
    await store.deleteSavedRider(rider.id)
    await loadRiders()
  })
}

function returnToBoard(order: OrderSummary) {
  return runOrderAction(order.id, () => store.returnToBoard(order.id))
}

function notifyRider(order: OrderSummary) {
  const draft = riderDraft(order.id)
  const name = draft.name.trim()
  if (!name) {
    opsError.value = 'Give the rider a name first — the customer sees it on their tracking page.'
    return Promise.resolve()
  }

  return runOrderAction(order.id, async () => {
    await store.notifyRider(order.id, {
      riderName: name,
      riderPhone: draft.phone.trim() || null,
      saveRider: draft.save,
    })
    delete riderDrafts[order.id]
    // A number typed here may have matched a rider account, which changes what
    // the picker offers next time. Cheaper to re-read than to guess.
    if (draft.save) void loadRiders()
  })
}

function advanceDelivery(order: OrderSummary) {
  const next = nextDeliveryStage(order.deliveryStage ?? 'pending')
  if (!next) return Promise.resolve()
  return runOrderAction(order.id, () => store.advanceDelivery(order.id, next))
}

// ── The delivery map ────────────────────────────────────────────────────────

/**
 * Which rows have their map open.
 *
 * Six deliveries out cannot show six maps at 260px each and still be a list,
 * so each row has a toggle. The default is open exactly when there is a live
 * rider to watch — which is the row the merchant opened this to look at.
 */
const openMaps = reactive<Record<string, boolean>>({})

function mapOpen(order: OrderSummary): boolean {
  return openMaps[order.id] ?? (!!order.riderPosition && !order.riderPosition.stale)
}

function toggleMap(order: OrderSummary) {
  openMaps[order.id] = !mapOpen(order)
}

/** Nothing to draw at all — no rider, no shop pin, no door pin. */
function mappable(order: OrderSummary): boolean {
  return !!order.riderPosition || !!order.route?.pickup?.lat || !!order.route?.dropoff?.lat
}

/** Digits only — a saved number like "0917 000 0000" won't dial as typed. */
function telHref(phone: string) {
  return `tel:${phone.replace(/[^\d+]/g, '')}`
}

function smsHref(order: OrderSummary) {
  const phone = (order.riderPhone ?? '').replace(/[^\d+]/g, '')
  const body = `Omaykan order ${order.ticketNumber} for ${order.deliveryAddress ?? 'pickup at the shop'}`
  return `sms:${phone}?body=${encodeURIComponent(body)}`
}
</script>

<template>
  <ChartCard
    class="delivery-board"
    title="Deliveries"
    summary="Who is carrying what, and how far along they are."
  >
    <p v-if="opsError" class="delivery-error" role="alert">{{ opsError }}</p>

    <p v-if="activeDeliveries.length === 0" class="delivery-empty">
      No deliveries out. Pickup orders don't need a rider.
    </p>

    <ul v-else class="delivery-list">
      <li v-for="order in activeDeliveries" :key="order.id" class="delivery-row">
        <div class="delivery-row__head">
          <strong>{{ order.ticketNumber }}</strong>
          <span
            class="delivery-chip"
            :class="order.riderName ? 'delivery-chip--good' : 'delivery-chip--warn'"
          >{{ deliveryStageLabel(order.deliveryStage ?? 'pending') }}</span>
        </div>
        <p class="delivery-row__meta">{{ order.deliveryAddress || 'No address on the order' }}</p>

        <!-- No rider yet. Three ways out of this state and all of them are
             fine: leave it on the platform board for whoever taps first, pick
             one of the shop's own riders, or type a name. Nothing here forces
             a shop onto the board, which is the whole point — a carinderia
             with a nephew on a tricycle is not looking for a stranger. -->
        <template v-if="!order.riderName">
          <div v-if="savedRiders.length" class="delivery-riders">
            <span class="delivery-riders__label">Your riders</span>
            <button
              v-for="rider in savedRiders"
              :key="rider.id"
              type="button"
              class="delivery-rider-chip"
              :class="{ 'delivery-rider-chip--off': !riderAssignable(rider) }"
              :disabled="busyOrderId === order.id || !riderAssignable(rider)"
              :title="riderAssignable(rider) ? (rider.note || rider.phone || '') : 'Their rider account is ' + rider.status"
              @click="assignSavedRider(order, rider)"
            >
              <!-- A filled dot means the app is reporting a position, so
                   assigning reaches a phone rather than a phone call. -->
              <span
                v-if="rider.onPlatform"
                class="delivery-rider-chip__dot"
                :class="{ 'delivery-rider-chip__dot--live': rider.online }"
                aria-hidden="true"
              />
              <span>{{ rider.name }}</span>
            </button>
          </div>

          <form class="delivery-rider" @submit.prevent="notifyRider(order)">
            <input
              v-model="riderDraft(order.id).name"
              class="delivery-input"
              type="text"
              placeholder="Rider name"
              autocomplete="off"
            >
            <input
              v-model="riderDraft(order.id).phone"
              class="delivery-input"
              type="tel"
              placeholder="Mobile number"
              autocomplete="off"
            >
            <label class="delivery-rider__save">
              <input v-model="riderDraft(order.id).save" type="checkbox">
              <span>Save for next time</span>
            </label>
            <button class="delivery-action delivery-action--primary" type="submit" :disabled="busyOrderId === order.id">
              {{ busyOrderId === order.id ? 'Sending…' : 'Notify rider' }}
            </button>
          </form>

          <p class="delivery-rider__hint">
            Or leave it — any approved rider on the platform can pick this up from the board.
          </p>
        </template>

        <div v-else class="delivery-rider delivery-rider--assigned">
          <!--
            The face and the bike, for the counter.

            Only for a rider with a platform account — a name typed in at the
            till has neither, and falls through to the plain name it always
            had. What this buys the person holding the bag is the ability to
            pick the right one of three people waiting without calling out an
            order number.
          -->
          <template v-if="order.riderProfile">
            <img
              v-if="order.riderProfile.photoUrl"
              class="delivery-rider__photo"
              :src="order.riderProfile.photoUrl"
              :alt="`Photo of ${order.riderProfile.name}`"
              loading="lazy"
            />
            <span v-else class="delivery-rider__photo delivery-rider__photo--letter">
              {{ order.riderProfile.name.trim().charAt(0).toUpperCase() || 'R' }}
            </span>
          </template>
          <span class="delivery-rider__name">
            {{ order.riderName }}
            <small v-if="order.riderProfile" class="delivery-rider__bike">
              {{ order.riderProfile.vehicle.label }}
              · {{ order.riderProfile.vehicle.plateNumber }}
            </small>
          </span>
          <template v-if="order.riderPhone">
            <a class="delivery-action" :href="telHref(order.riderPhone)">
              <Phone :size="14" />
              <span>Call</span>
            </a>
            <a class="delivery-action" :href="smsHref(order)">
              <MessageSquare :size="14" />
              <span>Text</span>
            </a>
          </template>
          <button
            v-if="mappable(order)"
            class="delivery-action"
            type="button"
            @click="toggleMap(order)"
          >
            <MapPin :size="14" />
            <span>{{ mapOpen(order) ? 'Hide map' : 'Track' }}</span>
          </button>
          <!-- Only before pickup. Once the food is in the bag, handing the
               order back is a phone call, and the API refuses this. -->
          <button
            v-if="(order.deliveryStage ?? 'pending') === 'assigned'"
            class="delivery-action"
            type="button"
            :disabled="busyOrderId === order.id"
            @click="returnToBoard(order)"
          >
            <Undo2 :size="14" />
            <span>Back to board</span>
          </button>
          <button
            v-if="nextDeliveryStage(order.deliveryStage ?? 'pending')"
            class="delivery-action delivery-action--primary"
            type="button"
            :disabled="busyOrderId === order.id"
            @click="advanceDelivery(order)"
          >
            {{ deliveryStageLabel(nextDeliveryStage(order.deliveryStage ?? 'pending')!) }}
          </button>
        </div>

        <LiveDeliveryMap
          v-if="mappable(order) && mapOpen(order)"
          class="delivery-map"
          :pickup="order.route?.pickup ?? null"
          :dropoff="order.route?.dropoff ?? null"
          :rider="order.riderPosition ?? null"
          :rider-name="order.riderName"
          :stage="order.deliveryStage ?? 'pending'"
          :dark="props.dark"
          height="220px"
        />
      </li>
    </ul>

    <!-- Riders who have delivered here but are not on the shop's list. The
         shop already has their name and number off those orders; this is just
         the one tap that stops it being retyped. -->
    <div v-if="ridersLoaded && recentRiders.length" class="delivery-riders delivery-riders--recent">
      <span class="delivery-riders__label">Delivered for you before</span>
      <button
        v-for="rider in recentRiders"
        :key="rider.riderId"
        type="button"
        class="delivery-rider-chip delivery-rider-chip--add"
        :disabled="busyOrderId === rider.riderId"
        @click="keepRecentRider(rider)"
      >
        + {{ rider.name }}
      </button>
    </div>

    <div v-if="savedRiders.length" class="delivery-riders delivery-riders--manage">
      <span class="delivery-riders__label">Saved</span>
      <span v-for="rider in savedRiders" :key="rider.id" class="delivery-rider-saved">
        {{ rider.name }}
        <button
          type="button"
          class="delivery-rider-saved__x"
          :disabled="busyOrderId === rider.id"
          :aria-label="'Remove ' + rider.name + ' from your riders'"
          @click="forgetRider(rider)"
        >&times;</button>
      </span>
    </div>
  </ChartCard>
</template>

<style scoped>
.delivery-error {
  margin: 0 0 12px;
  padding: 12px 16px;
  border: 1px solid color-mix(in srgb, var(--danger) 20%, transparent);
  border-radius: 14px;
  background: color-mix(in srgb, var(--danger) 8%, transparent);
  color: var(--danger);
  font: var(--type-subhead);
}

.delivery-empty {
  margin: 0;
  padding: 20px 0;
  color: var(--text-tertiary);
  font: var(--type-subhead);
  text-align: center;
}

.delivery-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.delivery-row {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--separator);
  border-radius: 14px;
  background: color-mix(in srgb, var(--bg-elevated) 60%, transparent);
}

.delivery-row__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.delivery-row__meta {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.delivery-chip {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  font: var(--type-caption);
  font-weight: 600;
}

.delivery-chip--warn {
  background: color-mix(in srgb, var(--warning) 16%, transparent);
  color: color-mix(in srgb, var(--warning) 75%, var(--text-primary));
}

.delivery-chip--good {
  background: color-mix(in srgb, var(--success) 16%, transparent);
  color: color-mix(in srgb, var(--success) 75%, var(--text-primary));
}

.delivery-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-pill);
  background: var(--bg-elevated);
  color: var(--text-primary);
  font: var(--type-caption);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
}

.delivery-action:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--accent) 30%, transparent);
  color: var(--accent);
}

.delivery-action--primary {
  border-color: transparent;
  background: var(--accent);
  color: var(--accent-text-on);
}

.delivery-action--primary:hover:not(:disabled) {
  background: var(--accent-pressed);
  color: var(--accent-text-on);
}

.delivery-action:disabled {
  opacity: 0.5;
}

/* The rider row wraps to its own line on a narrow card rather than squeezing
   two inputs and a button into a strip too small to type in. */
.delivery-rider {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.delivery-rider__name {
  font: var(--type-subhead);
  font-weight: 700;
}

.delivery-riders {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.delivery-riders__label {
  font: var(--type-caption);
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.delivery-riders--recent,
.delivery-riders--manage {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--separator);
}

.delivery-rider-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--separator);
  border-radius: 999px;
  background: var(--bg-elevated);
  color: var(--text-primary);
  font: var(--type-footnote);
  font-weight: 600;
  cursor: pointer;
}

/* A rider whose account is suspended still shows — the shop needs to see why
   their usual person is not available, rather than find them silently gone. */
.delivery-rider-chip--off {
  opacity: 0.45;
  cursor: not-allowed;
}

.delivery-rider-chip--add {
  border-style: dashed;
}

/* Hollow: has an account but is not reporting. Filled: reporting right now,
   so assigning them reaches a phone rather than starting a phone call. */
.delivery-rider-chip__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  border: 1.5px solid var(--text-secondary);
}

.delivery-rider-chip__dot--live {
  border-color: #10b981;
  background: #10b981;
}

.delivery-rider__save {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font: var(--type-footnote);
  color: var(--text-secondary);
  white-space: nowrap;
}

.delivery-rider__hint {
  margin: 6px 0 0;
  font: var(--type-caption);
  color: var(--text-secondary);
}

.delivery-rider-saved {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font: var(--type-footnote);
  color: var(--text-secondary);
}

.delivery-rider-saved__x {
  border: none;
  background: none;
  color: var(--text-secondary);
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
  padding: 2px 4px;
}

.delivery-rider-saved__x:hover {
  color: var(--danger);
}

.delivery-map {
  margin-top: 12px;
}

.delivery-input {
  flex: 1 1 130px;
  min-width: 0;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--separator);
  border-radius: 999px;
  background: var(--bg-elevated);
  color: var(--text-primary);
  font: var(--type-subhead);
}

.delivery-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 14%, transparent);
}

/* -- The rider at the counter -------------------------------------------- */

.delivery-rider__photo {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

/* The same fallback the app and the tracking page draw. Most riders never
   upload a photo, and an empty disc reads as a broken image. */
.delivery-rider__photo--letter {
  display: grid;
  place-items: center;
  background: var(--accent-soft, #e3efe7);
  color: var(--accent);
  font-weight: 600;
  font-size: 0.85rem;
}

.delivery-rider__bike {
  display: block;
  font-size: 0.78rem;
  font-weight: 400;
  color: var(--text-secondary);
}
</style>
