package com.sharvary.billing.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One row per (scope, key). The stored response is replayed verbatim when the same key arrives
 * again with the same request hash; a different hash for the same key is rejected.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.Pk.class)
public class IdempotencyKey {

    @Id
    @Column(name = "scope", nullable = false)
    private String scope;

    @Id
    @Column(name = "idem_key", nullable = false)
    private String key;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String scope, String key, String requestHash, String responseBody, Instant createdAt) {
        this.scope = scope;
        this.key = key;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public String getScope() {
        return scope;
    }

    public String getKey() {
        return key;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static class Pk implements Serializable {
        private String scope;
        private String key;

        public Pk() {
        }

        public Pk(String scope, String key) {
            this.scope = scope;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Pk pk && scope.equals(pk.scope) && key.equals(pk.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scope, key);
        }
    }
}
