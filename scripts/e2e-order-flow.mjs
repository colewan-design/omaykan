/*
 * End-to-end sample: seller lists a product → customer orders → rider delivers
 * → order completes. Ten runs against the HTTP API the apps actually use.
 * Nothing is stubbed.
 *
 * LOCAL ONLY. It creates orders, riders and products; pointing it at production
 * would put fake rows in a live merchant's books. API is hardcoded to
 * 127.0.0.1 for that reason — do not parameterise it.
 *
 * Prerequisites:
 *   cd backend
 *   php artisan migrate:fresh --seed --force
 *   php artisan platform-admin:create ops@test.local --password="OpsTester123!"
 *   php artisan serve        # and, for broadcasts, php artisan queue:work
 *
 * Run:  node scripts/e2e-order-flow.mjs
 *
 * Findings from the first run are in documentation/e2e-findings.md.
 */
import { randomUUID } from 'node:crypto'
import { execSync } from 'node:child_process'

/*
 * The API is rate limited on purpose (online-orders 20/min, rider register
 * 4/min, platform-admin login 5/min). A ten-run sample exceeds several of those,
 * so the limiter is reset between phases. This is a harness concession to a
 * real protection, not a defect — see the notes doc.
 */
function resetThrottle() {
  try { execSync('php artisan cache:clear', { cwd: 'backend', stdio: 'ignore' }) } catch {}
}

const API = 'http://127.0.0.1:8000'
const ORG = 'demo-coffee'
const STORE = 'main'
const MODE = 'coffee-shop'
const STORE_PIN = { lat: 16.4095, lng: 120.5995 }

const log = []
let deviceToken = null
let opsToken = null

async function call(path, { method = 'GET', body, token, form } = {}) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    body: form ?? (body ? JSON.stringify(body) : undefined),
  })
  const text = await res.text()
  let data = null
  try { data = JSON.parse(text) } catch { data = text }
  if (data && typeof data === 'object') { delete data.trace; delete data.file; delete data.line }
  return { status: res.status, ok: res.ok, data }
}

// A 1x1 PNG — rider docs are validated as real images, not just extensions.
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

async function setup() {
  const dev = await call('/api/device-sessions', {
    method: 'POST',
    body: {
      organizationSlug: ORG, storeCode: STORE, pairingCode: '123456',
      deviceName: 'E2E Till', platform: 'test', appVersion: '0.1.0',
    },
  })
  deviceToken = dev.data?.token
  if (!deviceToken) throw new Error('device session failed: ' + JSON.stringify(dev.data))

  const ops = await call('/api/platform-admin/login', {
    method: 'POST',
    body: { email: 'ops@test.local', password: 'OpsTester123!' },
  })
  opsToken = ops.data?.token
  if (!opsToken) throw new Error('operator login failed: ' + JSON.stringify(ops.data))

  return { deviceOk: !!deviceToken, opsOk: !!opsToken, orgId: dev.data.organization.id, storeId: dev.data.store.id }
}

async function registerAndApproveRider(n) {
  const form = new FormData()
  form.set('name', `Test Rider ${n}`)
  form.set('email', `rider${n}.${Date.now()}@test.local`)
  form.set('phone', `0917000${String(n).padStart(4, '0')}`)
  form.set('password', 'RiderPass123!')
  form.set('password_confirmation', 'RiderPass123!')
  form.set('licenseNumber', `LIC-${1000 + n}`)
  form.set('plateNumber', `PLT-${100 + n}`)
  form.set('licenseImage', new Blob([PNG], { type: 'image/png' }), 'license.png')
  form.set('plateImage', new Blob([PNG], { type: 'image/png' }), 'plate.png')

  const reg = await call('/api/rider/register', { method: 'POST', form })
  if (!reg.ok) return { ok: false, step: 'register', detail: reg.data }

  const queue = await call('/api/rider-review', { method: 'POST', token: opsToken })
  const pending = (queue.data?.riders ?? []).find((r) => r.email === form.get('email'))
  if (!pending) return { ok: false, step: 'review-queue', detail: queue.data }

  const decision = await call(`/api/rider-review/${pending.id}/decision`, {
    method: 'POST', token: opsToken, body: { status: 'approved', note: 'e2e' },
  })
  if (!decision.ok) return { ok: false, step: 'approve', detail: decision.data }

  const login = await call('/api/rider/login', {
    method: 'POST', body: { email: form.get('email'), password: 'RiderPass123!' },
  })
  if (!login.data?.token) return { ok: false, step: 'rider-login', detail: login.data }

  return { ok: true, id: pending.id, token: login.data.token, email: form.get('email') }
}

