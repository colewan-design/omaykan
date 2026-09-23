// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'

/**
 * The product sheet's category picker adds a category in place, since a new
 * shop has none and a product cannot be saved without one.
 */

let wrapper: VueWrapper | null = null

async function openWith(options: Array<{ value: string; label: string }>, createLabel = 'category') {
  wrapper = mount(AutocompleteSelect, {
    props: { modelValue: '', options, label: 'Category', createLabel },
    attachTo: document.body,
  })
  await wrapper.find('.acselect__trigger').trigger('click')
  return wrapper
}

describe('adding an option from the picker', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('asks for a name when there is nothing to pick yet', async () => {
    const picker = await openWith([])

    expect(picker.find('.acselect__empty').text()).toBe('Type a name to add a category')
  })

  it('offers what was typed and emits it', async () => {
    const picker = await openWith([])
    await picker.find('input').setValue('  Coffee ')

    const add = picker.find('.acselect__create')
    expect(add.text()).toBe('Add category "Coffee"')
    await add.trigger('click')

    expect(picker.emitted('create')).toEqual([['Coffee']])
  })

  it('adds on Enter when nothing matches', async () => {
    const picker = await openWith([{ value: 'a', label: 'Tea' }])
    const input = picker.find('input')
    await input.setValue('Coffee')
    await input.trigger('keydown', { key: 'Enter' })

    expect(picker.emitted('create')).toEqual([['Coffee']])
  })

  it('does not offer a name that already exists', async () => {
    const picker = await openWith([{ value: 'a', label: 'Coffee' }])
    await picker.find('input').setValue('coffee')

    expect(picker.find('.acselect__create').exists()).toBe(false)
  })

  it('stays a plain picker without a createLabel', async () => {
    const picker = await openWith([], '')
    await picker.find('input').setValue('Coffee')

    expect(picker.find('.acselect__create').exists()).toBe(false)
    expect(picker.find('.acselect__empty').text()).toBe('No matches')
  })
})
