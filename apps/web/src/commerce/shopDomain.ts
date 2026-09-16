import { slugFromShopHost } from '@pos/shared/index'

// Where shops live: `<slug>.<SHOP_ROOT_DOMAIN>`. Blank for a build with no shop
// domain, where a shop's page is `/shop/<slug>` on the serving origin instead.
export const SHOP_ROOT_DOMAIN: string = (import.meta.env.VITE_SHOP_ROOT_DOMAIN ?? '').trim().toLowerCase()

/** The slug this page's host names, or '' when this is not a shop subdomain. */
export function shopSlugFromHost(): string {
  if (typeof window === 'undefined') return ''
  return slugFromShopHost(window.location.hostname, SHOP_ROOT_DOMAIN)
}

/**
 * The main site, as an absolute origin when this page is on a shop subdomain
 * and '' (same origin) otherwise.
 *
 * On a subdomain every link to the rest of the site — the cart, the account,
 * the shop list — has to name the main host: nginx only serves the shop page
 * and its assets there, and the customer's sign-in lives on the main origin.
 */
export function mainSiteOrigin(): string {
  return shopSlugFromHost() !== '' ? `https://${SHOP_ROOT_DOMAIN}` : ''
}
