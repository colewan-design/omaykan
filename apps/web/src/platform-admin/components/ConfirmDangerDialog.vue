<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/*
 * Retype-to-confirm, for the one action in the portal that cannot be undone.
 *
 * The typed phrase is the whole point: a misclick on the wrong row in a list
 * of tenants would otherwise destroy a merchant's entire history, and every
 * other dialog in this product is dismissable with Enter. The server checks
 * the same thing again — this is the part that makes the operator read the
 * name of what they are about to delete.
 */

const props = defineProps<{
  title: string
  copy: string
  /** The exact string the operator has to type. */
  confirmPhrase: string
  busy?: boolean
}>()

const emit = defineEmits<{ cancel: []; confirm: [] }>()

const typed = ref('')

// Reset whenever the dialog is pointed at a different target, so a phrase
// typed for one tenant can never confirm another.
watch(() => props.confirmPhrase, () => { typed.value = '' })

const matches = computed(() => typed.value.trim() === props.confirmPhrase)
</script>

<template>
  <div class="pa-scrim" @click.self="emit('cancel')">
    <div class="pa-dialog" role="dialog" aria-modal="true">
      <h2>{{ title }}</h2>
      <p class="pa-dialog__copy">{{ copy }}</p>

      <label class="pa-field">
        <span class="pa-field__label">Type <code>{{ confirmPhrase }}</code> to confirm</span>
        <input v-model="typed" class="pa-input" type="text" autocomplete="off" spellcheck="false">
      </label>

      <div class="pa-dialog__actions">
        <button class="pa-button pa-button--quiet" type="button" @click="emit('cancel')">Cancel</button>
        <button
          class="pa-button pa-button--danger"
          type="button"
          :disabled="!matches || busy"
          @click="emit('confirm')"
        >
          {{ busy ? 'Deleting…' : 'Delete permanently' }}
        </button>
      </div>
    </div>
  </div>
</template>
