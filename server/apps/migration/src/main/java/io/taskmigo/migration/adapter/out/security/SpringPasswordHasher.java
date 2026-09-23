package io.taskmigo.migration.adapter.out.security;

import io.taskmigo.migration.application.port.out.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/// Adapts installation credential hashing to Spring Security.
@Component
final class SpringPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    SpringPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String raw) {
        return this.passwordEncoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String encoded) {
        return this.passwordEncoder.matches(raw, encoded);
    }
}
