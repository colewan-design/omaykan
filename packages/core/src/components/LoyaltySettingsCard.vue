<script setup lang="ts">
import { Award } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { formatCurrency } from '@pos/shared/index'
import type { LoyaltyProgram } from '@pos/data/index'
import { getPosRepository } from '@pos/core/services/runtime'

/**
 * The points programme's rules: on or off, how points are earned, what one is
 * worth, and when they expire. Off until the owner turns it on.
 *
 * Points live on the server — the ledger is what every till reads — so a till
 * with no connection shows nothing here. See documentation/merchant-features.md §9.
 */

const repository = getPosRepository()
const program = ref<LoyaltyProgram | null>(null)
const open = ref(false)
const saving = ref(false)
const error = ref('')
const saved = ref(false)

const form = reactive({ spendPesos: '100', pointPesos: '1', minRedeem: '10', expiryMonths: '' })

const summary = computed(() => {
  const p = program.value
  if (!p) return ''
  const earn = `1 point per ${formatCurrency(p.spendCentsPerPoint)} spent`
  const worth = `worth ${formatCurrency(p.pointValueCents)} each`
  const expiry = p.expiryMonths ? `, expiring after ${p.expiryMonths} months` : ''
  return `${earn}, ${worth}${expiry}.`
})

function fill(p: LoyaltyProgram) {
  form.spendPesos = String(p.spendCentsPerPoint / 100)
  form.pointPesos = String(p.pointValueCents / 100)
  form.minRedeem = String(p.minRedeemPoints)
  form.expiryMonths = p.expiryMonths ? String(p.expiryMonths) : ''
}

async function save(patch: Partial<LoyaltyProgram>) {
  saving.value = true
  error.value = ''
  saved.value = false
  try {
    program.value = await repository.saveLoyaltyProgram(patch)
    fill(program.value)
    saved.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : "That didn't save."
  } finally {
    saving.value = false
  }
}

function saveRules() {
  const spend = Math.round(Number(form.spendPesos) * 100)
  const worth = Math.round(Number(form.pointPesos) * 100)
  const min = Math.round(Number(form.minRedeem))
  if (!(spend >= 100) || !(worth >= 1) || !(min >= 1)) {
    error.value = 'Earning needs at least ₱1 per point, and a point must be worth something.'
    return
  }
  void save({
    spendCentsPerPoint: spend,
    pointValueCents: worth,
    minRedeemPoints: min,
    expiryMonths: form.expiryMonths.trim() ? Math.round(Number(form.expiryMonths)) : null,
  })
}

onMounted(async () => {
  try {
    program.value = await repository.loadLoyaltyProgram()
    if (program.value) fill(program.value)
  } catch {
    program.value = null
  }
})
</script>

<template>
  <section v-if="program" class="loyalty-card">
    <div class="loyalty-card__head">
      <span class="loyalty-card__icon" aria-hidden="true"><Award :size="18" /></span>
      <div class="loyalty-card__copy">
        <strong>Points {{ program.enabled ? 'are on' : 'are off' }}</strong>
        <small v-if="program.enabled">{{ summary }} Only enrolled customers earn.</small>
        <small v-else>Turn on to let enrolled customers earn points at the counter and spend them as a discount.</small>
      </div>
      <button class="secondary-button" type="button" :disabled="saving" @click="save({ enabled: !program.enabled })">
        {{ program.enabled ? 'Turn off' : 'Turn on' }}
      </button>
      <button class="loyalty-card__rules-toggle" type="button" :aria-expanded="open" @click="open = !open">
        {{ open ? 'Hide rules' : 'Rules' }}
      </button>
    </div>

    <form v-if="open" class="loyalty-card__rules" @submit.prevent="saveRules">
      <label>
        <span>₱ spent per point</span>
        <input v-model="form.spendPesos" class="sheet-input" inputmode="decimal" />
      </label>
      <label>
        <span>A point is worth (₱)</span>
        <input v-model="form.pointPesos" class="sheet-input" inputmode="decimal" />
      </label>
      <label>
        <span>Fewest points to spend</span>
        <input v-model="form.minRedeem" class="sheet-input" inputmode="numeric" />
      </label>
      <label>
        <span>Expire after (months)</span>
        <input v-model="form.expiryMonths" class="sheet-input" inputmode="numeric" placeholder="Never" />
      </label>
      <div class="loyalty-card__actions">
        <button class="primary-button" type="submit" :disabled="saving">{{ saving ? 'Saving…' : 'Save rules' }}</button>
        <span v-if="saved" class="loyalty-card__saved">Saved.</span>
      </div>
    </form>

    <p v-if="error" class="loyalty-card__error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.loyalty-card {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-lg);
  background: var(--bg-elevated);
}

.loyalty-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
}

.loyalty-card__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
}

.loyalty-card__copy {
  display: grid;
  flex: 1;
  min-width: 200px;
  gap: 2px;
  font-size: 13px;
  color: var(--text-primary);
}

.loyalty-card__copy small {
  color: var(--text-secondary);
}

.loyalty-card__rules-toggle {
  padding: 6px 4px;
  border: 0;
  background: none;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--accent);
  cursor: pointer;
}

.loyalty-card__rules {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.loyalty-card__rules label {
  display: grid;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.loyalty-card__actions {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 10px;
}

.loyalty-card__saved {
  font-size: 13px;
  color: var(--accent);
}

.loyalty-card__error {
  margin: 0;
  font-size: 12px;
  color: var(--danger);
}
</style>
