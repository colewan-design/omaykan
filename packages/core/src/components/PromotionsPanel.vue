<script setup lang="ts">
import { Plus, TicketPercent, Trash2 } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { formatCurrency } from '@pos/shared/index'
import type { PromoCode, PromoCodeInput } from '@pos/data/index'
import { getPosRepository } from '@pos/core/services/runtime'

/**
 * The shop's promo and voucher codes: what each takes off, where it works,
 * how often it has been used, and a switch to pause it.
 *
 * Codes live on the server — a redemption cap cannot be kept by a till that
 * is offline — so on a till with no connection this says so instead of
 * listing anything. See documentation/merchant-features.md §8.
 */

const repository = getPosRepository()

const codes = ref<PromoCode[] | null>(null)
const loading = ref(true)
const loadError = ref('')
const creating = ref(false)
const saving = ref(false)
const formError = ref('')
const rowError = ref<Record<string, string>>({})

const form = reactive({
  code: '',
  kind: 'percent' as 'percent' | 'amount',
  value: '',
  minOrder: '',
  cap: '',
  channel: 'online' as 'online' | 'counter' | 'both',
  endsAt: '',
  maxRedemptions: '',
  perCustomerLimit: '1',
})

const CHANNELS: Record<PromoCode['channel'], string> = {
  online: 'Online',
  counter: 'At the counter',
  both: 'Online and counter',
}

/** The server's own sentence out of whatever backendFetch threw. */
function reasonFrom(error: unknown, fallback: string): string {
  if (!(error instanceof Error)) return fallback
  try {
    const body = JSON.parse(error.message) as { errors?: Record<string, string[]>; message?: string }
    return Object.values(body.errors ?? {})[0]?.[0] ?? body.message ?? fallback
  } catch {
    return error.message || fallback
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    codes.value = await repository.loadPromoCodes()
  } catch (error) {
    loadError.value = reasonFrom(error, "Couldn't load promo codes.")
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    code: '', kind: 'percent', value: '', minOrder: '', cap: '',
    channel: 'online', endsAt: '', maxRedemptions: '', perCustomerLimit: '1',
  })
  formError.value = ''
}

const pesosToCents = (text: string) => (text.trim() === '' ? null : Math.round(Number(text.replace(/,/g, '')) * 100))
const wholeOrNull = (text: string) => (text.trim() === '' ? null : Math.round(Number(text)))

async function create() {
  if (saving.value) return
  formError.value = ''

  const number = Number(form.value.replace(/,/g, ''))
  if (!form.code.trim()) return void (formError.value = 'Give the code a name, like WELCOME10.')
  if (!Number.isFinite(number) || number <= 0) return void (formError.value = 'Enter how much it takes off.')

  const input: PromoCodeInput & { code: string } = {
    code: form.code.trim(),
    kind: form.kind,
    ...(form.kind === 'percent' ? { percent: number } : { amountCents: Math.round(number * 100) }),
    minSubtotalCents: pesosToCents(form.minOrder) ?? 0,
    maxDiscountCents: form.kind === 'percent' ? pesosToCents(form.cap) : null,
    channel: form.channel,
    endsAt: form.endsAt ? new Date(`${form.endsAt}T23:59:59`).toISOString() : null,
    maxRedemptions: wholeOrNull(form.maxRedemptions),
    perCustomerLimit: wholeOrNull(form.perCustomerLimit),
  }

  saving.value = true
  try {
    const created = await repository.savePromoCode(input)
    codes.value = [created, ...(codes.value ?? [])]
    creating.value = false
    resetForm()
  } catch (error) {
    formError.value = reasonFrom(error, "That code didn't save.")
  } finally {
    saving.value = false
  }
}

async function setActive(code: PromoCode, isActive: boolean) {
  rowError.value = { ...rowError.value, [code.id]: '' }
  try {
    const saved = await repository.savePromoCode({ isActive }, code.id)
    codes.value = (codes.value ?? []).map((entry) => (entry.id === saved.id ? saved : entry))
  } catch (error) {
    rowError.value = { ...rowError.value, [code.id]: reasonFrom(error, "That didn't save.") }
  }
}

async function retire(code: PromoCode) {
  if (!window.confirm(`Retire ${code.code}? It stops working at once, and the name can't be used again.`)) return
  try {
    await repository.deletePromoCode(code.id)
    codes.value = (codes.value ?? []).filter((entry) => entry.id !== code.id)
  } catch (error) {
    rowError.value = { ...rowError.value, [code.id]: reasonFrom(error, "That didn't save.") }
  }
}

function uses(code: PromoCode): string {
  return code.maxRedemptions != null ? `${code.redemptions} of ${code.maxRedemptions} used` : `${code.redemptions} used`
}

function rules(code: PromoCode): string {
  const parts = [CHANNELS[code.channel]]
  if (code.minSubtotalCents > 0) parts.push(`orders over ${formatCurrency(code.minSubtotalCents)}`)
  if (code.perCustomerLimit != null) parts.push(`${code.perCustomerLimit}× per customer`)
  if (code.endsAt) parts.push(`until ${new Date(code.endsAt).toLocaleDateString()}`)
  return parts.join(' · ')
}

const expired = (code: PromoCode) => code.endsAt != null && new Date(code.endsAt) < new Date()
const hasCodes = computed(() => (codes.value?.length ?? 0) > 0)

onMounted(load)
</script>

