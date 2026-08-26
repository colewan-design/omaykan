<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { MapPin, Plus } from '@lucide/vue'
import { useCustomerAccount } from '@pos/web/commerce/customer'

// Places a rider can be sent. One of them is the default, which is the one
// checkout opens on — a list with no default would make the customer choose
// again on every order.

const account = useCustomerAccount()

const adding = ref(false)
const error = ref('')

const blank = { label: '', line1: '', barangay: '', city: 'Baguio City', notes: '' }
const form = reactive({ ...blank })

const canSave = computed(() => form.label.trim() !== '' && form.line1.trim() !== '')

function open() {
  Object.assign(form, blank)
  error.value = ''
  adding.value = true
}

function save() {
  if (!canSave.value) {
    error.value = 'A name for the place and a street address are both needed.'
    return
  }
  account.addAddress({
    label: form.label.trim(),
    line1: form.line1.trim(),
    barangay: form.barangay.trim(),
    city: form.city.trim(),
    notes: form.notes.trim(),
  })
  adding.value = false
}

/** The one line a rider reads. Empty parts drop out rather than leaving commas. */
function oneLine(address: { line1: string; barangay: string; city: string }): string {
  return [address.line1, address.barangay, address.city].filter(Boolean).join(', ')
}
</script>

<template>
  <div>
    <div class="acct-head">
      <h1 class="acct-head__title">Delivery addresses</h1>
      <p class="acct-head__sub">
        Where riders bring your orders. The default is the one checkout opens on — you can still
        change it for a single order.
      </p>
    </div>

    <div v-if="account.addresses.value.length === 0 && !adding" class="acct-empty">
      <p class="acct-empty__title">No addresses saved yet</p>
      <p class="acct-empty__note">
        Add the place you order to most often and checkout will fill it in for you.
      </p>
      <button type="button" class="acct-btn" style="margin-top: 18px" @click="open">
        <Plus :size="17" :stroke-width="2" />
        Add an address
      </button>
    </div>

    <div v-for="address in account.addresses.value" :key="address.id" class="acct-card acct-addr">
      <MapPin class="acct-addr__pin" :size="20" :stroke-width="1.6" />
      <div class="acct-addr__body">
        <p class="acct-card__title">
          {{ address.label }}
          <span v-if="address.isDefault" class="acct-tag" style="margin-left: 8px">Default</span>
        </p>
        <p class="acct-card__note">{{ oneLine(address) }}</p>
        <p v-if="address.notes" class="acct-card__note acct-addr__notes">{{ address.notes }}</p>

        <div class="acct-actions" style="margin-top: 13px">
          <button
            v-if="!address.isDefault"
            type="button"
            class="acct-link"
            @click="account.setDefaultAddress(address.id)"
          >
            Make default
          </button>
          <button
            type="button"
            class="acct-link acct-link--danger"
            @click="account.removeAddress(address.id)"
          >
            Remove
          </button>
        </div>
      </div>
    </div>

    <form v-if="adding" class="acct-card" @submit.prevent="save">
      <p class="acct-card__title" style="margin-bottom: 18px">New address</p>

      <div class="acct-row">
        <label class="acct-field">
          <span class="acct-field__label">Name this place</span>
          <input v-model="form.label" class="acct-input" type="text" placeholder="Home" />
        </label>
        <label class="acct-field">
          <span class="acct-field__label">Barangay</span>
          <input v-model="form.barangay" class="acct-input" type="text" placeholder="Bakakeng" />
        </label>
      </div>

      <label class="acct-field">
        <span class="acct-field__label">Street address</span>
        <input
          v-model="form.line1"
          class="acct-input"
          type="text"
          autocomplete="street-address"
          placeholder="14 Marcoville Road"
        />
      </label>

      <label class="acct-field">
        <span class="acct-field__label">City</span>
        <input v-model="form.city" class="acct-input" type="text" autocomplete="address-level2" />
      </label>

      <label class="acct-field">
        <span class="acct-field__label">Notes for the rider <span style="font-weight: 500; color: #9ca3af">(optional)</span></span>
        <textarea
          v-model="form.notes"
          class="acct-textarea"
          placeholder="Green gate beside the sari-sari store. Ring twice."
        ></textarea>
        <span class="acct-field__hint">Landmarks help more than pins do on the steeper roads.</span>
      </label>

      <div class="acct-actions">
        <button type="submit" class="acct-btn" :disabled="!canSave">Save address</button>
        <button type="button" class="acct-link" @click="adding = false">Cancel</button>
      </div>

      <p v-if="error" class="acct-flash acct-flash--error">{{ error }}</p>
    </form>

    <button
      v-else-if="account.addresses.value.length > 0"
      type="button"
      class="acct-btn acct-btn--ghost"
      style="margin-top: 16px"
      @click="open"
    >
      <Plus :size="17" :stroke-width="2" />
      Add another address
    </button>
  </div>
</template>

<style scoped>
.acct-addr {
  display: flex;
  gap: 16px;
}

.acct-addr__pin {
  flex-shrink: 0;
  margin-top: 2px;
  color: #6b7280;
}

.acct-addr__body {
  min-width: 0;
}

.acct-addr__notes {
  margin-top: 6px;
  color: #9ca3af;
}
</style>
