#!/usr/bin/env node
// Regenerates the grocery half of the demo catalog from the product dump that
// lives in the old Dongdong-Ay-POS repo (a Puppeteer scrape of smmarkets.ph,
// ~5k items with real Philippine prices, unit sizes and product photos).
//
//   git clone --depth 1 https://github.com/colewan-design/Dongdong-Ay-POS.git
//   node scripts/import-legacy-products.mjs --source ../Dongdong-Ay-POS/products.json
//
// Output is a generated module — edit this script, not the module.
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`)
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

const sourcePath = resolve(repoRoot, arg('source', '../Dongdong-Ay-POS/products.json'))
const outPath = resolve(repoRoot, arg('out', 'packages/shared/src/groceryCatalog.generated.ts'))
const perCategory = Number(arg('per-category', '60'))

// Scraped aisle -> our category. `pantry` and the store's own private-label
// aisle both land in the existing "Groceries" bucket so we don't end up with
// three near-identical shelf names in the storefront header.
const CATEGORIES = {
  'fresh-produce': { id: 'produce', name: 'Produce', sku: 'PRO' },
  'fresh-meat-seafood': { id: 'meat-seafood', name: 'Meat & Seafood', sku: 'MSF' },
  bakery: { id: 'bakery', name: 'Bakery', sku: 'BAK' },
  'frozen-goods': { id: 'frozen', name: 'Frozen', sku: 'FRZ' },
  pantry: { id: 'groceries', name: 'Groceries', sku: 'GRO' },
  'only-in-sm-markets': { id: 'groceries', name: 'Groceries', sku: 'GRO' },
  'international-goods': { id: 'international', name: 'International', sku: 'INT' },
  'ready-to-cook': { id: 'ready-to-cook', name: 'Ready to Cook', sku: 'RTC' },
  'ready-to-heat-eat-items': { id: 'ready-to-eat', name: 'Ready to Eat', sku: 'RTE' },
}

// Categories the hand-written demo catalog already declares; only the rest need
// to be appended to demoCategories.
const EXISTING_CATEGORY_IDS = new Set(['groceries', 'produce', 'dairy', 'snacks'])

const pesosToCents = (text) => {
  const n = Number(String(text).replace(/[^\d.]/g, ''))
  return Number.isFinite(n) ? Math.round(n * 100) : null
}

// "SM Bonus J Onion Red | 350g-400g" -> "SM Bonus J Onion Red"
const cleanName = (name) => name.replace(/\s*\|\s*[^|]*$/, '').replace(/\s+/g, ' ').trim()

const slugify = (text) =>
  text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 48)

// Deterministic so a regeneration doesn't reshuffle every stock number.
function hash(text) {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h = Math.imul(h ^ text.charCodeAt(i), 16777619) >>> 0
  }
  return h
}

const source = JSON.parse(readFileSync(sourcePath, 'utf8'))

const seenNames = new Set()
const byCategory = new Map()

for (const raw of source) {
  const mapped = CATEGORIES[raw.category]
  if (!mapped || !raw.image || !raw.name) continue

  // The source site falls back to its own grey logo when an item has no photo;
  // those are worse than useless as demo tiles.
  if (raw.image.includes('/placeholder/default/')) continue

  const name = cleanName(raw.name)
  if (name.length < 3 || seenNames.has(name.toLowerCase())) continue

  // Skip the source chain's own private label — a demo storefront for local
  // Baguio shops shouldn't be stocked with a national chain's house brand.
  if (/\bSM\s+(Bonus|Markets)\b/i.test(name) || /^SM\b/i.test(name)) continue

  // Items sold loose carry a "₱189.00/KG" shelf price; those become weighted
  // products priced per kilo, the way the POS scale flow expects.
  const perKilo = /\/\s*KG/i.test(raw.weightedPrice ?? '')
  const priceCents = pesosToCents(perKilo ? raw.weightedPrice : raw.price)
  if (!priceCents || priceCents < 100 || priceCents > 500000) continue

  seenNames.add(name.toLowerCase())
  if (!byCategory.has(raw.category)) byCategory.set(raw.category, [])
  byCategory.get(raw.category).push({ raw, mapped, name, priceCents, perKilo })
}

// Sample evenly across each aisle instead of taking the first N — the scrape is
// page-ordered, so the first N are all the same handful of brands.
function sample(items, count) {
  if (items.length <= count) return items
  const step = items.length / count
  return Array.from({ length: count }, (_, i) => items[Math.floor(i * step)])
}

const products = []
let index = 0

for (const [scrapedCategory, items] of byCategory) {
  for (const item of sample(items, perCategory)) {
    index += 1
    const { raw, mapped, name, priceCents, perKilo } = item
    const h = hash(name)
    const id = `sm-${slugify(name)}-${(h % 997).toString(36)}`

    const product = {
      id,
      categoryId: mapped.id,
      sku: `${mapped.sku}-${String(index).padStart(4, '0')}`,
      barcode: String(4810000000000 + index),
      name,
      priceCents,
      taxRate: 0.12,
      kind: perKilo ? 'weighted' : 'standard',
      imageUrl: raw.image,
      unitLabel: perKilo ? '/ kg' : raw.uom || undefined,
      businessModes: ['grocery'],
    }

    // A slice of the shelf carries a strikethrough "was" price and a couple of
    // items are out of stock, so the discount badge and the empty-shelf state
    // both have something to render.
    if (h % 7 === 0) product.compareAtPriceCents = Math.round((priceCents * 1.25) / 50) * 50
    if (h % 53 === 0) {
      product.stockQty = 0
      product.outOfStock = true
    } else {
      product.stockQty = 4 + (h % 60)
      product.lowStockThreshold = 8
    }

    products.push(product)
    void scrapedCategory
  }
}

products.sort((a, b) => a.categoryId.localeCompare(b.categoryId) || a.name.localeCompare(b.name))

const categories = []
const seenCategories = new Set()
for (const { id, name } of Object.values(CATEGORIES)) {
  if (EXISTING_CATEGORY_IDS.has(id) || seenCategories.has(id)) continue
  seenCategories.add(id)
  categories.push({ id, name })
}

const serialize = (value) =>
  JSON.stringify(value)
    .replace(/"([A-Za-z][A-Za-z0-9]*)":/g, '$1: ')
    .replace(/,(?=[A-Za-z][A-Za-z0-9]*: )/g, ', ')
    .replace(/^\{/, '{ ')
    .replace(/\}$/, ' }')

const file = `// Generated by scripts/import-legacy-products.mjs — do not edit by hand.
//
// Placeholder grocery shelf carried over from the Dongdong-Ay-POS scrape of
// smmarkets.ph: real Philippine prices, pack sizes and product photos, which is
// what the demo storefront needs to look like a store rather than a fixture.
// The photos are hotlinked from the source site, so treat this as demo data and
// swap it for the tenant's own catalog before anything goes to production.
import type { Category, Product } from './index'

export const groceryCatalogCategories: Category[] = [
${categories.map((c) => `  ${serialize(c)},`).join('\n')}
]

export const groceryCatalogProducts: Product[] = [
${products.map((p) => `  ${serialize(p)},`).join('\n')}
]
`

writeFileSync(outPath, file)

const counts = products.reduce((acc, p) => ({ ...acc, [p.categoryId]: (acc[p.categoryId] ?? 0) + 1 }), {})
console.log(`wrote ${products.length} products to ${outPath}`)
console.log(counts)
