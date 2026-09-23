<?php

namespace Database\Seeders;

use App\Models\Device;
use App\Models\InventoryLevel;
use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Organization;
use App\Models\Payment;
use App\Models\Product;
use App\Models\Store;
use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/**
 * Six weeks of trading for every demo seller, so the dashboard has something
 * to draw.
 *
 * DemoSellerSeeder fills the shelves; nothing filled the till. A freshly
 * seeded install therefore opened on a seller dashboard reading ₱0.00 across
 * every card, an empty sales chart and "Nothing waiting" — which looks like a
 * broken screen rather than a new shop, and makes the layout impossible to
 * judge.
 *
 * What this produces per store, working backwards from what the dashboard
 * actually reads:
 *
 *   - **Settled history** across the last 42 days, weighted so weekends and
 *     the lunch/merienda peaks are visibly busier than a Tuesday morning.
 *     Feeds Total Revenue, Transactions, Average Order Value, Sales Overview
 *     and Top Products.
 *   - **All four channels** — dine-in, takeaway, online pickup, online
 *     delivery — so Sales by Channel has more than one arc. A store's mode
 *     decides the mix: a nail salon is nearly all in-person, a grocery leans
 *     online.
 *   - **Named repeat customers**, so Return Customers is not 0.0%.
 *   - **Today's live queue**: a few orders still preparing or ready, one
 *     unpaid online order, one delivery with nobody carrying it and one with
 *     a rider already en route. Feeds Pending Orders, Orders needing action,
 *     Deliveries, Waiting on a rider and Unpaid online.
 *   - **Stock pulled down** on a handful of products — some to zero, some to
 *     just under their reorder level — so Stock alerts shows both severities
 *     rather than an empty card.
 *
 * Deterministic: seeded RNG and dates relative to today, so two people running
 * it see the same shop and a screenshot taken from it stays reproducible. Safe
 * to re-run — every order carries a `DEMO-` ticket prefix and the whole set is
 * deleted and rebuilt per store rather than appended to.
 */
class DemoOrderSeeder extends Seeder
{
    use WithoutModelEvents;

    /** How far back the settled history runs. */
    private const HISTORY_DAYS = 42;

    /** Ticket prefix that marks a row as ours, and therefore safe to replace. */
    private const TICKET_PREFIX = 'DEMO-';

    /** Products carry a rate (0.12); the column is a percentage (12.00). */
    private const TAX_RATE_SCALE = 100;

    /**
     * Per business mode: how a day's orders split across the four channels, and
     * how many a busy day sees. A salon books appointments in person; a grocery
     * that has just put its catalog online sells most of it that way.
     *
     * @var array<string, array{channels: array<string, int>, perDay: array{0: int, 1: int}, items: array{0: int, 1: int}}>
     */
    private const MODE_PROFILES = [
        'coffee-shop' => [
            'channels' => ['dine_in' => 40, 'takeaway' => 35, 'online_pickup' => 15, 'online_delivery' => 10],
            'perDay' => [6, 14],
            'items' => [1, 3],
        ],
        'grocery' => [
            'channels' => ['dine_in' => 0, 'takeaway' => 45, 'online_pickup' => 20, 'online_delivery' => 35],
            'perDay' => [5, 12],
            'items' => [2, 6],
        ],
        'restaurant' => [
            'channels' => ['dine_in' => 55, 'takeaway' => 20, 'online_pickup' => 10, 'online_delivery' => 15],
            'perDay' => [8, 18],
            'items' => [2, 5],
        ],
        'nail-salon' => [
            'channels' => ['dine_in' => 75, 'takeaway' => 10, 'online_pickup' => 15, 'online_delivery' => 0],
            'perDay' => [3, 8],
            'items' => [1, 2],
        ],
    ];

