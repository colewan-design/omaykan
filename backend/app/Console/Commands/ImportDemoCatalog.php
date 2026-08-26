<?php

namespace App\Console\Commands;

use App\Models\Category;
use App\Models\InventoryLevel;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Product;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\Subscription;
use App\Models\User;
use App\Support\ReadablePassword;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

/**
 * Seeds one demonstration merchant from an exported demo catalog.
 *
 * The storefront has always had shelves to show — packages/shared carries a
 * hand-written demo catalog plus ~450 rows scraped from smmarkets.ph — but they
 * lived only in the browser bundle, as a fallback for when the catalog endpoint
 * returns nothing. That fallback is not a shop: it has no seller, no stock that
 * can be decremented, and no order can be placed against it.
 *
 * This command lands the same data in the database as a real tenant, so the
 * live site exercises the actual path — organization, store, categories,
 * products, inventory levels — rather than the fallback. Everything it creates
 * is an ordinary row that the POS, the platform admin portal and checkout all
 * treat like any other merchant's.
 *
 * The JSON it reads is generated, not written by hand:
 *
 *   node scripts/export-demo-catalog.mjs --mode grocery
 *
 * Idempotent: the organization is matched by slug, categories by name, products
 * by SKU and inventory by store+product, so re-running updates in place rather
 * than duplicating. Re-running never touches the owner's password and never
 * reissues a store code that customers may already have.
 */
class ImportDemoCatalog extends Command
{
    protected $signature = 'catalog:import-demo
        {--name=SM : Business name}
        {--slug= : Organization slug (defaults to a slug of the name)}
        {--mode=grocery : Which exported catalog to read}
        {--email= : Owner login email}
        {--username= : Owner username}
        {--password= : Leave unset to have one generated}
        {--address= : Store address shown on the storefront}
        {--store-code= : Public code customers type to pair (generated if unset)}';

    protected $description = 'Seed a demonstration merchant and its catalog from an exported demo catalog';

    /** No 0/O or 1/I/L: the owner reads this back to customers out loud. */
    private const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

