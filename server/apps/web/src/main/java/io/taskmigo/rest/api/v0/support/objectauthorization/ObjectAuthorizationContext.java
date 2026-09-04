package io.taskmigo.rest.api.v0.support.objectauthorization;

import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/// Builds object authorization plans for authenticated user API requests.
public final class ObjectAuthorizationContext {

    /// Stores the snapshot created by the request authorization manager on the current HTTP request.
    public static final String SNAPSHOT_ATTRIBUTE = "taskmigo.authorization.snapshot";

    private ObjectAuthorizationContext() {}

    /// Resolves the object plan from the request authorization snapshot.
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
        AuthorizationSnapshot snapshot = currentSnapshot();
        if (snapshot == null) throw new IllegalStateException("authorization snapshot is missing for object access");
        return authorization.plan(snapshot, method, path);
    }

    private static @Nullable AuthorizationSnapshot currentSnapshot() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return null;
        Object snapshot = servlet.getRequest().getAttribute(SNAPSHOT_ATTRIBUTE);
        return snapshot instanceof AuthorizationSnapshot value ? value : null;
    }
}
