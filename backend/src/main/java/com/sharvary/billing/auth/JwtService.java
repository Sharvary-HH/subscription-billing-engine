package com.sharvary.billing.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Token lifetime is measured on the wall clock, deliberately not the injected billing clock.
 * A session is an infrastructure concern: advancing the demo clock by a month must not log the
 * visitor out, and a test that steps time must not have its token expire under it.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock wallClock = Clock.systemUTC();

    public JwtService(@Value("${billing.jwt.secret}") String secret,
                      @Value("${billing.jwt.ttl-minutes:480}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String issue(AuthUser user) {
        Date now = Date.from(wallClock.instant());
        var builder = Jwts.builder()
                .subject(user.userId().toString())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .issuedAt(now)
                .expiration(Date.from(wallClock.instant().plus(ttl)));
        if (user.customerId() != null) {
            builder.claim("customerId", user.customerId().toString());
        }
        return builder.signWith(key).compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).clock(() -> Date.from(wallClock.instant())).build()
                    .parseSignedClaims(token).getPayload();
            String customer = claims.get("customerId", String.class);
            return Optional.of(new AuthUser(UUID.fromString(claims.getSubject()), claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class)), customer == null ? null : UUID.fromString(customer)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
