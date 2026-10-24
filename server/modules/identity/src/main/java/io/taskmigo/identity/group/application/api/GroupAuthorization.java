package io.taskmigo.identity.group.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.identity.group.GroupInfo;

/// Typed Object Authorization contracts published by the Group feature.
public final class GroupAuthorization {
    private GroupAuthorization() {}

    public interface List extends ObjectAuthorizationSchema<GroupInfo> {}
}