    /**
     * Regulars, so Return Customers has something to count. Roughly half of
     * each day's orders get a name off this list and the rest stay walk-ins,
     * which is about what a counter actually records.
     *
     * @var array<int, array{name: string, phone: string}>
     */
    private const REGULARS = [
        ['name' => 'Maria Santos', 'phone' => '0917 555 0101'],
        ['name' => 'Juan Dela Cruz', 'phone' => '0917 555 0102'],
        ['name' => 'Ana Reyes', 'phone' => '0918 555 0103'],
        ['name' => 'Mark Villanueva', 'phone' => '0919 555 0104'],
        ['name' => 'Paolo Reyes', 'phone' => '0917 555 0105'],
        ['name' => 'Cristina Lim', 'phone' => '0920 555 0106'],
        ['name' => 'Ava Lim', 'phone' => '0921 555 0107'],
        ['name' => 'Cruz Family', 'phone' => '0922 555 0108'],
    ];

    /**
     * Customers who bought once and have not come back.
     *
     * Without these, every named order belonged to one of eight regulars and
     * Return Customers read a flat 100.0% — a number no shop has ever seen,
     * and one that made the card useless for judging the layout. Each name is
     * handed out at most twice, so the rate lands somewhere believable.
     *
     * @var array<int, string>
     */
    private const ONE_OFF_NAMES = [
        'Liza Mendoza', 'Rico Alvarez', 'Bea Tolentino', 'Jomar Castillo',
        'Grace Pascual', 'Nico Aquino', 'Trina Soriano', 'Edgar Ramos',
        'Mylene Cabrera', 'Dante Ocampo', 'Rhea Navarro', 'Kiko Salazar',
        'Vilma Estrada', 'Arnel Domingo', 'Charisse Yap', 'Bong Hidalgo',
        'Katrina Bautista', 'Ferdie Manalo', 'Joy Sarmiento', 'Elmer Padilla',
        'Divine Rosales', 'Toto Lagman', 'Sheryl Angeles', 'Bernard Ilagan',
    ];

    /** Rolls through ONE_OFF_NAMES so the same stranger is not used all month. */
    private int $oneOffCursor = 0;

    /** Baguio streets, for delivery rows that have to render an address. */
    private const ADDRESSES = [
        ['line' => '24 Leonard Wood Road, Baguio City', 'lat' => 16.4108, 'lng' => 120.6042],
        ['line' => '7 Bokawkan Road, Baguio City', 'lat' => 16.4231, 'lng' => 120.5884],
        ['line' => '112 Marcos Highway, Baguio City', 'lat' => 16.3998, 'lng' => 120.5817],
        ['line' => '3 Outlook Drive, Baguio City', 'lat' => 16.4055, 'lng' => 120.6188],
        ['line' => '58 Naguilian Road, Baguio City', 'lat' => 16.4076, 'lng' => 120.5712],
    ];

    /** Seeded so the demo shop is the same shop on every machine. */
    private int $randomState = 20260914;

    public function run(): void
    {
        $stores = Store::query()->with('organization')->get();

        foreach ($stores as $store) {
            $organization = $store->organization;
            if (! $organization instanceof Organization) {
                continue;
            }

            $products = Product::query()
                ->where('organization_id', $organization->id)
                ->where('is_active', true)
                ->orderBy('name')
                ->get();

            if ($products->isEmpty()) {
                $this->command?->warn("Skipping {$store->name} — no products to sell.");

                continue;
            }

            DB::transaction(function () use ($organization, $store, $products) {
                $this->clearPreviousDemoOrders($store);

                $device = $this->demoDevice($organization, $store);
                $cashier = $this->cashierFor($organization);
                $profile = self::MODE_PROFILES[$store->business_mode] ?? self::MODE_PROFILES['coffee-shop'];

                $ticket = 1000;
                $this->seedHistory($organization, $store, $device, $cashier, $products, $profile, $ticket);
                $this->seedTodaysQueue($organization, $store, $device, $cashier, $products, $profile, $ticket);
                $this->seedStockAlerts($organization, $store, $products);
            });

            $this->command?->info("Seeded demo orders for {$store->name}.");
        }
    }

    /**
     * Replace rather than append: re-running must not double a shop's revenue,
     * and only rows this seeder wrote are in scope.
     */
    private function clearPreviousDemoOrders(Store $store): void
    {
        $ids = Order::query()
            ->withTrashed()
            ->where('store_id', $store->id)
            ->where('ticket_number', 'like', self::TICKET_PREFIX.'%')
            ->pluck('id');

        if ($ids->isEmpty()) {
            return;
        }

        Payment::query()->withTrashed()->whereIn('order_id', $ids)->forceDelete();
        OrderItem::query()->withTrashed()->whereIn('order_id', $ids)->forceDelete();
        Order::query()->withTrashed()->whereIn('id', $ids)->forceDelete();
    }

