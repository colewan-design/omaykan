<script setup lang="ts">
import { ChevronDown, LayoutGrid, ShoppingBasket } from '@lucide/vue'
import { ALL_AISLES } from './listing'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { useStorefrontCart } from '@pos/web/commerce/cart'
import { useStockedCategories, useStorefrontCatalog } from '@pos/web/commerce/catalog'
import { storefrontUrl } from '@pos/shared/index'
import { fetchStores, type StoreSummary } from '@pos/web/commerce/api'
import { useDeliveryLocation } from '@pos/web/commerce/deliveryLocation'
import { SHOP_ROOT_DOMAIN, mainSiteOrigin } from '@pos/web/commerce/shopDomain'
import { categoryIcon } from '@pos/core/utils/categoryIcons'
import AddressDialog from './AddressDialog.vue'
import SignupBanner from './SignupBanner.vue'

// The site chrome: one dark forest bar with the brand, where you are, the ways
// into the shop, the search, and your account and cart. Shared by the landing,
// about and account pages so they can't drift apart; each page decides what a
// search submit means via @search.
//
// There used to be a second row under it — a rail of every stocked category.
// The redesign folds that into a Categories menu: the front page now shows
// the aisles as picture tiles directly under the hero, and a permanent second
// row cost every page 66px to repeat them. The menu lists the same stocked
// categories the rail did, never a fixed taxonomy, because picking one has to
// produce that category's products.
//
// `shopHref` is where the category items point: the landing page handles a
// category in place (@category, no navigation), every other page sends the
// browser home with ?category= for the landing page to pick up.
//
// The cart is a page of its own (/cart, see cart/CartPage.vue). It used to be
// a slide-over opened from here; the redesign gives it a page, which is also
// what lets a shopper pick which lines go into an order.
//
// `accountBanner` is on everywhere the chrome appears except the account
// portal itself, where inviting someone to create an account directly above
// the form that creates one is noise. `categoryNav` is off there too: a menu
// of aisles above someone's addresses and order history is shop chrome on a
// page that isn't a shop.
const props = withDefaults(
  defineProps<{
    shopHref?: string
    activeCategory?: string
    accountBanner?: boolean
    categoryNav?: boolean
    fallbackCategories?: Array<{ id: string; name: string }>
    /**
     * Where the cart icon goes. A shop subdomain points it at the main site's
     * cart with the basket handed over, since the two origins do not share one.
     */
    cartHref?: string
  }>(),
  {
    cartHref: '/cart',
    shopHref: '#shop',
    activeCategory: '',
    accountBanner: true,
    categoryNav: true,
    fallbackCategories: () => [],
  },
)

const emit = defineEmits<{
  search: [term: string]
  category: [categoryId: string]
  product: [productId: string]
}>()

// "Enter your address" opens the address panel, and lives in the header
// rather than the landing page so the about and account pages get the same
// working control, the way the cart does.
const delivery = useDeliveryLocation()

const stockedCategories = useStockedCategories()
const categories = computed(() =>
  stockedCategories.value.length > 0 ? stockedCategories.value : props.fallbackCategories,
)

/** True on the landing page, where the shelves are on this same document. */
const handlesCategoryInPage = computed(() => props.shopHref.startsWith('#'))

function categoryHref(categoryId: string): string {
  if (handlesCategoryInPage.value) return props.shopHref
  return `${props.shopHref}?category=${encodeURIComponent(categoryId)}`
}

// A category the catalog invented rather than one we seeded still gets a menu
// item — it just wears the generic basket. An unknown aisle costs an icon,
// never a place in the menu.
function navIcon(categoryName: string) {
  return categoryIcon(categoryName) ?? ShoppingBasket
}

// ── The categories menu ─────────────────────────────────────────────────

const menuOpen = ref(false)
const menuRoot = ref<HTMLElement | null>(null)

function closeMenu() {
  menuOpen.value = false
}

// Closed by a click anywhere else and by Escape, like every other menu on the
// web: it holds nothing worth staying open for.
function onPointerDown(event: PointerEvent) {
  if (menuOpen.value && menuRoot.value && !menuRoot.value.contains(event.target as Node)) {
    closeMenu()
  }
}

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape' && menuOpen.value) closeMenu()
}

function selectCategory(categoryId: string, event: MouseEvent) {
  closeMenu()
  // Off the landing page the anchor is a real navigation home; leave it alone.
  if (!handlesCategoryInPage.value) return
  event.preventDefault()
  emit('category', categoryId)
}

