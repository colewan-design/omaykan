<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowRight,
  Banknote,
  Bike,
  Check,
  ChevronLeft,
  HandCoins,
  HeartHandshake,
  Lock,
  Minus,
  Plus,
  Store,
  Trash2,
} from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import FdHeader from '@pos/web/landing/FdHeader.vue'
import FdFooter from '@pos/web/landing/FdFooter.vue'
import ProductRow from '@pos/web/landing/ProductRow.vue'
import MountainMark from '@pos/web/landing/MountainMark.vue'
import { useStorefrontCart, type CartLine } from '@pos/web/commerce/cart'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { useSavedProducts } from '@pos/web/commerce/favorites'
import { useCheckout } from '@pos/web/commerce/checkout'
import { DELIVERY_BASE_FEE_CENTS } from '@pos/web/commerce/delivery'
import CheckoutForm from './CheckoutForm.vue'

// The cart as a page, in the highland redesign: a photographic banner, the
// lines on the left with a tick beside each, the order summary on the right,
// and more from the same shelf underneath.
//
// It used to be a slide-over in the header (CartDrawer). The redesign gives it
// a page, and a page is what lets a shopper choose which lines go into this
// order: anything left unticked stays in the basket for next time.
//
// Three steps on one document, like the drawer had — cart, checkout, placed.
// Checkout is in the URL (?step=checkout) so Back walks out of it, and so a
// shopper sent off to sign in comes back to the step they left.
//
// Every slot the reference fills with something the API does not have holds
// something it does: no delivery-date promises (nothing here knows one), no
// voucher box (there are no vouchers), and the reassurances are the platform's
// own promises rather than claims about a product.

const cart = useStorefrontCart()
const catalog = useStorefrontCatalog()
const account = useCustomerAccount()
const saved = useSavedProducts()

// ── Checking the basket against the shelf ─────────────────────────────────

const liveById = computed(() => new Map(catalog.products.map((product) => [product.id, product])))

// Today's price and stock, not the snapshot from whenever it was added.
watch(
  () => catalog.products,
  (products) => {
    if (products.length > 0) cart.refresh(products)
  },
  { immediate: true },
)

type LineState = 'checking' | 'in' | 'low' | 'gone'

function lineState(line: CartLine): LineState {
  if (catalog.loading) return 'checking'
  // A shelf that failed to load says nothing about any one line, and must not
  // block an order the server may well accept.
  if (catalog.error || catalog.products.length === 0) return 'in'

  // The API drops sold-out products from the shelf, so absent means sold out
  // or taken off sale — either way, not something this order can have.
  const live = liveById.value.get(line.product.id)
  if (!live || live.outOfStock) return 'gone'

  const { stockQty, lowStockThreshold } = live
  if (stockQty !== undefined && lowStockThreshold !== undefined && stockQty > 0 && stockQty <= lowStockThreshold) {
    return 'low'
  }
  return 'in'
}

// ── Which lines go into this order ────────────────────────────────────────

// The unticked ones are what is remembered, so a line added from the shelf
// below arrives ticked like every other.
const unticked = ref<Set<string>>(new Set())

const orderable = computed(() => cart.cartLines.value.filter((line) => lineState(line) !== 'gone'))
const selectedLines = computed(() => orderable.value.filter((line) => !unticked.value.has(line.product.id)))
const allTicked = computed(
  () => orderable.value.length > 0 && selectedLines.value.length === orderable.value.length,
)

function isTicked(line: CartLine): boolean {
  return lineState(line) !== 'gone' && !unticked.value.has(line.product.id)
}

function toggleLine(productId: string) {
  const next = new Set(unticked.value)
  if (next.has(productId)) next.delete(productId)
  else next.add(productId)
  unticked.value = next
}

function toggleAll() {
  unticked.value = allTicked.value ? new Set(orderable.value.map((line) => line.product.id)) : new Set()
}

const checkout = useCheckout(selectedLines)
const totals = checkout.totals

// ── Steps ─────────────────────────────────────────────────────────────────

type Step = 'cart' | 'checkout' | 'placed'

function readStep(): Step {
  try {
    return new URLSearchParams(window.location.search).get('step') === 'checkout' ? 'checkout' : 'cart'
  } catch {
    return 'cart'
  }
}

const step = ref<Step>(readStep())

