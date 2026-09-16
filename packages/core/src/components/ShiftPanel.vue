<script setup lang="ts">
import { computed, inject, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, ChevronDown, Clock, Landmark, LayoutDashboard, LogOut, Menu, MinusCircle, PlusCircle, Power } from '@lucide/vue'
import { OPEN_APP_NAV } from '@pos/core/app/navDrawer'
import { useAuthStore } from '@pos/core/stores/auth'
import { usePosStore } from '@pos/core/stores/pos'
import { formatCompactDate, formatCurrency } from '@pos/shared/index'

const auth = useAuthStore()
const store = usePosStore()
const router = useRouter()

const isManageOpen = ref(false)
const isUserOpen = ref(false)
const openingCashInput = ref('')
const movementAmountInput = ref('')
const movementReason = ref('')
const closingCashInput = ref('')
const isSaving = ref(false)

const now = ref(new Date())
let clockTimer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  clockTimer = setInterval(() => { now.value = new Date() }, 30_000)
})
onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})

const formattedDate = computed(() =>
  new Intl.DateTimeFormat('en-PH', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }).format(now.value),
)
const formattedTime = computed(() =>
  new Intl.DateTimeFormat('en-PH', { hour: 'numeric', minute: '2-digit', hour12: true }).format(now.value),
)
const userInitials = computed(() => {
  const parts = (auth.currentUser?.fullName || 'JD').trim().split(/\s+/).filter(Boolean)
  return parts.slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || 'JD'
})

// Below the shell's breakpoint the nav rail is a drawer, and Register hides the
// workspace topbar that would otherwise open it — so this button does.
const openAppNav = inject(OPEN_APP_NAV, null)

function openNav() {
  if (openAppNav) {
    openAppNav()
    return
  }
  void router.push({ name: 'dashboard' })
}

async function goToDashboard() {
  isUserOpen.value = false
  await router.push({ name: 'dashboard' })
}

async function handleSignOut() {
  await auth.logout()
  await router.replace({ name: 'auth' })
}

async function openShiftManager() {
  try {
    await store.refreshActiveShift()
  } catch {
    // If refresh fails, keep whatever cached state we already have and still open the panel.
  }
  isManageOpen.value = true
}

const currentUserId = computed(() => {
  const userId = auth.currentUser?.id
  return userId && userId !== '__guest__' ? userId : null
})

function parseCurrencyInput(value: string): number {
  const normalized = value.trim().replace(/,/g, '')
  if (!normalized) {
    return 0
  }

  const amount = Number(normalized)
  if (!Number.isFinite(amount) || amount < 0) {
    return 0
  }

  return Math.round(amount * 100)
}

async function handleOpenShift() {
  isSaving.value = true
  try {
    await store.openShift(parseCurrencyInput(openingCashInput.value), currentUserId.value)
    openingCashInput.value = ''
    store.clearShiftError()
    isManageOpen.value = false
  } catch (error) {
    store.shiftError = error instanceof Error ? error.message : 'Unable to open shift.'
  } finally {
    isSaving.value = false
  }
}

async function handleMovement(type: 'pay_in' | 'pay_out') {
  isSaving.value = true
  try {
    await store.addCashMovement(
      type,
      parseCurrencyInput(movementAmountInput.value),
      movementReason.value.trim() || undefined,
      currentUserId.value,
    )
    movementAmountInput.value = ''
    movementReason.value = ''
    store.clearShiftError()
  } catch (error) {
    store.shiftError = error instanceof Error ? error.message : 'Unable to record cash movement.'
  } finally {
    isSaving.value = false
  }
}