const cart = useStorefrontCart()

const searchTerm = ref('')

// ── Suggestions ─────────────────────────────────────────────────────────
// Two sources, because a marketplace search has two kinds of answer. Products
// are already in memory — the catalog composable holds the whole shelf — so
// they match instantly; shops are a debounced call, since only the API can
// search a name the visitor has never loaded.
const MAX_PRODUCT_HINTS = 5
const MAX_SHOP_HINTS = 4

const catalog = useStorefrontCatalog()
const shopHints = ref<StoreSummary[]>([])
const suggestOpen = ref(false)
const cursor = ref(-1)

let shopDebounce: ReturnType<typeof setTimeout> | null = null
let shopSequence = 0

const needle = computed(() => searchTerm.value.trim().toLowerCase())

const productHints = computed(() => {
  if (needle.value.length < 2) return []
  return catalog.products
    .filter((product) => product.name.toLowerCase().includes(needle.value))
    .slice(0, MAX_PRODUCT_HINTS)
})

/** One flat list behind the two headings, so the arrow keys walk it as one. */
const hints = computed(() => [
  ...productHints.value.map((p) => ({ kind: 'product' as const, id: p.id, label: p.name })),
  ...shopHints.value.map((s) => ({ kind: 'shop' as const, id: s.orgSlug, label: s.name, sub: s.address })),
])

const showSuggest = computed(() => suggestOpen.value && needle.value.length >= 2 && hints.value.length > 0)

watch(needle, (value) => {
  cursor.value = -1
  if (shopDebounce !== null) clearTimeout(shopDebounce)
  if (value.length < 2) {
    shopHints.value = []
    return
  }
  shopDebounce = setTimeout(async () => {
    const ticket = ++shopSequence
    try {
      const found = await fetchStores({ q: value })
      if (ticket === shopSequence) shopHints.value = found.slice(0, MAX_SHOP_HINTS)
    } catch {
      if (ticket === shopSequence) shopHints.value = []
    }
  }, 220)
})

/** -1 is "still typing"; the list wraps back through it rather than sticking. */
function moveCursor(delta: number) {
  if (!showSuggest.value) return
  const count = hints.value.length
  const next = cursor.value + delta
  if (next < -1) cursor.value = count - 1
  else if (next >= count) cursor.value = -1
  else cursor.value = next
}

function choose(hint: { kind: 'product' | 'shop'; id: string; label: string }) {
  suggestOpen.value = false
  cursor.value = -1
  // A shop suggestion goes to that shop's own page, wherever the search was
  // typed — the same address its card in the directory carries.
  if (hint.kind === 'shop') {
    window.location.href = storefrontUrl(hint.id, {
      rootDomain: SHOP_ROOT_DOMAIN,
      origin: mainSiteOrigin() || window.location.origin,
    })
    return
  }
  // On the landing page the product opens in place; anywhere else it is a
  // real navigation to the page that can show it.
  if (handlesCategoryInPage.value) {
    searchTerm.value = ''
    emit('product', hint.id)
  } else {
    window.location.href = `/?product=${encodeURIComponent(hint.id)}`
  }
}

function onSearchKey(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') { event.preventDefault(); moveCursor(1) }
  else if (event.key === 'ArrowUp') { event.preventDefault(); moveCursor(-1) }
  else if (event.key === 'Escape') {
    // A type="search" input clears itself on Escape. While the panel is open
    // Escape should only dismiss the panel — losing the typed query as well
    // is not what "close this dropdown" means.
    if (showSuggest.value) event.preventDefault()
    suggestOpen.value = false
    cursor.value = -1
  }
}

function submitSearch() {
  // A highlighted suggestion is what Enter means; the raw term is the fallback.
  const picked = cursor.value >= 0 ? hints.value[cursor.value] : null
  if (picked) { choose(picked); return }
  suggestOpen.value = false
  emit('search', searchTerm.value.trim())
}

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeyDown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeyDown)
})

defineExpose({ clear: () => (searchTerm.value = '') })
</script>

