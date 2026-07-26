package com.taskmigo.console.access.internal.application.oauth.management;

import java.time.Instant;

public record DeletionConfirmation(String clientId, String confirmationToken, Instant expiresAt) {}
