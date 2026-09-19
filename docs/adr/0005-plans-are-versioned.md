# 5. Plans are versioned; a price change is a new version

Status: accepted

## Context

Prices change. If the plan row is updated in place, every existing subscription silently
moves to the new price, historical invoices no longer match the plan they reference, and
"what did this customer sign up for?" becomes unanswerable.

## Decision

A `Plan` is just an identity (code, name, active). Everything with a price lives on a
`PlanVersion`: currency, interval, pricing model, base price and tiers. Versions are
immutable once created. Publishing a new price inserts version N+1. A subscription points at
the version it subscribed on and stays there until it is explicitly changed, which goes
through the normal proration path.

## Consequences

- Invoices and proration always compute from the version the subscription actually holds.
- Retiring a plan blocks new subscriptions but changes nothing for existing ones.
- The admin console shows every version of a plan; the customer console offers only the
  current version of each active plan.
