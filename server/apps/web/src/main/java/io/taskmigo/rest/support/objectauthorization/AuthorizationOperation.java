package io.taskmigo.rest.support.objectauthorization;

import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;

/// Carries the immutable authorization state and normalized target for one MVC authorization operation.
public record AuthorizationOperation(AuthorizationSnapshot snapshot, String method, String path) {
    /// Identifies the request attribute used to transport the snapshot from Spring Security to Spring MVC.
    public static final String SNAPSHOT_ATTRIBUTE = "taskmigo.authorization.snapshot";
}
