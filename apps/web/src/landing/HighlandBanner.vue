<script setup lang="ts">
import { SIZES, srcSet } from '@pos/web/ui/responsiveImg'

// The strip of highland across the top of the aisle listing, where the listing
// redesign puts a photograph of terraced mountains.
//
// Like the front page's hero, the photograph is a named slot rather than a
// chosen asset: whatever file is at /storefront/listing-highland.webp is the
// banner, and replacing it is a file drop with no code change. It is framed
// with object-fit: cover against a fixed strip height, so any wide landscape
// will sit. The strip is far wider than it is tall, so the crop keeps a band
// just below the middle — where today's photograph carries its terraces — and
// lets the sky and the near trees go first.
//
// It used to be an SVG drawing of layered ridges, standing in because none of
// the site's own photographs was a clear landscape. This one is, so the
// drawing is gone.

const BANNER_IMAGE = '/storefront/listing-highland.webp'
</script>

<template>
  <!-- Decorative: the listing's own <h1> names what is below it. -->
  <div class="hlb" aria-hidden="true">
    <img
      class="hlb__img"
      :src="BANNER_IMAGE"
      :srcset="srcSet(BANNER_IMAGE)"
      :sizes="SIZES.full"
      alt=""
      decoding="async"
      fetchpriority="high"
    />
  </div>
</template>

<style scoped>
.hlb {
  height: clamp(140px, 19vw, 290px);
  overflow: hidden;
  background: #efe3cc;
}

.hlb__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 60%;
}
</style>
