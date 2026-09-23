<script setup lang="ts" generic="T extends string">
import { ChevronDown, Plus, Search } from '@lucide/vue'
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

interface Option<T> {
  value: T
  label: string
}

const props = withDefaults(defineProps<{
  modelValue: T
  options: Option<T>[]
  label?: string
  placeholder?: string
  disabled?: boolean
  flat?: boolean
  /**
   * Set to let the person add an option that is not in the list, e.g.
   * "category". The panel then offers "Add <what they typed>" and emits
   * `create` with that name; the parent makes the option and selects it.
   */
  createLabel?: string
}>(), {
  label: '',
  placeholder: 'Search…',
  disabled: false,
  flat: false,
  createLabel: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: T): void
  (e: 'create', name: string): void
}>()

const open = ref(false)
const query = ref('')
const highlightedIndex = ref(0)
const triggerEl = ref<HTMLElement | null>(null)
const searchInputEl = ref<HTMLInputElement | null>(null)
const optionEls = ref<(HTMLLIElement | null)[]>([])
const listboxId = `acselect-${Math.random().toString(36).slice(2, 9)}`

const currentLabel = computed(
  () => props.options.find((o) => o.value === props.modelValue)?.label ?? props.modelValue,
)

const filteredOptions = computed(() => {
  const needle = query.value.trim().toLowerCase()
  if (!needle) return props.options
  return props.options.filter((o) => o.label.toLowerCase().includes(needle))
})

/** What "Add …" would create, or '' when there is nothing new to add. */
const creatableName = computed(() => {
  if (!props.createLabel) return ''
  const name = query.value.trim()
  if (!name) return ''
  const taken = props.options.some((o) => o.label.trim().toLowerCase() === name.toLowerCase())
  return taken ? '' : name
})

function create() {
  const name = creatableName.value
  if (!name) return
  emit('create', name)
  closeMenu()
}

watch(filteredOptions, () => {
  highlightedIndex.value = 0
})

watch(highlightedIndex, async (index) => {
  await nextTick()
  optionEls.value[index]?.scrollIntoView({ block: 'nearest' })
})

async function openMenu() {
  if (props.disabled || open.value) return
  open.value = true
  query.value = ''
  const selectedIndex = props.options.findIndex((o) => o.value === props.modelValue)
  highlightedIndex.value = Math.max(selectedIndex, 0)
  await nextTick()
  searchInputEl.value?.focus()
}

function closeMenu() {
  open.value = false
  query.value = ''
}

function toggle() {
  if (open.value) closeMenu()
  else void openMenu()
}

function select(value: T) {
  emit('update:modelValue', value)
  closeMenu()
}

function onOutsideClick(e: MouseEvent) {
  if (open.value && triggerEl.value && !triggerEl.value.contains(e.target as Node)) {
    closeMenu()
  }
}

function onSearchKeydown(e: KeyboardEvent) {
  const count = filteredOptions.value.length
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (count) highlightedIndex.value = (highlightedIndex.value + 1) % count
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (count) highlightedIndex.value = (highlightedIndex.value - 1 + count) % count
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const opt = filteredOptions.value[highlightedIndex.value]
    if (opt) select(opt.value)
    else create()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    closeMenu()
  }
}

// Use capture so we catch clicks before they bubble up
document.addEventListener('click', onOutsideClick, true)
onUnmounted(() => {
  document.removeEventListener('click', onOutsideClick, true)
})
</script>

