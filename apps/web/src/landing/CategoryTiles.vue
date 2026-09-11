<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, ChevronRight } from '@lucide/vue'
import { useStockedCategories, useStorefrontCatalog } from '@pos/web/commerce/catalog'

// "Shop by Category", as picture tiles.
//
// The aisles this shop actually stocks, each pictured with one of its own
// products — not stock photography. A photograph of a woven basket on a tile
// that opens onto tins of tuna would be a promise the aisle does not keep.
//
// Five across, the redesign's count; the rest behind "View all categories",
// which opens the grid in place rather than going anywhere.

const INITIAL = 5

const emit = defineEmits<{ category: [categoryId: string] }>()

const catalog = useStorefrontCatalog()
const stocked = useStockedCategories()
const expanded = ref(false)

const tiles = computed(() =>
  stocked.value
    .map((category) => {
      const inAisle = catalog.products.filter((p) => p.categoryId === category.id && !p.outOfStock)
      return {
        id: category.id,
        name: category.name,
        count: inAisle.length,
        cover: inAisle.find((p) => p.imageUrl)?.imageUrl ?? null,
      }
    })
    // Stocked but sold out today: the tile would open onto an empty aisle.
    .filter((tile) => tile.count > 0),
)

const shown = computed(() => (expanded.value ? tiles.value : tiles.value.slice(0, INITIAL)))

/** Same rule as a product card: only a plain left-click is handled in page. */
function open(categoryId: string, event: MouseEvent) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  emit('category', categoryId)
}
</script>

<template>
  <section v-if="tiles.length > 0" id="categories" class="sfcats" aria-labelledby="sfcats-title">
    <div class="sfcats__head">
      <h2 id="sfcats-title" class="sf-h2">Shop by Category</h2>
      <button
        v-if="tiles.length > INITIAL"
        type="button"
        class="sf-more"
        :aria-expanded="expanded"
        aria-controls="sfcats-grid"
        @click="expanded = !expanded"
      >
        {{ expanded ? 'Show fewer' : 'View all categories' }}
        <ArrowRight :size="15" :stroke-width="2" />
      </button>
    </div>

    <ul id="sfcats-grid" class="sfcats__grid">
      <li v-for="tile in shown" :key="tile.id">
        <a class="sfcat" :href="`/?category=${encodeURIComponent(tile.id)}`" @click="open(tile.id, $event)">
          <img v-if="tile.cover" class="sfcat__img" :src="tile.cover" alt="" loading="lazy" />
          <span class="sfcat__shade" aria-hidden="true"></span>
          <span class="sfcat__text">
            <span class="sfcat__name">{{ tile.name }}</span>
            <span class="sfcat__count">{{ tile.count }} item{{ tile.count === 1 ? '' : 's' }}</span>
          </span>
          <span class="sfcat__go" aria-hidden="true">
            <ChevronRight :size="18" :stroke-width="2" />
          </span>
        </a>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.sfcats { margin: 0 0 48px; }

.sfcats__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.sfcats__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

/* The name sits on a dark shade at the foot of the photo, so whatever the
   product shot looks like — most are on white — the words stay legible. */
.sfcat {
  position: relative;
  isolation: isolate;
  display: flex;
  align-items: flex-end;
  aspect-ratio: 16 / 7.4;
  min-height: 110px;
  overflow: hidden;
  border-radius: 8px;
  background: var(--sf-forest-soft);
  color: var(--sf-paper);
  text-decoration: none;
}

.sfcat__img {
  position: absolute;
  inset: 0;
  z-index: -2;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 300ms ease;
}
.sfcat:hover .sfcat__img { transform: scale(1.05); }

.sfcat__shade {
  position: absolute;
  inset: 0;
  z-index: -1;
  /* Darker than a photo tile would usually need: the covers are product
     shots, and most of them are packaging with its own lettering, which a
     light shade leaves fighting the aisle's name. */
  background: linear-gradient(0deg, rgba(23, 35, 28, 0.96) 0%, rgba(23, 35, 28, 0.78) 45%, rgba(23, 35, 28, 0.3) 100%);
}

.sfcat__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 14px 52px 14px 14px;
}

.sfcat__name {
  font-family: var(--sf-serif);
  font-size: 1.15rem;
  font-weight: 700;
  line-height: 1.15;
  overflow-wrap: anywhere;
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.55);
}

.sfcat__count {
  font-size: 12.5px;
  color: rgba(251, 248, 243, 0.82);
}

.sfcat__go {
  position: absolute;
  right: 12px;
  bottom: 14px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1.5px solid rgba(251, 248, 243, 0.85);
  border-radius: 50%;
  transition: background 150ms, border-color 150ms;
}
.sfcat:hover .sfcat__go { background: var(--sf-clay); border-color: var(--sf-clay); }
.sfcat:focus-visible { outline: 3px solid var(--sf-clay); outline-offset: 3px; }

@media (max-width: 1080px) {
  .sfcats__grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .sfcats__grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .sfcat { aspect-ratio: 4 / 3; min-height: 0; }
  .sfcat__text { padding: 12px 44px 12px 12px; }
  .sfcat__name { font-size: 1.02rem; }
  .sfcat__go { right: 10px; bottom: 12px; width: 26px; height: 26px; }
}

@media (prefers-reduced-motion: reduce) {
  .sfcat__img, .sfcat:hover .sfcat__img { transition: none; transform: none; }
}
</style>
