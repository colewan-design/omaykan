<script setup lang="ts">
import { computed } from 'vue'
import { Check, Copy, Share2 } from '@lucide/vue'
import { useClipboard, useCustomerAccount } from '@pos/web/commerce/customer'

// The code is derived from the customer's email (customer.ts), so it's stable
// and needs no server to issue it. What a referral is *worth* is the shop's
// call, not this page's — so nothing here promises an amount. Fill that in
// when the offer is decided.

const account = useCustomerAccount()
const { copied, copy } = useClipboard()

const canShare = computed(() => typeof navigator !== 'undefined' && 'share' in navigator)

async function share() {
  try {
    await navigator.share({
      title: 'Omaykan',
      text: 'Groceries from shops around Baguio, delivered the same day.',
      url: account.referralLink.value,
    })
  } catch {
    // Dismissed the sheet, or the browser refused — the link is on screen.
  }
}
</script>

<template>
  <div>
    <div class="acct-head">
      <h1 class="acct-head__title">Refer a friend</h1>
      <p class="acct-head__sub">
        Send someone your link. When they place their first order, it's tagged to you — and to the
        shop they ordered from, which keeps every peso either way.
      </p>
    </div>

    <div class="acct-card">
      <p class="acct-card__title">Your code</p>
      <p class="acct-ref__code">{{ account.referralCode.value }}</p>

      <div class="acct-ref__link">
        <input class="acct-input acct-ref__input" :value="account.referralLink.value" readonly @focus="($event.target as HTMLInputElement).select()" />
        <button type="button" class="acct-btn acct-btn--ghost" @click="copy(account.referralLink.value, 'link')">
          <component :is="copied === 'link' ? Check : Copy" :size="16" :stroke-width="2" />
          {{ copied === 'link' ? 'Copied' : 'Copy link' }}
        </button>
      </div>

      <div v-if="canShare" class="acct-actions">
        <button type="button" class="acct-btn" @click="share">
          <Share2 :size="16" :stroke-width="2" />
          Share
        </button>
      </div>
    </div>

    <div class="acct-card">
      <p class="acct-card__title">How it works</p>
      <ol class="acct-ref__steps">
        <li>Send the link to someone who shops in Baguio.</li>
        <li>They open it and order from any shop on Omaykan.</li>
        <li>Their first order carries your code, so we know it came from you.</li>
      </ol>
      <p class="acct-card__note acct-ref__foot">
        Referral rewards are set by each shop. Nothing is credited to your account until a shop
        confirms it.
      </p>
    </div>
  </div>
</template>

<style scoped>
.acct-ref__code {
  margin: 6px 0 20px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #1a6b3c;
}

.acct-ref__link {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.acct-ref__input {
  flex: 1;
  min-width: 200px;
  background: #f7f7f7;
  color: #6b7280;
  font-size: 14px;
}

.acct-ref__steps {
  margin: 12px 0 0;
  padding-left: 20px;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.75;
}

.acct-ref__foot {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e8eae9;
  color: #9ca3af;
}
</style>
