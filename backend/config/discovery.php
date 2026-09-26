<?php

/*
|--------------------------------------------------------------------------
| Town and category landing pages — /baguio/restaurants and the like
|--------------------------------------------------------------------------
|
| A shop does not choose a town or a category. Both are read off what it has
| already told us (see App\Services\Discovery\Discovery), so the pages fill
| themselves as shops sign up, and adding a page is an edit here.
|
| Every town slug is a top-level path on omaykan.com. It must not collide with
| one the web build already serves (app, about, account, cart, customers, shop,
| rider, seller, signup, founding, landing, platform-admin, support-inbox,
| checkout, assets, api), and adding one needs the same edit in three places:
|   - here,
|   - apps/web/src/landing/towns.ts (the footer links and the dev proxy),
|   - the nginx rule in documentation/deployment.md §6c.
|
| Shop cards link to each shop's subdomain when SHOP_ROOT_DOMAIN is set
| (config/shops.php), exactly as the storefront does.
|
*/

return [

    /*
     * The public site the pages are canonical on, and that
     * /sitemap-towns.xml names. APP_URL unless the API is ever given a host
     * of its own, which would otherwise put that hostname into every
     * canonical link.
     */
    'site_url' => rtrim((string) env('DISCOVERY_SITE_URL', env('APP_URL', 'http://localhost')), '/'),

    /*
     * A shop belongs to the town its address names — the last one it names,
     * so "Baguio–La Trinidad Road, Km 5, La Trinidad" is La Trinidad.
     * `aliases` are matched as whole words, case-insensitively.
     *
     * An address that names no town falls back to the pin: the nearest
     * `center` within its `radius_km`. The radius is deliberately loose — a
     * shop pinned just past a town line is better listed in the neighbouring
     * town than in none — and the address, being what the owner typed, always
     * outranks it.
     */
    'localities' => [
        'baguio' => [
            'name' => 'Baguio',
            'region' => 'Baguio City, Benguet',
            'aliases' => ['baguio', 'baguio city'],
            'center' => [16.4023, 120.5960],
            'radius_km' => 7.0,
        ],
        'la-trinidad' => [
            'name' => 'La Trinidad',
            'region' => 'La Trinidad, Benguet',
            'aliases' => ['la trinidad', 'latrinidad'],
            'center' => [16.4616, 120.5877],
            'radius_km' => 6.0,
        ],
    ],

    /*
     * A shop is in a category when any one of these holds:
     *   - `modes`: its till runs in that business mode;
     *   - `labels`: its business type (free text from signup — "Bakeshop",
     *     "Sari-sari store") contains one of these words;
     *   - at least `min_products` of what it sells either sits in an aisle
     *     whose name has one of the `aisles` words, or is itself named with
     *     one of the `products` words.
     *
     * Words match whole, case-insensitively, with an optional plural s/es;
     * a trailing * makes one a prefix instead ('bake*' is "Bakery" and
     * "Bakeshop"). Whole words, because substrings misfire in exactly the
     * places a customer would notice: "tea" is inside "Steakhouse", "bread"
     * inside "Breaded pork chop".
     *
     * The product rule is what puts a grocery with a real bread aisle on the
     * baked-goods page; the threshold is what keeps a café with one strawberry
     * frappé off the strawberries one. Vegetables leave potato and carrot out
     * of `products` on purpose — on a grocery shelf those are mostly chips and
     * cake.
     *
     * The order here is the order the town page lists them in.
     */
    'categories' => [
        'restaurants' => [
            'name' => 'Restaurants',
            'heading' => 'Restaurants',
            'blurb' => 'Hot meals from local kitchens, cooked to order and delivered.',
            'modes' => ['restaurant'],
            'labels' => ['restaurant', 'eatery', 'carinderia', 'karinderya', 'kainan', 'diner', 'grill', 'bistro', 'canteen', 'food house'],
            'aisles' => [],
            'products' => [],
            'min_products' => 0,
        ],
        'cafes' => [
            'name' => 'Cafés',
            'heading' => 'Cafés and coffee shops',
            'blurb' => 'Coffee, tea and something to go with it.',
            'modes' => ['coffee-shop'],
            'labels' => ['coffee', 'cafe', 'café', 'tea', 'teahouse', 'milk tea'],
            'aisles' => [],
            'products' => [],
            'min_products' => 0,
        ],
        'groceries' => [
            'name' => 'Groceries',
            'heading' => 'Groceries',
            'blurb' => 'Everyday essentials from the stores down the road.',
            'modes' => [],
            'labels' => ['grocer*', 'sari-sari', 'sari sari', 'supermarket', 'minimart', 'mini mart', 'mini-mart', 'convenience', 'general merchandise'],
            'aisles' => [],
            'products' => [],
            'min_products' => 0,
        ],
        'baked-goods' => [
            'name' => 'Baked goods',
            'heading' => 'Bakeries and baked goods',
            'blurb' => 'Bread, pastries and cakes, baked here.',
            'modes' => [],
            'labels' => ['bake*', 'pastr*', 'cake', 'bread', 'panaderia', 'patisserie'],
            'aisles' => ['bake*', 'bread', 'pastr*', 'cake'],
            'products' => ['bread', 'loaf', 'pandesal', 'ensaymada', 'croissant', 'muffin', 'brownie', 'cupcake', 'cinnamon roll', 'ube roll'],
            'min_products' => 3,
        ],
        'vegetables' => [
            'name' => 'Vegetables',
            'heading' => 'Fresh vegetables',
            'blurb' => 'Highland vegetables, straight from the trading post and the farm.',
            'modes' => [],
            'labels' => ['vegetable', 'gulay', 'produce'],
            'aisles' => ['vegetable', 'gulay', 'produce'],
            'products' => ['cabbage', 'lettuce', 'pechay', 'sayote', 'chayote', 'broccoli', 'cauliflower', 'baguio beans', 'celery', 'wombok'],
            'min_products' => 3,
        ],
        'strawberries' => [
            'name' => 'Strawberries',
            'heading' => 'Strawberries',
            'blurb' => 'Fresh berries, jams and pasalubong from the strawberry farms.',
            'modes' => [],
            'labels' => ['strawberr*'],
            'aisles' => ['strawberr*'],
            'products' => ['strawberr*'],
            'min_products' => 2,
        ],
    ],

];