function goTo(next: 'cart' | 'checkout') {
  step.value = next
  window.history.pushState({}, '', next === 'checkout' ? `${window.location.pathname}?step=checkout` : window.location.pathname)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function applyUrl() {
  if (step.value !== 'placed') step.value = readStep()
}

onMounted(() => window.addEventListener('popstate', applyUrl))
onBeforeUnmount(() => window.removeEventListener('popstate', applyUrl))

// A checkout step with nothing ticked, or nobody signed in to place it, is
// the cart. Waits for the shelf: until it is in, every line counts as orderable.
watch(
  [() => selectedLines.value.length, () => checkout.checkoutGated.value, () => catalog.loading],
  ([count, gated, loading]) => {
    if (step.value !== 'checkout') return
    if (gated || (!loading && count === 0)) {
      step.value = 'cart'
      window.history.replaceState({}, '', window.location.pathname)
    }
  },
  { immediate: true },
)

async function placeOrder() {
  if (await checkout.placeOrder()) {
    step.value = 'placed'
    unticked.value = new Set()
    window.history.replaceState({}, '', window.location.pathname)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

const heading = computed(() => {
  if (step.value === 'checkout') return 'Checkout'
  if (step.value === 'placed') return 'Order placed'
  return 'Shopping cart'
})

watch(heading, (title) => (document.title = `${title} — Omaykan`), { immediate: true })

// ── Around the edges ──────────────────────────────────────────────────────

/** The shelf's own record beats the name written down when the basket was filled. */
const shopName = computed(() => catalog.shop?.name || cart.cartShop.value?.name || '')

/**
 * Links back into the shop have to name it: the landing page otherwise opens
 * on the build-time tenant, whose shelf these products may not be on.
 */
function productHref(productId: string): string {
  const slug = cart.cartShop.value?.orgSlug
  const params = new URLSearchParams()
  if (slug) params.set('shop', slug)
  params.set('product', productId)
  return `/?${params.toString()}`
}

function openProduct(productId: string) {
  window.location.href = productHref(productId)
}

// Captured once: a card added from the shelf below would otherwise vanish from
// under the pointer the moment its Add button was pressed.
const excluded = new Set(cart.cartLines.value.map((line) => line.product.id))

/** The rest of the same aisles first, then anything else on the shelf. */
const recommended = computed(() => {
  const aisles = new Set(cart.cartLines.value.map((line) => line.product.categoryId))
  const pool = catalog.products.filter((product) => !product.outOfStock && !excluded.has(product.id))
  return [
    ...pool.filter((product) => aisles.has(product.categoryId)),
    ...pool.filter((product) => !aisles.has(product.categoryId)),
  ].slice(0, 12)
})

const promises = [
  { icon: Store, text: 'The same price you would pay at the counter' },
  { icon: HandCoins, text: 'No commission taken from the shop' },
  { icon: Bike, text: 'The rider keeps the whole delivery fee' },
  { icon: HeartHandshake, text: 'Keeps the market close to home' },
]

function search(term: string) {
  window.location.href = term ? `/?q=${encodeURIComponent(term)}` : '/'
}
</script>

<template>
  <div class="landing fd cartpg">
    <FdHeader shop-href="/" @search="search" />

    <main class="cartpg-main">
      <!-- ── The banner ────────────────────────────────────────────────── -->
      <section class="cartpg-hero">
        <img class="cartpg-hero__img" src="/storefront/hero.webp" alt="" />
        <div class="cartpg-hero__shade" aria-hidden="true"></div>
        <div class="cartpg-hero__loom" aria-hidden="true">
          <span class="sf-weave-v"></span>
          <span class="cartpg-hero__stripes"></span>
          <span class="sf-weave-v"></span>
        </div>

        <div class="cartpg-hero__copy">
          <h1 class="cartpg-hero__title">{{ heading }}</h1>
          <p class="cartpg-hero__tag">Local finds. Greater tomorrows.</p>
        </div>

        <p class="cartpg-hero__script" aria-hidden="true">Same roots.<br />Brighter days ahead.</p>
      </section>

      <div class="cartpg-wrap">
        <nav class="cartpg-crumbs" aria-label="Breadcrumb">
          <a href="/">Home</a>
          <span aria-hidden="true">›</span>
          <a v-if="step === 'checkout'" href="/cart" @click.prevent="goTo('cart')">Cart</a>
          <span v-else class="cartpg-crumbs__here">Cart</span>
          <template v-if="step === 'checkout'">
            <span aria-hidden="true">›</span>
            <span class="cartpg-crumbs__here">Checkout</span>
          </template>
        </nav>

        <!-- ── Placed ──────────────────────────────────────────────────── -->
        <section v-if="step === 'placed' && checkout.placed.value" class="cartpg-done">
          <div class="cartpg-done__mark" aria-hidden="true">
            <Check :size="30" :stroke-width="3" />
          </div>
          <h2 class="cartpg-done__title">Order {{ checkout.placed.value.ticketNumber }} is in</h2>
          <p class="cartpg-done__note">
            {{ shopName || 'The shop' }} is looking at it now. We'll reach you on the
            {{ checkout.placed.value.reachBy }} you gave us.
          </p>

          <ul class="cartpg-done__lines">
            <li v-for="line in checkout.placed.value.lines" :key="line.product.id">
              <span>{{ line.quantity }} × {{ line.product.name }}</span>
              <span>{{ formatCurrency(line.product.priceCents * line.quantity) }}</span>
            </li>
          </ul>

          <dl class="cartpg-rows cartpg-done__rows">
            <div v-if="checkout.placed.value.deliveryFeeCents">
              <dt>Delivery</dt>
              <dd>{{ formatCurrency(checkout.placed.value.deliveryFeeCents) }}</dd>
            </div>
            <div class="cartpg-rows__total">
              <dt>Paid on {{ checkout.placed.value.method === 'delivery' ? 'delivery' : 'pickup' }}</dt>
              <dd>{{ formatCurrency(checkout.placed.value.totalCents) }}</dd>
            </div>
          </dl>

          <div class="cartpg-done__actions">
            <a href="/account?section=orders" class="cartpg-cta">Track your order <ArrowRight :size="16" :stroke-width="2" /></a>
            <a href="/" class="cartpg-ghost">Keep shopping</a>
          </div>

          <p v-if="cart.cartLines.value.length > 0" class="cartpg-done__left">
            {{ cart.itemCount.value }} item{{ cart.itemCount.value === 1 ? ' is' : 's are' }} still in your cart.
            <button type="button" class="cartpg-linkbtn" @click="goTo('cart')">Back to your cart</button>
          </p>
        </section>

        <!-- ── Empty ───────────────────────────────────────────────────── -->
        <section v-else-if="cart.cartLines.value.length === 0" class="cartpg-empty">
          <MountainMark :size="84" class="cartpg-empty__mark" />
          <h2 class="cartpg-empty__title">Your cart is empty</h2>
          <p class="cartpg-empty__note">
            Add something from a shop's shelves and it will wait for you here.
          </p>
          <div class="cartpg-empty__actions">
            <a href="/" class="cartpg-cta">Start shopping</a>
            <a href="/#shops" class="cartpg-ghost">Browse shops</a>
          </div>
          <p v-if="saved.count.value > 0" class="cartpg-empty__saved">
            You have {{ saved.count.value }} saved item{{ saved.count.value === 1 ? '' : 's' }}.
            <a href="/account?section=wishlist">See your wishlist</a>
          </p>
        </section>

        <!-- ── Cart and checkout ───────────────────────────────────────── -->
        <div v-else class="cartpg-grid">
          <div class="cartpg-col">
            <template v-if="step === 'cart'">
              <div class="cartpg-listhead">
                <h2 class="sf-h2">Your cart ({{ cart.itemCount.value }} item{{ cart.itemCount.value === 1 ? '' : 's' }})</h2>
                <label v-if="orderable.length > 1" class="cartpg-all">
                  <input type="checkbox" :checked="allTicked" @change="toggleAll" />
                  Select all
                </label>
              </div>

              <ul class="cartpg-lines">
                <li
                  v-for="line in cart.cartLines.value"
                  :key="line.product.id"
                  class="cartpg-line"
                  :class="{ 'cartpg-line--off': !isTicked(line), 'cartpg-line--gone': lineState(line) === 'gone' }"
                >
                  <label class="cartpg-tick">
                    <input
                      type="checkbox"
                      :checked="isTicked(line)"
                      :disabled="lineState(line) === 'gone'"
                      :aria-label="`Include ${line.product.name} in this order`"
                      @change="toggleLine(line.product.id)"
                    />
                  </label>

                  <a :href="productHref(line.product.id)" class="cartpg-thumb">
                    <img v-if="line.product.imageUrl" :src="line.product.imageUrl" :alt="line.product.name" loading="lazy" />
                    <span v-else aria-hidden="true">🛒</span>
                  </a>

                  <div class="cartpg-line__info">
                    <a :href="productHref(line.product.id)" class="cartpg-line__name">{{ line.product.name }}</a>
                    <p v-if="shopName" class="cartpg-line__shop">From {{ shopName }}</p>
                    <p v-if="line.product.unitLabel" class="cartpg-line__unit">{{ line.product.unitLabel }}</p>

                    <p class="cartpg-stock" :class="`cartpg-stock--${lineState(line)}`">
                      <template v-if="lineState(line) === 'checking'">Checking the shelf…</template>
                      <template v-else-if="lineState(line) === 'gone'">Not on the shelf right now</template>
                      <template v-else-if="lineState(line) === 'low'">Only {{ liveById.get(line.product.id)?.stockQty }} left today</template>
                      <template v-else>In stock</template>
                    </p>
                  </div>

                  <div class="cartpg-stepper" role="group" :aria-label="`Quantity of ${line.product.name}`">
                    <button
                      type="button"
                      :aria-label="line.quantity === 1 ? `Remove ${line.product.name}` : `One fewer ${line.product.name}`"
                      @click="cart.decrement(line.product.id)"
                    >
                      <Minus :size="15" :stroke-width="2.4" />
                    </button>
                    <span aria-live="polite">{{ line.quantity }}</span>
                    <button
                      type="button"
                      :aria-label="`One more ${line.product.name}`"
                      :disabled="lineState(line) === 'gone'"
                      @click="cart.add(line.product)"
                    >
                      <Plus :size="15" :stroke-width="2.4" />
                    </button>
                  </div>

                  <div class="cartpg-line__end">
                    <p class="cartpg-line__total">{{ formatCurrency(line.product.priceCents * line.quantity) }}</p>
                    <p v-if="line.quantity > 1" class="cartpg-line__each">{{ formatCurrency(line.product.priceCents) }} each</p>
                    <button
                      type="button"
                      class="cartpg-remove"
                      :aria-label="`Remove ${line.product.name} from cart`"
                      @click="cart.remove(line.product.id)"
                    >
                      <Trash2 :size="14" :stroke-width="2" />
                      Remove
                    </button>
                  </div>
                </li>
              </ul>
            </template>

            <template v-else>
              <button type="button" class="cartpg-back" @click="goTo('cart')">
                <ChevronLeft :size="17" :stroke-width="2" />
                Back to cart
              </button>
              <CheckoutForm :checkout="checkout" />
            </template>
          </div>

          <aside class="cartpg-side">
            <section class="cartpg-summary" aria-labelledby="cartpg-summary-title">
              <h2 id="cartpg-summary-title" class="cartpg-summary__title">Order summary</h2>

              <!-- At checkout, what is actually being ordered: not the whole
                   basket once anything has been unticked. -->
              <ul v-if="step === 'checkout'" class="cartpg-mini">
                <li v-for="line in selectedLines" :key="line.product.id">
                  <span class="cartpg-mini__thumb">
                    <img v-if="line.product.imageUrl" :src="line.product.imageUrl" alt="" />
                  </span>
                  <span class="cartpg-mini__name">{{ line.product.name }}</span>
                  <span class="cartpg-mini__qty">× {{ line.quantity }}</span>
                </li>
              </ul>

              <dl class="cartpg-rows">
                <div>
                  <dt>Subtotal ({{ totals.itemCount }} item{{ totals.itemCount === 1 ? '' : 's' }})</dt>
                  <dd>{{ formatCurrency(totals.subtotalCents) }}</dd>
                </div>
                <div v-if="totals.taxCents > 0">
                  <dt>VAT</dt>
                  <dd>{{ formatCurrency(totals.taxCents) }}</dd>
                </div>
                <div v-if="step === 'cart'">
                  <dt>Delivery</dt>
                  <dd class="cartpg-rows__soft">From {{ formatCurrency(DELIVERY_BASE_FEE_CENTS) }} at checkout</dd>
                </div>
                <div v-else-if="checkout.isDelivery.value">
                  <dt>Delivery{{ checkout.deliveryQuote.value ? '' : ' (estimate)' }}</dt>
                  <dd>{{ formatCurrency(checkout.deliveryFeeCents.value) }}</dd>
                </div>
                <div v-else>
                  <dt>Pickup</dt>
                  <dd>Free</dd>
                </div>
                <div class="cartpg-rows__pay">
                  <dt><Banknote :size="16" :stroke-width="1.8" /> How you pay</dt>
                  <dd class="cartpg-rows__soft">Cash or GCash on arrival</dd>
                </div>
                <div class="cartpg-rows__total">
                  <dt>Total</dt>
                  <dd>{{ formatCurrency(step === 'checkout' ? checkout.grandTotalCents.value : totals.totalCents) }}</dd>
                </div>
              </dl>

              <p v-if="!checkout.canOrderOnline.value" class="cartpg-warn">
                This shop doesn't take online orders yet.
              </p>
              <p v-if="checkout.error.value" class="cartpg-error">{{ checkout.error.value }}</p>

              <!-- ── The one button ─────────────────────────────────────── -->
              <template v-if="step === 'cart'">
                <!-- Still trading the stored token for an account: neither door
                     is the right one to show yet. -->
                <button v-if="account.hydrating.value" type="button" class="cartpg-cta cartpg-cta--block" disabled>
                  One moment…
                </button>
                <a v-else-if="checkout.checkoutGated.value" :href="checkout.signInHref.value" class="cartpg-cta cartpg-cta--block">
                  Sign in to check out
                  <ArrowRight :size="16" :stroke-width="2" />
                </a>
                <button
                  v-else
                  type="button"
                  class="cartpg-cta cartpg-cta--block"
                  :disabled="!checkout.canOrderOnline.value || selectedLines.length === 0"
                  @click="goTo('checkout')"
                >
                  Proceed to checkout
                  <ArrowRight :size="16" :stroke-width="2" />
                </button>
                <p v-if="selectedLines.length === 0 && orderable.length > 0" class="cartpg-fine">
                  Tick at least one item to check out.
                </p>
                <p v-else-if="checkout.checkoutGated.value" class="cartpg-fine">
                  Orders are placed from an account, so your addresses and order history stay with
                  you. Your cart is kept while you sign in.
                </p>
              </template>

              <template v-else>
                <a
                  v-if="checkout.needsSignIn.value || checkout.checkoutGated.value"
                  :href="checkout.signInHref.value"
                  class="cartpg-cta cartpg-cta--block"
                >
                  Sign in to finish
                </a>
                <button
                  v-else
                  type="button"
                  class="cartpg-cta cartpg-cta--block"
                  :disabled="!checkout.canSubmit.value || checkout.submitting.value"
                  @click="placeOrder"
                >
                  {{ checkout.submitting.value ? 'Placing your order…' : `Place order — ${formatCurrency(checkout.grandTotalCents.value)}` }}
                </button>
                <p class="cartpg-fine">The shop confirms prices and the delivery fee when it accepts the order.</p>
              </template>

              <p class="cartpg-secure">
                <Lock :size="13" :stroke-width="2" />
                Nothing is charged online — you pay when your order arrives.
              </p>
            </section>

            <!-- The reference's "why buy here" card. Its four lines were claims
                 about the goods; these are the platform's own promises, which
                 are true of every line in every basket. -->
            <section class="cartpg-promise">
              <p class="cartpg-promise__title">A purchase today.<br />A stronger tomorrow.</p>
              <svg class="cartpg-promise__peaks" viewBox="0 0 160 70" aria-hidden="true">
                <path d="M0 70V46l28-18 20 12 34-30 30 26 22-12 26 18v28Z" fill="#d8cdbb" />
                <path d="m82 10-8 8 6-1 4 4 4-5 4 2Z" fill="#f6f1e8" />
                <path d="M0 70V58l34-14 28 10 34-18 34 16 30-6v24Z" fill="#b9ad99" />
              </svg>
              <ul class="cartpg-promise__list">
                <li v-for="item in promises" :key="item.text">
                  <component :is="item.icon" :size="18" :stroke-width="1.7" />
                  <span>{{ item.text }}</span>
                </li>
              </ul>
            </section>
          </aside>
        </div>

        <ProductRow
          v-if="step !== 'checkout'"
          :title="shopName ? `More from ${shopName}` : 'You may also like'"
          :products="recommended"
          @select="openProduct"
        />
      </div>
    </main>

    <div class="sf-weave" aria-hidden="true"></div>
    <FdFooter shop-href="/" />
  </div>
</template>

<style scoped>
.cartpg-main { background: var(--sf-cream); }

/* ── Banner ─────────────────────────────────────────────────────────── */

.cartpg-hero {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 190px;
  overflow: hidden;
  background: var(--sf-forest-deep);
  color: var(--sf-paper);
}

.cartpg-hero__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 40%;
}

.cartpg-hero__shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(23, 35, 28, 0.94) 0%, rgba(23, 35, 28, 0.78) 40%, rgba(23, 35, 28, 0.35) 100%);
}

