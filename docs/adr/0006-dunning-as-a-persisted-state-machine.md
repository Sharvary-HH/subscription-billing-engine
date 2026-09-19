# 6. Failed-payment recovery is an explicit persisted state machine

Status: accepted

## Context

The tempting implementation is a few columns on the invoice: `retry_count`, `next_retry`,
`is_suspended`, `card_updated`. Each new edge case adds a flag, the flags start
contradicting each other, and eventually nobody can say what state an invoice is in.

## Decision

A `DunningCase` row per failed invoice, with exactly one `state` column
(`RETRYING`, `RECOVERED`, `EXHAUSTED`, `CANCELED`), `retries_done` and `started_at`. The
next retry time is a function of those three and the schedule (days 1, 3, 5, 7 from
`started_at`), not an independent fact that can drift.

Rules, all in `DunningService`:

- First failure opens the case and moves the subscription to `PAST_DUE`.
- A failed retry advances `retries_done`; after the last one the case is `EXHAUSTED`, the
  invoice `UNCOLLECTIBLE`, the subscription `CANCELED`.
- Any successful charge resolves the case `RECOVERED`. The job only ever picks `RETRYING`
  cases, so retry 4 cannot fire after retry 3 succeeded.
- A card update restarts the sequence from now and requests an immediate retry.
- Cancelling the subscription closes the case `CANCELED`. The invoice stays `OPEN`: the debt
  is real, and an admin can still collect or void it.
- Every charge goes through one method that locks the invoice row and checks it is still
  `OPEN`, so a scheduled retry and a manual "pay now" at the same instant produce one charge.

## Consequences

- Illegal transitions throw, and the state table is tested exhaustively.
- The console's dunning queue is a straight read of `RETRYING` cases with their attempts.