<template>
  <!-- Two roots: the banner scrolls away with the page while `.fd-head` stays
       sticky on its own. Nesting it inside would pin the strip to the top for
       the whole scroll of a long shelf. -->
  <SignupBanner v-if="props.accountBanner" />

  <div class="fd-head">
    <header class="fd-bar">
      <a href="/" class="fd-brand" aria-label="Omaykan — home">
        <BrandLogo variant="light" :size="23" />
      </a>

      <!-- Where you are is what makes this shop list different from a national
           marketplace, so it is read as a value with a label rather than as a
           link labelled "Delivery". The whole block is the click target. -->
      <button
        type="button"
        class="fd-bar__delivery"
        :aria-label="delivery.isSet.value ? `Delivering to ${delivery.summary.value}. Change address.` : 'Set your delivery address'"
        @click="delivery.openDialog()"
      >
        <span class="fd-bar__delivery-label">Deliver to</span>
        <span class="fd-bar__addr">
          <svg class="fd-bar__pin" width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z"/></svg>
          <span class="fd-bar__addr-text">{{ delivery.isSet.value ? delivery.shortSummary.value : 'Set your location' }}</span>
          <svg class="fd-bar__caret" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>
        </span>
      </button>

      <nav class="fd-links" aria-label="Site">
        <!-- Always a real navigation to the front page's anchor: the shop list
             is only on the front page, and from inside an aisle or a product
             a bare #shops would point at nothing. -->
        <a href="/#shop" class="fd-link">Shop</a>

        <div v-if="props.categoryNav" ref="menuRoot" class="fd-menu">
          <button
            type="button"
            class="fd-link"
            aria-haspopup="true"
            aria-controls="fd-catmenu"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            Categories
            <ChevronDown
              :size="15"
              :stroke-width="2.2"
              class="fd-link__caret"
              :class="{ 'fd-link__caret--open': menuOpen }"
            />
          </button>

          <div v-if="menuOpen" id="fd-catmenu" class="fd-catmenu">
            <p v-if="categories.length === 0" class="fd-catmenu__empty">
              The aisles appear once the shop's shelf has loaded.
            </p>
            <!-- The whole shelf, as the listing: its first tile, and the one way
                 into it from pages that have no aisle row of their own. -->
            <a
              v-if="categories.length > 0"
              :href="categoryHref(ALL_AISLES)"
              class="fd-catmenu__item fd-catmenu__item--all"
              :class="{ 'fd-catmenu__item--on': props.activeCategory === ALL_AISLES }"
              :aria-current="props.activeCategory === ALL_AISLES ? 'true' : undefined"
              @click="selectCategory(ALL_AISLES, $event)"
            >
              <LayoutGrid :size="20" :stroke-width="1.6" />
              <span>All products</span>
            </a>
            <a
              v-for="cat in categories"
              :key="cat.id"
              :href="categoryHref(cat.id)"
              class="fd-catmenu__item"
              :class="{ 'fd-catmenu__item--on': cat.id === props.activeCategory }"
              :aria-current="cat.id === props.activeCategory ? 'true' : undefined"
              @click="selectCategory(cat.id, $event)"
            >
              <component :is="navIcon(cat.name)" :size="20" :stroke-width="1.6" />
              <span>{{ cat.name }}</span>
            </a>
          </div>
        </div>

        <a href="/#shops" class="fd-link">Stores</a>
        <a href="/#deals" class="fd-link">Deals</a>
        <a href="/about" class="fd-link">About</a>
        <a href="/seller/signup" class="fd-link fd-link--sell">Sell on Omaykan</a>
      </nav>

      <form class="fd-search" role="search" @submit.prevent="submitSearch">
        <button type="submit" class="fd-search__go" aria-label="Search">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        </button>
        <input
          v-model="searchTerm"
          type="search"
          placeholder="Search for local products, stores, or neighbourhoods…"
          aria-label="Search for local products, stores, or neighbourhoods"
          autocomplete="off"
          role="combobox"
          aria-autocomplete="list"
          :aria-expanded="showSuggest"
          aria-controls="fd-suggest"
          @focus="suggestOpen = true"
          @blur="suggestOpen = false"
          @keydown="onSearchKey"
        />

        <!-- Grouped, because "rice" and "SMJ Grocery" are different questions
             and a single blended list makes the visitor work out which kind of
             answer each row is. -->
        <div v-if="showSuggest" id="fd-suggest" class="fd-suggest" role="listbox">
          <template v-if="productHints.length > 0">
            <p class="fd-suggest__label">Products</p>
            <button
              v-for="(p, i) in productHints"
              :key="`p-${p.id}`"
              type="button"
              class="fd-suggest__row"
              :class="{ 'fd-suggest__row--on': cursor === i }"
              role="option"
              :aria-selected="cursor === i"
              @mousedown.prevent="choose({ kind: 'product', id: p.id, label: p.name })"
              @mouseenter="cursor = i"
            >
              <span class="fd-suggest__name">{{ p.name }}</span>
              <span v-if="p.unitLabel" class="fd-suggest__sub">{{ p.unitLabel }}</span>
            </button>
          </template>

          <template v-if="shopHints.length > 0">
            <p class="fd-suggest__label">Shops</p>
            <button
              v-for="(shop, i) in shopHints"
              :key="`s-${shop.orgSlug}`"
              type="button"
              class="fd-suggest__row"
              :class="{ 'fd-suggest__row--on': cursor === productHints.length + i }"
              role="option"
              :aria-selected="cursor === productHints.length + i"
              @mousedown.prevent="choose({ kind: 'shop', id: shop.orgSlug, label: shop.name })"
              @mouseenter="cursor = productHints.length + i"
            >
              <span class="fd-suggest__name">{{ shop.name }}</span>
              <span class="fd-suggest__sub">{{ shop.businessTypeLabel || shop.businessMode }}</span>
            </button>
          </template>
        </div>
      </form>

      <a
        href="/account?section=wishlist"
        class="fd-iconlink fd-iconlink--account"
        aria-label="Saved items"
      >
        <span class="fd-iconlink__icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/></svg>
        </span>
        <span class="fd-iconlink__label">
          Saved
        </span>
      </a>

      <a
        :href="props.cartHref"
        class="fd-iconlink fd-iconlink--cart"
        :aria-label="cart.itemCount.value > 0 ? `Cart, ${cart.itemCount.value} items` : 'Cart'"
      >
        <span class="fd-iconlink__icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="9" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/><path d="M2.5 3h2.6l2.4 12.2a1.6 1.6 0 0 0 1.6 1.3h8.6a1.6 1.6 0 0 0 1.6-1.2L21 8H6"/></svg>
          <span v-if="cart.itemCount.value > 0" class="fd-badge" aria-hidden="true">{{ cart.itemCount.value }}</span>
        </span>
        <span class="fd-iconlink__label">Cart</span>
      </a>
    </header>

    <AddressDialog :open="delivery.dialogOpen.value" @close="delivery.closeDialog()" />
  </div>