/* The loom edge: two woven borders either side of a band of plain stripes,
   standing in for the reference's photograph of a textile. */
.cartpg-hero__loom {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  display: flex;
}
.cartpg-hero__loom .sf-weave-v { height: 100%; }
.cartpg-hero__stripes {
  width: 64px;
  background: repeating-linear-gradient(
    180deg,
    #8f1d1f 0 7px,
    #1b1512 7px 10px,
    #c8a15a 10px 12px,
    #1b1512 12px 15px,
    #8f1d1f 15px 22px,
    #efe3cf 22px 24px
  );
}

.cartpg-hero__copy {
  position: relative;
  z-index: 1;
  padding: 36px var(--fd-gutter) 36px calc(104px + var(--fd-gutter) + 24px);
}

.cartpg-hero__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: clamp(2rem, 3.6vw, 2.9rem);
  font-weight: 700;
  line-height: 1.1;
}

.cartpg-hero__tag {
  margin: 8px 0 0;
  font-size: 17px;
  color: rgba(251, 248, 243, 0.9);
}

.cartpg-hero__script {
  position: absolute;
  right: calc(var(--fd-gutter) + 20px);
  top: 50%;
  z-index: 1;
  margin: 0;
  font-family: var(--sf-script);
  font-size: 30px;
  line-height: 1.1;
  text-align: right;
  color: rgba(251, 248, 243, 0.92);
  transform: translateY(-50%) rotate(-5deg);
}

