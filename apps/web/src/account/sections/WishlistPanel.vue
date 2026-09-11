<script setup lang="ts">
import { computed } from 'vue'
import { Heart, ShoppingBag } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { useSavedProducts, type SavedProduct } from '@pos/web/commerce/favorites'
import { useStorefrontCart } from '@pos/web/commerce/cart'

// Everything the shopper has hearted, from any shop.
//
// Kept in this browser (commerce/favorites.ts) — there is no wishlist behind
// the API — and said so, so nobody expects to find it on their phone. Prices
// are as they were when the heart was pressed; the shop's shelf has today's,
// and the cart page re-checks them against it.

const saved = useSavedProducts()
const cart = useStorefrontCart()

function productHref(item: SavedProduct): string {
  return `/?${new URLSearchParams({ shop: item.shop.orgSlug, product: item.product.id }).toString()}`
}

/**
 * One order goes to one shop, so the basket is one shop's. A saved item from
 * another shop waits until that basket is ordered or emptied, rather than
 * joining it and making the whole order fail at the last step.
 */
const cartShopKey = computed(() => {
  const shop = cart.cartShop.value
  return cart.cartLines.value.length > 0 && shop ? `${shop.orgSlug}/${shop.storeCode}` : ''
})

function fromOtherShop(item: SavedProduct): boolean {
  return cartShopKey.value !== '' && cartShopKey.value !== `${item.shop.orgSlug}/${item.shop.storeCode}`
}

function inCart(item: SavedProduct): number {
  return fromOtherShop(item) ? 0 : cart.quantityOf(item.product.id)
}

function addToCart(item: SavedProduct) {
  if (fromOtherShop(item)) return
  cart.add(item.product, 1, item.shop)
}
</script>

<template>
  <div>
    <div class="acct-head">
      <h1 class="acct-head__title">Wishlist</h1>
      <p class="acct-head__sub">
        Products you've hearted, kept on this browser. Prices are as they were when you saved
        them — the shop's shelf has today's.
      </p>
    </div>

    <div v-if="saved.items.value.length === 0" class="acct-empty">
      <p class="acct-empty__title">Nothing saved yet</p>
      <p class="acct-empty__note">
        Tap the heart on any product and it will wait for you here, ready to put in your cart.
      </p>
      <a href="/" class="acct-btn" style="margin-top: 18px">Start shopping</a>
    </div>

    <div v-else class="wish-grid">
      <article v-for="item in saved.items.value" :key="item.key" class="wish-card">
        <a :href="productHref(item)" class="wish-card__art">
          <img v-if="item.product.imageUrl" :src="item.product.imageUrl" :alt="item.product.name" loading="lazy" />
          <ShoppingBag v-else :size="34" :stroke-width="1.4" />
        </a>

        <button
          type="button"
          class="wish-card__heart"
          :aria-label="`Remove ${item.product.name} from your wishlist`"
          @click="saved.remove(item.key)"
        >
          <Heart :size="16" :stroke-width="2" fill="currentColor" />
        </button>

        <div class="wish-card__body">
          <a :href="productHref(item)" class="wish-card__name">{{ item.product.name }}</a>
          <p v-if="item.product.unitLabel" class="wish-card__unit">{{ item.product.unitLabel }}</p>
          <p class="wish-card__price">{{ formatCurrency(item.product.priceCents) }}</p>
          <p v-if="item.shop.name" class="wish-card__shop">{{ item.shop.name }}</p>
          <!-- Pushes the button to the card's foot, so a row of cards lines
               their buttons up whether or not each has a unit line. -->
          <span class="wish-card__spacer" aria-hidden="true"></span>

          <a v-if="inCart(item) > 0" href="/cart" class="acct-btn acct-btn--ghost wish-card__go">
            In your cart ({{ inCart(item) }})
          </a>
          <button
            v-else
            type="button"
            class="acct-btn wish-card__go"
            :disabled="fromOtherShop(item)"
            @click="addToCart(item)"
          >
            Add to cart
          </button>
          <p v-if="fromOtherShop(item)" class="wish-card__note">
            Your cart is from another shop. Order or empty it first.
          </p>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.wish-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 18px;
}

.wish-card {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--acct-rule);
  border-radius: var(--acct-radius);
  background: var(--acct-surface);
}

.wish-card__art {
  display: grid;
  place-items: center;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: var(--sf-sand, #ede5d8);
  color: var(--acct-faint);
}
.wish-card__art img { width: 100%; height: 100%; object-fit: cover; }

.wish-card__heart {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 253, 249, 0.95);
  color: var(--acct-accent);
  box-shadow: 0 2px 8px rgba(35, 29, 24, 0.16);
  cursor: pointer;
}
.wish-card__heart:hover { transform: scale(1.08); }

.wish-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 12px 14px 14px;
}

.wish-card__name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--acct-ink);
}
.wish-card__name:hover { color: var(--acct-accent); }

.wish-card__unit { margin: 2px 0 0; font-size: 12.5px; color: var(--acct-faint); }
.wish-card__price { margin: 6px 0 0; font-size: 16px; font-weight: 800; color: var(--acct-ink); }
.wish-card__shop { margin: 2px 0 0; font-size: 12.5px; color: var(--acct-muted); }

.wish-card__spacer { flex: 1; min-height: 12px; }
.wish-card__go { width: 100%; height: 38px; }

.wish-card__note { margin: 8px 0 0; font-size: 12px; line-height: 1.45; color: var(--acct-faint); }
</style>
