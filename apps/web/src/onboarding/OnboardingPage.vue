<script setup lang="ts">
import { Check, Copy, Eye, EyeOff } from '@lucide/vue'
import { computed, reactive, ref } from 'vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import { businessModeLabel, type BusinessMode } from '@pos/shared/index'
import { writePendingInitialSettings, writeStaffTenant } from '@pos/web/tenantBinding'
import PosMarketing from '@pos/web/landing/PosMarketing.vue'
import { GCASH_ACCOUNT_NAME, GCASH_NUMBER, PLAN_PRICE_PESOS } from './pricingConstants'

const mode = ref<'signup' | 'pair'>('signup')
const saving = ref(false)
const errorMessage = ref('')
const passwordVisible = ref(false)

const businessModeOptions = (['coffee-shop', 'grocery', 'restaurant', 'nail-salon'] as const).map((value) => ({
  value,
  label: businessModeLabel(value),
}))

const signupForm = reactive({
  businessName: '',
  ownerFullName: '',
  username: '',
  password: '',
  businessMode: 'coffee-shop' as BusinessMode,
  gcashReference: '',
})

const pairForm = reactive({
  pairingCode: '',
})

const priceLabel = computed(() => `₱${PLAN_PRICE_PESOS.toLocaleString('en-PH')}/month`)

// Set once signup succeeds, holding the UI on a confirmation step (instead of
// redirecting straight to /app) so the owner actually sees the code their
// customers will need — it's otherwise never shown anywhere else on first run.
const createdPairingCode = ref('')
const codeCopied = ref(false)

function clearError() {
  errorMessage.value = ''
}

function bindAndEnter(
  body: { organizationSlug?: string; storeCode?: string; error?: string },
  fallbackError: string,
): boolean {
  if (!body.organizationSlug || !body.storeCode) {
    errorMessage.value = body.error || fallbackError
    return false
  }
  writeStaffTenant({ organizationSlug: body.organizationSlug, storeCode: body.storeCode })
  return true
}

async function submitSignup() {
  clearError()
  if (saving.value) return
  saving.value = true
  try {
    const response = await fetch('/api/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(signupForm),
    })
    const body = await response.json().catch(() => ({})) as {
      organizationSlug?: string
      storeCode?: string
      pairingCode?: string
      error?: string
    }
    if (!response.ok) {
      errorMessage.value = body.error || 'Unable to create your store.'
      return
    }
    if (bindAndEnter(body, 'Unable to create your store.')) {
      // Only on signup — pairing binds to an *existing* store, which already
      // has its own settings that must not be reset.
      writePendingInitialSettings({
        businessName: signupForm.businessName,
        businessMode: signupForm.businessMode,
        pairingCode: body.pairingCode ?? '',
      })
      createdPairingCode.value = body.pairingCode ?? ''
    }
  } catch {
    errorMessage.value = 'Something went wrong — check your connection and try again.'
  } finally {
    saving.value = false
  }
}

async function copyPairingCode() {
  try {
    await navigator.clipboard.writeText(createdPairingCode.value)
    codeCopied.value = true
    setTimeout(() => { codeCopied.value = false }, 2000)
  } catch {
    // Clipboard permission denied — the code is still shown on screen to copy manually.
  }
}

function continueToStore() {
  window.location.href = '/app'
}

