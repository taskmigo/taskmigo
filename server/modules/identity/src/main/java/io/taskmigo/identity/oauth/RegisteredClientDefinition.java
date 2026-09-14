package io.taskmigo.identity.oauth;

import java.util.Objects;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/// Carries a Spring registered client together with its Taskmigo-owned classification.
public record RegisteredClientDefinition(RegisteredClient registeredClient, String type) {
    public RegisteredClientDefinition {
        Objects.requireNonNull(registeredClient, "registeredClient cannot be null");
        RegisteredClientType.requireValid(Objects.requireNonNull(type, "type cannot be null"));
    }
}
