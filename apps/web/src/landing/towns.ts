/**
 * The towns with their own landing page, /baguio and /baguio/restaurants.
 *
 * The pages themselves are Laravel's (DiscoveryPageController), rendered as
 * HTML so a search engine reads the shops in them; this list is only what the
 * web build needs to know about them — the footer links to each town, and the
 * dev server proxies their paths to Laravel the way nginx does in production.
 *
 * Mirrors backend/config/discovery.php `localities`. Adding a town is an edit
 * there, here, and to the nginx rule in documentation/deployment.md §6c.
 */
export const DISCOVERY_TOWNS = [
  { slug: 'baguio', name: 'Baguio' },
  { slug: 'la-trinidad', name: 'La Trinidad' },
] as const
