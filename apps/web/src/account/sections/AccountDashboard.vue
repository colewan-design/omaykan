<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Heart, House, Package, ShoppingBag } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { fetchCustomerOrders, type TrackedOrder } from '@pos/web/commerce/api'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { useSavedProducts, type SavedProduct } from '@pos/web/commerce/favorites'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'

// The portal's front page: who you are, your latest orders, where things get
// sent, what you have saved, and the order list as a table.
//
// Laid out after the redesign's account screen, filled with what the API has.
// Its three figures — orders, saved items, addresses — are the Android app's
// (AccountHome.kt): all three are things the shopper made, where the reference
// had a "member since" date the account payload does not carry. No ratings on
// the saved items; nothing on this platform rates a product.

const emit = defineEmits<{ open: [id: string, extra?: Record<string, string>] }>()

const account = useCustomerAccount()
const saved = useSavedProducts()
const catalog = useStorefrontCatalog()

const profile = computed(() => account.account.value)
const firstName = computed(() => profile.value?.name.trim().split(/\s+/)[0] ?? '')
const initial = computed(() => profile.value?.name.trim().charAt(0).toUpperCase() || '?')

/** The place their orders go, as the one line of "where" the card has room for. */
const place = computed(() => {
  const address = account.defaultAddress.value ?? account.addresses.value[0]
  return address ? [address.barangay, address.city].filter((part) => part.trim()).join(', ') : ''
})

// ── Orders ────────────────────────────────────────────────────────────────

const orders = ref<TrackedOrder[]>([])
const ordersLoaded = ref(false)
const ordersFailed = ref(false)

onMounted(async () => {
  try {
    orders.value = [...(await fetchCustomerOrders()).orders].sort((a, b) =>
      (b.placedAt ?? '').localeCompare(a.placedAt ?? ''),
    )
  } catch {
    ordersFailed.value = true
  } finally {
    ordersLoaded.value = true
  }
})

const recentOrders = computed(() => orders.value.slice(0, 3))
const tableOrders = computed(() => orders.value.slice(0, 6))

type Tone = 'done' | 'moving' | 'ready' | 'prep'

/**
 * One word for where an order is. For a delivery the road is the answer once
 * a rider has it; before that, and for pickup, it is the shop's own progress.
 */
function orderBadge(order: TrackedOrder): { label: string; tone: Tone } {
  if (order.fulfillmentMethod === 'delivery') {
    if (order.deliveryStage === 'delivered') return { label: 'Delivered', tone: 'done' }
    if (order.deliveryStage === 'picked_up') return { label: 'On the way', tone: 'moving' }
    if (order.deliveryStage === 'assigned') return { label: 'Rider assigned', tone: 'moving' }
  }
  if (order.status === 'served') return { label: 'Completed', tone: 'done' }
  if (order.status === 'ready') {
    return { label: order.fulfillmentMethod === 'pickup' ? 'Ready for pickup' : 'Ready', tone: 'ready' }
  }
  return { label: 'Preparing', tone: 'prep' }
}

function itemCount(order: TrackedOrder): number {
  return order.items.reduce((sum, item) => sum + item.quantity, 0)
}

function headline(order: TrackedOrder): string {
  const [first, ...rest] = order.items
  if (!first) return 'Order'
  return rest.length > 0 ? `${first.name} + ${rest.length} more` : first.name
}

// Order items carry no photograph. When the first one is on the shelf this
// page has loaded, its picture stands in for the order; otherwise a bag does.
const imageById = computed(() => new Map(catalog.products.map((product) => [product.id, product.imageUrl])))

function orderImage(order: TrackedOrder): string | undefined {
  const first = order.items[0]
  return first ? imageById.value.get(first.productId) : undefined
}

const dateFormat = new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })

function placedOn(order: TrackedOrder): string {
  return order.placedAt ? dateFormat.format(new Date(order.placedAt)) : '—'
}

function viewOrder(order: TrackedOrder) {
  emit('open', 'orders', { order: order.orderId })
}

// ── Addresses and saved items ─────────────────────────────────────────────