async function handleCloseShift() {
  isSaving.value = true
  try {
    await store.closeShift(parseCurrencyInput(closingCashInput.value), currentUserId.value)
    closingCashInput.value = ''
    store.clearShiftError()
    isManageOpen.value = false
  } catch (error) {
    store.shiftError = error instanceof Error ? error.message : 'Unable to close shift.'
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <section class="register-topbar">
    <button class="register-topbar__mobile-menu" type="button" aria-label="Open navigation" @click="openNav">
      <Menu :size="20" />
    </button>

    <div class="register-topbar__brand">
      <img src="/logo-mark.png" alt="" aria-hidden="true">
      <span>
        <strong>{{ store.settings.businessName || 'Omaykan' }}</strong>
        <small>POS</small>
      </span>
    </div>

    <div class="register-topbar__datetime">
      <span class="register-topbar__date"><Calendar :size="17" aria-hidden="true" />{{ formattedDate }}</span>
      <span class="register-topbar__sep" aria-hidden="true">–</span>
      <span class="register-topbar__time"><Clock :size="17" aria-hidden="true" />{{ formattedTime }}</span>
    </div>

    <div class="register-topbar__right">
      <button
        class="register-topbar__status"
        :class="{ 'register-topbar__status--active': store.activeShift }"
        type="button"
        @click="openShiftManager"
      >
        <span class="register-topbar__dot" :class="{ 'register-topbar__dot--active': store.activeShift }" aria-hidden="true" />
        Open Shift
        <ChevronDown :size="16" aria-hidden="true" />
      </button>

      <button class="register-topbar__end-shift" type="button" @click="openShiftManager">
        <Power :size="20" />
        <span>End Shift</span>
      </button>

      <div class="register-topbar__user-wrap">
        <button class="register-topbar__user" type="button" aria-label="Open user menu" @click="isUserOpen = !isUserOpen">
          <span>{{ userInitials }}</span>
          <ChevronDown :size="18" />
        </button>
        <div v-if="isUserOpen" class="register-topbar__user-menu">
          <button type="button" @click="goToDashboard">
            <LayoutDashboard :size="16" />
            Dashboard
          </button>
          <button class="register-topbar__user-menu-danger" type="button" @click="handleSignOut">
            <LogOut :size="16" />
            Sign out
          </button>
        </div>
      </div>
    </div>
  </section>

  <Teleport to="body">
    <div v-if="isManageOpen" class="sheet-overlay" @click.self="isManageOpen = false">
      <div class="sheet-panel shift-panel-sheet" role="dialog" aria-modal="true" aria-label="Shift management" tabindex="-1">
        <div class="sheet-grabber" />

        <div class="sheet-scroll">
          <div class="panel-section">
            <p class="section-label">Shift control</p>
            <h2 class="panel-title">Cash flow</h2>
          </div>

          <p v-if="store.shiftError" class="shift-panel__error">{{ store.shiftError }}</p>

          <div v-if="!store.activeShift" class="shift-section">
            <p class="settings-row__description">
              Open a shift before charging orders so cash sales and pay-ins/pay-outs stay tied to the right
              branch record.
            </p>
            <label class="settings-field">
              <span class="settings-row__label">Opening cash</span>
              <input
                v-model="openingCashInput"
                class="sheet-input"
                inputmode="decimal"
                placeholder="0.00"
                type="text"
              >
            </label>
            <button class="primary-button" :disabled="isSaving" type="button" @click="handleOpenShift">
              Open shift
            </button>
          </div>

          <div v-else class="shift-panel__body">
            <div class="shift-chip-row">
              <article class="shift-chip shift-chip--highlight">
                <span>Total sales</span>
                <strong>{{ formatCurrency(store.activeShift.totalSalesCents) }}</strong>
              </article>
              <article class="shift-chip">
                <span>Orders</span>
                <strong>{{ store.activeShift.orderCount }}</strong>
              </article>
              <article class="shift-chip">
                <span>Opening float</span>
                <strong>{{ formatCurrency(store.activeShift.openingCashCents) }}</strong>
              </article>
              <article class="shift-chip">
                <span>Cash sales</span>
                <strong>{{ formatCurrency(store.activeShift.cashSalesCents) }}</strong>
              </article>
              <article class="shift-chip">
                <span>Pay-ins</span>
                <strong>{{ formatCurrency(store.activeShift.payInsCents) }}</strong>
              </article>
              <article class="shift-chip">
                <span>Pay-outs</span>
                <strong>{{ formatCurrency(store.activeShift.payOutsCents) }}</strong>
              </article>
            </div>

            <div class="shift-columns">
              <div class="shift-section">
                <p class="section-label">Record cash movement</p>
                <label class="settings-field">
                  <span class="settings-row__label">Amount</span>
                  <input
                    v-model="movementAmountInput"
                    class="sheet-input"
                    inputmode="decimal"
                    placeholder="0.00"
                    type="text"
                  >
                </label>
                <label class="settings-field">
                  <span class="settings-row__label">Reason</span>
                  <input
                    v-model="movementReason"
                    class="sheet-input"
                    maxlength="255"
                    placeholder="Petty cash, supplies, safe drop..."
                    type="text"
                  >
                </label>
                <div class="shift-actions__buttons">
                  <button class="secondary-button" :disabled="isSaving" type="button" @click="handleMovement('pay_in')">
                    <PlusCircle :size="16" />
                    <span>Pay in</span>
                  </button>
                  <button class="secondary-button" :disabled="isSaving" type="button" @click="handleMovement('pay_out')">
                    <MinusCircle :size="16" />
                    <span>Pay out</span>
                  </button>
                </div>
              </div>

              <div class="shift-section shift-section--close">
                <p class="section-label">End of day</p>
                <label class="settings-field">
                  <span class="settings-row__label">Counted cash</span>
                  <input
                    v-model="closingCashInput"
                    class="sheet-input"
                    inputmode="decimal"
                    :placeholder="(store.activeShift.expectedCashCents / 100).toFixed(2)"
                    type="text"
                  >
                </label>
                <button class="outline-danger-button" :disabled="isSaving" type="button" @click="handleCloseShift">
                  <Landmark :size="16" />
                  <span>Close shift</span>
                </button>
              </div>
            </div>

            <div v-if="store.activeShift.movements.length" class="shift-log">
              <p class="shift-log__title">Recent cash movements</p>
              <div
                v-for="movement in store.activeShift.movements.slice(0, 5)"
                :key="movement.id"
                class="shift-log__row"
              >
                <div>
                  <strong>{{ movement.movementType === 'pay_in' ? 'Pay in' : 'Pay out' }}</strong>
                  <p>{{ movement.reason || 'No reason provided' }}</p>
                </div>
                <div class="shift-log__amount">
                  <strong>{{ formatCurrency(movement.amountCents) }}</strong>
                  <span>{{ formatCompactDate(movement.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.register-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) 20px;
  border: none;
  border-bottom: 0.5px solid var(--separator);
  border-radius: var(--radius-xl);
  background: var(--bg-surface);
  backdrop-filter: var(--material-bar);
}

.register-topbar__icon-btn {
  display: inline-grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--fill);
  color: var(--text-primary);
  transition:
    background var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.register-topbar__icon-btn:hover {
  background: color-mix(in srgb, var(--accent) 12%, var(--fill));
  color: var(--accent);
}

.register-topbar__icon-btn--danger:hover {
  background: color-mix(in srgb, var(--danger) 12%, var(--fill));
  color: var(--danger);
}

.register-topbar__icon-btn--ghost {
  background: transparent;
}

.register-topbar__icon-btn--ghost:hover {
  background: var(--fill);
  color: var(--text-primary);
}

.register-topbar__datetime {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  min-width: 0;
  color: var(--text-secondary);
  font: var(--type-subhead);
  font-weight: 600;
}

.register-topbar__date,
.register-topbar__time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 var(--space-3);
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-pill);
  background: var(--bg-elevated);
  white-space: nowrap;
}

.register-topbar__sep {
  color: var(--text-tertiary);
}

.register-topbar__right {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--space-3);
}

