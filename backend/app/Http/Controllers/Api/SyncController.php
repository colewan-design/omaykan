<?php

namespace App\Http\Controllers\Api;

use App\Events\OrderPlaced;
use App\Events\OrderStatusChanged;
use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\InventoryAdjustment;
use App\Models\InventoryLevel;
use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Payment;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\SyncCursor;
use App\Models\SyncEvent;
use App\Services\StoreContext;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

class SyncController extends Controller
{
    use ActsForAStore;

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
            ],
            'cursor' => $cursor,
        ]);
    }

    public function push(Request $request)
    {
        $context = $this->storeContext($request);

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
                    'payload' => $eventData['payload'],
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

            try {
                DB::transaction(function () use ($context, $eventData): void {
                    match ($eventData['entityType']) {
                        'order' => $this->applyOrderEvent($context, $eventData['payload']),
                        'category' => $this->applyCategoryEvent($context, $eventData['entityId'], $eventData['payload']),
                        'product' => $this->applyProductEvent($context, $eventData['entityId'], $eventData['payload']),
                        'inventory_adjustment' => $this->applyInventoryAdjustmentEvent($context, $eventData['entityId'], $eventData['payload']),
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
            ],
        ]);
    }

    private function applyOrderEvent(StoreContext $context, array $payload): void
    {
        $orderData = $payload['order'] ?? null;
        $items = $payload['items'] ?? [];
        $payments = $payload['payments'] ?? [];

        if (! is_array($orderData) || ! isset($orderData['id'], $orderData['ticketNumber'])) {
            throw new \InvalidArgumentException('Order payload is missing required fields.');
        }

        $existing = Order::query()->whereKey($orderData['id'])->first();
        $previousStatus = $existing?->order_status;

        $order = Order::query()->updateOrCreate(
            ['id' => $orderData['id']],
            [
                'organization_id' => $context->organizationId(),
                'store_id' => $context->storeId(),
                'user_id' => $context->user->id,
                'user_id' => $orderData['userId'] ?? null,
                'ticket_number' => $orderData['ticketNumber'],
                'order_status' => $orderData['orderStatus'] ?? 'completed',
                'order_type' => $orderData['orderType'] ?? 'takeaway',
                'payment_status' => $orderData['paymentStatus'] ?? 'paid',
                'subtotal_cents' => $orderData['subtotalCents'] ?? 0,
                'tax_cents' => $orderData['taxCents'] ?? 0,
                'total_cents' => $orderData['totalCents'] ?? 0,
                'business_date' => $orderData['businessDate'] ?? now()->toDateString(),
                'completed_at' => $orderData['completedAt'] ?? now(),
            ],
        );

        $order->items()->delete();
        InventoryAdjustment::query()
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId())
            ->where('order_id', $order->id)
            ->delete();

        foreach ($items as $item) {
            $orderItem = OrderItem::query()->create([
                'id' => $item['id'] ?? (string) str()->uuid(),
                'organization_id' => $context->organizationId(),
                'store_id' => $context->storeId(),
                'order_id' => $order->id,
                'product_id' => $item['productId'] ?? null,
                'product_name' => $item['productName'] ?? $item['name'] ?? 'Unknown product',
                'quantity' => $item['quantity'] ?? 1,
                'unit_price_cents' => $item['unitPriceCents'] ?? 0,
                'line_total_cents' => $item['lineTotalCents'] ?? 0,
            ]);

            $product = ! empty($item['productId'])
                ? Product::query()->find($item['productId'])
                : null;

            if ($product?->track_inventory) {
                $quantity = -1 * (float) ($item['quantity'] ?? 1);
                $this->recordInventoryAdjustment(
                    $context,
                    $item['inventoryAdjustmentId'] ?? (string) str()->uuid(),
                    $item['productId'],
                    $quantity,
                    'sale',
                    "order:{$order->ticket_number}",
                    $order->id,
                    $orderItem->created_at ?? now(),
                );
            }
        }

        $order->payments()->delete();
        foreach ($payments as $payment) {
            Payment::query()->create([
                'id' => $payment['id'] ?? (string) str()->uuid(),
                'organization_id' => $context->organizationId(),
                'store_id' => $context->storeId(),
                'order_id' => $order->id,
                'payment_method' => $payment['paymentMethod'] ?? 'cash',
                'amount_cents' => $payment['amountCents'] ?? ($orderData['totalCents'] ?? 0),
                'tendered_cents' => $payment['tenderedCents'] ?? null,
                'change_cents' => $payment['changeCents'] ?? null,
            ]);
        }

        // Broadcast after the write, but only once the surrounding transaction
        // commits — otherwise a listener can race ahead and query a row that is
        // not visible yet, or hear about an order the rollback removed.
        DB::afterCommit(function () use ($order, $existing, $previousStatus): void {
            if ($existing === null) {
                OrderPlaced::dispatch($order);

                return;
            }

            if ($previousStatus !== $order->order_status) {
                OrderStatusChanged::dispatch($order, $previousStatus);
            }
        });
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
                'tax_rate' => $payload['taxRate'] ?? 12,
                'price_cents' => $payload['priceCents'] ?? 0,
                'track_inventory' => $payload['trackInventory'] ?? true,
                'is_active' => $payload['isActive'] ?? true,
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

        $this->recordInventoryAdjustment(
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

    private function recordInventoryAdjustment(
        StoreContext $context,
        string $adjustmentId,
        string $productId,
        float $quantityDelta,
        string $adjustmentType,
        ?string $reason,
        ?string $orderId,
        Carbon|string $createdAt,
    ): void {
        InventoryAdjustment::query()->updateOrCreate(
            ['id' => $adjustmentId],
            [
                'organization_id' => $context->organizationId(),
                'store_id' => $context->storeId(),
                'product_id' => $productId,
                'order_id' => $orderId,
                'adjustment_type' => $adjustmentType,
                'quantity_delta' => $quantityDelta,
                'reason' => $reason,
                'created_at' => $createdAt,
                'synced_at' => now(),
                'deleted_at' => null,
            ],
        );

        $inventoryLevel = InventoryLevel::query()->firstOrNew([
            'organization_id' => $context->organizationId(),
            'store_id' => $context->storeId(),
            'product_id' => $productId,
        ]);

        if (! $inventoryLevel->exists) {
            $inventoryLevel->id = (string) str()->uuid();
            $inventoryLevel->qty_on_hand = 0;
        }

        $inventoryLevel->organization_id = $context->organizationId();
        $inventoryLevel->store_id = $context->storeId();
        $inventoryLevel->product_id = $productId;
        $inventoryLevel->qty_on_hand = max(0, (float) $inventoryLevel->qty_on_hand + $quantityDelta);
        $inventoryLevel->updated_at = now();
        $inventoryLevel->deleted_at = null;
        $inventoryLevel->save();
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