    /**
     * Orders need a device, and a seeded shop has never had one pair. This
     * stands in for the till so the foreign key resolves.
     */
    private function demoDevice(Organization $organization, Store $store): Device
    {
        return Device::query()->firstOrCreate(
            [
                'organization_id' => $organization->id,
                'store_id' => $store->id,
                'device_name' => 'Demo counter',
            ],
            [
                'platform' => 'web',
                'status' => 'active',
                'activated_at' => now(),
                'last_seen_at' => now(),
            ],
        );
    }

    private function cashierFor(Organization $organization): ?User
    {
        return User::query()
            ->whereHas('organizationMemberships', fn ($query) => $query->where('organization_id', $organization->id))
            ->orderBy('created_at')
            ->first();
    }

    /**
     * Everything before today: settled, paid, and delivered. History is what
     * the analytics half of the dashboard reads, and history has no loose ends.
     *
     * @param  \Illuminate\Support\Collection<int, Product>  $products
     * @param  array{channels: array<string, int>, perDay: array{0: int, 1: int}, items: array{0: int, 1: int}}  $profile
     */
    private function seedHistory(
        Organization $organization,
        Store $store,
        Device $device,
        ?User $cashier,
        $products,
        array $profile,
        int &$ticket,
    ): void {
        $today = Carbon::today();

        for ($daysAgo = self::HISTORY_DAYS; $daysAgo >= 1; $daysAgo--) {
            $day = $today->copy()->subDays($daysAgo);

            // A weekend shop is a busier shop, and a flat line reads as fake
            // data the moment it reaches a chart.
            $weekendLift = $day->isWeekend() ? 1.45 : 1.0;
            $orderCount = (int) round($this->between($profile['perDay'][0], $profile['perDay'][1]) * $weekendLift);

            for ($i = 0; $i < $orderCount; $i++) {
                $placedAt = $day->copy()
                    ->setTime($this->peakHour(), $this->between(0, 59), $this->between(0, 59));

                $this->makeOrder(
                    organization: $organization,
                    store: $store,
                    device: $device,
                    cashier: $cashier,
                    products: $products,
                    profile: $profile,
                    ticket: $ticket,
                    placedAt: $placedAt,
                    channelKey: $this->pickChannel($profile['channels']),
                    orderStatus: 'served',
                    paymentStatus: 'paid',
                    deliveryStage: 'delivered',
                );
            }
        }
    }

