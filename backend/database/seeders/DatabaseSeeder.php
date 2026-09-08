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
use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;
use InvalidArgumentException;

/**
 * The full set of page keys, which must stay in step with `appPageKeys` in
 * packages/shared/src/index.ts.
 *
 * Every key has to appear in the stored permissions, not just the granted
 * ones: withAllPermissionKeys() in the client reads a missing key as `false`,
 * so a short list here silently locks a role out of the pages it omits. This
 * previously listed a non-existent 'analytics' page and left out nine real
 * ones, which is exactly that bug.
 */
const APP_PAGE_KEYS = [
    'dashboard', 'sales', 'orders', 'products', 'customers', 'suppliers',
    'employees', 'inventory', 'tables', 'reports', 'integrations', 'register',
    'settings', 'diagnostics',
];

function permissionsFor(array $allowedPages): array
{
    $unknown = array_diff($allowedPages, APP_PAGE_KEYS);
    if ($unknown !== []) {
        throw new InvalidArgumentException(
            'Unknown page key(s): '.implode(', ', $unknown)
        );
    }

    $permissions = [];
    foreach (APP_PAGE_KEYS as $page) {
        $permissions[$page] = in_array($page, $allowedPages, true);
    }

    return $permissions;
}

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed the application's database.
     */
    public function run(): void
    {
        $organization = Organization::query()->firstOrCreate(
            ['slug' => 'demo-coffee'],
            [
                'name' => 'Demo Coffee Group',
                'status' => 'active',
            ],
        );

        $store = Store::query()->firstOrCreate(
            [
                'organization_id' => $organization->id,
                'code' => 'main',
            ],
            [
                'name' => 'Main Branch',
                'timezone' => 'Asia/Manila',
                'currency_code' => 'PHP',
                'status' => 'active',
                'business_mode' => 'coffee-shop',
                'address' => '12 Session Road, Baguio City',
                // Session Road, Baguio — the origin pin delivery is quoted from.
                'lat' => 16.4123,
                'lng' => 120.5960,
            ],
        );

        $admin = User::query()->firstOrCreate([
            'email' => 'admin@example.com',
        ], [
            'name' => 'Admin User',
            'username' => 'admin',
            'password' => Hash::make('password'),
            'email_verified_at' => now(),
            'status' => 'active',
        ]);

        if ($admin->email_verified_at === null) {
            $admin->forceFill(['email_verified_at' => now()])->save();
        }

        OrganizationMembership::query()->firstOrCreate([
            'organization_id' => $organization->id,
            'user_id' => $admin->id,
        ], [
            'membership_role' => 'admin',
        ]);

        StoreMembership::query()->firstOrCreate([
            'store_id' => $store->id,
            'user_id' => $admin->id,
        ], [
            'membership_role' => 'admin',
        ]);

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

        $beverages = Category::query()->firstOrCreate(
            [
                'organization_id' => $organization->id,
                'name' => 'Beverages',
            ],
            [
                'sort_order' => 1,
            ],
        );

        $espresso = Product::query()->firstOrCreate(
            [
                'organization_id' => $organization->id,
                'sku' => 'ESP-0001',
            ],
            [
                'category_id' => $beverages->id,
                'barcode' => '100000000001',
                'name' => 'Espresso',
                'product_type' => 'standard',
                'tax_rate' => 12,
                'price_cents' => 12000,
                'track_inventory' => true,
                'is_active' => true,
                // Without this the storefront will not list the product — see
                // OnlineOrderController.
                'business_modes' => ['coffee-shop'],
            ],
        );

        // A tracked product with no inventory row reads as out of stock, so the
        // demo store needs one before it can take an online order.
        InventoryLevel::query()->firstOrCreate(
            [
                'organization_id' => $organization->id,
                'store_id' => $store->id,
                'product_id' => $espresso->id,
            ],
            [
                'qty_on_hand' => 100,
            ],
        );

        // DemoSellerSeeder fills this shop out to its full shelf and adds a
        // seller for each of the other business modes.
        //
        // It is chained only on `local`, never on `testing`: the tests seed
        // through this class and assert against exactly the one category and
        // one product above, so chaining it unconditionally turns a fixture
        // into 550 products. Production is excluded for the obvious reason —
        // a live install must not grow four demo shops.
        //
        // The gate exists because the two-step version kept costing a rebuilt
        // local database its catalog: `db:seed` alone leaves a one-product
        // shop, the storefront then looks broken rather than empty, and the
        // second command is the one nobody remembers. Both seeders are
        // firstOrCreate throughout, so this stays safe to re-run.
        if (app()->environment('local')) {
            $this->call(DemoSellerSeeder::class);

            // Same gate, same reason, plus one of its own: RiderSeeder's
            // approved account has a password in this repository, and a rider
            // token reads live customer addresses and phone numbers.
            $this->call(RiderSeeder::class);
        }
    }
}
