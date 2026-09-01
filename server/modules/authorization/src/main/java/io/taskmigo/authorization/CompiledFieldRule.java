package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Effect;
import java.util.List;

record CompiledFieldRule(Effect effect, List<String> names, AuthorizationExpression condition) {
    CompiledFieldRule {
        names = List.copyOf(names);
    }
}
