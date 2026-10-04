package io.taskmigo.authorization.object.application.port.out;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.language.SchemaContext;
import java.util.Collection;
import java.util.List;

/// Resolves the Object Authorization schemas governed by a Statement API target.
public interface ObjectAuthorizationTargetResolver {
    /// Returns every schema whose application route may be governed by the supplied compiled target.
    List<ObjectAuthorizationBinding<?>> applicable(
        String method,
        StatementTargetPathMatcher pathMatcher
    );

    /// Returns the effective bindings for a target using provider-neutral runtime schema inputs.
    default List<ObjectAuthorizationBinding<?>> applicable(
        String method,
        StatementTargetPathMatcher pathMatcher,
        SchemaContext context
    ) {
        return this.applicable(method, pathMatcher);
    }

    /// Creates the framework-free fallback that applies every known schema to every target.
    static ObjectAuthorizationTargetResolver all(Collection<? extends ObjectAuthorizationBinding<?>> bindings) {
        List<ObjectAuthorizationBinding<?>> declared = List.copyOf(bindings);
        return (method, pathMatcher) -> declared;
    }
}
