<script setup lang="ts">
import { ArrowLeft, ArrowRight, Check, Eye, EyeOff, LockKeyhole } from '@lucide/vue'
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { businessModeLabel, SUPPORT_EMAIL, supportMailto, type BusinessMode } from '@pos/shared/index'
import { googleSignInAvailable, releaseGoogleButton, renderGoogleButton } from '@pos/core/services/google'
import { createBrowserPosRepository } from '@pos/data/index'
import {
  claimLocalCacheFor,
  resolveApiBaseUrl,
  writePendingInitialSettings,
  writeStaffTenant,
} from '@pos/web/tenantBinding'
import MerchantFooter from './MerchantFooter.vue'
import MerchantHeader from './MerchantHeader.vue'
import MerchantPitch from './MerchantPitch.vue'

const mode = ref<'signup' | 'signin'>('signup')
const saving = ref(false)
const errorMessage = ref('')
const passwordVisible = ref(false)
const signupStep = ref<1 | 2>(1)

const businessModeOptions = (['coffee-shop', 'grocery', 'restaurant', 'nail-salon'] as const).map((value) => ({
  value,
  label: businessModeLabel(value),
}))
const businessTypeSuggestions = businessModeOptions.map((option) => option.label)
const defaultBusinessMode = 'coffee-shop' as BusinessMode

const signupForm = reactive({
  businessName: '',
  ownerFullName: '',
  email: '',
  username: '',
  password: '',
  businessMode: defaultBusinessMode,
  businessTypeLabel: businessModeLabel(defaultBusinessMode),
})
const lastSuggestedBusinessType = ref(signupForm.businessTypeLabel)

const signInForm = reactive({
  identifier: '',
  password: '',
})

/**
 * The shops a sign-in turned up, when there is more than one.
 *
 * A person who works at one shop never sees this. A manager covering three
 * does, because the alternative — picking the first — binds this browser to a
 * branch they may not have meant and every order they advance afterwards is at
 * the wrong counter.
 */
interface SignInStore {
  id: string
  code: string
  name: string
  organizationSlug: string
}
const storeChoices = ref<SignInStore[]>([])
// The unscoped token from the last sign-in, held until a shop is chosen.
let signInToken = ''

// Set once signup succeeds, holding the UI on a confirmation step rather than
// redirecting straight to /app: the owner has an email to go and verify before
// they can sign in, and that instruction is worth a page of its own.
const signedUp = ref(false)

const googleAvailable = googleSignInAvailable()
const googleButton = ref<HTMLElement | null>(null)

function clearError() {
  errorMessage.value = ''
}

function advanceSignup() {
  clearError()
  if (!signupForm.ownerFullName.trim() || !signupForm.email.trim() || signupForm.password.length < 6) {
    errorMessage.value = 'Add your name, a valid email, and a password with at least 6 characters.'
    return
  }
  if (!/^\S+@\S+\.\S+$/.test(signupForm.email)) {
    errorMessage.value = 'Enter a valid email address.'
    return
  }
  signupStep.value = 2
}

