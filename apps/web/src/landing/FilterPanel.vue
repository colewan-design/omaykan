<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { X } from '@lucide/vue'
import { emptyListing, narrowingCount, sameNarrowing, type ListingFilters, type SoldBy } from './listing'

// The listing's sidebar: which aisles, what price, and the two other facts the
// catalog carries about a product — whether the shop has marked it down, and
// whether it is sold by the piece or by weight.
//
// The listing redesign draws Location and Seller Type here. Every product on
// this page is one shop's stock, so a location filter could only ever keep all
// of them or none, and the catalog has no seller type at all; a filter that
// cannot change the list is worse than no filter. Their places go to the two
// that can. The town each product comes from is still on every card.
//
// Changes are staged and land on "Apply filters", as the redesign has it. On a
// phone this is a sheet over the grid, and reshuffling the grid behind it on
// every tick would be work nobody can see.

const props = defineProps<{
  filters: ListingFilters
  aisles: Array<{ id: string; name: string; count: number }>
  /** Top of the price slider, whole pesos. */
  ceiling: number
  step: number
  offersSale: boolean
  offersSoldBy: boolean
  /** Drawn as a phone sheet, with its own close button. */
  sheet?: boolean
}>()

const emit = defineEmits<{ apply: [filters: ListingFilters]; close: [] }>()

interface Draft {
  categories: string[]
  min: number
  max: number
  saleOnly: boolean
  soldBy: SoldBy[]
}

const draft = reactive<Draft>({ categories: [], min: 0, max: 0, saleOnly: false, soldBy: [] })

function reset() {
  draft.categories = [...props.filters.categories]
  draft.min = Math.min(props.filters.minPeso ?? 0, props.ceiling)
  draft.max = Math.min(props.filters.maxPeso ?? props.ceiling, props.ceiling)
  draft.saleOnly = props.filters.saleOnly
  draft.soldBy = [...props.filters.soldBy]
}

// A new filters object arrives after every applied change, and the ceiling
// moves once the catalog loads; either way the draft starts from what is shown.
watch(() => [props.filters, props.ceiling], reset, { immediate: true, deep: true })

function staged(): ListingFilters {
  return {
    ...props.filters,
    categories: [...draft.categories],
    // The slider's ends mean "no limit", so a pricier item added tomorrow is
    // not quietly filtered out of a link saved today.
    minPeso: draft.min > 0 ? draft.min : null,
    maxPeso: draft.max < props.ceiling ? draft.max : null,
    saleOnly: draft.saleOnly,
    soldBy: [...draft.soldBy],
    page: 1,
  }
}

const dirty = computed(() => !sameNarrowing(staged(), props.filters))
const canClear = computed(() => narrowingCount(props.filters) > 0 || narrowingCount(staged()) > 0)

const pct = (value: number) => (props.ceiling > 0 ? (value / props.ceiling) * 100 : 0)

function clamp(value: number, low: number, high: number) {
  return Math.min(Math.max(value, low), high)
}

function toggleAisle(aisleId: string) {
  const at = draft.categories.indexOf(aisleId)
  if (at === -1) draft.categories.push(aisleId)
  else draft.categories.splice(at, 1)
}

/** "All products" can be ticked but not unticked: it is what having no aisle picked means. */
function allAisles(event: Event) {
  draft.categories = []
  ;(event.target as HTMLInputElement).checked = true
}

function toggleSoldBy(kind: SoldBy) {
  const at = draft.soldBy.indexOf(kind)
  if (at === -1) draft.soldBy.push(kind)
  else draft.soldBy.splice(at, 1)
}

// Each handler writes the clamped value back into the control, because a
// value the clamp left unchanged would not re-render it: a thumb dragged
// past the other one would otherwise stay where the pointer left it.
function onMin(event: Event) {
  const input = event.target as HTMLInputElement
  const value = Number(input.value)
  draft.min = clamp(Number.isFinite(value) ? Math.round(value) : 0, 0, draft.max)
  input.value = String(draft.min)
}

function onMax(event: Event) {
  const input = event.target as HTMLInputElement
  const value = input.value.trim() === '' ? props.ceiling : Number(input.value)
  draft.max = clamp(Number.isFinite(value) ? Math.round(value) : props.ceiling, draft.min, props.ceiling)
  input.value = String(draft.max)
}

function apply() {
  if (dirty.value) emit('apply', staged())
  else emit('close')
}

function clearAll() {
  emit('apply', { ...emptyListing(), sort: props.filters.sort })
}
</script>

