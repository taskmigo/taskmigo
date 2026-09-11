package io.taskmigo.auth.authorization.object;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/// Resolves the Object Authorization schemas applicable to an API Statement target.
public interface ObjectAuthorizationSchemaRegistry {
    /// Returns every route/schema registration known to the application boundary.
    Collection<ObjectAuthorizationSchemaRegistration> registrations();

    /// Returns schemas whose registered routes may be governed by the supplied Statement target.
    default List<ObjectAuthorizationSchema<?>> applicable(String method, String path) {
        return this.registrations()
            .stream()
            .filter(registration -> registration.matches(method, path))
            .<ObjectAuthorizationSchema<?>>map(registration -> registration.schema())
            .distinct()
            .toList();
    }

    /// Creates a registry from immutable application route registrations.
    static ObjectAuthorizationSchemaRegistry of(Collection<ObjectAuthorizationSchemaRegistration> registrations) {
        List<ObjectAuthorizationSchemaRegistration> declared = List.copyOf(registrations);
        if (declared.isEmpty()) {
            throw new IllegalArgumentException("at least one object schema route is required");
        }
        return () -> declared;
    }

    /// Creates the explicit fallback registry used by framework-free authorization tests and bootstrap.
    static ObjectAuthorizationSchemaRegistry all(Collection<ObjectAuthorizationSchema<?>> schemas) {
        List<ObjectAuthorizationSchema<?>> declaredSchemas = List.copyOf(schemas);
        List<ObjectAuthorizationSchemaRegistration> registrations = declaredSchemas
            .stream()
            .map(schema -> new ObjectAuthorizationSchemaRegistration("*", ".*", Objects.requireNonNull(schema)))
            .toList();
        return new ObjectAuthorizationSchemaRegistry() {
            @Override
            public Collection<ObjectAuthorizationSchemaRegistration> registrations() {
                return registrations;
            }

            @Override
            public List<ObjectAuthorizationSchema<?>> applicable(String method, String path) {
                return declaredSchemas;
            }
        };
    }
}