/* ── Page ───────────────────────────────────────────────────────────── */

.cartpg-wrap {
  max-width: 1280px;
  margin: 0 auto;
  padding: 18px var(--fd-gutter) 64px;
}

.cartpg-crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 22px;
  font-size: 13px;
  color: var(--sf-faint);
}
.cartpg-crumbs a { color: var(--sf-muted); }
.cartpg-crumbs a:hover { color: var(--sf-clay); text-decoration: underline; text-underline-offset: 3px; }
.cartpg-crumbs__here { color: var(--sf-ink); font-weight: 600; }

.cartpg-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 28px;
  align-items: start;
  margin-bottom: 56px;
}

.cartpg-col { min-width: 0; }

.cartpg-listhead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.cartpg-all {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--sf-muted);
  cursor: pointer;
}

.cartpg-all input,
.cartpg-tick input {
  width: 18px;
  height: 18px;
  margin: 0;
  accent-color: var(--sf-clay);
  cursor: pointer;
}

/* ── Lines ──────────────────────────────────────────────────────────── */

.cartpg-lines {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: #fffdf9;
}

.cartpg-line {
  display: grid;
  grid-template-columns: 22px 92px minmax(0, 1fr) auto 128px;
  align-items: center;
  gap: 18px;
  padding: 18px 20px;
  border-top: 1px solid var(--sf-rule);
  transition: opacity 150ms;
}
.cartpg-line:first-child { border-top: none; }

