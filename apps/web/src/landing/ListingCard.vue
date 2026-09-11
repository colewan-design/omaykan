<script setup lang="ts">
import { computed } from 'vue'
import { MapPin, ShoppingCart } from '@lucide/vue'
import { discountPercent, formatCurrency, type Product } from '@pos/shared/index'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useSavedProducts } from '@pos/web/commerce/favorites'

// One product on the aisle listing, drawn to the listing redesign: a framed
// card with the photo edge to edge, the name, the price, where it comes from,
// and a square cart button in the corner. `view="list"` lays the same pieces
// out as a row.
//
// The front page's shelves keep ProductCard. The behaviour is the same in both
// and follows the same rules: the card is a real link that opens in place on a
// plain left-click, the heart saves to the wishlist, and the add button turns
// into a stepper once the item is in the basket.

const props = withDefaults(
  defineProps<{
    product: Product
    /** The town the product comes from. Blank hides the line. */
    place?: string
    view?: 'grid' | 'list'
  }>(),
  { place: '', view: 'grid' },
)

const emit = defineEmits<{ select: [productId: string] }>()

const cart = useStorefrontCart()
const saved = useSavedProducts()

const quantity = computed(() => cart.quantityOf(props.product.id))
const discount = computed(() => discountPercent(props.product))
const isSaved = computed(() => saved.isSaved(props.product.id))
const href = computed(() => `/?product=${encodeURIComponent(props.product.id)}`)

function open(event: MouseEvent) {
  if (event.defaultPrevented || event.button !== 0) return
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  emit('select', props.product.id)
}

// Every control sits inside the card's <a>, so each calls off the navigation
// as well as the card's own click.
function addToCart(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  cart.add(props.product)
}

function removeOne(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  cart.decrement(props.product.id)
}

function toggleSaved(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  saved.toggle(props.product)
}
</script>

<template>
  <a :href="href" class="lcard" :class="`lcard--${view}`" @click="open">
    <div class="lcard__art">
      <span v-if="discount !== null" class="lcard__flag">SALE {{ discount }}%</span>
      <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" loading="lazy" />
      <div v-else class="lcard__placeholder" aria-hidden="true">🛒</div>
      <button
        type="button"
        class="lcard__save"
        :class="{ 'lcard__save--on': isSaved }"
        :aria-pressed="isSaved"
        :aria-label="isSaved ? `Remove ${product.name} from your wishlist` : `Save ${product.name} to your wishlist`"
        @click="toggleSaved"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" :fill="isSaved ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/></svg>
      </button>
    </div>

    <div class="lcard__body">
      <p class="lcard__name">{{ product.name }}</p>
      <p v-if="product.unitLabel" class="lcard__unit">{{ product.unitLabel }}</p>

      <!-- Pushed to the foot of the card, so prices and buttons line up along a
           grid row whatever the names above them do. -->
      <div class="lcard__foot">
        <div class="lcard__facts">
          <p class="lcard__price">
            <span class="lcard__now" :class="{ 'lcard__now--sale': discount !== null }">
              {{ formatCurrency(product.priceCents) }}
            </span>
            <span v-if="discount !== null" class="lcard__was">
              {{ formatCurrency(product.compareAtPriceCents!) }}
            </span>
          </p>
          <p v-if="place" class="lcard__place">
            <MapPin :size="13" :stroke-width="2" aria-hidden="true" />
            <span>{{ place }}</span>
          </p>
        </div>

        <button
          v-if="quantity === 0"
          type="button"
          class="lcard__add"
          :aria-label="`Add ${product.name} to cart`"
          @click="addToCart"
        >
          <ShoppingCart :size="18" :stroke-width="2" aria-hidden="true" />
          <span class="lcard__add-label">Add</span>
        </button>

        <!-- The same height as the button it replaces, so adding an item does
             not move the row under the pointer. -->
        <span v-else class="lcard__qty" role="group" :aria-label="`Quantity of ${product.name}`">
          <button
            type="button"
            class="lcard__step"
            :aria-label="quantity === 1 ? `Remove ${product.name} from cart` : `One fewer ${product.name}`"
            @click="removeOne"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="M5 12h14"/></svg>
          </button>
          <span class="lcard__qty-n" aria-live="polite">{{ quantity }}</span>
          <button
            type="button"
            class="lcard__step"
            :aria-label="`One more ${product.name}`"
            @click="addToCart"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
          </button>
        </span>
      </div>
    </div>
  </a>
</template>

<style scoped>
/* A query container, so the foot can stack when the card itself is narrow
   (two across on a phone) whatever the window is doing. */
