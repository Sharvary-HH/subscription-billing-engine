# 7. A retry after a provider timeout reuses the idempotency key

Status: accepted

## Context

A declined card is the easy failure: the provider said no, nothing moved. A timeout is the
hard one: the request may have charged the customer and the answer was lost. Retrying with a
fresh request risks charging twice; not retrying risks never collecting.

## Decision

Every charge carries an idempotency key. For a first attempt and for retries after a decline
it is `invoiceId:attemptNumber`. If the previous attempt is `TIMED_OUT`, the next attempt
reuses that attempt's key. A provider that honours idempotency keys (all the real ones do)
then returns the outcome of the original request instead of charging again, which resolves
the uncertainty either way.

The mock provider models this: a timed-out charge is, by default, treated as having gone
through on the provider side, and a replay with the same key reports success without
incrementing the charge counter. `DunningIT` asserts the customer is charged exactly once.

The same idea applies one layer up: `POST /admin/subscriptions` and `POST /usage` take a
client `Idempotency-Key`; a replay returns the stored response, and the same key with a
different body is refused rather than silently replayed.

## Consequences

- `payment_attempts.idempotency_key` is deliberately not unique.
- Tests can flip the mock between "timeout that charged" and "timeout that did not" and check
  both paths.
