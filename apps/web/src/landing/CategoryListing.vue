<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronDown, LayoutGrid, List, SlidersHorizontal } from '@lucide/vue'
import { discountPercent, type Category, type Product } from '@pos/shared/index'
import AisleTiles from './AisleTiles.vue'
import FilterPanel from './FilterPanel.vue'
import ListingCard from './ListingCard.vue'
import ListingPager from './ListingPager.vue'
import {
  ALL_AISLES,
  SORT_OPTIONS,
  applyListing,
  emptyListing,
  narrowingCount,
  priceCeiling,
  priceStep,
  type ListingFilters,
  type SortKey,
} from './listing'

// The landing page's listing face (?category=): the aisles across the top,
// filters down the side, and the shelf as a grid or a list, a page at a time —
// laid out to the listing redesign.
//
// It holds none of the listing's state. Every control emits the whole next
// ListingFilters and the landing page writes it to the address bar, so a
// filtered, sorted, paged listing is a link, and Back undoes the last change.
//
// Every slot keeps to what the catalog carries. The redesign's per-card
// location is the town on this shop's own sign, since every product here comes
// off one shelf; its "Latest" sort is the shop's own shelf order, since a
// product carries no date. See FilterPanel for the filters it swapped.

/** Six, four, three or two across as the column narrows — 24 fills whole rows at each. */
const PAGE_SIZE = 24

const props = defineProps<{
  filters: ListingFilters
  /** In stock, and already narrowed by the header search. */
  products: Product[]
  categories: Category[]
  loading: boolean
  error: string
  search: string
  /** The shop's town, for each card's location line. Blank hides it. */
  place: string
}>()

const emit = defineEmits<{
  change: [filters: ListingFilters]
  select: [productId: string]
  retry: []
  clearSearch: []
}>()

const aisles = computed(() =>
  props.categories.map((category) => ({
    id: category.id,
    name: category.name,
    count: props.products.filter((product) => product.categoryId === category.id).length,
  })),
)

const results = computed(() => applyListing(props.products, props.filters))
const pageCount = computed(() => Math.max(1, Math.ceil(results.value.length / PAGE_SIZE)))
/** A ?page= past the end — after a search or a filter shrank the list — shows the last page. */
const page = computed(() => Math.min(Math.max(1, props.filters.page), pageCount.value))
const firstIndex = computed(() => (page.value - 1) * PAGE_SIZE)
const paged = computed(() => results.value.slice(firstIndex.value, firstIndex.value + PAGE_SIZE))

const ceiling = computed(() => priceCeiling(props.products))
const step = computed(() => priceStep(ceiling.value))
const offersSale = computed(() => props.products.some((product) => discountPercent(product) !== null))
const offersSoldBy = computed(
  () => props.products.some((p) => p.kind === 'weighted') && props.products.some((p) => p.kind !== 'weighted'),
)

const narrowing = computed(() => narrowingCount(props.filters))
/** Everything but the aisles: what "Clear filters" undoes. */
const sideNarrowing = computed(() => narrowing.value - props.filters.categories.length)

const title = computed(() => {
  const picked = props.filters.categories
  if (picked.length === 0) return 'All products'
  // Aisle ids are uuids; until the catalog names them there is nothing worth printing.
  if (props.categories.length === 0) return props.loading ? 'Loading…' : 'This aisle'
  const names = picked.map((id) => props.categories.find((c) => c.id === id)?.name ?? id.replace(/-/g, ' '))
  if (names.length === 1) return names[0]
  if (names.length === 2) return `${names[0]} & ${names[1]}`
  return `${names.length} aisles`
})

const summary = computed(() => {
  const total = results.value.length
  const range = total > PAGE_SIZE ? `${firstIndex.value + 1}–${firstIndex.value + paged.value.length} of ` : ''
  return `${range}${total} item${total === 1 ? '' : 's'}`
})