.lcard {
  container-type: inline-size;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: var(--sf-paper);
  color: inherit;
  text-decoration: none;
  box-shadow: 0 1px 2px rgba(23, 35, 28, 0.05);
  transition: box-shadow 180ms ease, border-color 180ms ease, transform 180ms ease;
}
.lcard:hover {
  border-color: var(--sf-sand-deep);
  box-shadow: 0 10px 24px rgba(23, 35, 28, 0.12);
  transform: translateY(-2px);
}
.lcard:focus-visible { outline: 2px solid var(--sf-forest); outline-offset: 3px; }

.lcard__art {
  position: relative;
  flex-shrink: 0;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: var(--sf-sand);
}
.lcard__art img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 300ms ease;
}
.lcard:hover .lcard__art img { transform: scale(1.05); }
.lcard__placeholder { display: grid; place-items: center; height: 100%; font-size: 30px; }

.lcard__flag {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  padding: 4px 9px;
  border-radius: 0 0 6px 0;
  background: var(--sf-clay);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.lcard__save {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 253, 249, 0.94);
  color: var(--sf-ink);
  box-shadow: 0 2px 8px rgba(23, 35, 28, 0.16);
  cursor: pointer;
  transition: color 150ms, transform 150ms;
}
.lcard__save:hover { color: var(--sf-clay); transform: scale(1.08); }
.lcard__save:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }
.lcard__save--on { color: var(--sf-clay); }

.lcard__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 12px;
}

.lcard__name {
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--sf-ink);
}
.lcard:hover .lcard__name { text-decoration: underline; text-underline-offset: 2px; }

.lcard__unit { margin: 0; font-size: 12.5px; line-height: 1.3; color: var(--sf-muted); }

.lcard__foot {
  margin-top: auto;
  padding-top: 10px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
}

.lcard__facts { display: flex; flex-direction: column; gap: 4px; min-width: 0; }

.lcard__price {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 6px;
}
.lcard__now {
  font-size: 17px;
  font-weight: 800;
  color: var(--sf-ink);
  font-variant-numeric: tabular-nums;
}
.lcard__now--sale { color: var(--sf-clay); }
.lcard__was { font-size: 12.5px; color: var(--sf-faint); text-decoration: line-through; }

.lcard__place {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  font-size: 12px;
  color: var(--sf-muted);
}
.lcard__place svg { flex-shrink: 0; }
.lcard__place span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.lcard__add {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 42px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: var(--sf-forest);
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: background 150ms;
}
.lcard__add:hover { background: var(--sf-clay); }
.lcard__add:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }
.lcard__add-label { display: none; }

.lcard__qty {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  height: 40px;
  overflow: hidden;
  border-radius: 8px;
  background: var(--sf-forest);
  color: #fff;
}
.lcard__step {
  display: grid;
  place-items: center;
  width: 32px;
  height: 100%;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  cursor: pointer;
  transition: background 150ms;
}
.lcard__step:hover { background: rgba(255, 255, 255, 0.18); }
.lcard__step:focus-visible { outline: 2px solid var(--sf-gold); outline-offset: -3px; }
.lcard__qty-n {
  min-width: 22px;
  text-align: center;
  font-size: 14px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

/* Narrow cards: price and town get the full width, and the button goes under
   them as a full-width, labelled, 44px bar — also the right target for a thumb. */
@container (max-width: 199px) {
  .lcard__foot { flex-direction: column; align-items: stretch; }
  .lcard__add { width: 100%; height: 44px; }
  .lcard__add-label { display: inline; }
  .lcard__qty { height: 44px; justify-content: space-between; }
  .lcard__step { width: 46px; }
}

/* ── As a row ─────────────────────────────────────────────────────────── */

.lcard--list { flex-direction: row; }
.lcard--list .lcard__art {
  flex: 0 0 150px;
  align-self: stretch;
  aspect-ratio: auto;
  min-height: 140px;
}
.lcard--list .lcard__body { padding: 14px 16px; }
.lcard--list .lcard__name { font-size: 16px; }
.lcard--list .lcard__add { width: auto; padding: 0 16px; }
.lcard--list .lcard__add-label { display: inline; }

@container (max-width: 420px) {
  .lcard--list .lcard__art { flex-basis: 104px; min-height: 112px; }
  .lcard--list .lcard__body { padding: 10px 12px; }
  .lcard--list .lcard__name { font-size: 14.5px; }
}

@media (prefers-reduced-motion: reduce) {
  .lcard, .lcard:hover, .lcard__art img, .lcard:hover .lcard__art img { transition: none; transform: none; }
}
</style>
