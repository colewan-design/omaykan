<script setup lang="ts">
import { computed } from 'vue'
import { Minus, Plus, ShoppingCart, Trash2 } from '@lucide/vue'
import { formatCurrency, type Product } from '@pos/shared/index'
import { totalsFor, type CartLine } from '@pos/web/commerce/cart'
import ProductArt from '@pos/web/landing/ProductArt.vue'

// The basket beside a shop's menu: what is in it, what it comes to, and the
// way to checkout. The same lines the /cart page shows — this is a view onto
// the one basket, not a second one.
//
// The delivery fee is an estimate and says so. The server quotes it again from
// the shop's pin when the order goes in, and that is the figure charged; see
// commerce/delivery.ts.

const props = defineProps<{
  lines: CartLine[]
  merchantImageUrl?: string
  /** Null when there is no pin to measure from, and the fee can only be "from". */
  deliveryFeeCents: number | null
  /** The base fee, for "from ₱49" when the distance is unknown. */
  baseFeeCents: number
  /** False once the customer's pin is past the delivery radius. */
  deliverable: boolean
}>()

const emit = defineEmits<{
  add: [product: Product]
  decrement: [productId: string]
  remove: [productId: string]
  clear: []
  checkout: []
  setAddress: []
}>()

const totals = computed(() => totalsFor(props.lines))

/** Only a fee we could actually quote goes into the total; "from ₱49" does not. */
const feeInTotal = computed(() => props.deliverable && props.deliveryFeeCents !== null)

const total = computed(
  () => totals.value.totalCents + (feeInTotal.value ? (props.deliveryFeeCents ?? 0) : 0),
)
</script>

<template>
  <section class="sc" aria-labelledby="sc-title">
    <header class="sc__head">
      <ShoppingCart :size="22" :stroke-width="2" class="sc__icon" aria-hidden="true" />
      <h2 id="sc-title" class="sc__title">
        Your cart <span class="sc__count">({{ totals.itemCount }})</span>
      </h2>
      <button v-if="lines.length > 0" type="button" class="sc__clear" @click="emit('clear')">
        Clear all
      </button>
    </header>

    <p v-if="lines.length === 0" class="sc__empty">
      Nothing here yet. Tap <strong>Add</strong> on anything in the menu.
    </p>

    <ul v-else class="sc__lines">
      <li v-for="line in lines" :key="line.product.id" class="sc__line">
        <div class="sc__thumb">
          <ProductArt :product="line.product" :merchant-image-url="merchantImageUrl" :size="24" />
        </div>
        <div class="sc__line-body">
          <p class="sc__name">{{ line.product.name }}</p>
          <div class="sc__line-foot">
            <span class="sc__price">{{ formatCurrency(line.product.priceCents * line.quantity) }}</span>
            <div class="sc__step" role="group" :aria-label="`${line.product.name} quantity`">
              <button
                type="button"
                class="sc__step-btn"
                :aria-label="`Remove one ${line.product.name}`"
                @click="emit('decrement', line.product.id)"
              >
                <Minus :size="14" :stroke-width="2.4" />
              </button>
              <span class="sc__qty">{{ line.quantity }}</span>
              <button
                type="button"
                class="sc__step-btn"
                :aria-label="`Add one more ${line.product.name}`"
                @click="emit('add', line.product)"
              >
                <Plus :size="14" :stroke-width="2.4" />
              </button>
            </div>
          </div>
        </div>
        <button
          type="button"
          class="sc__remove"
          :aria-label="`Remove ${line.product.name} from cart`"
          @click="emit('remove', line.product.id)"
        >
          <Trash2 :size="17" :stroke-width="1.8" />
        </button>
      </li>
    </ul>

    <template v-if="lines.length > 0">
      <dl class="sc__sums">
        <div class="sc__row">
          <dt>Subtotal</dt>
          <dd>{{ formatCurrency(totals.subtotalCents) }}</dd>
        </div>
        <div v-if="totals.taxCents > 0" class="sc__row">
          <dt>VAT</dt>
          <dd>{{ formatCurrency(totals.taxCents) }}</dd>
        </div>
        <div class="sc__row">
          <dt>
            Delivery fee
            <button
              v-if="deliveryFeeCents === null"
              type="button"
              class="sc__hint"
              @click="emit('setAddress')"
            >
              Set address
            </button>
          </dt>
          <dd v-if="!deliverable" class="sc__muted">Pickup only</dd>
          <dd v-else-if="deliveryFeeCents === null">from {{ formatCurrency(baseFeeCents) }}</dd>
          <dd v-else>{{ formatCurrency(deliveryFeeCents) }}</dd>
        </div>
        <div class="sc__row sc__row--total">
          <dt>
            Total
            <span v-if="!feeInTotal" class="sc__sub">{{ deliverable ? 'before delivery' : 'for pickup' }}</span>
          </dt>
          <dd>{{ formatCurrency(total) }}</dd>
        </div>
      </dl>
      <p v-if="!deliverable" class="sc__note">
        Your address is past this shop's delivery area. You can still order for pickup.
      </p>
      <p v-else class="sc__note">
        Choose delivery or pickup at checkout. The delivery fee is confirmed when you order.
      </p>

      <button type="button" class="sc__checkout" @click="emit('checkout')">
        Checkout
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>
      </button>
    </template>
  </section>
