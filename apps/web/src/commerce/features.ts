/*
 * Storefront features whose endpoints arrive in a backend release that may not
 * be live yet.
 *
 * The web and the backend deploy separately (documentation/deployment.md §3),
 * so a frontend can go out ahead of the API it talks to. A feature listed here
 * is on unless its variable is exactly "false": local development keeps it,
 * and a production build set to "false" hides every entry point to it instead
 * of showing a screen whose every request is a 404.
 *
 * Remove a flag once the backend that serves it is deployed.
 */

/** Shopper ↔ shop messages: GET/POST /api/customer/conversations. */
export const MESSAGING_ENABLED = import.meta.env.VITE_FEATURE_MESSAGES !== 'false'

/** Rating the rider after a delivery: POST /api/customer/orders/{id}/rider-rating. */
export const RIDER_RATING_ENABLED = import.meta.env.VITE_FEATURE_RIDER_RATING !== 'false'
