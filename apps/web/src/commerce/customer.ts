import { computed, reactive, ref } from 'vue'
import {
  ApiRequestError,
  createCustomerAddress,
  createCustomerPaymentMethod,
  deleteCustomerAddress,
  deleteCustomerPaymentMethod,
  fetchCustomerAccount,
  loginCustomer,
  logoutCustomer,
  registerCustomer,
  requestPasswordReset,
  resetCustomerPassword,
  signInWithGoogle,
  updateCustomerAccount,
  updateCustomerAddress,
  updateCustomerEmail,
  updateCustomerPassword,
  updateCustomerPaymentMethod,
  type CustomerAccount,
  type CustomerAddressInput,
  type CustomerPaymentKind,
  type CustomerPreferences,
  type SubstitutionPreference,
} from '@pos/web/commerce/api'
import { customerToken, setCustomerToken } from '@pos/web/commerce/session'
import { forgetGoogleSession } from '@pos/web/commerce/google'

/*
 * The signed-in shopper.
 *
 * This used to be the whole account: name, addresses and payment methods lived
 * in localStorage, because the API had no notion of a customer at all. It now
 * holds a Sanctum token and the server's copy of the account, so the same
 * person on a laptop and a phone is one account, and signing out on one device
 * doesn't erase what the other one saved.
 *
 * The store is module-level, like the cart: the header greets whoever is
 * signed in, and it must be the same session the portal is showing.
 *
 * Two things are still device-local, and honestly so: gift card codes and
 * store credit. The backend has no gift card system to record them against —
 * inventing a balance server-side would be a bigger product decision than
 * authentication, so a saved code is still just a code kept for checkout.
 */

export type {
  CustomerAccount,
  CustomerAddress,
  CustomerPaymentKind,
  CustomerPaymentMethod,
  CustomerPreferences,
  SubstitutionPreference,
} from '@pos/web/commerce/api'

export const substitutionOptions: { value: SubstitutionPreference; label: string; hint: string }[] = [
  { value: 'call', label: 'Call me', hint: 'The rider rings before swapping anything.' },
  { value: 'best-match', label: 'Pick the closest match', hint: 'Same item, nearest size or brand.' },
  { value: 'refund', label: 'Leave it out', hint: 'Refund the line and deliver the rest.' },
]

interface AccountState {
  account: CustomerAccount | null
  /** True until the stored token has been checked against the API. */
  hydrating: boolean
}

const state = reactive<AccountState>({ account: null, hydrating: customerToken() !== null })

let hydrateStarted = false

/**
 * Trades the stored token for the account it belongs to.
 *
 * A token that the server no longer honours — revoked from another device, or
 * cleared by a password reset — is dropped here rather than left to fail the
 * next write. Anything else (offline, API down) leaves the token alone: a flaky
 * network is not a reason to sign someone out.
 */
async function hydrate(): Promise<void> {
  if (hydrateStarted) return
  hydrateStarted = true

  if (customerToken() === null) {
    state.hydrating = false
    return
  }

  try {
    state.account = (await fetchCustomerAccount()).account
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 401) {
      setCustomerToken(null)
      state.account = null
    }
  } finally {
    state.hydrating = false
  }
}

function adopt(session: { account: CustomerAccount; token?: string }): CustomerAccount {
  if (session.token) setCustomerToken(session.token)
  state.account = session.account
  state.hydrating = false
  return session.account
}

// -- Gift cards: device-local, for want of anywhere else ---------------------

export interface SavedGiftCard {
  id: string
  code: string
  addedAt: string
}

const GIFT_CARD_KEY = 'sf_gift_cards'