    /**
     * Today: part settled, part still moving. This is the half the top of the
     * dashboard reads, and every card up there needs a row that exercises it.
     *
     * @param  \Illuminate\Support\Collection<int, Product>  $products
     * @param  array{channels: array<string, int>, perDay: array{0: int, 1: int}, items: array{0: int, 1: int}}  $profile
     */
    private function seedTodaysQueue(
        Organization $organization,
        Store $store,
        Device $device,
        ?User $cashier,
        $products,
        array $profile,
        int &$ticket,
    ): void {
        $today = Carbon::today();
        $now = Carbon::now();

        // Settled trade so far today, up to the current hour — so "Today's
        // sales" carries a figure and its delta against yesterday means
        // something. Stops at the current hour: a shop cannot have rung up
        // 7pm at 10am.
        $settledToday = max(2, (int) round($profile['perDay'][0] * ($now->hour / 24) * 1.5));
        for ($i = 0; $i < $settledToday; $i++) {
            $minutesAgo = $this->between(60, max(90, (int) $now->diffInMinutes($today)));
            $this->makeOrder(
                organization: $organization,
                store: $store,
                device: $device,
                cashier: $cashier,
                products: $products,
                profile: $profile,
                ticket: $ticket,
                placedAt: $now->copy()->subMinutes($minutesAgo),
                channelKey: $this->pickChannel($profile['channels']),
                orderStatus: 'served',
                paymentStatus: 'paid',
                deliveryStage: 'delivered',
            );
        }

        // Still on the kitchen hand, and one already plated. These are the rows
        // that give "Orders needing action" its Mark ready / Mark served
        // buttons.
        foreach ([['preparing', 12], ['preparing', 28], ['ready', 41]] as [$status, $minutesAgo]) {
            $this->makeOrder(
                organization: $organization,
                store: $store,
                device: $device,
                cashier: $cashier,
                products: $products,
                profile: $profile,
                ticket: $ticket,
                placedAt: $now->copy()->subMinutes($minutesAgo),
                channelKey: 'takeaway',
                orderStatus: $status,
                paymentStatus: 'paid',
                deliveryStage: null,
            );
        }

        // Placed online and not yet paid for — the row with "Settle payment"
        // on it, and the one Unpaid online counts.
        $this->makeOrder(
            organization: $organization,
            store: $store,
            device: $device,
            cashier: $cashier,
            products: $products,
            profile: $profile,
            ticket: $ticket,
            placedAt: $now->copy()->subMinutes(18),
            channelKey: 'online_pickup',
            orderStatus: 'preparing',
            paymentStatus: 'unpaid',
            deliveryStage: null,
        );

        // Deliveries only make sense for a shop that does them. A nail salon
        // does not, and a Deliveries card full of manicures is noise.
        if (($profile['channels']['online_delivery'] ?? 0) === 0) {
            return;
        }

        // Nobody carrying it yet: this is what "Waiting on a rider" counts and
        // what the rider picker on the card is for.
        $this->makeOrder(
            organization: $organization,
            store: $store,
            device: $device,
            cashier: $cashier,
            products: $products,
            profile: $profile,
            ticket: $ticket,
            placedAt: $now->copy()->subMinutes(9),
            channelKey: 'online_delivery',
            orderStatus: 'preparing',
            paymentStatus: 'paid',
            deliveryStage: 'pending',
        );

        // And one already out, so the card has a row with a name, a number and
        // a next stage to advance.
        $this->makeOrder(
            organization: $organization,
            store: $store,
            device: $device,
            cashier: $cashier,
            products: $products,
            profile: $profile,
            ticket: $ticket,
            placedAt: $now->copy()->subMinutes(34),
            channelKey: 'online_delivery',
            orderStatus: 'ready',
            paymentStatus: 'paid',
            deliveryStage: 'picked_up',
            riderName: 'Dennis Bautista',
            riderPhone: '0917 555 0199',
        );
    }

