<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { Minus, Plus, ShoppingBasket, Trash2, X } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { useDeliveryAddress } from '@pos/web/commerce/deliveryAddress'

// The cart, at last given somewhere to be looked at.
//
// It has collected items since the storefront was rebuilt but had no surface
// of its own — the header's basket pointed at the shelves, so the only way to
// see what was in it was the little count on the icon. This is that surface,
// and the way to checkout.
//
// It lives in the header rather than on the landing page so it opens the same
// way from /about and /account, neither of which has any shelves to fall back
// to. Checkout itself is a face of the landing page, so off that page the
// button below is a real navigation home — same split the category nav makes.

const props = defineProps<{ open: boolean; checkoutInPage: boolean }>()

const emit = defineEmits<{ close: []; checkout: [] }>()

const cart = useStorefrontCart()
const catalog = useStorefrontCatalog()
const delivery = useDeliveryAddress()

const lines = computed(() => cart.cartLines.value)
const empty = computed(() => lines.value.length === 0)

/**
 * What delivery would add. The exact figure is only known once an address is
 * set — before that this is the flat starting fee, and the API is the one
 * quoting even that number rather than the client keeping its own copy.
 */
const deliveryFeeCents = computed(
  () => catalog.delivery.stores[0]?.feeCents ?? catalog.delivery.baseFeeCents,
)

const feeIsExact = computed(() => catalog.delivery.stores[0]?.feeCents != null)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) document.addEventListener('keydown', onKeydown)
    else document.removeEventListener('keydown', onKeydown)
    // The page behind a full-height sheet must not scroll under it.
    document.body.style.overflow = isOpen ? 'hidden' : ''
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

function goToCheckout() {
  if (props.checkoutInPage) {
    emit('checkout')
    emit('close')
    return
  }
  window.location.href = '/?checkout=1'
}
</script>

<template>
  <Teleport to="body">
    <div v-if="props.open" class="fdcart" role="dialog" aria-modal="true" aria-label="Your cart">
      <div class="fdcart__scrim" @click="emit('close')" />

      <aside class="fdcart__sheet">
        <header class="fdcart__head">
          <p class="fdcart__title">
            Your cart
            <span v-if="cart.itemCount.value > 0" class="fdcart__count">{{ cart.itemCount.value }}</span>
          </p>
          <button type="button" class="fdcart__close" aria-label="Close cart" @click="emit('close')">
            <X :size="19" :stroke-width="2" />
          </button>
        </header>

        <div v-if="empty" class="fdcart__empty">
          <ShoppingBasket :size="34" :stroke-width="1.4" />
          <p class="fdcart__empty-title">Nothing in here yet</p>
          <p class="fdcart__empty-note">
            Add something from the shelves and it will wait for you here.
          </p>
          <button type="button" class="fdcart__keep" @click="emit('close')">Start shopping</button>
        </div>

        <template v-else>
          <ul class="fdcart__lines">
            <li v-for="line in lines" :key="line.product.id" class="fdcart__line">
              <div class="fdcart__art">
                <img
                  v-if="line.product.imageUrl"
                  :src="line.product.imageUrl"
                  :alt="line.product.name"
                  loading="lazy"
                />
                <span v-else aria-hidden="true">🛒</span>
              </div>

              <div class="fdcart__body">
                <p class="fdcart__name">{{ line.product.name }}</p>
                <p class="fdcart__unit">
                  {{ formatCurrency(line.product.priceCents) }}
                  <template v-if="line.product.unitLabel"> · {{ line.product.unitLabel }}</template>
                  <!-- Which counter it comes off, once more than one can. -->
                  <template v-if="line.product.storeName && catalog.delivery.stores.length > 1">
                    · {{ line.product.storeName }}
                  </template>
                </p>

                <div class="fdcart__stepper">
                  <button
                    type="button"
                    :aria-label="`One less ${line.product.name}`"
                    @click="cart.decrement(line.product.id)"
                  >
                    <Minus :size="14" :stroke-width="2.4" />
                  </button>
                  <span>{{ line.quantity }}</span>
                  <button
                    type="button"
                    :aria-label="`One more ${line.product.name}`"
                    @click="cart.add(line.product)"
                  >
                    <Plus :size="14" :stroke-width="2.4" />
                  </button>

                  <button
                    type="button"
                    class="fdcart__remove"
                    :aria-label="`Remove ${line.product.name}`"
                    @click="cart.remove(line.product.id)"
                  >
                    <Trash2 :size="15" :stroke-width="1.8" />
                  </button>
                </div>
              </div>

              <p class="fdcart__amount">
                {{ formatCurrency(line.product.priceCents * line.quantity) }}
              </p>
            </li>
          </ul>

          <footer class="fdcart__foot">
            <dl class="fdcart__totals">
              <div>
                <dt>Subtotal</dt>
                <dd>{{ formatCurrency(cart.subtotalCents.value) }}</dd>
              </div>
              <div>
                <dt>VAT</dt>
                <dd>{{ formatCurrency(cart.taxCents.value) }}</dd>
              </div>
              <div class="fdcart__totals-note">
                <dt>Delivery</dt>
                <dd>{{ feeIsExact ? '' : 'from ' }}{{ formatCurrency(deliveryFeeCents) }}</dd>
              </div>
              <div class="fdcart__grand">
                <dt>Total</dt>
                <dd>{{ formatCurrency(cart.totalCents.value) }}</dd>
              </div>
            </dl>

            <p class="fdcart__fine">
              <template v-if="delivery.hasAddress.value">
                Delivering to {{ delivery.label.value }} — the fee is added at checkout, and pickup
                is free.
              </template>
              <template v-else>
                Delivery is added at checkout once you set an address; pickup is free.
              </template>
              Cash on arrival.
            </p>

            <button type="button" class="fdcart__go" @click="goToCheckout">
              Checkout · {{ formatCurrency(cart.totalCents.value) }}
            </button>
            <button type="button" class="fdcart__keep" @click="emit('close')">Keep shopping</button>
          </footer>
        </template>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.fdcart { position: fixed; inset: 0; z-index: 400; }

