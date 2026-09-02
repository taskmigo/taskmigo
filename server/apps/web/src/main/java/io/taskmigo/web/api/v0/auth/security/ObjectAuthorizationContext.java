package io.taskmigo.web.api.v0.auth.security;

import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;

/// Builds object authorization plans for authenticated user API requests.
public final class ObjectAuthorizationContext {

    private ObjectAuthorizationContext() {}

    /// Resolves the current user's object plan, or returns null for managed service principals.
    public static ObjectAuthorizationService.@Nullable ObjectAuthorizationPlan plan(
        ObjectAuthorizationService authorization,
        @Nullable Jwt jwt,
        String method,
        String path
    ) {
        if (jwt == null) return null;
        String principalType = jwt.getClaimAsString("principal_type");
        if (!"user".equals(principalType) && !"service".equals(principalType)) return null;
        String userId = jwt.getClaimAsString("user_id");
        if (userId == null) return null;
        UUID id = UUID.fromString(userId);
        return authorization.plan(
            id,
            method,
            path,
            Map.of(
                "principal",
                Map.of("id", userId, "username", principalUsername(jwt)),
                "request",
                Map.of("method", method, "path", path)
            )
        );
    }

    private static String principalUsername(Jwt jwt) {
        String username = jwt.getClaimAsString("principal_username");
        return username == null ? Objects.requireNonNull(jwt.getSubject()) : username;
    }
}
