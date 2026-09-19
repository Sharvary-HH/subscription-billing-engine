# 4. One invoice per subscription per period is guaranteed by the database

Status: accepted

## Context

The recurring billing job runs on a schedule. A deploy, a retry or a second replica can run
it twice at the same time. Application-level checks ("is there already an invoice for this
period?") race: both runs read no invoice, both insert one.

## Decision

Three layers, deliberately redundant:

1. Each due subscription is claimed with `SELECT ... FOR UPDATE SKIP LOCKED` in its own
   short transaction. A concurrent run skips whatever this one holds instead of waiting.
2. Rolling the period forward and inserting the invoice commit together. If anything fails,
   the subscription is still due next run and no half-state is left behind.
3. A partial unique index on `invoices (subscription_id, period_start) WHERE kind =
   'RECURRING'` rejects a duplicate at commit whatever the application believed.

The index is partial because proration invoices legitimately share a subscription and a
start date (two plan changes on the same day).

`ConcurrentBillingIT` runs four copies of the job at once against twenty due subscriptions
and asserts exactly one new invoice each, and separately that the index alone rejects a
duplicate insert.

## Consequences

- The scheduler stays on in every replica. Kubernetes with two replicas is a demonstration
  of the guarantee, not a threat to it.
- A job run that trips the index logs a warning and moves on; nothing needs manual repair.