/* Unticked lines stay readable — they are still in the basket — but step back. */
.cartpg-line--off .cartpg-thumb,
.cartpg-line--off .cartpg-line__info { opacity: 0.55; }

.cartpg-tick { display: grid; place-items: center; cursor: pointer; }
.cartpg-tick input:disabled { cursor: not-allowed; }

.cartpg-thumb {
  display: grid;
  place-items: center;
  width: 92px;
  height: 92px;
  overflow: hidden;
  border-radius: 8px;
  background: var(--sf-sand);
  font-size: 28px;
}
.cartpg-thumb img { width: 100%; height: 100%; object-fit: cover; }

.cartpg-line__info { min-width: 0; }

.cartpg-line__name {
  display: block;
  font-size: 15.5px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--sf-ink);
}
.cartpg-line__name:hover { color: var(--sf-clay); text-decoration: underline; text-underline-offset: 3px; }

.cartpg-line__shop,
.cartpg-line__unit {
  margin: 3px 0 0;
  font-size: 13px;
  color: var(--sf-muted);
}
.cartpg-line__unit { color: var(--sf-faint); }

.cartpg-stock {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0 0;
  font-size: 12.5px;
  font-weight: 700;
}
.cartpg-stock::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}
.cartpg-stock--in { color: #2f7a4a; }
.cartpg-stock--low { color: var(--sf-clay); }
.cartpg-stock--gone { color: #9a3b2a; }
.cartpg-stock--checking { color: var(--sf-faint); font-weight: 600; }

.cartpg-stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--sf-rule);
  border-radius: 6px;
  background: #fff;
}
.cartpg-stepper button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 36px;
  border: none;
  background: none;
  color: var(--sf-ink);
  cursor: pointer;
}
.cartpg-stepper button:hover:not(:disabled) { color: var(--sf-clay); background: var(--sf-cream); }
.cartpg-stepper button:disabled { color: var(--sf-rule); cursor: not-allowed; }
.cartpg-stepper span {
  min-width: 34px;
  text-align: center;
  font-size: 14.5px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  border-left: 1px solid var(--sf-rule);
  border-right: 1px solid var(--sf-rule);
  line-height: 36px;
}