    /**
     * One order, its lines, and its payment if it has been settled.
     *
     * @param  \Illuminate\Support\Collection<int, Product>  $products
     * @param  array{channels: array<string, int>, perDay: array{0: int, 1: int}, items: array{0: int, 1: int}}  $profile
     */
    private function makeOrder(
        Organization $organization,
        Store $store,
        Device $device,
        ?User $cashier,
        $products,
        array $profile,
        int &$ticket,
        Carbon $placedAt,
        string $channelKey,
        string $orderStatus,
        string $paymentStatus,
        ?string $deliveryStage,
        ?string $riderName = null,
        ?string $riderPhone = null,
    ): void {
        $isOnline = str_starts_with($channelKey, 'online_');
        $isDelivery = $channelKey === 'online_delivery';

        $lines = $this->pickLines($products, $profile['items']);
        $subtotal = array_sum(array_column($lines, 'line_total_cents'));

        // The rate lives on the product as a percentage; an order's tax is the
        // weighted sum of its lines, not a flat rate on the subtotal.
        $tax = 0;
        foreach ($lines as $line) {
            $tax += (int) round($line['line_total_cents'] * ($line['tax_rate'] / self::TAX_RATE_SCALE));
        }

        $deliveryFee = $isDelivery ? 4900 : 0;
        $total = $subtotal + $tax + $deliveryFee;

        // Half the counter's orders go under a name; a storefront order always
        // has one, because someone had to type it to check out. Roughly two in
        // five of those are somebody who never came back.
        $named = $isOnline || $this->between(0, 1) === 1;
        $customer = match (true) {
            ! $named => null,
            $this->between(1, 100) <= 60 => self::REGULARS[$this->between(0, count(self::REGULARS) - 1)],
            default => $this->nextOneOffCustomer(),
        };

        $address = $isDelivery ? self::ADDRESSES[$this->between(0, count(self::ADDRESSES) - 1)] : null;
        $settled = $paymentStatus === 'paid';
        $paymentMethod = $isOnline ? 'ewallet' : ($this->between(0, 2) === 0 ? 'ewallet' : 'cash');

        $order = Order::query()->create([
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            'device_id' => $device->id,
            'user_id' => $cashier?->id,
            'ticket_number' => self::TICKET_PREFIX.str_pad((string) $ticket++, 5, '0', STR_PAD_LEFT),
            'order_status' => $orderStatus,
            'order_type' => $channelKey === 'dine_in' ? 'dine_in' : 'takeaway',
            'payment_status' => $paymentStatus,
            'subtotal_cents' => $subtotal,
            'tax_cents' => $tax,
            'total_cents' => $total,
            'business_date' => $placedAt->toDateString(),
            'completed_at' => $orderStatus === 'served' ? $placedAt->copy()->addMinutes(8) : null,
            'channel' => $isOnline ? 'online' : 'in_person',
            'business_mode' => $store->business_mode,
            'table_number' => $channelKey === 'dine_in' ? (string) $this->between(1, 12) : null,
            'payment_method' => $paymentMethod,
            'fulfillment_method' => $isOnline ? ($isDelivery ? 'delivery' : 'pickup') : null,
            'delivery_address' => $address['line'] ?? null,
            'delivery_lat' => $address['lat'] ?? null,
            'delivery_lng' => $address['lng'] ?? null,
            'delivery_distance_km' => $isDelivery ? round($this->between(8, 42) / 10, 1) : null,
            // Not nullable — a register sale has a fee of zero, not no fee.
            'delivery_fee_cents' => $deliveryFee,
            'delivery_stage' => $deliveryStage,
            'rider_name' => $riderName,
            'rider_phone' => $riderPhone,
            'rider_accepted_at' => $riderName ? $placedAt->copy()->addMinutes(6) : null,
            'payment_confirmed_at' => $settled ? $placedAt->copy()->addMinutes(2) : null,
            'payment_confirmed_by_user_id' => $settled ? $cashier?->id : null,
            'guest_contact' => $customer ? ['name' => $customer['name'], 'phone' => $customer['phone']] : null,
        ]);

        // created_at is what the dashboard buckets by, and Eloquent would
        // otherwise stamp every one of these with "now".
        $order->forceFill([
            'created_at' => $placedAt,
            'updated_at' => $placedAt,
        ])->saveQuietly();

        foreach ($lines as $line) {
            OrderItem::query()->create([
                'organization_id' => $organization->id,
                'store_id' => $store->id,
                'order_id' => $order->id,
                'product_id' => $line['product_id'],
                'product_name' => $line['product_name'],
                'quantity' => $line['quantity'],
                'unit_price_cents' => $line['unit_price_cents'],
                'line_total_cents' => $line['line_total_cents'],
            ]);
        }

        if (! $settled) {
            return;
        }

        // Cash is tendered in notes and gets change back; a wallet pays exactly.
        $tendered = $paymentMethod === 'cash'
            ? (int) (ceil($total / 10000) * 10000)
            : $total;

        Payment::query()->create([
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            'order_id' => $order->id,
            'payment_method' => $paymentMethod,
            'amount_cents' => $total,
            'tendered_cents' => $tendered,
            'change_cents' => $tendered - $total,
        ]);
    }

