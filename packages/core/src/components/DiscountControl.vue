<script setup lang="ts">
import { Tag, X } from '@lucide/vue'
import { computed, ref } from 'vue'
import { formatCurrency, maxDiscountPercentFor } from '@pos/shared/index'
import { useAuthStore } from '@pos/core/stores/auth'
import { usePosStore } from '@pos/core/stores/pos'

/**
 * Take something off this sale.
 *
 * A row in the totals card of every register layout: the discount when there
 * is one, "Add discount" when there is not. Percentage or peso amount, and a
 * reason, because a discount nobody can explain at the end of the shift is
 * indistinguishable from money gone missing.
 *
 * A promo code is the third way in, and the selected customer's points the
 * fourth: both checked by the server while the till is online, and neither the
 * cashier's discretion, so no role limit applies to them.
 *
 * The signed-in person's role decides how much they may give on their own
 * (Roles → discount limit; cashiers none by default). Over that, it is
 * refused here with a sentence saying so — a manager signs in to give it —
 * rather than sent for the server to flag after the customer has paid.
 *
 * See documentation/merchant-features.md §7.
 */

const store = usePosStore()
const auth = useAuthStore()

const open = ref(false)
const mode = ref<'percent' | 'amount' | 'code' | 'points'>('percent')

/** Points only for a customer who agreed to be enrolled. */
const canSpendPoints = computed(() => Boolean(store.selectedCustomer?.loyaltyConsentAt))
const checking = ref(false)
const value = ref('')
const reason = ref('')
const error = ref('')

const limit = computed(() => maxDiscountPercentFor(auth.currentRole))

const label = computed(() => {
  const applied = store.appliedDiscount
  if (!applied) return ''
  if (applied.kind === 'loyalty') {
    return `${applied.points ?? ''} points`.trim()
  }
  if (applied.kind === 'promo') {
    return applied.percent != null ? `${applied.reason} · ${applied.percent}% off` : `Code ${applied.reason}`
  }
  const what = applied.percent != null ? `${applied.percent}% off` : 'Discount'
  return applied.reason ? `${what} · ${applied.reason}` : what
})

function start() {
  error.value = ''
  value.value = ''
  reason.value = ''
  open.value = true
}

async function apply() {
  if (mode.value === 'code' || mode.value === 'points') {
    if (checking.value) return
    checking.value = true
    error.value = ''
    try {
      const refusal = mode.value === 'code'
        ? await store.applyPromoCode(value.value)
        : await store.applyLoyaltyPoints(Number(value.value))
      if (refusal) {
        error.value = refusal
        return
      }
      open.value = false
    } finally {
      checking.value = false
    }
    return
  }

  const number = Number(value.value.replace(/,/g, ''))
  if (!value.value.trim() || !Number.isFinite(number)) {
    error.value = mode.value === 'percent' ? 'Enter a percentage.' : 'Enter an amount.'
    return
  }

  const refusal = store.applyDiscount(
    mode.value === 'percent'
      ? { kind: 'manual', percent: number, reason: reason.value }
      : { kind: 'manual', amountCents: Math.round(number * 100), reason: reason.value },
    limit.value,
  )

  if (refusal) {
    error.value = refusal
    return
  }

  open.value = false
}
</script>

