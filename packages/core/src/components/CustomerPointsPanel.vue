<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { CustomerLoyalty } from '@pos/data/index'
import { getPosRepository } from '@pos/core/services/runtime'

/**
 * One customer's points: the balance, the last few movements, and a manager's
 * correction — "their card from the old system had 40". A correction is a
 * new line in the ledger with a reason, never an edit to the number.
 *
 * Renders nothing when the server cannot be reached, or for a customer it has
 * not received yet. See documentation/merchant-features.md §9.
 */

const props = defineProps<{ customerId: string }>()

const repository = getPosRepository()
const loyalty = ref<CustomerLoyalty | null>(null)
const points = ref('')
const note = ref('')
const saving = ref(false)
const error = ref('')

const REASONS: Record<string, string> = { earn: 'Earned', redeem: 'Spent', expire: 'Expired', adjust: 'Corrected' }

async function load() {
  try {
    loyalty.value = await repository.loadCustomerLoyalty(props.customerId)
  } catch {
    loyalty.value = null
  }
}

async function adjust() {
  const delta = Math.round(Number(points.value))
  if (!Number.isFinite(delta) || delta === 0) return void (error.value = 'Enter points to add, or a negative number to take away.')
  if (!note.value.trim()) return void (error.value = 'Say why — the next person to look will want to know.')

  saving.value = true
  error.value = ''
  try {
    await repository.adjustLoyalty(props.customerId, delta, note.value.trim())
    points.value = ''
    note.value = ''
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : "That correction didn't save."
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section v-if="loyalty" class="points-panel" aria-label="Points">
    <p class="points-panel__balance"><strong>{{ loyalty.balance }}</strong> points</p>

    <ul v-if="loyalty.entries.length" class="points-panel__entries">
      <li v-for="entry in loyalty.entries.slice(0, 5)" :key="entry.id">
        <span>{{ REASONS[entry.reason] ?? entry.reason }}<template v-if="entry.note"> · {{ entry.note }}</template></span>
        <strong :class="{ 'is-down': entry.points < 0 }">{{ entry.points > 0 ? '+' : '' }}{{ entry.points }}</strong>
      </li>
    </ul>

    <form class="points-panel__adjust" @submit.prevent="adjust">
      <input v-model="points" class="sheet-input" inputmode="numeric" placeholder="+40 or −10" aria-label="Points to add or take away" />
      <input v-model="note" class="sheet-input" maxlength="200" placeholder="Why" aria-label="Reason for the correction" />
      <button class="secondary-button" type="submit" :disabled="saving">{{ saving ? 'Saving…' : 'Correct' }}</button>
    </form>
    <p v-if="error" class="points-panel__error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.points-panel {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-base);
}

.points-panel__balance {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.points-panel__balance strong {
  font-size: 20px;
  color: var(--text-primary);
}

.points-panel__entries {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 12px;
  color: var(--text-secondary);
}

.points-panel__entries li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.points-panel__entries strong {
  color: var(--accent);
}

.points-panel__entries strong.is-down {
  color: var(--text-secondary);
}

.points-panel__adjust {
  display: grid;
  grid-template-columns: 110px 1fr auto;
  gap: 8px;
}

.points-panel__error {
  margin: 0;
  font-size: 12px;
  color: var(--danger);
}

@media (max-width: 520px) {
  .points-panel__adjust {
    grid-template-columns: 1fr;
  }
}
</style>
