package io.taskmigo.authorization.object.application.resolution;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import java.util.Collection;
import java.util.List;

/// Resolves Object Authorization schemas governed by a Statement API target.
public interface ObjectAuthorizationTargetResolver {
    /// Returns every authorization schema whose application route may be governed by the compiled target.
    List<ObjectAuthorizationSchema<?>> applicable(String method, StatementTargetPathMatcher pathMatcher);

    /// Creates the framework-free fallback that applies every known authorization schema to every target.
    static ObjectAuthorizationTargetResolver all(Collection<? extends ObjectAuthorizationSchema<?>> schemas) {
        List<ObjectAuthorizationSchema<?>> declared = List.copyOf(schemas);
        return (method, pathMatcher) -> declared;
    }
}
