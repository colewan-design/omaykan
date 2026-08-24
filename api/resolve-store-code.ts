import type { VercelRequest, VercelResponse } from '@vercel/node'
import { ApiError, getDb, setCorsHeaders } from './_lib/admin'

// Ported from functions/src/index.ts's resolveStoreCode — see
// create-online-order.ts for why this moved off Firebase Cloud Functions.

type OnlineBusinessMode = 'coffee-shop' | 'grocery' | 'restaurant'
const SUPPORTED_MODES: readonly OnlineBusinessMode[] = ['coffee-shop', 'grocery', 'restaurant']

interface FsStore {
  name: string
  address?: string
  businessMode?: string
  pairingCode?: string
  /** Set by the staff app's store settings; absent for stores that never
      configured a pin, in which case the storefront falls back to a flat
      delivery fee instead of a distance-based quote. */
  lat?: number
  lng?: number
}

async function resolveStoreCode(rawCode: unknown) {
  const code = typeof rawCode === 'string' ? rawCode.trim() : ''
  if (!code) {
    throw new ApiError(400, 'A store code is required.')
  }

  const snap = await getDb().collectionGroup('stores').where('pairingCode', '==', code).limit(1).get()
  if (snap.empty) {
    throw new ApiError(404, "We couldn't find a store with that code.")
  }

  const storeDoc = snap.docs[0]
  const store = storeDoc.data() as FsStore
  const orgRef = storeDoc.ref.parent.parent
  if (!orgRef) {
    throw new ApiError(500, 'Store is missing its parent organization.')
  }
  if (!store.businessMode || !SUPPORTED_MODES.includes(store.businessMode as OnlineBusinessMode)) {
    throw new ApiError(409, 'This store is not set up for online ordering.')
  }

  return {
    orgSlug: orgRef.id,
    storeCode: storeDoc.id,
    businessMode: store.businessMode as OnlineBusinessMode,
    storeName: store.name,
    storeAddress: store.address ?? '',
    storeLat: typeof store.lat === 'number' ? store.lat : null,
    storeLng: typeof store.lng === 'number' ? store.lng : null,
  }
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  setCorsHeaders(res)
  if (req.method === 'OPTIONS') {
    res.status(204).end()
    return
  }
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed.' })
    return
  }

  try {
    const body = (req.body ?? {}) as { code?: unknown }
    const result = await resolveStoreCode(body.code)
    res.status(200).json(result)
  } catch (err) {
    if (err instanceof ApiError) {
      res.status(err.status).json({ error: err.message })
      return
    }
    console.error('resolve-store-code failed:', err)
    res.status(500).json({ error: 'Something went wrong looking up that store.' })
  }
}
