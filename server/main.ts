import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'

import createOnlineOrder from '../api/create-online-order'
import platformAdmin from '../api/platform-admin'
import resolveStaffStoreCode from '../api/resolve-staff-store-code'
import resolveStoreCode from '../api/resolve-store-code'
import signup from '../api/signup'
import staffCreate from '../api/staff-create'

// Self-hosted runner for the handlers in /api — the only thing serving them
// now that the project is off Vercel.
//
// The handlers only ever touch a handful of request/response members, spelled
// out as ApiRequest/ApiResponse in api/http.ts. This adapts a plain node:http
// request/response to that shape, so the handlers stay transport-agnostic
// files rather than being rewritten around node:http directly.

type Handler = (req: any, res: any) => unknown | Promise<unknown>

const ROUTES: Record<string, Handler> = {
  '/api/create-online-order': createOnlineOrder,
  '/api/platform-admin': platformAdmin,
  '/api/resolve-staff-store-code': resolveStaffStoreCode,
  '/api/resolve-store-code': resolveStoreCode,
  '/api/signup': signup,
  '/api/staff-create': staffCreate,
}

const PORT = Number(process.env.PORT) || 3005
const HOST = process.env.HOST || '127.0.0.1'
/** Refuse bodies larger than this rather than buffering without limit. */
const MAX_BODY_BYTES = 1_000_000

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks: Buffer[] = []
    req.on('data', (chunk: Buffer) => {
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        reject(new Error('payload-too-large'))
        req.destroy()
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    req.on('error', reject)
  })
}

/** Adds the `status()`/`json()` sugar ApiResponse promises. */
function decorate(res: ServerResponse) {
  const r = res as ServerResponse & {
    status: (code: number) => typeof r
    json: (body: unknown) => void
  }
  r.status = (code: number) => {
    r.statusCode = code
    return r
  }
  r.json = (body: unknown) => {
    if (!r.headersSent) r.setHeader('Content-Type', 'application/json; charset=utf-8')
    r.end(JSON.stringify(body))
  }
  return r
}

const server = createServer(async (req, res) => {
  const decorated = decorate(res)
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`)

  // Unauthenticated liveness probe for systemd/nginx checks — deliberately
  // does not touch Firestore, so it stays green even if credentials are wrong.
  if (url.pathname === '/api/health') {
    decorated.status(200).json({ ok: true, routes: Object.keys(ROUTES).length })
    return
  }

  const handler = ROUTES[url.pathname]
  if (!handler) {
    decorated.status(404).json({ error: 'Not found.' })
    return
  }

  try {
    let body: unknown = {}
    if (req.method !== 'GET' && req.method !== 'HEAD' && req.method !== 'OPTIONS') {
      const raw = await readBody(req)
      if (raw) {
        try {
          body = JSON.parse(raw)
        } catch {
          decorated.status(400).json({ error: 'Request body must be valid JSON.' })
          return
        }
      }
    }

    const apiReq = Object.assign(req, {
      body,
      query: Object.fromEntries(url.searchParams),
      cookies: {},
    })

    await handler(apiReq, decorated)
    if (!res.writableEnded) decorated.status(204).end()
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    if (message === 'payload-too-large') {
      if (!res.writableEnded) decorated.status(413).json({ error: 'Request body too large.' })
      return
    }
    // Never leak internals to the client; the detail goes to the journal.
    console.error(`[api] ${req.method} ${url.pathname} failed:`, err)
    if (!res.writableEnded) decorated.status(500).json({ error: 'Something went wrong.' })
  }
})

server.listen(PORT, HOST, () => {
  console.log(`[api] listening on http://${HOST}:${PORT} — ${Object.keys(ROUTES).length} routes`)
})

for (const signal of ['SIGTERM', 'SIGINT'] as const) {
  process.on(signal, () => {
    console.log(`[api] ${signal} received, shutting down`)
    server.close(() => process.exit(0))
  })
}
