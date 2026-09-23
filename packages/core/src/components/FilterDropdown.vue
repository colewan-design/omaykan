<script setup lang="ts" generic="T extends string">
import { Check, ChevronDown } from '@lucide/vue'
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

/**
 * A filter chip that opens its own menu, in place of a native <select>.
 *
 * The native one only opened when the click landed on the <select> itself, so
 * the chevron next to it looked like a control and did nothing. Here the whole
 * chip is one button — icon, label, value and chevron — so any part of it
 * opens the menu.
 *
 * The menu is teleported to <body> and positioned against the chip, so a card
 * with `overflow: hidden` somewhere above it cannot clip it.
 *
 * `modelValue` is optional: leave it out and the options behave as actions
 * (the Selection chip), with no checkmark on any of them.
 */

export interface FilterDropdownOption<V extends string> {
  value: V
  label: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  modelValue?: T | null
  options: FilterDropdownOption<T>[]
  label: string
  /** Shown in the chip. Defaults to the selected option's label. */
  display?: string
  align?: 'start' | 'end'
}>(), {
  modelValue: null,
  display: undefined,
  align: 'start',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: T): void
  (e: 'select', value: T): void
}>()

const open = ref(false)
const highlighted = ref(0)
const rootEl = ref<HTMLElement | null>(null)
const triggerEl = ref<HTMLButtonElement | null>(null)
const menuEl = ref<HTMLElement | null>(null)
const optionEls = ref<(HTMLElement | null)[]>([])
const menuStyle = ref<Record<string, string>>({})
const menuId = `fdrop-${Math.random().toString(36).slice(2, 9)}`

const shown = computed(() =>
  props.display ?? props.options.find((option) => option.value === props.modelValue)?.label ?? '',
)

function position() {
  const trigger = triggerEl.value
  if (!trigger) return

  const rect = trigger.getBoundingClientRect()
  const gutter = 8
  const width = Math.max(rect.width, 200)
  const maxLeft = window.innerWidth - width - gutter
  const left = props.align === 'end' ? rect.right - width : rect.left
  const below = window.innerHeight - rect.bottom
  const flip = below < 260 && rect.top > below

  menuStyle.value = {
    left: `${Math.max(gutter, Math.min(left, maxLeft))}px`,
    width: `${Math.min(width, window.innerWidth - gutter * 2)}px`,
    ...(flip
      ? { bottom: `${window.innerHeight - rect.top + 6}px`, maxHeight: `${rect.top - 14}px` }
      : { top: `${rect.bottom + 6}px`, maxHeight: `${below - 14}px` }),
  }
}

function firstEnabled(from: number, step: 1 | -1) {
  const count = props.options.length
  for (let i = 0; i < count; i++) {
    const index = (from + step * i + count * 2) % count
    if (!props.options[index]?.disabled) return index
  }
  return -1
}

async function openMenu() {
  if (open.value) return
  const selected = props.options.findIndex((option) => option.value === props.modelValue)
  highlighted.value = firstEnabled(Math.max(selected, 0), 1)
  open.value = true
  position()
  await nextTick()
  menuEl.value?.focus()
}

function closeMenu(refocus = false) {
  if (!open.value) return
  open.value = false
  if (refocus) triggerEl.value?.focus()
}

function toggle() {
  if (open.value) closeMenu()
  else void openMenu()
}

function choose(option: FilterDropdownOption<T>) {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  emit('select', option.value)
  closeMenu(true)
}

function move(step: 1 | -1) {
  const next = firstEnabled(highlighted.value + step, step)
  if (next >= 0) highlighted.value = next
}

function onTriggerKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    void openMenu()
  }
}

function onMenuKeydown(event: KeyboardEvent) {
  switch (event.key) {
    case 'ArrowDown': event.preventDefault(); move(1); break
    case 'ArrowUp': event.preventDefault(); move(-1); break
    case 'Home': event.preventDefault(); highlighted.value = firstEnabled(0, 1); break
    case 'End': event.preventDefault(); highlighted.value = firstEnabled(props.options.length - 1, -1); break
    case 'Enter':
    case ' ': {
      event.preventDefault()
      const option = props.options[highlighted.value]
      if (option) choose(option)
      break
    }
    case 'Escape': event.preventDefault(); closeMenu(true); break
    case 'Tab': closeMenu(); break
  }
}

function onPointerDown(event: PointerEvent) {
  const target = event.target as Node
  if (rootEl.value?.contains(target) || menuEl.value?.contains(target)) return
  closeMenu()
}

function onViewportChange() {
  if (open.value) position()
}

watch(highlighted, async (index) => {
  await nextTick()
  optionEls.value[index]?.scrollIntoView({ block: 'nearest' })
})

function unlisten() {
  document.removeEventListener('pointerdown', onPointerDown, true)
  window.removeEventListener('resize', onViewportChange)
  window.removeEventListener('scroll', onViewportChange, true)
}

// Listening only while open: a toolbar of six of these should cost nothing
// on every scroll when none of them is showing.
watch(open, (isOpen) => {
  if (!isOpen) return unlisten()
  document.addEventListener('pointerdown', onPointerDown, true)
  window.addEventListener('resize', onViewportChange)
  window.addEventListener('scroll', onViewportChange, true)
})

onUnmounted(unlisten)
</script>

<template>
  <div ref="rootEl" class="fdrop" :class="{ 'fdrop--open': open }">
    <button
      ref="triggerEl"
      class="pfilter fdrop__trigger"
      type="button"
      aria-haspopup="listbox"
      :aria-expanded="open"
      :aria-controls="menuId"
      :aria-label="`${label}: ${shown}`"
      @click="toggle"
      @keydown="onTriggerKeydown"
    >
      <slot name="icon" />
      <span><small>{{ label }}</small><strong>{{ shown }}</strong></span>
      <ChevronDown :size="15" class="fdrop__chevron" aria-hidden="true" />
    </button>

    <Teleport to="body">
      <Transition name="fdrop-menu">
        <ul
          v-if="open"
          :id="menuId"
          ref="menuEl"
          class="fdrop__menu"
          :style="menuStyle"
          role="listbox"
          tabindex="-1"
          :aria-label="label"
          :aria-activedescendant="highlighted >= 0 ? `${menuId}-${highlighted}` : undefined"
          @keydown="onMenuKeydown"
        >
          <li
            v-for="(option, index) in options"
            :id="`${menuId}-${index}`"
            :key="option.value"
            :ref="(el) => (optionEls[index] = el as HTMLElement | null)"
            class="fdrop__option"
            :class="{
              'fdrop__option--highlighted': index === highlighted,
              'fdrop__option--selected': option.value === modelValue,
              'fdrop__option--disabled': option.disabled,
            }"
            role="option"
            :aria-selected="option.value === modelValue"
            :aria-disabled="option.disabled || undefined"
            @pointerenter="!option.disabled && (highlighted = index)"
            @click="choose(option)"
          >
            <span>{{ option.label }}</span>
            <Check v-if="option.value === modelValue" :size="15" aria-hidden="true" />
          </li>
        </ul>
      </Transition>
    </Teleport>
  </div>
</template>
