package io.taskmigo.auth.authorization.request;

import java.util.Map;

/// Carries the already available HTTP request values used by Request Authorization.
public record AuthorizationRequest(String method, String path, Map<String, String> pathVariables) {
    public AuthorizationRequest {
        pathVariables = Map.copyOf(pathVariables);
    }
}