function loadGiftCards(): SavedGiftCard[] {
  try {
    const raw = window.localStorage.getItem(GIFT_CARD_KEY)
    const parsed = raw ? (JSON.parse(raw) as SavedGiftCard[]) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const giftCardList = ref<SavedGiftCard[]>(loadGiftCards())

function persistGiftCards() {
  try {
    window.localStorage.setItem(GIFT_CARD_KEY, JSON.stringify(giftCardList.value))
  } catch {
    // Private mode / quota — the list still holds for this session.
  }
}

/** `crypto.randomUUID` needs a secure context; the fallback covers plain http dev. */
function newId(): string {
  try {
    return window.crypto.randomUUID()
  } catch {
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
  }
}

/**
 * A referral code the customer can read out loud: derived from their email so
 * it is stable across a sign-out and back in, and so it needs no endpoint to
 * issue it. Not a secret — it names the referrer on a friend's first order.
 */
function referralCodeFor(email: string): string {
  let hash = 0
  for (const char of email.trim().toLowerCase()) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  }
  const handle = email.split('@')[0]?.replace(/[^a-z0-9]/gi, '').slice(0, 6).toUpperCase() || 'FRIEND'
  return `${handle}${hash.toString(36).slice(-4).toUpperCase()}`
}

export function useCustomerAccount() {
  void hydrate()

  const account = computed(() => state.account)
  const signedIn = computed(() => state.account !== null)
  const hydrating = computed(() => state.hydrating)

  // -- Session -------------------------------------------------------------

  async function register(input: { name: string; email: string; phone?: string; password: string }) {
    return registerCustomer(input)
  }

  async function signIn(email: string, password: string) {
    return adopt(await loginCustomer(email, password))
  }

  /**
   * The Google button's half of sign-in.
   *
   * Takes the ID token the button produced and ends where `signIn` ends: one
   * session, one account object, no trace downstream of which door was used.
   * Whether this made an account or found one is the server's business — a
   * shopper pressing "Continue with Google" does not care which it was.
   */
  async function signInWithGoogleCredential(credential: string) {
    return adopt(await signInWithGoogle(credential))
  }

  async function sendPasswordReset(email: string) {
    return requestPasswordReset(email)
  }

  async function resetPassword(input: { token: string; email: string; password: string }) {
    return adopt(await resetCustomerPassword(input))
  }

  /**
   * Signs out this device only. The API call is best-effort: if it fails the
   * local token still goes, because the person in front of the screen asked to
   * be signed out and a network error is no reason to leave them signed in.
   */
  async function signOut() {
    try {
      await logoutCustomer()
    } catch {
      // Already-invalid token, or offline. Either way, drop it below.
    }
    setCustomerToken(null)
    state.account = null
    // Otherwise GIS re-signs them in without asking on the next visit, which
    // makes "sign out" look broken to anyone signing out to hand over a laptop.
    forgetGoogleSession()
  }

  // -- Profile -------------------------------------------------------------

  async function updateProfile(patch: {
    name?: string
    phone?: string
    preferences?: Partial<CustomerPreferences>
  }) {
    state.account = (await updateCustomerAccount(patch)).account
  }

  async function changeEmail(email: string, currentPassword: string) {
    state.account = (await updateCustomerEmail(email, currentPassword)).account
  }

  async function changePassword(currentPassword: string, password: string) {
    state.account = (await updateCustomerPassword(currentPassword, password)).account
  }

  // -- Addresses -----------------------------------------------------------

  const addresses = computed(() => state.account?.addresses ?? [])
  const defaultAddress = computed(() => addresses.value.find((address) => address.isDefault) ?? null)

  async function addAddress(input: CustomerAddressInput) {
    state.account = (await createCustomerAddress(input)).account
  }

  async function editAddress(id: string, patch: Partial<CustomerAddressInput>) {
    state.account = (await updateCustomerAddress(id, patch)).account
  }

  async function setDefaultAddress(id: string) {
    state.account = (await updateCustomerAddress(id, { isDefault: true })).account
  }

  async function removeAddress(id: string) {
    state.account = (await deleteCustomerAddress(id)).account
  }

  // -- Payment methods -----------------------------------------------------

  const paymentMethods = computed(() => state.account?.paymentMethods ?? [])
  const defaultPaymentMethod = computed(
    () => paymentMethods.value.find((method) => method.isDefault) ?? null,
  )

  async function addPaymentMethod(input: { kind: CustomerPaymentKind; detail?: string }) {
    state.account = (await createCustomerPaymentMethod(input)).account
  }

  async function setDefaultPaymentMethod(id: string) {
    state.account = (await updateCustomerPaymentMethod(id, { isDefault: true })).account
  }

  async function removePaymentMethod(id: string) {
    state.account = (await deleteCustomerPaymentMethod(id)).account
  }

  // -- Gift cards and store credit -----------------------------------------

  const giftCards = computed(() => giftCardList.value)

  /**
   * Records a code to hand over at checkout. Deliberately not credited to a
   * balance: only the shop that sold the card knows what is left on it, and
   * there is no endpoint to ask. Inventing the number here would be lying to
   * the customer about money.
   */
  function saveGiftCard(code: string): SavedGiftCard | null {
    const normalized = code.trim().toUpperCase()
    if (!normalized) return null
    if (giftCardList.value.some((card) => card.code === normalized)) return null

    const card: SavedGiftCard = { id: newId(), code: normalized, addedAt: new Date().toISOString() }
    giftCardList.value = [card, ...giftCardList.value]
    persistGiftCards()
    return card
  }

  function removeGiftCard(id: string) {
    giftCardList.value = giftCardList.value.filter((card) => card.id !== id)
    persistGiftCards()
  }

  // Zero until there is a system that can credit it. The ledger it would be
  // the sum of does not exist server-side yet, and a balance nobody can
  // explain is worse than an honest nothing.
  const storeCreditCents = computed(() => 0)

  // -- Referrals -----------------------------------------------------------

  const referralCode = computed(() => (state.account ? referralCodeFor(state.account.email) : ''))
  const referralLink = computed(() =>
    referralCode.value ? `${window.location.origin}/?ref=${referralCode.value}` : '',
  )

  return {
    account,
    signedIn,
    hydrating,
    register,
    signIn,
    signInWithGoogle: signInWithGoogleCredential,
    signOut,
    sendPasswordReset,
    resetPassword,
    updateProfile,
    changeEmail,
    changePassword,
    addresses,
    defaultAddress,
    addAddress,
    editAddress,
    setDefaultAddress,
    removeAddress,
    paymentMethods,
    defaultPaymentMethod,
    addPaymentMethod,
    setDefaultPaymentMethod,
    removePaymentMethod,
    giftCards,
    saveGiftCard,
    removeGiftCard,
    storeCreditCents,
    referralCode,
    referralLink,
  }
}

/** Shared by the sections that copy something (referral link, gift code). */
export function useClipboard() {
  const copied = ref('')
  let timer: ReturnType<typeof setTimeout> | undefined

  async function copy(value: string, key = value) {
    try {
      await navigator.clipboard.writeText(value)
      copied.value = key
      clearTimeout(timer)
      timer = setTimeout(() => (copied.value = ''), 2000)
    } catch {
      // Denied clipboard permission, or an insecure origin — the value is
      // on screen and selectable either way.
    }
  }

  return { copied, copy }
}

/** The message to show a customer when a call fails, never the raw error. */
export function messageFor(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  return error instanceof Error && error.message ? error.message : fallback
}
