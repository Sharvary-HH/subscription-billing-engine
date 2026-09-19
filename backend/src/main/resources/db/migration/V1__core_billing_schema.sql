-- Core billing schema: customers, versioned plans, subscriptions, invoices.
-- All money columns are BIGINT minor units. The currency lives on the aggregate root
-- (customer, plan version, invoice) so line items never carry a currency of their own.

CREATE TABLE customers (
    id                   UUID PRIMARY KEY,
    name                 TEXT        NOT NULL,
    email                TEXT        NOT NULL,
    currency             CHAR(3)     NOT NULL,
    tax_region           TEXT        NOT NULL,
    credit_balance_minor BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_customers_email UNIQUE (email)
);

CREATE TABLE payment_methods (
    id             UUID PRIMARY KEY,
    customer_id    UUID        NOT NULL REFERENCES customers (id),
    provider_token TEXT        NOT NULL,
    brand          TEXT        NOT NULL,
    last4          CHAR(4)     NOT NULL,
    is_default     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_payment_methods_customer ON payment_methods (customer_id);

-- Exactly one default per customer, enforced by the database rather than by application code.
CREATE UNIQUE INDEX uq_payment_methods_default ON payment_methods (customer_id) WHERE is_default;

CREATE TABLE plans (
    id         UUID PRIMARY KEY,
    code       TEXT        NOT NULL,
    name       TEXT        NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_plans_code UNIQUE (code)
);

-- A price change never edits a version; it inserts the next one. Subscriptions pin a version.
CREATE TABLE plan_versions (
    id               UUID PRIMARY KEY,
    plan_id          UUID        NOT NULL REFERENCES plans (id),
    version          INTEGER     NOT NULL,
    currency         CHAR(3)     NOT NULL,
    billing_interval TEXT        NOT NULL CHECK (billing_interval IN ('MONTHLY', 'ANNUAL')),
    pricing_model    TEXT        NOT NULL CHECK (pricing_model IN ('FLAT', 'PER_SEAT', 'TIERED', 'VOLUME')),
    base_price_minor BIGINT      NOT NULL CHECK (base_price_minor >= 0),
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_plan_versions_plan_version UNIQUE (plan_id, version)
);

-- Bands for TIERED and VOLUME pricing. up_to NULL means "and everything above".
CREATE TABLE price_tiers (
    id               UUID PRIMARY KEY,
    plan_version_id  UUID    NOT NULL REFERENCES plan_versions (id),
    tier_index       INTEGER NOT NULL,
    up_to            BIGINT,
    unit_price_minor BIGINT  NOT NULL CHECK (unit_price_minor >= 0),
    flat_fee_minor   BIGINT  NOT NULL DEFAULT 0 CHECK (flat_fee_minor >= 0),
    CONSTRAINT uq_price_tiers_order UNIQUE (plan_version_id, tier_index)
);

CREATE TABLE subscriptions (
    id                   UUID PRIMARY KEY,
    customer_id          UUID        NOT NULL REFERENCES customers (id),
    plan_version_id      UUID        NOT NULL REFERENCES plan_versions (id),
    quantity             INTEGER     NOT NULL CHECK (quantity > 0),
    status               TEXT        NOT NULL CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'PAUSED', 'CANCELED')),
    anchor_day           INTEGER     NOT NULL CHECK (anchor_day BETWEEN 1 AND 31),
    current_period_start DATE        NOT NULL,
    current_period_end   DATE        NOT NULL,
    trial_end            DATE,
    cancel_at            DATE,
    canceled_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_subscriptions_period CHECK (current_period_end > current_period_start)
);

CREATE INDEX ix_subscriptions_customer ON subscriptions (customer_id);
-- The billing job's claim query: due subscriptions by period end, only in billable states.
CREATE INDEX ix_subscriptions_due ON subscriptions (current_period_end)
    WHERE status IN ('TRIALING', 'ACTIVE', 'PAST_DUE');

-- Metered add-ons attached to a subscription. The base plan itself is on the subscription row.
CREATE TABLE subscription_items (
    id              UUID PRIMARY KEY,
    subscription_id UUID        NOT NULL REFERENCES subscriptions (id),
    plan_version_id UUID        NOT NULL REFERENCES plan_versions (id),
    quantity        INTEGER     NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_subscription_items_subscription ON subscription_items (subscription_id);

CREATE SEQUENCE invoice_number_seq START 1000;

CREATE TABLE invoices (
    id                UUID PRIMARY KEY,
    invoice_number    TEXT        NOT NULL,
    customer_id       UUID        NOT NULL REFERENCES customers (id),
    subscription_id   UUID        REFERENCES subscriptions (id),
    status            TEXT        NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'PAID', 'VOID', 'UNCOLLECTIBLE', 'REFUNDED')),
    kind              TEXT        NOT NULL CHECK (kind IN ('RECURRING', 'PRORATION', 'FINAL')),
    currency          CHAR(3)     NOT NULL,
    period_start      DATE        NOT NULL,
    period_end        DATE        NOT NULL,
    subtotal_minor    BIGINT      NOT NULL DEFAULT 0,
    tax_minor         BIGINT      NOT NULL DEFAULT 0,
    total_minor       BIGINT      NOT NULL DEFAULT 0,
    amount_paid_minor BIGINT      NOT NULL DEFAULT 0,
    issued_at         TIMESTAMPTZ,
    due_at            TIMESTAMPTZ,
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_invoices_number UNIQUE (invoice_number)
);

-- The final guard against double billing. Two concurrent job runs can both pass every
-- application check; only one of them can commit a recurring invoice for a given period.
-- Proration invoices are excluded: two plan changes on the same day are legitimate.
CREATE UNIQUE INDEX uq_invoices_subscription_period
    ON invoices (subscription_id, period_start) WHERE kind = 'RECURRING';

CREATE INDEX ix_invoices_customer ON invoices (customer_id, created_at DESC);
CREATE INDEX ix_invoices_status ON invoices (status);

CREATE TABLE invoice_line_items (
    id               UUID    PRIMARY KEY,
    invoice_id       UUID    NOT NULL REFERENCES invoices (id),
    line_index       INTEGER NOT NULL,
    line_type        TEXT    NOT NULL CHECK (line_type IN ('PLAN', 'USAGE', 'PRORATION_CREDIT', 'PRORATION_CHARGE', 'CREDIT_BALANCE')),
    description      TEXT    NOT NULL,
    quantity         BIGINT  NOT NULL,
    unit_price_minor BIGINT  NOT NULL,
    amount_minor     BIGINT  NOT NULL,
    tax_minor        BIGINT  NOT NULL DEFAULT 0,
    period_start     DATE    NOT NULL,
    period_end       DATE    NOT NULL,
    proration        BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_invoice_line_items_order UNIQUE (invoice_id, line_index)
);

-- Replay store for client idempotency keys. One row per (scope, key), holding the response that
-- was returned the first time so a retry gets the same answer.
CREATE TABLE idempotency_keys (
    scope         TEXT        NOT NULL,
    idem_key      TEXT        NOT NULL,
    request_hash  TEXT        NOT NULL,
    response_body TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scope, idem_key)
);
