package io.taskmigo.authorization.request.application.service;

/// Represents the internal result of evaluating Request Authorization policy.
record RequestAuthorizationDecision(boolean allowed) {}
