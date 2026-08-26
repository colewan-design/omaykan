# Omaykan — Firestore schema

Firestore has no DDL, so this file documents the collection layout that
`packages/data/src/firebase-sync.ts` and `firebase/firestore.rules` assume.

## Collections

```
organizations/{orgSlug}                                    doc id = org slug
  { name: string, firstAdminClaimed: boolean, createdAt: Timestamp, suspended?: boolean }

organizations/{orgSlug}/stores/{storeCode}                 doc id = store code
  { name: string, createdAt: Timestamp, pairingCode: string, businessMode: string, address: string }

organizations/{orgSlug}/roles/{roleKey}
  { name: string, permissions: Record<AppPageKey, boolean>, deletedAt: Timestamp | null }

organizations/{orgSlug}/categories/{categoryId}            doc id = client-generated UUID
  { name: string, sortOrder: number, updatedAt: Timestamp, deletedAt: Timestamp | null }

organizations/{orgSlug}/products/{productId}                doc id = client-generated UUID
  { categoryId: string | null, sku: string, barcode: string, name: string, productType: string,
    taxRate: number, priceCents: number, trackInventory: boolean, isActive: boolean,
    businessModes: string[], stockQty: number | null, lowStockThreshold: number | null,
    updatedAt: Timestamp, deletedAt: Timestamp | null }

organizations/{orgSlug}/stores/{storeCode}/inventoryLevels/{productId}       doc id = productId
  { qtyOnHand: number, reorderLevel: number | null, updatedAt: Timestamp }

organizations/{orgSlug}/stores/{storeCode}/inventoryAdjustments/{adjId}
  { productId: string, orderId: string | null, adjustmentType: string, quantityDelta: number,
    reason: string | null, createdAt: Timestamp }

organizations/{orgSlug}/stores/{storeCode}/orders/{orderId}                  doc id = client-generated UUID
  { ticketNumber: string, businessMode: string, orderType: string, paymentMethod: string,
    subtotalCents: number, taxCents: number, totalCents: number, tenderedCents: number,
    changeCents: number, completedAt: string, createdAt: Timestamp }
  /items/{itemId}                                                            doc id = `item-${index}`
    { productId: string | null, productName: string, quantity: number,
      unitPriceCents: number, lineTotalCents: number }

organizations/{orgSlug}/stores/{storeCode}/shifts/{shiftId}                  doc id = client-generated
  { openedByUserId: string | null, closedByUserId: string | null, openingCashCents: number,
    closingCashCents: number | null, cashSalesCents: number, payInsCents: number,
    payOutsCents: number, expectedCashCents: number, varianceCashCents: number | null,
    openedAt: Timestamp, closedAt: Timestamp | null }
  /cashMovements/{movementId}
    { userId: string | null, movementType: 'pay_in' | 'pay_out', amountCents: number,
      reason: string | null, createdAt: Timestamp }

organizations/{orgSlug}/stores/{storeCode}/syncEvents/{eventId}              doc id = outbox event.id
  { entityType: string, entityId: string, operation: 'upsert',
    receivedAt: Timestamp, appliedAt: Timestamp }

users/{uid}                                                 top-level, doc id = Firebase Auth uid
  { organizationId: string, fullName: string, username: string, status: string,
    roleKey: string, createdAt: Timestamp }

appReleases/{appId}                                         top-level, doc id = e.g. "storefront-android"
  { versionCode: number, versionName: string, apkUrl: string, notes: string | null }
```

Notes:
- `organizations` and `stores` doc IDs are the slug/code themselves (not random IDs), so the
  client can resolve them with a plain `getDoc` — no query needed.
- `users` merges what used to be two Postgres tables (`users` + `organization_memberships`) — one
  membership per user in practice.
- `products.stockQty`/`lowStockThreshold` are a denormalized cache of `inventoryLevels`, kept in
  sync in the same transaction whenever stock changes (order push, inventory adjustment). This
  assumes a single store per organization, matching how the app is configured today
  (`VITE_POS_STORE_CODE`).
- `deletedAt` fields must be written as literal `null` (not omitted) — `firestore.rules`/queries
  that filter `where('deletedAt', '==', null)` (used for `roles`) require the field to exist.
- `stores.pairingCode` is a short, developer-assigned code unique across *all* stores of *every*
  org (not just within one org) — the mobile storefront app is a single shared build, and a
  customer types this code in on first launch to resolve which org/store/businessMode to show
  (see `resolveStoreCode` in `functions/src/index.ts`, which does a `collectionGroup('stores')`
  query on it — needs the `firestore.indexes.json` collection-group index on `pairingCode`).
  `stores.businessMode` is the Firestore-side counterpart of the staff app's local
  `AppSettings.businessMode` — the storefront has no authenticated way to read the latter, so the
  store doc carries its own copy for `resolveStoreCode` to hand back.
- `organizations.suspended` is set by the platform operator (now
  `backend/app/Http/Controllers/Api/Platform/OrganizationController.php`) to lock out
  an entire org's staff logins (checked in `firebase-sync.ts`'s `loginUser`). Absent/`false` means
  active. Like the rest of the org doc, it's readable by any signed-in user of any org — an
  existing, intentional `firestore.rules` pattern (`allow read: if signedIn()`, no org-scoping),
  not something this field introduces.

## Bootstrap (out-of-band provisioning)

`firestore.rules` denies client-side `create` on `organizations` and `write` on `stores` — these
must be seeded via the Firebase Console or a one-off Admin SDK script before the first login,
analogous to the commented-out seed `INSERT`s that used to live at the bottom of the old
Supabase `schema.sql`. Example seed values:

```
organizations/demo-coffee = { name: "Demo Coffee", firstAdminClaimed: false, createdAt: <now> }
organizations/demo-coffee/stores/main = { name: "Main Branch", createdAt: <now>,
  pairingCode: "DEMO01", businessMode: "coffee-shop", address: "123 Market Street" }
```

Also seed `appReleases/storefront-android` once a build is published (see above), and update it by
hand on each new release — the mobile app's Settings screen polls it for its update-available
indicator.

The first user to register against an org becomes `admin` (flips `firstAdminClaimed` to `true`
inside a transaction); everyone after becomes `cashier`.

## Setup steps

1. Create a Firebase project (console.firebase.google.com).
2. Authentication → Sign-in method → enable **Email/Password**.
3. Firestore Database → create database in **Native mode**.
4. Seed the org/store docs above (Console or Admin SDK script).
5. From the repo root: `firebase deploy --only firestore:rules,firestore:indexes`
   (requires `firebase-tools` and `firebase login`; update `.firebaserc`'s `default` project id
   first).
6. Copy the web app config (Project settings → General → Your apps → SDK setup) into
   `apps/web/.env` per `apps/web/.env.example`.
