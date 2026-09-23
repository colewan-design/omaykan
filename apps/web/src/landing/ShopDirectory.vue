<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { fetchStores, type StoreSummary } from '@pos/web/commerce/api'
import { DELIVERY_MAX_KM } from '@pos/web/commerce/delivery'
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

/**
 * `query` puts the list under the page's own search box instead of its own:
 * a marketplace search for "SMJ Grocery" has to be able to answer with a shop,
 * not only with the products that happen to mention it.
 */
const props = withDefaults(defineProps<{ query?: string }>(), { query: '' })

const delivery = useDeliveryLocation()

const stores = ref<StoreSummary[]>([])
const loading = ref(true)
const error = ref('')
const ownTerm = ref('')

/** Driven by the page's search when it is searching, by its own box otherwise. */
const searching = computed(() => props.query.trim() !== '')
const term = computed({
  get: () => (searching.value ? props.query : ownTerm.value),
  set: (next: string) => { ownTerm.value = next },
})

// Nothing to say when a product search simply is not a shop name — an empty
// shop list under "rice" is noise, not an answer.
const hidden = computed(() => searching.value && !loading.value && error.value === '' && stores.value.length === 0)

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

watch(() => term.value, () => {
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

/**
 * The town, not the doorstep.
 *
 * A shop's address is a street line — "Magsaysay Avenue, Baguio City" — and on
 * a card the useful half is the part that answers "is that near me": the last
 * segment, which is the city or municipality.
 */
function areaOf(store: StoreSummary): string {
  const parts = store.address.split(',').map((part) => part.trim()).filter((part) => part !== '')
  return parts.length === 0 ? '' : parts[parts.length - 1]
}

/**
 * Past this the checkout refuses the order outright — DeliveryQuoter throws
 * OutsideDeliveryArea beyond DELIVERY_MAX_KM. The directory still lists the
 * shop, because a shop out of range is not a shop that is closed, but sending
 * someone to browse a shelf they cannot order from is a dead click.
 *
 * Null distance means no pin on one side or the other, and the quoter charges
 * the flat base fee rather than refusing, so there is nothing to warn about.
 */
function outOfRange(store: StoreSummary): boolean {
  return store.distanceKm !== null && store.distanceKm > DELIVERY_MAX_KM
}

/** "Closed now", or "Back at 4:00 PM" when the shop said when. */
function pausedLabel(store: StoreSummary): string {
  if (!store.orderingResumesAt) return 'Closed now'
  const at = new Date(store.orderingResumesAt)
  const time = at.toLocaleTimeString('en-PH', { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Manila' })
  const sameDay = at.toLocaleDateString('en-PH', { timeZone: 'Asia/Manila' })
    === new Date().toLocaleDateString('en-PH', { timeZone: 'Asia/Manila' })
  const day = sameDay ? '' : `${at.toLocaleDateString('en-PH', { weekday: 'short', timeZone: 'Asia/Manila' })} `
  return `Back ${day}at ${time}`
}

/** Stands in for a shop that has no photo — its own initials, not a grey box. */
function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter((word) => /[a-z0-9]/i.test(word))
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('')
}

function keyOf(store: StoreSummary): string {
  return `${store.orgSlug}/${store.storeCode}`
}

// A shelf photo can 404 — several of the seeded ones are hotlinked to a third
// party. A broken <img> renders as a torn-page glyph, which looks like a bug
// rather than a shop without a picture, so a failed load falls back to the
// initials the same as no photo at all.
const brokenImages = reactive(new Set<string>())
</script>

<template>
  <section v-if="!hidden" id="shops" class="shops">
    <div class="shops__head">
      <div>
        <h2 class="shops__title">
          <template v-if="searching">Shops matching &ldquo;{{ props.query }}&rdquo;</template>
          <!-- Named when we know it: "Shops near you in La Trinidad" is the
               sentence a national marketplace cannot write. -->
          <template v-else-if="delivery.town.value">Shops near you in {{ delivery.town.value }}</template>
          <template v-else>Shops near you</template>
        </h2>
        <p class="shops__sub">
          <template v-if="searching">
            {{ stores.length }} shop{{ stores.length === 1 ? '' : 's' }} match your search.
          </template>
          <template v-else-if="delivery.isSet.value && delivery.location.value.lat !== null">
            Sorted by distance from your address.
          </template>
          <template v-else>
            Browse every shop on Omaykan — set your address to see the nearest first.
          </template>
        </p>
      </div>

      <label v-if="!searching" class="shops__searchwrap">
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
          <span class="shopcard__art">
            <img
              v-if="store.imageUrl && !brokenImages.has(keyOf(store))"
              :src="store.imageUrl"
              alt=""
              loading="lazy"
              @error="brokenImages.add(keyOf(store))"
            />
            <span v-else class="shopcard__art-fallback" aria-hidden="true">{{ initials(store.name) }}</span>
            <span v-if="store.orgSlug === ORG_SLUG" class="shopcard__badge">Browsing now</span>
          </span>

          <span class="shopcard__body">
            <span class="shopcard__name">{{ store.name }}</span>

            <span class="shopcard__meta">
              <span v-if="areaOf(store)" class="shopcard__area">{{ areaOf(store) }}</span>
              <span v-if="distanceLabel(store)" class="shopcard__dist">{{ distanceLabel(store) }}</span>
            </span>

            <span class="shopcard__type">{{ store.businessTypeLabel || store.businessMode }}</span>

            <span v-if="store.isNew || outOfRange(store) || store.orderingPaused" class="shopcard__tags">
              <!-- The shop's own pause. Listed rather than hidden, so a regular
                   sees "back at 4:00 PM" instead of assuming it has gone. -->
              <span v-if="store.orderingPaused" class="shopcard__tag shopcard__tag--paused">
                {{ pausedLabel(store) }}
              </span>
              <span v-if="store.isNew" class="shopcard__tag shopcard__tag--new">New on Omaykan</span>
              <span v-if="outOfRange(store)" class="shopcard__tag shopcard__tag--far">
                Outside delivery range
              </span>
            </span>

            <span v-if="store.categories.length > 0" class="shopcard__cats">
              {{ store.categories.join(' · ') }}
            </span>

            <span class="shopcard__foot">
              <span class="shopcard__count">{{ store.productCount }} items</span>
              <span class="shopcard__go">Browse shop &rarr;</span>
            </span>
          </span>
        </a>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.shops { margin: 8px 0 44px; }

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
  color: var(--sf-ink);
}
.shops__sub { margin: 0; font-size: 14px; color: var(--sf-muted); }

.shops__searchwrap { flex-shrink: 0; }
.shops__search {
  width: min(300px, 70vw);
  padding: 11px 16px;
  border: 1px solid var(--sf-rule);
  border-radius: 999px;
  font-size: 14px;
  color: var(--sf-ink);
}
.shops__search:focus { outline: 2px solid var(--sf-forest); outline-offset: 1px; border-color: transparent; }

.shops__note { margin: 0; padding: 18px 0; font-size: 14px; color: var(--sf-muted); }
.shops__note--error { color: #b3261e; }

.shops__grid {
  display: grid;
  /* Wide enough for a photo beside the words — below ~330px the two columns
     fight and the text sets three words to a line. */
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

/* Horizontal: photo left, everything the choice turns on to the right of it.
   The stacked card carried a name, a type and an address and read as a
   directory entry; a shopper picking between counters is picking on the
   picture, the distance and what is on the shelves. */
.shopcard {
  position: relative;
  display: flex;
  gap: 14px;
  height: 100%;
  padding: 14px;
  border: 1px solid var(--sf-rule);
  border-radius: 12px;
  background: var(--sf-paper);
  text-decoration: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}
.shopcard:hover {
  border-color: var(--sf-forest);
  box-shadow: 0 10px 24px rgba(23, 35, 28, 0.1);
  transform: translateY(-2px);
}
.shopcard--current { border-color: var(--sf-forest); background: var(--sf-sand); }

@media (prefers-reduced-motion: reduce) {
  .shopcard, .shopcard:hover { transition: none; transform: none; }
}

.shopcard__art {
  position: relative;
  flex: 0 0 auto;
  width: 104px;
  height: 104px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--sf-sand);
}
.shopcard__art img { width: 100%; height: 100%; object-fit: cover; display: block; }
.shopcard__art-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--sf-forest);
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.shopcard__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.shopcard__name {
  font-size: 15.5px;
  font-weight: 800;
  color: var(--sf-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Area and distance together: "where" and "how far" are one thought. */
.shopcard__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 13px;
  color: var(--sf-muted);
}
.shopcard__area { font-weight: 600; }
.shopcard__dist { font-weight: 700; color: var(--sf-forest); }
.shopcard__area + .shopcard__dist::before {
  content: '·';
  margin-right: 6px;
  color: var(--sf-faint);
  font-weight: 400;
}

.shopcard__type {
  margin-top: 1px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--sf-forest);
}

.shopcard__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin: 3px 0 1px;
}
.shopcard__tag {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
  white-space: nowrap;
}
.shopcard__tag--new { background: #efe3cf; color: var(--sf-forest); }
/* Amber, not red: the shop is fine, it is just too far to bring to this door. */
.shopcard__tag--far { background: #fdf1dc; color: #92500e; }
/* Grey: nothing is wrong with the shop; it is just not cooking right now. */
.shopcard__tag--paused { background: #ecebe8; color: #4a4843; }

.shopcard__cats {
  font-size: 12.5px;
  line-height: 1.4;
  color: var(--sf-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shopcard__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 10px;
  font-size: 12.5px;
  color: var(--sf-muted);
}
.shopcard__go { font-weight: 800; color: var(--sf-forest); white-space: nowrap; }
.shopcard:hover .shopcard__go { text-decoration: underline; }

/* Over the photo rather than the card corner — on a horizontal card the top
   right belongs to the shop's name. */
.shopcard__badge {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  padding: 3px 6px;
  background: var(--sf-gold);
  color: var(--sf-ink);
  font-size: 9.5px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  text-align: center;
}

@media (max-width: 520px) {
  .shops__grid { grid-template-columns: 1fr; }
  .shopcard__art { width: 84px; height: 84px; }
  .shopcard__cats { white-space: normal; }
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
.shops__title { font-family: var(--sf-serif); font-weight: 700; letter-spacing: 0; }
</style>
