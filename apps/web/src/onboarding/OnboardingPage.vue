<script setup lang="ts">
import { Check, Copy, Eye, EyeOff } from '@lucide/vue'
import { reactive, ref } from 'vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { businessModeLabel, SUPPORT_EMAIL, supportMailto, type BusinessMode } from '@pos/shared/index'
import { writePendingInitialSettings, writeStaffTenant } from '@pos/web/tenantBinding'
import MerchantFooter from './MerchantFooter.vue'
import MerchantHeader from './MerchantHeader.vue'
import MerchantPitch from './MerchantPitch.vue'

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
  email: '',
  username: '',
  password: '',
  businessMode: 'coffee-shop' as BusinessMode,
})

const pairForm = reactive({
  pairingCode: '',
})

// Set once signup succeeds, holding the UI on a confirmation step (instead of
// redirecting straight to /app) so the owner actually sees the code their
// customers will need — it's otherwise never shown anywhere else on first run.
const createdPairingCode = ref('')
const codeCopied = ref(false)

function clearError() {
  errorMessage.value = ''
}

/**
 * Laravel returns validation failures as `{ message, errors }`, not `{ error }`
 * — reading only `error` swallowed the useful ones ("that username is already
 * taken") behind the generic fallback.
 */
function messageFrom(body: { error?: string; message?: string }, fallback: string): string {
  return body.error || body.message || fallback
}