.fdcart__scrim {
  position: absolute;
  inset: 0;
  background: rgba(6, 36, 15, 0.45);
}

.fdcart__sheet {
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  width: 408px;
  max-width: 100vw;
  height: 100%;
  background: #fff;
  color: #1a1a1a;
  box-shadow: -16px 0 44px rgba(6, 36, 15, 0.22);
}

.fdcart__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 18px 20px;
  border-bottom: 1px solid #ecefed;
}

.fdcart__title { display: flex; align-items: center; gap: 9px; margin: 0; font-size: 17px; font-weight: 800; }

.fdcart__count {
  display: grid;
  place-items: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: #bbf451;
  color: #06240f;
  font-size: 12px;
  font-weight: 800;
}

.fdcart__close { border: none; background: none; padding: 2px; color: #6b7280; cursor: pointer; }

/* -- Empty ---------------------------------------------------------------- */

.fdcart__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 30px;
  text-align: center;
  color: #6b7280;
}

.fdcart__empty-title { margin: 8px 0 0; color: #1a1a1a; font-size: 15.5px; font-weight: 700; }
.fdcart__empty-note { margin: 0; font-size: 13.5px; line-height: 1.5; max-width: 260px; }

/* -- Lines ---------------------------------------------------------------- */

.fdcart__lines {
  flex: 1;
  overflow-y: auto;
  margin: 0;
  padding: 6px 20px;
  list-style: none;
}

.fdcart__line {
  display: grid;
  grid-template-columns: 56px 1fr auto;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f1f4f2;
}

.fdcart__art {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 8px;
  background: #f7f8f7;
  overflow: hidden;
  font-size: 20px;
}

.fdcart__art img { width: 100%; height: 100%; object-fit: cover; }

.fdcart__body { min-width: 0; }
.fdcart__name { margin: 0; font-size: 14px; font-weight: 700; line-height: 1.35; }
.fdcart__unit { margin: 2px 0 0; color: #6b7280; font-size: 12.5px; }

.fdcart__stepper { display: flex; align-items: center; gap: 6px; margin-top: 9px; }

.fdcart__stepper button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid #d7ded9;
  border-radius: 7px;
  background: #fff;
  color: #1a6b3c;
  cursor: pointer;
}

.fdcart__stepper button:hover { border-color: #1a6b3c; background: #f2f9f4; }
.fdcart__stepper span { min-width: 20px; text-align: center; font-size: 13.5px; font-weight: 700; }

.fdcart__remove { margin-left: 4px; color: #9aa3a0 !important; }
.fdcart__remove:hover { color: #b3261e !important; border-color: #e5b7b3 !important; background: #fdf0ef !important; }

.fdcart__amount { margin: 0; font-size: 14px; font-weight: 800; white-space: nowrap; }

/* -- Foot ----------------------------------------------------------------- */

.fdcart__foot { padding: 16px 20px 20px; border-top: 1px solid #ecefed; }

.fdcart__totals { margin: 0 0 12px; }
.fdcart__totals > div { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.fdcart__totals dt { color: #4a5b52; font-size: 13.5px; }
.fdcart__totals dd { margin: 0; font-size: 13.5px; font-weight: 600; }
.fdcart__totals > div + div { margin-top: 5px; }

.fdcart__grand { margin-top: 10px !important; padding-top: 10px; border-top: 1px solid #ecefed; }
.fdcart__grand dt { font-size: 15px !important; font-weight: 800; color: #1a1a1a !important; }
.fdcart__grand dd { font-size: 17px !important; font-weight: 800; }

.fdcart__fine { margin: 0 0 13px; color: #6b7280; font-size: 12px; line-height: 1.45; }

.fdcart__go {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: 999px;
  background: #22c55e;
  color: #06240f;
  font: 800 15px/1 inherit;
  cursor: pointer;
}

.fdcart__go:hover { background: #16a34a; color: #fff; }

.fdcart__keep {
  width: 100%;
  height: 40px;
  margin-top: 8px;
  border: none;
  background: none;
  color: #1a6b3c;
  font: 700 13.5px/1 inherit;
  text-decoration: underline;
  cursor: pointer;
}

@media (max-width: 480px) {
  .fdcart__sheet { width: 100%; }
}
</style>
