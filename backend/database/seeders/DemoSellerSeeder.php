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
 * One worked-through seller per business mode, each with the slice of the demo
 * catalog that its mode declares.
 *
 * The catalog is not written out here: it is read from
 * database/seeders/data/demo-catalog.json, which `node scripts/export-demo-catalog.mjs`
 * projects from packages/shared. The TypeScript stays the source of truth —
 * it is what the client renders from — so the two cannot drift into
 * disagreeing about what the demo store sells. The grocery half of it is the
 * smmarkets.ph scrape with real Philippine prices, pack sizes and photos.
 *
 * Everything is firstOrCreate, so this is safe to re-run and safe to run
 * against a database that DatabaseSeeder has already touched: the coffee shop
 * below is deliberately the same org/store/owner that DatabaseSeeder creates,
 * and is picked up rather than duplicated.
 */
class DemoSellerSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Products carry a rate (0.12); the column is a percentage (12.00) — see
     * the note in OnlineOrderController, which divides by 100 to charge tax.
     */
    private const TAX_RATE_SCALE = 100;

    /** What a stocked product gets when the catalog names no quantity. */
    private const DEFAULT_STOCK = 100;

    public function run(): void
    {
        // APP_PAGE_KEYS and permissionsFor() live at namespace scope in
        // DatabaseSeeder.php. Composer autoloads classes, not functions, so
        // touching the class first is what guarantees the file is loaded when
        // this seeder is run on its own with --class.
        if (! class_exists(DatabaseSeeder::class)) {
            throw new RuntimeException('DatabaseSeeder could not be loaded.');
        }

        $catalog = $this->loadCatalog();

        foreach ($this->sellers() as $seller) {
            DB::transaction(function () use ($seller, $catalog) {
                [$organization, $store] = $this->seedSeller($seller);
                $this->seedCatalog($organization, $store, $seller['businessMode'], $catalog);
            });
        }
    }

    /**
     * @return array{categories: array<int, array<string, mixed>>, products: array<int, array<string, mixed>>}
     */
    protected function loadCatalog(): array
    {
        $path = database_path('seeders/data/demo-catalog.json');

        if (! is_file($path)) {
            throw new RuntimeException(
                "Missing {$path} — regenerate it with: node scripts/export-demo-catalog.mjs"
            );
        }

        $catalog = json_decode((string) file_get_contents($path), true);

        if (! is_array($catalog) || ! isset($catalog['categories'], $catalog['products'])) {
            throw new RuntimeException("Malformed catalog at {$path}.");
        }

        return $catalog;
    }

    /**
     * The coffee shop repeats DatabaseSeeder's org, store code and owner email
     * on purpose — those are the lookup keys, so it adopts the existing rows
     * instead of standing up a second tenant.
     *
     * @return array<int, array<string, mixed>>
     */
    protected function sellers(): array
    {
        return [
            [
                'slug' => 'demo-coffee',
                'organizationName' => 'Demo Coffee Group',
                'storeName' => 'Main Branch',
                'businessMode' => 'coffee-shop',
                'businessTypeLabel' => 'Coffee shop',
                'address' => '12 Session Road, Baguio City',
                'lat' => 16.4123,
                'lng' => 120.5960,
                'owner' => [
                    'name' => 'Admin User',
                    'username' => 'admin',
                    'email' => 'admin@example.com',
                ],
            ],
            [
                'slug' => 'baguio-fresh-market',
                'organizationName' => 'Baguio Fresh Market',
                'storeName' => 'Baguio Fresh Market',
                'businessMode' => 'grocery',
                'businessTypeLabel' => 'Grocery store',
                'address' => 'Magsaysay Avenue, Baguio City',
                'lat' => 16.4145,
                'lng' => 120.5931,
                'owner' => [
                    'name' => 'Grocery Owner',
                    'username' => 'grocery',
                    'email' => 'grocery@example.com',
                ],
            ],
            [
                'slug' => 'session-road-grill',
                'organizationName' => 'Session Road Grill',
                'storeName' => 'Session Road Grill',
                'businessMode' => 'restaurant',
                'businessTypeLabel' => 'Restaurant',
                'address' => '88 Session Road, Baguio City',
                'lat' => 16.4110,
                'lng' => 120.5948,
                'owner' => [
                    'name' => 'Restaurant Owner',
                    'username' => 'restaurant',
                    'email' => 'restaurant@example.com',
                ],
            ],
            [
                'slug' => 'polished-nail-lounge',
                'organizationName' => 'Polished Nail Lounge',
                'storeName' => 'Polished Nail Lounge',
                'businessMode' => 'nail-salon',
                'businessTypeLabel' => 'Nail salon',
                'address' => 'Upper General Luna Road, Baguio City',
                'lat' => 16.4098,
                'lng' => 120.5977,
                'owner' => [
                    'name' => 'Salon Owner',
                    'username' => 'salon',
                    'email' => 'salon@example.com',
                ],
            ],
        ];
    }

    /**
     * @param  array<string, mixed>  $seller
     * @return array{0: Organization, 1: Store}
     */
    protected function seedSeller(array $seller): array
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
                'business_mode' => $seller['businessMode'],
                'business_type_label' => $seller['businessTypeLabel'],
                'address' => $seller['address'],
                'lat' => $seller['lat'],
                'lng' => $seller['lng'],
            ],
        );

        // Backfilled, not just set on create: the coffee shop is adopted from
        // DatabaseSeeder, which predates this column and leaves it null. The
        // shop directory falls back to the raw business_mode slug without it,
        // so an existing store would list itself as "coffee-shop".
        if ($store->business_type_label === null || $store->business_type_label === '') {
            $store->business_type_label = $seller['businessTypeLabel'];
            $store->save();
        }

        $owner = User::query()->firstOrCreate(
            ['email' => $seller['owner']['email']],
            [
                'name' => $seller['owner']['name'],
                'username' => $seller['owner']['username'],
                // Every seeded owner gets 'password' unless its entry names
                // another — SmLocalSeeder does, so a local SM can be given the
                // same password as the deployed one without that password
                // being written down in this repository.
                'password' => Hash::make($seller['owner']['password'] ?? 'password'),
                'email_verified_at' => now(),
                'status' => 'active',
            ],
        );

        // Signup gates sign-in behind a verification link; a seeded owner has no
        // mailbox to collect one from, so it is granted here.
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

        // Active, not pending: a seeded seller is one you want to sign into and
        // use, not one waiting on a GCash reference to be verified by hand.
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

    protected function seedRoles(Organization $organization): void
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
     * @param  array{categories: array<int, array<string, mixed>>, products: array<int, array<string, mixed>>}  $catalog
     */
    protected function seedCatalog(Organization $organization, Store $store, string $businessMode, array $catalog): void
    {
        $products = array_values(array_filter(
            $catalog['products'],
            fn (array $product): bool => in_array($businessMode, $product['businessModes'] ?? [], true),
        ));

        if ($products === []) {
            return;
        }

        $categoryIds = array_flip(array_column($products, 'categoryId'));

        // Only the aisles this seller actually stocks: seeding all 23 would
        // leave a coffee shop showing an empty "Meat & Seafood" shelf.
        $categoryMap = [];
        $sortOrder = 1;

        foreach ($catalog['categories'] as $category) {
            if (! isset($categoryIds[$category['id']])) {
                continue;
            }

            $row = Category::query()->firstOrCreate(
                [
                    'organization_id' => $organization->id,
                    'name' => $category['name'],
                ],
                ['sort_order' => $sortOrder++],
            );

            $categoryMap[$category['id']] = $row->id;
        }

        foreach ($products as $product) {
            // A quantity in the catalog is what marks a product as stocked.
            // The rest are services — a manicure has no shelf — and tracking
            // them would read as out of stock and drop them from the
            // storefront, which excludes any tracked product at zero.
            $stockQty = $product['stockQty'] ?? null;
            $tracksInventory = is_numeric($stockQty);

            $row = Product::query()->firstOrCreate(
                [
                    'organization_id' => $organization->id,
                    'sku' => $product['sku'],
                ],
                [
                    'category_id' => $categoryMap[$product['categoryId']],
                    'barcode' => $product['barcode'],
                    'name' => $product['name'],
                    'product_type' => $product['kind'] ?? 'standard',
                    'tax_rate' => round(((float) ($product['taxRate'] ?? 0.12)) * self::TAX_RATE_SCALE, 2),
                    'price_cents' => $product['priceCents'],
                    'compare_at_price_cents' => $product['compareAtPriceCents'] ?? null,
                    'image_url' => $product['imageUrl'] ?? null,
                    'unit_label' => $product['unitLabel'] ?? null,
                    'low_stock_threshold' => $product['lowStockThreshold'] ?? null,
                    'track_inventory' => $tracksInventory,
                    'is_active' => true,
                    // Without this the storefront will not list the product —
                    // see OnlineOrderController.
                    'business_modes' => $product['businessModes'],
                ],
            );

            if (! $tracksInventory) {
                continue;
            }

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