</template>

<style scoped>
.sc {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sc__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sc__icon {
  color: var(--sf-forest);
}

.sc__title {
  flex: 1;
  margin: 0;
  font-family: var(--sf-serif);
  font-size: 1.3rem;
  color: var(--sf-ink);
}

.sc__count {
  font-family: var(--font-sans);
  font-size: 1rem;
  font-weight: 500;
  color: var(--sf-muted);
}

.sc__clear,
.sc__hint {
  padding: 0;
  border: none;
  background: none;
  color: var(--sf-forest-soft);
  font: inherit;
  font-size: 13px;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.sc__hint {
  margin-left: 4px;
  font-size: 12px;
  color: var(--sf-clay);
}

.sc__empty {
  margin: 0;
  padding: 20px 16px;
  border: 1px dashed var(--sf-rule);
  border-radius: 12px;
  font-size: 14px;
  color: var(--sf-muted);
  text-align: center;
}

.sc__lines {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.sc__line {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.sc__thumb {
  position: relative;
  flex: 0 0 auto;
  width: 60px;
  height: 60px;
  overflow: hidden;
  border-radius: 10px;
  background: var(--sf-sand);
}

.sc__line-body {
  flex: 1;
  min-width: 0;
}

.sc__name {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--sf-ink);
  overflow-wrap: anywhere;
}

.sc__line-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.sc__price {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.sc__step {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--sf-rule);
  border-radius: 999px;
  background: #fff;
}

.sc__step-btn {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: none;
  color: var(--sf-ink);
  cursor: pointer;
}

.sc__step-btn:hover {
  background: var(--sf-sand);
}

.sc__qty {
  min-width: 22px;
  font-size: 14px;
  font-weight: 700;
  text-align: center;
}

.sc__remove {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--sf-faint);
  cursor: pointer;
}

.sc__remove:hover {
  background: var(--sf-sand);
  color: var(--sf-clay-deep);
}

.sc__sums {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding-top: 16px;
  border-top: 1px solid var(--sf-rule);
}

.sc__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 15px;
}

.sc__row dt {
  color: var(--sf-muted);
}

.sc__row dd {
  margin: 0;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.sc__row--total {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--sf-rule);
  font-size: 20px;
}

.sc__row--total dt,
.sc__row--total dd {
  font-weight: 800;
  color: var(--sf-leaf);
}

.sc__sub {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--sf-faint);
}

.sc__muted {
  color: var(--sf-muted);
}

.sc__note {
  margin: -4px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--sf-faint);
}

.sc__checkout {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 52px;
  border: none;
  border-radius: 12px;
  background: var(--sf-clay);
  color: #fff;
  font: inherit;
  font-size: 17px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(180, 83, 42, 0.25);
  transition: background 150ms ease;
}

.sc__checkout:hover {
  background: var(--sf-clay-deep);
}

.sc__clear:focus-visible,
.sc__hint:focus-visible,
.sc__step-btn:focus-visible,
.sc__remove:focus-visible,
.sc__checkout:focus-visible {
  outline: 2px solid var(--sf-forest);
  outline-offset: 2px;
}
</style>
