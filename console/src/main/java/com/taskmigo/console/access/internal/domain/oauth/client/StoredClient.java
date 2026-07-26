package com.taskmigo.console.access.internal.domain.oauth.client;

import java.time.Instant;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

public record StoredClient(
    RegisteredClient client, boolean active, boolean manualOverride, Instant updatedAt) {}
