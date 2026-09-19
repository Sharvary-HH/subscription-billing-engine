# 1. Money is an integer number of minor units plus a currency

Status: accepted

## Context

Every amount in a billing system is added, multiplied by a quantity, split by a ratio, taxed
and summed, over and over. Floating point cannot represent 0.10 exactly, so any of those
operations can lose or invent a cent, and the error only shows up when a total does not
match its parts. `BigDecimal` fixes representation but still leaves a rounding decision
at every multiply, and it is easy to forget the currency.

## Decision

`Money` is a Java record of `long minor` and `java.util.Currency`. Addition, subtraction
and multiplication by an integer stay in the integers. Multiplication by a ratio takes an
explicit `RoundingMode`. Combining two currencies throws. There is no constructor that
accepts a `double`, and `double`/`float` do not appear anywhere in the domain.

Over the wire, an amount is `{"amount": "19.99", "minor": 1999, "currency": "USD"}`. The
decimal is a string so a JSON parser cannot turn it into a double; the frontend formats the
string and never does arithmetic.

## Consequences

- The database stores `BIGINT` minor units and a `CHAR(3)` currency on each aggregate root.
- Splitting an amount uses largest-remainder allocation (ADR 3), never division per part.
- Currencies with zero minor units (JPY) work without special casing; `toMajor()` uses the
  currency's own fraction digits.