.cartpg-line__end { text-align: right; }
.cartpg-line__total { margin: 0; font-size: 17px; font-weight: 800; color: var(--sf-ink); }
.cartpg-line__each { margin: 2px 0 0; font-size: 12px; color: var(--sf-faint); }

.cartpg-remove {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 10px;
  padding: 0;
  border: none;
  background: none;
  color: var(--sf-clay);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.cartpg-remove:hover { color: var(--sf-clay-deep); text-decoration: underline; text-underline-offset: 3px; }

.cartpg-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 14px;
  padding: 0;
  border: none;
  background: none;
  color: var(--sf-muted);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.cartpg-back:hover { color: var(--sf-clay); }

/* ── Summary ────────────────────────────────────────────────────────── */

.cartpg-side {
  position: sticky;
  top: 96px;
  display: grid;
  gap: 18px;
}

.cartpg-summary {
  padding: 22px 22px 18px;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: #fffdf9;
}

.cartpg-summary__title {
  margin: 0 0 14px;
  font-family: var(--sf-serif);
  font-size: 19px;
  font-weight: 700;
  color: var(--sf-ink);
}

.cartpg-mini {
  display: grid;
  gap: 8px;
  margin: 0 0 14px;
  padding: 0 0 14px;
  list-style: none;
  border-bottom: 1px solid var(--sf-rule);
}
.cartpg-mini li { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.cartpg-mini__thumb {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  overflow: hidden;
  border-radius: 5px;
  background: var(--sf-sand);
}
.cartpg-mini__thumb img { width: 100%; height: 100%; object-fit: cover; }
.cartpg-mini__name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--sf-ink); }
.cartpg-mini__qty { color: var(--sf-muted); font-weight: 600; }

