<script setup lang="ts">
import { ArrowRight, Check, Clock3, CreditCard, X } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { api, type SubscriptionPaymentRow } from './api'
import { pesos, shortDate } from './format'

const rows = ref<SubscriptionPaymentRow[]>([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const busy = ref('')
const showAll = ref(false)
const accepting = ref<SubscriptionPaymentRow | null>(null)
const periodStart = ref('')
const periodEnd = ref('')
const rejecting = ref<SubscriptionPaymentRow | null>(null)
const reason = ref('')

const pending = computed(() => rows.value.filter((row) => row.status === 'submitted'))
const decided = computed(() => rows.value.filter((row) => row.status !== 'submitted'))
const ordered = computed(() => [...pending.value, ...decided.value])
const displayRows = computed(() => showAll.value ? ordered.value : ordered.value.slice(0, 3))

function peso(cents: number): string { return pesos(cents, { exact: true }) }
function isoDate(date: Date): string { return date.toISOString().slice(0, 10) }
function initials(row: SubscriptionPaymentRow): string {
  return (row.organizationName ?? row.organizationSlug ?? 'Seller')
    .split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

async function load() {
  loading.value = true
  error.value = ''
  try { rows.value = (await api.subscriptionPayments()).payments }
  catch (err) { error.value = err instanceof Error ? err.message : 'Could not load payments.' }
  finally { loading.value = false }
}

function openAccept(row: SubscriptionPaymentRow) {
  accepting.value = row
  rejecting.value = null
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
    await api.sellerAction('acceptPayment', { paymentId: row.id, periodStart: periodStart.value, periodEnd: periodEnd.value })
    notice.value = `${row.organizationName ?? row.organizationSlug} is paid to ${shortDate(periodEnd.value)}.`
    accepting.value = null
    await load()
  } catch (err) { error.value = err instanceof Error ? err.message : 'That did not work.' }
  finally { busy.value = '' }
}

async function confirmReject() {
  const row = rejecting.value
  if (!row || !reason.value.trim()) return
  busy.value = row.id
  error.value = ''
  notice.value = ''
  try {
    await api.sellerAction('rejectPayment', { paymentId: row.id, reason: reason.value.trim() })
    notice.value = 'Rejected, and the seller can see why.'
    rejecting.value = null
    await load()
  } catch (err) { error.value = err instanceof Error ? err.message : 'That did not work.' }
  finally { busy.value = '' }
}

onMounted(load)
</script>

<template>
  <section class="adm-card subq">
    <header class="subq__head">
      <span class="subq__headicon"><CreditCard :size="22" /></span>
      <div>
        <h2>Subscription payments</h2>
        <p>Recent subscription payments made by sellers.</p>
      </div>
      <button v-if="ordered.length > 3" type="button" class="subq__view" @click="showAll = !showAll">
        {{ showAll ? 'Show recent' : 'View all payments' }} <ArrowRight :size="15" />
      </button>
    </header>

    <p v-if="error" class="adm-note adm-note--error">{{ error }}</p>
    <p v-if="notice" class="subq__notice">{{ notice }}</p>
    <p v-if="loading" class="adm-note">Loading payments…</p>
    <p v-else-if="!ordered.length" class="subq__empty">No subscription payments have been reported yet.</p>

    <div v-else class="subq__grid" :class="{ 'subq__grid--all': showAll }">
      <article v-for="row in displayRows" :key="row.id" class="subq__card">
        <span class="subq__avatar">{{ initials(row) }}</span>
        <div class="subq__main">
          <strong>{{ row.organizationName ?? row.organizationSlug }}</strong>
          <small>{{ peso(row.amountCents) }} · ref {{ row.reference }}</small>
        </div>
        <div class="subq__meta">
          <time>{{ shortDate(row.submittedAt) }}</time>
          <span v-if="row.status === 'accepted'" class="subq__status subq__status--paid">Paid</span>
          <span v-else-if="row.status === 'rejected'" class="subq__status subq__status--bad">Rejected</span>
          <span v-else class="subq__status subq__status--pending"><Clock3 :size="11" /> Pending</span>
        </div>
        <div v-if="row.status === 'submitted'" class="subq__actions">
          <button type="button" class="subq__accept" :disabled="busy === row.id" @click="openAccept(row)"><Check :size="13" /> Accept</button>
          <button type="button" :disabled="busy === row.id" @click="openReject(row)"><X :size="13" /> Reject</button>
        </div>
      </article>
    </div>

    <div v-if="accepting" class="subq__form">
      <p>What period does {{ peso(accepting.amountCents) }} from <strong>{{ accepting.organizationName ?? accepting.organizationSlug }}</strong> cover?</p>
      <label><span>From</span><input v-model="periodStart" type="date" /></label>
      <label><span>Paid until</span><input v-model="periodEnd" type="date" /></label>
      <div><button type="button" class="subq__confirm" :disabled="busy !== ''" @click="confirmAccept">Confirm payment</button><button type="button" @click="accepting = null">Cancel</button></div>
    </div>

    <div v-if="rejecting" class="subq__form subq__form--reject">
      <p>Why can’t <strong>{{ rejecting.reference }}</strong> be matched? The seller will see this.</p>
      <input v-model="reason" type="text" maxlength="500" placeholder="e.g. No transfer with that reference arrived." />
      <div><button type="button" class="subq__reject" :disabled="busy !== '' || !reason.trim()" @click="confirmReject">Reject payment</button><button type="button" @click="rejecting = null">Cancel</button></div>
    </div>
  </section>
</template>

<style scoped>
.subq { padding: 12px; border-color: #e4e5e3; background: rgba(255, 255, 255, .92); box-shadow: 0 5px 20px rgba(35, 46, 39, .045); }
.subq__head { display: flex; align-items: center; gap: 11px; padding: 0 2px 11px; }
.subq__headicon { display: grid; place-items: center; width: 32px; height: 32px; border: 1.5px solid #202c26; border-radius: 8px; color: #121d17; }
.subq__head h2 { margin: 0; color: #101828; font-size: 15px; line-height: 1.2; }
.subq__head p { margin: 3px 0 0; color: #718096; font-size: 11.5px; }
.subq__view { display: inline-flex; align-items: center; gap: 9px; height: 34px; margin-left: auto; padding: 0 12px; border: 1px solid #dfe3e0; border-radius: 8px; background: #fff; color: #193326; font: inherit; font-size: 11.5px; font-weight: 650; cursor: pointer; }
.subq__grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.subq__grid--all { grid-template-columns: repeat(auto-fit, minmax(285px, 1fr)); }
.subq__card { position: relative; display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 66px; padding: 10px 12px; border: 1px solid #e3e6e3; border-radius: 9px; background: #fff; }
.subq__avatar { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 50%; background: linear-gradient(145deg, #15422e, #80aa5b); color: #fff; font-size: 10px; font-weight: 800; }
.subq__main { display: grid; min-width: 0; gap: 4px; }
.subq__main strong { overflow: hidden; color: #17221c; font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
.subq__main small { overflow: hidden; color: #718096; font-size: 10.5px; text-overflow: ellipsis; white-space: nowrap; }
.subq__meta { display: grid; justify-items: end; gap: 6px; }
.subq__meta time { color: #718096; font-size: 10px; white-space: nowrap; }
.subq__status { display: inline-flex; align-items: center; gap: 4px; padding: 3px 9px; border-radius: 999px; font-size: 10px; font-weight: 700; }
.subq__status--paid { background: #dff6e6; color: #098046; }
.subq__status--bad { background: #fde4e4; color: #a32828; }
.subq__status--pending { background: #fff0dc; color: #9d5c13; }
.subq__actions { grid-column: 2 / -1; display: flex; gap: 6px; justify-content: flex-end; }
.subq__actions button, .subq__form button { display: inline-flex; align-items: center; gap: 5px; height: 28px; padding: 0 9px; border: 1px solid #dfe3e0; border-radius: 7px; background: #fff; color: #26332c; font: inherit; font-size: 10.5px; font-weight: 650; cursor: pointer; }
.subq__actions .subq__accept, .subq__form .subq__confirm { border-color: #0b9650; background: #0b9650; color: #fff; }
.subq__form .subq__reject { border-color: #a32828; background: #a32828; color: #fff; }
.subq__notice, .subq__empty { margin: 0; padding: 16px; border-radius: 8px; background: #f4f7f5; color: #617068; font-size: 12px; text-align: center; }
.subq__notice { margin-bottom: 10px; background: #e9f8ef; color: #08783c; }
.subq__form { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; margin-top: 10px; padding: 12px; border: 1px solid #dfe3e0; border-radius: 9px; background: #fbfcfb; }
.subq__form p { flex: 1 0 100%; margin: 0; color: #445149; font-size: 12px; }
.subq__form label { display: grid; gap: 4px; color: #718096; font-size: 10.5px; }
.subq__form input { height: 32px; min-width: 160px; padding: 0 9px; border: 1px solid #dfe3e0; border-radius: 7px; background: #fff; color: #26332c; font: inherit; font-size: 11.5px; }
.subq__form > div { display: flex; gap: 6px; }
.subq__form--reject > input { flex: 1; min-width: 240px; }
@media (max-width: 1050px) { .subq__grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .subq__head { align-items: flex-start; } .subq__view { padding: 0 9px; } .subq__view svg { display: none; } .subq__card { grid-template-columns: 38px minmax(0, 1fr); } .subq__meta { grid-column: 2; justify-items: start; grid-auto-flow: column; justify-content: start; align-items: center; } .subq__actions { grid-column: 2; justify-content: flex-start; } }
</style>
