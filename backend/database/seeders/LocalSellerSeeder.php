<?php

namespace Database\Seeders;

use App\Models\Category;
use App\Models\InventoryLevel;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\PosRole;
use App\Models\Product;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use RuntimeException;

/**
 * Three sellers of the kind positioning.md §6 names as the beachhead: a Benguet
 * vegetable farmer selling direct, a Baguio public-market stall, and a
 * neighbourhood mini grocery. They exist to give the storefront, the shop
 * directory and the seller app something to be exercised against that is not
 * the demo tenant — a shelf of real Philippine goods at real Philippine prices.
 *
 * Unlike DemoSellerSeeder the catalog is written here rather than read from
 * database/seeders/data/demo-catalog.json. That file is projected out of
 * packages/shared because the client renders the demo catalog from the same
 * TypeScript; nothing renders these three from the client, so a JSON hop would
 * buy nothing and would put the prices two files away from the seller they
 * belong to.
 *
 * All three run in business mode `grocery`. It is the only mode in
 * Store::ONLINE_MODES that fits any of them, and a mode outside that list is
 * kept out of the shop directory entirely — a farmer nobody can order from is
 * not a simulated seller. What tells them apart on a directory card is
 * `business_type_label`, which is the field that exists for exactly that.
 *
 * Tax rate is 0, not the demo catalog's 12. Tax is added on top of price_cents
 * at checkout (OnlineOrderController), and all three of these are small
 * non-VAT merchants far under the VAT threshold — charging 12% over a market
 * stall's kasim would break the price-parity covenant in the one place a
 * customer can see it.
 *
 * Everything is firstOrCreate, so this is safe to re-run.
 */
class LocalSellerSeeder extends Seeder
{
    use WithoutModelEvents;

    /** What a stocked product gets when its catalog line names no quantity. */
    private const DEFAULT_STOCK = 40;

    /** Every seller here sells something a customer can put in a cart. */
    private const MODE = 'grocery';

