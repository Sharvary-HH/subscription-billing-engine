# Glossary

**Anchor day** — the day of the month a subscription bills on, fixed when it starts. A
subscription anchored on the 31st bills on the 28th (or 29th) of February and returns to the
31st in March; the anchor is stored separately so it cannot drift to the 28th.

**Billing period** — the half-open date range `[start, end)` a recurring invoice covers. Plan
charges are billed in advance for the period that is starting; metered usage is billed in
arrears for the period that just closed, on the same invoice.

**Credit balance** — money the system owes a customer, carried forward and drawn on their
next invoice. Fed by downgrades. Never refunded automatically.

**Credit note** — the only way to correct a paid invoice. A separate, append-only document
with its own number; the amount is refunded through the provider, and when credit notes reach
the invoice total the invoice becomes `REFUNDED`.

**Dunning** — the process of chasing a failed payment: scheduled retries, notifications, and
eventually writing the invoice off and suspending the subscription. Here it is a persisted
state machine, one case per failed invoice.

**Idempotency key** — a client-supplied token on a write that might be retried. The first
request with a key runs; later requests with the same key and body get the same response
without running again.

**Largest-remainder allocation** — splitting an integer total across weighted parts so the
parts sum exactly to the total: floor each share, then hand the leftover units to the parts
with the biggest fractional remainders.

**MRR** — monthly recurring revenue: the sum of base plan prices for subscriptions that are
`ACTIVE` or `PAST_DUE`, with annual plans counted at a twelfth. Usage is not included, since
it is not recurring.

**Metered pricing** — charging for usage recorded during a period rather than a fixed price.
*Tiered* charges each band's units at that band's rate; *volume* charges every unit at the
rate of the band the total falls in.

**Minor units** — the smallest unit of a currency: cents, pence, paise. All money is stored
and computed in minor units as integers.

**Plan version** — an immutable priced snapshot of a plan. A price change publishes a new
version; existing subscriptions keep theirs.

**Proration** — charging for part of a period. On a mid-cycle change, the unused part of the
old price is credited and the unused part of the new price is charged, both by calendar day.

**Skip locked** — `SELECT ... FOR UPDATE SKIP LOCKED`: claim a row for this transaction and
let concurrent claimers skip it rather than wait. How two copies of the billing job share the
work without double-billing.

**Time travel** — the demo's control over the injected clock. Advancing it runs the real
billing and dunning jobs for each elapsed day.
