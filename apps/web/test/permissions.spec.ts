import { describe, expect, it } from 'vitest'
import { appPageKeys, defaultRoles, ownerPageKeys } from '@pos/shared/index'

/*
 * The class of bug these guard against: a role stored with only *some* page
 * keys. `withAllPermissionKeys` in packages/core/src/stores/auth.ts coerces any
 * absent key to false, so a short permission map silently denies every page it
 * forgot to mention — it does not fall back to a default. The backend seeder
 * shipped exactly that, granting a page key ('analytics') that does not exist
 * while omitting nine that do.
 */

describe('appPageKeys', () => {
  it('has no duplicates', () => {
    expect(new Set(appPageKeys).size).toBe(appPageKeys.length)
  })

  it('contains every owner-only page', () => {
    for (const page of ownerPageKeys) {
      expect(appPageKeys).toContain(page)
    }
  })
})

describe('defaultRoles', () => {
  it('defines the four built-in roles', () => {
    expect(defaultRoles.map((role) => role.id).sort()).toEqual(
      ['admin', 'cashier', 'guest', 'manager'],
    )
  })

  it('gives every role an entry for every page key, granted or not', () => {
    for (const role of defaultRoles) {
      for (const page of appPageKeys) {
        expect(
          Object.prototype.hasOwnProperty.call(role.permissions, page),
          `role "${role.id}" is missing the "${page}" key`,
        ).toBe(true)
        expect(typeof role.permissions[page]).toBe('boolean')
      }
    }
  })

  it('grants no permission that is not a real page', () => {
    for (const role of defaultRoles) {
      for (const page of Object.keys(role.permissions)) {
        expect(appPageKeys, `role "${role.id}" grants unknown page "${page}"`)
          .toContain(page as (typeof appPageKeys)[number])
      }
    }
  })

  it('gives Admin every page', () => {
    const admin = defaultRoles.find((role) => role.id === 'admin')!
    for (const page of appPageKeys) {
      expect(admin.permissions[page], `admin should have ${page}`).toBe(true)
    }
  })

  it('gives Guest nothing — an all-false guest is what disables guest access', () => {
    const guest = defaultRoles.find((role) => role.id === 'guest')!
    expect(appPageKeys.some((page) => guest.permissions[page])).toBe(false)
  })

  it('keeps owner-only pages away from Manager and Cashier', () => {
    for (const id of ['manager', 'cashier']) {
      const role = defaultRoles.find((r) => r.id === id)!
      for (const page of ownerPageKeys) {
        expect(role.permissions[page], `${id} must not have ${page}`).toBe(false)
      }
    }
  })

  it('does not let Cashier reach staff management', () => {
    const cashier = defaultRoles.find((role) => role.id === 'cashier')!
    expect(cashier.canManageStaff).toBe(false)
    expect(cashier.permissions.employees).toBe(false)
  })
})
