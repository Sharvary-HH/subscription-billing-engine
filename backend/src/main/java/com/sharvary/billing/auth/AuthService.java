package com.sharvary.billing.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.clock = clock;
    }

    public record Session(String token, AuthUser user) {
    }

    @Transactional(readOnly = true)
    public Session login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new BadCredentialsException("bad credentials"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("bad credentials");
        }
        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getRole(), user.getCustomerId());
        return new Session(jwt.issue(principal), principal);
    }

    @Transactional
    public User createUser(String email, String password, Role role, UUID customerId) {
        users.findByEmailIgnoreCase(email).ifPresent(u -> {
            throw new IllegalArgumentException("a login for " + email + " already exists");
        });
        return users.save(new User(UUID.randomUUID(), email, encoder.encode(password), role, customerId, clock.instant()));
    }
}