// ── Grid or list ──────────────────────────────────────────────────────────
// Remembered per browser: it is how someone likes to look, not part of what
// they are looking at, so it stays out of the shareable URL.

type View = 'grid' | 'list'
const VIEW_KEY = 'omaykan.listing.view'

function storedView(): View {
  try {
    return window.localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'grid'
  } catch {
    return 'grid'
  }
}

const view = ref<View>(storedView())
watch(view, (next) => {
  try {
    window.localStorage.setItem(VIEW_KEY, next)
  } catch {
    // Private windows and blocked storage: the choice lasts the visit.
  }
})

// ── The phone's filter sheet ──────────────────────────────────────────────

const sheetOpen = ref(false)
const sidebar = ref<HTMLElement | null>(null)
const filterButton = ref<HTMLButtonElement | null>(null)

async function openSheet() {
  sheetOpen.value = true
  await nextTick()
  sidebar.value?.querySelector<HTMLElement>('button')?.focus()
}

function closeSheet() {
  if (!sheetOpen.value) return
  sheetOpen.value = false
  filterButton.value?.focus()
}

// The page behind a full-height sheet should not scroll under the thumb.
watch(sheetOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})
onBeforeUnmount(() => {
  document.body.style.overflow = ''
})

// ── Changes ───────────────────────────────────────────────────────────────

function change(patch: Partial<ListingFilters>) {
  emit('change', { ...props.filters, ...patch })
}

/** A tile picks that one aisle and keeps the price and the other filters. */
function pickAisle(aisleId: string) {
  change({ categories: aisleId === ALL_AISLES ? [] : [aisleId], page: 1 })
}

function applyFilters(next: ListingFilters) {
  closeSheet()
  emit('change', { ...next, page: 1 })
}

function setSort(event: Event) {
  change({ sort: (event.target as HTMLSelectElement).value as SortKey, page: 1 })
}

function goPage(next: number) {
  change({ page: next })
}

function clearSideFilters() {
  change({ ...emptyListing(props.filters.categories), sort: props.filters.sort })
}
</script>

