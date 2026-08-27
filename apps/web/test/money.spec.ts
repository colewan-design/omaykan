import { describe, expect, it } from 'vitest'
import { calculateTax, discountPercent, formatCurrency } from '@pos/shared/index'

describe('calculateTax', () => {
  it('rounds to whole centavos', () => {
    // 12% of ₱1.01 is 12.12 centavos — money cannot hold the fraction.
    expect(calculateTax(101, 0.12)).toBe(12)
    expect(calculateTax(104, 0.12)).toBe(12)
    expect(calculateTax(105, 0.12)).toBe(13)
  })

  it('is exact on round figures', () => {
    expect(calculateTax(10000, 0.12)).toBe(1200)
  })

  it('returns zero for a zero amount or a zero rate', () => {
    expect(calculateTax(0, 0.12)).toBe(0)
    expect(calculateTax(10000, 0)).toBe(0)
  })

  it('never returns a fraction of a centavo', () => {
    for (const amount of [1, 7, 33, 99, 12345, 999999]) {
      expect(Number.isInteger(calculateTax(amount, 0.12))).toBe(true)
    }
  })
})

describe('discountPercent', () => {
  it('reports the rounded percentage off', () => {
    expect(discountPercent({ priceCents: 750, compareAtPriceCents: 950 })).toBe(21)
  })

  it('is null when there is no compare-at price', () => {
    expect(discountPercent({ priceCents: 750, compareAtPriceCents: null })).toBeNull()
    expect(discountPercent({ priceCents: 750, compareAtPriceCents: undefined })).toBeNull()
  })

  it('is null when the compare-at price is not actually higher', () => {
    // A "sale" that is not a saving must not render a badge.
    expect(discountPercent({ priceCents: 750, compareAtPriceCents: 750 })).toBeNull()
    expect(discountPercent({ priceCents: 750, compareAtPriceCents: 500 })).toBeNull()
  })

  it('is null for a zero or non-finite compare-at price', () => {
    expect(discountPercent({ priceCents: 750, compareAtPriceCents: 0 })).toBeNull()
    expect(discountPercent({ priceCents: 750, compareAtPriceCents: Number.NaN })).toBeNull()
    expect(discountPercent({ priceCents: 750, compareAtPriceCents: Number.POSITIVE_INFINITY })).toBeNull()
  })
})

describe('formatCurrency', () => {
  it('renders centavos as peso amounts', () => {
    // Non-breaking space between symbol and digits in the en-PH locale.
    expect(formatCurrency(12000).replace(/ /g, ' ')).toBe('₱120.00')
    expect(formatCurrency(0).replace(/ /g, ' ')).toBe('₱0.00')
  })

  it('always shows two decimal places', () => {
    expect(formatCurrency(750)).toMatch(/750|7\.50/)
    expect(formatCurrency(1000)).toMatch(/10\.00/)
  })
})
