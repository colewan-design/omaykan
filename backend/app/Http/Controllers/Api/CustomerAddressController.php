<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use App\Models\CustomerAddress;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * The signed-in shopper's delivery addresses.
 *
 * Every route resolves the row through `$account->addresses()`, so an id
 * belonging to somebody else is a 404 rather than a leak — there is no
 * `CustomerAddress::findOrFail` anywhere in here on purpose.
 *
 * Each response returns the whole account, not the one address that changed:
 * saving an address can move the default off another one, so a partial reply
 * would leave the portal holding a list it has to patch up itself.
 */
class CustomerAddressController extends Controller
{
    public function store(Request $request): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();

        $data = $this->validated($request);

        $address = DB::transaction(function () use ($account, $data) {
            // The first address saved is the default: otherwise checkout would
            // open with nothing selected while there is exactly one choice.
            $isFirst = ! $account->addresses()->exists();

            $address = $account->addresses()->create([
                'label' => trim($data['label']),
                'line1' => trim($data['line1']),
                'barangay' => isset($data['barangay']) ? trim($data['barangay']) : null,
                'city' => trim($data['city']),
                'notes' => isset($data['notes']) ? trim($data['notes']) : null,
                'lat' => $data['lat'] ?? null,
                'lng' => $data['lng'] ?? null,
                'is_default' => $isFirst || ($data['isDefault'] ?? false),
            ]);

            if ($address->is_default) {
                $this->makeSoleDefault($account, $address);
            }

            return $address;
        });

        return response()->json([
            'account' => $account->fresh()->toStorefrontArray(),
            'addressId' => $address->id,
        ], 201);
    }

    public function update(Request $request, string $address): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();
        $row = $account->addresses()->findOrFail($address);

        $data = $this->validated($request, partial: true);

        DB::transaction(function () use ($account, $row, $data) {
            foreach (['label', 'line1', 'barangay', 'city', 'notes'] as $field) {
                if (array_key_exists($field, $data)) {
                    $row->{$field} = $data[$field] === null ? null : trim($data[$field]);
                }
            }

            foreach (['lat', 'lng'] as $field) {
                if (array_key_exists($field, $data)) {
                    $row->{$field} = $data[$field];
                }
            }

            // Only ever set here, never cleared: unsetting the default would
            // leave the list without one, so the way to move it is to make a
            // different address the default.
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

    public function destroy(Request $request, string $address): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();
        $row = $account->addresses()->findOrFail($address);

        DB::transaction(function () use ($account, $row) {
            $wasDefault = $row->is_default;
            $row->delete();

            // Never leave a non-empty list without a default.
            if ($wasDefault) {
                $next = $account->addresses()->oldest()->first();
                $next?->update(['is_default' => true]);
            }
        });

        return response()->json(['account' => $account->fresh()->toStorefrontArray()]);
    }

    /** @return array<string, mixed> */
    private function validated(Request $request, bool $partial = false): array
    {
        $required = $partial ? 'sometimes' : 'required';

        return $request->validate([
            'label' => [$required, 'string', 'max:60'],
            'line1' => [$required, 'string', 'max:200'],
            'barangay' => ['sometimes', 'nullable', 'string', 'max:120'],
            'city' => [$required, 'string', 'max:120'],
            'notes' => ['sometimes', 'nullable', 'string', 'max:500'],
            // Same pairing rule the order endpoint uses: half a coordinate is
            // worse than none, because it quietly changes the delivery quote.
            'lat' => ['sometimes', 'nullable', 'numeric', 'between:-90,90', 'required_with:lng'],
            'lng' => ['sometimes', 'nullable', 'numeric', 'between:-180,180', 'required_with:lat'],
            'isDefault' => ['sometimes', 'boolean'],
        ]);
    }

    /** Exactly one row carries the flag; this clears it from every other. */
    private function makeSoleDefault(CustomerAccount $account, CustomerAddress $address): void
    {
        $account->addresses()
            ->whereKeyNot($address->getKey())
            ->where('is_default', true)
            ->update(['is_default' => false]);
    }
}
