import type { ApiRequest, ApiResponse } from './http'
import { FieldValue } from 'firebase-admin/firestore'
import { ApiError, getAdminAuth, getDb, setCorsHeaders } from './_lib/admin'

// Lets an already-signed-in admin create a staff account directly, instead of
// the employee self-registering. Runs entirely through the Admin SDK so it
// never touches the calling admin's own client-side session — the client
// Auth SDK's createUserWithEmailAndPassword would otherwise sign the browser
// in as the *new* user, kicking the admin out of their own account.

interface StaffCreateRequest {
  fullName: string
  username: string
  password: string
  roleId: string
}

interface FsUser {
  organizationId: string
  fullName: string
  username: string
  status: string
  roleKey: string
}

function syntheticEmail(username: string, orgSlug: string): string {
  return `${username.trim().toLowerCase()}@${orgSlug}.pos`
}

async function requireCallingAdmin(req: ApiRequest): Promise<{ organizationId: string }> {
  const authHeader = req.headers.authorization
  const token = typeof authHeader === 'string' ? authHeader.replace(/^Bearer\s+/i, '') : ''
  if (!token) {
    throw new ApiError(401, 'Missing Authorization header.')
  }

  let uid: string
  try {
    uid = (await getAdminAuth().verifyIdToken(token)).uid
  } catch {
    throw new ApiError(401, 'Your session has expired — please sign in again.')
  }

  const callerSnap = await getDb().doc(`users/${uid}`).get()
  if (!callerSnap.exists) {
    throw new ApiError(403, 'Only an admin can add employees.')
  }

  const caller = callerSnap.data() as FsUser
  if (caller.roleKey !== 'admin') {
    throw new ApiError(403, 'Only an admin can add employees.')
  }

  return { organizationId: caller.organizationId }
}

async function createStaffAccount(organizationId: string, body: StaffCreateRequest) {
  const fullName = body.fullName?.trim() ?? ''
  const username = body.username?.trim().toLowerCase() ?? ''
  const password = body.password?.trim() ?? ''
  const roleId = body.roleId?.trim() ?? ''

  if (!fullName || !username || !password || !roleId) {
    throw new ApiError(400, 'Full name, username, password, and role are required.')
  }
  if (password.length < 6) {
    throw new ApiError(400, 'Password must be at least 6 characters.')
  }

  const db = getDb()
  const existing = await db
    .collection('users')
    .where('organizationId', '==', organizationId)
    .where('username', '==', username)
    .limit(1)
    .get()
  if (!existing.empty) {
    throw new ApiError(409, 'That username is already in use.')
  }

  const email = syntheticEmail(username, organizationId)

  let uid: string
  try {
    const created = await getAdminAuth().createUser({ email, password, displayName: fullName })
    uid = created.uid
  } catch (err) {
    if (err && typeof err === 'object' && 'code' in err && err.code === 'auth/email-already-exists') {
      throw new ApiError(409, 'That username is already in use.')
    }
    throw err
  }

  const createdAt = new Date()
  await db.doc(`users/${uid}`).set({
    organizationId,
    fullName,
    username,
    status: 'active',
    roleKey: roleId,
    createdAt: FieldValue.serverTimestamp(),
  })

  return {
    id: uid,
    fullName,
    username,
    roleId,
    createdAt: createdAt.toISOString(),
  }
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
    const { organizationId } = await requireCallingAdmin(req)
    const user = await createStaffAccount(organizationId, (req.body ?? {}) as StaffCreateRequest)
    res.status(200).json({ user })
  } catch (err) {
    if (err instanceof ApiError) {
      res.status(err.status).json({ error: err.message })
      return
    }
    console.error('staff-create failed:', err)
    res.status(500).json({ error: 'Something went wrong creating that account.' })
  }
}