.register-topbar__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  border: none;
  background: transparent;
  color: var(--text-primary);
  font: var(--type-subhead);
  font-weight: 700;
  white-space: nowrap;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.register-topbar__status:hover {
  opacity: 0.7;
}

.register-topbar__status--active {
  color: var(--danger);
}

.register-topbar__dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: var(--radius-pill);
  background: var(--text-tertiary);
}

.register-topbar__dot--active {
  background: var(--danger);
}

@media (max-width: 720px) {
  .register-topbar {
    flex-wrap: wrap;
  }

  .register-topbar__datetime {
    order: 3;
    flex-basis: 100%;
    justify-content: flex-start;
  }
}

.shift-panel__error {
  margin: 0;
  color: var(--danger);
  font-weight: 600;
}

.shift-panel-sheet {
  max-width: 640px;
}

.shift-panel__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-4);
}

.shift-chip-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
}

.shift-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  align-items: start;
}

.shift-chip {
  display: grid;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--fill) 70%, transparent);
}

.shift-chip--highlight {
  grid-column: 1 / -1;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
}

.shift-chip--highlight strong {
  color: var(--accent);
}

.shift-chip span {
  color: var(--text-secondary);
  font: var(--type-caption);
}

.shift-chip strong {
  font: var(--type-subhead);
  font-variant-numeric: tabular-nums;
}

.shift-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-4);
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-lg);
}

.shift-actions__buttons {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.shift-actions__buttons .secondary-button {
  flex: 1;
}

.shift-log {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0.9rem;
}

.shift-log__title {
  margin: 0;
  font-weight: 700;
}

.shift-log__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  border-radius: 1rem;
  background: rgba(148, 163, 184, 0.09);
}

.shift-log__row p {
  margin: 0.2rem 0 0;
  color: var(--text-secondary);
}

.shift-log__amount {
  display: grid;
  justify-items: end;
  gap: 0.2rem;
}

.shift-log__amount span {
  color: var(--text-secondary);
}

