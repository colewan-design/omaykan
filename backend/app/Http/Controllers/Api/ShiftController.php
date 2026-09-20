<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\CashMovement;
use App\Models\Order;
use App\Models\Payment;
use App\Models\Shift;
use App\Models\StoreMembership;
use App\Models\User;
use App\Services\StoreContext;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class ShiftController extends Controller
{
    use ActsForAStore;

    public function current(Request $request)
    {
        $context = $this->storeContext($request);
        $shift = $this->currentShiftForStore($context);

        return response()->json([
            'shift' => $shift ? $this->serializeShift($shift) : null,
        ]);
    }

    public function history(Request $request)
    {
        $context = $this->storeContext($request);

        $shifts = Shift::query()
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId())
            ->whereNotNull('closed_at')
            ->latest('closed_at')
            ->limit(50)
            ->get();

        return response()->json([
            'shifts' => $shifts->map(fn (Shift $shift) => $this->serializeShift($shift))->all(),
        ]);
    }

    public function open(Request $request)
    {
        $context = $this->writableStoreContext($request);
        $validated = $request->validate([
            'openingCashCents' => ['required', 'integer', 'min:0'],
            'userId' => ['nullable', 'uuid'],
        ]);

        if ($this->currentShiftForStore($context)) {
            abort(422, 'An active shift already exists for this store.');
        }

        $userId = $this->validatedUserId($context, $validated['userId'] ?? null);

        $shift = Shift::query()->create([
            'organization_id' => $context->organizationId(),
            'store_id' => $context->storeId(),
            // Null now, always: nothing pairs any more, so there is no terminal
            // to name. Who did it is `opened_by_user_id`, and that is no longer
            // a guess the client supplies.
            'device_id' => null,
            'opened_by_user_id' => $userId,
            'opening_cash_cents' => $validated['openingCashCents'],
            'opened_at' => now(),
        ]);

        return response()->json([
            'shift' => $this->serializeShift($shift->fresh('cashMovements')),
        ]);
    }

    public function addMovement(Request $request)
    {
        $context = $this->storeContext($request);
        $shift = $this->currentShiftForStore($context);
        abort_unless($shift, 422, 'No active shift is open for this store.');

        $validated = $request->validate([
            'movementType' => ['required', 'in:pay_in,pay_out'],
            'amountCents' => ['required', 'integer', 'min:1'],
            'reason' => ['nullable', 'string', 'max:255'],
            'userId' => ['nullable', 'uuid'],
        ]);

        $userId = $this->validatedUserId($context, $validated['userId'] ?? null);

        CashMovement::query()->create([
            'organization_id' => $context->organizationId(),
            'store_id' => $context->storeId(),
            'shift_id' => $shift->id,
            'user_id' => $userId,
            'movement_type' => $validated['movementType'],
            'amount_cents' => $validated['amountCents'],
            'reason' => $validated['reason'] ?? null,
        ]);

        return response()->json([
            'shift' => $this->serializeShift($shift->fresh('cashMovements')),
        ]);
    }

    public function close(Request $request)
    {
        $context = $this->storeContext($request);
        $shift = $this->currentShiftForStore($context);
        abort_unless($shift, 422, 'No active shift is open for this store.');

        $validated = $request->validate([
            'countedCashCents' => ['required', 'integer', 'min:0'],
            'userId' => ['nullable', 'uuid'],
        ]);

        $userId = $this->validatedUserId($context, $validated['userId'] ?? null);

        DB::transaction(function () use ($shift, $validated, $userId): void {
            $freshShift = $shift->fresh('cashMovements');
            $expectedCashCents = $this->expectedCashForShift($freshShift);
            $closingCashCents = $validated['countedCashCents'];

            $freshShift->forceFill([
                'closed_by_user_id' => $userId,
                'closing_cash_cents' => $closingCashCents,
                'expected_cash_cents' => $expectedCashCents,
                'variance_cash_cents' => $closingCashCents - $expectedCashCents,
                'closed_at' => now(),
            ])->save();
        });

        return response()->json([
            'shift' => $this->serializeShift($shift->fresh('cashMovements')),
        ]);
    }

    private function serializeShift(Shift $shift): array
    {
        $shift->loadMissing('cashMovements');

        $cashSalesCents = $this->cashSalesForShift($shift);
        $payInsCents = $shift->cashMovements
            ->where('movement_type', 'pay_in')
            ->sum('amount_cents');
        $payOutsCents = $shift->cashMovements
            ->where('movement_type', 'pay_out')
            ->sum('amount_cents');
        $expectedCashCents = $shift->closed_at
            ? ($shift->expected_cash_cents ?? $this->expectedCashForShift($shift))
            : $shift->opening_cash_cents + $cashSalesCents + $payInsCents - $payOutsCents;

        return [
            'id' => $shift->id,
            'openedByUserId' => $shift->opened_by_user_id,
            'closedByUserId' => $shift->closed_by_user_id,
            'openingCashCents' => $shift->opening_cash_cents,
            'closingCashCents' => $shift->closing_cash_cents,
            'cashSalesCents' => $cashSalesCents,
            'totalSalesCents' => $this->totalSalesForShift($shift),
            'orderCount' => $this->orderCountForShift($shift),
            'payInsCents' => $payInsCents,
            'payOutsCents' => $payOutsCents,
            'expectedCashCents' => $expectedCashCents,
            'varianceCashCents' => $shift->variance_cash_cents,
            'openedAt' => $shift->opened_at?->toIso8601String(),
            'closedAt' => $shift->closed_at?->toIso8601String(),
            'movements' => $shift->cashMovements
                ->sortByDesc('created_at')
                ->values()
                ->map(fn (CashMovement $movement) => [
                    'id' => $movement->id,
                    'userId' => $movement->user_id,
                    'movementType' => $movement->movement_type,
                    'amountCents' => $movement->amount_cents,
                    'reason' => $movement->reason,
                    'createdAt' => $movement->created_at?->toIso8601String(),
                ])
                ->all(),
        ];
    }

    private function expectedCashForShift(Shift $shift): int
    {
        $shift->loadMissing('cashMovements');

        $payInsCents = $shift->cashMovements
            ->where('movement_type', 'pay_in')
            ->sum('amount_cents');
        $payOutsCents = $shift->cashMovements
            ->where('movement_type', 'pay_out')
            ->sum('amount_cents');

        return $shift->opening_cash_cents + $this->cashSalesForShift($shift) + $payInsCents - $payOutsCents;
    }

    private function cashSalesForShift(Shift $shift): int
    {
        return (int) Payment::query()
            ->where('store_id', $shift->store_id)
            ->where('payment_method', 'cash')
            ->whereHas('order', function ($query) use ($shift) {
                $query
                    ->where('completed_at', '>=', $shift->opened_at)
                    ->when(
                        $shift->closed_at,
                        fn ($inner) => $inner->where('completed_at', '<=', $shift->closed_at),
                    );
            })
            ->sum('amount_cents');
    }

    private function ordersForShift(Shift $shift)
    {
        return Order::query()
            ->where('store_id', $shift->store_id)
            ->where('completed_at', '>=', $shift->opened_at)
            ->when(
                $shift->closed_at,
                fn ($query) => $query->where('completed_at', '<=', $shift->closed_at),
            );
    }

    private function totalSalesForShift(Shift $shift): int
    {
        return (int) $this->ordersForShift($shift)->sum('total_cents');
    }

    private function orderCountForShift(Shift $shift): int
    {
        return $this->ordersForShift($shift)->count();
    }

    private function currentShiftForStore(StoreContext $context): ?Shift
    {
        return Shift::query()
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId())
            ->whereNull('closed_at')
            ->latest('opened_at')
            ->first();
    }

    /**
     * Who this shift action is recorded against.
     *
     * A till used to have to be told, because a paired device had no person
     * behind it and the column would otherwise be null on every cash movement
     * in the shop. The caller is a person now, so the default is simply them —
     * and an explicit `userId` still wins, for a manager opening the drawer on
     * behalf of whoever is about to stand at it. That name is still checked
     * against the shop's membership, so it can only ever be someone who works
     * there.
     */
    private function validatedUserId(StoreContext $context, ?string $userId): ?string
    {
        if (! $userId) {
            return $context->user->id;
        }

        abort_unless(
            User::query()->whereKey($userId)->exists(),
            422,
            'Selected user was not found.',
        );

        $hasMembership = StoreMembership::query()
            ->where('store_id', $context->storeId())
            ->where('user_id', $userId)
            ->exists();

        abort_unless($hasMembership, 403, 'Selected user does not belong to this store.');

        return $userId;
    }
}
