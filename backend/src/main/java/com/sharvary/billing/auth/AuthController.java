package com.sharvary.billing.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record SessionResponse(String token, UUID userId, String email, Role role, UUID customerId) {
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.Session session = auth.login(request.email(), request.password());
        AuthUser u = session.user();
        return new SessionResponse(session.token(), u.userId(), u.email(), u.role(), u.customerId());
    }

    @GetMapping("/me")
    public SessionResponse me() {
        AuthUser u = CurrentUser.get();
        return new SessionResponse(null, u.userId(), u.email(), u.role(), u.customerId());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail badCredentials() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setDetail("bad credentials");
        return pd;
    }
}