<template>
  <form class="lflt" :class="{ 'lflt--sheet': sheet }" aria-labelledby="lflt-title" @submit.prevent="apply">
    <div class="lflt__head">
      <h2 id="lflt-title" class="lflt__title">Filters</h2>
      <button v-if="sheet" type="button" class="lflt__close" aria-label="Close filters" @click="emit('close')">
        <X :size="20" :stroke-width="2" />
      </button>
    </div>

    <div class="lflt__group" role="group" aria-labelledby="lflt-aisles">
      <p id="lflt-aisles" class="lflt__legend">Category</p>
      <label class="lflt__check">
        <input type="checkbox" :checked="draft.categories.length === 0" @change="allAisles" />
        <span class="lflt__label">All products</span>
      </label>
      <label v-for="aisle in aisles" :key="aisle.id" class="lflt__check">
        <input type="checkbox" :checked="draft.categories.includes(aisle.id)" @change="toggleAisle(aisle.id)" />
        <span class="lflt__label">{{ aisle.name }}</span>
        <span class="lflt__n">{{ aisle.count }}</span>
      </label>
    </div>

    <div class="lflt__group" role="group" aria-labelledby="lflt-price">
      <p id="lflt-price" class="lflt__legend">Price range</p>

      <div class="lflt__range" :style="{ '--lo': `${pct(draft.min)}%`, '--hi': `${pct(draft.max)}%` }">
        <span class="lflt__track" aria-hidden="true"><span class="lflt__fill"></span></span>
        <!-- Both thumbs sit on one track. The lower one is raised once it is in
             the upper half, so two thumbs parked at the right end can still be
             pulled apart by the one on top. -->
        <input
          type="range"
          class="lflt__thumb"
          :class="{ 'lflt__thumb--top': draft.min > ceiling / 2 }"
          :min="0"
          :max="ceiling"
          :step="step"
          :value="draft.min"
          aria-label="Lowest price, in pesos"
          @input="onMin"
        />
        <input
          type="range"
          class="lflt__thumb"
          :min="0"
          :max="ceiling"
          :step="step"
          :value="draft.max"
          aria-label="Highest price, in pesos"
          @input="onMax"
        />
      </div>

      <div class="lflt__prices">
        <label class="lflt__price">
          <span aria-hidden="true">₱</span>
          <input
            type="number"
            inputmode="numeric"
            min="0"
            :max="ceiling"
            step="1"
            :value="draft.min"
            aria-label="Lowest price, in pesos"
            @change="onMin"
          />
        </label>
        <label class="lflt__price">
          <span aria-hidden="true">₱</span>
          <input
            type="number"
            inputmode="numeric"
            min="0"
            :max="ceiling"
            step="1"
            :value="draft.max"
            aria-label="Highest price, in pesos"
            @change="onMax"
          />
        </label>
      </div>
    </div>

    <!-- Only when the shelf has markdowns to find. Kept while it is ticked,
         so it can still be unticked after a search leaves none. -->
    <div v-if="offersSale || draft.saleOnly" class="lflt__group" role="group" aria-labelledby="lflt-offers">
      <p id="lflt-offers" class="lflt__legend">Offers</p>
      <label class="lflt__check">
        <input v-model="draft.saleOnly" type="checkbox" />
        <span class="lflt__label">On sale</span>
      </label>
    </div>

    <div v-if="offersSoldBy || draft.soldBy.length > 0" class="lflt__group" role="group" aria-labelledby="lflt-sold">
      <p id="lflt-sold" class="lflt__legend">Sold by</p>
      <label class="lflt__check">
        <input type="checkbox" :checked="draft.soldBy.includes('piece')" @change="toggleSoldBy('piece')" />
        <span class="lflt__label">The piece</span>
      </label>
      <label class="lflt__check">
        <input type="checkbox" :checked="draft.soldBy.includes('weight')" @change="toggleSoldBy('weight')" />
        <span class="lflt__label">Weight</span>
      </label>
    </div>

    <div class="lflt__actions">
      <!-- Disabled with nothing to apply, except on a phone's sheet, where it
           is also the way back to the grid. -->
      <button type="submit" class="lflt__apply" :disabled="!dirty && !sheet">Apply filters</button>
      <button v-if="canClear" type="button" class="lflt__clear" @click="clearAll">Clear all</button>
    </div>
  </form>
</template>

<style scoped>
.lflt {
  padding: 18px 18px 20px;
  border: 1px solid var(--sf-rule);
  border-radius: 12px;
  background: var(--sf-paper);
  box-shadow: 0 1px 2px rgba(23, 35, 28, 0.05);
}

.lflt__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--sf-rule);
}

.lflt__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--sf-ink);
}

.lflt__close {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--sf-ink);
  cursor: pointer;
}
.lflt__close:hover { background: var(--sf-sand); }
.lflt__close:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }

.lflt__group {
  padding: 16px 0 14px;
  border-bottom: 1px solid var(--sf-rule);
}

