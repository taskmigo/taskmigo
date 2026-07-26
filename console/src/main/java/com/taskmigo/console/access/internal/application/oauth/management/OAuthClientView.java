package com.taskmigo.console.access.internal.application.oauth.management;

import java.time.Instant;

public record OAuthClientView(
    String clientId,
    String clientName,
    boolean active,
    boolean configured,
    boolean manualOverride,
    Instant updatedAt) {}