function updateBusinessMode(value: BusinessMode) {
  const nextLabel = businessModeLabel(value)
  const currentLabel = signupForm.businessTypeLabel.trim()
  const shouldSyncBusinessType = currentLabel === '' || currentLabel === lastSuggestedBusinessType.value

  signupForm.businessMode = value
  lastSuggestedBusinessType.value = nextLabel

  if (shouldSyncBusinessType) {
    signupForm.businessTypeLabel = nextLabel
  }
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

/**
 * Bind this browser to one shop, open it, and hand over to the register.
 *
 * This used to carry only the tenant binding, and the register asked for the
 * password again — so every sign-in here was two, and the second landed on
 * /app/auth looking like the first had failed. The shop is opened here now,
 * with the token this page's sign-in was given, and the session is written
 * where the register reads it at boot. The cache is claimed for the shop
 * first: /app's boot wipes a cache that names another tenant, session and all.
 *
 * A refusal (the shop is suspended, the membership went between the two
 * calls) is shown here, where the person is, rather than on a sign-in screen
 * that would not say why.
 */
async function enterStore(store: SignInStore) {
  clearError()
  const tenant = { organizationSlug: store.organizationSlug, storeCode: store.code }
  writeStaffTenant(tenant)

  if (signInToken) {
    saving.value = true
    try {
      await claimLocalCacheFor(tenant)
      await createBrowserPosRepository({ sync: { apiBaseUrl: resolveApiBaseUrl(), ...tenant } })
        .adoptRemoteSignIn({ token: signInToken, storeId: store.id })
    } catch (error) {
      errorMessage.value = error instanceof Error && error.message
        ? error.message
        : 'Could not open that shop. Try again.'
      saving.value = false
      return
    }
  }

  window.location.href = '/app'
}

async function submitSignup() {
  clearError()
  if (saving.value) return
  const businessTypeLabel = signupForm.businessTypeLabel.trim()
  if (!businessTypeLabel) {
    errorMessage.value = 'Please tell us what kind of business you run.'
    return
  }
  saving.value = true
  try {
    const response = await fetch('/api/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...signupForm,
        businessTypeLabel,
      }),
    })
    const body = await response.json().catch(() => ({})) as {
      organizationSlug?: string
      storeCode?: string
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
      })
      signedUp.value = true
      window.scrollTo({ top: 0 })
    }
  } catch {
    errorMessage.value = 'Something went wrong — check your connection and try again.'
  } finally {
    saving.value = false
  }
}

function continueToStore() {
  window.location.href = '/app'
}

/**
 * Sign in and find out which shops this account can open.
 *
 * This replaced "add a device", which took the shop's code: one string that
 * both found the shop and let a till join it, so anyone who could read a
 * receipt could add themselves a register. The proof is a person now.
 */
async function submitSignIn() {
  clearError()
  if (saving.value) return
  saving.value = true
  storeChoices.value = []
  try {
    await consumeSignIn(await fetch('/api/staff/sign-in', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(signInForm),
    }))
  } catch {
    errorMessage.value = 'Something went wrong — check your connection and try again.'
  } finally {
    saving.value = false
  }
}

async function signInWithGoogle(credential: string) {
  clearError()
  if (saving.value) return
  saving.value = true
  storeChoices.value = []
  try {
    await consumeSignIn(await fetch('/api/staff/auth/google', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ credential }),
    }))
  } catch {
    errorMessage.value = 'Something went wrong — check your connection and try again.'
  } finally {
    saving.value = false
  }
}

/** Both sign-in doors end here: one shop goes straight through, several ask. */
async function consumeSignIn(response: Response) {
  const body = await response.json().catch(() => ({})) as {
    token?: string
    stores?: SignInStore[]
    error?: string
    message?: string
  }

  if (!response.ok) {
    errorMessage.value = messageFrom(body, 'That sign-in did not work.')
    return
  }

  signInToken = body.token ?? ''

  const stores = body.stores ?? []

  if (stores.length === 0) {
    errorMessage.value = "This account isn't set up for any shop yet. Ask an admin to add you in Staff."
    return
  }

  if (stores.length === 1) {
    await enterStore(stores[0])
    return
  }

  storeChoices.value = stores
}

/**
 * Google's button is an iframe it draws itself, so it can only be rendered once
 * the element exists — which is after the sign-in tab is shown, not on mount.
 */
watch([mode, googleButton], async () => {
  if (mode.value !== 'signin' || !googleAvailable || !googleButton.value) return

  try {
    await renderGoogleButton(googleButton.value, signInWithGoogle)
  } catch {
    // Blocked, offline, or misconfigured. Password sign-in is unaffected and
    // the empty slot simply collapses.
  }
})

onBeforeUnmount(() => releaseGoogleButton(signInWithGoogle))
</script>