<template>
  <section class="lst" aria-labelledby="lst-title">
    <AisleTiles
      v-if="aisles.length > 0"
      :aisles="aisles"
      :active="filters.categories"
      :total="products.length"
      @select="pickAisle"
    />

    <div class="lst__layout">
      <aside
        id="lst-filters"
        ref="sidebar"
        class="lst__side"
        :class="{ 'lst__side--sheet': sheetOpen }"
        @keydown.esc="closeSheet"
      >
        <FilterPanel
          :filters="filters"
          :aisles="aisles"
          :ceiling="ceiling"
          :step="step"
          :offers-sale="offersSale"
          :offers-sold-by="offersSoldBy"
          :sheet="sheetOpen"
          @apply="applyFilters"
          @close="closeSheet"
        />
      </aside>
      <div v-if="sheetOpen" class="lst__scrim" aria-hidden="true" @click="closeSheet"></div>

      <div id="listing-results" class="lst__main">
        <div class="lst__head">
          <div class="lst__heading">
            <h1 id="lst-title" class="lst__title">{{ title }}</h1>
            <p v-if="!loading && !error" class="lst__count" aria-live="polite">
              {{ summary }}<template v-if="search">
                matching &ldquo;{{ search }}&rdquo;
                <button type="button" class="lst__linkbtn" @click="emit('clearSearch')">Clear search</button>
              </template>
            </p>
          </div>

          <div class="lst__tools">
            <!-- Phones only: the sidebar becomes a sheet this opens. -->
            <button
              ref="filterButton"
              type="button"
              class="lst__filterbtn"
              aria-controls="lst-filters"
              :aria-expanded="sheetOpen"
              @click="openSheet"
            >
              <SlidersHorizontal :size="17" :stroke-width="2" aria-hidden="true" />
              Filters
              <span v-if="narrowing > 0" class="lst__badge">{{ narrowing }}</span>
            </button>

            <label class="lst__sort">
              <span class="lst__sort-label">Sort by:</span>
              <select :value="filters.sort" @change="setSort">
                <option v-for="option in SORT_OPTIONS" :key="option.key" :value="option.key">
                  {{ option.label }}
                </option>
              </select>
              <ChevronDown class="lst__sort-caret" :size="16" :stroke-width="2" aria-hidden="true" />
            </label>

            <div class="lst__views" role="group" aria-label="Show products as">
              <button
                type="button"
                class="lst__view"
                :class="{ 'lst__view--on': view === 'grid' }"
                :aria-pressed="view === 'grid'"
                aria-label="Grid"
                @click="view = 'grid'"
              >
                <LayoutGrid :size="18" :stroke-width="1.8" />
              </button>
              <button
                type="button"
                class="lst__view"
                :class="{ 'lst__view--on': view === 'list' }"
                :aria-pressed="view === 'list'"
                aria-label="List"
                @click="view = 'list'"
              >
                <List :size="18" :stroke-width="1.8" />
              </button>
            </div>
          </div>
        </div>

        <p v-if="loading" class="lst__note">Loading today's catalog…</p>

        <p v-else-if="error" class="lst__note lst__note--error">
          {{ error }}
          <button type="button" class="lst__linkbtn" @click="emit('retry')">Try again</button>
        </p>

        <template v-else-if="paged.length > 0">
          <ul class="lst__items" :class="`lst__items--${view}`">
            <li v-for="product in paged" :key="product.id">
              <ListingCard :product="product" :place="place" :view="view" @select="emit('select', $event)" />
            </li>
          </ul>

          <ListingPager v-if="pageCount > 1" :page="page" :total="pageCount" @go="goPage" />
        </template>

        <!-- Each empty state offers the one step that undoes what emptied it. -->
        <div v-else class="lst__empty">
          <template v-if="sideNarrowing > 0 || filters.categories.length > 1">
            <p>Nothing here matches these filters.</p>
            <button type="button" class="lst__btn" @click="clearSideFilters">Clear filters</button>
          </template>
          <template v-else-if="search">
            <p>Nothing in {{ title }} matches &ldquo;{{ search }}&rdquo;.</p>
            <button type="button" class="lst__btn" @click="emit('clearSearch')">Clear search</button>
          </template>
          <template v-else>
            <p>Nothing in {{ title }} today.</p>
            <button type="button" class="lst__btn" @click="pickAisle(ALL_AISLES)">Browse all products</button>
          </template>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.lst { margin-bottom: 56px; }

.lst__layout {
  display: grid;
  grid-template-columns: 252px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

/* A query container, so the number of columns follows the space the results
   actually get — which the sidebar takes a fixed bite out of. */
.lst__main {
  container-type: inline-size;
  min-width: 0;
  scroll-margin-top: 96px;
}

.lst__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px 20px;
  margin-bottom: 18px;
}

.lst__heading { min-width: 0; }

.lst__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: clamp(1.6rem, 2.6vw, 2.15rem);
  font-weight: 700;
  line-height: 1.15;
  color: var(--sf-ink);
}

.lst__count { margin: 6px 0 0; font-size: 14px; color: var(--sf-muted); }

.lst__tools { display: flex; align-items: center; gap: 10px; }

.lst__filterbtn {
  display: none;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 14px;
  border: 1.5px solid var(--sf-forest);
  border-radius: 8px;
  background: var(--sf-paper);
  color: var(--sf-forest);
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.lst__badge {
  display: grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--sf-clay);
  color: #fff;
  font-size: 11.5px;
  font-weight: 800;
}

/* The box is the label, so "Sort by:" reads as part of the control; the
   select runs under the caret so the whole right side opens it. */