    public function handle(): int
    {
        $mode = (string) $this->option('mode');
        $path = database_path("data/demo-catalog-{$mode}.json");

        if (! is_file($path)) {
            $this->components->error("No exported catalog at {$path}.");
            $this->line("  Generate it first:  node scripts/export-demo-catalog.mjs --mode {$mode}");

            return self::FAILURE;
        }

        $payload = json_decode((string) file_get_contents($path), true);

        if (! is_array($payload) || ! isset($payload['categories'], $payload['products'])) {
            $this->components->error("{$path} is not a catalog export.");

            return self::FAILURE;
        }

        $name = trim((string) $this->option('name'));
        $slug = trim((string) ($this->option('slug') ?: str()->slug($name)));
        $email = strtolower(trim((string) ($this->option('email') ?: "owner@{$slug}.demo")));
        $username = strtolower(trim((string) ($this->option('username') ?: $slug)));
        $password = (string) ($this->option('password') ?: ReadablePassword::generate());
        $generatedPassword = ! $this->option('password');

        $result = DB::transaction(function () use ($payload, $mode, $name, $slug, $email, $username, $password) {
            $organization = Organization::query()->firstOrCreate(
                ['slug' => $slug],
                ['id' => (string) str()->uuid(), 'name' => $name, 'status' => 'active'],
            );

            $store = Store::query()->firstOrNew([
                'organization_id' => $organization->id,
                'code' => 'main',
            ]);

            $newStore = ! $store->exists;

            $store->fill([
                'name' => $name,
                'address' => (string) ($this->option('address') ?? ''),
                'business_mode' => $mode,
                'timezone' => 'Asia/Manila',
                'currency_code' => 'PHP',
                'status' => 'active',
            ]);

            // Issued once only. Re-running must not invalidate a code that has
            // already been handed to a customer or paired to a till.
            if ($newStore || $store->public_store_code === null) {
                $store->setPairingCode((string) ($this->option('store-code') ?: $this->uniqueStoreCode()));
            }

            $store->save();

            $owner = User::query()->where('email', $email)->first();
            $ownerCreated = $owner === null;

            if ($ownerCreated) {
                $owner = User::query()->create([
                    'name' => "{$name} Owner",
                    'username' => $username,
                    'email' => $email,
                    'password' => $password,
                    'status' => 'active',
                ]);
            }

            OrganizationMembership::query()->firstOrCreate(
                ['organization_id' => $organization->id, 'user_id' => $owner->id],
                ['membership_role' => 'admin'],
            );

            StoreMembership::query()->firstOrCreate(
                ['store_id' => $store->id, 'user_id' => $owner->id],
                ['membership_role' => 'admin'],
            );

            // Active, not pending: a demonstration tenant sitting in the
            // operators' verification queue would be noise in a queue that
            // exists to show real signups waiting on a human.
            Subscription::query()->firstOrCreate(
                ['organization_id' => $organization->id],
                [
                    'id' => (string) str()->uuid(),
                    'status' => Subscription::STATUS_ACTIVE,
                    'plan' => 'standard-monthly',
                    'amount_cents' => 49900,
                    'gcash_reference' => '',
                    'submitted_at' => now(),
                ],
            );

            // Export slug ("groceries") to the row's uuid, so each product can
            // be pointed at the category its foreign key actually needs.
            $categoryIds = [];

            foreach ($payload['categories'] as $category) {
                $row = Category::query()->firstOrCreate(
                    ['organization_id' => $organization->id, 'name' => $category['name']],
                    ['id' => (string) str()->uuid(), 'sort_order' => $category['sortOrder'] ?? 0],
                );

                $row->fill(['sort_order' => $category['sortOrder'] ?? 0])->save();

                $categoryIds[$category['id']] = $row->id;
            }

            $created = 0;
            $updated = 0;

            foreach ($payload['products'] as $item) {
                $product = Product::query()->firstOrNew([
                    'organization_id' => $organization->id,
                    'sku' => $item['sku'],
                ]);

                $product->exists ? $updated++ : $created++;

                $product->fill([
                    'category_id' => $categoryIds[$item['categoryId']] ?? null,
                    'barcode' => $item['barcode'],
                    'name' => $item['name'],
                    'product_type' => $item['kind'],
                    'tax_rate' => $item['taxRate'],
                    'price_cents' => $item['priceCents'],
                    'compare_at_price_cents' => $item['compareAtPriceCents'],
                    'image_url' => $item['imageUrl'],
                    'unit_label' => $item['unitLabel'],
                    'low_stock_threshold' => $item['lowStockThreshold'],
                    'business_modes' => $item['businessModes'],
                    'track_inventory' => true,
                    'is_active' => true,
                ]);

                $product->save();

                // The catalog endpoint hides anything tracked with no stock, so
                // without a level row every one of these would import and then
                // be invisible.
                InventoryLevel::query()->updateOrCreate(
                    ['store_id' => $store->id, 'product_id' => $product->id],
                    [
                        'organization_id' => $organization->id,
                        'qty_on_hand' => $item['stockQty'],
                        'reorder_level' => $item['lowStockThreshold'],
                    ],
                );
            }

            return compact('organization', 'store', 'owner', 'ownerCreated', 'created', 'updated');
        });

        $this->newLine();
        $this->components->info("Imported the {$payload['businessMode']} catalog for {$name}.");

        $this->components->twoColumnDetail('Organization slug', $result['organization']->slug);
        $this->components->twoColumnDetail('Store code', $result['store']->code);
        $this->components->twoColumnDetail('Public store code', (string) $result['store']->public_store_code);
        $this->components->twoColumnDetail('Categories', (string) count($payload['categories']));
        $this->components->twoColumnDetail('Products created', (string) $result['created']);
        $this->components->twoColumnDetail('Products updated', (string) $result['updated']);
        $this->components->twoColumnDetail('Owner email', $result['owner']->email);
        $this->components->twoColumnDetail('Owner username', (string) $result['owner']->username);

        if ($result['ownerCreated']) {
            $this->components->twoColumnDetail('Owner password', $password);

            if ($generatedPassword) {
                $this->components->warn('Shown once. Nothing stores the plaintext.');
            }
        } else {
            $this->components->twoColumnDetail('Owner password', 'unchanged (the account already existed)');
        }

        $this->newLine();

        return self::SUCCESS;
    }

    private function uniqueStoreCode(): string
    {
        for ($attempt = 0; $attempt < 20; $attempt++) {
            $code = '';

            for ($i = 0; $i < 6; $i++) {
                $code .= self::CODE_ALPHABET[random_int(0, strlen(self::CODE_ALPHABET) - 1)];
            }

            if (Store::query()->where('public_store_code', $code)->doesntExist()) {
                return $code;
            }
        }

        throw new \RuntimeException('Could not generate a unique store code.');
    }
}
