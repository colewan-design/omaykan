<script setup lang="ts">
import { formatCurrency } from '@pos/shared/index'
import { useCustomerAccount } from '@pos/web/commerce/customer'

// The balance is zero and says so, because there is nothing server-side that
// could make it anything else — see customer.ts. There is no ledger to list
// under it for the same reason: the API has no credit entries to return, and
// a figure with nothing behind it is a number nobody can explain. This one is
// money, so it stays honest until a real ledger exists to read.

const account = useCustomerAccount()
</script>

<template>
  <div>
    <div class="acct-head">
      <h1 class="acct-head__title">Store credit</h1>
      <p class="acct-head__sub">
        Credit comes off your total automatically at checkout, before any delivery fee.
      </p>
    </div>

    <div class="acct-card acct-balance">
      <p class="acct-balance__label">Available</p>
      <p class="acct-balance__amount">{{ formatCurrency(account.storeCreditCents.value) }}</p>
    </div>

    <div class="acct-empty" style="margin-top: 16px">
      <p class="acct-empty__title">Nothing here yet</p>
      <p class="acct-empty__note">
        Credit lands here when a shop refunds part of an order — an item that turned out to be out
        of stock, say — or when a redeemed gift card is confirmed.
      </p>
    </div>
  </div>
</template>

<style scoped>
.acct-balance {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.acct-balance__label {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: #9ca3af;
}

.acct-balance__amount {
  margin: 0;
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #1a1a1a;
}

</style>