const addressPreview = computed(() => account.addresses.value.slice(0, 2))
const savedPreview = computed(() => saved.items.value.slice(0, 2))

function savedHref(item: SavedProduct): string {
  return `/?${new URLSearchParams({ shop: item.shop.orgSlug, product: item.product.id }).toString()}`
}
</script>

<template>
  <div class="dash">
    <header class="dash-hello">
      <div>
        <h1 class="dash-hello__title">Welcome back, {{ firstName }}!</h1>
        <p class="dash-hello__sub">Thank you for being part of the Omaykan community.</p>
      </div>
      <p class="dash-hello__script" aria-hidden="true">"Local choices<br />create a brighter<br />tomorrow."</p>
    </header>

    <!-- ── Who ──────────────────────────────────────────────────────────── -->
    <section class="dash-card dash-profile" aria-label="Your profile">
      <div class="dash-profile__who">
        <img v-if="profile?.avatarUrl" class="dash-profile__avatar" :src="profile.avatarUrl" alt="" referrerpolicy="no-referrer" />
        <span v-else class="dash-profile__avatar dash-profile__avatar--letter" aria-hidden="true">{{ initial }}</span>

        <div class="dash-profile__text">
          <p class="dash-profile__name">{{ profile?.name }}</p>
          <p class="dash-profile__line">{{ profile?.email }}</p>
          <p v-if="place" class="dash-profile__line">{{ place }}</p>
          <button type="button" class="acct-btn acct-btn--outline" @click="emit('open', 'preferences')">
            Edit profile
          </button>
        </div>
      </div>

      <div class="dash-stats">
        <button type="button" class="dash-stat" @click="emit('open', 'orders')">
          <Package :size="24" :stroke-width="1.6" />
          <strong>{{ ordersLoaded && !ordersFailed ? orders.length : '—' }}</strong>
          <span>Total orders</span>
        </button>
        <button type="button" class="dash-stat" @click="emit('open', 'wishlist')">
          <Heart :size="24" :stroke-width="1.6" />
          <strong>{{ saved.count.value }}</strong>
          <span>Saved items</span>
        </button>
        <button type="button" class="dash-stat" @click="emit('open', 'addresses')">
          <House :size="24" :stroke-width="1.6" />
          <strong>{{ account.addresses.value.length }}</strong>
          <span>Saved addresses</span>
        </button>
      </div>
    </section>

    <!-- ── Recent orders ───────────────────────────────────────────────── -->
    <section class="dash-block">
      <div class="dash-block__head">
        <h2 class="dash-block__title">Recent orders</h2>
        <button type="button" class="acct-more" @click="emit('open', 'orders')">
          View all orders <ArrowRight :size="14" :stroke-width="2" />
        </button>
      </div>

      <p v-if="!ordersLoaded" class="dash-quiet">Getting your orders…</p>

      <div v-else-if="ordersFailed" class="acct-empty">
        <p class="acct-empty__title">Your orders didn't load</p>
        <p class="acct-empty__note">
          That's on our side, not yours. Your order list still has every order this browser placed.
        </p>
      </div>

      <div v-else-if="orders.length === 0" class="acct-empty">
        <p class="acct-empty__title">No orders yet</p>
        <p class="acct-empty__note">Once you check out, your orders and where they've got to show up here.</p>
        <a href="/" class="acct-btn" style="margin-top: 16px">Start shopping</a>
      </div>

      <div v-else class="dash-recent">
        <button
          v-for="order in recentOrders"
          :key="order.orderId"
          type="button"
          class="dash-card dash-order"
          @click="viewOrder(order)"
        >
          <span class="dash-order__art">
            <img v-if="orderImage(order)" :src="orderImage(order)" alt="" loading="lazy" />
            <ShoppingBag v-else :size="28" :stroke-width="1.5" />
          </span>
          <span class="dash-order__body">
            <span class="dash-order__no">Order #{{ order.ticketNumber }}</span>
            <span class="dash-order__name">{{ headline(order) }}</span>
            <span class="dash-order__meta">
              {{ formatCurrency(order.totalCents) }} · {{ itemCount(order) }} item{{ itemCount(order) === 1 ? '' : 's' }}
            </span>
            <span class="acct-status" :class="`acct-status--${orderBadge(order).tone}`">{{ orderBadge(order).label }}</span>
            <span class="dash-order__date">{{ placedOn(order) }}</span>
          </span>
        </button>
      </div>
    </section>

    <!-- ── Addresses and wishlist ──────────────────────────────────────── -->
    <div class="dash-pair">
      <section class="dash-card dash-block dash-block--boxed">
        <div class="dash-block__head">
          <h2 class="dash-block__title">Saved addresses</h2>
          <button type="button" class="acct-more" @click="emit('open', 'addresses')">
            Manage addresses <ArrowRight :size="14" :stroke-width="2" />
          </button>
        </div>

        <p v-if="addressPreview.length === 0" class="dash-quiet">
          None yet. Save the place you order to most and checkout fills it in.
        </p>

        <div v-else class="dash-addrs">
          <article v-for="address in addressPreview" :key="address.id" class="dash-addr">
            <div class="dash-addr__top">
              <House :size="18" :stroke-width="1.7" />
              <span v-if="address.isDefault" class="acct-tag">Default</span>
            </div>
            <p class="dash-addr__label">{{ address.label }}</p>
            <p class="dash-addr__line">{{ profile?.name }}</p>
            <p class="dash-addr__line">{{ address.line1 }}</p>
            <p class="dash-addr__line">{{ [address.barangay, address.city].filter(Boolean).join(', ') }}</p>
            <button type="button" class="acct-btn acct-btn--outline dash-addr__edit" @click="emit('open', 'addresses')">
              Edit
            </button>
          </article>
        </div>
      </section>

      <section class="dash-card dash-block dash-block--boxed">
        <div class="dash-block__head">
          <h2 class="dash-block__title">My wishlist</h2>
          <button type="button" class="acct-more" @click="emit('open', 'wishlist')">
            View wishlist <ArrowRight :size="14" :stroke-width="2" />
          </button>
        </div>

        <p v-if="savedPreview.length === 0" class="dash-quiet">
          Nothing saved yet. Tap the heart on any product to keep it here.
        </p>

        <div v-else class="dash-saved">
          <article v-for="item in savedPreview" :key="item.key" class="dash-saved__item">
            <a :href="savedHref(item)" class="dash-saved__art">
              <img v-if="item.product.imageUrl" :src="item.product.imageUrl" :alt="item.product.name" loading="lazy" />
              <ShoppingBag v-else :size="28" :stroke-width="1.5" />
            </a>
            <button
              type="button"
              class="dash-saved__heart"
              :aria-label="`Remove ${item.product.name} from your wishlist`"
              @click="saved.remove(item.key)"
            >
              <Heart :size="15" :stroke-width="2" fill="currentColor" />
            </button>
            <a :href="savedHref(item)" class="dash-saved__name">{{ item.product.name }}</a>
            <p class="dash-saved__price">{{ formatCurrency(item.product.priceCents) }}</p>
            <p v-if="item.shop.name" class="dash-saved__shop">{{ item.shop.name }}</p>
          </article>
        </div>
      </section>
    </div>

    <!-- ── Order history ───────────────────────────────────────────────── -->
    <section v-if="ordersLoaded && orders.length > 0" class="dash-card dash-block dash-block--boxed">
      <div class="dash-block__head">
        <h2 class="dash-block__title">Order history</h2>
        <button type="button" class="acct-more" @click="emit('open', 'orders')">
          View all orders <ArrowRight :size="14" :stroke-width="2" />
        </button>
      </div>

      <div class="dash-table-wrap">
        <table class="dash-table">
          <thead>
            <tr>
              <th scope="col">Order #</th>
              <th scope="col">Date</th>
              <th scope="col">Items</th>
              <th scope="col">Total</th>
              <th scope="col">Status</th>
              <th scope="col"><span class="dash-sr">Action</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in tableOrders" :key="order.orderId">
              <td class="dash-table__no">{{ order.ticketNumber }}</td>
              <td>{{ placedOn(order) }}</td>
              <td>{{ itemCount(order) }} item{{ itemCount(order) === 1 ? '' : 's' }}</td>
              <td>{{ formatCurrency(order.totalCents) }}</td>
              <td><span class="acct-status" :class="`acct-status--${orderBadge(order).tone}`">{{ orderBadge(order).label }}</span></td>
              <td>
                <button type="button" class="acct-more" @click="viewOrder(order)">View details</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dash { display: grid; gap: 22px; }

