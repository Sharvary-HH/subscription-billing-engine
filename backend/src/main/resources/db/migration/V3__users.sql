-- Login accounts. A customer user is pinned to exactly one customer row; admins have none.

CREATE TABLE users (
    id            UUID        PRIMARY KEY,
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER')),
    customer_id   UUID        REFERENCES customers (id),
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_customer_role CHECK (
        (role = 'CUSTOMER' AND customer_id IS NOT NULL) OR (role = 'ADMIN' AND customer_id IS NULL)
    )
);

CREATE UNIQUE INDEX uq_users_customer ON users (customer_id) WHERE customer_id IS NOT NULL;
