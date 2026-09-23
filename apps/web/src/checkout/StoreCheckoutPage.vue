<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  Banknote,
  Check,
  Leaf,
  Lock,
  MapPin,
  Minus,
  Plus,
  ShieldCheck,
  Store,
  Trash2,
} from '@lucide/vue'
import { formatCurrency, storeCheckoutPath, storefrontUrl } from '@pos/shared/index'
import type { StoreSummary } from '@pos/web/commerce/api'
import { useStorefrontCart, type CartLine } from '@pos/web/commerce/cart'
import { retryStorefrontCatalog, useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { useCheckout } from '@pos/web/commerce/checkout'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { SIZES, srcSet } from '@pos/web/ui/responsiveImg'
import { SHOP_ROOT_DOMAIN } from '@pos/web/commerce/shopDomain'
import ProductArt from '@pos/web/landing/ProductArt.vue'
import CheckoutForm from '@pos/web/cart/CheckoutForm.vue'

// One shop's own checkout, at /shop/<slug>/checkout.
//
// Every shop used to finish on the same Omaykan-dressed /cart page, which
// named the shop in one line of small print. This page is the shop's: its
// photo, its name over the door, its address for pickup, and a way back to its
// own menu — with Omaykan down to "Powered by" at the foot, the way the shop
// page already has it.
//
// It lives on the main site, not the shop's subdomain: sign-in, Google sign-in
// and saved addresses belong to the main origin (see commerce/basketHandoff.ts).
// The order logic is the one useCheckout the cart page used — only the
// dressing and the address are the shop's.

const props = defineProps<{
  slug: string
  store: StoreSummary | null
  directoryFailed: boolean
  /** Lines the cart page left unticked: they stay in the basket, out of this order. */
  skipped: string[]
}>()

const cart = useStorefrontCart()
const catalog = useStorefrontCatalog()
const account = useCustomerAccount()

// ── Who is selling ────────────────────────────────────────────────────────

const shopName = computed(() => catalog.shop?.name || props.store?.name || 'This shop')
const shopKind = computed(() => catalog.shop?.businessTypeLabel || props.store?.businessTypeLabel || '')
const shopAddress = computed(() => catalog.shop?.address || props.store?.address || '')
const shopPhoto = computed(() => catalog.shop?.imageUrl || props.store?.imageUrl || '')
const coverPhoto = computed(() => shopPhoto.value || '/storefront/market-stall-cover.jpg')
const initials = computed(() =>
  shopName.value
    .split(/\s+/)
    .filter((word) => /^[\p{L}\p{N}]/u.test(word))
    .slice(0, 2)
    .map((word) => word[0]!.toUpperCase())
    .join(''),
)

/** The shop's own page: its subdomain where there is one. */
const shopHref = computed(() =>
  storefrontUrl(props.slug, { rootDomain: SHOP_ROOT_DOMAIN, origin: window.location.origin }),
)

// ── What is being ordered ─────────────────────────────────────────────────

/**
 * The basket is this shop's only if it was filled here. One from another shop
 * is never checked out on this page — its order would be posted to this shop,
 * which has none of those products — and the page points to where it belongs.
 */
const ownsBasket = computed(() => {
  const from = cart.cartShop.value
  return from?.orgSlug === props.store?.orgSlug && from?.storeCode === props.store?.storeCode
})
const otherShop = computed(() => (!ownsBasket.value && cart.cartLines.value.length > 0 ? cart.cartShop.value : null))

watch(
  () => catalog.products,
  (products) => {
    if (products.length > 0 && ownsBasket.value) cart.refresh(products)
  },
  { immediate: true },
)

const liveById = computed(() => new Map(catalog.products.map((product) => [product.id, product])))

/** Sold out or off sale since it went in the basket. Unknown while the shelf is out. */
function isGone(line: CartLine): boolean {
  if (catalog.loading || catalog.error || catalog.products.length === 0) return false
  const live = liveById.value.get(line.product.id)
  return !live || !!live.outOfStock
}

const skippedIds = new Set(props.skipped)
const shopLines = computed(() =>
  ownsBasket.value ? cart.cartLines.value.filter((line) => !skippedIds.has(line.product.id)) : [],
)
const goneLines = computed(() => shopLines.value.filter(isGone))
const orderLines = computed(() => shopLines.value.filter((line) => !isGone(line)))
const leftInBasket = computed(() =>
  ownsBasket.value ? cart.cartLines.value.filter((line) => skippedIds.has(line.product.id)).length : 0,
)

const checkout = useCheckout(orderLines)
const totals = checkout.totals

const orderingPaused = computed(() => catalog.shop?.orderingPausedMessage ?? '')

// ── Placing it ────────────────────────────────────────────────────────────

const placedNow = ref(false)

async function placeOrder() {
  if (orderingPaused.value) return
  if (await checkout.placeOrder()) {
    placedNow.value = true
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

watch(
  [shopName, placedNow],
  ([name, placed]) => (document.title = `${placed ? 'Order placed' : 'Checkout'} — ${name}`),
  { immediate: true },
)

function reload() {
  window.location.reload()
}
</script>

<template>
  <div class="landing fd sco">
    <!-- ── The shop's own bar ──────────────────────────────────────────── -->
    <header class="sco-bar">
      <a class="sco-bar__back" :href="store ? shopHref : '/'" :aria-label="store ? `Back to ${shopName}` : 'Back to Omaykan'">
        <ArrowLeft :size="20" :stroke-width="2" />
      </a>
      <a v-if="store" class="sco-bar__shop" :href="shopHref">
        <span class="sco-logo" :class="{ 'sco-logo--photo': shopPhoto }" aria-hidden="true">
          <img v-if="shopPhoto" :src="shopPhoto" alt="" />
          <strong v-else>{{ initials || 'O' }}</strong>
        </span>
        <span class="sco-bar__name">
          <strong>{{ shopName }}</strong>
          <small>{{ shopKind || 'Local seller' }}</small>
        </span>
      </a>
      <p class="sco-bar__secure"><Lock :size="15" :stroke-width="2" /> Secure checkout</p>
    </header>

    <main class="sco-main">
      <!-- ── No such shop ──────────────────────────────────────────────── -->
      <section v-if="!store" class="sco-card sco-state">
        <Store :size="44" :stroke-width="1.4" aria-hidden="true" />
        <template v-if="directoryFailed">
          <h1>We couldn't open this checkout just now</h1>
          <p>Check your connection and try again. Your cart is kept.</p>
          <button type="button" class="sco-btn" @click="reload">Try again</button>
        </template>
        <template v-else>
          <h1>We couldn't find that shop</h1>
          <p>The link may be mistyped, or the shop may no longer be taking orders on Omaykan.</p>
          <a class="sco-btn" href="/#shops">Browse shops</a>
        </template>
      </section>

      <template v-else>
        <section class="sco-hero">
          <!-- The shop's own photo when it has one, the market fallback when
               it does not. Only the fallback has build-time variants; srcSet
               returns nothing for an uploaded /api/ photo. -->
          <img
            class="sco-hero__img"
            :src="coverPhoto"
            :srcset="srcSet(coverPhoto)"
            :sizes="SIZES.full"
            alt=""
          />
          <div class="sco-hero__shade" aria-hidden="true" />
          <div class="sco-hero__copy">
            <p class="sco-hero__eyebrow">{{ placedNow ? 'Thank you' : 'Checkout' }}</p>
            <h1 class="sco-hero__title">
              {{ placedNow ? `Your order is with ${shopName}` : `Your order from ${shopName}` }}
            </h1>
            <p v-if="shopAddress" class="sco-hero__place"><MapPin :size="15" :stroke-width="2.2" /> {{ shopAddress }}</p>
          </div>
        </section>

        <div class="sco-wrap">
          <!-- ── Placed ──────────────────────────────────────────────────── -->
          <section v-if="placedNow && checkout.placed.value" class="sco-card sco-done">
            <div class="sco-done__mark" aria-hidden="true"><Check :size="30" :stroke-width="3" /></div>
            <h2>Order {{ checkout.placed.value.ticketNumber }} is in</h2>
            <p class="sco-done__note">
              {{ shopName }} is looking at it now. We'll reach you on the
              {{ checkout.placed.value.reachBy }} you gave us.
            </p>

            <ul class="sco-done__lines">
              <li v-for="line in checkout.placed.value.lines" :key="line.product.id">
                <span>{{ line.quantity }} × {{ line.product.name }}</span>
                <span>{{ formatCurrency(line.product.priceCents * line.quantity) }}</span>
              </li>
            </ul>

            <dl class="sco-rows">
              <div v-if="checkout.placed.value.deliveryFeeCents">
                <dt>Delivery</dt>
                <dd>{{ formatCurrency(checkout.placed.value.deliveryFeeCents) }}</dd>
              </div>
              <div class="sco-rows__total">
                <dt>Paid on {{ checkout.placed.value.method === 'delivery' ? 'delivery' : 'pickup' }}</dt>
                <dd>{{ formatCurrency(checkout.placed.value.totalCents) }}</dd>
              </div>
            </dl>

            <div class="sco-actions">
              <a href="/account?section=orders" class="sco-btn">Track your order <ArrowRight :size="16" :stroke-width="2" /></a>
              <a :href="shopHref" class="sco-btn sco-btn--ghost">Back to {{ shopName }}</a>
            </div>
          </section>

          <!-- ── Nothing from this shop to check out ─────────────────────── -->
          <section v-else-if="orderLines.length === 0 && goneLines.length === 0" class="sco-card sco-state">
            <Leaf :size="40" :stroke-width="1.5" aria-hidden="true" />
            <template v-if="otherShop">
              <h2>Your cart is from another shop</h2>
              <p>
                It has items from <strong>{{ otherShop.name || 'another shop' }}</strong>, and an order can
                only come from one shop.
              </p>
              <div class="sco-actions">
                <a class="sco-btn" :href="storeCheckoutPath(otherShop.orgSlug)">Check out from {{ otherShop.name || 'that shop' }}</a>
                <a class="sco-btn sco-btn--ghost" :href="shopHref">Shop {{ shopName }}</a>
              </div>
            </template>
            <template v-else>
              <h2>Nothing from {{ shopName }} in your cart yet</h2>
              <p v-if="leftInBasket > 0">Everything in your cart was left out of this order. Tick what you want on the cart page.</p>
              <p v-else>Add something from the menu and it will be waiting here.</p>
              <div class="sco-actions">
                <a class="sco-btn" :href="shopHref">See the menu</a>
                <a v-if="leftInBasket > 0" class="sco-btn sco-btn--ghost" href="/cart">Back to your cart</a>
              </div>
            </template>
          </section>

          <!-- ── Checkout ────────────────────────────────────────────────── -->
          <div v-else class="sco-grid">
            <div class="sco-col">
              <p v-if="catalog.closed" class="sco-note sco-note--warn" role="status">
                {{ shopName }} isn't taking orders right now. Your cart is kept.
              </p>
              <p v-else-if="orderingPaused" class="sco-note sco-note--warn" role="status">
                <strong>{{ orderingPaused }}</strong> Your cart is kept until then.
              </p>

              <!-- Still trading a stored token for the account: neither the
                   form nor the sign-in wall is the right thing to show yet. -->
              <section v-if="account.hydrating.value" class="sco-card sco-state sco-state--quiet" aria-busy="true">
                <p>One moment…</p>
              </section>
              <section v-else-if="checkout.checkoutGated.value" class="sco-card sco-gate">
                <ShieldCheck :size="30" :stroke-width="1.7" aria-hidden="true" />
                <h2>Sign in to order from {{ shopName }}</h2>
                <p>
                  Orders are placed from an account, so your addresses and order history stay with
                  you. Your cart is kept while you sign in, and you'll come straight back here.
                </p>
                <a class="sco-btn" :href="checkout.signInHref.value">Sign in to check out <ArrowRight :size="16" :stroke-width="2" /></a>
              </section>
              <CheckoutForm v-else :checkout="checkout" />
            </div>

            <aside class="sco-side">
              <section class="sco-card sco-summary" aria-labelledby="sco-summary-title">
                <header class="sco-summary__head">
                  <span class="sco-logo sco-logo--small" :class="{ 'sco-logo--photo': shopPhoto }" aria-hidden="true">
                    <img v-if="shopPhoto" :src="shopPhoto" alt="" />
                    <strong v-else>{{ initials || 'O' }}</strong>
                  </span>
                  <div>
                    <h2 id="sco-summary-title">Your order</h2>
                    <a :href="shopHref">from {{ shopName }}</a>
                  </div>
                </header>

                <ul class="sco-lines">
                  <li v-for="line in orderLines" :key="line.product.id" class="sco-line">
                    <span class="sco-line__thumb">
                      <ProductArt :product="line.product" :merchant-image-url="shopPhoto" :size="20" />
                    </span>
                    <span class="sco-line__info">
                      <strong>{{ line.product.name }}</strong>
                      <small>{{ formatCurrency(line.product.priceCents) }}<template v-if="line.product.unitLabel"> · {{ line.product.unitLabel }}</template></small>
                      <span class="sco-step" role="group" :aria-label="`Quantity of ${line.product.name}`">
                        <button
                          type="button"
                          :aria-label="line.quantity === 1 ? `Remove ${line.product.name}` : `One fewer ${line.product.name}`"
                          @click="cart.decrement(line.product.id)"
                        >
                          <Trash2 v-if="line.quantity === 1" :size="13" :stroke-width="2" />
                          <Minus v-else :size="13" :stroke-width="2.4" />
                        </button>
                        <span aria-live="polite">{{ line.quantity }}</span>
                        <button type="button" :aria-label="`One more ${line.product.name}`" @click="cart.add(line.product)">
                          <Plus :size="13" :stroke-width="2.4" />
                        </button>
                      </span>
                    </span>
                    <b>{{ formatCurrency(line.product.priceCents * line.quantity) }}</b>
                  </li>
                  <li v-for="line in goneLines" :key="line.product.id" class="sco-line sco-line--gone">
                    <span class="sco-line__thumb">
                      <ProductArt :product="line.product" :merchant-image-url="shopPhoto" :size="20" />
                    </span>
                    <span class="sco-line__info">
                      <strong>{{ line.product.name }}</strong>
                      <small>Not on the shelf right now — left out of this order</small>
                    </span>
                    <button type="button" class="sco-linkbtn" @click="cart.remove(line.product.id)">Remove</button>
                  </li>
                </ul>

                <p v-if="catalog.error" class="sco-note">
                  We couldn't check today's shelf. {{ shopName }} confirms every line when it accepts the order.
                  <button type="button" class="sco-linkbtn" @click="retryStorefrontCatalog()">Check again</button>
                </p>

                <dl class="sco-rows">
                  <div>
                    <dt>Subtotal ({{ totals.itemCount }} item{{ totals.itemCount === 1 ? '' : 's' }})</dt>
                    <dd>{{ formatCurrency(totals.subtotalCents) }}</dd>
                  </div>
                  <div v-if="checkout.appliedPromo.value" class="sco-rows__promo">
                    <dt>
                      {{ checkout.appliedPromo.value.code }} · {{ checkout.appliedPromo.value.description }}
                      <button type="button" class="sco-linkbtn" @click="checkout.removePromo()">Remove</button>
                    </dt>
                    <dd>−{{ formatCurrency(totals.discountCents) }}</dd>
                  </div>
                  <div v-if="totals.taxCents > 0">
                    <dt>VAT</dt>
                    <dd>{{ formatCurrency(totals.taxCents) }}</dd>
                  </div>
                  <div v-if="checkout.isDelivery.value">
                    <dt>Delivery{{ checkout.deliveryQuote.value ? '' : ' (estimate)' }}</dt>
                    <dd>{{ formatCurrency(checkout.deliveryFeeCents.value) }}</dd>
                  </div>
                  <div v-else>
                    <dt>Pickup at {{ shopName }}</dt>
                    <dd>Free</dd>
                  </div>
                  <div class="sco-rows__pay">
                    <dt><Banknote :size="16" :stroke-width="1.8" /> How you pay</dt>
                    <dd class="sco-rows__soft">Cash or GCash on arrival</dd>
                  </div>
                  <div class="sco-rows__total">
                    <dt>Total</dt>
                    <dd>{{ formatCurrency(checkout.grandTotalCents.value) }}</dd>
                  </div>
                </dl>

                <!-- Asked of the server before anything is promised. -->
                <form v-if="!checkout.appliedPromo.value && !checkout.checkoutGated.value" class="sco-promo" @submit.prevent="checkout.applyPromo()">
                  <input
                    v-model="checkout.promoInput.value"
                    maxlength="40"
                    autocapitalize="characters"
                    placeholder="Promo code"
                    aria-label="Promo code"
                  />
                  <button type="submit" :disabled="checkout.promoChecking.value || checkout.promoInput.value.trim() === ''">
                    {{ checkout.promoChecking.value ? 'Checking…' : 'Apply' }}
                  </button>
                </form>
                <p v-if="checkout.promoMessage.value" class="sco-promo__message" role="alert">{{ checkout.promoMessage.value }}</p>

                <p v-if="!checkout.canOrderOnline.value" class="sco-note sco-note--warn">{{ shopName }} doesn't take online orders yet.</p>
                <p v-if="checkout.error.value" class="sco-error">{{ checkout.error.value }}</p>

                <a
                  v-if="checkout.needsSignIn.value || checkout.checkoutGated.value"
                  :href="checkout.signInHref.value"
                  class="sco-btn sco-btn--block"
                >
                  Sign in to finish
                </a>
                <button
                  v-else
                  type="button"
                  class="sco-btn sco-btn--block"
                  :disabled="!checkout.canSubmit.value || checkout.submitting.value || orderingPaused !== '' || catalog.closed || account.hydrating.value"
                  @click="placeOrder"
                >
                  {{ checkout.submitting.value ? 'Placing your order…' : `Place order — ${formatCurrency(checkout.grandTotalCents.value)}` }}
                </button>
                <p class="sco-fine">{{ shopName }} confirms prices and the delivery fee when it accepts the order.</p>
                <p v-if="leftInBasket > 0" class="sco-fine">
                  {{ leftInBasket }} item{{ leftInBasket === 1 ? '' : 's' }} you left unticked stay{{ leftInBasket === 1 ? 's' : '' }} in your cart.
                </p>
                <p class="sco-secure"><Lock :size="13" :stroke-width="2" /> Nothing is charged online — you pay when your order arrives.</p>
              </section>
            </aside>
          </div>
        </div>
      </template>
    </main>

    <footer class="sco-foot">
      <p v-if="store"><strong>{{ shopName }}</strong><span>{{ shopKind || 'Local seller' }}</span></p>
      <p class="sco-foot__powered">
        Powered by <a href="/"><img src="/logo-wordmark.png" alt="Omaykan" /></a>
      </p>
    </footer>
  </div>
</template>

<style scoped>
/* The shop page's own palette (shop/store-menu.css): its deep green sign and
   its amber buttons, so the checkout reads as the same shop, one step on.
   --sf-clay is re-pointed too, which is what CheckoutForm's toggles and focus
   rings are drawn in. */
.sco {
  --sco-brand: #164b34;
  --sco-brand-ink: #f6e8b8;
  --sco-accent: #b8620c;
  --sco-accent-deep: #96500a;
  --sco-wash: #fbf1e3;
  --sf-clay: var(--sco-accent);
  --sf-clay-deep: var(--sco-accent-deep);

  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f7f6f2;
  color: #171d19;
  font-family: var(--sf-sans, Inter, ui-sans-serif, system-ui, sans-serif);
}

/* ── Bar ────────────────────────────────────────────────────────────── */

.sco-bar {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px var(--fd-gutter);
  border-bottom: 1px solid #e8e4db;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
}

.sco-bar__back {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 1px solid #e3ded4;
  border-radius: 50%;
  color: #16231c;
}
.sco-bar__back:hover { border-color: var(--sco-brand); color: var(--sco-brand); }

.sco-bar__shop { display: flex; min-width: 0; align-items: center; gap: 11px; color: inherit; text-decoration: none; }
.sco-bar__name { display: grid; min-width: 0; }
.sco-bar__name strong {
  overflow: hidden;
  font-family: var(--sf-serif);
  font-size: 18px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sco-bar__name small { color: #676e69; font-size: 12.5px; }

.sco-bar__secure {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  margin: 0 0 0 auto;
  color: #17623c;
  font-size: 13px;
  font-weight: 700;
}

.sco-logo {
  display: grid;
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  place-items: center;
  overflow: hidden;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--sco-brand);
  color: var(--sco-brand-ink);
  box-shadow: 0 2px 8px rgba(28, 38, 30, 0.2);
}
.sco-logo strong { font-family: var(--sf-script, cursive); font-size: 19px; line-height: 1; }
.sco-logo img { width: 100%; height: 100%; object-fit: cover; }
.sco-logo--small { width: 38px; height: 38px; }
.sco-logo--small strong { font-size: 16px; }

/* ── Hero ───────────────────────────────────────────────────────────── */

.sco-hero {
  position: relative;
  display: flex;
  min-height: 170px;
  align-items: flex-end;
  overflow: hidden;
  background: var(--sco-brand);
  color: #fff;
}
.sco-hero__img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center 54%; }
.sco-hero__shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(0deg, rgba(14, 42, 29, 0.92) 0%, rgba(14, 42, 29, 0.6) 55%, rgba(14, 42, 29, 0.25) 100%);
}
.sco-hero__copy { position: relative; width: min(100%, 1180px); margin: 0 auto; padding: 34px var(--fd-gutter) 26px; }
.sco-hero__eyebrow {
  margin: 0 0 4px;
  color: var(--sco-brand-ink);
  font-family: var(--sf-script, cursive);
  font-size: 24px;
  line-height: 1;
}
.sco-hero__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: clamp(1.6rem, 3.4vw, 2.5rem);
  line-height: 1.12;
  overflow-wrap: anywhere;
}
.sco-hero__place {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

/* ── Layout ─────────────────────────────────────────────────────────── */

.sco-main { flex: 1 0 auto; padding-bottom: 56px; }
.sco-wrap { width: min(100%, 1180px); margin: 0 auto; padding: 26px var(--fd-gutter) 0; }

.sco-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 26px;
  align-items: start;
}
.sco-col { display: grid; min-width: 0; gap: 16px; }
.sco-side { position: sticky; top: 84px; }

.sco-card {
  padding: 22px;
  border: 1px solid #e8e4db;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 3px 12px rgba(47, 44, 36, 0.05);
}

/* ── States ─────────────────────────────────────────────────────────── */

.sco-state {
  display: grid;
  max-width: 560px;
  justify-items: center;
  gap: 4px;
  margin: 28px auto 0;
  padding: 40px 28px;
  color: var(--sco-brand);
  text-align: center;
}
.sco-state h1,
.sco-state h2 { margin: 10px 0 4px; color: #171d19; font-family: var(--sf-serif); font-size: 24px; }
.sco-state p { max-width: 42ch; margin: 0; color: #676e69; font-size: 15px; line-height: 1.6; }
.sco-state--quiet { margin: 0; max-width: none; padding: 28px; }

.sco-gate { display: grid; justify-items: start; gap: 8px; color: var(--sco-brand); }
.sco-gate h2 { margin: 4px 0 0; color: #171d19; font-family: var(--sf-serif); font-size: 21px; }
.sco-gate p { margin: 0 0 8px; color: #676e69; font-size: 14.5px; line-height: 1.6; }

.sco-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin-top: 20px; }

/* ── Summary ────────────────────────────────────────────────────────── */

.sco-summary__head { display: flex; align-items: center; gap: 11px; padding-bottom: 14px; border-bottom: 1px solid #efebe4; }
.sco-summary__head h2 { margin: 0; font-family: var(--sf-serif); font-size: 19px; }
.sco-summary__head a { color: #676e69; font-size: 13px; }
.sco-summary__head a:hover { color: var(--sco-accent); text-decoration: underline; text-underline-offset: 3px; }

.sco-lines { display: grid; gap: 12px; margin: 0; padding: 14px 0; border-bottom: 1px solid #efebe4; list-style: none; }
.sco-line { display: grid; grid-template-columns: 48px minmax(0, 1fr) auto; align-items: start; gap: 11px; }
.sco-line__thumb { position: relative; display: grid; width: 48px; height: 48px; place-items: center; overflow: hidden; border-radius: 9px; background: #f1efe9; }
.sco-line__info { display: grid; min-width: 0; gap: 2px; }
.sco-line__info strong { font-size: 14px; line-height: 1.3; }
.sco-line__info small { color: #676e69; font-size: 12.5px; }
.sco-line b { font-size: 14px; }
.sco-line--gone .sco-line__thumb,
.sco-line--gone strong { opacity: 0.5; }
.sco-line--gone small { color: #9a3b2a; }

.sco-step {
  display: inline-flex;
  width: max-content;
  align-items: center;
  margin-top: 5px;
  border: 1px solid #e3ded4;
  border-radius: 999px;
}
.sco-step button {
  display: grid;
  width: 30px;
  height: 28px;
  place-items: center;
  border: 0;
  background: none;
  color: #273029;
  cursor: pointer;
}
.sco-step button:hover { color: var(--sco-accent); }
.sco-step span { min-width: 22px; font-size: 13px; font-weight: 800; text-align: center; }

.sco-rows { margin: 10px 0 0; }
.sco-rows > div { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; padding: 6px 0; font-size: 14px; }
.sco-rows dt { color: #676e69; }
.sco-rows dd { margin: 0; color: #171d19; font-weight: 650; text-align: right; }
.sco-rows__soft { color: #676e69 !important; font-size: 13px; font-weight: 500 !important; }
.sco-rows__pay { margin-top: 4px; padding-top: 10px !important; border-top: 1px solid #efebe4; }
.sco-rows__pay dt { display: inline-flex; align-items: center; gap: 7px; white-space: nowrap; }
.sco-rows__total { margin-top: 6px; padding-top: 13px !important; border-top: 1px solid #efebe4; }
.sco-rows__total dt { color: #171d19; font-size: 16px; font-weight: 700; }
.sco-rows__total dd { font-size: 22px; font-weight: 850; }
.sco-rows__promo dt,
.sco-rows__promo dd { color: #1f6b3c; }

.sco-promo { display: flex; gap: 8px; margin-top: 10px; }
.sco-promo input {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #e3ded4;
  border-radius: 10px;
  font: inherit;
  font-size: 14px;
  text-transform: uppercase;
}
.sco-promo input::placeholder { text-transform: none; }
.sco-promo input:focus { outline: none; border-color: var(--sco-accent); box-shadow: 0 0 0 3px rgba(184, 98, 12, 0.15); }
.sco-promo button {
  padding: 10px 16px;
  border: 0;
  border-radius: 10px;
  background: var(--sco-brand);
  color: #fff;
  font: inherit;
  font-weight: 750;
  cursor: pointer;
}
.sco-promo button:disabled { opacity: 0.55; cursor: default; }
.sco-promo__message { margin: 6px 0 0; color: #9a3412; font-size: 13px; }

/* ── Buttons and notes ──────────────────────────────────────────────── */

.sco-btn {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 0 20px;
  border: 0;
  border-radius: 10px;
  background: var(--sco-accent);
  color: #fff;
  font: inherit;
  font-size: 15px;
  font-weight: 750;
  text-decoration: none;
  cursor: pointer;
  transition: background 150ms;
}
.sco-btn:hover:not(:disabled) { background: var(--sco-accent-deep); }
.sco-btn:focus-visible { outline: 2px solid var(--sco-brand); outline-offset: 3px; }
.sco-btn:disabled { background: #e2ded6; color: #8f8980; cursor: not-allowed; }
.sco-btn--ghost { border: 1px solid #d8d3c9; background: #fff; color: #273029; }
.sco-btn--ghost:hover:not(:disabled) { border-color: var(--sco-accent); background: #fff; color: var(--sco-accent); }
.sco-btn--block { display: flex; width: 100%; margin-top: 16px; }

.sco-linkbtn {
  padding: 0;
  border: 0;
  background: none;
  color: var(--sco-accent);
  font: inherit;
  font-size: 12.5px;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.sco-note { margin: 12px 0 0; color: #676e69; font-size: 13px; line-height: 1.5; }
.sco-col > .sco-note { margin: 0; }
.sco-note--warn { padding: 11px 14px; border-radius: 10px; background: var(--sco-wash); color: #8a4a09; font-weight: 600; }
.sco-error { margin: 12px 0 0; padding: 9px 12px; border-radius: 8px; background: #fbeae4; color: #9a3b2a; font-size: 13px; font-weight: 600; }
.sco-fine { margin: 10px 0 0; color: #8a8f8b; font-size: 12.5px; line-height: 1.5; text-align: center; }
.sco-secure { display: flex; align-items: center; justify-content: center; gap: 6px; margin: 12px 0 0; color: #676e69; font-size: 12px; text-align: center; }

/* ── Placed ─────────────────────────────────────────────────────────── */

.sco-done { max-width: 560px; margin: 0 auto; padding: 36px 30px; text-align: center; }
.sco-done h2 { margin: 0 0 8px; font-family: var(--sf-serif); font-size: 25px; }
.sco-done__note { max-width: 40ch; margin: 0 auto; color: #676e69; font-size: 15px; line-height: 1.6; }
.sco-done__mark {
  display: grid;
  width: 66px;
  height: 66px;
  place-items: center;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: #e4efe4;
  color: #2f7a4a;
}
.sco-done__lines { margin: 22px 0 0; padding: 14px 0 0; border-top: 1px solid #efebe4; list-style: none; text-align: left; }
.sco-done__lines li { display: flex; justify-content: space-between; gap: 16px; padding: 3px 0; color: #676e69; font-size: 14px; }
.sco-done .sco-rows { text-align: left; }

/* ── Foot ───────────────────────────────────────────────────────────── */

.sco-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px var(--fd-gutter);
  border-top: 1px solid #e8e4db;
  background: #fff;
  font-size: 13px;
}
.sco-foot p { display: flex; align-items: center; gap: 8px; margin: 0; }
.sco-foot span { color: #676e69; }
.sco-foot__powered { margin-left: auto !important; color: #676e69; }
.sco-foot__powered img { display: block; height: 18px; width: auto; }

/* ── Narrower screens ───────────────────────────────────────────────── */

@media (max-width: 920px) {
  .sco-grid { grid-template-columns: 1fr; }
  .sco-side { position: static; }
}

@media (max-width: 560px) {
  .sco-bar { gap: 10px; }
  .sco-bar__secure { font-size: 0; gap: 0; }
  .sco-hero { min-height: 140px; }
  .sco-hero__copy { padding: 26px var(--fd-gutter) 20px; }
  .sco-card { padding: 18px 16px; }
  .sco-done { padding: 30px 18px; }
}
</style>
