package io.taskmigo.auth.authorization.embeddedlanguage;

import io.taskmigo.auth.authorization.object.AuthorizationObjectQueryDialect;
import io.taskmigo.auth.authorization.object.FilterSchema;
import io.taskmigo.embeddedlanguage.EnvironmentSchema;
import io.taskmigo.embeddedlanguage.LanguageType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// Builds the consumer-owned Embedded Language schemas used by authorization.
public final class AuthorizationEmbeddedLanguageSchemas {

    private AuthorizationEmbeddedLanguageSchemas() {}

    /// Returns the request-only schema required by the authorization specification.
    public static EnvironmentSchema request() {
        return new EnvironmentSchema(
            "taskmigo.authorization.request.0.3.1",
            Map.of(
                "principal",
                root(Map.of("id", string(), "username", string())),
                "request",
                root(Map.of("method", string(), "path", string(), "pathVariables", dynamicString()))
            )
        );
    }

    /// Returns an object schema containing the fields registered by all dialects.
    public static EnvironmentSchema object(List<? extends AuthorizationObjectQueryDialect> dialects) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (AuthorizationObjectQueryDialect dialect : dialects) {
            dialect.fields().forEach((name, type) -> fields.putIfAbsent(name, field(name, type, dialect)));
        }
        return new EnvironmentSchema(
            "taskmigo.authorization.object.0.3.1",
            Map.of(
                "principal",
                root(Map.of("id", string(), "username", string())),
                "request",
                root(Map.of("method", string(), "path", string(), "pathVariables", dynamicString())),
                "object",
                root(fields)
            )
        );
    }

    /// Returns an object schema for one concrete query dialect.
    public static EnvironmentSchema object(AuthorizationObjectQueryDialect dialect) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        dialect.fields().forEach((name, type) -> fields.put(name, field(name, type, dialect)));
        return new EnvironmentSchema(
            "taskmigo.authorization.object.0.3.1." + dialect.method() + dialect.path(),
            Map.of(
                "principal",
                root(Map.of("id", string(), "username", string())),
                "request",
                root(Map.of("method", string(), "path", string(), "pathVariables", dynamicString())),
                "object",
                root(fields)
            )
        );
    }

    private static EnvironmentSchema.Root root(Map<String, EnvironmentSchema.Field> fields) {
        return new EnvironmentSchema.Root(string(), fields);
    }

    private static EnvironmentSchema.Field field(String name, Class<?> type, FilterSchema schema) {
        boolean nullable = schema.nullableFields().contains(name);
        return new EnvironmentSchema.Field(policyType(type), nullable, true, true);
    }

    private static EnvironmentSchema.Field string() {
        return new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false, false);
    }

    private static EnvironmentSchema.Field dynamicString() {
        return new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false, false, LanguageType.Scalar.STRING);
    }

    private static LanguageType policyType(Class<?> type) {
        if (type == String.class || type == UUID.class) {
            return LanguageType.Scalar.STRING;
        }
        if (type == Boolean.class || type == boolean.class) {
            return LanguageType.Scalar.BOOL;
        }
        if (
            Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class && type != char.class)
        ) {
            return LanguageType.Scalar.NUMBER;
        }
        throw new IllegalArgumentException("unsupported authorization query field type: " + type.getName());
    }
}
