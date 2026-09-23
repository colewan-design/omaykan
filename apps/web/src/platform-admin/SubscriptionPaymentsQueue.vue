<script setup lang="ts">
import { Check, Clock, X } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { api, type SubscriptionPaymentRow } from './api'
import { pesos, shortDate } from './format'

/**
 * The operator's side of manual collection: transfers merchants say they made.
 *
 * There is no gateway (documentation/plan.md §4a), so this queue *is* billing.
 * Accepting a row is the only thing in the whole product that writes a
 * subscription's billing period — everything else leaves shops resting on
 * their trial.
 *
 * Accepting therefore asks for the period rather than inferring it. A merchant
 * who pays for two months at once, or pays short, is a conversation; guessing
 * from the amount would turn that into a silent arithmetic error on somebody's
 * right to trade.
 */

const rows = ref<SubscriptionPaymentRow[]>([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const busy = ref('')

/** The row being accepted, with the dates the operator is choosing. */
const accepting = ref<SubscriptionPaymentRow | null>(null)
const periodStart = ref('')
const periodEnd = ref('')

/** The row being rejected, with the reason the merchant will read. */
const rejecting = ref<SubscriptionPaymentRow | null>(null)
const reason = ref('')

const pending = computed(() => rows.value.filter((row) => row.status === 'submitted'))
const decided = computed(() => rows.value.filter((row) => row.status !== 'submitted').slice(0, 10))

/**
 * `exact` everywhere here: these are single transactions an operator is
 * reconciling against a GCash screenshot, which is precisely the case the
 * shared formatter's whole-peso default is not for.
 */
function peso(cents: number): string {
  return pesos(cents, { exact: true })
}

const when = shortDate

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

async function load() {
  loading.value = true
  try {
    rows.value = (await api.subscriptionPayments()).payments
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load payments.'
  } finally {
    loading.value = false
  }
}

function openAccept(row: SubscriptionPaymentRow) {
  accepting.value = row
  rejecting.value = null

  // A month from today, as the obvious default. The operator changes it when
  // the transfer says otherwise, which is the case this form exists for.
  const today = new Date()
  const nextMonth = new Date(today)
  nextMonth.setMonth(nextMonth.getMonth() + 1)

  periodStart.value = isoDate(today)
  periodEnd.value = isoDate(nextMonth)
}

function openReject(row: SubscriptionPaymentRow) {
  rejecting.value = row
  accepting.value = null
  reason.value = ''
}

async function confirmAccept() {
  const row = accepting.value
  if (!row) return

  busy.value = row.id
  error.value = ''
  notice.value = ''

  try {
    await api.sellerAction('acceptPayment', {
      paymentId: row.id,
      periodStart: periodStart.value,
      periodEnd: periodEnd.value,
    })
    notice.value = `${row.organizationName ?? row.organizationSlug} is paid to ${when(periodEnd.value)}.`
    accepting.value = null
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'That did not work.'
  } finally {
    busy.value = ''
  }
}

async function confirmReject() {
  const row = rejecting.value
  if (!row || !reason.value.trim()) return

  busy.value = row.id
  error.value = ''
  notice.value = ''

  try {
    await api.sellerAction('rejectPayment', { paymentId: row.id, reason: reason.value.trim() })
    notice.value = 'Rejected, and the shop can see why.'
    rejecting.value = null
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'That did not work.'
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="adm-card subq">
    <header class="subq__head">
      <div>
        <h2 class="subq__title">Subscription payments</h2>
        <p class="subq__sub">
          Transfers merchants say they've made. Accepting one is what sets their paid-to date.
        </p>
      </div>
      <span v-if="pending.length" class="subq__badge">{{ pending.length }} waiting</span>
    </header>

    <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>
    <p v-if="notice" class="subq__notice">{{ notice }}</p>
    <p v-if="loading" class="adm-note">Loading payments…</p>

    <p v-else-if="!pending.length && !decided.length" class="adm-note">
      No payments reported yet. Merchants record transfers from their register,
      under Settings &rsaquo; Subscription.
    </p>

    <ul v-else class="subq__list">
      <li v-for="row in pending" :key="row.id" class="subq__row">
        <Clock :size="16" class="subq__icon" aria-hidden="true" />

        <div class="subq__main">
          <p class="subq__shop">{{ row.organizationName ?? row.organizationSlug }}</p>
          <p class="subq__detail">
            <strong>{{ peso(row.amountCents) }}</strong> &middot; ref {{ row.reference }}
            &middot; {{ when(row.submittedAt) }}
            <span v-if="row.submittedBy"> &middot; {{ row.submittedBy }}</span>
          </p>
          <p v-if="row.note" class="subq__note">“{{ row.note }}”</p>
        </div>

        <div class="subq__actions">
          <button type="button" class="subq__btn subq__btn--ok" :disabled="busy === row.id" @click="openAccept(row)">
            <Check :size="15" aria-hidden="true" /> Accept
          </button>
          <button type="button" class="subq__btn" :disabled="busy === row.id" @click="openReject(row)">
            <X :size="15" aria-hidden="true" /> Reject
          </button>
        </div>
      </li>

      <li v-for="row in decided" :key="row.id" class="subq__row subq__row--done">
        <Check v-if="row.status === 'accepted'" :size="16" class="subq__icon subq__icon--ok" aria-hidden="true" />
        <X v-else :size="16" class="subq__icon subq__icon--bad" aria-hidden="true" />

        <div class="subq__main">
          <p class="subq__shop">{{ row.organizationName ?? row.organizationSlug }}</p>
          <p class="subq__detail">
            {{ peso(row.amountCents) }} &middot; ref {{ row.reference }}
            <span v-if="row.status === 'accepted' && row.periodEnd"> &middot; paid to {{ when(row.periodEnd) }}</span>
            <span v-else-if="row.rejectionReason"> &middot; {{ row.rejectionReason }}</span>
          </p>
        </div>
      </li>
    </ul>

    <!-- Accept: the operator says what the money bought. -->
    <div v-if="accepting" class="subq__form">
      <p class="subq__formtitle">
        What does {{ peso(accepting.amountCents) }} from
        {{ accepting.organizationName ?? accepting.organizationSlug }} cover?
      </p>
      <div class="subq__fields">
        <label class="subq__field">
          <span>From</span>
          <input v-model="periodStart" type="date" />
        </label>
        <label class="subq__field">
          <span>Paid until</span>
          <input v-model="periodEnd" type="date" />
        </label>
      </div>
      <div class="subq__formactions">
        <button type="button" class="subq__btn subq__btn--ok" :disabled="busy !== ''" @click="confirmAccept">
          Confirm
        </button>
        <button type="button" class="subq__btn" @click="accepting = null">Cancel</button>
      </div>
    </div>

    <!-- Reject: a reason is required, because the merchant reads it. -->
    <div v-if="rejecting" class="subq__form">
      <p class="subq__formtitle">
        Why can't {{ rejecting.reference }} be matched? The shop will see this.
      </p>
      <input
        v-model="reason"
        class="subq__reason"
        type="text"
        maxlength="500"
        placeholder="e.g. No transfer with that reference arrived."
      />
      <div class="subq__formactions">
        <button type="button" class="subq__btn" :disabled="busy !== '' || !reason.trim()" @click="confirmReject">
          Reject payment
        </button>
        <button type="button" class="subq__btn" @click="rejecting = null">Cancel</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.subq {
  margin-bottom: 18px;
}

.subq__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.subq__title {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}

.subq__sub {
  margin: 3px 0 0;
  font-size: 13px;
  color: var(--adm-muted, #64748b);
}

.subq__badge {
  margin-left: auto;
  padding: 3px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, #d97706 15%, transparent);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.subq__notice {
  margin: 0 0 10px;
  font-size: 13px;
  color: #15803d;
}

.subq__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.subq__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 0;
  border-top: 1px solid var(--adm-line, #e2e8f0);
}

.subq__row--done {
  opacity: 0.62;
}

.subq__icon {
  flex: none;
  margin-top: 2px;
  color: #d97706;
}

.subq__icon--ok {
  color: #15803d;
}

.subq__icon--bad {
  color: #b91c1c;
}

.subq__main {
  flex: 1;
  min-width: 0;
}

.subq__shop {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.subq__detail {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--adm-muted, #64748b);
}

.subq__note {
  margin: 3px 0 0;
  font-size: 13px;
  font-style: italic;
  color: var(--adm-muted, #64748b);
}

.subq__actions {
  display: flex;
  flex: none;
  gap: 6px;
}

.subq__btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border: 1px solid var(--adm-line, #e2e8f0);
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  cursor: pointer;
}

.subq__btn--ok {
  border-color: transparent;
  background: #15803d;
  color: #fff;
}

.subq__btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.subq__form {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--adm-line, #e2e8f0);
  border-radius: 10px;
}

.subq__formtitle {
  margin: 0 0 10px;
  font-size: 13px;
}

.subq__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.subq__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--adm-muted, #64748b);
}

.subq__field input,
.subq__reason {
  padding: 7px 9px;
  border: 1px solid var(--adm-line, #e2e8f0);
  border-radius: 8px;
  font-size: 13px;
}

.subq__reason {
  width: 100%;
}

.subq__formactions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
</style>