<template>
  <div v-if="store.appliedDiscount" class="totals-row discount-row">
    <span class="discount-row__label">
      <Tag :size="14" aria-hidden="true" />
      {{ label }}
      <button class="discount-row__remove" type="button" aria-label="Remove discount" @click="store.removeDiscount()">
        <X :size="14" />
      </button>
    </span>
    <strong class="discount-row__amount">−{{ formatCurrency(store.discountCents) }}</strong>
  </div>

  <template v-else-if="store.cartLines.length > 0">
    <button v-if="!open" class="discount-add" type="button" @click="start">
      <Tag :size="14" aria-hidden="true" />
      Add discount
    </button>

    <form v-else class="discount-form" @submit.prevent="apply">
      <div class="discount-form__mode" role="radiogroup" aria-label="Discount type">
        <button
          type="button"
          role="radio"
          :aria-checked="mode === 'percent'"
          :class="{ 'is-on': mode === 'percent' }"
          @click="mode = 'percent'"
        >
          %
        </button>
        <button
          type="button"
          role="radio"
          :aria-checked="mode === 'amount'"
          :class="{ 'is-on': mode === 'amount' }"
          @click="mode = 'amount'"
        >
          ₱
        </button>
        <button
          type="button"
          role="radio"
          :aria-checked="mode === 'code'"
          :class="{ 'is-on': mode === 'code' }"
          @click="mode = 'code'"
        >
          Code
        </button>
        <button
          v-if="canSpendPoints"
          type="button"
          role="radio"
          :aria-checked="mode === 'points'"
          :class="{ 'is-on': mode === 'points' }"
          @click="mode = 'points'"
        >
          Points
        </button>
      </div>
      <input
        v-if="mode === 'code'"
        v-model="value"
        class="discount-form__value discount-form__code"
        autocapitalize="characters"
        maxlength="40"
        placeholder="WELCOME10"
        aria-label="Promo code"
      />
      <input
        v-else-if="mode === 'points'"
        v-model="value"
        class="discount-form__value"
        inputmode="numeric"
        placeholder="50"
        :aria-label="`Points to spend for ${store.selectedCustomer?.name ?? 'this customer'}`"
      />
      <input
        v-else
        v-model="value"
        class="discount-form__value"
        inputmode="decimal"
        :placeholder="mode === 'percent' ? '10' : '20.00'"
        :aria-label="mode === 'percent' ? 'Percentage off' : 'Amount off'"
      />
      <input
        v-if="mode !== 'code' && mode !== 'points'"
        v-model="reason"
        class="discount-form__reason"
        maxlength="120"
        placeholder="Reason (optional)"
        aria-label="Reason"
      />
      <div class="discount-form__actions">
        <button class="discount-form__apply" type="submit" :disabled="checking">
          {{ checking ? 'Checking…' : 'Apply' }}
        </button>
        <button class="discount-form__cancel" type="button" @click="open = false">Cancel</button>
      </div>
      <p v-if="error" class="discount-form__error" role="alert">{{ error }}</p>
      <p v-else-if="mode === 'code'" class="discount-form__hint">Checked with Omaykan — needs this till online.</p>
      <p v-else-if="mode === 'points'" class="discount-form__hint">
        {{ store.selectedCustomer?.name }}'s balance is checked with Omaykan — needs this till online.
      </p>
      <p v-else-if="limit < 100" class="discount-form__hint">Your role can give up to {{ limit }}% off.</p>
    </form>
  </template>
</template>

<style scoped>
.discount-row__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--accent);
}

.discount-row__amount {
  color: var(--accent);
}

.discount-row__remove {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--fill);
  color: var(--text-secondary);
  cursor: pointer;
}

.discount-add {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  padding: 4px 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--accent);
  cursor: pointer;
}

.discount-form {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
}

.discount-form__mode {
  display: inline-flex;
  border: 1px solid var(--separator);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.discount-form__mode button {
  min-width: 36px;
  padding: 6px 10px;
  border: 0;
  background: none;
  font: inherit;
  font-weight: 700;
  color: var(--text-secondary);
  cursor: pointer;
}

.discount-form__mode button.is-on {
  background: var(--accent);
  color: var(--accent-text-on);
}

.discount-form__value,
.discount-form__reason {
  min-width: 0;
  padding: 7px 10px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  font: inherit;
  color: var(--text-primary);
}

.discount-form__reason {
  grid-column: 1 / -1;
}

.discount-form__actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 8px;
}

.discount-form__apply,
.discount-form__cancel {
  padding: 7px 14px;
  border-radius: var(--radius-pill);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.discount-form__apply {
  border: 0;
  background: var(--accent);
  color: var(--accent-text-on);
}

.discount-form__cancel {
  border: 1px solid var(--separator);
  background: none;
  color: var(--text-primary);
}

.discount-form__code {
  text-transform: uppercase;
}

.discount-form__apply:disabled {
  opacity: 0.6;
  cursor: default;
}

.discount-form__error,
.discount-form__hint {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 12px;
}

.discount-form__error {
  color: var(--danger);
}

.discount-form__hint {
  color: var(--text-secondary);
}
</style>
