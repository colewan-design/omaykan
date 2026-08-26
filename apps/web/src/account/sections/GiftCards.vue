<script setup lang="ts">
import { ref } from 'vue'
import { Check, Copy, Gift } from '@lucide/vue'
import { formatCompactDate } from '@pos/shared/index'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'
import { useClipboard, useCustomerAccount } from '@pos/web/commerce/customer'

// A card saved here is a code kept for checkout, not a balance.
//
// Only the shop that sold a gift card knows what's left on it, and there is no
// endpoint to ask. Crediting the balance in the browser would be inventing
// money on screen, so this page does the one thing it honestly can: hold the
// code so it doesn't get lost, and say plainly when it's worth something.

const account = useCustomerAccount()
const { copied, copy } = useClipboard()

const code = ref('')
const error = ref('')

function save() {
  const entered = code.value.trim()
  if (!entered) return
  if (!account.saveGiftCard(entered)) {
    error.value = 'That code is already saved.'
    return
  }
  error.value = ''
  code.value = ''
}
</script>

<template>
  <div>
    <div class="acct-head">
      <h1 class="acct-head__title">Gift cards</h1>
      <p class="acct-head__sub">
        Keep a code here and it'll be waiting at checkout. The shop applies it and confirms what
        it's worth — the balance then shows under Store credit.
      </p>
    </div>

    <form class="acct-card acct-gift__form" @submit.prevent="save">
      <label class="acct-field" style="margin: 0; flex: 1; min-width: 220px">
        <span class="acct-field__label">Gift card code</span>
        <input v-model="code" class="acct-input" type="text" placeholder="OMYK-XXXX-XXXX" />
      </label>
      <button type="submit" class="acct-btn" :disabled="!code.trim()">Save code</button>
    </form>
    <p v-if="error" class="acct-flash acct-flash--error">{{ error }}</p>

    <div v-if="account.giftCards.value.length === 0" class="acct-empty" style="margin-top: 16px">
      <Gift :size="26" :stroke-width="1.4" style="color: #9ca3af; margin: 0 auto 12px" />
      <p class="acct-empty__title">No gift cards saved</p>
      <p class="acct-empty__note">
        Want to send one? Write to
        <a :href="supportMailto('Omaykan gift card')" class="acct-gift__mail">{{ SUPPORT_EMAIL }}</a>
        and we'll set it up with the shop.
      </p>
    </div>

    <ul v-else class="acct-gift__list">
      <li v-for="card in account.giftCards.value" :key="card.id" class="acct-card acct-gift__row">
        <div class="acct-gift__body">
          <p class="acct-gift__code">{{ card.code }}</p>
          <p class="acct-card__note">Saved {{ formatCompactDate(card.addedAt) }} · applies at checkout</p>
        </div>
        <div class="acct-gift__acts">
          <button type="button" class="acct-link acct-gift__copy" @click="copy(card.code, card.id)">
            <component :is="copied === card.id ? Check : Copy" :size="13" :stroke-width="2" />
            {{ copied === card.id ? 'Copied' : 'Copy' }}
          </button>
          <button type="button" class="acct-link acct-link--danger" @click="account.removeGiftCard(card.id)">
            Remove
          </button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.acct-gift__form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 14px;
}

.acct-gift__mail {
  color: #1a6b3c;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.acct-gift__list {
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.acct-gift__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.acct-gift__body {
  min-width: 0;
}

.acct-gift__code {
  margin: 0 0 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 15.5px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #1a1a1a;
}

.acct-gift__acts {
  display: flex;
  align-items: center;
  gap: 16px;
}

.acct-gift__copy {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
}
</style>
