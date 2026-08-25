<?php

namespace App\Services;

use App\Models\Store;

/**
 * Delivery pricing, merged in from Baguio Delivery.
 *
 * The same numbers are quoted to the customer by
 * apps/web/src/storefront/delivery.ts, but that quote is never trusted: the
 * fee actually charged is always recomputed here from the store's own pin.
 * Keep the two in step.
 */
class DeliveryQuoter
{
    public const BASE_FEE_CENTS = 4900;

    public const BASE_KM = 2;

    public const PER_KM_CENTS = 1500;

    public const MAX_KM = 15;

    /** Port of DistanceService::haversineKm from the Baguio Delivery backend. */
    public function haversineKm(float $lat1, float $lng1, float $lat2, float $lng2): float
    {
        $earthRadiusKm = 6371;

        $latDelta = deg2rad($lat2 - $lat1);
        $lngDelta = deg2rad($lng2 - $lng1);

        $a = sin($latDelta / 2) ** 2
            + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($lngDelta / 2) ** 2;

        return $earthRadiusKm * 2 * atan2(sqrt($a), sqrt(1 - $a));
    }

    /** Flag-down covers the first 2km; every started km after that adds ₱15. */
    public function feeForKm(float $distanceKm): int
    {
        $extraKm = max(0, (int) ceil($distanceKm - self::BASE_KM));

        return self::BASE_FEE_CENTS + $extraKm * self::PER_KM_CENTS;
    }

    /**
     * Resolve what this delivery costs and how far it is.
     *
     * With a pin missing on either side the flat base fee is charged rather
     * than refusing the order — that matches what the checkout quoted.
     *
     * @return array{feeCents: int, distanceKm: float|null}
     *
     * @throws OutsideDeliveryAreaException
     */
    public function quote(Store $store, ?float $dropLat, ?float $dropLng): array
    {
        $hasDropPin = $dropLat !== null && $dropLng !== null;

        if (! $store->hasPin() || ! $hasDropPin) {
            return ['feeCents' => self::BASE_FEE_CENTS, 'distanceKm' => null];
        }

        $distanceKm = $this->haversineKm($store->lat, $store->lng, $dropLat, $dropLng);

        if ($distanceKm > self::MAX_KM) {
            throw new OutsideDeliveryAreaException;
        }

        return [
            'feeCents' => $this->feeForKm($distanceKm),
            'distanceKm' => round($distanceKm, 2),
        ];
    }
}
