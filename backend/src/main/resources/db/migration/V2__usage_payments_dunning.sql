-- Usage metering, payment attempts, the dunning state machine, credit notes and notifications.

-- Append-only. There is no UPDATE or DELETE path for this table anywhere in the application.
CREATE TABLE usage_records (
    id                   UUID        PRIMARY KEY,
    subscription_item_id UUID        NOT NULL REFERENCES subscription_items (id),
    quantity             BIGINT      NOT NULL CHECK (quantity > 0),
    recorded_at          TIMESTAMPTZ NOT NULL,
    idempotency_key      TEXT        NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_usage_records_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_usage_records_item_time ON usage_records (subscription_item_id, recorded_at);

CREATE TABLE payment_attempts (
    id              UUID        PRIMARY KEY,
    invoice_id      UUID        NOT NULL REFERENCES invoices (id),
    attempt_number  INTEGER     NOT NULL,
    idempotency_key TEXT        NOT NULL,
    amount_minor    BIGINT      NOT NULL,
    currency        CHAR(3)     NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED', 'TIMED_OUT')),
    provider_ref    TEXT,
    failure_reason  TEXT,
    triggered_by    TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_payment_attempts_invoice_number UNIQUE (invoice_id, attempt_number)
);

-- The provider idempotency key is deliberately not unique: a retry after a timeout reuses the
-- key of the attempt whose outcome is unknown, so the provider can tell us what happened
-- instead of charging again.

-- One dunning case per invoice that has failed payment. The state column is the state machine;
-- there are no boolean flags here on purpose.
CREATE TABLE dunning_cases (
    id              UUID        PRIMARY KEY,
    invoice_id      UUID        NOT NULL REFERENCES invoices (id),
    subscription_id UUID        NOT NULL REFERENCES subscriptions (id),
    state           TEXT        NOT NULL CHECK (state IN ('RETRYING', 'RECOVERED', 'EXHAUSTED', 'CANCELED')),
    retries_done    INTEGER     NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    started_at      TIMESTAMPTZ NOT NULL,
    resolved_at     TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_dunning_cases_invoice UNIQUE (invoice_id)
);

CREATE INDEX ix_dunning_cases_due ON dunning_cases (next_retry_at) WHERE state = 'RETRYING';

CREATE SEQUENCE credit_note_number_seq START 1;

CREATE TABLE credit_notes (
    id                 UUID        PRIMARY KEY,
    credit_note_number TEXT        NOT NULL,
    invoice_id         UUID        NOT NULL REFERENCES invoices (id),
    reason             TEXT        NOT NULL,
    currency           CHAR(3)     NOT NULL,
    total_minor        BIGINT      NOT NULL CHECK (total_minor > 0),
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_credit_notes_number UNIQUE (credit_note_number)
);

CREATE INDEX ix_credit_notes_invoice ON credit_notes (invoice_id);

CREATE TABLE credit_note_lines (
    id             UUID    PRIMARY KEY,
    credit_note_id UUID    NOT NULL REFERENCES credit_notes (id),
    line_index     INTEGER NOT NULL,
    description    TEXT    NOT NULL,
    amount_minor   BIGINT  NOT NULL CHECK (amount_minor > 0),
    CONSTRAINT uq_credit_note_lines_order UNIQUE (credit_note_id, line_index)
);

-- Notifications are recorded, not delivered. The row is what a real mailer would have sent.
CREATE TABLE notifications (
    id          UUID        PRIMARY KEY,
    customer_id UUID        NOT NULL REFERENCES customers (id),
    kind        TEXT        NOT NULL,
    subject     TEXT        NOT NULL,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_notifications_customer ON notifications (customer_id, created_at DESC);