async function submitPairing() {
  clearError()
  if (saving.value) return
  saving.value = true
  try {
    const response = await fetch('/api/resolve-staff-store-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: pairForm.pairingCode }),
    })
    const body = await response.json().catch(() => ({})) as { organizationSlug?: string; storeCode?: string; error?: string }
    if (!response.ok) {
      errorMessage.value = body.error || 'Unable to find that store.'
      return
    }
    if (bindAndEnter(body, 'Unable to find that store.')) {
      window.location.href = '/app'
    }
  } catch {
    errorMessage.value = 'Something went wrong — check your connection and try again.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="onboarding-shell">
    <!-- The Baguio Online Market product story, moved here off the landing page: the
         landing page is now the customer-facing delivery storefront, so the
         merchant pitch belongs on the page merchants actually register on.
         Hidden once signup succeeds — at that point they're a customer, not
         a prospect, and only need their store code. -->
    <PosMarketing v-if="!createdPairingCode" signup-href="#register" />

    <div id="register" class="auth-page">
    <section class="auth-card">
      <div class="auth-brand">
        <div class="auth-brand-mark">B</div>
        <strong>Baguio Online Market</strong>
      </div>

      <template v-if="createdPairingCode">
        <div class="auth-card__hero">
          <h1 class="auth-card__title">Your store is ready</h1>
          <p class="auth-card__copy">
            Share this store code with customers — they enter it in the Baguio Online Market app to find and order from your
            store. You can find it again anytime in Settings.
          </p>
        </div>

        <div class="onboarding-code">
          <span class="onboarding-code__value">{{ createdPairingCode }}</span>
          <button class="auth-password-toggle onboarding-code__copy" type="button" @click="copyPairingCode">
            <Check v-if="codeCopied" :size="16" />
            <Copy v-else :size="16" />
            <span>{{ codeCopied ? 'Copied' : 'Copy code' }}</span>
          </button>
        </div>

        <button class="primary-button auth-submit" type="button" @click="continueToStore">
          Continue to your store
        </button>
      </template>

      <template v-else>
        <div class="segmented-control auth-mode-switch" role="group" aria-label="Get started mode">
          <button
            class="segment-button"
            :class="{ active: mode === 'signup' }"
            type="button"
            @click="mode = 'signup'; clearError()"
          >
            <span>Sign up</span>
          </button>
          <button
            class="segment-button"
            :class="{ active: mode === 'pair' }"
            type="button"
            @click="mode = 'pair'; clearError()"
          >
            <span>I already have a store</span>
          </button>
        </div>

        <div v-if="mode === 'signup'" class="auth-card__hero">
          <h1 class="auth-card__title">Create your store</h1>
          <p class="auth-card__copy">Set up your business and start selling right away.</p>
        </div>
        <div v-else class="auth-card__hero">
          <h1 class="auth-card__title">Welcome back</h1>
          <p class="auth-card__copy">Enter your store's code to set up this device.</p>
        </div>

        <Transition name="auth-form-fade" mode="out-in">
          <form v-if="mode === 'signup'" key="signup" class="auth-form" @submit.prevent="submitSignup">
          <label class="settings-field">
            <span class="settings-row__label">Business name</span>
            <input v-model="signupForm.businessName" class="sheet-input" type="text" autocomplete="organization">
          </label>
          <label class="settings-field">
            <span class="settings-row__label">Your full name</span>
            <input v-model="signupForm.ownerFullName" class="sheet-input" type="text" autocomplete="name">
          </label>
          <label class="settings-field">
            <span class="settings-row__label">Username</span>
            <input v-model="signupForm.username" class="sheet-input" type="text" autocomplete="username">
          </label>
          <label class="settings-field">
            <span class="settings-row__label">Password</span>
            <div class="auth-password-field">
              <input
                v-model="signupForm.password"
                class="sheet-input"
                :type="passwordVisible ? 'text' : 'password'"
                autocomplete="new-password"
              >
              <button
                class="auth-password-toggle"
                type="button"
                :aria-label="passwordVisible ? 'Hide password' : 'Show password'"
                @click="passwordVisible = !passwordVisible"
              >
                <EyeOff v-if="passwordVisible" :size="16" />
                <Eye v-else :size="16" />
              </button>
            </div>
          </label>
          <label class="settings-field">
            <span class="settings-row__label">Business type</span>
            <AutocompleteSelect v-model="signupForm.businessMode" label="Business type" :options="businessModeOptions" />
          </label>

          <div class="onboarding-payment">
            <p class="onboarding-payment__title">Pay via GCash — {{ priceLabel }}</p>
            <p class="onboarding-payment__detail">Send to <strong>{{ GCASH_NUMBER }}</strong> ({{ GCASH_ACCOUNT_NAME }})</p>
            <p class="onboarding-payment__helper">Your store activates immediately — we'll verify the payment shortly after.</p>
          </div>

          <label class="settings-field">
            <span class="settings-row__label">GCash reference number</span>
            <input v-model="signupForm.gcashReference" class="sheet-input" type="text" autocomplete="off">
          </label>

          <button class="primary-button auth-submit" type="submit" :disabled="saving">
            {{ saving ? 'Creating your store…' : 'Create your store' }}
          </button>
        </form>

        <form v-else key="pair" class="auth-form" @submit.prevent="submitPairing">
          <label class="settings-field">
            <span class="settings-row__label">Store code</span>
            <input v-model="pairForm.pairingCode" class="sheet-input" type="text" autocomplete="off" placeholder="e.g. DEMO01">
          </label>
          <button class="primary-button auth-submit" type="submit" :disabled="saving">
            {{ saving ? 'Connecting…' : 'Continue' }}
          </button>
        </form>
        </Transition>

        <p v-if="errorMessage" class="auth-error">{{ errorMessage }}</p>
      </template>
    </section>
    </div>
  </div>
</template>

<style scoped>
/* The page is now marketing-first: the PosMarketing block fills the viewport,
   and the registration card sits below it as the conversion step. */
.onboarding-shell {
  min-height: 100vh;
  background: var(--bg-base);
  /* The form is built from app components (.primary-button, .segment-button,
     focus rings) which read --accent — still the app's blue. Re-point it here
     only, so the card matches the green marketing above it without changing
     the POS app's own accent. Dark ink rather than white on the bright green:
     white would be ~2.2:1, this is ~7.5:1. */
  --accent: #22c55e;
  --accent-pressed: #16a34a;
  --accent-text-on: #06240f;
}

.onboarding-shell .auth-page {
  scroll-margin-top: 24px;
}

.onboarding-payment {
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--fill);
  display: grid;
  gap: var(--space-1);
}

.onboarding-payment__title {
  margin: 0;
  font: var(--type-subhead);
  font-weight: 600;
  color: var(--text-primary);
}

.onboarding-payment__detail {
  margin: 0;
  font: var(--type-subhead);
  color: var(--text-primary);
}

.onboarding-payment__helper {
  margin: 0;
  font: var(--type-caption);
  color: var(--text-secondary);
}

.onboarding-code {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--fill);
}

.onboarding-code__value {
  font: var(--type-title2);
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--text-primary);
}

.onboarding-code__copy {
  position: static;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  width: auto;
  padding: 0 var(--space-3);
}
</style>