function bindAndEnter(
  body: { organizationSlug?: string; storeCode?: string; error?: string; message?: string },
  fallbackError: string,
): boolean {
  if (!body.organizationSlug || !body.storeCode) {
    errorMessage.value = messageFrom(body, fallbackError)
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
      message?: string
    }
    if (!response.ok) {
      errorMessage.value = messageFrom(body, 'Unable to create your store.')
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
      window.scrollTo({ top: 0 })
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
    const body = await response.json().catch(() => ({})) as {
      organizationSlug?: string
      storeCode?: string
      error?: string
      message?: string
    }
    if (!response.ok) {
      errorMessage.value = messageFrom(body, 'Unable to find that store.')
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
    <MerchantHeader :compact="Boolean(createdPairingCode)" />

    <!-- Before signup: the merchant pitch, with the form handed to its closing
         section so the two sit side by side rather than the form floating on a
         blank field below the story. After signup they're a customer, not a
         prospect — the pitch comes down and the store code is the whole page. -->
    <MerchantPitch v-if="!createdPairingCode">
      <template #form>
        <!-- .auth-page carries the green form palette every control inside
             reads; --inline drops its full-screen framing so it can sit in the
             pitch's grid column. -->
        <div class="auth-page auth-page--inline">
          <section class="auth-card">
            <div class="auth-brand">
              <BrandLogo variant="light" :size="21" />
            </div>

            <div class="segmented-control auth-mode-switch" role="group" aria-label="Get started mode">
              <button
                class="segment-button"
                :class="{ active: mode === 'signup' }"
                type="button"
                @click="mode = 'signup'; clearError()"
              >
                <span>Create a store</span>
              </button>
              <button
                class="segment-button"
                :class="{ active: mode === 'pair' }"
                type="button"
                @click="mode = 'pair'; clearError()"
              >
                <span>Add a device</span>
              </button>
            </div>

            <div v-if="mode === 'signup'" class="auth-card__hero">
              <h1 class="auth-card__title">Create your store</h1>
              <p class="auth-card__copy">Live on the app and ready to ring up sales in about a minute.</p>
            </div>
            <div v-else class="auth-card__hero">
              <h1 class="auth-card__title">Add a device</h1>
              <p class="auth-card__copy">Already have a store? Enter its code to set this device up as another register.</p>
            </div>

            <Transition name="auth-form-fade" mode="out-in">
              <form v-if="mode === 'signup'" key="signup" class="auth-form" @submit.prevent="submitSignup">
                <label class="settings-field">
                  <span class="settings-row__label">Business name</span>
                  <input v-model="signupForm.businessName" class="sheet-input" type="text" autocomplete="organization" required>
                </label>
                <label class="settings-field">
                  <span class="settings-row__label">Your full name</span>
                  <input v-model="signupForm.ownerFullName" class="sheet-input" type="text" autocomplete="name" required>
                </label>
                <label class="settings-field">
                  <span class="settings-row__label">Email address</span>
                  <input v-model="signupForm.email" class="sheet-input" type="email" autocomplete="email" required>
                  <span class="onboarding-hint">Where we send your store details. Not shown to customers.</span>
                </label>
                <label class="settings-field">
                  <span class="settings-row__label">Username</span>
                  <input v-model="signupForm.username" class="sheet-input" type="text" autocomplete="username" required>
                  <span class="onboarding-hint">What you type to sign in.</span>
                </label>
                <label class="settings-field">
                  <span class="settings-row__label">Password</span>
                  <div class="auth-password-field">
                    <input
                      v-model="signupForm.password"
                      class="sheet-input"
                      :type="passwordVisible ? 'text' : 'password'"
                      autocomplete="new-password"
                      minlength="6"
                      required
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
                  <span class="onboarding-hint">At least 6 characters.</span>
                </label>
                <label class="settings-field">
                  <span class="settings-row__label">Business type</span>
                  <AutocompleteSelect v-model="signupForm.businessMode" label="Business type" :options="businessModeOptions" />
                  <span class="onboarding-hint">Sets your starting catalog, layout, and checkout flow. Changeable later.</span>
                </label>

                <!-- Was a GCash transfer collected mid-form, against a
                     placeholder number, while every other surface promised
                     early access was free. Nothing is charged now, so the form
                     says so plainly instead of asking for a reference. -->
                <p class="onboarding-note">
                  <strong>No payment now.</strong> Omaykan is free while we're in early access, and
                  we'll tell you well before that changes.
                </p>

                <button class="primary-button auth-submit" type="submit" :disabled="saving">
                  {{ saving ? 'Creating your store…' : 'Create your store' }}
                </button>
              </form>

              <form v-else key="pair" class="auth-form" @submit.prevent="submitPairing">
                <label class="settings-field">
                  <span class="settings-row__label">Store code</span>
                  <input v-model="pairForm.pairingCode" class="sheet-input" type="text" autocomplete="off" placeholder="e.g. DEMO01" required>
                  <span class="onboarding-hint">Find it in Settings on a device that's already set up.</span>
                </label>
                <button class="primary-button auth-submit" type="submit" :disabled="saving">
                  {{ saving ? 'Connecting…' : 'Continue' }}
                </button>
              </form>
            </Transition>

            <p v-if="errorMessage" class="auth-error">{{ errorMessage }}</p>

            <p class="onboarding-support">
              Stuck signing up? Email <a :href="supportMailto('Omaykan signup')">{{ SUPPORT_EMAIL }}</a>.
            </p>
          </section>
        </div>
      </template>
    </MerchantPitch>

    <!-- Success: the store code is the only thing on screen worth reading. -->
    <div v-else class="auth-page onboarding-done">
      <section class="auth-card">
        <div class="auth-brand">
          <BrandLogo variant="light" :size="21" />
        </div>

        <div class="auth-card__hero">
          <span class="onboarding-done__badge">
            <Check :size="15" />
            Store created
          </span>
          <h1 class="auth-card__title">You're live on Omaykan.</h1>
          <p class="auth-card__copy">
            Share this store code with your customers — they enter it in the Omaykan app to find your
            shop and order from it. It's in Settings whenever you need it again.
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

        <ol class="onboarding-next">
          <li>Add your products, or edit the starter catalog we set up for you.</li>
          <li>Ring up your first sale at the counter.</li>
          <li>Hand the code to customers so they can order for delivery.</li>
        </ol>

        <button class="primary-button auth-submit" type="button" @click="continueToStore">
          Continue to your store
        </button>

        <p class="onboarding-support">
          Something not right? Email <a :href="supportMailto('Omaykan signup')">{{ SUPPORT_EMAIL }}</a>.
        </p>
      </section>
    </div>

    <MerchantFooter />
  </div>
</template>

<style scoped>
/* Marketing-first page: header, the pitch (which now holds the form in its
   closing section), then the footer. The form's own palette lives on
   .auth-page in packages/core/src/styles/app.css — nothing is re-pointed here,
   so the POS app keeps its own accent. */
.onboarding-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #ffffff;
}

/* The success step is the whole viewport, minus the header that stays above it. */
.onboarding-done {
  flex: 1;
  min-height: calc(100vh - 68px);
  background: linear-gradient(180deg, #f5f9f6 0%, #ffffff 55%);
}

.onboarding-done__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-self: start;
  margin-bottom: 4px;
  padding: 5px 12px 5px 9px;
  border-radius: 980px;
  background: color-mix(in srgb, #1a6b3c 10%, transparent);
  font-size: 12.5px;
  font-weight: 700;
  color: #1a6b3c;
}

/* Field-level help. Small and secondary — it explains, it never warns. */
.onboarding-hint {
  font: var(--type-caption);
  line-height: 1.5;
  color: var(--text-secondary);
}

.onboarding-note {
  margin: 0;
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, #1a6b3c 7%, transparent);
  font: var(--type-caption);
  line-height: 1.6;
  color: var(--text-secondary);
}
.onboarding-note strong { color: var(--text-primary); font-weight: 700; }

.onboarding-next {
  display: grid;
  gap: 10px;
  margin: 0;
  padding-left: 20px;
  font: var(--type-caption);
  line-height: 1.55;
  color: var(--text-secondary);
}

.onboarding-support {
  margin: 0;
  font: var(--type-caption);
  line-height: 1.55;
  color: var(--text-secondary);
  text-align: center;
}
/* --text-primary, not --accent: the accent on this card is the deep green,
   which is fine as a button fill behind white ink but thin as 13px link text.
   The underline carries the affordance instead. */
.onboarding-support a {
  color: var(--text-primary);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 2px;
  word-break: break-word;
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
  /* "Copy code" wraps to two lines and squeezes the icon out otherwise. */
  white-space: nowrap;
}

@media (max-width: 720px) {
  .onboarding-done { min-height: 0; padding-top: var(--space-6); }
}
</style>
