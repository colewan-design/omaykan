<script setup lang="ts">
import { Gift } from '@lucide/vue'
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
      <span class="loyalty-card__icon" aria-hidden="true"><Gift :size="21" /></span>
      <div class="loyalty-card__copy">
        <strong>Loyalty points are currently {{ program.enabled ? 'on' : 'off' }}</strong>
        <small v-if="program.enabled">{{ summary }} Only enrolled customers earn.</small>
        <small v-else>
          Turn on loyalty points to reward your customers, encourage repeat purchases, and grow long-term loyalty.
        </small>
      </div>
      <button
        class="loyalty-card__switch"
        :class="program.enabled ? 'secondary-button' : 'primary-button'"
        type="button"
        :disabled="saving"
        @click="save({ enabled: !program.enabled })"
      >
        {{ program.enabled ? 'Turn off loyalty' : 'Turn on loyalty' }}
      </button>
      <button class="loyalty-card__rules-toggle" type="button" :aria-expanded="open" @click="open = !open">
        {{ open ? 'Hide rules' : 'Rules' }}
      </button>

      <!-- The present on the right is decoration, and says nothing the copy
           beside it doesn't already say. -->
      <svg class="loyalty-card__art" viewBox="0 0 132 104" aria-hidden="true" focusable="false">
        <rect class="loyalty-card__art-box" x="30" y="46" width="72" height="46" rx="6" />
        <rect class="loyalty-card__art-lid" x="24" y="33" width="84" height="18" rx="5" />
        <rect class="loyalty-card__art-ribbon" x="60" y="33" width="12" height="59" />
        <path class="loyalty-card__art-bow" d="M66 33c-4-12-14-17-19-13s-1 13 19 13zm0 0c4-12 14-17 19-13s1 13-19 13z" />
        <g class="loyalty-card__art-spark">
          <path d="M113 24l2.4 5.6 5.6 2.4-5.6 2.4-2.4 5.6-2.4-5.6-5.6-2.4 5.6-2.4z" />
          <path d="M20 16l1.6 3.7 3.7 1.6-3.7 1.6L20 26.6l-1.6-3.7-3.7-1.6 3.7-1.6z" />
          <path d="M119 66l1.3 3 3 1.3-3 1.3-1.3 3-1.3-3-3-1.3 3-1.3z" />
        </g>
      </svg>
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
/*
 * A banner, not a settings row: the programme is off in most shops, and this
 * is the one place that invites turning it on. It tints from the shop's own
 * accent so a store on another colour theme doesn't get a stray green band.
 */
.loyalty-card {
  position: relative;
  display: grid;
  gap: 12px;
  padding: 16px 18px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, transparent);
  border-radius: 15px;
  background:
    linear-gradient(
      100deg,
      color-mix(in srgb, var(--accent) 9%, var(--bg-elevated)) 0%,
      color-mix(in srgb, var(--accent) 4%, var(--bg-elevated)) 52%,
      var(--bg-elevated) 100%
    );
}

.loyalty-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
}

.loyalty-card__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 16%, var(--bg-elevated));
  color: var(--accent);
}

.loyalty-card__copy {
  display: grid;
  flex: 1;
  min-width: 220px;
  gap: 3px;
  color: var(--text-primary);
}

.loyalty-card__copy strong {
  font: 700 1rem/1.35rem var(--font-sans);
  letter-spacing: -0.01em;
}

.loyalty-card__copy small {
  max-width: 68ch;
  color: var(--text-secondary);
  font: 400 0.8125rem/1.15rem var(--font-sans);
}

.loyalty-card__switch {
  min-height: 40px;
  padding-inline: 16px;
  border-radius: 11px;
  font: 600 0.875rem/1rem var(--font-sans);
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

.loyalty-card__art {
  width: 118px;
  height: 92px;
  margin: -14px -6px -14px 0;
  flex: none;
}

.loyalty-card__art-box    { fill: color-mix(in srgb, var(--accent) 34%, transparent); }
.loyalty-card__art-lid    { fill: color-mix(in srgb, var(--accent) 52%, transparent); }
.loyalty-card__art-ribbon,
.loyalty-card__art-bow    { fill: color-mix(in srgb, var(--accent) 72%, transparent); }
.loyalty-card__art-spark  { fill: color-mix(in srgb, var(--accent) 40%, transparent); }

@media (max-width: 900px) {
  .loyalty-card__art {
    display: none;
  }
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
