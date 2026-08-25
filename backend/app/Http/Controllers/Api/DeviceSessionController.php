<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\Store;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;

class DeviceSessionController extends Controller
{
    public function store(Request $request)
    {
        $validated = $request->validate([
            'organizationSlug' => ['required', 'string'],
            'storeCode' => ['required', 'string'],
            'pairingCode' => ['required', 'string'],
            'deviceName' => ['required', 'string', 'max:120'],
            'platform' => ['required', 'string', 'max:40'],
            'appVersion' => ['nullable', 'string', 'max:40'],
        ]);

        $store = Store::query()
            ->with('organization')
            ->where('code', $validated['storeCode'])
            ->whereHas('organization', fn ($query) => $query->where('slug', $validated['organizationSlug']))
            ->firstOrFail();

        // Codes are typed by hand, so the normalised (uppercased, trimmed) form
        // is what Store::setPairingCode() hashes. The raw form is still
        // accepted as a fallback for any row written before that existed,
        // whose hash was made from whatever case the merchant first entered.
        $submitted = $validated['pairingCode'];
        $matches = $store->pairing_code_hash && (
            Hash::check(Store::normalizePairingCode($submitted), $store->pairing_code_hash)
            || Hash::check($submitted, $store->pairing_code_hash)
        );

        if (! $matches) {
            abort(422, 'Invalid pairing code.');
        }

        $device = Device::query()->firstOrCreate(
            [
                'organization_id' => $store->organization_id,
                'store_id' => $store->id,
                'device_name' => $validated['deviceName'],
            ],
            [
                'platform' => $validated['platform'],
                'app_version' => $validated['appVersion'] ?? null,
                'status' => 'active',
                'activated_at' => now(),
            ],
        );

        $device->forceFill([
            'platform' => $validated['platform'],
            'app_version' => $validated['appVersion'] ?? $device->app_version,
            'status' => 'active',
            'last_seen_at' => now(),
            'activated_at' => $device->activated_at ?? now(),
        ])->save();

        $token = $device->createToken('device-session', ['sync'])->plainTextToken;

        return response()->json([
            'token' => $token,
            'device' => [
                'id' => $device->id,
                'name' => $device->device_name,
                'platform' => $device->platform,
                'appVersion' => $device->app_version,
            ],
            'store' => [
                'id' => $store->id,
                'name' => $store->name,
                'code' => $store->code,
            ],
            'organization' => [
                'id' => $store->organization->id,
                'name' => $store->organization->name,
                'slug' => $store->organization->slug,
            ],
        ]);
    }
}