    /**
     * A basket. Weighted toward the front of the catalog so the same handful
     * of products keep coming back — otherwise "Top Products" is five items
     * that sold once each, which tells a shop nothing.
     *
     * @param  \Illuminate\Support\Collection<int, Product>  $products
     * @param  array{0: int, 1: int}  $itemRange
     * @return array<int, array<string, mixed>>
     */
    private function pickLines($products, array $itemRange): array
    {
        $count = min($this->between($itemRange[0], $itemRange[1]), $products->count());
        $bestSellerPool = max(1, (int) ceil($products->count() * 0.25));

        $lines = [];
        $used = [];

        while (count($lines) < $count) {
            $index = $this->between(0, 3) === 0
                ? $this->between(0, $products->count() - 1)
                : $this->between(0, $bestSellerPool - 1);

            if (isset($used[$index])) {
                continue;
            }
            $used[$index] = true;

            /** @var Product $product */
            $product = $products[$index];
            $quantity = $this->between(1, 3);
            $unitPrice = (int) $product->price_cents;

            $lines[] = [
                'product_id' => $product->id,
                'product_name' => $product->name,
                'quantity' => $quantity,
                'unit_price_cents' => $unitPrice,
                'line_total_cents' => $unitPrice * $quantity,
                'tax_rate' => (float) ($product->tax_rate ?? 0),
            ];
        }

        return $lines;
    }

    /**
     * Push a few products under water so Stock alerts has both severities to
     * show — red at zero, amber below the reorder level.
     *
     * @param  \Illuminate\Support\Collection<int, Product>  $products
     */
    private function seedStockAlerts(Organization $organization, Store $store, $products): void
    {
        $tracked = $products->where('track_inventory', true)->values();
        if ($tracked->isEmpty()) {
            return;
        }

        // Taken from the back of the list, so the shop's best sellers — the
        // front of it, per pickLines() — are not the ones showing as empty.
        $candidates = $tracked->reverse()->take(6)->values();

        foreach ($candidates as $position => $product) {
            // First two empty, the rest limping along under the reorder level.
            $reorderLevel = 10;
            $onHand = $position < 2 ? 0 : $this->between(1, $reorderLevel - 1);

            InventoryLevel::query()->updateOrCreate(
                [
                    'organization_id' => $organization->id,
                    'store_id' => $store->id,
                    'product_id' => $product->id,
                ],
                [
                    'qty_on_hand' => $onHand,
                    'reorder_level' => $reorderLevel,
                ],
            );
        }
    }

    /**
     * The next stranger, with a number that will not collide with a regular's.
     *
     * @return array{name: string, phone: string}
     */
    private function nextOneOffCustomer(): array
    {
        $index = $this->oneOffCursor % count(self::ONE_OFF_NAMES);
        $this->oneOffCursor++;

        return [
            'name' => self::ONE_OFF_NAMES[$index],
            'phone' => '09'.str_pad((string) (17 + ($index % 5)), 2, '0', STR_PAD_LEFT)
                .' 555 '.str_pad((string) (200 + $index), 4, '0', STR_PAD_LEFT),
        ];
    }

    /**
     * Which channel this order came through, drawn from the mode's weights.
     *
     * @param  array<string, int>  $weights
     */
    private function pickChannel(array $weights): string
    {
        $total = array_sum($weights);
        $roll = $this->between(1, max(1, $total));

        foreach ($weights as $channel => $weight) {
            $roll -= $weight;
            if ($roll <= 0) {
                return $channel;
            }
        }

        return 'takeaway';
    }

    /**
     * An hour of the day, biased to the two rushes. A shop that sells evenly
     * from 7am to 9pm produces a flat chart nobody would believe.
     */
    private function peakHour(): int
    {
        $roll = $this->between(1, 100);

        return match (true) {
            $roll <= 30 => $this->between(11, 13),  // lunch
            $roll <= 55 => $this->between(15, 17),  // merienda
            $roll <= 75 => $this->between(7, 10),   // morning
            default => $this->between(18, 20),      // evening
        };
    }

    /**
     * Inclusive integer in range, from a small deterministic generator.
     *
     * Not `random_int`: the point is that two people who run this seeder see
     * the same shop, so a screenshot of the dashboard is reproducible and a
     * layout bug at a particular row count does not vanish on re-seed.
     */
    private function between(int $min, int $max): int
    {
        if ($max <= $min) {
            return $min;
        }

        // Lehmer / MINSTD. Cheap, and the sequence is all this needs to be.
        $this->randomState = (int) (($this->randomState * 48271) % 2147483647);

        return $min + ($this->randomState % ($max - $min + 1));
    }
}
