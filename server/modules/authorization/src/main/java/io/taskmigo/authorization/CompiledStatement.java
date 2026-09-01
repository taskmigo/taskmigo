package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Target;
import java.util.List;

record CompiledStatement(
    String key,
    String method,
    SafePathPattern path,
    Target target,
    Effect effect,
    boolean hasTopLevelEffect,
    AuthorizationExpression condition,
    List<CompiledFieldRule> fields,
    Origin origin
) {
    CompiledStatement {
        fields = List.copyOf(fields);
    }
}
