package io.taskmigo.authorization.object.application.port.out;

import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.List;

/// Resolves operation query schemas governed by a Statement API target.
public interface ObjectAuthorizationTargetResolver {
    /// Returns every operation schema whose application route may be governed by the compiled target.
    List<QuerySchemaView> applicable(String method, StatementTargetPathMatcher pathMatcher);

    /// Creates the framework-free fallback that applies every known schema to every target.
    static ObjectAuthorizationTargetResolver all(Collection<? extends QuerySchemaView> schemas) {
        List<QuerySchemaView> declared = List.copyOf(schemas);
        return (method, pathMatcher) -> declared;
    }
}
