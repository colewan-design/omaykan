/*
 * How the operator portal writes numbers and dates.
 *
 * One module so that a peso on the dashboard, a peso in the order table and a
 * peso in a chart tooltip are the same peso. The rounding rule in particular
 * is a judgement — see below — and it should be made once.
 */

/**
 * Money, from centavos.
 *
 * Whole pesos by default: the portal's numbers are totals across a
 * marketplace, where two decimal places are noise that makes a column of
 * figures harder to scan. `exact` is for the places where the number is a
 * single transaction someone may be reconciling against a receipt.
 */
export function pesos(cents: number, options: { exact?: boolean } = {}): string {
  const value = (cents ?? 0) / 100

  return `₱${value.toLocaleString('en-PH', {
    minimumFractionDigits: options.exact ? 2 : 0,
    maximumFractionDigits: options.exact ? 2 : 0,
  })}`
}

/** Shortened for chart axes, where the full figure will not fit. */
export function pesosCompact(cents: number): string {
  const value = (cents ?? 0) / 100

  if (Math.abs(value) >= 1_000_000) return `₱${(value / 1_000_000).toFixed(1)}M`
  if (Math.abs(value) >= 1_000) return `₱${Math.round(value / 1_000)}K`

  return `₱${Math.round(value)}`
}

export function count(value: number): string {
  return (value ?? 0).toLocaleString('en-PH')
}

/**
 * A change against the previous window, or null when there was nothing to
 * compare against. Null is rendered as an em dash by the tiles rather than as
 * "+0%", because "no basis for comparison" and "no change" are different.
 */
export function percentDelta(value: number | null): string | null {
  if (value === null || value === undefined) return null

  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

export function shortDate(iso: string | null): string {
  if (!iso) return '—'

  return new Date(iso).toLocaleDateString('en-PH', { month: 'short', day: 'numeric', year: 'numeric' })
}

/** Axis ticks: "May 1". The year is in the range label above the chart. */
export function axisDate(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-PH', { month: 'short', day: 'numeric' })
}

export function dateTime(iso: string | null): string {
  if (!iso) return '—'

  return new Date(iso).toLocaleString('en-PH', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

/** The four words, title-cased for a pill. */
export const PROGRESS_LABELS: Record<string, string> = {
  processing: 'Processing',
  shipped: 'Shipped',
  completed: 'Completed',
  cancelled: 'Cancelled',
}
