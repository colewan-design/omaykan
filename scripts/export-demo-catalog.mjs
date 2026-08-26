#!/usr/bin/env node
// Exports one business mode's slice of the demo catalog to JSON, for the
// Laravel importer to seed a demonstration seller from.
//
//   node scripts/export-demo-catalog.mjs --mode grocery
//
// The catalog lives in packages/shared as TypeScript (demoCategories,
// demoProducts, and the ~450 smmarkets.ph rows in groceryCatalog.generated.ts).
// Node cannot import that directly — the modules use extensionless specifiers —
// so it is bundled with the rolldown already in node_modules, then imported.
// Output is data, not code: backend/database/data/demo-catalog-<mode>.json,
// read by `php artisan catalog:import-demo`.
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { tmpdir } from 'node:os'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`)
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

const mode = arg('mode', 'grocery')

const bundle = resolve(tmpdir(), `omaykan-shared-${process.pid}.mjs`)
// Rolldown's own CLI entry, run through this same node binary. The .bin shims
// are not usable here: the extensionless one is a shell script Windows cannot
// execute, and node 24 refuses to spawnSync a .cmd without a shell.
const rolldownCli = resolve(repoRoot, 'node_modules/rolldown/bin/cli.mjs')

execFileSync(process.execPath, [
  rolldownCli,
  'packages/shared/src/index.ts',
  '--format', 'esm',
  '--file', bundle,
], { cwd: repoRoot, stdio: 'pipe' })

let shared
try {
  shared = await import(pathToFileURL(bundle).href)
} finally {
  rmSync(bundle, { force: true })
}

const { demoCategories, demoProducts } = shared

// Same rule the storefront's own fallback applies, so the seeded catalog and
// the demo shelves show the same thing: this mode's products, minus anything
// the demo data already marks out of stock.
const products = demoProducts.filter((p) => p.businessModes?.includes(mode) && !p.outOfStock)

const usedCategoryIds = new Set(products.map((p) => p.categoryId))

// demoCategories order is the display order the storefront category strip uses;
// preserved here as sortOrder so the seeded store reads the same left to right.
const categories = demoCategories
  .filter((c) => usedCategoryIds.has(c.id))
  .map((c, index) => ({ id: c.id, name: c.name, sortOrder: index }))

const payload = {
  businessMode: mode,
  generatedAt: new Date().toISOString(),
  source: 'packages/shared/src/index.ts + groceryCatalog.generated.ts',
  categories,
  products: products.map((p) => ({
    externalId: p.id,
    categoryId: p.categoryId,
    sku: p.sku ?? null,
    barcode: p.barcode ?? null,
    name: p.name,
    priceCents: p.priceCents,
    compareAtPriceCents: p.compareAtPriceCents ?? null,
    taxRate: p.taxRate ?? 0.12,
    kind: p.kind === 'weighted' ? 'weighted' : 'standard',
    imageUrl: p.imageUrl ?? null,
    unitLabel: p.unitLabel ?? null,
    businessModes: p.businessModes ?? [mode],
    stockQty: p.stockQty ?? 0,
    lowStockThreshold: p.lowStockThreshold ?? null,
  })),
}

const outDir = resolve(repoRoot, 'backend/database/data')
mkdirSync(outDir, { recursive: true })
const outFile = resolve(outDir, `demo-catalog-${mode}.json`)
writeFileSync(outFile, `${JSON.stringify(payload, null, 2)}\n`)

console.log(`${mode}: ${categories.length} categories, ${products.length} products`)
console.log(`wrote ${outFile}`)
