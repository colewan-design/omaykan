/*
 * Second e2e pass: boundaries, authorization and races rather than the happy
 * path. Where the first suite asked "does the chain work", this asks "what
 * happens when someone pushes on it".
 *
 * LOCAL ONLY — creates and mutates orders, stock and riders.
 *
 *   cd backend && php artisan migrate:fresh --seed --force
 *   php artisan platform-admin:create ops@test.local --password="OpsTester123!"
 *   php artisan serve
 *   node scripts/e2e-edge-cases.mjs
 *
 * Each case states the invariant it is checking. A case "passes" when the
 * system upholds the invariant, which for most of these means *refusing*
 * something.
 */
import { randomUUID } from 'node:crypto'
import { execSync } from 'node:child_process'

const API = 'http://127.0.0.1:8000'
const ORG = 'demo-coffee'
const STORE = 'main'
const MODE = 'coffee-shop'

let deviceToken = null
let opsToken = null
let SETUP = null
let PRODUCT = null

function resetThrottle() {
  try { execSync('php artisan cache:clear', { cwd: 'backend', stdio: 'ignore' }) } catch {}
}

async function call(path, { method = 'GET', body, token, form } = {}) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API}${path}`, {
    method, headers, body: form ?? (body ? JSON.stringify(body) : undefined),
  })
  const text = await res.text()
  let data = null
  try { data = JSON.parse(text) } catch { data = text }
  if (data && typeof data === 'object') { delete data.trace; delete data.file; delete data.line }
  return { status: res.status, ok: res.ok, data }
}

const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

/**
 * State is read back through the API, never by shelling into the database.
 * The first run's write-up called reaching past the API a lapse; doing it again
 * here broke the suite on Windows quoting, so it is gone.
 */
async function stockOf(productId) {
  const cat = await call(`/api/storefront/catalog?orgSlug=${ORG}&storeCode=${STORE}`)
  const row = (cat.data?.products ?? []).find((p) => p.id === productId)
  return row ? Number(row.stockQty) : 0
}

async function productsInSync(sku) {
  // Nested under `changes`, not top level. Reading `data.products` here gave a
  // false "0 rows" and nearly booked a correct system as a bug — the second
  // time this suite guessed a field name instead of checking it.
  const pull = await call('/api/sync/pull', { token: deviceToken })
  return (pull.data?.changes?.products ?? []).filter((p) => p.sku === sku).length
}

async function setup() {
  const dev = await call('/api/device-sessions', {
    method: 'POST',
    body: {
      organizationSlug: ORG, storeCode: STORE, pairingCode: '123456',
      deviceName: 'Edge Till', platform: 'test', appVersion: '0.1.0',
    },
  })
  deviceToken = dev.data?.token
  const ops = await call('/api/platform-admin/login', {
    method: 'POST', body: { email: 'ops@test.local', password: 'OpsTester123!' },
  })
  opsToken = ops.data?.token
  const cat = await call(`/api/storefront/catalog?orgSlug=${ORG}&storeCode=${STORE}`)
  PRODUCT = cat.data.products[0]
  SETUP = { orgId: dev.data.organization.id, storeId: dev.data.store.id }
  return { deviceToken: !!deviceToken, opsToken: !!opsToken, product: PRODUCT.name, price: PRODUCT.priceCents }
}

async function makeRider(n, { approve = true } = {}) {
  const email = `edge${n}.${Date.now()}@test.local`
  const form = new FormData()
  form.set('name', `Edge Rider ${n}`)
  form.set('email', email)
  form.set('phone', `0917900${String(n).padStart(4, '0')}`)
  form.set('password', 'RiderPass123!')
  form.set('password_confirmation', 'RiderPass123!')
  form.set('licenseNumber', `EDGE-${n}`)
  form.set('plateNumber', `EDG-${n}`)
  form.set('licenseImage', new Blob([PNG], { type: 'image/png' }), 'l.png')
  form.set('plateImage', new Blob([PNG], { type: 'image/png' }), 'p.png')

  const reg = await call('/api/rider/register', { method: 'POST', form })
  if (!reg.ok) return { ok: false, detail: reg.data }

  const queue = await call('/api/rider-review', { method: 'POST', token: opsToken })
  const row = (queue.data?.riders ?? []).find((r) => r.email === email)
  if (approve) {
    await call(`/api/rider-review/${row.id}/decision`, {
      method: 'POST', token: opsToken, body: { status: 'approved' },
    })
  }
  const login = await call('/api/rider/login', {
    method: 'POST', body: { email, password: 'RiderPass123!' },
  })
  return { ok: true, id: row.id, email, token: login.data?.token ?? null, loginStatus: login.status }
}

async function setStock(qty) {
  const current = await stockOf(PRODUCT.id)
  const delta = qty - current
  if (delta === 0) return qty
  await call('/api/sync/push', {
    method: 'POST', token: deviceToken,
    body: {
      organizationId: SETUP.orgId, storeId: SETUP.storeId,
      events: [{
        id: randomUUID(), entityType: 'inventory_adjustment', entityId: randomUUID(),
        operation: 'create', occurredAt: new Date().toISOString(),
        payload: { productId: PRODUCT.id, quantityDelta: delta, reason: 'edge-test' },
      }],
    },
  })
  return await stockOf(PRODUCT.id)
}

function order(qty, extra = {}) {
  return call('/api/online-orders', {
    method: 'POST',
    body: {
      orgSlug: ORG, storeCode: STORE, businessMode: MODE,
      items: [{ productId: PRODUCT.id, quantity: qty }],
      guest: { name: 'Edge Buyer', phone: '09189990000' },
      fulfillment: { method: 'delivery', address: 'Edge St', lat: 16.4123, lng: 120.596 },
      paymentMethod: 'cash',
      ...extra,
    },
  })
}

const results = []
function record(n, name, invariant, pass, detail) {
  results.push({ n, name, invariant, pass, detail })
  console.log(`${String(n).padStart(2)}. ${pass ? 'PASS' : 'FAIL'}  ${name}`)
  console.log(`      expects: ${invariant}`)
  console.log(`      actual:  ${detail}`)
}

// ── cases ────────────────────────────────────────────────────────────────
async function c1() {
  const stock = await setStock(3)
  const res = await order(10)
  const msg = res.data?.errors?.items?.[0] ?? res.data?.message ?? ''
  record(1, 'Oversell', 'refuse an order larger than stock',
    !res.ok && /enough stock/i.test(msg), `stock=${stock} qty=10 → ${res.status} "${msg}"`)
}

async function c2() {
  const before = await setStock(20)
  const res = await order(3)
  const after = await stockOf(PRODUCT.id)
  record(2, 'Stock decrement', 'stock falls by exactly the quantity ordered',
    res.ok && after === before - 3, `${before} → ${after} after qty 3 (expected ${before - 3})`)
}

async function c3() {
  await setStock(1)
  const [a, b] = await Promise.all([order(1), order(1)])
  const ok = [a, b].filter((r) => r.ok).length
  const after = await stockOf(PRODUCT.id)
  record(3, 'Concurrent last unit', 'exactly one of two simultaneous orders wins, stock never negative',
    ok === 1 && after >= 0, `${ok}/2 succeeded, stock now ${after}`)
}

async function c4() {
  await setStock(20)
  const honest = await order(1)
  const tampered = await order(1, {
    items: [{ productId: PRODUCT.id, quantity: 1, priceCents: 1, totalCents: 1 }],
    totalCents: 1, subtotalCents: 1, taxCents: 0, deliveryFeeCents: 0,
  })
  record(4, 'Client price tampering', 'server recomputes money and ignores client-sent totals',
    tampered.ok && tampered.data.totalCents === honest.data.totalCents,
    `honest=${honest.data?.totalCents} tampered-claim=1 → charged ${tampered.data?.totalCents}`)
}

async function c5() {
  const res = await call('/api/sync/push', {
    method: 'POST', token: deviceToken,
    body: {
      organizationId: randomUUID(), storeId: randomUUID(),
      events: [{
        id: randomUUID(), entityType: 'product', entityId: randomUUID(),
        operation: 'upsert', occurredAt: new Date().toISOString(),
        payload: { name: 'Cross-tenant smuggle', priceCents: 1 },
      }],
    },
  })
  record(5, 'Cross-tenant sync push', 'a device cannot write into another org/store (403)',
    res.status === 403, `${res.status} "${res.data?.message ?? ''}"`)
}

async function c6(riderA, riderB) {
  await setStock(20)
  const o = await order(1)
  await call(`/api/rider/deliveries/${o.data.orderId}/accept`, { method: 'POST', token: riderA.token })
  const steal = await call(`/api/rider/deliveries/${o.data.orderId}/stage`, {
    method: 'POST', token: riderB.token, body: { stage: 'picked_up' },
  })
  record(6, "Another rider's delivery", "a rider cannot advance a delivery they do not hold (403)",
    steal.status === 403, `${steal.status} "${steal.data?.message ?? ''}"`)
  return o.data.orderId
}

async function c7(pending) {
  const board = await call('/api/rider/board', { token: pending.token })
  const detail = pending.token
    ? `board → ${board.status}`
    : `login refused outright (${pending.loginStatus})`
  record(7, 'Unapproved rider', 'a rider awaiting approval cannot reach the delivery board',
    !pending.token || board.status === 403, detail)
}

async function c8(riderA) {
  await setStock(20)
  const o = await order(1)
  const id = o.data.orderId
  const first = await call(`/api/seller/online-orders/${id}/settle-payment`, {
    method: 'POST', token: deviceToken, body: { paymentMethod: 'cash', tenderedCents: o.data.totalCents },
  })
  const second = await call(`/api/seller/online-orders/${id}/settle-payment`, {
    method: 'POST', token: deviceToken, body: { paymentMethod: 'cash', tenderedCents: o.data.totalCents },
  })
  record(8, 'Double payment settlement', 'an already-paid order must refuse a second settlement',
    !second.ok,
    `first=${first.status} second=${second.status}` +
    (second.ok
      ? ` — accepted again; settlePayment creates a Payment row unconditionally, so a ₱${(o.data.totalCents / 100).toFixed(2)} order now carries two`
      : ''))
}

async function c9(riderA) {
  await setStock(20)
  const o = await order(1)
  const id = o.data.orderId
  await call(`/api/rider/deliveries/${id}/accept`, { method: 'POST', token: riderA.token })
  const skip = await call(`/api/rider/deliveries/${id}/stage`, {
    method: 'POST', token: riderA.token, body: { stage: 'delivered' },
  })
  record(9, 'Skipping a delivery stage', 'cannot mark delivered without picking up first (422)',
    skip.status === 422, `${skip.status} "${skip.data?.message ?? ''}"`)
}

async function c10() {
  const eventId = randomUUID()
  const entityId = randomUUID()
  const body = {
    organizationId: SETUP.orgId, storeId: SETUP.storeId,
    events: [{
      id: eventId, entityType: 'product', entityId,
      operation: 'upsert', occurredAt: new Date().toISOString(),
      payload: { sku: 'IDEM-1', name: 'Idempotency probe', priceCents: 12345, trackInventory: false, isActive: true },
    }],
  }
  const first = await call('/api/sync/push', { method: 'POST', token: deviceToken, body })
  const second = await call('/api/sync/push', { method: 'POST', token: deviceToken, body })
  const count = await productsInSync('IDEM-1')
  const status2 = second.data?.results?.[0]?.status
  record(10, 'Replayed sync event', 'a repeated event id is reported duplicate and applied once',
    count === 1 && status2 === 'duplicate',
    `first=${first.data?.results?.[0]?.status} second=${status2}, ${count} product row(s)`)
}

// ── run ──────────────────────────────────────────────────────────────────
resetThrottle()
console.log('setup:', JSON.stringify(await setup()))
resetThrottle()
const riderA = await makeRider(1)
resetThrottle()
const riderB = await makeRider(2)
resetThrottle()
const pending = await makeRider(3, { approve: false })
console.log(`riders: A=${riderA.ok} B=${riderB.ok} pending=${pending.ok}\n`)

resetThrottle(); await c1()
resetThrottle(); await c2()
resetThrottle(); await c3()
resetThrottle(); await c4()
resetThrottle(); await c5()
resetThrottle(); await c6(riderA, riderB)
resetThrottle(); await c7(pending)
resetThrottle(); await c8(riderA)
resetThrottle(); await c9(riderA)
resetThrottle(); await c10()

const failed = results.filter((r) => !r.pass)
console.log(`\n--- ${results.length - failed.length}/${results.length} invariants upheld ---`)
if (failed.length) {
  console.log('BROKEN:')
  for (const f of failed) console.log(`  ${f.n}. ${f.name} — expected ${f.invariant}; got ${f.detail}`)
}
