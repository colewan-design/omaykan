<script setup lang="ts">
// The three steps, for a service nobody has used before.
//
// Numbered rather than iconed on purpose: WhyOmaykan sits a shelf below with a
// row of outlined icons, and two icon grids that close together read as the
// same section twice. A numeral also says "these happen in order", which is
// the whole point of this block.

const steps = [
  {
    title: 'Choose your location',
    // Not "see shops that deliver to your area": nothing filters the list by a
    // delivery radius. What is true is the ordering, and the flag on the ones
    // past DELIVERY_MAX_KM that checkout would refuse.
    body: 'Set where you are ordering to. Shops are sorted by how close they are, and any too far to deliver are marked.',
  },
  {
    title: 'Order from a local store',
    body: 'Browse a real shop’s shelves at the prices it charges at its own counter.',
  },
  {
    title: 'Get it delivered',
    body: 'The shop packs your order and a local rider brings it over. Pay cash or GCash at the door — nothing is charged online.',
  },
]
</script>

<template>
  <section class="fd-how" aria-labelledby="fd-how-title">
    <h2 id="fd-how-title" class="fd-how__title">How Omaykan works</h2>

    <ol class="fd-how__steps">
      <li v-for="(step, i) in steps" :key="step.title" class="fd-how__step">
        <span class="fd-how__n" aria-hidden="true">{{ i + 1 }}</span>
        <h3 class="fd-how__h">{{ step.title }}</h3>
        <p class="fd-how__p">{{ step.body }}</p>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.fd-how {
  margin: 8px 0 52px;
  padding: 30px 32px;
  border-radius: 14px;
  background: #f5f9f6;
}

.fd-how__title {
  margin: 0 0 22px;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #06240f;
}

.fd-how__steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 28px;
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: none;
}

/* The rule between steps is the sequence made visible; it stops at the last
   one so the row does not look like it continues off the edge. */
.fd-how__step { position: relative; min-width: 0; }
.fd-how__step:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 19px;
  left: 50px;
  right: -28px;
  height: 1.5px;
  background: #d7e7dd;
}

.fd-how__n {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  margin-bottom: 14px;
  border-radius: 999px;
  background: #1a6b3c;
  color: #bbf451;
  font-size: 17px;
  font-weight: 800;
}

.fd-how__h {
  margin: 0 0 5px;
  font-size: 15.5px;
  font-weight: 800;
  color: #06240f;
}

.fd-how__p {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: #5b6b60;
}

@media (max-width: 860px) {
  .fd-how { padding: 24px; }
  .fd-how__steps { grid-template-columns: 1fr; gap: 20px; }
  /* Stacked, the horizontal rule would point at nothing. */
  .fd-how__step:not(:last-child)::after { display: none; }
  .fd-how__step { display: grid; grid-template-columns: 38px 1fr; column-gap: 14px; }
  .fd-how__n { grid-row: span 2; margin-bottom: 0; }
  .fd-how__p { grid-column: 2; }
}
</style>