</template>

<style scoped>
.fd-head {
  position: sticky;
  top: 0;
  z-index: 100;
}

/* Relative so the categories menu can hang off the whole bar on a phone,
   where the button that opens it sits in a row of its own. */
.fd-bar {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 var(--fd-gutter);
  min-height: 74px;
  background: var(--sf-forest);
  color: var(--sf-cream);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.2);
}

.fd-brand { display: flex; align-items: center; flex-shrink: 0; }

/* ── Deliver to ───────────────────────────────────────────────────────── */

.fd-bar__delivery {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  flex-shrink: 0;
  line-height: 1.25;
  padding: 4px 10px 4px 18px;
  margin-left: -6px;
  border: none;
  border-left: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 0 8px 8px 0;
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}
.fd-bar__delivery:hover { background: rgba(255, 255, 255, 0.08); }
.fd-bar__delivery:focus-visible { outline: 2px solid var(--sf-gold); outline-offset: 2px; }

.fd-bar__delivery-label {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}
.fd-bar__addr {
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: 190px;
  font-size: 13.5px;
  font-weight: 700;
}
.fd-bar__pin { flex-shrink: 0; color: var(--sf-gold); }
.fd-bar__addr-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fd-bar__caret { flex-shrink: 0; color: rgba(255, 255, 255, 0.7); }

/* ── Links and the categories menu ───────────────────────────────────── */

.fd-links {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.fd-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 11px;
  border: none;
  border-radius: 8px;
  background: none;
  color: rgba(255, 255, 255, 0.92);
  font-family: inherit;
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  cursor: pointer;
}
.fd-link:hover,
.fd-link[aria-expanded='true'] { background: rgba(255, 255, 255, 0.09); color: #fff; }
.fd-link:focus-visible { outline: 2px solid var(--sf-gold); outline-offset: 2px; }

.fd-link__caret { transition: transform 150ms ease; }
.fd-link__caret--open { transform: rotate(180deg); }

.fd-menu { position: relative; }

.fd-catmenu {
  position: absolute;
  top: calc(100% + 16px);
  left: -8px;
  z-index: 60;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px;
  width: min(540px, calc(100vw - 2 * var(--fd-gutter)));
  max-height: min(70vh, 520px);
  overflow-y: auto;
  padding: 10px;
  border: 1px solid var(--sf-rule);
  border-radius: 12px;
  background: var(--sf-paper);
  box-shadow: 0 18px 44px rgba(23, 35, 28, 0.3);
}

.fd-catmenu__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--sf-ink);
  font-size: 14.5px;
  font-weight: 600;
  text-decoration: none;
}
.fd-catmenu__item svg { flex-shrink: 0; color: var(--sf-forest); }
.fd-catmenu__item:hover { background: var(--sf-sand); }
.fd-catmenu__item:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: -2px; }

