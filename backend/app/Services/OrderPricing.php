<?php

namespace App\Services;

/**
 * Pricing an order with a discount — the server's copy of `priceOrder` in
 * packages/shared. **Change one and change both.**
 *
 * VAT is charged per line at that line's own rate, and an order-level
 * discount comes off before tax: it is shared across the lines in proportion
 * to their value — whole centavos, floored, the remainder on the largest line —
 * and each line is taxed on what is left of it.
 *
 * Rates here are percentages (12.00), as `products.tax_rate` holds them; the
 * TypeScript copy takes fractions. The arithmetic is otherwise identical, down
 * to rounding half away from zero on positive amounts.
 */
class OrderPricing
{
    /**
     * @param  list<array{lineTotalCents: int, taxRatePercent: float}>  $lines
     * @return array{subtotalCents: int, discountCents: int, taxCents: int, totalCents: int}
     */
    public static function price(array $lines, int $requestedDiscountCents = 0): array
    {
        $subtotal = array_sum(array_column($lines, 'lineTotalCents'));
        $discount = max(0, min($subtotal, $requestedDiscountCents));

        $shares = array_map(
            fn (array $line) => $subtotal > 0 ? intdiv($discount * $line['lineTotalCents'], $subtotal) : 0,
            $lines,
        );

        $remainder = $discount - array_sum($shares);
        if ($remainder > 0 && $lines !== []) {
            $largest = 0;
            foreach ($lines as $index => $line) {
                if ($line['lineTotalCents'] > $lines[$largest]['lineTotalCents']) {
                    $largest = $index;
                }
            }
            $shares[$largest] += $remainder;
        }

        $tax = 0;
        foreach ($lines as $index => $line) {
            $tax += (int) round(($line['lineTotalCents'] - $shares[$index]) * $line['taxRatePercent'] / 100);
        }

        return [
            'subtotalCents' => $subtotal,
            'discountCents' => $discount,
            'taxCents' => $tax,
            'totalCents' => $subtotal - $discount + $tax,
        ];
    }
}
