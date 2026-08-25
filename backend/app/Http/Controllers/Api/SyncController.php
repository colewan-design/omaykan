<?php

namespace App\Http\Controllers\Api;

use App\Events\OrderPlaced;
use App\Events\OrderStatusChanged;
use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\Device;
use App\Models\InventoryAdjustment;
use App\Models\InventoryLevel;
use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Payment;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\SyncCursor;
use App\Models\SyncEvent;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

class SyncController extends Controller
{
    public function bootstrap(Request $request)
    {
        $device = $this->deviceFromRequest($request);

        $categories = Category::query()
            ->where('organization_id', $device->organization_id)
            ->whereNull('deleted_at')
            ->orderBy('sort_order')
            ->get();

        $products = Product::query()
            ->where('organization_id', $device->organization_id)
            ->whereNull('deleted_at')
            ->with('category')
            ->orderBy('name')
            ->get();

        $overrides = ProductStoreOverride::query()
            ->where('organization_id', $device->organization_id)
            ->where('store_id', $device->store_id)
            ->whereNull('deleted_at')
            ->get();

        $inventoryLevels = InventoryLevel::query()
            ->where('organization_id', $device->organization_id)
            ->where('store_id', $device->store_id)
            ->whereNull('deleted_at')
            ->get();

        $cursor = now()->toIso8601String();

        SyncCursor::query()->updateOrCreate(
            [
                'device_id' => $device->id,
                'cursor_name' => 'catalog',
            ],
            [
                'organization_id' => $device->organization_id,
                'store_id' => $device->store_id,
                'cursor_value' => $cursor,
                'updated_at' => now(),
            ],
        );

        return response()->json([
            'organization' => $device->organization()->first(['id', 'name', 'slug']),
            'store' => $device->store()->first(['id', 'name', 'code', 'timezone', 'currency_code']),
            'device' => [
                'id' => $device->id,
                'name' => $device->device_name,
                'platform' => $device->platform,
                'appVersion' => $device->app_version,
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
        $device = $this->deviceFromRequest($request);

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
            $validated['organizationId'] === $device->organization_id && $validated['storeId'] === $device->store_id,
            403,
            'Device scope mismatch.',
        );

        $results = [];

        foreach ($validated['events'] as $eventData) {
            $idempotencyKey = "{$device->id}:{$eventData['id']}";

            $syncEvent = SyncEvent::query()->firstOrCreate(
                ['idempotency_key' => $idempotencyKey],
                [
                    'organization_id' => $device->organization_id,
                    'store_id' => $device->store_id,
                    'device_id' => $device->id,
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
                DB::transaction(function () use ($device, $eventData): void {
                    match ($eventData['entityType']) {
                        'order' => $this->applyOrderEvent($device, $eventData['payload']),
                        'category' => $this->applyCategoryEvent($device, $eventData['entityId'], $eventData['payload']),
                        'product' => $this->applyProductEvent($device, $eventData['entityId'], $eventData['payload']),
                        'inventory_adjustment' => $this->applyInventoryAdjustmentEvent($device, $eventData['entityId'], $eventData['payload']),
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
        $device = $this->deviceFromRequest($request);
        $cursor = Carbon::parse($request->query('cursor', '1970-01-01T00:00:00Z'));
        $nextCursor = now()->toIso8601String();

        $categories = Category::query()
            ->where('organization_id', $device->organization_id)
            ->where('updated_at', '>', $cursor)
            ->get();

        $products = Product::query()
            ->where('organization_id', $device->organization_id)
            ->where('updated_at', '>', $cursor)
            ->get();

        $overrides = ProductStoreOverride::query()
            ->where('organization_id', $device->organization_id)
            ->where('store_id', $device->store_id)
            ->where('updated_at', '>', $cursor)
            ->get();

        $inventoryLevels = InventoryLevel::query()
            ->where('organization_id', $device->organization_id)
            ->where('store_id', $device->store_id)
            ->where('updated_at', '>', $cursor)
            ->get();

        SyncCursor::query()->updateOrCreate(
            [
                'device_id' => $device->id,
                'cursor_name' => 'catalog',
            ],
            [
                'organization_id' => $device->organization_id,
                'store_id' => $device->store_id,
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

    private function applyOrderEvent(Device $device, array $payload): void
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
                'organization_id' => $device->organization_id,
                'store_id' => $device->store_id,
                'device_id' => $device->id,
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
            ->where('organization_id', $device->organization_id)
            ->where('store_id', $device->store_id)
            ->where('order_id', $order->id)
            ->delete();

        foreach ($items as $item) {
            $orderItem = OrderItem::query()->create([
                'id' => $item['id'] ?? (string) str()->uuid(),
                'organization_id' => $device->organization_id,
                'store_id' => $device->store_id,
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
                    $device,
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
                'organization_id' => $device->organization_id,
                'store_id' => $device->store_id,
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

    private function applyCategoryEvent(Device $device, string $entityId, array $payload): void
    {
        Category::query()->updateOrCreate(
            ['id' => $entityId],
            [
                'organization_id' => $device->organization_id,
                'name' => $payload['name'] ?? 'Unnamed category',
                'sort_order' => $payload['sortOrder'] ?? 0,
                'created_by_device_id' => $device->id,
                'deleted_at' => ! empty($payload['deletedAt']) ? Carbon::parse($payload['deletedAt']) : null,
            ],
        );
    }

    private function applyProductEvent(Device $device, string $entityId, array $payload): void
    {
        $product = Product::query()->updateOrCreate(
            ['id' => $entityId],
            [
                'organization_id' => $device->organization_id,
                'category_id' => $payload['categoryId'] ?? null,
                'sku' => $payload['sku'] ?? null,
                'barcode' => $payload['barcode'] ?? null,
                'name' => $payload['name'] ?? 'Unnamed product',
                'product_type' => $payload['productType'] ?? 'standard',
                'tax_rate' => $payload['taxRate'] ?? 12,
                'price_cents' => $payload['priceCents'] ?? 0,
                'track_inventory' => $payload['trackInventory'] ?? true,
                'is_active' => $payload['isActive'] ?? true,
                'created_by_device_id' => $device->id,
                'deleted_at' => ! empty($payload['deletedAt']) ? Carbon::parse($payload['deletedAt']) : null,
            ],
        );

        if ($product->track_inventory) {
            $inventory = InventoryLevel::query()->firstOrNew([
                'organization_id' => $device->organization_id,
                'store_id' => $device->store_id,
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

            $inventory->organization_id = $device->organization_id;
            $inventory->store_id = $device->store_id;
            $inventory->updated_at = now();
            $inventory->deleted_at = null;
            $inventory->save();
        }
    }

    private function applyInventoryAdjustmentEvent(Device $device, string $entityId, array $payload): void
    {
        if (empty($payload['productId']) || ! array_key_exists('quantityDelta', $payload)) {
            throw new \InvalidArgumentException('Inventory adjustment payload is missing required fields.');
        }

        $product = Product::query()->find($payload['productId']);
        if (! $product || ! $product->track_inventory) {
            return;
        }

        $this->recordInventoryAdjustment(
            $device,
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
        Device $device,
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
                'organization_id' => $device->organization_id,
                'store_id' => $device->store_id,
                'device_id' => $device->id,
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
            'organization_id' => $device->organization_id,
            'store_id' => $device->store_id,
            'product_id' => $productId,
        ]);

        if (! $inventoryLevel->exists) {
            $inventoryLevel->id = (string) str()->uuid();
            $inventoryLevel->qty_on_hand = 0;
        }

        $inventoryLevel->organization_id = $device->organization_id;
        $inventoryLevel->store_id = $device->store_id;
        $inventoryLevel->product_id = $productId;
        $inventoryLevel->qty_on_hand = max(0, (float) $inventoryLevel->qty_on_hand + $quantityDelta);
        $inventoryLevel->updated_at = now();
        $inventoryLevel->deleted_at = null;
        $inventoryLevel->save();
    }

    private function deviceFromRequest(Request $request): Device
    {
        $device = $request->user();
        abort_unless($device instanceof Device, 403, 'Authenticated device required.');

        $device->forceFill([
            'last_seen_at' => now(),
        ])->save();

        return $device;
    }
}
