import {
  Beef,
  Carrot,
  Coffee,
  Cookie,
  CookingPot,
  Croissant,
  CupSoda,
  Donut,
  Footprints,
  Gem,
  GlassWater,
  Globe,
  Hand,
  IceCreamCone,
  Leaf,
  Milk,
  Sandwich,
  ShoppingBag,
  ShoppingBasket,
  Snowflake,
  Soup,
  Sparkles,
  UtensilsCrossed,
} from '@lucide/vue'
import type { Component } from 'vue'

/**
 * A lucide glyph per aisle, so a category strip reads as a row of aisles
 * rather than a row of identical baskets.
 *
 * Keyed on the category's *name*, not its id. The seeded taxonomy's slugs
 * (`produce`, `meat-seafood`, …) stop at DemoSellerSeeder: it `firstOrCreate`s
 * on organization + name and lets the database mint the id, and
 * `categories.id` is a uuid column. So every id the API serves is a uuid, and
 * a slug-keyed lookup matched nothing while still compiling and shipping —
 * which is exactly how the storefront nav came to draw one basket eleven
 * times. The Android app hit the same thing; see CategoryIcons.kt, which this
 * mirrors.
 *
 * Names are the seeded ones, so a merchant who happens to file their own aisle
 * under "Produce" gets the carrot too. Matching is on the whole normalised
 * name, never a substring: "Frozen" is a freezer aisle and "Frozen Yogurt" is
 * a dessert, and the caller's fallback beats confidently drawing the wrong
 * glyph.
 */
const CATEGORY_ICONS: Record<string, Component> = {
  // Grocery aisles
  groceries: ShoppingBasket,
  produce: Carrot,
  dairy: Milk,
  snacks: Cookie,
  'meat & seafood': Beef,
  'meat and seafood': Beef,
  bakery: Croissant,
  frozen: Snowflake,
  international: Globe,
  'ready to cook': CookingPot,
  'ready to eat': Sandwich,
  // Coffee shop
  coffee: Coffee,
  tea: Leaf,
  pastry: Donut,
  'cold drinks': CupSoda,
  // Restaurant
  starters: Soup,
  mains: UtensilsCrossed,
  desserts: IceCreamCone,
  beverages: GlassWater,
  // Nail salon. "Enhancements", "Add-ons" and "Retail" are the seeded names —
  // unqualified enough that another trade's "Retail" aisle would draw a polish
  // bottle. They are here because the seeded salon is the one that ships.
  manicures: Hand,
  pedicures: Footprints,
  enhancements: Sparkles,
  'add-ons': Gem,
  'add ons': Gem,
  addons: Gem,
  retail: ShoppingBag,
}

/** Case and stray whitespace are the merchant's; the lookup shouldn't care. */
function normalize(categoryName: string): string {
  return categoryName.trim().toLowerCase().replace(/\s+/g, ' ')
}

/**
 * The glyph for an aisle, or null when it isn't one we draw — each surface
 * picks its own fallback (a basket on the storefront, a chef's hat on the
 * restaurant register) rather than sharing one that suits none of them.
 */
export function categoryIcon(categoryName: string): Component | null {
  return CATEGORY_ICONS[normalize(categoryName)] ?? null
}
