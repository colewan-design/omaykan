<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import { pageWindow } from './listing'

// Numbered pages under the listing, as the redesign has them. Buttons, not
// links: the landing page writes ?page= itself, alongside the rest of the
// listing's state, so Back still steps back a page.

const props = defineProps<{ page: number; total: number }>()

const emit = defineEmits<{ go: [page: number] }>()

const pages = computed(() => pageWindow(props.page, props.total))

function go(page: number) {
  if (page !== props.page && page >= 1 && page <= props.total) emit('go', page)
}
</script>

<template>
  <nav class="pager" aria-label="Pages">
    <button
      type="button"
      class="pager__btn"
      :disabled="page <= 1"
      aria-label="Previous page"
      @click="go(page - 1)"
    >
      <ChevronLeft :size="18" :stroke-width="2" />
    </button>

    <template v-for="(entry, i) in pages" :key="entry === 'gap' ? `gap-${i}` : entry">
      <span v-if="entry === 'gap'" class="pager__gap" aria-hidden="true">…</span>
      <button
        v-else
        type="button"
        class="pager__btn"
        :class="{ 'pager__btn--on': entry === page }"
        :aria-current="entry === page ? 'page' : undefined"
        :aria-label="`Page ${entry}`"
        @click="go(entry)"
      >
        {{ entry }}
      </button>
    </template>

    <button
      type="button"
      class="pager__btn"
      :disabled="page >= total"
      aria-label="Next page"
      @click="go(page + 1)"
    >
      <ChevronRight :size="18" :stroke-width="2" />
    </button>
  </nav>
</template>

<style scoped>
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 36px;
}

.pager__btn {
  display: grid;
  place-items: center;
  min-width: 38px;
  height: 38px;
  padding: 0 8px;
  border: 1px solid var(--sf-rule);
  border-radius: 8px;
  background: var(--sf-paper);
  color: var(--sf-ink);
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: border-color 150ms, background 150ms, color 150ms;
}
.pager__btn:hover:not(:disabled) { border-color: var(--sf-forest); }
.pager__btn:focus-visible { outline: 2px solid var(--sf-clay); outline-offset: 2px; }
.pager__btn:disabled { opacity: 0.4; cursor: default; }

.pager__btn--on {
  border-color: var(--sf-forest);
  background: var(--sf-forest);
  color: var(--sf-paper);
  cursor: default;
}

.pager__gap { min-width: 20px; text-align: center; color: var(--sf-faint); }

@media (max-width: 420px) {
  .pager { gap: 6px; }
  .pager__btn { min-width: 36px; height: 36px; }
}
</style>
