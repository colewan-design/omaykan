<script setup lang="ts">
import { BadgePercent, Bike, ShoppingBasket, Store } from '@lucide/vue'

// The answer to "why not just use GrabMart?", on the page rather than left to
// the visitor to work out.
//
// It sits between the first two product shelves on purpose: it is the reason
// the shelves are worth browsing, and it keeps two near-identical carousels
// from running back to back.

const reasons = [
  {
    icon: Store,
    title: 'Shop local',
    body: 'Order directly from businesses around Baguio and La Trinidad.',
  },
  {
    icon: BadgePercent,
    title: 'Same local prices',
    // "No commission" on its own is a merchant's benefit read out to a
    // shopper. The order matters: the shop sets the price, and the reason it
    // can leave it alone is that nothing is being taken out of the sale.
    body: "Stores set their own prices. Omaykan takes no percentage of a sale, so shops can keep them close to their counter prices.",
  },
  {
    icon: Bike,
    title: 'Nearby delivery',
    // Not "sellers that can serve your area": nothing here filters shops by a
    // delivery radius. Shops are ordered by distance from the pin, and that
    // is all this may promise.
    body: 'Shops are sorted by how close they are, so you order from a counter near you.',
  },
  {
    icon: ShoppingBasket,
    title: 'One marketplace',
    body: 'Groceries, wet-market goods, sari-sari essentials, and specialty stores in one place.',
  },
]
</script>

<template>
  <section class="fd-why" aria-labelledby="fd-why-title">
    <h2 id="fd-why-title" class="fd-why__title">Why shop through Omaykan?</h2>

    <ul class="fd-why__grid">
      <li v-for="reason in reasons" :key="reason.title" class="fd-why__item">
        <span class="fd-why__icon">
          <component :is="reason.icon" :size="22" :stroke-width="1.6" />
        </span>
        <h3 class="fd-why__h">{{ reason.title }}</h3>
        <p class="fd-why__p">{{ reason.body }}</p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.fd-why { margin: 8px 0 52px; }

.fd-why__title {
  margin: 0 0 20px;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #06240f;
}

.fd-why__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin: 0;
  padding: 0;
  list-style: none;
}

/* No card chrome: four boxes here would read as a fifth and sixth shelf. The
   icon carries the separation instead. */
.fd-why__item { min-width: 0; }

.fd-why__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  margin-bottom: 12px;
  border-radius: 999px;
  background: #eef6f0;
  color: #1a6b3c;
}

.fd-why__h {
  margin: 0 0 5px;
  font-size: 15.5px;
  font-weight: 800;
  color: #06240f;
}

.fd-why__p {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: #5b6b60;
}

@media (max-width: 900px) {
  .fd-why__grid { grid-template-columns: 1fr 1fr; gap: 24px 20px; }
}
@media (max-width: 520px) {
  .fd-why { margin-bottom: 36px; }
  .fd-why__grid { grid-template-columns: 1fr; gap: 20px; }
  .fd-why__item { display: grid; grid-template-columns: 42px 1fr; column-gap: 14px; }
  .fd-why__icon { grid-row: span 2; margin-bottom: 0; }
  .fd-why__p { grid-column: 2; }
}
</style>
