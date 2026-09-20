<script setup lang="ts">
import { CheckCircle2, Clock, CreditCard, XCircle } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import type { SubscriptionOverview } from '@pos/data/index'
import { getPosRepository } from '@pos/core/services/runtime'

/**
 * What the shop owes us, and how it says it paid.
 *
 * Two ways to pay, and the panel leads with the one that finishes by itself:
 * GCash through PayMongo, which settles without anybody reviewing it, and the
 * manual transfer underneath for a shop that would rather send money the way
 * it always has (documentation/plan.md §4a).
 *
 * The Pay button only appears when the server says the gateway is configured
 * — `/checkout` answers 503 otherwise, and an install with no keys shows
 * exactly what it showed before: a price, a reference field, and what happened
 * to it.
 *
 * Renders nothing on a till with no server behind it, or for anyone but the
 * owner: the endpoint answers 403 and the repository turns that into null.
 */

const repository = getPosRepository()

const overview = ref<SubscriptionOverview | null>(null)
const reference = ref('')
const amountPesos = ref<number | null>(null)
const note = ref('')
const saving = ref(false)
const error = ref('')
const sent = ref(false)

const payingOnline = ref(false)
const gatewayError = ref('')
/** Hidden until the server proves it can take a payment. See `probeGateway`. */
const gatewayReady = ref(false)

const pending = computed(() =>
  overview.value?.payments.find((payment) => payment.status === 'submitted') ?? null,
)

const recent = computed(() => overview.value?.payments.slice(0, 5) ?? [])

const priceLabel = computed(() => peso(overview.value?.plan.amountCents ?? 0))

/**
 * Shown only when the shop's signup price differs from today's. Most shops
 * will never see it; the one whose price has moved should not find out from a
 * dunning email.
 */
const agreedDiffers = computed(() => {
  const subscription = overview.value?.subscription
  if (!subscription || !overview.value) return false
  return subscription.agreedAmountCents !== overview.value.plan.amountCents
})

const standing = computed(() => {
  const subscription = overview.value?.subscription
  if (!subscription) return null

  if (subscription.trialEndsAt && new Date(subscription.trialEndsAt) > new Date()) {
    return { tone: 'ok' as const, text: `Free until ${date(subscription.trialEndsAt)}` }
  }

  if (subscription.currentPeriodEndsAt) {
    const ends = new Date(subscription.currentPeriodEndsAt)
    return ends > new Date()
      ? { tone: 'ok' as const, text: `Paid until ${date(subscription.currentPeriodEndsAt)}` }
      : { tone: 'warn' as const, text: `Ran out on ${date(subscription.currentPeriodEndsAt)}` }
  }

  return null
})

