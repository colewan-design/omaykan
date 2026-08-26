<script setup lang="ts">
import { computed, ref } from 'vue'
import { Banknote, Plus, Smartphone } from '@lucide/vue'
import { useCustomerAccount, type CustomerPaymentKind } from '@pos/web/commerce/customer'

// Only the two ways an order can actually be paid for today: `paymentMethod`
// on POST /api/online-orders is 'cash' | 'ewallet', and both are settled on
// arrival — nothing is charged when the order is placed. So there is no card
// to store, no token to hold, and this page never touches a card number. The
// note at the bottom says so, because a "Payment methods" page that stays
// empty otherwise reads like something is broken.

const account = useCustomerAccount()

const adding = ref<CustomerPaymentKind | null>(null)
const walletNumber = ref('')

const kindLabel: Record<CustomerPaymentKind, string> = {
  cash: 'Cash on delivery',
  ewallet: 'GCash',
}

const kindHint: Record<CustomerPaymentKind, string> = {
  cash: 'Pay the rider when your order arrives.',
  ewallet: 'Send payment on arrival and show the rider the receipt.',
}

const hasCash = computed(() => account.paymentMethods.value.some((method) => method.kind === 'cash'))

function open(kind: CustomerPaymentKind) {
  adding.value = kind
  walletNumber.value = ''
  // Cash needs no detail — save it the moment it's picked rather than showing
  // a form with nothing in it.
  if (kind === 'cash') {
    account.addPaymentMethod({ kind: 'cash' })
    adding.value = null
  }
}

function saveWallet() {
  const detail = walletNumber.value.trim()
  if (!detail) return
  account.addPaymentMethod({ kind: 'ewallet', detail })
  adding.value = null
}
</script>

<template>
  <div>
    <div class="acct-head">
      <h1 class="acct-head__title">Payment methods</h1>
      <p class="acct-head__sub">
        How you'd like to settle up. Everything is paid on arrival — nothing is charged when you
        place an order.
      </p>
    </div>

    <div v-if="account.paymentMethods.value.length === 0" class="acct-empty">
      <p class="acct-empty__title">No payment method saved</p>
      <p class="acct-empty__note">
        Pick one and checkout will preselect it. You can change it on any single order.
      </p>
    </div>

    <div v-for="method in account.paymentMethods.value" :key="method.id" class="acct-card acct-pay">
      <component
        :is="method.kind === 'cash' ? Banknote : Smartphone"
        class="acct-pay__icon"
        :size="20"
        :stroke-width="1.6"
      />
      <div class="acct-pay__body">
        <p class="acct-card__title">
          {{ kindLabel[method.kind] }}
          <span v-if="method.isDefault" class="acct-tag" style="margin-left: 8px">Default</span>
        </p>
        <p class="acct-card__note">{{ method.detail || kindHint[method.kind] }}</p>

        <div class="acct-actions" style="margin-top: 13px">
          <button
            v-if="!method.isDefault"
            type="button"
            class="acct-link"
            @click="account.setDefaultPaymentMethod(method.id)"
          >
            Make default
          </button>
          <button
            type="button"
            class="acct-link acct-link--danger"
            @click="account.removePaymentMethod(method.id)"
          >
            Remove
          </button>
        </div>
      </div>
    </div>

    <form v-if="adding === 'ewallet'" class="acct-card" @submit.prevent="saveWallet">
      <p class="acct-card__title" style="margin-bottom: 18px">Add GCash</p>
      <label class="acct-field">
        <span class="acct-field__label">GCash number</span>
        <input
          v-model="walletNumber"
          class="acct-input"
          type="tel"
          inputmode="tel"
          placeholder="09XX XXX XXXX"
        />
        <span class="acct-field__hint">
          Kept on this device so you don't retype it. Payment still happens between you and the
          rider on arrival.
        </span>
      </label>
      <div class="acct-actions">
        <button type="submit" class="acct-btn" :disabled="!walletNumber.trim()">Save</button>
        <button type="button" class="acct-link" @click="adding = null">Cancel</button>
      </div>
    </form>

    <div v-else class="acct-pay__add">
      <button v-if="!hasCash" type="button" class="acct-btn acct-btn--ghost" @click="open('cash')">
        <Plus :size="17" :stroke-width="2" />
        Cash on delivery
      </button>
      <button type="button" class="acct-btn acct-btn--ghost" @click="open('ewallet')">
        <Plus :size="17" :stroke-width="2" />
        GCash
      </button>
    </div>

    <p class="acct-pay__foot">
      Cards aren't accepted yet. When they are, they'll be added here — and the number will live
      with the payment processor, never on this page.
    </p>
  </div>
</template>

<style scoped>
.acct-pay {
  display: flex;
  gap: 16px;
}

.acct-pay__icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: #6b7280;
}

.acct-pay__body {
  min-width: 0;
}

.acct-pay__add {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.acct-pay__foot {
  margin: 22px 0 0;
  max-width: 56ch;
  font-size: 13px;
  line-height: 1.6;
  color: #9ca3af;
}
</style>