    public function run(): void
    {
        // APP_PAGE_KEYS and permissionsFor() live at namespace scope in
        // DatabaseSeeder.php. Composer autoloads classes, not functions, so
        // touching the class first is what guarantees the file is loaded when
        // this seeder is run on its own with --class.
        if (! class_exists(DatabaseSeeder::class)) {
            throw new RuntimeException('DatabaseSeeder could not be loaded.');
        }

        foreach ($this->sellers() as $seller) {
            DB::transaction(function () use ($seller) {
                [$organization, $store] = $this->seedSeller($seller);
                $this->seedCatalog($organization, $store, $seller);
            });

            $this->command?->info("Seeded {$seller['organizationName']} ({$seller['slug']}).");
        }
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    private function sellers(): array
    {
        return [
            [
                'slug' => 'balili-highland-farm',
                'organizationName' => 'Balili Highland Farm',
                'storeName' => 'Balili Highland Farm',
                'businessTypeLabel' => 'Farm produce',
                'address' => 'Balili, La Trinidad, Benguet',
                'lat' => 16.4553,
                'lng' => 120.5885,
                'barcodePrefix' => '4801001',
                'owner' => [
                    'name' => 'Ernesto Calixto',
                    'username' => 'farmer',
                    'email' => 'farmer@example.com',
                ],
                'categories' => [
                    'Highland Vegetables',
                    'Root Crops',
                    'Fruits',
                    'Eggs & Dairy',
                ],
                // [category, sku, name, price in centavos, unit label, image, opening stock]
                'products' => [
                    ['Highland Vegetables', 'FARM-001', 'Baguio Beans', 12000, 'per kg', null, 60],
                    ['Highland Vegetables', 'FARM-002', 'Carrots', 9000, 'per kg', '/products/carrots.jpg', 80],
                    ['Highland Vegetables', 'FARM-003', 'Cabbage (Scorpio)', 7000, 'per kg', '/products/cabbage.jpg', 70],
                    ['Highland Vegetables', 'FARM-004', 'Chinese Pechay', 6000, 'per kg', '/products/mixed-greens-salad.jpg', 40],
                    ['Highland Vegetables', 'FARM-005', 'Broccoli', 18000, 'per kg', null, 25],
                    ['Highland Vegetables', 'FARM-006', 'Cauliflower', 15000, 'per kg', null, 25],
                    ['Highland Vegetables', 'FARM-007', 'Sayote', 4000, 'per kg', null, 90],
                    ['Highland Vegetables', 'FARM-008', 'Bell Pepper, Green', 16000, 'per kg', null, 30],
                    ['Highland Vegetables', 'FARM-009', 'Romaine Lettuce', 14000, 'per kg', '/products/mixed-greens-salad.jpg', 35],
                    ['Highland Vegetables', 'FARM-010', 'Wombok (Chinese Cabbage)', 7500, 'per kg', '/products/cabbage.jpg', 45],
                    ['Root Crops', 'FARM-011', 'Potatoes', 8500, 'per kg', '/products/potatoes.jpg', 120],
                    ['Root Crops', 'FARM-012', 'Kamote (Sweet Potato)', 6000, 'per kg', null, 80],
                    ['Root Crops', 'FARM-013', 'Ginger', 4500, 'per 1/4 kg', null, 30],
                    ['Fruits', 'FARM-014', 'Strawberries', 15000, 'per 250 g box', null, 18],
                    ['Fruits', 'FARM-015', 'Saba Banana', 6500, 'per kg', '/products/bananas.jpg', 50],
                    ['Eggs & Dairy', 'FARM-016', 'Free-range Eggs', 28000, 'tray of 30', '/products/eggs-dozen.jpg', 24],
                ],
            ],
            [
                'slug' => 'nenas-market-stall',
                'organizationName' => 'Aling Nena Market Stall',
                'storeName' => 'Aling Nena Market Stall',
                'businessTypeLabel' => 'Public market stall',
                'address' => 'Stall 14, Magsaysay Avenue, Baguio City Public Market',
                'lat' => 16.4155,
                'lng' => 120.5935,
                'barcodePrefix' => '4801002',
                'owner' => [
                    'name' => 'Nena Bagtas',
                    'username' => 'market',
                    'email' => 'market@example.com',
                ],
                'categories' => [
                    'Meat & Poultry',
                    'Fish & Seafood',
                    'Rice & Grains',
                    'Dried Goods',
                    'Fresh Produce',
                ],
                'products' => [
                    ['Meat & Poultry', 'MKT-001', 'Pork Kasim', 28000, 'per kg', '/products/pork-bbq.jpg', 30],
                    ['Meat & Poultry', 'MKT-002', 'Pork Liempo', 33000, 'per kg', '/products/pork-bbq.jpg', 25],
                    ['Meat & Poultry', 'MKT-003', 'Pork Giniling', 30000, 'per kg', null, 20],
                    ['Meat & Poultry', 'MKT-004', 'Beef Brisket', 42000, 'per kg', null, 15],
                    ['Meat & Poultry', 'MKT-005', 'Whole Chicken, dressed', 20000, 'per kg', null, 35],
                    ['Meat & Poultry', 'MKT-006', 'Chicken Wings', 23000, 'per kg', null, 20],
                    ['Fish & Seafood', 'MKT-007', 'Bangus (Milkfish)', 20000, 'per kg', '/products/grilled-bangus.jpg', 28],
                    ['Fish & Seafood', 'MKT-008', 'Tilapia', 18000, 'per kg', null, 30],
                    ['Fish & Seafood', 'MKT-009', 'Galunggong', 24000, 'per kg', null, 22],
                    ['Fish & Seafood', 'MKT-010', 'Pusit (Squid)', 38000, 'per kg', '/products/calamari.jpg', 12],
                    ['Fish & Seafood', 'MKT-011', 'Suahe (Shrimp)', 45000, 'per kg', null, 10],
                    ['Rice & Grains', 'MKT-012', 'Sinandomeng Rice 25 kg', 145000, 'per sack', '/products/rice.jpg', 14],
                    ['Rice & Grains', 'MKT-013', 'Dinorado Rice 5 kg', 33000, 'per pack', '/products/rice.jpg', 40],
                    ['Rice & Grains', 'MKT-014', 'Malagkit Rice', 8500, 'per kg', '/products/rice.jpg', 30],
                    ['Dried Goods', 'MKT-015', 'Dried Danggit 250 g', 18000, 'per pack', null, 25],
                    ['Dried Goods', 'MKT-016', 'Mongo Beans', 9500, 'per kg', null, 35],
                    ['Dried Goods', 'MKT-017', 'Raw Peanuts', 14000, 'per kg', '/products/roasted-peanuts.jpg', 25],
                    ['Fresh Produce', 'MKT-018', 'Red Onions', 12000, 'per kg', '/products/red-onions.jpg', 45],
                    ['Fresh Produce', 'MKT-019', 'Native Garlic', 18000, 'per 1/2 kg', '/products/garlic.jpg', 30],
                    ['Fresh Produce', 'MKT-020', 'Tomatoes', 8000, 'per kg', '/products/tomatoes.jpg', 50],
                ],
            ],
            [
                'slug' => 'lourdes-mini-grocery',
                'organizationName' => 'Lourdes Mini Grocery',
                'storeName' => 'Lourdes Mini Grocery',
                'businessTypeLabel' => 'Mini grocery',
                'address' => '22 Lourdes Subdivision Proper, Baguio City',
                'lat' => 16.4088,
                'lng' => 120.5865,
                'barcodePrefix' => '4801003',
                'owner' => [
                    'name' => 'Joel Ramirez',
                    'username' => 'minigrocery',
                    'email' => 'minigrocery@example.com',
                ],
                'categories' => [
                    'Rice & Staples',
                    'Canned & Instant',
                    'Beverages',
                    'Snacks',
                    'Household & Personal Care',
                    'Chilled & Bakery',
                ],
                'products' => [
                    ['Rice & Staples', 'MG-001', 'Well-milled Rice', 5200, 'per kg', '/products/rice.jpg', 150],
                    ['Rice & Staples', 'MG-002', 'Cooking Oil 1 L pouch', 11500, 'per pouch', '/products/cooking-oil.jpg', 48],
                    ['Rice & Staples', 'MG-003', 'White Sugar 1 kg', 8500, 'per pack', '/products/white-sugar.jpg', 40],
                    ['Rice & Staples', 'MG-004', 'Iodized Salt 1 kg', 2500, 'per pack', null, 40],
                    ['Rice & Staples', 'MG-005', 'Soy Sauce 1 L', 4800, 'per bottle', null, 36],
                    ['Rice & Staples', 'MG-006', 'Vinegar 1 L', 4200, 'per bottle', null, 36],
                    ['Canned & Instant', 'MG-007', 'Instant Noodles, Beef 60 g', 1500, 'per pack', '/products/instant-noodles.jpg', 200],
                    ['Canned & Instant', 'MG-008', 'Sardines in Tomato Sauce 155 g', 2800, 'per can', null, 120],
                    ['Canned & Instant', 'MG-009', 'Corned Beef 150 g', 4500, 'per can', null, 60],
                    ['Canned & Instant', 'MG-010', 'Condensed Milk 300 ml', 5200, 'per can', '/products/condensed-milk.jpg', 48],
                    ['Canned & Instant', 'MG-011', 'Evaporated Milk 370 ml', 4500, 'per can', '/products/milk.jpg', 48],
                    ['Beverages', 'MG-012', 'Softdrink in Can 330 ml', 3500, 'per can', '/products/soda-can.jpg', 96],
                    ['Beverages', 'MG-013', 'Bottled Water 500 ml', 1800, 'per bottle', '/products/still-water.jpg', 120],
                    ['Beverages', 'MG-014', '3-in-1 Coffee Sachet', 1200, 'per sachet', null, 300],
                    ['Beverages', 'MG-015', 'Powdered Juice Sachet', 1000, 'per sachet', null, 250],
                    ['Snacks', 'MG-016', 'Potato Chips 60 g', 3800, 'per pack', '/products/potato-chips.jpg', 60],
                    ['Snacks', 'MG-017', 'Crackers 250 g', 4200, 'per pack', '/products/crackers.jpg', 45],
                    ['Snacks', 'MG-018', 'Chocolate Bar 40 g', 3000, 'per bar', '/products/chocolate-bar.jpg', 70],
                    ['Snacks', 'MG-019', 'Roasted Peanuts 100 g', 2500, 'per pack', '/products/roasted-peanuts.jpg', 55],
                    ['Household & Personal Care', 'MG-020', 'Laundry Detergent Bar 380 g', 3200, 'per bar', null, 60],
                    ['Household & Personal Care', 'MG-021', 'Shampoo Sachet 12 ml', 800, 'per sachet', null, 300],
                    ['Household & Personal Care', 'MG-022', 'Bath Soap 90 g', 3800, 'per bar', null, 72],
                    ['Household & Personal Care', 'MG-023', 'Dishwashing Liquid 250 ml', 5500, 'per bottle', null, 36],
                    ['Chilled & Bakery', 'MG-024', 'Fresh Milk 1 L', 9800, 'per carton', '/products/milk.jpg', 24],
                    ['Chilled & Bakery', 'MG-025', 'Eggs, tray of 12', 10500, 'per tray', '/products/eggs-dozen.jpg', 30],
                    ['Chilled & Bakery', 'MG-026', 'Sliced Bread Loaf', 6800, 'per loaf', '/products/bread-loaf.jpg', 20],
                    ['Chilled & Bakery', 'MG-027', 'Cheddar Cheese 165 g', 7800, 'per pack', '/products/cheddar-cheese.jpg', 24],
                    ['Chilled & Bakery', 'MG-028', 'Yogurt Cup 110 g', 3500, 'per cup', '/products/yogurt-cup.jpg', 36],
                ],
            ],
        ];
    }

    /**
     * @param  array<string, mixed>  $seller
     * @return array{0: Organization, 1: Store}
     */
    private function seedSeller(array $seller): array
    {
        $organization = Organization::query()->firstOrCreate(
            ['slug' => $seller['slug']],
            [
                'name' => $seller['organizationName'],
                'status' => 'active',
            ],
        );

        $store = Store::query()->firstOrCreate(
            [
                'organization_id' => $organization->id,
                'code' => 'main',
            ],
            [
                'name' => $seller['storeName'],
                'timezone' => 'Asia/Manila',
                'currency_code' => 'PHP',
                'status' => 'active',
                'business_mode' => self::MODE,
                'business_type_label' => $seller['businessTypeLabel'],
                'address' => $seller['address'],
                'lat' => $seller['lat'],
                'lng' => $seller['lng'],
            ],
        );

        $owner = User::query()->firstOrCreate(
            ['email' => $seller['owner']['email']],
            [
                'name' => $seller['owner']['name'],
                'username' => $seller['owner']['username'],
                'password' => Hash::make('password'),
                'email_verified_at' => now(),
                'status' => 'active',
            ],
        );

        // Signup gates sign-in behind a verification link; a seeded owner has
        // no mailbox to collect one from, so it is granted here.
        if ($owner->email_verified_at === null) {
            $owner->forceFill(['email_verified_at' => now()])->save();
        }

        OrganizationMembership::query()->firstOrCreate(
            [
                'organization_id' => $organization->id,
                'user_id' => $owner->id,
            ],
            ['membership_role' => 'admin'],
        );

        StoreMembership::query()->firstOrCreate(
            [
                'store_id' => $store->id,
                'user_id' => $owner->id,
            ],
            ['membership_role' => 'admin'],
        );

        $this->seedRoles($organization);

        // Active rather than pending: these are sellers you want to sign into
        // and order from, not ones waiting on a GCash reference to be verified
        // by hand.
        Subscription::query()->firstOrCreate(
            ['organization_id' => $organization->id],
            [
                'status' => Subscription::STATUS_ACTIVE,
                'plan' => 'standard-monthly',
                'amount_cents' => 49900,
                'gcash_reference' => '',
                'submitted_at' => now(),
                'verified_at' => now(),
            ],
        );

        return [$organization, $store];
    }

    private function seedRoles(Organization $organization): void
    {
        $roles = [
            'admin' => ['name' => 'Admin', 'permissions' => permissionsFor(APP_PAGE_KEYS)],
            'manager' => ['name' => 'Manager', 'permissions' => permissionsFor([
                'dashboard', 'sales', 'orders', 'products', 'customers', 'suppliers',
                'employees', 'inventory', 'reports', 'register', 'settings', 'tables',
            ])],
            'cashier' => ['name' => 'Cashier', 'permissions' => permissionsFor([
                'dashboard', 'sales', 'orders', 'register', 'settings', 'tables',
            ])],
            'guest' => ['name' => 'Guest', 'permissions' => permissionsFor([])],
        ];

        foreach ($roles as $roleKey => $roleData) {
            PosRole::query()->firstOrCreate(
                [
                    'organization_id' => $organization->id,
                    'role_key' => $roleKey,
                ],
                [
                    'name' => $roleData['name'],
                    'permissions' => $roleData['permissions'],
                ],
            );
        }
    }

    /**
     * @param  array<string, mixed>  $seller
     */
    private function seedCatalog(Organization $organization, Store $store, array $seller): void
    {
        $categoryMap = [];
        $sortOrder = 1;

        foreach ($seller['categories'] as $name) {
            $categoryMap[$name] = Category::query()->firstOrCreate(
                [
                    'organization_id' => $organization->id,
                    'name' => $name,
                ],
                ['sort_order' => $sortOrder++],
            )->id;
        }

        $sequence = 0;

        foreach ($seller['products'] as [$categoryName, $sku, $name, $priceCents, $unitLabel, $imageUrl, $stockQty]) {
            $sequence++;

            $row = Product::query()->firstOrCreate(
                [
                    'organization_id' => $organization->id,
                    'sku' => $sku,
                ],
                [
                    'category_id' => $categoryMap[$categoryName],
                    // Barcodes are unique per organization only, so the
                    // per-seller prefix is what keeps three shelves of the same
                    // staples from colliding if they are ever merged.
                    'barcode' => $seller['barcodePrefix'].str_pad((string) $sequence, 5, '0', STR_PAD_LEFT),
                    'name' => $name,
                    'product_type' => 'standard',
                    'tax_rate' => 0,
                    'price_cents' => $priceCents,
                    'image_url' => $imageUrl,
                    'unit_label' => $unitLabel,
                    // A tenth of opening stock, floored at 5: enough for the
                    // POS low-stock badge to mean something without flagging
                    // the whole shelf on day one.
                    'low_stock_threshold' => max(5, (int) floor(($stockQty ?? self::DEFAULT_STOCK) / 10)),
                    'track_inventory' => true,
                    'is_active' => true,
                    // Without this the storefront will not list the product —
                    // see StorefrontCatalogController.
                    'business_modes' => [self::MODE],
                ],
            );

            // A tracked product with no inventory row reads as out of stock, so
            // the store needs one before it can take an online order.
            InventoryLevel::query()->firstOrCreate(
                [
                    'organization_id' => $organization->id,
                    'store_id' => $store->id,
                    'product_id' => $row->id,
                ],
                ['qty_on_hand' => (int) ($stockQty ?? self::DEFAULT_STOCK)],
            );
        }
    }
}
