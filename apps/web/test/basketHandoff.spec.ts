import { describe, expect, it } from 'vitest'
import { decodeBasketHandoff, encodeBasketHandoff } from '@pos/web/commerce/basketHandoff'

const A = '01a0aa40-2b35-7004-82f4-29f63dbd00fe'
const B = '01a0aa40-2c18-7348-a6ea-9f7a5811dba5'

describe('basket handoff', () => {
  it('round-trips a basket', () => {
    const basket = { orgSlug: 'nenas-market-stall', storeCode: 'main', lines: [{ productId: A, quantity: 2 }, { productId: B, quantity: 1 }] }
    const encoded = encodeBasketHandoff(basket)
    expect(encoded).toBe(`nenas-market-stall~main~${A}*2.${B}*1`)
    expect(decodeBasketHandoff(encoded)).toEqual(basket)
    // Survives the query string untouched.
    expect(new URLSearchParams(`basket=${encoded}`).get('basket')).toBe(encoded)
  })

  it('leaves out lines no order could carry', () => {
    const encoded = encodeBasketHandoff({
      orgSlug: 's',
      storeCode: 'main',
      lines: [{ productId: 'demo-coffee', quantity: 1 }, { productId: A, quantity: 0 }, { productId: B, quantity: 5000 }],
    })
    expect(encoded).toBe(`s~main~${B}*999`)
  })

  it('refuses anything malformed', () => {
    for (const raw of [
      null,
      '',
      's~main',
      `s~main~`,
      `s~main~${A}`,
      `s~main~${A}*0`,
      `s~main~${A}*1000`,
      `s~main~${A}*1.${A}*2`,
      `s~main~not-a-uuid*1`,
      `s/x~main~${A}*1`,
      `s~main~${A}*1*2`,
      `s~main~${A}*1~extra`,
    ]) {
      expect(decodeBasketHandoff(raw)).toBeNull()
    }
  })
})
