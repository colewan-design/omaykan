import { describe, expect, it } from 'vitest'
import {
  DELIVERY_BASE_FEE_CENTS,
  DELIVERY_MAX_KM,
  feeForKm,
  haversineKm,
  quoteDelivery,
} from '@pos/web/commerce/delivery'

/*
 * These mirror backend/app/Services/DeliveryQuoter.php. The client quotes what
 * the shopper is shown; the server recomputes what is actually charged. If the
 * two drift, checkout quotes one number and bills another — so the constants
 * and the banding are asserted here rather than assumed.
 */

describe('feeForKm', () => {
  it('charges the flat base fee inside the base distance', () => {
    expect(feeForKm(0)).toBe(DELIVERY_BASE_FEE_CENTS)
    expect(feeForKm(1)).toBe(DELIVERY_BASE_FEE_CENTS)
    expect(feeForKm(2)).toBe(DELIVERY_BASE_FEE_CENTS)
  })

  it('adds a whole ₱15 band per started kilometre past the first two', () => {
    expect(feeForKm(2.1)).toBe(4900 + 1500)
    expect(feeForKm(3)).toBe(4900 + 1500)
    expect(feeForKm(3.4)).toBe(4900 + 3000)
    expect(feeForKm(5)).toBe(4900 + 4500)
  })

  it('never goes below the base fee', () => {
    expect(feeForKm(-5)).toBe(DELIVERY_BASE_FEE_CENTS)
  })
})

describe('quoteDelivery', () => {
  it('is serviceable up to and including the maximum distance', () => {
    expect(quoteDelivery(DELIVERY_MAX_KM).serviceable).toBe(true)
  })

  it('refuses past the maximum distance, and quotes no fee for it', () => {
    const quote = quoteDelivery(DELIVERY_MAX_KM + 0.1)
    expect(quote.serviceable).toBe(false)
    expect(quote.feeCents).toBe(0)
  })

  it('rounds the reported distance to two places', () => {
    expect(quoteDelivery(3.456).distanceKm).toBe(3.46)
  })
})

describe('haversineKm', () => {
  it('is zero for the same point', () => {
    expect(haversineKm(16.4123, 120.596, 16.4123, 120.596)).toBe(0)
  })

  it('is symmetric', () => {
    const a = haversineKm(16.4123, 120.596, 16.4095, 120.5995)
    const b = haversineKm(16.4095, 120.5995, 16.4123, 120.596)
    expect(a).toBeCloseTo(b, 10)
  })

  it('measures a known short hop in Baguio to within 100m', () => {
    // Session Road to SM City Baguio — a little under half a kilometre.
    const km = haversineKm(16.4123, 120.596, 16.4095, 120.5995)
    expect(km).toBeGreaterThan(0.3)
    expect(km).toBeLessThan(0.7)
  })
})
