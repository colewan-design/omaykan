<script setup lang="ts">
import { Crown } from '@lucide/vue'
import MountainMark from '@pos/web/landing/MountainMark.vue'
import { srcSet } from '@pos/web/ui/responsiveImg'

/**
 * What a founding seller's badge looks like, shown beside the hero.
 *
 * The number is an illustration and nothing else. A real badge is assigned by
 * an operator when an application is accepted (`founding_number`, null until
 * then), so this page has no way to know what anyone's would be — and a card
 * that looked like a reservation would be exactly the fake-urgency trick the
 * campaign is meant to avoid. Hence `role="img"` with a label that says
 * "example", and a caption that talks about the badge rather than the number.
 */
const PHOTO = '/storefront/market-stall-cover.jpg'
</script>

<template>
  <figure class="badge" role="img" aria-label="Example of the Omaykan founding-seller badge, shown on a market stall's shop card">
    <div class="badge__ribbon">
      <Crown :size="16" :stroke-width="2.2" aria-hidden="true" />
      <span>Omaykan Founding Seller #07</span>
    </div>

    <div class="badge__frame">
      <img
        class="badge__photo"
        :src="PHOTO"
        :srcset="srcSet(PHOTO)"
        sizes="(max-width: 1080px) 90vw, 420px"
        width="420"
        height="300"
        alt=""
        loading="eager"
        decoding="async"
      />
      <p class="badge__sign">
        Support<br />local<br />Baguio
      </p>
    </div>

    <figcaption class="badge__caption">
      <span class="badge__script">A Real Local Business<br />A Stronger Community</span>
      <MountainMark :size="52" />
    </figcaption>
  </figure>
</template>

<style scoped>
.badge {
  position: relative;
  margin: 0;
  width: min(100%, 420px);
  padding: 14px 14px 12px;
  border-radius: 20px;
  background: var(--sf-paper);
  box-shadow: 0 26px 60px -28px rgba(13, 52, 36, 0.55), 0 2px 10px rgba(13, 52, 36, 0.08);
  /* A shade off square, the way a card pinned to a board sits. Small enough
     that the photograph's own horizon still reads as level. */
  transform: rotate(-1.4deg);
}

.badge__ribbon {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: -30px auto 12px;
  padding: 10px 18px;
  width: fit-content;
  max-width: 100%;
  border-radius: 999px;
  background: var(--sf-forest);
  color: #fff;
  font-size: 13.5px;
  font-weight: 800;
  letter-spacing: -0.01em;
  line-height: 1.2;
  text-align: center;
  box-shadow: 0 8px 20px -10px rgba(13, 52, 36, 0.8);
}

.badge__ribbon :deep(svg) {
  color: var(--sf-gold);
  flex-shrink: 0;
}

.badge__frame {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  background: var(--sf-sand);
}

.badge__photo {
  width: 100%;
  height: auto;
  aspect-ratio: 7 / 5;
  object-fit: cover;
}

/* The hand-lettered card propped against the produce in the photograph. */
.badge__sign {
  position: absolute;
  right: 14px;
  bottom: 14px;
  margin: 0;
  padding: 10px 14px;
  border-radius: 6px;
  background: #fdf6e8;
  color: #8a5a1f;
  font-family: var(--sf-script);
  font-size: 19px;
  font-weight: 700;
  line-height: 1.05;
  text-align: center;
  text-transform: uppercase;
  transform: rotate(3deg);
  box-shadow: 0 6px 16px -8px rgba(35, 29, 24, 0.7);
}

.badge__caption {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 6px 2px;
  color: var(--sf-forest);
}

.badge__script {
  font-family: var(--sf-script);
  font-size: 21px;
  font-weight: 700;
  line-height: 1.15;
}

.badge__caption :deep(.mtn) {
  color: var(--sf-forest-soft);
  opacity: 0.85;
}

@media (max-width: 1080px) {
  .badge {
    margin-inline: auto;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .badge { transform: none; }
}
</style>
