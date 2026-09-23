<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\InventoryLevel;
use App\Models\PosCustomer;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\SyncCursor;
use App\Models\SyncEvent;
use App\Services\ImageRejected;
use App\Services\ImageStore;
use App\Services\RegisterSales;
use App\Services\StoreContext;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

class SyncController extends Controller
{
    use ActsForAStore;

    /** A product description is a paragraph or two, not a spec sheet. */
    private const DESCRIPTION_MAX = 2000;

    public function bootstrap(Request $request)
    {
        $context = $this->storeContext($request);

        $categories = Category::query()
            ->where('organization_id', $context->organizationId())
            ->whereNull('deleted_at')
            ->orderBy('sort_order')
            ->get();

        $products = Product::query()
            ->where('organization_id', $context->organizationId())
            ->whereNull('deleted_at')
            ->with('category')
            ->orderBy('name')
            ->get();

        $overrides = ProductStoreOverride::query()
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId())
            ->whereNull('deleted_at')
            ->get();

        $inventoryLevels = InventoryLevel::query()
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId())
            ->whereNull('deleted_at')
            ->get();

        // Named counter customers, shared by every till in the organization.
        $customers = PosCustomer::query()
            ->where('organization_id', $context->organizationId())
            ->orderBy('name')
            ->get();

        $cursor = now()->toIso8601String();

        SyncCursor::query()->updateOrCreate(
            [
                'store_id' => $context->storeId(),
                'user_id' => $context->user->id,
                'cursor_name' => 'catalog',
            ],
            [
                'organization_id' => $context->organizationId(),
                'cursor_value' => $cursor,
                'updated_at' => now(),
            ],
        );

        return response()->json([
            'organization' => $context->store->organization()->first(['id', 'name', 'slug']),
            'store' => $context->store->only(['id', 'name', 'code', 'timezone', 'currency_code']),
            // Was the paired device. A session is a person now, and the client
            // wants the same thing from it: something to put in the title bar
            // and a role to decide what to show.
            'user' => [
                'id' => $context->user->id,
                'fullName' => $context->user->name,
                'username' => $context->user->username,
                'email' => $context->user->email,
                'avatarUrl' => $context->user->avatar_url,
                'roleId' => $context->role,
            ],
            'catalog' => [
                'categories' => $categories,
                'products' => $products,
                'overrides' => $overrides,
                'inventoryLevels' => $inventoryLevels,
                'customers' => $customers->map(fn (PosCustomer $customer) => $this->presentCustomer($customer))->values(),
            ],
            'cursor' => $cursor,
        ]);
    }

    public function push(Request $request)
    {
        $context = $this->writableStoreContext($request);

        $sessionKey = $this->sessionKey($request);

        $validated = $request->validate([
            'organizationId' => ['required', 'uuid'],
            'storeId' => ['required', 'uuid'],
            'events' => ['required', 'array'],
            'events.*.id' => ['required', 'uuid'],
            'events.*.entityType' => ['required', 'string'],
            'events.*.entityId' => ['required', 'uuid'],
            'events.*.operation' => ['required', 'string'],
            'events.*.occurredAt' => ['required', 'date'],
            'events.*.payload' => ['required', 'array'],
        ]);

        abort_unless(
            $validated['organizationId'] === $context->organizationId() && $validated['storeId'] === $context->storeId(),
            403,
            'Session scope mismatch.',
        );

        $results = [];

        foreach ($validated['events'] as $eventData) {
            // Was the device id — what kept one till's replayed outbox from
            // colliding with another's. The token is the equivalent: one
            // signed-in client, one outbox, and re-pushing the same event from
            // the same client is still the no-op it has to be.
            $idempotencyKey = "{$sessionKey}:{$eventData['id']}";

            $syncEvent = SyncEvent::query()->firstOrCreate(
                ['idempotency_key' => $idempotencyKey],
                [
                    'organization_id' => $context->organizationId(),
                    'store_id' => $context->storeId(),
                    'user_id' => $context->user->id,
                    'entity_type' => $eventData['entityType'],
                    'entity_id' => $eventData['entityId'],
                    'operation' => $eventData['operation'],
                    // The audit copy, with any inline photo left out: a till
                    // syncs a picked photo as base64, and the file it becomes
                    // is the record of it. Re-applying reads the request, never
                    // this column, so nothing depends on the bytes being here.
                    'payload' => $this->withoutInlineImages($eventData['payload']),
                    'idempotency_key' => $idempotencyKey,
                    'received_at' => now(),
                ],
            );

            if ($syncEvent->applied_at) {
                $results[] = [
                    'eventId' => $eventData['id'],
                    'status' => 'duplicate',
                ];
                continue;
            }

            // Checked per event, never per batch: one push carries a sale, its
            // stock movement and perhaps a price change together, and refusing
            // the whole thing over the price change would lose the sale.
            //
            // `rejected` rather than `failed`. A failed event stays in the
            // till's outbox and is sent again on every flush, which is right
            // for a database hiccup and an infinite loop for a permission. A
            // rejected one is dropped by the client, and the catalog pull that
            // follows puts the server's version back on the device.
            $refusal = $this->refusalFor($context, $eventData['entityType'], $eventData['payload']);

            if ($refusal !== null) {
                $syncEvent->forceFill([
                    'failed_at' => now(),
                    'error_message' => $refusal,
                ])->save();

                $results[] = [
                    'eventId' => $eventData['id'],
                    'status' => 'rejected',
                    'message' => $refusal,
                ];
                continue;
            }

            try {
                DB::transaction(function () use ($context, $eventData): void {
                    match ($eventData['entityType']) {
                        // Sales are no longer synced: the till records each one
                        // through /api/register/orders before completing it.
                        // Failed, not rejected, so a sale an old till still has
                        // queued stays on it rather than being thrown away.
                        'order' => throw new \InvalidArgumentException('Sales are recorded through /api/register/orders. Update this till.'),
                        'category' => $this->applyCategoryEvent($context, $eventData['entityId'], $eventData['payload']),
                        'product' => $this->applyProductEvent($context, $eventData['entityId'], $eventData['payload']),
                        'inventory_adjustment' => $this->applyInventoryAdjustmentEvent($context, $eventData['entityId'], $eventData['payload']),
                        'customer' => $this->applyCustomerEvent($context, $eventData['entityId'], $eventData['payload']),
                        'app_event' => null,
                        default => throw new \InvalidArgumentException("Unsupported entity type [{$eventData['entityType']}]."),
                    };
                });

                $syncEvent->forceFill([
                    'applied_at' => now(),
                    'failed_at' => null,
                    'error_message' => null,
                ])->save();

                $results[] = [
                    'eventId' => $eventData['id'],
                    'status' => 'applied',
                ];
            } catch (\Throwable $exception) {
                $syncEvent->forceFill([
                    'failed_at' => now(),
                    'error_message' => $exception->getMessage(),
                ])->save();

                $results[] = [
                    'eventId' => $eventData['id'],
                    'status' => 'failed',
                    'message' => $exception->getMessage(),
                ];
            }
        }

        return response()->json([
            'results' => $results,
        ]);
    }

    public function pull(Request $request)
    {
        $context = $this->storeContext($request);
        $cursor = Carbon::parse($request->query('cursor', '1970-01-01T00:00:00Z'));
        $nextCursor = now()->toIso8601String();

        $categories = Category::query()
            ->where('organization_id', $context->organizationId())
            ->where('updated_at', '>', $cursor)
            ->get();

        $products = Product::query()
            ->where('organization_id', $context->organizationId())
            ->where('updated_at', '>', $cursor)
            ->get();

        $overrides = ProductStoreOverride::query()
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId())
            ->where('updated_at', '>', $cursor)
            ->get();

        $inventoryLevels = InventoryLevel::query()
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId())
            ->where('updated_at', '>', $cursor)
            ->get();

        // Deleted ones too, so another till can drop them.
        $customers = PosCustomer::withTrashed()
            ->where('organization_id', $context->organizationId())
            ->where('updated_at', '>', $cursor)
            ->get();

        SyncCursor::query()->updateOrCreate(
            [
                'store_id' => $context->storeId(),
                'user_id' => $context->user->id,
                'cursor_name' => 'catalog',
            ],
            [
                'organization_id' => $context->organizationId(),
                'cursor_value' => $nextCursor,
                'updated_at' => now(),
            ],
        );

        return response()->json([
            'cursor' => $nextCursor,
            'changes' => [
                'categories' => $categories,
                'products' => $products,
                'overrides' => $overrides,
                'inventoryLevels' => $inventoryLevels,
                'customers' => $customers->map(fn (PosCustomer $customer) => $this->presentCustomer($customer))->values(),
            ],
        ]);
    }

    /**
     * A named counter customer, made or changed at a till.
     *
     * The id is the till's own, so a customer made offline keeps it. One that
     * belongs to another organization is refused outright — a till cannot
     * rename somebody else's customer by guessing an id. Consent to points is
     * recorded only when the key is sent, so an older till's edit does not
     * withdraw it.
     *
     * @param  array<string, mixed>  $payload
     */
    private function applyCustomerEvent(StoreContext $context, string $entityId, array $payload): void
    {
        $existing = PosCustomer::withTrashed()->whereKey($entityId)->first();

        if ($existing !== null && $existing->organization_id !== $context->organizationId()) {
            throw new \InvalidArgumentException('That customer belongs to another shop.');
        }

        $customer = $existing ?? new PosCustomer(['id' => $entityId, 'organization_id' => $context->organizationId()]);

        $text = fn (string $key, int $max) => array_key_exists($key, $payload)
            ? (is_string($payload[$key]) && trim($payload[$key]) !== '' ? mb_substr(trim($payload[$key]), 0, $max) : null)
            : $customer->{$key};

        $customer->fill([
            'name' => $text('name', 255) ?? $customer->name ?? 'Customer',
            'phone' => $text('phone', 40),
            'email' => $text('email', 190),
            'notes' => $text('notes', 2000),
        ]);

        if (array_key_exists('loyaltyConsentAt', $payload)) {
            $customer->loyalty_consent_at = $payload['loyaltyConsentAt'] ? Carbon::parse($payload['loyaltyConsentAt']) : null;
        }

        $customer->deleted_at = ! empty($payload['deletedAt']) ? Carbon::parse($payload['deletedAt']) : null;
        $customer->save();
    }

    /** @return array<string, mixed> */
    private function presentCustomer(PosCustomer $customer): array
    {
        return [
            'id' => $customer->id,
            'name' => $customer->name,
            'phone' => $customer->phone,
            'email' => $customer->email,
            'notes' => $customer->notes,
            'loyaltyConsentAt' => $customer->loyalty_consent_at?->toIso8601String(),
            'createdAt' => $customer->created_at?->toIso8601String(),
            'updatedAt' => $customer->updated_at?->toIso8601String(),
            'deletedAt' => $customer->deleted_at?->toIso8601String(),
        ];
    }

    /**
     * Why this person may not make this change, or null when they may.
     *
     * The till hides the Products and Inventory pages from roles without them,
     * and the seller app hides the product form's Save from cashiers. Both are
     * courtesies; this is the control. A cashier's token used to be able to
     * reprice the whole catalog.
     *
     * A stock movement that belongs to an order is not a catalog change. The
     * register records a sale's stock as a `sale` adjustment, and a void puts
     * it back as a `manual_correction` carrying the voided order's id — both
     * part of ringing up, which every role at the register does. What needs
     * the Inventory page is a movement with no order behind it: a restock, a
     * count, a correction.
     */
    private function refusalFor(StoreContext $context, string $entityType, array $payload): ?string
    {
        $belongsToAnOrder = ($payload['adjustmentType'] ?? null) === 'sale' || ! empty($payload['orderId']);

        $page = match ($entityType) {
            'product', 'category' => 'products',
            'inventory_adjustment' => $belongsToAnOrder ? null : 'inventory',
            default => null,
        };

        if ($page === null || $context->can($page)) {
            return null;
        }

        return $page === 'products'
            ? 'Your role cannot change products. Ask a manager to make this change.'
            : 'Your role cannot adjust stock. Ask a manager to make this change.';
    }

    private function applyCategoryEvent(StoreContext $context, string $entityId, array $payload): void
    {
        Category::query()->updateOrCreate(
            ['id' => $entityId],
            [
                'organization_id' => $context->organizationId(),
                'name' => $payload['name'] ?? 'Unnamed category',
                'sort_order' => $payload['sortOrder'] ?? 0,
                // Nothing pairs any more, so there is no terminal to credit.
                // The column stays for the rows that have one.
                'created_by_device_id' => null,
                'deleted_at' => ! empty($payload['deletedAt']) ? Carbon::parse($payload['deletedAt']) : null,
            ],
        );
    }

    private function applyProductEvent(StoreContext $context, string $entityId, array $payload): void
    {
        // Read first: an update that simply omits a field must not blank it.
        // Older tills push a payload with no gallery, no brand and no
        // packaging in it at all, and their next price change should not strip
        // the photographs off a product someone else set up.
        $existing = Product::query()->whereKey($entityId)->first();
        $payload = $this->withStoredImages($payload);

        $product = Product::query()->updateOrCreate(
            ['id' => $entityId],
            [
                'organization_id' => $context->organizationId(),
                'business_modes' => $this->businessModesFor($context, $entityId, $payload),
                'category_id' => $payload['categoryId'] ?? null,
                'sku' => $payload['sku'] ?? null,
                'barcode' => $payload['barcode'] ?? null,
                'name' => $payload['name'] ?? 'Unnamed product',
                'product_type' => $payload['productType'] ?? 'standard',
                // Stored as a percentage; the till sends a fraction. See
                // percentTaxRate. Absent on a new product is the standard 12%.
                'tax_rate' => array_key_exists('taxRate', $payload)
                    ? RegisterSales::percentTaxRate($payload['taxRate'])
                    : ($existing?->tax_rate ?? 12),
                'price_cents' => $payload['priceCents'] ?? 0,
                'track_inventory' => $payload['trackInventory'] ?? true,
                'is_active' => $payload['isActive'] ?? true,
                // How a product looks on a storefront, which until now got no
                // further than the till it was typed into. None of these were
                // written here: a merchant who photographed a product, marked
                // it down, or labelled it "per kg" saw all three on their own
                // screen and none of them online, silently, because the row
                // saved and synced exactly as expected. The gallery this
                // release adds would have gone the same way.
                'image_url' => $this->keptField($payload, 'imageUrl', $existing?->image_url),
                'photo_urls' => $this->keptGallery($payload, $existing?->photo_urls),
                'brand' => $this->keptField($payload, 'brand', $existing?->brand),
                'packaging_type' => $this->keptField($payload, 'packagingType', $existing?->packaging_type),
                'unit_label' => $this->keptField($payload, 'unitLabel', $existing?->unit_label),
                // What the product is, in the shop's words — shown on the
                // storefront's product page. Capped rather than refused: a
                // paste of a supplier's whole spec sheet is not worth losing
                // the rest of the product over.
                'description' => ($description = $this->keptField($payload, 'description', $existing?->description)) === null
                    ? null
                    : mb_substr($description, 0, self::DESCRIPTION_MAX),
                'compare_at_price_cents' => array_key_exists('compareAtPriceCents', $payload)
                    ? $payload['compareAtPriceCents']
                    : $existing?->compare_at_price_cents,
                'low_stock_threshold' => array_key_exists('lowStockThreshold', $payload)
                    ? $payload['lowStockThreshold']
                    : $existing?->low_stock_threshold,
                // Nothing pairs any more, so there is no terminal to credit.
                // The column stays for the rows that have one.
                'created_by_device_id' => null,
                'deleted_at' => ! empty($payload['deletedAt']) ? Carbon::parse($payload['deletedAt']) : null,
            ],
        );

        if ($product->track_inventory) {
            $inventory = InventoryLevel::query()->firstOrNew([
                'organization_id' => $context->organizationId(),
                'store_id' => $context->storeId(),
                'product_id' => $product->id,
            ]);

            if (! $inventory->exists) {
                $inventory->id = (string) str()->uuid();
                $inventory->qty_on_hand = (float) ($payload['stockQty'] ?? 0);
            }

            if (array_key_exists('lowStockThreshold', $payload)) {
                $inventory->reorder_level = $payload['lowStockThreshold'] !== null
                    ? (float) $payload['lowStockThreshold']
                    : null;
            }

            $inventory->organization_id = $context->organizationId();
            $inventory->store_id = $context->storeId();
            $inventory->updated_at = now();
            $inventory->deleted_at = null;
            $inventory->save();
        }
    }

    /**
     * A presentation field the client may simply not have sent.
     *
     * An absent key is "no opinion" and keeps what is stored; a present one
     * wins, including an explicit empty string, which is a merchant clearing
     * the field and is stored as null rather than as "".
     */
    /**
     * Pull any photo sent inline as a data URL out into a file, and put its
     * URL in the payload instead.
     *
     * The till reads a picked photo with FileReader.readAsDataURL and syncs
     * exactly that, so until this ran every photographed product carried tens
     * of kilobytes of base64 in its row and in every catalog response. Doing
     * it here, on arrival, means an offline till needs no change at all.
     *
     * A photo that is not really an image, or is too large, is dropped rather
     * than failing the product: a price change must not be lost because the
     * picture beside it was bad. A rejected primary photo leaves the key out,
     * so keptField keeps whatever the product already had.
     *
     * @param  array<string, mixed>  $payload
     * @return array<string, mixed>
     */
    private function withStoredImages(array $payload): array
    {
        $images = app(ImageStore::class);

        $store = function (string $dataUrl) use ($images): ?string {
            try {
                return ProductImageController::urlFor($images->store($dataUrl, ProductImageController::DIRECTORY));
            } catch (ImageRejected) {
                return null;
            }
        };

        if (ImageStore::isDataUrl($payload['imageUrl'] ?? null)) {
            $url = $store($payload['imageUrl']);

            if ($url === null) {
                unset($payload['imageUrl']);
            } else {
                $payload['imageUrl'] = $url;
            }
        }

        if (is_array($payload['photoUrls'] ?? null)) {
            $payload['photoUrls'] = array_values(array_filter(array_map(
                fn ($photo) => ImageStore::isDataUrl($photo) ? $store($photo) : $photo,
                $payload['photoUrls'],
            ), fn ($photo) => $photo !== null));
        }

        return $payload;
    }

    /**
     * @param  array<string, mixed>  $payload
     * @return array<string, mixed>
     */
    private function withoutInlineImages(array $payload): array
    {
        array_walk_recursive($payload, function (&$value): void {
            if (ImageStore::isDataUrl($value)) {
                $value = '[inline image]';
            }
        });

        return $payload;
    }

    private function keptField(array $payload, string $key, ?string $stored): ?string
    {
        if (! array_key_exists($key, $payload)) {
            return $stored;
        }

        $value = is_string($payload[$key]) ? trim($payload[$key]) : null;

        return $value === '' ? null : $value;
    }

    /**
     * The extra photographs, after the primary one in image_url.
     *
     * Strings only, blanks dropped, and the merchant's order kept — this is
     * the whole of what the gallery's ordering means. An absent key keeps
     * whatever is stored, so a till that predates galleries cannot strip one.
     *
     * @param  array<string, mixed>  $payload
     * @param  array<int, string>|null  $stored
     * @return array<int, string>|null
     */
    private function keptGallery(array $payload, ?array $stored): ?array
    {
        if (! array_key_exists('photoUrls', $payload)) {
            return $stored;
        }

        if (! is_array($payload['photoUrls'])) {
            return null;
        }

        $urls = array_values(array_filter(
            array_map(fn ($url) => is_string($url) ? trim($url) : '', $payload['photoUrls']),
            fn (string $url) => $url !== '',
        ));

        return $urls === [] ? null : $urls;
    }

    /**
     * Which storefronts a product is sold in.
     *
     * This decides whether a product is sellable at all: the storefront catalog
     * filters on it, and OnlineOrderController refuses any line whose product
     * does not carry the store's mode. Until 2026-08-27 nothing on this path
     * wrote it, so every product a merchant created in their own till was
     * invisible online and unorderable — silently, because it saved, synced and
     * listed in the POS exactly as expected.
     *
     * Order of preference:
     *  1. what the client sent, for an organization running several modes
     *  2. what the product already had, so an update that omits the field does
     *     not quietly un-list it
     *  3. the store's own mode, which is what a single-mode merchant means
     *
     * @param  array<string, mixed>  $payload
     * @return array<int, string>
     */
    private function businessModesFor(StoreContext $context, string $entityId, array $payload): array
    {
        $sent = $payload['businessModes'] ?? null;
        if (is_array($sent) && $sent !== []) {
            return array_values(array_unique(array_filter($sent, 'is_string')));
        }

        $existing = Product::query()->where('id', $entityId)->value('business_modes');
        if (is_array($existing) && $existing !== []) {
            return $existing;
        }

        $storeMode = $context->store->business_mode;

        return $storeMode === null ? [] : [$storeMode];
    }

    private function applyInventoryAdjustmentEvent(StoreContext $context, string $entityId, array $payload): void
    {
        if (empty($payload['productId']) || ! array_key_exists('quantityDelta', $payload)) {
            throw new \InvalidArgumentException('Inventory adjustment payload is missing required fields.');
        }

        $product = Product::query()->find($payload['productId']);
        if (! $product || ! $product->track_inventory) {
            return;
        }

        // A sale's stock is recorded with the sale itself (RegisterSales). The
        // till used to send it a second time as its own adjustment, and the
        // server took it off the shelf twice.
        if (($payload['adjustmentType'] ?? null) === 'sale' && ! empty($payload['orderId'])) {
            return;
        }

        app(RegisterSales::class)->adjustStock(
            $context,
            $entityId,
            $payload['productId'],
            (float) $payload['quantityDelta'],
            $payload['adjustmentType'] ?? 'manual_correction',
            $payload['reason'] ?? null,
            $payload['orderId'] ?? null,
            now(),
        );
    }

    /**
     * A stable id for this signed-in client, for the idempotency key.
     *
     * The token id, not the user id: one person can have the counter tablet and
     * their own phone signed in at once, each with its own outbox, and keying
     * both on the user would make the second one's events look like replays of
     * the first's.
     */
    private function sessionKey(Request $request): string
    {
        return (string) ($request->user()?->currentAccessToken()?->id ?? 'session');
    }
}