.lflt__legend {
  margin: 0 0 8px;
  font-size: 14.5px;
  font-weight: 800;
  color: var(--sf-ink);
}

.lflt__check {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
  font-size: 14px;
  color: var(--sf-ink);
  cursor: pointer;
}
.lflt__check input {
  flex-shrink: 0;
  width: 17px;
  height: 17px;
  margin: 0;
  accent-color: var(--sf-forest);
  cursor: pointer;
}
.lflt__label { flex: 1; min-width: 0; }
.lflt__n { font-size: 12px; color: var(--sf-faint); font-variant-numeric: tabular-nums; }

/* ── The two-thumb slider ─────────────────────────────────────────────── */

.lflt__range { position: relative; height: 28px; margin: 4px 0 12px; }

/* Inset by half a thumb, so the filled stretch ends under each thumb's
   centre rather than at the input's edges. */
.lflt__track {
  position: absolute;
  top: 12px;
  left: 9px;
  right: 9px;
  height: 4px;
  border-radius: 2px;
  background: var(--sf-sand-deep);
}
.lflt__fill {
  position: absolute;
  top: 0;
  bottom: 0;
  left: var(--lo);
  right: calc(100% - var(--hi));
  border-radius: 2px;
  background: var(--sf-forest);
}

/* The inputs are transparent and let the pointer through; only their thumbs
   take it, so the lower thumb is not hidden under the upper input's track. */
.lflt__thumb {
  position: absolute;
  top: 5px;
  left: 0;
  z-index: 2;
  width: 100%;
  height: 18px;
  margin: 0;
  background: none;
  pointer-events: none;
  -webkit-appearance: none;
  appearance: none;
}
.lflt__thumb--top { z-index: 3; }

.lflt__thumb::-webkit-slider-runnable-track { height: 18px; background: none; }
.lflt__thumb::-moz-range-track { background: none; }

.lflt__thumb::-webkit-slider-thumb {
  width: 18px;
  height: 18px;
  border: 3px solid var(--sf-paper);
  border-radius: 50%;
  background: var(--sf-forest);
  box-shadow: 0 0 0 1px var(--sf-forest), 0 1px 4px rgba(0, 0, 0, 0.25);
  pointer-events: auto;
  cursor: grab;
  -webkit-appearance: none;
  appearance: none;
}
.lflt__thumb::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border: 3px solid var(--sf-paper);
  border-radius: 50%;
  background: var(--sf-forest);
  box-shadow: 0 0 0 1px var(--sf-forest), 0 1px 4px rgba(0, 0, 0, 0.25);
  pointer-events: auto;
  cursor: grab;
}
.lflt__thumb:focus-visible { outline: none; }
.lflt__thumb:focus-visible::-webkit-slider-thumb { box-shadow: 0 0 0 1px var(--sf-forest), 0 0 0 4px rgba(180, 83, 42, 0.45); }
.lflt__thumb:focus-visible::-moz-range-thumb { box-shadow: 0 0 0 1px var(--sf-forest), 0 0 0 4px rgba(180, 83, 42, 0.45); }

.lflt__prices { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }

.lflt__price {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 38px;
  padding: 0 10px;
  border: 1px solid var(--sf-rule);
  border-radius: 8px;
  background: #fff;
  color: var(--sf-muted);
  font-size: 13.5px;
}
.lflt__price:focus-within { border-color: var(--sf-forest); box-shadow: 0 0 0 2px rgba(31, 46, 37, 0.15); }

.lflt__price input {
  flex: 1;
  width: 100%;
  min-width: 0;
  padding: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--sf-ink);
  -moz-appearance: textfield;
  appearance: textfield;
}
.lflt__price input::-webkit-inner-spin-button,
.lflt__price input::-webkit-outer-spin-button { margin: 0; -webkit-appearance: none; }

/* ── Actions ──────────────────────────────────────────────────────────── */

.lflt__actions { display: grid; gap: 10px; padding-top: 18px; }

.lflt__apply {
  height: 46px;
  border: none;
  border-radius: 8px;
  background: var(--sf-forest);
  color: #fff;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: background 150ms, opacity 150ms;
}
.lflt__apply:hover:not(:disabled) { background: var(--sf-forest-soft); }
.lflt__apply:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }
.lflt__apply:disabled { opacity: 0.45; cursor: default; }

.lflt__clear {
  justify-self: center;
  padding: 4px;
  border: none;
  background: none;
  color: var(--sf-clay);
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

/* On the phone's sheet the actions stay in reach at the bottom however long
   the aisle list is. */
.lflt--sheet {
  min-height: 100%;
  border: none;
  border-radius: 0;
  box-shadow: none;
}
.lflt--sheet .lflt__actions {
  position: sticky;
  bottom: 0;
  padding-bottom: 18px;
  background: var(--sf-paper);
}
</style>
