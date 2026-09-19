# 3. Splitting an amount uses largest-remainder allocation

Status: accepted

## Context

An invoice's tax is computed once on the taxable subtotal, and the line items need a per-line
tax column that adds up to that figure. Splitting 100.00 three ways by dividing gives
33.33 x 3 = 99.99. The missing unit has to go somewhere, and it has to go there
deterministically so two runs of the same calculation produce the same invoice.

## Decision

`Allocation.largestRemainder(total, weights)`: each part gets the floor of its exact share;
the leftover units (always fewer than there are parts) go one at a time to the parts with the
largest fractional remainders, ties broken by index. Negative totals are allocated on the
absolute value and flipped back.

The single invariant is `sum(parts) == total`. `AllocationProperties` checks it for thousands
of generated totals and weight vectors, including negative totals and zero weights.

## Consequences

- `Money.allocate(int)` and `Money.allocate(long[])` are the only ways to split an amount.
- Per-line tax always sums to invoice tax. The same helper is available for any future
  split, such as spreading a discount across lines.
- Ties are resolved by position, so the first line can be one unit larger than the last on
  an otherwise equal split. That is documented rather than hidden.
