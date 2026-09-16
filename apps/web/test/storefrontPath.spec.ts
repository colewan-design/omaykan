import { describe, expect, it } from 'vitest'
import {
  isShopSubdomainLabel,
  slugFromShopHost,
  slugFromStorefrontPath,
  storefrontPath,
  storefrontUrl,
} from '@pos/shared/index'

describe('storefrontPath', () => {
  it('builds the shop page path', () => {
    expect(storefrontPath('marias-kitchen')).toBe('/shop/marias-kitchen')
  })

  it('round-trips a slug that needs escaping', () => {
    expect(slugFromStorefrontPath(storefrontPath('a b&c'))).toBe('a b&c')
  })
})

describe('storefrontUrl', () => {
  it('is the shop subdomain when a shop domain is configured', () => {
    expect(storefrontUrl('marias-kitchen', { rootDomain: 'omaykan.com', origin: 'https://omaykan.com' }))
      .toBe('https://marias-kitchen.omaykan.com')
  })

  it('falls back to the path without a shop domain', () => {
    expect(storefrontUrl('marias-kitchen', { rootDomain: '', origin: 'http://localhost:5173/' }))
      .toBe('http://localhost:5173/shop/marias-kitchen')
  })

  it('falls back to the path for a slug that cannot be a hostname', () => {
    expect(storefrontUrl('app', { rootDomain: 'omaykan.com', origin: 'https://omaykan.com' }))
      .toBe('https://omaykan.com/shop/app')
    expect(storefrontUrl('a'.repeat(64), { rootDomain: 'omaykan.com', origin: 'https://omaykan.com' }))
      .toBe(`https://omaykan.com/shop/${'a'.repeat(64)}`)
  })
})

describe('isShopSubdomainLabel', () => {
  it('takes ordinary slugs', () => {
    expect(isShopSubdomainLabel('nenas-market-stall')).toBe(true)
    expect(isShopSubdomainLabel('sm')).toBe(true)
    expect(isShopSubdomainLabel('7eleven')).toBe(true)
  })

  it('refuses reserved names and anything DNS would', () => {
    for (const label of ['www', 'api', 'app', '-x', 'x-', 'A', 'a_b', 'a.b', '', 'a'.repeat(64)]) {
      expect(isShopSubdomainLabel(label)).toBe(false)
    }
  })
})

describe('slugFromShopHost', () => {
  it('reads a shop subdomain', () => {
    expect(slugFromShopHost('nenas-market-stall.omaykan.com', 'omaykan.com')).toBe('nenas-market-stall')
    expect(slugFromShopHost('Nenas-Market-Stall.Omaykan.com.', 'omaykan.com')).toBe('nenas-market-stall')
  })

  it('is blank for the root, reserved names, deeper hosts and other domains', () => {
    expect(slugFromShopHost('omaykan.com', 'omaykan.com')).toBe('')
    expect(slugFromShopHost('www.omaykan.com', 'omaykan.com')).toBe('')
    expect(slugFromShopHost('a.b.omaykan.com', 'omaykan.com')).toBe('')
    expect(slugFromShopHost('evilomaykan.com', 'omaykan.com')).toBe('')
    expect(slugFromShopHost('shop.salidumay.com', 'omaykan.com')).toBe('')
    expect(slugFromShopHost('nenas.omaykan.com', '')).toBe('')
  })
})

describe('slugFromStorefrontPath', () => {
  it('reads the slug, with or without a trailing slash', () => {
    expect(slugFromStorefrontPath('/shop/marias-kitchen')).toBe('marias-kitchen')
    expect(slugFromStorefrontPath('/shop/marias-kitchen/')).toBe('marias-kitchen')
  })

  it('is blank for anything that is not a shop page', () => {
    expect(slugFromStorefrontPath('/')).toBe('')
    expect(slugFromStorefrontPath('/shop/')).toBe('')
    expect(slugFromStorefrontPath('/shop')).toBe('')
    expect(slugFromStorefrontPath('/shop/a/b')).toBe('')
    expect(slugFromStorefrontPath('/cart')).toBe('')
    expect(slugFromStorefrontPath('/shop/%E0%A4%A')).toBe('')
  })
})
