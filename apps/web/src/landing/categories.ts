import type { Component } from 'vue'
import {
  Beef,
  Cookie,
  CookingPot,
  Croissant,
  CupSoda,
  Coffee,
  Carrot,
  Globe,
  IceCreamCone,
  Leaf,
  Milk,
  Salad,
  ShoppingBasket,
  Snowflake,
  Sparkles,
  UtensilsCrossed,
} from '@lucide/vue'

// The header nav lists the store's real, stocked categories — clicking one has
// to produce a product list, so the nav can only name categories the catalog
// actually has. What lives here is the decoration: a lucide glyph per known
// category id, since Category itself carries only an id and a name.
//
// Ids come from the seeded taxonomy (packages/shared/src/index.ts and
// groceryCatalog.generated.ts). A tenant's own category falls through to the
// basket, so an unknown id costs an icon, never a nav item.
const categoryIcons: Record<string, Component> = {
  // Grocery aisles
  groceries: ShoppingBasket,
  produce: Carrot,
  dairy: Milk,
  snacks: Cookie,
  bakery: Croissant,
  frozen: Snowflake,
  'meat-seafood': Beef,
  international: Globe,
  'ready-to-cook': CookingPot,
  'ready-to-eat': UtensilsCrossed,
  // Coffee shop
  coffee: Coffee,
  tea: Leaf,
  pastry: Croissant,
  'cold-drinks': CupSoda,
  beverages: CupSoda,
  // Restaurant
  starters: Salad,
  mains: UtensilsCrossed,
  desserts: IceCreamCone,
  // Salon
  manicures: Sparkles,
  pedicures: Sparkles,
  'nail-enhancements': Sparkles,
  'nail-addons': Sparkles,
  'salon-retail': ShoppingBasket,
}

export function categoryIcon(categoryId: string): Component {
  return categoryIcons[categoryId] ?? ShoppingBasket
}
