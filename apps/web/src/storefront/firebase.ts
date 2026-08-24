import { initializeApp, getApps, getApp } from 'firebase/app'
import { connectFirestoreEmulator, getFirestore } from 'firebase/firestore'
import type { BusinessMode } from '@pos/shared/index'

// Unauthenticated Firebase client for the public storefront — deliberately
// separate from packages/data/src/firebase-sync.ts, which is coupled to the
// staff app's authenticated sync session (Auth, outbox, local cache). The
// storefront only ever reads the public catalog directly and never signs in
// or writes Firestore directly; order creation and store-code lookups go
// through the Vercel API routes in /api instead (see API_BASE below) — this
// project stays on Firebase's free Spark plan, which can't deploy Cloud
// Functions (that needs the paid Blaze plan), so those two endpoints live on
// Vercel's free tier and talk to Firestore via firebase-admin there.
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

const app = getApps().length ? getApp() : initializeApp(firebaseConfig)

export const db = getFirestore(app)

// Empty by default so the web deployment (served from the same Vercel
// domain as /api) uses relative paths. The mobile app is not served from
// that domain, so it must set VITE_ONLINE_ORDER_API_BASE to the full
// https://your-app.vercel.app URL.
const API_BASE = (import.meta.env.VITE_ONLINE_ORDER_API_BASE ?? '').replace(/\/$/, '')

async function postJson<TResult>(path: string, body: unknown): Promise<TResult> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(data?.error || 'Something went wrong. Please try again.')
  }
  return data as TResult
}

// Local-only escape hatch to point the storefront at `firebase emulators:start`
// instead of the real project — never active in a production build.
if (import.meta.env.DEV && import.meta.env.VITE_USE_FIREBASE_EMULATOR === 'true') {
  connectFirestoreEmulator(db, '127.0.0.1', 8080)
}

// Mutable (not const): the web storefront sets these once from build-time
// env vars and never touches them again, but the mobile storefront resolves
// them at runtime instead (customer pairs to a store via a short code — see
// setStorefrontContext below and apps/mobile/src/storefront/pairing.ts). ESM
// live-bindings mean every existing importer of these four just sees the
// current value — nothing downstream (catalog.ts, copy.ts, HeroBanner.vue,
// etc.) needs to change or handle an "unresolved yet" state, as long as
// nothing reads them before pairing has happened on mobile.
export let ORG_SLUG: string = import.meta.env.VITE_POS_ORGANIZATION_SLUG
export let STORE_CODE: string = import.meta.env.VITE_POS_STORE_CODE
export let STORE_ADDRESS: string = import.meta.env.VITE_POS_STORE_ADDRESS ?? ''
// The physical store's business mode is fixed per storefront deployment —
// unlike the staff app, the storefront has no authenticated way to read the
// store's settings doc (Firestore rules keep that staff-only), so it's
// configured directly via env instead.
export let BUSINESS_MODE = import.meta.env.VITE_POS_BUSINESS_MODE as BusinessMode
// Store pin used to quote a distance-based delivery fee (merged in from
// Baguio Delivery). Null when the store never set one — the checkout then
// falls back to the flat base fee.
export let STORE_LAT: number | null = Number(import.meta.env.VITE_POS_STORE_LAT) || null
export let STORE_LNG: number | null = Number(import.meta.env.VITE_POS_STORE_LNG) || null

export function setStorefrontContext(ctx: {
  orgSlug: string
  storeCode: string
  storeAddress: string
  businessMode: BusinessMode
  storeLat?: number | null
  storeLng?: number | null
}) {
  ORG_SLUG = ctx.orgSlug
  STORE_CODE = ctx.storeCode
  STORE_ADDRESS = ctx.storeAddress
  BUSINESS_MODE = ctx.businessMode
  STORE_LAT = ctx.storeLat ?? null
  STORE_LNG = ctx.storeLng ?? null
}

export interface CreateOnlineOrderItem {
  productId: string
  quantity: number
}

export interface CreateOnlineOrderGuest {
  name: string
  phone?: string
  email?: string
}

export interface CreateOnlineOrderFulfillment {
  method: 'pickup' | 'delivery'
  address?: string
  /**
   * Optional drop-off coordinates, sent when the customer shares their
   * location. The API recomputes the fee from these rather than trusting the
   * client's quote — see api/create-online-order.ts.
   */
  lat?: number
  lng?: number
}

export interface CreateOnlineOrderResult {
  orderId: string
  ticketNumber: string
  totalCents: number
  /** Server-computed; 0 for pickup orders. */
  deliveryFeeCents?: number
}

export async function createOnlineOrder(
  items: CreateOnlineOrderItem[],
  guest: CreateOnlineOrderGuest,
  fulfillment: CreateOnlineOrderFulfillment,
  paymentMethod?: 'cash' | 'ewallet',
): Promise<CreateOnlineOrderResult> {
  return postJson<CreateOnlineOrderResult>('/api/create-online-order', {
    orgSlug: ORG_SLUG,
    storeCode: STORE_CODE,
    businessMode: BUSINESS_MODE,
    items,
    guest,
    fulfillment,
    paymentMethod,
  })
}

export interface ResolveStoreCodeResult {
  orgSlug: string
  storeCode: string
  businessMode: BusinessMode
  storeName: string
  storeAddress: string
  storeLat?: number | null
  storeLng?: number | null
}

// Only used by the mobile storefront (see apps/mobile/src/storefront/pairing.ts)
// — the web deployment stays on its build-time env vars and never calls this.
export async function resolveStoreCode(code: string): Promise<ResolveStoreCodeResult> {
  return postJson<ResolveStoreCodeResult>('/api/resolve-store-code', { code })
}