<template>
  <section class="promos" aria-label="Promo codes">
    <div v-if="loading" class="empty-state p-empty"><strong>Loading promo codes…</strong></div>

    <div v-else-if="loadError" class="empty-state p-empty">
      <strong>{{ loadError }}</strong>
      <button class="table-action" type="button" @click="load">Try again</button>
    </div>

    <div v-else-if="codes === null" class="empty-state p-empty">
      <TicketPercent :size="30" />
      <strong>Promo codes need Omaykan online</strong>
      <span>Codes live on the server so a use limit means the same thing at every till. Turn on online sync in Settings.</span>
    </div>

    <template v-else>
      <div class="promos__head">
        <p class="promos__intro">
          A code a shopper types at checkout, or a cashier types at the register. Uses are counted as orders come in.
        </p>
        <button v-if="!creating" class="primary-button promos__new" type="button" @click="creating = true">
          <Plus :size="16" /> New code
        </button>
      </div>

      <form v-if="creating" class="promos__form" @submit.prevent="create">
        <label class="promos__field">
          <span>Code</span>
          <input v-model="form.code" class="sheet-input promos__code-input" maxlength="40" placeholder="WELCOME10" />
        </label>
        <label class="promos__field">
          <span>Takes off</span>
          <span class="promos__value">
            <select v-model="form.kind" class="sheet-input" aria-label="Percentage or pesos">
              <option value="percent">%</option>
              <option value="amount">₱</option>
            </select>
            <input v-model="form.value" class="sheet-input" inputmode="decimal" :placeholder="form.kind === 'percent' ? '10' : '50'" aria-label="Amount" />
          </span>
        </label>
        <label v-if="form.kind === 'percent'" class="promos__field">
          <span>Up to (₱, optional)</span>
          <input v-model="form.cap" class="sheet-input" inputmode="decimal" placeholder="No cap" />
        </label>
        <label class="promos__field">
          <span>Minimum order (₱)</span>
          <input v-model="form.minOrder" class="sheet-input" inputmode="decimal" placeholder="0" />
        </label>
        <label class="promos__field">
          <span>Works</span>
          <select v-model="form.channel" class="sheet-input">
            <option value="online">Online</option>
            <option value="counter">At the counter</option>
            <option value="both">Online and counter</option>
          </select>
        </label>
        <label class="promos__field">
          <span>Last day (optional)</span>
          <input v-model="form.endsAt" class="sheet-input" type="date" />
        </label>
        <label class="promos__field">
          <span>Total uses (optional)</span>
          <input v-model="form.maxRedemptions" class="sheet-input" inputmode="numeric" placeholder="Unlimited" />
        </label>
        <label class="promos__field">
          <span>Per customer</span>
          <input v-model="form.perCustomerLimit" class="sheet-input" inputmode="numeric" placeholder="Unlimited" />
        </label>

        <p v-if="formError" class="promos__error" role="alert">{{ formError }}</p>
        <div class="promos__actions">
          <button class="primary-button" type="submit" :disabled="saving">{{ saving ? 'Saving…' : 'Create code' }}</button>
          <button class="secondary-button" type="button" @click="creating = false; resetForm()">Cancel</button>
        </div>
      </form>

      <div v-if="!hasCodes && !creating" class="empty-state p-empty">
        <TicketPercent :size="30" />
        <strong>No promo codes yet</strong>
        <span>Make one for a slow afternoon, a first order, or a regular.</span>
      </div>

      <ul v-else class="promos__list">
        <li v-for="code in codes" :key="code.id" class="promos__row" :class="{ 'promos__row--off': !code.isActive || expired(code) }">
          <div class="promos__main">
            <strong class="promos__code">{{ code.code }}</strong>
            <span class="promos__desc">{{ code.description }}</span>
            <small class="promos__rules">{{ rules(code) }}</small>
            <small v-if="rowError[code.id]" class="promos__error">{{ rowError[code.id] }}</small>
          </div>
          <span class="promos__uses">{{ expired(code) ? 'Expired' : uses(code) }}</span>
          <button class="table-action" type="button" @click="setActive(code, !code.isActive)">
            {{ code.isActive ? 'Pause' : 'Resume' }}
          </button>
          <button class="table-action table-action--icon" type="button" :aria-label="`Retire ${code.code}`" @click="retire(code)">
            <Trash2 :size="15" />
          </button>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.promos {
  display: grid;
  gap: 14px;
  padding-top: 8px;
}

.promos__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.promos__intro {
  flex: 1;
  min-width: 220px;
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.promos__new {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.promos__form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-lg);
  background: var(--bg-elevated);
}

.promos__field {
  display: grid;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.promos__value {
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 6px;
}

.promos__code-input {
  text-transform: uppercase;
}

.promos__actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 10px;
}

.promos__error {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 12px;
  color: var(--danger);
}

.promos__list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.promos__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
}

.promos__row--off {
  opacity: 0.6;
}

.promos__main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.promos__code {
  font-size: 15px;
  letter-spacing: 0.04em;
  color: var(--text-primary);
}

.promos__desc {
  font-size: 13px;
  color: var(--text-primary);
}

.promos__rules {
  font-size: 12px;
  color: var(--text-secondary);
}

.promos__uses {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
}

@media (max-width: 560px) {
  .promos__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .promos__uses {
    grid-column: 1 / -1;
  }
}
</style>