/* The aisle you are standing in. The menu is the only chrome left that can
   say so, now that the rail is gone. */
.fd-catmenu__item--on { background: var(--sf-sand); color: var(--sf-clay); }
.fd-catmenu__item--on svg { color: var(--sf-clay); }

.fd-catmenu__empty {
  grid-column: 1 / -1;
  margin: 0;
  padding: 12px;
  font-size: 14px;
  color: var(--sf-muted);
}

/* ── Search ───────────────────────────────────────────────────────────── */

.fd-search {
  /* The suggestion panel hangs off this. */
  position: relative;
  flex: 1;
  min-width: 0;
  max-width: 480px;
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  height: 44px;
  padding: 0 14px 0 4px;
  border-radius: 6px;
  background: var(--sf-paper);
  color: var(--sf-muted);
}
.fd-search:focus-within { box-shadow: 0 0 0 3px rgba(217, 163, 91, 0.6); }

.fd-search__go {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: none;
  color: var(--sf-ink);
  cursor: pointer;
}
.fd-search__go:hover { background: var(--sf-sand); }

.fd-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  /* Longhands: `inherit` is only legal as the shorthand's entire value, so
     `font: 500 15px/1 inherit` is dropped whole and the element renders at
     the inherited 17px/400 instead. Same trap as .fd-totop in FdFooter. */
  font-family: inherit;
  font-size: 14.5px;
  font-weight: 500;
  line-height: 1;
  color: var(--sf-ink);
}
.fd-search input::placeholder { color: var(--sf-faint); }

.fd-suggest {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 60;
  max-height: 60vh;
  overflow-y: auto;
  padding: 6px;
  border: 1px solid var(--sf-rule);
  border-radius: 10px;
  background: var(--sf-paper);
  box-shadow: 0 16px 40px rgba(23, 35, 28, 0.2);
  text-align: left;
}

.fd-suggest__label {
  margin: 6px 8px 4px;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sf-faint);
}

.fd-suggest__row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: none;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}
.fd-suggest__row--on { background: var(--sf-sand); }

.fd-suggest__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: var(--sf-ink);
}
.fd-suggest__sub { flex-shrink: 0; font-size: 12px; color: var(--sf-muted); }

/* ── Account and cart ─────────────────────────────────────────────────── */

/* Icon over label, as the redesign has them. A button and a link share the
   one look, so the reset an <a> doesn't need is here for the button's sake. */
.fd-iconlink {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  padding: 4px 6px;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--sf-cream);
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1;
  text-decoration: none;
  cursor: pointer;
}
.fd-iconlink:hover { background: rgba(255, 255, 255, 0.09); }
.fd-iconlink:focus-visible { outline: 2px solid var(--sf-gold); outline-offset: 2px; }

.fd-iconlink__icon { position: relative; display: grid; place-items: center; }

.fd-iconlink__label {
  max-width: 11ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Cart count and unread messages. Terracotta, the one colour on the page that
   means "this is waiting for you". */
.fd-badge {
  position: absolute;
  top: -7px;
  right: -10px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--sf-clay);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  box-shadow: 0 0 0 2px var(--sf-forest);
}

/* ── Narrower screens ─────────────────────────────────────────────────── */

/* The delivery control goes first: DeliveryBand under the hero carries it on
   every screen, so the bar can give its width to the search. */
@media (max-width: 1180px) {
  .fd-bar__delivery { display: none; }
}

/* Phones and small tablets: brand, account and cart on the top row, the
   search the full width under them, the links under that. */
