package io.taskmigo.authorization.spi;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import java.util.Collection;
import java.util.List;

/// Resolves the Object Authorization schemas governed by a Statement API target.
public interface ObjectAuthorizationTargetResolver {
    /// Returns every schema whose application route may be governed by the supplied target.
    List<ObjectAuthorizationSchema<?>> applicable(String method, String path);

    /// Creates the framework-free fallback that applies every known schema to every target.
    static ObjectAuthorizationTargetResolver all(Collection<? extends ObjectAuthorizationSchema<?>> schemas) {
        List<ObjectAuthorizationSchema<?>> declared = schemas
            .stream()
            .map(schema -> (ObjectAuthorizationSchema<?>) schema)
            .toList();
        return (method, path) -> declared;
    }
}
