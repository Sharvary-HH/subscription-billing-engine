# 8. Time is injected, never read from the system clock in business code

Status: accepted

## Context

Billing is a function of time. If services call `LocalDate.now()`, a test of "advance two
months and see two invoices" has to either wait two months or mock a static method, and a
demo can only show what happened to be due at the moment someone opened it.

## Decision

Every service takes a `java.time.Clock` through its constructor. Production wires
`Clock.systemUTC()`. Tests wire a `MutableClock` fixed at a known instant and step it. The
demo profile wires the same `MutableClock` and exposes admin endpoints that advance it a day
at a time, running the billing and dunning jobs after each step so retries land on the day
they were scheduled for rather than all at once.

The seeder is built on the same idea: it subscribes customers, then walks the clock forward
six months and lets the real jobs produce the history a visitor sees. No invoice in the demo
was written by hand.

## Consequences

- `grep -r "now()" backend/src/main` finds only the seeder's real-time start point and the
  clock bean itself.
- A testability decision made in week one is what makes the whole system demonstrable.
