package io.taskmigo.authorization.role.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.role.RoleInfo;

/// Typed Object Authorization contracts published by the Role feature.
public final class RoleAuthorization {
    private RoleAuthorization() {}

    public interface List extends ObjectAuthorizationSchema<RoleInfo> {}
}
