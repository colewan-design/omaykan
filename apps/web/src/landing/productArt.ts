import type { Component } from 'vue'
import { categoryIcon } from '@pos/core/utils/categoryIcons'

/**
 * What to draw in a product tile's art slot.
 *
 * Most of a real shop's catalog has no photograph. A merchant types a hundred
 * lines into the register at the counter and photographs almost none of them,
 * so an aisle listing is mostly placeholders — and the placeholder was one
 * shopping-trolley emoji, repeated down the whole page, which made a stocked
 * aisle look broken rather than unphotographed.
 *
 * So the tile falls back in order, best available first:
 *
 *   1. the product's own photograph;
 *   2. the aisle's glyph — the same one the header menu and the aisle tiles
 *      give that category, so a carrot means Produce everywhere on the site;
 *   3. the Omaykan mark, for an aisle the icon set does not know.
 *
 * The shop's own photo, when its owner has uploaded one, rides underneath
 * whichever mark is drawn — dimmed, never on its own. That is deliberate: a
 * photograph of a storefront sitting square in a product tile reads as a
 * picture of the product, and on a marketplace where the shopper pays cash to
 * a rider for goods they have not seen, a tile that implies the wrong contents
 * is worse than a tile that admits it has no photo.
 *
 * There is no fourth tier generating a product photograph. An invented
 * photograph of a real thing someone is about to buy is a claim about what
 * arrives in the bag, and nothing here can stand behind it.
 */
export type ProductArt =
  /** The product's own photograph. */
  | { kind: 'photo'; src: string }
  /** The aisle's glyph, on the branded panel. */
  | { kind: 'aisle'; icon: Component; backdrop: string | null; tone: number }
  /** The Omaykan mark, for an aisle with no glyph of its own. */
  | { kind: 'brand'; backdrop: string | null; tone: number }

/** Blank and whitespace-only urls are as absent as missing ones. */
function url(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim()
  return trimmed === '' ? null : trimmed
}

/**
 * How many washes the panel comes in. A page of placeholders all the exact
 * same green reads as one flat block rather than as a shelf of items, so each
 * tile takes one of a few tints of it.
 */
export const ART_TONES = 4

/**
 * Stable per product, so a tile keeps its wash across a sort, a filter, a page
 * turn and a reload — a tint that reshuffled on every render would be the kind
 * of movement that draws the eye to nothing.
 */
export function artTone(productId: string): number {
  let hash = 0
  for (let i = 0; i < productId.length; i += 1) {
    hash = (hash * 31 + productId.charCodeAt(i)) | 0
  }
  return Math.abs(hash) % ART_TONES
}

export function productArt(
  product: { id: string; imageUrl?: string },
  /** The product's aisle, as the catalog names it. Blank when unknown. */
  categoryName: string,
  /** The shop's own photo, if its owner uploaded one. */
  merchantImageUrl?: string,
): ProductArt {
  const photo = url(product.imageUrl)
  if (photo !== null) return { kind: 'photo', src: photo }

  const backdrop = url(merchantImageUrl)
  const tone = artTone(product.id)
  const icon = categoryIcon(categoryName)

  return icon === null ? { kind: 'brand', backdrop, tone } : { kind: 'aisle', icon, backdrop, tone }
}
