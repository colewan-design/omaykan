<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\LoyaltyEntry;
use App\Models\LoyaltyProgram;
use App\Models\PosCustomer;
use App\Services\Loyalty\Loyalty;
use App\Services\StoreContext;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

/**
 * The points programme: its rules, each customer's balance, spending points
 * at the counter, and a manager's correction.
 *
 * Reading — the rules, a balance, a check before spending — is for anyone at
 * the register. Changing the rules or correcting a balance is money policy,
 * and needs the Customers page.
 *
 * Earning is not here. It happens when a sale syncs (SyncController, through
 * Loyalty::earnFor), because the sale is what earns.
 *
 * See documentation/merchant-features.md §9.
 */
class LoyaltyController extends Controller
{
    use ActsForAStore;

    public function __construct(private readonly Loyalty $loyalty)
    {
    }

    public function show(Request $request): JsonResponse
    {
        $context = $this->storeContext($request);

        return response()->json(['program' => $this->presentProgram(LoyaltyProgram::for($context->organizationId()))]);
    }

    public function update(Request $request): JsonResponse
    {
        $context = $this->writableStoreContext($request);
        $this->authorizeManage($context);

        $validated = $request->validate([
            'enabled' => ['sometimes', 'boolean'],
            'spendCentsPerPoint' => ['sometimes', 'integer', 'min:100', 'max:10000000'],
            'pointValueCents' => ['sometimes', 'integer', 'min:1', 'max:1000000'],
            'minRedeemPoints' => ['sometimes', 'integer', 'min:1', 'max:1000000'],
            'expiryMonths' => ['sometimes', 'nullable', 'integer', 'min:1', 'max:120'],
        ]);

        $program = LoyaltyProgram::for($context->organizationId());

        foreach ([
            'enabled' => 'enabled',
            'spendCentsPerPoint' => 'spend_cents_per_point',
            'pointValueCents' => 'point_value_cents',
            'minRedeemPoints' => 'min_redeem_points',
            'expiryMonths' => 'expiry_months',
        ] as $input => $column) {
            if (array_key_exists($input, $validated)) {
                $program->{$column} = $validated[$input];
            }
        }

        $program->save();

        return response()->json(['program' => $this->presentProgram($program)]);
    }

    /** Every customer's balance, for the Customers page. One query. */
    public function balances(Request $request): JsonResponse
    {
        $context = $this->storeContext($request);

        $balances = LoyaltyEntry::query()
            ->where('organization_id', $context->organizationId())
            ->groupBy('pos_customer_id')
            ->selectRaw('pos_customer_id, sum(points) as balance')
            ->pluck('balance', 'pos_customer_id')
            ->map(fn ($balance) => (int) $balance);

        return response()->json(['balances' => $balances]);
    }

    /** One customer's balance and their recent history. */
    public function customer(Request $request, PosCustomer $posCustomer): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->authorizeOwnership($context, $posCustomer);

        return response()->json([
            'balance' => $this->loyalty->balance($posCustomer),
            'enrolled' => $posCustomer->loyalty_consent_at !== null,
            'entries' => $posCustomer->loyaltyEntries()
                ->latest()
                ->limit(50)
                ->get()
                ->map(fn (LoyaltyEntry $entry) => [
                    'id' => $entry->id,
                    'reason' => $entry->reason,
                    'points' => $entry->points,
                    'note' => $entry->note,
                    'createdAt' => $entry->created_at?->toIso8601String(),
                ]),
        ]);
    }

    /**
     * Whether a customer can spend these points on a sale of this size, and
     * what they are worth. Online only, like promo codes: a balance checked
     * offline could be spent twice at two tills.
     */
    public function check(Request $request): JsonResponse
    {
        $context = $this->writableStoreContext($request);

        $validated = $request->validate([
            'customerId' => ['required', 'uuid'],
            'points' => ['required', 'integer', 'min:1'],
            'subtotalCents' => ['required', 'integer', 'min:0'],
        ]);

        $customer = PosCustomer::query()
            ->where('organization_id', $context->organizationId())
            ->whereKey($validated['customerId'])
            ->first();

        if ($customer === null) {
            throw ValidationException::withMessages([
                'customerId' => 'That customer has not reached Omaykan yet. Try again in a moment.',
            ]);
        }

        $quote = $this->loyalty->quote($customer, $validated['points'], $validated['subtotalCents']);

        if (! $quote['ok']) {
            throw ValidationException::withMessages(['points' => $quote['message']]);
        }

        return response()->json([
            'points' => $quote['points'],
            'discountCents' => $quote['discountCents'],
            'balance' => $this->loyalty->balance($customer),
        ]);
    }

    /** A manager's correction: a new ledger row, never an edit. */
    public function adjust(Request $request, PosCustomer $posCustomer): JsonResponse
    {
        $context = $this->writableStoreContext($request);
        $this->authorizeManage($context);
        $this->authorizeOwnership($context, $posCustomer);

        $validated = $request->validate([
            'points' => ['required', 'integer', 'not_in:0', 'min:-1000000', 'max:1000000'],
            'note' => ['required', 'string', 'max:200'],
        ]);

        if ($this->loyalty->balance($posCustomer) + $validated['points'] < 0) {
            throw ValidationException::withMessages(['points' => 'That would take the balance below zero.']);
        }

        LoyaltyEntry::query()->create([
            'organization_id' => $context->organizationId(),
            'pos_customer_id' => $posCustomer->id,
            'reason' => LoyaltyEntry::ADJUST,
            'points' => $validated['points'],
            'note' => trim($validated['note']),
            'created_by' => $context->user->id,
        ]);

        return response()->json(['balance' => $this->loyalty->balance($posCustomer)]);
    }

    private function authorizeManage(StoreContext $context): void
    {
        abort_unless($context->can('customers'), 403, 'Your role cannot change the points programme.');
    }

    private function authorizeOwnership(StoreContext $context, PosCustomer $customer): void
    {
        abort_unless($customer->organization_id === $context->organizationId(), 404, 'No such customer.');
    }

    /** @return array<string, mixed> */
    private function presentProgram(LoyaltyProgram $program): array
    {
        return [
            'enabled' => (bool) $program->enabled,
            'spendCentsPerPoint' => $program->spend_cents_per_point,
            'pointValueCents' => $program->point_value_cents,
            'minRedeemPoints' => $program->min_redeem_points,
            'expiryMonths' => $program->expiry_months,
        ];
    }
}
