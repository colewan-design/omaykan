<script setup lang="ts">
import { Check, Copy } from '@lucide/vue'
import { ref } from 'vue'

/*
 * A password shown exactly once.
 *
 * Two callers: resetting a merchant owner's login, and creating an operator.
 * Both hand the value to a person who relays it by chat or over the phone, and
 * nothing on the server stored the plaintext — so this panel is the only place
 * it will ever exist. That is why the copy says so, and why dismissing it is a
 * deliberate click rather than a timeout.
 */

const props = defineProps<{ title: string; value: string }>()
const emit = defineEmits<{ done: [] }>()

const copied = ref(false)

async function copy() {
  try {
    await navigator.clipboard.writeText(props.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    // Clipboard permission denied — the value is still on screen to read.
  }
}
</script>

<template>
  <div class="pa-reveal">
    <div class="pa-reveal__copy">
      <p class="pa-reveal__title">{{ title }}</p>
      <p>Share this now — it won't be shown again.</p>
    </div>
    <div class="pa-reveal__value">{{ value }}</div>
    <button class="pa-button pa-button--quiet" type="button" @click="copy">
      <Check v-if="copied" :size="15" />
      <Copy v-else :size="15" />
      <span>{{ copied ? 'Copied' : 'Copy' }}</span>
    </button>
    <button class="pa-button pa-button--quiet" type="button" @click="emit('done')">Done</button>
  </div>
</template>