.cartpg-rows { margin: 0; }
.cartpg-rows > div {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 14px;
  padding: 7px 0;
  font-size: 14px;
}
.cartpg-rows dt { color: var(--sf-muted); }
.cartpg-rows dd { margin: 0; font-weight: 600; color: var(--sf-ink); text-align: right; }
.cartpg-rows__soft { font-weight: 500 !important; color: var(--sf-muted) !important; font-size: 13px; }

.cartpg-rows__pay { margin-top: 4px; padding-top: 11px !important; border-top: 1px solid var(--sf-rule); }
.cartpg-rows__pay dt { display: inline-flex; align-items: center; gap: 7px; white-space: nowrap; }

.cartpg-rows__total { margin-top: 6px; padding-top: 14px !important; border-top: 1px solid var(--sf-rule); }
.cartpg-rows__total dt { font-size: 16px; font-weight: 700; color: var(--sf-ink); }
.cartpg-rows__total dd { font-size: 22px; font-weight: 800; }

.cartpg-warn { margin: 10px 0 0; font-size: 13px; font-weight: 600; color: #a05a14; }
.cartpg-error {
  margin: 12px 0 0;
  padding: 9px 12px;
  border-radius: 6px;
  background: #fbeae4;
  font-size: 13px;
  font-weight: 600;
  color: #9a3b2a;
}

.cartpg-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px 22px;
  border: none;
  border-radius: 6px;
  background: var(--sf-clay);
  color: #fff;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
  text-decoration: none;
  cursor: pointer;
  transition: background 150ms;
}
.cartpg-cta:hover:not(:disabled) { background: var(--sf-clay-deep); }
.cartpg-cta:focus-visible { outline: 2px solid var(--sf-forest); outline-offset: 3px; }
.cartpg-cta:disabled { background: var(--sf-sand-deep); color: var(--sf-faint); cursor: not-allowed; }
.cartpg-cta--block { display: flex; width: 100%; margin-top: 16px; }

.cartpg-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 13px 22px;
  border: 1px solid var(--sf-rule);
  border-radius: 6px;
  background: #fff;
  color: var(--sf-ink);
  font-size: 14.5px;
  font-weight: 600;
}
.cartpg-ghost:hover { border-color: var(--sf-clay); color: var(--sf-clay); }

.cartpg-fine { margin: 10px 0 0; font-size: 12.5px; line-height: 1.5; color: var(--sf-faint); text-align: center; }

