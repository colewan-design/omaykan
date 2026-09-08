<script setup lang="ts">
import { Check, Eye, EyeOff } from '@lucide/vue'
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { businessModeLabel, SUPPORT_EMAIL, supportMailto, type BusinessMode } from '@pos/shared/index'
import { googleSignInAvailable, releaseGoogleButton, renderGoogleButton } from '@pos/web/commerce/google'
import { writePendingInitialSettings, writeStaffTenant } from '@pos/web/tenantBinding'
import MerchantFooter from './MerchantFooter.vue'
import MerchantHeader from './MerchantHeader.vue'
import MerchantPitch from './MerchantPitch.vue'

const mode = ref<'signup' | 'signin'>('signup')
const saving = ref(false)
const errorMessage = ref('')
const passwordVisible = ref(false)

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

// Set once signup succeeds, holding the UI on a confirmation step rather than
// redirecting straight to /app: the owner has an email to go and verify before
// they can sign in, and that instruction is worth a page of its own.
const signedUp = ref(false)

const googleAvailable = googleSignInAvailable()
const googleButton = ref<HTMLElement | null>(null)

function clearError() {
  errorMessage.value = ''
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
 * Bind this browser to one shop and hand over to the register.
 *
 * Signing in here does not carry a session into /app — the register asks for
 * the password again on its own lock screen, and that is the point: this page
 * decides *which shop* this browser is, and the register decides who is
 * standing at it. What crosses over is the tenant binding and nothing else.
 */
function enterStore(store: SignInStore) {
  writeStaffTenant({ organizationSlug: store.organizationSlug, storeCode: store.code })
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
    stores?: SignInStore[]
    error?: string
    message?: string
  }

  if (!response.ok) {
    errorMessage.value = messageFrom(body, 'That sign-in did not work.')
    return
  }

  const stores = body.stores ?? []

  if (stores.length === 0) {
    errorMessage.value = "This account isn't set up for any shop yet. Ask an admin to add you in Staff."
    return
  }

  if (stores.length === 1) {
    enterStore(stores[0])
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
                :class="{ active: mode === 'signin' }"
                type="button"
                @click="mode = 'signin'; clearError()"
              >
                <span>Sign in</span>
              </button>
            </div>

            <div v-if="mode === 'signup'" class="auth-card__hero">
              <h1 class="auth-card__title">Create your store</h1>
              <p class="auth-card__copy">Live on the app and ready to ring up sales in about a minute.</p>
            </div>
            <div v-else class="auth-card__hero">
              <h1 class="auth-card__title">Sign in</h1>
              <p class="auth-card__copy">Already work somewhere on Omaykan? Sign in and this browser opens that shop.</p>
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
                  <input
                    v-model="signupForm.businessTypeLabel"
                    class="sheet-input"
                    type="text"
                    list="business-type-suggestions"
                    autocomplete="off"
                    placeholder="e.g. Bakery, Flower shop, Pet supplies"
                    required
                  >
                  <datalist id="business-type-suggestions">
                    <option v-for="option in businessTypeSuggestions" :key="option" :value="option">
                      {{ option }}
                    </option>
                  </datalist>
                  <span class="onboarding-hint">Type it your way, or pick one of the suggestions.</span>
                </label>
                <label class="settings-field">
                  <span class="settings-row__label">Starter setup</span>
                  <AutocompleteSelect
                    :model-value="signupForm.businessMode"
                    label="Starter setup"
                    :options="businessModeOptions"
                    @update:model-value="(value) => updateBusinessMode(value as BusinessMode)"
                  />
                  <span class="onboarding-hint">Sets your starting catalog, layout, and checkout flow. Pick the closest fit; changeable later.</span>
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
                       a dead button: see commerce/google.ts. -->
                  <div v-if="googleAvailable" ref="googleButton" class="onboarding-google"></div>
                </template>
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