.lst__sort {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 34px 0 12px;
  border: 1px solid var(--sf-rule);
  border-radius: 8px;
  background: var(--sf-paper);
  cursor: pointer;
}
.lst__sort:focus-within { border-color: var(--sf-forest); box-shadow: 0 0 0 2px rgba(31, 46, 37, 0.15); }

.lst__sort-label { font-size: 13.5px; color: var(--sf-muted); white-space: nowrap; }

.lst__sort select {
  height: 100%;
  margin-right: -34px;
  padding: 0 34px 0 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  color: var(--sf-ink);
  cursor: pointer;
  -webkit-appearance: none;
  appearance: none;
}

.lst__sort-caret {
  position: absolute;
  top: 50%;
  right: 12px;
  transform: translateY(-50%);
  color: var(--sf-ink);
  pointer-events: none;
}

.lst__views { display: flex; gap: 6px; }

.lst__view {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--sf-rule);
  border-radius: 8px;
  background: var(--sf-paper);
  color: var(--sf-ink);
  cursor: pointer;
  transition: background 150ms, color 150ms, border-color 150ms;
}
.lst__view:hover { border-color: var(--sf-forest); }
.lst__view--on { border-color: var(--sf-forest); background: var(--sf-forest); color: var(--sf-paper); }

.lst__view:focus-visible,
.lst__filterbtn:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }

/* ── The shelf ────────────────────────────────────────────────────────── */

.lst__items { display: grid; margin: 0; padding: 0; list-style: none; }

.lst__items--grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
@container (min-width: 520px) {
  .lst__items--grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
}
@container (min-width: 820px) {
  .lst__items--grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@container (min-width: 1400px) {
  .lst__items--grid { grid-template-columns: repeat(6, minmax(0, 1fr)); }
}

.lst__items--list { grid-template-columns: minmax(0, 1fr); gap: 12px; }
@container (min-width: 1200px) {
  .lst__items--list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

.lst__note { margin: 0; padding: 24px 0; font-size: 15px; color: var(--sf-faint); }
.lst__note--error { color: #b3261e; }

.lst__linkbtn {
  margin-left: 8px;
  padding: 0;
  border: none;
  background: none;
  color: var(--sf-clay);
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

.lst__empty {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 56px 20px;
  border: 1px dashed var(--sf-sand-deep);
  border-radius: 12px;
  text-align: center;
  font-size: 15px;
  color: var(--sf-muted);
}
.lst__empty p { margin: 0; }

.lst__btn {
  padding: 11px 22px;
  border: none;
  border-radius: 8px;
  background: var(--sf-forest);
  color: #fff;
  font-family: inherit;
  font-size: 14.5px;
  font-weight: 700;
  cursor: pointer;
}
.lst__btn:hover { background: var(--sf-clay); }
.lst__btn:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }

.lst__scrim { display: none; }

/* ── Narrower screens ─────────────────────────────────────────────────── */

/* The header wraps onto three rows here, so an anchored scroll needs more room. */
@media (max-width: 980px) {
  .lst__main { scroll-margin-top: 180px; }
}

/* The sidebar gives its column to the results and becomes a sheet from the
   left, opened by the Filters button. Above the sticky header (z-index 100). */
@media (max-width: 960px) {
  .lst__layout { grid-template-columns: minmax(0, 1fr); }
  .lst__side { display: none; }
  .lst__side--sheet {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 210;
    display: block;
    width: min(360px, 90vw);
    overflow-y: auto;
    background: var(--sf-paper);
    box-shadow: 8px 0 40px rgba(23, 35, 28, 0.3);
  }
  .lst__scrim {
    position: fixed;
    inset: 0;
    z-index: 205;
    display: block;
    background: rgba(23, 35, 28, 0.45);
  }
  .lst__filterbtn { display: inline-flex; }
}

@media (max-width: 560px) {
  .lst__tools { width: 100%; }
  .lst__sort { flex: 1; min-width: 0; }
  .lst__sort select { flex: 1; min-width: 0; }
  .lst__sort-label { display: none; }
}
</style>
