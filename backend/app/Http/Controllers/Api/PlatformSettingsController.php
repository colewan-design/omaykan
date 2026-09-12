<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\PlatformSetting;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * What the marketplace calls itself, and the two policies it sets centrally.
 *
 * The only writable screen in the operator portal that is not an action on
 * somebody else's record — everything here belongs to the platform, so there
 * is no ownership line to cross by saving it.
 *
 * ## What is deliberately not here
 *
 * **Payment configuration.** Which tenders the platform accepts is decided by
 * the payment integration's own credentials, in the server's `.env`. Offering
 * a toggle here that cannot actually turn GCash off would be a control that
 * lies; the screen shows the live configuration read-only instead.
 *
 * **Operator accounts.** Created from the console (`php artisan
 * platform-admin:create`) on purpose — see routes/api.php. A portal that can
 * mint its own operators is a portal where one stolen session is permanent.
 *
 * Behind `auth:platform` with the rest of the operator tools; see routes/api.php.
 */
class PlatformSettingsController extends Controller
{
    public function show(Request $request): JsonResponse
    {
        return response()->json(['settings' => $this->asArray(PlatformSetting::current())]);
    }

    /**
     * Save the identity block, the delivery policy, or the notification
     * preferences — whichever of the three the screen sent.
     *
     * Each block is optional and applied only when present, so the three tabs
     * can save independently without the one being edited blanking the two
     * that are not on screen.
     */
    public function update(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => ['sometimes', 'string', 'min:2', 'max:120'],
            'tagline' => ['sometimes', 'nullable', 'string', 'max:160'],
            'description' => ['sometimes', 'nullable', 'string', 'max:2000'],
            'website' => ['sometimes', 'nullable', 'url', 'max:200'],
            'address' => ['sometimes', 'nullable', 'string', 'max:250'],
            'contactEmail' => ['sometimes', 'nullable', 'email', 'max:160'],
            'contactPhone' => ['sometimes', 'nullable', 'string', 'max:40'],

            // A fee is money, so it is centavos and cannot be negative. The
            // ceilings are sanity rails, not policy: they exist so a slipped
            // decimal point is a validation message rather than a ₱49,000
            // delivery charge quoted to a real customer.
            'delivery' => ['sometimes', 'array'],
            'delivery.baseFeeCents' => ['required_with:delivery', 'integer', 'min:0', 'max:1000000'],
            'delivery.freeDeliveryOverCents' => ['required_with:delivery', 'integer', 'min:0', 'max:100000000'],
            'delivery.maxDistanceKm' => ['required_with:delivery', 'numeric', 'min:0', 'max:100'],

            'notifications' => ['sometimes', 'array'],
            'notifications.newOrder' => ['required_with:notifications', 'boolean'],
            'notifications.newSeller' => ['required_with:notifications', 'boolean'],
            'notifications.lowStock' => ['required_with:notifications', 'boolean'],
            'notifications.weeklySummary' => ['required_with:notifications', 'boolean'],
        ]);

        $settings = PlatformSetting::current();

        foreach ([
            'name' => 'name',
            'tagline' => 'tagline',
            'description' => 'description',
            'website' => 'website',
            'address' => 'address',
            'contactEmail' => 'contact_email',
            'contactPhone' => 'contact_phone',
        ] as $input => $column) {
            if (array_key_exists($input, $validated)) {
                $settings->{$column} = $validated[$input];
            }
        }

        if (array_key_exists('delivery', $validated)) {
            // Merged over what is stored rather than replacing it, so a future
            // key this release does not know about survives a save from it.
            $settings->delivery = array_merge($settings->deliverySettings(), $validated['delivery']);
        }

        if (array_key_exists('notifications', $validated)) {
            $settings->notifications = array_merge($settings->notificationSettings(), $validated['notifications']);
        }

        $settings->save();

        return response()->json(['settings' => $this->asArray($settings)]);
    }

    private function asArray(PlatformSetting $settings): array
    {
        return [
            'name' => $settings->name,
            'tagline' => $settings->tagline,
            'description' => $settings->description,
            'website' => $settings->website,
            'address' => $settings->address,
            'contactEmail' => $settings->contact_email,
            'contactPhone' => $settings->contact_phone,
            'delivery' => $settings->deliverySettings(),
            'notifications' => $settings->notificationSettings(),
            'updatedAt' => $settings->updated_at?->toIso8601String(),
        ];
    }
}
