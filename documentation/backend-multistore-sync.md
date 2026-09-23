# Multi-Store Backend Sync Spec

Last updated: June 20, 2026

## Goal

Design the Laravel + PostgreSQL backend so:

- one business can manage multiple stores
- each POS device belongs to exactly one store at a time
- sales and inventory records never collide across stores
- sync remains offline-first and idempotent
- catalog can be shared across stores without duplicating everything

This document is the implementation-ready companion to [plan.md](./plan.md).

## Core tenancy model

Use four top-level ownership layers:

1. `organization`
2. `store`
3. `device`
4. `user`

Rules:

- An `organization` owns many `stores`.
- A `store` belongs to one `organization`.
- A `device` belongs to one `store`.
- A `user` can belong to one or more organizations and stores through membership tables.
- Every transactional record must be scoped so the server can reject cross-store writes immediately.

## Ownership rules

These rules are the main defense against conflicts.

### Organization-scoped records

Shared across stores in the same business:

- `categories`
- `products`
- `roles`
- `tax_profiles`
- `organization_settings`

### Store-scoped records

Isolated per branch:

- `devices`
- `orders`
- `order_items`
- `payments`
- `shifts`
- `cash_movements`
- `inventory_levels`
- `inventory_adjustments`
- `store_settings`
- `product_store_overrides`

### Device-scoped records

Created by a single terminal:

- `sync_outbox`
- `sync_cursor_state`
- `app_events`
- `device_sessions`

## Record identity strategy

Use client-generated UUIDs for all business records written offline.

Rules:

- The client creates UUIDs before sync.
- The server treats those UUIDs as stable public IDs.
- Sync endpoints are idempotent by record UUID plus operation type.
- Deletes are soft deletes using `deleted_at`.
- Inventory is synchronized as adjustment events, not absolute quantity replacements.

That prevents:

- duplicate order creation after retry
- store A overwriting store B
- one device clobbering another device's stock count

## PostgreSQL schema

The schema below is the recommended first production shape. Types are shown in PostgreSQL form, but Laravel migrations can express the same structure directly.

### Organizations

```sql
create table organizations (
  id uuid primary key,
  name text not null,
  slug text not null unique,
  status text not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
```

### Stores

```sql
create table stores (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  name text not null,
  code text not null,
  timezone text not null default 'Asia/Manila',
  currency_code char(3) not null default 'PHP',
  status text not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (organization_id, code)
);

create index stores_organization_id_idx on stores (organization_id);
```

### Users

```sql
create table users (
  id uuid primary key,
  name text not null,
  email text unique,
  username text unique,
  password_hash text,
  status text not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
```

### Organization memberships

```sql
create table organization_memberships (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  user_id uuid not null references users(id),
  membership_role text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, user_id)
);
```

### Store memberships

```sql
create table store_memberships (
  id uuid primary key,
  store_id uuid not null references stores(id),
  user_id uuid not null references users(id),
  membership_role text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (store_id, user_id)
);
```

### Devices

```sql
create table devices (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  device_name text not null,
  platform text not null,
  app_version text,
  status text not null default 'active',
  last_seen_at timestamptz,
  activated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index devices_store_id_idx on devices (store_id);
create index devices_organization_id_idx on devices (organization_id);
```

### Categories

```sql
create table categories (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  name text not null,
  sort_order integer not null default 0,
  created_by_device_id uuid references devices(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index categories_organization_id_idx on categories (organization_id);
```

### Products

```sql
create table products (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  category_id uuid references categories(id),
  sku text,
  barcode text,
  name text not null,
  product_type text not null default 'standard',
  tax_rate numeric(5,2) not null default 12.00,
  price_cents integer not null,
  track_inventory boolean not null default true,
  is_active boolean not null default true,
  created_by_device_id uuid references devices(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (organization_id, sku),
  unique (organization_id, barcode)
);

create index products_organization_id_idx on products (organization_id);
create index products_category_id_idx on products (category_id);
```

