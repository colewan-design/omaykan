<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use App\Models\CustomerPaymentMethod;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * How the signed-in shopper prefers to pay.
 *
 * Not payment instruments: `cash` and `ewallet` are the only two values the
 * order endpoint accepts and both settle on arrival, so nothing stored here
 * could move money on its own. That is why an e-wallet number is kept as plain
 * `detail` and why there is no card kind — there is no processor to tokenise
 * against yet.
 *
 * Same ownership rule and same whole-account response as the addresses
 * controller, for the same reasons.
 */
class CustomerPaymentMethodController extends Controller
{
    public function store(Request $request): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();

        $data = $request->validate([
            'kind' => ['required', Rule::in(CustomerPaymentMethod::KINDS)],
            'detail' => ['nullable', 'string', 'max:60', 'required_if:kind,ewallet'],
            'isDefault' => ['sometimes', 'boolean'],
        ]);

        // Cash carries no detail, so a second cash row would be an exact
        // duplicate of the first and there is nothing to tell them apart by.
        if ($data['kind'] === 'cash' && $account->paymentMethods()->where('kind', 'cash')->exists()) {
            throw ValidationException::withMessages([
                'kind' => 'Cash on delivery is already saved.',
            ]);
        }

        $method = DB::transaction(function () use ($account, $data) {
            $isFirst = ! $account->paymentMethods()->exists();

            $method = $account->paymentMethods()->create([
                'kind' => $data['kind'],
                'detail' => isset($data['detail']) ? trim($data['detail']) : null,
                'is_default' => $isFirst || ($data['isDefault'] ?? false),
            ]);

            if ($method->is_default) {
                $this->makeSoleDefault($account, $method);
            }

            return $method;
        });

        return response()->json([
            'account' => $account->fresh()->toStorefrontArray(),
            'paymentMethodId' => $method->id,
        ], 201);
    }

    public function update(Request $request, string $paymentMethod): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();
        $row = $account->paymentMethods()->findOrFail($paymentMethod);

        $data = $request->validate([
            'detail' => ['sometimes', 'nullable', 'string', 'max:60'],
            'isDefault' => ['sometimes', 'boolean'],
        ]);

        DB::transaction(function () use ($account, $row, $data) {
            if (array_key_exists('detail', $data)) {
                $row->detail = $data['detail'] === null ? null : trim($data['detail']);
            }

            if (($data['isDefault'] ?? false) === true) {
                $row->is_default = true;
            }

            $row->save();

            if ($row->is_default) {
                $this->makeSoleDefault($account, $row);
            }
        });

        return response()->json(['account' => $account->fresh()->toStorefrontArray()]);
    }

    public function destroy(Request $request, string $paymentMethod): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();
        $row = $account->paymentMethods()->findOrFail($paymentMethod);

        DB::transaction(function () use ($account, $row) {
            $wasDefault = $row->is_default;
            $row->delete();

            if ($wasDefault) {
                $next = $account->paymentMethods()->oldest()->first();
                $next?->update(['is_default' => true]);
            }
        });

        return response()->json(['account' => $account->fresh()->toStorefrontArray()]);
    }

    private function makeSoleDefault(CustomerAccount $account, CustomerPaymentMethod $method): void
    {
        $account->paymentMethods()
            ->whereKeyNot($method->getKey())
            ->where('is_default', true)
            ->update(['is_default' => false]);
    }
}
