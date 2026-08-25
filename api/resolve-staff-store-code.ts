import type { ApiRequest, ApiResponse } from './http'
import { ApiError, getDb, setCorsHeaders } from './_lib/admin'

// Looks up a store by pairing code with no businessMode gating — used only
// to bind a staff browser to an existing org ("I already have a store" on
// the signup page), not for customer online-ordering eligibility. Kept
// separate from api/resolve-store-code.ts (the customer-storefront lookup,
// which rejects nail-salon stores) rather than relaxing a check that endpoint
// still needs for its own purpose.

async function resolveStaffStoreCode(rawCode: unknown) {
  const code = typeof rawCode === 'string' ? rawCode.trim().toUpperCase() : ''
  if (!code) {
    throw new ApiError(400, 'A store code is required.')
  }

  const snap = await getDb().collectionGroup('stores').where('pairingCode', '==', code).limit(1).get()
  if (snap.empty) {
    throw new ApiError(404, "We couldn't find a store with that code.")
  }

  const storeDoc = snap.docs[0]
  const orgRef = storeDoc.ref.parent.parent
  if (!orgRef) {
    throw new ApiError(500, 'Store is missing its parent organization.')
  }

  return { organizationSlug: orgRef.id, storeCode: storeDoc.id }
}

export default async function handler(req: ApiRequest, res: ApiResponse) {
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
    const result = await resolveStaffStoreCode(body.code)
    res.status(200).json(result)
  } catch (err) {
    if (err instanceof ApiError) {
      res.status(err.status).json({ error: err.message })
      return
    }
    console.error('resolve-staff-store-code failed:', err)
    res.status(500).json({ error: 'Something went wrong looking up that store.' })
  }
}