### Product store overrides

Use this only for store-specific price, availability, or local display changes.

```sql
create table product_store_overrides (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  product_id uuid not null references products(id),
  price_cents integer,
  is_available boolean,
  display_name text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (store_id, product_id)
);

create index product_store_overrides_store_id_idx on product_store_overrides (store_id);
```

### Inventory levels

This is the current derived state for one product in one store.

```sql
create table inventory_levels (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  product_id uuid not null references products(id),
  qty_on_hand numeric(12,3) not null default 0,
  reorder_level numeric(12,3),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (store_id, product_id)
);

create index inventory_levels_store_id_idx on inventory_levels (store_id);
create index inventory_levels_product_id_idx on inventory_levels (product_id);
```

### Inventory adjustments

This is the sync-safe source of truth for stock movement.

```sql
create table inventory_adjustments (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  device_id uuid references devices(id),
  product_id uuid not null references products(id),
  order_id uuid,
  adjustment_type text not null,
  quantity_delta numeric(12,3) not null,
  reason text,
  created_at timestamptz not null default now(),
  synced_at timestamptz,
  deleted_at timestamptz
);

create index inventory_adjustments_store_id_idx on inventory_adjustments (store_id, created_at);
create index inventory_adjustments_product_id_idx on inventory_adjustments (product_id, created_at);
```

### Orders

```sql
create table orders (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  device_id uuid not null references devices(id),
  user_id uuid references users(id),
  ticket_number text not null,
  order_status text not null default 'completed',
  order_type text not null,
  payment_status text not null default 'paid',
  subtotal_cents integer not null,
  tax_cents integer not null,
  total_cents integer not null,
  business_date date not null,
  completed_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index orders_store_id_completed_at_idx on orders (store_id, completed_at desc);
create index orders_device_id_idx on orders (device_id);
create unique index orders_store_ticket_number_uniq on orders (store_id, ticket_number);
```

### Order items

```sql
create table order_items (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  order_id uuid not null references orders(id),
  product_id uuid references products(id),
  product_name text not null,
  quantity numeric(12,3) not null,
  unit_price_cents integer not null,
  line_total_cents integer not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index order_items_order_id_idx on order_items (order_id);
```

### Payments

```sql
create table payments (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  order_id uuid not null references orders(id),
  payment_method text not null,
  amount_cents integer not null,
  tendered_cents integer,
  change_cents integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index payments_order_id_idx on payments (order_id);
create index payments_store_id_idx on payments (store_id, created_at desc);
```

### Shifts

```sql
create table shifts (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  device_id uuid references devices(id),
  opened_by_user_id uuid references users(id),
  closed_by_user_id uuid references users(id),
  opening_cash_cents integer not null default 0,
  closing_cash_cents integer,
  expected_cash_cents integer,
  variance_cash_cents integer,
  opened_at timestamptz not null,
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index shifts_store_id_opened_at_idx on shifts (store_id, opened_at desc);
```

### Cash movements

```sql
create table cash_movements (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  shift_id uuid not null references shifts(id),
  user_id uuid references users(id),
  movement_type text not null,
  amount_cents integer not null,
  reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index cash_movements_shift_id_idx on cash_movements (shift_id);
```

### App events

```sql
create table app_events (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  device_id uuid not null references devices(id),
  event_type text not null,
  payload jsonb not null,
  app_version text,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index app_events_store_id_occurred_at_idx on app_events (store_id, occurred_at desc);
```

### Sync event ledger

This is the server-side ingestion ledger. It is the core idempotency table for pushed changes.

```sql
create table sync_events (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  device_id uuid not null references devices(id),
  entity_type text not null,
  entity_id uuid not null,
  operation text not null,
  payload jsonb not null,
  idempotency_key text not null,
  received_at timestamptz not null default now(),
  applied_at timestamptz,
  failed_at timestamptz,
  error_message text,
  unique (idempotency_key)
);

create index sync_events_store_id_received_at_idx on sync_events (store_id, received_at desc);
create index sync_events_entity_idx on sync_events (entity_type, entity_id);
```

