#!/usr/bin/env node
// Exports the shared demo catalog (packages/shared) to JSON so the Laravel
// seeders can read it without a TypeScript toolchain.
//
//   node scripts/export-demo-catalog.mjs
//
// The catalog is the source of truth in TS — it is what the client renders
// from — and this is a projection of it, not a second copy to edit. Re-run
// after changing demoProducts/demoCategories or regenerating the grocery half
// with import-legacy-products.mjs.
//
// The module is evaluated rather than parsed: demoProducts is built through
// helper functions (standardProduct, serviceProduct, …), so reading the values
// out of a running module is the only way to get the real rows.
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs'
import { resolve, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import ts from 'typescript'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const srcDir = join(repoRoot, 'packages/shared/src')
const outPath = join(repoRoot, 'backend/database/seeders/data/demo-catalog.json')

// Transpiled into a scratch directory as CommonJS: index.ts imports
// ./groceryCatalog.generated, so both halves have to sit next to each other
// under a package.json that marks them CJS.
const workDir = join(repoRoot, 'tmp/.catalog-export')
rmSync(workDir, { recursive: true, force: true })
mkdirSync(workDir, { recursive: true })
writeFileSync(join(workDir, 'package.json'), JSON.stringify({ type: 'commonjs' }))

for (const name of ['groceryCatalog.generated', 'index']) {
  const source = readFileSync(join(srcDir, `${name}.ts`), 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  })
  writeFileSync(join(workDir, `${name}.js`), outputText)
}

const require = createRequire(import.meta.url)
const shared = require(join(workDir, 'index.js'))

const categories = shared.demoCategories
const products = shared.demoProducts

if (!Array.isArray(categories) || !Array.isArray(products)) {
  throw new Error('demoCategories/demoProducts missing from packages/shared')
}

// Every product names a category id; a product whose category was never
// declared would be seeded with a dangling reference and vanish from the
// storefront, so fail loudly here instead.
const known = new Set(categories.map((c) => c.id))
const orphans = [...new Set(products.map((p) => p.categoryId).filter((id) => !known.has(id)))]
if (orphans.length > 0) {
  throw new Error(`products reference unknown categories: ${orphans.join(', ')}`)
}

const byMode = {}
for (const product of products) {
  for (const mode of product.businessModes ?? []) {
    byMode[mode] = (byMode[mode] ?? 0) + 1
  }
}

mkdirSync(dirname(outPath), { recursive: true })
writeFileSync(
  outPath,
  `${JSON.stringify({ generatedAt: new Date().toISOString(), categories, products }, null, 2)}\n`,
)

rmSync(workDir, { recursive: true, force: true })

console.log(`categories: ${categories.length}`)
console.log(`products:   ${products.length}`)
for (const [mode, count] of Object.entries(byMode).sort()) {
  console.log(`  ${mode.padEnd(12)} ${count}`)
}
console.log(`wrote ${outPath}`)