function peso(cents: number): string {
  return `₱${(cents / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function date(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString([], { day: 'numeric', month: 'long', year: 'numeric' })
}

async function submit() {
  const amount = Math.round((amountPesos.value ?? 0) * 100)

  if (!reference.value.trim()) {
    error.value = 'Enter the reference number from your transfer.'
    return
  }

  if (amount < 1) {
    error.value = 'Enter how much you sent.'
    return
  }

  saving.value = true
  error.value = ''

  try {
    await repository.submitSubscriptionPayment({
      reference: reference.value.trim(),
      amountCents: amount,
      note: note.value.trim() || undefined,
    })

    reference.value = ''
    note.value = ''
    sent.value = true
    overview.value = await repository.loadSubscription()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'That did not send. Try again.'
  } finally {
    saving.value = false
  }
}

/**
 * Send the merchant to GCash.
 *
 * The row is written server-side before this returns, so an abandoned
 * checkout is still something an operator can look up rather than a gap.
 */
async function payOnline() {
  payingOnline.value = true
  gatewayError.value = ''

  try {
    const checkout = await repository.startSubscriptionCheckout()
    // Remembered because the merchant leaves the app entirely; on the way
    // back there is nothing else to say which checkout they were on.
    sessionStorage.setItem('omaykan.subscription.checkout', checkout.id)
    window.location.href = checkout.checkoutUrl
  } catch (err) {
    gatewayError.value = err instanceof Error ? err.message : 'Could not open the payment page.'
    payingOnline.value = false
  }
}

/**
 * Coming back from GCash.
 *
 * Only ever an accelerator: the webhook settles the same checkout whether or
 * not the merchant returns to this screen. So a failure here is swallowed —
 * telling somebody their payment failed when it has merely not been confirmed
 * yet would be worse than saying nothing.
 */
async function settleOnReturn() {
  const sessionId = sessionStorage.getItem('omaykan.subscription.checkout')

  if (!sessionId) return

  sessionStorage.removeItem('omaykan.subscription.checkout')

  try {
    await repository.settleSubscriptionCheckout(sessionId)
  } catch {
    // Left to the webhook.
  }
}

onMounted(async () => {
  await settleOnReturn()
  overview.value = await repository.loadSubscription()
  gatewayReady.value = overview.value?.gatewayReady ?? false
  // Pre-fill with what we are actually asking for, so the common case is one
  // field and a button.
  amountPesos.value = (overview.value?.plan.amountCents ?? 0) / 100
})
</script>

<template>
  <div v-if="overview" class="subs">
    <div class="subs__head">
      <p class="subs__price">
        <strong>{{ priceLabel }}</strong><span class="subs__per">/ month</span>
      </p>
      <p v-if="standing" class="subs__standing" :class="`subs__standing--${standing.tone}`">
        {{ standing.text }}
      </p>
    </div>

    <p v-if="agreedDiffers" class="subs__note">
      You signed up at {{ peso(overview.subscription!.agreedAmountCents) }} a month.
      The current price is {{ priceLabel }} — we will confirm what applies to you
      before anything changes.
    </p>

    <!--
      Pay online first, because it is the path that finishes by itself. The
      manual route stays underneath, unchanged, for a shop that would rather
      send money the way it always has — and it is the only route at all on
      an install with no PayMongo keys.
    -->
    <div v-if="gatewayReady && !pending" class="subs__pay">
      <button type="button" class="subs__paybtn" :disabled="payingOnline" @click="payOnline">
        {{ payingOnline ? 'Opening GCash…' : `Pay ${priceLabel} with GCash` }}
      </button>
      <p class="subs__payhint">
        Opens GCash. Your subscription updates as soon as the payment clears —
        nobody has to check it by hand.
      </p>
      <p v-if="gatewayError" class="subs__error">{{ gatewayError }}</p>
    </div>

    <p class="subs__how">
      <template v-if="gatewayReady">Would rather transfer it yourself? Send your</template>
      <template v-else>Send your</template>
      {{ overview.howToPay.method.toLowerCase() }}, then record the
      reference below so we can match it. Questions:
      <a :href="`mailto:${overview.howToPay.supportEmail}`">{{ overview.howToPay.supportEmail }}</a>.
    </p>

    <!-- One open claim at a time: the server refuses a second, so showing the
         form would only produce an error the merchant did not earn. -->
    <div v-if="pending" class="subs__pending">
      <Clock :size="16" aria-hidden="true" />
      <span>
        <strong>{{ peso(pending.amountCents) }}</strong> &middot; {{ pending.reference }} —
        waiting for us to check it. We will email you.
      </span>
    </div>

    <form v-else class="subs__form" @submit.prevent="submit">
      <label class="subs__field">
        <span class="subs__label">Reference number</span>
        <input
          v-model="reference"
          class="subs__input"
          type="text"
          maxlength="120"
          placeholder="e.g. 1234 5678 9012"
          :disabled="saving"
        >
      </label>

      <label class="subs__field subs__field--amount">
        <span class="subs__label">Amount sent</span>
        <input
          v-model.number="amountPesos"
          class="subs__input"
          type="number"
          min="1"
          step="0.01"
          :disabled="saving"
        >
      </label>

      <label class="subs__field subs__field--wide">
        <span class="subs__label">Note <em>(optional)</em></span>
        <input
          v-model="note"
          class="subs__input"
          type="text"
          maxlength="500"
          placeholder="e.g. paid for two months"
          :disabled="saving"
        >
      </label>

      <button class="subs__submit" type="submit" :disabled="saving">
        <CreditCard :size="16" aria-hidden="true" />
        {{ saving ? 'Sending…' : 'Record this payment' }}
      </button>
    </form>

    <p v-if="error" class="subs__error" role="alert">{{ error }}</p>
    <p v-else-if="sent && !pending" class="subs__sent">Thanks — we will confirm by email.</p>

    <ul v-if="recent.length" class="subs__history">
      <li v-for="payment in recent" :key="payment.id" class="subs__row">
        <CheckCircle2 v-if="payment.status === 'accepted'" :size="15" class="subs__icon subs__icon--ok" aria-hidden="true" />
        <XCircle v-else-if="payment.status === 'rejected'" :size="15" class="subs__icon subs__icon--bad" aria-hidden="true" />
        <Clock v-else :size="15" class="subs__icon" aria-hidden="true" />
        <span class="subs__rowmain">
          {{ peso(payment.amountCents) }} &middot; {{ payment.reference }}
          <em v-if="payment.status === 'accepted' && payment.periodEnd">
            — covers you to {{ date(payment.periodEnd) }}
          </em>
          <em v-else-if="payment.status === 'rejected'">— {{ payment.rejectionReason }}</em>
        </span>
        <span class="subs__when">{{ date(payment.submittedAt) }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.subs {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
}

.subs__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 12px;
}

.subs__price {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.subs__per {
  margin-left: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

.subs__standing {
  margin: 0;
  margin-left: auto;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
  background: color-mix(in srgb, var(--success, #1a6b3c) 12%, transparent);
  color: var(--text-primary);
}

.subs__standing--warn {
  background: color-mix(in srgb, var(--warning) 16%, transparent);
}

.subs__note,
.subs__how {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
}

.subs__pending {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--warning) 12%, transparent);
  font-size: 13px;
  color: var(--text-primary);
}

.subs__form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.subs__field {
  display: flex;
  flex: 1 1 180px;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.subs__field--amount {
  flex: 0 1 140px;
}

.subs__field--wide {
  flex: 1 1 100%;
}

.subs__label {
  font-size: 12px;
  color: var(--text-secondary);
}

.subs__label em {
  font-style: normal;
  opacity: 0.7;
}

.subs__input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  color: var(--text-primary);
  font-size: 14px;
}

.subs__submit {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  align-self: flex-end;
  padding: 9px 16px;
  border: 0;
  border-radius: 999px;
  background: var(--accent);
  color: var(--accent-contrast, #fff);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.subs__submit:disabled {
  opacity: 0.6;
  cursor: default;
}

.subs__error {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--danger);
}

.subs__sent {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.subs__history {
  margin: 14px 0 0;
  padding: 12px 0 0;
  border-top: 1px solid var(--separator);
  list-style: none;
}

.subs__row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
  color: var(--text-primary);
}

.subs__icon {
  flex: none;
  color: var(--text-secondary);
}

.subs__icon--ok {
  color: var(--success, #1a6b3c);
}

.subs__icon--bad {
  color: var(--danger);
}

.subs__rowmain {
  flex: 1;
  min-width: 0;
}

.subs__rowmain em {
  font-style: normal;
  color: var(--text-secondary);
}

.subs__when {
  flex: none;
  color: var(--text-secondary);
}

.subs__pay {
  margin: 0 0 1rem;
}

.subs__paybtn {
  width: 100%;
  padding: 0.85rem 1rem;
  border: 0;
  border-radius: 0.6rem;
  background: #0f7a3d;
  color: #fff;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
}

.subs__paybtn:disabled {
  opacity: 0.6;
  cursor: default;
}

.subs__payhint {
  margin: 0.5rem 0 0;
  font-size: 0.82rem;
  opacity: 0.75;
}
</style>
