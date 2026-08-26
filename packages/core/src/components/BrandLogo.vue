<script setup lang="ts">
import { computed } from 'vue'

// Brand lockup: the "omaykan" lettering, deep green into gold, with the leaf
// growing out of the y.
//
// This was a basket mark beside a wordmark typeset in Nunito. It is one piece
// of artwork now, so the lockup can't drift when the font is slow or missing.
// Two files rather than one recolour: the green half of the lettering is a
// near match for the deep-green bar it sits on in the `dark` variant, so that
// file lifts it to the lime those surfaces already use.
const props = withDefaults(
  defineProps<{
    /** `dark` = sits on the deep-green bar; `light` = on white. */
    variant?: 'dark' | 'light'
    /** Height of the lowercase letters in px; the lockup scales with it. */
    size?: number
  }>(),
  { variant: 'dark', size: 19 },
)

// The artwork is 640x149, and the lowercase letters fill 84 of those 149 rows —
// the rest is the k's ascender and the leaf under the y. Scaling off the letters
// rather than the file keeps `size` meaning what it did when the word was live
// text, so no caller had to change its number.
const height = computed(() => Math.round((props.size * 149) / 84))
const width = computed(() => Math.round((height.value * 640) / 149))

// Sized inline rather than from the stylesheet on purpose. Every landing
// surface ships `img { max-width: 100% }`, so an <img> that loses its size rule
// for any reason doesn't fall back to something small — it falls back to its
// 640px intrinsic width and fills the column it sits in.
const box = computed(() => ({ width: `${width.value}px`, height: `${height.value}px` }))
</script>

<template>
  <img
    class="brand"
    :src="variant === 'dark' ? '/logo-wordmark-light.png' : '/logo-wordmark.png'"
    alt="Omaykan"
    :width="width"
    :height="height"
    :style="box"
  />
</template>

<style scoped>
.brand {
  display: block;
  flex-shrink: 0;
}
</style>