.cartpg-secure {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--sf-muted);
  text-align: center;
}

.cartpg-linkbtn {
  margin-left: 6px;
  padding: 0;
  border: none;
  background: none;
  color: var(--sf-clay);
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

/* ── Promise card ───────────────────────────────────────────────────── */

.cartpg-promise {
  position: relative;
  overflow: hidden;
  padding: 22px 22px 20px;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: linear-gradient(180deg, #f3ece0 0%, #efe6d6 100%);
}

.cartpg-promise__title {
  position: relative;
  z-index: 1;
  margin: 0 0 18px;
  font-family: var(--sf-script);
  font-size: 25px;
  line-height: 1.1;
  color: var(--sf-ink);
  transform: rotate(-4deg);
  transform-origin: left;
}

.cartpg-promise__peaks {
  position: absolute;
  top: 10px;
  right: -6px;
  width: 128px;
  height: auto;
  opacity: 0.75;
}

.cartpg-promise__list {
  position: relative;
  display: grid;
  gap: 11px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.cartpg-promise__list li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--sf-ink);
}
.cartpg-promise__list svg { flex-shrink: 0; color: var(--sf-forest); }

/* ── Empty and placed ───────────────────────────────────────────────── */

.cartpg-empty,
.cartpg-done {
  max-width: 560px;
  margin: 20px auto 64px;
  padding: 40px 32px;
  border: 1px solid var(--sf-rule);
  border-radius: 12px;
  background: #fffdf9;
  text-align: center;
}

.cartpg-empty__mark { margin: 0 auto 10px; color: var(--sf-forest); }
.cartpg-empty__title,
.cartpg-done__title {
  margin: 0 0 8px;
  font-family: var(--sf-serif);
  font-size: 26px;
  font-weight: 700;
  color: var(--sf-ink);
}
.cartpg-empty__note,
.cartpg-done__note { margin: 0 auto; max-width: 40ch; font-size: 15px; line-height: 1.6; color: var(--sf-muted); }

.cartpg-empty__actions,
.cartpg-done__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}

.cartpg-empty__saved { margin: 22px 0 0; font-size: 14px; color: var(--sf-muted); }
.cartpg-empty__saved a { color: var(--sf-clay); font-weight: 600; text-decoration: underline; text-underline-offset: 3px; }

.cartpg-done__mark {
  display: grid;
  place-items: center;
  width: 66px;
  height: 66px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: #e4efe4;
  color: #2f7a4a;
}

.cartpg-done__lines {
  margin: 24px 0 0;
  padding: 16px 0 0;
  border-top: 1px solid var(--sf-rule);
  list-style: none;
  text-align: left;
}
.cartpg-done__lines li {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 3px 0;
  font-size: 14px;
  color: var(--sf-muted);
}
.cartpg-done__rows { margin-top: 8px; text-align: left; }
.cartpg-done__left { margin: 20px 0 0; font-size: 14px; color: var(--sf-muted); }

/* ── Narrower screens ───────────────────────────────────────────────── */

@media (max-width: 1080px) {
  .cartpg-grid { grid-template-columns: minmax(0, 1fr) 300px; }
  .cartpg-line { grid-template-columns: 22px 76px minmax(0, 1fr) 110px; }
  .cartpg-thumb { width: 76px; height: 76px; }
  /* The stepper drops under the name rather than squeezing it. */
  .cartpg-stepper { grid-column: 3; grid-row: 2; justify-self: start; }
  .cartpg-line__end { grid-column: 4; grid-row: 1 / span 2; }
}

@media (max-width: 900px) {
  .cartpg-grid { grid-template-columns: 1fr; }
  .cartpg-side { position: static; }
  .cartpg-hero__script { display: none; }
}

@media (max-width: 560px) {
  .cartpg-hero { min-height: 150px; }
  .cartpg-hero__loom { display: none; }
  .cartpg-hero__copy { padding: 28px var(--fd-gutter); }
  .cartpg-line {
    grid-template-columns: 22px 64px minmax(0, 1fr);
    gap: 12px;
    padding: 16px 14px;
  }
  .cartpg-thumb { width: 64px; height: 64px; }
  .cartpg-stepper { grid-column: 3; grid-row: 2; }
  .cartpg-line__end {
    grid-column: 3;
    grid-row: 3;
    display: flex;
    align-items: baseline;
    gap: 12px;
    text-align: left;
  }
  .cartpg-remove { margin: 0 0 0 auto; }
  .cartpg-empty,
  .cartpg-done { padding: 32px 20px; }
}
</style>
