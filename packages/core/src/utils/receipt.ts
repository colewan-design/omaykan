import { businessModeLabel, formatCompactDate, formatCurrency, type OrderSummary } from '@pos/shared/index'

export interface ReceiptBusinessInfo {
  name: string
  imageUrl?: string
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function orderTypeLabel(order: OrderSummary): string {
  if (order.businessMode === 'nail-salon') {
    return order.orderType === 'dine_in' ? 'Walk-in' : 'Appointment'
  }
  return order.orderType === 'dine_in' ? 'Dine In' : 'Takeaway'
}

function paymentMethodLabel(order: OrderSummary): string {
  switch (order.paymentMethod) {
    case 'cash':
      return 'Cash'
    case 'card':
      return 'Card'
    case 'ewallet':
      return 'E-wallet'
  }
}

export function buildReceiptHtml(order: OrderSummary, business: ReceiptBusinessInfo): string {
  const businessName = escapeHtml(business.name || businessModeLabel(order.businessMode))

  const itemRows = order.items
    .map((item) => `
      <div class="receipt-item">
        <div class="receipt-item__name">${escapeHtml(item.quantity > 1 ? `${item.quantity}x ` : '')}${escapeHtml(item.name)}</div>
        <div class="receipt-item__total">${escapeHtml(formatCurrency(item.lineTotalCents))}</div>
      </div>
      ${item.quantity > 1
        ? `<div class="receipt-item__unit">${escapeHtml(formatCurrency(item.unitPriceCents))} each</div>`
        : ''}
    `)
    .join('')

  const cashRows = order.paymentMethod === 'cash'
    ? `
      <div class="receipt-row">
        <span>Tendered</span>
        <span>${escapeHtml(formatCurrency(order.tenderedCents))}</span>
      </div>
      <div class="receipt-row">
        <span>Change</span>
        <span>${escapeHtml(formatCurrency(order.changeCents))}</span>
      </div>
    `
    : ''

  return `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>Receipt ${escapeHtml(order.ticketNumber)}</title>
<style>
  @page { size: 80mm auto; margin: 4mm; }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 0;
    width: 72mm;
    font-family: 'Courier New', Courier, monospace;
    font-size: 12px;
    color: #000;
  }
  .receipt-center { text-align: center; }
  .receipt-business-name { font-size: 15px; font-weight: 700; margin: 0 0 2px; }
  .receipt-meta { margin: 0; font-size: 11px; }
  .receipt-divider { border-top: 1px dashed #000; margin: 8px 0; }
  .receipt-row { display: flex; justify-content: space-between; gap: 8px; }
  .receipt-item { display: flex; justify-content: space-between; gap: 8px; margin-top: 4px; }
  .receipt-item__name { flex: 1; }
  .receipt-item__unit { font-size: 10px; color: #444; margin-left: 2px; }
  .receipt-total-row { display: flex; justify-content: space-between; font-weight: 700; font-size: 14px; margin-top: 4px; }
  .receipt-footer { margin-top: 12px; font-size: 11px; }
</style>
</head>
<body>
  <div class="receipt-center">
    <p class="receipt-business-name">${businessName}</p>
    <p class="receipt-meta">Ticket ${escapeHtml(order.ticketNumber)}</p>
    <p class="receipt-meta">${escapeHtml(formatCompactDate(order.createdAt))}</p>
  </div>

  <div class="receipt-divider"></div>

  <div class="receipt-row">
    <span>Order type</span>
    <span>${escapeHtml(orderTypeLabel(order))}</span>
  </div>
  <div class="receipt-row">
    <span>Customer</span>
    <span>${escapeHtml(order.customerName)}</span>
  </div>
  <div class="receipt-row">
    <span>Payment</span>
    <span>${escapeHtml(paymentMethodLabel(order))}</span>
  </div>

  <div class="receipt-divider"></div>

  ${itemRows}

  <div class="receipt-divider"></div>

  <div class="receipt-row">
    <span>Subtotal</span>
    <span>${escapeHtml(formatCurrency(order.subtotalCents))}</span>
  </div>
  <div class="receipt-row">
    <span>Tax</span>
    <span>${escapeHtml(formatCurrency(order.taxCents))}</span>
  </div>
  <div class="receipt-total-row">
    <span>Total</span>
    <span>${escapeHtml(formatCurrency(order.totalCents))}</span>
  </div>

  ${cashRows}

  <div class="receipt-divider"></div>

  <div class="receipt-center receipt-footer">
    <p>Thank you for your purchase!</p>
  </div>
</body>
</html>`
}

function printHtmlDocument(html: string): void {
  const iframe = document.createElement('iframe')
  iframe.style.position = 'fixed'
  iframe.style.right = '0'
  iframe.style.bottom = '0'
  iframe.style.width = '0'
  iframe.style.height = '0'
  iframe.style.border = '0'
  document.body.appendChild(iframe)

  let cleaned = false
  const cleanup = () => {
    if (cleaned) return
    cleaned = true
    iframe.remove()
  }
  // afterprint fires once the browser's print dialog closes; the timeout is a
  // fallback for browsers/print pipelines that never emit it.
  const fallback = window.setTimeout(cleanup, 60000)

  iframe.onload = () => {
    const win = iframe.contentWindow
    if (!win) {
      window.clearTimeout(fallback)
      cleanup()
      return
    }

    win.addEventListener('afterprint', () => {
      window.clearTimeout(fallback)
      cleanup()
    })
    win.focus()
    win.print()
  }

  const doc = iframe.contentDocument
  if (!doc) {
    iframe.remove()
    return
  }

  doc.open()
  doc.write(html)
  doc.close()
}

export function printReceipt(order: OrderSummary, business: ReceiptBusinessInfo): void {
  printHtmlDocument(buildReceiptHtml(order, business))
}

function buildTestReceiptHtml(business: ReceiptBusinessInfo): string {
  const businessName = escapeHtml(business.name || 'Omaykan')
  const timestamp = escapeHtml(new Date().toLocaleString())

  return `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>Test print</title>
<style>
  @page { size: 80mm auto; margin: 4mm; }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 0;
    width: 72mm;
    font-family: 'Courier New', Courier, monospace;
    font-size: 12px;
    color: #000;
    text-align: center;
  }
  .receipt-business-name { font-size: 15px; font-weight: 700; margin: 0 0 2px; }
  .receipt-meta { margin: 0; font-size: 11px; }
  .receipt-divider { border-top: 1px dashed #000; margin: 8px 0; }
</style>
</head>
<body>
  <p class="receipt-business-name">${businessName}</p>
  <p class="receipt-meta">Printer test page</p>
  <div class="receipt-divider"></div>
  <p class="receipt-meta">Printed ${timestamp}</p>
  <p class="receipt-meta">If this printed correctly, your receipt printer is ready to use.</p>
</body>
</html>`
}

export function printTestReceipt(business: ReceiptBusinessInfo): void {
  printHtmlDocument(buildTestReceiptHtml(business))
}