/* Grid items default to min-width: auto, so the order table's own min-width
   would widen the whole column — and the page — instead of scrolling inside
   its wrapper on a phone. */
.dash > * { min-width: 0; }

/* ── Greeting ───────────────────────────────────────────────────────── */

.dash-hello {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.dash-hello__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: clamp(1.7rem, 2.6vw, 2.2rem);
  font-weight: 700;
  line-height: 1.15;
  color: var(--acct-ink);
  overflow-wrap: anywhere;
}

.dash-hello__sub { margin: 8px 0 0; font-size: 14.5px; color: var(--acct-muted); }

.dash-hello__script {
  flex-shrink: 0;
  margin: -4px 12px 0 0;
  font-family: var(--sf-script);
  font-size: 23px;
  line-height: 1.05;
  text-align: center;
  color: var(--acct-muted);
  transform: rotate(-8deg);
}

/* ── Cards ──────────────────────────────────────────────────────────── */

.dash-card {
  border: 1px solid var(--acct-rule);
  border-radius: var(--acct-radius);
  background: var(--acct-surface);
  box-shadow: 0 1px 2px rgba(35, 29, 24, 0.04);
}

/* ── Profile ────────────────────────────────────────────────────────── */

.dash-profile {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 24px;
  padding: 20px 24px;
}