/** Seller lists a product from the till, the way the POS actually does it. */
async function sellerListsProduct(n) {
  const productId = randomUUID()
  const push = await call('/api/sync/push', {
    method: 'POST', token: deviceToken,
    body: {
      organizationId: SETUP.orgId, storeId: SETUP.storeId,
      events: [{
        id: randomUUID(), entityType: 'product', entityId: productId,
        operation: 'upsert', occurredAt: new Date().toISOString(),
        payload: {
          sku: `E2E-${n}`, name: `E2E Product ${n}`, productType: 'standard',
          taxRate: 12, priceCents: 15000 + n * 500, trackInventory: true,
          isActive: true, stockQty: 50,
        },
      }],
    },
  })
  if (!push.ok) return { ok: false, productId, detail: push.data }
  return { ok: true, productId }
}

/** The seeded product, which has a business mode — the only way one gets set. */
async function seededProduct() {
  const cat = await call(`/api/storefront/catalog?orgSlug=${ORG}&storeCode=${STORE}`)
  const p = (cat.data?.products ?? [])[0]
  if (!p) throw new Error('no seeded storefront product: ' + JSON.stringify(cat.data))
  return p.id
}

async function storefrontShows(productId) {
  const cat = await call(`/api/storefront/catalog?orgSlug=${ORG}&storeCode=${STORE}`)
  return (cat.data?.products ?? []).some((p) => p.id === productId)
}

async function run(n, cfg) {
  const r = { n, name: cfg.name, steps: {} }

  let productId
  if (cfg.useSeeded) {
    productId = SEEDED
    r.steps.sellerListed = true
  } else {
    const listed = await sellerListsProduct(n)
    r.steps.sellerListed = listed.ok
    if (!listed.ok) { r.failedAt = 'seller-list'; r.detail = listed.detail; return r }
    productId = listed.productId
  }
  r.productId = productId
  r.steps.onStorefront = await storefrontShows(productId)

  let order
  for (let attempt = 0; attempt < 3; attempt++) {
    order = await call('/api/online-orders', {
    method: 'POST',
    body: {
      orgSlug: ORG, storeCode: STORE, businessMode: MODE,
      items: [{ productId, quantity: cfg.qty }],
      guest: { name: `E2E Buyer ${n}`, phone: `0918000${String(n).padStart(4, '0')}` },
      fulfillment: cfg.delivery
        ? { method: 'delivery', address: `${n} Test St, Baguio`, ...(cfg.drop ?? {}) }
        : { method: 'pickup' },
      paymentMethod: 'cash',
      },
    })
    // SQLite allows one writer; the queue worker holds it briefly after each
    // broadcast. Postgres in production does not have this constraint.
    if (order.ok || !/database is locked/i.test(JSON.stringify(order.data))) break
    r.sqliteLockRetries = (r.sqliteLockRetries ?? 0) + 1
    await new Promise((res) => setTimeout(res, 400))
  }
  r.steps.customerOrdered = order.ok
  if (cfg.expectRefusal) {
    // The 15km service-area guard is supposed to refuse this one.
    const msg = order.data?.errors?.fulfillment?.[0] ?? order.data?.message ?? ''
    r.passed = !order.ok && /delivery area/i.test(msg)
    r.note = r.passed ? `correctly refused: ${msg}` : `expected a refusal, got ${order.status}`
    return r
  }
  if (!order.ok) {
    r.failedAt = 'customer-order'
    r.detail = order.data?.errors?.items?.[0] ?? order.data?.message
    return r
  }
  r.orderId = order.data.orderId
  r.ticket = order.data.ticketNumber
  r.totalCents = order.data.totalCents
  r.deliveryFeeCents = order.data.deliveryFeeCents

  if (cfg.delivery) {
    const board = await call('/api/rider/board', { token: cfg.rider.token })
    const onBoard = (board.data?.orders ?? []).some((o) => o.id === r.orderId)
    r.steps.onRiderBoard = onBoard
    if (!onBoard) { r.failedAt = 'rider-board'; r.detail = board.data; return r }

    const accept = await call(`/api/rider/deliveries/${r.orderId}/accept`, { method: 'POST', token: cfg.rider.token })
    r.steps.riderAccepted = accept.ok
    if (!accept.ok) { r.failedAt = 'rider-accept'; r.detail = accept.data?.message; return r }

    const pick = await call(`/api/rider/deliveries/${r.orderId}/stage`, {
      method: 'POST', token: cfg.rider.token, body: { stage: 'picked_up' },
    })
    r.steps.pickedUp = pick.ok
    if (!pick.ok) { r.failedAt = 'picked-up'; r.detail = pick.data?.message; return r }

    const drop = await call(`/api/rider/deliveries/${r.orderId}/stage`, {
      method: 'POST', token: cfg.rider.token, body: { stage: 'delivered' },
    })
    r.steps.delivered = drop.ok
    if (!drop.ok) { r.failedAt = 'delivered'; r.detail = drop.data?.message; return r }
  }

  const settle = await call(`/api/seller/online-orders/${r.orderId}/settle-payment`, {
    method: 'POST', token: deviceToken,
    body: { paymentMethod: 'cash', tenderedCents: r.totalCents, changeCents: 0 },
  })
  r.steps.paymentSettled = settle.ok
  if (!settle.ok) { r.failedAt = 'settle-payment'; r.detail = settle.data?.message; return r }

  const served = await call(`/api/seller/online-orders/${r.orderId}/status`, {
    method: 'POST', token: deviceToken, body: { status: 'served' },
  })
  r.steps.completed = served.ok
  if (!served.ok) { r.failedAt = 'complete'; r.detail = served.data?.message; return r }

  r.passed = true
  return r
}

