import { cert, getApps, initializeApp, type App } from 'firebase-admin/app'
import { getAuth, type Auth } from 'firebase-admin/auth'
import { getFirestore, type Firestore } from 'firebase-admin/firestore'

// The VPS has no ambient Google Cloud credentials the way Firebase Cloud
// Functions does, so the Admin SDK is initialized from an explicit service
// account key supplied through server-side env vars. Generate the key from
// Firebase Console > Project Settings > Service Accounts (a free,
// Spark-plan-compatible action) and never commit it.
//
// Lazily initialized (not a top-level `export const db = ...`) so a missing
// env var throws inside a request's try/catch — caught and turned into a
// clean 500 JSON response — instead of throwing at module load time, which
// crashes the whole function process before it can respond at all.
let cachedApp: App | null = null
let cachedDb: Firestore | null = null
let cachedAuth: Auth | null = null

function getAdminApp(): App {
  if (cachedApp) return cachedApp

  const existing = getApps()
  if (existing.length) {
    cachedApp = existing[0]
    return cachedApp
  }

  const projectId = process.env.FIREBASE_PROJECT_ID
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL
  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n')

  if (!projectId || !clientEmail || !privateKey) {
    throw new Error(
      'Missing Firebase Admin credentials — set FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL, and FIREBASE_PRIVATE_KEY in the server environment.',
    )
  }

  cachedApp = initializeApp({ credential: cert({ projectId, clientEmail, privateKey }) })
  return cachedApp
}

export function getDb(): Firestore {
  if (!cachedDb) cachedDb = getFirestore(getAdminApp())
  return cachedDb
}

export function getAdminAuth(): Auth {
  if (!cachedAuth) cachedAuth = getAuth(getAdminApp())
  return cachedAuth
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function setCorsHeaders(res: { setHeader: (name: string, value: string) => void }) {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization')
}