### Sync cursors

```sql
create table sync_cursors (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  store_id uuid not null references stores(id),
  device_id uuid not null references devices(id),
  cursor_name text not null,
  cursor_value text not null,
  updated_at timestamptz not null default now(),
  unique (device_id, cursor_name)
);
```

## Required integrity checks

The database and application should both enforce these:

- `stores.organization_id` must match `devices.organization_id`
- `devices.store_id` must belong to the same organization as the device
- `orders.store_id` must match the authenticated device's store
- `orders.organization_id` must match the store's organization
- `order_items.store_id` must match the parent order's store
- `inventory_levels` and `inventory_adjustments` must never cross store boundaries
- `product_store_overrides.organization_id` must match both the store and the product organization

In Laravel, validate these before write. In Postgres, add foreign keys and unique constraints everywhere practical.

## Laravel model map

Recommended Eloquent models:

- `Organization`
- `Store`
- `User`
- `OrganizationMembership`
- `StoreMembership`
- `Device`
- `Category`
- `Product`
- `ProductStoreOverride`
- `InventoryLevel`
- `InventoryAdjustment`
- `Order`
- `OrderItem`
- `Payment`
- `Shift`
- `CashMovement`
- `AppEvent`
- `SyncEvent`
- `SyncCursor`

## Laravel relationship model

Suggested relationship shape:

```php
// Organization
hasMany(Store::class)
hasMany(Category::class)
hasMany(Product::class)
hasMany(Device::class)
hasMany(Order::class)
belongsToMany(User::class, 'organization_memberships')

// Store
belongsTo(Organization::class)
hasMany(Device::class)
hasMany(Order::class)
hasMany(Shift::class)
hasMany(InventoryLevel::class)
hasMany(InventoryAdjustment::class)
hasMany(ProductStoreOverride::class)
belongsToMany(User::class, 'store_memberships')

// Device
belongsTo(Organization::class)
belongsTo(Store::class)
hasMany(Order::class)
hasMany(AppEvent::class)
hasMany(SyncEvent::class)

// Product
belongsTo(Organization::class)
belongsTo(Category::class)
hasMany(ProductStoreOverride::class)
hasMany(InventoryLevel::class)
hasMany(InventoryAdjustment::class)

// Order
belongsTo(Organization::class)
belongsTo(Store::class)
belongsTo(Device::class)
belongsTo(User::class)
hasMany(OrderItem::class)
hasMany(Payment::class)

// Shift
belongsTo(Organization::class)
belongsTo(Store::class)
belongsTo(Device::class)
belongsTo(User::class, 'opened_by_user_id')
belongsTo(User::class, 'closed_by_user_id')
hasMany(CashMovement::class)
```

## Auth and access control

For POS devices, do not rely on broad user tokens alone. Devices need their own scoped credentials.

Recommended:

- user login for back office and admin actions
- device token for sync
- device token bound to one `organization_id` and one `store_id`

Server authorization rules:

- a device can only push records for its assigned `store_id`
- a device can only pull records belonging to its assigned `organization_id`
- a store-scoped device should only receive store-level deltas plus organization-level catalog data
- admin users can access multiple stores through memberships

## Sync contract

### Push

`POST /api/sync/push`

Request batch:

```json
{
  "deviceId": "uuid",
  "storeId": "uuid",
  "organizationId": "uuid",
  "events": [
    {
      "id": "uuid",
      "entityType": "order",
      "entityId": "uuid",
      "operation": "upsert",
      "occurredAt": "2026-06-20T03:00:00Z",
      "payload": {}
    }
  ]
}
```

Rules:

- Each event gets an `idempotency_key`, for example `deviceId:eventId`.
- The server writes the raw batch into `sync_events` first.
- Only after that does it apply the event to domain tables.
- Safe retries return success without duplicating the domain write.

