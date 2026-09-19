package com.sharvary.billing.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs an externally triggered write exactly once per client-supplied key.
 *
 * <p>Usage: {@code idempotency.execute("subscriptions", key, request, Response.class, () -> ...)}.
 * The first call runs the supplier and stores its JSON result. Later calls with the same key and
 * the same request return the stored result without touching the supplier. Same key, different
 * body, is a client bug and is rejected rather than silently replayed.
 *
 * <p>The key row and the business write commit together, so a crash between them leaves no
 * half-recorded key. A concurrent duplicate that races the first request hits the primary key
 * on {@code idempotency_keys} at commit and rolls back, which is what we want: the database is
 * the final arbiter here, not an in-memory lock.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T execute(String scope, String key, Object request, Class<T> responseType, Supplier<T> action) {
        String hash = hash(request);
        Optional<IdempotencyKey> existing = repository.findById(new IdempotencyKey.Pk(scope, key));
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException(key);
            }
            return read(existing.get().getResponseBody(), responseType);
        }
        T result = action.get();
        repository.save(new IdempotencyKey(scope, key, hash, write(result), clock.instant()));
        return result;
    }

    String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(write(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise idempotent response", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not deserialise stored idempotent response", e);
        }
    }
}
