<script setup lang="ts">
import type { Product } from '@pos/shared/index'
import ProductCard from './ProductCard.vue'

// The category listing: every product in the aisle, wrapping down the page,
// as against ProductRow's shelf which shows a curated dozen sideways.
//
// Tracks are minmax(158px, 1fr) so a full row always spans the gutter width;
// the shelf can't do that (a short shelf would blow its cards up), but a grid
// is never short — it wraps.

defineProps<{ products: Product[] }>()

defineEmits<{ select: [productId: string] }>()
</script>

<template>
  <div class="fdgrid">
    <ProductCard
      v-for="product in products"
      :key="product.id"
      :product="product"
      @select="$emit('select', $event)"
    />
  </div>
</template>

<style scoped>
.fdgrid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(158px, 1fr));
  gap: 34px 20px;
}

@media (max-width: 720px) {
  .fdgrid {
    grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
    gap: 26px 14px;
  }
}
</style>
