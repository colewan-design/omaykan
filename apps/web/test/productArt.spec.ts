import { describe, expect, it } from 'vitest'
import { categoryIcon } from '@pos/core/utils/categoryIcons'
import { ART_TONES, artTone, productArt } from '@pos/web/landing/productArt'

/*
 * The fallback chain a product tile draws when the merchant never photographed
 * the product — which, on a real sari-sari shelf typed in at the counter, is
 * most of the catalog.
 */

const bare = { id: 'p-1' }
const photographed = { id: 'p-2', imageUrl: 'https://cdn.example/tinapa.png' }
const SHOP_PHOTO = '/api/stores/abc/image?v=1a2b3c4d'

describe('productArt', () => {
  it("prefers the product's own photograph to anything drawn", () => {
    expect(productArt(photographed, 'Produce', SHOP_PHOTO)).toEqual({
      kind: 'photo',
      src: 'https://cdn.example/tinapa.png',
    })
  })

  it("draws the aisle's glyph when the product has no photo", () => {
    const art = productArt(bare, 'Meat & Seafood')
    expect(art.kind).toBe('aisle')
    // The same glyph the header menu and the aisle tiles give that aisle, so a
    // category means one picture everywhere on the site.
    expect(art).toMatchObject({ icon: categoryIcon('Meat & Seafood') })
  })

  it("falls back to the Omaykan mark for an aisle the icon set doesn't know", () => {
    expect(productArt(bare, 'Hardware').kind).toBe('brand')
    expect(productArt(bare, '').kind).toBe('brand')
  })

  it('keys the glyph on the aisle name, never the uuid the API serves as its id', () => {
    expect(productArt(bare, '0199f3ab-6c1e-7a45-9d2f-1b8e4c0a77d3').kind).toBe('brand')
  })

  it("carries the shop's photo as a backdrop, never as the product's photo", () => {
    // Both drawn tiers can take it, and neither ever reports itself as a photo
    // of the product: the merchant image is texture behind the mark.
    for (const aisle of ['Produce', 'Hardware']) {
      const art = productArt(bare, aisle, SHOP_PHOTO)
      expect(art.kind).not.toBe('photo')
      expect(art).toMatchObject({ backdrop: SHOP_PHOTO })
    }
  })

  it('has no backdrop when the shop uploaded no photo', () => {
    expect(productArt(bare, 'Produce')).toMatchObject({ backdrop: null })
  })

  // An empty string is what the API's null becomes by the time it has been
  // through an optional prop with a '' default, and `v-if="art.backdrop"`
  // would already hide it — but `src=""` re-requests the page itself, so it
  // has to be null here rather than merely falsy.
  it('treats blank and whitespace urls as absent', () => {
    expect(productArt({ id: 'p-3', imageUrl: '   ' }, 'Produce', '  ').kind).toBe('aisle')
    expect(productArt({ id: 'p-3', imageUrl: '' }, 'Produce', '')).toMatchObject({ backdrop: null })
  })
})

describe('artTone', () => {
  it('is stable for a product, so a tile keeps its wash across sorts and reloads', () => {
    expect(artTone('sm-marby-super-loaf-d0')).toBe(artTone('sm-marby-super-loaf-d0'))
  })

  it('stays inside the tones the panel actually defines', () => {
    const ids = ['a', 'p-1', 'sm-marby-super-loaf-d0', '0199f3ab-6c1e-7a45-9d2f-1b8e4c0a77d3', '']
    for (const id of ids) {
      const tone = artTone(id)
      expect(Number.isInteger(tone)).toBe(true)
      expect(tone).toBeGreaterThanOrEqual(0)
      expect(tone).toBeLessThan(ART_TONES)
    }
  })

  it('spreads a shelf across the tones rather than washing it all one colour', () => {
    const shelf = Array.from({ length: 60 }, (_, i) => artTone(`sm-product-${i}`))
    expect(new Set(shelf).size).toBe(ART_TONES)
  })
})
