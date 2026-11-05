package io.taskmigo.identity.user.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.identity.user.UserInfo;

/// Typed Object Authorization contracts published by the User feature.
public final class UserAuthorization {

    private UserAuthorization() {}

    public interface List extends ObjectAuthorizationSchema<UserInfo> {}

    public interface Delete extends ObjectAuthorizationSchema<UserInfo> {}

    public interface UpdateStatements extends ObjectAuthorizationSchema<UserInfo> {}
}