@media (max-width: 720px) {
  .shift-chip-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .shift-columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .shift-log__row {
    align-items: stretch;
    flex-direction: column;
  }

  .shift-log__amount {
    justify-items: start;
  }
}

/* Seller POS utility header */
.register-topbar {
  gap: 24px;
  min-height: 78px;
  padding: 10px 26px;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 4px 18px rgba(4, 71, 58, 0.035);
}

.register-topbar__mobile-menu {
  display: none;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 1px solid #dbe7e4;
  border-radius: 12px;
  background: white;
  color: #10201d;
}

.register-topbar__brand {
  display: flex;
  min-width: 164px;
  align-items: center;
  gap: 9px;
}

.register-topbar__brand img {
  width: 36px;
  height: 36px;
  object-fit: contain;
}

.register-topbar__brand > span {
  display: grid;
  gap: 0;
}

.register-topbar__brand strong,
.register-topbar__brand small {
  overflow: hidden;
  max-width: 120px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.register-topbar__brand strong {
  color: #073d35;
  font-size: 20px;
  font-weight: 800;
  line-height: 22px;
  letter-spacing: -0.025em;
}

.register-topbar__brand small {
  color: #61726d;
  font-size: 12px;
  font-weight: 600;
  line-height: 15px;
}

.register-topbar__datetime {
  flex: 0 1 auto;
  justify-content: flex-start;
  gap: 0;
  min-width: 0;
  color: #273330;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
}

.register-topbar__date,
.register-topbar__time {
  gap: 9px;
  min-height: 43px;
  padding: 0 14px;
  border: 1px solid #e0e8e6;
  border-radius: 0;
  background: #fff;
}

.register-topbar__date {
  border-right: 0;
  border-radius: 12px 0 0 12px;
}

.register-topbar__time {
  border-left-color: #edf2f0;
  border-radius: 0 12px 12px 0;
}

.register-topbar__sep {
  display: none;
}

.register-topbar__right {
  gap: 18px;
  margin-left: auto;
  flex: none;
}

.register-topbar__status {
  gap: 9px;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid #d9e8e3;
  border-radius: 12px;
  background: #f3faf7;
  color: #104f42;
  font-size: 14px;
}

.register-topbar__status--active {
  color: #0b5345;
}

.register-topbar__dot {
  background: #36a95d;
  box-shadow: 0 0 0 4px rgba(54, 169, 93, 0.09);
}

.register-topbar__dot--active {
  background: #36a95d;
  box-shadow: 0 0 0 4px rgba(54, 169, 93, 0.09);
}

.register-topbar__end-shift {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border: 0;
  border-left: 1px solid #e1e9e7;
  background: transparent;
  color: #14201e;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.register-topbar__user-wrap {
  position: relative;
}

.register-topbar__user {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  gap: 14px;
  padding: 0 4px 0 0;
  border: 0;
  background: transparent;
  color: #20312d;
}

.register-topbar__user span {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 50%;
  background: #06483c;
  color: white;
  font-size: 13px;
  font-weight: 700;
}

.register-topbar__user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 40;
  min-width: 150px;
  padding: 7px;
  border: 1px solid #dde8e5;
  border-radius: 12px;
  background: white;
  box-shadow: 0 16px 32px rgba(3, 54, 45, 0.14);
}

.register-topbar__user-menu button {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #20312d;
  font-size: 13px;
}

.register-topbar__user-menu button:hover {
  background: #f1f7f5;
}

.register-topbar__user-menu .register-topbar__user-menu-danger {
  color: #b33d3d;
}

/* This header spans the shell's content column, not the viewport: with the nav
   rail on screen it has ~328px less to work with, so these thresholds sit that
   much above the widths they actually describe. */
@media (max-width: 1748px) {
  .register-topbar { gap: 14px; }
  .register-topbar__brand { min-width: auto; }
}

/* Rail on screen and the column narrow enough that even the compact header runs
   out of room. The clock is the one thing here a cashier can read off the till
   itself, so it goes first. */
@media (min-width: 1081px) and (max-width: 1200px) {
  .register-topbar__datetime { display: none; }
}

@media (max-width: 720px) {
  .register-topbar {
    min-height: 64px;
    gap: 10px;
    padding: 8px 12px;
  }

  .register-topbar__mobile-menu { display: grid; }
  .register-topbar__brand { flex: 1; min-width: 0; }
  .register-topbar__brand > span,
  .register-topbar__datetime,
  .register-topbar__end-shift { display: none; }
  .register-topbar__status { padding: 0 10px; }
  .register-topbar__status svg { display: none; }
}
</style>
