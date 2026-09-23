<script setup lang="ts">
import { SIZES, srcSet } from '@pos/web/ui/responsiveImg'

// The band at the top of every operator screen: the page's name over a strip
// of highland, with whatever controls the page needs sitting to its right.
//
// The photograph is the same file the storefront's aisle listing uses. That is
// deliberate rather than lazy — it is already in the browser's cache for
// anyone who has looked at the shop, it is the one image on this site that is
// unmistakably the Cordillera, and pointing both at one path means a new
// photograph replaces it in both places at once.

defineProps<{
  title: string
  subtitle?: string
}>()

const BANNER = '/storefront/listing-highland.webp'
</script>

<template>
  <header class="hero">
    <img
      class="hero__img"
      :src="BANNER"
      :srcset="srcSet(BANNER)"
      :sizes="SIZES.full"
      alt=""
      decoding="async"
    />
    <!-- The scrim, not the photo, is what makes the text legible: a band this
         short crops to a different part of the image at every width, so the
         contrast behind the words cannot be left to the photograph. -->
    <div class="hero__scrim" aria-hidden="true"></div>

    <div class="hero__inner">
      <div class="hero__copy">
        <h1 class="hero__title">{{ title }}</h1>
        <p v-if="subtitle" class="hero__sub">{{ subtitle }}</p>
      </div>

      <div class="hero__tools">
        <slot name="tools" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.hero {
  position: relative;
  margin: var(--adm-gutter) var(--adm-gutter) 0;
  border-radius: var(--adm-radius);
  overflow: hidden;
  background: var(--sf-forest);
  isolation: isolate;
}

.hero__img,
.hero__scrim {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.hero__img {
  object-fit: cover;
  object-position: 50% 58%;
}

.hero__scrim {
  background: linear-gradient(100deg, rgba(23, 35, 28, 0.92) 12%, rgba(23, 35, 28, 0.58) 58%, rgba(23, 35, 28, 0.3) 100%);
}

.hero__inner {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  min-height: 104px;
  padding: 20px 22px;
}

.hero__copy {
  min-width: 0;
}

.hero__title {
  margin: 0;
  font-family: var(--sf-serif);
  font-size: clamp(1.35rem, 2.4vw, 1.75rem);
  font-weight: 700;
  line-height: 1.15;
  color: #fff;
}

.hero__sub {
  margin: 6px 0 0;
  max-width: 56ch;
  color: rgba(246, 241, 232, 0.86);
  font-size: 13.5px;
  line-height: 1.45;
}

.hero__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

@media (max-width: 560px) {
  .hero {
    margin: 16px 16px 0;
  }

  .hero__inner {
    padding: 16px;
  }
}
</style>