<template>
  <div class="onboarding-shell">
    <MerchantHeader :compact="signedUp" />

    <!-- Before signup: the merchant pitch, with the form handed to its closing
         section so the two sit side by side rather than the form floating on a
         blank field below the story. After signup they're a customer, not a
         prospect — the pitch comes down and what to do next is the whole page. -->
    <MerchantPitch v-if="!signedUp">
      <template #form>
        <!-- .auth-page carries the green form palette every control inside
             reads; --inline drops its full-screen framing so it can sit in the
             pitch's grid column. -->
        <div class="auth-page auth-page--inline">
          <section class="auth-card">
            <div v-if="mode === 'signup'" class="signup-progress" aria-label="Signup progress">
              <button type="button" :class="{ active: signupStep === 1 }" @click="signupStep = 1; clearError()"><b>1</b><span>Create your account</span></button>
              <i><ArrowRight :size="15" /></i>
              <button type="button" :class="{ active: signupStep === 2 }" @click="advanceSignup"><b>2</b><span>Set up your store</span></button>
            </div>
            <button v-else class="signup-back" type="button" @click="mode = 'signup'; clearError()"><ArrowLeft :size="14" /> Create a store</button>

            <div v-if="mode === 'signup'" class="auth-card__hero">
              <h1 class="auth-card__title">{{ signupStep === 1 ? 'Create your account' : 'Set up your store' }}</h1>
              <p class="auth-card__copy">
                {{ signupStep === 1 ? 'Already have an account?' : 'Tell us a little about your business.' }}
                <button v-if="signupStep === 1" class="signin-link" type="button" @click="mode = 'signin'; clearError()">Sign in</button>
              </p>
            </div>
            <div v-else class="auth-card__hero">
              <h1 class="auth-card__title">Sign in</h1>
              <p class="auth-card__copy">Already work somewhere on Omaykan? Sign in and this browser opens that shop.</p>
            </div>

            <Transition name="auth-form-fade" mode="out-in">
              <form v-if="mode === 'signup'" key="signup" class="auth-form" @submit.prevent="submitSignup">
                <template v-if="signupStep === 1">
                  <label class="settings-field"><span class="settings-row__label">Full name</span><input v-model="signupForm.ownerFullName" class="sheet-input" type="text" autocomplete="name" placeholder="Juan Dela Cruz" required></label>
                  <label class="settings-field"><span class="settings-row__label">Email address</span><input v-model="signupForm.email" class="sheet-input" type="email" autocomplete="email" placeholder="name@example.com" required></label>
                  <label class="settings-field"><span class="settings-row__label">Password</span><div class="auth-password-field"><input v-model="signupForm.password" class="sheet-input" :type="passwordVisible ? 'text' : 'password'" autocomplete="new-password" minlength="6" placeholder="Create a password" required><button class="auth-password-toggle" type="button" :aria-label="passwordVisible ? 'Hide password' : 'Show password'" @click="passwordVisible = !passwordVisible"><EyeOff v-if="passwordVisible" :size="16" /><Eye v-else :size="16" /></button></div></label>
                  <button class="primary-button auth-submit" type="button" @click="advanceSignup">Continue to store setup <ArrowRight :size="16" /></button>
                </template>
                <template v-else>
                  <label class="settings-field"><span class="settings-row__label">Business name</span><input v-model="signupForm.businessName" class="sheet-input" type="text" autocomplete="organization" placeholder="Your shop name" required></label>
                  <label class="settings-field"><span class="settings-row__label">Username</span><input v-model="signupForm.username" class="sheet-input" type="text" autocomplete="username" placeholder="What you’ll use to sign in" required></label>
                  <label class="settings-field"><span class="settings-row__label">Business type</span><input v-model="signupForm.businessTypeLabel" class="sheet-input" type="text" list="business-type-suggestions" autocomplete="off" placeholder="e.g. Bakery, Flower shop, Pet supplies" required><datalist id="business-type-suggestions"><option v-for="option in businessTypeSuggestions" :key="option" :value="option">{{ option }}</option></datalist></label>
                  <label class="settings-field"><span class="settings-row__label">Starter setup</span><AutocompleteSelect :model-value="signupForm.businessMode" label="Starter setup" :options="businessModeOptions" @update:model-value="(value) => updateBusinessMode(value as BusinessMode)" /></label>
                  <p class="onboarding-note"><strong>No payment now.</strong> Omaykan is free while we’re in early access.</p>
                  <div class="signup-buttons"><button class="signup-back" type="button" @click="signupStep = 1; clearError()"><ArrowLeft :size="14" /> Back</button><button class="primary-button auth-submit" type="submit" :disabled="saving">{{ saving ? 'Creating your store…' : 'Create your store' }} <ArrowRight :size="16" /></button></div>
                </template>
              </form>

              <form v-else key="signin" class="auth-form" @submit.prevent="submitSignIn">
                <!-- Several shops: the sign-in worked, and the only thing left
                     is which counter this browser is standing at. -->
                <template v-if="storeChoices.length">
                  <p class="onboarding-hint">You work at more than one shop. Which is this one?</p>
                  <button
                    v-for="store in storeChoices"
                    :key="store.id"
                    class="primary-button auth-submit"
                    type="button"
                    :disabled="saving"
                    @click="enterStore(store)"
                  >
                    {{ store.name }}
                  </button>
                </template>

                <template v-else>
                  <label class="settings-field">
                    <span class="settings-row__label">Username or email</span>
                    <input v-model="signInForm.identifier" class="sheet-input" type="text" autocomplete="username" required>
                  </label>
                  <label class="settings-field">
                    <span class="settings-row__label">Password</span>
                    <input v-model="signInForm.password" class="sheet-input" type="password" autocomplete="current-password" required>
                  </label>
                  <button class="primary-button auth-submit" type="submit" :disabled="saving">
                    {{ saving ? 'Signing in…' : 'Sign in' }}
                  </button>

                  <!-- Absent entirely on a build with no client id, rather than
                       a dead button: see services/google.ts. -->
                  <div v-if="googleAvailable" ref="googleButton" class="onboarding-google"></div>
                </template>
              </form>
            </Transition>

            <p v-if="errorMessage" class="auth-error">{{ errorMessage }}</p>

            <p class="onboarding-support"><LockKeyhole :size="13" /> Your information is safe and secure.</p>
          </section>
        </div>
      </template>
    </MerchantPitch>

    <!-- Success: one instruction, which is to go and read their email. -->
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
            Verify the email we just sent, then sign in with the account you just made. Your shop
            has its own page and its own link — both are in Settings &rsaquo; Online Store.
          </p>
        </div>

        <ol class="onboarding-next">
          <li>Verify your email address, so you can sign in.</li>
          <li>Add your products, or edit the starter catalog we set up for you.</li>
          <li>Ring up your first sale at the counter.</li>
          <li>Add whoever works with you under Staff — they sign in as themselves.</li>
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

