<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Camera } from '@lucide/vue'

/*
 * One document photo: the licence, or the plate.
 *
 * Its own component because the two are identical in everything but their
 * words, and because the checking has to happen here rather than at submit.
 * A rider on mobile data who picks a 12MB photo should be told before the
 * upload, not after it has spent a minute of their signal to come back 422.
 *
 * The limits mirror RiderAuthController exactly. They are a courtesy, not a
 * control — the server checks the decoded image, which is the check that
 * matters; this one only saves the round trip.
 */

/** `mimes:jpeg,jpg,png,webp` on the API side. */
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp']

/** `max:8192`, in KB, on the API side. */
const MAX_BYTES = 8192 * 1024

const props = defineProps<{
  modelValue: File | null
  label: string
  hint: string
  /** A message from the server's 422, shown under the control. */
  error?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [File | null]
}>()

const localError = ref('')
const previewUrl = ref('')

const shownError = computed(() => localError.value || props.error || '')

function readableSize(bytes: number): string {
  const mb = bytes / (1024 * 1024)
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

/**
 * The preview is an object URL, so it has to be released by hand — one per
 * retaken photo, and a rider correcting a blurry shot four times would
 * otherwise leave four full-size images pinned in memory on a phone.
 */
function setPreview(file: File | null) {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = file ? URL.createObjectURL(file) : ''
}

function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null

  if (file === null) return

  if (!ACCEPTED.includes(file.type)) {
    localError.value = 'That needs to be a photo — JPG, PNG or WebP.'
    // Cleared so picking the same bad file again still fires `change`, and so
    // a rejected file is never left sitting in the form as if it were valid.
    input.value = ''
    emit('update:modelValue', null)
    setPreview(null)
    return
  }

  if (file.size > MAX_BYTES) {
    localError.value = `That photo is ${readableSize(file.size)}. The limit is 8 MB — try your camera's normal quality setting.`
    input.value = ''
    emit('update:modelValue', null)
    setPreview(null)
    return
  }

  localError.value = ''
  emit('update:modelValue', file)
  setPreview(file)
}

// Keeps the preview honest when the parent clears the form after a successful
// submit, rather than leaving the last photo on screen under an empty field.
watch(
  () => props.modelValue,
  (file) => {
    if (file === null && previewUrl.value) setPreview(null)
  },
)

onBeforeUnmount(() => setPreview(null))
</script>

<template>
  <div class="rdr-doc">
    <span class="rdr-field__label">{{ label }}</span>

    <label>
      <!--
        The native input stays in the DOM and keeps the click, the focus ring
        and the screen-reader announcement; only its appearance is replaced.

        No `capture` attribute on purpose: it sends some browsers straight to
        the camera with no way back to the gallery, and plenty of riders
        already have a photo of their licence on the phone.
      -->
      <input
        class="rdr-doc__input"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        @change="onPick"
      >
      <span
        class="rdr-doc__drop"
        :class="{
          'rdr-doc__drop--filled': modelValue !== null,
          'rdr-doc__drop--error': shownError !== '',
        }"
      >
        <img v-if="previewUrl" class="rdr-doc__thumb" :src="previewUrl" alt="">
        <span v-else class="rdr-doc__icon">
          <Camera :size="26" :stroke-width="1.5" />
        </span>

        <span class="rdr-doc__body">
          <template v-if="modelValue">
            <span class="rdr-doc__name">Photo attached</span>
            <span class="rdr-doc__note">{{ modelValue.name }} · {{ readableSize(modelValue.size) }}</span>
            <span class="rdr-doc__swap">Take another</span>
          </template>
          <template v-else>
            <span class="rdr-doc__name">Add a photo</span>
            <span class="rdr-doc__note">{{ hint }}</span>
          </template>
        </span>
      </span>
    </label>

    <span v-if="shownError" class="rdr-field__error">{{ shownError }}</span>
  </div>
</template>