### Pull

`GET /api/sync/pull?cursor=...`

Return:

- organization-scoped catalog deltas
- store-scoped inventory and operational deltas
- a new cursor

Use monotonically increasing cursor values from the server, not client timestamps.

## Conflict policy

Keep the first release simple and rule-based.

### No-merge entities

Append-only, never edited after completion:

- `orders`
- `order_items`
- `payments`
- `inventory_adjustments`
- `app_events`

Conflicts here should be treated as duplicate retry attempts unless payloads differ. If payloads differ for the same UUID, reject and flag for audit.

### Back-office-owned entities

Usually edited from admin surfaces:

- `products`
- `categories`
- `tax_profiles`
- `organization_settings`

Policy:

- back office is source of truth
- device edits are allowed only if the user's permissions explicitly allow it
- server uses `updated_at` and role checks, but ownership matters more than timestamp

### Store-local override entities

- `product_store_overrides`
- `store_settings`

Policy:

- only users or devices attached to that store can change them
- one store cannot affect another store's overrides

### Inventory

Never sync inventory as "set quantity to X".

Always sync:

- sale deduction
- restock
- spoilage
- manual correction

Then recompute or transactionally update `inventory_levels`.

This avoids the most common multi-store conflict class.

## Server-side write rules

When ingesting a device batch:

1. authenticate device token
2. load device, store, organization
3. verify request `deviceId`, `storeId`, `organizationId` match token scope
4. record each incoming event in `sync_events`
5. apply each event in a database transaction
6. reject any event whose payload references a foreign store or organization
7. return per-event status so the client can mark only successful outbox rows as sent

## Recommended Laravel migration order

1. `organizations`
2. `stores`
3. `users`
4. `organization_memberships`
5. `store_memberships`
6. `devices`
7. `categories`
8. `products`
9. `product_store_overrides`
10. `inventory_levels`
11. `orders`
12. `order_items`
13. `payments`
14. `inventory_adjustments`
15. `shifts`
16. `cash_movements`
17. `app_events`
18. `sync_events`
19. `sync_cursors`

## What should change in the app next

The current web app persists local arrays and settings in [packages/data/src/index.ts](../packages/data/src/index.ts). To support this backend cleanly, the next data-layer steps should be:

1. Split settings into device-local settings and remote organization/store settings.
2. Add `organizationId`, `storeId`, and `deviceId` to locally stored syncable records.
3. Introduce local tables or structured stores for:
   - `orders`
   - `order_items`
   - `products`
   - `categories`
   - `inventory_adjustments`
   - `sync_outbox`
4. Write inventory as adjustment events, not direct quantity overwrites.
5. Add a background sync service that pushes outbox rows and pulls server deltas by cursor.

## First implementation slice

If building in the safest order, start with:

1. Laravel migrations for:
   - `organizations`
   - `stores`
   - `devices`
   - `categories`
   - `products`
   - `orders`
   - `order_items`
   - `payments`
   - `sync_events`
   - `sync_cursors`
2. Device auth scoped to one store.
3. Push sync for orders only.
4. Pull sync for categories and products.
5. Inventory adjustments after order sync is stable.

That gives multi-store isolation early, without taking on every conflict class at once.

## Current web integration

The current `apps/web` client is now wired to the Laravel backend through Vite environment variables:

```env
VITE_API_BASE_URL=http://127.0.0.1:8000
VITE_POS_ORGANIZATION_SLUG=demo-coffee
VITE_POS_STORE_CODE=main
VITE_POS_DEVICE_NAME=Front Counter
VITE_POS_APP_VERSION=0.1.0
```

Notes:

- The repository still keeps local cache and local auth in IndexedDB so the app remains usable offline.
- Turning on `Online sync` in Settings makes staff sign-in reach the API; the token it mints is what catalog bootstrap and outbox sync then run on.
- Orders, products, and categories are queued locally first, then pushed to Laravel when sync is enabled.
