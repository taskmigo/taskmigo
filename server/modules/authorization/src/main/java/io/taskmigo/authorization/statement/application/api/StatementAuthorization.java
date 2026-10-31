package io.taskmigo.authorization.statement.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.statement.StatementInfo;

/// Typed Object Authorization contracts published by the Statement feature.
public final class StatementAuthorization {

    private StatementAuthorization() {}

    public interface List extends ObjectAuthorizationSchema<StatementInfo> {}
}
