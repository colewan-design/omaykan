import { describe, expect, it } from 'vitest'
import { discountPercentOf, maxDiscountPercentFor, priceOrder, defaultRoles } from '@pos/shared/index'

/**
 * The one calculation the cart shows, the till records and the receipt prints.
 * See documentation/merchant-features.md §7.
 */
describe('priceOrder', () => {
  it('charges each line at its own rate when there is no discount', () => {
    // A zero-rated vegetable beside a 12% drink: the old flat 12% taxed both.
    const priced = priceOrder([
      { lineTotalCents: 10000, taxRate: 0 },
      { lineTotalCents: 5000, taxRate: 0.12 },
    ])

    expect(priced).toEqual({ subtotalCents: 15000, discountCents: 0, taxCents: 600, totalCents: 15600, discount: null })
  })

  it('takes a percentage off before tax — the worked example the server test uses', () => {
    const priced = priceOrder([{ lineTotalCents: 24000, taxRate: 0.12 }], { kind: 'manual', percent: 10, reason: ' Regular ' })

    expect(priced.subtotalCents).toBe(24000)
    expect(priced.discountCents).toBe(2400)
    expect(priced.taxCents).toBe(2592)
    expect(priced.totalCents).toBe(24192)
    expect(priced.discount).toEqual({ kind: 'manual', amountCents: 2400, percent: 10, reason: 'Regular' })
  })

  it('never takes off more than the subtotal', () => {
    const priced = priceOrder([{ lineTotalCents: 5000, taxRate: 0.12 }], { kind: 'manual', amountCents: 999999 })

    expect(priced.discountCents).toBe(5000)
    expect(priced.taxCents).toBe(0)
    expect(priced.totalCents).toBe(0)
  })

  it('shares an order discount across lines so each is taxed at its own rate on what is left', () => {
    // ₱10 off ₱300: ₱100 zero-rated, ₱200 at 12%. Shares 333 / 666, the
    // left-over centavo on the larger line: 333 / 667.
    const priced = priceOrder(
      [
        { lineTotalCents: 10000, taxRate: 0 },
        { lineTotalCents: 20000, taxRate: 0.12 },
      ],
      { kind: 'manual', amountCents: 1000 },
    )

    expect(priced.discountCents).toBe(1000)
    expect(priced.taxCents).toBe(Math.round((20000 - 667) * 0.12))
    expect(priced.totalCents).toBe(30000 - 1000 + priced.taxCents)
  })

  it('treats an empty cart and a zero discount as no discount', () => {
    expect(priceOrder([], { kind: 'manual', percent: 50 }).discount).toBeNull()
    expect(priceOrder([{ lineTotalCents: 100, taxRate: 0.12 }], { kind: 'manual', amountCents: 0 }).discount).toBeNull()
  })
})

describe('discount limits', () => {
  const role = (id: string) => defaultRoles.find((entry) => entry.id === id)!

  it('lets admin give anything and cashiers nothing unless the shop says otherwise', () => {
    expect(maxDiscountPercentFor(role('admin'))).toBe(100)
    expect(maxDiscountPercentFor({ ...role('admin'), maxDiscountPercent: 5 })).toBe(100)
    expect(maxDiscountPercentFor(role('manager'))).toBe(100)
    expect(maxDiscountPercentFor(role('cashier'))).toBe(0)
    expect(maxDiscountPercentFor({ ...role('cashier'), maxDiscountPercent: 10 })).toBe(10)
    expect(maxDiscountPercentFor(null)).toBe(0)
  })

  it('measures a discount against the subtotal it came off', () => {
    expect(discountPercentOf(2400, 24000)).toBe(10)
    expect(discountPercentOf(100, 0)).toBe(0)
  })
})
