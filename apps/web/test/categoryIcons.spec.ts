import { describe, expect, it } from 'vitest'
import { categoryIcon } from '@pos/core/utils/categoryIcons'

/*
 * The mirror of apps/mobile-android/.../CategoryIconsTest.kt, guarding the same
 * bug on the same data.
 *
 * These are the `name` column of every category in
 * backend/database/seeders/data/demo-catalog.json — the strings the API
 * actually serves. That file's `id` slugs stop at the seeder, which
 * firstOrCreates on organization + name and lets PostgreSQL mint the uuid. An
 * earlier cut of categoryIcon keyed on those slugs, so it matched nothing in
 * production while compiling and shipping every glyph: the storefront nav drew
 * one fallback basket for all eleven grocery aisles.
 */
const SEEDED_NAMES = [
  'Coffee', 'Tea', 'Pastry', 'Cold Drinks',
  'Groceries', 'Produce', 'Dairy', 'Snacks',
  'Meat & Seafood', 'Bakery', 'Frozen', 'International',
  'Ready to Cook', 'Ready to Eat',
  'Starters', 'Mains', 'Desserts', 'Beverages',
  'Manicures', 'Pedicures', 'Enhancements', 'Add-ons', 'Retail',
]

describe('categoryIcon', () => {
  it('gives every seeded aisle a glyph', () => {
    expect(SEEDED_NAMES.filter((name) => categoryIcon(name) === null)).toEqual([])
  })

  it('gives each seeded aisle its own glyph', () => {
    const icons = SEEDED_NAMES.map((name) => categoryIcon(name))
    expect(new Set(icons).size).toBe(icons.length)
  })

  it('matches through casing and stray whitespace', () => {
    expect(categoryIcon('  cold   drinks ')).toBe(categoryIcon('Cold Drinks'))
    expect(categoryIcon('COFFEE')).toBe(categoryIcon('Coffee'))
    expect(categoryIcon('meat and seafood')).toBe(categoryIcon('Meat & Seafood'))
  })

  it("leaves a merchant's own aisle to the caller's fallback", () => {
    expect(categoryIcon('Vape')).toBeNull()
    expect(categoryIcon('Hardware')).toBeNull()
    // Substrings must not match: a freezer aisle and a dessert are not the same
    // shelf, and the fallback beats confidently drawing the wrong glyph.
    expect(categoryIcon('Frozen')).not.toBeNull()
    expect(categoryIcon('Frozen Yogurt')).toBeNull()
  })

  it('never keys on the seeded slugs, which the API does not serve', () => {
    for (const slug of ['meat-seafood', 'ready-to-cook', 'cold-drinks', 'nail-enhancements']) {
      expect(categoryIcon(slug)).toBeNull()
    }
    // And a uuid, which is what `category.id` actually is.
    expect(categoryIcon('0199f3ab-6c1e-7a45-9d2f-1b8e4c0a77d3')).toBeNull()
  })
})
