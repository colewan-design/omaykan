<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\PromoCode;
use App\Services\StoreContext;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * A shop's promo and voucher codes.
 *
 * Managing them — listing, creating, changing, retiring — is for roles with
 * the Products page, the same authority that sets prices. Checking one typed
 * at the counter is for anybody at the register: that is ringing up, not
 * changing the catalog.
 *
 * Codes belong to the organization, so a chain's code works at every branch.
 *
 * See documentation/merchant-features.md §8.
 */
class PromoCodeController extends Controller
{
    use ActsForAStore;

    public function index(Request $request): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->authorizeManage($context);

        $codes = PromoCode::query()
            ->where('organization_id', $context->organizationId())
            ->orderByDesc('created_at')
            ->get();

        return response()->json(['promoCodes' => $codes->map(fn (PromoCode $code) => $this->present($code))->values()]);
    }

    public function store(Request $request): JsonResponse
    {
        $context = $this->writableStoreContext($request);
        $this->authorizeManage($context);

        $validated = $request->validate($this->rules(creating: true));
        $code = PromoCode::normalise($validated['code']);

        // Unique among the shop's codes, retired ones included: a code that
        // meant "20% off" last month must not quietly mean "₱50 off" now, on
        // flyers still in people's pockets.
        if (PromoCode::withTrashed()->where('organization_id', $context->organizationId())->where('code', $code)->exists()) {
            throw ValidationException::withMessages(['code' => "{$code} has been used before. Pick another code."]);
        }

        $promo = PromoCode::query()->create($this->attributes($validated) + [
            'organization_id' => $context->organizationId(),
            'code' => $code,
            'created_by' => $context->user->id,
        ]);

        return response()->json(['promoCode' => $this->present($promo)], 201);
    }

    public function update(Request $request, PromoCode $promoCode): JsonResponse
    {
        $context = $this->writableStoreContext($request);
        $this->authorizeManage($context);
        $this->authorizeOwnership($context, $promoCode);

        // The code itself is not editable, for the reason in store().
        $validated = $request->validate($this->rules(creating: false));
        $promoCode->fill($this->attributes($validated, $promoCode))->save();

        return response()->json(['promoCode' => $this->present($promoCode->fresh())]);
    }

    /** Retired, not erased: past orders still say which code they used. */
    public function destroy(Request $request, PromoCode $promoCode): JsonResponse
    {
        $context = $this->writableStoreContext($request);
        $this->authorizeManage($context);
        $this->authorizeOwnership($context, $promoCode);

        $promoCode->delete();

        return response()->json(['deleted' => true]);
    }

    /**
     * Whether a code typed at the counter applies to this sale, and for how
     * much. The till only accepts a code while it can reach this: a check made
     * offline could not enforce a redemption cap, since two offline tills
     * would both honour the last use.
     *
     * The use is counted when the sale syncs, as an `order_discounts` row.
     */
    public function check(Request $request): JsonResponse
    {
        $context = $this->writableStoreContext($request);

        $validated = $request->validate([
            'code' => ['required', 'string', 'max:40'],
            'subtotalCents' => ['required', 'integer', 'min:0'],
        ]);

        $promo = PromoCode::findForOrganization($context->organizationId(), $validated['code']);

        if ($promo === null) {
            throw ValidationException::withMessages(['code' => "That code isn't valid for this shop."]);
        }

        $verdict = $promo->evaluate([
            'subtotalCents' => $validated['subtotalCents'],
            'channel' => PromoCode::CHANNEL_COUNTER,
        ]);

        if (! $verdict['ok']) {
            throw ValidationException::withMessages(['code' => $verdict['message']]);
        }

        return response()->json([
            'promoCodeId' => $promo->id,
            'code' => $promo->code,
            'description' => $promo->describe(),
            'discountCents' => $verdict['discountCents'],
            'percent' => $promo->kind === PromoCode::KIND_PERCENT ? $promo->value / 100 : null,
        ]);
    }

    private function authorizeManage(StoreContext $context): void
    {
        abort_unless($context->can('products'), 403, 'Your role cannot manage promo codes.');
    }

    private function authorizeOwnership(StoreContext $context, PromoCode $promoCode): void
    {
        // Same message as a missing code: one shop must not learn another's.
        abort_unless($promoCode->organization_id === $context->organizationId(), 404, 'No such promo code.');
    }

    /** @return array<string, mixed> */
    private function rules(bool $creating): array
    {
        $sometimes = $creating ? 'required' : 'sometimes';

        return [
            'code' => $creating
                ? ['required', 'string', 'min:3', 'max:40', 'regex:/^[A-Za-z0-9_-]+$/']
                : ['prohibited'],
            'kind' => [$sometimes, Rule::in([PromoCode::KIND_PERCENT, PromoCode::KIND_AMOUNT])],
            // Whole or half percents: 1–100.
            'percent' => ['nullable', 'numeric', 'min:0.5', 'max:100', 'required_if:kind,percent'],
            'amountCents' => ['nullable', 'integer', 'min:1', 'max:10000000', 'required_if:kind,amount'],
            'minSubtotalCents' => ['sometimes', 'integer', 'min:0', 'max:100000000'],
            'maxDiscountCents' => ['sometimes', 'nullable', 'integer', 'min:1', 'max:10000000'],
            'channel' => ['sometimes', Rule::in([PromoCode::CHANNEL_ONLINE, PromoCode::CHANNEL_COUNTER, PromoCode::CHANNEL_BOTH])],
            'startsAt' => ['sometimes', 'nullable', 'date'],
            'endsAt' => ['sometimes', 'nullable', 'date', 'after:startsAt'],
            'maxRedemptions' => ['sometimes', 'nullable', 'integer', 'min:1', 'max:1000000'],
            'perCustomerLimit' => ['sometimes', 'nullable', 'integer', 'min:1', 'max:1000'],
            'isActive' => ['sometimes', 'boolean'],
        ];
    }

    /**
     * API fields onto columns. A percentage is stored as basis points (10% =
     * 1000) so the column is always an integer, whichever kind it is.
     *
     * @param  array<string, mixed>  $validated
     * @return array<string, mixed>
     */
    private function attributes(array $validated, ?PromoCode $existing = null): array
    {
        $attributes = [];
        $kind = $validated['kind'] ?? $existing?->kind;

        if (array_key_exists('kind', $validated) || array_key_exists('percent', $validated) || array_key_exists('amountCents', $validated)) {
            $attributes['kind'] = $kind;
            $attributes['value'] = $kind === PromoCode::KIND_PERCENT
                ? (int) round(((float) ($validated['percent'] ?? ($existing?->value ?? 0) / 100)) * 100)
                : (int) ($validated['amountCents'] ?? $existing?->value ?? 0);
        }

        foreach ([
            'minSubtotalCents' => 'min_subtotal_cents',
            'maxDiscountCents' => 'max_discount_cents',
            'channel' => 'channel',
            'startsAt' => 'starts_at',
            'endsAt' => 'ends_at',
            'maxRedemptions' => 'max_redemptions',
            'perCustomerLimit' => 'per_customer_limit',
            'isActive' => 'is_active',
        ] as $input => $column) {
            if (array_key_exists($input, $validated)) {
                $attributes[$column] = $validated[$input];
            }
        }

        return $attributes;
    }

    /** @return array<string, mixed> */
    private function present(PromoCode $code): array
    {
        return [
            'id' => $code->id,
            'code' => $code->code,
            'kind' => $code->kind,
            'percent' => $code->kind === PromoCode::KIND_PERCENT ? $code->value / 100 : null,
            'amountCents' => $code->kind === PromoCode::KIND_AMOUNT ? $code->value : null,
            'minSubtotalCents' => $code->min_subtotal_cents,
            'maxDiscountCents' => $code->max_discount_cents,
            'channel' => $code->channel,
            'startsAt' => $code->starts_at?->toIso8601String(),
            'endsAt' => $code->ends_at?->toIso8601String(),
            'maxRedemptions' => $code->max_redemptions,
            'perCustomerLimit' => $code->per_customer_limit,
            'isActive' => $code->is_active,
            'redemptions' => $code->redemptionCount(),
            'description' => $code->describe(),
        ];
    }
}
