<?php

namespace Database\Seeders;

/**
 * The deployed `sm` tenant, reproduced on a development machine.
 *
 * Production pins the web build to one organization — `sm`, the supermarket
 * whose shelves omaykan.com actually sells (VITE_POS_ORGANIZATION_SLUG in
 * apps/web/.env.production). That tenant exists only in the server's database:
 * no seeder creates it, so a developer signing in locally with the deployed
 * owner's credentials is told the username is wrong, and the only way to click
 * through the seller app as SM is to click through it on the live shop, where
 * every edit is in front of customers.
 *
 * This seeder closes that gap. The organization, the store, the owner and the
 * catalog all match what is deployed closely enough to be the same screens,
 * and nothing here can reach production.
 *
 * The catalog needs no argument: SM's shelves in the JSON *are* the grocery
 * slice of demo-catalog.json. Both descend from the same smmarkets.ph scrape
 * — see scripts/import-legacy-products.mjs, which is where the `sm-` product
 * ids come from — so inheriting DemoSellerSeeder's grocery path is not an
 * approximation of the deployed catalog, it is the same products. Expect a few
 * fewer than the 464 that are live: the JSON is the export as of its last
 * `node scripts/export-demo-catalog.mjs`, not a mirror kept in step.
 *
 * **Not wired into DatabaseSeeder, and it should stay that way.** Run it by
 * hand:
 *
 *     php artisan db:seed --class=SmLocalSeeder
 *
 * Everything it touches is firstOrCreate, inherited unchanged, so re-running
 * is safe. That also means it would adopt rather than duplicate a live `sm` if
 * it were ever pointed at the server's database, and then write demo rows into
 * a real shop — which is the reason it is not in the default seed list.
 */
class SmLocalSeeder extends DemoSellerSeeder
{
    /**
     * Store details read off production so the local copy is the same shop:
     * name, address and pin as they are deployed.
     *
     * `business_type_label` is the one deliberate difference. Production has
     * none, which is a live bug rather than a fact worth copying — the shop
     * directory falls back to the raw `business_mode` slug without it and
     * lists the shop as "grocery". Seeding the label it should have is what
     * makes the directory card render the way the code intends.
     *
     * The password is read from the environment, never committed. Without
     * `SM_LOCAL_PASSWORD` in backend/.env the owner gets `password`, the same
     * as every other seeded account. Set it to the deployed password if you
     * want one set of credentials for both.
     *
     * @return array<int, array<string, mixed>>
     */
    protected function sellers(): array
    {
        return [
            [
                'slug' => 'sm',
                'organizationName' => 'SM',
                'storeName' => 'SM',
                'businessMode' => 'grocery',
                'businessTypeLabel' => 'Supermarket',
                'address' => 'SM City Baguio, Luneta Hill, Baguio',
                'lat' => 16.4095,
                'lng' => 120.5995,
                'owner' => [
                    'name' => 'SM Owner',
                    'username' => 'sm',
                    'email' => 'owner@sm.demo',
                    'password' => env('SM_LOCAL_PASSWORD', 'password'),
                ],
            ],
        ];
    }
}