.dash-profile__who { display: flex; align-items: center; gap: 20px; min-width: 0; }

.dash-profile__avatar {
  flex-shrink: 0;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  object-fit: cover;
}
.dash-profile__avatar--letter {
  display: grid;
  place-items: center;
  background: var(--sf-forest, #1f2e25);
  color: var(--sf-cream, #f6f1e8);
  font-family: var(--sf-serif);
  font-size: 38px;
  font-weight: 700;
}

.dash-profile__text { min-width: 0; }
.dash-profile__name {
  margin: 0 0 4px;
  font-family: var(--sf-serif);
  font-size: 19px;
  font-weight: 700;
  color: var(--acct-ink);
  overflow-wrap: anywhere;
}
.dash-profile__line { margin: 0; font-size: 13.5px; line-height: 1.55; color: var(--acct-muted); overflow-wrap: anywhere; }
.dash-profile__text .acct-btn { margin-top: 10px; }

.dash-stats { display: flex; }

.dash-stat {
  display: grid;
  justify-items: center;
  gap: 4px;
  min-width: 118px;
  padding: 8px 16px;
  border: none;
  border-left: 1px solid var(--acct-rule);
  background: none;
  color: var(--acct-accent);
  font-family: inherit;
  cursor: pointer;
}
.dash-stat strong { margin-top: 6px; font-size: 18px; font-weight: 800; color: var(--acct-ink); }
.dash-stat span { font-size: 12.5px; color: var(--acct-muted); }
.dash-stat:hover span { color: var(--acct-accent); text-decoration: underline; text-underline-offset: 3px; }
.dash-stat:focus-visible { outline: 2px solid var(--acct-accent); outline-offset: -2px; border-radius: 6px; }

/* ── Blocks ─────────────────────────────────────────────────────────── */

.dash-block__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.dash-block__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: 19px;
  font-weight: 700;
  color: var(--acct-ink);
}

.dash-block--boxed { padding: 20px 22px; }

.dash-quiet { margin: 0; font-size: 14px; line-height: 1.6; color: var(--acct-muted); }

/* ── Recent orders ──────────────────────────────────────────────────── */

.dash-recent {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.dash-order {
  display: flex;
  gap: 14px;
  padding: 14px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 150ms, box-shadow 150ms;
}
.dash-order:hover { border-color: #d8c7b2; box-shadow: 0 6px 18px rgba(35, 29, 24, 0.08); }
.dash-order:focus-visible { outline: 2px solid var(--acct-accent); outline-offset: 2px; }

.dash-order__art {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 84px;
  height: 96px;
  overflow: hidden;
  border-radius: 6px;
  background: var(--sf-sand, #ede5d8);
  color: var(--acct-faint);
}
.dash-order__art img { width: 100%; height: 100%; object-fit: cover; }

.dash-order__body { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; min-width: 0; }
.dash-order__no { font-size: 12px; color: var(--acct-muted); }
.dash-order__name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--acct-ink);
}
.dash-order__meta { font-size: 12.5px; color: var(--acct-muted); margin-bottom: 4px; }
.dash-order__date { margin-top: 4px; font-size: 12px; color: var(--acct-faint); }

/* ── Addresses and wishlist ─────────────────────────────────────────── */

.dash-pair {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 22px;
}

.dash-addrs,
.dash-saved {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.dash-addr {
  padding: 14px 16px;
  border: 1px solid var(--acct-rule);
  border-radius: 8px;
  background: #fff;
}
.dash-addr__top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; color: var(--acct-ink); }
.dash-addr__label { margin: 0 0 4px; font-size: 14px; font-weight: 700; color: var(--acct-ink); }
.dash-addr__line { margin: 0; font-size: 13px; line-height: 1.55; color: var(--acct-muted); overflow-wrap: anywhere; }
.dash-addr__edit { margin-top: 12px; height: 30px; }

.dash-saved__item { position: relative; min-width: 0; }

.dash-saved__art {
  display: grid;
  place-items: center;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 8px;
  background: var(--sf-sand, #ede5d8);
  color: var(--acct-faint);
}
.dash-saved__art img { width: 100%; height: 100%; object-fit: cover; }

.dash-saved__heart {
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 253, 249, 0.94);
  color: var(--acct-accent);
  box-shadow: 0 2px 8px rgba(35, 29, 24, 0.16);
  cursor: pointer;
}

.dash-saved__name {
  display: block;
  margin-top: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--acct-ink);
}
.dash-saved__name:hover { color: var(--acct-accent); }
.dash-saved__price { margin: 2px 0 0; font-size: 13.5px; font-weight: 800; color: var(--acct-ink); }
.dash-saved__shop { margin: 2px 0 0; font-size: 12px; color: var(--acct-faint); }

/* ── Table ──────────────────────────────────────────────────────────── */

/* Relative so the visually-hidden "Action" label positions inside the
   scroller; otherwise it escapes the clip and widens the page on a phone. */
.dash-table-wrap { position: relative; overflow-x: auto; }

.dash-table {
  width: 100%;
  min-width: 620px;
  border-collapse: collapse;
  font-size: 13.5px;
}

.dash-table th {
  padding: 10px 14px;
  background: #f3ede3;
  font-weight: 600;
  text-align: left;
  color: var(--acct-ink);
}
.dash-table th:first-child { border-radius: 6px 0 0 6px; }
.dash-table th:last-child { border-radius: 0 6px 6px 0; }

.dash-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--acct-rule);
  color: var(--acct-ink);
}
.dash-table tr:last-child td { border-bottom: none; }
.dash-table__no { font-weight: 600; font-variant-numeric: tabular-nums; }

.dash-sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

/* ── Narrower screens ───────────────────────────────────────────────── */

@media (max-width: 1180px) {
  .dash-profile { grid-template-columns: 1fr; }
  .dash-stats { border-top: 1px solid var(--acct-rule); padding-top: 12px; }
  .dash-stat { flex: 1; min-width: 0; }
  .dash-stat:first-child { border-left: none; }
  .dash-recent { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dash-pair { grid-template-columns: 1fr; }
}

@media (max-width: 760px) {
  .dash-hello__script { display: none; }
  .dash-recent { grid-template-columns: 1fr; }
  .dash-profile__avatar { width: 68px; height: 68px; }
  .dash-profile__avatar--letter { font-size: 28px; }
}

@media (max-width: 480px) {
  .dash-addrs { grid-template-columns: 1fr; }
  .dash-profile { padding: 18px 16px; }
  .dash-block--boxed { padding: 18px 16px; }
}
</style>
