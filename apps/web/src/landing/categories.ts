import {
  Bike,
  Croissant,
  CupSoda,
  Pill,
  ShoppingBasket,
  Stethoscope,
  UtensilsCrossed,
  WashingMachine,
} from '@lucide/vue'

// The marketplace taxonomy, not one shop's catalog categories — the site is a
// front door to every store in the city. Shared because the header nav, the
// "Shop by category" block and the footer all have to name the same set.
// `icon` is only used by the nav bar; the block uses the photo cards.
export const serviceCategories = [
  { slug: 'food', label: 'Food', icon: UtensilsCrossed },
  { slug: 'groceries', label: 'Groceries', icon: ShoppingBasket },
  { slug: 'pharmacy', label: 'Pharmacy', icon: Pill },
  { slug: 'errands', label: 'Errands', icon: Bike },
  { slug: 'bakery', label: 'Bakery', icon: Croissant },
  { slug: 'beverages', label: 'Beverages', icon: CupSoda },
  { slug: 'laundry', label: 'Laundry', icon: WashingMachine },
  { slug: 'medicine', label: 'Medicine', icon: Stethoscope },
]