<template>
  <div ref="triggerEl" class="acselect" :class="{ 'acselect--open': open, 'acselect--disabled': disabled }">
    <button
      class="acselect__trigger"
      :class="{ 'acselect__trigger--flat': flat }"
      type="button"
      :aria-label="label"
      :aria-expanded="open"
      :aria-controls="listboxId"
      :disabled="disabled"
      @click="toggle"
    >
      <span class="acselect__label">{{ currentLabel }}</span>
      <ChevronDown :size="15" class="acselect__chevron" aria-hidden="true" />
    </button>

    <Transition name="acselect-drop">
      <div v-if="open" class="acselect__panel">
        <label class="acselect__search">
          <Search :size="14" class="acselect__search-icon" aria-hidden="true" />
          <input
            ref="searchInputEl"
            v-model="query"
            class="acselect__search-input"
            type="text"
            role="combobox"
            :placeholder="placeholder"
            :aria-label="label ? `Search ${label}` : 'Search options'"
            aria-autocomplete="list"
            aria-expanded="true"
            :aria-controls="listboxId"
            autocomplete="off"
            @keydown="onSearchKeydown"
          />
        </label>

        <ul :id="listboxId" class="acselect__list" role="listbox" :aria-label="label">
          <li
            v-for="(opt, index) in filteredOptions"
            :key="opt.value"
            :ref="(el) => (optionEls[index] = el as HTMLLIElement)"
            class="acselect__option"
            :class="{
              'acselect__option--active': opt.value === modelValue,
              'acselect__option--highlighted': index === highlightedIndex,
            }"
            role="option"
            :aria-selected="opt.value === modelValue"
            @mouseenter="highlightedIndex = index"
            @click="select(opt.value)"
          >
            {{ opt.label }}
          </li>
          <li v-if="!filteredOptions.length && !creatableName" class="acselect__empty">
            {{ createLabel && !query.trim() ? `Type a name to add a ${createLabel}` : 'No matches' }}
          </li>
          <li
            v-if="creatableName"
            class="acselect__option acselect__create"
            :class="{ 'acselect__option--highlighted': !filteredOptions.length }"
            role="option"
            aria-selected="false"
            @click="create"
          >
            <Plus :size="14" aria-hidden="true" />
            <span>Add {{ createLabel }} "{{ creatableName }}"</span>
          </li>
        </ul>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.acselect {
  position: relative;
  display: inline-flex;
}

.acselect__trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
  height: 44px;
  min-height: 44px;
  padding: 0 var(--space-3);
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
  color: var(--text-primary);
  font: var(--type-subhead);
  cursor: pointer;
  outline: none;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background var(--dur-fast) var(--ease-out);
  white-space: nowrap;
  user-select: none;
}

.acselect__label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.acselect__trigger:focus-visible {
  border-color: var(--accent);
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.acselect__trigger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.acselect--open .acselect__trigger {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--fill) 80%, var(--bg-elevated));
}

.acselect__trigger--flat {
  border-color: transparent;
  background: var(--fill);
}

.acselect--open .acselect__trigger--flat {
  border-color: transparent;
  background: color-mix(in srgb, var(--accent) 14%, var(--fill));
}

.acselect__chevron {
  color: var(--text-tertiary);
  transition: transform var(--dur-fast) var(--ease-out);
  flex-shrink: 0;
}

.acselect--open .acselect__chevron {
  transform: rotate(180deg);
}

.acselect__panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 200;
  min-width: 100%;
  width: max-content;
  max-width: 280px;
  background: var(--bg-elevated);
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.acselect__search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 38px;
  padding: 0 var(--space-3);
  border-bottom: 0.5px solid var(--separator);
  background: var(--fill);
}

.acselect__search-icon {
  flex: none;
  color: var(--text-tertiary);
}

.acselect__search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font: var(--type-subhead);
}

.acselect__search-input::placeholder {
  color: var(--text-tertiary);
}

.acselect__list {
  margin: 0;
  padding: var(--space-1) 0;
  list-style: none;
  max-height: 240px;
  overflow-y: auto;
}

.acselect__option {
  display: flex;
  align-items: center;
  min-height: 38px;
  padding: 0 var(--space-4);
  font: var(--type-subhead);
  color: var(--text-primary);
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out);
}

.acselect__option--highlighted {
  background: var(--fill);
}

.acselect__option--active {
  color: var(--accent);
  font-weight: 600;
}

.acselect__create {
  gap: var(--space-2);
  color: var(--accent);
  font-weight: 600;
}

.acselect__create span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.acselect__empty {
  padding: var(--space-3) var(--space-4);
  color: var(--text-tertiary);
  font: var(--type-subhead);
}

/* ── Transition ─────────────────────────────────────────────────────────────── */

.acselect-drop-enter-active,
.acselect-drop-leave-active {
  transition:
    opacity var(--dur-fast) var(--ease-out),
    transform var(--dur-fast) var(--ease-out);
}

.acselect-drop-enter-from,
.acselect-drop-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.97);
}
</style>