const SETUP = await setup()
const SEEDED = await seededProduct()
console.log('setup:', JSON.stringify(SETUP))

resetThrottle()
const riderA = await registerAndApproveRider(1)
resetThrottle()
const riderB = await registerAndApproveRider(2)
console.log('riderA:', riderA.ok ? 'approved' : JSON.stringify(riderA))
console.log('riderB:', riderB.ok ? 'approved' : JSON.stringify(riderB))
if (!riderA.ok) process.exit(1)

// Near pin ≈ 0.5km from the store; far pin ≈ 10km; beyond ≈ 35km.
const NEAR = { lat: 16.4123, lng: 120.596 }
const FAR = { lat: 16.5, lng: 120.62 }
const BEYOND = { lat: 16.7, lng: 120.7 }

const scenarios = [
  { name: 'POS-listed product, delivery',            useSeeded: false, qty: 1, delivery: true,  drop: NEAR, rider: riderA },
  { name: 'POS-listed product, pickup',              useSeeded: false, qty: 2, delivery: false,             rider: riderA },
  { name: 'POS-listed product, delivery no pin',     useSeeded: false, qty: 1, delivery: true,              rider: riderA },
  { name: 'Seeded product, delivery near pin',      useSeeded: true,   qty: 1, delivery: true,  drop: NEAR, rider: riderA },
  { name: 'Seeded product, delivery far pin',       useSeeded: true,   qty: 2, delivery: true,  drop: FAR,  rider: riderA },
  { name: 'Seeded product, delivery no pin (flat)', useSeeded: true,   qty: 1, delivery: true,              rider: riderB },
  { name: 'Seeded product, pickup',                 useSeeded: true,   qty: 3, delivery: false,             rider: riderA },
  { name: 'Seeded product, delivery qty 5',         useSeeded: true,   qty: 5, delivery: true,  drop: NEAR, rider: riderB },
  { name: 'Seeded product, second rider',           useSeeded: true,   qty: 1, delivery: true,  drop: NEAR, rider: riderB },
  { name: 'Seeded product, beyond service area',    useSeeded: true,   qty: 1, delivery: true,  drop: BEYOND, rider: riderA, expectRefusal: true },
]

for (let i = 0; i < scenarios.length; i++) {
  resetThrottle()
  const r = await run(i + 1, scenarios[i])
  log.push(r)
  const mark = r.passed ? (r.note ? 'PASS*' : 'PASS') : 'FAIL'
  console.log(
    `${String(i + 1).padStart(2)}. ${mark}  ${r.name}` +
    (r.passed
      ? `  ticket=${r.ticket} total=₱${(r.totalCents / 100).toFixed(2)} fee=₱${((r.deliveryFeeCents ?? 0) / 100).toFixed(2)}`
      : `  stopped at ${r.failedAt}: ${typeof r.detail === 'string' ? r.detail : JSON.stringify(r.detail)}`) +
    (r.note ? `  ${r.note}` : ''),
  )
  if (!r.passed && r.steps.sellerListed) {
    console.log(`      storefront listed it: ${r.steps.onStorefront}`)
  }
}

console.log('\n--- summary ---')
console.log(`passed ${log.filter((r) => r.passed).length}/${log.length}`)
console.log(`seller product visible on storefront: ${log.filter((r) => r.steps.onStorefront).length}/${log.length}`)
