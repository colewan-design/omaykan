<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchStores, type StoreSummary } from '@pos/web/commerce/api'
import { ORG_SLUG } from '@pos/web/commerce/context'
import { useDeliveryLocation } from '@pos/web/commerce/deliveryLocation'

/**
 * The shop list — what turns the landing page from one stall into a market.
 *
 * Picking a shop is a real navigation, not a client-side swap: the catalog
 * composable is a module-level singleton that loads once, so switching tenants
 * in place would mean invalidating it and everything derived from it. A page
 * load costs a moment and gets the whole app — cart scoping included —
 * consistently pointed at one shop.
 */

const delivery = useDeliveryLocation()

const stores = ref<StoreSummary[]>([])
const loading = ref(true)
const error = ref('')
const term = ref('')

let debounce: ReturnType<typeof setTimeout> | null = null
// Guards against a slow early response landing after a later one and
// overwriting it with stale results.
let sequence = 0

async function load(): Promise<void> {
  const ticket = ++sequence
  loading.value = true
  error.value = ''

  try {
    const found = await fetchStores({
      q: term.value.trim(),
      lat: delivery.location.value.lat,
      lng: delivery.location.value.lng,
    })
    if (ticket !== sequence) return
    stores.value = found
  } catch {
    if (ticket !== sequence) return
    error.value = 'Could not load the shops. Please try again.'
    stores.value = []
  } finally {
    if (ticket === sequence) loading.value = false
  }
}

watch(term, () => {
  if (debounce !== null) clearTimeout(debounce)
  debounce = setTimeout(load, 250)
})

// Re-sort when the visitor sets or clears their address.
watch(() => [delivery.location.value.lat, delivery.location.value.lng], load)

onMounted(load)
onBeforeUnmount(() => {
  if (debounce !== null) clearTimeout(debounce)
})

/**
 * Keeps whatever path serves the landing page — it is reachable as both `/`
 * and `/landing.html`, and hard-coding either one breaks the other.
 */
function shopUrl(store: StoreSummary): string {
  return `${window.location.pathname}?shop=${encodeURIComponent(store.orgSlug)}`
}

function distanceLabel(store: StoreSummary): string {
  if (store.distanceKm === null) return ''
  return store.distanceKm < 1
    ? `${Math.round(store.distanceKm * 1000)} m away`
    : `${store.distanceKm.toFixed(1)} km away`
}
</script>

<template>
  <section id="shops" class="shops">
    <div class="shops__head">
      <div>
        <h2 class="shops__title">Shops near you</h2>
        <p class="shops__sub">
          <template v-if="delivery.isSet.value && delivery.location.value.lat !== null">
            Sorted by distance from your address.
          </template>
          <template v-else>
            Browse every shop on Omaykan — set your address to see the nearest first.
          </template>
        </p>
      </div>

      <label class="shops__searchwrap">
        <span class="sr-only">Search shops</span>
        <input
          v-model="term"
          class="shops__search"
          type="search"
          placeholder="Search shops by name or area"
        />
      </label>
    </div>

    <p v-if="loading" class="shops__note">Loading shops…</p>
    <p v-else-if="error" class="shops__note shops__note--error">{{ error }}</p>
    <p v-else-if="stores.length === 0" class="shops__note">
      <template v-if="term.trim() !== ''">No shops match &ldquo;{{ term }}&rdquo;.</template>
      <template v-else>No shops are open for orders yet.</template>
    </p>

    <ul v-else class="shops__grid">
      <li v-for="store in stores" :key="`${store.orgSlug}/${store.storeCode}`">
        <a
          :href="shopUrl(store)"
          class="shopcard"
          :class="{ 'shopcard--current': store.orgSlug === ORG_SLUG }"
        >
          <span class="shopcard__name">{{ store.name }}</span>
          <span class="shopcard__type">{{ store.businessTypeLabel || store.businessMode }}</span>
          <span v-if="store.address" class="shopcard__addr">{{ store.address }}</span>

          <span class="shopcard__foot">
            <span class="shopcard__count">{{ store.productCount }} items</span>
            <span v-if="distanceLabel(store)" class="shopcard__dist">{{ distanceLabel(store) }}</span>
          </span>

          <span v-if="store.orgSlug === ORG_SLUG" class="shopcard__badge">Browsing now</span>
        </a>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.shops { margin: 44px 0 8px; }

.shops__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.shops__title {
  margin: 0 0 4px;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #06240f;
}
.shops__sub { margin: 0; font-size: 14px; color: #5b6b60; }

.shops__searchwrap { flex-shrink: 0; }
.shops__search {
  width: min(300px, 70vw);
  padding: 11px 16px;
  border: 1px solid #d6e2d9;
  border-radius: 999px;
  font-size: 14px;
  color: #06240f;
}
.shops__search:focus { outline: 2px solid #1a6b3c; outline-offset: 1px; border-color: transparent; }

.shops__note { margin: 0; padding: 18px 0; font-size: 14px; color: #5b6b60; }
.shops__note--error { color: #b3261e; }

.shops__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.shopcard {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
  padding: 18px 18px 16px;
  border: 1px solid #e2ebe4;
  border-radius: 12px;
  background: #fff;
  text-decoration: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}
.shopcard:hover {
  border-color: #1a6b3c;
  box-shadow: 0 10px 24px rgba(6, 36, 15, 0.1);
  transform: translateY(-2px);
}
.shopcard--current { border-color: #1a6b3c; background: #f5f9f6; }

@media (prefers-reduced-motion: reduce) {
  .shopcard, .shopcard:hover { transition: none; transform: none; }
}

.shopcard__name { font-size: 15.5px; font-weight: 800; color: #06240f; }
/* The badge is positioned over the corner, so the name has to keep clear of
   it — without this a long shop name runs underneath and is unreadable. */
.shopcard--current .shopcard__name { padding-right: 96px; }
.shopcard__type {
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #1a6b3c;
}
.shopcard__addr {
  font-size: 13px;
  line-height: 1.45;
  color: #5b6b60;
}

.shopcard__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding-top: 12px;
  font-size: 12.5px;
  color: #6b7a70;
}
.shopcard__dist { font-weight: 700; color: #1a6b3c; }

.shopcard__badge {
  position: absolute;
  top: 14px;
  right: 14px;
  padding: 3px 9px;
  border-radius: 999px;
  background: #bbf451;
  color: #06240f;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
