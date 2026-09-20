<script setup lang="ts">
import { LayoutGrid, ShoppingBasket } from '@lucide/vue'
import { categoryIcon } from '@pos/core/utils/categoryIcons'
import { ALL_AISLES } from './listing'

// The aisles as a row of tiles across the top of the listing, as the listing
// redesign draws them. One press picks one aisle — the sidebar's boxes are for
// picking several — so each tile is a real link to that aisle alone, and a
// middle-click opens it in a new tab like any other link.
//
// The redesign pictures each aisle with an illustration. Here each wears the
// glyph the header's Categories menu gives it, so an aisle looks the same in
// both places, and one the icon set does not know gets the menu's basket.

defineProps<{
  aisles: Array<{ id: string; name: string; count: number }>
  /** The listing's picked aisles; empty is "All products". */
  active: string[]
  total: number
}>()

const emit = defineEmits<{ select: [aisleId: string] }>()

/** Same rule as a product card: only a plain left-click is handled in page. */
function open(aisleId: string, event: MouseEvent) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  emit('select', aisleId)
}

function iconFor(name: string) {
  return categoryIcon(name) ?? ShoppingBasket
}

const itemsLabel = (count: number) => `${count} item${count === 1 ? '' : 's'}`
</script>

<template>
  <nav class="aisles" aria-label="Aisles">
    <ul class="aisles__row">
      <li>
        <a
          :href="`/?category=${ALL_AISLES}`"
          class="aisle"
          :class="{ 'aisle--on': active.length === 0 }"
          :aria-current="active.length === 0 ? 'page' : undefined"
          @click="open(ALL_AISLES, $event)"
        >
          <LayoutGrid class="aisle__icon" :size="30" :stroke-width="1.5" aria-hidden="true" />
          <span class="aisle__name">All products</span>
          <span class="aisle__n">{{ itemsLabel(total) }}</span>
        </a>
      </li>

      <li v-for="aisle in aisles" :key="aisle.id">
        <!-- Dark when it is the one aisle showing; outlined when it is one of
             several picked in the sidebar. -->
        <a
          :href="`/?category=${encodeURIComponent(aisle.id)}`"
          class="aisle"
          :class="{
            'aisle--on': active.length === 1 && active[0] === aisle.id,
            'aisle--in': active.length > 1 && active.includes(aisle.id),
          }"
          :aria-current="active.length === 1 && active[0] === aisle.id ? 'page' : undefined"
          @click="open(aisle.id, $event)"
        >
          <component :is="iconFor(aisle.name)" class="aisle__icon" :size="30" :stroke-width="1.5" aria-hidden="true" />
          <span class="aisle__name">{{ aisle.name }}</span>
          <span class="aisle__n">{{ itemsLabel(aisle.count) }}</span>
        </a>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.aisles { margin: 0 0 12px; }

/* Columns share the width when there are few aisles and scroll sideways when
   there are more than fit, rather than wrapping into a second ragged row.
   A scroller crops whatever paints outside its padding, so the padding is deep
   enough to hold the lit tile's shadow and the lift on hover. */
.aisles__row {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(118px, 1fr);
  gap: 12px;
  margin: 0;
  padding: 6px 4px 22px;
  list-style: none;
  overflow-x: auto;
  scroll-snap-type: x proximity;
  scrollbar-width: thin;
  scrollbar-color: var(--sf-sand-deep) transparent;
}
.aisles__row li { scroll-snap-align: start; }

.aisle {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 100%;
  min-height: 116px;
  padding: 16px 10px 12px;
  border: 1px solid var(--sf-rule);
  border-radius: 12px;
  background: var(--sf-paper);
  color: var(--sf-ink);
  text-align: center;
  text-decoration: none;
  box-shadow: 0 1px 2px rgba(23, 35, 28, 0.05);
  transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
}
.aisle:hover {
  border-color: var(--sf-sand-deep);
  box-shadow: 0 8px 20px rgba(23, 35, 28, 0.1);
  transform: translateY(-2px);
}
.aisle:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 3px; }

.aisle__icon { flex-shrink: 0; color: var(--sf-clay); }

.aisle__name {
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.aisle__n { font-size: 11.5px; color: var(--sf-muted); }

.aisle--on {
  border-color: var(--sf-forest);
  background: var(--sf-forest);
  color: var(--sf-paper);
  box-shadow: 0 8px 18px rgba(23, 35, 28, 0.24);
}
.aisle--on .aisle__icon { color: var(--sf-paper); }
.aisle--on .aisle__n { color: rgba(255, 255, 255, 0.75); }

.aisle--in { border-color: var(--sf-forest); background: var(--sf-sand); }

@media (max-width: 640px) {
  .aisles__row { grid-auto-columns: 104px; gap: 10px; }
  .aisle { min-height: 100px; padding: 12px 8px 10px; }
  .aisle__name { font-size: 12.5px; }
}

@media (prefers-reduced-motion: reduce) {
  .aisle, .aisle:hover { transition: none; transform: none; }
}
</style>
