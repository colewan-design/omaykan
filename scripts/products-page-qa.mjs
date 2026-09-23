import { chromium } from 'playwright'
import path from 'node:path'

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1680, height: 947 }, deviceScaleFactor: 1 })
const consoleErrors = []
const pageErrors = []
page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()) })
page.on('pageerror', (error) => pageErrors.push(error.message))

await page.goto('http://127.0.0.1:5173/app/products', { waitUntil: 'networkidle' })

if (await page.getByRole('heading', { name: 'Create your account' }).isVisible().catch(() => false)) {
  await page.getByLabel('Full name').fill('SM Owner')
  await page.getByLabel('Username').fill('sm-owner')
  await page.locator('input[autocomplete="new-password"]').fill('password')
  await page.getByRole('button', { name: 'Create account' }).click()
  await page.waitForLoadState('networkidle')
  await page.goto('http://127.0.0.1:5173/app/products', { waitUntil: 'networkidle' })
} else if (await page.getByRole('heading', { name: 'Welcome back' }).isVisible().catch(() => false)) {
  await page.getByLabel('Username').fill('sm-owner')
  await page.locator('input[autocomplete="current-password"]').fill('password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForLoadState('networkidle')
  await page.goto('http://127.0.0.1:5173/app/products', { waitUntil: 'networkidle' })
}

await page.locator('.products-page').waitFor({ state: 'visible' })
await page.screenshot({ path: path.resolve('documentation/design/products-refresh/2026-09-14/products-implementation-desktop.png'), fullPage: true })

const desktop = await page.evaluate(() => ({
  viewport: [window.innerWidth, window.innerHeight],
  bodyHeight: document.documentElement.scrollHeight,
  tableOverflowY: getComputedStyle(document.querySelector('.ptable')).overflowY,
  toolbarRows: Math.round(document.querySelector('.ptoolbar').getBoundingClientRect().height),
  visibleRows: document.querySelectorAll('.ptable__row').length,
}))

await page.getByRole('button', { name: /Low Stock/ }).click()
const lowStockValue = await page.getByLabel('Stock').inputValue()
await page.getByRole('button', { name: /Total Products/ }).click()
await page.getByRole('checkbox', { name: 'Select products on this page' }).click()
await page.getByRole('toolbar', { name: 'Bulk actions' }).waitFor({ state: 'visible' })
const selectedText = await page.locator('.bulk-bar__count').innerText()
await page.getByRole('button', { name: 'Clear' }).click()
await page.getByRole('tab', { name: 'Categories' }).click()
await page.getByRole('heading', { name: 'Categories' }).waitFor({ state: 'visible' })
await page.getByRole('tab', { name: 'Products' }).click()

await page.setViewportSize({ width: 390, height: 844 })
await page.screenshot({ path: path.resolve('documentation/design/products-refresh/2026-09-14/products-implementation-mobile.png'), fullPage: true })
const mobile = await page.evaluate(() => ({
  viewport: [window.innerWidth, window.innerHeight],
  documentWidth: document.documentElement.scrollWidth,
  bodyHeight: document.documentElement.scrollHeight,
  toolbarColumns: getComputedStyle(document.querySelector('.ptoolbar')).gridTemplateColumns,
  tableOverflowY: getComputedStyle(document.querySelector('.ptable')).overflowY,
}))

process.stdout.write(JSON.stringify({ desktop, mobile, lowStockValue, selectedText, consoleErrors, pageErrors }, null, 2))
await browser.close()
