# 2. Proration is daily, computed through a cumulative function

Status: accepted

## Context

When a customer changes plan mid-period they owe the old plan for the days they used it and
the new plan for the days remaining. Two decisions hide in that sentence: the granularity
(days or seconds) and how rounding is distributed when the price does not divide evenly.

Per-span rounding (`round(price * days / total)` for each span) breaks a property that
finance teams check: if a customer upgrades, downgrades and upgrades again inside one
period, the total charged should equal the sum of each plan's price for the days it was
active. With per-span rounding the four spans can drift by a cent or two from the whole.

## Decision

Granularity is the calendar day. A change on any time of a day takes effect from that date.
Most providers do the same, it is what customers expect to see on an invoice, and it keeps
the calculator free of time zones.

The amount for a span is defined through a cumulative function:

    owed(d)        = floor(price * d / daysInPeriod)
    charge(a, b)   = owed(b) - owed(a)

Because every span is a difference of the same cumulative function, any partition of the
period telescopes back to exactly `price`, no matter how many changes happen. No individual
day is worth more than one minor unit more than any other. The property test in
`ProrationProperties` generates random prices, periods and cut points and asserts this.

Upgrades (net charge) are invoiced immediately. Downgrades (net credit) go on the customer's
credit balance and are drawn on the next invoice. Nothing is refunded for a downgrade.

## Consequences

- `ProrationCalculator` is a pure function with no clock, no repository and no Spring.
- The change-churn integration test asserts the exact telescoping equality end to end
  through the real invoices and the credit balance.
- A credit larger than the next invoice simply stays on the balance and keeps drawing down.