@media (max-width: 980px) {
  .fd-bar {
    flex-wrap: wrap;
    gap: 8px 10px;
    padding: 12px var(--fd-gutter) 6px;
    min-height: 0;
  }
  .fd-brand { margin-right: auto; }
  .fd-search { order: 3; flex-basis: 100%; max-width: none; margin-left: 0; height: 42px; }
  .fd-links { order: 4; flex-basis: 100%; flex-wrap: wrap; margin: 0 -10px; }
  .fd-link { font-size: 14px; }

  /* Static, so the open menu anchors to the bar rather than to its button
     and can use the bar's whole width. */
  .fd-menu { position: static; }
  .fd-catmenu { top: 100%; left: var(--fd-gutter); right: var(--fd-gutter); width: auto; }

  .fd-iconlink__label { display: none; }
}

/* "Sell with us" is also the hero's second button and a footer link, and on
   the narrowest phones it is the one that would push the row onto two lines. */
@media (max-width: 420px) {
  .fd-link { padding: 8px 8px; }
  .fd-link--sell { display: none; }
  .fd-catmenu { grid-template-columns: 1fr; }
}

/* Marketplace header — the reference uses a white navigation deck above a
   dedicated search row. These final rules intentionally supersede the older
   single dark-bar layout while retaining its menus and behaviours. */
.fd-bar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto auto;
  grid-template-areas:
    'brand links links account cart'
    'search search search delivery delivery';
  gap: 9px 18px;
  min-height: 0;
  padding: 12px var(--fd-inset) 13px;
  border-bottom: 1px solid var(--sf-rule);
  background: #fff;
  color: var(--sf-forest);
  box-shadow: 0 8px 24px rgba(20, 56, 39, 0.06);
}

.fd-brand { grid-area: brand; }
.fd-links {
  grid-area: links;
  justify-self: start;
  margin-left: 24px;
}
.fd-link {
  color: #264b39;
  font-size: 13.5px;
  font-weight: 650;
}
.fd-link:hover,
.fd-link[aria-expanded='true'] { background: #edf5ef; color: #123f2b; }

.fd-search {
  grid-area: search;
  justify-self: end;
  width: min(920px, 100%);
  max-width: none;
  height: 42px;
  margin: 0;
  border: 1px solid #dce5df;
  border-radius: 999px 0 0 999px;
  background: #fff;
  box-shadow: 0 3px 12px rgba(20, 56, 39, 0.05);
}
.fd-search:focus-within { box-shadow: 0 0 0 3px rgba(30, 111, 72, 0.15); }

.fd-bar__delivery {
  grid-area: delivery;
  display: flex;
  min-width: 220px;
  height: 42px;
  margin: 0 0 0 -19px;
  padding: 5px 18px;
  border: 1px solid #dce5df;
  border-left: 0;
  border-radius: 0 999px 999px 0;
  background: #f8fbf9;
  color: #264b39;
  justify-content: center;
}
.fd-bar__delivery:hover { background: #edf5ef; }
.fd-bar__delivery-label { display: none; }
.fd-bar__addr { max-width: 190px; font-size: 12.5px; }
.fd-bar__pin { color: #1e6f48; }
.fd-bar__caret { color: #6f8478; }

.fd-iconlink { color: #264b39; }
.fd-iconlink:hover { background: #edf5ef; }
.fd-iconlink--account { grid-area: account; }
.fd-iconlink--cart { grid-area: cart; }
.fd-iconlink__label { display: none; }
.fd-badge { box-shadow: 0 0 0 2px #fff; }

.fd-catmenu { top: calc(100% + 10px); }

@media (max-width: 1080px) {
  .fd-bar {
    grid-template-columns: minmax(0, 1fr) auto auto;
    grid-template-areas:
      'brand account cart'
      'links links links'
      'search search delivery';
    gap: 8px 10px;
  }
  .fd-links {
    justify-self: stretch;
    margin: 0 -8px;
    overflow-x: auto;
    scrollbar-width: none;
  }
  .fd-links::-webkit-scrollbar { display: none; }
  .fd-search { order: initial; flex-basis: auto; }
}

@media (max-width: 700px) {
  .fd-bar {
    grid-template-areas:
      'brand account cart'
      'search search search'
      'links links links';
    padding: 10px var(--fd-gutter) 8px;
  }
  .fd-bar__delivery { display: none; }
  .fd-search {
    width: 100%;
    height: 42px;
    border-radius: 999px;
  }
  .fd-links { padding-bottom: 2px; }
  .fd-link { padding: 7px 9px; font-size: 13px; }
  .fd-menu { position: static; }
  .fd-catmenu { top: 100%; left: var(--fd-gutter); right: var(--fd-gutter); width: auto; }
}
</style>