.auth-page--inline :deep(.auth-card) {
  width: 100%;
  max-width: none;
  gap: 18px;
  padding: 30px 34px 28px;
  border: 0;
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(2, 43, 22, 0.24);
}

.auth-page--inline :deep(.auth-card__title) { font-size: 24px; }
.auth-page--inline :deep(.auth-form) { gap: 14px; }
.auth-page--inline :deep(.settings-field) { gap: 6px; }
.auth-page--inline :deep(.settings-row__label) { color: #25312b; font-size: 11px; }
.auth-page--inline :deep(.sheet-input) { min-height: 44px; border-radius: 8px; background: #fff; font-size: 12px; }
.auth-page--inline :deep(.primary-button) { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 46px; border-radius: 8px; background: #087c3d; font-size: 12px; }

.signup-progress { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 10px; padding-bottom: 15px; border-bottom: 1px solid #e8eee9; }
.signup-progress button { display: flex; align-items: center; gap: 8px; padding: 0; border: 0; background: none; color: #8b9590; font-size: 10px; font-weight: 700; text-align: left; cursor: pointer; }
.signup-progress button b { display: grid; place-items: center; width: 24px; height: 24px; flex: 0 0 auto; border-radius: 50%; background: #dfe5e1; color: #fff; font-size: 10px; }
.signup-progress button.active { color: #087c3d; }.signup-progress button.active b { background: #087c3d; }
.signup-progress i { display: grid; place-items: center; color: #b4beb8; }
.signin-link { padding: 0; border: 0; background: none; color: #087c3d; font: inherit; font-weight: 800; cursor: pointer; }
.signup-back { display: inline-flex; align-items: center; gap: 6px; width: fit-content; padding: 8px 0; border: 0; background: none; color: #66716b; font-size: 11px; font-weight: 700; cursor: pointer; }
.signup-buttons { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 14px; }.signup-buttons .auth-submit { width: 100%; }

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
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
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
  .auth-page--inline :deep(.auth-card) { padding: 26px 20px; }
  .signup-progress button span { display: none; }
}
</style>
